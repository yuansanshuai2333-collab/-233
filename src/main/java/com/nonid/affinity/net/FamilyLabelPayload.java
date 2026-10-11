package com.nonid.affinity.net;

import java.util.UUID;
import com.nonid.affinity.NonCreatureAffinity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

/** Optional separate channel keeps the existing affinity packet format unchanged. */
public record FamilyLabelPayload(UUID targetUuid, String label) implements CustomPayload {
    public static final Id<FamilyLabelPayload> ID = new Id<>(
            Identifier.of(NonCreatureAffinity.MOD_ID, "family_label"));
    public static final PacketCodec<RegistryByteBuf, FamilyLabelPayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, FamilyLabelPayload::targetUuid,
            PacketCodecs.string(16), FamilyLabelPayload::label, FamilyLabelPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
