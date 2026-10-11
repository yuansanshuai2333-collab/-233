package com.nonid.affinity.client;

import com.nonid.affinity.item.AffinityDetectorItem;
import com.nonid.affinity.net.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.*;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** The v7 aimed HUD: plain information empty-handed, expanded information with a detector. */
public final class AffinityLabelRenderer {
    private static final double MAX_DISTANCE=32;
    private static CompanionInfoLease lease;
    private static CompanionInfoData snapshot;
    private static UUID target;
    private static String dimension;
    private static boolean detailed;
    private static int ticks;
    private AffinityLabelRenderer(){}
    public static void register(){HudElementRegistry.addLast(Identifier.of("noncreatureaffinity","affinity_label"),AffinityLabelRenderer::render);ClientTickEvents.END_CLIENT_TICK.register(AffinityLabelRenderer::tickDetector);}
    private static void clear(){if(lease!=null)lease.close();lease=null;snapshot=null;target=null;dimension=null;ClientRequestState.clearDetector();}
    public static void tickDetector(MinecraftClient c){
        if(c.player==null||c.world==null||c.currentScreen!=null||!AffinityDetectorState.isEnabled(c.player.getUuid())){clear();return;}
        // Other held items do not become a second way to obtain the detailed detector HUD.
        boolean held=AffinityDetectorItem.isHeldBy(c.player);
        if(!held&&(!c.player.getMainHandStack().isEmpty()||!c.player.getOffHandStack().isEmpty())){clear();return;}
        var e=findLookedAtCreature(c,1f);if(e==null||e instanceof net.minecraft.entity.player.PlayerEntity){clear();return;}
        String dim=c.world.getRegistryKey().getValue().toString();
        if(lease==null||!e.getUuid().equals(target)||!dim.equals(dimension)||held!=detailed){clear();target=e.getUuid();dimension=dim;detailed=held;lease=new CompanionInfoLease(target,dimension);ticks=0;ClientRequestState.setRequestedTarget(target);}
        lease.tick();if(lease.expired())snapshot=null;
        if(ticks++%10==0&&ClientPlayNetworking.canSend(CompanionInfoQuery.ID))ClientPlayNetworking.send(new CompanionInfoQuery(lease.page(),lease.issue(),target,dimension));
    }
    public static void receive(CompanionInfoData data){var c=MinecraftClient.getInstance();if(lease==null||c.player==null||c.currentScreen!=null||AffinityDetectorItem.isHeldBy(c.player)!=detailed)return;
        var q=data.query();if(lease.accept(q.page(),q.target(),q.dimension(),q.sequence()))snapshot=data;
    }
    private static Text t(String k,Object...args){return CompanionInfoScreen.t(k,args);}
    private static void line(DrawContext c,Text text,int x,int y,int w,int color){var tr=MinecraftClient.getInstance().textRenderer;c.drawText(tr,tr.trimToWidth(text.getString(),Math.max(1,w)),x,y,color,false);}
    private static String num(float value){return value<0?"—":String.format(Locale.ROOT,"%.1f",value);}
    private static void metric(DrawContext c,String label,String value,int x,int y,int w){var tr=MinecraftClient.getInstance().textRenderer;int n=tr.getWidth(value);line(c,t(label),x,y,w-n-6,0xFFF8EDF2);c.drawText(tr,value,x+w-n,y,0xFFF8EDF2,false);}
    private static void render(DrawContext c,RenderTickCounter delta){
        var mc=MinecraftClient.getInstance();if(mc.player==null||mc.world==null||mc.options.hudHidden||mc.currentScreen!=null||AffinityHud.hidesAffinityHud())return;
        if(!AffinityDetectorState.isEnabled(mc.player.getUuid())){
            if(AffinityDetectorItem.isHeldBy(mc.player)){var msg=Text.translatable("message.noncreatureaffinity.detector_hint");int w=Math.min(mc.getWindow().getScaledWidth()-16,mc.textRenderer.getWidth(msg)+16);CompanionHudSkin.frame(c,8,8,w,24,null,.4f);line(c,msg,16,16,w-16,0xFFF8EDF2);}return;
        }
        if(target==null||lease==null)return;var entity=mc.world.getEntity(target);if(!(entity instanceof LivingEntity)||!entity.isAlive())return;
        int sw=mc.getWindow().getScaledWidth(),sh=mc.getWindow().getScaledHeight();int w=Math.min(detailed?189:168,Math.min((int)(sw*.30),sw/2-34));if(w<80)return;
        var d=snapshot;boolean valid=d!=null&&d.hasEntity();
        if (entity instanceof net.minecraft.entity.decoration.ArmorStandEntity
                && (!valid || d.affinity() <= 0)) return;
        Text family=valid?familyText(d.family()):Text.empty();int familyHeight=family.getString().isEmpty()?0:12;
        boolean full=detailed&&(!ClientRequestState.isActive()||sh/2+30>=148);int h=(detailed?(full?160:88):62)+familyHeight;
        int x=sw/2+24,y=Math.max(8,Math.min(sh/2+12,sh-h-12));if(ClientRequestState.isActive())y=Math.max(8,sh/2+30-h-12);
        String stage=valid?d.stage():null;float scale=w/(detailed?504f:448f);
        CompanionHudSkin.frame(c,x,y,w,h,stage,scale);
        String marker=valid?Text.translatable("stage.noncreatureaffinity."+stage).getString():"—";int mw=mc.textRenderer.getWidth(marker);
        Text name=valid&&!d.name().isEmpty()?Text.literal(d.name()):entity.getName();line(c,name,x+8,y+10,w-22-mw,0xFFF8EDF2);line(c,Text.literal(marker),x+w-8-mw,y+10,mw,0xFFF29CC3);
        if(!valid){line(c,t("status."+(d==null?"loading":d.status())),x+8,y+30,w-16,0xFFBFA1B6);return;}
        if(familyHeight>0)line(c,family,x+8,y+23,w-16,0xFFF29CC3);
        // Keep the approved frame and content spacing; only reserve one text row below the name.
        y+=familyHeight;
        int affinityY=y+(detailed?52:30);
        if(detailed){metric(c,"health",num(d.health())+" / "+num(d.maxHealth()),x+8,y+28,w-16);CompanionHudSkin.bar(c,x+8,y+40,w-16,d.health(),d.maxHealth(),true,stage,scale);}
        metric(c,"affinity",d.affinity()+" / "+d.maximum(),x+8,affinityY,w-16);CompanionSkin.image(c,"specialty/hearts/"+CompanionHudSkin.stage(stage),x+8,affinityY+8,18,18,128,128);CompanionHudSkin.bar(c,x+28,affinityY+13,w-36,d.affinity(),d.maximum(),false,stage,scale);
        if(full){
            int column=(w-16)/3;
            attribute(c,"attack","hud_attack",value(d.attack()),x+8,y+82,column,stage);
            attribute(c,"defense","hud_defense",value(d.armor()),x+8+column,y+82,column,stage);
            attribute(c,"regeneration","hud_regeneration",t("unknown").getString(),x+8+2*column,y+82,w-16-2*column,stage);
            line(c,t("owner").copy().append(": ").append(t("owner."+d.owner())),x+8,y+110,w-16,CompanionUi.TEXT);
            line(c,t("control").copy().append(": ").append(t("control."+d.control())),x+8,y+122,w-16,CompanionUi.TEXT);
            line(c,t("mode").copy().append(": ").append(t("mode."+d.mode())),x+8,y+134,w-16,CompanionUi.TEXT);
            String number=d.tokens()<0?"—":Integer.toString(d.tokens());int numberW=mc.textRenderer.getWidth(number),balloon=d.tokens()>0?20:0,gap=balloon>0?4:0;
            String label=mc.textRenderer.trimToWidth(t("tokens").getString(),Math.max(0,w-16-numberW-balloon-gap-6));
            int labelW=mc.textRenderer.getWidth(label),groupW=labelW+6+numberW+gap+balloon,groupX=x+w-8-groupW;
            line(c,Text.literal(label),groupX,y+146,labelW,CompanionUi.TEXT);
            line(c,Text.literal(number),groupX+labelW+6,y+146,numberW,CompanionUi.TEXT);
            if(balloon>0){int level=Math.min(6,d.tokens());CompanionSkin.image(c,"counts/balloon-"+level,x+w-28,y+141,20,level<=3?13:16,level<=3?1536:1402,level<=3?1024:1122);}

        }
    }
    private static Text familyText(String key) {
        return switch(key) {
            case "son", "daughter", "child", "grandson", "granddaughter", "grandchild", "descendant",
                    "father", "mother", "parent", "grandfather", "grandmother", "grandparent", "ancestor" ->
                Text.translatable("hud.noncreatureaffinity.family."+key);
            default -> Text.empty();
        };
    }
    private static String value(double number){return !Double.isFinite(number)||number<0?t("unknown").getString():String.format(Locale.ROOT,"%.1f",number);}
    private static void attribute(DrawContext c,String icon,String key,String number,int x,int y,int w,String stage){
        CompanionSkin.image(c,"increment/attributes/"+icon+"-"+CompanionHudSkin.stage(stage),x,y,10,10,64,64);
        line(c,t(key),x+13,y,Math.max(0,w-13),CompanionUi.MUTED);
        line(c,Text.literal(number),x,y+11,w,CompanionUi.TEXT);
    }
    @Nullable
    private static LivingEntity findLookedAtCreature(MinecraftClient client, float tickDelta) {
        if (client.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity living
                && isEligible(client, living)) {
            return living;
        }

        Vec3d start = client.player.getCameraPosVec(tickDelta);
        Vec3d direction = client.player.getRotationVec(tickDelta);
        Vec3d end = start.add(direction.multiply(MAX_DISTANCE));
        Box searchBox = client.player.getBoundingBox()
                .stretch(direction.multiply(MAX_DISTANCE))
                .expand(1.0, 1.0, 1.0);

        EntityHitResult result = ProjectileUtil.raycast(
                client.player,
                start,
                end,
                searchBox,
                entity -> entity instanceof LivingEntity && isEligible(client, entity),
                MAX_DISTANCE * MAX_DISTANCE);
        if (result != null && result.getEntity() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private static boolean isEligible(MinecraftClient client, Entity entity) {
        return entity instanceof LivingEntity
                && entity != client.player
                && entity.isAlive()
                && !entity.isSpectator()
                && entity.squaredDistanceTo(client.player) <= MAX_DISTANCE * MAX_DISTANCE;
    }
}
