package com.nonid.affinity.net;

import com.nonid.affinity.NonCreatureAffinity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client to server: the animation the player picked from the menu.
 *
 * @param animationId full id, e.g. {@code needsofnature:wolfmplayer}
 */
public record AnimationChoicePayload(String animationId) implements CustomPayload {

    public static final CustomPayload.Id<AnimationChoicePayload> ID =
            new CustomPayload.Id<>(Identifier.of(NonCreatureAffinity.MOD_ID, "animation_choice"));

    public static final PacketCodec<RegistryByteBuf, AnimationChoicePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(256), AnimationChoicePayload::animationId,
            AnimationChoicePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
