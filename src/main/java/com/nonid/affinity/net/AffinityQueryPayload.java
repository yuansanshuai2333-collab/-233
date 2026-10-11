package com.nonid.affinity.net;

import com.nonid.affinity.NonCreatureAffinity;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

/**
 * Client to server: request the authoritative relation values for a specific creature.
 *
 * <p>Carries the target uuid. Without it the server re-resolved the target from the player's view,
 * so under latency the reply could describe a different creature than the client was waiting for.
 */
public record AffinityQueryPayload(UUID targetUuid) implements CustomPayload {

    public static final CustomPayload.Id<AffinityQueryPayload> ID =
            new CustomPayload.Id<>(Identifier.of(NonCreatureAffinity.MOD_ID, "affinity_query"));

    public static final PacketCodec<RegistryByteBuf, AffinityQueryPayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, AffinityQueryPayload::targetUuid,
            AffinityQueryPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
