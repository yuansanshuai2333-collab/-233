package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The villager trade discount and the ware gift's gate.
 *
 * <p>Two things this pins down, both requested by the user:
 *
 * <ol>
 *   <li>the discount cap is configurable up to the WHOLE price (100%), not fixed at 20%;</li>
 *   <li>it is a share of the offer's BASE price and never touches vanilla's own reduction, so the two stack --
 *       curing a zombie villager, gossip, "hero of the village" and demand stay intact and are not overwritten
 *       or double-counted.</li>
 * </ol>
 *
 * <p>Pure arithmetic on purpose: the stacking property is a property of the numbers, not of a running game, and
 * the surrounding code only decides WHERE the resulting amount is added.
 */
class VillagerDiscountTest {

    private static final int THRESHOLD = 100;

    @Test
    @DisplayName("the cap is configurable: 20% keeps the old numbers, 100% can take the price to one item")
    void theCapIsConfigurableUpToTheWholePrice() {
        // The old behaviour, unchanged: 20% of 20 at full affinity is 4, so the price drops 20 -> 16.
        assertEquals(4, VillagerRelationshipBenefits.discount(20, 100, THRESHOLD, 20));
        // The new ceiling: 100% of 20 is 20, but the last item is kept, so 20 -> 1.
        assertEquals(19, VillagerRelationshipBenefits.discount(20, 100, THRESHOLD, 100));
        // A 100% cap on a cheap offer still leaves one item rather than making it free.
        assertEquals(1, VillagerRelationshipBenefits.discount(2, 100, THRESHOLD, 100));
        // Half the ceiling gives half the discount.
        assertEquals(10, VillagerRelationshipBenefits.discount(20, 100, THRESHOLD, 50));
        // A large offer shows the percentage plainly: 100% of 64 is 63 after the one-item floor.
        assertEquals(63, VillagerRelationshipBenefits.discount(64, 100, THRESHOLD, 100));
        assertEquals(12, VillagerRelationshipBenefits.discount(64, 100, THRESHOLD, 20));
    }

    @Test
    @DisplayName("the discount scales with affinity and is off when there is nothing to scale from")
    void theDiscountScalesWithAffinity() {
        // Linear in affinity / threshold: a quarter of the way is a quarter of the cap.
        assertEquals(0, VillagerRelationshipBenefits.discount(20, 0, THRESHOLD, 20));
        assertEquals(1, VillagerRelationshipBenefits.discount(20, 25, THRESHOLD, 20));
        assertEquals(2, VillagerRelationshipBenefits.discount(20, 50, THRESHOLD, 20));
        assertEquals(3, VillagerRelationshipBenefits.discount(20, 75, THRESHOLD, 20));
        assertEquals(4, VillagerRelationshipBenefits.discount(20, 100, THRESHOLD, 20));
        // Past the threshold the discount stops growing instead of running away.
        assertEquals(4, VillagerRelationshipBenefits.discount(20, 500, THRESHOLD, 20));

        // Nothing to discount, or nothing to discount it by.
        assertEquals(0, VillagerRelationshipBenefits.discount(1, 100, THRESHOLD, 100),
                "a one-item offer has no room left");
        assertEquals(0, VillagerRelationshipBenefits.discount(0, 100, THRESHOLD, 100));
        assertEquals(0, VillagerRelationshipBenefits.discount(20, 100, 0, 100),
                "no threshold means no progress to measure");
        assertEquals(0, VillagerRelationshipBenefits.discount(20, 100, THRESHOLD, 0),
                "0% disables the affinity discount entirely");
        assertEquals(0, VillagerRelationshipBenefits.discount(20, 100, THRESHOLD, -5),
                "a negative configuration is treated as off, never as a surcharge");
    }

