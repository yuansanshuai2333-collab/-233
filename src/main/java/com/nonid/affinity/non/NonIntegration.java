package com.nonid.affinity.non;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.service.AffinityService;
import com.nonid.affinity.service.RequestSystem;
import com.nonid.affinity.service.ReplayIsolation;
import com.nonid.affinity.service.InteractionStartFeedback;
import com.nonid.affinity.non.compat.DebugStartScope;
import com.nonid.api.NonEnergySnapshot;
import com.nonid.api.NonEntityApi;
import com.nonid.api.animation.NonAnimationActor;
import com.nonid.api.animation.NonAnimationApi;
import com.nonid.api.animation.NonAnimationCandidate;
import com.nonid.api.animation.NonAnimationDamageBehavior;
import com.nonid.api.animation.NonAnimationDefinition;
import com.nonid.api.animation.NonAnimationEvents;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * Actual NeedsOfNature integration.
 *
 * <p>This class resolves NON types directly, so it must only be loaded after NON is known
 * to be present. Everything outside this package goes through {@link NonBridge}.
 */
final class NonIntegration {

    /**
     * Per-instance state of every mating animation, whoever started it.
     *
     * <p>Replaces the previous pair of structures (a record of animations this module started, plus a set of
     * escaped instance ids). Those could not answer the questions settlement actually needs, and the gaps were
     * exactly the reported defects: an animation nobody recorded was settled as a completion, an escape could
     * only mark animations this module had started, and a duplicate stop fell through to the autonomous path
     * and paid a second time.
     */
    private static final com.nonid.affinity.service.GroupMatingSessions SESSIONS =
            new com.nonid.affinity.service.GroupMatingSessions();
    private static final com.nonid.affinity.service.MatingSessionLifecycle<net.minecraft.server.MinecraftServer, ServerWorld> LIFECYCLE =
            new com.nonid.affinity.service.MatingSessionLifecycle<>(SESSIONS,
                    (server, world) -> world.getServer() == server && server.getWorld(world.getRegistryKey()) == world,
                    (world, id) -> NonAnimationApi.getSession(world, id) != null);

    private static final Map<UUID, PendingRequest> PENDING_REQUESTS = new ConcurrentHashMap<>();

    private static final Identifier PLAYER_ENTITY_ID = Identifier.of("minecraft", "player");

    /**
     * Creatures that asked the player and are still waiting for an answer.
     *
     * <p>NeedsOfNature starts its own animations from the same code path it uses for player-driven
     * ones, and it passes no requester, so an outside addon cannot tell "the creature asked" from
     * "the creature is forcing itself on the player". Blocking the start while a request is pending
     * is therefore the only reliable way to make the creature wait for the player's answer.
     */
    private static final Set<UUID> AWAITING_ANSWER = ConcurrentHashMap.newKeySet();

    private static boolean listenersRegistered;

    private NonIntegration() {
    }


    static int rememberedGender(LivingEntity entity) {
        if (!(entity instanceof com.nonid.GenderHolder holder)) return 0;
        int mask = holder.getGenderMask();
        return mask >= 1 && mask <= 3 ? mask : 0;
    }
    static void restoreRememberedGender(LivingEntity entity, int mask) {
        if (mask < 1 || mask > 3 || !(entity instanceof com.nonid.GenderHolder holder)) return;
        entity.removeCommandTag("gender.male");
        entity.removeCommandTag("gender.female");
        if ((mask & 1) != 0) entity.addCommandTag("gender.male");
        if ((mask & 2) != 0) entity.addCommandTag("gender.female");
        holder.setGenderMask(mask);
    }

    // ------------------------------------------------------------------
    // State access
    // ------------------------------------------------------------------

    static boolean isActorBusy(ServerWorld world, LivingEntity entity) {
        return NonAnimationApi.isActorPendingOrActive(world, entity.getUuid());
    }

    static int getEnergy(LivingEntity entity) {
        NonEnergySnapshot snapshot = NonEntityApi.getEnergy(entity);
        return snapshot == null ? 0 : snapshot.energy();
    }

    static com.nonid.affinity.service.RequestEnergy requestEnergy(LivingEntity entity) {
        NonEnergySnapshot snapshot = NonEntityApi.getEnergy(entity);
        return snapshot == null ? com.nonid.affinity.service.RequestEnergy.UNKNOWN
                : new com.nonid.affinity.service.RequestEnergy(snapshot.energy(),snapshot.maxEnergy(),snapshot.initialized());
    }
    static int getMaxEnergy(LivingEntity entity) {
        NonEnergySnapshot snapshot = NonEntityApi.getEnergy(entity);
        return snapshot == null ? 0 : snapshot.maxEnergy();
    }

    static boolean hasEnergy(LivingEntity entity) {
        return NonEntityApi.hasEnergy(entity);
    }


    /** Number of animation definitions currently loaded (0 usually means "no content pack"). */
    static int loadedDefinitionCount() {
        return NonAnimationApi.getLoadedDefinitions().size();
    }

    /** Ids of the loaded definitions, for the startup self-check. */
    static List<String> loadedDefinitionIds() {
        return NonAnimationApi.getLoadedDefinitions().stream()
                .map(definition -> definition.id().toString())
                .toList();
    }

