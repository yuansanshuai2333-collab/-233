package com.nonid.affinity.service.policy;

import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * The minimal state of one companion's ongoing fight.
 *
 * <p>Extracted so the production controller and its tests drive the SAME transitions, rather than a
 * test-only copy that can drift. It holds no Minecraft types: the caller supplies ids and ticks.
 *
 * <p>Progress has two sources, and deliberately no third:
 * <ul>
 *   <li>closing distance on the target, and</li>
 *   <li>landing a real hit on the target.</li>
 * </ul>
 * There is no "refresh every round" path. A companion standing still and missing must eventually be
 * judged stalled, or an unreachable or invulnerable target would be chased forever.
 */
public final class EngagementState {

    /** Sentinel meaning "no progress recorded", distinct from any real tick including 0. */
    private static final long NO_PROGRESS = -1L;

    /** Target this companion is fighting, or null when not engaged. */
    private UUID targetId;

    /**
     * When progress was last observed, in server ticks.
     *
     * <p>-1 means "no fight in progress". Zero is NOT usable as that sentinel: tick 0 is a legal
     * server tick, and treating it as "unset" made a fight that began at tick 0 look permanently
     * fresh, so it could never be judged stalled.
     */
    private long lastProgressTick = NO_PROGRESS;

    /**
     * Monotonic counter incremented every time a new fight begins.
     *
     * <p>Used to reject late reports from a previous fight: a hit resolved after the engagement
     * changed must not count towards the new one.
     */
    private long generation;

    /**
     * A target this companion was just made to give up.
     *
     * <p>Prevents the chase-recall loop: without it, a companion recalled at the distance limit would
     * immediately re-select the same enemy on the next scan.
     */
    /** Target given up by the most recent end(), kept for a recall that arrives afterwards. */
    private UUID lastAbandoned;
    private long lastAbandonedSequence;
    /** Watermark of the fight currently running, captured into lastAbandoned when it ends. */
    private long engagedSequenceSnapshot;

    private UUID suppressedTarget;
    private long suppressedUntilTick;
    /** Event watermark at the moment of suppression; facts at or below it cannot undo the release. */
    private long suppressedSequence;

    /**
     * Whether the current suppression came from a STALL rather than a recall.
     *
     * <p>Diagnostics only: the two exits reach the same rule, and a reader of the debug log needs to
     * know which one released the fight.
     */
    private boolean suppressedByStall;

    /**
     * True while the companion is travelling back after a recall.
     *
     * <p>Distinct from suppression: suppression blocks one specific target, returning blocks ALL new
     * engagements until the companion is back with its owner.
     */
    private boolean returning;
    private long returningUntilTick;

    /** Begin (or retarget) a fight. Starts a fresh generation, so late hits for the old target die. */
    public void begin(UUID newTargetId, long nowTick) {
        begin(newTargetId, nowTick, 0L);
    }

    /** Begin a fight, recording the event watermark that justified it. */
    public void begin(UUID newTargetId, long nowTick, long sequence) {
        Objects.requireNonNull(newTargetId);
        this.targetId = newTargetId;
        this.lastProgressTick = nowTick;
        this.engagedSequenceSnapshot = sequence;
        this.generation++;
    }

    /** End the fight. Any later hit report belongs to a generation that no longer exists. */
    public void end() {
        // Capture BEFORE clearing. The recall handler runs later and may find nothing left here: the
        // controller can end an out-of-range fight first and the recall arrives second.
        if (this.targetId != null) {
            this.lastAbandoned = this.targetId;
            this.lastAbandonedSequence = this.engagedSequenceSnapshot;
        }
        this.targetId = null;
        this.lastProgressTick = NO_PROGRESS;
        this.generation++;
        // Suppression and returning are deliberately NOT cleared here: they exist to outlive the fight
        // that just ended. Clearing them would re-open the very loop this class guards against.
    }

    /** The target most recently given up, retained so a later recall can suppress it. */
    @Nullable
    public UUID lastAbandoned() {
        return lastAbandoned;
    }

    /** Event watermark at the moment that target was abandoned. */
    public long lastAbandonedSequence() {
        return lastAbandonedSequence;
    }

    public boolean isEngaged() {
        return targetId != null;
    }

