package com.nonid.affinity.net;
import com.nonid.affinity.gift.GiftService;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.LivingEntity;
public final class GiftNetworking {
 private static final Map<UUID,Long> QUERIES=new HashMap<>(),CLAIMS=new HashMap<>();
 private GiftNetworking(){}
 public static void register(){
  PayloadTypeRegistry.playC2S().register(GiftQueryPayload.ID,GiftQueryPayload.CODEC);
  PayloadTypeRegistry.playC2S().register(GiftClaimPayload.ID,GiftClaimPayload.CODEC);
  PayloadTypeRegistry.playS2C().register(GiftViewPayload.ID,GiftViewPayload.CODEC);
  PayloadTypeRegistry.playS2C().register(GiftResultPayload.ID,GiftResultPayload.CODEC);
  PayloadTypeRegistry.playS2C().register(GiftOpeningPayload.ID,GiftOpeningPayload.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(GiftQueryPayload.ID,(p,c)->c.server().execute(()->{
   if(p.sequence()<1||!allow(QUERIES,c.player().getUuid(),c.server().getTicks(),5))return;
   if(ServerPlayNetworking.canSend(c.player(),GiftViewPayload.ID))ServerPlayNetworking.send(c.player(),new GiftViewPayload(p.session(),p.sequence(),c.player().getEntityWorld().getRegistryKey().getValue().toString(),GiftService.views(c.player())));
  }));
  ServerPlayNetworking.registerGlobalReceiver(GiftClaimPayload.ID,(p,c)->c.server().execute(()->{
   if(!allow(CLAIMS,c.player().getUuid(),c.server().getTicks(),2))return;
   String status;if(p.bequest())status=GiftService.claimBequest(c.player(),p.gift());
   else {var e=c.player().getEntityWorld().getEntity(p.target());status=e instanceof LivingEntity living?GiftService.claim(c.player(),p.gift(),living):"unavailable";}
   if(ServerPlayNetworking.canSend(c.player(),GiftResultPayload.ID))ServerPlayNetworking.send(c.player(),new GiftResultPayload(p.session(),p.gift(),status));
  }));
  ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{QUERIES.remove(h.player.getUuid());CLAIMS.remove(h.player.getUuid());});
  ServerLifecycleEvents.SERVER_STOPPED.register(s->{QUERIES.clear();CLAIMS.clear();});
 }
 private static boolean allow(Map<UUID,Long> map,UUID player,long tick,int interval){var previous=map.get(player);if(previous!=null&&tick>=previous&&tick-previous<interval)return false;map.put(player,tick);return true;}
}
