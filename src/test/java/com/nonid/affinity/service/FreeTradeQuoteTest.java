package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The single quote used by both the screen and the server-side completion.
 *
 * <p>The cases here are the acceptance table's price rows, plus the boundary the reviewer asked for
 * specifically: three free trades, and the FOURTH must fall back to the normal price instead of being free
 * again or being refused outright.
 */
class FreeTradeQuoteTest {

    /** 20 of an item, no demand, price multiplier 1, item stack 64: the shape used by the acceptance rows. */
    private static FreeTradeQuote quote(boolean friend, int allowance, int specialPrice) {
        return FreeTradeQuote.quote(friend, allowance, 0L, 20, 0, 1.0F, specialPrice, 64, true);
    }

    @Test
    @DisplayName("a non-friend is refused even when the price has reached zero")
    void nonFriendIsNeverFree() {
        FreeTradeQuote q = quote(false, 3, -25);
        assertFalse(q.free());
        assertEquals(FreeTradeQuota.Ineligible.NOT_CLOSE_FRIEND, q.refusal());
        assertTrue(q.effectiveFirst() <= 0, "the price really did reach zero; the refusal is about the relation");
    }

    @Test
    @DisplayName("a paid count of 1 (vanilla floor), 0 and -1: only the latter two are free")
    void onlyAZeroOrNegativeEffectiveCountIsFree() {
        FreeTradeQuote stillCosts = quote(true, 3, -19);
        assertEquals(1, stillCosts.effectiveFirst(), "base 20 with -19 is one item");
        assertFalse(stillCosts.free());
        assertEquals(FreeTradeQuota.Ineligible.PRICE_NOT_FREE, stillCosts.refusal());
        assertEquals(1, stillCosts.paidFirstCount());

        FreeTradeQuote exactlyZero = quote(true, 3, -20);
        assertEquals(0, exactlyZero.effectiveFirst());
        assertTrue(exactlyZero.free(), "the price reached zero, so the allowance may serve it");

        FreeTradeQuote negative = quote(true, 3, -25);
        assertTrue(negative.free());
        assertEquals(1, negative.vanillaFirstCount(), "vanilla would clamp it, which is why we act on our own count");
    }

    @Test
    @DisplayName("a demand penalty that keeps the price above zero is paid, and costs no allowance")
    void demandCanPriceATradeOutOfTheFreeBranch() {
        // base 20, raw demand 1, multiplier 1.0 -> demand cost 20, so even special price -25 is 15 to pay.
        FreeTradeQuote q = FreeTradeQuote.quote(true, 3, 0L, 20, 1, 1.0F, -25, 64, true);
        assertEquals(20, q.demandCost());
        assertEquals(15, q.effectiveFirst());
        assertFalse(q.free());
        assertEquals(FreeTradeQuota.Ineligible.PRICE_NOT_FREE, q.refusal());
        assertEquals(3, q.allowanceLeft(), "the allowance is untouched by a trade it cannot serve");
    }

    @Test
    @DisplayName("boundary: the fourth trade is a normal trade, not free and not refused")
    void theFourthTradeFallsBackToTheNormalPrice() {
        // Allowance 1: the third trade of the three.
        FreeTradeQuote third = quote(true, 1, -20);
        assertTrue(third.free());
        assertEquals(1, third.allowanceLeft());

        // It completes: the allowance is spent.
        FreeTradeQuota.State spent = FreeTradeQuota.spend(
                new FreeTradeQuota.State(1, 0L, 0L, 0L, 0L, 2, 0));
        assertEquals(0, spent.remaining());

        // The fourth: the SAME offer, the same player, the same discounts -- and it costs the normal price.
        FreeTradeQuote fourth = quote(true, spent.remaining(), -20);
        assertFalse(fourth.free());
        assertEquals(FreeTradeQuota.Ineligible.NO_ALLOWANCE_LEFT, fourth.refusal());
        assertEquals(1, fourth.paidFirstCount(),
                "the vanilla floor of one is what a fourth trade costs: normal price, never 'no trade'");

        // And the revision moved, so a quote cached before the third trade is recognisably stale.
        assertTrue(spent.quotaRevision() > 0L);
    }

    @Test
    @DisplayName("a trade the game itself refuses is not free, and does not consume anything")
    void unavailableTradeIsRefusedBeforeThePriceIsConsidered() {
        FreeTradeQuote q = FreeTradeQuote.quote(true, 3, 0L, 20, 0, 1.0F, -25, 64, false);
        assertFalse(q.free());
        assertEquals(FreeTradeQuota.Ineligible.TRADE_NOT_AVAILABLE, q.refusal());
        assertEquals(3, q.allowanceLeft());
    }

    @Test
    @DisplayName("the quoted numbers are the vanilla shape: base + demand + special price")
    void theArithmeticMatchesVanish() {
        // base 20, raw demand 2, multiplier 0.05 -> floor(20 * 2 * 0.05) = 2, NOT 2 items of raw demand added.
        FreeTradeQuote q = FreeTradeQuote.quote(true, 3, 0L, 20, 2, 0.05F, -5, 64, true);
        assertEquals(2, q.demandCost());
        assertEquals(17, q.effectiveFirst());
        assertFalse(q.free());
        assertEquals(17, q.paidFirstCount());

        // The same raw demand with a larger multiplier is a different price: this is the mistake the shape avoids.
        FreeTradeQuote bigger = FreeTradeQuote.quote(true, 3, 0L, 20, 2, 0.2F, -5, 64, true);
        assertEquals(8, bigger.demandCost());
        assertEquals(23, bigger.effectiveFirst());
    }

    @Test
    @DisplayName("an item capped below the paid count is clamped in the paid number only")
    void theVanillaClampIsReportedSeparately() {
        FreeTradeQuote q = FreeTradeQuote.quote(true, 0, 0L, 20, 0, 1.0F, 0, 4, true);
        assertEquals(20, q.effectiveFirst());
        assertEquals(4, q.vanillaFirstCount(), "vanilla clamps to the item's max stack");
        assertFalse(q.free());
    }
}
