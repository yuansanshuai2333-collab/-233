package com.nonid.affinity.net;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
/** A committed energy change, never a request to add energy. */
public record ReunionChargePayload(UUID receipt,Identifier dimension,String bond,long fuel,int before,int after,boolean offHand) implements CustomPayload {
 public ReunionChargePayload {
  if(bond.isEmpty()||bond.length()>128||before<0||after>3||after<=before||fuel<after)throw new IllegalArgumentException("Invalid committed charge receipt");
 }
 public static final Id<ReunionChargePayload> ID=new Id<>(Identifier.of("noncreatureaffinity","reunion_charge"));
 public static final PacketCodec<RegistryByteBuf,ReunionChargePayload> CODEC=new PacketCodec<>(){
  public ReunionChargePayload decode(RegistryByteBuf b){return new ReunionChargePayload(b.readUuid(),b.readIdentifier(),b.readString(128),b.readVarLong(),b.readVarInt(),b.readVarInt(),b.readBoolean());}
  public void encode(RegistryByteBuf b,ReunionChargePayload p){b.writeUuid(p.receipt);b.writeIdentifier(p.dimension);b.writeString(p.bond,128);b.writeVarLong(p.fuel);b.writeVarInt(p.before);b.writeVarInt(p.after);b.writeBoolean(p.offHand);}
 };
 @Override public Id<? extends CustomPayload> getId(){return ID;}
}
