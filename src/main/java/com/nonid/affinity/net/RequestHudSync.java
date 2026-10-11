package com.nonid.affinity.net;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.service.RequestSystem;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Display-only snapshots. Validation never starts, answers or charges for a request. */
public final class RequestHudSync {
    private record Snapshot(RequestStatePayload payload, ServerWorld world) {}
    private static final Map<UUID, Snapshot> ACTIVE = new HashMap<>();
    private static long sequence;
    private RequestHudSync() {}
    public static void show(ServerPlayerEntity player, Entity mob, long expiry, int affinity, int threshold) {
        var energy = energy(mob);
        var payload = new RequestStatePayload(mob.getUuid(), mob.getName().getString(), expiry,
                affinity, threshold, ++sequence, RequestSystem.requestToken(mob.getUuid()),
                player.getEntityWorld().getRegistryKey().getValue().toString(), energy.stage(), energy);
        ACTIVE.put(player.getUuid(), new Snapshot(payload, player.getEntityWorld()));
        send(player, payload);
    }
    public static void close(ServerPlayerEntity player, UUID mob) {
        var snapshot = ACTIVE.get(player.getUuid());
        if (snapshot == null || !snapshot.payload().mobUuid().equals(mob)) return;
        ACTIVE.remove(player.getUuid());
        send(player, new RequestStatePayload(mob, "", 0, 0, 0, snapshot.payload().requestId()));
    }
    public static void tick(MinecraftServer server) {
        for (var id : java.util.List.copyOf(ACTIVE.keySet())) {
            var snapshot = ACTIVE.get(id);
            var player = server.getPlayerManager().getPlayer(id);
            if (player == null) { ACTIVE.remove(id); continue; }
            var payload = snapshot.payload();
            var mob = snapshot.world().getEntity(payload.mobUuid());
            double range = com.nonid.affinity.service.RequestPresentation.ANSWER_RANGE;
            if (player.getEntityWorld() != snapshot.world() || !player.isAlive() || player.isSpectator()
                    || mob == null || !mob.isAlive() || mob.isRemoved()
                    || mob.squaredDistanceTo(player) > range * range
                    || snapshot.world().getTime() >= payload.expireTick()
                    || !RequestSystem.isWaitingFor(payload.mobUuid(), player.getUuid())) {
                close(player, payload.mobUuid());
            } else if (server.getTicks() % 20 == 0) {
                var relation=com.nonid.affinity.service.AffinityService.getRelation(mob,player);
                var energy = energy(mob);
                var current=new RequestStatePayload(payload.mobUuid(),payload.mobName(),payload.expireTick(),
                        relation==null?payload.affinity():relation.getAffinity(),NonCreatureAffinity.getConfig().familiarThreshold,payload.requestId(), payload.token(), payload.dimension(), energy.stage(), energy);
                ACTIVE.put(id,new Snapshot(current,snapshot.world()));send(player,current);
            }
        }
    }
    private static com.nonid.affinity.service.RequestEnergy energy(Entity mob) {
        return mob instanceof net.minecraft.entity.LivingEntity living
                ? com.nonid.affinity.non.NonBridge.requestEnergy(living) : com.nonid.affinity.service.RequestEnergy.UNKNOWN;
    }
    private static void send(ServerPlayerEntity player, RequestStatePayload payload) {
        if (ServerPlayNetworking.canSend(player, RequestStatePayload.ID)) ServerPlayNetworking.send(player, payload);
    }
    public static void forget(UUID player) { ACTIVE.remove(player); }
    public static void clear() { ACTIVE.clear(); }
}
