package com.nonid.affinity.reunion;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.data.AffinityHolder;
import com.nonid.affinity.data.CreatureAffinityData;
import com.nonid.affinity.item.NonAffinityItems;
import com.nonid.affinity.service.*;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;

public final class ReunionService {
    public static void register() {
        ReunionData.init(); ReunionChargeFeedback.register(); ReunionRecipe.register(); ReunionPending.register(); ReunionRecall.register();
        UseEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            var stack = player.getStackInHand(hand);
            if (!stack.isOf(NonAffinityItems.REUNION_WISH)) return ActionResult.PASS;
            if (player instanceof ServerPlayerEntity serverPlayer) {
                if (target instanceof MobEntity mob) {
                    if (!ReunionData.item(stack).contains("mob") && player.isSneaking()) bind(serverPlayer,mob,stack);
                    else if (player.isSneaking()) ReunionRecall.request(serverPlayer,stack,hand);
                    else heal(serverPlayer,mob,stack);
                }
                else message(serverPlayer, "bind_hint");
            }
            return ActionResult.SUCCESS;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> capture(entity));
    }
    static void message(ServerPlayerEntity player, String key) {
        if(key.equals("revived")&&net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,com.nonid.affinity.net.ReunionResultPayload.ID))return;
        if(ReunionNoticeFeedback.send(player,key))return;
        String translation = switch (key) {
            case "bind_hint" -> "message.noncreatureaffinity.reunion_bind_hint";
            case "already_bound" -> "message.noncreatureaffinity.reunion_already_bound";
            case "not_friend" -> "message.noncreatureaffinity.reunion_not_friend";
            case "other_owner" -> "message.noncreatureaffinity.reunion_other_owner";
            case "unsupported" -> "message.noncreatureaffinity.reunion_unsupported";
            case "bound" -> "message.noncreatureaffinity.reunion_bound";
            case "invalid" -> "message.noncreatureaffinity.reunion_invalid";
            case "empty" -> "message.noncreatureaffinity.reunion_empty";
            case "alive" -> "message.noncreatureaffinity.reunion_alive";
            case "wait" -> "message.noncreatureaffinity.reunion_wait";
            case "no_space" -> "message.noncreatureaffinity.reunion_no_space";
            case "revived" -> "message.noncreatureaffinity.reunion_revived";
            default -> "message.noncreatureaffinity.reunion_" + key;
        };
        com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(translation), true);
    }
    public static boolean bind(ServerPlayerEntity player, MobEntity mob, ItemStack stack) {
        if (player.isSpectator() || !player.isAlive() || !mob.isAlive() || mob.isRemoved()
                || mob.getEntityWorld() != player.getEntityWorld() || player.squaredDistanceTo(mob) > 36
                || !stack.isOf(NonAffinityItems.REUNION_WISH)) return false;
        if (CreakingMemory.bound(mob)) {
            com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable("message.noncreatureaffinity.creaking_reunion"), true);
            return false;
        }
        if (ReunionData.item(stack).contains("mob")) { message(player, "already_bound"); return false; }
        var relation = AffinityService.getRelation(mob, player);
        if (relation == null || !RelationshipStages.friend(relation.getAffinity(), NonCreatureAffinity.getConfig())) {
            message(player, "not_friend"); return false;
        }
        var owner = FriendlyDefenderSystem.vanillaOwnerUuid(mob);
        var affinity = ((AffinityHolder) mob).peekAffinityData();
        var companion = affinity == null ? null : affinity.getCompanionOwner();
        if ((owner != null && !owner.equals(player.getUuid()))
                || (companion != null && !companion.equals(player.getUuid()))) {
            message(player, "other_owner"); return false;
        }
        if (ReplayIsolation.isActor(mob.getUuid()) || ReplayIsolation.isActor(player.getUuid()) || !mob.getType().isSaveable()) {
            message(player, "unavailable"); return false;
        }
        var server = player.getEntityWorld().getServer();
        String id = mob.getUuidAsString();
        if (!ReunionData.record(server, id).isEmpty()) { message(player, "already_bound"); return false; }
        var record = new NbtCompound();
        record.putString("bond", UUID.randomUUID().toString()); record.putString("owner", player.getUuidAsString());
        record.putString("mob", id); record.putString("type", Registries.ENTITY_TYPE.getId(mob.getType()).toString());
        name(record, mob); record.putLong("spentFuel", 0); record.putLong("spent", 0); record.putBoolean("dead", false);
        // Confirm serialization is supported before committing a permanent binding.
        try { snapshot(mob); } catch (RuntimeException e) {
            NonCreatureAffinity.LOGGER.warn("Cannot bind reunion wish for {}", id, e);
            message(player, "unsupported"); return false;
        }
        ReunionData.save(server, id, record);
        var item = record.copy(); item.remove("spent"); item.remove("dead");
        item.putLong("fuel", 3); item.putInt("energy", 3); item.putLong("charge", 1); item.putBoolean("dim", false); ReunionData.item(stack, item);
        ReunionRecall.remember(mob); message(player, "bound"); return true;
    }
    private static void name(NbtCompound record, MobEntity mob) {
        record.putBoolean("custom_name", mob.hasCustomName());
        record.putString("name", mob.hasCustomName() ? mob.getName().getString() : mob.getType().getTranslationKey());
    }
    public static NbtCompound snapshot(MobEntity mob) {
        var view = NbtWriteView.create(ErrorReporter.EMPTY, mob.getEntityWorld().getRegistryManager());
        mob.writeData(view);
        var result = view.getNbt().copy();
        sanitize(result);
        return result;
    }
    /** Item data is never restored, including nested mod inventories using vanilla stack codecs. */
    public static void sanitize(NbtCompound nbt) {
        sanitize(nbt, id -> Registries.ITEM.containsId(id));
    }
    /** The registry lookup is supplied separately so data cleanup can also be tested without a game loader. */
    static void sanitize(NbtCompound nbt, java.util.function.Predicate<net.minecraft.util.Identifier> registeredItem) {
        for (String key : new String[]{"equipment", "ArmorItems", "HandItems", "body_armor_item", "saddle",
                "SaddleItem", "DecorItem", "Inventory", "inventory", "Items", "items", "ChestedHorse",
                "Passengers", "Leash", "leash", "Brain", "Health", "DeathTime", "HurtTime", "HurtByTimestamp",
                "Fire", "FallDistance", "fall_distance", "Motion", "Pos", "PortalCooldown", "DeathLootTable"}) nbt.remove(key);
        stripStacks(nbt, registeredItem);
    }
    private static void stripStacks(NbtElement element, java.util.function.Predicate<net.minecraft.util.Identifier> registeredItem) {
        if (element instanceof NbtCompound compound) {
            for (String key : java.util.List.copyOf(compound.getKeys())) {
                var child = compound.get(key);
                if (child instanceof NbtCompound tag && isStoredStack(tag, registeredItem))
                    compound.remove(key);
                else if (!key.equals("Offers")) stripStacks(child, registeredItem);
            }
        } else if (element instanceof NbtList list) {
            for (int i = list.size() - 1; i >= 0; i--) {
                var child = list.get(i);
                if (child instanceof NbtCompound tag && isStoredStack(tag, registeredItem)) list.remove(i);
                else stripStacks(child, registeredItem);
            }
        }
    }
    private static boolean isStoredStack(NbtCompound tag, java.util.function.Predicate<net.minecraft.util.Identifier> registeredItem) {
        if (!tag.contains("count") && !tag.contains("Count")) return false;
        var id = net.minecraft.util.Identifier.tryParse(tag.getString("id", ""));
        // id/count is also a common non-item schema in other mods. Only registered items are possessions.
        return id != null && registeredItem.test(id);
    }
    public static void capture(LivingEntity entity) {
        if (!(entity instanceof MobEntity mob) || !(entity.getEntityWorld() instanceof ServerWorld world)
                || mob.isAlive() || CreakingMemory.bound(mob) || ReplayIsolation.isActor(mob.getUuid())) return;
        var record = ReunionData.record(world.getServer(), mob.getUuidAsString());
        if (record.isEmpty() || record.getBoolean("dead", false)) return;
        try {
            record.put("snapshot", snapshot(mob));
            var affinity = ((AffinityHolder) mob).peekAffinityData();
            if (affinity != null) record.put("affinity", affinity.toNbt());
            else record.remove("affinity");
            name(record, mob); record.putBoolean("dead", true);
            ReunionData.save(world.getServer(), mob.getUuidAsString(), record);
        } catch (RuntimeException e) {
            NonCreatureAffinity.LOGGER.error("Cannot capture reunion snapshot for {}", mob.getUuidAsString(), e);
        }
    }
    public static void refresh(ItemStack stack, ServerWorld world) {
        var item = ReunionData.item(stack);
        if (!item.contains("mob")) return;
        var record = ReunionData.record(world.getServer(), item.getString("mob", ""));
        if (!ReunionData.authentic(item, record)) return;
        var next = item.copy();
        next.putString("name", record.getString("name", "?"));
        next.putBoolean("custom_name", record.getBoolean("custom_name", false));
        // Stale copies become dim but keep their generation; they cannot skip ahead for free.
        int energy=ReunionData.energy(item,record);
        if(ReunionData.fuel(item)<ReunionData.spent(record) && ReunionData.spent(record)>=0) next.putLong("fuel",ReunionData.spent(record));
        next.putInt("energy",energy); next.putBoolean("dim",energy==0);
        if (!next.equals(item)) ReunionData.item(stack, next);
    }
    static boolean permitted(ServerPlayerEntity player, ItemStack stack) {
        if(player.isSpectator() || !player.isAlive() || !stack.isOf(NonAffinityItems.REUNION_WISH)
                || ReplayIsolation.isActor(player.getUuid()) || ReunionPending.contains(player)) return false;
        refresh(stack,player.getEntityWorld());
        var item=ReunionData.item(stack);
        var record=ReunionData.record(player.getEntityWorld().getServer(),item.getString("mob",""));
        if(!ReunionData.authentic(item,record) || !player.getUuidAsString().equals(record.getString("owner",""))) {
            message(player,"invalid"); return false;
        }
        if(ReunionData.energy(item,record)<1){message(player,"empty");return false;}
        return true;
    }
    static boolean owns(ServerPlayerEntity player, MobEntity mob) {
        var owner=FriendlyDefenderSystem.vanillaOwnerUuid(mob);
        var data=((AffinityHolder)mob).peekAffinityData();
        return (owner==null || owner.equals(player.getUuid()))
                && (data==null || data.getCompanionOwner()==null || data.getCompanionOwner().equals(player.getUuid()));
    }
    static void spendOne(ServerPlayerEntity player, ItemStack stack) {
        var item=ReunionData.item(stack);var id=item.getString("mob","");
        var record=ReunionData.record(player.getEntityWorld().getServer(),id);
        record.putLong("spentFuel",ReunionData.spent(record)+1);
        ReunionData.save(player.getEntityWorld().getServer(),id,record);refresh(stack,player.getEntityWorld());
    }
    public static boolean heal(ServerPlayerEntity player, MobEntity mob, ItemStack stack) {
        if(!permitted(player,stack))return false;
        if(!mob.getUuidAsString().equals(ReunionData.item(stack).getString("mob","")) || !owns(player,mob)) {
            message(player,"wrong_target");return false;
        }
        if(!mob.isAlive() || mob.isRemoved() || mob.getEntityWorld()!=player.getEntityWorld()
                || player.squaredDistanceTo(mob)>36 || ReplayIsolation.isActor(mob.getUuid()))return false;
        if(mob.getHealth()>=mob.getMaxHealth()){message(player,"full_health");return false;}
        float before = mob.getHealth();
        double eligible = com.nonid.affinity.service.HealthHistory.recover(mob, player,
                () -> mob.setHealth(mob.getMaxHealth()));
        if (mob.getHealth() <= before) return false;
        spendOne(player,stack);
        double ratio = Math.min(1, eligible / mob.getMaxHealth());
        com.nonid.affinity.service.AffinityService.addPreciseAffinity(mob, player, ratio);
        com.nonid.affinity.service.RelationshipBond.add(mob, player, ratio * 0.05);
        ReunionSuccessFeedback.send(player,mob,true);return true;
    }
    public static boolean revive(ServerPlayerEntity player, ItemStack stack) {
        if (ReunionPending.contains(player)) {
            com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable("message.noncreatureaffinity.reunion_preparing"), true);
            return false;
        }
        var hand = player.getMainHandStack() == stack ? net.minecraft.util.Hand.MAIN_HAND
                : player.getOffHandStack() == stack ? net.minecraft.util.Hand.OFF_HAND : null;
        return performRevival(player, stack, hand, null);
    }
    static boolean complete(ServerPlayerEntity player, ItemStack stack, Vec3d position) {
        return performRevival(player, stack, null, position);
    }
    private static boolean performRevival(ServerPlayerEntity player, ItemStack stack, net.minecraft.util.Hand prepareHand, Vec3d reservedPosition) {
        if (player.isSpectator() || !player.isAlive() || !stack.isOf(NonAffinityItems.REUNION_WISH)) return false;
        var world = player.getEntityWorld(); var server = world.getServer();
        refresh(stack, world);
        var item = ReunionData.item(stack); String id = item.getString("mob", "");
        if (id.isEmpty()) { message(player, "bind_hint"); return false; }
        var record = ReunionData.record(server, id);
        if (!ReunionData.authentic(item, record) || !player.getUuidAsString().equals(record.getString("owner", ""))) {
            message(player, "invalid"); return false;
        }
        if (!ReunionData.charged(item, record)) { message(player, "need_three"); return false; }
        if (!record.getBoolean("dead", false)) { message(player, "alive"); return false; }
        if (ReplayIsolation.isActor(player.getUuid())) { message(player, "unavailable"); return false; }
        UUID uuid;
        try { uuid = UUID.fromString(id); } catch (IllegalArgumentException e) { message(player, "invalid"); return false; }
        // Keep the original UUID; wait for the corpse to disappear rather than forcefully deleting it.
        for (var dimension : server.getWorlds()) if (dimension.getEntity(uuid) != null) {
            message(player, "wait"); return false;
        }
        var type = EntityType.get(record.getString("type", "")).orElse(null);
        if (type == null || (world.getDifficulty() == Difficulty.PEACEFUL && !type.isAllowedInPeaceful())) {
            message(player, "unsupported"); return false;
        }
        if (!record.contains("snapshot")) { message(player, "unsupported"); return false; }
        try {
            var entity = type.create(world, SpawnReason.COMMAND);
            if (!(entity instanceof MobEntity mob)) { message(player, "unsupported"); return false; }
            var nbt = record.getCompoundOrEmpty("snapshot").copy(); sanitize(nbt);
            mob.readData(NbtReadView.create(ErrorReporter.EMPTY, world.getRegistryManager(), nbt));
            if (CreakingMemory.bound(mob)) {
                com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable("message.noncreatureaffinity.creaking_reunion"), true);
                return false;
            }
            mob.setUuid(uuid); mob.deathTime = 0;
            mob.setFireTicks(0); mob.fallDistance = 0; mob.setVelocity(Vec3d.ZERO); mob.setTarget(null);
            for (var slot : EquipmentSlot.values()) mob.equipStack(slot, ItemStack.EMPTY);
            mob.setHealth(mob.getMaxHealth()); mob.setAir(mob.getMaxAir());
            if (record.contains("affinity")) ((AffinityHolder) mob).setAffinityDataRaw(
                    CreatureAffinityData.fromNbt(record.getCompoundOrEmpty("affinity")));
            if (!(reservedPosition == null ? place(world, player, mob) : safeAt(world, mob, reservedPosition))) {
                message(player, "no_space"); return false;
            }
            if (prepareHand != null) {
                ReunionPending.begin(player, mob, stack, prepareHand);
                return true;
            }
            // Commit before spawning: reentrant entity-load callbacks cannot spend twice.
            var previous = record.copy();
            record.putLong("spentFuel", ReunionData.fuel(item)); record.putLong("spent", item.getLong("charge", 0)); record.putBoolean("dead", false); record.remove("snapshot");
            ReunionData.save(server, id, record);
            boolean spawned;
            try { spawned = world.spawnEntity(mob); }
            catch (RuntimeException failure) {
                // If an external callback threw after insertion, the resurrection still consumed the charge.
                spawned = world.getEntity(uuid) == mob;
                if (!spawned) ReunionData.save(server, id, previous);
                else rewardRevival(player, mob);
                throw failure;
            }
            if (!spawned) { ReunionData.save(server, id, previous); message(player, "unsupported"); return false; }
            rewardRevival(player, mob);
            item.putInt("energy",0); item.putBoolean("dim", true); ReunionData.item(stack, item);
            message(player, "revived"); return true;
        } catch (RuntimeException e) {
            NonCreatureAffinity.LOGGER.error("Cannot revive reunion creature {}", id, e);
            refresh(stack, world); message(player, "unsupported"); return false;
        }
    }
    private static void rewardRevival(ServerPlayerEntity player, MobEntity mob) {
        com.nonid.affinity.service.AffinityService.addAffinity(mob, player, 3, "reunion_revive");
        com.nonid.affinity.service.RelationshipBond.add(mob, player, 0.5);
    }
    static boolean place(ServerWorld world, ServerPlayerEntity player, MobEntity mob) {
        BlockPos base = player.getBlockPos();
        // Prefer open ground in front of the player, so the gem and revival share a visible destination.
        double yaw = Math.toRadians(player.getYaw());
        Vec3d preferred = player.getEntityPos().add(-Math.sin(yaw) * 3.5, 0, Math.cos(yaw) * 3.5);
        var candidates = new java.util.ArrayList<BlockPos>();
        for (int radius = 2; radius <= 6; radius++) for (int y : new int[]{0,1,-1,2,-2})
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                candidates.add(base.add(x, y, z));
            }
        candidates.sort(java.util.Comparator.comparingDouble(pos -> {
            double dx = pos.getX() + .5 - preferred.x, dz = pos.getZ() + .5 - preferred.z;
            double dy = pos.getY() - base.getY();
            return dx * dx + dz * dz + 4 * dy * dy;
        }));
        for (var pos : candidates) {
            if (safeAt(world, mob, new Vec3d(pos.getX() + .5, pos.getY(), pos.getZ() + .5))) return true;
        }
        return false;
    }
    /** Initial selection and delayed commit use exactly the same safety rules. Never move a reserved landing. */
    static boolean safeAt(ServerWorld world, MobEntity mob, Vec3d position) {
        mob.setPosition(position);
        var pos = mob.getBlockPos();
        var box = mob.getBoundingBox();
        if (box.minY < world.getBottomY() || box.maxY > world.getTopYInclusive() + 1
                || !world.getWorldBorder().contains(box)) return false;
        for (var block : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ),
                BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
            if (!world.isChunkLoaded(block)) return false;
            var state = world.getBlockState(block);
            if (state.getFluidState().isIn(FluidTags.LAVA) || state.isOf(net.minecraft.block.Blocks.FIRE)
                    || state.isOf(net.minecraft.block.Blocks.POWDER_SNOW) || state.isOf(net.minecraft.block.Blocks.SOUL_FIRE)
                    || state.isOf(net.minecraft.block.Blocks.WITHER_ROSE) || state.isOf(net.minecraft.block.Blocks.SWEET_BERRY_BUSH)) return false;
        }
        if (!world.isSpaceEmpty(mob, box) || !world.getOtherEntities(mob, box, e -> !e.isSpectator()).isEmpty()) return false;
        boolean waterAnimal = mob instanceof WaterCreatureEntity || mob instanceof net.minecraft.entity.passive.WaterAnimalEntity;
        boolean aquatic = waterAnimal || (mob.getType().isIn(net.minecraft.registry.tag.EntityTypeTags.AQUATIC) && mob.canBreatheInWater());
        if (!waterAnimal && !mob.canBreatheInWater()
                && world.getFluidState(BlockPos.ofFloored(mob.getEyePos())).isIn(FluidTags.WATER)) return false;
        if (aquatic && world.getFluidState(pos).isIn(FluidTags.WATER)) return true;
        if (waterAnimal || !world.getFluidState(pos).isEmpty()) return false;
        var floor = world.getBlockState(pos.down());
        return floor.isSolidBlock(world, pos.down()) && !floor.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)
                && !floor.isOf(net.minecraft.block.Blocks.CACTUS) && !floor.isOf(net.minecraft.block.Blocks.CAMPFIRE)
                && !floor.isOf(net.minecraft.block.Blocks.SOUL_CAMPFIRE);
    }
    private ReunionService() { }
}

