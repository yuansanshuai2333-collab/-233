package com.nonid.affinity.service.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P1-b corrections, driven through the SAME methods production calls.
 *
 * <p>Nothing here fills in state by hand to manufacture a pass: each test performs the transitions in
 * the order the game performs them, including the ordering trap the review identified, where the
 * controller ends an out-of-range fight BEFORE the follow service attempts the recall.
 */
class RecallResultRulesTest {

    private static final UUID A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID TARGET = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final double RECALL = 10.0;

    @Test
    @DisplayName("a recall arriving AFTER the fight was ended still suppresses the abandoned target")
    void recallAfterFightEndedStillSuppresses() {
        EngagementState state = new EngagementState();

        // --- the actual game order ---
        // 1. The controller notices the companion is beyond the recall distance and ends the fight.
        state.begin(TARGET, 0L, 500L);
        state.end();
        // The live target is now gone; a naive handler reading it would suppress nothing.
        assertNull(state.targetId());

        // 2. The follow service then performs the recall successfully.
        assertTrue(state.applyRecallResult(true, 100L, 200L, 60L));

        // 3. The abandoned target must still be off limits.
        assertFalse(state.canEngage(TARGET, 150L, 900L, 5.0, RECALL),
                "the target given up before the recall must still be suppressed");
        // ...and it really is the one retained from end(), not an empty suppression.
        assertTrue(state.lastAbandoned() != null);
    }

    @Test
    @DisplayName("after a successful recall, a nearby threat is answered without waiting out a timer")
    void nearbyThreatAnsweredWhenAlreadyBack() {
        EngagementState state = new EngagementState();
        state.begin(TARGET, 0L, 500L);
        state.end();
        state.applyRecallResult(true, 100L, 200L, 600L);   // long return window

        // The companion is already back within range: the reason for returning is gone, so a nearby
        // NEW threat must be answerable immediately rather than blocked for the whole window.
        assertTrue(state.canEngage(A, 150L, 900L, 4.0, RECALL),
                "a new threat beside an already-returned companion must be answered");
    }

    @Test
    @DisplayName("a suppression window expiring never restarts an out-of-range chase")
    void expiryDoesNotRestartDistantChase() {
        EngagementState state = new EngagementState();
        state.begin(TARGET, 0L, 500L);
        state.end();
        state.applyRecallResult(true, 100L, 50L, 50L);      // both windows close at tick 150

        // Windows have expired, but the companion is still far from its owner.
        assertFalse(state.canEngage(TARGET, 500L, 900L, 30.0, RECALL),
                "range applies on its own, independent of the suppression window");
        // And for a different target too: it is the RANGE that blocks, not the suppression.
        assertFalse(state.canEngage(B, 500L, 900L, 30.0, RECALL));
        // Once genuinely back in range, the old target is available again (not banned forever).
        assertTrue(state.canEngage(TARGET, 500L, 900L, 5.0, RECALL));
    }

    @Test
    @DisplayName("the event watermark is actually read: older facts cannot undo a release")
    void staleFactsCannotUndoTheRelease() {
        EngagementState state = new EngagementState();
        state.begin(TARGET, 0L, 500L);
        state.end();
        state.applyRecallResult(true, 100L, 400L, 400L);

        // A fact from BEFORE the release (sequence <= watermark) must not re-open the target.
        assertFalse(state.canEngage(TARGET, 120L, 400L, 5.0, RECALL),
                "a fact at or below the watermark is from before the release");
        assertFalse(state.canEngage(TARGET, 120L, 100L, 5.0, RECALL));
    }

    @Test
    @DisplayName("a failed recall performs no success transition at all")
    void failedRecallChangesNothing() {
        EngagementState state = new EngagementState();
        state.begin(TARGET, 0L, 500L);
        state.end();

        assertFalse(state.applyRecallResult(false, 100L, 200L, 60L),
                "a failed recall must report that the success path did not run");

        // Nothing suppressed, nothing returning: engagement still works normally.
        assertTrue(state.canEngage(TARGET, 110L, 900L, 5.0, RECALL),
                "a recall that did not happen must not suppress anything");
    }

    @Test
    @DisplayName("one companion's recall leaves the other completely unaffected")
    void recallOfOneDoesNotDisturbTheOther() {
        EngagementState a = new EngagementState();
        EngagementState b = new EngagementState();

        a.begin(TARGET, 0L, 500L);
        a.end();
        a.applyRecallResult(true, 100L, 200L, 600L);

        // B was never recalled and holds the same target.
        b.begin(TARGET, 0L, 500L);
        assertTrue(b.canEngage(TARGET, 150L, 900L, 5.0, RECALL),
                "B must still answer threats after A was recalled");
        assertFalse(a.canEngage(TARGET, 150L, 900L, 5.0, RECALL),
                "A remains suppressed");
    }

    @Test
    @DisplayName("continuous scanning cannot restart an out-of-range chase")
    void continuousScanCannotRestartDistantChase() {
        EngagementState state = new EngagementState();
        state.begin(TARGET, 0L, 500L);
        state.end();
        state.applyRecallResult(true, 100L, 30L, 30L);

        // A scan keeps producing newer and newer facts.
        long sequence = 900L;
        for (int round = 0; round < 10; round++) {
            sequence += 10L;
            assertFalse(state.canEngage(TARGET, 200L + round, sequence, 40.0, RECALL),
                    "round " + round + ": a fresh fact must not restart a distant chase");
        }
    }
}
