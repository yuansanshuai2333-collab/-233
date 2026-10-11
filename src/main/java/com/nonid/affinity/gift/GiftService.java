package com.nonid.affinity.gift;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.data.*;
import com.nonid.affinity.service.*;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.CreakingEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.server.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Authoritative gift backend; all content is frozen by the server production provider. */
public final class GiftService {
    public interface Generator { GiftContent prepare(LivingEntity mob, UUID viewer, int tier, long clock); }
    public record Marker(UUID gift, String name, GiftEnvelope.Location location) {}
    private record Presence(UUID entity, String dimension) {}
    private record Pair(UUID viewer, String creature) {}
    public static final AttachmentType<GiftLedger> LEDGER = AttachmentRegistry.create(Identifier.of(NonCreatureAffinity.MOD_ID, "gift_ledger"), b -> b.initializer(GiftLedger::new).persistent(NbtCompoundCodec.of(GiftLedger::toNbt, GiftLedger::fromNbt)));
    public static final AttachmentType<NbtCompound> REWARD_DROP = AttachmentRegistry.create(Identifier.of(NonCreatureAffinity.MOD_ID, "gift_reward_receipt"), b -> b.persistent(NbtCompoundCodec.of(NbtCompound::copy, NbtCompound::copy)));
    private static final Map<String, Presence> LOADED = new HashMap<>();
    private static final GiftDayClock DAY_CLOCK = new GiftDayClock();
    private static final Set<UUID> OPENING = new HashSet<>();
    private static final Set<Pair> ELIGIBLE_AT_START = new HashSet<>();
    private static Generator generator;
    private static int placementBudget;
    private static java.math.BigInteger daylight = java.math.BigInteger.ZERO;
    private GiftService() {}
    /** Explicit adapter hook for isolated QA; production is registered anew for every server runtime. */
    public static void generator(Generator confirmedTables) { generator = confirmedTables; }
    public static GiftLedger peek(MinecraftServer server) { return server.getOverworld().getAttached(LEDGER); }
    public static void save(MinecraftServer server, GiftLedger ledger) { server.getOverworld().setAttached(LEDGER, ledger); }
    public static void register() {
        GiftBoxItem.register();
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            placementBudget=4096; ELIGIBLE_AT_START.clear(); var data = peek(server); if (data == null) return;
            for (var cycle : data.cycles()) if (eligible(server, cycle)) ELIGIBLE_AT_START.add(new Pair(cycle.viewer(), cycle.creature()));
        });
        ServerTickEvents.START_WORLD_TICK.register(world -> { if (world == world.getServer().getOverworld()) DAY_CLOCK.begin(world.getTimeOfDay()); });
        ServerTickEvents.END_WORLD_TICK.register(world -> { if (world == world.getServer().getOverworld()) daylight = DAY_CLOCK.endUnobserved(world.getTimeOfDay()); });
        ServerTickEvents.END_SERVER_TICK.register(GiftService::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register(GiftService::death);
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> { if (entity instanceof ItemEntity item) recoveredDrop(item); else if (entity instanceof LivingEntity living) observe(living); });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> { if (entity instanceof LivingEntity living) { GiftProductionGenerator.forget(entity.getUuid()); String id = CompanionDirectory.identityOf(living); var found = LOADED.get(id); if (found != null && found.entity().equals(entity.getUuid())) LOADED.remove(id); } });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> { GiftProductionGenerator.reset(); generator = GiftProductionGenerator.INSTANCE; var data=peek(server); if(data!=null) { data.expiryDays(GiftSettings.expiryDays()); save(server,data); } });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { LOADED.clear(); OPENING.clear(); ELIGIBLE_AT_START.clear(); GiftProductionGenerator.reset(); generator = null; daylight = java.math.BigInteger.ZERO; DAY_CLOCK.reset(); });
    }
    public static void timeChanged(ServerWorld world, long before, long after) {
        if (world != world.getServer().getOverworld()) return;
        var elapsed=DAY_CLOCK.changed(before, after); if(elapsed.signum()==0) return;
        var data=peek(world.getServer()); if(data==null) return;
        advanceHabitats(world.getServer(),data,elapsed);
        for(var cycle:data.cycles()) cycle.creditExact(elapsed,eligible(world.getServer(),cycle),generator!=null);
        save(world.getServer(),data);
    }
    private static boolean vanilla(LivingEntity entity) {
        // Canonical vanilla constants, rather than merely trusting a third-party "minecraft" namespace.
        if (!Registries.ENTITY_TYPE.getId(entity.getType()).getNamespace().equals("minecraft")) return false;
        for (var field : EntityType.class.getFields()) if (field.getType() == EntityType.class && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            try { if (field.get(null) == entity.getType()) return true; }
            catch (IllegalAccessException invalid) { throw new IllegalStateException("Cannot inspect vanilla entity types", invalid); }
        }
        return false;
    }
    public static void observe(LivingEntity mob) {
        if (!(mob.getEntityWorld() instanceof ServerWorld world) || mob instanceof net.minecraft.entity.player.PlayerEntity || !mob.isAlive() || mob.isRemoved() || world.getEntity(mob.getUuid()) != mob) return;
        var data = peek(world.getServer()); if (data == null && generator == null) return;
        String id = CompanionDirectory.identityOf(mob); if (id == null) return;
        if (data == null) { data = new GiftLedger(); data.expiryDays(GiftSettings.expiryDays()); }
        boolean known = data.cycles().stream().anyMatch(cycle -> cycle.creature().equals(id));
        var affinity = mob instanceof AffinityHolder holder ? holder.peekAffinityData() : null;
        if (generator != null && vanilla(mob) && (generator != GiftProductionGenerator.INSTANCE || GiftProductionGenerator.supports(mob)) && affinity != null) for (String key : affinity.entries().keySet()) {
            UUID viewer; try { viewer = UUID.fromString(key); } catch (IllegalArgumentException invalid) { NonCreatureAffinity.LOGGER.warn("Invalid gift relationship viewer {}", key); continue; }
            var relation = affinity.get(viewer);
            if (!ReplayIsolation.protects(mob.getUuid(), viewer) && RelationshipStages.defends(relation.getAffinity(), NonCreatureAffinity.getConfig())) { data.track(viewer, id); known = true; }
        }
        if (!known) return;
        LOADED.put(id, new Presence(mob.getUuid(), world.getRegistryKey().getValue().toString()));
        for (var cycle : data.cycles()) if (cycle.creature().equals(id) && !ReplayIsolation.protects(mob.getUuid(), cycle.viewer()) && cycle.dead()) {
            var gift = cycle.gift() == null ? null : data.gift(cycle.gift()); if (gift != null) gift.revive(); cycle.revive();
        }
        save(world.getServer(), data);
    }
    private static void advanceHabitats(MinecraftServer server, GiftLedger data, java.math.BigInteger elapsed) {
        if (generator != GiftProductionGenerator.INSTANCE || elapsed.signum() == 0) return;
        var known = new HashSet<String>();
        for (var cycle : data.cycles()) {
            var mob = loaded(server,cycle.creature());
            if (mob != null && !ReplayIsolation.protects(mob.getUuid(),cycle.viewer())) known.add(cycle.creature());
        }
        for (String id : known) GiftProductionGenerator.advance(data,loaded(server,id),elapsed);
    }

    private static ServerWorld world(MinecraftServer server, String dimension) {
        var id = Identifier.tryParse(dimension); return id == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
    }
    private static LivingEntity loaded(MinecraftServer server, String id) {
        var presence = LOADED.get(id); if (presence == null) return null; var world = world(server, presence.dimension());
        var entity = world == null ? null : world.getEntity(presence.entity()); return entity instanceof LivingEntity living && living.isAlive() && !living.isRemoved() ? living : null;
    }
    private static boolean eligible(MinecraftServer server, GiftCycle cycle) {
        var mob = loaded(server, cycle.creature()); var player = server.getPlayerManager().getPlayer(cycle.viewer());
        var affinity = mob instanceof AffinityHolder holder ? holder.peekAffinityData() : null; var relation = affinity == null ? null : affinity.get(cycle.viewer());
        return mob != null && player != null && relation != null && RelationshipStages.defends(relation.getAffinity(), NonCreatureAffinity.getConfig()) && !ReplayIsolation.protects(mob.getUuid(), cycle.viewer());
    }
    private static void tick(MinecraftServer server) {
        var data = peek(server); if (data == null) { daylight = java.math.BigInteger.ZERO; return; } data.tick();
        var advance = daylight; daylight = java.math.BigInteger.ZERO;
        advanceHabitats(server,data,advance);
        java.util.random.RandomGenerator chance = () -> server.getOverworld().random.nextLong();
        for (var cycle : data.cycles()) {
            var mob = loaded(server, cycle.creature()); var player = server.getPlayerManager().getPlayer(cycle.viewer());
            var affinity = mob instanceof AffinityHolder holder ? holder.peekAffinityData() : null; var relation = affinity == null ? null : affinity.get(cycle.viewer());
            boolean eligible = ELIGIBLE_AT_START.contains(new Pair(cycle.viewer(), cycle.creature())) && eligible(server, cycle);
            int tier = relation == null ? -1 : RelationshipBond.tier(relation.getBond(), relation.getAffinity());
            final GiftLedger ledger = data; final GiftContent[] prepared = {null};
            var factory = generator == null || tier < 0 ? null : (java.util.function.Supplier<GiftContent>)() -> {
                var content = Objects.requireNonNull(generator.prepare(mob, cycle.viewer(), tier, ledger.clock()));
                if (content.tier() != tier || ledger.gift(content.id()) != null) throw new IllegalArgumentException("Invalid frozen gift tier or duplicate identity");
                return prepared[0] = content;
            };
            try { if (cycle.advanceExact(advance, eligible, ledger.clock(), chance, factory) == GiftCycle.Step.PREPARED) data.prepared(prepared[0]).actor(mob.getUuid()); }
            catch (RuntimeException failed) { NonCreatureAffinity.LOGGER.error("Gift preparation adapter failed for {} / {}; no item issued", cycle.viewer(), cycle.creature(), failed); }
        }
        for (var gift : data.all()) {
            if (gift.state() == GiftEnvelope.State.DELIVERING) recoverKnown(server, gift.deliveryLocation(), gift.drop());
            if (gift.rewardDrop() != null) recoverKnown(server, gift.rewardLocation(), gift.rewardDrop());
            if (gift.expire(data.clock())) { var cycle = data.get(gift.content().viewer(), gift.content().creature()); if (cycle != null) cycle.claimed(gift.content().id()); }
            if ((server.getTicks() % 20 == 0 || gift.searchCursor() > 0) && gift.state() == GiftEnvelope.State.READY && gift.death() != null && gift.placed() == null) place(server, data, gift);
        }
        save(server, data);
    }
    private static void death(LivingEntity mob, DamageSource source) {
        if (!(mob.getEntityWorld() instanceof ServerWorld world) || mob instanceof CreakingEntity creaking && creaking.getHomePos() != null) return;
        var data = peek(world.getServer()); if (data == null) return; String id = CompanionDirectory.identityOf(mob); if (id == null) return;
        for (var cycle : data.cycles()) if (cycle.creature().equals(id) && !ReplayIsolation.protects(mob.getUuid(), cycle.viewer())) {
            if (!cycle.death()) continue; var gift = cycle.gift() == null ? null : data.gift(cycle.gift());
            if (gift != null) { var attacker = source.getAttacker(); String cause = source.getTypeRegistryEntry().getKey().map(key -> key.getValue().toString()).orElse("unknown");
                gift.died(new GiftEnvelope.Location(world.getRegistryKey().getValue().toString(), mob.getX(), mob.getY(), mob.getZ()), cause, attacker instanceof ServerPlayerEntity player ? player.getUuid() : null); place(world.getServer(), data, gift); }
        }
        save(world.getServer(), data);
    }
    private static void place(MinecraftServer server, GiftLedger data, GiftEnvelope gift) {
        var anchor = gift.death(); var world = world(server, anchor.dimension()); if (world == null || placementBudget<=0) return;
        var result = GiftPlacement.search(anchor, pos -> {
            if (!world.isChunkLoaded(pos) || !world.isChunkLoaded(pos.down())) return GiftPlacement.Probe.UNLOADED;
            var feet = world.getBlockState(pos); var support = world.getBlockState(pos.down());
            if (!feet.getFluidState().isEmpty() || !support.getFluidState().isEmpty() || !support.isSideSolidFullSquare(world, pos.down(), Direction.UP)
                    || feet.isIn(net.minecraft.registry.tag.BlockTags.FIRE) || support.isIn(net.minecraft.registry.tag.BlockTags.FIRE)
                    || support.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK) || support.isOf(net.minecraft.block.Blocks.CACTUS)
                    || support.isOf(net.minecraft.block.Blocks.CAMPFIRE) || support.isOf(net.minecraft.block.Blocks.SOUL_CAMPFIRE)) return GiftPlacement.Probe.UNSAFE;
            var box = new Box(pos.getX() + .375, pos.getY(), pos.getZ() + .375, pos.getX() + .625, pos.getY() + .25, pos.getZ() + .625);
            return world.isSpaceEmpty(null, box) ? GiftPlacement.Probe.SAFE : GiftPlacement.Probe.UNSAFE;
        }, gift.searchCursor(), gift.searchUnloaded(), placementBudget);
        placementBudget-=result.checked(); gift.searchProgress(result);
        if (result.status() == GiftPlacement.Status.PLACED) gift.place(result.location(), data.clock(), data.expiryDays() * 24000L);
        else if (result.status() == GiftPlacement.Status.LOST) { gift.lost(); var cycle = data.get(gift.content().viewer(), gift.content().creature()); if (cycle != null) cycle.claimed(gift.content().id()); }
    }
    public static List<Marker> markers(ServerPlayerEntity player) {
        if (!player.isAlive() || player.isSpectator() || !com.nonid.affinity.item.AffinityDetectorItem.isHeldBy(player)) return List.of();
        var data = peek(player.getEntityWorld().getServer()); if (data == null) return List.of();
        return data.owned(player.getUuid()).stream().filter(gift -> !isolated(gift, player) && gift.state() == GiftEnvelope.State.READY && gift.placed() != null && gift.placed().dimension().equals(player.getEntityWorld().getRegistryKey().getValue().toString()))
                .map(gift -> new Marker(gift.content().id(), gift.content().giverName(), gift.placed())).toList();
    }
    public static List<GiftMarkers.Group> markerGroups(ServerPlayerEntity player) { return GiftMarkers.aggregate(markers(player)); }

    /** Reads existing owned records without creating relationships or loading chunks. */
    public static List<com.nonid.affinity.net.GiftViewPayload.Row> views(ServerPlayerEntity player) {
        if(!player.isAlive()||player.isSpectator())return List.of();
        var data=peek(player.getEntityWorld().getServer());if(data==null)return List.of();
        var rows=new ArrayList<com.nonid.affinity.net.GiftViewPayload.Row>();
        for(var gift:data.owned(player.getUuid())) {
            if(gift.state()!=GiftEnvelope.State.READY||gift.death()!=null||isolated(gift,player))continue;
            var target=loaded(player.getEntityWorld().getServer(),gift.content().creature());
            if(target==null||!target.isAlive()||target.getEntityWorld()!=player.getEntityWorld()||target.squaredDistanceTo(player)>64
                    ||ReplayIsolation.protects(target.getUuid(),player.getUuid()))continue;
            var content=gift.content();var at=target.getEntityPos();
            rows.add(new com.nonid.affinity.net.GiftViewPayload.Row(content.id(),target.getUuid(),false,at.x,at.y,at.z,content.giverName(),content.giverType(),content.wrapping(),1));
        }
        for(var group:markerGroups(player)) {
            var at=group.location();if(!player.getEntityWorld().isChunkLoaded(BlockPos.ofFloored(at.x(),at.y(),at.z())))continue;
            var gift=data.gift(group.nextGift());if(gift==null||gift.expired(data.clock()))continue;
            rows.add(new com.nonid.affinity.net.GiftViewPayload.Row(group.nextGift(),new UUID(0,0),true,at.x(),at.y(),at.z(),group.nextName(),gift.content().giverType(),gift.content().wrapping(),group.count()));
        }
        rows.sort(Comparator.comparingDouble(r->player.squaredDistanceTo(r.x(),r.y(),r.z())));
        return List.copyOf(rows.subList(0,Math.min(64,rows.size())));
    }
    public static String claim(ServerPlayerEntity player, UUID giftId, LivingEntity target) {
        if (!player.isAlive() || player.isSpectator() || !player.getMainHandStack().isEmpty() || target == null || player.getEntityWorld() != target.getEntityWorld()
                || target.isRemoved() || !target.isAlive() || !player.canInteractWithEntity(target, 0)) return "unavailable";
        var world = player.getEntityWorld(); if (world.getEntity(target.getUuid()) != target) return "unavailable";
        if (!loadedLine(world, player.getEyePos(), target.getEyePos()) || !player.canSee(target)) return "obstructed";
        var data = peek(world.getServer()); var gift = data == null ? null : data.gift(giftId);
        if (gift == null || !gift.content().creature().equals(CompanionDirectory.identityOf(target)) || ReplayIsolation.protects(target.getUuid(), player.getUuid())) return "unavailable";
        return emit(player, data, gift, false, new GiftEnvelope.Location(world.getRegistryKey().getValue().toString(), target.getX(), target.getY() + .5, target.getZ()));
    }
    public static String claimBequest(ServerPlayerEntity player, UUID giftId) {
        if (!player.isAlive() || player.isSpectator() || !com.nonid.affinity.item.AffinityDetectorItem.isHeldBy(player)) return "detector_required";
        var world = player.getEntityWorld(); var data = peek(world.getServer()); var gift = data == null ? null : data.gift(giftId); var place = gift == null ? null : gift.placed();
        if (place == null || isolated(gift, player) || !place.dimension().equals(world.getRegistryKey().getValue().toString()) || !world.isChunkLoaded(BlockPos.ofFloored(place.x(), place.y(), place.z()))) return "unavailable";
        var box = new Box(place.x() - .125, place.y(), place.z() - .125, place.x() + .125, place.y() + .25, place.z() + .125);
        if (!player.canInteractWithEntityIn(box, 0)) return "out_of_range";
        var point = new Vec3d(place.x(), place.y() + .125, place.z()); if (!loadedLine(world, player.getEyePos(), point)) return "unavailable";
        var hit = world.raycast(new net.minecraft.world.RaycastContext(player.getEyePos(), point, net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) return "obstructed";
        if (gift.expire(data.clock())) { var cycle = data.get(gift.content().viewer(), gift.content().creature()); if (cycle != null) cycle.claimed(gift.content().id()); save(world.getServer(), data); return "expired"; }
        return emit(player, data, gift, true, place);
    }
    private static boolean isolated(GiftEnvelope gift, ServerPlayerEntity player) { return gift.actor() != null && ReplayIsolation.protects(gift.actor(), player.getUuid()); }
    private static boolean loadedLine(ServerWorld world, Vec3d from, Vec3d to) {
        int minX = Math.floorDiv((int)Math.floor(Math.min(from.x, to.x)), 16), maxX = Math.floorDiv((int)Math.floor(Math.max(from.x, to.x)), 16);
        int minZ = Math.floorDiv((int)Math.floor(Math.min(from.z, to.z)), 16), maxZ = Math.floorDiv((int)Math.floor(Math.max(from.z, to.z)), 16);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) if (!world.isChunkLoaded(new BlockPos(x * 16, 0, z * 16))) return false;
        return true;
    }
    private static String emit(ServerPlayerEntity player, GiftLedger data, GiftEnvelope gift, boolean bequest, GiftEnvelope.Location location) {
        UUID dropId = UUID.randomUUID(); if (!gift.reserve(player.getUuid(), dropId, bequest)) return "unavailable";
        var world = player.getEntityWorld(); gift.emission(location, data.clock()); save(world.getServer(), data);
        var drop = new ItemEntity(world, location.x(), location.y(), location.z(), GiftBoxItem.box(gift)); drop.setUuid(dropId); drop.setVelocity(0, .15, 0);
        boolean reported = false;
        try { reported = world.spawnEntity(drop); }
        catch (RuntimeException failed) { NonCreatureAffinity.LOGGER.error("Gift box spawn callback failed; actually inserted={}, state={}", world.getEntity(dropId) == drop, gift.state(), failed); }
        boolean inserted = world.getEntity(dropId) == drop;
        boolean issued = Objects.equals(gift.drop(), dropId) && (gift.state() == GiftEnvelope.State.ISSUED
                || gift.state() == GiftEnvelope.State.OPENING || gift.state() == GiftEnvelope.State.OPENED);
        if (!inserted && !issued) {
            boolean rolledBack = gift.rollback(dropId); save(world.getServer(), data);
            return rolledBack ? "spawn_failed" : "delivery_pending";
        }
        if (!reported) NonCreatureAffinity.LOGGER.warn("Gift box spawn did not report success, but persisted issuance or actual entity was confirmed: {}", dropId);
        if (gift.state() == GiftEnvelope.State.DELIVERING) data.issued(gift, dropId);
        save(world.getServer(), data); return "claimed";
    }
    private static Optional<Item> registered(String id) { var key = Identifier.tryParse(id); return key == null ? Optional.empty() : Registries.ITEM.getOptionalValue(key).filter(item -> item != Items.AIR); }
    public static String open(ServerPlayerEntity player, Hand hand) {
        var stack = player.getStackInHand(hand); if (!player.isAlive() || player.isSpectator() || stack.getItem() != GiftBoxItem.ITEM) return "unavailable";
        var ticket = GiftBoxItem.ticket(stack); UUID id, token;
        try { id = UUID.fromString(ticket.getString("gift", "")); token = UUID.fromString(ticket.getString("token", "")); } catch (IllegalArgumentException invalid) { return "unavailable"; }
        var world = player.getEntityWorld(); var data = peek(world.getServer()); var gift = data == null ? null : data.gift(id);
        if (gift == null || !gift.token().equals(token) || !OPENING.add(id)) return "unavailable";
        try {
            if (gift.state() == GiftEnvelope.State.DELIVERING) {
                if (!data.confirmHeldTicket(gift, token)) return "delivery_pending";
                save(world.getServer(), data);
            }
            if (gift.state() == GiftEnvelope.State.ISSUED) {
                var resolved = GiftRewardStacks.initial(world.getServer(), gift.content());
                if (!gift.startOpen(token,resolved.rewards(),resolved.fallbackApplied())) return "missing_rewards";
            } else if (gift.state() != GiftEnvelope.State.OPENING) return "unavailable";
            if (gift.rewardDrop() != null) return "delivery_pending";
            gift.repairRemaining(reward -> GiftRewardStacks.create(world.getServer(),reward).isPresent());
            save(world.getServer(), data);
            while (!gift.remaining().isEmpty()) {
                var reward = gift.remaining().getFirst(); var item = registered(reward.item()); if (item.isEmpty()) return "missing_rewards";
                var created = GiftRewardStacks.create(world.getServer(),reward); if (created.isEmpty()) return "missing_rewards";
                var offered = created.get(); player.getInventory().insertStack(offered);
                int inserted = reward.count() - offered.getCount(); if (inserted > 0) gift.delivered(inserted);
                while (!offered.isEmpty()) {
                    var part = offered.split(Math.min(offered.getCount(), offered.getMaxCount())); var drop = new ItemEntity(world, player.getX(), player.getY(), player.getZ(), part); UUID dropId = UUID.randomUUID(); drop.setUuid(dropId);
                    var remainingBefore = gift.remaining(); int emittedCount = part.getCount();
                    if (!gift.reserveReward(dropId, emittedCount)) return "delivery_pending";
                    gift.rewardLocation(new GiftEnvelope.Location(world.getRegistryKey().getValue().toString(), player.getX(), player.getY(), player.getZ()));
                    var receipt = new NbtCompound(); receipt.putString("gift", id.toString()); receipt.putString("token", token.toString()); receipt.putInt("count", part.getCount()); drop.setAttached(REWARD_DROP, receipt); save(world.getServer(), data);
                    boolean reported = false;
                    try { reported = world.spawnEntity(drop); }
                    catch (RuntimeException failed) { NonCreatureAffinity.LOGGER.error("Gift overflow spawn callback failed; actually inserted={}, pending={}", world.getEntity(dropId) == drop, gift.rewardDrop(), failed); }
                    var expected = new ArrayList<>(remainingBefore); var head = expected.removeFirst();
                    if (emittedCount < head.count()) expected.addFirst(head.withCount(head.count() - emittedCount));
                    boolean completed = gift.rewardDrop() == null && gift.remaining().equals(expected);
                    boolean entityInserted = world.getEntity(dropId) == drop;
                    if (!entityInserted && !completed) {
                        boolean rolledBack = gift.rewardFailed(dropId); save(world.getServer(), data);
                        return rolledBack ? "spawn_failed" : "delivery_pending";
                    }
                    if (!reported) NonCreatureAffinity.LOGGER.warn("Gift overflow spawn did not report success, but exact receipt progress or actual entity was confirmed: {}", dropId);
                    if (gift.rewardDrop() != null) gift.rewardSpawned(dropId);
                }
                save(world.getServer(), data);
            }
            if (gift.state() == GiftEnvelope.State.OPENED) {
                stack.decrement(1); player.currentScreenHandler.sendContentUpdates(); save(world.getServer(), data);
                if(net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,com.nonid.affinity.net.GiftOpeningPayload.ID))
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new com.nonid.affinity.net.GiftOpeningPayload(
                            gift.content().id(),world.getRegistryKey().getValue(),gift.content().wrapping()));
                return "opened";
            }
            return "delivery_pending";
        } finally { OPENING.remove(id); }
    }
    private static void recoverKnown(MinecraftServer server, GiftEnvelope.Location location, UUID id) {
        if (location == null || id == null) return; var world = world(server, location.dimension());
        if (world != null && world.getEntity(id) instanceof ItemEntity item) recoveredDrop(item);
    }
    private static void recoveredDrop(ItemEntity item) {
        if (!(item.getEntityWorld() instanceof ServerWorld world)) return; var data = peek(world.getServer()); if (data == null) return;
        var reward = item.getAttached(REWARD_DROP);
        if (reward != null) {
            try { var gift = data.gift(UUID.fromString(reward.getString("gift", ""))); if (gift != null && gift.token().toString().equals(reward.getString("token", "")) && gift.rewardDrop() != null && gift.rewardDrop().equals(item.getUuid())
                    && gift.rewardCount() == item.getStack().getCount() && !gift.remaining().isEmpty() && GiftRewardStacks.matches(world.getServer(),gift.remaining().getFirst(),item.getStack())) { gift.rewardSpawned(item.getUuid()); save(world.getServer(), data); } }
            catch (IllegalArgumentException invalid) { NonCreatureAffinity.LOGGER.warn("Malformed gift overflow receipt on {}", item.getUuid(), invalid); }
        }
        if (item.getStack().getItem() != GiftBoxItem.ITEM) return;
        var ticket = GiftBoxItem.ticket(item.getStack());
        try { var gift = data.gift(UUID.fromString(ticket.getString("gift", ""))); if (gift != null && gift.token().toString().equals(ticket.getString("token", "")) && gift.state() == GiftEnvelope.State.DELIVERING && Objects.equals(gift.drop(), item.getUuid())) { data.issued(gift, item.getUuid()); save(world.getServer(), data); } }
        catch (IllegalArgumentException invalid) { NonCreatureAffinity.LOGGER.warn("Malformed gift box receipt on {}", item.getUuid(), invalid); }
    }
}

