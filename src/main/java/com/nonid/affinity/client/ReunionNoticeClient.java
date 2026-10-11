package com.nonid.affinity.client;
import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.net.ReunionNoticePayload;
import com.nonid.affinity.net.GameplayNoticePayload;
import com.nonid.affinity.service.GameplayNoticeStyle;
import java.util.*;
import net.fabricmc.api.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
@Environment(EnvType.CLIENT)
public final class ReunionNoticeClient {
 private record Entry(GameplayNoticePayload payload,long start){}
 private static Entry active;private static final GameplayNoticeQueue<GameplayNoticePayload> QUEUE=new GameplayNoticeQueue<>();private static Identifier dimension;
 private static final String[] PORTRAITS={"empty","full","missing","restricted"};
 private static final String KEY="message.noncreatureaffinity.notice.";
 private static Identifier texture(String name){return Identifier.of("noncreatureaffinity","textures/gui/reunion-notice/"+name+".png");}
 public static void register(){
  ClientPlayNetworking.registerGlobalReceiver(ReunionNoticePayload.ID,(p,c)->c.client().execute(()->accept(p)));
  ClientPlayNetworking.registerGlobalReceiver(GameplayNoticePayload.ID,(p,c)->c.client().execute(()->accept(p)));
  ClientPlayConnectionEvents.DISCONNECT.register((h,c)->clear());
  ClientTickEvents.END_CLIENT_TICK.register(c->{
   if(c.world==null||c.player==null||!c.player.isAlive()){clear();return;}
   if(!c.world.getRegistryKey().getValue().equals(dimension)){clear();dimension=c.world.getRegistryKey().getValue();}
   long now=System.nanoTime()/1_000_000;
   if(active!=null&&!ReunionNoticeTiming.active((System.nanoTime()-active.start)/1_000_000))active=null;
   if(active==null&&c.currentScreen==null&&!ClientHudCleanup.displaySuppressed(c)){var next=QUEUE.poll(now);if(next!=null)active=new Entry(next,System.nanoTime());}
  });
  HudElementRegistry.addLast(Identifier.of("noncreatureaffinity","reunion_notice"),ReunionNoticeClient::render);
 }
 private static void clear(){active=null;QUEUE.clear();dimension=null;}
 private static void accept(ReunionNoticePayload p){
  String name=PORTRAITS[p.kind()];Text detail=Text.translatable(KEY+name+".detail");
  if(p.kind()==0)detail=Text.translatable(KEY+(p.requiredEnergy()==3?"empty.three":"empty.one"));
  accept(new GameplayNoticePayload(p.receipt(),p.dimension(),p.kind(),2,Text.translatable(KEY+name+".title"),detail,0,0,0));
 }
 public static void local(Text detail){
  var c=MinecraftClient.getInstance();if(c.world==null||c.player==null)return;var style=GameplayNoticeStyle.of(detail);
  if(detail.getString().length()>2048)detail=Text.literal(detail.getString().substring(0,2048));
  accept(new GameplayNoticePayload(UUID.randomUUID(),c.world.getRegistryKey().getValue(),style.kind(),style.priority(),Text.translatable("message.noncreatureaffinity.feedback."+style.title()),detail,0,0,0));
 }
 private static void accept(GameplayNoticePayload p){
  var c=MinecraftClient.getInstance();if(c.world==null||c.player==null||!p.dimension().equals(c.world.getRegistryKey().getValue()))return;
  if(!p.dimension().equals(dimension)){clear();dimension=p.dimension();}
  QUEUE.offer(p.receipt(),p,p.priority(),System.nanoTime()/1_000_000);
 }

