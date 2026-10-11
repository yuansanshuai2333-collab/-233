package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nonid.affinity.service.FreeTradeGuard.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The decision that has to be made BEFORE a villager trade's goods move.
 *
 * <p>The defects this pins, in order of how they were found:
 *
 * <ol>
 *   <li>a check at the completion hook is too late -- vanilla has already put the item in the cursor (click) or
 *       the inventory (shift-click), so an invalidated free quote handed the goods over for nothing;</li>
 *   <li>"the payment slots are not empty" is NOT "the player can pay": a wrong item, too few of the right one or a
 *       missing second material all leave a slot occupied and the trade unpayable, and the same leak follows;</li>
 *   <li>"available" cannot be taken on trust from generation time: the recipe list, the stock and the customer can
 *       all change before the extraction.</li>
 * </ol>
 */
class FreeTradeGuardTest {

    @Test
    @DisplayName("a stale free display with unpayable materials is refused, however full the slots look")
    void staleFreeDisplayWithWrongOrInsufficientMaterialsIsRefused() {
        // Displayed as free, no longer free, and vanilla's own match does not succeed: this is the wrong-item /
        // too-few / missing-second-material case. Non-empty slots must not make it pass.
        assertEquals(Verdict.REFUSE_STALE, FreeTradeGuard.verdict(true, false, false));
    }

    @Test
    @DisplayName("a stale display is refused even when the player could pay")
    void aStaleOfferIsRefusedRatherThanCharged() {
        // The offer is no longer sellable at all (out of stock, recipe replaced, another customer): nothing may be
        // delivered, so the player is not charged for a display that is simply wrong.
        assertEquals(Verdict.REFUSE_STALE, FreeTradeGuard.verdict(false, false, true));
        assertEquals(Verdict.REFUSE_STALE, FreeTradeGuard.verdict(false, true, false));
    }

    @Test
    @DisplayName("a live free quote is allowed for free")
    void liveFreeQuoteIsAllowed() {
        assertEquals(Verdict.ALLOW_FREE, FreeTradeGuard.verdict(true, true, false));
        assertEquals(Verdict.ALLOW_FREE, FreeTradeGuard.verdict(true, true, true));
    }

    @Test
    @DisplayName("an ordinary payable trade is allowed, and is charged by vanilla")
    void payableTradeIsAllowedToBeCharged() {
        assertEquals(Verdict.ALLOW_PAID, FreeTradeGuard.verdict(true, false, true));
    }
}
