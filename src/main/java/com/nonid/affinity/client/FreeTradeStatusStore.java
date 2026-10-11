package com.nonid.affinity.client;

import com.nonid.affinity.service.FreeTradeStatus;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * The client's one current trade window, and the server's word on what it should show.
 *
 * <p><b>The rule that shapes this class: a status is only believed once the client has been told which window it
 * belongs to, by an answer to the question it actually asked.</b> Vanilla reuses small {@code syncId} values, so
 * a number alone cannot tell two openings apart, and the same villager reopened is a different window with the
 * same id. Two things therefore have to match before anything is shown:
 *
 * <ol>
 *   <li>the SERVER's instance token for this opening, because it is the only value that cannot repeat; and</li>
 *   <li>the client's own request id, echoed back, because the token identifies the window but not the question --
 *       after a rapid close-and-reopen the server's current window need not be the one this screen is waiting
 *       for, and adopting its identity would show one window's free state on another.</li>
 * </ol>
 *
 * <p>A status also carries the window's own quote number, and only a HIGHER number replaces what is shown. A
 * connection can deliver packets out of order, and without that rule an older answer arriving late would overwrite
 * a newer one -- the exact shape of "the server recorded a free trade while the screen showed a price". An equal
 * number is the same answer again and changes nothing, so a repeat is neither redrawn nor re-announced.
 *
 * <p>State is deliberately per client, not per screen object: the screen is recreated by the game, the store is
 * not, and the store is what survives the round trip between the screen closing and the packet that started it.
 *
 * <p>No Minecraft types beyond a UUID: this is testable without a game.
 */
public final class FreeTradeStatusStore {

    private FreeTradeStatusStore() {
    }

    /** The server-issued identity of the window the client currently has open. */
    private static int windowId = -1;
    private static UUID villagerUuid;
    private static long instanceToken;
    private static boolean bound;

    /**
     * The id of the request that opened this window, or 0 when none has been sent.
     *
     * <p>Generated per OPENING, not per client: a second window must not be answerable by the first window's
     * reply, and a reply is matched by this value alone.
     */
    private static long requestId;

    /** Source of unique request ids. Monotonic, so two openings cannot share one even within a tick. */
    private static long nextRequestId;

    /** The offer the window is asking about, or -1 when the server has named none yet. */
    private static int pendingOfferIndex = -1;

    /** The last status accepted for {@link #statusOfferIndex}, or null while waiting. */
    private static FreeTradeStatus current;

    /**
     * The recipe the accepted status describes.
     *
     * <p>Kept apart from {@link #pendingOfferIndex}, which is the recipe the screen is showing: the two can differ
     * for the moment between the player switching recipe and the server answering about the new one, and that
     * moment must be drawn as "waiting" rather than as the previous recipe's answer.
     */
    private static int statusOfferIndex = -1;

    /** The window instance token the last accepted status carried. */
    private static long statusToken;

    /** The quote number of the last accepted status, so an older answer cannot replace a newer one. */
    private static long statusQuoteNumber;

    /**
     * Open a window: remember which one, and refuse every status until the server confirms it.
     *
     * <p>Called when a merchant screen is shown. Any previous window's state is dropped here, so a late payload
     * for the old window can never be drawn over the new one -- it would have to match a token and a request id
     * that no longer exist.
     */
    public static void openWindow(int windowId, UUID villagerUuid) {
        FreeTradeStatusStore.windowId = windowId;
        FreeTradeStatusStore.villagerUuid = villagerUuid;
        FreeTradeStatusStore.instanceToken = 0L;
        FreeTradeStatusStore.bound = false;
        FreeTradeStatusStore.requestId = 0L;
        FreeTradeStatusStore.pendingOfferIndex = -1;
        FreeTradeStatusStore.current = null;
        FreeTradeStatusStore.statusToken = 0L;
        FreeTradeStatusStore.statusQuoteNumber = 0L;
        FreeTradeStatusStore.statusOfferIndex = -1;
    }

    /**
     * Allocate the id for a new opening request.
     *
     * <p>Called once per opening, BEFORE the request is sent, and remembered: the answer is only accepted when it
     * echoes this value. Monotonic within the client process, which is all the correlation needs -- the value
     * never leaves the two sides that are comparing it.
     */
    public static long newRequestId() {
        long id = ++nextRequestId;
        // Starting above zero keeps "no request has been sent" (0) distinct from every real request, which matters
        // because an unset request id must match nothing.
        FreeTradeStatusStore.requestId = id;
        return id;
    }

