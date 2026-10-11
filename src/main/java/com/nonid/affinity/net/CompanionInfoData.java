package com.nonid.affinity.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** One atomic authoritative snapshot. Unknown numeric fields use -1, never an invented zero. */
public record CompanionInfoData(CompanionInfoQuery query, String status, String name, String type,
        String stage, int affinity, int maximum, int friendThreshold, float health, float maxHealth,
        double armor, double attack, String owner, String control, String mode, String restriction,
        String family, double distance, int completed, int deep, int agreed, int rejected, int tokens)
        implements CustomPayload {
    public static final Id<CompanionInfoData> ID = new Id<>(Identifier.of("noncreatureaffinity", "companion_info_data"));
    public static final PacketCodec<RegistryByteBuf, CompanionInfoData> CODEC = PacketCodec.ofStatic(
            (b, v) -> {
                CompanionInfoQuery.CODEC.encode(b, v.query);
                b.writeString(v.status, 32); b.writeString(v.name, 256); b.writeString(v.type, 256);
                b.writeString(v.stage, 24); b.writeInt(v.affinity); b.writeInt(v.maximum); b.writeInt(v.friendThreshold);
                b.writeFloat(v.health); b.writeFloat(v.maxHealth); b.writeDouble(v.armor); b.writeDouble(v.attack);
                b.writeString(v.owner, 24); b.writeString(v.control, 24); b.writeString(v.mode, 24);
                b.writeString(v.restriction, 32); b.writeString(v.family, 64); b.writeDouble(v.distance);
                b.writeInt(v.completed); b.writeInt(v.deep); b.writeInt(v.agreed); b.writeInt(v.rejected); b.writeInt(v.tokens);
            }, b -> new CompanionInfoData(CompanionInfoQuery.CODEC.decode(b), b.readString(32), b.readString(256),
                    b.readString(256), b.readString(24), b.readInt(), b.readInt(), b.readInt(), b.readFloat(), b.readFloat(),
                    b.readDouble(), b.readDouble(), b.readString(24), b.readString(24), b.readString(24), b.readString(32),
                    b.readString(64), b.readDouble(), b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readInt()));
    public static CompanionInfoData unavailable(CompanionInfoQuery query, String reason) {
        return new CompanionInfoData(query, reason, "", "", "", 0, 0, 0, -1, -1, -1, -1,
                "unknown", "unknown", "unknown", "unknown", "", -1, -1, -1, -1, -1, -1);
    }
    public boolean hasEntity() { return status.equals("ready") || status.equals("no_relation"); }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
