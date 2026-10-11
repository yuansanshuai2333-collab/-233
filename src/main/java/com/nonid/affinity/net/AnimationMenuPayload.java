package com.nonid.affinity.net;

import com.nonid.affinity.NonCreatureAffinity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server to client: the animations available for the aimed creature.
 *
 * <p>Each entry is {@code id|displayName|eligible}; entries are joined with newlines because a flat
 * payload keeps the codec simple and the list is always small.
 */
public record AnimationMenuPayload(String mobName, String entries) implements CustomPayload {

    public static final CustomPayload.Id<AnimationMenuPayload> ID =
            new CustomPayload.Id<>(Identifier.of(NonCreatureAffinity.MOD_ID, "animation_menu"));

    public static final PacketCodec<RegistryByteBuf, AnimationMenuPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(64), AnimationMenuPayload::mobName,
            PacketCodecs.string(8192), AnimationMenuPayload::entries,
            AnimationMenuPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Parse the packed entries back into rows. */
    public List<String[]> rows() {
        List<String[]> result = new ArrayList<>();
        if (this.entries == null || this.entries.isEmpty()) {
            return result;
        }
        for (String line : this.entries.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\|", 3);
            // Defensive parsing: skip entries that are not a usable animation id.
            if (parts.length == 0 || !parts[0].contains(":") || parts[0].length() > 200) {
                continue;
            }
            result.add(parts);
        }
        return result;
    }
}
