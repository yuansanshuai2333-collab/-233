package com.nonid.affinity.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import com.nonid.affinity.net.CompanionInfoData;
import com.nonid.affinity.net.CompanionBrowse;

public final class CompanionInfoClient {
    private CompanionInfoClient() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CompanionBrowse.Reply.ID,(data,context)->context.client().execute(()->{
            if(context.client().currentScreen instanceof CompanionDirectoryScreen screen)screen.receive(data);
        }));
        CompanionHighlightClient.register();
        CompanionModelPreview.register();
        ClientPlayNetworking.registerGlobalReceiver(com.nonid.affinity.net.DirectInteractionOption.ID, (data, context) -> context.client().execute(() -> {
            if(context.client().currentScreen instanceof CompanionInfoScreen screen) screen.receiveOption(data);
            if(context.client().currentScreen instanceof CompanionDirectoryScreen screen) screen.receiveOption(data);
        }));
        ClientPlayNetworking.registerGlobalReceiver(CompanionInfoData.ID, (data, context) -> context.client().execute(() -> {
            if(context.client().currentScreen instanceof CompanionInfoScreen screen) screen.receive(data);
            AffinityLabelRenderer.receive(data);
        }));
    }
    /** Snapshot the actual aimed entity once. Never choose an arbitrary nearby creature. */
    public static void open() {
        var c = MinecraftClient.getInstance();
        if(c.world == null || c.player == null || c.currentScreen != null) return;
        LivingEntity target = c.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living
                && !(living instanceof PlayerEntity) ? living : null;
        c.setScreen(new CompanionInfoScreen(target == null ? null : target.getUuid(), c.world.getRegistryKey().getValue().toString()));
    }
    public static void openDirectory() {
        if(tryDetectorHighlight())return;
        var c=MinecraftClient.getInstance();
        if(c.player==null||c.world==null||c.currentScreen!=null||!com.nonid.affinity.item.AffinityDetectorItem.isHeldBy(c.player))return;
        if(c.crosshairTarget!=null&&c.crosshairTarget.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)return;
        c.setScreen(new CompanionDirectoryScreen());
    }
    public static boolean tryDetectorHighlight(){
        var c=MinecraftClient.getInstance();
        if(c.world==null||c.player==null||c.currentScreen!=null||!c.player.isAlive()||c.player.isSpectator()||!c.player.isSneaking()||!com.nonid.affinity.item.AffinityDetectorItem.isHeldBy(c.player))return false;
        CompanionHighlightClient.request();return true;
    }
    /** The old empty-hand highlight entry has moved to the detector; companion mode interaction is unchanged. */
    public static void afterUnusedEmptyHand() {}
}
