package com.nonid.affinity.service;
import com.nonid.affinity.net.VillagerGiftPayload;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
public final class VillagerGiftFeedback {
 private VillagerGiftFeedback(){}
 public static boolean supported(ServerPlayerEntity p){return ServerPlayNetworking.canSend(p,VillagerGiftPayload.ID);}
 public static void sent(ServerPlayerEntity p,VillagerEntity giver,ItemStack actual){
  if(supported(p))ServerPlayNetworking.send(p,new VillagerGiftPayload(UUID.randomUUID(),giver.getUuid(),giver.getEntityWorld().getRegistryKey().getValue(),actual));
 }
}
