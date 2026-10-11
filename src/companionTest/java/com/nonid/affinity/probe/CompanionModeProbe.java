package com.nonid.affinity.probe;

import com.mojang.authlib.GameProfile;
import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.config.AffinityConfig;
import com.nonid.affinity.data.AffinityHolder;
import com.nonid.affinity.data.CompanionMode;
import com.nonid.affinity.data.CreatureAffinityData;
import com.nonid.affinity.service.*;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Real server/entities/navigation. Air flags alone are injected; this is not a swimming test. */
public final class CompanionModeProbe implements ModInitializer {
    private ServerWorld world;
    private ServerPlayerEntity owner;
    private ServerPlayerEntity otherOwner;
    private TestWolf wolf;
    private WolfEntity second;
    private MobEntity pig;
    private MobEntity hostile;
    private MobEntity archer;
    private AffinityConfig config;
    private int ticks, passed, failed;
    private float meleeStartHealth, rangedStartHealth, swimStartHealth;
    private int maxSwimAir;
    private float lastRangedHealth;
    private int rangedHits, lastRangedHitTick;
    private boolean allRangedHitsCredited;

    public static final class TestWolf extends WolfEntity {
        boolean underwater;
        TestWolf(World world) { super(EntityType.WOLF, world); }
        @Override public boolean isSubmergedInWater() { return underwater || super.isSubmergedInWater(); }
    }

