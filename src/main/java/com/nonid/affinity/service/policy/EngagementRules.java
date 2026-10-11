package com.nonid.affinity.service.policy;

import java.util.Objects;

/**
 * Whether an engagement that is already under way may continue.
 *
 * <p>Pure, and separate from both the short-lived evidence that STARTS a fight and the controller that
 * applies it. This split exists because of a real defect: the controller chose purely from evidence
 * facts, which expire after {@code companionEventMaxAgeTicks}. Once the fact that began a fight aged
 * out, the controller returned IGNORE and released its own target, so any melee that outlived the
 * evidence window stopped in the middle of fighting.
 *
 * <p>Evidence remains short-lived -- that is correct, it is what permits a fight to start. An
 * established engagement is instead re-validated every round against the conditions that actually
 * matter, and ends only for a real reason.
 */
public final class EngagementRules {

    private EngagementRules() {
    }

    /**
     * Why an engagement ended, or {@link #CONTINUE} while it may go on.
     *
     * <p>Each value is a genuine exit condition. "The evidence expired" is deliberately absent: that
     * is the bug this class exists to prevent.
     */
    public enum Outcome {
        CONTINUE,
        TARGET_GONE,
        TARGET_PROTECTED,
        WRONG_WORLD,
        OWNER_UNAVAILABLE,
        MODE_REJECTED,
        OUT_OF_RANGE,
        NO_PROGRESS
    }

    /**
     * Decide whether to keep fighting.
     *
     * @param alive            target is still alive
     * @param sameWorld        target is in the companion's world
     * @param protectedTarget  target is protected (owner, companion, tamed pet, friend, player)
     * @param ownerAvailable   the owner is present and usable
     * @param waitMode         the companion is in WAIT, where it only ever answers in place
     * @param selfDefence      this engagement began as self-defence rather than owner assistance
     * @param recallExceeded   the companion is further from its owner than the recall distance
     * @param withinLeash      the target is still within the pursuit limit
     * @param stalledTicks     ticks since the companion last closed distance on the target
     * @param stallLimitTicks  how long no progress is tolerated before giving up
     */
    public static Outcome evaluate(boolean alive, boolean sameWorld, boolean protectedTarget,
                                   boolean ownerAvailable, boolean waitMode, boolean selfDefence,
                                   boolean recallExceeded, boolean withinLeash, long stalledTicks,
                                   long stallLimitTicks) {
        if (!alive) {
            return Outcome.TARGET_GONE;
        }
        if (!sameWorld) {
            return Outcome.WRONG_WORLD;
        }
        if (protectedTarget) {
            return Outcome.TARGET_PROTECTED;
        }
        // WAIT answers only in place, and its own rules decide that; the checks below are about
        // chasing, which WAIT never does.
        if (waitMode) {
            return Outcome.MODE_REJECTED;
        }
        // Assistance needs the owner; self-defence does not, which is why WAIT can still fight back.
        if (!ownerAvailable && !selfDefence) {
            return Outcome.OWNER_UNAVAILABLE;
        }
        // The user's rule: beyond the recall distance the companion comes back, and that outranks
        // continuing a chase.
        if (recallExceeded) {
            return Outcome.OUT_OF_RANGE;
        }
        if (!withinLeash) {
            return Outcome.OUT_OF_RANGE;
        }
        if (stallLimitTicks > 0 && stalledTicks > stallLimitTicks) {
            return Outcome.NO_PROGRESS;
        }
        return Outcome.CONTINUE;
    }

    /** Convenience for callers that only need the yes/no answer. */
    public static boolean keeps(Outcome outcome) {
        return Objects.requireNonNull(outcome) == Outcome.CONTINUE;
    }

    /**
     * Whether to restore the module's own target, given what the creature is currently holding.
     *
     * <p>Restoring a target the vanilla AI CLEARED is correct; overwriting a target another system
     * currently holds is the ownership fault this module has already had to fix more than once. The
     * two cases must therefore be distinguishable, which is what this predicate encodes.
     *
     * @param holdingNothing      the creature currently has no target (cleared externally)
     * @param holdingOurTarget    the creature already holds the target this module claims
     */
    public static boolean shouldRestore(boolean holdingNothing, boolean holdingOurTarget) {
        return holdingNothing && !holdingOurTarget;
    }

    /** Whether an external live target must be left alone. */
    public static boolean shouldYield(boolean holdingNothing, boolean holdingOurTarget) {
        return !holdingNothing && !holdingOurTarget;
    }

    /**
     * Whether an engagement this module still records should have its target re-asserted.
     *
     * <p>Deliberately does NOT ask whether the physical target still matches the registry. A target
     * can be cleared or replaced by the vanilla AI while the fight is genuinely still in progress;
     * requiring a match there meant the whole keep-alive path was skipped and the fight was abandoned
     * with no outward sign. This is the exact defect the function exists to make testable.
     *
     * @param hasEngagementRecord the module still records this creature as engaged
     * @param outcome             the re-validation result for that engagement
     */
    public static boolean shouldReassert(boolean hasEngagementRecord, Outcome outcome) {
        return hasEngagementRecord && keeps(outcome);
    }
}
