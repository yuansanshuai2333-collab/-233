package com.nonid.affinity.net;

import com.nonid.affinity.NonCreatureAffinity;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

/** An answer names one issued request, never a nearby or subsequent request. */
public record RequestAnswerPayload(UUID mob, String dimension, long token, long expiry, boolean accept)
        implements CustomPayload {
    public static final Id<RequestAnswerPayload> ID = new Id<>(Identifier.of(NonCreatureAffinity.MOD_ID, "request_answer"));
    public static final PacketCodec<RegistryByteBuf, RequestAnswerPayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, RequestAnswerPayload::mob,
            PacketCodecs.string(256), RequestAnswerPayload::dimension,
            PacketCodecs.VAR_LONG, RequestAnswerPayload::token,
            PacketCodecs.VAR_LONG, RequestAnswerPayload::expiry,
            PacketCodecs.BOOLEAN, RequestAnswerPayload::accept, RequestAnswerPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
