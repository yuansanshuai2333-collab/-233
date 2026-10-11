package com.nonid.affinity.net;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReunionChargePayloadTest {
 @Test void sixTransitionsBothHandsAndLongFuelRoundTrip(){
  var b=new RegistryByteBuf(Unpooled.buffer(),DynamicRegistryManager.EMPTY);
  try{for(int before=0;before<3;before++)for(int after=before+1;after<=3;after++)for(boolean off:new boolean[]{false,true}){
   var p=new ReunionChargePayload(UUID.randomUUID(),Identifier.ofVanilla("the_nether"),UUID.randomUUID().toString(),Long.MAX_VALUE,before,after,off);
   ReunionChargePayload.CODEC.encode(b,p);assertEquals(p,ReunionChargePayload.CODEC.decode(b));assertEquals(0,b.readableBytes());
  }}finally{b.release();}
 }
 @Test void noFullOrDecreasingOrUnboundSuccessReceipt(){
  var id=UUID.randomUUID();var dim=Identifier.ofVanilla("overworld");
  assertThrows(IllegalArgumentException.class,()->new ReunionChargePayload(id,dim,"bond",3,3,3,false));
  assertThrows(IllegalArgumentException.class,()->new ReunionChargePayload(id,dim,"bond",3,2,1,false));
  assertThrows(IllegalArgumentException.class,()->new ReunionChargePayload(id,dim,"",3,0,3,false));
  assertThrows(IllegalArgumentException.class,()->new ReunionChargePayload(id,dim,"bond",1,0,3,false));
 }
}
