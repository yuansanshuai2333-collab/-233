package com.nonid.affinity.service;

/**
 * One offer's price/eligibility decision, computed ONCE and then used by everything that needs it.
 *
 * <p>Why an object instead of a predicate: the screen and the server-side completion have to agree. Computing
 * "is this free" from the numbers in two places is how a screen ends up showing a price the completion does not
 * charge, or vice versa. So the arithmetic happens here, and the trade layer only reads fields.
 *
 * <p><b>The number this module acts on is {@link #effectiveFirst}, not the displayed one.</b> Vanilla clamps the
 * first payment count to {@code 1..item max}, which is checked and used elsewhere; the clamp is what makes
 * "zero price" unrepresentable in the vanilla field. {@code effectiveFirst} is the same expression with the clamp
 * removed, so a value of zero or less is the honest statement "the discounts reached zero", and that is the
 * condition for a free trade.
 *
 * <p>{@link #vanillaFirstCount} is kept beside it because that is what the offer actually shows and what a paid
 * trade charges -- when the allowance is gone, this module changes nothing and that number is the whole story.
 */
public record FreeTradeQuote(
        boolean free,
        FreeTradeQuota.Ineligible refusal,
        int baseCount,
        int demandCost,
        int specialPrice,
        int effectiveFirst,
        int vanillaFirstCount,
        int allowanceLeft,
        long quotaRevision) {

    /**
     * Compute the one quote for one offer and one player.
     *
     * @param closeFriend     the player is a close friend of this villager, judged from the CURRENT affinity
     * @param allowanceLeft   persisted free trades left for this pair
     * @param quotaRevision   persisted revision, so a cached quote can be recognised as stale
     * @param baseCount       the offer's own base price ({@code getOriginalFirstBuyItem().getCount()})
     * @param rawDemand       the offer's RAW demand value ({@code getDemandBonus()}), which is a multiplier input
     *                        and not an item count
     * @param priceMultiplier the offer's price multiplier
     * @param specialPrice    the special price AFTER vanilla's reductions and this module's discount
     * @param itemMaxCount    the payment item's max stack size, for the vanilla clamp
     * @param tradeAvailable  the recipe exists, stock remains, the target and screen state are usable. It must NOT
     *                        mean "the input slots hold enough materials": a free trade is exactly the case where
     *                        they do not, which is why this is passed in separately by the caller.
     */
    public static FreeTradeQuote quote(boolean closeFriend, int allowanceLeft, long quotaRevision,
                                       int baseCount, int rawDemand, float priceMultiplier, int specialPrice,
                                       int itemMaxCount, boolean tradeAvailable) {
        int demand = FreeTradeQuota.demandCost(baseCount, rawDemand, priceMultiplier);
        int effective = FreeTradeQuota.effectiveFirstPayment(baseCount, rawDemand, priceMultiplier, specialPrice);
        int vanilla = clamp(effective, 1, Math.max(1, itemMaxCount));
        FreeTradeQuota.Ineligible refusal =
                FreeTradeQuota.refusalReason(closeFriend, allowanceLeft, effective, tradeAvailable);
        return new FreeTradeQuote(refusal == null, refusal, baseCount, demand, specialPrice, effective, vanilla,
                Math.max(0, allowanceLeft), Math.max(0L, quotaRevision));
    }

    /** Whether the free branch is what the player is being offered. */
    public boolean freeEligible() {
        return this.free;
    }

    /**
     * The first payment count to charge for a NON-free trade: the vanilla number, unchanged.
     *
     * <p>Stated explicitly so the paid path cannot accidentally charge {@link #effectiveFirst} (which may be zero
     * or negative) or skip the clamp.
     */
    public int paidFirstCount() {
        return this.vanillaFirstCount;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
