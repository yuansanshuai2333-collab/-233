package com.nonid.affinity.reunion;
import com.nonid.affinity.net.ReunionNoticePayload;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
/** Replaces only the corresponding confirmed reason, preserving legacy fallback. */
public final class ReunionNoticeFeedback {
 private ReunionNoticeFeedback(){}
 public static boolean send(ServerPlayerEntity p,String reason){
  int kind=switch(reason){case "empty","need_three"->0;case "full_health"->1;case "not_found"->2;case "recall_attached"->3;default->-1;};
  if(kind<0||!ServerPlayNetworking.canSend(p,ReunionNoticePayload.ID))return false;
  ServerPlayNetworking.send(p,new ReunionNoticePayload(UUID.randomUUID(),p.getEntityWorld().getRegistryKey().getValue(),kind,kind==0?(reason.equals("need_three")?3:1):0));return true;
 }
}
