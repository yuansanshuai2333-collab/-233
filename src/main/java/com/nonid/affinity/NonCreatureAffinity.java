package com.nonid.affinity;

import com.nonid.affinity.command.AffinityCommand;
import com.nonid.affinity.config.AffinityConfig;
import com.nonid.affinity.data.AffinityAttachments;
import com.nonid.affinity.data.AffinityHolder;
import com.nonid.affinity.data.CreatureAffinityData;
import com.nonid.affinity.data.PlayerAffinity;
import com.nonid.affinity.item.AffinityDetectorItem;
import com.nonid.affinity.item.NonAffinityItems;
import com.nonid.affinity.net.AffinityNetworking;
import com.nonid.affinity.non.NonBridge;
import com.nonid.affinity.service.CompanionCombatController;
import com.nonid.affinity.service.CompanionEventLog;
import com.nonid.affinity.service.CompanionService;
import com.nonid.affinity.service.FriendlyDefenderSystem;
import com.nonid.affinity.service.InheritanceService;
import com.nonid.affinity.service.RequestSystem;
import com.nonid.affinity.service.VillagerFreeTrades;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NoN Creature Affinity - a relationship system for creatures, built on NeedsOfNature.
 *
 * <ol>
 *   <li>A key lets the player actively trigger a NON mating animation with a creature.</li>
 *   <li>After mating (voluntary or not) the creature gains affinity toward the player.</li>
 *   <li>At the affinity threshold tameable creatures are tamed, others become friendly
 *       units that protect the player.</li>
 *   <li>Friendly creatures that reach the arousal and affinity thresholds ask for mating;
 *       the player can accept or reject.</li>
 *   <li>Rejecting costs affinity and triggers one forced mating the player can escape.</li>
 *   <li>Offspring start at full affinity and belong to the player.</li>
 * </ol>
 */
public class NonCreatureAffinity implements ModInitializer {

    public static final String MOD_ID = "noncreatureaffinity";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Verbose logging switch driven by the config. */
    public static volatile boolean LOG_VERBOSE = false;

    private static volatile AffinityConfig CONFIG = new AffinityConfig();

    public static AffinityConfig getConfig() {
        return CONFIG;
    }

    @Override
    public void onInitialize() {
        CONFIG = AffinityConfig.load();
        LOG_VERBOSE = CONFIG.debugLogging;

        NonAffinityItems.register();
        com.nonid.affinity.reunion.ReunionService.register();
        AffinityAttachments.init();
        com.nonid.affinity.service.CompanionArchive.register();
        com.nonid.affinity.gift.GiftService.register();
        com.nonid.affinity.service.CompanionArchiveQueries.register();
        com.nonid.affinity.service.CompanionMemorial.register();
        com.nonid.affinity.net.CompanionArchiveNetworking.register();
        com.nonid.affinity.service.CompanionDirectory.register();
        com.nonid.affinity.service.CreakingMemory.init();
        com.nonid.affinity.data.FreeTradeHeroEpoch.init();
        AffinityNetworking.registerPayloads();
        com.nonid.affinity.service.GameplayFeedback.register();
        com.nonid.affinity.net.CompanionInfoNetworking.register();
        AffinityNetworking.registerServerReceivers();
        com.nonid.affinity.net.GiftNetworking.register();
        com.nonid.affinity.service.CompanionInteractions.register();
        com.nonid.affinity.service.VillagerFreeTrades.registerCureWatcher();
        AffinityCommand.register();
        NonBridge.init();

        registerCreativeTab();
        registerServerLifecycle();
        com.nonid.affinity.service.CompanionDamageEvents.register();
        registerTick();
        registerTradeWindowTick();
        registerEntityLoad();

        LOGGER.info("[NoN Affinity] initialized (NeedsOfNature present: {})", NonBridge.isAvailable());
    }

    /** Reload the config file from disk. */
    public static void reloadConfig() {
        tryReloadConfig();
    }

