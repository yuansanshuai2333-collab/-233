package com.nonid.affinity.net;

import com.nonid.affinity.NonCreatureAffinity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server to client: the authoritative relation values between the player and one creature.
 *
 * <p>Carries the target uuid so a reply that arrives late cannot be shown for a newer target.
 *
 * @param targetUuid  the creature these values belong to
 * @param found       false when the player has no relation with that creature yet
 * @param affinity    current affinity
 * @param maxAffinity configured maximum, so the client bar scales correctly
 * @param matings     completed mating count
 * @param friend      whether the creature has reached friend status
 * @param companionMode "FOLLOW", "WANDER", or "" when this player is not its companion owner
 * @param friendThreshold affinity needed to become a friend, used for the panel's progress bar
 * @param tokensReceived tokens this player has actually received from this creature
 */
public record AffinityDataPayload(java.util.UUID targetUuid, boolean found, int affinity,
                                  int maxAffinity, int matings, boolean friend, String companionMode,
                                  int friendThreshold, int tokensReceived)
        implements CustomPayload {

    public static final CustomPayload.Id<AffinityDataPayload> ID =
            new CustomPayload.Id<>(Identifier.of(NonCreatureAffinity.MOD_ID, "affinity_data"));

    public static final PacketCodec<RegistryByteBuf, AffinityDataPayload> CODEC = PacketCodec.tuple(
            net.minecraft.util.Uuids.PACKET_CODEC, AffinityDataPayload::targetUuid,
            PacketCodecs.BOOLEAN, AffinityDataPayload::found,
            PacketCodecs.VAR_INT, AffinityDataPayload::affinity,
            PacketCodecs.VAR_INT, AffinityDataPayload::maxAffinity,
            PacketCodecs.VAR_INT, AffinityDataPayload::matings,
            PacketCodecs.BOOLEAN, AffinityDataPayload::friend,
            PacketCodecs.string(16), AffinityDataPayload::companionMode,
            PacketCodecs.VAR_INT, AffinityDataPayload::friendThreshold,
            PacketCodecs.VAR_INT, AffinityDataPayload::tokensReceived,
            AffinityDataPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
