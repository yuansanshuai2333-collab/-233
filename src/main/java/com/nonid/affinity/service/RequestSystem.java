package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.config.AffinityConfig;
import com.nonid.affinity.data.AffinityHolder;
import com.nonid.affinity.data.CreatureAffinityData;
import com.nonid.affinity.data.PlayerAffinity;
import com.nonid.affinity.net.AffinityNetworking;
import com.nonid.affinity.non.NonBridge;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Friendly creatures asking the player for mating, plus accept/reject handling.
 *
 * <p>NoN's autonomous start supplies the server-owned intent. Accepting revalidates and
 * resumes that intent; rejection and timeout retain their existing, separate consequences.
 * NaR does not manufacture invitations through a periodic scan.
 */
public final class RequestSystem {

    private static final String KEY = "message.noncreatureaffinity.";
    private static final String BTN = "button.noncreatureaffinity.";
    private static final String TIP = "tooltip.noncreatureaffinity.";

    private static final Set<UUID> WAITING = new HashSet<>();
    private static final java.util.Map<UUID, UUID> REQUEST_PLAYERS = new java.util.HashMap<>();
    private static final java.util.Map<UUID, net.minecraft.registry.RegistryKey<net.minecraft.world.World>>
            REQUEST_WORLDS = new java.util.HashMap<>();

    @FunctionalInterface public interface NativeIntent { boolean start(ServerWorld world, ServerPlayerEntity player, MobEntity mob); }
    private static final java.util.Map<UUID, NativeIntent> INTENTS = new java.util.HashMap<>();
    private RequestSystem() {
    }

    public static void clear() {
        LAST_DEFERRAL.clear();
        WAITING.clear();
        INTENTS.clear();
        REQUEST_PLAYERS.clear();
        REQUEST_IDS.clear();
        REQUEST_WORLDS.clear();
    }

