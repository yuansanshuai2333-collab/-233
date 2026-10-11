package com.nonid.affinity.net;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
public record ReunionNoticePayload(UUID receipt,Identifier dimension,int kind,int requiredEnergy) implements CustomPayload {
 public static final Id<ReunionNoticePayload> ID=new Id<>(Identifier.of("noncreatureaffinity","reunion_notice"));
 public ReunionNoticePayload {
  if(kind<0||kind>3||(kind==0?requiredEnergy!=1&&requiredEnergy!=3:requiredEnergy!=0))throw new IllegalArgumentException("Invalid notice");
 }
 public static final PacketCodec<RegistryByteBuf,ReunionNoticePayload> CODEC=PacketCodec.ofStatic((b,p)->{b.writeUuid(p.receipt);b.writeIdentifier(p.dimension);b.writeVarInt(p.kind);b.writeVarInt(p.requiredEnergy);},b->new ReunionNoticePayload(b.readUuid(),b.readIdentifier(),b.readVarInt(),b.readVarInt()));
 @Override public Id<? extends CustomPayload> getId(){return ID;}
}
