package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.data.*;
import com.nonid.affinity.net.*;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.CreakingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/** Evidence is collected on the server from already loaded actors. Query methods only peek. */
public final class CompanionArchive {
    public static final AttachmentType<CompanionArchiveData> DATA = AttachmentRegistry.create(
            Identifier.of("noncreatureaffinity", "companion_archive"), b -> b.initializer(CompanionArchiveData::new)
                    .persistent(NbtCompoundCodec.of(CompanionArchiveData::toNbt, CompanionArchiveData::fromNbt)));
    private record Birth(List<FamilyGraph.Node> parents, Set<UUID> viewers, FamilyGraph.Evidence source) {}
    private static final Map<LivingEntity, Birth> BIRTHS = new WeakHashMap<>();
    private static final UUID ZERO = new UUID(0, 0);
    private CompanionArchive() {}
    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(CompanionArchive::death);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BIRTHS.clear());
    }
    public static CompanionArchiveData peek(MinecraftServer server) { return server.getOverworld().getAttached(DATA); }
    private static void save(MinecraftServer server, CompanionArchiveData data) { server.getOverworld().setAttached(DATA, data); }
    public static void stageBirth(LivingEntity child, ServerPlayerEntity parent) {
        if (child == null || parent == null || ReplayIsolation.protects(child.getUuid(), parent.getUuid())) return;
        BIRTHS.put(child, new Birth(List.of(node(parent)), Set.of(parent.getUuid()), FamilyGraph.Evidence.NON_BIRTH));
    }
    public static void stageBirth(LivingEntity child, LivingEntity first, LivingEntity second) {
        if (child == null || first == null || second == null) return;
        var viewers = new HashSet<UUID>(); collectViewers(first, viewers); collectViewers(second, viewers);
        viewers.removeIf(viewer -> ReplayIsolation.protects(first.getUuid(), viewer) || ReplayIsolation.protects(second.getUuid(), viewer));
        if (!viewers.isEmpty()) BIRTHS.put(child, new Birth(List.of(node(first), node(second)), Set.copyOf(viewers), FamilyGraph.Evidence.VANILLA_BIRTH));
    }
    private static void collectViewers(LivingEntity entity, Set<UUID> viewers) {
        var attached = entity instanceof AffinityHolder holder ? holder.peekAffinityData() : null;
        if (attached != null) for (String key : attached.entries().keySet()) try { viewers.add(UUID.fromString(key)); }
        catch (IllegalArgumentException invalid) { NonCreatureAffinity.LOGGER.warn("Invalid relationship viewer UUID {}", key); }
        if (entity.getEntityWorld() instanceof ServerWorld world) {
            var archive = peek(world.getServer()); var id = CompanionDirectory.identityOf(entity);
            if (archive != null && id != null) { viewers.addAll(archive.viewers(id)); viewers.addAll(archive.kinViewers(id)); }
        }
    }
    public static FamilyGraph.Node identityNode(Entity entity) { return node(entity); }
    private static FamilyGraph.Node node(Entity entity) {
        String id = entity instanceof PlayerEntity ? "player:" + entity.getUuid() : entity instanceof LivingEntity living ? CompanionDirectory.identityOf(living) : null;
        if (id == null) id = "entity:" + entity.getUuid();
        return new FamilyGraph.Node(id, bounded(entity.getName().getString(), 256), Registries.ENTITY_TYPE.getId(entity.getType()).toString(), FamilyService.gender(entity),
                entity instanceof PlayerEntity ? FamilyGraph.NameKind.PLAYER : entity.hasCustomName() ? FamilyGraph.NameKind.CUSTOM : FamilyGraph.NameKind.DEFAULT_TYPE);
    }
    private static String bounded(String value, int length) { return value.length() <= length ? value : value.substring(0, length); }
    /** Called by the directory's bounded background queue, never by a client information read. */
    public static void observe(LivingEntity entity) {
        if (!(entity.getEntityWorld() instanceof ServerWorld world) || entity instanceof PlayerEntity
                || !entity.isAlive() || entity.isRemoved() || world.getEntity(entity.getUuid()) != entity) return;
        var attached = entity instanceof AffinityHolder holder ? holder.peekAffinityData() : null;
        var data = peek(world.getServer()); String id = CompanionDirectory.identityOf(entity); if (id == null) return;
        var birth = BIRTHS.remove(entity);
        var exact = BirthIdentity.fromNbt(entity.getAttached(AffinityAttachments.BIRTH_IDENTITY));
        if (exact != null && !exact.isolated()) birth = new Birth(
                exact.father() == null ? List.of(exact.mother()) : List.of(exact.mother(), exact.father()),
                Set.of(exact.viewer()), FamilyGraph.Evidence.NON_BIRTH);
        if ((attached == null || attached.isEmpty()) && birth == null && (data == null || data.viewers(id).isEmpty())) return;
        if (data == null) data = new CompanionArchiveData(); boolean changed = false;
        if (attached != null) for (var item : attached.entries().entrySet()) {
            UUID viewer; try { viewer = UUID.fromString(item.getKey()); } catch (IllegalArgumentException invalid) { continue; }
            if (ReplayIsolation.protects(entity.getUuid(), viewer)) continue;
            var relation = item.getValue();
            if (relation.getAffinity() >= NonCreatureAffinity.getConfig().familiarThreshold || relation.isBefriended() || data.get(viewer, id) != null)
                changed |= remember(data, entity, viewer, relation.isBefriended() || RelationshipStages.friend(relation.getAffinity(), NonCreatureAffinity.getConfig()));
        }
        for (UUID viewer : data.viewers(id)) if (!ReplayIsolation.protects(entity.getUuid(), viewer))
            changed |= data.revived(viewer, id, System.currentTimeMillis(), dimension(world));
        if (birth != null) for (UUID viewer : birth.viewers()) {
            if (ReplayIsolation.protects(entity.getUuid(), viewer)) continue;
            var graph = data.recordFamily(viewer); changed |= graph.put(node(entity));
            for (var parent : birth.parents()) {
                // Persisted snapshots must not replace newer names or downgrade an offline parent.
                if (exact == null || graph.get(parent.id()) == null) changed |= graph.put(parent);
                changed |= graph.link(parent.id(), id, birth.source());
            }
        }
        // Old exact metadata supplies an edge, not an invented dated birth event or a second parent.
        if (attached != null && attached.getBirthParent() != null) {
            var viewers = new HashSet<>(data.viewers(id)); viewers.add(attached.getBirthParent());
            var parent = world.getServer().getPlayerManager().getPlayer(attached.getBirthParent());
            var parentNode = parent == null ? new FamilyGraph.Node("player:" + attached.getBirthParent(), "", "minecraft:player", 0) : node(parent);
            for (UUID viewer : viewers) if (!ReplayIsolation.protects(entity.getUuid(), viewer)) {
                var graph = data.recordFamily(viewer); changed |= graph.put(node(entity));
                // Do not erase an already recorded player name when they disconnect.
                if (parent != null || graph.get(parentNode.id()) == null) changed |= graph.put(parentNode);
                changed |= graph.link(parentNode.id(), id, FamilyGraph.Evidence.SAVED_BIRTH_PARENT);
            }
        }
        if (changed) save(world.getServer(), data);
    }
    private static boolean remember(CompanionArchiveData data, LivingEntity entity, UUID viewer, boolean friend) {
        String id = CompanionDirectory.identityOf(entity); if (id == null) return false;
        var world = (ServerWorld)entity.getEntityWorld();
        var query = new CompanionInfoQuery(ZERO, 0, entity.getUuid(), dimension(world));
        boolean changed = data.remember(viewer, new CompanionRecord(id, System.currentTimeMillis(), "historical", CompanionInfoService.snapshot(entity, viewer, query)), friend);
        return data.recordFamily(viewer).put(node(entity)) || changed;
    }
    private static String dimension(ServerWorld world) { return world.getRegistryKey().getValue().toString(); }
    private static void death(LivingEntity entity, DamageSource source) {
        if (!(entity.getEntityWorld() instanceof ServerWorld world) || entity instanceof PlayerEntity
                || (entity instanceof CreakingEntity c && c.getHomePos() != null)) return;
        String id = CompanionDirectory.identityOf(entity); if (id == null) return;
        var data = peek(world.getServer()); var viewers = new HashSet<UUID>(); collectViewers(entity, viewers);
        if (viewers.isEmpty()) return; if (data == null) data = new CompanionArchiveData(); boolean changed = false;
        var attached = entity instanceof AffinityHolder holder ? holder.peekAffinityData() : null;
        String cause = source.getTypeRegistryEntry().getKey().map(key -> key.getValue().toString()).orElse("");
        var death = new CompanionArchiveData.Death(System.currentTimeMillis(), dimension(world), entity.getX(), entity.getY(), entity.getZ(), cause);
        for (UUID viewer : viewers) {
            if (ReplayIsolation.protects(entity.getUuid(), viewer)) continue;
            var relation = attached == null ? null : attached.entries().get(viewer.toString());
            if (relation != null) changed |= remember(data, entity, viewer, relation.isBefriended() || RelationshipStages.friend(relation.getAffinity(), NonCreatureAffinity.getConfig()));
            changed |= data.died(viewer, id, death);
        }
        if (changed) save(world.getServer(), data);
    }
    public static void event(Entity entity, ServerPlayerEntity player, CompanionArchiveData.Kind kind, String source, String detail, int before, int after) {
        recordEvent(entity, player.getUuid(), kind, source, detail, before, after);
    }
    private static void recordEvent(Entity entity, UUID viewer, CompanionArchiveData.Kind kind, String source, String detail, int before, int after) {
        if (!(entity instanceof LivingEntity living) || !(entity.getEntityWorld() instanceof ServerWorld world)
                || ReplayIsolation.protects(entity.getUuid(), viewer)) return;
        var attached = entity instanceof AffinityHolder holder ? holder.peekAffinityData() : null;
        var relation = attached == null ? null : attached.entries().get(viewer.toString()); if (relation == null) return;
        String id = CompanionDirectory.identityOf(living); if (id == null) return;
        var data = peek(world.getServer()); if (data == null) data = new CompanionArchiveData();
        boolean changed = remember(data, living, viewer, relation.isBefriended() || RelationshipStages.friend(relation.getAffinity(), NonCreatureAffinity.getConfig()));
        changed |= data.event(viewer, id, source, kind, System.currentTimeMillis(), dimension(world), bounded(detail, 512), before, after,
                kind == CompanionArchiveData.Kind.STAGE_CHANGED ? after
                : kind == CompanionArchiveData.Kind.ACCEPTED || kind == CompanionArchiveData.Kind.COMPLETED
                ? RelationshipStages.of(relation.getAffinity(), NonCreatureAffinity.getConfig()).ordinal() : -1);
        if (changed) save(world.getServer(), data);
    }
    public static void stageChanged(Entity entity, PlayerEntity player, int before, int after, com.nonid.affinity.config.AffinityConfig config, String cause) {
        if (player != null) stageChanged(entity, player.getUuid(), before, after, config, cause);
    }
    public static void stageChanged(Entity entity, UUID viewer, int before, int after, com.nonid.affinity.config.AffinityConfig config, String cause) {
        var from = RelationshipStages.of(before, config); var to = RelationshipStages.of(after, config);
        if (from != to) recordEvent(entity, viewer, CompanionArchiveData.Kind.STAGE_CHANGED, "stage:" + UUID.randomUUID(), cause, from.ordinal(), to.ordinal());
    }
}
