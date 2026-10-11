package com.nonid.affinity.net;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.*;
public record GiftClaimPayload(UUID session,UUID gift,UUID target,boolean bequest) implements CustomPayload {
 public static final Id<GiftClaimPayload> ID=new Id<>(Identifier.of("noncreatureaffinity","gift_claim"));
 public static final PacketCodec<RegistryByteBuf,GiftClaimPayload> CODEC=PacketCodec.tuple(Uuids.PACKET_CODEC,GiftClaimPayload::session,Uuids.PACKET_CODEC,GiftClaimPayload::gift,Uuids.PACKET_CODEC,GiftClaimPayload::target,PacketCodecs.BOOLEAN,GiftClaimPayload::bequest,GiftClaimPayload::new);
 @Override public Id<? extends CustomPayload> getId(){return ID;}
}
