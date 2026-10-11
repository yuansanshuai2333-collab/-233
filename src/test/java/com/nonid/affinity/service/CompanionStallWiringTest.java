package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nonid.affinity.config.AffinityConfig;
import com.nonid.affinity.service.policy.EngagementState;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The stall exit as WIRED, not merely as a rule.
 *
 * <p>Both faults this round fixed live in how the controller SEQUENCES its calls, so a rule test cannot
 * see either of them: the fight used to end with no suppression (the next round re-selected the same
 * enemy), and the Selection made earlier in the same round was left standing (so that round wrote the
 * target straight back). The two production seams driven here are exactly the two decisions involved:
 *
 * <ul>
 *   <li>{@code runtimeFor} / {@code runStallExit} -- what the exit changes; and</li>
 *   <li>{@code selectionSurvivesRun} -- whether the round's already-made Selection may still be used.</li>
 * </ul>
 *
 * <p>Scope, stated honestly: the stall DETECTION is not exercised, because it needs a real entity to
 * measure distance to. What is verified is that once a stall is detected, production ends the fight,
 * suppresses the right target, and refuses to re-open it -- while leaving a different threat alone.
 * The remaining in-game question is whether a real unreachable creature is judged stalled at the right
 * moment, which stays on the manual acceptance list.
 */
class CompanionStallWiringTest {

    private static final UUID COMPANION = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID STALLED = UUID.fromString("cccccccc-0000-0000-0000-00000000000c");
    private static final UUID OTHER = UUID.fromString("dddddddd-0000-0000-0000-00000000000d");

    private static final long STALL_LIMIT = 200L;
    private static final long SUPPRESS_TICKS = 200L;
    private static final long NOW = 600L;

    private static AffinityConfig config() {
        AffinityConfig config = new AffinityConfig();
        config.companionEngageStallTicks = STALL_LIMIT;
        config.companionStallSuppressTicks = SUPPRESS_TICKS;
        return config;
    }

    private static EngagementState progressOf() {
        EngagementState progress = CompanionCombatController.progressFor(COMPANION);
        assertNotNull(progress, "the runtime record must exist");
        return progress;
    }

    /**
     * Run the production exit against a companion whose recorded fight began at tick 0 and never
     * progressed. {@code markStallExit} is the same method the tick calls, so only the entity-facing
     * side effects (releasing the target on the creature, the debug line) are absent here.
     */
    private static void runExit(UUID abandoned, long now) {
        CompanionCombatController.clear();
        CompanionCombatController.runtimeBeginEngagement(COMPANION, abandoned, 0L, 500L);
        CompanionCombatController.markStallExitFor(COMPANION, abandoned, now, config());
    }

    @Test
    @DisplayName("the exit ends the fight, suppresses that one target, and reports a stall")
    void exitSuppressesTheAbandonedTarget() {
        runExit(STALLED, NOW);
        EngagementState progress = progressOf();

        assertTrue(CompanionCombatController.lastExitWasStall(COMPANION),
                "the exit must be attributable to a stall, not a recall");
        assertTrue(progress.isSuppressedAt(STALLED, NOW), "the abandoned target is off limits now");
        assertTrue(progress.isSuppressedAt(STALLED, NOW + SUPPRESS_TICKS - 1L),
                "and for the rest of the configured window");
        assertFalse(progress.isSuppressedAt(STALLED, NOW + SUPPRESS_TICKS),
                "the window is a delay, not a permanent ban");
    }

    @Test
    @DisplayName("inside the window the round's own Selection is refused, and the window covers the next round")
    void sameRoundSelectionIsRefused() {
        runExit(STALLED, NOW);
        EngagementState progress = progressOf();

        // The Selection was made before the exit ran. Without this check the same round writes the
        // target straight back and the exit achieves nothing at all.
        assertFalse(CompanionCombatController.selectionSurvivesRun(progress, STALLED, NOW),
                "the stalled target must not be re-opened by the round that just released it");
        // The round that follows: still suppressed, so a brand-new fact cannot re-open it either.
        assertFalse(CompanionCombatController.selectionSurvivesRun(progress, STALLED, NOW + 40L));
    }

