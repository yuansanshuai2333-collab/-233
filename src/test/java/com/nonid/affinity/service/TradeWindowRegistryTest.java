package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The window identity rules: which window each player has open, which request opened it, and which quote number a
 * status may carry.
 *
 * <p>These are the rules that decide whether a late or stale payload can be shown as the current trade. They are
 * pure (no game, no network), which is the point of the registry existing separately from the class that reads the
 * villager out of a screen handler.
 */
class TradeWindowRegistryTest {

    private static final UUID PLAYER = UUID.nameUUIDFromBytes("player".getBytes());
    private static final UUID VILLAGER = UUID.nameUUIDFromBytes("villager".getBytes());
    private static final UUID OTHER_VILLAGER = UUID.nameUUIDFromBytes("other-villager".getBytes());

    @AfterEach
    void clearRegistry() {
        TradeWindowRegistry.forgetAll();
    }

    private static FreeTradeStatus status(int windowId, int offerIndex, boolean free, int remaining, long revision) {
        return new FreeTradeStatus(windowId, offerIndex, free, remaining, 3, 7, "Emerald",
                free ? FreeTradeStatus.Reason.FREE : FreeTradeStatus.Reason.NO_ALLOWANCE_LEFT, revision);
    }

    @Test
    @DisplayName("each opened window gets its own token, and a reused syncId cannot inherit the old one")
    void everyOpeningGetsItsOwnToken() {
        TradeWindowRegistry.Record first = TradeWindowRegistry.register(PLAYER, 3, VILLAGER, 11L);
        TradeWindowRegistry.Record second = TradeWindowRegistry.register(PLAYER, 3, VILLAGER, 12L);

        assertNotEquals(first.instanceToken(), second.instanceToken(),
                "the same syncId reopened is a different window and must not keep the same identity");
        assertEquals(12L, second.requestId(), "the identity belongs to the request that opened it");
        assertNull(TradeWindowRegistry.current(PLAYER, 3, OTHER_VILLAGER),
                "a window for one villager is not a window for another");
    }

    @Test
    @DisplayName("a window is 'still coming up' until it has produced an answer, and never un-resolves afterwards")
    void aWindowRemembersWhetherItHasEverProducedAnAnswer() {
        // This history is what separates "the game has not finished building this window" from "this window has
        // stopped working": a villager whose customer has not been registered yet and a villager that has LOST its
        // customer are the same observation, and only the history tells them apart. Without it the second case was
        // reported as a reassuring "still coming", which is a worse answer than the refusal it replaced.
        TradeWindowRegistry.register(PLAYER, 4, VILLAGER, 1L);

        assertFalse(TradeWindowRegistry.hasEverResolved(PLAYER, 4),
                "a freshly opened window has not produced anything yet");
        TradeWindowRegistry.noteResolved(PLAYER, 4);
        assertTrue(TradeWindowRegistry.hasEverResolved(PLAYER, 4),
                "after a real verdict it is a window that works, so a later failure is a change to report");

        // Idempotent, and it survives the other writes the poller makes every time it sends.
        TradeWindowRegistry.noteResolved(PLAYER, 4);
        TradeWindowRegistry.recordSent(PLAYER, 4, status(4, 0, true, 3, 0L));
        TradeWindowRegistry.noteSelectedOffer(PLAYER, 4, 1);
        assertTrue(TradeWindowRegistry.hasEverResolved(PLAYER, 4),
                "recording a sent status or a selection must not forget that this window already worked");

        // A NEW window starts over: a reopened trade is being built again, whatever the previous one did.
        TradeWindowRegistry.register(PLAYER, 4, VILLAGER, 2L);
        assertFalse(TradeWindowRegistry.hasEverResolved(PLAYER, 4),
                "a reopened window is a new opening and has to come up again");

        assertFalse(TradeWindowRegistry.hasEverResolved(PLAYER, 99), "not the open window");
    }

    @Test
    @DisplayName("repeating the same request for the same open window keeps the token instead of churning it")
    void repeatedRequestKeepsTheToken() {
        TradeWindowRegistry.Record first = TradeWindowRegistry.register(PLAYER, 4, VILLAGER, 21L);
        TradeWindowRegistry.Record repeat = TradeWindowRegistry.register(PLAYER, 4, VILLAGER, 21L);

        assertEquals(first.instanceToken(), repeat.instanceToken(),
                "a retry must not invalidate the statuses already in flight for this window");
    }

    @Test
    @DisplayName("closing names the window: a close for an old window cannot clear the one open now")
    void closeOnlyDropsTheNamedWindow() {
        TradeWindowRegistry.register(PLAYER, 3, VILLAGER, 1L);
        TradeWindowRegistry.register(PLAYER, 5, VILLAGER, 2L);

        assertFalse(TradeWindowRegistry.close(PLAYER, 3), "window 3 is no longer open");
        assertTrue(TradeWindowRegistry.close(PLAYER, 5));
        assertEquals(-1, TradeWindowRegistry.trackedWindowId(PLAYER));
    }

