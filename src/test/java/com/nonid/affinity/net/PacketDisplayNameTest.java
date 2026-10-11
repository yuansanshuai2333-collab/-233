package com.nonid.affinity.net;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.EncoderException;
import java.util.UUID;
import net.minecraft.network.encoding.StringEncoding;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PacketDisplayNameTest {
    @Test void originalOverlongRequestNameFailsTheMinecraftEncoder() {
        var buffer = Unpooled.buffer();
        try {
            assertThrows(EncoderException.class, () -> StringEncoding.encode(buffer, "村".repeat(65), 64));
        } finally { buffer.release(); }
    }

    @Test void requestPayloadBoundsItsNameBeforeEncoding() {
        UUID id = UUID.randomUUID();
        var payload = new RequestStatePayload(id, "村".repeat(200), 500, 80, 60);
        assertEquals(id, payload.mobUuid());
        assertEquals(500, payload.expireTick());
        assertEquals(64, payload.mobName().length());
        assertRoundTrip(payload.mobName(), 64);
    }

    @Test void fullRequestPacketRoundTripPreservesIdentityAndRequestValues() {
        var payload = new RequestStatePayload(UUID.randomUUID(), "狼".repeat(100), 500, 80, 60);
        var buffer = new net.minecraft.network.RegistryByteBuf(Unpooled.buffer(),
                net.minecraft.registry.DynamicRegistryManager.EMPTY);
        try {
            RequestStatePayload.CODEC.encode(buffer, payload);
            assertEquals(payload, RequestStatePayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void truncationDoesNotSplitAnEmoji() {
        String name = "村".repeat(63) + "🐺" + "后缀";
        String fitted = new RequestStatePayload(UUID.randomUUID(), name, 500, 80, 60).mobName();
        assertEquals("村".repeat(63), fitted);
        assertRoundTrip(fitted, 64);
    }

    @Test void exactBoundaryAndEmptyCancellationNamesArePreserved() {
        String name = "村".repeat(62) + "🐺";
        assertEquals(name, PacketDisplayName.fit(name, 64));
        assertRoundTrip(name, 64);
        assertEquals("", new RequestStatePayload(UUID.randomUUID(), null, 0, 0, 0).mobName());
    }

    @Test void tradeNamesUseTheSameUnicodeBoundaryRule() {
        var payload = new FreeTradeStatusPayload(1, UUID.randomUUID(), 1, 1, 0, false,
                0, 0, 1, "TRADE_UNAVAILABLE", "村".repeat(127) + "🐺尾", 1);
        assertEquals("村".repeat(127), payload.firstItemName());
        assertRoundTrip(payload.firstItemName(), 128);
    }

    private static void assertRoundTrip(String name, int limit) {
        var buffer = Unpooled.buffer();
        try {
            StringEncoding.encode(buffer, name, limit);
            assertEquals(name, StringEncoding.decode(buffer, limit));
        } finally { buffer.release(); }
    }
}
