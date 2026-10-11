package com.nonid.affinity.net;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

/** Private display receipt; clients cannot request a reward through this S2C channel. */
public record FriendshipTokenPayload(UUID receipt, UUID target, Identifier dimension) implements CustomPayload {
    public static final Id<FriendshipTokenPayload> ID=new Id<>(Identifier.of("noncreatureaffinity","friendship_token"));
    public static final PacketCodec<RegistryByteBuf,FriendshipTokenPayload> CODEC=PacketCodec.tuple(
            Uuids.PACKET_CODEC,FriendshipTokenPayload::receipt,Uuids.PACKET_CODEC,FriendshipTokenPayload::target,
            Identifier.PACKET_CODEC,FriendshipTokenPayload::dimension,FriendshipTokenPayload::new);
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
