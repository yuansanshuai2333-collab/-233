package com.nonid.affinity.net;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.*;
/** Sent only after actual reward delivery. Wrapping comes from the validated saved gift. */
public record GiftOpeningPayload(UUID gift,Identifier dimension,int wrapping) implements CustomPayload {
 public static final Id<GiftOpeningPayload> ID=new Id<>(Identifier.of("noncreatureaffinity","gift_opening"));
 public static final PacketCodec<RegistryByteBuf,GiftOpeningPayload> CODEC=PacketCodec.tuple(Uuids.PACKET_CODEC,GiftOpeningPayload::gift,Identifier.PACKET_CODEC,GiftOpeningPayload::dimension,PacketCodecs.VAR_INT,GiftOpeningPayload::wrapping,GiftOpeningPayload::new);
 @Override public Id<? extends CustomPayload> getId(){return ID;}
}
