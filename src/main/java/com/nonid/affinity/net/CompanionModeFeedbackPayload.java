package com.nonid.affinity.net;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

/** Server-confirmed companion mode feedback; it carries display facts only. */
public record CompanionModeFeedbackPayload(UUID receipt, Identifier dimension, UUID target, Text name, Text mode)
        implements CustomPayload {
    public CompanionModeFeedbackPayload {
        if (name.getString().length() > 256 || mode.getString().length() > 256) {
            throw new IllegalArgumentException("Invalid companion mode feedback");
        }
    }

    public static final Id<CompanionModeFeedbackPayload> ID =
            new Id<>(Identifier.of("noncreatureaffinity", "companion_mode_feedback"));
    public static final PacketCodec<RegistryByteBuf, CompanionModeFeedbackPayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> {
                buf.writeUuid(payload.receipt());
                buf.writeIdentifier(payload.dimension());
                buf.writeUuid(payload.target());
                TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, payload.name());
                TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, payload.mode());
            },
            buf -> new CompanionModeFeedbackPayload(buf.readUuid(), buf.readIdentifier(), buf.readUuid(),
                    TextCodecs.REGISTRY_PACKET_CODEC.decode(buf), TextCodecs.REGISTRY_PACKET_CODEC.decode(buf)));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
