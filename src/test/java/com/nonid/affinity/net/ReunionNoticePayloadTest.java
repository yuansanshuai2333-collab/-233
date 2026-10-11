package com.nonid.affinity.net;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReunionNoticePayloadTest {
 @Test void allReasonsAndActualEnergyRequirementsRoundTrip(){
  var b=new RegistryByteBuf(Unpooled.buffer(),DynamicRegistryManager.EMPTY);
  try{for(int kind=0;kind<4;kind++)for(int required:kind==0?new int[]{1,3}:new int[]{0}){
   var p=new ReunionNoticePayload(UUID.randomUUID(),Identifier.ofVanilla("the_nether"),kind,required);ReunionNoticePayload.CODEC.encode(b,p);assertEquals(p,ReunionNoticePayload.CODEC.decode(b));assertEquals(0,b.readableBytes());
  }}finally{b.release();}
 }
 @Test void doesNotAcceptUnrelatedReasonsOrInventedRequirements(){
  var id=UUID.randomUUID();var dim=Identifier.ofVanilla("overworld");
  assertThrows(IllegalArgumentException.class,()->new ReunionNoticePayload(id,dim,4,0));
  assertThrows(IllegalArgumentException.class,()->new ReunionNoticePayload(id,dim,0,2));
  assertThrows(IllegalArgumentException.class,()->new ReunionNoticePayload(id,dim,1,3));
 }
}
