package com.nonid.affinity.net;

import com.nonid.affinity.NonCreatureAffinity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client to server: the player pressed the request key and wants to mate with the creature
 * they are looking at.
 *
 * <p>Carries no parameters: the server re-resolves the target from the player's view and
 * range, so a client cannot forge it.
 */
public record MatingRequestPayload() implements CustomPayload {

    public static final CustomPayload.Id<MatingRequestPayload> ID =
            new CustomPayload.Id<>(Identifier.of(NonCreatureAffinity.MOD_ID, "mating_request"));

    public static final PacketCodec<RegistryByteBuf, MatingRequestPayload> CODEC =
            PacketCodec.unit(new MatingRequestPayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
