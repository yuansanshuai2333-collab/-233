package com.nonid.affinity.net;

import com.nonid.affinity.NonCreatureAffinity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client to server: the player pressed the break-free key.
 *
 * <p>Carries no parameters on purpose: the server decides WHICH interaction is being broken out of from its
 * own per-instance state, so a client cannot name a target and cannot end someone else's animation.
 */
public record BreakFreePayload() implements CustomPayload {

    public static final CustomPayload.Id<BreakFreePayload> ID =
            new CustomPayload.Id<>(Identifier.of(NonCreatureAffinity.MOD_ID, "break_free"));

    public static final PacketCodec<RegistryByteBuf, BreakFreePayload> CODEC =
            PacketCodec.unit(new BreakFreePayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