    @Test
    @DisplayName("a client-reported close must match number, token AND request, or a reused number closes the new window")
    void clientCloseMatchesTheWholeIdentity() {
        TradeWindowRegistry.Record first = TradeWindowRegistry.register(PLAYER, 3, VILLAGER, 11L);
        TradeWindowRegistry.Record second = TradeWindowRegistry.register(PLAYER, 3, VILLAGER, 12L);

        assertFalse(TradeWindowRegistry.closeMatching(PLAYER, 3, first.instanceToken(), 11L),
                "the number matches, but this message is about the previous opening of it");
        assertTrue(TradeWindowRegistry.isCurrent(PLAYER, 3), "the window the player is using is still open");

        assertFalse(TradeWindowRegistry.closeMatching(PLAYER, 3, second.instanceToken(), 11L),
                "the token matches but the request does not: a reopening's identity is not interchangeable");
        assertFalse(TradeWindowRegistry.closeMatching(PLAYER, 3, first.instanceToken(), 12L));
        assertTrue(TradeWindowRegistry.isCurrent(PLAYER, 3));

        assertTrue(TradeWindowRegistry.closeMatching(PLAYER, 3, second.instanceToken(), 12L),
                "the exact identity is accepted");
        assertEquals(-1, TradeWindowRegistry.trackedWindowId(PLAYER));
    }

    @Test
    @DisplayName("the quote number advances only when a status is recorded, and restarts for a new window")
    void quoteNumbersAdvancePerRecordedStatus() {
        TradeWindowRegistry.register(PLAYER, 7, VILLAGER, 1L);

        assertEquals(1L, TradeWindowRegistry.recordSent(PLAYER, 7, status(7, 0, true, 3, 0L)));
        assertEquals(2L, TradeWindowRegistry.recordSent(PLAYER, 7, status(7, 1, true, 3, 0L)));
        assertNotNull(TradeWindowRegistry.lastSent(PLAYER, 7));

        // A new window starts its numbering again: the client's record of what it has seen is per window.
        TradeWindowRegistry.register(PLAYER, 8, VILLAGER, 2L);
        assertNull(TradeWindowRegistry.lastSent(PLAYER, 8), "nothing has been sent for the new window yet");
        assertEquals(1L, TradeWindowRegistry.recordSent(PLAYER, 8, status(8, 0, true, 3, 0L)));
    }

    @Test
    @DisplayName("a window is only current for the player, the number and the villager that all match")
    void currentRequiresAllThreeToMatch() {
        TradeWindowRegistry.register(PLAYER, 9, VILLAGER, 1L);

        assertNotNull(TradeWindowRegistry.current(PLAYER, 9, VILLAGER));
        assertNull(TradeWindowRegistry.current(PLAYER, 9, OTHER_VILLAGER));
        assertNull(TradeWindowRegistry.current(PLAYER, 10, VILLAGER));
        assertNull(TradeWindowRegistry.current(UUID.randomUUID(), 9, VILLAGER));
        assertTrue(TradeWindowRegistry.isCurrent(PLAYER, 9));
    }

    @Test
    @DisplayName("the recorded selection follows the window, and an old window's selection is not reported")
    void selectionIsPerWindow() {
        TradeWindowRegistry.register(PLAYER, 2, VILLAGER, 1L);

        assertEquals(-1, TradeWindowRegistry.selectedOfferIndex(PLAYER, 2));
        assertTrue(TradeWindowRegistry.noteSelectedOffer(PLAYER, 2, 4));
        assertEquals(4, TradeWindowRegistry.selectedOfferIndex(PLAYER, 2));
        assertFalse(TradeWindowRegistry.noteSelectedOffer(PLAYER, 2, 4), "the same value is not a change");
        assertFalse(TradeWindowRegistry.noteSelectedOffer(PLAYER, 99, 1), "not the open window");
    }

    @Test
    @DisplayName("a syncId is only a candidate, never an identity: two players can hold the same number")
    void syncIdLookupFindsACandidateOnly() {
        UUID other = UUID.nameUUIDFromBytes("other-player".getBytes());
        TradeWindowRegistry.register(PLAYER, 6, VILLAGER, 1L);
        TradeWindowRegistry.register(other, 6, VILLAGER, 2L);

        UUID found = TradeWindowRegistry.playerWithWindow(6);
        assertNotNull(found, "the lookup is documented as a candidate search, not an identity");
        assertTrue(found.equals(PLAYER) || found.equals(other));
        assertNull(TradeWindowRegistry.playerWithWindow(77));
    }

    @Test
    @DisplayName("forgetting a player drops the window and everything sent for it")
    void forgettingAPlayerDropsEverything() {
        TradeWindowRegistry.register(PLAYER, 1, VILLAGER, 1L);
        TradeWindowRegistry.recordSent(PLAYER, 1, status(1, 0, true, 3, 0L));

        TradeWindowRegistry.forget(PLAYER);

        assertEquals(-1, TradeWindowRegistry.trackedWindowId(PLAYER));
        assertNull(TradeWindowRegistry.lastSent(PLAYER, 1));
        assertTrue(TradeWindowRegistry.trackedPlayers().isEmpty());
    }
}
