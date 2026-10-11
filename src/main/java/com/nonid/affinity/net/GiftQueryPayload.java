package com.nonid.affinity.net;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.*;
public record GiftQueryPayload(UUID session,long sequence) implements CustomPayload {
 public static final Id<GiftQueryPayload> ID=new Id<>(Identifier.of("noncreatureaffinity","gift_query"));
 public static final PacketCodec<RegistryByteBuf,GiftQueryPayload> CODEC=PacketCodec.tuple(Uuids.PACKET_CODEC,GiftQueryPayload::session,PacketCodecs.VAR_LONG,GiftQueryPayload::sequence,GiftQueryPayload::new);
 @Override public Id<? extends CustomPayload> getId(){return ID;}
}
