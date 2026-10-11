package com.nonid.affinity.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.Nullable;

/**
 * The server's record of WHICH trade window each player has open, and the instance token that goes with it.
 *
 * <p><b>Why a token and not just the syncId.</b> Vanilla numbers screen handlers with small reused integers: the
 * same {@code syncId} is handed out again for a later window, and a status payload that arrives after a window is
 * closed can carry a number that matches the window open now. Comparing numbers alone therefore cannot tell "an
 * answer to my question" from "an answer about the previous window", and a screen that believed it would
 * occasionally show the previous window's free state. Every status sent for a window is stamped with a token the
 * server allocated for THAT window, and the client refuses anything it has no token for.
 *
 * <p><b>Why the client's request id is stored beside the token.</b> The token identifies the window; it does not
 * identify the client's QUESTION. The client asks with a fresh request id every time it opens a window, and after
 * a rapid close-and-reopen the player's current window need not be the one an answer was meant for. Keeping the
 * request id that produced this token lets the server:
 *
 * <ul>
 *   <li>recognise a repeat of the same request for the same window and answer it with the SAME token, instead of
 *       invalidating every status already in flight for that window;</li>
 *   <li>refuse to answer a request that does not match the token it is holding for this player, so an outdated
 *       question cannot be answered with the identity of a newer window.</li>
 * </ul>
 *
 * <p>The record is per player and holds one window at a time: opening a second window replaces the first, which is
 * exactly what the client's own one-current-screen model does.
 *
 * <p>The token is a static counter, incremented once per opened window. It is deliberately NOT derived from the
 * syncId, the villager or the time: a derived value can repeat when its inputs repeat, and the whole point is that
 * it cannot.
 *
 * <p>Nothing here touches a screen handler, an entity or a world: which villager a window belongs to is resolved by
 * the caller from the server's own handler and passed in as a UUID. That keeps every rule in this class testable
 * without a game.
 */
public final class TradeWindowRegistry {

    private TradeWindowRegistry() {
    }

    /**
     * One open window: its syncId, the villager behind it, the token that identifies this instance, and the client
     * request it answers.
     */
    public record Record(int windowId, UUID villagerUuid, long instanceToken, long requestId) {
    }

    /**
     * One player's open window, as the server tracks it, including the recipe it last selected.
     *
     * <p>{@code everResolved} records whether this window has EVER produced a real, judgeable answer. It is what
     * separates "this window is still being built" from "this window has stopped working", which otherwise look
     * identical -- a villager whose customer has not been registered yet and a villager that has lost its customer
     * are the same observation from the outside, and only the history tells them apart.
     */
    private record OpenWindow(int windowId, UUID villagerUuid, long instanceToken, long requestId,
                              int selectedOfferIndex, long nextQuoteNumber, @Nullable FreeTradeStatus lastSent,
                              boolean everResolved) {
    }

    private static final Map<UUID, OpenWindow> OPEN = new ConcurrentHashMap<>();

    /** Monotonic instance token, never reused for the lifetime of the server process. */
    private static final AtomicLong NEXT_TOKEN = new AtomicLong(1L);

