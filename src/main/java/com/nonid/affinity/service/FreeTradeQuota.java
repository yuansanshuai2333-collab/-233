package com.nonid.affinity.service;

import java.util.Objects;

/**
 * The "close friend courtesy" allowance: how many trades a player may take for free from one particular villager.
 *
 * <p>Pure logic on purpose. The rules are all about WHICH event refreshes the allowance and which must not, and
 * that is exactly the part a running game makes hardest to check -- so the whole decision lives here, with no
 * world, no entity and no item involved.
 *
 * <p><b>Where the state lives.</b> The per-villager part is stored on the player's relation entry inside the
 * villager's own affinity data. That matters for the cure path: a villager -&gt; zombie villager -&gt; villager
 * conversion creates an entity, and the relation (and therefore the remaining allowance) has to travel with the
 * individual rather than being keyed on a UUID nobody guarantees. The per-player part (the hero epoch) is shared
 * by every villager, because the hero event is an event about the player.
 *
 * <p><b>The two refresh events.</b>
 * <ol>
 *   <li>A hero-of-the-village epoch the relation has not applied yet. The event is per player, so it is NOT
 *       applied while walking the world: the player's epoch is bumped when the effect is newly acquired, and each
 *       relation applies it lazily the next time it quotes. Villagers that are not loaded, and villagers the
 *       player meets later, are therefore handled correctly.</li>
 *   <li>A successful cure performed by this player on THIS villager.</li>
 * </ol>
 *
 * <p>Both set the allowance to the full amount, never add to it: a refresh is "three from now", not "+3".
 * Repeating the same notification does nothing, because each event carries an epoch and the relation remembers
 * the last one it applied. Two different events arriving while nothing was spent leave the allowance at three.
 *
 * <p><b>What does not refresh it.</b> Extending or upgrading an active hero effect, restocking, switching
 * recipes, reopening the screen, relogging, restarting, and dropping out of close-friend status and returning to
 * it. The last one also does not RESET the remainder: losing eligibility only suspends the allowance, so the
 * player resumes with what was left.
 */
public final class FreeTradeQuota {

    /** How many free trades one refresh grants. */
    public static final int FULL_ALLOWANCE = 3;

    /** Why an allowance refresh was applied, or why it was refused. */
    public enum RefreshSource {
        /** This player newly acquired the hero-of-the-village effect, at a new epoch. */
        HERO_EPOCH,
        /** This player successfully cured this very villager. */
        CURE,
        /** The notification repeats an event this relation already applied. */
        ALREADY_APPLIED,
        /** The event belongs to somebody else (another player, or another villager). */
        NOT_THIS_PAIR
    }

    /**
     * The persisted state of one pair, plus the shared player epoch.
     *
     * <p>{@code remaining} is clamped to 0..3 on read, so a hand-edited or future-version save cannot produce a
     * larger allowance.
     *
     * <p>{@code quotaRevision} changes whenever the ALLOWANCE changes -- a refresh, or a consumption. It is the
     * number a cached quote can be checked against, because it is exactly what decides whether the next trade is
     * still free: an old quote carrying a revision that no longer matches must not be served for free.
     * {@code offerRevision} is a separate counter reserved for the trade layer's own offer versioning, which is
     * about the PRICES a screen is showing and does not move when a free trade is spent. Keeping them apart is
     * deliberate: conflating the two would make one mechanism responsible for two different questions.
     */
    public record State(int remaining, long lastHeroEpoch, long lastCureEpoch, long quotaRevision,
                        long offerRevision, int totalFreeTrades, long totalCompletions) {

        /**
         * State for a relation that has never seen an event: nothing granted, both markers at zero.
         *
         * <p><b>Used for a NEW relation AND for a relation migrated from a pre-feature save.</b> Those are the same
         * thing, and that is the point: the enable boundary IS zero. The hero epoch counter lives on the player and
         * starts at zero when the feature is installed, so an epoch above zero can only come from an acquisition
         * this module actually observed. Baselining a migration at anything else would consume an event:
         *
         * <p>The failure that rule prevents, stated concretely. A save is upgraded while the player has NO hero
         * effect; the player then really acquires one (epoch 1); only afterwards do they open an old, not-yet-loaded
         * villager. If the migration had taken "the epoch at the first quote" (1) as its baseline, that genuine new
         * event would be recorded as already handled and would never pay out. With a baseline of 0 it is new and
         * grants.
         *
         * <p>The opposite mistake is also covered, and it is why the baseline is zero rather than "now": an effect
         * the player was ALREADY enjoying at install time cannot fabricate an allowance either, because the counter
         * only moves on a NEW acquisition -- an active effect that predates the feature never bumped it, so it is
         * still zero and nothing is granted.
         *
         * <p>The markers therefore stay at zero so that the first real event -- including one that happened before
         * this relation was ever loaded -- is recognised as new.
         */
        public static State forNewRelation() {
            return new State(0, 0L, 0L, 0L, 0L, 0, 0);
        }

        /** Clamp anything read from disk into the shape the rules allow. */
        public State sanitized() {
            return new State(clampRemaining(this.remaining), Math.max(0L, this.lastHeroEpoch),
                    Math.max(0L, this.lastCureEpoch), Math.max(0L, this.quotaRevision),
                    Math.max(0L, this.offerRevision), Math.max(0, this.totalFreeTrades),
                    Math.max(0L, this.totalCompletions));
        }

        private static int clampRemaining(int value) {
            return Math.max(0, Math.min(FULL_ALLOWANCE, value));
        }
    }

