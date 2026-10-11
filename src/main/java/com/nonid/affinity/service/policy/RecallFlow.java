package com.nonid.affinity.service.policy;

import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * The recall call chain, with the world behind a thin adapter.
 *
 * <p>Why this exists: the recall behaviour was previously only reachable through
 * {@code CompanionRecall.tryRecall}, which needs a live {@code ServerWorld}, a {@code MobEntity} and
 * a loaded chunk. Nothing about the ordering that actually matters could be tested, so "the success
 * callback runs exactly once" and "a failure runs it not at all" were claims without evidence -- and
 * the failure case is exactly where a false success would hide, because a failed recall that runs the
 * success path leaves a suppression and a returning window behind for a teleport that never happened.
 *
 * <p>So what the flow needs from the world is reduced to ONE adapter method, which reports both facts
 * the decision depends on at once: whether the teleport happened, and where the companion ended up.
 * Production supplies a lambda that calls {@code CompanionRecall.needed} and
 * {@code CompanionRecall.tryRecall} directly, so the distance rule still has exactly one
 * implementation. Tests supply a double and observe the call chain instead of the world.
 *
 * <p>The adapter reports an OUTCOME rather than a boolean. A boolean was not enough: the returning
 * window has to know whether the companion is back beside its owner, and only the caller of the
 * teleport can see that. Reporting only success forced the decision onto the pre-attempt distance,
 * which is outside the recall radius by definition -- so the "already back" branch was dead in
 * production while a test double made it look covered.
 *
 * <p>Deliberately free of Minecraft types: this is what makes it runnable in a plain JUnit process.
 */
public final class RecallFlow {

    /**
     * Everything the recall decision reads, sampled once by the caller.
     *
     * @param companionId       the companion being recalled
     * @param ownerId           its owner, or null when the owner cannot be resolved
     * @param ownerUsable       the owner is alive and not spectating
     * @param distanceToOwner   current companion-to-owner distance in blocks, BEFORE the attempt
     * @param sameWorld         the owner is in the companion's world
     * @param recallDistance    the configured recall distance
     * @param nowTick           current server tick
     * @param suppressTicks     how long the abandoned target stays off limits
     * @param returnTicks       how long the returning window lasts when the companion is still out
     */
    public record RecallContext(UUID companionId, @Nullable UUID ownerId, boolean ownerUsable,
                                double distanceToOwner, boolean sameWorld, double recallDistance,
                                long nowTick, long suppressTicks, long returnTicks) {
        public RecallContext {
            Objects.requireNonNull(companionId, "companionId");
            if (ownerId == null && ownerUsable) {
                // An unusable owner flag with no owner at all is a contradiction, and it is the shape
                // that let the eligibility rule read only one of the two fields and still look correct.
                // Refused here so the two can never disagree.
                throw new IllegalArgumentException("an unusable-owner context must not claim a usable owner");
            }
        }
    }

    /**
     * What one recall attempt actually did.
     *
     * @param recalled              whether the companion really moved
     * @param distanceAfterRecall   the companion's distance to its owner AFTER the attempt, or
     *                              {@link Double#NaN} when nothing can be measured (the attempt failed,
     *                              or the owner is gone). The returning-window decision reads ONLY this:
     *                              the pre-attempt distance is the distance that made the recall due, so
     *                              it is outside the recall radius by definition and can never answer
     *                              "is the companion back now?".
     */
    public record RecallOutcome(boolean recalled, double distanceAfterRecall) {

        /** Nothing moved. */
        public static RecallOutcome notRecalled() {
            return new RecallOutcome(false, Double.NaN);
        }

        /** A recall happened, and this is where the companion ended up. */
        public static RecallOutcome recalledAt(double distanceAfter) {
            return new RecallOutcome(true, distanceAfter);
        }
    }

