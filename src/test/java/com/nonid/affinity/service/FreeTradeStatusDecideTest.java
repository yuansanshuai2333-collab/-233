package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the window is told when it has just been opened and the player has touched nothing.
 *
 * <p>This exists because of a defect reported from play: opening a qualifying trade window and <em>not</em> clicking
 * anything showed "trade unavailable" instead of the free offer. The cause was that a merchant window is not
 * initialised in one step -- the handler starts with a default recipe index, the villager's offer list and its
 * customer arrive separately, and the selected offer is only resolved on the first content change -- and the normal
 * decision was being run over that half-built state. Every shape of "not ready" then reads as a refusal.
 *
 * <p>{@link FreeTradeStatus#decide} is the one place that separates the two, so it is driven directly here. The
 * three cases the reviewer asked to re-check are all the same rule seen from different openings:
 *
 * <ul>
 *   <li><b>first open</b> -- nothing is ready yet;</li>
 *   <li><b>close and reopen</b> -- the same, on a window that has been built once already (a new window is a new
 *       opening, and the villager has to be told its customer again);</li>
 *   <li><b>first open after a cure</b> -- the allowance changed underneath, but the window is still coming up, so the
 *       answer is still "still coming" rather than a verdict computed from a half-built window.</li>
 * </ul>
 *
 * <p>What is deliberately also asserted: once the window IS ready, every genuine refusal survives unchanged. The fix
 * must not buy a nicer display by weakening the decision.
 */
class FreeTradeStatusDecideTest {

    private static final int WINDOW = 7;

    /**
     * A quote for a close friend with allowance left and a price that reached zero: the free case.
     *
     * <p>The base count is zero so the effective first payment really is zero. A base of one leaves one item to pay
     * however large the discount is, which is the {@code PRICE_NOT_FREE} case and not this one -- asserting "free"
     * against that quote was a mistake in the test, not in the code.
     */
    private static FreeTradeQuote freeQuote() {
        return FreeTradeQuote.quote(true, FreeTradeQuota.FULL_ALLOWANCE, 4L, 0, 0, 0.0F, 0, 64, true);
    }

    /** A quote refused because the price did not reach zero. */
    private static FreeTradeQuote pricedQuote() {
        return FreeTradeQuote.quote(true, FreeTradeQuota.FULL_ALLOWANCE, 4L, 5, 0, 0.0F, 0, 64, true);
    }

    /** A quote refused because the trade itself cannot be offered (out of stock, gone, wrong customer). */
    private static FreeTradeQuote unavailableQuote() {
        return FreeTradeQuote.quote(true, FreeTradeQuota.FULL_ALLOWANCE, 4L, 1, 0, 0.0F, 0, 64, false);
    }

    private static FreeTradeStatus decide(boolean ready, boolean currentCustomer, FreeTradeQuote quote,
                                          FreeTradeStatus.Reason unavailableReason) {
        return FreeTradeStatus.decide(WINDOW, 0, ready, currentCustomer, quote, quote.allowanceLeft(),
                FreeTradeQuota.FULL_ALLOWANCE, "Emerald", unavailableReason);
    }

    @Test
    @DisplayName("first open: a window that is still coming up is never described as unavailable")
    void firstOpenSaysWaitingNotUnavailable() {
        // The reported defect. Nothing about this window is ready, and the shape a real one takes here is exactly the
        // unavailable shape: no customer yet, and a quote computed against a trade that cannot be offered.
        FreeTradeStatus status = decide(false, false, unavailableQuote(),
                FreeTradeStatus.Reason.TRADE_UNAVAILABLE);

        assertFalse(status.showAsUnavailable(),
                "a window that has just been opened must not tell the player the trade is unavailable");
        assertFalse(status.showAsOutOfStock());
        assertFalse(status.showAsFree(), "and it must not claim the trade is free before it knows either");
        assertTrue(status.showAsPending(), "it must say the quote is still coming");
        assertEquals(FreeTradeStatus.Reason.PENDING, status.reason(),
                "the pending reason is its own value, so no test of 'unavailable' can answer yes for it");
        assertEquals(0L, status.quoteRevision(), "zero means no answer has been sent for this offer yet");
    }

    @Test
    @DisplayName("close and reopen: the new opening is still coming up, however well built the old one was")
    void reopenedWindowSaysWaitingAgain() {
        // A reopened window is a new opening: the villager is told its customer again and the selection is resolved
        // again. The previous window's answer must not be inherited by the new one.
        FreeTradeStatus status = decide(false, false, freeQuote(), FreeTradeStatus.Reason.TRADE_UNAVAILABLE);

        assertFalse(status.showAsFree(), "the reopened window must not inherit the free verdict of the old one");
        assertEquals(0L, status.quoteRevision());
    }

    @Test
    @DisplayName("first open after a cure: the changed allowance does not turn a half-built window into a verdict")
    void firstOpenAfterACureStillSaysWaiting() {
        // A cure refreshes this pair's allowance. That does not make a window that is still being built judgeable:
        // the answer is still "still coming", and the refreshed allowance shows up in the real quote that follows.
        FreeTradeQuote refreshed = FreeTradeQuote.quote(true, FreeTradeQuota.FULL_ALLOWANCE, 9L, 0, 0, 0.0F, 0, 64,
                true);
        FreeTradeStatus status = decide(false, false, refreshed, FreeTradeStatus.Reason.TRADE_UNAVAILABLE);

        assertFalse(status.showAsUnavailable());
        assertFalse(status.showAsFree());
        assertEquals(0L, status.quoteRevision());
    }

    @Test
    @DisplayName("once the window is ready the free offer is reported normally")
    void readyWindowReportsTheFreeOffer() {
        FreeTradeStatus status = decide(true, true, freeQuote(), FreeTradeStatus.Reason.TRADE_UNAVAILABLE);

        assertTrue(status.showAsFree(), "a ready window with a free quote must say free");
        assertEquals(FreeTradeQuota.FULL_ALLOWANCE, status.remaining());
        assertEquals(4L, status.quoteRevision(), "a real verdict carries the allowance revision, not zero");
    }

    @Test
    @DisplayName("a ready window keeps every genuine refusal: price, stock and customer are not softened")
    void readyWindowKeepsGenuineRefusals() {
        // The fix must not buy a nicer display by weakening the decision.
        FreeTradeStatus priced = decide(true, true, pricedQuote(), FreeTradeStatus.Reason.TRADE_UNAVAILABLE);
        assertEquals(FreeTradeStatus.Reason.PRICE_NOT_FREE, priced.reason());
        assertFalse(priced.showAsFree());
        assertFalse(priced.showAsUnavailable(), "a trade that merely costs money is an ordinary trade");

        FreeTradeStatus outOfStock = decide(true, true, unavailableQuote(), FreeTradeStatus.Reason.OUT_OF_STOCK);
        assertTrue(outOfStock.showAsOutOfStock());
        assertFalse(outOfStock.showAsFree());

        FreeTradeStatus gone = decide(true, true, unavailableQuote(), FreeTradeStatus.Reason.TRADE_UNAVAILABLE);
        assertTrue(gone.showAsUnavailable(), "a trade that cannot be offered at all is still unavailable");

        FreeTradeStatus someoneElse = decide(true, false, freeQuote(), FreeTradeStatus.Reason.TRADE_UNAVAILABLE);
        assertTrue(someoneElse.showAsUnavailable(),
                "another player's customer is not a free trade for this one, however free the quote looks");
        assertFalse(someoneElse.showAsFree());
    }

    @Test
    @DisplayName("readiness overrides the quote entirely: a ready-check never reports free from a stale quote")
    void readinessOverridesEverything() {
        // Even with a quote that looks perfectly free, a window that is not ready gets the waiting answer. That order
        // is the whole fix: the quoted numbers cannot be trusted until the window they describe exists.
        FreeTradeStatus notReady = decide(false, true, freeQuote(), FreeTradeStatus.Reason.TRADE_UNAVAILABLE);

        assertFalse(notReady.showAsFree());
        assertEquals(0L, notReady.quoteRevision());
    }
}