    /**
     * Register the window this player has just opened, allocating its instance token.
     *
     * <p>Repeating the request that is already the current window's keeps the existing token: giving one open
     * window a second identity would invalidate every status already in flight for it, and the client would spend a
     * round trip showing "waiting" for no reason.
     *
     * <p>A request id that matches nothing this player has open is not refused here -- the caller decides what to
     * do with it, and returning the record for a window the request did not open is what lets that decision be made
     * with the token in hand.
     *
     * @param requestId the id of the client's opening request, echoed back in the binding and every status
     * @return the registered window
     */
    public static Record register(UUID playerUuid, int windowId, UUID villagerUuid, long requestId) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(villagerUuid, "villagerUuid");
        OpenWindow existing = OPEN.get(playerUuid);
        if (existing != null && existing.windowId() == windowId && existing.requestId() == requestId
                && existing.villagerUuid().equals(villagerUuid)) {
            return new Record(existing.windowId(), existing.villagerUuid(), existing.instanceToken(),
                    existing.requestId());
        }
        long token = NEXT_TOKEN.getAndIncrement();
        OPEN.put(playerUuid, new OpenWindow(windowId, villagerUuid, token, requestId, -1, 1L, null, false));
        return new Record(windowId, villagerUuid, token, requestId);
    }

    /**
     * Record which recipe this window has selected.
     *
     * <p>The server's own record, written from the server's own handler: a client that claimed a different index
     * than the one its window moved to would otherwise be answered about a recipe it is not looking at.
     *
     * @return whether the selection was recorded (false when this is not the current window)
     */
    public static boolean noteSelectedOffer(UUID playerUuid, int windowId, int offerIndex) {
        OpenWindow window = OPEN.get(playerUuid);
        if (window == null || window.windowId() != windowId) {
            return false;
        }
        if (window.selectedOfferIndex() == offerIndex) {
            return false;
        }
        OPEN.put(playerUuid, new OpenWindow(window.windowId(), window.villagerUuid(), window.instanceToken(),
                window.requestId(), offerIndex, window.nextQuoteNumber(), window.lastSent(),
                window.everResolved()));
        return true;
    }

    /** The recipe this window has selected, or -1 when none has been observed yet. */
    public static int selectedOfferIndex(UUID playerUuid, int windowId) {
        OpenWindow window = OPEN.get(playerUuid);
        return window == null || window.windowId() != windowId ? -1 : window.selectedOfferIndex();
    }

    // ------------------------------------------------------------------
    // Has this window ever produced a real answer?
    // ------------------------------------------------------------------

    /**
     * Whether this window has ever produced a judgeable answer.
     *
     * <p>False means the window is still being built, which is the only state in which "nothing is ready yet" is the
     * honest description. True means a real answer was produced at least once, so anything missing now is a change
     * that happened rather than a window that has not finished coming up -- the two are indistinguishable from a
     * single observation and only the history separates them.
     */
    public static boolean hasEverResolved(UUID playerUuid, int windowId) {
        OpenWindow window = OPEN.get(playerUuid);
        return window != null && window.windowId() == windowId && window.everResolved();
    }

    /**
     * Record that this window has now produced a real answer. Idempotent.
     *
     * <p>Called only for a verdict about a ready window, so a window that is still building never marks itself
     * resolved and can never lose its "still coming" answer.
     */
    public static void noteResolved(UUID playerUuid, int windowId) {
        OpenWindow window = OPEN.get(playerUuid);
        if (window == null || window.windowId() != windowId || window.everResolved()) {
            return;
        }
        OPEN.put(playerUuid, new OpenWindow(window.windowId(), window.villagerUuid(), window.instanceToken(),
                window.requestId(), window.selectedOfferIndex(), window.nextQuoteNumber(), window.lastSent(), true));
    }

    /**
     * The window this player has open with this villager, when it is the one the caller means.
     *
     * <p>A mismatch is not an error to be repaired: it is a status about a window that is gone, and the honest
     * answer is to send nothing.
     *
     * @param villagerUuid the villager the caller resolved from the server's own handler, or null to accept any
     */
    @Nullable
    public static Record current(UUID playerUuid, int windowId, @Nullable UUID villagerUuid) {
        OpenWindow window = OPEN.get(playerUuid);
        if (window == null || window.windowId() != windowId) {
            return null;
        }
        if (villagerUuid != null && !villagerUuid.equals(window.villagerUuid())) {
            return null;
        }
        return new Record(window.windowId(), window.villagerUuid(), window.instanceToken(), window.requestId());
    }

    /** Whether this player has this exact window open. */
    public static boolean isCurrent(UUID playerUuid, int windowId) {
        OpenWindow window = OPEN.get(playerUuid);
        return window != null && window.windowId() == windowId;
    }

    // ------------------------------------------------------------------
    // The quote number and the last answer sent
    // ------------------------------------------------------------------

    /**
     * Record that this status is now what the window has been told, and hand back the quote number it goes out
     * under.
     *
     * <p>The number advances only here, and only when the caller has decided the answer CHANGED. That is what makes
     * it a number the client can trust: a repeat of an old answer cannot carry a newer number, so a status that
     * arrives late over a laggy connection is recognisable by its number and is dropped rather than drawn over a
     * fresher one.
     *
     * <p>The number restarts at one for a new window: it is per window, not per player, because the client's record
     * of what it has seen is per window too.
     *
     * @return the number this status must be sent under
     */
    public static long recordSent(UUID playerUuid, int windowId, FreeTradeStatus status) {
        Objects.requireNonNull(status, "status");
        OpenWindow window = OPEN.get(playerUuid);
        if (window == null || window.windowId() != windowId) {
            // Nothing is open: the status is numbered from one anyway, so a caller that has somehow lost the window
            // record cannot divide by zero or smuggle a huge number past the client's ordering check.
            return 1L;
        }
        long number = window.nextQuoteNumber();
        OPEN.put(playerUuid, new OpenWindow(window.windowId(), window.villagerUuid(), window.instanceToken(),
                window.requestId(), window.selectedOfferIndex(), number + 1L, status, window.everResolved()));
        return number;
    }

    /** The last status sent to this player for this window, or null when nothing has been sent for it. */
    @Nullable
    public static FreeTradeStatus lastSent(UUID playerUuid, int windowId) {
        OpenWindow window = OPEN.get(playerUuid);
        return window == null || window.windowId() != windowId ? null : window.lastSent();
    }

    // ------------------------------------------------------------------
    // Forgetting
    // ------------------------------------------------------------------

    /**
     * Forget this player's window, but only when it is the one named.
     *
     * <p>The check is what makes a late close packet harmless: a close for window 3 arriving after window 5 was
     * opened must not clear window 5.
     *
     * <p>This matches on the number alone, which is enough for the checks the SERVER runs on its own initiative: it
     * has just looked at the player's own handler. A message from the CLIENT goes through
     * {@link #closeMatching(UUID, int, long, long)} instead, because a client-supplied number is not evidence of
     * which window is meant.
     *
     * @return whether a window was actually dropped
     */
    public static boolean close(UUID playerUuid, int windowId) {
        OpenWindow window = OPEN.get(playerUuid);
        if (window == null || window.windowId() != windowId) {
            return false;
        }
        OPEN.remove(playerUuid);
        return true;
    }

    /**
     * Forget this player's window only when the whole identity matches: number, instance token and opening request.
     *
     * <p>Used for the close the client reports. Comparing the number alone lets a close for an earlier window match a
     * later one that reused the number, which would drop a record the player is still using -- costing that window
     * its status updates until it asks to be bound again. The token rules out every earlier window with the same
     * number, and the request id rules out a later reopening of the same villager.
     *
     * @return whether the message described the window that is actually open
     */
    public static boolean closeMatching(UUID playerUuid, int windowId, long instanceToken, long requestId) {
        OpenWindow window = OPEN.get(playerUuid);
        if (window == null || window.windowId() != windowId || window.instanceToken() != instanceToken
                || window.requestId() != requestId) {
            return false;
        }
        OPEN.remove(playerUuid);
        return true;
    }

    /** Forget everything about this player (disconnect, respawn). */
    public static void forget(UUID playerUuid) {
        OPEN.remove(playerUuid);
    }

    /** Every player with an open window, for the per-tick change check. */
    public static List<UUID> trackedPlayers() {
        return List.copyOf(OPEN.keySet());
    }

    /** The window number this player's record holds, or -1. */
    public static int trackedWindowId(UUID playerUuid) {
        OpenWindow window = OPEN.get(playerUuid);
        return window == null ? -1 : window.windowId();
    }

    /** Forget every player's window (server stop). */
    public static void forgetAll() {
        OPEN.clear();
    }

    /**
     * The player whose open window carries this syncId, or null.
     *
     * <p><b>Not a way to identify a player.</b> A syncId is only unique among the windows one player has open at one
     * moment, so two players can hold the same number: anything resolved through this method can land on the wrong
     * player, and in a window whose customer is the other one it would describe somebody else's trade. It is kept
     * only so code that has nothing but a handler number can find a CANDIDATE, and every caller must confirm the
     * identity from the window's own customer before acting.
     */
    @Nullable
    public static UUID playerWithWindow(int windowId) {
        for (Map.Entry<UUID, OpenWindow> entry : OPEN.entrySet()) {
            if (entry.getValue().windowId() == windowId) {
                return entry.getKey();
            }
        }
        return null;
    }
}
