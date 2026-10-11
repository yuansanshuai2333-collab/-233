package com.nonid.affinity.service.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The recall CALL CHAIN, through a test double instead of a live world.
 *
 * <p>A rule test can show that {@code applyRecallResult} does the right thing when called. It cannot
 * show that production calls it at all, exactly once, and never on the failure branch. Those properties
 * live here.
 *
 * <p>The double reports an OUTCOME rather than a boolean, which is what lets these tests pin the case
 * the previous design got wrong: the returning window must be decided from the companion's distance
 * AFTER the attempt, never from the pre-attempt sample. A boolean port could not express the difference,
 * and a double that always answered "recalled" hid it.
 */
class RecallFlowTest {

    private static final UUID COMPANION = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID OWNER = UUID.fromString("eeeeeeee-0000-0000-0000-00000000000e");
    private static final UUID ABANDONED = UUID.fromString("cccccccc-0000-0000-0000-00000000000c");
    private static final UUID NEARBY_THREAT = UUID.fromString("dddddddd-0000-0000-0000-00000000000d");

    private static final double RECALL = 10.0;
    private static final double MARGIN = 1.0;
    private static final long SUPPRESS_TICKS = 200L;
    private static final long RETURN_TICKS = 600L;

    /** Records every invocation and answers with a scripted outcome. */
    private static final class RecordingPort implements RecallFlow.RecallPort {
        final List<RecallFlow.RecallContext> calls = new ArrayList<>();
        private final RecallFlow.RecallOutcome answer;

        RecordingPort(RecallFlow.RecallOutcome answer) {
            this.answer = answer;
        }

        @Override
        public RecallFlow.RecallOutcome attempt(RecallFlow.RecallContext context) {
            this.calls.add(context);
            return this.answer;
        }
    }

    private static RecallFlow.RecallContext context(double distanceBefore, long now) {
        return new RecallFlow.RecallContext(COMPANION, OWNER, true, distanceBefore, true, RECALL, now,
                SUPPRESS_TICKS, RETURN_TICKS);
    }

    @Test
    @DisplayName("a successful recall runs the port exactly once and suppresses the abandoned target")
    void successRunsThePortOnceAndSuppresses() {
        EngagementState state = new EngagementState();
        state.begin(ABANDONED, 0L, 500L);
        // The controller ends the out-of-range fight BEFORE the recall is attempted: this ordering is
        // the trap that made an earlier version suppress nothing at all.
        state.end();

        // The companion landed far from its owner, so the returning window applies.
        RecordingPort port = new RecordingPort(RecallFlow.RecallOutcome.recalledAt(30.0));
        assertTrue(RecallFlow.attempt(state, context(30.0, 400L), port).recalled());

        assertEquals(1, port.calls.size(), "the teleport must be attempted exactly once");
        assertEquals(COMPANION, port.calls.get(0).companionId());
        assertEquals(500L, state.suppressedSequence(),
                "the recall must record the abandoned target's own watermark");
        // The abandoned target is off limits, including for a fact newer than the release.
        assertFalse(state.canEngageUnderMode(ABANDONED, 410L, 900L, true, 2.0, 3.0, RECALL, MARGIN));
    }

    @Test
    @DisplayName("the returning window is decided from the distance AFTER the attempt")
    void returningWindowUsesThePostRecallDistance() {
        // A recall can land the companion back beside its owner (it searches a 2-3 block ring around the
        // owner). In that case the reason for returning is gone, and a nearby real threat must be
        // answered at once instead of being blocked for the whole configured window.
        //
        // The reported landing distance and the distance used at the engagement check are the SAME fact
        // here, as they are in the game: the companion is where the recall put it.
        EngagementState alreadyBack = new EngagementState();
        alreadyBack.begin(ABANDONED, 0L, 500L);
        alreadyBack.end();
        RecallFlow.attempt(alreadyBack, context(30.0, 400L),
                new RecordingPort(RecallFlow.RecallOutcome.recalledAt(2.0)));
        assertTrue(alreadyBack.canEngageUnderMode(NEARBY_THREAT, 401L, 900L, true, 2.0, 3.0,
                        RECALL, MARGIN),
                "a recall that landed the companion home must not block a real nearby threat");

        // A recall that landed the companion FAR away keeps the window open for a new target, because
        // the companion is genuinely not back yet. Same code path, different reported landing point.
        EngagementState stillOut = new EngagementState();
        stillOut.begin(ABANDONED, 0L, 500L);
        stillOut.end();
        RecallFlow.attempt(stillOut, context(30.0, 400L),
                new RecordingPort(RecallFlow.RecallOutcome.recalledAt(25.0)));
        assertFalse(stillOut.canEngageUnderMode(NEARBY_THREAT, 401L, 900L, true, 25.0, 26.0,
                        RECALL, MARGIN),
                "a recall that left the companion out of range must keep it returning");
    }

