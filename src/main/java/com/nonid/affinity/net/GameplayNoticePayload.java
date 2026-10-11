package com.nonid.affinity.net;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
/** Private display facts, never a client request or a gameplay decision. */
public record GameplayNoticePayload(UUID receipt,Identifier dimension,int kind,int priority,Text title,Text detail,int before,int after,int maximum) implements CustomPayload {
 public GameplayNoticePayload {
  if(kind<0||kind>3||priority<0||priority>2||maximum<0||title.getString().length()>2048||detail.getString().length()>2048)throw new IllegalArgumentException("Invalid gameplay notice");
 }
 public static final Id<GameplayNoticePayload> ID=new Id<>(Identifier.of("noncreatureaffinity","gameplay_notice"));
 public static final PacketCodec<RegistryByteBuf,GameplayNoticePayload> CODEC=PacketCodec.ofStatic((b,p)->{
  b.writeUuid(p.receipt);b.writeIdentifier(p.dimension);b.writeVarInt(p.kind);b.writeVarInt(p.priority);
  TextCodecs.REGISTRY_PACKET_CODEC.encode(b,p.title);TextCodecs.REGISTRY_PACKET_CODEC.encode(b,p.detail);
  b.writeVarInt(p.before);b.writeVarInt(p.after);b.writeVarInt(p.maximum);
 },b->new GameplayNoticePayload(b.readUuid(),b.readIdentifier(),b.readVarInt(),b.readVarInt(),TextCodecs.REGISTRY_PACKET_CODEC.decode(b),TextCodecs.REGISTRY_PACKET_CODEC.decode(b),b.readVarInt(),b.readVarInt(),b.readVarInt()));
 @Override public Id<? extends CustomPayload> getId(){return ID;}
}
