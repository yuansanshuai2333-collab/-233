package com.nonid.affinity.client;

import com.nonid.affinity.mixin.MerchantInventoryMerchantAccessor;
import com.nonid.affinity.mixin.MerchantScreenHandlerAccess;
import com.nonid.affinity.net.FreeTradeBindRequestPayload;
import com.nonid.affinity.net.FreeTradeClosePayload;
import com.nonid.affinity.service.FreeTradeStatus;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.village.MerchantInventory;
import org.jetbrains.annotations.Nullable;

/**
 * The trade window's side of the free-trade display: ask for the window's identity when it opens, follow the recipe
 * the player actually moves to, report when the window goes away, and answer "what should be drawn" while it is up.
 *
 * <p>Everything shown is the SERVER's answer. Nothing is derived on this side: the free flag, the remaining count
 * and the reason all arrive in a status, and this class only decides how to say them. That is deliberate -- the
 * regression being closed is "the server recorded a free trade while the screen showed a price", and a client that
 * recomputed the price from the ingredients would be able to disagree with the server all over again.
 *
 * <p>The request id is generated per opening and remembered until the answer echoes it, so an answer to an older
 * opening of the same window number cannot be mistaken for this one. See {@link FreeTradeStatusStore}.
 */
@Environment(EnvType.CLIENT)
public final class FreeTradeWindowClient {

    private FreeTradeWindowClient() {
    }

    /**
     * A screen has been shown: bind the trade window when it is a merchant screen, and drop the previous window
     * otherwise.
     *
     * @return the syncId of the window that was opened, or -1 when this screen is not a trade window. The caller
     *         must pass this value back to {@link #onScreenClosed}: a screen's own removal must name the window it
     *         was opened for, not whatever window happens to be current when it is removed.
     */
    public static int onScreenOpened(Screen screen) {
        if (!(screen instanceof MerchantScreen merchantScreen)) {
            // Any other screen means the trade window is gone (or was never up). Clearing here as well as on the
            // close event covers a window replaced directly by another screen, which never reports a removal.
            FreeTradeStatusStore.clear();
            return -1;
        }
        MerchantScreenHandler handler = merchantScreen.getScreenHandler();
        int windowId = handler.syncId;
        // The villager is not known on this side -- only the server can read it from its own handler. It stays
        // null until the binding arrives, which is also the value every status is checked against.
        FreeTradeStatusStore.openWindow(windowId, null);
        FreeTradeStatusStore.selectOffer(recipeIndex(handler));
        long requestId = FreeTradeStatusStore.newRequestId();
        if (ClientPlayNetworking.canSend(FreeTradeBindRequestPayload.ID)) {
            ClientPlayNetworking.send(new FreeTradeBindRequestPayload(windowId, requestId));
        } else {
            // A server without this mod: there is nothing to show, and clearing is better than a window that
            // waits forever for an answer that cannot come.
            FreeTradeStatusStore.clear();
            return -1;
        }
        return windowId;
    }

    /**
     * Follow the recipe the screen is now showing: note it as the offer being waited for, and return what may be
     * drawn for it.
     *
     * <p><b>Why this has to run while the window is open, and not only when it opens.</b> The player changes recipe
     * by clicking a trade-list button, which moves the selection on BOTH sides; the server then sends a status for
     * the new recipe. If this client still considered the OLD recipe the one being asked about, that status would be
     * refused as "about an offer we have moved on from" -- and since every later status is about the new recipe too,
     * the window would show its waiting line forever. Noting the selection the moment the screen shows a different
     * recipe is what keeps the two sides agreeing about which offer is being described.
     *
     * <p>Noting it also clears the previous recipe's answer, so the window falls back to its waiting line for the
     * moment between the switch and the server's reply, rather than describing the new recipe with the old one's
     * verdict.
     *
     * <p>The recipe index is read from the game's own handler, so this cannot drift from what is drawn: it is the
     * same value the server reads on its side.
     */
    @Nullable
    public static FreeTradeStatus syncSelection(MerchantScreenHandler handler) {
        FreeTradeStatusStore.selectOffer(recipeIndex(handler));
        return computeStatus(handler);
    }

    /**
     * The window this client currently has open, or -1.
     *
     * <p>Free of any screen: it is the store's own answer to "which window am I looking at".
     */
    public static int openWindowId() {
        return FreeTradeStatusStore.windowId();
    }

    /** Whether this client has a trade window with state worth drawing. */
    public static boolean hasWindow() {
        return openWindowId() >= 0;
    }

    /**
     * The status to draw, or null when this window has no answer for the recipe it is showing.
     *
     * <p>Free of any screen, so the "is the answer I hold about the recipe on screen" rule is testable on its own.
     */
    @Nullable
    public static FreeTradeStatus computeStatus(MerchantScreenHandler handler) {
        return FreeTradeStatusText.statusFor(handler.syncId, recipeIndex(handler));
    }