    /** Whether any loaded definition is a "player plus one creature" mating shape. */
    static boolean hasPlayerCreatureDefinition() {
        for (NonAnimationDefinition definition : NonAnimationApi.getLoadedDefinitions()) {
            if (isPlayerCreatureDefinition(definition)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Drop a pending request for this creature.
     *
     * <p>Needed when the player explicitly presses the request key: their own action must take
     * priority over the creature's outstanding request, otherwise the waiting gate would swallow
     * the animation and the key would appear to do nothing.
     */
    static void abortRequest(UUID mobUuid) {
        if (mobUuid == null) {
            return;
        }
        PENDING_REQUESTS.remove(mobUuid);
        AWAITING_ANSWER.remove(mobUuid);
    }

    /** Mark a creature as waiting for the player's answer. */
    static void markAwaitingAnswer(UUID mobUuid) {
        if (mobUuid != null) {
            AWAITING_ANSWER.add(mobUuid);
        }
    }

    /** Clear the waiting flag, e.g. once the player answered or the request expired. */
    static void clearAwaitingAnswer(UUID mobUuid) {
        if (mobUuid != null) {
            AWAITING_ANSWER.remove(mobUuid);
        }
    }

    static boolean isAwaitingAnswer(UUID mobUuid) {
        return mobUuid != null && AWAITING_ANSWER.contains(mobUuid);
    }

    // ------------------------------------------------------------------
    // Starting mating
    // ------------------------------------------------------------------

    /**
     * Why the last mating attempt did not start.
     *
     * <p>Exists because "it failed" is not actionable: the player-facing message for a creature with no
     * usable animation at all must differ from "an animation exists but its conditions are unmet right
     * now", and the two were previously indistinguishable outside the log.
     */
    public enum MatingRefusal {
        /** A usable animation ran. */
        NONE,
        /** No animation definition matches this player and creature at all. */
        NO_ANIMATION,
        /** Definitions match, but none is startable right now (space, blocks, water, ...). */
        CONDITIONS,
        /** One of the two participants is already occupied. */
        BUSY,
        /** The animation engine threw while starting. */
        ERROR
    }

    /**
     * The refusal of the most recent attempt.
     *
     * <p>A field rather than a return value because the bridge API returns a boolean that several callers
     * depend on. Written and read on the server thread only, so no synchronisation is needed.
     */
    @Nullable
    private static MatingRefusal lastRefusal;

    /** The refusal of the most recent attempt, or null when there has not been one. */
    @Nullable
    static MatingRefusal lastRefusal() {
        return lastRefusal;
    }

    /** Clear the stored refusal, so a stale reason cannot be reported for a later attempt. */
    static void clearRefusal() {
        lastRefusal = null;
    }

    /**
     * Start one mating animation between the player and the creature.
     *
     * @param forced true when triggered after a rejected request
     * @return whether the animation started
     */
    private static boolean resumeNativeIntent(com.nonid.affinity.non.compat.NativeStartIntent intent,
                                             ServerWorld world, ServerPlayerEntity player, MobEntity creature) {
        InteractionStartFeedback.observeEngine(true);
        try {
            UUID instance = intent.resume(world, player, creature);
            if (instance == null) {
                InteractionStartFeedback.reportFallback("CONDITIONS");
                return false;
            }
            // This is a resumed NoN-owned request. Observe it as native; do not promote it to a NaR-owned
            // session, because that changes only NaR settlement classification and can interfere with NoN's
            // own completion/peak bookkeeping.
            return true;
        } catch (RuntimeException error) {
            InteractionStartFeedback.report(InteractionStartFeedback.Reason.ENGINE_ERROR);
            NonCreatureAffinity.LOGGER.error("[NoN Affinity] native request resume failed", error);
            return false;
        } finally {
            InteractionStartFeedback.observeEngine(false);
        }
    }

    static boolean startMating(ServerWorld world, ServerPlayerEntity player, LivingEntity creature,
                               boolean forced) {
        lastRefusal = null;
        if (world == null || player == null || creature == null) {
            lastRefusal = MatingRefusal.CONDITIONS;
            return false;
        }
        if (isActorBusy(world, player) || isActorBusy(world, creature)) {
            // Reported as busy rather than as a missing animation, so the player is not told the creature
            // has no animations when it is merely occupied.
            lastRefusal = MatingRefusal.BUSY;
            return false;
        }
        List<Entity> actors = new ArrayList<>(2);
        actors.add(player);
        actors.add(creature);

        // A large content pack offers many definitions for the same actor pair, and only some
        // of them are startable right now (space, nearby blocks, water, ...). Asking the matcher
        // for a single "best" pick made the key fail intermittently, so walk all candidates and
        // start the first one that really works.
        List<NonAnimationCandidate> candidates = NonAnimationApi.findCandidates(
                world, actors, NonAnimationApi.MatchOptions.defaults());
        // Try eligible definitions first, preserving their match order. Retain ineligible definitions so
        // the engine can validate the actual start and report its concrete refusal (no guessed reason).
        candidates = new ArrayList<>(candidates);
        candidates.sort(java.util.Comparator.comparing(candidate -> !candidate.startEligible()));
        if (candidates.isEmpty()) {
            lastRefusal = MatingRefusal.NO_ANIMATION;
            NoNLog.warn("no animation definition matches {} + {} (loaded definitions: {})",
                    player.getName().getString(), creature.getName().getString(), loadedDefinitionCount());
            return false;
        }

        int shapeRejected = 0;
        for (NonAnimationCandidate candidate : candidates) {
            NonAnimationDefinition definition = candidate.definition();
            if (!isPlayerCreatureDefinition(definition)) {
                shapeRejected++;
                continue;
            }
            UUID instanceId;
            InteractionStartFeedback.observeEngine(true);
            try {
                instanceId = startCandidate(world, player, definition, actors, candidate.actorKeys(), forced);
            } catch (RuntimeException failure) {
                // No temporary state of ours exists yet at this point, so the engine's own refusal is
                // recorded rather than swallowed: an unexpected fault must stay visible.
                lastRefusal = MatingRefusal.ERROR;
                NoNLog.warn("starting {} threw: {}", definition.id(), failure.toString());
                return false;
            } finally {
                InteractionStartFeedback.observeEngine(false);
            }
            if (instanceId == null) {
                continue;
            }
            LIFECYCLE.recordOurStart(world, instanceId, player.getUuid(), creature.getUuid(), forced,
                    world.getTime());
            NoNLog.info("started mating animation {} (instance {}, forced={})",
                    definition.id(), instanceId, forced);
            return true;
        }
        // Candidates existed for the pair, but none could actually be started. Attributed by cause: a
        // shape mismatch means there is no usable animation, anything else is a condition problem.
        lastRefusal = candidates.size() == shapeRejected
                ? MatingRefusal.NO_ANIMATION : MatingRefusal.CONDITIONS;
        NoNLog.warn("no startable definition for {} + {} ({} candidate(s), {} rejected for actor shape)",
                player.getName().getString(), creature.getName().getString(),
                candidates.size(), shapeRejected);
        return false;
    }

    /**
     * Hand one candidate to the animation engine.
     *
     * <p>A FORCED mating is normally started as an attack interaction so the player gets NON's own A/D struggle
     * screen with real escape progress: the screen is only sent for animations started with an attack context,
     * and it is the only way out, because attacks cannot be made while an animation runs. The plain start is
     * used for voluntary matings, when the option is off, and whenever the attack start is not available on
     * this NON build (which is reported rather than hidden).
     */
    @Nullable
    private static UUID startCandidate(ServerWorld world, ServerPlayerEntity player,
                                       NonAnimationDefinition definition, List<Entity> actors,
                                       List<String> actorKeys, boolean forced) {
        if (forced && NonCreatureAffinity.getConfig().nonStruggleScreen) {
            UUID escapable = startEscapableForcedMating(world, player, definition, actors, actorKeys);
            if (escapable != null) {
                return escapable;
            }
        }
        // Plain start. Damage stops the animation, which matters for a voluntary mating the player wants to
        // abandon; a forced mating falls back to this module's own break-free key when the attack start failed.
        return NonAnimationApi.start(
                world,
                definition.id(),
                actors,
                actorKeys,
                new NonAnimationApi.StartOptions(
                        NonAnimationDamageBehavior.STOP_ON_DAMAGE,
                        false,
                        null,
                        player,
                        Map.of("nonaffinity", forced ? "forced" : "voluntary"),
                        false,
                        false));
    }

    /**
     * Start a forced mating so that NON treats it as a mob attack, giving the player its A/D struggle screen and
     * real escape progress.
     *
     * <p><b>Which attack shape.</b> NON distinguishes two of them by the session data alone:
     * <ul>
     *   <li>a <b>player</b> attack carries a {@code NonAnimationSessionData.PlayerAttack}
     *       ({@code isPlayerAttackContext(...) == true});</li>
     *   <li>a <b>creature</b> attack is plain attack mode with NO {@code PlayerAttack}. NON's own mob attacks are
     *       built exactly that way ({@code NeedsOfNature.buildModeContext(true)}, which is
     *       {@code NonAnimationSessionData.attack()}), and {@code NonGiveUpSystem} recognises the case as
     *       {@code attack && !isPlayerAttackContext(...) && hasMobActor(...)}.</li>
     * </ul>
     * This uses the second one. The first was tried in {@code rc.39} and could never work: NON's
     * {@code PlayerAttack} constructor rejects a null {@code finishOwnerUuid}, so the start threw
     * {@link IllegalArgumentException} straight into the fallback below (the struggle screen was never reached),
     * and a non-null value would have made NON believe a <em>player</em> was attacking the victim.
     *
     * <p>The escape itself is not a contest: {@code NonPlayerAttackSystem.onAnimationStarted} only builds one
     * when an attacker uuid resolves to an online <em>player</em>, and ours is the creature. Without a contest
     * NON sends the escape control with {@code PvpRole.NONE}, the client accumulates the mash progress locally
     * and asks the server to stop the animation, which is what reaches {@code completeAttackEscape}.
     *
     * <p>Returns null when the attack start is unavailable or throws, so the caller falls back instead of
     * losing the interaction. Everything here is version-gated by the optional integration's mixin plugin; a
     * NON build without these members is reported at startup and never reaches this code.
     */
    @Nullable
    private static UUID startEscapableForcedMating(ServerWorld world, ServerPlayerEntity player,
                                                   NonAnimationDefinition definition, List<Entity> actors,
                                                   List<String> actorKeys) {
        try {
            // NON's OWN stage list for this animation, taken from the definition NON itself would start
            // (AnimationDefinitions.getDefinition is what NonAnimationApi.start uses). Building that list by hand
            // from the public view dropped each prop's suppressed-slot mask -- which held items stay hidden
            // during the animation -- and would drift with every NON release.
            com.nonid.internal.animation.data.AnimationDefinitions.Definition internal =
                    com.nonid.internal.animation.data.AnimationDefinitions.getDefinition(definition.id());
            if (internal == null) {
                return null;
            }
            List<com.nonid.internal.animation.network.AnimationStageInfo> stages = internal.stages();
            // NON's own "a creature attacks the player" session data. PlayerAttack is the PVP shape and must not
            // be hand-built here: see the method comment.
            var context = com.nonid.internal.animation.server.AnimationStartContext
                    .addonMetadata(Map.of("nonaffinity", "forced"))
                    .withSessionData(com.nonid.NonAnimationSessionData.attack());
            UUID instanceId = com.nonid.internal.animation.server.ServerAnimationController.startNowWithContext(
                    world,
                    player,
                    definition.id(),
                    actors,
                    actorKeys,
                    stages,
                    NonAnimationDamageBehavior.STOP_ON_DAMAGE,
                    false,
                    null,
                    context,
                    false,
                    false);
            if (instanceId != null) {
                NoNLog.info("started forced mating {} as a mob-attack escape (instance {}) so the player gets"
                                + " NON's struggle screen ({} of {} stage(s) escapable); breaking out is a"
                                + " successful struggle, not a damage escape",
                        definition.id(), instanceId, escapableStageSummary(stages), stages.size());
            }
            return instanceId;
        } catch (Throwable unavailable) {
            NoNLog.warn("could not start the forced mating as an attack escape ({}); falling back to a plain"
                    + " animation, where this module's break-free key is the way out", unavailable.toString());
            return null;
        }
    }

    /**
     * How many of a definition's stages NON considers escapable, for the start log only.
     *
     * <p>NON sends its struggle control per stage ({@code escapable = isAttack && isEscapableStage(stage)},
     * default {@code true}), so a content pack that marks a mating animation non-escapable gets no struggle bar
     * at all. Printing the count at start turns "the bar never appeared" from a guess into a log line. Failures
     * are reported as {@code unknown}: this is diagnostics, and it must never affect the start.
     */
    private static String escapableStageSummary(
            List<com.nonid.internal.animation.network.AnimationStageInfo> stages) {
        try {
            int escapable = 0;
            for (com.nonid.internal.animation.network.AnimationStageInfo stage : stages) {
                if (com.nonid.internal.animation.data.AnimationGameplayMetadata.escapable(stage.animationId())) {
                    escapable++;
                }
            }
            return escapable + "/" + stages.size();
        } catch (Throwable unsupported) {
            return "unknown";
        }
    }

    /** Whether the definition is exactly one player plus one creature, one active one passive. */
    private static boolean isPlayerCreatureDefinition(NonAnimationDefinition definition) {
        List<NonAnimationActor> actors = definition.actors();
        if (actors == null || actors.size() != 2) {
            return false;
        }
        boolean hasPlayer = false;
        boolean hasCreature = false;
        int active = 0;
        int passive = 0;
        for (NonAnimationActor actor : actors) {
            Set<Identifier> types = actor.entityTypes();
            if (types != null && types.contains(PLAYER_ENTITY_ID)) {
                hasPlayer = true;
            } else {
                hasCreature = true;
            }
            if (actor.activity() == NonAnimationActor.Activity.ACTIVE) {
                active++;
            } else {
                passive++;
            }
        }
        return hasPlayer && hasCreature && active == 1 && passive == 1;
    }

    /** One choosable animation for a given player + creature pair. */
    record CandidateInfo(Identifier id, String displayName, boolean startEligible) {
    }

    /**
     * Monotonic id for one start attempt.
     *
     * <p>It exists so "the start was refused" and "the engine created an instance that ended immediately" can be
     * told apart in the log. Without it the attempt line and the later stop line carry nothing in common, and four
     * "stopped but was never observed running" records cannot be tied to the nine refusals that preceded them.
     */
    private static final java.util.concurrent.atomic.AtomicLong NEXT_START_ATTEMPT =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * Every animation that matches this player and creature, in match order.
     *
     * <p>{@code startMating} stops at the first one that actually starts, which is why the key
     * always played the same animation. This exposes the whole list so the player can pick.
     */
    static List<CandidateInfo> listCandidates(ServerWorld world, ServerPlayerEntity player,
                                              LivingEntity creature) {
        List<Entity> actors = new ArrayList<>(2);
        actors.add(player);
        actors.add(creature);
        List<CandidateInfo> result = new ArrayList<>();
        for (NonAnimationCandidate candidate : NonAnimationApi.findCandidates(
                world, actors, NonAnimationApi.MatchOptions.defaults())) {
            NonAnimationDefinition definition = candidate.definition();
            if (!isPlayerCreatureDefinition(definition)) {
                continue;
            }
            String display = definition.displayName();
            result.add(new CandidateInfo(definition.id(),
                    display == null || display.isBlank() ? definition.id().toString() : display,
                    candidate.startEligible()));
        }
        return result;
    }

    /**
     * Start one specific animation chosen by the player.
     *
     * <p><b>This method's control flow is UNCHANGED from the version that was accepted for testing.</b> A diagnostic
     * build must observe, not alter: an earlier attempt at these logs added a {@code continue} when the matcher
     * reported a candidate ineligible, and made a failed start fall through to later candidates. Both changed which
     * call was made and whether the attempt ended, so a reproduction could have produced a different outcome from
     * the original fault and told us nothing about it. The lines added here are all inside the original branches.
     */
    static boolean startChosen(ServerWorld world, ServerPlayerEntity player, LivingEntity creature,
                               Identifier animationId) {
        long attempt = NEXT_START_ATTEMPT.incrementAndGet();
        if (world == null || player == null || creature == null || animationId == null) {
            NoNLog.warn("start attempt {} for {} refused before lookup: a required reference is null", attempt,
                    animationId);
            return false;
        }
        // The busy test is evaluated exactly as before -- both actors, in the same order -- and its ANSWER is logged
        // per actor. It used to return silently, so a refusal here was reported to the player as a generic failure.
        if (isActorBusy(world, player) || isActorBusy(world, creature)) {
            com.nonid.affinity.service.InteractionStartFeedback.report(
                    com.nonid.affinity.service.InteractionStartFeedback.Reason.BUSY);
            NoNLog.warn("start attempt {} for {} refused: actor busy (playerBusy={}, targetBusy={}, target={})",
                    attempt, animationId, isActorBusy(world, player), isActorBusy(world, creature),
                    creature.getName().getString());
            return false;
        }
        List<Entity> actors = new ArrayList<>(2);
        actors.add(player);
        actors.add(creature);
        for (NonAnimationCandidate candidate : NonAnimationApi.findCandidates(
                world, actors, NonAnimationApi.MatchOptions.defaults())) {
            NonAnimationDefinition definition = candidate.definition();
            if (!definition.id().equals(animationId) || !isPlayerCreatureDefinition(definition)) {
                continue;
            }
            // OBSERVED, not acted on: the value is recorded and the original call is made regardless. Skipping the
            // call when this is false would change what the engine is asked and therefore what the log means.
            NoNLog.info("start attempt {} for {}: candidate found (startEligible={}, actors={}, actorTypes={})",
                    attempt, animationId, candidate.startEligible(), candidate.actorKeys().size(),
                    describeActors(actors));
            UUID instanceId;
            com.nonid.affinity.service.InteractionStartFeedback.observeEngine(true);
            try {
                instanceId = startCandidate(world, player, definition, actors, candidate.actorKeys(), false);
            } catch (RuntimeException failure) {
                // The engine's own complaint, WITH its stack trace, so the throwing call site is locatable. The
                // exception still propagates exactly as it did before: this is not a catch that changes the outcome.
                NoNLog.warn("start attempt {} for {} threw {}: {}", attempt, animationId,
                        failure.getClass().getName(), failure.getMessage(), failure);
                throw failure;
            } finally {
                com.nonid.affinity.service.InteractionStartFeedback.observeEngine(false);
            }
            if (instanceId == null) {
                if (!candidate.startEligible()) {
                    com.nonid.affinity.service.InteractionStartFeedback.report(
                            com.nonid.affinity.service.InteractionStartFeedback.Reason.REQUIREMENTS);
                }
                // The whole reason this diagnostic exists: this single message used to be the only report of a
                // failure whose cause was never recorded. The original early return is kept.
                NoNLog.warn("start attempt {} for {}: the engine returned no instance (startEligible was {})",
                        attempt, animationId, candidate.startEligible());
                return false;
            }
            LIFECYCLE.recordOurStart(world, instanceId, player.getUuid(), creature.getUuid(), false,
                    world.getTime());
            NoNLog.info("start attempt {} started {} (instance {})", attempt, animationId, instanceId);
            return true;
        }
        NoNLog.warn("chosen animation {} does not match {} + {}", animationId,
                player.getName().getString(), creature.getName().getString());
        com.nonid.affinity.service.InteractionStartFeedback.report(
                com.nonid.affinity.service.InteractionStartFeedback.Reason.NO_MATCH);
        return false;
    }

    /** A short description of the actor list, for the start diagnostics. */
    private static String describeActors(List<Entity> actors) {
        StringBuilder out = new StringBuilder();
        for (Entity actor : actors) {
            if (out.length() > 0) {
                out.append(" + ");
            }
            out.append(actor.getType().toString());
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Event listeners
    // ------------------------------------------------------------------

    static boolean hasActiveInteraction(ServerWorld world, LivingEntity creature, ServerPlayerEntity player) {
        if (!creature.isAlive() || !player.isAlive() || creature.isRemoved() || player.isRemoved()
                || creature.getEntityWorld() != world || player.getEntityWorld() != world) return false;
        var tracked = SESSIONS.findActivePair(player.getUuid(), creature.getUuid());
        if (tracked == null) return false;
        var live = NonAnimationApi.getSession(world, tracked.instanceId());
        return live != null && live.currentStage() != null && !ReplayIsolation.isReplay(live.metadata())
                && live.actorUuids().contains(player.getUuid())
                && live.actorUuids().contains(creature.getUuid());
    }

    static synchronized void registerListeners() {
        if (listenersRegistered) {
            return;
        }
        listenersRegistered = true;
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(LIFECYCLE::start);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> LIFECYCLE.close());
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            com.nonid.affinity.non.compat.NativeSelection.expire(server.getTicks());
            LIFECYCLE.sweep();
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                com.nonid.affinity.non.compat.NativeSelection.forget(handler.player.getUuid()));
        // Say out loud whether the real completion paths are observed, because that decides whether a completed
        // mating can be told from an interrupted one at all. Without the adapter the settlement refuses every
        // reward, and this line is how that state is discovered instead of guessed at.
        NoNLog.warn("mating settlement stop-reason status: {}",
                com.nonid.affinity.service.MatingStopEvidence.adapterStatus());
        // Gate: a friend creature must ask before mating.
        //
        // Waiting on "a request is already pending" was not enough: a creature that never issued a
        // request could act freely, which is exactly what the "asks first" rule is meant to stop.
        // So any autonomous mating involving a friend is refused and turned into a request.
        NonAnimationEvents.BEFORE_START.register(context -> {
            // Replays are neither autonomous requests nor real relationship interactions.
            if (ReplayIsolation.isReplay(context.metadata())) return true;
            ServerWorld world = context.world();
            if (world == null) {
                return true;
            }
            // Explicit starts by this mod carry a marker and are always allowed.
            if (DebugStartScope.active()
                    || (context.metadata() != null && context.metadata().containsKey("nonaffinity"))) {
                return true;
            }

            List<UUID> actorUuids = context.actorUuids();
            if (actorUuids == null || actorUuids.size() != 2) {
                return true;
            }
            ServerPlayerEntity player = null;
            MobEntity friend = null;
            for (UUID actorUuid : actorUuids) {
                Entity entity = world.getEntity(actorUuid);
                if (entity instanceof ServerPlayerEntity serverPlayer) {
                    player = serverPlayer;
                } else if (entity instanceof MobEntity mob) {
                    friend = mob;
                }
            }
            if (player == null || friend == null) {
                return true;
            }
            var config = NonCreatureAffinity.getConfig();
            if (!RequestSystem.takesOverNativeInteraction(friend, player, config)) {
                return true;
            }
            if (com.nonid.affinity.non.compat.NativeEventContext.continuation()) return true;
            var intent = com.nonid.affinity.non.compat.NativeStartIntent.current(context);
            if (intent == null) return true; // Unclassified/API/queue entry: do not invent an autonomous origin.
            if (intent.consumeMatchingResume()) return true;
            if (!intent.autonomous()) return true; // Join, continuation, player action and pregnancy.
            if (RequestSystem.directAllowed(friend, player)) return true;
            // Capture the original native intent; accepting never enumerates a different animation.
            boolean asked = RequestSystem.requestNow(world, friend, player, config,
                    (targetWorld, targetPlayer, targetMob) -> resumeNativeIntent(intent, targetWorld, targetPlayer, targetMob));
            if (!asked) RequestSystem.reportNativeDeferral(world, friend, player,
                    RequestSystem.requestBlockReason(friend, player, config), context.animationId().toString());
            else if (config.debugLogging) NonCreatureAffinity.LOGGER.info(
                    "[NoN Affinity] autonomous prompt animation={} player={} creature={} token={}",
                    context.animationId(), player.getUuid(), friend.getUuid(), RequestSystem.requestToken(friend.getUuid()));
            if (asked && RequestSystem.isWaitingForAnswer(friend.getUuid())
                    && !net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,com.nonid.affinity.net.RequestStatePayload.ID)) {
                player.sendMessage(net.minecraft.text.Text.translatable(
                        "message.noncreatureaffinity.blocked_autonomous", friend.getName())
                        .formatted(net.minecraft.util.Formatting.YELLOW), false);
            }
            return false;
        });

        // Every animation is tracked from its start, not only the ones this module launches. The public stop
        // event says nothing about WHY it stopped, so the settlement needs both the identity of the animation
        // and the real reason, which the optional adapter records from NON's own stop call.
        NonAnimationEvents.STARTED.register(context -> {
            ServerWorld world = context.world();
            if (world == null || context.session() == null
                    || !LIFECYCLE.admissible(world, context.session().instanceId())) {
                return;
            }
            if (ReplayIsolation.begin(context.session().instanceId(), context.session().actorUuids(),
                    context.session().metadata())) {
                RequestSystem.cancelForReplay(world.getServer());
                return;
            }
            UUID id = context.session().instanceId();
            if (!LIFECYCLE.begin(world, id)) return;
            refreshParticipants(world, id, context.session().actorUuids());
            if (NonCreatureAffinity.getConfig().debugLogging) NonCreatureAffinity.LOGGER.info(
                    "[NoN Affinity] session admitted instance={} animation={} actors={} dimension={}",
                    id, context.session().animationId(), context.session().actorUuids(), world.getRegistryKey().getValue());
            UUID initiator = DebugStartScope.initiator();
            if (initiator != null && context.session().actorUuids().contains(initiator)) {
                for (UUID actor : context.session().actorUuids()) {
                    Entity entity = world.getEntity(actor);
                    if (entity instanceof LivingEntity && !(entity instanceof net.minecraft.entity.player.PlayerEntity))
                        LIFECYCLE.recordOurStart(world, id, initiator, actor, false, world.getTime());
                }
            }
            Map<String, String> metadata = context.session().metadata();
            // The old explicit-start qualification belongs to the actual original pair, not later joiners.
            ActorPair pair = resolveActorPair(world, context.session().actorUuids());
            if (pair != null) {
                boolean ours = DebugStartScope.active() || metadata != null && metadata.containsKey("nonaffinity");
                if (ours) LIFECYCLE.recordOurStart(world, id, pair.player().getUuid(), pair.creature().getUuid(),
                        metadata != null && "forced".equals(metadata.get("nonaffinity")), world.getTime());
                if (DebugStartScope.active() || metadata != null && "voluntary".equals(metadata.get("nonaffinity")))
                    RequestSystem.clearForExplicitStart(pair.creature(), pair.player());
            }
        });
        // No public JOIN event: observe the engine's live membership, plus stage and terminal snapshots.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(world -> {
            for (var entry : LIFECYCLE.runningWorlds().entrySet()) {
                if (entry.getValue() != world) continue;
                var live = NonAnimationApi.getSession(world, entry.getKey());
                if (live == null) {
                    // An unacknowledged disappearance is terminal without a reward, never guessed completion.
                    try { LIFECYCLE.discard(world, entry.getKey()); }
                    finally { com.nonid.affinity.service.MatingStopEvidence.forget(entry.getKey()); }
                    continue;
                }
                if (ReplayIsolation.isReplay(live.metadata())) {
                    try { LIFECYCLE.discard(world, entry.getKey()); }
                    finally { com.nonid.affinity.service.MatingStopEvidence.forget(entry.getKey()); }
                    continue;
                }
                refreshParticipants(world, entry.getKey(), live.actorUuids());
            }
        });
        NonAnimationEvents.STAGE_CHANGED.register(context -> {
            if (LIFECYCLE.runningWorlds().get(context.instanceId()) == context.world())
                refreshParticipants(context.world(), context.instanceId(), context.actorUuids());
        });
        NonAnimationEvents.STOPPED.register(context -> {
            ServerWorld world = context.world();
            UUID instanceId = context.instanceId();
            if (!LIFECYCLE.current(world) || instanceId == null || SESSIONS.isHandled(instanceId)) return;
            var trackedWorld = LIFECYCLE.runningWorlds().get(instanceId);
            if (trackedWorld != null && trackedWorld != world) return;
            if (ReplayIsolation.end(instanceId)) {
                try { LIFECYCLE.discard(world, instanceId); }
                finally { com.nonid.affinity.service.MatingStopEvidence.forget(instanceId); }
                return;
            }
            if (trackedWorld == world) refreshParticipants(world, instanceId, context.actorUuids());
            var sessions = LIFECYCLE.take(world, instanceId);
            if (sessions == null) {
                if (NonCreatureAffinity.getConfig().debugLogging) NonCreatureAffinity.LOGGER.info(
                        "[NoN Affinity] session stop instance={} actors={} outcome=untracked", instanceId, context.actorUuids());
                com.nonid.affinity.service.MatingStopEvidence.forget(instanceId);
                return;
            }
            try {
                if (sessions.isEmpty()) {
                    if (NonCreatureAffinity.getConfig().debugLogging) NonCreatureAffinity.LOGGER.info(
                            "[NoN Affinity] session stop instance={} actors={} outcome=no_relationship_pairs", instanceId, context.actorUuids());
                    return;
                }
                var evidence = com.nonid.affinity.service.MatingStopEvidence.observedKind(instanceId);
                String reason = com.nonid.affinity.service.MatingStopEvidence.observedReasonName(instanceId);
                for (var session : sessions) settle(world, context.animationId(), instanceId, session, evidence, reason);
            } finally {
                try { com.nonid.affinity.service.MatingSessionLifecycle.release(sessions); }
                finally {
                    com.nonid.affinity.service.MatingStopEvidence.forget(instanceId);
                    LIFECYCLE.finish(instanceId);
                }
            }
        });
    }

