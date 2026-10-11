package com.nonid.affinity.net;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Finishes or cancels only the matching in-flight wish; never grants client authority over revival. */
public record ReunionResultPayload(UUID player, String bond, boolean success) implements CustomPayload {
    public static final Id<ReunionResultPayload> ID = new Id<>(Identifier.of("noncreatureaffinity", "reunion_result"));
    public static final PacketCodec<RegistryByteBuf, ReunionResultPayload> CODEC = PacketCodec.ofStatic(
            (buf, value) -> { buf.writeUuid(value.player()); buf.writeString(value.bond(), 36); buf.writeBoolean(value.success()); },
            buf -> new ReunionResultPayload(buf.readUuid(), buf.readString(36), buf.readBoolean()));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
