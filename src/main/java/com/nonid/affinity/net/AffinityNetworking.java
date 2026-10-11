package com.nonid.affinity.net;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.config.AffinityConfig;
import com.nonid.affinity.data.PlayerAffinity;
import com.nonid.affinity.non.NonBridge;
import java.util.List;
import java.util.Map;
import com.nonid.affinity.service.AffinityService;
import com.nonid.affinity.service.TradeWindows;
import com.nonid.affinity.service.VillagerFreeTrades;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Networking: two minimal channels, the key request (C2S) and request state (S2C).
 *
 * <p>Every decision is made on the server; the client only sends a parameterless request.
 */
public final class AffinityNetworking {

    private static final String KEY = "message.noncreatureaffinity.";

    private AffinityNetworking() {
    }

    /** Register payload types on both sides. */
    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(FriendshipTokenPayload.ID, FriendshipTokenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VillagerGiftPayload.ID, VillagerGiftPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReunionChargePayload.ID, ReunionChargePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReunionFullPayload.ID, ReunionFullPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReunionSuccessPayload.ID, ReunionSuccessPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReunionNoticePayload.ID, ReunionNoticePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GameplayNoticePayload.ID, GameplayNoticePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CompanionModeFeedbackPayload.ID, CompanionModeFeedbackPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AffinityIncreasePayload.ID, AffinityIncreasePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReunionEffectPayload.ID, ReunionEffectPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReunionResultPayload.ID, ReunionResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RelationshipStagePayload.ID, RelationshipStagePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FamilyLabelPayload.ID, FamilyLabelPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MatingRequestPayload.ID, MatingRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RequestStatePayload.ID, RequestStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestAnswerPayload.ID, RequestAnswerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AnimationMenuRequestPayload.ID, AnimationMenuRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AnimationChoicePayload.ID, AnimationChoicePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AnimationMenuPayload.ID, AnimationMenuPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AffinityQueryPayload.ID, AffinityQueryPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AffinityDataPayload.ID, AffinityDataPayload.CODEC);
        // The trade window: the client binds to the window it opened (C2S), the server answers with that window's
        // instance identity and then every status (S2C), and the client reports the close so the server's record
        // does not outlive the screen.
        PayloadTypeRegistry.playC2S().register(FreeTradeBindRequestPayload.ID, FreeTradeBindRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(FreeTradeClosePayload.ID, FreeTradeClosePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FreeTradeWindowPayload.ID, FreeTradeWindowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FreeTradeStatusPayload.ID, FreeTradeStatusPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BreakFreePayload.ID, BreakFreePayload.CODEC);
    }