    /**
     * Apply the settlement decision for one finished animation.
     *
     * <p>The decision itself lives in {@link com.nonid.affinity.service.MatingSettlementRules}, and the stop
     * evidence it uses comes from {@link com.nonid.affinity.service.MatingStopEvidence}, which the optional
     * adapter fills from NON's own stop paths: the tick-driven completion of the final stage, and the explicit
     * advance entry that NON's "next stage" debug control uses (which stops at the final stage too). Nothing
     * here estimates anything from elapsed time.
     */
    private static void settle(ServerWorld world, Identifier animationId, UUID instanceId,
                               com.nonid.affinity.service.MatingSessions.Session session,
                               com.nonid.affinity.service.MatingStopEvidence.Kind evidence, String reasonName) {
        boolean escapePaid = false;
        try {
            var facts = new com.nonid.affinity.service.MatingSettlementRules.Facts(false,
                    session.interrupted(), evidence, session.startedByMod(), session.forced());
            var decision = com.nonid.affinity.service.MatingSettlementRules.decide(facts);

            if (!com.nonid.affinity.service.MatingSettlementRules.grants(decision)) {
                // Deliberately no reward here. A completion that arrived through either observed end path was
                // already granted above, so what reaches this branch is a stop that could not be shown to be an
                // end at all -- interrupted, escaped, already handled, or unobserved. The only charge that may
                // follow is the explicitly configured loss for breaking out of a FORCED mating (the player
                // asked for that), applied once per instance by the session registry -- and it can never hand
                // out a friendship token, because that needs a positive gain tagged as a completed mating.
                NoNLog.info("mating {} ({}) settled as NONE: interrupted={} escapedByPlayer={} stopReason={}"
                                + " evidence={} startedByUs={} forced={}",
                        instanceId, animationId, session.interrupted(), session.escapedByPlayer(), reasonName,
                        evidence, session.startedByMod(), session.forced());
                escapePaid = applyEscapeLoss(world, instanceId, animationId, session);
                return;
            }
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(session.playerId());
            Entity creature = world.getEntity(session.creatureId());
            if (player == null || creature == null || !player.isAlive() || !creature.isAlive()
                    || player.getEntityWorld() != world || creature.getEntityWorld() != world) {
                // Target gone (disconnect, unload, death): no reward can be attributed, and nothing is guessed.
                NoNLog.info("mating {} ({}) completed but its actors are gone; no affinity granted",
                        instanceId, animationId);
                return;
            }
            boolean playerInitiated = com.nonid.affinity.service.MatingSettlementRules.playerInitiated(facts);
            NoNLog.info("mating {} ({}) completed with stopReason={} evidence={} startedByUs={} forced={}"
                            + " -> playerInitiated={}",
                    instanceId, animationId, reasonName, evidence, session.startedByMod(), session.forced(),
                    playerInitiated);
            com.nonid.affinity.reunion.ReunionCompletionCharge.award(world,player,creature);
            try {
                AffinityService.recordMating(world, player, creature, playerInitiated,
                    NonCreatureAffinity.getConfig());
            } finally {
                // The original settlement creates a first relationship. Record after that creation,
                // including genuine completions whose later reward notification threw.
                com.nonid.affinity.service.CompanionArchive.event(creature, player,
                        com.nonid.affinity.data.CompanionArchiveData.Kind.COMPLETED, "completed:" + instanceId,
                        playerInitiated ? "player_initiated" : "native", -1, -1);
            }
        } finally { session.settleEscapeFeedback(escapePaid); }
    }

