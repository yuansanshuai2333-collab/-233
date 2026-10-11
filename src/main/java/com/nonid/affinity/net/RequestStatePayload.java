package com.nonid.affinity.net;

import com.nonid.affinity.NonCreatureAffinity;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Authoritative display snapshot. requestId orders HUD updates; token identifies business state. */
public record RequestStatePayload(UUID mobUuid, String mobName, long expireTick, int affinity,
        int threshold, long requestId, long token, String dimension, int desireStage, com.nonid.affinity.service.RequestEnergy repression) implements CustomPayload {
    public static final int MAX_NAME_LENGTH = 64;
    public RequestStatePayload(UUID mob,String name,long expiry,int affinity,int threshold,long id,long token,String dimension,int stage){
        this(mob,name,expiry,affinity,threshold,id,token,dimension,stage,com.nonid.affinity.service.RequestEnergy.UNKNOWN);
    }
    public RequestStatePayload(UUID mob, String name, long expiry, int affinity, int threshold, long id) {
        this(mob, name, expiry, affinity, threshold, id, 0, "", 0);
    }
    public RequestStatePayload(UUID mob, String name, long expiry, int affinity, int threshold) {
        this(mob, name, expiry, affinity, threshold, 0);
    }
    public RequestStatePayload {
        mobName = PacketDisplayName.fit(mobName, MAX_NAME_LENGTH);
        desireStage = desireStage >= 1 && desireStage <= 4 ? desireStage : 0;
    }
    public static final Id<RequestStatePayload> ID = new Id<>(Identifier.of(NonCreatureAffinity.MOD_ID, "request_state_v4"));
    public static final PacketCodec<RegistryByteBuf, RequestStatePayload> CODEC = new PacketCodec<>() {
        @Override public RequestStatePayload decode(RegistryByteBuf b) {
            return new RequestStatePayload(b.readUuid(), b.readString(MAX_NAME_LENGTH), b.readVarLong(),
                    b.readVarInt(), b.readVarInt(), b.readVarLong(), b.readVarLong(), b.readString(256), b.readVarInt(), new com.nonid.affinity.service.RequestEnergy(b.readVarInt(), b.readVarInt(), b.readBoolean()));
        }
        @Override public void encode(RegistryByteBuf b, RequestStatePayload p) {
            b.writeUuid(p.mobUuid()); b.writeString(p.mobName(), MAX_NAME_LENGTH); b.writeVarLong(p.expireTick());
            b.writeVarInt(p.affinity()); b.writeVarInt(p.threshold()); b.writeVarLong(p.requestId());
            b.writeVarLong(p.token()); b.writeString(p.dimension(), 256); b.writeVarInt(p.desireStage());
            b.writeVarInt(p.repression().value()); b.writeVarInt(p.repression().maximum()); b.writeBoolean(p.repression().initialized());
        }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}