package com.nonid.affinity.net;

import com.nonid.affinity.NonCreatureAffinity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client to server: the player pressed the request key and wants a list of animations to choose from.
 *
 * <p>Carries no target: the server resolves the creature from the player's view and range.
 */
public record AnimationMenuRequestPayload() implements CustomPayload {

    public static final CustomPayload.Id<AnimationMenuRequestPayload> ID =
            new CustomPayload.Id<>(Identifier.of(NonCreatureAffinity.MOD_ID, "animation_menu_request"));

    public static final PacketCodec<RegistryByteBuf, AnimationMenuRequestPayload> CODEC =
            PacketCodec.unit(new AnimationMenuRequestPayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
