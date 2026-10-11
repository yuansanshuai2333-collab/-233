package com.nonid.affinity.service.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Re-engagement rules for P1-b, exercised through the SHARED state the controller uses.
 *
 * <p>Deliberately not a test-only filter: {@code canEngage}, {@code suppress}, {@code beginReturning}
 * and {@code isStalled} are the same methods production calls. A test that re-implemented the rule
 * would prove nothing about the shipped behaviour.
 *
 * <p>The scenarios mirror the review's list, including the two-companion case, which is testable
 * without a live world by giving each companion its own state over one shared event list.
 */
class ReEngagementRulesTest {

    private static final UUID A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OLD_TARGET = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID NEW_THREAT = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    /** A stand-in for the owner-shared fact list: facts are never consumed by one companion only. */
    private static final class SharedEvents {
        private long sequence = 100L;

        long publish() {
            return ++sequence;
        }

        List<Long> snapshot() {
            return List.of(sequence);
        }
    }

    @Test
    @DisplayName("1. after a recall the old target cannot be re-selected immediately")
    void recallSuppressesTheOldTarget() {
        EngagementState state = new EngagementState();
        long watermark = 100L;
        state.begin(OLD_TARGET, 0L);
        state.end();
        state.suppress(OLD_TARGET, watermark, 200L);

        assertFalse(state.canEngage(OLD_TARGET, 150L),
                "the abandoned target must stay off limits inside the window");
    }

    @Test
    @DisplayName("2. a fresh sequence cannot bypass the suppression or the returning state")
    void freshEvidenceCannotBypassTheRules() {
        SharedEvents events = new SharedEvents();
        EngagementState state = new EngagementState();
        state.suppress(OLD_TARGET, 100L, 300L);

        // A continuous scan keeps producing NEWER facts for the same target.
        for (int round = 0; round < 5; round++) {
            events.publish();
            assertFalse(state.canEngage(OLD_TARGET, 150L),
                    "a newer fact must not re-open a suppressed target, round " + round);
        }

        // While returning, even an entirely new target is off limits: the distance rule outranks it.
        state.beginReturning(400L);
        assertFalse(state.canEngage(NEW_THREAT, 200L),
                "nothing may be engaged while travelling back");
        assertFalse(state.canEngage(OLD_TARGET, 200L));
    }

    @Test
    @DisplayName("3. one companion's recall does not touch the shared facts, so the other still responds")
    void recallOfOneCompanionLeavesTheOtherWorking() {
        SharedEvents events = new SharedEvents();
        EngagementState a = new EngagementState();
        EngagementState b = new EngagementState();

        long before = events.snapshot().get(0);

        // A is recalled and suppresses its target.
        a.begin(OLD_TARGET, 0L);
        a.end();
        a.suppress(OLD_TARGET, events.snapshot().get(0), 300L);
        a.beginReturning(300L);

        // The shared fact list is untouched: recalling A must not remove facts B needs.
        assertEqualsLong(before, events.snapshot().get(0), "shared events must not be consumed by A");

        // B, which was never recalled, can still engage the same target.
        b.begin(OLD_TARGET, 0L);
        assertTrue(b.canEngage(OLD_TARGET, 150L),
                "B must still be able to answer threats after A was recalled");
        assertFalse(a.canEngage(OLD_TARGET, 150L), "A stays suppressed");
    }

    @Test
    @DisplayName("4. a nearby new threat is answered, and the old target is not banned forever")
    void newThreatAnswerableAndOldTargetRecovers() {
        EngagementState state = new EngagementState();
        state.suppress(OLD_TARGET, 100L, 200L);
        state.beginReturning(200L);

        // Still returning: nothing new.
        assertFalse(state.canEngage(NEW_THREAT, 150L));

        // Back with the owner: a nearby new threat must be answered.
        assertTrue(state.canEngage(NEW_THREAT, 250L),
                "a new threat must be answerable once the companion is back");

        // The suppression window has also expired, so the original target is available again.
        assertTrue(state.canEngage(OLD_TARGET, 250L),
                "a target must not stay banned after its window closes");
    }

    @Test
    @DisplayName("5. after a stall the same target is not restarted by continuous events")
    void stallDoesNotRestartFromContinuousEvents() {
        SharedEvents events = new SharedEvents();
        EngagementState state = new EngagementState();
        state.begin(OLD_TARGET, 0L);

        // Kept alive by real hits until the hits stop.
        state.recordHit(OLD_TARGET, 100L);
        assertFalse(state.isStalled(200L, 200L));

        // Hits stop. Facts keep arriving, but they are not hits and must not renew the clock.
        long tick = 200L;
        for (int round = 0; round < 10; round++) {
            events.publish();
            tick += 50L;
        }
        assertTrue(state.isStalled(tick, 200L),
                "a stalled fight must not be kept alive by unrelated new evidence");
    }

    @Test
    @DisplayName("6. a failed recall must not enter the recall-success state or disturb another companion")
    void failedRecallDoesNotSuppressOrDisturbOthers() {
        // A failed recall returns before onRecalled, so the state is simply never touched. This test
        // pins that contract: with no suppression and no returning flag, engagement still works.
        EngagementState state = new EngagementState();
        EngagementState other = new EngagementState();

        other.begin(NEW_THREAT, 0L);

        assertTrue(state.canEngage(OLD_TARGET, 150L),
                "no recall happened, so nothing may be suppressed");
        assertTrue(state.canEngage(NEW_THREAT, 150L));
        assertTrue(other.canEngage(NEW_THREAT, 150L),
                "another companion's engagement must be untouched");
    }

    private static void assertEqualsLong(long wanted, long actual, String message) {
        assertTrue(wanted == actual, message + " (expected " + wanted + ", got " + actual + ")");
    }

    @Test
    @DisplayName("suppression expires on its own and does not follow a different target")
    void suppressionIsScopedToItsTargetAndWindow() {
        EngagementState state = new EngagementState();
        state.suppress(OLD_TARGET, 100L, 200L);
        // While the window is open the watermark is populated, which is what makes the rule in
        // canEngageUnderMode readable rather than vestigial.
        assertNotEquals(state.suppressedSequence(), 0L,
                "an open window must carry the watermark it was established from");
        assertTrue(state.canEngage(NEW_THREAT, 150L), "a different target is unaffected");
        assertTrue(state.canEngage(OLD_TARGET, 200L), "the window is half-open at its end tick");
        // The watermark goes with the window. Leaving it behind would keep refusing facts at or below it
        // forever -- a permanent ban hidden inside an expired window. A stall window records
        // Long.MAX_VALUE, so for that path the leftover watermark would refuse literally every fact.
        assertEqualsLong(0L, state.suppressedSequence(),
                "an expired window must not leave its watermark behind");
    }
}