    @Override public void onInitialize() {
        if (!Boolean.getBoolean("nonaffinity.companionProbe")) return;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                if (++ticks == 20) setup(server);
                if (ticks == 40) {
                    runChecks();
                    config.companionFollowIntervalTicks = 10;
                    reset(CompanionMode.FOLLOW, 8);
                }
                runLiveChecks(server);
            } catch (Throwable error) {
                System.out.println("COMPANION_PROBE_FATAL " + error);
                error.printStackTrace();
                server.stop(false);
            }
        });
    }

    private void setup(MinecraftServer server) {
        world = server.getOverworld();
        config = NonCreatureAffinity.getConfig();
        config.debugLogging = true;
        // A small, loaded, flat arena in a disposable world.
        for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) world.getChunk(cx, cz);
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
            world.setBlockState(new BlockPos(x, 64, z), Blocks.STONE.getDefaultState(), 3);
            for (int y = 65; y <= 69; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
        }
        owner = player(server, "CompanionTestA", "00000000-0000-0000-0000-000000000041", 0);
        otherOwner = player(server, "CompanionTestB", "00000000-0000-0000-0000-000000000042", 12);
        // Exercise the registered item's real consumption path before companion fixtures are created.
        owner.getHungerManager().setFoodLevel(10);
        owner.getHungerManager().setSaturationLevel(0);
        var foodStack = com.nonid.affinity.item.NonAffinityItems.tokenStack();
        foodStack.setCount(2);
        var consumable = foodStack.get(net.minecraft.component.DataComponentTypes.CONSUMABLE);
        check("token is edible when hungry", consumable != null && consumable.canConsume(owner, foodStack));
        foodStack = foodStack.finishUsing(world, owner);
        check("token restores five hunger and consumes one item",
                owner.getHungerManager().getFoodLevel() == 15 && foodStack.getCount() == 1
                && owner.getHungerManager().getSaturationLevel() == 0);
        owner.getHungerManager().setFoodLevel(18);
        foodStack = foodStack.finishUsing(world, owner);
        check("token hunger caps at twenty and last item is consumed",
                owner.getHungerManager().getFoodLevel() == 20 && foodStack.isEmpty());
        check("token cannot be eaten at full hunger", !consumable.canConsume(owner,
                com.nonid.affinity.item.NonAffinityItems.tokenStack()));
        owner.getHungerManager().setSaturationLevel(5);
        runVillagerChecks();
        wolf = new TestWolf(world);
        wolf.setPersistent();
        wolf.refreshPositionAndAngles(2.5, 65, 0.5, 0, 0);
        wolf.setTamedBy(owner);
        world.spawnEntity(wolf);
        second = EntityType.WOLF.create(world, SpawnReason.COMMAND);
        second.setPersistent();
        second.refreshPositionAndAngles(2.5, 65, 12.5, 0, 0);
        second.setTamedBy(otherOwner);
        world.spawnEntity(second);
        pig = EntityType.PIG.create(world, SpawnReason.COMMAND);
        pig.setAiDisabled(true);
        pig.setInvulnerable(true);
        pig.refreshPositionAndAngles(3.5, 65, 0.5, 0, 0);
        pig.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
        world.spawnEntity(pig);
        hostile = EntityType.ZOMBIE.create(world, SpawnReason.COMMAND);
        hostile.setAiDisabled(true);
        hostile.setInvulnerable(true);
        hostile.refreshPositionAndAngles(4.5, 65, 0.5, 0, 0);
        world.spawnEntity(hostile);
        check("vanilla tame creates friend without fixture injection",
                data(wolf).get(owner.getUuid()) != null
                && data(wolf).get(owner.getUuid()).isBefriended()
                && owner.getUuid().equals(data(wolf).getCompanionOwner()));
        try {
            var source = server.getCommandSource().withEntity(owner).withWorld(world);
            var dispatcher = server.getCommandManager().getDispatcher();
            int refused = dispatcher.execute("nonaffinity friend " + second.getUuid(), source);
            check("friend command refuses another owner without creating relation", refused == 0
                    && data(second).get(owner.getUuid()) == null
                    && otherOwner.getUuid().equals(data(second).getCompanionOwner()));
            int accepted = dispatcher.execute("nonaffinity friend " + pig.getUuid(), source);
            check("friend command binds fresh creature", accepted == 1
                    && data(pig).get(owner.getUuid()).isBefriended()
                    && owner.getUuid().equals(data(pig).getCompanionOwner()));
            data(pig).setCompanionMode(CompanionMode.WAIT);
            dispatcher.execute("nonaffinity friend " + pig.getUuid(), source);
            check("repeated friend command preserves mode", data(pig).getCompanionMode() == CompanionMode.WAIT);
            int threshold = config.friendThreshold;
            config.friendThreshold = config.maxAffinity + 1;
            try {
                int invalid = dispatcher.execute("nonaffinity friend " + hostile.getUuid(), source);
                check("invalid threshold refuses without creating relation", invalid == 0
                        && data(hostile).get(owner.getUuid()) == null);
            } finally { config.friendThreshold = threshold; }
            CompanionService.clearBond(pig);
            data(pig).entries().clear();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
            throw new IllegalStateException(error);
        }
        bond(wolf, owner);
        bond(second, otherOwner);
        System.out.println("COMPANION_PROBE_SETUP complete");
    }

    private void runVillagerChecks() {
        var villager = EntityType.VILLAGER.create(world, SpawnReason.COMMAND);
        villager.setAiDisabled(true);
        villager.refreshPositionAndAngles(20.5, 65, 20.5, 0, 0);
        villager.setVillagerData(villager.getVillagerData().withProfession(
                world.getRegistryManager(), net.minecraft.village.VillagerProfession.FARMER));
        world.spawnEntity(villager);
        var offers = new net.minecraft.village.TradeOfferList();
        var sale = new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND, 3);
        sale.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal("Gift fixture"));
        var offer = new net.minecraft.village.TradeOffer(
                new net.minecraft.village.TradedItem(net.minecraft.item.Items.EMERALD, 20), sale, 12, 1, 0);
        offers.add(offer);
        villager.setOffers(offers);
        var relation = data(villager).getOrCreate(owner.getUuid(), 100);
        relation.setBefriended(true);
        owner.setSneaking(false);
        owner.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
        villager.interactMob(owner, net.minecraft.util.Hand.MAIN_HAND);
        check("villager real trade opening applies twenty percent", offer.getDisplayedFirstBuyItem().getCount() == 16);
        owner.closeHandledScreen();
        check("villager closing trade clears discount", offer.getSpecialPrice() == 0);
        villager.interactMob(otherOwner, net.minecraft.util.Hand.MAIN_HAND);
        check("villager unrelated customer gets original price", offer.getDisplayedFirstBuyItem().getCount() == 20);
        otherOwner.closeHandledScreen();
        villager.interactMob(owner, net.minecraft.util.Hand.MAIN_HAND);
        check("villager reopening does not stack discount", offer.getDisplayedFirstBuyItem().getCount() == 16);
        relation.setAffinity(50, 0, 100);
        villager.restock();
        check("villager restock refreshes current discount", offer.getDisplayedFirstBuyItem().getCount() == 18);
        owner.closeHandledScreen();
        relation.setAffinity(50, 0, 100);
        villager.interactMob(owner, net.minecraft.util.Hand.MAIN_HAND);
        check("villager half affinity gives ten percent", offer.getDisplayedFirstBuyItem().getCount() == 18);
        owner.closeHandledScreen();
        relation.setAffinity(100, 0, 100);
        check("villager prices never become free", VillagerRelationshipBenefits.discount(1, 100, 100) == 0
                && VillagerRelationshipBenefits.discount(4, 100, 100) == 0
                && VillagerRelationshipBenefits.discount(20, 1000, 100) == 4);
        int before = owner.getInventory().count(net.minecraft.item.Items.DIAMOND);
        AffinityService.recordMating(world, owner, villager, true, config);
        check("villager completed player event gives exactly one sale item",
                owner.getInventory().count(net.minecraft.item.Items.DIAMOND) == before + 1);
        boolean named = false;
        for (int slot = 0; slot < owner.getInventory().size(); slot++) {
            var item = owner.getInventory().getStack(slot);
            if (item.isOf(net.minecraft.item.Items.DIAMOND) && "Gift fixture".equals(item.getName().getString())) named = true;
        }
        check("villager gift keeps components and original offer", named && offer.getSellItem().getCount() == 3 && offer.getUses() == 0);
        AffinityService.recordMating(world, owner, villager, true, config);
        check("villager each new completion can give another gift",
                owner.getInventory().count(net.minecraft.item.Items.DIAMOND) == before + 2);
        AffinityService.recordMating(world, owner, villager, false, config);
        check("villager autonomous completion gives no trade gift",
                owner.getInventory().count(net.minecraft.item.Items.DIAMOND) == before + 2);
        check("villager newly befriended completion is not eligible",
                !VillagerRelationshipBenefits.giveCompletionGift(villager, owner, true, false, false));
        villager.setOffers(new net.minecraft.village.TradeOfferList());
        check("villager empty offer list gives no gift",
                !VillagerRelationshipBenefits.giveCompletionGift(villager, owner, true, true, false));
        villager.discard();
    }

    private ServerPlayerEntity player(MinecraftServer server, String name, String id, int z) {
        GameProfile profile = new GameProfile(UUID.fromString(id), name);
        ServerPlayerEntity p = new ServerPlayerEntity(server, world, profile, SyncedClientOptions.createDefault());
        p.networkHandler = new ServerPlayNetworkHandler(server, new ClientConnection(NetworkSide.SERVERBOUND), p,
                ConnectedClientData.createDefault(profile, false));
        p.refreshPositionAndAngles(0.5, 65, z + 0.5, 0, 0);
        world.onPlayerConnected(p);
        return p;
    }

    private static CreatureAffinityData data(MobEntity mob) { return ((AffinityHolder) mob).affinityData(); }
    private void bond(MobEntity mob, ServerPlayerEntity player) {
        data(mob).setCompanionOwner(player.getUuid());
        data(mob).getOrCreate(player.getUuid(), 100).setBefriended(true);
    }

    private void reset(CompanionMode mode, double x) {
        wolf.underwater = false;
        wolf.setAir(wolf.getMaxAir());
        wolf.setSitting(false);
        wolf.setInSittingPose(false);
        wolf.refreshPositionAndAngles(x + 0.5, 65, 0.5, 0, 0);
        owner.refreshPositionAndAngles(0.5, 65, 0.5, 0, 0);
        pig.refreshPositionAndAngles(3.5, 65, 0.5, 0, 0);
        hostile.setTarget(null);
        wolf.setTarget(null);
        second.setTarget(null);
        second.setSitting(false);
        second.refreshPositionAndAngles(2.5, 65, 12.5, 0, 0);
        wolf.getNavigation().stop();
        second.getNavigation().stop();
        CompanionCombatController.clear();
        CompanionFollowService.clear();
        CompanionEventLog.clear();
        data(wolf).setCompanionMode(mode);
        data(wolf).clearAnchor();
        if (mode == CompanionMode.WAIT) data(wolf).setAnchor("minecraft:overworld", wolf.getX(), 65, wolf.getZ());
        data(second).setCompanionMode(CompanionMode.WAIT);
        data(second).setAnchor("minecraft:overworld", second.getX(), 65, second.getZ());
        CompanionCombatController.onModeCommand(wolf);
        CompanionCombatController.onModeCommand(second);
    }

    private void test(String name, Runnable action) {
        try { action.run(); passed++; System.out.println("COMPANION_PROBE_PASS " + name); }
        catch (Throwable error) { failed++; System.out.println("COMPANION_PROBE_FAIL " + name + ": " + error); error.printStackTrace(); }
    }
    private void check(String reason, boolean condition) { if (condition) { passed++; System.out.println("COMPANION_PROBE_PASS " + reason); } else { failed++; System.out.println("COMPANION_PROBE_FAIL " + reason); } }
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }

    private void runChecks() {
        test("four-mode-cycle-and-anchor", () -> {
            reset(CompanionMode.FOLLOW, 2);
            for (CompanionMode expected : new CompanionMode[]{CompanionMode.WANDER, CompanionMode.WAIT, CompanionMode.ATTACK, CompanionMode.FOLLOW}) {
                check(CompanionService.toggle(owner, wolf, config) == CompanionService.Result.TOGGLED, "command rejected");
                CompanionCombatController.onModeCommand(wolf);
                check(CompanionService.modeOf(wolf) == expected, "wrong mode");
                check(data(wolf).hasAnchor() == (expected == CompanionMode.WAIT), "only WAIT should retain an anchor");
            }
        });
        test("explicit-mobile-command-releases-sitting", () -> {
            reset(CompanionMode.WAIT, 2);
            wolf.setSitting(true);
            wolf.setInSittingPose(true);
            check(CompanionService.toggle(owner, wolf, config) == CompanionService.Result.TOGGLED, "command rejected");
            check(!wolf.isSitting() && !wolf.isInSittingPose(), "ATTACK recorded but wolf is still sitting");
        });
        test("other-player-cannot-switch", () -> {
            reset(CompanionMode.WAIT, 2);
            wolf.setSitting(true);
            data(wolf).getOrCreate(otherOwner.getUuid(), 100).setBefriended(true);
            check(CompanionService.toggle(otherOwner, wolf, config) == CompanionService.Result.NOT_OWNER, "wrong owner accepted");
            check(CompanionService.modeOf(wolf) == CompanionMode.WAIT && data(wolf).hasAnchor(), "rejected command changed state");
            check(wolf.isSitting(), "rejected command woke another player's pet");
        });
        test("follow-ten-block-boundary", () -> {
            reset(CompanionMode.FOLLOW, 10);
            CompanionFollowService.tick(world, config);
            check(wolf.squaredDistanceTo(owner) == 100.0, "recalled at exactly ten blocks");
        });
        test("follow-recall-and-second-companion-isolation", () -> {
            reset(CompanionMode.FOLLOW, 15);
            double secondX = second.getX(), secondZ = second.getZ();
            CompanionFollowService.tick(world, config);
            check(wolf.squaredDistanceTo(owner) < 100.0, "did not recall to safe loaded ground");
            check(second.getX() == secondX && second.getZ() == secondZ, "another companion moved");
        });
        test("attack-recalls", () -> {
            reset(CompanionMode.ATTACK, 15);
            CompanionFollowService.tick(world, config);
            check(wolf.squaredDistanceTo(owner) < 100.0, "ATTACK did not recall");
        });
        test("recall-refuses-hazardous-landing", () -> {
            reset(CompanionMode.FOLLOW, 15);
            try {
                for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
                    world.setBlockState(new BlockPos(x, 64, z), Blocks.MAGMA_BLOCK.getDefaultState(), 3);
                CompanionFollowService.tick(world, config);
                check(wolf.squaredDistanceTo(owner) == 225.0, "recalled onto hazardous ground");
            } finally {
                for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
                    world.setBlockState(new BlockPos(x, 64, z), Blocks.STONE.getDefaultState(), 3);
            }
        });
        test("wander-and-wait-never-recall", () -> {
            for (CompanionMode mode : new CompanionMode[]{CompanionMode.WANDER, CompanionMode.WAIT}) {
                reset(mode, 15);
                CompanionFollowService.tick(world, config);
                check(wolf.squaredDistanceTo(owner) == 225.0, mode + " recalled");
            }
        });
        test("follow-requests-real-path", () -> {
            reset(CompanionMode.FOLLOW, 8);
            CompanionFollowService.tick(world, config);
            check(wolf.getNavigation().getCurrentPath() != null, "no follow path");
        });
        test("wait-returns-to-order-position", () -> {
            reset(CompanionMode.WAIT, 8);
            wolf.refreshPositionAndAngles(14.5, 65, 0.5, 0, 0);
            CompanionFollowService.tick(world, config);
            check(wolf.getNavigation().getCurrentPath() != null, "no path to waiting anchor");
            check(wolf.getNavigation().getCurrentPath().getTarget().getX() == 8, "path does not end at anchor");
        });
        test("follow-assists-owner-attack", () -> {
            reset(CompanionMode.FOLLOW, 2);
            CompanionEventLog.recordOwnerAttack(owner, pig, false);
            CompanionCombatController.tick(world, config);
            check(wolf.getTarget() == pig, "FOLLOW did not engage");
        });
        test("wander-refuses-neutral-owner-attack", () -> {
            reset(CompanionMode.WANDER, 2);
            CompanionEventLog.recordOwnerAttack(owner, pig, false);
            CompanionCombatController.tick(world, config);
            check(wolf.getTarget() == null, "WANDER attacked a neutral target");
        });
        test("wander-defends-hurt-owner", () -> {
            reset(CompanionMode.WANDER, 2);
            CompanionEventLog.recordOwnerHurt(owner, hostile, true);
            CompanionCombatController.tick(world, config);
            check(wolf.getTarget() == hostile, "WANDER did not defend its owner");
        });
        test("wait-ignores-owner-combat", () -> {
            reset(CompanionMode.WAIT, 2);
            CompanionEventLog.recordOwnerAttack(owner, pig, false);
            CompanionEventLog.recordOwnerHurt(owner, hostile, true);
            CompanionCombatController.tick(world, config);
            check(wolf.getTarget() == null, "WAIT joined owner's fight");
        });
        test("wait-defends-without-pursuit", () -> {
            reset(CompanionMode.WAIT, 2);
            CompanionEventLog.recordSelfDefence(wolf, pig);
            CompanionCombatController.tick(world, config);
            check(wolf.getTarget() == pig, "WAIT ignored nearby attacker");
            check(wolf.getNavigation().isIdle(), "WAIT started pursuit");
            pig.refreshPositionAndAngles(9.5, 65, 0.5, 0, 0);
            CompanionCombatController.tick(world, config);
            check(wolf.getTarget() == null, "WAIT kept out-of-reach attacker");
        });
        test("attack-responds-to-owner-specific-hostility", () -> {
            reset(CompanionMode.ATTACK, 2);
            hostile.setTarget(owner);
            CompanionEventLog.recordHostileToOwner(owner, hostile);
            CompanionCombatController.tick(world, config);
            check(wolf.getTarget() == hostile, "ATTACK ignored actual hostility");
        });
        test("two-owners-do-not-share-commands", () -> {
            reset(CompanionMode.FOLLOW, 2);
            data(second).setCompanionMode(CompanionMode.FOLLOW);
            CompanionCombatController.onModeCommand(second);
            CompanionEventLog.recordOwnerAttack(owner, pig, false);
            CompanionCombatController.tick(world, config);
            check(wolf.getTarget() == pig && second.getTarget() == null, "owner command leaked to another companion");
        });
        test("mode-switch-discards-old-combat", () -> {
            reset(CompanionMode.FOLLOW, 2);
            CompanionEventLog.recordOwnerAttack(owner, pig, false);
            CompanionCombatController.tick(world, config);
            data(wolf).setCompanionMode(CompanionMode.WAIT);
            CompanionCombatController.onModeCommand(wolf);
            CompanionCombatController.tick(world, config);
            check(wolf.getTarget() == null, "old attack survived switch");
        });
        test("owner-pets-are-protected", () -> {
            reset(CompanionMode.ATTACK, 2);
            CompanionEventLog.recordOwnerAttack(owner, second, true);
            CompanionCombatController.tick(world, config);
            check(wolf.getTarget() == null, "ATTACK selected another player's pet");
        });
        test("low-air-preserves-path-even-after-wait-defence", () -> {
            reset(CompanionMode.WAIT, 2);
            CompanionEventLog.recordSelfDefence(wolf, pig);
            CompanionCombatController.tick(world, config);
            wolf.underwater = true;
            wolf.setAir(10);
            check(wolf.getNavigation().startMovingTo(2.5, 65, 8.5, 1), "fixture path could not start");
            var breathingPath = wolf.getNavigation().getCurrentPath();
            CompanionCombatController.tick(world, config);
            CompanionFollowService.tick(world, config);
            check(wolf.getNavigation().getCurrentPath() == breathingPath && !wolf.getNavigation().isIdle(), "breathing path was stopped/replaced");
            check(wolf.getTarget() == null, "companion kept combat target during breathing yield");
        });
        test("mode-and-anchor-survive-save", () -> {
            for (CompanionMode mode : CompanionMode.values()) {
                reset(mode, 8);
                var restored = CreatureAffinityData.fromNbt(data(wolf).toNbt());
                check(restored.getCompanionOwner().equals(owner.getUuid()), "owner lost");
                check(restored.getCompanionMode() == mode, "mode lost: " + mode);
                check(restored.hasAnchor() == (mode == CompanionMode.WAIT), "anchor mismatch: " + mode);
                if (mode == CompanionMode.WAIT) check(restored.anchorX() == 8.5
                        && restored.anchorIn("minecraft:overworld"), "WAIT anchor lost");
            }
        });
        test("clearing-bond-removes-wait-anchor", () -> {
            reset(CompanionMode.WAIT, 8);
            check(CompanionService.clearBond(wolf).equals(owner.getUuid()), "wrong released owner");
            check(!data(wolf).hasAnchor(), "cleared bond retained stationary order");
            bond(wolf, owner);
        });
    }

    private void runLiveChecks(MinecraftServer server) {
        if (ticks == 120) {
            test("live-follow-walks-to-owner", () -> check(wolf.squaredDistanceTo(owner) <= 16,
                    "did not walk within four blocks: distance=" + Math.sqrt(wolf.squaredDistanceTo(owner))));
            reset(CompanionMode.WAIT, 8);
            wolf.refreshPositionAndAngles(14.5, 65, 0.5, 0, 0);
        }
        if (ticks == 200) {
            test("live-wait-walks-back-to-anchor", () -> check(wolf.squaredDistanceTo(8.5, 65, 0.5)
                    <= config.companionWaitRadius * config.companionWaitRadius + 0.5, "did not return to WAIT radius"));
            reset(CompanionMode.FOLLOW, 2);
            config.companionEventMaxAgeTicks = 20;
            config.companionEngageStallTicks = 120;
            pig.setInvulnerable(false);
            pig.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(1000);
            pig.setHealth(1000);
            meleeStartHealth = pig.getHealth();
            CompanionEventLog.recordOwnerAttack(owner, pig, false);
        }
        if (ticks == 460) {
            test("live-melee-renews-beyond-evidence-and-stall-time", () -> {
                var progress = CompanionCombatController.progressOf(wolf);
                check(pig.getHealth() < meleeStartHealth, "no real melee damage");
                check(progress != null && progress.isEngaged() && !progress.isStalled(world.getTime(), 120),
                        "melee engagement stalled despite live attacks");
            });
            reset(CompanionMode.WAIT, -12);
            // The earlier hostility fixture must not stand between the bow and its target.
            hostile.refreshPositionAndAngles(20.5, 65, 20.5, 0, 0);
            // Skeletons naturally flee nearby wolves, including the other test companion.
            // Keep unrelated fixtures outside that range and remove daylight avoidance.
            second.refreshPositionAndAngles(20.5, 65, -20.5, 0, 0);
            data(second).setAnchor("minecraft:overworld", 20.5, 65, -20.5);
            wolf.refreshPositionAndAngles(-20.5, 65, 20.5, 0, 0);
            data(wolf).setAnchor("minecraft:overworld", -20.5, 65, 20.5);
            world.setTimeOfDay(18000);
            // Vanilla bow AI can retreat to thirteen blocks. Use a walled firing arena so
            // this sustained-hit test cannot accidentally become a ten-block recall test.
            // Both the bow AI and projectile collision/damage remain entirely vanilla.
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                if (Math.abs(x) == 5 || Math.abs(z) == 5) {
                    for (int y = 65; y <= 67; y++) world.setBlockState(new BlockPos(x, y, z),
                            Blocks.STONE.getDefaultState(), 3);
                }
            }
            // Centre the bow's natural strafing circle beside the owner. With a target six
            // blocks away, vanilla strafing crosses the ten-block recall boundary and the
            // correctly-ended fight cannot test sustained hit renewal.
            owner.refreshPositionAndAngles(0.5, 65, -1.5, 0, 0);
            // A large stationary target makes hit-renewal repeatable without changing bow
            // accuracy or faking damage; small pigs can legitimately be missed repeatedly.
            pig.discard();
            pig = EntityType.RAVAGER.create(world, SpawnReason.COMMAND);
            pig.setAiDisabled(true);
            pig.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(1000);
            pig.refreshPositionAndAngles(0.5, 65, 0.5, 0, 0);
            pig.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
            world.spawnEntity(pig);
            pig.setHealth(1000);
            rangedStartHealth = pig.getHealth();
            lastRangedHealth = rangedStartHealth;
            rangedHits = 0;
            lastRangedHitTick = 0;
            allRangedHitsCredited = true;
            archer = EntityType.SKELETON.create(world, SpawnReason.COMMAND);
            archer.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND,
                    new net.minecraft.item.ItemStack(net.minecraft.item.Items.BOW));
            ((net.minecraft.entity.mob.AbstractSkeletonEntity) archer).updateAttackType();
            archer.getRandom().setSeed(41);
            archer.setPersistent();
            archer.setInvulnerable(true);
            archer.refreshPositionAndAngles(4.5, 65, 0.5, 0, 0);
            world.spawnEntity(archer);
            bond(archer, owner);
            data(archer).setCompanionMode(CompanionMode.FOLLOW);
            CompanionCombatController.onModeCommand(archer);
        }
        if (ticks == 461) CompanionEventLog.recordOwnerAttack(owner, pig, false);
        if (ticks > 461 && ticks <= 720 && pig.getHealth() < lastRangedHealth) {
            var hitState = CompanionCombatController.progressOf(archer);
            rangedHits++;
            lastRangedHitTick = ticks;
            allRangedHitsCredited &= hitState != null && hitState.isEngaged()
                    && hitState.lastProgressTick() == world.getTime();
            lastRangedHealth = pig.getHealth();
        }
        if (ticks > 461 && ticks < 720 && ticks % 40 == 0) {
            var state = CompanionCombatController.progressOf(archer);
            System.out.println("COMPANION_PROBE_RANGED tick=" + ticks + " age=" + archer.age
                    + " pos=" + archer.getEntityPos() + " target=" + archer.getTarget()
                    + " pigPos=" + pig.getEntityPos() + " health=" + pig.getHealth()
                    + " bow=" + archer.getMainHandStack() + " sees=" + archer.canSee(pig)
                    + " drawing=" + archer.isUsingItem() + " drawTicks=" + archer.getItemUseTime()
                    + " arrows=" + world.getEntitiesByClass(net.minecraft.entity.projectile.PersistentProjectileEntity.class,
                            archer.getBoundingBox().expand(24), entity -> true).size()
                    + " lastProgress=" + (state == null ? -2 : state.lastProgressTick()));
        }
        if (ticks == 720) {
            test("live-ranged-renews-beyond-evidence-and-stall-time", () -> {
                check(pig.getHealth() < rangedStartHealth, "no real arrow damage");
                check(rangedHits >= 2 && lastRangedHitTick - 460 > 120,
                        "fixture did not land sustained hits beyond the initial stall window");
                check(allRangedHitsCredited, "an actual arrow hit did not renew the current engagement");
            });
            System.out.println("COMPANION_PROBE_RANGED_CREDITS hits=" + rangedHits
                    + " lastHitTick=" + lastRangedHitTick + " allCredited=" + allRangedHitsCredited);
            archer.discard();
            reset(CompanionMode.WAIT, 0);
            // Four-block-deep pool: vanilla water detection, jumping and breathing run normally.
            for (int x = -3; x <= 3; x++) for (int z = -11; z <= -5; z++) {
                world.setBlockState(new BlockPos(x, 60, z), Blocks.STONE.getDefaultState(), 3);
                for (int y = 61; y <= 64; y++) world.setBlockState(new BlockPos(x, y, z),
                        Blocks.WATER.getDefaultState(), 3);
            }
            wolf.refreshPositionAndAngles(0.5, 61, -8.5, 0, 0);
            data(wolf).setAnchor("minecraft:overworld", 0.5, 61, -8.5);
            wolf.setHealth(wolf.getMaxHealth());
            wolf.setAir(40);
            swimStartHealth = wolf.getHealth();
            maxSwimAir = 0;
        }
        if (ticks > 720 && ticks <= 1120) maxSwimAir = Math.max(maxSwimAir, wolf.getAir());
        if (ticks == 1120) {
            test("live-wait-surfaces-without-drowning", () -> {
                check(wolf.isAlive() && wolf.getHealth() >= swimStartHealth, "WAIT drowned or took damage in pool");
                check(maxSwimAir > 100, "WAIT never recovered meaningful air, maximum=" + maxSwimAir);
            });
            System.out.println("COMPANION_PROBE_SWIM maxAir=" + maxSwimAir + " health=" + wolf.getHealth());
            System.out.println("COMPANION_PROBE_RESULT passed=" + passed + " failed=" + failed);
            server.stop(false);
        }
    }
}