 private static int color(int rgb,float opacity){return (Math.round(255*opacity)<<24)|(rgb&0xffffff);}
 private static void sprite(DrawContext d,String name,int x,int y,int w,int h,float opacity){d.drawTexture(RenderPipelines.GUI_TEXTURED,texture(name),x,y,0,0,w,h,w,h,color(0xffffff,opacity));}
 private static boolean clear(float x,float y,float w,float h){return !AffinityHud.overlaps(x,y,w,h)&&!GiftHudClient.overlaps(x,y,w,h)&&!FriendshipTokenClient.overlaps(x,y,w,h)&&!VillagerGiftClient.overlaps(x,y,w,h);}
 private static void text(DrawContext d,MinecraftClient c,Text text,float x,float y,float size,float limit,int rgb,float opacity){
  float s=Math.min(size/c.textRenderer.fontHeight,limit/Math.max(1,c.textRenderer.getWidth(text)));
  d.getMatrices().pushMatrix();d.getMatrices().translate(x,y);d.getMatrices().scale(s,s);d.drawText(c.textRenderer,text,0,0,color(rgb,opacity),false);d.getMatrices().popMatrix();
 }
 private static void render(DrawContext d,RenderTickCounter tick){
  var c=MinecraftClient.getInstance();var e=active;
  if(e==null||c.world==null||c.player==null||c.currentScreen!=null||ClientHudCleanup.displaySuppressed(c)||!e.payload.dimension().equals(c.world.getRegistryKey().getValue()))return;
  long ms=(System.nanoTime()-e.start)/1_000_000;if(!ReunionNoticeTiming.active(ms))return;
  int w=c.getWindow().getScaledWidth(),h=c.getWindow().getScaledHeight();if(w<24||h<80)return;
  float gui=(float)c.getWindow().getFramebufferWidth()/w;
  float s=Math.min(Math.clamp(c.getWindow().getFramebufferWidth()*.42f,320,520)/(500*gui),Math.min((w-8)/500f,(h/2f-40)/170f));if(s<=0)return;
  float bw=500*s,bh=170*s,x=(w-bw)/2,y=h/2f+30/gui;boolean placed=false;
  search:for(float offset:new float[]{0,25,50,75,100}){
   float yy=y+offset*s;if(yy+bh>h-38)continue;
   for(float dx:new float[]{0,100,-100,200,-200}){
    float xx=Math.clamp(x+dx*s,4,w-bw-4);
    if(clear(xx,yy,bw,bh)){x=xx;y=yy;placed=true;break search;}
   }
  }
  if(!placed)return;
  float opacity=ReunionNoticeTiming.opacity(ms);double t=ms/1000.0;int kind=e.payload.kind();
  d.getMatrices().pushMatrix();d.getMatrices().translate(x,y+20*s);d.getMatrices().scale(s,s);
  sprite(d,"frame",0,0,500,150,opacity);
  int bob=(int)Math.round(-8*Math.sin(Math.min(1,t/.5)*Math.PI));int shift=kind==2?(int)Math.round(3*Math.sin(t*4)*Math.min(1,t/.3)):kind==3?(int)Math.round(3*Math.sin(t*15)*Math.max(0,1-t/.7)):0;
  String expression=GameplayNoticeStyle.portrait(e.payload.title(),e.payload.detail());
  sprite(d,expression==null?PORTRAITS[kind]:"expression-"+expression,shift,bob,138,138,opacity);
  if(ReunionNoticeTiming.title(ms))text(d,c,e.payload.title(),150,42,29,320,0xfff0e4,opacity);
  if(ReunionNoticeTiming.detail(ms)){
   sprite(d,"pill",151,87,318,30,opacity);
   Text detail=e.payload.detail();
   if(c.textRenderer.getWidth(detail)*19f/c.textRenderer.fontHeight<=296)text(d,c,detail,163,90,19,296,0xefb8d3,opacity);
   else{
    float fs=14f/c.textRenderer.fontHeight;var lines=c.textRenderer.wrapLines(detail,(int)(296/fs));
    while(lines.size()>2&&fs>.25f){fs-=.1f;lines=c.textRenderer.wrapLines(detail,(int)(296/fs));}
    d.getMatrices().pushMatrix();d.getMatrices().translate(163,88);d.getMatrices().scale(fs,fs);
    for(int i=0;i<Math.min(2,lines.size());i++)d.drawText(c.textRenderer,lines.get(i),0,i*c.textRenderer.fontHeight,color(0xefb8d3,opacity),false);
    d.getMatrices().popMatrix();
   }
  }
  if(e.payload.maximum()>0&&e.payload.after()<e.payload.before()){
   double ratio=AffinityIncreaseTimeline.shownRatio(e.payload.before(),e.payload.after(),e.payload.maximum(),ms);
   d.fill(150,126,468,134,color(0x211624,opacity));d.fill(151,127,151+(int)Math.round(316*ratio),133,color(0xE77BAC,opacity));
   if(t>.25&&t<2.2)sprite(d,"heart",106,15+(int)Math.round((t-.25)*20),19,18,opacity*(float)Math.max(0,1-(t-.25)/2));
  }
  if(kind==1&&t>.45&&t<1.8)for(int i=0;i<2;i++){double age=t-.45-i*.22;if(age>0&&age<1.1)sprite(d,"heart",94+i*31,5-(int)Math.round(age*19),19,18,opacity*(float)Math.min(1,Math.min(age/.12,(1.1-age)/.35)));}
  if(kind==2&&t>.5&&t<2){double age=t-.5;text(d,c,Text.literal("?"),108,-(float)Math.round(Math.min(age,.7)*14),29,30,0xefb8d3,opacity*(float)Math.min(1,Math.min(age/.15,(1.5-age)/.4)));}
  if(kind==3&&t>.55&&t<1.55){double age=t-.55;sprite(d,"puff",111+(int)Math.round(age*18),80-(int)Math.round(age*8),16,12,opacity*(float)(1-age)*200/255f);}
  d.getMatrices().popMatrix();
 }
 private ReunionNoticeClient(){}
}
