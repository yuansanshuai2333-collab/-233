package com.nonid.affinity.service;

import com.nonid.affinity.data.CompanionArchiveData;
import com.nonid.affinity.item.AffinityDetectorItem;
import com.nonid.affinity.net.MemorialNotice;
import com.nonid.affinity.reunion.ReunionData;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;

public final class CompanionMemorial {
    private record Active(UUID token, Set<String> ids, long until) {}
    private static final Map<UUID, MemorialVisits> VISITS = new HashMap<>();
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private CompanionMemorial() {}
    public static void register() {
        PayloadTypeRegistry.playS2C().register(MemorialNotice.ID, MemorialNotice.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            for (var player : server.getPlayerManager().getPlayerList()) tick(player, System.currentTimeMillis());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { VISITS.remove(handler.player.getUuid()); ACTIVE.remove(handler.player.getUuid()); });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { VISITS.clear(); ACTIVE.clear(); });
    }
    /** Computes proximity from saved coordinates only, never an entity/chunk lookup. */
    public static List<String> nearby(ServerPlayerEntity player, CompanionArchiveData data) {
        if (data == null) return List.of(); var result = new ArrayList<String>();
        String dimension = player.getEntityWorld().getRegistryKey().getValue().toString();
        for (String id : data.ids(player.getUuid())) {
            var record = data.get(player.getUuid(), id); var death = record.death();
            if (ReplayIsolation.protects(record.snapshot().info().query().target(), player.getUuid())) continue;
            if (!record.dead() || !record.wasFriend() || death == null || !dimension.equals(death.dimension())) continue;
            double x = player.getX() - death.x(), y = player.getY() - death.y(), z = player.getZ() - death.z();
            if (x*x + y*y + z*z > 256) continue;
            var binding = ReunionData.record(player.getEntityWorld().getServer(), record.snapshot().info().query().target().toString());
            if (!binding.getString("bond", "").isEmpty()) continue;
            result.add(id);
        }
        return List.copyOf(result);
    }
    public static void tick(ServerPlayerEntity player, long now) {
        var data = CompanionArchive.peek(player.getEntityWorld().getServer()); var nearby = nearby(player, data);
        boolean held = player.isAlive() && !player.isSpectator() && AffinityDetectorItem.isHeldBy(player);
        var active = ACTIVE.get(player.getUuid()); long ticks = player.getEntityWorld().getServer().getTicks();
        if (active != null && (!held || ticks >= active.until() || !new HashSet<>(nearby).containsAll(active.ids()))) {
            if (ServerPlayNetworking.canSend(player, MemorialNotice.ID)) ServerPlayNetworking.send(player, new MemorialNotice(active.token(), player.getEntityWorld().getRegistryKey().getValue().toString(), 0, List.of(), 0));
            ACTIVE.remove(player.getUuid());
        }
        var previous = new HashMap<String, Long>(); for (String id : nearby) previous.put(id, data.get(player.getUuid(), id).lastMemorialTime());
        var entered = VISITS.computeIfAbsent(player.getUuid(), key -> new MemorialVisits()).update(previous, held && ServerPlayNetworking.canSend(player, MemorialNotice.ID), now);
        if (entered.isEmpty()) return;
        var names = entered.stream().limit(2).map(id -> data.get(player.getUuid(), id).snapshot().info().name()).toList();
        var types = entered.stream().limit(2).map(id -> data.get(player.getUuid(), id).snapshot().info().type()).toList();
        UUID token = UUID.randomUUID(); ACTIVE.put(player.getUuid(), new Active(token, Set.copyOf(entered), ticks + 160));
        var anchor = data.get(player.getUuid(), entered.getFirst()).death();
        ServerPlayNetworking.send(player, new MemorialNotice(token, player.getEntityWorld().getRegistryKey().getValue().toString(), 160, names, types, entered.size(), true, anchor.x(), anchor.y(), anchor.z()));
        boolean changed = false; for (String id : entered) changed |= data.reminded(player.getUuid(), id, now);
        if (changed) player.getEntityWorld().getServer().getOverworld().setAttached(CompanionArchive.DATA, data);
    }
}
