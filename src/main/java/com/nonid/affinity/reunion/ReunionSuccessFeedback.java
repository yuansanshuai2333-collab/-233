package com.nonid.affinity.reunion;
import com.nonid.affinity.net.ReunionSuccessPayload;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
public final class ReunionSuccessFeedback {
 private ReunionSuccessFeedback(){}
 public static void send(ServerPlayerEntity player,MobEntity actual,boolean healing){
  if(actual!=null&&ServerPlayNetworking.canSend(player,ReunionSuccessPayload.ID)){
   ServerPlayNetworking.send(player,new ReunionSuccessPayload(UUID.randomUUID(),actual.getEntityWorld().getRegistryKey().getValue(),actual.getUuid(),actual.getId(),healing,actual.getEntityPos(),actual.getWidth(),actual.getHeight()));
  }else ReunionService.message(player,healing?"healed":"recalled");
 }
}
