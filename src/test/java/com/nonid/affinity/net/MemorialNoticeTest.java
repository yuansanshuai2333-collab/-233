package com.nonid.affinity.net;

import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MemorialNoticeTest {
    @Test void roundTripAndExplicitCancellation() {
        var buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            var notice = new MemorialNotice(UUID.randomUUID(), "minecraft:overworld", 160, List.of("第一位伙伴", "第二位伙伴"), 7);
            MemorialNotice.CODEC.encode(buffer, notice); assertEquals(notice, MemorialNotice.CODEC.decode(buffer));
            var clear = new MemorialNotice(notice.token(), notice.dimension(), 0, List.of(), 0);
            MemorialNotice.CODEC.encode(buffer, clear); assertEquals(clear, MemorialNotice.CODEC.decode(buffer));
            var unnamed = new MemorialNotice(notice.token(), notice.dimension(), 160, List.of(""), List.of("minecraft:pig"), 1);
            MemorialNotice.CODEC.encode(buffer, unnamed); assertEquals(unnamed, MemorialNotice.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void rejectsUnboundedNameCountBeforeReadingOrAllocatingNames() {
        var buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            buffer.writeUuid(UUID.randomUUID()); buffer.writeString("minecraft:overworld"); buffer.writeVarInt(160); buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> MemorialNotice.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void rejectsInvalidDurationAndAmbiguousClearState() {
        UUID token = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new MemorialNotice(token, "minecraft:overworld", 161, List.of("伙伴"), 1));
        assertThrows(IllegalArgumentException.class, () -> new MemorialNotice(token, "minecraft:overworld", 0, List.of("伙伴"), 1));
        assertThrows(IllegalArgumentException.class, () -> new MemorialNotice(token, "minecraft:overworld", 160, List.of(), 0));
    }
}
