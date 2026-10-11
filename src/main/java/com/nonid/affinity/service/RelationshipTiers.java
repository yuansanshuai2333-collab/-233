package com.nonid.affinity.service;

/**
 * Historical four-tier proposal, retained for legacy callers only.
 * Runtime permissions use {@link RelationshipStages}, the user-confirmed five-stage policy.
 *
 * <p>Pure logic: the tier of a creature is a FUNCTION of the player's current affinity and the configured
 * boundaries, never a stored flag. That is what makes demotion possible at all -- the old design kept a
 * "befriended" flag that only ever went up, so losing affinity changed nothing.
 *
 * <p>Four contiguous ranges are derived from three boundaries, which is the only shape that cannot produce
 * overlapping ranges or gaps:
 *
 * <pre>
 * min &lt; friendlyLower &lt; familiarLower &lt; closeFriendLower &le; max
 * stranger   = [min, friendlyLower)
 * friendly   = [friendlyLower, familiarLower)
 * familiar   = [familiarLower, closeFriendLower)
 * closeFriend= [closeFriendLower, max]
 * </pre>
 *
 * <p>The LEFT side is strictly less than {@code min}: with {@code friendlyLower == min} the stranger range would
 * be empty.
 */
public final class RelationshipTiers {

    /** The four tiers, weakest first. */
    public enum Tier {
        /** Baseline behaviour. */
        STRANGER,
        /** Does not attack this player on its own; no companion commands. */
        FRIENDLY,
        /** Basic companion: follow, wait, protect the owner. */
        FAMILIAR,
        /** Full companion: mode switching, plus free trades when the other conditions hold. */
        CLOSE_FRIEND
    }

    /** The three boundaries plus the configured range. */
    public record Boundaries(int min, int friendlyLower, int familiarLower, int closeFriendLower, int max) {

        /** Whether the four ranges are all non-empty and contiguous. */
        public boolean isValid() {
            return this.min < this.friendlyLower
                    && this.friendlyLower < this.familiarLower
                    && this.familiarLower < this.closeFriendLower
                    && this.closeFriendLower <= this.max;
        }
    }

    /** The default boundaries: 0~29 / 30~59 / 60~89 / 90~100. */
    public static final Boundaries DEFAULT = new Boundaries(0, 30, 60, 90, 100);

    private RelationshipTiers() {
    }

    /**
     * The tier this affinity falls into.
     *
     * <p><b>Illegal boundaries are refused, not interpreted.</b> {@link Boundaries} is a plain record, so an
     * invalid one can be constructed -- and an invalid set would be silently read as "some tier" by any
     * comparison chain, which is exactly the kind of quiet wrong answer that is hard to trace later. The
     * configuration layer is responsible for publishing only validated boundaries (and for keeping the last valid
     * set when a player's edit is invalid); this check makes a mistake there fail loudly instead of producing a
     * plausible tier. It is a programming-error guard, not a runtime input path.
     *
     * @throws IllegalArgumentException when {@code boundaries} does not satisfy the four-range invariant
     */
    public static Tier tierFor(int affinity, Boundaries boundaries) {
        if (!boundaries.isValid()) {
            throw new IllegalArgumentException("invalid relationship boundaries: " + boundaries);
        }
        if (affinity < boundaries.friendlyLower()) {
            return Tier.STRANGER;
        }
        if (affinity < boundaries.familiarLower()) {
            return Tier.FRIENDLY;
        }
        if (affinity < boundaries.closeFriendLower()) {
            return Tier.FAMILIAR;
        }
        return Tier.CLOSE_FRIEND;
    }

    /**
     * Derive four ranges from a save that used the old single friend threshold.
     *
     * <p><b>The old threshold becomes the CLOSE FRIEND boundary, unchanged.</b> Everything below it is divided
     * into the three remaining ranges. The conditions are checked BEFORE the division, so there are no repair
     * branches afterwards: if they hold, the result is necessarily strictly increasing.
     *
     * <p>The subtraction is done in {@code long}. With a negative {@code min} the difference can exceed the
     * {@code int} range only in theory, but computing it in {@code int} first and widening afterwards would
     * already have overflowed -- so the widening happens before the subtraction, as a rule with no exceptions.
     *
     * @return the migrated boundaries, or {@code null} when migration is IMPOSSIBLE. A null result means "keep the
     *         old single-threshold rules and tell the player to adjust the configuration": it must never be
     *         replaced by an invented default, and the old threshold must never be moved to make the ranges fit.
     */
    public static Boundaries migrateFromLegacyThreshold(int min, int max, int legacyFriendThreshold) {
        long difference = (long) legacyFriendThreshold - (long) min;
        boolean rangeIsUsable = min < legacyFriendThreshold
                && legacyFriendThreshold <= max
                && difference >= 3L;
        if (!rangeIsUsable) {
            return null;
        }
        // The whole expression stays in long and is narrowed ONCE, at the end, through Math.toIntExact: writing
        // `min + (int) Math.floorDiv(...)` narrows the quotient first and then adds in int, which is a different
        // computation from the one this method claims to perform -- and would hide an out-of-range boundary
        // instead of failing.
        int friendlyLower = Math.toIntExact((long) min + Math.floorDiv(difference, 3L));
        int familiarLower = Math.toIntExact((long) min + Math.floorDiv(2L * difference, 3L));
        Boundaries migrated = new Boundaries(min, friendlyLower, familiarLower, legacyFriendThreshold, max);
        // Post-check, not a repair: the pre-conditions above make this hold, and if it somehow does not, the
        // honest answer is "cannot migrate" rather than a silently adjusted configuration.
        return migrated.isValid() ? migrated : null;
    }
}