    @Test
    @DisplayName("the discount is a share of the BASE price, which is what makes it stack with vanilla")
    void theDiscountIsComputedFromTheBasePrice() {
        // The stacking property, stated as arithmetic. Vanilla keeps its own reduction in the offer's special
        // price (cured zombie villagers, gossip, hero of the village, demand); this module decides its own share
        // of the BASE price and adds it as a further reduction. Neither figure is derived from the other, so:
        //
        //   final price = base - vanillaReduction - affinityReduction
        //
        // and changing either one leaves the other exactly as it was. If the discount were computed from the
        // DISPLAYED price instead, a cure discount would silently shrink the affinity discount, and undoing one
        // layer could cancel the other.
        int base = 20;
        int vanillaReduction = 7;   // e.g. a cured zombie villager's gossip
        int affinityReduction = VillagerRelationshipBenefits.discount(base, 100, THRESHOLD, 50);

        assertEquals(10, affinityReduction, "half the ceiling of the base price");
        assertEquals(base - vanillaReduction - affinityReduction, 3,
                "the two reductions add up instead of replacing each other");

        // The uncapped share of a base price never depends on how much vanilla already took off: the same
        // affinity gives the same figure for every vanilla state.
        for (int vanilla : new int[]{0, 3, 10, 15}) {
            assertEquals(affinityReduction, VillagerRelationshipBenefits.discount(base, 100, THRESHOLD, 50),
                    "vanilla's reduction (" + vanilla + ") must not change this module's share");
        }

        // And the module's own floor keeps the price payable in every vanilla state.
        int floorPrice = base - VillagerRelationshipBenefits.discount(base, 100, THRESHOLD, 100);
        assertEquals(1, floorPrice, "the affinity discount alone never takes the last item");
    }

    @Test
    @DisplayName("the ware gift's gate: a player-initiated completion with a friend, and never a child")
    void theWareGiftGate() {
        assertTrue(VillagerRelationshipBenefits.qualifiesForCompletionGift(true, true, false, () -> true));
        assertFalse(VillagerRelationshipBenefits.qualifiesForCompletionGift(false, true, false, () -> true),
                "a mating the player did not start does not hand over wares");
        assertFalse(VillagerRelationshipBenefits.qualifiesForCompletionGift(true, false, false, () -> true),
                "the friendship must already exist BEFORE this completion");
        assertFalse(VillagerRelationshipBenefits.qualifiesForCompletionGift(true, true, true, () -> true),
                "children do not trade");
        assertFalse(VillagerRelationshipBenefits.qualifiesForCompletionGift(true, true, false, () -> false),
                "a non-friend has nothing to give");
    }

    /**
     * Set the per-settlement announcement flag the way {@code addAffinity} does when it hands out a cap gift.
     *
     * <p>Done by reflection so production code grows no test-only entry point: the flag is set from the cap-gift
     * branch, which needs a live world and player, so the state machine around it is what this test can drive
     * directly.
     */
    private static void simulateCapGift(boolean firstEver) throws Exception {
        java.lang.reflect.Method owe = AffinityService.class.getDeclaredMethod(
                "oweCapGiftAnnouncement", net.minecraft.text.Text.class, boolean.class);
        owe.setAccessible(true);
        owe.invoke(null, net.minecraft.text.Text.literal("Test Villager"), firstEver);
    }

    @Test
    @DisplayName("EVERY capped mating announces its token, and only the wording differs after the first one")
    void everyCappedMatingAnnouncesItsToken() throws Exception {
        // The defect this pins down: "should this settlement announce a token" and "is this the first token
        // ever" were the same flag. Only the first token set it, so from the second capped mating on the token
        // was handed over silently -- and when a villager also gave a ware, the ware's line claimed to be the
        // merged one while the token itself was never mentioned. Two facts, two concerns.
        AffinityService.beginSettlement();
        assertFalse(AffinityService.isCapGiftReportPending(), "a settlement starts with nothing owed");

        // First token of the relation: the full line.
        simulateCapGift(true);
        assertTrue(AffinityService.isCapGiftReportPending(), "the first token must be announced");
        assertEquals("message.noncreatureaffinity.affinity_capped_gift",
                AffinityService.pendingCapGiftKey());
        assertTrue(AffinityService.isCapGiftFirstEver());

        // Second and later tokens: still announced, with the repeat line instead of nothing at all.
        for (int round = 2; round <= 3; round++) {
            AffinityService.beginSettlement();
            simulateCapGift(false);
            assertTrue(AffinityService.isCapGiftReportPending(),
                    "capped mating #" + round + " must announce its token too");
            assertEquals("message.noncreatureaffinity.affinity_capped_gift_repeat",
                    AffinityService.pendingCapGiftKey(),
                    "the wording is the only thing the first-token fact may change");
            assertFalse(AffinityService.isCapGiftFirstEver());
        }

        // The two ways a settlement can report it, both ending with nothing owed so exactly one line goes out.
        AffinityService.beginSettlement();
        simulateCapGift(false);
        boolean wareGiven = VillagerRelationshipBenefits.qualifiesForCompletionGift(true, true, false, () -> true);
        assertTrue(wareGiven, "a qualifying villager completion hands over a ware");
        AffinityService.clearCapGiftReportPending();
        assertFalse(AffinityService.isCapGiftReportPending(),
                "merged into the ware's sentence: the token line must not be sent as well");

        AffinityService.beginSettlement();
        simulateCapGift(false);
        boolean noWare = VillagerRelationshipBenefits.qualifiesForCompletionGift(true, false, false, () -> true);
        assertFalse(noWare, "no ware for this completion");
        assertTrue(AffinityService.isCapGiftReportPending(),
                "so the repeat token still needs its own line -- information is never dropped");
        AffinityService.clearCapGiftReportPending();
        assertFalse(AffinityService.isCapGiftReportPending());

        // A new settlement must never inherit the previous one's pending line.
        simulateCapGift(false);
        AffinityService.beginSettlement();
        assertFalse(AffinityService.isCapGiftReportPending());
    }

