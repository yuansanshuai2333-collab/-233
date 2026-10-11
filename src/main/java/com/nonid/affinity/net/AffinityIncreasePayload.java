package com.nonid.affinity.net;

import com.nonid.affinity.NonCreatureAffinity;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

/** Display-only, sent to the participant after the authoritative relationship changed. */
public record AffinityIncreasePayload(UUID target, Identifier dimension, int before, int after, int maximum)
        implements CustomPayload {
    public static final Id<AffinityIncreasePayload> ID = new Id<>(
            Identifier.of(NonCreatureAffinity.MOD_ID, "affinity_increase"));
    public static final PacketCodec<RegistryByteBuf, AffinityIncreasePayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, AffinityIncreasePayload::target,
            Identifier.PACKET_CODEC, AffinityIncreasePayload::dimension,
            PacketCodecs.VAR_INT, AffinityIncreasePayload::before,
            PacketCodecs.VAR_INT, AffinityIncreasePayload::after,
            PacketCodecs.VAR_INT, AffinityIncreasePayload::maximum, AffinityIncreasePayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
