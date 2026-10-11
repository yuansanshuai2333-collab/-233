package com.nonid.affinity.service;

import com.nonid.affinity.data.PlayerAffinity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EscapeAttackSettlementTest {
    @Test void genericInterruptionStillTracksPossibleOldChargeButAnAlreadyEscapedSessionDoesNotExcuseNewAttacks() {
        var sessions = new GroupMatingSessions(); var player = UUID.randomUUID(); var creature = UUID.randomUUID();
        var id = UUID.randomUUID(); sessions.recordOurStart(id, player, creature, true, 0);
        sessions.observe(id, List.of(), List.of(creature), 1);
        assertEquals(1, sessions.escapeChargeCandidates(player, creature).size());
        sessions.markPlayerEscape(player, creature);
        assertTrue(sessions.escapeChargeCandidates(player, creature).isEmpty());
    }
    @Test void failedNotificationAfterFallbackCannotReplayTheActualDeduction() {
        var id = UUID.randomUUID(); var value = new PlayerAffinity(75);
        var state = new EscapeAttackSettlement(value, -200, 200, List.of(id), loss -> {
            value.addAffinity(-loss, -200, 200); throw new IllegalStateException("notification failure");
        });
        state.record(1); assertThrows(IllegalStateException.class, () -> state.settled(id, false));
        state.settled(id, false); assertEquals(74, value.getAffinity());
        assertFalse(value.toNbt().contains("pending_escape_attacks"));
    }
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    final PlayerAffinity relation = new PlayerAffinity(75);
    EscapeAttackSettlement feedback(List<UUID> ids) {
        return new EscapeAttackSettlement(relation, -200, 200, ids, loss -> relation.addAffinity(-loss, -200, 200));
    }
    @Test void actualOldChargeExcusesOnlyTheNewAffinityAndConsumesObligation() {
        relation.addBond(.95); var state = feedback(List.of(a)); state.record(1);
        relation.addAffinity(-5, -200, 200); state.settled(a, true); state.settled(a, false);
        assertEquals(70, relation.getAffinity()); assertEquals(.95, relation.getBond());
        assertFalse(relation.toNbt().contains("pending_escape_attacks"));
    }
    @Test void skippedOldChargeFallsBackExactlyOnceWithoutBondReplay() {
        relation.addBond(.95); var state = feedback(List.of(a)); state.record(1); state.record(2);
        state.settled(a, false); state.settled(a, false);
        assertEquals(74, relation.getAffinity()); assertEquals(.95, relation.getBond());
        assertFalse(relation.toNbt().contains("pending_escape_attacks"));
    }
    @Test void stopBeforeActualHealthWriteWorksWithBothActualOutcomes() {
        var skipped = feedback(List.of(a)); skipped.settled(a, false); skipped.record(1); skipped.record(1);
        assertEquals(74, relation.getAffinity());
        var paid = feedback(List.of(b)); paid.settled(b, true); paid.record(2);
        assertEquals(74, relation.getAffinity());
    }
    @Test void multipleSessionsWaitForAllFailuresButOneActualPaymentIsEnough() {
        var state = feedback(List.of(a, b)); state.record(2); state.settled(a, false);
        assertEquals(75, relation.getAffinity()); state.settled(b, true);
        assertEquals(75, relation.getAffinity());
        state = feedback(List.of(a, b)); state.record(2); state.settled(a, false); state.settled(b, false);
        assertEquals(73, relation.getAffinity());
    }
    @Test void saveIsReadOnlyAndReloadConsumesPendingActualAttackOnce() {
        relation.addBond(.95); var state = feedback(List.of(a)); state.record(1);
        var tag = relation.toNbt(); assertEquals(75, relation.getAffinity());
        var loaded = PlayerAffinity.fromNbt(tag); assertEquals(74, loaded.getAffinity()); assertEquals(.95, loaded.getBond());
        loaded = PlayerAffinity.fromNbt(loaded.toNbt()); assertEquals(74, loaded.getAffinity());
        state.settled(a, false); assertEquals(74, relation.getAffinity());
    }
    @Test void noActualAttackOrAlreadyPaidSaveCreatesNoNewPenalty() {
        var state = feedback(List.of(a)); state.settled(a, false);
        assertEquals(75, PlayerAffinity.fromNbt(relation.toNbt()).getAffinity());
        state = feedback(List.of(b)); state.record(1); state.settled(b, true);
        assertEquals(75, PlayerAffinity.fromNbt(relation.toNbt()).getAffinity());
    }
    @Test void registryDiscardAndClearSettleActualObligationAndLeaveNoDuplicate() {
        var sessions = new GroupMatingSessions(); var player = UUID.randomUUID(); var creature = UUID.randomUUID();
        sessions.recordOurStart(a, player, creature, true, 0);
        var state = feedback(List.of(a)); sessions.escapeChargeCandidates(player, creature).getFirst().trackEscapeFeedback(state);
        state.record(1); sessions.discard(a); sessions.discard(a); sessions.clear();
        assertEquals(74, relation.getAffinity());
        sessions.recordOurStart(b, player, creature, true, 1);
        state = feedback(List.of(b)); sessions.escapeChargeCandidates(player, creature).getFirst().trackEscapeFeedback(state);
        state.record(2); sessions.clear(); sessions.clear(); assertEquals(72, relation.getAffinity());
    }
    @Test void pendingPenaltyRespectsFractionAndLowerBound() {
        relation.setAffinity(-199, -200, 200); relation.addPreciseAffinity(.25, -200, 200);
        var state = feedback(List.of(a)); state.record(2);
        assertEquals(-200, PlayerAffinity.fromNbt(relation.toNbt()).getExactAffinity());
        state.settled(a, false); assertEquals(-200, relation.getExactAffinity());
    }
}