    /**
     * Record that a NON struggle was won for this instance, so the settlement treats it as a player escape.
     *
     * <p>Called from the optional integration's mixin on NON's own escape-completion call. Scoped to the
     * instance: an escape that concerns a NON-native interaction marks nothing of ours.
     */
    public static void markPlayerEscapeForInstance(UUID instanceId) {
        if (SESSIONS.markPlayerEscapeForInstance(instanceId)) {
            NoNLog.info("NON reported a successful struggle for mating {}; it will be settled as an escape",
                    instanceId);
        }
    }

    /**
     * The player pressed the break-free key.
     *
     * <p>This is the fallback escape, used when the forced mating is NOT running as an escapable attack: the
     * option {@code nonStruggleScreen} is off, this NON build lacks the members the attack start needs, or the
     * animation's stages are marked non-escapable so NON sends no struggle bar. In those cases the player has no
     * NON-side way out, and this key ends the forced mating through the public API and charges the configured
     * {@code affinityLossOnEscape}, which is what a successful struggle is meant to cost.
     *
     * <p>When the struggle bar IS up, this key is not needed and not used: NON's own escape completion reports
     * the win (see {@link #markPlayerEscapeForInstance}).
     */
    public static void breakFree(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        com.nonid.affinity.service.MatingSessions.Session session = SESSIONS.findForcedForPlayer(player.getUuid());
        if (session == null) {
            // Not inside one of our forced matings: nothing to end, and nothing to say.
            return;
        }
        UUID instanceId = session.instanceId();
        ServerWorld world = player.getEntityWorld() instanceof ServerWorld server ? server : null;
        if (world == null) {
            return;
        }
        // Record the escape FIRST, so the settlement that the stop triggers sees it and charges the loss
        // instead of granting anything.
        SESSIONS.markPlayerEscape(player.getUuid(), session.creatureId());
        NoNLog.info("player {} is breaking free of forced mating {}", player.getName().getString(), instanceId);
        // Stop directly: advancing can loop or wait on stage conditions indefinitely.
        NonAnimationApi.stop(world, instanceId);
        if (SESSIONS.runningIds().contains(instanceId)) {
            NoNLog.warn("forced mating {} did not acknowledge the stop call",
                    instanceId);
        }
    }