    /** A replay must not turn an outstanding prompt into an ignored/rejected request. */
    public static void cancelForReplay(net.minecraft.server.MinecraftServer server) {
        for (UUID mob : new HashSet<>(REQUEST_PLAYERS.keySet())) {
            UUID playerId = REQUEST_PLAYERS.get(mob);
            // Only the replay participant's own prompts, never another player's pending request.
            if (!ReplayIsolation.isActor(playerId)) continue;
            NonBridge.removeRequest(mob);
            NonBridge.clearAwaitingAnswer(mob);
            WAITING.remove(mob);
            clearRequestId(mob);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) AffinityNetworking.clearRequestState(player, mob);
        }
    }

    /** Only a successful explicit start consumes the participant's pending prompt; no rejection charge. */
    public static void clearForExplicitStart(Entity mob, ServerPlayerEntity player) {
        UUID id = mob.getUuid();
        if (!player.getUuid().equals(NonBridge.requestPlayer(id))) return;
        NonBridge.removeRequest(id);
        clearRequestId(id);
        NonBridge.clearAwaitingAnswer(id);
        WAITING.remove(id);
        AffinityNetworking.sendRequestState(player, mob, 0L, 0, 0);
    }

    // ------------------------------------------------------------------
    // Issuing requests
    // ------------------------------------------------------------------

    /**
     * Advance per-relation cooldowns.
     *
     * <p>Must run every tick: {@code tickCooldown()} previously existed but was never called, so a
     * single request put the relation on a cooldown that never expired.
     */
    public static void tickCooldowns(ServerWorld world) {
        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof AffinityHolder holder)) {
                continue;
            }
            // Read-only: this runs for EVERY entity in the world on every tick, so using the creating
            // accessor made the first tick attach an empty record to every mob that had none and persist
            // it. A creature with no relations has no cooldown to advance.
            CreatureAffinityData data = holder.peekAffinityData();
            if (data == null) {
                continue;
            }
            for (PlayerAffinity relation : data.entries().values()) {
                relation.tickCooldown();
            }
        }
    }

    /** Retired compatibility entry: NaR no longer manufactures invitations by scanning. */
    @Deprecated
    public static void tickRequests(ServerWorld world, AffinityConfig config) { }
    /**
     * Whether this creature and player have any interaction animation at all.
     *
     * <p>Separated from {@link #canRequest} only in naming: both request entries call it, so the periodic scan and
     * the "a friend tried to act on its own" path cannot disagree about which creatures are allowed to ask. Without
     * NeedsOfNature, or with no content pack, this is false and therefore nothing asks -- which is the honest outcome,
     * because there is nothing to start.
     */
    public static boolean hasAnyAnimation(ServerWorld world, MobEntity mob, ServerPlayerEntity player) {
        return NonBridge.hasMatchingAnimation(world, player, mob);
    }

    /** Why a creature may or may not raise a request, with the "no animation" case named separately. */
    public enum RequestVerdict {
        /** The request may be raised. */
        ALLOWED,
        /**
         * This pair has no interaction animation at all, so no request may be raised.
         *
         * <p>Distinct from the other refusals because it is permanent for this species rather than temporary: the
         * others clear on their own (the creature stops waiting, the cooldown expires, the player answers), while this
         * one only changes when the content or the pair changes.
         */
        NO_ANIMATION,
        /** Some other request rule already refuses. */
        REFUSED
    }

    /**
     * The request gate as a pure decision, so the "no animation means no request" rule is checked without a world.
     *
     * <p>The rule lives here rather than only inside the scan because getting it wrong is invisible in play: a
     * creature that asks and is then accepted into nothing looks exactly like a creature whose interaction failed for
     * a ground or space reason, and the player is offered a choice that cannot be honoured.
     *
     * @param hasAnimation whether ANY animation matches this creature and player, regardless of whether it could
     *                     start from where they are standing. Start eligibility must NOT be used here: an animation
     *                     blocked by the surroundings is still something the creature may ask for, and accepting it
     *                     then explains the real reason
     * @param otherRulesAllow whether every other request rule already passed
     */
    public static RequestVerdict requestVerdict(boolean hasAnimation, boolean otherRulesAllow) {
        if (!hasAnimation) {
            return RequestVerdict.NO_ANIMATION;
        }
        return otherRulesAllow ? RequestVerdict.ALLOWED : RequestVerdict.REFUSED;
    }

    /**
     * Whether this creature has an existing positive relationship with the player.
     * This does not define the native interaction takeover threshold.
     */
    public static boolean hasRelationWith(MobEntity mob, ServerPlayerEntity player) {
        var relation = AffinityService.getRelation(mob, player);
        return relation != null && relation.getAffinity() > 0;
    }

    /** Shared boundary for native takeover and autonomous request eligibility. */
    public static boolean reachesTakeoverThreshold(int affinity, int familiarThreshold) {
        return affinity > 0 && affinity >= familiarThreshold;
    }

    /** Read-only: checking a native interaction must not create a relationship. */
    public static boolean takesOverNativeInteraction(MobEntity mob, ServerPlayerEntity player,
                                                     AffinityConfig config) {
        PlayerAffinity relation = AffinityService.getRelation(mob, player);
        return relation != null && reachesTakeoverThreshold(relation.getAffinity(), config.familiarThreshold);
    }

    /** Per-creature preference; request eligibility is checked separately before it can act. */
    public static boolean directAllowed(MobEntity mob, ServerPlayerEntity player) {
        PlayerAffinity relation = AffinityService.getRelation(mob, player);
        return relation != null && DirectInteractionPreference.permits(
                DirectInteractionPreference.enabled(player,mob), relation.getAffinity());
    }

    public static boolean relationshipAllowsRequest(MobEntity mob, ServerPlayerEntity player,
                                                     PlayerAffinity relation, AffinityConfig config) {
        if (relation == null || !reachesTakeoverThreshold(relation.getAffinity(), config.familiarThreshold)) return false;
        if (!CompanionService.mayControl(CompanionService.companionOwner(mob),
                CompanionService.vanillaOwner(mob), player.getUuid())) return false;
        if (AffinityService.isTamedCreature(mob)) {
            UUID owner = CompanionService.vanillaOwner(mob);
            if (owner == null) return false;
            // Known vanilla ownership is independent of legacy automatic relationship creation.
            if (!CompanionService.knownVanillaPet(mob) && !config.allowVanillaTamedCreatures) return false;
        }
        return true;
    }

    /** Whether this creature is currently waiting for an answer. */
    public static boolean isWaitingForAnswer(java.util.UUID mobUuid) {
        return WAITING.contains(mobUuid) || NonBridge.hasRequest(mobUuid);
    }

    /**
     * Issue a request on the spot (used when a friend creature tried to act on its own).
     *
     * @return whether a request was actually created
     */
    public static boolean requestNow(ServerWorld world, MobEntity mob, ServerPlayerEntity player,
                                     AffinityConfig config, NativeIntent intent) {
        // The native event must not override an unfinished pair cooldown.
        if (intent == null || !canRequest(mob, player, config)) {
            return false;
        }
        PlayerAffinity relation = AffinityService.relationOrCreate(mob, player, config);
        if (relation == null) {
            return false;
        }
        sendRequest(world, mob, player, relation, config, intent);
        return true;
    }

    /**
     * Whether this creature may start a new request toward this player.
     *
     * <p>The animation-event entry is the only producer; NoN already selected its definition.
     */
    /** Pure current rejection reason; never creates relationships or schedules autonomous work. */
    public static String requestBlockReason(MobEntity mob, ServerPlayerEntity player, AffinityConfig config) {
        if (mob == null || player == null || !mob.isAlive() || mob.isBaby()
                || !player.isAlive() || player.isSpectator()) return "invalid";
        if (ReplayIsolation.protects(mob.getUuid(), player.getUuid())) return "replay";
        if (mob.getEntityWorld() != player.getEntityWorld()) return "dimension";
        if (mob.squaredDistanceTo(player) > RequestPresentation.ANSWER_RANGE * RequestPresentation.ANSWER_RANGE) return "range";
        if (mob.getEntityWorld() instanceof ServerWorld world
                && (NonBridge.isActorBusy(world, mob) || NonBridge.isActorBusy(world, player))) return "busy";
        if (isWaitingForAnswer(mob.getUuid())) return "waiting";
        if (hasPending(player)) return "other_request";
        PlayerAffinity relation = AffinityService.getRelation(mob, player);
        if (relation == null || !reachesTakeoverThreshold(relation.getAffinity(), config.familiarThreshold)) return "relationship";
        if (!CompanionService.mayControl(CompanionService.companionOwner(mob), CompanionService.vanillaOwner(mob), player.getUuid())) return "ownership";
        if (!relationshipAllowsRequest(mob, player, relation, config)) return "unsupported_pet";
        if (relation.getRequestCooldownTicks() > 0) return "cooldown";
        return "ready";
    }

    public static boolean canRequest(MobEntity mob, ServerPlayerEntity player, AffinityConfig config) {
        return requestVerdict(true, "ready".equals(requestBlockReason(mob, player, config))) == RequestVerdict.ALLOWED;
    }

    private static final java.util.Map<String, Long> LAST_DEFERRAL = new java.util.LinkedHashMap<>();

    /** Background native retries stay off the HUD; explicit answers report their own failures. */
    public static void reportNativeDeferral(ServerWorld world, MobEntity mob, ServerPlayerEntity player,
                                             String reason, String animation) {
        if (!NonCreatureAffinity.getConfig().debugLogging) return;
        String key = world.getRegistryKey().getValue() + ":" + player.getUuid() + ":" + mob.getUuid() + ":" + reason;
        // Diagnostic pacing follows real elapsed time even when the server tick rate is accelerated.
        // This clock never controls request eligibility, expiry, native retries or gameplay cooldowns.
        long now = System.nanoTime();
        Long previous = LAST_DEFERRAL.get(key);
        if (previous != null && now - previous < java.util.concurrent.TimeUnit.SECONDS.toNanos(5)) return;
        if (!LAST_DEFERRAL.containsKey(key) && LAST_DEFERRAL.size() >= 512)
            LAST_DEFERRAL.remove(LAST_DEFERRAL.keySet().iterator().next());
        LAST_DEFERRAL.put(key, now);
        NonCreatureAffinity.LOGGER.info(
                "[NoN Affinity] autonomous deferred animation={} player={} creature={} dimension={} reason={}",
                animation, player.getUuid(), mob.getUuid(), world.getRegistryKey().getValue(), reason);
    }

    /** Issue one request; its passive bubble is delivered only to its participant. */
    /** Session-unique request ids. The expiry tick is NOT unique: two requests created in the same
     * tick would share it, which is what made it unusable as a button token. */
    private static final java.util.concurrent.atomic.AtomicLong NEXT_REQUEST_ID =
            new java.util.concurrent.atomic.AtomicLong(1L);

    /** Current request id per creature, cleared when the request ends. */
    private static final java.util.Map<UUID, Long> REQUEST_IDS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void sendRequest(ServerWorld world, MobEntity mob, ServerPlayerEntity player,
                                   PlayerAffinity relation, AffinityConfig config, NativeIntent intent) {
        if (intent == null || !canRequest(mob, player, config)) return;
        long expireTick = world.getTime() + config.requestTimeoutTicks;
        long requestId = NEXT_REQUEST_ID.getAndIncrement();
        REQUEST_IDS.put(mob.getUuid(), requestId);
        INTENTS.put(mob.getUuid(), intent);
        REQUEST_PLAYERS.put(mob.getUuid(), player.getUuid());
        REQUEST_WORLDS.put(mob.getUuid(), world.getRegistryKey());
        NonBridge.putRequest(mob.getUuid(), player.getUuid(), expireTick);
        NonBridge.markAwaitingAnswer(mob.getUuid());
        WAITING.add(mob.getUuid());
        relation.setRequestCooldownTicks(config.requestCooldownTicks);

        // Let the client HUD know about the pending request; expireTick 0 means "cleared".
        AffinityNetworking.sendRequestState(player, mob, expireTick,
                relation.getAffinity(), config.familiarThreshold);

        if (config.debugLogging) {
            NonCreatureAffinity.LOGGER.info("[NoN Affinity] {} requests mating from {} (arousal {})",
                    mob.getName().getString(), player.getName().getString(), NonBridge.getEnergy(mob));
        }
    }

    // ------------------------------------------------------------------
    // Timeouts
    // ------------------------------------------------------------------

    /** Expire requests that were not answered. */
    public static void tickTimeouts(ServerWorld world, AffinityConfig config) {
        for (UUID mobUuid : new HashSet<>(REQUEST_WORLDS.keySet())) {
            // Requests are global, but this callback runs once per dimension. Only the issuing world owns it.
            if (!world.getRegistryKey().equals(REQUEST_WORLDS.get(mobUuid))) continue;
            UUID targetId = REQUEST_PLAYERS.get(mobUuid);
            ServerPlayerEntity target = targetId == null ? null
                    : world.getServer().getPlayerManager().getPlayer(targetId);
            Entity targetMob = world.getEntity(mobUuid);
            if (!NonBridge.hasRequest(mobUuid) || targetMob == null || targetMob.isRemoved()
                    || (targetMob instanceof MobEntity requestMob && requestMob.isBaby())
                    || !targetMob.isAlive() || target == null || !target.isAlive() || target.isSpectator()
                    || target.getEntityWorld() != world
                    || targetMob.squaredDistanceTo(target) > RequestPresentation.ANSWER_RANGE * RequestPresentation.ANSWER_RANGE) {
                debounceInvalid(world, mobUuid, targetId, config);
                NonBridge.removeRequest(mobUuid);
                clearRequestId(mobUuid);
                NonBridge.clearAwaitingAnswer(mobUuid);
                WAITING.remove(mobUuid);
                if (target != null) AffinityNetworking.clearRequestState(target, mobUuid);
                continue;
            }
            long expireTick = NonBridge.requestExpireTick(mobUuid);
            if (expireTick < 0L || world.getTime() < expireTick) {
                continue;
            }
            UUID playerUuid = NonBridge.removeRequest(mobUuid);
            clearRequestId(mobUuid);
            NonBridge.clearAwaitingAnswer(mobUuid);
            WAITING.remove(mobUuid);
            if (playerUuid == null) {
                continue;
            }
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerUuid);
            Entity mob = world.getEntity(mobUuid);
            if (player != null) AffinityNetworking.clearRequestState(player, mobUuid);
            if (player != null && mob != null) {
                // Being ignored for the whole window costs affinity. This is the ONLY path that
                // charges: an acceptance that failed for any other reason is never penalised, and a
                // rejection has its own separate handling.
                PlayerAffinity relation = AffinityService.getRelation(mob, player);
                if (relation != null) relation.setRequestCooldownTicks(config.requestCooldownTicks);
                if (config.affinityLossOnTimeout > 0) {
                    AffinityService.addAffinity(mob, player, -config.affinityLossOnTimeout,
                            "request_timeout");
                }
                com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "request_timeout", mob.getName())
                        .formatted(Formatting.GRAY), true);
                if (NonCreatureAffinity.LOG_VERBOSE) {
                    NonCreatureAffinity.LOGGER.info(
                            "[NoN Affinity] request from {} expired after {} ticks without an answer",
                            mob.getName().getString(), config.requestTimeoutTicks);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Player answers
    // ------------------------------------------------------------------

    /** Player accepts: start mating immediately. */
    public static boolean accept(ServerPlayerEntity player, @Nullable MobEntity specific) {
        return accept(player, specific, 0L);
    }

    /** Accept a request, optionally requiring that it is the one identified by {@code token}. */
    public static boolean accept(ServerPlayerEntity player, @Nullable MobEntity specific, long token) {
        AffinityConfig config = NonCreatureAffinity.getConfig();
        Pending pair = resolvePending(player, specific, token);
        if (pair == null) {
            return false;
        }
        if (!validateAnswer(pair, config)) return false;
        NativeIntent intent = INTENTS.get(pair.mob().getUuid());
        NonBridge.removeRequest(pair.mob().getUuid());
        clearRequestId(pair.mob().getUuid());
        NonBridge.clearAwaitingAnswer(pair.mob().getUuid());
        WAITING.remove(pair.mob().getUuid());
        AffinityNetworking.sendRequestState(player, pair.mob(), 0L, 0, 0);
        ServerWorld world = asServerWorld(pair.mob());
        if (world == null) {
            return false;
        }
        // The start is observed so the player can be told WHY accepting did not work, using the same reason
        // vocabulary the manual selection menu already reports. The attempt is observational only: it neither
        // retries nor changes what the engine is asked.
        PlayerAffinity answeredRelation = AffinityService.getRelation(pair.mob(), player);
        if (answeredRelation != null) answeredRelation.setRequestCooldownTicks(config.requestCooldownTicks);
        boolean started;
        try (var feedback = InteractionStartFeedback.begin()) {
            NonBridge.clearMatingRefusal();
            started = intent != null && intent.start(world, player, pair.mob());
            if (started) {
                // The pending request was consumed before starting. Both manual and automatic
                // acceptance use this path; engine notifications and duplicate replies cannot count again.
                if (!ReplayIsolation.protects(pair.mob().getUuid(), player.getUuid())) {
                    PlayerAffinity relation = AffinityService.getRelation(pair.mob(), player);
                    if (relation != null) relation.recordAgreement();
                    CompanionArchive.event(pair.mob(), player, com.nonid.affinity.data.CompanionArchiveData.Kind.ACCEPTED,
                            "accepted:" + java.util.UUID.randomUUID(), "", -1, -1);
                    RelationshipBond.add(pair.mob(), player, 0.1);
                }
                com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "accepted", pair.mob().getName())
                        .formatted(Formatting.LIGHT_PURPLE), false);
            } else {
                reportStartFailure(player, pair.mob(), feedback.reason(), world);
            }
        }
        // Deliberately NO affinity change on this path. Accepting is not a refusal and not an unanswered request, so
        // it must not cost anything: the only two places that charge are the timeout (the player ignored the request)
        // and an explicit rejection. A failed start is the game's doing, not the player's, and is not penalised.
        return started;
    }

    /**
     * Tell the player why an accepted request could not start, and record it.
     *
     * <p>The reason comes from the same per-attempt observation the manual menu uses, so the two paths cannot
     * describe the same failure differently. When the engine reported nothing specific the message says so instead of
     * guessing: a vague explanation is more useful than a confident wrong one.
     */
    private static void reportStartFailure(ServerPlayerEntity player, MobEntity mob,
                                           InteractionStartFeedback.Reason reason, ServerWorld world) {
        String reasonKey = reason.name().toLowerCase(java.util.Locale.ROOT);
        // Action bar AND chat: the action bar is where the player is looking when they click the button, and chat
        // keeps the reason readable afterwards.
        Text message = Text.translatable(KEY + "interaction_failure." + reasonKey).formatted(Formatting.RED);
        com.nonid.affinity.service.GameplayFeedback.send(player,message, true);
        com.nonid.affinity.service.GameplayFeedback.send(player,message, false);
        NonCreatureAffinity.LOGGER.warn(
                "[NoN Affinity] accepted request could not start: creature={} player={} reason={}",
                mob.getUuid(), player.getUuid(), reason);
    }

    /**
     * Player rejects: lose affinity, then either force mating (if arousal is high enough) or simply
     * put the creature on cooldown.
     */
    public static boolean reject(ServerPlayerEntity player, @Nullable MobEntity specific) {
        return reject(player, specific, 0L);
    }

    /** Reject a request, optionally requiring that it is the one identified by {@code token}. */
    public static boolean reject(ServerPlayerEntity player, @Nullable MobEntity specific, long token) {
        Pending pair = resolvePending(player, specific, token);
        if (pair == null) {
            return false;
        }
        AffinityConfig config = NonCreatureAffinity.getConfig();
        if (!validateAnswer(pair, config)) return false;
        NonBridge.removeRequest(pair.mob().getUuid());
        clearRequestId(pair.mob().getUuid());
        NonBridge.clearAwaitingAnswer(pair.mob().getUuid());
        WAITING.remove(pair.mob().getUuid());
        AffinityNetworking.sendRequestState(player, pair.mob(), 0L, 0, 0);
        ServerWorld world = asServerWorld(pair.mob());
        if (world == null) {
            return false;
        }
        AffinityService.recordRejection(world, player, pair.mob(), config);

        if (!config.forceMatingOnReject) {
            com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "rejected_calm", pair.mob().getName())
                    .formatted(Formatting.GRAY), false);
            return true;
        }

        // Only a sufficiently aroused creature forces itself on the player. A separate cooldown is
        // used here so it never mixes with the ordinary request cooldown.
        int energy = NonBridge.getEnergy(pair.mob());
        int maxEnergy = NonBridge.getMaxEnergy(pair.mob());
        boolean arousedEnough = maxEnergy > 0
                && (double) energy / (double) maxEnergy >= config.forcedMatingEnergyRatio;
        if (!arousedEnough) {
            PlayerAffinity relation = AffinityService.getRelation(pair.mob(), player);
            if (relation != null) {
                relation.setRequestCooldownTicks(config.requestCooldownTicks);
            }
            com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "rejected_cooldown", pair.mob().getName())
                    .formatted(Formatting.GRAY), false);
            return true;
        }

        com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "rejected_enraged", pair.mob().getName())
                .formatted(Formatting.DARK_RED), false);
        if (config.forcedMatingEscapable) {
            com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "escape_hint")
                    .formatted(Formatting.DARK_GRAY), true);
        }
        boolean started = NonBridge.startForcedMating(world, player, pair.mob());
        if (!started) {
            com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "forced_failed")
                    .formatted(Formatting.GRAY), true);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Read only: no request or relationship is created by a snapshot. */
    public static long requestToken(UUID mob) { return REQUEST_IDS.getOrDefault(mob, 0L); }

    /** Network answers cannot use the token-free manual-command fallback. */
    public static boolean answer(ServerPlayerEntity player, com.nonid.affinity.net.RequestAnswerPayload answer) {
        if (answer.token() <= 0 || answer.expiry() <= 0
                || !player.getEntityWorld().getRegistryKey().getValue().toString().equals(answer.dimension())
                || !isWaitingFor(answer.mob(), player.getUuid())
                || requestToken(answer.mob()) != answer.token()
                || NonBridge.requestExpireTick(answer.mob()) != answer.expiry()) return false;
        Entity entity = player.getEntityWorld().getEntity(answer.mob());
        if (!(entity instanceof MobEntity mob)) return false;
        return answer.accept() ? accept(player, mob, answer.token()) : reject(player, mob, answer.token());
    }
    private static boolean validateAnswer(Pending pair, AffinityConfig config) {
        MobEntity mob = pair.mob();
        ServerPlayerEntity player = pair.player();
        boolean valid = RequestAnswerRules.allowed(player.isAlive() && !player.isSpectator(),
                mob.isAlive() && !mob.isRemoved() && !mob.isBaby(),
                mob.getEntityWorld() == player.getEntityWorld()
                        && player.getEntityWorld().getRegistryKey().equals(REQUEST_WORLDS.get(mob.getUuid())),
                mob.squaredDistanceTo(player), RequestPresentation.ANSWER_RANGE);
        valid = valid && relationshipAllowsRequest(mob, player, AffinityService.getRelation(mob, player), config);
        if (!valid) {
            PlayerAffinity relation = AffinityService.getRelation(mob, player);
            if (relation != null) relation.setRequestCooldownTicks(config.requestCooldownTicks);
            clearForExplicitStart(mob, player);
            com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "request_stale").formatted(Formatting.GRAY), true);
        }
        return valid;
    }

    /** Cancel without a penalty; only the existing participant relation receives a fresh debounce. */
    private static void debounceInvalid(ServerWorld world, UUID mobId, UUID playerId, AffinityConfig config) {
        if (playerId == null) return;
        Entity mob = world.getEntity(mobId);
        if (mob == null) {
            // A dimension change can leave the issuing world. Consult loaded entities only.
            for (ServerWorld other : world.getServer().getWorlds()) {
                if (other == world) continue;
                mob = other.getEntity(mobId);
                if (mob != null) break;
            }
        }
        if (mob instanceof AffinityHolder holder) {
            var data = holder.peekAffinityData();
            var relation = data == null ? null : data.get(playerId);
            if (relation != null) relation.setRequestCooldownTicks(config.requestCooldownTicks);
        }
    }

    @Nullable
    private static Pending resolvePending(ServerPlayerEntity player, @Nullable MobEntity specific,
                                          long token) {
        if (specific != null) {
            if (!tokenMatches(specific.getUuid(), token) || isExpired(specific.getUuid(), player)) {
                return null;
            }
            UUID expected = NonBridge.requestPlayer(specific.getUuid());
            if (expected != null && expected.equals(player.getUuid())) {
                return new Pending(specific, player);
            }
            return null;
        }
        ServerWorld world = player.getEntityWorld();
        for (UUID mobUuid : NonBridge.pendingMobIds()) {
            UUID owner = NonBridge.requestPlayer(mobUuid);
            if (owner == null || !owner.equals(player.getUuid())) {
                continue;
            }
            if (!tokenMatches(mobUuid, token) || isExpired(mobUuid, player)) {
                continue;
            }
            Entity entity = world.getEntity(mobUuid);
            if (entity instanceof MobEntity mob) {
                return new Pending(mob, player);
            }
        }
        return null;
    }

    /**
     * Whether the stored request matches the token taken from a button.
     *
     * @param token 0 means "no token", which keeps the bare manual command usable
     */
    private static boolean tokenMatches(UUID mobUuid, long token) {
        if (token == 0L) {
            // Bare manual command: it means "the request I currently have".
            return true;
        }
        Long current = REQUEST_IDS.get(mobUuid);
        return current != null && current == token;
    }

    /** Drop the id of a request that has ended. */
    private static void clearRequestId(UUID mobUuid) {
        INTENTS.remove(mobUuid);
        REQUEST_IDS.remove(mobUuid);
        REQUEST_PLAYERS.remove(mobUuid);
        REQUEST_WORLDS.remove(mobUuid);
    }

    /** Drop every request id, used when the server stops. */
    public static void clearRequestIds() {
        INTENTS.clear();
        REQUEST_PLAYERS.clear();
        REQUEST_IDS.clear();
        REQUEST_WORLDS.clear();
    }

    /** Whether the stored request has already passed its deadline. */
    private static boolean isExpired(UUID mobUuid, ServerPlayerEntity player) {
        long expireTick = NonBridge.requestExpireTick(mobUuid);
        return RequestAnswerRules.expired(player.getEntityWorld().getTime(), expireTick);
    }

    /** Tell the player that a button belonged to a request which is already over. */
    public static void reportStaleRequest(ServerPlayerEntity player) {
        com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "request_stale").formatted(Formatting.GRAY), false);
    }

    /** Whether this player has an unanswered request. */
    public static boolean hasPending(ServerPlayerEntity player) {
        for (UUID mobUuid : NonBridge.pendingMobIds()) {
            UUID owner = NonBridge.requestPlayer(mobUuid);
            if (owner != null && owner.equals(player.getUuid())) {
                return true;
            }
        }
        return false;
    }

    /** Whether this creature is waiting for an answer. */
    public static boolean isWaiting(UUID mobUuid) {
        return WAITING.contains(mobUuid);
    }

    /** Identity check for the display mirror; never changes request or relationship state. */
    public static boolean isWaitingFor(UUID mobUuid, UUID playerUuid) {
        return WAITING.contains(mobUuid) && playerUuid.equals(REQUEST_PLAYERS.get(mobUuid))
                && playerUuid.equals(NonBridge.requestPlayer(mobUuid));
    }

    /** Creature worlds are always server worlds in practice, but the API only promises World. */
    private static ServerWorld asServerWorld(Entity entity) {
        return entity.getEntityWorld() instanceof ServerWorld serverWorld ? serverWorld : null;
    }

    private record Pending(MobEntity mob, ServerPlayerEntity player) {
    }
}

