package com.nonid.affinity.net;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client requests contain only a nonce; identities and duration come from the server. */
public final class CompanionHighlight {
    private CompanionHighlight() {}
    public record Request(UUID nonce) implements CustomPayload {
        public static final Id<Request> ID=new Id<>(Identifier.of("noncreatureaffinity","highlight_request"));
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,v)->b.writeUuid(v.nonce),b->new Request(b.readUuid()));
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Target(UUID id,boolean hasRelation,int affinity) {}
    public record Reply(UUID nonce,String dimension,int maximum,boolean accepted,List<Target> entries) implements CustomPayload {
        public Reply {entries=List.copyOf(entries);}
        public Reply(UUID nonce,String dimension,int maximum,List<Target> entries){this(nonce,dimension,maximum,true,entries);}
        public List<UUID> targets(){return entries.stream().map(Target::id).toList();}
        /** Legacy test/source callers describe absent relations, never fabricated positive affinity. */
        public Reply(UUID nonce,String dimension,List<UUID> targets){this(nonce,dimension,0,targets.stream().map(id->new Target(id,false,0)).toList());}
        public static final Id<Reply> ID=new Id<>(Identifier.of("noncreatureaffinity","highlight_colors"));
        public static final PacketCodec<RegistryByteBuf,Reply> CODEC=PacketCodec.ofStatic((b,v)->{
            b.writeUuid(v.nonce);b.writeString(v.dimension,256);b.writeVarInt(v.maximum);b.writeBoolean(v.accepted);b.writeVarInt(v.entries.size());for(var t:v.entries){b.writeUuid(t.id);b.writeBoolean(t.hasRelation);b.writeVarInt(t.affinity);}
        },b->{var nonce=b.readUuid();var dimension=b.readString(256);int maximum=b.readVarInt();boolean accepted=b.readBoolean();int count=b.readVarInt();if(count<0||count>16384)throw new IllegalArgumentException("Invalid highlight target count");var rows=new java.util.ArrayList<Target>(count);for(int i=0;i<count;i++)rows.add(new Target(b.readUuid(),b.readBoolean(),b.readVarInt()));return new Reply(nonce,dimension,maximum,accepted,rows);});
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
}

