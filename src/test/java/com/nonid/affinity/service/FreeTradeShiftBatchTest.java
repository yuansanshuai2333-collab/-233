package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shift-click boundary: a batch that already spent a free trade must not roll into paid purchases.
 *
 * <p>The behaviour, and why it needs a decision rather than a default: with materials in the inventory, vanilla's
 * matching and auto-fill keep producing an output after the allowance runs out, so one keypress would deliver three
 * free trades and then silently buy more at the normal price. That is not an extra free trade -- the counts stay
 * correct -- but it is not what the player asked for either. The rule here is that such a batch stops, and the
 * player presses again to buy deliberately.
 */
class FreeTradeShiftBatchTest {

    @AfterEach
    void clear() {
        FreeTradeShiftBatch.end(true);
    }

    @Test
    @DisplayName("a CLIENT click must not clear a server batch that is still running")
    void clientClickCannotDisturbTheServerBatch() {
        // Single-player: client and integrated server share one process, so these flags are shared too. The client
        // answers a click locally before the server does; if that reset the state, the server batch still in
        // progress would lose the "already spent a free trade" fact and could roll into paid purchases.
        FreeTradeShiftBatch.beginForTest(true);
        FreeTradeShiftBatch.begin(false);
        assertTrue(FreeTradeShiftBatch.mustStopPaidContinuation(false),
                "a client-side begin must be a no-op while the server batch is running");
        FreeTradeShiftBatch.end(false);
        assertTrue(FreeTradeShiftBatch.mustStopPaidContinuation(false),
                "a client-side end must be a no-op too");
    }

    @Test
    @DisplayName("a SERVER click does start and end the batch")
    void serverClickDrivesTheBatch() {
        FreeTradeShiftBatch.begin(true);
        assertFalse(FreeTradeShiftBatch.mustStopPaidContinuation(false),
                "a fresh batch has spent nothing yet");
        FreeTradeShiftBatch.noteFreeTradeSpent();
        assertTrue(FreeTradeShiftBatch.mustStopPaidContinuation(false));
        FreeTradeShiftBatch.end(true);
        assertFalse(FreeTradeShiftBatch.mustStopPaidContinuation(false), "the batch is over");
    }

    @Test
    @DisplayName("a batch that spent a free trade stops once free trading is over")
    void batchThatSpentAFreeTradeStopsRatherThanCharging() {
        FreeTradeShiftBatch.beginForTest(true);
        assertTrue(FreeTradeShiftBatch.mustStopPaidContinuation(false),
                "the allowance ran out mid-batch: stop instead of charging for more");
        assertFalse(FreeTradeShiftBatch.mustStopPaidContinuation(true),
                "while it is still free there is nothing to stop");
    }

    @Test
    @DisplayName("a fresh click with no allowance left still trades at the normal price")
    void aFreshPaidClickIsNotAffected() {
        FreeTradeShiftBatch.beginForTest(false);
        assertFalse(FreeTradeShiftBatch.mustStopPaidContinuation(false),
                "a deliberate purchase must keep working exactly as before");
    }

    @Test
    @DisplayName("the state never survives the click that created it")
    void stateIsScopedToOneClick() {
        FreeTradeShiftBatch.beginForTest(true);
        FreeTradeShiftBatch.end(true);
        assertFalse(FreeTradeShiftBatch.mustStopPaidContinuation(false),
                "outside a click there is no batch, so nothing is suppressed");
        FreeTradeShiftBatch.begin(true);
        assertFalse(FreeTradeShiftBatch.mustStopPaidContinuation(false),
                "a new click starts from a clean state, even if the previous one ended badly");
    }
}
