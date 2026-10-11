package com.nonid.affinity.service;

/**
 * The one thing the trade window is allowed to show, derived from the server's own judgement.
 *
 * <p>It exists so the client never re-derives anything: the free flag, the remaining count and the reason all come
 * from the server's quote. The regression this closes is "the server recorded a free trade while the screen still
 * showed a price" -- the screen was showing vanilla's number because nothing told it otherwise.
 *
 * <p>The two cases the display must keep apart:
 *
 * <ul>
 *   <li><b>Not this player's customer</b> (the window belongs to somebody else, or the villager dropped this
 *       customer): {@link Reason#TRADE_UNAVAILABLE}. The free state is cleared and the window must NOT fall back to
 *       a normal price, which would read as "still purchasable".</li>
 *   <li><b>Eligibility lapsed but the trade is fine</b> (no longer a close friend, allowance gone, price not
 *       zero): a normal price WITH the reason, so the player understands why the free offer disappeared.</li>
 * </ul>
 *
 * <p>Pure data: no world, no screen, no networking.
 *
 * @param firstItemName what a paid trade charges first, already translated to the viewer's language, so a statement
 *                      about the price can name the item and not just a bare number. A count on its own is not a
 *                      price: "20" beside a trade that may charge 20 of two different things tells the player
 *                      nothing. Empty when this is the waiting state and no offer has been named.
 */
