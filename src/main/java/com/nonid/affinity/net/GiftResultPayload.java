package com.nonid.affinity.net;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.*;
public record GiftResultPayload(UUID session,UUID gift,String status) implements CustomPayload {
 public static final Id<GiftResultPayload> ID=new Id<>(Identifier.of("noncreatureaffinity","gift_result"));
 public static final PacketCodec<RegistryByteBuf,GiftResultPayload> CODEC=PacketCodec.tuple(Uuids.PACKET_CODEC,GiftResultPayload::session,Uuids.PACKET_CODEC,GiftResultPayload::gift,PacketCodecs.string(64),GiftResultPayload::status,GiftResultPayload::new);
 @Override public Id<? extends CustomPayload> getId(){return ID;}
}
