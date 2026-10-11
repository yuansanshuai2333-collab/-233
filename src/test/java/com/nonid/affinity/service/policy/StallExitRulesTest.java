package com.nonid.affinity.service.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The stall exit as a complete path, driven through the SAME methods production calls.
 *
 * <p>What this class does NOT claim: that the controller wires the exit up correctly. Selecting a
 * target, detecting a stall and applying the result are separate steps in
 * {@code CompanionCombatController.apply}, and a rule test cannot prove the controller calls them in
 * that order. The wiring itself is what {@code CompanionStallWiringTest} covers.
 *
 * <p>What it does prove is the part that used to be missing: that a stalled target is retained,
 * suppressed, and stays suppressed against the continuous evidence an unreachable enemy generates.
 */
class StallExitRulesTest {

    private static final UUID STALLED = UUID.fromString("cccccccc-0000-0000-0000-00000000000c");
    private static final UUID OTHER_THREAT = UUID.fromString("dddddddd-0000-0000-0000-00000000000d");
    private static final UUID RECALLED = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b");

    private static final double RECALL = 10.0;
    private static final double MARGIN = 1.0;
    private static final long SUPPRESS_TICKS = 200L;
    private static final long STALL_LIMIT = 200L;

    @Test
    @DisplayName("a stalled target is retained, then suppressed for the whole window")
    void stallExitRetainsAndSuppresses() {
        EngagementState state = new EngagementState();
        // The fight began at tick 0 from fact 500 and never progressed: no approach, no hit.
        state.begin(STALLED, 0L, 500L);
        assertTrue(state.isStalled(400L, STALL_LIMIT), "no progress, so the fight has stalled");

        // The complete exit: end the fight, then establish the suppression.
        state.end();
        assertTrue(state.suppressStalledTarget(state.lastAbandoned(), 400L + SUPPRESS_TICKS),
                "the exit must report that a suppression was established");

        assertTrue(state.suppressedByStall(), "the window must be attributed to the stall");
        assertEquals(Long.MAX_VALUE, state.suppressedSequence(),
                "a stall must not carry a watermark that a newer fact could beat");
    }

    @Test
    @DisplayName("inside the window, no fact however new re-opens the same target")
    void continuousEvidenceCannotUndoTheStallExit() {
        EngagementState state = new EngagementState();
        state.begin(STALLED, 0L, 500L);
        state.end();
        state.suppressStalledTarget(STALLED, 400L + SUPPRESS_TICKS);

        // The hostility scan republishes this enemy every second while it is in range, and the
        // companion is beside its owner the whole time. Neither may re-open the fight.
        long sequence = 500L;
        for (int round = 0; round < 10; round++) {
            sequence += 20L;
            long tick = 400L + round * 20L;
            assertFalse(state.canEngageUnderMode(STALLED, tick, sequence, true, 2.0, 3.0,
                            RECALL, MARGIN),
                    "round " + round + ": a fresh scan fact must not restart the stalled fight");
        }
    }

    @Test
    @DisplayName("the stall window is closed by the clock alone, holding the last tick and opening the next")
    void windowIsBoundedInBothDirections() {
        // TWO states, because the two questions below are about different failures and answering the
        // first must not consume the second.
        EngagementState checkedAtTheEdge = new EngagementState();
        checkedAtTheEdge.begin(STALLED, 0L, 500L);
        checkedAtTheEdge.end();
        checkedAtTheEdge.suppressStalledTarget(STALLED, 1000L);
        // The last tick the window covers.
        assertTrue(checkedAtTheEdge.isSuppressedAt(STALLED, 999L));
        assertFalse(checkedAtTheEdge.canEngageUnderMode(STALLED, 999L, 9000L, true, 2.0, 3.0,
                RECALL, MARGIN));

        EngagementState checkedAfterExpiry = new EngagementState();
        checkedAfterExpiry.begin(STALLED, 0L, 500L);
        checkedAfterExpiry.end();
        checkedAfterExpiry.suppressStalledTarget(STALLED, 1000L);
        // On the end tick the window is over. This is the case that caught a real defect: the stall
        // branch used to leave its Long.MAX_VALUE watermark behind after expiry, and reading that
        // watermark refused every later fact for this target, forever.
        assertTrue(checkedAfterExpiry.canEngageUnderMode(STALLED, 1000L, 9001L, true, 2.0, 3.0,
                        RECALL, MARGIN),
                "the window must open on the tick the exit recorded as its end");
        assertEquals(0L, checkedAfterExpiry.suppressedSequence(),
                "an expired window must not leave a watermark that keeps refusing facts");
        assertFalse(checkedAfterExpiry.suppressedByStall(),
                "an expired window must not keep claiming a stall");
    }