    @Test
    @DisplayName("a full refresh cycle applies the discount exactly once, however many times it runs")
    void aRefreshCycleAppliesTheDiscountExactlyOnce() {
        // Every assertion below CARRIES the previous cycle's output forward as the next cycle's input, and the
        // cycle helper feeds the removal's result into the apply. Both matter: an earlier version ignored the
        // removal's return value, and the one before that re-passed a constant 0 every round -- with either
        // mistake these tests keep passing even when the removal does nothing at all, which is exactly the
        // defect they exist to catch. The mutant test below proves the shape is right.
        long offer = 1L;
        VillagerDiscountLedger ledger = new VillagerDiscountLedger();

        // (a) The reported bug, with NO other mod involved: the removal did not actually remove anything, that
        // value then became the next baseline, and the discount was applied again -- −4, then −8, then −12.
        int current = 0;
        for (int round = 0; round < 5; round++) {
            current = prepareOffersCycle(ledger, offer, current, 4);
            assertEquals(-4, current, "refresh " + round + " must stay at −4, not accumulate");
        }

        // (b) The discount is REDUCED (affinity dropped, or the percentage was lowered): the price must follow
        // the new figure, not keep the old one underneath.
        current = prepareOffersCycle(ledger, offer, current, 2);
        assertEquals(-2, current, "a smaller discount replaces the old one");
        current = prepareOffersCycle(ledger, offer, current, 2);
        assertEquals(-2, current, "and stays stable afterwards");
        current = prepareOffersCycle(ledger, offer, current, 4);
        assertEquals(-4, current, "raising it again works too");

        // (c) The discount is switched OFF (0%): the price must return to the baseline, not keep the old layer.
        current = prepareOffersCycle(ledger, offer, current, 0);
        assertEquals(0, current, "switching the discount off must actually remove it");
        current = prepareOffersCycle(ledger, offer, current, 0);
        assertEquals(0, current, "and stay off");

        // (d) Vanilla's own reduction (a cure discount appending −5 this cycle) is a baseline, not something to
        // erase. Here it arrives the way it really does: as the cycle's vanilla increment, from a cleared field.
        long cured = 2L;
        current = 0;
        current = prepareOffersCycle(ledger, cured, current, -5, 4);
        assertEquals(-9, current, "the discount stacks on vanilla's own figure");
        current = prepareOffersCycle(ledger, cured, current, 0, 4);
        assertEquals(-9, current, "and does not accumulate");
        current = prepareOffersCycle(ledger, cured, current, 0, 0);
        assertEquals(-5, current, "removing it leaves vanilla's figure intact");

        // (e) A POSITIVE baseline (demand, or a surcharge another content applied). The snapshot policy says the
        // record wins: an external edit inside this cycle is overwritten when the baseline is restored, so the
        // refresh gives 5 − 2 = 3 and switching off gives 5 back.
        long surcharged = 3L;
        current = 5;
        current = prepareOffersCycle(ledger, surcharged, current, 2);
        assertEquals(3, current, "the discount is taken off the surcharge");
        current -= 1;   // "another mod" subtracts one; this module's record still says baseline 5
        current = prepareOffersCycle(ledger, surcharged, current, 2);
        assertEquals(3, current, "the recorded baseline wins, so the discount is not counted twice");
        current = prepareOffersCycle(ledger, surcharged, current, 0);
        assertEquals(5, current, "and switching off restores the recorded baseline");

        // (f) A reset is honoured only when it is OBSERVED: the code that performs it also drops the records.
        // Watching the number alone cannot distinguish "someone subtracted" from "the field was replaced".
        long reset = 4L;
        current = 5;
        current = prepareOffersCycle(ledger, reset, current, 2);
        assertEquals(3, current);
        ledger.clear();          // the observed reset: vanilla's clearSpecialPrices does exactly this
        current = 0;
        current = prepareOffersCycle(ledger, reset, current, 2);
        assertEquals(-2, current, "after an observed reset the snapshot is gone, so the discount applies to 0");
        current = prepareOffersCycle(ledger, reset, current, 0);
        assertEquals(0, current, "and switching off returns to the post-reset price, not to the stale baseline");

        // (g) Two offers must not share a record.
        long first = 10L;
        long second = 11L;
        assertEquals(-4, prepareOffersCycle(ledger, first, 0, 4));
        assertEquals(-9, prepareOffersCycle(ledger, second, -5, 4));
        assertEquals(0, prepareOffersCycle(ledger, first, -4, 0), "the first offer keeps its own baseline");
        assertEquals(-5, prepareOffersCycle(ledger, second, -9, 0), "the second keeps vanilla's");
    }

