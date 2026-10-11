package com.nonid.affinity.service;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps track of the discount this module put into a villager offer's special price, so one refresh cycle can
 * remove exactly what the previous one applied.
 *
 * <p><b>Why a ledger instead of inspecting the price.</b> Two attempts to infer the state from the price itself
 * both failed, and the reasons are worth keeping:
 *
 * <ul>
 *   <li>"add the discount back" overcharges once somebody else reset the price;</li>
 *   <li>"only add it back while the number is unchanged" and "clamp the write with {@code min}" both FAILED TO
 *       REMOVE the discount at all -- the price kept its discounted value, that value was then read as the next
 *       baseline, and the discount was applied again on top. Two consecutive refreshes produced −8, three
 *       produced −12, with no other mod involved.</li>
 * </ul>
 *
 * <p>So the contribution is recorded explicitly, together with the baseline it was computed from, and removal
 * restores that baseline rather than guessing. The consequence is stated plainly: a reset performed by somebody
 * else inside the same window is undone when this ledger restores its baseline, and this module's discount is
 * re-applied on the next refresh. Neither of the two failure directions above can happen.
 *
 * <p>The baseline is only ever captured from a price that vanilla has just recomputed -- at that point the field
 * holds vanilla's own figure and nothing of this module's, because the removal at the start of the cycle put it
 * back. That is what makes the recorded baseline trustworthy.
 *
 * <p>Pure Java: the whole refresh cycle is a unit test, which is exactly what the two failed attempts lacked.
 */
public final class VillagerDiscountLedger {

    /** What this module currently has applied to one offer. */
    public record Applied(int baseline, int netSpecialPrice) {
    }

    private final Map<Long, Applied> applied = new HashMap<>();

    /**
     * Take this module's discount back out of {@code current}, returning the price to write.
     *
     * <p>Restores the recorded baseline EXACTLY, with no inspection of the current value. An earlier version
     * added a "the baseline is positive and the price moved, so skip the restore" branch; that reintroduced the
     * very defect this ledger exists to remove -- the discounted value stayed in place, and it was then read as
     * the next baseline, so the discount was counted twice (baseline +5, discount 2, external −1: the refresh
     * wrote 0 instead of 3, and disabling it gave 2 instead of 5).
     *
     * <p>The reason no amount of inspecting the number can help: seeing {@code 2} after writing {@code 3} is
     * consistent BOTH with "someone subtracted 1" and with "the whole field was replaced by 2", and those two
     * require different removals. A single observed value cannot decide it.
     *
     * <p>So the policy is chosen and stated rather than guessed at: <b>the recorded baseline wins</b>. External
     * edits made inside this pricing cycle are overwritten when this module restores its baseline, and a reset
     * is honoured only when it is OBSERVED -- i.e. when the code that performs it also drops the records
     * ({@link #clear()}, which the {@code clearSpecialPrices} hook calls).
     *
     * <p>When there is no record the price is returned unchanged: this module has nothing applied, so it has
     * nothing to undo.
     */
    public int beginCycle(long offerId, int current) {
        Applied record = this.applied.remove(offerId);
        return record == null ? current : record.baseline();
    }

    /**
     * Record the discount applied for this cycle.
     *
     * @param offerId  identifies the offer
     * @param baseline the price vanilla left in the field this cycle
     * @param discount the amount taken off (a positive number)
     * @return the price to write
     */
    public int apply(long offerId, int baseline, int discount) {
        if (discount <= 0) {
            // Nothing applied, so nothing may be recorded: a record here would make a later removal "restore" a
            // price this module never changed.
            this.applied.remove(offerId);
            return baseline;
        }
        int net = baseline - discount;
        this.applied.put(offerId, new Applied(baseline, net));
        return net;
    }

    /**
     * Forget everything, for when vanilla itself reset the price field.
     *
     * <p>Called from the {@code clearSpecialPrices} hook: after that call there is no contribution of this
     * module left in any price, so a record would only make the next removal write a stale baseline.
     */
    public void clear() {
        this.applied.clear();
    }

    /** What is recorded for one offer, or null. Diagnostics and tests. */
    public Applied appliedFor(long offerId) {
        return this.applied.get(offerId);
    }

    /** How many offers currently carry a record. */
    public int size() {
        return this.applied.size();
    }
}
