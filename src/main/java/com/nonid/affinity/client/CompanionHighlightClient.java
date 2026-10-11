package com.nonid.affinity.client;

import com.nonid.affinity.net.CompanionHighlight;
import com.nonid.affinity.service.CompanionHighlightService;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/** A private outline decision, never a mutation of the synced glowing flag or scoreboard team. */
public final class CompanionHighlightClient {
    private static Set<UUID> targets=Set.of();
    private static java.util.Map<UUID,CompanionHighlight.Target> colors=java.util.Map.of();private static int maximum;
    private static final ThreadLocal<Boolean> EXTERNAL_CHECK=ThreadLocal.withInitial(()->false);
    private static UUID pending;
    private static String dimension="";
    private static int remaining,pendingAge;
    private CompanionHighlightClient() {}
    public static void register(){
        ClientPlayNetworking.registerGlobalReceiver(CompanionHighlight.Reply.ID,(reply,ctx)->ctx.client().execute(()->receive(reply)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.world==null||client.player==null||!client.player.isAlive()||!client.world.getRegistryKey().getValue().toString().equals(dimension)){clear();return;}
            if(pending!=null&&++pendingAge>=60){pending=null;ReunionNoticeClient.local(net.minecraft.text.Text.translatable("message.noncreatureaffinity.highlight_timeout"));}
            if(remaining>0&&--remaining==0){targets=Set.of();colors=java.util.Map.of();}
        });
    }
    public static void request(){
        var c=MinecraftClient.getInstance();
        if(c.world==null||c.player==null||c.currentScreen!=null||!c.player.isSneaking()||c.player.isSpectator()||!com.nonid.affinity.item.AffinityDetectorItem.isHeldBy(c.player))return;
        if(!ClientPlayNetworking.canSend(CompanionHighlight.Request.ID)){ReunionNoticeClient.local(net.minecraft.text.Text.translatable("message.noncreatureaffinity.highlight_unavailable"));return;}
        if(pending!=null&&pendingAge<5)return;
        dimension=c.world.getRegistryKey().getValue().toString();pending=UUID.randomUUID();pendingAge=0;
        ClientPlayNetworking.send(new CompanionHighlight.Request(pending));
    }
    private static void receive(CompanionHighlight.Reply reply){
        var c=MinecraftClient.getInstance();
        if(pending==null||!pending.equals(reply.nonce())||pendingAge>=60||c.world==null||!dimension.equals(reply.dimension())||!dimension.equals(c.world.getRegistryKey().getValue().toString()))return;
                pending=null;if(!reply.accepted()){ReunionNoticeClient.local(net.minecraft.text.Text.translatable("message.noncreatureaffinity.highlight_unavailable"));return;}targets=Set.copyOf(reply.targets());maximum=reply.maximum();var updated=new java.util.HashMap<UUID,CompanionHighlight.Target>();for(var row:reply.entries())updated.put(row.id(),row);colors=java.util.Map.copyOf(updated);remaining=CompanionHighlightService.DURATION_TICKS;
        ReunionNoticeClient.local(net.minecraft.text.Text.translatable(targets.isEmpty()?"message.noncreatureaffinity.highlight_empty":"message.noncreatureaffinity.highlight_done"));
    }
    public static boolean outlines(Entity entity){
        var c=MinecraftClient.getInstance();
        return !EXTERNAL_CHECK.get()&&remaining>0&&c.world!=null&&c.player!=null&&c.player.isAlive()&&entity instanceof LivingEntity&&entity.isAlive()&&!entity.isRemoved()&&entity.getEntityWorld()==c.world&&c.world.getRegistryKey().getValue().toString().equals(dimension)&&targets.contains(entity.getUuid())&&c.player.squaredDistanceTo(entity)<=CompanionHighlightService.RANGE*CompanionHighlightService.RANGE;
    }
    /** Other outline providers and scoreboard styling retain precedence. Never changes the entity/team. */
    public static int color(Entity entity,int original){
        if(!outlines(entity)||entity.isGlowing()||entity.getScoreboardTeam()!=null||(original&0xFFFFFF)!=0xFFFFFF)return original;
        boolean previous=EXTERNAL_CHECK.get();EXTERNAL_CHECK.set(true);
        try {if(MinecraftClient.getInstance().hasOutline(entity))return original;}finally{EXTERNAL_CHECK.set(previous);}
        var row=colors.get(entity.getUuid());
        return row==null?original:CompanionHighlightPalette.color(row.hasRelation(),row.affinity(),maximum);
    }
    private static void clear(){targets=Set.of();colors=java.util.Map.of();pending=null;remaining=0;dimension="";maximum=0;}
}


