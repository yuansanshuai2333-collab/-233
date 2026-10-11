package com.nonid.affinity.non;

import com.nonid.affinity.NonCreatureAffinity;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * Only public entry point of the NeedsOfNature integration layer.
 *
 * <p>This class never references NON types directly; it only touches {@link NonIntegration},
 * which lives in this package. When NON is missing every method degrades safely to a
 * default value instead of throwing.
 */
public final class NonBridge {

    private static volatile boolean available;
    private static volatile boolean probed;

    private NonBridge() {
    }


    public static boolean isAvailable() {
        if (!probed) {
            synchronized (NonBridge.class) {
                if (!probed) {
                    available = FabricLoader.getInstance().isModLoaded("needsofnature");
                    probed = true;
                    if (available) {
                        NonCreatureAffinity.LOGGER.info(
                                "[NoN Affinity] NeedsOfNature detected; animation integration enabled.");
                    } else {
                        NonCreatureAffinity.LOGGER.warn(
                                "[NoN Affinity] NeedsOfNature not found; running in degraded mode.");
                    }
                }
            }
        }
        return available;
    }

    /** Register NON event listeners; no-op when NON is missing. */
    public static void init() {
        if (!isAvailable()) {
            return;
        }
        try {
            NonIntegration.registerListeners();
        } catch (Throwable throwable) {
            NonCreatureAffinity.LOGGER.error(
                    "[NoN Affinity] failed to register NeedsOfNature listeners: {}", throwable.toString());
        }
    }

    /** Heart continuity only; zero keeps NoN's native generation and is never permanently pinned. */
    public static int rememberedGender(LivingEntity entity) {
        return isAvailable() ? NonIntegration.rememberedGender(entity) : 0;
    }
    public static void restoreRememberedGender(LivingEntity entity, int mask) {
        if (isAvailable()) NonIntegration.restoreRememberedGender(entity, mask);
    }

    /** Whether the entity currently takes part in a NON animation. */
    public static boolean isActorBusy(ServerWorld world, LivingEntity entity) {
        if (!isAvailable() || world == null || entity == null) {
            return false;
        }
        try {
            return NonIntegration.isActorBusy(world, entity);
        } catch (Throwable throwable) {
            return false;
        }
    }

    /** True only for this live pair's running interaction, never a queued request. */
    public static boolean hasActiveInteraction(ServerWorld world, LivingEntity creature, ServerPlayerEntity player) {
        return isAvailable() && world != null && creature != null && player != null
                && NonIntegration.hasActiveInteraction(world, creature, player);
    }

    /** Arousal value from NON; 0 when NON is missing or the entity has none. */
    public static int getEnergy(LivingEntity entity) {
        if (!isAvailable() || entity == null) {
            return 0;
        }
        try {
            return NonIntegration.getEnergy(entity);
        } catch (Throwable throwable) {
            return 0;
        }
    }

    /** One read-only snapshot. Zero means unknown, never inferred low desire. */
    public static int requestDesireStage(LivingEntity entity) {
        if (!isAvailable() || entity == null) return 0;
        return requestEnergy(entity).stage();
    }

    public static com.nonid.affinity.service.RequestEnergy requestEnergy(LivingEntity entity) {
        if (!isAvailable() || entity == null) return com.nonid.affinity.service.RequestEnergy.UNKNOWN;
        return NonIntegration.requestEnergy(entity);
    }

    public static int getMaxEnergy(LivingEntity entity) {
        if (!isAvailable() || entity == null) {
            return 0;
        }
        try {
            return NonIntegration.getMaxEnergy(entity);
        } catch (Throwable throwable) {
            return 0;
        }
    }

    public static boolean hasEnergy(LivingEntity entity) {
        if (!isAvailable() || entity == null) {
            return false;
        }
        try {
            return NonIntegration.hasEnergy(entity);
        } catch (Throwable throwable) {
            return false;
        }
    }


