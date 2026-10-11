package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nonid.affinity.service.RelationshipTiers.Boundaries;
import com.nonid.affinity.service.RelationshipTiers.Tier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The four tiers, the boundary invariant, and the migration algorithm with its four worked examples. */
class RelationshipTiersTest {

    @Test
    @DisplayName("the default boundaries give 0-29 / 30-59 / 60-89 / 90-100")
    void defaultBoundariesAndTiers() {
        assertTrue(RelationshipTiers.DEFAULT.isValid());
        assertEquals(Tier.STRANGER, RelationshipTiers.tierFor(0, RelationshipTiers.DEFAULT));
        assertEquals(Tier.STRANGER, RelationshipTiers.tierFor(29, RelationshipTiers.DEFAULT));
        assertEquals(Tier.FRIENDLY, RelationshipTiers.tierFor(30, RelationshipTiers.DEFAULT));
        assertEquals(Tier.FRIENDLY, RelationshipTiers.tierFor(59, RelationshipTiers.DEFAULT));
        assertEquals(Tier.FAMILIAR, RelationshipTiers.tierFor(60, RelationshipTiers.DEFAULT));
        assertEquals(Tier.FAMILIAR, RelationshipTiers.tierFor(89, RelationshipTiers.DEFAULT));
        assertEquals(Tier.CLOSE_FRIEND, RelationshipTiers.tierFor(90, RelationshipTiers.DEFAULT));
        assertEquals(Tier.CLOSE_FRIEND, RelationshipTiers.tierFor(100, RelationshipTiers.DEFAULT));
    }

    @Test
    @DisplayName("dropping below the close-friend boundary demotes immediately, and rising re-promotes")
    void tierFollowsAffinityInBothDirections() {
        assertEquals(Tier.CLOSE_FRIEND, RelationshipTiers.tierFor(95, RelationshipTiers.DEFAULT));
        assertEquals(Tier.FAMILIAR, RelationshipTiers.tierFor(89, RelationshipTiers.DEFAULT),
                "one point below the boundary is already the lower tier");
        assertEquals(Tier.CLOSE_FRIEND, RelationshipTiers.tierFor(90, RelationshipTiers.DEFAULT));
    }

    @Test
    @DisplayName("a boundary set whose stranger range is empty is invalid")
    void anEmptyStrangerRangeIsInvalid() {
        assertFalse(new Boundaries(0, 0, 60, 90, 100).isValid(),
                "friendlyLower == min leaves the stranger range empty");
        assertFalse(new Boundaries(0, 30, 30, 90, 100).isValid(), "equal boundaries are not contiguous");
        assertFalse(new Boundaries(0, 30, 60, 101, 100).isValid(), "the close-friend boundary must be <= max");
        assertTrue(new Boundaries(0, 30, 60, 100, 100).isValid(), "== max is allowed: a one-value top range");
    }

    @Test
    @DisplayName("migration example 1: the default threshold maps to itself")
    void migrationExampleDefault() {
        Boundaries migrated = RelationshipTiers.migrateFromLegacyThreshold(0, 100, 90);
        assertNotNull(migrated);
        assertEquals(new Boundaries(0, 30, 60, 90, 100), migrated);
    }

    @Test
    @DisplayName("migration example 2: a threshold BELOW the new default still works")
    void migrationExampleLowThreshold() {
        Boundaries migrated = RelationshipTiers.migrateFromLegacyThreshold(0, 100, 45);
        assertNotNull(migrated);
        assertEquals(new Boundaries(0, 15, 30, 45, 100), migrated);
    }

    @Test
    @DisplayName("migration example 3: a negative minimum, with the arithmetic corrected")
    void migrationExampleNegativeMinimum() {
        // T=45, min=-50 -> D=95; P = -50 + floor(95/3) = -50 + 31 = -19; M = -50 + floor(190/3) = -50 + 63 = 13
        Boundaries migrated = RelationshipTiers.migrateFromLegacyThreshold(-50, 120, 45);
        assertNotNull(migrated);
        assertEquals(new Boundaries(-50, -19, 13, 45, 120), migrated,
                "the earlier document example (-35/-20/45) was an arithmetic error");
        assertEquals(Tier.STRANGER, RelationshipTiers.tierFor(-20, migrated),
                "-20 is the last stranger value: the stranger range is [-50, -19)");
        assertEquals(Tier.FRIENDLY, RelationshipTiers.tierFor(-19, migrated),
                "-19 is the FIRST friendly value, since friendlyLower itself belongs to friendly");
        assertEquals(Tier.FRIENDLY, RelationshipTiers.tierFor(12, migrated));
        assertEquals(Tier.FAMILIAR, RelationshipTiers.tierFor(13, migrated));
        assertEquals(Tier.CLOSE_FRIEND, RelationshipTiers.tierFor(45, migrated));
    }

