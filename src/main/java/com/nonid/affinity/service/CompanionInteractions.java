package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.data.CompanionMode;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

/**
 * Sneak + empty main-hand right-click cycles follow, wander, wait and attack.
 *
 * <p>Handling rules, each of which exists to avoid a specific failure:
 * <ul>
 *   <li>main hand only, so the off-hand callback cannot toggle twice for one click;</li>
 *   <li>server side only, so the client cannot fake the outcome;</li>
 *   <li>an empty hand only, so normal item use is untouched;</li>
 *   <li>SUCCESS is returned after a real change, which consumes the click so the same right-click
 *       does not also start riding, sitting or another mod's menu;</li>
 *   <li>anything that is not a valid companion falls through as PASS, leaving vanilla behaviour
 *       exactly as it was.</li>
 * </ul>
 */
public final class CompanionInteractions {

    /**
     * Successful switches already applied, keyed by (world, tick, player, target).
     *
     * <p>Measured evidence: one physical click produces two applied switches in the SAME tick --
     * once through the entry point that carries a hit result and once through the one that does not.
     * Both are legitimate entry points, so rather than guess which to ignore, the second callback
     * for the same key is dropped.
     *
     * <p>Keyed per interaction rather than held in a single set of fields: with one record, an
     * interleaved sequence (A/X ok, B/Y ok, then A/X's duplicate) overwrote the entry and let X flip
     * twice.
     *
     * <p><b>Exact limits</b> (stated precisely, because the earlier comment over-claimed):
     * <ul>
     *   <li>two entry points landing in DIFFERENT ticks are still not merged;</li>
     *   <li>a held right click that keeps producing new ticks is not stopped by this;</li>
     *   <li>two genuinely independent clicks within the SAME tick cannot be told apart and are
     *       merged.</li>
     * </ul>
     * It is a short-term guard for the measured defect, not a substitute for fixing the input path.
     */
    private record SwitchKey(String dimension, int tick, java.util.UUID player, java.util.UUID target) {}

    private static final java.util.Map<SwitchKey, Integer> RECENT_SWITCHES =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** How many ticks a record stays valid. One tick: the duplicate arrives in the same tick. */
    private static final int RETENTION_TICKS = 1;

    /** How many duplicate callbacks have been dropped, so the guard's effect is measurable. */
    private static long suppressedDuplicates;

    /** Drop records that are older than the retention window, so the map cannot grow without bound. */
    private static void prune(int now) {
        RECENT_SWITCHES.entrySet().removeIf(entry -> {
            int elapsed = now - entry.getValue();
            return elapsed < 0 || elapsed > RETENTION_TICKS;
        });
    }

    /** All worlds use the server clock. A different dimension cannot expire this tick's guard. */
    static boolean alreadySwitched(String dimension, int serverTick,
                                   java.util.UUID player, java.util.UUID target) {
        prune(serverTick);
        return RECENT_SWITCHES.containsKey(new SwitchKey(dimension, serverTick, player, target));
    }

    static void rememberSwitch(String dimension, int serverTick,
                               java.util.UUID player, java.util.UUID target) {
        RECENT_SWITCHES.put(new SwitchKey(dimension, serverTick, player, target), serverTick);
    }

    /** Forget all records; called when the server stops or a world unloads. */
    public static void clearState() {
        RECENT_SWITCHES.clear();
    }

    private CompanionInteractions() {
    }

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            // Server authoritative: the client result is not trusted.
            if (!(world instanceof ServerWorld)) {
                return ActionResult.PASS;
            }
            // Main hand only: the off-hand callback would otherwise toggle a second time.
            if (hand != Hand.MAIN_HAND) {
                return ActionResult.PASS;
            }
            // Empty hand only, so ordinary item interactions are preserved.
            if (!player.getStackInHand(hand).isEmpty()) {
                return ActionResult.PASS;
            }
            if (!(player instanceof ServerPlayerEntity serverPlayer)
                    || !(entity instanceof MobEntity mob)) {
                return ActionResult.PASS;
            }
            // Spectators must not change companion state.
            if (serverPlayer.isSpectator()) {
                return ActionResult.PASS;
            }
            if (!mob.isAlive()) {
                return ActionResult.PASS;
            }
            // Sneaking is required. A plain right click therefore keeps riding, sitting and every
            // other vanilla or modded interaction; only Shift + empty hand switches the mode.
            // Sneak state is used rather than the raw Shift key so a rebound key still works.
            if (!serverPlayer.isSneaking()) {
                return ActionResult.PASS;
            }

