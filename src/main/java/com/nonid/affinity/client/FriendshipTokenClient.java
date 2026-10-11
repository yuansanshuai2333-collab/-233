package com.nonid.affinity.client;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.net.FriendshipTokenPayload;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class FriendshipTokenClient {
    private record Entry(FriendshipTokenPayload payload,long started) {}
    private static final Map<UUID,Entry> ACTIVE=new LinkedHashMap<>();
    private static final Set<UUID> SEEN=new LinkedHashSet<>();
    private static final List<float[]> BOUNDS=new ArrayList<>();
    public static boolean overlaps(float x,float y,float w,float h){
        for(float[] b:BOUNDS)if(x<b[0]+b[2]&&x+w>b[0]&&y<b[1]+b[3]&&y+h>b[1])return true;
        return false;
    }
    private static final Identifier[] FRAMES=new Identifier[60];
    static {for(int i=0;i<FRAMES.length;i++)FRAMES[i]=Identifier.of("noncreatureaffinity",String.format(Locale.ROOT,"textures/gui/token/frame-%02d.png",i));}
    private FriendshipTokenClient() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FriendshipTokenPayload.ID,(p,c)->c.client().execute(()->accept(p)));
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{ACTIVE.clear();SEEN.clear();});
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(c.world==null||c.player==null){ACTIVE.clear();return;}
            long now=System.nanoTime();
            ACTIVE.values().removeIf(e->!e.payload.dimension().equals(c.world.getRegistryKey().getValue())
                    ||FriendshipTokenTimeline.frame((now-e.started)/1_000_000)<0);
        });
        HudElementRegistry.addLast(Identifier.of("noncreatureaffinity","friendship_token"),FriendshipTokenClient::render);
    }
    private static void accept(FriendshipTokenPayload p) {
        var c=MinecraftClient.getInstance();
        if(c.world==null||c.player==null||!p.dimension().equals(c.world.getRegistryKey().getValue())||!SEEN.add(p.receipt()))return;
        if(SEEN.size()>1024)SEEN.remove(SEEN.iterator().next());
        if(ACTIVE.size()>=16)ACTIVE.remove(ACTIVE.keySet().iterator().next());
        ACTIVE.put(p.receipt(),new Entry(p,System.nanoTime()));
    }
    private static void render(DrawContext draw,RenderTickCounter tick) {
        BOUNDS.clear();
        var c=MinecraftClient.getInstance();
        if(c.world==null||c.player==null||c.currentScreen!=null||c.options.hudHidden||!NonCreatureAffinity.getConfig().hudEnabled)return;
        var camera=c.gameRenderer.getCamera();
        Vec3d right=new Vec3d(camera.getRotation().transform(new org.joml.Vector3f(1,0,0)));
        int w=c.getWindow().getScaledWidth(),h=c.getWindow().getScaledHeight();
        float gui=(float)c.getWindow().getFramebufferWidth()/w,scale=Math.min(1/gui,Math.min((w-8)/108f,(h-8)/260f));
        Set<UUID> drawn=new HashSet<>();
        for(var e:ACTIVE.values()) {
            int frame=FriendshipTokenTimeline.frame((System.nanoTime()-e.started)/1_000_000);if(frame<0)continue;
            if(!e.payload.dimension().equals(c.world.getRegistryKey().getValue()))continue;
            var entity=c.world.getEntity(e.payload.target());
            if(!(entity instanceof LivingEntity mob)||!mob.isAlive()||!drawn.add(mob.getUuid()))continue;
            Vec3d anchor=AffinityHud.headAnchor(c,mob,mob.getLerpedPos(tick.getTickProgress(false)),right);if(anchor==null)continue;
            anchor=anchor.subtract(right.multiply(mob.getWidth()*.5+.25));
            if(anchor.subtract(camera.getCameraPos()).dotProduct(new Vec3d(camera.getHorizontalPlane()))<=0)continue;
            var p=c.gameRenderer.project(anchor);
            if(!Double.isFinite(p.x)||!Double.isFinite(p.y)||p.z< -1||p.z>1||Math.abs(p.x)>1||Math.abs(p.y)>1)continue;
            float x=(float)((p.x+1)*w/2)-108*scale,y=(float)((1-p.y)*h/2)-130*scale;
            x=Math.clamp(x,4,w-108*scale-4);y=Math.clamp(y,4,h-260*scale-4);
            BOUNDS.add(new float[]{x,y,108*scale,260*scale});
            draw.getMatrices().pushMatrix();draw.getMatrices().translate(x,y);draw.getMatrices().scale(scale,scale);
            draw.drawTexture(RenderPipelines.GUI_TEXTURED,FRAMES[frame],0,0,0,0,108,260,108,260);
            draw.getMatrices().popMatrix();
        }
    }
}
