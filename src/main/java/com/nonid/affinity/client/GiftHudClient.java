package com.nonid.affinity.client;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.item.AffinityDetectorItem;
import com.nonid.affinity.net.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** Private ready-gift bubbles and detector-only bequests. All delivery remains server-side. */
public final class GiftHudClient {
 private record Hit(GiftViewPayload.Row row,Vec3d entityPosition,long frame,int width,int height,
                    float x,float y,float widthPixels,float heightPixels,Vec3d camera,org.joml.Quaternionf rotation){}
 private record Bounds(UUID target,float x,float y,float width,float height){}
 private static final GiftViewState STATE=new GiftViewState();
 private static final List<Bounds> BOUNDS=new ArrayList<>();
 private static final StyleSpriteSource.Font FONT=new StyleSpriteSource.Font(Identifier.of("noncreatureaffinity","request"));
 private static Hit hit;private static UUID pressed,pendingClaim;private static long claimAt;
 private static int age,poll,waiting;private static String dimension="";
 private GiftHudClient(){}
 public static void register(){
  ClientPlayNetworking.registerGlobalReceiver(GiftViewPayload.ID,(p,c)->c.client().execute(()->{if(STATE.accept(p)){age=0;waiting=0;}}));
  ClientPlayNetworking.registerGlobalReceiver(GiftResultPayload.ID,(p,c)->c.client().execute(()->{
   if(!STATE.session().equals(p.session())||!p.gift().equals(pendingClaim))return;
   pendingClaim=null;STATE.settled(p);
   // Preserve the normal query interval and any valid in-flight snapshot; claiming never bypasses polling.
   if(!p.status().equals("claimed")&&c.client().player!=null)ReunionNoticeClient.local(Text.translatable("message.noncreatureaffinity.gift_"+p.status()));
  }));
  ClientPlayConnectionEvents.DISCONNECT.register((h,c)->reset(""));
  ClientTickEvents.END_CLIENT_TICK.register(GiftHudClient::tick);
  HudElementRegistry.addLast(Identifier.of("noncreatureaffinity","gift_markers"),GiftHudClient::render);
 }
 private static void reset(String dim){dimension=dim;STATE.reset(dim);hit=null;pressed=null;pendingClaim=null;age=0;poll=10;waiting=0;BOUNDS.clear();}
 private static void tick(MinecraftClient c){
  if(c.world==null||c.player==null){reset("");return;}
  String dim=c.world.getRegistryKey().getValue().toString();if(!dim.equals(dimension))reset(dim);
  if(++age>40)STATE.expire();
  if(pendingClaim!=null&&System.nanoTime()-claimAt>3_000_000_000L)pendingClaim=null;
  if(c.currentScreen!=null||!c.player.isAlive()||c.player.isSpectator()){hit=null;pressed=null;return;}
  if(waiting>0&&++waiting<=60)return;
  if(++poll>=10&&ClientPlayNetworking.canSend(GiftQueryPayload.ID)){poll=0;waiting=1;ClientPlayNetworking.send(STATE.query());}
 }
 public static boolean hasGift(UUID target){return STATE.rows().stream().anyMatch(r->!r.bequest()&&r.target().equals(target));}
 public static boolean overlaps(float x,float y,float width,float height){return BOUNDS.stream().anyMatch(b->x<b.x+b.width&&x+width>b.x&&y<b.y+b.height&&y+height>b.y);}
 public static float feedbackFloor(UUID target){return BOUNDS.stream().filter(b->b.target.equals(target)).map(b->b.y+b.height+4).max(Float::compare).orElse(0f);}
 private static boolean visible(MinecraftClient c,Vec3d at){return c.world.raycast(new RaycastContext(c.gameRenderer.getCamera().getCameraPos(),at,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,c.player)).getType()==HitResult.Type.MISS;}
 private static Vec3d projected(MinecraftClient c,Vec3d at){var cam=c.gameRenderer.getCamera();if(at.subtract(cam.getCameraPos()).dotProduct(new Vec3d(cam.getHorizontalPlane()))<=0)return null;var p=c.gameRenderer.project(at);return Double.isFinite(p.x)&&Double.isFinite(p.y)&&p.z>=-1&&p.z<=1&&Math.abs(p.x)<=1&&Math.abs(p.y)<=1?p:null;}
 private static void image(DrawContext c,String path,int x,int y,int w,int h,int tw,int th){c.drawTexture(RenderPipelines.GUI_TEXTURED,Identifier.of("noncreatureaffinity","textures/gui/gift/"+path+".png"),x,y,0,0,w,h,tw,th,tw,th);}
 private static Text name(GiftViewPayload.Row row){if(!row.name().isBlank())return Text.literal(row.name());var id=Identifier.tryParse(row.type());return id==null?Text.translatable("message.noncreatureaffinity.gift_unknown_giver"):net.minecraft.registry.Registries.ENTITY_TYPE.getOptionalValue(id).map(e->e.getName()).orElse(Text.translatable("message.noncreatureaffinity.gift_unknown_giver"));}
 private static void label(DrawContext c,Text value,int center,int y,int maxWidth,float scale){
  var r=MinecraftClient.getInstance().textRenderer;Text shown=value.copy().styled(s->s.withFont(FONT));int max=(int)(maxWidth/scale);
  if(r.getWidth(shown)>max){String v=shown.getString();int end=v.length();do{end=v.offsetByCodePoints(end,-1);shown=Text.literal(v.substring(0,end)+"…").setStyle(shown.getStyle());}while(end>0&&r.getWidth(shown)>max);}
  c.getMatrices().pushMatrix();c.getMatrices().translate(center,y);c.getMatrices().scale(scale,scale);c.drawText(r,shown,-r.getWidth(shown)/2,0,0xFFFFE9F4,false);c.getMatrices().popMatrix();
 }
 private static void render(DrawContext draw,RenderTickCounter tick){
  hit=null;BOUNDS.clear();var c=MinecraftClient.getInstance();if(c.player==null||c.world==null||c.currentScreen!=null||ClientHudCleanup.displaySuppressed(c)||!c.player.isAlive()||c.player.isSpectator())return;
  int w=c.getWindow().getScaledWidth(),h=c.getWindow().getScaledHeight();float gui=(float)c.getWindow().getFramebufferWidth()/w;
  var camera=c.gameRenderer.getCamera();Vec3d right=new Vec3d(camera.getRotation().transform(new org.joml.Vector3f(1,0,0)));
  for(var row:STATE.rows()){
   Vec3d at;LivingEntity entity=null;
   if(row.bequest()){
    if(!AffinityDetectorItem.isHeldBy(c.player))continue;at=new Vec3d(row.x(),row.y()+.4,row.z());var block=BlockPos.ofFloored(at);
    if(!c.world.isChunkLoaded(block.getX()>>4,block.getZ()>>4)||!visible(c,at))continue;
   }else{
    var loaded=c.world.getEntity(row.target());if(!(loaded instanceof LivingEntity living)||!living.isAlive()||living.squaredDistanceTo(c.player)>64)continue;entity=living;
    at=AffinityHud.headAnchor(c,living,living.getLerpedPos(tick.getTickProgress(false)),right);if(at==null)continue;
   }
   var p=projected(c,at);if(p==null)continue;float ax=(float)((p.x+1)*w/2),ay=(float)((1-p.y)*h/2);
   float x,y,hw,hh;
   if(row.bequest()){
    float scale=1/gui;x=ax-24*scale;y=ay-24*scale;hw=48*scale;hh=48*scale;
    if(x<2||y<2||x+hw>w-2||y+hh+38*scale>h-2)continue;
    draw.getMatrices().pushMatrix();draw.getMatrices().translate(x,y);draw.getMatrices().scale(scale,scale);
    image(draw,"legacy-envelope",0,0,48,48,48,48);
    label(draw,Text.translatable("gift_ui.noncreatureaffinity.bequest",name(row),row.count()),24,52,180,1.5f);
    double distance=c.player.getEntityPos().distanceTo(new Vec3d(row.x(),row.y(),row.z()));
    label(draw,Text.literal(String.format(java.util.Locale.ROOT,"%.1fm",distance)),24,69,150,1.4f);draw.getMatrices().popMatrix();
   }else{
    float scale=Math.min(200/(380*gui),Math.min((w-8)/380f,(h-8)/208f));
    float requestScale=RequestBubbleGeometry.physicalWidth(c.getWindow().getFramebufferWidth())/(380*gui);
    x=Math.clamp(ax+33*requestScale+10/gui,4,w-380*scale-4);y=Math.clamp(ay-101*requestScale-104*scale,4,h-208*scale-4);
    // A bubble is not visible/input-capable through a wall merely because its entity is visible.
    var point=at.add(right.multiply(.4));if(!visible(c,point))continue;
    BOUNDS.add(new Bounds(row.target(),x,y,380*scale,208*scale));
    float iconX=x+126*scale,iconY=y+37*scale;hw=128*scale;hh=128*scale;
    boolean hover=w/2f>=iconX&&w/2f<=iconX+hw&&h/2f>=iconY&&h/2f<=iconY+hh;
    float iconScale=row.gift().equals(pressed)?.92f:hover?1.1f:1;
    draw.getMatrices().pushMatrix();draw.getMatrices().translate(x,y);draw.getMatrices().scale(scale,scale);
    image(draw,"gift-tail-left",0,0,380,208,380,208);
    draw.getMatrices().pushMatrix();draw.getMatrices().translate(190,101);draw.getMatrices().scale(iconScale,iconScale);
    if(hover||row.gift().equals(pressed))for(int[] offset:new int[][]{{-2,0},{2,0},{0,-2},{0,2}}){
      // Small warm outline follows the icon; the approved bubble frame never scales.
      draw.drawTexture(RenderPipelines.GUI_TEXTURED,Identifier.of("noncreatureaffinity","textures/gui/gift/tier-"+(row.wrapping()+1)+".png"),-64+offset[0],-64+offset[1],0,0,128,128,96,96,96,96,0xFFFFE6B8);
    }
    image(draw,"tier-"+(row.wrapping()+1),-64,-64,128,128,96,96);draw.getMatrices().popMatrix();draw.getMatrices().popMatrix();
    x=iconX;y=iconY;
   }
   if(w/2f>=x&&w/2f<=x+hw&&h/2f>=y&&h/2f<=y+hh&&hit==null)
    hit=new Hit(row,entity==null?null:entity.getEntityPos(),System.nanoTime(),w,h,x,y,hw,hh,camera.getCameraPos(),new org.joml.Quaternionf(camera.getRotation()));
  }
 }
 private static boolean current(Hit h){var c=MinecraftClient.getInstance();return h!=null&&c.player!=null&&c.world!=null&&c.currentScreen==null&&!ClientHudCleanup.displaySuppressed(c)&&c.mouse.isCursorLocked()
   &&System.nanoTime()-h.frame<250_000_000L&&c.getWindow().getScaledWidth()==h.width&&c.getWindow().getScaledHeight()==h.height&&c.gameRenderer.getCamera().getCameraPos().equals(h.camera)&&c.gameRenderer.getCamera().getRotation().equals(h.rotation)
   &&STATE.rows().stream().anyMatch(r->r.gift().equals(h.row.gift()));}
 private static boolean reachable(Hit h){var c=MinecraftClient.getInstance();var r=h.row;
  if(r.bequest()){var at=new Vec3d(r.x(),r.y(),r.z());return AffinityDetectorItem.isHeldBy(c.player)&&c.player.canInteractWithEntityIn(new Box(at.x-.125,at.y,at.z-.125,at.x+.125,at.y+.25,at.z+.125),0)&&visible(c,at.add(0,.125,0));}
  var entity=c.world.getEntity(r.target());return entity instanceof LivingEntity living&&living.isAlive()&&living.getEntityPos().equals(h.entityPosition)&&c.player.getMainHandStack().isEmpty()&&c.player.canInteractWithEntity(living,0)&&visible(c,living.getEntityPos().add(0,living.getHeight()*.5,0));
 }
 public static boolean press(){var h=hit;if(pendingClaim!=null||!current(h)||!reachable(h))return false;pressed=h.row.gift();return true;}
 public static void release(){var h=hit;var chosen=pressed;pressed=null;if(chosen==null||!current(h)||!chosen.equals(h.row.gift())||!reachable(h)||!ClientPlayNetworking.canSend(GiftClaimPayload.ID))return;
  pendingClaim=chosen;claimAt=System.nanoTime();ClientPlayNetworking.send(new GiftClaimPayload(STATE.session(),chosen,h.row.target(),h.row.bequest()));
 }
}