            // Diagnostic: the reported "dense switching" needs to be traced to a cause. This records
            // every callback that reaches the switch, so a single physical click producing two
            // toggles can be seen directly instead of guessed at.
            if (NonCreatureAffinity.getConfig().debugLogging) {
                String modeBefore = CompanionService.modeOf(mob).name();
                NonCreatureAffinity.LOGGER.info(
                        "[NoN Affinity] companion interact tick={} player={} target={} hand={} "
                                + "sneaking={} hitResult={} modeBefore={}",
                        world.getTime(), serverPlayer.getUuid(), mob.getUuid(), hand,
                        serverPlayer.isSneaking(), hitResult != null, modeBefore);
            }

            // Drop the second callback of the same physical click. The key is per interaction, so
            // an interleaved switch on another creature cannot displace this record.
            int now = ((ServerWorld) world).getServer().getTicks();
            String dimension = world.getRegistryKey().getValue().toString();
            if (alreadySwitched(dimension, now, serverPlayer.getUuid(), mob.getUuid())) {
                suppressedDuplicates++;
                NonCreatureAffinity.LOGGER.info(
                        "[NoN Affinity] companion interact SUPPRESSED duplicate tick={} target={} "
                                + "hitResult={} totalSuppressed={}",
                        now, mob.getUuid(), hitResult != null, suppressedDuplicates);
                // The click has already been handled; consume it without switching again.
                return ActionResult.SUCCESS;
            }

            if (CreakingMemory.bound(mob)) {
                com.nonid.affinity.service.GameplayFeedback.send(serverPlayer,Text.translatable("message.noncreatureaffinity.creaking_home"), true);
                return ActionResult.PASS;
            }
            CompanionService.Result result =
                    CompanionService.toggle(serverPlayer, mob, NonCreatureAffinity.getConfig());
            if (result == CompanionService.Result.BOUND || result == CompanionService.Result.TOGGLED) {
                CompanionCombatController.onModeCommand(mob);
                // Only a successful change arms the guard, so a refused interaction never blocks a
                // later legitimate one.
                rememberSwitch(dimension, now, serverPlayer.getUuid(), mob.getUuid());
                if (NonCreatureAffinity.getConfig().debugLogging) {
                    NonCreatureAffinity.LOGGER.info(
                            "[NoN Affinity] companion interact applied tick={} target={} modeAfter={}",
                            now, mob.getUuid(), CompanionService.modeOf(mob).name());
                }
                if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
                        serverPlayer, com.nonid.affinity.net.CompanionModeFeedbackPayload.ID)) {
                    CompanionMode mode = CompanionService.modeOf(mob);
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer,
                            new com.nonid.affinity.net.CompanionModeFeedbackPayload(
                                    java.util.UUID.randomUUID(),
                                    world.getRegistryKey().getValue(), mob.getUuid(), mob.getName(),
                                    Text.translatable(mode.translationKey())));
                }
            }
            switch (result) {
                case BOUND, TOGGLED -> {
                    report(serverPlayer, mob);
                    // Consume the click: the same right-click must not also ride/sit/open a menu.
                    return ActionResult.SUCCESS;
                }
                case NOT_OWNER -> {
                    com.nonid.affinity.service.GameplayFeedback.send(serverPlayer,Text.translatable(
                            "message.noncreatureaffinity.companion_not_owner", mob.getName())
                            .formatted(Formatting.GRAY), true);
                    // PASS, not SUCCESS: the intent is only to forbid changing ownership, so the
                    // original interaction (riding, sitting, another mod's menu) must still run.
                    return ActionResult.PASS;
                }
                default -> {
                    return ActionResult.PASS;
                }
            }
        });
    }

    private static void report(ServerPlayerEntity player, MobEntity mob) {
        CompanionMode mode = CompanionService.modeOf(mob);
        // Same mapping the panel uses, so every mode reports its own name.
        Text suffix = Text.translatable(mode.translationKey());
        com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable("message.noncreatureaffinity.companion_mode",
                mob.getName(), suffix).formatted(Formatting.AQUA), true);
    }
}