    /**
     * Charge the configured extra loss when the player broke out of a FORCED mating.
     *
     * <p>Only the loss: no mating count, no reward, and no friendship token (the token needs a positive gain
     * tagged as a completed mating). The size comes from
     * {@link com.nonid.affinity.service.MatingSettlementRules#escapeLoss}, so a voluntary mating the player
     * abandons, an interruption that was not the player's doing, and a configured value of 0 all cost nothing.
     */
    private static boolean applyEscapeLoss(ServerWorld world, UUID instanceId, Identifier animationId,
                                        com.nonid.affinity.service.MatingSessions.Session session) {
        int loss = com.nonid.affinity.service.MatingSettlementRules.escapeLoss(session.escapedByPlayer(),
                session.forced(), NonCreatureAffinity.getConfig().affinityLossOnEscape);
        if (loss <= 0) {
            return false;
        }
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(session.playerId());
        Entity creature = world.getEntity(session.creatureId());
        if (player == null || creature == null || !player.isAlive() || !creature.isAlive()
                    || player.getEntityWorld() != world || creature.getEntityWorld() != world) {
            NoNLog.info("mating {} ({}) was escaped but its actors are gone; no escape loss charged",
                    instanceId, animationId);
            return false;
        }
        NoNLog.info("mating {} ({}) was escaped by the player; charging the configured escape loss {}",
                instanceId, animationId, loss);
        var relation = AffinityService.getRelation(creature, player);
        double before = relation == null ? 0 : relation.getExactAffinity();
        try {
            return AffinityService.recordForcedEscape(world, player, creature, loss, NonCreatureAffinity.getConfig()) < 0;
        } finally {
            // A later notification failure must not erase the fact that the old charge landed.
            if (relation != null && relation.getExactAffinity() < before) session.settleEscapeFeedback(true);
        }
    }