    /**
     * The thin adapter: everything this flow needs from the world.
     *
     * <p>{@code attempt} owns the side effect, so it must be called exactly once and its outcome is what
     * the flow routes. Reporting a distance it did not actually observe is a failure of the adapter, not
     * a detail: the returning window depends on it.
     */
    @FunctionalInterface
    public interface RecallPort {
        /**
         * Attempt the recall and report what happened, including where the companion ended up.
         *
         * @return the outcome; {@code recalled = false} means nothing happened and no success
         *         transition may run
         */
        RecallOutcome attempt(RecallContext context);
    }

    private RecallFlow() {
    }

    /**
     * The ONE recall-eligibility rule.
     *
     * <p>Kept as a single method on purpose. The rule was previously written in more than one place --
     * here, and again inline in the follow service around the port's own call -- which is how "attempt
     * it" and "should it have been attempted at all" could disagree.
     *
     * <p>Three conditions, all required, and all the caller's business to sample honestly:
     * <ul>
     *   <li>the owner must be resolvable, alive and not a spectator;</li>
     *   <li>the owner must be in the companion's own world -- this stage does not teleport across
     *       dimensions or load distant chunks; and</li>
     *   <li>the companion must be STRICTLY further than the recall distance. Exactly ten blocks is not
     *       yet a recall.</li>
     * </ul>
     */
    public static boolean recallDue(boolean ownerUsable, boolean sameWorld, double distanceToOwner,
                                    double recallDistance) {
        if (!ownerUsable || !sameWorld) {
            // Nothing to return to: an owner in another dimension, offline, dead or spectating.
            return false;
        }
        return distanceToOwner > recallDistance;
    }

    /** Convenience for the common case where the caller already resolved a usable owner. */
    public static boolean recallDue(RecallContext context) {
        // ownerUsable is read here too. Leaving it out would let the record form of the rule disagree
        // with the four-argument form: a caller holding an owner id that is dead or spectating would
        // have been told a recall was due.
        return recallDue(context.ownerUsable(), context.sameWorld(),
                context.distanceToOwner(), context.recallDistance());
    }

    /**
     * Run one recall attempt and route the outcome, calling the port exactly once.
     *
     * <p>On success the success transition runs and, when the companion is STILL out of range after the
     * attempt, the returning window opens. When it is back within range the window opens for zero ticks:
     * the reason for returning no longer applies, and blocking a nearby real threat for a fixed timer
     * would be a bug rather than a precaution.
     *
     * <p>On failure nothing happens at all -- no suppression, no returning window, no state change. This
     * is the property that a failure must never look like a success.
     *
     * @param state   the companion's engagement state, or null when it has none yet
     * @param context the sampled facts
     * @param port    the world adapter
     * @return the outcome the port reported, so the caller can see both that it happened and where
     */
    public static RecallOutcome attempt(@Nullable EngagementState state, RecallContext context,
                                        RecallPort port) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(port, "port");
        if (!recallDue(context)) {
            return RecallOutcome.notRecalled();
        }
        RecallOutcome outcome = Objects.requireNonNull(port.attempt(context), "port outcome");
        if (!outcome.recalled()) {
            // The teleport did not happen, so no consequence may be recorded. Returning early here is
            // the whole point of routing both outcomes through one place.
            return outcome;
        }
        if (state != null) {
            boolean stillOut = Double.isNaN(outcome.distanceAfterRecall())
                    // The adapter could not measure the new position. Treat that as "not back": opening
                    // no returning window would let the companion charge straight back out, which is the
                    // loop the window exists to break.
                    || outcome.distanceAfterRecall() > context.recallDistance();
            state.applyRecallResult(true, context.nowTick(), context.suppressTicks(),
                    stillOut ? context.returnTicks() : 0L);
        }
        return outcome;
    }

    /**
     * A port that never recalls anything.
     *
     * <p>Used when the recall stage is disabled, and as a safe default in tests that only care about
     * the decision.
     */
    public static RecallPort never() {
        return context -> RecallOutcome.notRecalled();
    }
}
