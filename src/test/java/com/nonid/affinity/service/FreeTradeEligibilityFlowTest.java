package com.nonid.affinity.service;

import com.nonid.affinity.data.PlayerAffinity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FreeTradeEligibilityFlowTest {
    @Test void friendshipAloneNeverGrantsAndOnlyQualifyingDeliveriesSpend() {
        PlayerAffinity relation = new PlayerAffinity(100);
        relation.setBefriended(true);
        var beforeHero = FreeTradeAccounts.synchronizedState(relation, 0).state();
        assertEquals(0, beforeHero.remaining());
        var neverGranted = FreeTradeStatus.of(1, 0,
                FreeTradeQuote.quote(true, 0, 0, 1, 0, 0, -1, 64, true),
                0, 3, true, "Emerald").withAllowanceHistory(false);
        assertEquals(FreeTradeStatus.Reason.NO_ALLOWANCE_GRANTED, neverGranted.reason());

        var state = FreeTradeAccounts.synchronizedState(relation, 1).state();
        assertEquals(3, state.remaining());
        assertFalse(FreeTradeQuote.quote(true, state.remaining(), state.quotaRevision(),
                10, 0, 0, -2, 64, true).free(), "friendship discount alone is still paid");
        assertFalse(FreeTradeQuote.quote(false, state.remaining(), state.quotaRevision(),
                10, 0, 0, -10, 64, true).free(), "demotion suspends courtesy");
        assertEquals(3, FreeTradeAccounts.stateOf(relation).remaining());
        for (int i = 0; i < 3; i++) {
            assertTrue(FreeTradeQuote.quote(true, state.remaining(), state.quotaRevision(),
                    10, 0, 0, -10, 64, true).free());
            state = FreeTradeQuota.spend(state);
        }
        FreeTradeAccounts.store(relation, state);
        assertFalse(FreeTradeQuote.quote(true, state.remaining(), state.quotaRevision(),
                10, 0, 0, -10, 64, true).free());
        PlayerAffinity reloaded = PlayerAffinity.fromNbt(relation.toNbt());
        assertEquals(0, FreeTradeAccounts.synchronizedState(reloaded, 1).state().remaining());
        assertEquals(3, FreeTradeAccounts.synchronizedState(reloaded, 2).state().remaining());
    }
}
