package com.nonid.affinity.net;

import com.nonid.affinity.command.AffinityCommand;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DetectorQueryLifecycleTest {
    @AfterEach void clear() { AffinityNetworking.clearAllState(); }

    @Test void rateLimitUsesServerTicksAndDoesNotConsumeRejectedRequests() {
        UUID player = UUID.randomUUID();
        assertTrue(AffinityNetworking.allowDetectorQuery(player, 1000));
        assertFalse(AffinityNetworking.allowDetectorQuery(player, 1009));
        // World time may jump on a dimension change; the shared server clock continues.
        assertTrue(AffinityNetworking.allowDetectorQuery(player, 1010));
        assertFalse(AffinityNetworking.allowDetectorQuery(player, 1010));
    }

    @Test void playersAndDisconnectsAreIndependent() {
        UUID one = UUID.randomUUID(), two = UUID.randomUUID();
        assertTrue(AffinityNetworking.allowDetectorQuery(one, 100));
        assertTrue(AffinityNetworking.allowDetectorQuery(two, 100));
        AffinityNetworking.clearPlayerState(one);
        assertTrue(AffinityNetworking.allowDetectorQuery(one, 100));
        assertFalse(AffinityNetworking.allowDetectorQuery(two, 100));
    }

    @Test void serverCounterWrapAndResetDoNotLockQueries() {
        UUID player = UUID.randomUUID();
        assertTrue(AffinityNetworking.allowDetectorQuery(player, Integer.MAX_VALUE - 4));
        assertFalse(AffinityNetworking.allowDetectorQuery(player, Integer.MIN_VALUE));
        assertTrue(AffinityNetworking.allowDetectorQuery(player, Integer.MIN_VALUE + 5));
        AffinityNetworking.clearAllState();
        assertTrue(AffinityNetworking.allowDetectorQuery(player, 0));
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, List<String>> commandLists() throws Exception {
        var field = AffinityCommand.class.getDeclaredField("LAST_CANDIDATES");
        field.setAccessible(true);
        return (Map<UUID, List<String>>) field.get(null);
    }

    @Test void disconnectClearsOnlyTheDepartingPlayersCommandList() throws Exception {
        UUID one = UUID.randomUUID(), two = UUID.randomUUID();
        var lists = commandLists();
        lists.put(one, List.of("test:first")); lists.put(two, List.of("test:second"));
        AffinityNetworking.clearPlayerState(one);
        assertFalse(lists.containsKey(one));
        assertEquals(List.of("test:second"), lists.get(two));
    }

    @Test void serverStopCannotCarryAnimationListsIntoAnotherSave() throws Exception {
        var lists = commandLists();
        lists.put(UUID.randomUUID(), List.of("old_pack:animation"));
        AffinityNetworking.clearAllState();
        assertTrue(lists.isEmpty());
    }
}
