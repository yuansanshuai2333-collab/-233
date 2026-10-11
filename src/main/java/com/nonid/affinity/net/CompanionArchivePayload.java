package com.nonid.affinity.net;

import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** A client names an operation/record, never a viewer, affinity, death state, binding or result. */
public final class CompanionArchivePayload {
    private CompanionArchivePayload() {}
    public record Request(UUID page, long sequence, String operation, String root, long cursor, int limit, UUID nonce) implements CustomPayload {
        public static final Id<Request> ID = new Id<>(Identifier.of("noncreatureaffinity", "companion_archive_query"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.ofStatic((b, v) -> {
            b.writeUuid(v.page()); b.writeLong(v.sequence()); b.writeString(v.operation(), 16); b.writeString(v.root(), 80);
            b.writeLong(v.cursor()); b.writeVarInt(v.limit()); b.writeUuid(v.nonce());
        }, b -> new Request(b.readUuid(), b.readLong(), b.readString(16), b.readString(80), b.readLong(), b.readVarInt(), b.readUuid()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record Reply(UUID page, long sequence, String operation, String status, NbtCompound body) implements CustomPayload {
        public Reply { body = body.copy(); }
        public static final Id<Reply> ID = new Id<>(Identifier.of("noncreatureaffinity", "companion_archive_data"));
        public static final PacketCodec<RegistryByteBuf, Reply> CODEC = PacketCodec.ofStatic((b, v) -> {
            b.writeUuid(v.page()); b.writeLong(v.sequence()); b.writeString(v.operation(), 16); b.writeString(v.status(), 32); b.writeNbt(v.body());
        }, b -> new Reply(b.readUuid(), b.readLong(), b.readString(16), b.readString(32), b.readNbt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
}
