package com.nonid.affinity.net;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReunionSuccessPayloadTest {
 @Test void bothKindsKeepExactPrivateReceiptAndLanding(){
  var b=new RegistryByteBuf(Unpooled.buffer(),DynamicRegistryManager.EMPTY);
  try{for(boolean heal:new boolean[]{false,true}){
   var p=new ReunionSuccessPayload(UUID.randomUUID(),Identifier.ofVanilla("the_nether"),UUID.randomUUID(),12,heal,new Vec3d(-22.25,90.5,12.75),.6f,1.95f);
   ReunionSuccessPayload.CODEC.encode(b,p);assertEquals(p,ReunionSuccessPayload.CODEC.decode(b));assertEquals(0,b.readableBytes());
  }}finally{b.release();}
 }
 @Test void rejectsNonFiniteOrNegativeGeometry(){
  var id=UUID.randomUUID();var dim=Identifier.ofVanilla("overworld");
  assertThrows(IllegalArgumentException.class,()->new ReunionSuccessPayload(id,dim,id,1,true,new Vec3d(Double.NaN,0,0),1,1));
  assertThrows(IllegalArgumentException.class,()->new ReunionSuccessPayload(id,dim,id,1,false,Vec3d.ZERO,Float.POSITIVE_INFINITY,1));
  assertThrows(IllegalArgumentException.class,()->new ReunionSuccessPayload(id,dim,id,1,false,Vec3d.ZERO,1,-1));
 }
}