    private static void refreshParticipants(ServerWorld world, UUID id, List<UUID> actors) {
        var players = new HashSet<UUID>();
        var creatures = new HashSet<UUID>();
        if (actors != null) for (UUID actor : actors) {
            Entity entity = world.getEntity(actor);
            if (entity == null || !entity.isAlive() || entity.isRemoved()) continue;
            if (entity instanceof ServerPlayerEntity player) players.add(player.getUuid());
            else if (entity instanceof LivingEntity) creatures.add(entity.getUuid());
        }
        LIFECYCLE.observe(world, id, players, creatures, world.getTime());
    }

    public static void markPlayerEscapeForInstance(UUID instanceId, UUID playerId) {
        SESSIONS.markPlayerEscapeForInstance(instanceId, playerId);
    }
    /** One player plus one non-player living actor, or null when the animation is something else. */
    @Nullable
    private static ActorPair resolveActorPair(ServerWorld world, List<UUID> actorUuids) {
        if (actorUuids == null || actorUuids.size() != 2) {
            return null;
        }
        ServerPlayerEntity player = null;
        LivingEntity creature = null;
        for (UUID actorUuid : actorUuids) {
            Entity entity = world.getEntity(actorUuid);
            if (entity instanceof ServerPlayerEntity serverPlayer) {
                player = serverPlayer;
            } else if (entity instanceof LivingEntity living) {
                creature = living;
            }
        }
        return player == null || creature == null ? null : new ActorPair(player, creature);
    }

