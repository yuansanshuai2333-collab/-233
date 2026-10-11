package com.nonid.affinity.net;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Identity and context only; the client supplies no relationship or permission values. */
public record CompanionInfoQuery(UUID page, long sequence, UUID target, String dimension) implements CustomPayload {
    public static final Id<CompanionInfoQuery> ID = new Id<>(Identifier.of("noncreatureaffinity", "companion_info_query"));
    public static final PacketCodec<RegistryByteBuf, CompanionInfoQuery> CODEC = PacketCodec.ofStatic(
            (b, v) -> { b.writeUuid(v.page); b.writeVarLong(v.sequence); b.writeUuid(v.target); b.writeString(v.dimension, 256); },
            b -> new CompanionInfoQuery(b.readUuid(), b.readVarLong(), b.readUuid(), b.readString(256)));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
