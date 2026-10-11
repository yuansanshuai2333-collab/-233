package com.nonid.affinity.net;

import com.nonid.affinity.service.CompanionInfoService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class CompanionInfoNetworking {
    private static final Map<UUID, Integer> LAST = new HashMap<>();
    private static final Map<UUID, Integer> LAST_HIGHLIGHT = new HashMap<>();
    private CompanionInfoNetworking() {}
    public static void register() {
        CompanionBrowseNetworking.register();
        PayloadTypeRegistry.playC2S().register(CompanionHighlight.Request.ID, CompanionHighlight.Request.CODEC);
        PayloadTypeRegistry.playS2C().register(CompanionHighlight.Reply.ID, CompanionHighlight.Reply.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CompanionHighlight.Request.ID,(q,context)->context.server().execute(()->{
            var p=context.player();if(!ServerPlayNetworking.canSend(p,CompanionHighlight.Reply.ID))return;
            int now=context.server().getTicks();var last=LAST_HIGHLIGHT.get(p.getUuid());
            if(last!=null&&now-last>=0&&now-last<5)return;
            LAST_HIGHLIGHT.put(p.getUuid(),now);
            ServerPlayNetworking.send(p,com.nonid.affinity.service.CompanionHighlightService.read(p,q.nonce()));
        }));
        PayloadTypeRegistry.playC2S().register(DirectInteractionOption.ID, DirectInteractionOption.CODEC);
        PayloadTypeRegistry.playS2C().register(DirectInteractionOption.ID, DirectInteractionOption.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DirectInteractionOption.ID, (q, context) -> context.server().execute(() -> {
            var player = context.player();
            if (!ServerPlayNetworking.canSend(player, DirectInteractionOption.ID)) return;
            ServerPlayNetworking.send(player, CompanionBrowseNetworking.option(player,q,context.server().getTicks()));
        }));
        PayloadTypeRegistry.playC2S().register(CompanionInfoQuery.ID, CompanionInfoQuery.CODEC);
        PayloadTypeRegistry.playS2C().register(CompanionInfoData.ID, CompanionInfoData.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CompanionInfoQuery.ID, (q, context) -> context.server().execute(() -> {
            var p = context.player();
            if (!ServerPlayNetworking.canSend(p, CompanionInfoData.ID)) return;
            int now = context.server().getTicks();
            Integer last = LAST.get(p.getUuid());
            if (last != null && now - last >= 0 && now - last < 10) return;
            LAST.put(p.getUuid(), now);
            ServerPlayNetworking.send(p, CompanionInfoService.forHands(p, CompanionInfoService.read(p, q)));
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {LAST.remove(handler.player.getUuid());LAST_HIGHLIGHT.remove(handler.player.getUuid());});
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {LAST.clear();LAST_HIGHLIGHT.clear();});
    }
}