    @Test
    @DisplayName("the pre-attempt distance can never satisfy the eligibility rule, so it cannot decide the window")
    void preAttemptDistanceIsNeverInsideTheRecallRadius() {
        // This is the defect the outcome-shaped port exists to prevent, stated as a property: for any
        // distance that makes a recall due, that same distance is outside the recall radius. A decision
        // that read it could therefore only ever conclude "still out", and the "already back" branch was
        // unreachable in production.
        for (double distance : new double[]{10.0001, 12.0, 30.0, 64.0}) {
            assertTrue(RecallFlow.recallDue(true, true, distance, RECALL),
                    "distance " + distance + " is beyond the recall radius");
            assertFalse(distance <= RECALL,
                    "the distance that triggered the recall is never inside the radius");
        }
        // Exactly at the radius is not a recall at all, so it never reaches the window decision.
        assertFalse(RecallFlow.recallDue(true, true, RECALL, RECALL));
    }

    @Test
    @DisplayName("an unmeasurable post-recall distance is treated as still out, not as already home")
    void unmeasurableDistanceKeepsTheReturningWindow() {
        EngagementState state = new EngagementState();
        state.begin(ABANDONED, 0L, 500L);
        state.end();
        // recalled with no distance reported. Opening no window would let the companion charge straight
        // back out, which is the loop the window exists to break, so the safe reading is "not back".
        RecallFlow.attempt(state, context(30.0, 400L),
                new RecordingPort(RecallFlow.RecallOutcome.recalledAt(Double.NaN)));
        assertFalse(state.canEngageUnderMode(NEARBY_THREAT, 401L, 900L, true, 25.0, 26.0,
                        RECALL, MARGIN),
                "an unmeasurable result must not be read as 'already back'");
    }

    @Test
    @DisplayName("a failed recall calls the port but runs no success transition at all")
    void failureRunsNothingButTheAttempt() {
        EngagementState state = new EngagementState();
        state.begin(ABANDONED, 0L, 500L);
        state.end();

        RecordingPort port = new RecordingPort(RecallFlow.RecallOutcome.notRecalled());
        assertFalse(RecallFlow.attempt(state, context(30.0, 400L), port).recalled());

        assertEquals(1, port.calls.size(), "the attempt still happens; only its consequences are skipped");
        // Nothing was suppressed and nothing is returning: the target is available immediately.
        assertTrue(state.canEngageUnderMode(ABANDONED, 401L, 900L, true, 2.0, 3.0, RECALL, MARGIN),
                "a recall that did not happen must not ban anything");
        assertEquals(0L, state.suppressedSequence(),
                "a failure must not leave a watermark behind, real or fabricated");
    }

    @Test
    @DisplayName("no recall is attempted while the companion is inside the recall distance")
    void insideTheDistanceNothingIsAttempted() {
        EngagementState state = new EngagementState();
        state.begin(ABANDONED, 0L, 500L);

        RecordingPort port = new RecordingPort(RecallFlow.RecallOutcome.recalledAt(2.0));
        // Exactly at the distance is deliberately NOT enough: the user's rule is "beyond ten blocks".
        assertFalse(RecallFlow.attempt(state, context(RECALL, 400L), port).recalled());
        assertFalse(RecallFlow.attempt(state, context(9.9, 401L), port).recalled());
        assertEquals(0, port.calls.size(), "the port must not be called when no recall is due");
        // The fight is untouched: a companion beside its owner keeps fighting.
        assertTrue(state.isEngaged());
    }

    @Test
    @DisplayName("an unusable owner or another world never triggers a recall")
    void unusableOwnerNeverRecalls() {
        RecordingPort port = new RecordingPort(RecallFlow.RecallOutcome.recalledAt(2.0));

        // Offline/dead/spectating owner: ownerUsable false.
        assertFalse(RecallFlow.attempt(new EngagementState(),
                new RecallFlow.RecallContext(COMPANION, OWNER, false, 40.0, true, RECALL, 400L,
                        SUPPRESS_TICKS, RETURN_TICKS),
                port).recalled());
        // No owner resolved at all. The context refuses to claim a usable owner alongside a null id, so
        // this is the only shape the "nobody to return to" case can take.
        assertFalse(RecallFlow.attempt(new EngagementState(),
                new RecallFlow.RecallContext(COMPANION, null, false, 40.0, true, RECALL, 400L,
                        SUPPRESS_TICKS, RETURN_TICKS),
                port).recalled());
        // Owner in another dimension.
        assertFalse(RecallFlow.attempt(new EngagementState(),
                new RecallFlow.RecallContext(COMPANION, OWNER, true, 40.0, false, RECALL, 400L,
                        SUPPRESS_TICKS, RETURN_TICKS),
                port).recalled());

        assertEquals(0, port.calls.size(),
                "the recall stage must not teleport across worlds or toward an unusable owner");
    }

