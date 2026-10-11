package com.nonid.affinity.gift;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GiftLedgerSerializationTest {
    @Test
    void malformedPairAndEnvelopeRecordsAreSkippedWithoutReplacement() {
        UUID viewer = UUID.randomUUID();
        String creature = "entity:" + UUID.randomUUID();
        var seed = new GiftLedger();
        seed.track(viewer, creature);
        var root = seed.toNbt();

        var pairs = root.getCompoundOrEmpty("pairs");
        var owned = pairs.getCompoundOrEmpty(viewer.toString());
        owned.put("entity:malformed", new NbtCompound());
        pairs.put(viewer.toString(), owned);
        pairs.put("not-a-uuid", new NbtCompound());
        root.put("pairs", pairs);

        var gifts = root.getCompoundOrEmpty("gifts");
        gifts.put("not-a-uuid", new NbtCompound());
        root.put("gifts", gifts);

        GiftLedger restored = assertDoesNotThrow(() -> GiftLedger.fromNbt(root));

        assertEquals(1, restored.cycles().size());
        assertEquals(viewer, restored.cycles().getFirst().viewer());
        assertEquals(creature, restored.cycles().getFirst().creature());
        assertTrue(restored.all().isEmpty());
    }
}
