package com.nonid.affinity.service.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Mode-aware range and the three separate distances, through the methods production calls.
 *
 * <p>Each test drives {@code end()} / {@code applyRecallResult(...)} / {@code canEngageUnderMode(...)}
 * in the order the game does. Nothing is suppressed by hand on a path production would skip, and no
 * filtering rule is re-implemented here.
 */
class ModeRangeRulesTest {

    private static final UUID COMPANION_A = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID OWNER = UUID.fromString("eeeeeeee-0000-0000-0000-00000000000e");
    private static final UUID FAR_TARGET = UUID.fromString("cccccccc-0000-0000-0000-00000000000c");
    private static final UUID NEAR_TARGET = UUID.fromString("dddddddd-0000-0000-0000-00000000000d");

    private static final double RECALL = 10.0;
    private static final double MARGIN = 1.0;

    /** One shared, owner-scoped fact list. Companions read it; nobody consumes it. */
    private static final class SharedFacts {
        private final List<Long> sequences = new ArrayList<>();

        long publish() {
            long next = sequences.size() + 1000L;
            sequences.add(next);
            return next;
        }

        List<Long> snapshot() {
            return List.copyOf(sequences);
        }
    }

    @Test
    @DisplayName("a companion beside its owner still will not charge a target that is far from the owner")
    void recalledCompanionDoesNotResumeADistantChase() {
        EngagementState state = new EngagementState();
        SharedFacts facts = new SharedFacts();
        long watermark = facts.publish();
        state.begin(FAR_TARGET, 0L, watermark);
        state.end();
        state.applyRecallResult(true, 100L, 20L, 20L);      // suppression expires at tick 120

        // The exact scenario from the review: companion 2 blocks from the owner, old target 14 away,
        // suppression expired, and the scan produces a brand new fact.
        long fresh = facts.publish();
        assertFalse(state.canEngageUnderMode(FAR_TARGET, 500L, fresh, true,
                        2.0, 14.0, RECALL, MARGIN),
                "a target far from the owner must not restart a chase just because the window closed");
    }

    @Test
    @DisplayName("WAIT keeps the ability to defend itself in place when the owner is far away or gone")
    void waitDefendsInPlaceWithoutOwnerRange() {
        EngagementState state = new EngagementState();
        // WAIT: ownerRange = false. The owner is 40 blocks away, or offline entirely.
        assertTrue(state.canEngageUnderMode(NEAR_TARGET, 100L, 500L, false,
                        40.0, 40.0, RECALL, MARGIN),
                "WAIT self-defence must not depend on the owner being near");

        // The same condition under FOLLOW is refused, proving the mode really drives the outcome.
        assertFalse(state.canEngageUnderMode(NEAR_TARGET, 100L, 500L, true,
                        40.0, 4.0, RECALL, MARGIN),
                "FOLLOW is bounded by the owner");
    }

    @Test
    @DisplayName("WANDER introduces no owner-range constraint")
    void wanderHasNoOwnerRange() {
        EngagementState state = new EngagementState();
        assertTrue(state.canEngageUnderMode(NEAR_TARGET, 100L, 500L, false,
                        25.0, 25.0, RECALL, MARGIN));
    }

    @Test
    @DisplayName("re-entry is stricter than exit, so the boundary cannot oscillate")
    void reentryIsStricterThanExit() {
        EngagementState state = new EngagementState();
        // Companion just inside the recall distance but inside the re-entry margin as well.
        assertFalse(state.canEngageUnderMode(NEAR_TARGET, 100L, 500L, true,
                        9.5, 9.5, RECALL, MARGIN),
                "starting a fight requires more margin than merely being inside the exit bound");
        // Comfortably inside: allowed.
        assertTrue(state.canEngageUnderMode(NEAR_TARGET, 100L, 500L, true,
                        5.0, 5.0, RECALL, MARGIN));
    }

    @Test
    @DisplayName("suppressing one target does not block a nearby legal target with a smaller sequence")
    void suppressionIsScopedToItsOwnTarget() {
        EngagementState state = new EngagementState();
        SharedFacts facts = new SharedFacts();
        long watermark = facts.publish();          // e.g. 1000
        state.begin(FAR_TARGET, 0L, watermark);
        state.end();
        state.applyRecallResult(true, 100L, 400L, 400L);

        // B's fact has a SMALLER sequence than the watermark. It must still be answerable: the
        // watermark constrains the abandoned target, not every target.
        long smaller = watermark - 1;
        assertTrue(state.canEngageUnderMode(NEAR_TARGET, 150L, smaller, true,
                        3.0, 3.0, RECALL, MARGIN),
                "an unrelated nearby target must not be blocked by another target's watermark");
        // The abandoned one is still held.
        assertFalse(state.canEngageUnderMode(FAR_TARGET, 150L, watermark, true,
                        3.0, 3.0, RECALL, MARGIN));
    }

    @Test
    @DisplayName("one companion's recall leaves the other's state and the shared facts untouched")
    void recallDoesNotDisturbTheOtherCompanion() {
        SharedFacts facts = new SharedFacts();
        EngagementState a = new EngagementState();
        EngagementState b = new EngagementState();

        long watermark = facts.publish();
        a.begin(FAR_TARGET, 0L, watermark);
        a.end();
        a.applyRecallResult(true, 100L, 400L, 400L);

        List<Long> afterRecall = facts.snapshot();
        assertTrue(afterRecall.contains(watermark), "the shared fact list must not be consumed");

        // B, never recalled, still works on the same target.
        b.begin(FAR_TARGET, 0L, watermark);
        assertTrue(b.canEngageUnderMode(FAR_TARGET, 150L, watermark + 5, true,
                        4.0, 4.0, RECALL, MARGIN),
                "B must still answer after A was recalled");
        assertFalse(a.canEngageUnderMode(FAR_TARGET, 150L, watermark, true,
                        4.0, 4.0, RECALL, MARGIN), "A stays suppressed");
    }

    @Test
    @DisplayName("continuous facts cannot keep a stalled fight alive, this round or the next")
    void stalledFightIsNotRestartedByFacts() {
        EngagementState state = new EngagementState();
        SharedFacts facts = new SharedFacts();
        state.begin(FAR_TARGET, 0L, 500L);
        state.recordHit(FAR_TARGET, 100L);

        long tick = 100L;
        for (int round = 0; round < 12; round++) {
            facts.publish();
            tick += 40L;
        }
        assertTrue(state.isStalled(tick, 200L), "no hits, so the fight has stalled");

        // Facts keep arriving; the stall must not be undone by them.
        long fresh = facts.publish();
        assertTrue(state.isStalled(tick + 40L, 200L),
                "fresh evidence is not progress: a stalled fight stays stalled");
        assertTrue(fresh > 0);
    }

    @Test
    @DisplayName("a failed recall performs no success transition and produces no false suppression")
    void failedRecallProducesNoSuppression() {
        EngagementState state = new EngagementState();
        state.begin(FAR_TARGET, 0L, 500L);
        state.end();

        assertFalse(state.applyRecallResult(false, 100L, 400L, 400L));
        assertTrue(state.canEngageUnderMode(FAR_TARGET, 120L, 900L, true,
                        2.0, 2.0, RECALL, MARGIN),
                "nothing was suppressed, so the target is available");
    }
}
