package com.nonid.affinity.net;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Server-confirmed cosmetic event; contains no inventory or relationship snapshot. */
public record ReunionEffectPayload(UUID player, String bond, boolean offHand,
                                   Identifier dimension, Vec3d revivedAt, float creatureHeight) implements CustomPayload {
    public static final Id<ReunionEffectPayload> ID = new Id<>(Identifier.of("noncreatureaffinity", "reunion_effect_v2"));
    public static final PacketCodec<RegistryByteBuf, ReunionEffectPayload> CODEC = PacketCodec.ofStatic(
            (buf, value) -> {
                buf.writeUuid(value.player()); buf.writeString(value.bond(), 36); buf.writeBoolean(value.offHand());
                buf.writeIdentifier(value.dimension());
                buf.writeDouble(value.revivedAt().x); buf.writeDouble(value.revivedAt().y); buf.writeDouble(value.revivedAt().z);
                buf.writeFloat(value.creatureHeight());
            }, buf -> new ReunionEffectPayload(buf.readUuid(), buf.readString(36), buf.readBoolean(),
                    buf.readIdentifier(), new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat()));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