    /** Player and creature mating started by the player. */
    public static boolean startVoluntaryMating(ServerWorld world, ServerPlayerEntity player, LivingEntity creature) {
        if (!isAvailable()) {
            com.nonid.affinity.service.InteractionStartFeedback.report(
                    com.nonid.affinity.service.InteractionStartFeedback.Reason.UNAVAILABLE);
            return false;
        }
        try {
            boolean started = NonIntegration.startMating(world, player, creature, false);
            if (!started) {
                var refusal = NonIntegration.lastRefusal();
                com.nonid.affinity.service.InteractionStartFeedback.reportFallback(
                        refusal == null ? null : refusal.name());
            }
            return started;
        } catch (Throwable throwable) {
            com.nonid.affinity.service.InteractionStartFeedback.report(
                    com.nonid.affinity.service.InteractionStartFeedback.Reason.ENGINE_ERROR);
            NonCreatureAffinity.LOGGER.error("[NoN Affinity] failed to start mating: {}", throwable.toString());
            return false;
        }
    }

    /**
     * Why the most recent {@link #startVoluntaryMating} did not start, or null when it did.
     *
     * <p>Exposed so the caller can tell the player something specific. A creature with no usable animation
     * at all and one whose conditions are momentarily unmet are different situations for the player, and
     * only the server can distinguish them.
     */
    @Nullable
    public static NonIntegration.MatingRefusal lastMatingRefusal() {
        if (!isAvailable()) {
            return null;
        }
        try {
            return NonIntegration.lastRefusal();
        } catch (Throwable throwable) {
            return null;
        }
    }

    /** Clear the stored refusal so a stale reason is never reported for a later attempt. */
    public static void clearMatingRefusal() {
        if (!isAvailable()) {
            return;
        }
        try {
            NonIntegration.clearRefusal();
        } catch (Throwable ignored) {
            // Degraded mode.
        }
    }

    /**
     * Note that the player broke out of a mating.
     *
     * <p>Exposed through this facade because the integration class is package-private. Returns whether any
     * running animation of that player and creature was marked -- a signal only, not the settlement decision:
     * NeedsOfNature resolves an attack escape after a delay, so the stop event can arrive first, and a stop
     * that cannot show a natural end is refused whether or not this mark arrived in time.
     */
    public static boolean markEscaped(ServerPlayerEntity player, LivingEntity creature) {
        if (!isAvailable()) {
            return false;
        }
        try {
            return NonIntegration.markEscaped(player, creature);
        } catch (Throwable throwable) {
            NonCreatureAffinity.LOGGER.error("[NoN Affinity] failed to mark escape: {}",
                    throwable.toString());
            return false;
        }
    }

    public static boolean escapeCostsAffinity(ServerPlayerEntity player, LivingEntity creature) {
        return isAvailable() && NonIntegration.escapeCostsAffinity(player, creature);
    }
    public static com.nonid.affinity.service.EscapeAttackSettlement prepareEscapeFeedback(
            ServerPlayerEntity player, LivingEntity creature) {
        return isAvailable() ? NonIntegration.prepareEscapeFeedback(player, creature) : null;
    }

    /** Identity-aware native struggle; never charge another participant for this player's escape. */
    public static void markPlayerEscapeForInstance(java.util.UUID instanceId, java.util.UUID playerId) {
        if (isAvailable()) NonIntegration.markPlayerEscapeForInstance(instanceId, playerId);
    }

    /** A NON struggle was won for this instance: record it as the player's escape. */
    public static void markPlayerEscapeForInstance(java.util.UUID instanceId) {
        if (!isAvailable()) {
            return;
        }
        try {
            NonIntegration.markPlayerEscapeForInstance(instanceId);
        } catch (Throwable throwable) {
            NonCreatureAffinity.LOGGER.error("[NoN Affinity] failed to mark the escape of {}: {}",
                    instanceId, throwable.toString());
        }
    }