    @Nullable
    public UUID targetId() {
        return targetId;
    }

    public long generation() {
        return generation;
    }

    public long lastProgressTick() {
        return lastProgressTick;
    }

    /**
     * Record a landing hit.
     *
     * <p><b>What this does NOT do:</b> it does not verify which fight the hit belongs to. The target id
     * is compared, so a hit on a DIFFERENT creature is rejected, but a delayed projectile that lands on
     * the SAME creature after this fight ended and a new one began would still renew the new fight.
     * Use {@link #recordHitAtGeneration} for that: it rejects a stamp from a fight that is over. This
     * form remains for callers that hold no launch stamp.
     *
     * @param victimId  who was actually damaged
     * @param nowTick   server tick of the damage
     * @return whether this counted as progress for the current fight
     */
    public boolean recordHit(UUID victimId, long nowTick) {
        if (targetId == null || !targetId.equals(victimId)) {
            // Either not fighting, or the hit landed on something else. A hit on another creature is
            // not evidence that this fight is progressing.
            return false;
        }
        lastProgressTick = nowTick;
        return true;
    }

    /**
     * Record a landing hit that was LAUNCHED under a known generation.
     *
     * <p>This is the form the controller uses. {@link #recordHit} alone compares only the target id,
     * which cannot tell an arrow fired during this fight from one fired during an earlier fight against
     * the same creature: both land on the same victim. The launch generation is captured when the
     * attack is initiated, so a hit whose stamp belongs to a fight that has since ended is rejected
     * here instead of renewing the current one.
     *
     * @param victimId            who was actually damaged
     * @param launchGeneration    the generation recorded when the attack was launched
     * @param nowTick             server tick of the damage
     * @return whether this counted as progress for the current fight
     */
    public boolean recordHitAtGeneration(UUID victimId, long launchGeneration, long nowTick) {
        if (!ownsGeneration(launchGeneration)) {
            // A late report from a fight that no longer exists. It must not renew anything: the fight
            // it belonged to is over, and its "progress" says nothing about the current one.
            return false;
        }
        return recordHit(victimId, nowTick);
    }

    /** Record progress made by closing on the target. */
    public boolean recordApproach(long nowTick) {
        if (targetId == null) {
            return false;
        }
        lastProgressTick = nowTick;
        return true;
    }

    /**
     * Whether the fight has stalled.
     *
     * @param nowTick  current server tick
     * @param limitTicks how long without progress is tolerated; zero disables the check
     */
    public boolean isStalled(long nowTick, long limitTicks) {
        if (targetId == null || limitTicks <= 0L || lastProgressTick == NO_PROGRESS) {
            return false;
        }
        return nowTick - lastProgressTick > limitTicks;
    }

    /**
     * Remember that a target was given up, together with the event watermark at that moment.
     */
    public void suppress(UUID targetId, long sequence, long untilTick) {
        this.suppressedTarget = targetId;
        this.suppressedSequence = sequence;
        this.suppressedUntilTick = untilTick;
        this.suppressedByStall = false;
    }

    /**
     * Give up a target that made NO progress, and keep it off limits for a while.
     *
     * <p>This is the stall exit's own path, and it deliberately does not share the recall exit's event
     * watermark. A recall answer is a fresh decision to go back, so a fact newer than the release is
     * new information and may legitimately re-open the target. A stall is the opposite: there is no
     * new decision, the fight simply was not working. Continuous evidence keeps arriving for an
     * unreachable enemy -- {@code scanHostility} republishes it every second -- so a watermark-based
     * suppression would be undone by the very next scan and the companion would walk back into the
     * same unreachable fight forever. Here the window itself is the whole rule: inside it no fact,
     * however new, re-opens THIS target.
     *
     * <p>Scoped to one target id, so an unrelated threat beside the companion is still answered.
     *
     * @param targetId   the target that stalled; ignored when null
     * @param untilTick  the tick at which the target becomes available again
     * @return whether a suppression was actually established
     */
    public boolean suppressStalledTarget(@Nullable UUID targetId, long untilTick) {
        if (targetId == null) {
            return false;
        }
        this.suppressedTarget = targetId;
        this.suppressedSequence = Long.MAX_VALUE;
        this.suppressedUntilTick = untilTick;
        this.suppressedByStall = true;
        return true;
    }

