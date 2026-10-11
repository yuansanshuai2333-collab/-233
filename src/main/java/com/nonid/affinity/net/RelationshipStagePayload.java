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
public record RelationshipStagePayload(UUID targetUuid, String label) implements CustomPayload {
    public static final Id<RelationshipStagePayload> ID = new Id<>(
            Identifier.of(NonCreatureAffinity.MOD_ID, "relationship_stage"));
    public static final PacketCodec<RegistryByteBuf, RelationshipStagePayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, RelationshipStagePayload::targetUuid,
            PacketCodecs.string(16), RelationshipStagePayload::label, RelationshipStagePayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}

