package com.nonid.affinity.net;
import java.util.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
/** Owned display metadata only: never sends rewards or receipt tokens. */
public record GiftViewPayload(UUID session,long sequence,String dimension,List<Row> rows) implements CustomPayload {
 public record Row(UUID gift,UUID target,boolean bequest,double x,double y,double z,String name,String type,int wrapping,int count) {
  public Row {Objects.requireNonNull(gift);Objects.requireNonNull(target);if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||name==null||name.length()>256||type==null||type.length()>256||wrapping<0||wrapping>3||count<1)throw new IllegalArgumentException("Invalid gift row");}
 }
 public GiftViewPayload {rows=List.copyOf(rows);if(session==null||sequence<1||dimension==null||dimension.length()>256||rows.size()>64)throw new IllegalArgumentException("Invalid gift view");}
 public static final Id<GiftViewPayload> ID=new Id<>(Identifier.of("noncreatureaffinity","gift_view"));
 public static final PacketCodec<RegistryByteBuf,GiftViewPayload> CODEC=PacketCodec.ofStatic((b,v)->{
  b.writeUuid(v.session);b.writeLong(v.sequence);b.writeString(v.dimension,256);b.writeVarInt(v.rows.size());
  for(var r:v.rows){b.writeUuid(r.gift);b.writeUuid(r.target);b.writeBoolean(r.bequest);b.writeDouble(r.x);b.writeDouble(r.y);b.writeDouble(r.z);b.writeString(r.name,256);b.writeString(r.type,256);b.writeVarInt(r.wrapping);b.writeVarInt(r.count);}
 },b->{var session=b.readUuid();long sequence=b.readLong();String dimension=b.readString(256);int count=b.readVarInt();if(count<0||count>64)throw new IllegalArgumentException("Invalid gift count");var rows=new ArrayList<Row>();for(int i=0;i<count;i++)rows.add(new Row(b.readUuid(),b.readUuid(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble(),b.readString(256),b.readString(256),b.readVarInt(),b.readVarInt()));return new GiftViewPayload(session,sequence,dimension,rows);});
 @Override public Id<? extends CustomPayload> getId(){return ID;}
}