    /** The player pressed the break-free key; the server decides whether they are inside a forced mating. */
    public static boolean openNativeSelection(ServerPlayerEntity player, LivingEntity target) {
        if (!isAvailable()) return false;
        try {
            com.nonid.affinity.non.compat.NativeSelection.open(player, target, () ->
                    com.nonid.NonDebugAnimationSelectionSystem.requestDirect(player,
                            java.util.List.of(player.getId(), target.getId()),
                            com.nonid.api.animation.NonAnimationDamageBehavior.STOP_ON_DAMAGE,
                            false, target.getId()));
            return true;
        } catch (RuntimeException | LinkageError failure) {
            NonCreatureAffinity.LOGGER.error("[NoN Affinity] native selection unavailable", failure);
            return false;
        }
    }

    public static void breakFree(ServerPlayerEntity player) {
        if (!isAvailable()) {
            return;
        }
        try {
            NonIntegration.breakFree(player);
        } catch (Throwable throwable) {
            NonCreatureAffinity.LOGGER.error("[NoN Affinity] failed to break free: {}",
                    throwable.toString());
        }
    }

    /** Forced mating after a rejected request. */
    public static boolean startForcedMating(ServerWorld world, ServerPlayerEntity player, LivingEntity creature) {
        if (!isAvailable()) {
            return false;
        }
        try {
            return NonIntegration.startForcedMating(world, player, creature);
        } catch (Throwable throwable) {
            NonCreatureAffinity.LOGGER.error("[NoN Affinity] failed to start forced mating: {}", throwable.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Request queue
    // ------------------------------------------------------------------

    public static void putRequest(UUID mobUuid, UUID playerUuid, long expireTick) {
        if (!isAvailable()) {
            return;
        }
        try {
            NonIntegration.putRequest(mobUuid, new NonIntegration.PendingRequest(mobUuid, playerUuid, expireTick));
        } catch (Throwable ignored) {
            // Degraded mode: ignore.
        }
    }

    public static boolean hasRequest(UUID mobUuid) {
        if (!isAvailable()) {
            return false;
        }
        try {
            return NonIntegration.getRequest(mobUuid) != null;
        } catch (Throwable throwable) {
            return false;
        }
    }

    @Nullable
    public static UUID requestPlayer(UUID mobUuid) {
        if (!isAvailable()) {
            return null;
        }
        try {
            NonIntegration.PendingRequest request = NonIntegration.getRequest(mobUuid);
            return request == null ? null : request.playerUuid();
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static long requestExpireTick(UUID mobUuid) {
        if (!isAvailable()) {
            return -1L;
        }
        try {
            NonIntegration.PendingRequest request = NonIntegration.getRequest(mobUuid);
            return request == null ? -1L : request.expireTick();
        } catch (Throwable throwable) {
            return -1L;
        }
    }

    @Nullable
    public static UUID removeRequest(UUID mobUuid) {
        if (!isAvailable()) {
            return null;
        }
        try {
            NonIntegration.PendingRequest request = NonIntegration.removeRequest(mobUuid);
            return request == null ? null : request.playerUuid();
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static Set<UUID> pendingMobIds() {
        if (!isAvailable()) {
            return Set.of();
        }
        try {
            return NonIntegration.pendingMobIds();
        } catch (Throwable throwable) {
            return Set.of();
        }
    }

    /** Number of animation definitions NON has loaded (0 means no content pack). */
    public static int loadedDefinitionCount() {
        if (!isAvailable()) {
            return 0;
        }
        try {
            return NonIntegration.loadedDefinitionCount();
        } catch (Throwable throwable) {
            return 0;
        }
    }

    /** Ids of the loaded animation definitions, for diagnostics. */
    public static java.util.List<String> loadedDefinitionIds() {
        if (!isAvailable()) {
            return java.util.List.of();
        }
        try {
            return NonIntegration.loadedDefinitionIds();
        } catch (Throwable throwable) {
            return java.util.List.of();
        }
    }

    /** Whether at least one loaded definition has the "player plus one creature" shape we need. */
    public static boolean hasPlayerCreatureDefinition() {
        if (!isAvailable()) {
            return false;
        }
        try {
            return NonIntegration.hasPlayerCreatureDefinition();
        } catch (Throwable throwable) {
            return false;
        }
    }

    /**
     * Whether ANY animation matches this specific creature and player, whether or not it could start right now.
     *
     * <p>This is the "there is an interaction for this species at all" question, which is not the same as
     * {@link #hasPlayerCreatureDefinition()} (that one only says the installed content pack knows the player+creature
     * shape somewhere) and not the same as start eligibility (a real animation that cannot start from where the pair
     * is standing is still an interaction the creature may ask for).
     *
     * <p>Deliberately independent of position so the answer does not flicker as the pair walks around: a creature
     * whose species has no animation must never raise a request, while one that has an animation but is standing
     * badly may still ask -- and the player is then told why accepting did not work.
     */
    public static boolean hasMatchingAnimation(ServerWorld world, ServerPlayerEntity player,
                                               LivingEntity creature) {
        if (!isAvailable()) {
            return false;
        }
        try {
            for (NonIntegration.CandidateInfo info : NonIntegration.listCandidates(world, player, creature)) {
                if (info != null && info.id() != null) {
                    return true;
                }
            }
            return false;
        } catch (Throwable throwable) {
            return false;
        }
    }

    /** Drop any pending request for this creature (used before an explicit player start). */
    public static void abortRequest(UUID mobUuid) {
        if (!isAvailable()) {
            return;
        }
        try {
            NonIntegration.abortRequest(mobUuid);
        } catch (Throwable ignored) {
            // Degraded mode.
        }
    }

    /** Mark a creature as waiting for the player's answer to its request. */
    public static void markAwaitingAnswer(UUID mobUuid) {
        if (!isAvailable()) {
            return;
        }
        try {
            NonIntegration.markAwaitingAnswer(mobUuid);
        } catch (Throwable ignored) {
            // Degraded mode.
        }
    }

    /** Clear the waiting flag for a creature. */
    public static void clearAwaitingAnswer(UUID mobUuid) {
        if (!isAvailable()) {
            return;
        }
        try {
            NonIntegration.clearAwaitingAnswer(mobUuid);
        } catch (Throwable ignored) {
            // Degraded mode.
        }
    }

    /** Whether the creature is still waiting for the player's answer. */
    public static boolean isAwaitingAnswer(UUID mobUuid) {
        if (!isAvailable()) {
            return false;
        }
        try {
            return NonIntegration.isAwaitingAnswer(mobUuid);
        } catch (Throwable throwable) {
            return false;
        }
    }

    /** Every animation matching this player and creature. */
    public static java.util.List<String> candidateIds(ServerWorld world, ServerPlayerEntity player,
                                                      LivingEntity creature) {
        if (!isAvailable()) {
            return java.util.List.of();
        }
        try {
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (NonIntegration.CandidateInfo info : NonIntegration.listCandidates(world, player, creature)) {
                ids.add(info.id() + "|" + info.displayName() + "|" + info.startEligible());
            }
            return ids;
        } catch (Throwable throwable) {
            return java.util.List.of();
        }
    }

    /** Start one animation chosen by the player. */
    public static boolean startChosen(ServerWorld world, ServerPlayerEntity player, LivingEntity creature,
                                      net.minecraft.util.Identifier animationId) {
        if (!isAvailable()) {
            com.nonid.affinity.service.InteractionStartFeedback.report(
                    com.nonid.affinity.service.InteractionStartFeedback.Reason.UNAVAILABLE);
            return false;
        }
        try {
            return NonIntegration.startChosen(world, player, creature, animationId);
        } catch (Throwable throwable) {
            com.nonid.affinity.service.InteractionStartFeedback.report(
                    com.nonid.affinity.service.InteractionStartFeedback.Reason.ENGINE_ERROR);
            NonCreatureAffinity.LOGGER.error("[NoN Affinity] failed to start chosen animation: {}",
                    throwable.toString());
            return false;
        }
    }

    /** Clear runtime state when the server stops. */
    public static void clearRuntimeState() {
        if (!isAvailable()) {
            return;
        }
        try {
            NonIntegration.clear();
        } catch (Throwable ignored) {
            // Ignore.
        }
    }
}
