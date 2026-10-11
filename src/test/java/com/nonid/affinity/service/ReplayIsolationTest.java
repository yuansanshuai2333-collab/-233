package com.nonid.affinity.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayIsolationTest {
    private final UUID instance = UUID.randomUUID(), player = UUID.randomUUID(), creature = UUID.randomUUID();
    private static final Map<String, String> REPLAY = Map.of("recall_room", "true");
    @AfterEach void clear() { ReplayIsolation.clear(); }

    @Test void onlyExplicitReplayMetadataIsRecognized() {
        assertTrue(ReplayIsolation.isReplay(REPLAY));
        assertFalse(ReplayIsolation.isReplay(null));
        assertFalse(ReplayIsolation.isReplay(Map.of()));
        assertFalse(ReplayIsolation.isReplay(Map.of("recall_room", "false")));
        assertFalse(ReplayIsolation.isReplay(Map.of("nonaffinity", "voluntary")));
        assertFalse(ReplayIsolation.isReplay(Map.of("recall_room_entry", "some-history")));
    }
    @Test void failedOrNormalStartNeverProtectsAnyone() {
        assertFalse(ReplayIsolation.begin(null, List.of(player, creature), REPLAY));
        assertFalse(ReplayIsolation.begin(instance, List.of(player, creature), Map.of()));
        assertFalse(ReplayIsolation.isActor(player));
    }
    @Test void onlyTheReplayedRelationshipIsProtected() {
        assertTrue(ReplayIsolation.begin(instance, List.of(player, creature), REPLAY));
        assertTrue(ReplayIsolation.protects(creature, player));
        assertFalse(ReplayIsolation.protects(creature, UUID.randomUUID()), "other player, same creature");
        assertFalse(ReplayIsolation.protects(UUID.randomUUID(), player), "same player, unrelated creature");
    }
    @Test void simultaneousReplaysDoNotCombineTheirActors() {
        UUID secondPlayer = UUID.randomUUID(), secondCreature = UUID.randomUUID();
        ReplayIsolation.begin(instance, List.of(player, creature), REPLAY);
        ReplayIsolation.begin(UUID.randomUUID(), List.of(secondPlayer, secondCreature), REPLAY);
        assertFalse(ReplayIsolation.protects(creature, secondPlayer));
        assertFalse(ReplayIsolation.protects(secondCreature, player));
        assertTrue(ReplayIsolation.protects(secondCreature, secondPlayer));
    }
    @Test void naturalEndAndEscapeBothReleaseOnlyTheirInstance() {
        UUID other = UUID.randomUUID();
        ReplayIsolation.begin(instance, List.of(player, creature), REPLAY);
        ReplayIsolation.begin(other, List.of(player, creature), REPLAY);
        assertTrue(ReplayIsolation.end(instance));
        assertTrue(ReplayIsolation.protects(creature, player));
        assertFalse(ReplayIsolation.end(instance));
        assertTrue(ReplayIsolation.end(other));
        assertFalse(ReplayIsolation.protects(creature, player));
    }
    @Test void repeatedStartDoesNotAddAnUnrelatedPlayer() {
        UUID stranger = UUID.randomUUID();
        ReplayIsolation.begin(instance, List.of(player, creature), REPLAY);
        ReplayIsolation.begin(instance, List.of(stranger, creature), REPLAY);
        assertFalse(ReplayIsolation.protects(creature, stranger));
    }
    @Test void savedRelationKeysAreMatchedWithoutCrashingOnBadData() {
        ReplayIsolation.begin(instance, List.of(player, creature), REPLAY);
        assertTrue(ReplayIsolation.protectsKey(creature, player.toString()));
        assertFalse(ReplayIsolation.protectsKey(creature, "not-a-uuid"));
        assertFalse(ReplayIsolation.protectsKey(creature, null));
    }
    @Test void serverStopClearsAllIsolation() {
        ReplayIsolation.begin(instance, List.of(player, creature), REPLAY);
        ReplayIsolation.clear();
        assertFalse(ReplayIsolation.isActor(player));
        assertFalse(ReplayIsolation.protects(creature, player));
    }
}