    @Test
    @DisplayName("a stalled target does not block a different threat, even one with an older fact")
    void otherThreatsRemainAnswerable() {
        EngagementState state = new EngagementState();
        state.begin(STALLED, 0L, 500L);
        state.end();
        state.suppressStalledTarget(STALLED, 400L + SUPPRESS_TICKS);

        // A second enemy attacks the companion from the other side. Its fact is OLDER than some of the
        // scan facts above; the suppression is scoped to the stalled id and must not touch it.
        assertTrue(state.canEngageUnderMode(OTHER_THREAT, 410L, 100L, true, 2.0, 3.0, RECALL, MARGIN),
                "the suppression owns one target, not the companion's whole target selection");
    }

    @Test
    @DisplayName("a stall window refuses facts outright, where a recall window is bounded by its watermark")
    void stallAndRecallWindowsAreDifferentRules() {
        // A recall answer is a fresh DECISION to go back, so it records the event watermark it was
        // answering and facts older than that cannot undo it -- while newer facts still can. A stall
        // carries no decision at all, so the window itself is the whole rule: no fact, however new,
        // re-opens the target inside it.

        EngagementState stalled = new EngagementState();
        stalled.begin(STALLED, 0L, 500L);
        stalled.end();
        stalled.suppressStalledTarget(STALLED, 600L);
        assertTrue(stalled.suppressedByStall(), "this window came from a stall");
        assertEquals(Long.MAX_VALUE, stalled.suppressedSequence(),
                "a stall records no watermark, so no fact can ever be newer than its release");

        EngagementState recalled = new EngagementState();
        recalled.begin(RECALLED, 0L, 500L);
        recalled.end();
        recalled.applyRecallResult(true, 400L, SUPPRESS_TICKS, 1L);
        assertFalse(recalled.suppressedByStall(), "this window came from a recall");
        assertEquals(500L, recalled.suppressedSequence(),
                "a recall records the watermark it answered");

        // Inside its window the stalled target is held against the newest possible fact. This is the
        // whole difference from a recall: there is no watermark a fact could be newer than.
        assertFalse(stalled.canEngageUnderMode(STALLED, 599L, Long.MAX_VALUE - 1L, true, 2.0, 3.0,
                        RECALL, MARGIN),
                "a stall is not a decision, so no fact can supersede it");
        // The recall window is half-open exactly like the stall window: the target is held while the
        // window is still active, whatever the fact's age.
        assertFalse(recalled.canEngageUnderMode(RECALLED, 599L, 501L, true, 2.0, 3.0, RECALL, MARGIN),
                "the recall window is active until its end tick");
        // On that end tick both windows close together, and both targets become available again.
        assertTrue(stalled.canEngageUnderMode(STALLED, 600L, 9001L, true, 2.0, 3.0, RECALL, MARGIN));
        assertTrue(recalled.canEngageUnderMode(RECALLED, 600L, 9001L, true, 2.0, 3.0, RECALL, MARGIN));
    }

    @Test
    @DisplayName("suppressing a null target is refused rather than creating a window on nothing")
    void suppressingNothingIsRefused() {
        EngagementState state = new EngagementState();
        assertFalse(state.suppressStalledTarget(null, 100L));
        assertFalse(state.suppressedByStall());

        // And nothing else got banned as a side effect.
        assertTrue(state.canEngageUnderMode(OTHER_THREAT, 50L, 900L, true, 2.0, 2.0, RECALL, MARGIN));
    }
}
