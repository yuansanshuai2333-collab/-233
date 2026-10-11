package com.nonid.affinity.net;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
/** Authenticated player comes from the connection; target is authorized against a private directory session. */
public record DirectInteractionOption(UUID page,long sequence,UUID session,String target,boolean write,boolean enabled,String status) implements CustomPayload {
    public static final Id<DirectInteractionOption> ID=new Id<>(Identifier.of("noncreatureaffinity","creature_direct_interaction"));
    public static final PacketCodec<RegistryByteBuf,DirectInteractionOption> CODEC=PacketCodec.ofStatic(
        (b,v)->{b.writeUuid(v.page);b.writeVarLong(v.sequence);b.writeUuid(v.session);b.writeString(v.target,80);b.writeBoolean(v.write);b.writeBoolean(v.enabled);b.writeString(v.status,24);},
        b->new DirectInteractionOption(b.readUuid(),b.readVarLong(),b.readUuid(),b.readString(80),b.readBoolean(),b.readBoolean(),b.readString(24)));
    /** Legacy source callers cannot write: missing target/session always fails authorization. */
    public DirectInteractionOption(UUID page,long sequence,boolean write,boolean enabled){this(page,sequence,new UUID(0,0),"",write,enabled,"invalid");}
    public DirectInteractionOption reply(String status,boolean value){return new DirectInteractionOption(page,sequence,session,target,false,value,status);}
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