    private record ActorPair(ServerPlayerEntity player, LivingEntity creature) {
    }

    /**
     * Note that the player broke out of a mating.
     *
     * <p>Called from the damage path when the player attacks a creature. <b>This is a signal, not the
     * decision:</b> NeedsOfNature resolves an attack escape after a delay of its own, so the stop event can
     * arrive before this mark. A stop that cannot show a natural end is refused whether or not the mark
     * arrived in time, which is why the ordering is no longer load-bearing.
     *
     * <p>Marks EVERY running animation of that pair, including ones this module did not start -- the player's
     * escape is evidence about the animation, not about who began it. Returns whether anything was marked.
     */
    public static boolean markEscaped(ServerPlayerEntity player, LivingEntity creature) {
        if (player == null || creature == null) {
            return false;
        }
        // A PLAYER escape specifically: it is the only interruption that may cost affinity, so it is recorded
        // separately from a generic interruption (an actor dying or unloading is not the player's doing).
        int marked = SESSIONS.markPlayerEscape(player.getUuid(), creature.getUuid());
        if (marked > 0) {
            NoNLog.info("player {} broke out of {} mating animation(s) with {}; no reward for those",
                    player.getName().getString(), marked, creature.getName().getString());
        }
        return marked > 0;
    }

    public static boolean escapeCostsAffinity(ServerPlayerEntity player, LivingEntity creature) {
        return SESSIONS.hasForcedPair(player.getUuid(), creature.getUuid())
                && NonCreatureAffinity.getConfig().affinityLossOnEscape > 0;
    }