public record FreeTradeStatus(
        int windowId,
        int offerIndex,
        boolean free,
        int remaining,
        int total,
        int paidFirstCount,
        String firstItemName,
        Reason reason,
        long quoteRevision) {

    /** Why this offer is not free, in the words the window needs. */
    public enum Reason {
        /** Free right now: both payment materials are waived. */
        FREE,
        /** The player is not a close friend of this villager. */
        NOT_CLOSE_FRIEND,
        /** The allowance is used up; the next trade pays normally. */
        NO_ALLOWANCE_LEFT,
        /** No qualifying refresh event has ever granted this pair an allowance. */
        NO_ALLOWANCE_GRANTED,
        /** The price did not reach zero (demand, or a smaller discount), so it is an ordinary trade. */
        PRICE_NOT_FREE,
        /** Out of stock: the recipe exists and is still on offer, but it has been traded out. */
        OUT_OF_STOCK,
        /**
         * The trade cannot be offered at all: the recipe is gone, the villager is traded out and cannot restock, the
         * window belongs to somebody else, or nothing at all is selected.
         */
        TRADE_UNAVAILABLE,
        /**
         * <b>No verdict has been computed yet.</b> The window has just been opened, or a recipe has just been
         * selected, and the answer is still being worked out.
         *
         * <p>This is deliberately NOT {@link #TRADE_UNAVAILABLE}. The two used to share a value, and that is exactly
         * how "not ready yet" reached the player as "the trade is unavailable": every test of the unavailable state
         * -- {@link #showAsUnavailable()}, and the client's branch for what to say -- answered yes for a window that
         * merely had no answer yet. A separate reason makes the two impossible to confuse by construction, not by
         * remembering to check the quote number first.
         *
         * <p>Never describes a finished decision: nothing that has been judged is reported with this reason.
         */
        PENDING
    }

    public FreeTradeStatus {
        if (firstItemName == null) {
            firstItemName = "";
        }
    }

    /** Whether the window may be shown as free. */
    public boolean showAsFree() {
        return this.free && this.reason == Reason.FREE;
    }

    /** Whether the window must say "this trade cannot be made" rather than show a price. */
    public boolean showAsUnavailable() {
        return this.reason == Reason.TRADE_UNAVAILABLE;
    }

    /**
     * Whether no verdict has been computed for this offer yet.
     *
     * <p>The window shows its "still coming" line for this, and must not show a price or a refusal. A waiting status
     * answers false to {@link #showAsUnavailable()} by construction, which is the point of the separate reason.
     */
    public boolean showAsPending() {
        return this.reason == Reason.PENDING;
    }

    /** Whether the recipe is simply out of stock, and will be sellable again after a restock. */
    public boolean showAsOutOfStock() {
        return this.reason == Reason.OUT_OF_STOCK;
    }

    /**
     * The status for a window that is still being built, or the real verdict once it is ready.
     *
     * <p><b>What this decides, and why it is not the same question as "is this free".</b> A merchant window is not
     * initialised in one step: the handler is constructed with a default recipe index, the villager's offer list and
     * its record of who the customer is arrive separately, and the selected offer is only resolved on the first
     * content change. Until all of that has happened there is <em>nothing to judge</em> -- no recipe list to index
     * into, no selection, or a villager that has not been told who is trading with it. Running the normal decision
     * over that state produces a refusal for every one of those shapes, and the window then tells a player who has
     * just opened a perfectly good trade and touched nothing that the trade is unavailable.
     *
     * <p>So readiness is checked FIRST and it overrides everything: while the window is still coming up the answer is
     * {@link #updating}, never a price and never a refusal. Once it is ready, the real decision runs exactly as
     * before -- a genuine out-of-stock shelf, a lapsed close-friend status, another player's customer, and a recipe
     * that has gone are all still reported as the refusals they are. Nothing here forces a trade to be free in order
     * to make the display look better.
     *
     * <p>Kept as a separate entry point, taking plain values, so the rule can be exercised without a game: the
     * decision that failed in practice was precisely "which of these two things do I say right now".
     *
     * @param ready              whether the window has finished coming up (offer list built, selection resolved,
     *                           customer set). When false nothing else is looked at
     * @param currentCustomer    whether this player really is the villager's customer, judged from the game
     * @param unavailableReason  what to say when the quote refuses on availability: out of stock, or unavailable
     */
    public static FreeTradeStatus decide(int windowId, int offerIndex, boolean ready, boolean currentCustomer,
                                         FreeTradeQuote quote, int remaining, int total, String firstItemName,
                                         Reason unavailableReason) {
        if (!ready) {
            return updating(windowId, offerIndex);
        }
        return of(windowId, offerIndex, quote, remaining, total, currentCustomer, firstItemName,
                quote.quotaRevision(), unavailableReason);
    }

    /** Build the display state from the server's quote and the current allowance. */
    public static FreeTradeStatus of(int windowId, int offerIndex, FreeTradeQuote quote,
                                     int remaining, int total, boolean currentCustomer, String firstItemName) {
        return of(windowId, offerIndex, quote, remaining, total, currentCustomer, firstItemName,
                quote.quotaRevision(), Reason.TRADE_UNAVAILABLE);
    }

    /**
     * The same, with the window's OWN quote number and the reason to use when the quote itself cannot say what
     * happened.
     *
     * <p>The two numbers answer different questions and must not be conflated. The allowance revision moves only
     * when the allowance moves; a price, a stock level, an affinity or the trade's availability can all change
     * without it, and the window still has to be told. The caller therefore advances a per-window quote number every
     * time the answer changes at all, and that number is what the client compares.
     *
     * <p>{@code unavailableReason} exists because {@link FreeTradeQuote} answers one question -- "is this free" --
     * with a single refusal value, and "out of stock" is not among its answers: for an empty shelf the quote reports
     * exactly what it reports for a missing recipe, {@code TRADE_NOT_AVAILABLE}. Out of stock is a shelf state, not a
     * pricing one, so the caller that can already tell the two apart supplies the distinction here instead of this
     * class guessing it. Only {@code OUT_OF_STOCK} and {@code TRADE_UNAVAILABLE} are accepted; anything else falls
     * back to {@code TRADE_UNAVAILABLE}, because a caller must not use this hook to invent a pricing reason.
     */
    public static FreeTradeStatus of(int windowId, int offerIndex, FreeTradeQuote quote,
                                     int remaining, int total, boolean currentCustomer, String firstItemName,
                                     long windowQuoteNumber, Reason unavailableReason) {
        Reason fallback = unavailableReason == Reason.OUT_OF_STOCK ? Reason.OUT_OF_STOCK
                : Reason.TRADE_UNAVAILABLE;
        // Customer identity is checked FIRST and overrides the quote: a quote computed for another player (or for
        // a window that is no longer ours) must not be displayed as an offer of any kind.
        Reason reason = currentCustomer ? reasonOf(quote, fallback) : Reason.TRADE_UNAVAILABLE;
        boolean free = currentCustomer && quote.free() && reason == Reason.FREE;
        return new FreeTradeStatus(windowId, offerIndex, free, Math.max(0, remaining), Math.max(0, total),
                quote.paidFirstCount(), firstItemName, reason, Math.max(0L, windowQuoteNumber));
    }

    public FreeTradeStatus withAllowanceHistory(boolean everGranted) {
        if (everGranted || reason != Reason.NO_ALLOWANCE_LEFT) return this;
        return new FreeTradeStatus(windowId, offerIndex, free, remaining, total, paidFirstCount,
                firstItemName, Reason.NO_ALLOWANCE_GRANTED, quoteRevision);
    }

    private static Reason reasonOf(FreeTradeQuote quote, Reason unavailableReason) {
        if (quote.free()) {
            return Reason.FREE;
        }
        FreeTradeQuota.Ineligible refusal = quote.refusal();
        if (refusal == null) {
            // A quote that is not free must carry a reason; treating it as "unavailable" is the safe display.
            return unavailableReason;
        }
        return switch (refusal) {
            case NOT_CLOSE_FRIEND -> Reason.NOT_CLOSE_FRIEND;
            case NO_ALLOWANCE_LEFT -> Reason.NO_ALLOWANCE_LEFT;
            case PRICE_NOT_FREE -> Reason.PRICE_NOT_FREE;
            // The quote cannot tell a traded-out shelf from a missing recipe; the caller can, and passed the
            // distinction in.
            case TRADE_NOT_AVAILABLE -> unavailableReason;
        };
    }

    /**
     * The waiting state: a new quote has been requested but has not arrived.
     *
     * <p>The reason is {@link Reason#PENDING}, which is what makes this state impossible to mistake for a refusal:
     * the window asks {@link #showAsUnavailable()} and gets false, so a window that has only just been opened can
     * never be described as "trade unavailable". The zero quote number is the client's second, independent signal
     * that nothing has been sent for this offer yet.
     */
    public static FreeTradeStatus updating(int windowId, int offerIndex) {
        return new FreeTradeStatus(windowId, offerIndex, false, 0, 0, 0, "", Reason.PENDING, 0L);
    }
}
