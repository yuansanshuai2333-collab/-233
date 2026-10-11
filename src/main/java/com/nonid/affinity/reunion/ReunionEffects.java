package com.nonid.affinity.reunion;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.net.ReunionEffectPayload;
import com.nonid.affinity.net.ReunionResultPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

public final class ReunionEffects {
    /** Starts after server validation, before the delayed spawn. Does not consume charge. */
    public static void start(ServerPlayerEntity player, MobEntity mob, ItemStack stack) {
        Hand hand = player.getMainHandStack() == stack ? Hand.MAIN_HAND
                : player.getOffHandStack() == stack ? Hand.OFF_HAND : null;
        if (hand == null) return;
        var payload = new ReunionEffectPayload(player.getUuid(), ReunionData.item(stack).getString("bond", ""),
                hand == Hand.OFF_HAND, player.getEntityWorld().getRegistryKey().getValue(), mob.getEntityPos(), mob.getHeight());
        for (var viewer : player.getEntityWorld().getPlayers()) {
            if (viewer != player && viewer.squaredDistanceTo(player) > 32 * 32) continue;
            try {
                if (ServerPlayNetworking.canSend(viewer, ReunionEffectPayload.ID)) ServerPlayNetworking.send(viewer, payload);
            } catch (RuntimeException failure) {
                NonCreatureAffinity.LOGGER.warn("Cannot send reunion visual to {}", viewer.getUuidAsString(), failure);
            }
        }
    }
    public static void finish(net.minecraft.server.world.ServerWorld world, java.util.UUID player, String bond, boolean success) {
        var payload = new ReunionResultPayload(player, bond, success);
        for (var viewer : world.getPlayers()) {
            try {
                if (ServerPlayNetworking.canSend(viewer, ReunionResultPayload.ID)) ServerPlayNetworking.send(viewer, payload);
            } catch (RuntimeException failure) {
                NonCreatureAffinity.LOGGER.warn("Cannot finish reunion visual for {}", viewer.getUuidAsString(), failure);
            }
        }
    }
    private ReunionEffects() { }
}