    public static com.nonid.affinity.service.EscapeAttackSettlement prepareEscapeFeedback(
            ServerPlayerEntity player, LivingEntity creature) {
        var cfg = NonCreatureAffinity.getConfig();
        if (cfg.affinityLossOnEscape <= 0 || !(creature instanceof net.minecraft.entity.mob.MobEntity mob)) return null;
        var relation = AffinityService.getRelation(mob, player);
        if (relation == null) return null;
        var sessions = SESSIONS.escapeChargeCandidates(player.getUuid(), creature.getUuid());
        if (sessions.isEmpty()) return null;
        var mobRef = new java.lang.ref.WeakReference<>(mob);
        var playerRef = new java.lang.ref.WeakReference<>(player);
        var feedback = new com.nonid.affinity.service.EscapeAttackSettlement(relation, cfg.minAffinity, cfg.maxAffinity,
                sessions.stream().map(com.nonid.affinity.service.MatingSessions.Session::instanceId).toList(),
                loss -> {
                    var target = mobRef.get(); var owner = playerRef.get();
                    if (target != null && owner != null) AffinityService.applyRecordedAttackLoss(target, owner, relation, loss);
                    else relation.addAffinity(-loss, cfg.minAffinity, cfg.maxAffinity);
                });
        for (var session : sessions) session.trackEscapeFeedback(feedback);
        return feedback;
    }

    // ------------------------------------------------------------------
    // Forced mating
    // ------------------------------------------------------------------

    /**
     * Start the forced mating that follows a rejected request.
     *
     * <p><b>How the player gets out of it.</b> By default ({@code nonStruggleScreen}) the animation is started
     * as NON's own "a creature attacks the player" interaction, so the player gets NON's A/D struggle bar with
     * real progress; winning it is reported by NON as a successful escape and costs
     * {@code affinityLossOnEscape}. When that start is unavailable -- the option is off, this NON build lacks
     * the members, or the animation's stages are not escapable -- the animation is a plain one started with
     * {@code STOP_ON_DAMAGE}, and this module's break-free key ends it at the same cost. Either way an escape is
     * the player's doing, and only the player's.
     */
    static boolean startForcedMating(ServerWorld world, ServerPlayerEntity player, LivingEntity creature) {
        return startMating(world, player, creature, true);
    }

    // ------------------------------------------------------------------
    // Request queue
    // ------------------------------------------------------------------

    static void putRequest(UUID mobUuid, PendingRequest request) {
        PENDING_REQUESTS.put(mobUuid, request);
    }

    @Nullable
    static PendingRequest getRequest(UUID mobUuid) {
        return PENDING_REQUESTS.get(mobUuid);
    }

    @Nullable
    static PendingRequest removeRequest(UUID mobUuid) {
        return PENDING_REQUESTS.remove(mobUuid);
    }

    static Set<UUID> pendingMobIds() {
        return new HashSet<>(PENDING_REQUESTS.keySet());
    }

    static void clear() {
        try { LIFECYCLE.close(); }
        finally {
            ReplayIsolation.clear();
            com.nonid.affinity.non.compat.NativeSelection.clear();
            com.nonid.affinity.service.MatingStopEvidence.clearAll();
            PENDING_REQUESTS.clear();
            AWAITING_ANSWER.clear();
        }
    }

    /** A creature waiting for the player to answer its request. */
    record PendingRequest(UUID mobUuid, UUID playerUuid, long expireTick) {
    }
}