    public static boolean tryReloadConfig() {
        AffinityConfig loaded = AffinityConfig.loadOrKeep(CONFIG);
        if (loaded == CONFIG) return false;
        applyConfig(loaded);
        LOGGER.info("[NoN Affinity] config reloaded.");
        return true;
    }

    /** Publish a fully edited, sanitized configuration instead of exposing partially edited fields. */
    public static void applyConfig(AffinityConfig config) {
        config.sanitize();
        CONFIG = config;
        LOG_VERBOSE = config.debugLogging;
    }

    /** A dedicated group; only its display stack uses the logo model. */
    private void registerCreativeTab() {
        Registry.register(Registries.ITEM_GROUP, Identifier.of(MOD_ID, "main"),
                FabricItemGroup.builder()
                        .displayName(Text.translatable("itemGroup.noncreatureaffinity.main"))
                        .icon(() -> {
                            ItemStack icon = NonAffinityItems.tokenStack();
                            icon.set(DataComponentTypes.ITEM_MODEL, Identifier.of(MOD_ID, "creative_tab_icon"));
                            return icon;
                        })
                        .entries((context, entries) -> {
                            entries.add(NonAffinityItems.tokenStack());
                            entries.add(NonAffinityItems.detectorStack());
                            entries.add(new ItemStack(NonAffinityItems.REUNION_WISH));
                        })
                        .build());
    }

    // ------------------------------------------------------------------
    // Server lifecycle
    // ------------------------------------------------------------------

