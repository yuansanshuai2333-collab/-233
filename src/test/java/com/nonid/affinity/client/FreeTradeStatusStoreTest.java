package com.nonid.affinity.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nonid.affinity.service.FreeTradeStatus;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The client's acceptance rules for trade statuses.
 *
 * <p>What this pins, and why each one matters to the user-visible defect ("the server recorded a free trade while
 * the screen still showed a price"):
 *
 * <ul>
 *   <li>an answer to a different opening of the same window number must not be adopted -- otherwise the previous
 *       window's free state can be drawn on this one;</li>
 *   <li>an answer about a recipe the window has moved away from must not be drawn;</li>
 *   <li>an older answer must not replace a newer one, however it was reordered on the way here;</li>
 *   <li>an answer that has not arrived must leave the window in its waiting state rather than falling back to a
 *       price.</li>
 * </ul>
 */
class FreeTradeStatusStoreTest {

    private static final UUID VILLAGER = UUID.nameUUIDFromBytes("villager".getBytes());
    private static final UUID OTHER_VILLAGER = UUID.nameUUIDFromBytes("other".getBytes());

    @BeforeEach
    @AfterEach
    void clearStore() {
        FreeTradeStatusStore.clear();
    }

    private static FreeTradeStatus status(int windowId, int offerIndex, boolean free, int remaining, long quote) {
        return new FreeTradeStatus(windowId, offerIndex, free, remaining, 3, 9, "Emerald",
                free ? FreeTradeStatus.Reason.FREE : FreeTradeStatus.Reason.NO_ALLOWANCE_LEFT, quote);
    }

    /** Open a window and send the request that goes with it, returning the request id. */
    private static long openWindow(int windowId) {
        FreeTradeStatusStore.openWindow(windowId, null);
        return FreeTradeStatusStore.newRequestId();
    }

    @Test
    @DisplayName("a status cannot be shown before the window's identity has been confirmed")
    void nothingIsShownBeforeBinding() {
        long request = openWindow(3);

        assertFalse(FreeTradeStatusStore.accept(3, VILLAGER, 100L, request, 0, status(3, 0, true, 3, 1L)),
                "without the server's token there is nothing to believe");
        assertNull(FreeTradeStatusStore.statusFor(3, 0));
        assertFalse(FreeTradeStatusStore.hasStatus());
    }

    @Test
    @DisplayName("the identity is only accepted when it echoes the request still outstanding")
    void bindingRequiresTheOutstandingRequest() {
        long request = openWindow(3);

        assertFalse(FreeTradeStatusStore.bind(3, VILLAGER, 100L, request + 1), "an answer to another question");
        assertFalse(FreeTradeStatusStore.isBound());
        assertTrue(FreeTradeStatusStore.bind(3, VILLAGER, 100L, request));
        assertTrue(FreeTradeStatusStore.isBound());
    }

    @Test
    @DisplayName("the first accepted status names the offer, so a window opened on the default recipe is not stuck")
    void theFirstStatusNamesTheOffer() {
        long request = openWindow(3);
        assertTrue(FreeTradeStatusStore.bind(3, VILLAGER, 100L, request));

        assertTrue(FreeTradeStatusStore.accept(3, VILLAGER, 100L, request, 0, status(3, 0, true, 3, 1L)));
        assertEquals(0, FreeTradeStatusStore.pendingOfferIndex());
        assertNotNull(FreeTradeStatusStore.statusFor(3, 0));
        assertTrue(FreeTradeStatusStore.statusFor(3, 0).showAsFree());
    }

    @Test
    @DisplayName("a status for a previous opening of the same window number is refused")
    void aStatusFromAnotherOpeningIsRefused() {
        long first = openWindow(3);
        assertTrue(FreeTradeStatusStore.bind(3, VILLAGER, 100L, first));
        assertTrue(FreeTradeStatusStore.accept(3, VILLAGER, 100L, first, 0, status(3, 0, true, 3, 1L)));

        // The window is closed and the same syncId is handed out again, then used by a late packet from before.
        FreeTradeStatusStore.closeWindow(3);
        long second = openWindow(3);
        assertTrue(FreeTradeStatusStore.bind(3, VILLAGER, 200L, second));

        assertFalse(FreeTradeStatusStore.accept(3, VILLAGER, 200L, first, 0, status(3, 0, true, 1, 2L)),
                "the token matches the new window but the request belongs to the old opening");
        assertFalse(FreeTradeStatusStore.accept(3, VILLAGER, 100L, second, 0, status(3, 0, true, 1, 2L)),
                "the request matches but the token belongs to the old window");
        assertNull(FreeTradeStatusStore.statusFor(3, 0), "nothing from the old opening may be drawn");
    }

    @Test
    @DisplayName("an answer for a recipe the window has moved away from is not adopted as the new recipe's")
    void aStatusForAnotherRecipeIsNotDrawn() {
        FreeTradeStatusStore.openWindow(3, null);
        FreeTradeStatusStore.selectOffer(0);
        long request = FreeTradeStatusStore.newRequestId();
        FreeTradeStatusStore.bind(3, VILLAGER, 100L, request);

        // The player switches to recipe 1. The answer still in flight is about recipe 0, so it must not be
        // adopted: the window shows "waiting" for the recipe actually on screen.
        FreeTradeStatusStore.selectOffer(1);
        assertFalse(FreeTradeStatusStore.accept(3, VILLAGER, 100L, request, 0, status(3, 0, true, 3, 1L)),
                "recipe 0 is no longer the recipe being asked about");
        assertNull(FreeTradeStatusStore.statusFor(3, 1));
        assertNull(FreeTradeStatusStore.statusFor(3, 0));

        // The server answers about recipe 1, which ends the wait.
        assertTrue(FreeTradeStatusStore.accept(3, VILLAGER, 100L, request, 1, status(3, 1, true, 3, 2L)));
        assertNotNull(FreeTradeStatusStore.statusFor(3, 1));
        assertTrue(FreeTradeStatusStore.statusFor(3, 1).showAsFree());
        assertNull(FreeTradeStatusStore.statusFor(3, 0), "the previous recipe's answer is not offered for this one");
    }

    @Test
    @DisplayName("an older answer never replaces a newer one, and a repeat changes nothing")
    void quoteNumbersOnlyMoveForward() {
        long request = openWindow(3);
        FreeTradeStatusStore.bind(3, VILLAGER, 100L, request);
        assertTrue(FreeTradeStatusStore.accept(3, VILLAGER, 100L, request, 0, status(3, 0, false, 0, 5L)));

        assertFalse(FreeTradeStatusStore.accept(3, VILLAGER, 100L, request, 0, status(3, 0, true, 3, 4L)),
                "an answer older than what is shown must be dropped, not drawn");
        assertFalse(FreeTradeStatusStore.statusFor(3, 0).showAsFree());

        assertTrue(FreeTradeStatusStore.accept(3, VILLAGER, 100L, request, 0, status(3, 0, true, 3, 6L)));
        assertTrue(FreeTradeStatusStore.statusFor(3, 0).showAsFree());

        assertFalse(FreeTradeStatusStore.accept(3, VILLAGER, 100L, request, 0, status(3, 0, true, 3, 6L)),
                "the same answer again is neither redrawn nor re-announced");
        assertTrue(FreeTradeStatusStore.statusFor(3, 0).showAsFree());
    }

    @Test
    @DisplayName("a status for a villager that is not the bound one is refused")
    void aStatusForAnotherVillagerIsRefused() {
        FreeTradeStatusStore.openWindow(3, VILLAGER);
        long request = FreeTradeStatusStore.newRequestId();
        FreeTradeStatusStore.bind(3, VILLAGER, 100L, request);

        assertFalse(FreeTradeStatusStore.accept(3, OTHER_VILLAGER, 100L, request, 0, status(3, 0, true, 3, 1L)));
        assertNull(FreeTradeStatusStore.statusFor(3, 0));
    }

    @Test
    @DisplayName("closing only forgets the window it names, so a late close cannot wipe the current one")
    void closingNamesTheWindow() {
        long request = openWindow(5);
        FreeTradeStatusStore.bind(5, VILLAGER, 100L, request);
        FreeTradeStatusStore.accept(5, VILLAGER, 100L, request, 0, status(5, 0, true, 3, 1L));

        FreeTradeStatusStore.closeWindow(3);
        assertTrue(FreeTradeStatusStore.statusMatchesBinding(), "a close for another window changed nothing");

        FreeTradeStatusStore.closeWindow(5);
        assertFalse(FreeTradeStatusStore.isBound());
        assertNull(FreeTradeStatusStore.statusFor(5, 0));
        assertEquals(-1, FreeTradeStatusStore.windowId());
    }

    @Test
    @DisplayName("switching recipe clears the old answer and lets the new recipe's answer be adopted")
    void switchingRecipeMovesTheWaitAndAcceptsTheNewAnswer() {
        long request = openWindow(3);
        FreeTradeStatusStore.bind(3, VILLAGER, 100L, request);
        assertTrue(FreeTradeStatusStore.accept(3, VILLAGER, 100L, request, 0, status(3, 0, true, 3, 1L)),
                "the answer for the recipe the window opened on is accepted");
        assertTrue(FreeTradeStatusStore.statusFor(3, 0).showAsFree());

        // The player clicks the second recipe. The client must stop waiting for recipe 0 and drop its answer.
        FreeTradeStatusStore.selectOffer(1);
        assertEquals(1, FreeTradeStatusStore.pendingOfferIndex());
        assertNull(FreeTradeStatusStore.statusFor(3, 0), "the old recipe's verdict is no longer drawn");
        assertNull(FreeTradeStatusStore.statusFor(3, 1), "and the new recipe is waiting for its own answer");

        assertFalse(FreeTradeStatusStore.accept(3, VILLAGER, 100L, request, 0, status(3, 0, false, 0, 2L)),
                "a status still in flight for the recipe we left must not be adopted");
        assertTrue(FreeTradeStatusStore.accept(3, VILLAGER, 100L, request, 1, status(3, 1, true, 2, 3L)),
                "the answer for the recipe now on screen is accepted, so the wait ends");
        assertNotNull(FreeTradeStatusStore.statusFor(3, 1));
        assertTrue(FreeTradeStatusStore.statusFor(3, 1).showAsFree());
        assertEquals(2, FreeTradeStatusStore.statusFor(3, 1).remaining());
    }

    @Test
    @DisplayName("a reply that arrives in the same tick as the recipe click is still accepted")
    void aReplyArrivingBeforeTheNextTickIsStillAccepted() {
        // The race the receiver's ordering closes: the player clicks another recipe, the server answers that new
        // recipe at once, and the packet is processed BEFORE the per-tick follower has run. Judged against the
        // recipe noted before the click, the reply would be refused -- and the server would not send it again,
        // because from its side nothing changed. The window would then wait forever with its own answer in hand.
        long request = openWindow(3);
        FreeTradeStatusStore.bind(3, VILLAGER, 100L, request);
        assertTrue(FreeTradeStatusStore.accept(3, VILLAGER, 100L, request, 0, status(3, 0, true, 3, 1L)));

        // The click has already moved the screen to recipe 1; the follower has NOT run yet.
        int screenRecipe = 1;
        assertTrue(FreeTradeWindowClient.acceptFromServer(3, VILLAGER, 100L, request, 1,
                        status(3, 1, true, 2, 2L), screenRecipe),
                "the receiver notes the screen's recipe before judging, so the reply is not refused as stale");
        assertNotNull(FreeTradeStatusStore.statusFor(3, 1));
        assertTrue(FreeTradeStatusStore.statusFor(3, 1).showAsFree());
    }

    @Test
    @DisplayName("the receiver still refuses a reply about a recipe the screen is not showing")
    void aReplyForARecipeTheScreenLeftIsStillRefused() {
        long request = openWindow(3);
        FreeTradeStatusStore.bind(3, VILLAGER, 100L, request);

        // The screen has moved to recipe 1, but the packet is the answer to recipe 0 -- a reply that lost the race
        // the other way. Noting the screen's recipe first must not turn that into an acceptance.
        assertFalse(FreeTradeWindowClient.acceptFromServer(3, VILLAGER, 100L, request, 0,
                        status(3, 0, true, 3, 1L), 1),
                "an answer about a recipe the player has left must not be adopted, however it is ordered");
        assertNull(FreeTradeStatusStore.statusFor(3, 0));
        assertNull(FreeTradeStatusStore.statusFor(3, 1));
    }

    @Test
    @DisplayName("a close naming the opening it made is accepted; a reused number with the old request is not")
    void theCloseMessageNamesTheWholeIdentity() {
        long first = openWindow(3);
        FreeTradeStatusStore.bind(3, VILLAGER, 100L, first);

        FreeTradeStatusStore.WindowIdentity identity = FreeTradeStatusStore.identityFor(3, first);
        assertNotNull(identity, "the open window has an identity to report");
        assertEquals(3, identity.windowId());
        assertEquals(100L, identity.instanceToken());
        assertEquals(first, identity.requestId());

        // The window is closed and the SAME syncId is handed to a new window -- the reuse case that makes reading
        // the identity at close time unsafe.
        FreeTradeStatusStore.closeWindow(3);
        long second = openWindow(3);
        FreeTradeStatusStore.bind(3, VILLAGER, 200L, second);

        assertNull(FreeTradeStatusStore.identityFor(3, first),
                "a late removal callback from the OLD screen must not be handed the new window's identity: it "
                        + "would clear the new state and close the window the player is using");
        assertTrue(FreeTradeStatusStore.isBound(), "the new window is untouched by the late callback");
        assertNotNull(FreeTradeStatusStore.identityFor(3, second), "the new opening can still report itself");
    }

    @Test
    @DisplayName("every opening gets a request id of its own, and an unset id matches nothing")
    void requestIdsAreUniqueAndNonZero() {
        long first = FreeTradeStatusStore.newRequestId();
        long second = FreeTradeStatusStore.newRequestId();

        assertTrue(first > 0L && second > 0L);
        assertFalse(first == second);
        FreeTradeStatusStore.clear();
        assertEquals(0L, FreeTradeStatusStore.requestId(), "after a clear there is no outstanding question");
        assertFalse(FreeTradeStatusStore.bind(3, VILLAGER, 100L, second), "and no window to answer about");
    }
}
