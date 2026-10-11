package com.nonid.affinity.reunion;

import com.nonid.affinity.item.NonAffinityItems;
import com.nonid.affinity.service.ReplayIsolation;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/** In-memory preparations only. A restart cancels safely because the death snapshot/charge stay untouched. */
public final class ReunionPending {
    private record Preparation(UUID player, UUID creature, String bond, long charge, Hand hand,
                               ServerWorld world, Vec3d position, int started) { }
    private static final Map<UUID, Preparation> ACTIVE = new HashMap<>();
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ReunionPending::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
    }
    public static boolean contains(ServerPlayerEntity player) { return ACTIVE.containsKey(player.getUuid()); }
    public static void begin(ServerPlayerEntity player, MobEntity creature, ItemStack stack, Hand hand) {
        var item = ReunionData.item(stack);
        ACTIVE.put(player.getUuid(), new Preparation(player.getUuid(), creature.getUuid(), item.getString("bond", ""),
                ReunionData.fuel(item), hand, player.getEntityWorld(), creature.getEntityPos(), player.getEntityWorld().getServer().getTicks()));
        ReunionEffects.start(player, creature, stack);
        com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable("message.noncreatureaffinity.reunion_preparing"), true);
    }
    private static boolean valid(Preparation pending, ServerPlayerEntity player) {
        if (player == null || !player.isAlive() || player.isSpectator() || player.getEntityWorld() != pending.world()
                || player.squaredDistanceTo(pending.position()) > 16 * 16 || ReplayIsolation.isActor(player.getUuid())) return false;
        var stack = player.getStackInHand(pending.hand());
        if (!stack.isOf(NonAffinityItems.REUNION_WISH)) return false;
        var item = ReunionData.item(stack);
        return pending.bond().equals(item.getString("bond", ""))
                && pending.creature().toString().equals(item.getString("mob", ""))
                && pending.charge() == ReunionData.fuel(item);
    }
    private static void tick(MinecraftServer server) {
        // Leave the reservation installed through spawn callbacks to reject reentrant attempts.
        for (var pending : java.util.List.copyOf(ACTIVE.values())) {
            var player = server.getPlayerManager().getPlayer(pending.player());
            if (!valid(pending, player)) {
                ACTIVE.remove(pending.player());
                ReunionEffects.finish(pending.world(), pending.player(), pending.bond(), false);
                if (player != null) com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable("message.noncreatureaffinity.reunion_cancelled"), true);
                continue;
            }
            if (server.getTicks() - pending.started() < ReunionAnimation.TURN_END) continue;
            boolean success = ReunionService.complete(player, player.getStackInHand(pending.hand()), pending.position());
            // An external spawn callback may throw after insertion. Do not tell the client a committed revival failed.
            if (!success) {
                var record = ReunionData.record(server, pending.creature().toString());
                success = pending.world().getEntity(pending.creature()) instanceof MobEntity mob && mob.isAlive()
                        && ReunionData.spent(record) == pending.charge() && !record.getBoolean("dead", true);
            }
            ACTIVE.remove(pending.player());
            ReunionEffects.finish(pending.world(), pending.player(), pending.bond(), success);
        }
    }
    private ReunionPending() { }
}
