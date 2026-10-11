package com.nonid.affinity.service;

/**
 * Whether a displayed trade may still be TAKEN, decided before the goods move.
 *
 * <p><b>Why this has to happen before the move.</b> Vanilla extracts first and reports afterwards: an ordinary
 * click puts the output into the cursor and only then calls {@code TradeOutputSlot.onTakeItem}; a shift-click puts
 * it into the inventory and then reaches the same method. By that point the item is already the player's, so a
 * check made there can only decide whether to charge. The decision therefore belongs at the two points vanilla
 * consults before extracting:
 *
 * <ul>
 *   <li>{@code Slot.canTakeItems(player)} -- called by the click handler before either extraction path;</li>
 *   <li>{@code MerchantScreenHandler.quickMove(player, slot)} -- called once per iteration of the shift-click
 *       loop, which is the only place a batch can be stopped part-way.</li>
 * </ul>
 *
 * <p><b>What counts as "the player can pay".</b> NOT "the payment slots are not empty": materials in a slot do not
 * mean the trade is payable. The wrong item, too few of the right one, or a missing second material all fail the
 * normal trade, and in each case vanilla would extract the displayed item and then fail to take anything for it.
 * The caller therefore passes the result of vanilla's OWN non-consuming match -- {@code TradeOffer.matchesBuyItems},
 * tried in both slot orders, exactly as the completion path tries {@code depleteBuyItems} -- rather than a guess
 * based on emptiness.
 *
 * <p>The rule itself is separated here so it can be checked without a screen.
 */
public final class FreeTradeGuard {

    private FreeTradeGuard() {
    }

    /** Why a take was refused, for the log and for the tests. */
    public enum Verdict {
        /** Take it: the trade is free right now. */
        ALLOW_FREE,
        /** Take it: this is an ordinary paid trade and vanilla can complete it. */
        ALLOW_PAID,
        /**
         * Refuse, and refresh the output: what is displayed can no longer be delivered.
         *
         * <p>Three different situations share this verdict because they share the same action -- refresh, then hand
         * nothing over -- and the caller has nothing different to do about any of them:
         *
         * <ul>
         *   <li>the displayed pair is inconsistent (an item with no recipe recorded behind it);</li>
         *   <li>the offer can no longer be delivered (out of stock, recipe gone, not this player's customer);</li>
         *   <li>the quote has lapsed and the materials do not pay for it either.</li>
         * </ul>
         *
         * <p>Only the "and both extraction paths must ask" part is new for the first case: the two paths did not
         * previously consult the same rule about it.
         */
        REFUSE_STALE
    }

    /**
     * The rule stated as one branch table, so both extraction paths use identical logic.
     *
     * <p><b>{@code consistent} comes first on purpose.</b> A caller reaches this having already seen an item in the
     * output slot, so "there is no recipe recorded" is not "there is nothing to take": it is a window whose item and
     * recipe disagree, and such a window must hand nothing over. Vanilla never produces that pair -- it fills the
     * slot and records the recipe together, as does this mod's own mixin -- so it is an anomaly guard rather than a
     * normal path.
     *
     * <p><b>Why it is a separate parameter at all.</b> The click path can ask the question directly, but the
     * shift-click loop has its own guard and had been reading an absent recipe as "nothing to refuse" and letting the
     * loop continue. Stating the rule once means the two cannot drift apart again.
     *
     * @param consistent  a recipe is recorded for the item being displayed
     * @param available   the offer is still sellable to THIS player right now: still in the villager's list, in
     *                    stock, and this player is still its customer
     * @param quoteFree   the offer is free-eligible right now (close friend + allowance + price reached zero)
     * @param canPayNow   vanilla's own non-consuming payment match succeeds right now, in one of the two slot orders
     *                    the completion path accepts
     */
    public static Verdict verdict(boolean consistent, boolean available, boolean quoteFree, boolean canPayNow) {
        if (!consistent) {
            // An item with no recipe behind it. Nothing downstream can be trusted about it, so nothing is delivered.
            return Verdict.REFUSE_STALE;
        }
        return verdict(available, quoteFree, canPayNow);
    }

    /**
     * Decide one take.
     *
     * @param available   the offer is still sellable to THIS player right now: the recipe is still in the
     *                    villager's list, it is not out of stock, and the player is still its customer. Checked
     *                    again here because generation time is not extraction time.
     * @param quoteFree   the offer is free-eligible right now (close friend + allowance + price reached zero)
     * @param canPayNow   vanilla's own non-consuming payment match succeeds right now, in one of the two slot
     *                    orders the completion path accepts
     */
    public static Verdict verdict(boolean available, boolean quoteFree, boolean canPayNow) {
        if (!available) {
            // Out of stock, no longer a recipe, or not this player's trade any more. Nothing may be delivered, free
            // or paid -- and a stale display is exactly what this catches.
            //
            // <b>The empty shelf arrives through THIS guard, and only through it.</b> "Available" is the result of
            // VillagerFreeTrades.tradeAvailable, which is false for a disabled offer, and an offer is disabled when
            // its uses are exhausted. So a sold-out recipe is refused here whatever the player is holding -- which is
            // the required behaviour -- and the caller must not try to re-establish stock separately: a second stock
            // check would be a second source of truth for the same fact, and the two could disagree.
            return Verdict.REFUSE_STALE;
        }
        if (quoteFree) {
            return Verdict.ALLOW_FREE;
        }
        if (canPayNow) {
            return Verdict.ALLOW_PAID;
        }
        // Displayed as free, no longer free, and the materials do not actually pay for it either: vanilla would
        // extract the item and then fail to deplete anything. Refuse while the item is still in the slot.
        return Verdict.REFUSE_STALE;
    }
}
