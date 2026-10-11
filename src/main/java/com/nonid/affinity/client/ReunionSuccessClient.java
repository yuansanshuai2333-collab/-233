package com.nonid.affinity.client;
import com.nonid.affinity.net.ReunionSuccessPayload;
import com.nonid.affinity.reunion.ReunionData;
import java.util.*;
import net.fabricmc.api.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Atlases;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
/** Native depth-tested world particles; never changes entities or persistent state. */
@Environment(EnvType.CLIENT)
public final class ReunionSuccessClient {
 private static final Set<UUID> SEEN=new LinkedHashSet<>();
 private static final List<Emission> ACTIVE=new ArrayList<>();
 private static final class Emission {
  final ReunionSuccessPayload payload;final ClientWorld world;int age,index;
  Emission(ReunionSuccessPayload p,ClientWorld w){payload=p;world=w;}
 }
 public static void register(){
  ClientPlayNetworking.registerGlobalReceiver(ReunionSuccessPayload.ID,(p,c)->c.client().execute(()->accept(p)));
  ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{ACTIVE.clear();SEEN.clear();});
  ClientTickEvents.END_CLIENT_TICK.register(ReunionSuccessClient::tick);
 }
 private static void accept(ReunionSuccessPayload p){
  var c=MinecraftClient.getInstance();
  if(c.world==null||c.player==null||!c.world.getRegistryKey().getValue().equals(p.dimension())||c.player.squaredDistanceTo(p.position())>64*64||!SEEN.add(p.receipt()))return;
  if(SEEN.size()>1024)SEEN.remove(SEEN.iterator().next());
  if(ACTIVE.size()>=32)ACTIVE.removeFirst();ACTIVE.add(new Emission(p,c.world));
  if(!p.healing()){
   double radius=Math.max(.3,Math.min(2,p.width()*.65));
   for(int i=0;i<28;i++){double a=i*Math.PI*2/28;spawn(c,2,p.position().add(Math.cos(a)*radius,.045,Math.sin(a)*radius),Math.cos(a)*radius*.32/14,0,Math.sin(a)*radius*.32/14);}
  }
 }
 private static void tick(MinecraftClient c){
  if(c.world==null||c.player==null){ACTIVE.clear();return;}if(c.isPaused())return;
  ACTIVE.removeIf(e->{
   if(e.world!=c.world||!c.player.isAlive()||c.player.squaredDistanceTo(e.payload.position())>64*64)return true;
   var p=e.payload;var entity=c.world.getEntityById(p.entityId());
   Vec3d origin=p.position();
   if(p.healing()&&entity!=null&&entity.getUuid().equals(p.creature())){if(!entity.isAlive()||entity.isRemoved())return true;origin=entity.getEntityPos();}
   int count=p.healing()?19:12;
   while(e.index<count&&e.age>=(p.healing()?ReunionParticleTiming.healStart(e.index):ReunionParticleTiming.sparkStart(e.index))){
    int i=e.index++;double a=i*2.4;double radius=Math.max(.22,Math.min(2,p.width()*.65));
    if(p.healing()){
     double height=Math.max(.3,Math.min(4,p.height()));
     spawn(c,i%3==0?0:1,origin.add(Math.cos(a)*radius,(.2+(i*37%101)/130.0)*height,Math.sin(a)*radius),Math.sin(a)*.004,.027,Math.cos(a)*.004);
    }else spawn(c,3,origin.add(Math.cos(a)*radius*(.6+i%3*.14),.07,Math.sin(a)*radius*(.6+i%3*.14)),0,.022+(i%4)*.005,0);
   }
   e.age++;return e.index>=count;
  });
 }
 private static void spawn(MinecraftClient c,int kind,Vec3d p,double vx,double vy,double vz){
  Identifier id=kind==0?Identifier.ofVanilla("heart"):ReunionData.id(kind==1?"reunion_plus":kind==2?"reunion_ring":"reunion_spark");
  Sprite sprite=c.getAtlasManager().getAtlasTexture(Atlases.PARTICLES).getSprite(id);
  c.particleManager.addParticle(new Visual(c.world,p.x,p.y,p.z,vx,vy,vz,sprite,kind));
 }
 private static final class Visual extends BillboardParticle {
  final int kind;
  Visual(ClientWorld w,double x,double y,double z,double vx,double vy,double vz,Sprite sprite,int kind){
   super(w,x,y,z,sprite);this.kind=kind;velocityX=vx;velocityY=vy;velocityZ=vz;collidesWithWorld=false;gravityStrength=0;velocityMultiplier=1;
   maxAge=kind==2?40:kind==3?28:32;scale=kind==0?.11f:kind==1?.09f:kind==2?.035f:.026f;alpha=0;
  }
  @Override public void tick(){
   super.tick();if(kind==2&&age>=14){velocityX=0;velocityZ=0;}
   alpha=ReunionParticleTiming.alpha(age,maxAge,kind==2?4:kind==3?3:2.4f,kind==2?13:9)*(kind==2?210/255f:kind==3?230/255f:1);
  }
  @Override protected RenderType getRenderType(){return RenderType.PARTICLE_ATLAS_TRANSLUCENT;}
 }
 private ReunionSuccessClient(){}
}