    /**
     * The status to draw for whatever the screen is showing, without noting a selection.
     *
     * <p>Used by the renderer, which runs after {@link #syncSelection} has already noted the selection and only
     * needs the answer.
     */
    @Nullable
    public static FreeTradeStatus statusFor(MerchantScreenHandler handler) {
        return computeStatus(handler);
    }

    /**
     * The screen this client has open, if it is a trade window: follow whichever recipe it is showing.
     *
     * <p>Registered as the per-tick callback for a trade screen. Reading the recipe from the screen and noting it in
     * the store is the whole job -- see {@link #syncSelection} for why that has to happen while the window is open.
     */
    public static void followScreenSelection(Screen screen) {
        if (screen instanceof MerchantScreen merchantScreen) {
            syncSelection(merchantScreen.getScreenHandler());
        }
    }

    /**
     * Note a recipe read from the screen, then judge a status from the server against it.
     *
     * <p><b>The ordering is the code.</b> The per-tick follower makes the wait correct once per tick, but a reply can
     * arrive in the very tick the player clicked another recipe: the server answers the new recipe at once, and that
     * reply is judged against the recipe this client last noted -- the one before the click. It would be refused as
     * "about an offer we have moved on from", the server would not send it again because from its side nothing
     * changed, and the window would sit on its waiting line with its own answer already in hand. Reading the
     * selection first and judging second closes that gap completely: every reply is compared against the selection
     * the game holds at the moment of judging.
     *
     * <p>Takes the recipe index as a plain number, and is public, so the ordering can be exercised on the test path
     * -- where no Minecraft class can be linked. The receiver reads the index from the client's current screen and
     * calls this with it.
     *
     * @param screenRecipe the recipe the screen is showing right now, or a negative value when there is no screen
     * @return whether the status became the displayed one
     */
    public static boolean acceptFromServer(int windowId, java.util.UUID villagerUuid, long instanceToken,
                                           long requestId, int offerIndex, FreeTradeStatus status,
                                           int screenRecipe) {
        FreeTradeStatusStore.selectOffer(screenRecipe);
        return FreeTradeStatusStore.accept(windowId, villagerUuid, instanceToken, requestId, offerIndex, status);
    }

    /**
     * The request id of the opening that produced this window, or 0 when that opening is no longer current.
     *
     * <p>Called immediately after {@link #onScreenOpened}, and the value is what the screen's removal callback
     * carries for the rest of that screen's life. Capturing it here rather than reading it at close time is what
     * makes a late callback harmless: the store may have moved on to a newer window that reused the same syncId, and
     * a callback that compared only the number would act on that newer window.
     */
    public static long openedRequestId(int windowId) {
        if (windowId < 0 || !FreeTradeStatusStore.isOpen(windowId)) {
            return 0L;
        }
        return FreeTradeStatusStore.requestId();
    }

    /**
     * The window this screen was opened for has gone away: drop the state and tell the server.
     *
     * <p><b>The request id is captured when the screen OPENS, not looked up when it closes.</b> That is the whole
     * point of passing it in. Looking it up at close time means reading it out of whatever window the store holds
     * then, and vanilla reuses syncIds: if a later trade window took the same number, a late removal callback from
     * this screen would read the NEW window's identity out of the store, clear the new window's state, and send a
     * close message for it -- shutting down a window the player is still looking at. Matching on the number and the
     * request captured at open time makes such a callback recognisable as being about a window that is no longer
     * there, and it then does nothing at all.
     */
    public static void onScreenClosed(int windowId, long requestId) {
        if (windowId < 0 || requestId == 0L) {
            return;
        }
        FreeTradeStatusStore.WindowIdentity identity = FreeTradeStatusStore.identityFor(windowId, requestId);
        if (identity == null) {
            // Not the opening this screen made: either the store moved on to a newer window that reused the number,
            // or this window was already closed. Nothing is cleared and no message is sent -- the server drops a
            // dead window on its own, and a close built from a mismatched identity would be refused anyway.
            return;
        }
        FreeTradeStatusStore.closeWindow(windowId);
        if (ClientPlayNetworking.canSend(FreeTradeClosePayload.ID)) {
            ClientPlayNetworking.send(new FreeTradeClosePayload(identity.windowId(), identity.instanceToken(),
                    identity.requestId()));
        }
    }

    /** Leaving the world drops everything: a window cannot outlive its connection. */
    public static void onDisconnected() {
        FreeTradeStatusStore.clear();
    }

    /**
     * The recipe the client's own window is showing, or -1.
     *
     * <p>Read from the handler's trade inventory, which is the same field the server reads on its side. Used to keep
     * the two sides agreeing about which offer is being described; it is not an input to any pricing decision.
     *
     * <p>Two accessors are read in sequence rather than one composite accessor on the handler, because a mixin
     * interface may only hold accessors: a {@code default} method there is rejected when the game starts.
     */
    public static int recipeIndex(MerchantScreenHandler handler) {
        MerchantInventory inventory =
                ((MerchantScreenHandlerAccess) handler).noncreatureaffinity$merchantInventory();
        return ((MerchantInventoryMerchantAccessor) inventory).noncreatureaffinity$offerIndex();
    }
}