    /**
     * Register the server's identity for the open window.
     *
     * <p>A confirmation for a different window, or an answer to a question this client is no longer asking, is
     * ignored rather than adopted: a screen that closed between the request and its answer must not be resurrected
     * by the answer.
     *
     * @return whether the identity was accepted
     */
    public static boolean bind(int windowId, UUID villagerUuid, long instanceToken, long requestId) {
        if (!isOpen(windowId)) {
            return false;
        }
        if (FreeTradeStatusStore.requestId == 0L || requestId != FreeTradeStatusStore.requestId) {
            // An answer to a request that is not outstanding: either an older opening's reply, or a reply that
            // arrived after the client moved on. Adopting its token would let that window's statuses through.
            return false;
        }
        if (FreeTradeStatusStore.villagerUuid != null && villagerUuid != null
                && !FreeTradeStatusStore.villagerUuid.equals(villagerUuid)) {
            // Same syncId, different villager: this is the reuse case the token exists for.
            return false;
        }
        FreeTradeStatusStore.villagerUuid = villagerUuid;
        FreeTradeStatusStore.instanceToken = instanceToken;
        FreeTradeStatusStore.bound = true;
        return true;
    }

    /**
     * Accept a status, but only for the window that is bound, only from the instance it was bound to, and only
     * when it answers the request that binding came from.
     *
     * @return whether the status became the displayed one
     */
    public static boolean accept(int windowId, UUID villagerUuid, long instanceToken, long requestId,
                                 int offerIndex, FreeTradeStatus status) {
        if (!isOpen(windowId) || !bound || instanceToken != FreeTradeStatusStore.instanceToken) {
            // Not our window, or a window we were never told the identity of. This is the "late payload for a
            // closed window" case, and showing it would resurrect a trade the player is no longer looking at.
            return false;
        }
        if (requestId != FreeTradeStatusStore.requestId) {
            // A status from an older opening of the same window number. The token already rules most of these
            // out; this rules out the rest, where a reopened window got a new token but an old status was still
            // in flight carrying it.
            return false;
        }
        if (FreeTradeStatusStore.villagerUuid != null && villagerUuid != null
                && !FreeTradeStatusStore.villagerUuid.equals(villagerUuid)) {
            return false;
        }
        if (pendingOfferIndex >= 0 && offerIndex != pendingOfferIndex) {
            // An answer about an offer the window has moved on from. Not adopted as current, and not allowed to
            // end the wait for the offer actually selected.
            return false;
        }
        if (status.quoteRevision() < statusQuoteNumber) {
            // Older than what is already shown: packets can arrive out of order, and an older answer must never
            // replace a newer one.
            return false;
        }
        if (status.quoteRevision() == statusQuoteNumber && current != null) {
            // The same answer again. Nothing changes, and nothing is re-announced.
            return false;
        }
        if (pendingOfferIndex < 0) {
            // The first answer for this window names the offer the server is describing. Adopting it is what
            // makes the window show anything at all: the selection hook only fires when the recipe MOVES, so a
            // window opened on the default recipe would otherwise wait forever for an offer it never hears about.
            pendingOfferIndex = offerIndex;
        }
        FreeTradeStatusStore.current = status;
        FreeTradeStatusStore.statusToken = instanceToken;
        FreeTradeStatusStore.statusQuoteNumber = status.quoteRevision();
        FreeTradeStatusStore.statusOfferIndex = offerIndex;
        return true;
    }

    /** The {@linkplain #accept(int, UUID, long, long, int, FreeTradeStatus) same decision} with the fields spelled out. */
    public static boolean accept(int windowId, UUID villagerUuid, long instanceToken, int offerIndex,
                                 FreeTradeStatus status) {
        return accept(windowId, villagerUuid, instanceToken, FreeTradeStatusStore.requestId, offerIndex, status);
    }

