package com.nonid.affinity.service.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Progress tracking for an ongoing fight.
 *
 * <p>The case that motivated this: a companion standing still and hitting its target was judged
 * stalled, because only closing distance counted as progress. These tests pin down both halves --
 * a real hit must renew the fight, and everything that is NOT a real hit must not.
 */
class EngagementStateTest {

    private static final UUID TARGET = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Test
    @DisplayName("standing still and hitting keeps the fight alive past the stall limit")
    void repeatedHitsKeepTheFightAlive() {
        EngagementState state = new EngagementState();
        state.begin(TARGET, 0L);

        // No approach at all: the companion never closes distance. Only hits renew progress.
        long tick = 0L;
        for (int round = 0; round < 20; round++) {
            tick += 100L;                       // each round is longer than the 200-tick limit
            assertTrue(state.recordHit(TARGET, tick), "a real hit on the target must count");
            // Still fine, because the hit was recorded inside the limit each time.
            assertFalse(state.isStalled(tick, 200L),
                    "a companion landing hits must not be judged stalled at round " + round);
        }
    }

    @Test
    @DisplayName("without hits or approach the fight stalls")
    void noHitsStalls() {
        EngagementState state = new EngagementState();
        state.begin(TARGET, 0L);
        assertTrue(state.isStalled(300L, 200L));
        assertFalse(state.isStalled(150L, 200L));
    }

    @Test
    @DisplayName("a hit on a different creature does not renew the fight")
    void hitOnAnotherTargetDoesNotCount() {
        EngagementState state = new EngagementState();
        state.begin(TARGET, 0L);
        assertFalse(state.recordHit(OTHER, 100L), "damage to something else must not count");
        assertTrue(state.isStalled(300L, 200L), "the fight is still stalled");
    }

    @Test
    @DisplayName("a hit while not engaged does nothing")
    void hitWithoutEngagementDoesNothing() {
        EngagementState state = new EngagementState();
        assertFalse(state.recordHit(TARGET, 100L));
        assertFalse(state.isStalled(1000L, 200L), "there is no fight to stall");
    }

    @Test
    @DisplayName("closing distance also renews progress")
    void approachCounts() {
        EngagementState state = new EngagementState();
        state.begin(TARGET, 0L);
        assertTrue(state.recordApproach(150L));
        assertFalse(state.isStalled(300L, 200L));
    }

    @Test
    @DisplayName("a late hit from a previous fight does not renew the new one")
    void lateHitFromPreviousFightIsRejected() {
        EngagementState state = new EngagementState();
        state.begin(TARGET, 0L);
        long oldGeneration = state.generation();

        // The fight was abandoned and a new one started (recall, mode change, retarget).
        state.end();
        state.begin(OTHER, 10L);

        // A hit resolved late for the OLD target arrives now.
        assertFalse(state.recordHit(TARGET, 20L),
                "a stale hit for the previous target must not renew the current fight");
        assertFalse(state.ownsGeneration(oldGeneration),
                "the previous generation is no longer current");
        assertTrue(state.isStalled(1000L, 200L));
    }

    @Test
    @DisplayName("ending a fight starts a new generation, so ownership can be checked")
    void generationAdvancesOnEveryTransition() {
        EngagementState state = new EngagementState();
        long initial = state.generation();
        state.begin(TARGET, 0L);
        long afterBegin = state.generation();
        assertTrue(afterBegin > initial);

        state.end();
        long afterEnd = state.generation();
        assertTrue(afterEnd > afterBegin);
        assertFalse(state.ownsGeneration(afterBegin));
    }

    @Test
    @DisplayName("a zero stall limit disables the check")
    void zeroLimitDisablesStalling() {
        EngagementState state = new EngagementState();
        state.begin(TARGET, 0L);
        assertFalse(state.isStalled(999999L, 0L));
    }

    @Test
    @DisplayName("retargeting replaces the target and resets progress")
    void retargetReplacesTarget() {
        EngagementState state = new EngagementState();
        state.begin(TARGET, 0L);
        state.recordHit(TARGET, 150L);
        state.begin(OTHER, 400L);
        assertEquals(OTHER, state.targetId());
        // The old target can no longer renew progress.
        assertFalse(state.recordHit(TARGET, 450L));
        assertFalse(state.isStalled(500L, 200L), "progress restarted with the new target");
    }
}
