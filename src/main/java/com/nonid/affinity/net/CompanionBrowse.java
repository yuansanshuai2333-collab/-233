package com.nonid.affinity.net;

import java.util.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class CompanionBrowse {
    public static final int PAGE_SIZE=12;
    private CompanionBrowse() {}
    public record Request(UUID page,long sequence,int index,String selected) implements CustomPayload {
        public static final Id<Request> ID=new Id<>(Identifier.of("noncreatureaffinity","companion_browse"));
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,v)->{
            b.writeUuid(v.page);b.writeVarLong(v.sequence);b.writeVarInt(v.index);b.writeString(v.selected,80);
        },b->new Request(b.readUuid(),b.readVarLong(),b.readVarInt(),b.readString(80)));
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Reply(UUID page,long sequence,UUID session,int index,boolean more,String status,List<CompanionRecord> rows) implements CustomPayload {
        public static final Id<Reply> ID=new Id<>(Identifier.of("noncreatureaffinity","companion_browse_reply"));
        public static final PacketCodec<RegistryByteBuf,Reply> CODEC=PacketCodec.ofStatic((b,v)->{
            b.writeUuid(v.page);b.writeVarLong(v.sequence);b.writeUuid(v.session);b.writeVarInt(v.index);
            b.writeBoolean(v.more);b.writeString(v.status,24);b.writeVarInt(v.rows.size());for(var row:v.rows)CompanionRecord.CODEC.encode(b,row);
        },b->{var page=b.readUuid();long sequence=b.readVarLong();var session=b.readUuid();int index=b.readVarInt();boolean more=b.readBoolean();String status=b.readString(24);
            int count=b.readVarInt();if(count<0||count>PAGE_SIZE)throw new IllegalArgumentException("Invalid directory page length");
            var rows=new ArrayList<CompanionRecord>(count);for(int i=0;i<count;i++)rows.add(CompanionRecord.CODEC.decode(b));
            return new Reply(page,sequence,session,index,more,status,List.copyOf(rows));});
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
}