    private FreeTradeQuota() {
    }

    // ------------------------------------------------------------------
    // Refresh events
    // ------------------------------------------------------------------

    /**
     * Apply BOTH observed event epochs in one step. This is the only refresh entry point callers should use.
     *
     * <p><b>Why one entry point and not two.</b> Applying the hero and cure events separately let a stale grant be
     * banked: if hero epoch 1 was already true but had not been synchronised yet, applying cure 1 granted three,
     * the player spent two, and the later synchronisation of hero 1 granted three AGAIN -- so an old event paid
     * out twice. The sequences that follow from processing one marker at a time are the bug, not any single event.
     *
     * <p>So both markers advance together, and the allowance is granted at most once per call:
     * <ul>
     *   <li>neither observed value is newer than its marker -&gt; nothing happens (a repeated or stale
     *       notification, including an extension of an active effect which does not move the epoch);</li>
     *   <li>either observed value is newer -&gt; the allowance is SET to three exactly once and BOTH markers move
     *       up to the observed values. The second event therefore cannot grant a second time, and a hero event
     *       that really happens AFTER the spending is still new and does grant.</li>
     * </ul>
     *
     * <p>Call it on every quote and again before validating a completed trade, so a cure applied immediately can
     * carry the hero epoch that was already true at that moment instead of leaving it to be "discovered" later.
     *
     * @param heroEpoch the player's current hero-of-the-village epoch (0 when the feature has never seen one)
     * @param cureEpoch the cure epoch observed for THIS pair (0 when none)
     */
    public static Result applyObservedEvents(State state, long heroEpoch, long cureEpoch) {
        State safe = state.sanitized();
        long observedHero = Math.max(0L, heroEpoch);
        long observedCure = Math.max(0L, cureEpoch);

        boolean heroIsNew = observedHero > safe.lastHeroEpoch();
        boolean cureIsNew = observedCure > safe.lastCureEpoch();
        if (!heroIsNew && !cureIsNew) {
            return new Result(safe, RefreshSource.ALREADY_APPLIED, false, false);
        }
        State granted = new State(FULL_ALLOWANCE, Math.max(safe.lastHeroEpoch(), observedHero),
                Math.max(safe.lastCureEpoch(), observedCure), safe.quotaRevision() + 1,
                safe.offerRevision(), safe.totalFreeTrades(), safe.totalCompletions());
        return new Result(granted, heroIsNew ? RefreshSource.HERO_EPOCH : RefreshSource.CURE, true,
                heroIsNew && cureIsNew);
    }

    /** One refresh attempt's outcome. */
    public record Result(State state, RefreshSource source, boolean changed, boolean bothEventsAtOnce) {
        public Result {
            Objects.requireNonNull(state, "state");
        }
    }

    // ------------------------------------------------------------------
    // Eligibility and spending
    // ------------------------------------------------------------------

    /** Why a trade may or may not be free, for logging and for the UI's explanation. */
    public enum Ineligible {
        /** The player is not a close friend of this villager. */
        NOT_CLOSE_FRIEND,
        /** No allowance left; the trade is offered at the normal price. */
        NO_ALLOWANCE_LEFT,
        /**
         * The price did not reach zero, so this trade is simply not a candidate. The allowance is untouched: a
         * demanded trade that still costs something must not consume a free trade.
         */
        PRICE_NOT_FREE,
        /** Another condition of the game (stock, recipe, screen state) refuses the trade. */
        TRADE_NOT_AVAILABLE
    }

    /**
     * Whether a trade may be taken for free right now.
     *
     * @param closeFriend    the player is a close friend, judged by the current affinity (see the class notes)
     * @param remaining      the persisted allowance
     * @param effectiveFirst the first payment count AFTER every discount, BEFORE the vanilla floor of one
     * @param tradeAvailable whether the trade itself is offerable: a matching recipe exists, stock remains, the
     *                       target is valid and the screen state is usable.
     *                       <b>It must NOT mean "the input slots currently hold enough materials"</b> -- a free
     *                       trade is precisely the case where they do not, so reusing vanilla's
     *                       "materials sufficient" boolean here would reject every free trade before it began.
     */
    public static boolean isFreeEligible(boolean closeFriend, int remaining, int effectiveFirst,
                                         boolean tradeAvailable) {
        return refusalReason(closeFriend, remaining, effectiveFirst, tradeAvailable) == null;
    }

