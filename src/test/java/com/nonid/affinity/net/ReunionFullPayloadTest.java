package com.nonid.affinity.net;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReunionFullPayloadTest {
 @Test void fullDisplayBothHandsRoundTripOnSeparateChannel(){
  var b=new RegistryByteBuf(Unpooled.buffer(),DynamicRegistryManager.EMPTY);
  try{for(boolean off:new boolean[]{false,true}){
   var p=new ReunionFullPayload(UUID.randomUUID(),Identifier.ofVanilla("overworld"),UUID.randomUUID().toString(),Long.MAX_VALUE-1,off);
   ReunionFullPayload.CODEC.encode(b,p);assertEquals(p,ReunionFullPayload.CODEC.decode(b));assertEquals(0,b.readableBytes());
   assertNotEquals(ReunionChargePayload.ID.id(),p.getId().id());
  }}finally{b.release();}
 }
 @Test void invalidIdentityAndFuelCannotProduceDisplayReceipt(){
  var id=UUID.randomUUID();var dim=Identifier.ofVanilla("overworld");
  for(long fuel:new long[]{-1,0,2,Long.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->new ReunionFullPayload(id,dim,"bond",fuel,false));
  assertThrows(IllegalArgumentException.class,()->new ReunionFullPayload(id,dim,"",3,false));
 }
}
