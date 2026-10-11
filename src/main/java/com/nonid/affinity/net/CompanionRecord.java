package com.nonid.affinity.net;

import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

/** A viewer-specific observation, never an entity restore snapshot or an authority to control it. */
public record CompanionRecord(String id, long observedAt, String availability, CompanionInfoData info) {
    public static final PacketCodec<RegistryByteBuf, CompanionRecord> CODEC = PacketCodec.ofStatic((b,v)->{
        b.writeString(v.id,80); b.writeLong(v.observedAt); b.writeString(v.availability,24);
        CompanionInfoData.CODEC.encode(b,v.info);
    },b->new CompanionRecord(b.readString(80),b.readLong(),b.readString(24),CompanionInfoData.CODEC.decode(b)));
    public CompanionRecord historical(String state) {
        var d=info;
        return new CompanionRecord(id,observedAt,state,new CompanionInfoData(d.query(),d.status(),d.name(),d.type(),d.stage(),
                d.affinity(),d.maximum(),d.friendThreshold(),d.health(),d.maxHealth(),d.armor(),d.attack(),d.owner(),d.control(),
                d.mode(),d.restriction(),d.family(),-1,d.completed(),d.deep(),d.agreed(),d.rejected(),d.tokens()));
    }
    public NbtCompound toNbt() {
        var n=new NbtCompound();var d=info;
        n.putString("id",id);n.putLong("observed",observedAt);n.putString("state",availability);
        n.putString("entity",d.query().target().toString());n.putString("dimension",d.query().dimension());
        n.putString("name",d.name());n.putString("type",d.type());n.putString("stage",d.stage());
        n.putInt("affinity",d.affinity());n.putInt("maximum",d.maximum());n.putInt("friend",d.friendThreshold());
        n.putFloat("health",d.health());n.putFloat("max_health",d.maxHealth());
        n.putDouble("armor",d.armor());n.putDouble("attack",d.attack());
        n.putString("owner",d.owner());n.putString("control",d.control());n.putString("mode",d.mode());
        n.putString("restriction",d.restriction());n.putString("family",d.family());
        n.putInt("completed",d.completed());n.putInt("deep",d.deep());n.putInt("agreed",d.agreed());
        n.putInt("rejected",d.rejected());n.putInt("tokens",d.tokens());return n;
    }
    public static CompanionRecord fromNbt(NbtCompound n) {
        String[] fields={"id","state","dimension","name","type","stage","owner","control","mode","restriction","family"};
        int[] limits={80,24,256,256,256,24,24,24,24,32,64};
        for(int i=0;i<fields.length;i++)if(n.getString(fields[i],"").length()>limits[i])
            throw new IllegalArgumentException("Oversized companion observation: "+fields[i]);
        var q=new CompanionInfoQuery(new UUID(0,0),0,UUID.fromString(n.getString("entity","")),n.getString("dimension",""));
        return new CompanionRecord(n.getString("id",""),n.getLong("observed",0),n.getString("state","historical"),
                new CompanionInfoData(q,"ready",n.getString("name",""),n.getString("type",""),n.getString("stage",""),
                    n.getInt("affinity",0),n.getInt("maximum",0),n.getInt("friend",0),n.getFloat("health",-1),n.getFloat("max_health",-1),
                    n.getDouble("armor",-1),n.getDouble("attack",-1),n.getString("owner","unknown"),n.getString("control","unknown"),
                    n.getString("mode","unknown"),n.getString("restriction","unknown"),n.getString("family",""),-1,
                    n.getInt("completed",-1),n.getInt("deep",-1),n.getInt("agreed",-1),n.getInt("rejected",-1),n.getInt("tokens",-1)));
    }
}
