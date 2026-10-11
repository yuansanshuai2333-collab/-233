package com.nonid.affinity.client;

import com.nonid.affinity.item.AffinityDetectorItem;
import com.nonid.affinity.mixin.client.MemorialHudAccess;
import com.nonid.affinity.net.MemorialNotice;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Separate quiet layer: never replaces vanilla's action bar or plays a notification sound. */
public final class CompanionMemorialClient {
    private static MemorialNotice notice;
    private static int remaining;
    private CompanionMemorialClient() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(MemorialNotice.ID, (payload, context) -> context.client().execute(() -> {
            if (payload.ticks() == 0) {
                if (notice != null && notice.token().equals(payload.token())) clear();
            } else { notice = payload; remaining = payload.ticks(); }
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!valid(client)) clear();
            else if (!client.isPaused() && --remaining <= 0) clear();
        });
        HudElementRegistry.addLast(Identifier.of("noncreatureaffinity", "companion_memorial"), (context, counter) -> {
            var client = MinecraftClient.getInstance();
            if (!valid(client) || client.currentScreen != null || client.options.hudHidden || ClientRequestState.isActive()
                    || ((MemorialHudAccess)client.inGameHud).affinity$overlayRemaining() > 0) return;
            if (!notice.located()) return;
            var point = new net.minecraft.util.math.Vec3d(notice.x(), notice.y()+.25, notice.z());
            var camera=client.gameRenderer.getCamera();
            if (point.subtract(camera.getCameraPos()).dotProduct(new net.minecraft.util.math.Vec3d(camera.getHorizontalPlane())) <= 0) return;
            if (client.player.getEntityPos().squaredDistanceTo(new net.minecraft.util.math.Vec3d(notice.x(),notice.y(),notice.z())) > 256) return;
            var block=net.minecraft.util.math.BlockPos.ofFloored(point);
            if (!client.world.isChunkLoaded(block.getX()>>4,block.getZ()>>4)) return;
            if (client.world.raycast(new net.minecraft.world.RaycastContext(camera.getCameraPos(),point,
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,client.player)).getType()!=net.minecraft.util.hit.HitResult.Type.MISS) return;
            var projected=client.gameRenderer.project(point);
            if (!Double.isFinite(projected.x)||!Double.isFinite(projected.y)||projected.z < -1 || projected.z > 1 || Math.abs(projected.x)>1 || Math.abs(projected.y)>1) return;
            var labels = new java.util.ArrayList<String>();
            for (int i=0;i<notice.names().size();i++) {
                String name=notice.names().get(i),type=notice.types().get(i);
                if (name.isBlank()) { var id=Identifier.tryParse(type); name=id==null?type:net.minecraft.registry.Registries.ENTITY_TYPE.getOptionalValue(id).map(e->e.getName().getString()).orElse(type); }
                labels.add(name);
            }
            int width=client.getWindow().getScaledWidth(),height=client.getWindow().getScaledHeight();
            double distance=client.player.getEntityPos().distanceTo(new net.minecraft.util.math.Vec3d(notice.x(),notice.y(),notice.z()));
            double t=Math.clamp(distance/16,0,1),smooth=t*t*(3-2*t);
            int size=(int)Math.round(32+24*smooth),cx=(int)Math.round((projected.x+1)*width/2),cy=(int)Math.round((1-projected.y)*height/2);
            // Partial off-screen panels are omitted, never clamped into a false world location.
            if(cx-size/2<4||cx+size/2>width-4||cy-size-24<4||cy>height-4)return;
            CompanionSkin.image(context,"history-icons/memorial",cx-size/2,cy-size-24,size,size,64,64);
            int alpha=Math.min(255,remaining*255/20);if(alpha<=3)return;
            Text line1=Text.translatable("companion_archive.noncreatureaffinity.memorial_title");
            Text line2=Text.translatable("companion_archive.noncreatureaffinity.memorial_names",String.join("、",labels),notice.total());
            for(int i=0;i<2;i++) { var text=i==0?line1:line2;String visible=client.textRenderer.trimToWidth(text.getString(),Math.max(1,Math.min(240,2*Math.min(cx-4,width-cx-4))));
                context.drawText(client.textRenderer,visible,cx-client.textRenderer.getWidth(visible)/2,cy-20+i*11,(alpha<<24)|(i==0?0xEFA3C7:0xF8EDF2),true); }

        });
    }
    private static boolean valid(MinecraftClient client) {
        return notice != null && remaining > 0 && client.world != null && client.player != null && client.player.isAlive()
                && !client.player.isSpectator() && AffinityDetectorItem.isHeldBy(client.player)
                && client.world.getRegistryKey().getValue().toString().equals(notice.dimension());
    }
    private static void clear() { notice = null; remaining = 0; }
}