    @Test
    @DisplayName("a context claiming a usable owner with no owner id is refused at construction")
    void contradictoryOwnerContextIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new RecallFlow.RecallContext(COMPANION, null, true, 40.0, true, RECALL, 400L,
                        SUPPRESS_TICKS, RETURN_TICKS),
                "the two owner fields must never contradict each other");
    }

    @Test
    @DisplayName("an owner id that is dead or spectating does not make a recall due")
    void unusableOwnerWithAnIdIsStillRefused() {
        // This case exposed a real defect: the record form of the eligibility rule read only the owner ID,
        // so a resolved-but-unusable owner (dead, spectating) was treated as a usable one and the
        // teleport was attempted. The four-argument form and the record form must agree, and this is the
        // input that told them apart.
        RecallFlow.RecallContext deadOwner = new RecallFlow.RecallContext(COMPANION, OWNER, false, 40.0,
                true, RECALL, 400L, SUPPRESS_TICKS, RETURN_TICKS);
        assertFalse(RecallFlow.recallDue(deadOwner),
                "an unusable owner means there is nobody to return to");

        RecordingPort port = new RecordingPort(RecallFlow.RecallOutcome.recalledAt(2.0));
        assertFalse(RecallFlow.attempt(new EngagementState(), deadOwner, port).recalled());
        assertEquals(0, port.calls.size(), "no teleport may be attempted toward a dead owner");
    }

    @Test
    @DisplayName("the eligibility rule has exactly one implementation, used by both callers")
    void eligibilityRuleIsSingleSourced() {
        // The rule is exercised through both of its entry points and must agree. Two copies is how
        // "attempt it" and "should it have been attempted" drift apart.
        RecallFlow.RecallContext due = context(30.0, 400L);
        assertTrue(RecallFlow.recallDue(due));
        assertTrue(RecallFlow.recallDue(due.ownerId() != null && due.ownerUsable(), due.sameWorld(),
                due.distanceToOwner(), due.recallDistance()));

        RecallFlow.RecallContext notDue = context(RECALL, 400L);
        assertFalse(RecallFlow.recallDue(notDue));
        assertFalse(RecallFlow.recallDue(notDue.ownerId() != null && notDue.ownerUsable(),
                notDue.sameWorld(), notDue.distanceToOwner(), notDue.recallDistance()));
    }

    @Test
    @DisplayName("one evaluation runs one port call and one transition, never two of either")
    void oneEvaluationProducesOneAttemptAndOneTransition() {
        EngagementState state = new EngagementState();
        state.begin(ABANDONED, 0L, 500L);
        state.end();

        RecordingPort port = new RecordingPort(RecallFlow.RecallOutcome.recalledAt(30.0));
        assertTrue(RecallFlow.attempt(state, context(30.0, 400L), port).recalled());

        assertEquals(1, port.calls.size(), "one evaluation must attempt the recall exactly once");
        // The window is the configured length measured from the attempt, not from some later tick, so it
        // cannot have been pushed out by a repeated transition.
        assertTrue(state.isSuppressedAt(ABANDONED, 599L));
        assertFalse(state.isSuppressedAt(ABANDONED, 600L));
    }

    @Test
    @DisplayName("a null engagement state is tolerated: the recall still happens")
    void missingStateDoesNotBlockTheRecall() {
        // A companion can be recalled before the controller ever built a runtime record for it.
        RecordingPort port = new RecordingPort(RecallFlow.RecallOutcome.recalledAt(2.0));
        assertTrue(RecallFlow.attempt(null, context(30.0, 400L), port).recalled());
        assertEquals(1, port.calls.size());
    }

    @Test
    @DisplayName("the flow refuses a null port or a port that reports no outcome")
    void malformedPortIsRejected() {
        assertThrows(NullPointerException.class,
                () -> RecallFlow.attempt(new EngagementState(), context(30.0, 400L), null));
        // A null outcome cannot be silently read as failure: an adapter that forgets to report would
        // otherwise look like "nothing happened" while the teleport did happen.
        assertThrows(NullPointerException.class,
                () -> RecallFlow.attempt(new EngagementState(), context(30.0, 400L), ctx -> null));
    }

    @Test
    @DisplayName("the never() port performs no recall, so nothing else can be triggered by it")
    void neverPortRecallsNothing() {
        EngagementState state = new EngagementState();
        state.begin(ABANDONED, 0L, 500L);
        state.end();
        assertFalse(RecallFlow.attempt(state, context(30.0, 400L), RecallFlow.never()).recalled());
        assertTrue(state.canEngageUnderMode(ABANDONED, 401L, 900L, true, 2.0, 3.0, RECALL, MARGIN));
    }
}
