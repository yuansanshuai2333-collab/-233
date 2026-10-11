package com.nonid.affinity.net;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
/** Server-confirmed cosmetic result, private to the successful actor. */
public record ReunionSuccessPayload(UUID receipt, Identifier dimension, UUID creature, int entityId,
                                   boolean healing, Vec3d position, float width, float height) implements CustomPayload {
 public static final Id<ReunionSuccessPayload> ID=new Id<>(Identifier.of("noncreatureaffinity","reunion_success"));
 public ReunionSuccessPayload {
  if(!Double.isFinite(position.x)||!Double.isFinite(position.y)||!Double.isFinite(position.z)
    ||!Float.isFinite(width)||!Float.isFinite(height)||width<0||height<0)throw new IllegalArgumentException("Invalid cosmetic geometry");
 }
 public static final PacketCodec<RegistryByteBuf,ReunionSuccessPayload> CODEC=PacketCodec.ofStatic((b,p)->{
  b.writeUuid(p.receipt);b.writeIdentifier(p.dimension);b.writeUuid(p.creature);b.writeVarInt(p.entityId);b.writeBoolean(p.healing);
  b.writeDouble(p.position.x);b.writeDouble(p.position.y);b.writeDouble(p.position.z);b.writeFloat(p.width);b.writeFloat(p.height);
 },b->new ReunionSuccessPayload(b.readUuid(),b.readIdentifier(),b.readUuid(),b.readVarInt(),b.readBoolean(),new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readFloat()));
 @Override public Id<? extends CustomPayload> getId(){return ID;}
}
