package com.nonid.affinity.client;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.net.AffinityIncreasePayload;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

@Environment(EnvType.CLIENT)
public final class AffinityIncreaseClient {
    private record Entry(AffinityIncreasePayload data, long started) {}
    private static final Map<UUID, Entry> ENTRIES = new LinkedHashMap<>();
    private static final StyleSpriteSource.Font FONT = new StyleSpriteSource.Font(
            Identifier.of(NonCreatureAffinity.MOD_ID, "request"));
    private AffinityIncreaseClient() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(AffinityIncreasePayload.ID,
                (payload, context) -> context.client().execute(() -> accept(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ENTRIES.clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) { ENTRIES.clear(); return; }
            long now = System.nanoTime();
            ENTRIES.values().removeIf(e -> !e.data.dimension().equals(client.world.getRegistryKey().getValue())
                    || !AffinityIncreaseTimeline.active((now - e.started) / 1_000_000));
        });
        HudElementRegistry.addLast(Identifier.of(NonCreatureAffinity.MOD_ID, "affinity_increase"),
                AffinityIncreaseClient::render);
    }
    public static void accept(AffinityIncreasePayload data) {
        var c = MinecraftClient.getInstance();
        if (c.world == null || c.player == null || data.after() <= data.before() || data.maximum() <= 0
                || !data.dimension().equals(c.world.getRegistryKey().getValue())) return;
        ENTRIES.remove(data.target());
        if (ENTRIES.size() >= 16) ENTRIES.remove(ENTRIES.keySet().iterator().next());
        ENTRIES.put(data.target(), new Entry(data, System.nanoTime()));
    }
    private static int color(int rgb, double alpha) { return ((int)(255 * alpha) << 24) | rgb; }
    private static void heart(DrawContext c, int x, int y, int color) {
        c.fill(x, y, x+2, y+1, color); c.fill(x+3, y, x+5, y+1, color);
        c.fill(x-1, y+1, x+6, y+3, color); c.fill(x, y+3, x+5, y+4, color);
        c.fill(x+1, y+4, x+4, y+5, color); c.fill(x+2, y+5, x+3, y+6, color);
    }
    private static void render(DrawContext draw, RenderTickCounter tick) {
        var c = MinecraftClient.getInstance();
        if (c.player == null || c.world == null || c.currentScreen != null || c.options.hudHidden
                || !NonCreatureAffinity.getConfig().hudEnabled) return;
        long now = System.nanoTime();
        var camera = c.gameRenderer.getCamera();
        Vec3d right = new Vec3d(camera.getRotation().transform(new org.joml.Vector3f(1,0,0)));
        int width=c.getWindow().getScaledWidth(), height=c.getWindow().getScaledHeight();
        float guiPixels=(float)c.getWindow().getFramebufferWidth()/width;
        float scale=1/guiPixels;
        for (var entry : ENTRIES.values()) {
            var d=entry.data; long elapsed=(now-entry.started)/1_000_000;
            double alpha=AffinityIncreaseTimeline.opacity(elapsed); if (alpha<=0) continue;
            if (!d.dimension().equals(c.world.getRegistryKey().getValue())) continue;
            var entity=c.world.getEntity(d.target());
            if (!(entity instanceof LivingEntity mob) || !mob.isAlive()) continue;
            Vec3d pos=mob.getLerpedPos(tick.getTickProgress(false));
            Vec3d anchor=pos.add(right.multiply(mob.getWidth()*.5+.22)).add(0,mob.getHeight()*.65,0);
            if (anchor.subtract(camera.getCameraPos()).dotProduct(new Vec3d(camera.getHorizontalPlane()))<=0
                    || c.world.raycast(new RaycastContext(camera.getCameraPos(),pos.add(0,mob.getHeight()*.65,0),
                    RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,c.player)).getType()!=HitResult.Type.MISS) continue;
            var projected=c.gameRenderer.project(anchor);
            if (!Double.isFinite(projected.x)||!Double.isFinite(projected.y)||projected.z < -1||projected.z>1
                    || Math.abs(projected.x)>1||Math.abs(projected.y)>1) continue;
            float x=(float)((projected.x+1)*width/2), y=(float)((1-projected.y)*height/2);
            // Below the head bubbles; clamp only the decoration, never any target or relationship.
            y=Math.clamp(Math.max(y,GiftHudClient.feedbackFloor(d.target())),4,height-110*scale-4); x=Math.clamp(x,4,width-105*scale-4);
            draw.getMatrices().pushMatrix(); draw.getMatrices().translate(x,y); draw.getMatrices().scale(scale,scale);
            draw.fill(0,0,12,104,color(0xAB5A83,alpha)); draw.fill(1,1,11,103,color(0x211624,alpha));
            double ratio=AffinityIncreaseTimeline.shownRatio(d.before(),d.after(),d.maximum(),elapsed);
            int fill=(int)Math.round(102*ratio); if(fill>0)draw.fill(1,103-fill,11,103,color(0xE77BAC,alpha));
            Text percentage=Text.literal(Math.round(ratio*100)+"%").styled(s->s.withFont(FONT));
            draw.getMatrices().pushMatrix();draw.getMatrices().translate(20,84);draw.getMatrices().scale(1.4f,1.4f);
            draw.drawText(c.textRenderer,percentage,0,0,color(0xFFE9F4,alpha),false);draw.getMatrices().popMatrix();
            for(int i=0;i<3;i++) {
                double t=Math.clamp((elapsed-200-i*140)/1700.0,0,1);
                if(t>0&&t<1)heart(draw,19+(i%2)*17,(int)(65-t*70)+i*4,color(0xF49EC2,alpha*Math.sin(t*Math.PI)));
            }
            draw.getMatrices().popMatrix();
        }
    }
}