    /** Whether the current suppression window was established by a stall rather than a recall. */
    public boolean suppressedByStall() {
        return suppressedByStall;
    }

    /**
     * Whether this exact target is still blocked by a suppression window, reading only the clock.
     *
     * <p>Used by the controller to re-validate the Selection it has ALREADY made in the same round
     * the stall was detected: the round's candidate was evaluated before the exit ran, so nothing else
     * stops that same target from being written straight back.
     */
    public boolean isSuppressedAt(@Nullable UUID candidateId, long nowTick) {
        if (suppressedTarget == null || candidateId == null) {
            return false;
        }
        if (nowTick >= suppressedUntilTick) {
            // The window closed: the target becomes available again and must not stay banned forever.
            // The watermark goes with it. A STALL window records MAX_VALUE as its watermark, and leaving
            // that behind would keep refusing every fact for this target forever, because the
            // sequence <= suppressedSequence test would still match -- a permanent ban disguised as an
            // expired window.
            suppressedTarget = null;
            suppressedSequence = 0L;
            suppressedByStall = false;
            return false;
        }
        return suppressedTarget.equals(candidateId);
    }

    /** Whether this target is still suppressed. */
    public boolean isSuppressed(UUID candidateId, long nowTick) {
        // Delegates so the production selection path and this older entry point cannot drift apart.
        return isSuppressedAt(candidateId, nowTick);
    }

    /** The event watermark recorded when the target was suppressed. */
    public long suppressedSequence() {
        return suppressedSequence;
    }

    /** Enter the returning state after a recall. */
    public void beginReturning(long untilTick) {
        this.returning = true;
        this.returningUntilTick = untilTick;
    }

    /** Whether the companion is still travelling back. */
    public boolean isReturning(long nowTick) {
        if (!returning) {
            return false;
        }
        if (nowTick >= returningUntilTick) {
            returning = false;
            return false;
        }
        return true;
    }

    /** Leave the returning state early, e.g. once the companion is back with its owner. */
    public void endReturning() {
        this.returning = false;
    }

    /**
     * Whether a candidate may be engaged right now.
     *
     * <p>THE single rule, used by production and by the tests. Every condition must hold together;
     * none of them alone is sufficient:
     *
     * <ul>
     *   <li><b>eligibility</b> -- the caller has already established the target is a legal threat
     *       (alive, same world, not protected, the creature can fight);</li>
     *   <li><b>follow range</b> -- a companion further from its owner than the recall distance never
     *       starts a fight, however fresh the evidence is;</li>
     *   <li><b>event watermark</b> -- facts at or below the suppression watermark are from before the
     *       release and cannot undo it;</li>
     *   <li><b>recovery</b> -- while returning, a DISTANT target stays off limits, but once the
     *       companion is back within range a nearby threat is answered immediately rather than being
     *       blocked for a fixed timer.</li>
     * </ul>
     *
     * <p>Suppression expiring is NOT permission on its own: the follow-range check still applies, so a
     * window closing can never restart an out-of-range chase.
     *
     * @param candidateId        the candidate target
     * @param nowTick            current server tick
     * @param sequence           event sequence of the fact proposing this candidate
     * @param distanceFromOwner  companion's distance to its owner, in blocks
     * @param recallDistance     the configured recall distance
     */
    public boolean canEngage(UUID candidateId, long nowTick, long sequence,
                             double distanceFromOwner, double recallDistance) {
        // Kept for callers that only know the companion's distance. Delegates so the two paths cannot
        // drift apart; production uses the mode-aware form.
        return canEngageUnderMode(candidateId, nowTick, sequence, true, distanceFromOwner,
                distanceFromOwner, recallDistance, 0.0);
    }

