package com.nonid.affinity.data;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerAffinitySerializationTest {
    @Test
    void legacyRelationWithoutQuotaMarkerStaysUninitialized() {
        var saved = new NbtCompound();
        saved.putInt("affinity", 12);

        var relation = PlayerAffinity.fromNbt(saved);

        assertFalse(relation.isQuotaInitialized());
        var rewritten = relation.toNbt();
        assertFalse(rewritten.contains("quota_initialized"));
        assertFalse(rewritten.contains("free_trades_left"));
    }

    @Test
    void quotaMarkerRoundTripsItsPersistedGroup() {
        var saved = new NbtCompound();
        saved.putInt("affinity", 24);
        saved.putBoolean("quota_initialized", true);
        saved.putInt("free_trades_left", 2);
        saved.putLong("last_hero_epoch", 7L);
        saved.putLong("last_cure_epoch", 8L);
        saved.putLong("observed_cure_epoch", 9L);
        saved.putString("last_cure_source", "conversion-1");
        saved.putLong("quota_revision", 10L);
        saved.putInt("total_free_trades", 11);
        saved.putLong("total_completions", 12L);

        var relation = PlayerAffinity.fromNbt(saved);

        assertTrue(relation.isQuotaInitialized());
        assertEquals(2, relation.getRemainingFreeTrades());
        assertEquals(7L, relation.getLastAppliedHeroEpoch());
        assertEquals(8L, relation.getLastAppliedCureEpoch());
        assertEquals(9L, relation.getObservedCureEpoch());
        assertEquals("conversion-1", relation.getLastCureSourceId());
        assertEquals(10L, relation.getQuotaRevision());
        assertEquals(11, relation.getTotalFreeTrades());
        assertEquals(12L, relation.getTotalCompletions());
    }
}
