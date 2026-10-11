package com.nonid.affinity.service.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nonid.affinity.service.policy.EngagementRules.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the "evidence expired, so the fight stopped" defect.
 *
 * <p>The first test is the one that would have caught it: a long, healthy engagement must continue
 * regardless of how much time has passed. The rest pin down the exit conditions that must still
 * apply, so the fix cannot be "never stop fighting".
 */
class EngagementRulesTest {

    /** A healthy engagement: alive, same world, unprotected, owner present, in range, progressing. */
    private static Outcome healthy() {
        return EngagementRules.evaluate(true, true, false, true, false, false, false, true, 0L, 200L);
    }

    @Test
    @DisplayName("a long engagement continues: elapsed time is not an exit condition")
    void elapsedTimeDoesNotEndAFight() {
        assertTrue(EngagementRules.keeps(healthy()),
                "an established fight must survive the expiry of the evidence that started it");
    }

    @Test
    @DisplayName("the same engagement continues at any amount of elapsed time")
    void manyRoundsStillContinue() {
        // The defect showed up after roughly 100 ticks (5 seconds). Nothing in the signature carries
        // elapsed time, which is the point: time alone can never end an engagement.
        for (int round = 0; round < 1000; round++) {
            assertTrue(EngagementRules.keeps(healthy()), "round " + round);
        }
    }

    @Test
    @DisplayName("a dead target ends the engagement")
    void deadTargetEnds() {
        assertEquals(Outcome.TARGET_GONE,
                EngagementRules.evaluate(false, true, false, true, false, false, false, true, 0L, 200L));
    }

    @Test
    @DisplayName("a target that turned friendly ends the engagement")
    void protectedTargetEnds() {
        assertEquals(Outcome.TARGET_PROTECTED,
                EngagementRules.evaluate(true, true, true, true, false, false, false, true, 0L, 200L));
    }

    @Test
    @DisplayName("switching to WAIT ends the chase immediately")
    void waitEndsTheChase() {
        assertEquals(Outcome.MODE_REJECTED,
                EngagementRules.evaluate(true, true, false, true, true, false, false, true, 0L, 200L));
    }

    @Test
    @DisplayName("recall distance outranks continuing the chase")
    void recallDistanceEndsTheChase() {
        assertEquals(Outcome.OUT_OF_RANGE,
                EngagementRules.evaluate(true, true, false, true, false, false, true, true, 0L, 200L),
                "beyond the recall distance the companion returns");
    }

    @Test
    @DisplayName("a target beyond the pursuit limit ends the engagement")
    void beyondLeashEnds() {
        assertEquals(Outcome.OUT_OF_RANGE,
                EngagementRules.evaluate(true, true, false, true, false, false, false, false, 0L, 200L));
    }

    @Test
    @DisplayName("assistance stops when the owner is gone, but self-defence does not")
    void ownerUnavailableIsSpecificToAssistance() {
        assertEquals(Outcome.OWNER_UNAVAILABLE,
                EngagementRules.evaluate(true, true, false, false, false, false, false, true, 0L, 200L));
        // Self-defence must still work with the owner away, or a WAIT companion could never fight back.
        assertEquals(Outcome.CONTINUE,
                EngagementRules.evaluate(true, true, false, false, false, true, false, true, 0L, 200L));
    }

    @Test
    @DisplayName("an unreachable target is given up after the stall limit")
    void noProgressEnds() {
        assertEquals(Outcome.NO_PROGRESS,
                EngagementRules.evaluate(true, true, false, true, false, false, false, true, 400L, 200L));
        assertEquals(Outcome.CONTINUE,
                EngagementRules.evaluate(true, true, false, true, false, false, false, true, 100L, 200L),
                "progress resets the stall clock, so a long fight is not punished");
    }

    @Test
    @DisplayName("a stall limit of zero disables the no-progress check")
    void zeroStallLimitIsIgnored() {
        assertEquals(Outcome.CONTINUE,
                EngagementRules.evaluate(true, true, false, true, false, false, false, true, 99999L, 0L));
    }

    @Test
    @DisplayName("a target in another dimension ends the engagement")
    void wrongWorldEnds() {
        assertEquals(Outcome.WRONG_WORLD,
                EngagementRules.evaluate(true, false, false, true, false, false, false, true, 0L, 200L));
    }

    @Test
    @DisplayName("a live engagement is re-asserted even when the physical target was cleared")
    void engagementIsReassertedWithoutAPhysicalTarget() {
        // This is the P1-a gap. The vanilla AI can clear the target mid-fight; the module still holds
        // its own engagement record and must put the target back rather than quietly give up.
        assertTrue(EngagementRules.shouldReassert(true, Outcome.CONTINUE),
                "a recorded, healthy engagement must be re-asserted");
    }

    @Test
    @DisplayName("no engagement record means nothing to re-assert")
    void noRecordMeansNoReassert() {
        assertFalse(EngagementRules.shouldReassert(false, Outcome.CONTINUE));
    }

    @Test
    @DisplayName("a finished engagement is not re-asserted")
    void finishedEngagementIsNotReasserted() {
        for (Outcome ended : new Outcome[]{Outcome.TARGET_GONE, Outcome.TARGET_PROTECTED,
                Outcome.WRONG_WORLD, Outcome.OWNER_UNAVAILABLE, Outcome.MODE_REJECTED,
                Outcome.OUT_OF_RANGE, Outcome.NO_PROGRESS}) {
            assertFalse(EngagementRules.shouldReassert(true, ended),
                    "must not hold on to an engagement that ended: " + ended);
        }
    }

    @Test
    @DisplayName("a cleared target is restored, but an external live target is not overwritten")
    void restoreVersusYield() {
        // Externally cleared: restoring our own target is the whole point.
        assertTrue(EngagementRules.shouldRestore(true, false),
                "a target the vanilla AI cleared must be restored");
        assertFalse(EngagementRules.shouldYield(true, false));

        // Another system holds a live target: leave it alone.
        assertTrue(EngagementRules.shouldYield(false, false),
                "an external live target must not be overwritten");
        assertFalse(EngagementRules.shouldRestore(false, false));

        // Already ours: neither action is needed.
        assertFalse(EngagementRules.shouldRestore(false, true));
        assertFalse(EngagementRules.shouldYield(false, true));
    }

    @Test
    @DisplayName("the recall threshold is strict: exactly the distance is not yet a recall")
    void recallThresholdIsStrict() {
        // Ten blocks exactly is not a recall; the rule is "more than ten".
        assertFalse(com.nonid.affinity.service.CompanionRecall.needed(10.0 * 10.0, 10.0));
        assertTrue(com.nonid.affinity.service.CompanionRecall.needed(10.0001 * 10.0001, 10.0));
    }

    @Test
    @DisplayName("the recall threshold follows its configured value, not a constant")
    void recallThresholdFollowsConfig() {
        // Guards the regression where the threshold was hardcoded while the config said otherwise.
        assertTrue(com.nonid.affinity.service.CompanionRecall.needed(5.0 * 5.0, 4.0));
        assertFalse(com.nonid.affinity.service.CompanionRecall.needed(5.0 * 5.0, 8.0));
    }

    @Test
    @DisplayName("exit reasons are distinguishable, so a release can be attributed")
    void outcomesAreDistinct() {
        assertFalse(EngagementRules.keeps(Outcome.TARGET_GONE));
        assertFalse(EngagementRules.keeps(Outcome.NO_PROGRESS));
        assertTrue(EngagementRules.keeps(Outcome.CONTINUE));
    }
}