    private void registerServerLifecycle() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, ignoredServer) -> AffinityNetworking.clearPlayerState(handler.getPlayer().getUuid()));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            RequestSystem.clear();
            FriendlyDefenderSystem.clear();
            reportDiagnostics();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CompanionEventLog.clear();
            CompanionCombatController.clear();
            com.nonid.affinity.service.CompanionDamageEvents.clear();
            com.nonid.affinity.service.CompanionMovementGoal.clear();
            NonBridge.clearRuntimeState();
            RequestSystem.clearRequestIds();
            com.nonid.affinity.service.CompanionFollowService.clear();
            com.nonid.affinity.service.CompanionInteractions.clearState();
            AffinityNetworking.clearAllState();
            RequestSystem.clear();
            FriendlyDefenderSystem.clear();
        });
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    /**
     * Startup self-check. The most common reason a mating request silently does nothing is a
     * missing NeedsOfNature content pack, because the base mod ships no animation definitions
     * at all. Saying so explicitly in the log saves a lot of guessing.
     */
    private static void reportDiagnostics() {
        if (!NonBridge.isAvailable()) {
            LOGGER.warn("[NoN Affinity] NeedsOfNature is not installed: mating interactions are disabled, "
                    + "affinity tracking and commands still work.");
            return;
        }
        int count = NonBridge.loadedDefinitionCount();
        if (count == 0) {
            LOGGER.warn("[NoN Affinity] NeedsOfNature loaded 0 animation definitions. Put a content pack "
                    + "(zip or folder) into <game dir>/needsofnature/ - without one no mating animation can start.");
            return;
        }
        LOGGER.info("[NoN Affinity] {} animation definition(s) loaded: {}",
                count, String.join(", ", NonBridge.loadedDefinitionIds()));
        if (!NonBridge.hasPlayerCreatureDefinition()) {
            LOGGER.warn("[NoN Affinity] none of the loaded definitions is a \"one player plus one creature\" "
                    + "animation, so the request key cannot match anything. Add an actor pair like "
                    + "player(passive) + mob(active).");
        }
    }

    // ------------------------------------------------------------------
    // Per-tick logic
    // ------------------------------------------------------------------

    private void registerTick() {
        CompanionEventLog.clear();
        CompanionCombatController.clear();
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            AffinityConfig config = CONFIG;
            long time = world.getTime();
            if (time % 20L == 0L) com.nonid.affinity.service.RelationshipBehavior.reconcile(world);

            // NoN originates invitations; retain cooldown and expiry maintenance.
            RequestSystem.tickCooldowns(world);
            RequestSystem.tickTimeouts(world, config);

            if (time % 20L == 0L) {
                // Live hostility observation for ATTACK mode.
                CompanionCombatController.scanHostility(world, config);
            }
            // The single target-decision entry point runs at the companion interval.
            if (time % Math.max(1, config.companionFollowIntervalTicks) == 0L) {
                CompanionCombatController.tick(world, config);
            }
            if (config.friendlyMobsDefendPlayer && time % 20L == 0L) {
                FriendlyDefenderSystem.tick(world, config);
            } else if (!config.friendlyMobsDefendPlayer && time % 20L == 0L) {
                // Disabled at runtime: the scan above no longer runs, so release here instead.
                // Only this service's own records are affected; COMPANION_ATTACK is untouched.
                FriendlyDefenderSystem.releaseAllOwned(world);
            }
            // Companion following runs on its own interval; it only ever asks the creature's own
            // navigation to move, so no Mixin or Goal injection is involved.
            if (config.companionFollowIntervalTicks > 0
                    && time % config.companionFollowIntervalTicks == 0L) {
                com.nonid.affinity.service.CompanionFollowService.tick(world, config);
            }
            if (config.affinityDecayPerDay > 0 && time % 24000L == 0L) {
                tickDailyDecay(world, config);
            }
        });
    }

    /**
     * The per-tick change check for open trade windows.
     *
     * <p>Registered once, on the server tick rather than per world: the record of open windows is per player, and a
     * player only has one window regardless of which dimension they are standing in. The check itself does nothing
     * at all while no window is open.
     */
    private void registerTradeWindowTick() {
        ServerTickEvents.END_SERVER_TICK.register(VillagerFreeTrades::tickOpenWindows);
    }

    private void tickDailyDecay(ServerWorld world, AffinityConfig config) {        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof AffinityHolder holder) || entity instanceof ServerPlayerEntity) {
                continue;
            }
            // Read without creating: a creature with no relations has nothing to decay, and creating data
            // for it would attach an empty record to every wild mob in the world. AffinityHolder documents
            // that split, and this loop previously violated it -- observed in a production run as
            // "entries: []" on an untamed wolf.
            CreatureAffinityData data = holder.peekAffinityData();
            if (data == null) {
                continue;
            }
            for (var entry : data.entries().entrySet()) {
                if (com.nonid.affinity.service.ReplayIsolation.protectsKey(entity.getUuid(), entry.getKey())) continue;
                PlayerAffinity relation = entry.getValue();
                if (com.nonid.affinity.service.RelationshipStages.friend(relation.getAffinity(), config)) {
                    continue;
                }
                int before = relation.getAffinity();
                relation.addAffinity(-config.affinityDecayPerDay, config.minAffinity, config.maxAffinity);
                java.util.UUID viewer;
                try {
                    viewer = java.util.UUID.fromString(entry.getKey());
                } catch (IllegalArgumentException invalid) {
                    LOGGER.warn("Cannot record stage history for invalid relationship UUID {}", entry.getKey());
                    continue;
                }
                com.nonid.affinity.service.CompanionArchive.stageChanged(entity, viewer, before, relation.getAffinity(), config, "daily_decay");
            }
        }
    }

    // ------------------------------------------------------------------
    // Entity load: bind offspring
    // ------------------------------------------------------------------

    private void registerEntityLoad() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            CompanionCombatController.forget(entity.getUuid());
            CompanionEventLog.forget(entity.getUuid());
            com.nonid.affinity.service.TargetRegistry.forgetEntity(entity.getUuid());
            if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
                com.nonid.affinity.service.CompanionFollowService.forget(mob);
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof MobEntity mob) || !mob.isBaby()) {
                return;
            }
            InheritanceService.tryInheritFromParents(world, mob, CONFIG);
        });
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /** Affinity data of an entity, or null when it does not carry any. */
    public static CreatureAffinityData dataOf(Entity entity) {
        return entity instanceof AffinityHolder holder ? holder.affinityData() : null;
    }

    /** Whether the entity is a tameable creature. */
    public static boolean isTameable(Entity entity) {
        return entity instanceof TameableEntity;
    }
}