    /**
     * Whether a candidate may be engaged, with the range rule applied per mode.
     *
     * <p>The single rule production and the tests share.
     *
     * <p>Range is NOT applied to every mode:
     * <ul>
     *   <li><b>FOLLOW / ATTACK</b> -- bounded by the owner, on both the companion's distance and the
     *       target's distance. The target bound is what stops a recalled companion from resuming a
     *       chase against an enemy that is still far from the owner.</li>
     *   <li><b>WAIT</b> -- never bounded by the owner. It answers an attacker from where it stands and
     *       must keep that ability when the owner walks off or logs out.</li>
     *   <li><b>WANDER</b> -- no follow-range constraint.</li>
     * </ul>
     *
     * <p>Re-entry is deliberately stricter than the exit bound: a target already being fought is
     * released beyond {@code recallDistance}, but a fresh engagement additionally needs the target to
     * be inside {@code recallDistance - reengageMargin}. Without that gap a target sitting exactly on
     * the boundary would be dropped and re-acquired every round.
     *
     * @param candidateId      the candidate target
     * @param nowTick          current server tick
     * @param sequence         event sequence proposing this candidate
     * @param ownerRange       whether the owner's range applies in this mode
     * @param companionToOwner companion's distance to its owner
     * @param targetToOwner    the TARGET's distance to the owner; kept separate on purpose
     * @param recallDistance   the configured recall distance (ten blocks)
     * @param reengageMargin   extra margin required to START a fight
     */
    public boolean canEngageUnderMode(UUID candidateId, long nowTick, long sequence,
                                      boolean ownerRange, double companionToOwner,
                                      double targetToOwner, double recallDistance,
                                      double reengageMargin) {
        if (candidateId == null) {
            return false;
        }
        // Facts from before the release cannot undo it, while the release is still in force. Only the
        // suppressed target is affected, so an unrelated nearby target with a smaller sequence is free.
        // Once the clock passes the window the watermark is cleared too (see isSuppressedAt), because an
        // expired suppression must leave nothing behind that can still refuse a fact.
        if (suppressedTarget != null && suppressedTarget.equals(candidateId)
                && nowTick < suppressedUntilTick && sequence <= suppressedSequence) {
            return false;
        }
        if (isSuppressed(candidateId, nowTick)) {
            return false;
        }
        if (ownerRange) {
            double reengageLimit = Math.max(1.0, recallDistance - reengageMargin);
            if (companionToOwner > reengageLimit) {
                return false;
            }
            // The target's own distance matters: otherwise a companion standing beside its owner
            // happily charges something fourteen blocks away.
            if (targetToOwner > reengageLimit) {
                return false;
            }
        }
        if (isReturning(nowTick)) {
            // Returning blocks a DISTANT target; once the companion is genuinely back, a nearby threat
            // is answered immediately instead of waiting out a fixed timer.
            if (ownerRange && companionToOwner > recallDistance) {
                return false;
            }
            returning = false;
        }
        return true;
    }

    /**
     * Convenience overload for callers without a distance, used only where range is not relevant.
     *
     * @deprecated prefer the full form; range is part of the rule.
     */
    @Deprecated
    public boolean canEngage(UUID candidateId, long nowTick) {
        return candidateId != null && !isReturning(nowTick) && !isSuppressed(candidateId, nowTick);
    }

    /**
     * Apply the outcome of a recall attempt.
     *
     * <p>Pure, so the success and failure paths can be tested without a world. On success the target
     * most recently abandoned is suppressed and the companion starts returning; on failure NOTHING is
     * changed, so a failed recall never looks like a successful one.
     *
     * <p>Uses {@link #lastAbandoned()} rather than the live target, because the controller commonly
     * ends an out-of-range fight before the recall is attempted, leaving the live target null.
     *
     * @param recallSucceeded whether the recall actually happened
     * @param nowTick        current server tick
     * @param suppressTicks  how long the abandoned target stays off limits
     * @param returnTicks    how long the returning state lasts
     * @return whether the success path ran
     */
    public boolean applyRecallResult(boolean recallSucceeded, long nowTick, long suppressTicks,
                                     long returnTicks) {
        if (!recallSucceeded) {
            // A failed recall must not suppress anything or change state: it did not happen.
            return false;
        }
        UUID abandoned = lastAbandoned;
        if (abandoned != null) {
            suppress(abandoned, lastAbandonedSequence, nowTick + suppressTicks);
        }
        beginReturning(nowTick + returnTicks);
        return true;
    }

    /** Whether a report tagged with this generation belongs to the fight in progress. */
    public boolean ownsGeneration(long reportedGeneration) {
        return generation == reportedGeneration;
    }
}