    @Test
    @DisplayName("migration example 4: too little room below the threshold means NO migration, never a repair")
    void migrationFailsWhenThereIsNoRoomForThreeRanges() {
        assertNull(RelationshipTiers.migrateFromLegacyThreshold(0, 100, 2),
                "D=2 cannot hold three non-empty ranges below the close-friend boundary");
        assertNotNull(RelationshipTiers.migrateFromLegacyThreshold(0, 100, 3), "D=3 is the smallest workable case");
        assertEquals(new Boundaries(0, 1, 2, 3, 100), RelationshipTiers.migrateFromLegacyThreshold(0, 100, 3));
        assertNull(RelationshipTiers.migrateFromLegacyThreshold(0, 100, 101), "above max is impossible");
        assertNull(RelationshipTiers.migrateFromLegacyThreshold(0, 100, 0), "== min leaves no room");
    }

    @Test
    @DisplayName("illegal boundaries are refused rather than interpreted as some tier")
    void illegalBoundariesCannotReachTierComputation() {
        Boundaries broken = new Boundaries(0, 0, 60, 90, 100);
        assertFalse(broken.isValid());
        assertThrows(IllegalArgumentException.class, () -> RelationshipTiers.tierFor(50, broken),
                "an invalid set must fail loudly: reading it as a tier would be a silent wrong answer");
        assertDoesNotThrow(() -> RelationshipTiers.tierFor(50, RelationshipTiers.DEFAULT));
    }

    @Test
    @DisplayName("the subtraction is wide: extremes that would overflow int still migrate correctly")
    void migrationSurvivesIntegerExtremes() {
        // min = -2e9, T = 2e9: D = 4e9, which does NOT fit in an int. Computing (T - min) as int first and
        // widening afterwards would overflow and produce a nonsense boundary.
        Boundaries migrated = RelationshipTiers.migrateFromLegacyThreshold(-2_000_000_000, 2_000_000_000,
                2_000_000_000);
        assertNotNull(migrated, "the wide subtraction must make this workable");
        assertEquals(2_000_000_000, migrated.closeFriendLower(), "F == T");
        assertEquals(-2_000_000_000, migrated.min());
        assertTrue(migrated.isValid());
        // P = min + floor(D/3), M = min + floor(2D/3) with D = 4_000_000_000 (computed in long, then narrowed)
        assertEquals((int) (-2_000_000_000L + 1_333_333_333L), migrated.friendlyLower());
        assertEquals((int) (-2_000_000_000L + 2_666_666_666L), migrated.familiarLower());

        // Integer.MIN_VALUE as the floor: still three values of room above it.
        Boundaries smallest = RelationshipTiers.migrateFromLegacyThreshold(Integer.MIN_VALUE,
                Integer.MAX_VALUE, Integer.MIN_VALUE + 3);
        assertNotNull(smallest);
        assertEquals(Integer.MIN_VALUE + 1, smallest.friendlyLower());
        assertEquals(Integer.MIN_VALUE + 2, smallest.familiarLower());
        assertEquals(Integer.MIN_VALUE + 3, smallest.closeFriendLower());
        assertEquals(Tier.CLOSE_FRIEND, RelationshipTiers.tierFor(Integer.MAX_VALUE, smallest));
    }

    @Test
    @DisplayName("the legacy threshold is never moved, whatever the other numbers are")
    void theLegacyThresholdIsNeverAdjusted() {
        Boundaries migrated = RelationshipTiers.migrateFromLegacyThreshold(-10, 55, 50);
        assertNotNull(migrated);
        assertEquals(50, migrated.closeFriendLower(), "F == T, always");
        assertEquals(-10, migrated.min());
        assertEquals(55, migrated.max());
        assertTrue(migrated.isValid());
    }
}
