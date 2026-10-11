package com.nonid.affinity.client;

import com.nonid.affinity.net.GiftOpeningPayload;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.*;
import net.minecraft.util.Identifier;

/** Approved independent sprites and particle motion. No screen, input lock or reward creation. */
public final class GiftOpeningClient {
 private record Effect(GiftOpeningPayload payload,long started,PositionedSoundInstance sound){}
 private record Particle(double vx,double vy,double delay,int kind,double phase){}
 private static final Map<UUID,Long> SEEN=new LinkedHashMap<>();
 private static final Deque<GiftOpeningPayload> QUEUE=new ArrayDeque<>();
 private static Effect effect;
 private static final int[] CLOSED_Y={243,235,246,240},BODY_H={130,130,129,128},LID_H={101,109,97,103};
 private static final int[] COLORS={0xED82AE,0xFFF2D6,0xE5BC72,0xC2A4DF};
 private static final List<Particle> CONFETTI=new ArrayList<>();
 static {var r=new Random(12);for(int i=0;i<42;i++)CONFETTI.add(new Particle(r.nextDouble()*230-115,r.nextDouble()*95-245,r.nextDouble()*.15,i%4,r.nextDouble()*6.28));}
 private GiftOpeningClient(){}
 public static void register(){
  ClientPlayNetworking.registerGlobalReceiver(GiftOpeningPayload.ID,(p,c)->c.client().execute(()->accept(p)));
  ClientPlayConnectionEvents.DISCONNECT.register((h,c)->clear(c));
  ClientTickEvents.END_CLIENT_TICK.register(c->{
   if(c.world==null||c.player==null){clear(c);return;}
   long now=System.nanoTime();SEEN.values().removeIf(t->now-t>60_000_000_000L);
   if(effect!=null&&!effect.payload.dimension().equals(c.world.getRegistryKey().getValue())){c.getSoundManager().stop(effect.sound);effect=null;}
   if(effect!=null&&now-effect.started>=3_000_000_000L)effect=null;
   while(effect==null&&!QUEUE.isEmpty()){var next=QUEUE.removeFirst();if(next.dimension().equals(c.world.getRegistryKey().getValue()))start(next,c);}
  });
  HudElementRegistry.addLast(Identifier.of("noncreatureaffinity","gift_opening"),(draw,tick)->render(draw));
 }
 private static void accept(GiftOpeningPayload p){
  var c=MinecraftClient.getInstance();if(c.world==null||c.player==null||p.wrapping()<0||p.wrapping()>3||!p.dimension().equals(c.world.getRegistryKey().getValue())||SEEN.containsKey(p.gift()))return;
  if(SEEN.size()>=128)SEEN.remove(SEEN.keySet().iterator().next());SEEN.put(p.gift(),System.nanoTime());
  if(effect==null)start(p,c);else if(QUEUE.size()<8)QUEUE.addLast(p);
 }
 private static void start(GiftOpeningPayload p,MinecraftClient c){
  var sound=new PositionedSoundInstance(SoundEvent.of(Identifier.of("noncreatureaffinity","gift_open")),SoundCategory.PLAYERS,1f,1f,net.minecraft.util.math.random.Random.create(),c.player.getX(),c.player.getEyeY(),c.player.getZ());
  effect=new Effect(p,System.nanoTime(),sound);c.getSoundManager().play(sound); // B2 already includes the 200/430ms cues.
 }
 private static void clear(MinecraftClient c){if(effect!=null)c.getSoundManager().stop(effect.sound);effect=null;QUEUE.clear();SEEN.clear();}
 private static void sprite(DrawContext c,String part,int tier,int x,int y,int w,int h){c.drawTexture(RenderPipelines.GUI_TEXTURED,Identifier.of("noncreatureaffinity","textures/gui/gift/opening/tier-"+tier+"-"+part+".png"),x,y,0,0,w,h,w,h);}
 private static int alpha(int rgb,double a){return ((int)(255*Math.clamp(a,0,1))<<24)|rgb;}
 private static int step(double n){return (int)Math.round(n/2)*2;}
 private static void render(DrawContext draw){
  var c=MinecraftClient.getInstance();if(effect==null||c.world==null||c.player==null||c.options.hudHidden||!effect.payload.dimension().equals(c.world.getRegistryKey().getValue()))return;
  double t=(System.nanoTime()-effect.started)/1_000_000_000.0;if(t<0||t>=3)return;
  int index=effect.payload.wrapping(),tier=index+1,w=c.getWindow().getScaledWidth(),h=c.getWindow().getScaledHeight();
  float physical=(float)c.getWindow().getFramebufferWidth()/w;
  float scale=Math.min(1/physical,Math.min((w-8)/400f,(h-8)/440f));
  draw.getMatrices().pushMatrix();draw.getMatrices().translate(w/2f-200*scale,h/2f-220*scale);draw.getMatrices().scale(scale,scale);
  sprite(draw,"body",tier,130,275,140,BODY_H[index]);
  double q=Math.clamp((t-.2)/.4,0,1),ease=1-Math.pow(1-q,3),angle=Math.toRadians(22*ease);
  double expandedH=Math.abs(148*Math.sin(angle))+Math.abs(LID_H[index]*Math.cos(angle));
  draw.getMatrices().pushMatrix();draw.getMatrices().translate((float)(200-88*ease),(float)(CLOSED_Y[index]-105*ease+expandedH/2));draw.getMatrices().rotate((float)-angle);
  sprite(draw,"lid",tier,-74,-LID_H[index]/2,148,LID_H[index]);draw.getMatrices().popMatrix();
  for(var p:CONFETTI){double age=t-.43-p.delay;if(age<0)continue;int x=step(200+p.vx*age*.72+Math.sin(age*5+p.phase)*5*age),y=step(300+p.vy*age+95*age*age);if(y>412)continue;
   int color=alpha(COLORS[p.kind],(2.85-t)/.65),width=2+(int)Math.round(Math.abs(Math.cos(age*7+p.phase))*3)*2;
   if(p.kind%2==1)for(int j=0;j<3;j++)draw.fill(x+j*2,y+j*3,x+j*2+width+1,y+j*3+3,color);else draw.fill(x,y,x+width+1,y+6,color);
  }
  var random=new Random(120+tier);
  for(int i=0;i<tier*5;i++){double vx=random.nextDouble()*130-65,vy=random.nextDouble()*60-180,delay=random.nextDouble()*.12,age=t-.45-delay;if(age<0)continue;
   int x=step(200+vx*age),y=step(300+vy*age+105*age*age);if(y>410)continue;double a=(2.35-t)/.45;
   draw.fill(x,y-6,x+3,y+3,alpha(0xF5E5C6,a));draw.fill(x-2,y,x+5,y+6,alpha(0xFFF5DC,a));draw.fill(x,y-1,x+3,y+3,alpha(0xFFFFF6,a));
  }
  draw.getMatrices().popMatrix();
 }
}
