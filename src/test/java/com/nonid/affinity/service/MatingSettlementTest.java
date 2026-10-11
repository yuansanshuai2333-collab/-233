package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nonid.affinity.service.MatingSettlementRules.Decision;
import com.nonid.affinity.service.MatingSettlementRules.Facts;
import com.nonid.affinity.service.MatingStopEvidence.Kind;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The affinity a mating animation is allowed to pay out.
 *
 * <p>Written for the reported defect (a creature forced itself on the player, the player broke out, affinity
 * went UP anyway) and rewritten after review rejected the first fix: that version inferred completion from
 * elapsed time, which cannot separate a natural end from a late escape. The decision now consumes the REAL stop
 * path observed by the optional NeedsOfNature adapter, and refuses whenever it is unknown.
 *
 * <p>A later report (playing a mating to its end with NON's "next stage" debug key paid no affinity) showed the
 * opposite error: that path was refused as a manual advance even though NON stops the instance at its FINAL
 * stage there, which is the animation's own end state. The decision now counts both completion paths; what
 * keeps a break-out from being rewarded is the escape mark, which is checked first, and test 1f pins that for
 * every kind of evidence.
 *
 * <p>The counter-examples below are the ones the review asked for by name.
 */
class MatingSettlementTest {

    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID CREATURE = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b");
    private static final UUID OTHER_CREATURE = UUID.fromString("cccccccc-0000-0000-0000-00000000000c");
    private static final UUID INSTANCE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static Facts facts(Kind evidence, boolean startedByMod, boolean forced) {
        return new Facts(false, false, evidence, startedByMod, forced);
    }

    // ------------------------------------------------------------------
    // 1. Escape / interruption, at any point in time
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. an escape earns nothing, however long the animation had already run")
    void escapeEarnsNothingAtAnyTime() {
        // The review's first counter-example: the final stage really lasts longer than any estimate, and the
        // player breaks out after that estimate has passed. There is no time input in this decision at all --
        // the reason NON reported is what settles it.
        assertEquals(Decision.NONE,
                MatingSettlementRules.decide(new Facts(false, false, Kind.INTERRUPTED, true, true)));
        assertEquals(Decision.NONE,
                MatingSettlementRules.decide(new Facts(false, false, Kind.INTERRUPTED, true, false)));

        // And an escape mark is enough on its own, whatever the reason says.
        assertEquals(Decision.NONE,
                MatingSettlementRules.decide(new Facts(false, true, Kind.NATURAL_END, true, false)));
        assertEquals(Decision.NONE,
                MatingSettlementRules.decide(new Facts(false, true, Kind.UNKNOWN, false, false)));
    }

    @Test
    @DisplayName("1b. the decision cannot depend on time: its facts carry no durations")
    void decisionHasNoTimeInputs() {
        // The rejected fix used "the final stage ran at least N ticks" as proof of completion. Pinning the
        // shape of the facts is what keeps that from creeping back: no field may be a duration or tick count.
        for (var component : Facts.class.getRecordComponents()) {
            String name = component.getName().toLowerCase(java.util.Locale.ROOT);
            assertFalse(name.contains("tick") || name.contains("duration") || name.contains("elapsed")
                            || name.contains("time") || name.contains("window"),
                    "settlement must not be decided from time; found fact '" + component.getName() + "'");
        }
        for (var method : MatingSettlementRules.class.getDeclaredMethods()) {
            assertFalse(method.getName().contains("expectedStageTicks"),
                    "the time-estimate helper must be gone, not merely unused");
        }
    }

    @Test
    @DisplayName("1d. the end reached with NON's next-stage control counts, but never as a struggle")
    void manualAdvanceToTheFinalStageCounts() {
        // The end of the animation is requested instead of waited for: the debug "next stage" key runs the
        // stages out one press at a time, and NON stops the instance at its FINAL stage with its own
        // NATURAL_END reason. That IS the animation's own end state, so it settles exactly like the last
        // stage running out -- the settlement is not withheld merely because the end was requested. The forced
        // flag keeps deciding the player-initiated bonus, here as everywhere else.
        assertEquals(Decision.REWARD_VOLUNTARY,
                MatingSettlementRules.decide(new Facts(false, false, Kind.MANUAL_ADVANCE, true, true)));
        assertEquals(Decision.REWARD_PLAYER_INITIATED,
                MatingSettlementRules.decide(new Facts(false, false, Kind.MANUAL_ADVANCE, true, false)));
        assertEquals(Decision.REWARD_VOLUNTARY,
                MatingSettlementRules.decide(new Facts(false, false, Kind.MANUAL_ADVANCE, false, false)));

        // It is still not a struggle: only a real break-out is charged the escape loss, and this path has no
        // break-out behind it.
        assertEquals(0, MatingSettlementRules.escapeLoss(false, true, 25),
                "a requested end carries no escape loss: only a real break-out does");
    }

    @Test
    @DisplayName("1f. the break-free action still pays nothing, because an escape is refused before the evidence")
    void anEscapeIsRefusedBeforeTheStopEvidence() {
        // This is the guard that allows a requested end to count (test 1d). The break-free action ends a FORCED
        // mating by advancing it past its final stage, so it arrives with the SAME evidence as a debug-key end.
        // What separates them is the session: the action marks the player's escape BEFORE it advances, and an
        // interrupted session is refused outright -- whatever the evidence says, including a natural end.
        for (Kind evidence : Kind.values()) {
            assertEquals(Decision.NONE,
                    MatingSettlementRules.decide(new Facts(false, true, evidence, true, true)),
                    "an escaped session must settle to nothing for evidence " + evidence);
            assertEquals(Decision.NONE,
                    MatingSettlementRules.decide(new Facts(true, false, evidence, true, false)),
                    "an already-handled instance must settle to nothing for evidence " + evidence);
        }
        // And the loss the break-out does charge is driven by the escape mark, not by the evidence.
        assertEquals(25, MatingSettlementRules.escapeLoss(true, true, 25),
                "a real break-out of a forced mating still costs the configured loss");
    }

    @Test
    @DisplayName("1e. an interruption is never a punishment either")
    void interruptionIsNeverPenalised() {
        assertEquals(3, Decision.values().length,
                "only NONE and the two reward shapes may exist; a penalty here would double-charge an escape");
        for (Decision decision : Decision.values()) {
            assertTrue(decision == Decision.NONE || MatingSettlementRules.grants(decision));
        }
    }

    // ------------------------------------------------------------------
    // 2. Unknown / unobserved stops
    // ------------------------------------------------------------------

    @Test
    @DisplayName("2. an unobserved stop is never a completion (adapter off, foreign path, autonomous animation)")
    void unknownStopIsNeverACompletion() {
        assertEquals(Decision.NONE, MatingSettlementRules.decide(facts(Kind.UNKNOWN, false, false)),
                "without the real reason nothing may be granted");
        assertEquals(Decision.NONE, MatingSettlementRules.decide(facts(Kind.UNKNOWN, true, false)));
    }

    @Test
    @DisplayName("3. an autonomous animation is not a completion just because it stopped")
    void autonomousAnimationNeedsTheSameEvidence() {
        // Creature-initiated animation, stop observed but interrupted: nothing.
        assertEquals(Decision.NONE,
                MatingSettlementRules.decide(facts(Kind.INTERRUPTED, false, false)));
        // Creature-initiated animation that really ended: settles without the player-initiated bonus.
        assertEquals(Decision.REWARD_VOLUNTARY,
                MatingSettlementRules.decide(facts(Kind.NATURAL_END, false, false)));
    }

    // ------------------------------------------------------------------
    // 4. Player attribution
    // ------------------------------------------------------------------

    @Test
    @DisplayName("4. a forced mating started by this module never gets the player-initiated bonus")
    void forcedMatingNeverGetsThePlayerBonus() {
        Facts forced = facts(Kind.NATURAL_END, true, true);
        assertFalse(MatingSettlementRules.playerInitiated(forced),
                "the player rejected the request; the module starting the animation is not the player choosing it");
        assertEquals(Decision.REWARD_VOLUNTARY, MatingSettlementRules.decide(forced));

        Facts voluntary = facts(Kind.NATURAL_END, true, false);
        assertTrue(MatingSettlementRules.playerInitiated(voluntary));
        assertEquals(Decision.REWARD_PLAYER_INITIATED, MatingSettlementRules.decide(voluntary));
    }

    // ------------------------------------------------------------------
    // 5. Normal completion, exactly once
    // ------------------------------------------------------------------

    @Test
    @DisplayName("5. a normal voluntary completion settles once, and a shorter final stage is irrelevant")
    void voluntaryCompletionSettlesOnce() {
        // The review's third counter-example: the final stage is SHORTER than the previous one and the
        // animation ends normally. Because the reason is NATURAL_END, the duration comparison that would have
        // refused this no longer exists.
        MatingSessions sessions = new MatingSessions();
        sessions.open(INSTANCE, PLAYER, CREATURE, true, false, 100L);

        MatingSessions.Session session = sessions.take(INSTANCE);
        assertNotNull(session, "the running animation must be found");
        Facts facts = new Facts(false, session.interrupted(), Kind.NATURAL_END, session.startedByMod(),
                session.forced());
        assertEquals(Decision.REWARD_PLAYER_INITIATED, MatingSettlementRules.decide(facts));

        // The second stop notification for the same instance settles nothing.
        assertNull(sessions.take(INSTANCE), "a duplicate stop must not settle again");
        assertTrue(sessions.isHandled(INSTANCE));
        assertEquals(Decision.NONE, MatingSettlementRules.decide(new Facts(sessions.isHandled(INSTANCE),
                false, Kind.NATURAL_END, true, false)));
    }

    @Test
    @DisplayName("6. a stop arriving before the interrupt mark cannot be changed afterwards")
    void stopBeforeMarkIsDecidedByTheReason() {
        // The review's second counter-example. The stop event and the escape mark are independent signals; the
        // decision is taken once, from the real reason, when the stop arrives. A mark that lands afterwards
        // finds nothing to mark and cannot retroactively add or remove a settlement.
        MatingSessions sessions = new MatingSessions();
        sessions.open(INSTANCE, PLAYER, CREATURE, true, true, 10L);

        MatingSessions.Session session = sessions.take(INSTANCE);
        assertNotNull(session);
        assertEquals(Decision.REWARD_VOLUNTARY,
                MatingSettlementRules.decide(new Facts(false, session.interrupted(), Kind.NATURAL_END,
                        session.startedByMod(), session.forced())));

        assertEquals(0, sessions.markInterrupted(PLAYER, CREATURE),
                "a consumed instance is gone: a late escape mark cannot resurrect it or double-count");
    }

    // ------------------------------------------------------------------
    // Evidence store
    // ------------------------------------------------------------------

    @Test
    @DisplayName("7. evidence: the natural-completion paths count, an unmarked stop does not, and 'unknown' is not 'interrupted'")
    void evidenceClassifiesStops() {
        // A store with no adapter watching may NOT claim anything: an unobserved stop is UNKNOWN, and the
        // settlement refuses it. Treating it as "interrupted" would be a claim about the animation that the
        // adapter cannot support.
        MatingStopEvidence.clearAll();
        MatingStopEvidence.setAdapterStatus("NeedsOfNature is not installed; settlement is inactive");
        assertFalse(MatingStopEvidence.adapterActive());
        assertEquals(Kind.UNKNOWN, MatingStopEvidence.observedKind(INSTANCE));
        assertEquals(Decision.NONE, MatingSettlementRules.decide(
                new Facts(false, false, MatingStopEvidence.observedKind(INSTANCE), true, false)));

        // With the adapter watching, a stop that took neither completion path was an interruption.
        MatingStopEvidence.setAdapterStatus("enabled for NeedsOfNature 1.5.1+1.21.11; settlement uses the"
                + " real stop paths");
        assertTrue(MatingStopEvidence.adapterActive());
        assertEquals(Kind.INTERRUPTED, MatingStopEvidence.observedKind(INSTANCE));

        // The tick-driven completion path is a completion.
        UUID tickPath = UUID.randomUUID();
        MatingStopEvidence.recordNaturalEnd(tickPath);
        assertEquals(Kind.NATURAL_END, MatingStopEvidence.observedKind(tickPath));

        // The requested END of the animation is a completion. It is recorded ONLY by the instruction that ends
        // the FINAL stage, never by being inside the advance call, so this asserts the mark rather than a state
        // that a mere stage step would also produce.
        UUID advancePath = UUID.randomUUID();
        assertEquals(Kind.INTERRUPTED, MatingStopEvidence.observedKind(advancePath));
        MatingStopEvidence.recordFinalStageAdvance(advancePath);
        assertEquals(Kind.MANUAL_ADVANCE, MatingStopEvidence.observedKind(advancePath));
        assertTrue(MatingSettlementRules.observedCompletion(MatingStopEvidence.observedKind(advancePath)),
                "the animation stopped at its own final stage, so it is a completion");

        // A reason, should a future adapter be able to read one, is classified the same conservative way.
        UUID reasonPath = UUID.randomUUID();
        MatingStopEvidence.recordReason(reasonPath, "NATURAL_END");
        assertEquals(Kind.NATURAL_END, MatingStopEvidence.observedKind(reasonPath));
        MatingStopEvidence.recordReason(reasonPath, "SOME_FUTURE_REASON");
        assertEquals(Kind.INTERRUPTED, MatingStopEvidence.observedKind(reasonPath),
                "an unknown reason must not be treated as a completion");
        MatingStopEvidence.recordReason(reasonPath, "DAMAGE_STOP");
        assertEquals(Kind.INTERRUPTED, MatingStopEvidence.observedKind(reasonPath),
                "an explicit interruption must not be overwritten by a later path marker");

        MatingStopEvidence.forget(reasonPath);
        assertEquals(Kind.INTERRUPTED, MatingStopEvidence.observedKind(reasonPath));
        MatingStopEvidence.clearAll();
        MatingStopEvidence.setAdapterStatus("not initialised");
    }

    @Test
    @DisplayName("7b. advancing a stage is not restarting the animation, and being inside the call proves nothing")
    void advancingIsNotRestartingAndTheCallIsNotEvidence() {
        // Regression guard for the defect this file's counterpart test 7b used to encode. NON's advance entry
        // does two different things: it STEPS to the next stage, and -- only from the final stage -- it ENDS the
        // animation. An earlier version of this adapter treated the whole call as a completion, so an animation
        // that a third-party stage listener stopped mid-step, or one whose call was left marked by an exception,
        // could be settled as completed. Evidence is now recorded only where the final stage really ends, and
        // these two properties are what that means:
        //
        //   (a) no public method restarts an animation, so a stop that consumed its session is final;
        //   (b) no public method claims "a request is running", because that is not knowledge about an animation.
        MatingStopEvidence.clearAll();
        MatingStopEvidence.setAdapterStatus("enabled for NeedsOfNature 1.5.1+1.21.11; settlement uses the"
                + " real completion paths");

        // (a) A step request that did not end the animation leaves no completion mark at all.
        UUID stepped = UUID.randomUUID();
        assertEquals(Kind.INTERRUPTED, MatingStopEvidence.observedKind(stepped),
                "a stage step is an ordinary running state, so it is never a completion");
        assertEquals(Decision.NONE, MatingSettlementRules.decide(new Facts(false, false,
                MatingStopEvidence.observedKind(stepped), true, false)),
                "a step must not settle");

        // A requested end is a completion, and forgetting it (what the settlement does once decided) must not
        // leave anything behind that a later stop could read.
        UUID ended = UUID.randomUUID();
        MatingStopEvidence.recordFinalStageAdvance(ended);
        assertEquals(Kind.MANUAL_ADVANCE, MatingStopEvidence.observedKind(ended));
        MatingStopEvidence.forget(ended);
        assertEquals(Kind.INTERRUPTED, MatingStopEvidence.observedKind(ended),
                "once decided, an instance's evidence is gone: nothing can be settled twice from it");

        // A session that was consumed cannot be restarted by a late mark, so a stop that already settled cannot
        // be turned into a second settlement.
        MatingSessions consumed = new MatingSessions();
        consumed.open(INSTANCE, PLAYER, CREATURE, true, false, 5L);
        assertNotNull(consumed.take(INSTANCE));
        MatingStopEvidence.recordFinalStageAdvance(INSTANCE);
        assertEquals(Decision.NONE, MatingSettlementRules.decide(new Facts(consumed.isHandled(INSTANCE), false,
                        MatingStopEvidence.observedKind(INSTANCE), true, false)),
                "a late completion mark cannot resurrect a handled instance");

        // (b) "Being inside the advance call" is not observable knowledge any more: the only public way to learn
        // about an animation is a completion that was actually recorded.
        for (java.lang.reflect.Method method : MatingStopEvidence.class.getDeclaredMethods()) {
            assertFalse(java.lang.reflect.Modifier.isPublic(method.getModifiers())
                            && (method.getName().contains("Advancing")
                            || method.getName().contains("EnterAdvance")
                            || method.getName().contains("LeaveAdvance")),
                    "the store must not expose 'inside the advance call' as evidence; found "
                            + method.getName());
        }

        MatingStopEvidence.clearAll();
        MatingStopEvidence.setAdapterStatus("not initialised");
    }

    // ------------------------------------------------------------------
    // Session registry
    // ------------------------------------------------------------------

    @Test
    @DisplayName("9. an escape marks every animation of that pair, including ones we did not start")
    void escapeMarksEveryAnimationOfThePair() {
        MatingSessions sessions = new MatingSessions();
        UUID ours = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();
        sessions.open(ours, PLAYER, CREATURE, true, true, 10L);
        sessions.open(theirs, PLAYER, CREATURE, false, false, 12L);
        sessions.open(unrelated, PLAYER, OTHER_CREATURE, false, false, 12L);

        assertEquals(2, sessions.markInterrupted(PLAYER, CREATURE),
                "both animations of that pair must be marked, whoever started them");
        assertTrue(sessions.isInterrupted(ours));
        assertTrue(sessions.isInterrupted(theirs));
        assertFalse(sessions.isInterrupted(unrelated),
                "an animation with another creature must not be marked by this escape");

        assertEquals(1, sessions.markInterrupted(PLAYER, OTHER_CREATURE));
        assertTrue(sessions.isInterrupted(unrelated));
    }

    @Test
    @DisplayName("10. an unknown stop is remembered as handled, so a later path cannot reward it")
    void unknownStopIsRememberedAsHandled() {
        MatingSessions sessions = new MatingSessions();
        assertNull(sessions.take(INSTANCE));
        assertTrue(sessions.isHandled(INSTANCE), "an untracked stop must still be remembered as handled");
        assertEquals(Decision.NONE, MatingSettlementRules.decide(new Facts(sessions.isHandled(INSTANCE),
                false, Kind.NATURAL_END, true, false)));
    }

    @Test
    @DisplayName("11. this module's own start upgrades the session without losing the forced flag")
    void ourStartUpgradesTheOpenSession() {
        MatingSessions sessions = new MatingSessions();
        sessions.open(INSTANCE, PLAYER, CREATURE, false, false, 100L);
        sessions.recordOurStart(INSTANCE, PLAYER, CREATURE, true, 101L);
        MatingSessions.Session session = sessions.take(INSTANCE);
        assertNotNull(session);
        assertTrue(session.startedByMod());
        assertTrue(session.forced(),
                "the forced flag decides the player-initiated bonus, so it must survive");
        assertFalse(MatingSettlementRules.playerInitiated(new Facts(false, false, Kind.NATURAL_END,
                session.startedByMod(), session.forced())));

        // Reverse order: our start first, the plain start event must not downgrade it.
        MatingSessions other = new MatingSessions();
        UUID second = UUID.randomUUID();
        other.recordOurStart(second, PLAYER, CREATURE, false, 10L);
        other.open(second, PLAYER, CREATURE, false, false, 10L);
        MatingSessions.Session secondSession = other.take(second);
        assertNotNull(secondSession);
        assertTrue(secondSession.startedByMod(),
                "a plain start event must not downgrade a session this module started");
    }

    @Test
    @DisplayName("12. bounds hold: one session per instance, bounded terminal memory")
    void boundsAreEnforced() {
        MatingSessions sessions = new MatingSessions();
        sessions.open(INSTANCE, PLAYER, CREATURE, true, false, 10L);
        sessions.open(INSTANCE, PLAYER, CREATURE, true, false, 11L);
        assertEquals(1, sessions.runningCount(), "the same instance cannot be tracked twice");

        for (int i = 0; i < 5000; i++) {
            sessions.markHandled(UUID.randomUUID());
        }
        assertTrue(sessions.handledCount() <= 1024,
                "terminal instance memory must stay bounded on a long-running server");
    }

    // ------------------------------------------------------------------
    // Escape loss
    // ------------------------------------------------------------------

    @Test
    @DisplayName("13. breaking out of a FORCED mating costs the configured extra loss, and only then")
    void escapeLossAppliesOnlyToAForcedMatingThePlayerBrokeOutOf() {
        assertEquals(25, MatingSettlementRules.escapeLoss(true, true, 25),
                "the player fought the creature off: the configured loss is charged");
        assertEquals(0, MatingSettlementRules.escapeLoss(false, true, 25),
                "an interruption that was not the player's doing costs nothing");
        assertEquals(0, MatingSettlementRules.escapeLoss(true, false, 25),
                "a voluntary mating the player abandons costs nothing extra");
        assertEquals(0, MatingSettlementRules.escapeLoss(true, true, 0),
                "0 disables the loss, restoring the older behaviour");
        assertEquals(0, MatingSettlementRules.escapeLoss(true, true, -5),
                "a negative configuration is treated as off, never as a gain");
    }

    @Test
    @DisplayName("14. a struggle can never pay out a friendship token")
    void escapeCannotGrantTheFriendshipToken() {
        // The token is handed out only for a POSITIVE change whose reason is a completed mating. The escape path
        // uses its own reason and a negative delta, so it cannot reach the gift -- asserted on the gate itself
        // rather than on the hope that the two stay apart.
        assertTrue(AffinityService.isMatingGainReason(AffinityService.MATING_REASON));
        assertTrue(AffinityService.isMatingGainReason(AffinityService.PLAYER_INITIATED_REASON));
        assertFalse(AffinityService.isMatingGainReason(AffinityService.ESCAPE_REASON),
                "an escape must not be able to trigger the cap gift");
        assertFalse(AffinityService.isMatingGainReason(AffinityService.REJECT_REASON));
        assertFalse(AffinityService.isMatingGainReason("peak"));
        assertFalse(AffinityService.isMatingGainReason(null));
    }

    @Test
    @DisplayName("15. the escape is recorded once per instance and only for that pair")
    void escapeIsRecordedPerInstanceAndPerPair() {
        MatingSessions sessions = new MatingSessions();
        UUID forced = UUID.randomUUID();
        sessions.open(forced, PLAYER, CREATURE, true, true, 10L);
        assertEquals(1, sessions.markPlayerEscape(PLAYER, CREATURE));

        MatingSessions.Session session = sessions.take(forced);
        assertNotNull(session);
        assertTrue(session.escapedByPlayer());
        assertTrue(session.forced());
        assertEquals(25, MatingSettlementRules.escapeLoss(session.escapedByPlayer(), session.forced(), 25));

        // The instance is consumed, so a repeated mark (or a second settlement) cannot charge again.
        assertEquals(0, sessions.markPlayerEscape(PLAYER, CREATURE));
        assertNull(sessions.take(forced));

        // A different creature is untouched.
        UUID otherPair = UUID.randomUUID();
        sessions.open(otherPair, PLAYER, OTHER_CREATURE, true, true, 10L);
        assertEquals(0, sessions.markPlayerEscape(PLAYER, CREATURE));
        MatingSessions.Session other = sessions.take(otherPair);
        assertNotNull(other);
        assertFalse(other.escapedByPlayer());
        assertEquals(0, MatingSettlementRules.escapeLoss(other.escapedByPlayer(), other.forced(), 25));
    }

    @Test
    @DisplayName("16. the break-free action only ever finds THIS player's FORCED mating")
    void breakFreeTargetsOnlyThePlayersForcedMating() {
        MatingSessions sessions = new MatingSessions();
        UUID forced = UUID.randomUUID();
        UUID voluntary = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();
        sessions.open(forced, PLAYER, CREATURE, true, true, 10L);
        sessions.open(voluntary, PLAYER, OTHER_CREATURE, true, false, 10L);
        sessions.open(someoneElse, UUID.randomUUID(), CREATURE, true, true, 10L);

        MatingSessions.Session found = sessions.findForcedForPlayer(PLAYER);
        assertNotNull(found, "the forced mating must be findable");
        assertEquals(forced, found.instanceId(),
                "a voluntary mating must never be the target of a break-free request");
        assertEquals(forced, sessions.findForcedForPlayer(PLAYER).instanceId(),
                "another player's forced mating must not be reachable");

        // Breaking free marks the escape, which is what makes the configured loss apply and the reward not.
        assertEquals(1, sessions.markPlayerEscape(PLAYER, CREATURE));
        MatingSessions.Session taken = sessions.take(forced);
        assertNotNull(taken);
        assertTrue(taken.escapedByPlayer());
        assertTrue(taken.forced());
        assertEquals(25, MatingSettlementRules.escapeLoss(taken.escapedByPlayer(), taken.forced(), 25));
        assertEquals(Decision.NONE, MatingSettlementRules.decide(new Facts(false, taken.interrupted(),
                Kind.MANUAL_ADVANCE, taken.startedByMod(), taken.forced())),
                "the break-free causes the same evidence as a requested end, and is still refused: the escape"
                        + " mark is checked before the evidence");

        // Once consumed, the action finds nothing.
        assertNull(sessions.findForcedForPlayer(PLAYER));
    }

    @Test
    @DisplayName("17. a struggle won on NON's own screen is recorded as an escape for that instance only")
    void nonStruggleWinIsRecordedPerInstance() {
        MatingSessions sessions = new MatingSessions();
        UUID mine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        sessions.open(mine, PLAYER, CREATURE, true, true, 10L);

        // NON reports the win by instance id, which is how the escape is matched to our own session.
        assertTrue(sessions.markPlayerEscapeForInstance(mine));
        MatingSessions.Session session = sessions.take(mine);
        assertNotNull(session);
        assertTrue(session.escapedByPlayer(), "a won struggle must count as the player's escape");
        assertEquals(25, MatingSettlementRules.escapeLoss(session.escapedByPlayer(), session.forced(), 25),
                "a won struggle therefore costs the configured escape loss");

        // An instance we never started is simply ignored: NON's own escapes must not mark anything of ours.
        assertFalse(sessions.markPlayerEscapeForInstance(theirs));
        assertFalse(sessions.markPlayerEscapeForInstance(null));
        assertEquals(0, sessions.runningCount() - 0);
    }
}