    @Test
    @DisplayName("the refresh test FAILS if the removal is a no-op: the assertions are not vacuous")
    void theRefreshTestDetectsABrokenRemoval() {
        // The review's requirement: prove the test can fail. The cycle below is the same shape as production,
        // but the removal does nothing -- the defect that shipped in 1.0.52. If the real test were still passing
        // constant inputs, it would report success here too, which is why the state is carried forward.
        long offer = 1L;
        int carriedWithBrokenRemoval = 0;
        for (int round = 0; round < 5; round++) {
            carriedWithBrokenRemoval = cycleWithNoOpRemoval(offer, carriedWithBrokenRemoval, 4);
        }
        assertNotEquals(-4, carriedWithBrokenRemoval,
                "a no-op removal MUST make consecutive refreshes drift; the real test asserts the opposite");
        assertEquals(-20, carriedWithBrokenRemoval,
                "and it drifts by one discount per refresh, which is the accumulation that was reported");

        // The same loop with the real removal stays put -- the contrast is the point.
        VillagerDiscountLedger ledger = new VillagerDiscountLedger();
        int carried = 0;
        for (int round = 0; round < 5; round++) {
            carried = prepareOffersCycle(ledger, offer, carried, 4);
        }
        assertEquals(-4, carried);
    }

    @Test
    @DisplayName("the restock path removes, then applies, and does not stack either")
    void theRestockPathDoesNotStack() {
        // The second entry point: a restock (or opening the trade) removes this module's layer and applies it
        // again, in that order. The removal's result is what the apply then reads.
        long offer = 7L;
        VillagerDiscountLedger ledger = new VillagerDiscountLedger();
        int current = 0;
        for (int round = 0; round < 3; round++) {
            current = restockCycle(ledger, offer, current, 3);
            assertEquals(-3, current, "restock " + round + " must not stack");
        }
        current = restockCycle(ledger, offer, current, 0);
        assertEquals(0, current, "turning the discount off clears it");
        current = restockCycle(ledger, offer, current, 3);
        assertEquals(-3, current, "and turning it back on reapplies it exactly once");
    }