    /**
     * Note that the window is now asking about a different offer, and go back to "waiting".
     *
     * <p>Waiting is a real state, not the absence of one: the previous offer's answer says nothing about this
     * one, and falling back to the normal price would assert something the server has not said.
     *
     * <p>The value itself is only used to recognise the answer that ends the wait; what the window DRAWS is
     * gated on {@link #statusOfferIndex}, which is the recipe the accepted answer actually describes. That is
     * deliberate: a selection this client has made but the server has not answered for yet must show the waiting
     * line rather than the previous recipe's answer, and a status that arrives for the previous recipe must not
     * be adopted as if it were about the new one.
     */
    public static void selectOffer(int offerIndex) {
        if (offerIndex < 0 || offerIndex == pendingOfferIndex) {
            return;
        }
        FreeTradeStatusStore.pendingOfferIndex = offerIndex;
        FreeTradeStatusStore.current = null;
        FreeTradeStatusStore.statusOfferIndex = -1;
    }

    /** Whether the client currently has a window open and knows its identity. */
    public static boolean isBound() {
        return bound && windowId >= 0;
    }

    /** Whether this is the window the client has open. */
    public static boolean isOpen(int windowId) {
        return FreeTradeStatusStore.windowId >= 0 && FreeTradeStatusStore.windowId == windowId;
    }

    /** The window the client has open, or -1. */
    public static int windowId() {
        return windowId;
    }

    /** The id of the request that opened the current window, or 0. */
    public static long requestId() {
        return requestId;
    }

    /** The server-issued instance token of the current window, or 0 before the binding arrives. */
    public static long instanceToken() {
        return instanceToken;
    }

    /**
     * The identity of the window with this number, when it is the opening named, or null.
     *
     * <p><b>Both values are checked, and the request id is the one captured when the screen opened.</b> Matching on
     * the number alone is not enough: vanilla reuses syncIds, so a late removal callback from a screen whose number
     * has since been handed to a newer window would read the NEW window's identity here, clear the new window's
     * state and close it on the server. Requiring the request id of the opening that is being reported turns that
     * into a miss, and a miss means "do nothing at all".
     *
     * <p>Returns null instead of the current window whenever the two do not both match, because the caller must not
     * be able to close a window it did not open.
     */
    @Nullable
    public static WindowIdentity identityFor(int windowId, long requestId) {
        if (!isOpen(windowId) || requestId == 0L || requestId != FreeTradeStatusStore.requestId) {
            return null;
        }
        return new WindowIdentity(windowId, instanceToken, requestId);
    }

    /** The three values that name one exact opening, as the close message carries them. */
    public record WindowIdentity(int windowId, long instanceToken, long requestId) {
    }

    /** The villager behind the open window, or null. */
    @Nullable
    public static UUID villagerUuid() {
        return villagerUuid;
    }

    /** The offer the window is asking about, or -1. */
    public static int pendingOfferIndex() {
        return pendingOfferIndex;
    }

    /** Whether a status has been received for the offer being asked about. */
    public static boolean hasStatus() {
        return current != null;
    }

    /**
     * The status the open window may draw, or null when it may not draw one.
     *
     * <p>Null covers three different situations on purpose -- no window, no identity yet, and an answer that has
     * not arrived -- because the window shows the same thing for all three: it is waiting. What the caller must
     * NOT do is substitute a price, which is the whole point of keeping "unknown" distinct from "not free".
     */
    @Nullable
    public static FreeTradeStatus statusFor(int windowId, int offerIndex) {
        if (!isOpen(windowId) || offerIndex != statusOfferIndex || current == null) {
            return null;
        }
        return current;
    }

    /** Whether the displayed status came from the instance the window is currently bound to. */
    public static boolean statusMatchesBinding() {
        return current != null && bound && statusToken == instanceToken;
    }

    /**
     * Forget the window only when it is the one named.
     *
     * <p>A screen reports its removal after a later window may already have replaced it, so a close that cleared
     * whatever is current would wipe the state of a window the player is looking at. Naming the window makes the
     * late report a no-op, which is the same rule the server applies to the close message.
     */
    public static void closeWindow(int windowId) {
        if (isOpen(windowId)) {
            clear();
        }
    }

    /** Forget everything: the window closed. */
    public static void clear() {        windowId = -1;
        villagerUuid = null;
        instanceToken = 0L;
        bound = false;
        requestId = 0L;
        pendingOfferIndex = -1;
        current = null;
        statusToken = 0L;
        statusQuoteNumber = 0L;
        statusOfferIndex = -1;
    }
}