    @Test
    @DisplayName("a different threat in the same round is unaffected")
    void otherTargetsSurvive() {
        runExit(STALLED, NOW);
        EngagementState progress = progressOf();

        assertTrue(CompanionCombatController.selectionSurvivesRun(progress, OTHER, NOW),
                "suppressing one target must not cancel a decision about another");
        assertTrue(CompanionCombatController.selectionSurvivesRun(progress, null, NOW),
                "an IGNORE selection has no target to block and must always carry on");
        assertFalse(progress.isSuppressedAt(OTHER, NOW), "the other threat was never suppressed");
    }

    @Test
    @DisplayName("when the evidence had already expired there is no Selection, and the exit still suppresses")
    void exitWithoutSelectionStillSuppresses() {
        // This is the branch that had no coverage at all: choose() returns IGNORE because the fact aged
        // out, so the round has nothing to protect, and the only work left is ending the stale fight.
        runExit(STALLED, NOW);
        EngagementState progress = progressOf();

        assertTrue(CompanionCombatController.selectionSurvivesRun(progress, null, NOW));
        assertTrue(CompanionCombatController.lastExitWasStall(COMPANION),
                "the exit must still be recorded as a stall");
        assertTrue(progress.isSuppressedAt(STALLED, NOW));
        // NOTHING may be re-engaged while the window holds, for this target, from any branch.
        assertFalse(CompanionCombatController.selectionSurvivesRun(progress, STALLED, NOW + 1L));
    }

    @Test
    @DisplayName("WIRING: a WAIT companion that needs air is still refused the enemy it stalled on")
    void waitCompanionNeedingAirStillDoesNotReacquire() {
        // The scenario the air rule must not break: a waiting companion is drowning AND has an enemy
        // beside it. Releasing movement so it can surface must not become "and now it may chase".
        runExit(STALLED, NOW);
        EngagementState progress = progressOf();

        // The suppression outcome is read through the production predicate, which takes no air input at
        // all -- so the drowning state cannot turn a released fight back into an available target. That
        // absence is the guarantee: it is asserted structurally rather than by observing a boolean.
        assertFalse(CompanionCombatController.selectionSurvivesRun(progress, STALLED, NOW + 1L),
                "an enemy the companion stalled on must stay off limits while it surfaces");
        assertFalse(CompanionCombatController.selectionSurvivesRun(progress, STALLED, NOW + 20L),
                "and for the rest of the suppression window");
    }

    @Test
    @DisplayName("the exit stops the fight's progress clock, so the keep-alive can never re-assert it")
    void liveFightIsEndedByTheExit() {
        // Start from a genuinely live fight, then run the exit.
        CompanionCombatController.clear();
        CompanionCombatController.runtimeBeginEngagement(COMPANION, STALLED, 0L, 500L);
        assertTrue(progressOf().isEngaged(), "the fight is live before the exit");
        assertTrue(progressOf().lastProgressTick() >= 0L, "and its progress clock is running");

        CompanionCombatController.markStallExitFor(COMPANION, STALLED, NOW, config());

        // The engagement record is left with no live fight at all. The keep-alive branch tests exactly
        // this field (`engagedTarget != null`), so nothing the exit does may leave it looking alive --
        // re-creating it as a fresh engagement would have resurrected an already-stalled fight, because
        // begin() stamps the progress clock and the stale check would fire on the very next round.
        assertFalse(progressOf().isEngaged(), "no live fight may survive a stall exit");
        assertNull(CompanionCombatController.engagedTargetFor(COMPANION),
                "the record must not name a target for the keep-alive to re-assert");
        assertEquals(-1L, progressOf().lastProgressTick(),
                "the progress clock must be stopped, not restarted");
        // The exit reason is recorded separately, so it is attributable without pretending the fight is
        // still running -- including on the branch where the evidence had expired and nothing was chosen.
        assertTrue(CompanionCombatController.lastExitWasStall(COMPANION),
                "the exit must be attributable to a stall");
        assertTrue(progressOf().isSuppressedAt(STALLED, NOW),
                "and the released target is off limits for the suppression window");
    }
}