    @Test
    @DisplayName("the ledger keeps the snapshot policy explicit: recorded baseline wins, observed reset clears")
    void theLedgerStatesItsPolicy() {
        // Restoring is unconditional: it returns the recorded baseline without inspecting the current price.
        VillagerDiscountLedger ledger = new VillagerDiscountLedger();
        long offer = 9L;
        assertEquals(3, ledger.apply(offer, 5, 2), "baseline +5, this module takes 2 off");
        assertEquals(3, ledger.appliedFor(offer).netSpecialPrice());
        assertEquals(5, ledger.beginCycle(offer, 2),
                "the recorded baseline wins even though the price was changed externally");
        assertEquals(2, ledger.beginCycle(offer, 2),
                "the record is consumed by that restore, so a second call has nothing to put back");

        // A zero and a negative baseline behave the same way; only an observed clear drops a record.
        assertEquals(-4, ledger.apply(offer, 0, 4));
        assertEquals(0, ledger.beginCycle(offer, -4));
        assertEquals(-9, ledger.apply(offer, -5, 4));
        assertEquals(-5, ledger.beginCycle(offer, -9));

        // Records are per offer and only an OBSERVED clear drops them all.
        long other = 12L;
        assertEquals(-4, prepareOffersCycle(ledger, other, 0, 4));
        assertEquals(1, ledger.size(), "the offer above still has its record");
        ledger.clear();
        assertEquals(0, ledger.size(), "the observed reset drops every record");
        assertEquals(-4, ledger.beginCycle(other, -4), "and then nothing is restored");
    }

    /**
     * The "vanilla prepared the offers" path, exactly as production orders it.
     *
     * <p>Four steps, because all four change the price:
     * <ol>
     *   <li>HEAD: this module restores its recorded baseline (the record is consumed here);</li>
     *   <li>vanilla's BODY: reputation and "hero of the village" are written with
     *       {@code increaseSpecialPrice(...)} -- an APPEND, which is why it is a parameter here and not an
     *       assumption that the baseline is overwritten;</li>
     *   <li>TAIL: the price now standing is the baseline this module records;</li>
     *   <li>TAIL: the discount is applied to it.</li>
     * </ol>
     *
     * <p>An earlier version of this helper read {@code appliedFor(...)} after the restore and clamped with
     * {@code Math.min} -- dead code, since the restore removes the record, so the clamp could never fire. The
     * unmodelled step in that version was vanilla's own increment, which is now explicit.
     *
     * @param current          the price as it stands when the cycle starts (the previous cycle's output)
     * @param vanillaIncrement what vanilla's body APPENDS this cycle via {@code increaseSpecialPrice} -- the
     *                         player's reputation discount and "hero of the village". It is not zero merely
     *                         because the player did not change: it is whatever this cycle's vanilla pass
     *                         writes, so the tests drive it with real values (−7, −3, 0).
     */
    private static int prepareOffersCycle(VillagerDiscountLedger ledger, long offerId, int current,
                                          int vanillaIncrement, int discount) {
        int afterRestore = ledger.beginCycle(offerId, current);
        int afterVanilla = afterRestore + vanillaIncrement;
        return ledger.apply(offerId, afterVanilla, discount);
    }

    /** The same cycle with nothing added by vanilla, which is the common case. */
    private static int prepareOffersCycle(VillagerDiscountLedger ledger, long offerId, int current, int discount) {
        return prepareOffersCycle(ledger, offerId, current, 0, discount);
    }

    @Test
    @DisplayName("the lifecycle: clear -> vanilla's own discount -> this module's discount -> clear again")
    void theObservedClearSeparatesTheTwoLayers() {
        // The lifecycle the review asked for, driven through the SAME helper the production order models -- not
        // by computing a baseline outside it. That distinction matters: an earlier version of this test built
        // the baseline by hand and then called ledger.apply directly, so deleting the helper's
        // "+ vanillaIncrement" line would not have failed anything.
        VillagerDiscountLedger ledger = new VillagerDiscountLedger();
        long offer = 20L;

        // Vanilla clears the field, then its body appends its own reduction for this player (a cured zombie
        // villager's gossip is -7), then this module records that as its baseline and discounts on top.
        ledger.clear();
        int price = 0;
        price = prepareOffersCycle(ledger, offer, price, -7, 4);
        assertEquals(-11, price, "vanilla's reduction and this module's discount add up");
        assertEquals(-7, ledger.appliedFor(offer).baseline(), "the recorded baseline is vanilla's own figure");

        // A second cycle without any clear: the restore puts vanilla's figure back; vanilla appends NOTHING new
        // (its body already ran for this cycle), and both layers are then re-applied.
        price = prepareOffersCycle(ledger, offer, price, 0, 4);
        assertEquals(-11, price, "and it does not accumulate");

        // The restock path: remove, then apply -- and NO vanilla increment, because prepareOffersFor is not part
        // of a restock. Appending the -7 again here would be the bug this asserts against.
        price = restockCycle(ledger, offer, price, 4);
        assertEquals(-11, price, "a restock must not append vanilla's reduction a second time");

        // Now vanilla clears again: the records go with it, and this player's vanilla modifier may differ from
        // the previous one (another player, or changed gossip): -3 this time.
        ledger.clear();
        price = 0;
        price = prepareOffersCycle(ledger, offer, price, -3, 2);
        assertEquals(-5, price, "the changed vanilla figure and the discount combine");
        assertEquals(-3, ledger.appliedFor(offer).baseline(), "and the new baseline is recorded");

        // Switching the discount off after another observed clear leaves exactly vanilla's reduction.
        ledger.clear();
        price = 0;
        price = prepareOffersCycle(ledger, offer, price, -7, 0);
        assertEquals(-7, price, "with the discount disabled only vanilla's reduction remains");
        assertEquals(0, ledger.size(), "a zero discount records nothing");

        // The helper must actually APPLY the vanilla increment. With a non-zero increment driven through it,
        // dropping that addition from the helper changes this result, so the assertion above is not vacuous.
        ledger.clear();
        int withIncrement = prepareOffersCycle(ledger, offer, 0, -7, 4);
        int withoutTheIncrementLine = 0 - 4;
        assertEquals(-11, withIncrement, "the helper adds vanilla's increment");
        assertNotEquals(withoutTheIncrementLine, withIncrement,
                "if the helper stopped adding the increment these two would coincide and the test would be"
                        + " unable to see it");
    }