    /** The reason a trade is not free, or null when it is. */
    public static Ineligible refusalReason(boolean closeFriend, int remaining, int effectiveFirst,
                                           boolean tradeAvailable) {
        if (!closeFriend) {
            return Ineligible.NOT_CLOSE_FRIEND;
        }
        if (remaining <= 0) {
            return Ineligible.NO_ALLOWANCE_LEFT;
        }
        if (!tradeAvailable) {
            return Ineligible.TRADE_NOT_AVAILABLE;
        }
        // The rule is "the price reached zero", not "the configured discount is 100%": a configured 50% that
        // still takes a demanded trade to zero qualifies, and a configured 100% that does not reach zero (because
        // of a demand penalty) does not.
        if (effectiveFirst > 0) {
            return Ineligible.PRICE_NOT_FREE;
        }
        return null;
    }

    /**
     * Spend one free trade, after the game has actually delivered the goods.
     *
     * <p>Called only on a successful completion, so a refused or failed delivery cannot consume the allowance.
     * Never takes the count below zero.
     *
     * <p><b>The revision moves here too.</b> Consuming changes the allowance, and the allowance is what decides
     * whether the NEXT trade is free -- so a cached quote holding the previous revision must be treated as stale.
     * Advancing the revision only on refreshes left the number unchanged across a purchase, which is precisely the
     * state a stale quote would have been validated against. A spend that changes nothing (nothing left) does not
     * move it, because then nothing changed.
     */
    public static State spend(State state) {
        State safe = state.sanitized();
        if (safe.remaining() <= 0) {
            return safe;
        }
        return new State(safe.remaining() - 1, safe.lastHeroEpoch(), safe.lastCureEpoch(),
                safe.quotaRevision() + 1, safe.offerRevision(), safe.totalFreeTrades() + 1,
                safe.totalCompletions() + 1);
    }

    /**
     * Record a successful completion that was NOT free, so the running totals stay meaningful.
     *
     * <p>Deliberately does not touch either revision: nothing about the allowance or the prices changed, and
     * inflating a revision here would invalidate cached quotes for no reason. Whether a price-affecting event
     * should move {@code offerRevision} is the trade layer's decision, not this state machine's.
     */
    public static State recordPaidCompletion(State state) {
        State safe = state.sanitized();
        return new State(safe.remaining(), safe.lastHeroEpoch(), safe.lastCureEpoch(), safe.quotaRevision(),
                safe.offerRevision(), safe.totalFreeTrades(), safe.totalCompletions() + 1);
    }

    /**
     * Mark the prices a screen is showing as changed, for the trade layer's own quote checks.
     *
     * <p>Separate from {@link #spend(State)} on purpose: a restock, a reputation change or a hero effect ending can
     * invalidate a displayed price without the allowance moving at all, and a consumed free trade can move the
     * allowance without any price changing.
     */
    public static State invalidateOffers(State state) {
        State safe = state.sanitized();
        return new State(safe.remaining(), safe.lastHeroEpoch(), safe.lastCureEpoch(), safe.quotaRevision(),
                safe.offerRevision() + 1, safe.totalFreeTrades(), safe.totalCompletions());
    }

    // ------------------------------------------------------------------
    // The effective price
    // ------------------------------------------------------------------

    /**
     * The first payment count a trade would need after every discount, before vanilla clamps it to at least one.
     *
     * <p>Computed from the offer's own inputs, mirroring vanilla's arithmetic EXACTLY rather than taking a
     * pre-chewed number: the demand part is {@code max(0, floor(baseCount * rawDemand * priceMultiplier))}, where
     * {@code rawDemand} is {@code TradeOffer.getDemandBonus()} -- a RAW demand value that must not be added to the
     * price directly. Doing so is an easy and invisible mistake: with a base of 20, raw demand 2 and a price
     * multiplier of 0.05 the real addition is {@code floor(20 * 2 * 0.05) = 2}, while a multiplier of 0.2 gives
     * {@code floor(20 * 2 * 0.2) = 8}. The same raw 2 produces two different prices, so the multiplier belongs in
     * the calculation.
     *
     * <p>The result can be zero or negative, and those are exactly the cases the allowance may serve; vanilla
     * clamps to 1 afterwards, which is what this deliberately does not do.
     *
     * <p>The same float-and-floor shape as vanilla is kept on purpose: an integer rewrite would disagree with the
     * screen at the boundary.
     */
    public static int effectiveFirstPayment(int baseCount, int rawDemand, float priceMultiplier,
                                            int specialPrice) {
        int demandCost = Math.max(0, (int) Math.floor(baseCount * rawDemand * priceMultiplier));
        return baseCount + demandCost + specialPrice;
    }

    /**
     * The demand addition alone, for callers that need to show or log it separately.
     *
     * <p>Named {@code demandCost} and not {@code demandBonus} because it is a COUNT OF ITEMS to pay, not the raw
     * demand value the offer stores.
     */
    public static int demandCost(int baseCount, int rawDemand, float priceMultiplier) {
        return Math.max(0, (int) Math.floor(baseCount * rawDemand * priceMultiplier));
    }
}
