package com.nonid.affinity.net;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
/** Display-only full-energy completion; never an energy grant or a charged-item count. */
public record ReunionFullPayload(UUID receipt,Identifier dimension,String bond,long fuel,boolean offHand) implements CustomPayload {
 public ReunionFullPayload {
  if(bond.isEmpty()||bond.length()>128||fuel<3||fuel==Long.MAX_VALUE)throw new IllegalArgumentException("Invalid full-energy display receipt");
 }
 public static final Id<ReunionFullPayload> ID=new Id<>(Identifier.of("noncreatureaffinity","reunion_full"));
 public static final PacketCodec<RegistryByteBuf,ReunionFullPayload> CODEC=new PacketCodec<>(){
  public ReunionFullPayload decode(RegistryByteBuf b){return new ReunionFullPayload(b.readUuid(),b.readIdentifier(),b.readString(128),b.readVarLong(),b.readBoolean());}
  public void encode(RegistryByteBuf b,ReunionFullPayload p){b.writeUuid(p.receipt);b.writeIdentifier(p.dimension);b.writeString(p.bond,128);b.writeVarLong(p.fuel);b.writeBoolean(p.offHand);}
 };
 @Override public Id<? extends CustomPayload> getId(){return ID;}
}