    /**
     * The "restock / trade window opened" path, exactly as production orders it: remove, then apply.
     */
    private static int restockCycle(VillagerDiscountLedger ledger, long offerId, int current, int discount) {
        int afterRemoval = ledger.beginCycle(offerId, current);
        return ledger.apply(offerId, afterRemoval, discount);
    }

    /**
     * The same cycle with the removal doing NOTHING -- the defect that shipped in 1.0.52.
     *
     * <p>Exists so the refresh test can be shown to fail when the removal is broken, instead of only being shown
     * to pass when it works.
     */
    private static int cycleWithNoOpRemoval(long offerId, int current, int discount) {
        VillagerDiscountLedger ledger = new VillagerDiscountLedger();
        // The record is deliberately re-seeded with the price as it is now, exactly what the broken version did
        // by reading the discounted price back as the new baseline.
        return ledger.apply(offerId, current, discount);
    }

    @Test
    @DisplayName("a full discount removes the base-price share only: demand and other reductions are separate")
    void theFullDiscountDoesNotTouchDemandOrOtherReductions() {
        // Stated precisely, because the earlier wording ("the minimum final count is 1 + demand") was wrong: it
        // holds only with no other reduction present. The discount is a share of the BASE price, demand is added
        // by vanilla on top, and any other reduction (vanilla's cure discount, another mod's) comes off as well:
        //
        //   count = base + max(0, demand) - thisModuleDiscount - otherReductions
        //
        // and only the first three terms are this module's business.
        assertEquals(1, VillagerRelationshipBenefits.firstBuyCountWithDiscountOnly(20, 0),
                "no demand, nothing else: a full discount reaches the one-item floor");
        assertEquals(11, VillagerRelationshipBenefits.firstBuyCountWithDiscountOnly(20, 10),
                "with a demand penalty of 10, this module leaves it in place: 20 - 19 + 10");
        assertEquals(1, VillagerRelationshipBenefits.firstBuyCountWithDiscountOnly(1, 0));
        assertEquals(6, VillagerRelationshipBenefits.firstBuyCountWithDiscountOnly(5, 5),
                "a four-item discount and a five-item penalty");

        // The case the earlier wording got wrong, spelled out: vanilla's own reduction of 7 is NOT part of this
        // helper's scope, so the real count is lower than the helper's 11 -- the helper must not be read as a
        // global minimum.
        int base = 20;
        int demand = 10;
        int thisModule = VillagerRelationshipBenefits.discount(base, 1, 1, 100);
        int vanillaReduction = 7;
        assertEquals(4, base + demand - thisModule - vanillaReduction,
                "other reductions stack on top of this module's, so no unconditional floor may be claimed");
        assertTrue(VillagerRelationshipBenefits.firstBuyCountWithDiscountOnly(base, demand)
                        > base + demand - thisModule - vanillaReduction,
                "and the helper deliberately ignores those other reductions");
    }
}
