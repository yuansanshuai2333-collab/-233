package com.nonid.affinity.client;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.net.VillagerGiftPayload;
import java.util.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** A private, read-only receipt; inventory and reward eligibility remain entirely server-side. */
@Environment(EnvType.CLIENT)
public final class VillagerGiftClient {
    private record Entry(VillagerGiftPayload payload,long started) {}
    private record Placement(float x,float y,float tx,float ty) {}
    private static final Map<UUID,Entry> ACTIVE=new LinkedHashMap<>();
    private static final Set<UUID> SEEN=new LinkedHashSet<>();
    private static final List<float[]> BOUNDS=new ArrayList<>();
    private static final Identifier FRAME=Identifier.of("noncreatureaffinity","textures/gui/request/frame/villager-gift.png");
    private VillagerGiftClient() {}
    public static void register(){
        ClientPlayNetworking.registerGlobalReceiver(VillagerGiftPayload.ID,(p,c)->c.client().execute(()->accept(p)));
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{ACTIVE.clear();SEEN.clear();BOUNDS.clear();});
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(c.world==null||c.player==null){ACTIVE.clear();return;}
            long now=System.nanoTime();
            ACTIVE.values().removeIf(e->!e.payload.dimension().equals(c.world.getRegistryKey().getValue())
                    ||!VillagerGiftTimeline.active((now-e.started)/1_000_000));
        });
        HudElementRegistry.addLast(Identifier.of("noncreatureaffinity","villager_gift"),VillagerGiftClient::render);
    }
    private static void accept(VillagerGiftPayload p){
        var c=MinecraftClient.getInstance();
        if(c.world==null||c.player==null||!p.dimension().equals(c.world.getRegistryKey().getValue())||!SEEN.add(p.receipt()))return;
        if(SEEN.size()>1024)SEEN.remove(SEEN.iterator().next());
        if(ACTIVE.size()>=16)ACTIVE.remove(ACTIVE.keySet().iterator().next());
        ACTIVE.put(p.receipt(),new Entry(p,System.nanoTime()));
    }
    public static boolean overlaps(float x,float y,float w,float h){
        for(var b:BOUNDS)if(x<b[0]+b[2]&&x+w>b[0]&&y<b[1]+b[3]&&y+h>b[1])return true;
        return false;
    }
    private static Placement placement(MinecraftClient c,Vec3d anchor,Vec3d right,Vec3d up,Vec3d p,float s,int w,int h){
        float ax=(float)((p.x+1)*w/2),ay=(float)((1-p.y)*h/2),bh=208*s*RequestBubbleGeometry.HEIGHT_RATIO;
        var pr=c.gameRenderer.project(anchor.add(right));var pu=c.gameRenderer.project(anchor.add(up));
        double sx=(pr.x-p.x)*w/2,sy=(pu.y-p.y)*h/2;
        float[][] offsets={{-32,-205},{-347,-205},{75,-200},{-455,-200},{75,-410},{-455,-410},{-32,-425},{-347,-425},{75,0},{-455,0}};
        for(var o:offsets){
            float x=Math.clamp(ax+o[0]*s,4,w-380*s-4),y=Math.clamp(ay+o[1]*s*RequestBubbleGeometry.HEIGHT_RATIO,4,h-bh-4);
            if(AffinityHud.overlaps(x,y,380*s,bh)||GiftHudClient.overlaps(x,y,380*s,bh)
                    ||FriendshipTokenClient.overlaps(x,y,380*s,bh)||overlaps(x,y,380*s,bh))continue;
            if(!AffinityHud.panelClear(c,anchor,right,up,ax,ay,x,y,s,sx,sy))continue;
            return new Placement(x,y,(ax-x)/s,(ay-y)/(s*RequestBubbleGeometry.HEIGHT_RATIO));
        }
        return null;
    }
    private static int alpha(int color,float a){return (Math.round((color>>>24)*a)<<24)|(color&0xffffff);}
    private static void frame(DrawContext c,float tx,float ty,float opacity){
        if(Math.abs(tx-32)<1&&Math.abs(ty-205)<1){
            c.drawTexture(RenderPipelines.GUI_TEXTURED,FRAME,0,0,0,0,380,208,380,208,alpha(0xffffffff,opacity));return;
        }
        // Same approved ellipse and tail union as requests, with independently translucent background.
        double[] tail=RequestBubbleGeometry.tail(tx,ty);
        int minX=Math.min(3,(int)Math.floor(tx)-2),maxX=Math.max(376,(int)Math.ceil(tx)+2);
        int minY=Math.min(15,(int)Math.floor(ty)-2),maxY=Math.max(196,(int)Math.ceil(ty)+2);
        for(int y=minY;y<=maxY;y++){
            int start=minX,previous=0;
            for(int x=minX;x<=maxX+1;x++){
                int color=0;
                if(x<=maxX&&RequestBubbleGeometry.inside(x+.5,y+.5,tail)){
                    boolean edge=!RequestBubbleGeometry.inside(x-1.5,y+.5,tail)||!RequestBubbleGeometry.inside(x+2.5,y+.5,tail)
                            ||!RequestBubbleGeometry.inside(x+.5,y-1.5,tail)||!RequestBubbleGeometry.inside(x+.5,y+2.5,tail);
                    color=alpha(edge?0xffad5a83:0xa0211624,opacity);
                }
                if(color!=previous){if(previous!=0)c.fill(start,y,x,y+1,previous);start=x;previous=color;}
            }
        }
    }
    private static void render(DrawContext draw,RenderTickCounter tick){
        BOUNDS.clear();var c=MinecraftClient.getInstance();
        if(c.world==null||c.player==null||c.currentScreen!=null||c.options.hudHidden||!NonCreatureAffinity.getConfig().hudEnabled)return;
        var camera=c.gameRenderer.getCamera();
        Vec3d right=new Vec3d(camera.getRotation().transform(new org.joml.Vector3f(1,0,0))),up=new Vec3d(camera.getRotation().transform(new org.joml.Vector3f(0,1,0)));
        int w=c.getWindow().getScaledWidth(),h=c.getWindow().getScaledHeight();if(w<16||h<16)return;
        float gui=(float)c.getWindow().getFramebufferWidth()/w;
        float base=Math.min(RequestBubbleGeometry.physicalWidth(c.getWindow().getFramebufferWidth())/(380*gui),Math.min((w-8)/390f,(h-8)/(214*RequestBubbleGeometry.HEIGHT_RATIO)));
        for(var e:ACTIVE.values()){
            long age=(System.nanoTime()-e.started)/1_000_000;if(!VillagerGiftTimeline.active(age))continue;
            if(!e.payload.dimension().equals(c.world.getRegistryKey().getValue()))continue;
            var entity=c.world.getEntity(e.payload.villager());if(!(entity instanceof LivingEntity mob)||!mob.isAlive())continue;
            Vec3d anchor=AffinityHud.headAnchor(c,mob,mob.getLerpedPos(tick.getTickProgress(false)),right);if(anchor==null)continue;
            if(anchor.subtract(camera.getCameraPos()).dotProduct(new Vec3d(camera.getHorizontalPlane()))<=0)continue;
            var point=c.gameRenderer.project(anchor);
            if(!Double.isFinite(point.x)||!Double.isFinite(point.y)||point.z< -1||point.z>1||Math.abs(point.x)>1||Math.abs(point.y)>1)continue;
            float scale=base*VillagerGiftTimeline.scale(age),opacity=VillagerGiftTimeline.opacity(age);
            var p=placement(c,anchor,right,up,point,scale,w,h);if(p==null)continue;
            BOUNDS.add(new float[]{p.x(),p.y(),380*scale,208*scale*RequestBubbleGeometry.HEIGHT_RATIO});
            draw.getMatrices().pushMatrix();draw.getMatrices().translate(p.x(),p.y());draw.getMatrices().scale(scale,scale*RequestBubbleGeometry.HEIGHT_RATIO);
            frame(draw,p.tx(),p.ty(),opacity);
            Text title=Text.translatable("message.noncreatureaffinity.villager_gift_bubble",e.payload.item().getName());
            float font=18f/(c.textRenderer.fontHeight*scale*gui);int limit=(int)(270/font);
            if(c.textRenderer.getWidth(title)>limit){String value=title.getString();int end=value.length();do{end=value.offsetByCodePoints(end,-1);title=Text.literal(value.substring(0,end)+"…");}while(end>0&&c.textRenderer.getWidth(title)>limit);}
            draw.getMatrices().pushMatrix();draw.getMatrices().translate(190,54);draw.getMatrices().scale(font,font);
            draw.drawText(c.textRenderer,title,-c.textRenderer.getWidth(title)/2,0,alpha(0xffffe9f4,opacity),false);draw.getMatrices().popMatrix();
            draw.getMatrices().pushMatrix();draw.getMatrices().translate(158,95);draw.getMatrices().scale(4,4/RequestBubbleGeometry.HEIGHT_RATIO);
            ScopedItemFade.draw(draw,e.payload.item(),0,0,opacity);draw.getMatrices().popMatrix();
            draw.getMatrices().popMatrix();
        }
    }
}
