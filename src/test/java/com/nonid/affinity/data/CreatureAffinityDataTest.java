package com.nonid.affinity.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

/**
 * Data layer tests: they exercise the parts that do not need a running server,
 * namely NBT round trips, history retention and relation bookkeeping.
 */
class CreatureAffinityDataTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CAROL = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void unknownPlayerHasNoRelation() {
        CreatureAffinityData data = new CreatureAffinityData();
        assertTrue(data.isEmpty());
        assertNull(data.get(ALICE));
        assertEquals(0, data.size());
    }

    @Test
    void creationUsesDefaultAffinity() {
        CreatureAffinityData data = new CreatureAffinityData();
        PlayerAffinity relation = data.getOrCreate(ALICE, 7);

        assertNotNull(relation);
        assertEquals(7, relation.getAffinity());
        assertEquals(1, data.size());
        // The same player must map to the same record.
        assertEquals(relation, data.getOrCreate(ALICE, 999));
        assertEquals(1, data.size());
    }

    @Test
    void affinityIsClampedToConfiguredRange() {
        PlayerAffinity relation = new PlayerAffinity(50);

        assertEquals(-40, relation.addAffinity(-40, 0, 100), "delta is reported, not the new value");
        assertEquals(10, relation.getAffinity());

        assertEquals(90, relation.addAffinity(500, 0, 100));
        assertEquals(100, relation.getAffinity());

        // Already at the maximum: no change is reported.
        assertEquals(0, relation.addAffinity(5, 0, 100));
    }

    @Test
    void untrimmedEntriesSurviveMaxSize() {
        CreatureAffinityData data = new CreatureAffinityData();
        for (int i = 0; i < CreatureAffinityData.MAX_ENTRIES; i++) {
            data.getOrCreate(UUID.nameUUIDFromBytes(("player-" + i).getBytes()), 1);
        }
        assertEquals(CreatureAffinityData.MAX_ENTRIES, data.size());
    }

    @Test
    void exceedingHistoricalLimitKeepsLowestNonFriendEntry() {
        CreatureAffinityData data = new CreatureAffinityData();
        UUID weakest = UUID.nameUUIDFromBytes("weakest".getBytes());
        PlayerAffinity weakRelation = data.getOrCreate(weakest, 1);
        weakRelation.setAffinity(0, 0, 100);

        for (int i = 0; i < CreatureAffinityData.MAX_ENTRIES; i++) {
            PlayerAffinity relation = data.getOrCreate(UUID.nameUUIDFromBytes(("p" + i).getBytes()), 50);
            relation.setAffinity(50, 0, 100);
        }

        assertEquals(CreatureAffinityData.MAX_ENTRIES + 1, data.size());
        assertEquals(weakRelation, data.get(weakest), "even the weakest relation retains its history");
    }

    @Test
    void friendsAndNonFriendsBothSurviveNewRelations() {
        CreatureAffinityData data = new CreatureAffinityData();
        UUID friend = UUID.nameUUIDFromBytes("friend".getBytes());
        PlayerAffinity friendRelation = data.getOrCreate(friend, 0);
        friendRelation.setAffinity(0, 0, 100);
        friendRelation.setBefriended(true);

        for (int i = 0; i < CreatureAffinityData.MAX_ENTRIES + 5; i++) {
            PlayerAffinity relation = data.getOrCreate(UUID.nameUUIDFromBytes(("q" + i).getBytes()), 1);
            relation.setAffinity(1, 0, 100);
        }

        assertNotNull(data.get(friend), "a friend must never be trimmed away");
        assertTrue(data.hasAnyBefriended());
        assertEquals(CreatureAffinityData.MAX_ENTRIES + 6, data.size());
    }

    @Test
    void newLowAffinityPlayerCanAccumulateProgressBeyond64Relations() {
        CreatureAffinityData data = new CreatureAffinityData();
        for (int i = 0; i < 64; i++) data.getOrCreate(new UUID(0, i), 80).setBefriended(true);
        PlayerAffinity created = data.getOrCreate(ALICE, 0);
        created.addAffinity(5, 0, 100);
        assertEquals(created, data.get(ALICE));
        assertEquals(5, data.getOrCreate(ALICE, 0).getAffinity());
        assertEquals(65, data.size());
    }

    @Test
    void allRelationsAndTheirHistorySurviveSavingBeyondTheOldLimit() {
        CreatureAffinityData data = new CreatureAffinityData();
        for (int i = 0; i < 200; i++) {
            PlayerAffinity relation = data.getOrCreate(new UUID(0, i), i);
            relation.incrementMatingCount();
            relation.setRequestCooldownTicks(i);
        }
        CreatureAffinityData restored = CreatureAffinityData.fromNbt(data.toNbt());
        assertEquals(200, restored.size());
        for (int i = 0; i < 200; i++) {
            PlayerAffinity relation = restored.get(new UUID(0, i));
            assertNotNull(relation);
            assertEquals(i, relation.getAffinity());
            assertEquals(1, relation.getMatingCount());
            assertEquals(i, relation.getRequestCooldownTicks());
        }
    }

    @Test
    void nbtRoundTripKeepsEveryField() {
        CreatureAffinityData data = new CreatureAffinityData();
        data.setGeneration(3);

        PlayerAffinity alice = data.getOrCreate(ALICE, 42);
        alice.setAffinity(88, 0, 100);
        alice.setBefriended(true);
        alice.setTamedByUs(true);
        alice.setLastMatingTick(1234L);
        alice.incrementMatingCount();
        alice.incrementMatingCount();
        alice.incrementPeakCount();
        alice.incrementRejectionCount();
        alice.setGeneration(2);
        alice.setRequestCooldownTicks(120);

        PlayerAffinity bob = data.getOrCreate(BOB, 5);
        bob.setAffinity(-10, -20, 100);

        NbtCompound tag = data.toNbt();
        CreatureAffinityData restored = CreatureAffinityData.fromNbt(tag);

        assertEquals(3, restored.getGeneration());
        assertEquals(2, restored.size());

        PlayerAffinity restoredAlice = restored.get(ALICE);
        assertNotNull(restoredAlice);
        assertEquals(88, restoredAlice.getAffinity());
        assertTrue(restoredAlice.isBefriended());
        assertTrue(restoredAlice.isTamedByUs());
        assertEquals(1234L, restoredAlice.getLastMatingTick());
        assertEquals(2, restoredAlice.getMatingCount());
        assertEquals(1, restoredAlice.getPeakCount());
        assertEquals(1, restoredAlice.getRejectionCount());
        assertEquals(2, restoredAlice.getGeneration());
        assertEquals(120, restoredAlice.getRequestCooldownTicks());

        PlayerAffinity restoredBob = restored.get(BOB);
        assertNotNull(restoredBob);
        assertEquals(-10, restoredBob.getAffinity());
        assertFalse(restoredBob.isBefriended());
        assertEquals(-1L, restoredBob.getLastMatingTick());
    }

    @Test
    void emptyDataRoundTrips() {
        CreatureAffinityData restored = CreatureAffinityData.fromNbt(new CreatureAffinityData().toNbt());
        assertTrue(restored.isEmpty());
        assertEquals(0, restored.getGeneration());
    }

    @Test
    void highestAffinityPicksMaximum() {
        CreatureAffinityData data = new CreatureAffinityData();
        data.getOrCreate(ALICE, 10);
        data.getOrCreate(BOB, 90);
        PlayerAffinity carol = data.getOrCreate(CAROL, 50);
        carol.setAffinity(50, 0, 100);

        PlayerAffinity highest = data.highestAffinity();
        assertNotNull(highest);
        assertEquals(90, highest.getAffinity());
    }

    @Test
    void cooldownCountsDown() {
        PlayerAffinity relation = new PlayerAffinity(0);
        relation.setRequestCooldownTicks(2);

        assertTrue(relation.tickCooldown(), "still on cooldown after one tick");
        assertFalse(relation.tickCooldown(), "cooldown finished");
        assertFalse(relation.tickCooldown(), "zero stays zero");
    }

    @Test
    void cooldownResumesAfterReloadRatherThanRestartingOrDisappearing() {
        PlayerAffinity relation = new PlayerAffinity(10);
        relation.setRequestCooldownTicks(3);
        relation.tickCooldown();
        PlayerAffinity restored = PlayerAffinity.fromNbt(relation.toNbt());
        assertEquals(2, restored.getRequestCooldownTicks());
        assertTrue(restored.tickCooldown());
        assertFalse(restored.tickCooldown());
        assertEquals(0, PlayerAffinity.fromNbt(restored.toNbt()).getRequestCooldownTicks());
    }

    @Test
    void legacyAndInvalidCooldownsLoadAsZero() {
        NbtCompound tag = new NbtCompound();
        assertEquals(0, PlayerAffinity.fromNbt(tag).getRequestCooldownTicks());
        tag.putInt("request_cooldown_ticks", -10);
        assertEquals(0, PlayerAffinity.fromNbt(tag).getRequestCooldownTicks());
    }

    @Test
    void checksumChangesWhenRelationChanges() {
        CreatureAffinityData data = new CreatureAffinityData();
        PlayerAffinity relation = data.getOrCreate(ALICE, 0);
        long before = data.checksum(ALICE);

        relation.incrementMatingCount();
        long after = data.checksum(ALICE);

        assertTrue(before != after, "checksum must reflect the change");
        assertEquals(0L, data.checksum(CAROL), "unknown player has no checksum");
    }
}