    /** Register global receivers once during mod initialization, after payload types. */
    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(RequestAnswerPayload.ID, (payload, context) ->
                context.server().execute(() -> com.nonid.affinity.service.RequestSystem.answer(context.player(), payload)));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(RequestHudSync::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> RequestHudSync.clear());
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RequestHudSync.forget(handler.player.getUuid()));
        ServerPlayNetworking.registerGlobalReceiver(MatingRequestPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> handleMatingRequest(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(AnimationMenuRequestPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> handleMenuRequest(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(AffinityQueryPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> handleAffinityQuery(context.player(), payload.targetUuid())));
        ServerPlayNetworking.registerGlobalReceiver(AnimationChoicePayload.ID,
                (payload, context) -> context.server().execute(
                        () -> handleAnimationChoice(context.player(), payload.animationId())));
        // Breaking free is resolved entirely from the server's own session state: the payload names no target.
        ServerPlayNetworking.registerGlobalReceiver(BreakFreePayload.ID,
                (payload, context) -> context.server().execute(
                        () -> com.nonid.affinity.non.NonBridge.breakFree(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(FreeTradeBindRequestPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> handleTradeBind(context.player(), payload.windowId(), payload.requestId())));
        ServerPlayNetworking.registerGlobalReceiver(FreeTradeClosePayload.ID,
                (payload, context) -> context.server().execute(() -> handleTradeClose(context.player(),
                        payload.windowId(), payload.windowInstanceToken(), payload.requestId())));
    }

    // ------------------------------------------------------------------
    // The trade window
    // ------------------------------------------------------------------

    /**
     * Answer a client that has opened a trade window: issue the window's identity, then its first status.
     *
     * <p>The window id in the payload is a CLAIM, checked against the handler the server itself has this player in.
     * Without that check a client could name any number and be handed a token for a window it does not have -- and
     * the token is the one value that makes a status believable.
     *
     * <p>The request id is echoed, not trusted: it identifies the client's QUESTION, so the answer can be matched
     * to the opening it belongs to. When the request is one the server is already holding for this same window,
     * the existing token is returned instead of a new one -- a retry, or a screen initialised twice, must not give
     * one open window two identities. When it is a DIFFERENT request for the same syncId, the window is treated as
     * newly opened and gets a fresh token: that is the reopened-villager case the token exists for.
     *
     * <p>The two messages go out in this order on purpose: the client refuses every status until it has been told
     * which window it belongs to, so a status sent first would discard the very answer that makes this visible.
     */
    private static void handleTradeBind(ServerPlayerEntity player, int windowId, long requestId) {
        if (!(player.currentScreenHandler instanceof net.minecraft.screen.MerchantScreenHandler handler)
                || handler.syncId != windowId) {
            return;
        }
        VillagerEntity villager = VillagerFreeTrades.villagerOfWindow(player);
        if (villager == null) {
            // A wandering trader is a merchant too, but it has no relationship with anybody: no window, no status.
            return;
        }
        TradeWindows.WindowState window = TradeWindows.register(player, windowId, villager, requestId);
        if (ServerPlayNetworking.canSend(player, FreeTradeWindowPayload.ID)) {
            ServerPlayNetworking.send(player, new FreeTradeWindowPayload(window.windowId(), window.villagerUuid(),
                    window.instanceToken(), window.requestId()));
        }
        // The selection is read from the server's own handler when nothing has been observed yet: a window that
        // opens onto recipe 0 never fires the selection hook, and would otherwise be announced as "no recipe".
        VillagerFreeTrades.sendStatus(player, window,
                selectionFor(player, windowId));
    }

    /**
     * The recipe a freshly bound window is showing.
     *
     * <p>The recorded selection wins when there is one (the player has moved the selection at least once); the
     * handler's own value is the fallback for the first moment of a window. Both are the SERVER's values.
     */
    private static int selectionFor(ServerPlayerEntity player, int windowId) {
        int recorded = TradeWindows.selectedOfferIndex(player, windowId);
        return recorded >= 0 ? recorded : VillagerFreeTrades.selectionOfHandler(player);
    }

    /**
     * Drop the server's record of a window the client has closed.
     *
     * <p><b>The identity is checked, not just the number.</b> The syncId is reused by vanilla, so a close built only
     * from it can match a window opened AFTER the one the message means -- the record for the window the player is
     * looking at would be dropped, and that window would stop receiving statuses until it asked to be bound again.
     * The message therefore names the instance token and the opening's request id as well, and all three must match
     * the record the server holds, or nothing is dropped.
     *
     * <p>A message that does not match is not an error to report: it is a late close for a window that is already
     * gone, which needs no action. And the server does not depend on this message at all -- the per-tick check sees
     * for itself that the player has left the handler -- so a refused close only delays the cleanup.
     */
    private static void handleTradeClose(ServerPlayerEntity player, int windowId, long instanceToken,
                                         long requestId) {
        TradeWindows.closeMatching(player, windowId, instanceToken, requestId);
    }

    // ------------------------------------------------------------------
    // Voluntary mating
    // ------------------------------------------------------------------

    private static void handleMatingRequest(ServerPlayerEntity player) {
        if (!player.isAlive() || player.isSpectator()) return;
        AffinityConfig config = NonCreatureAffinity.getConfig();
        LivingEntity target = resolveTarget(player, config);
        if (target == null) {
            com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "no_target").formatted(Formatting.GRAY), true);
            return;
        }
        if (target.isBaby()) return;
        ServerWorld world = player.getEntityWorld();
        if (NonBridge.isActorBusy(world, player)) {
            com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "player_busy").formatted(Formatting.GRAY), true);
            return;
        }
        if (NonBridge.isActorBusy(world, target)) {
            com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "target_busy").formatted(Formatting.GRAY), true);
            return;
        }
        if (!NonBridge.isAvailable()) {
            com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "no_non").formatted(Formatting.RED), true);
            return;
        }
        // Addon-initiated starts already pass the explicit-start gate. Preserve pending requests
        // until a start succeeds, and never consume a request addressed to another player.
        NonBridge.clearMatingRefusal();
        boolean started = NonBridge.startVoluntaryMating(world, player, target);
        if (!started) {
            // The server has already established WHY it refused -- no usable animation at all, conditions
            // unmet, a participant busy, or the engine throwing. Saying only "it failed" hides that, so the
            // player is told something specific and actionable. Nothing has started, no affinity changed
            // and existing requests stay intact.
            var refusal = com.nonid.affinity.non.MatingRefusal.current();
            if (refusal == com.nonid.affinity.non.MatingRefusal.NO_ANIMATION) {
                com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "cannot_start_no_animation")
                        .formatted(Formatting.GRAY), true);
            } else if (refusal == com.nonid.affinity.non.MatingRefusal.BUSY) {
                com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "cannot_start_busy", target.getName())
                        .formatted(Formatting.GRAY), true);
            } else {
                com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "cannot_start", target.getName())
                        .formatted(Formatting.GRAY), true);
            }
            if (!NonBridge.loadedDefinitionIds().isEmpty()) {
                NonCreatureAffinity.LOGGER.info(
                        "[NoN Affinity] request key failed for {} + {}: refusal={}; see the preceding log "
                                + "lines for the reason.",
                        player.getName().getString(), target.getName().getString(), refusal);
            }
            return;
        }
        com.nonid.affinity.service.RequestSystem.clearForExplicitStart(target, player);
        int affinity = AffinityService.getAffinity(target, player);
        com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "voluntary_started", target.getName(), affinity)
                .formatted(Formatting.LIGHT_PURPLE), true);
    }

    /**
     * Answer a detector query with the authoritative values for the aimed creature.
     *
     * <p>Rate limited per player: the client asks while the detector is on, and this must not become
     * a free entity scan.
     */
    private static void handleAffinityQuery(ServerPlayerEntity player, java.util.UUID targetUuid) {
        if (!allowDetectorQuery(player.getUuid(), player.getEntityWorld().getServer().getTicks())) return;

        AffinityConfig config = NonCreatureAffinity.getConfig();

        // Validate the requested creature rather than trusting the client, and rather than
        // re-resolving from the player's view (which could pick a different creature).
        net.minecraft.entity.Entity resolved = player.getEntityWorld().getEntity(targetUuid);
        if (!(resolved instanceof LivingEntity target) || !target.isAlive()) {
            replyInvalid(player, targetUuid, config);
            return;
        }
        if (player.squaredDistanceTo(target) > (double) MAX_QUERY_DISTANCE * MAX_QUERY_DISTANCE) {
            // Beyond the range the client itself probes. Refusing here is what previously made
            // creatures between interactRange * 2 and 32 blocks look like "no relation": the client
            // asked, the server refused, and the refusal is indistinguishable from having no data.
            replyInvalid(player, targetUuid, config);
            return;
        }
        PlayerAffinity relation = AffinityService.getRelation(target, player);
        // The mode is only meaningful to the creature's own companion owner, and only mobs can be
        // companions.
        String mode = "";
        if (target instanceof net.minecraft.entity.mob.MobEntity targetMob) {
            java.util.UUID companionOwner =
                    com.nonid.affinity.service.CompanionService.companionOwner(targetMob);
            if (!com.nonid.affinity.service.CompanionService.usesNativeControl(targetMob)
                    && companionOwner != null && companionOwner.equals(player.getUuid())
                    && com.nonid.affinity.service.RelationshipBehavior.controls(targetMob, player.getUuid())) {
                mode = com.nonid.affinity.service.CompanionService.modeOf(targetMob).name();
            }
        }
        AffinityDataPayload reply = relation == null
                ? new AffinityDataPayload(target.getUuid(), false, config.initialAffinity,
                        config.maxAffinity, 0, false, mode, config.friendThreshold, 0)
                : new AffinityDataPayload(target.getUuid(), true, relation.getAffinity(),
                        config.maxAffinity, relation.getMatingCount(),
                        com.nonid.affinity.service.RelationshipStages.friend(relation.getAffinity(), config), mode,
                        config.friendThreshold, relation.getTokensReceived());
        if (ServerPlayNetworking.canSend(player, AffinityDataPayload.ID)) {
            if(!com.nonid.affinity.item.AffinityDetectorItem.isHeldBy(player))
            reply=new AffinityDataPayload(reply.targetUuid(),reply.found(),reply.affinity(),reply.maxAffinity(),-1,reply.friend(),"",reply.friendThreshold(),-1);
        ServerPlayNetworking.send(player, reply);
        }
        if (ServerPlayNetworking.canSend(player, FamilyLabelPayload.ID)) {
            ServerPlayNetworking.send(player, new FamilyLabelPayload(target.getUuid(),
                    com.nonid.affinity.service.FamilyService.label(target, player)));
        }
        if (ServerPlayNetworking.canSend(player, RelationshipStagePayload.ID)) {
            ServerPlayNetworking.send(player, new RelationshipStagePayload(target.getUuid(),
                    com.nonid.affinity.service.RelationshipStages.of(relation == null ? 0 : relation.getAffinity(), config)
                            .name().toLowerCase(java.util.Locale.ROOT)));
        }
    }

    /** Target each player's animation menu was opened for, with an expiry. */
    private static final Map<java.util.UUID, MenuSession> MENU_TARGETS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Maximum distance a detector query is answered for, in blocks.
     *
     * <p>Must stay in step with {@code AffinityLabelRenderer.MAX_DISTANCE} on the client: if the
     * server answers a narrower range, the client shows "no relation" for creatures it can see.
     */
    private static final int MAX_QUERY_DISTANCE = 32;

    /** At most this many animations are sent in one menu payload. */
    private static final int MAX_MENU_ENTRIES = 128;

    /** Character budget for one menu payload, well below the packet limit. */
    private static final int MAX_MENU_CHARS = 4096;

    /** How long an opened animation menu stays valid. */
    private static final long MENU_TIMEOUT_TICKS = 200L;

    /** An opened menu: which creature it was for, and when it stops being valid. */
    private record MenuSession(java.util.UUID targetUuid, long expireTick) {
    }

    /** Forget per-player state (disconnect or server stop). */
    public static void clearPlayerState(java.util.UUID playerUuid) {
        com.nonid.affinity.command.AffinityCommand.clearPlayerState(playerUuid);
        MENU_TARGETS.remove(playerUuid);
        LAST_QUERY_TICK.remove(playerUuid);
        // The trade window record goes too: a window cannot survive its connection, and leaving it behind would
        // let a reused syncId match a record whose player has left.
        VillagerFreeTrades.forgetWindow(playerUuid);
    }

    /** Forget all per-player state. */
    public static void clearAllState() {
        com.nonid.affinity.command.AffinityCommand.clearRuntimeState();
        MENU_TARGETS.clear();
        LAST_QUERY_TICK.clear();
        TradeWindows.forgetAll();
    }

    /** Answer a query for a creature that is gone or too far away. */
    private static void replyInvalid(ServerPlayerEntity player, java.util.UUID targetUuid,
                                     AffinityConfig config) {
        if (ServerPlayNetworking.canSend(player, AffinityDataPayload.ID)) {
            ServerPlayNetworking.send(player, new AffinityDataPayload(targetUuid, false,
                    config.initialAffinity, config.maxAffinity, 0, false, "", config.friendThreshold, 0));
        }
    }

    /** Per-player rate limit for detector queries. */
    private static final Map<java.util.UUID, Integer> LAST_QUERY_TICK = new java.util.concurrent.ConcurrentHashMap<>();

    /** Server ticks are shared across dimensions. Integer subtraction also tolerates counter wrap. */
    static boolean allowDetectorQuery(java.util.UUID player, int serverTick) {
        Integer last = LAST_QUERY_TICK.get(player);
        if (last != null) {
            int elapsed = serverTick - last;
            if (elapsed >= 0 && elapsed < QUERY_INTERVAL_TICKS) return false;
        }
        LAST_QUERY_TICK.put(player, serverTick);
        return true;
    }

    /** Minimum ticks between detector queries from one player. */
    private static final long QUERY_INTERVAL_TICKS = 10L;

    /** Send the player every animation matching the creature they are aiming at. */
    private static void handleMenuRequest(ServerPlayerEntity player) {
        AffinityConfig config = NonCreatureAffinity.getConfig();
        LivingEntity target = resolveTarget(player, config);
        if (target == null) {
            com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "no_target").formatted(Formatting.GRAY), true);
            return;
        }
        if (target.isBaby() || !target.isAlive() || player.isSpectator()) return;
        if (!NonBridge.openNativeSelection(player, target)) {
            com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "interaction_failure.unavailable"), false);
        }
    }
    /** Start the animation the player picked from the menu. */
    private static void handleAnimationChoice(ServerPlayerEntity player, String animationId) {
        // Use the target the menu was opened for, and fail safely if it is no longer valid.
        MenuSession session = MENU_TARGETS.remove(player.getUuid());
        if (session == null || player.getEntityWorld().getTime() > session.expireTick()) {
            return;
        }
        java.util.UUID targetUuid = session.targetUuid();
        net.minecraft.entity.Entity resolved = player.getEntityWorld().getEntity(targetUuid);
        if (!(resolved instanceof LivingEntity target) || !target.isAlive()) {
            com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "target_gone").formatted(Formatting.RED), false);
            return;
        }
        Identifier id = Identifier.tryParse(animationId);
        if (id == null) {
            return;
        }
        try (var feedback = com.nonid.affinity.service.InteractionStartFeedback.begin()) {
            boolean started = NonBridge.startChosen(player.getEntityWorld(), player, target, id);
            if (started) {
                com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable(KEY + "chosen_started", id.toString())
                        .formatted(Formatting.GREEN), true);
            } else {
                String reasonKey = feedback.reason().name().toLowerCase(java.util.Locale.ROOT);
                Text message = Text.translatable(KEY + "interaction_failure." + reasonKey)
                        .formatted(Formatting.RED);
                com.nonid.affinity.service.GameplayFeedback.send(player,message, true);
                com.nonid.affinity.service.GameplayFeedback.send(player,message, false);
                com.nonid.affinity.NonCreatureAffinity.LOGGER.warn(
                        "[NoN Affinity] interaction refused: animation={} target={} reason={}",
                        id, target.getUuid(), feedback.reason());
            }
        }
    }

    /** Resolve the intended target: crosshair hit first, then nearest creature in the view cone. */
    @Nullable
    private static LivingEntity resolveTarget(ServerPlayerEntity player, AffinityConfig config) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F);
        ServerWorld world = player.getEntityWorld();
        Vec3d end = eye.add(look.multiply(config.interactRange));
        var blockHit = world.raycast(new net.minecraft.world.RaycastContext(eye, end,
                net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
        double distanceLimit = blockHit.getType() == HitResult.Type.MISS
                ? config.interactRange * config.interactRange : eye.squaredDistanceTo(blockHit.getPos());
        // Entity.raycast only finds blocks. Use the entity ray before the view-cone fallback.
        EntityHitResult entityHit = net.minecraft.entity.projectile.ProjectileUtil.raycast(player,
                eye, end, player.getBoundingBox().stretch(look.multiply(config.interactRange)).expand(1.0),
                entity -> entity instanceof LivingEntity living && isEligibleTarget(living), distanceLimit);
        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity living) return living;
        LivingEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Entity entity : world.getOtherEntities(player,
                player.getBoundingBox().expand(config.interactRange))) {
            if (!(entity instanceof LivingEntity living) || !isEligibleTarget(living)) {
                continue;
            }
            Vec3d toEntity = living.getEyePos().subtract(eye);
            double distance = toEntity.length();
            if (distance < 0.001 || distance > config.interactRange) {
                continue;
            }
            double dot = toEntity.normalize().dotProduct(look);
            if (dot < 0.5) {
                // Outside a roughly 60 degree cone: ignore.
                continue;
            }
            // Keep the existing aiming assistance, but never choose a creature through a wall.
            if (world.raycast(new net.minecraft.world.RaycastContext(eye, living.getEyePos(),
                    net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE, player)).getType()
                    != HitResult.Type.MISS) continue;
            double score = dot * 10.0 - distance;
            if (score > bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    private static boolean isEligibleTarget(LivingEntity living) {
        return living.isAlive()
                && !living.isSpectator()
                && !(living instanceof ServerPlayerEntity);
    }

    // ------------------------------------------------------------------
    // State sync
    // ------------------------------------------------------------------

    /** Cancelling a vanished entity must not require resolving that entity. */
    public static void clearRequestState(ServerPlayerEntity player, java.util.UUID mobUuid) {
        RequestHudSync.close(player, mobUuid);
    }

    /** Send request state to a player (expireTick 0 cancels it). */
    public static void sendRequestState(ServerPlayerEntity player, Entity mob, long expireTick,
                                        int affinity, int threshold) {
        if (expireTick <= 0) RequestHudSync.close(player, mob.getUuid());
        else RequestHudSync.show(player, mob, expireTick, affinity, threshold);
    }
}
