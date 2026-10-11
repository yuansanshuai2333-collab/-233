package com.nonid.affinity.net;

import java.util.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Private saved-location notice. No other viewers' records or navigation outside its range. */
public record MemorialNotice(UUID token, String dimension, int ticks, List<String> names, List<String> types, int total, boolean located, double x, double y, double z) implements CustomPayload {
    public MemorialNotice(UUID token, String dimension, int ticks, List<String> names, List<String> types, int total) {
        this(token, dimension, ticks, names, types, total, false, 0, 0, 0);
    }
    public MemorialNotice(UUID token, String dimension, int ticks, List<String> names, int total) {
        this(token, dimension, ticks, names, Collections.nCopies(names.size(), ""), total);
    }
    public MemorialNotice {
        names = List.copyOf(names);
        types = List.copyOf(types);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || token == null || dimension == null || dimension.length() > 256 || ticks < 0 || ticks > 160
                || names.size() > 2 || types.size() != names.size() || total < names.size()
                || names.stream().anyMatch(name -> name.length() > 256) || types.stream().anyMatch(type -> type.length() > 256))
            throw new IllegalArgumentException("Invalid memorial notice");
        if (ticks == 0 ? !names.isEmpty() || total != 0 : names.isEmpty()) throw new IllegalArgumentException("Invalid memorial state");
    }
    public static final Id<MemorialNotice> ID = new Id<>(Identifier.of("noncreatureaffinity", "memorial_notice"));
    public static final PacketCodec<RegistryByteBuf, MemorialNotice> CODEC = PacketCodec.ofStatic((b, v) -> {
        b.writeUuid(v.token()); b.writeString(v.dimension(), 256); b.writeVarInt(v.ticks()); b.writeVarInt(v.names().size());
        for (int i = 0; i < v.names().size(); i++) { b.writeString(v.names().get(i), 256); b.writeString(v.types().get(i), 256); }
        b.writeVarInt(v.total()); b.writeBoolean(v.located()); b.writeDouble(v.x()); b.writeDouble(v.y()); b.writeDouble(v.z());
    }, b -> {
        UUID token = b.readUuid(); String dimension = b.readString(256); int ticks = b.readVarInt(); int count = b.readVarInt();
        if (count < 0 || count > 2) throw new IllegalArgumentException("Invalid memorial name count");
        var names = new ArrayList<String>(); var types = new ArrayList<String>();
        for (int i = 0; i < count; i++) { names.add(b.readString(256)); types.add(b.readString(256)); }
        return new MemorialNotice(token, dimension, ticks, names, types, b.readVarInt(), b.readBoolean(), b.readDouble(), b.readDouble(), b.readDouble());
    });
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
