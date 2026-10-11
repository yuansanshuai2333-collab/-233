package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.config.AffinityConfig;
import com.nonid.affinity.data.AffinityHolder;
import com.nonid.affinity.data.CreatureAffinityData;
import com.nonid.affinity.data.PlayerAffinity;
import com.nonid.affinity.non.NonBridge;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

/**
 * Single business entry point of the affinity system.
 *
 * <p>Every read and write goes through here so that value clamping, friend-threshold handling,
 * taming and player feedback stay consistent.
 */
public final class AffinityService {

    private static final String KEY = "message.noncreatureaffinity.";
    private static final String REASON = "reason.noncreatureaffinity.";

    /** Reason tag for a rejected request. */
    public static final String REJECT_REASON = "rejected";
    /** Reason tag for the extra loss charged when the player breaks out of a forced mating. */
    public static final String ESCAPE_REASON = "escape";
    /** Reason tag of a completed mating the player did not start. */
    public static final String MATING_REASON = "mating";
    /** Reason tag of a completed mating the player started. */
    public static final String PLAYER_INITIATED_REASON = "player_initiated";

    /**
     * The chat line the cap gift of the CURRENT settlement still owes, or null when nothing is owed.
     *
     * <p>Two different facts live here, and conflating them was a defect:
     *
     * <ol>
     *   <li><b>a token was awarded at all</b> -- true for EVERY capped mating, not only the first;</li>
     *   <li><b>which line to print</b> -- the full explanation only for the first token ever, a shorter one
     *       afterwards.</li>
     * </ol>
     *
     * <p>The line is built where the token is awarded (inside {@link #addAffinity}) and only SENT by the
     * settlement, because the settlement may hand the player a second reward for the same completion (a
     * villager's ware). Sending both produced two chat lines for one reward, which reads as two tokens; the
     * deferred line is therefore merged into the ware's sentence when there is one. Gating that deferral on
     * "first token ever" instead would silently drop the announcement for every later capped mating, which is
     * why the message and the flag are decided together here rather than by two separate flags.
     *
     * <p>Safe as static state because the settlement runs on the server thread and every settlement resets it
     * before it starts.
     */
    private static volatile Text capGiftAnnouncement;

    /**
     * Whether this settlement is handing out the FIRST token this relation ever received.
     *
     * <p>Set by the settlement from the relation, because the relation is not available at the point the line
     * is actually sent.
     */
    private static volatile boolean capGiftFirstEver;

    /** Reset the per-settlement announcement state. Called at the start of every settlement. */
    public static void beginSettlement() {
        capGiftAnnouncement = null;
        capGiftFirstEver = false;
    }

    /** Whether a cap gift from the current settlement still has to be announced. */
    public static boolean isCapGiftReportPending() {
        return capGiftAnnouncement != null;
    }

    /** The line the pending cap gift owes, or null when nothing is owed. */
    public static Text pendingCapGiftMessage() {
        return capGiftAnnouncement;
    }

    /** Whether the pending cap gift is the first token this relation ever received. */
    public static boolean isCapGiftFirstEver() {
        return capGiftFirstEver;
    }

    /** The translation key of the line the pending cap gift owes, or null when nothing is owed. */
    static String pendingCapGiftKey() {
        if (capGiftAnnouncement == null) {
            return null;
        }
        Object content = capGiftAnnouncement.getContent();
        if (content instanceof net.minecraft.text.TranslatableTextContent translatable) {
            return translatable.getKey();
        }
        // MutableText#formatted wraps the content in a style-carrying text, so the key is looked up through the
        // siblings the wrapper exposes; this keeps the accessor honest instead of assuming one shape.
        for (Text sibling : capGiftAnnouncement.getSiblings()) {
            if (sibling.getContent() instanceof net.minecraft.text.TranslatableTextContent translatable) {
                return translatable.getKey();
            }
        }
        return null;
    }

    /**
     * Record that the current settlement awarded a token, and build the line it owes.
     *
     * @param creatureName the creature's name, already copied by the caller
     * @param firstEver    whether this is the first token this relation ever received, which decides whether the
     *                     full explanation or the shorter repeat line is printed
     */
    static void oweCapGiftAnnouncement(Text creatureName, boolean firstEver) {
        capGiftFirstEver = firstEver;
        capGiftAnnouncement = Text.translatable(
                        KEY + (firstEver ? "affinity_capped_gift" : "affinity_capped_gift_repeat"), creatureName)
                .formatted(Formatting.GOLD);
    }

    /**
     * Record that the current settlement awarded a token from this creature.
     *
     * <p>The production entry point; kept apart from the message builder so a test can drive the builder with a
     * name it controls instead of needing a live entity.
     */
    private static void oweCapGiftAnnouncement(Entity creature, boolean firstEver) {
        oweCapGiftAnnouncement(creature.getName().copy(), firstEver);
    }

    /** The pending cap gift has been announced (alone or merged), so it must not be repeated. */
    public static void clearCapGiftReportPending() {
        capGiftAnnouncement = null;
    }

    private AffinityService() {
    }

    /**
     * Whether a positive change with this reason may earn a token at the close-friend threshold.
     *
     * <p>Only a COMPLETED mating qualifies. This is the gate that keeps a struggle from paying out a token:
     * the escape path passes {@link #ESCAPE_REASON} and a negative delta, and a rejection passes
     * {@link #REJECT_REASON}, so neither can reach the gift.
     */
    public static boolean isMatingGainReason(String reason) {
        return MATING_REASON.equals(reason) || PLAYER_INITIATED_REASON.equals(reason);
    }

    // ------------------------------------------------------------------
    // Basic access
    // ------------------------------------------------------------------

    /** Relation of one player toward one creature, or null when none exists yet. */
    @Nullable
    public static PlayerAffinity getRelation(Entity creature, PlayerEntity player) {
        if (creature == null || player == null || !(creature instanceof AffinityHolder holder)) {
            return null;
        }
        CreatureAffinityData data = holder.peekAffinityData();
        return data == null ? null : data.get(player.getUuid());
    }

    /** Relation of one player toward one creature, created on demand. */
    @Nullable
    public static PlayerAffinity relationOrCreate(Entity creature, PlayerEntity player, AffinityConfig config) {
        if (!(creature instanceof AffinityHolder holder)) {
            return null;
        }
        return holder.affinityData().getOrCreate(player.getUuid(), config.initialAffinity);
    }

    public static int getAffinity(Entity creature, PlayerEntity player) {
        PlayerAffinity relation = getRelation(creature, player);
        if (relation != null) {
            return relation.getAffinity();
        }
        return NonCreatureAffinity.getConfig().initialAffinity;
    }

    /** Whether the creature has reached friend status with this player. */
    public static boolean isFriend(Entity creature, PlayerEntity player, AffinityConfig config) {
        PlayerAffinity relation = getRelation(creature, player);
        if (relation == null) {
            return false;
        }
        return RelationshipStages.friend(relation.getAffinity(), config);
    }

    /** Whether this module already turned the creature into a friendly/tamed unit. */
    public static boolean isFriendly(Entity creature, PlayerEntity player) {
        PlayerAffinity relation = getRelation(creature, player);
        return relation != null && RelationshipStages.friend(relation.getAffinity(), NonCreatureAffinity.getConfig());
    }

    // ------------------------------------------------------------------
    // Affinity changes
    // ------------------------------------------------------------------

    /**
     * Change affinity by a delta and run all follow-up logic.
     *
     * @param delta  signed change
     * @param reason translation key suffix describing the cause
     * @return the change that was actually applied
     */
    public static int addAffinity(Entity creature, PlayerEntity player, int delta, String reason) {
        if (ReplayIsolation.protects(creature == null ? null : creature.getUuid(), player == null ? null : player.getUuid())) return 0;
        AffinityConfig config = NonCreatureAffinity.getConfig();
        PlayerAffinity relation = relationOrCreate(creature, player, config);
        if (relation == null) {
            return 0;
        }
        int before = relation.getAffinity();
        int applied = relation.addAffinity(delta, config.minAffinity, config.maxAffinity);
        CompanionArchive.stageChanged(creature, player, before, relation.getAffinity(), config, reason);
        if (applied == 0 || CompletionRewardRules.earnsToken(delta, relation.getAffinity(),
                config.friendThreshold, isMatingGainReason(reason))) {
            // The threshold check must still run: a creature that reached the required value by
            // being clamped at the cap would otherwise never become a friend.
            checkFriendThreshold(creature, player, relation, config);
            if ((delta > 0 || delta == 0 && isMatingGainReason(reason)) && RelationshipStages.friend(relation.getAffinity(), config)
                    && player instanceof ServerPlayerEntity serverPlayer) {
                // Every completed interaction at the current close-friend threshold earns one token.
                // A configured zero affinity gain does not disable this independent completion reward.
                // The numeric cap is not a separate reward stage.
                //
                // Restricted to mating causes: gains also arrive from animation peaks, and without
                // this check one mating would hand over several tokens.
                // Restricted to COMPLETED matings: gains also arrive from animation peaks, and a struggle must
                // never be thanked with a token, so the gate is the shared predicate rather than two literals.
                boolean fromMating = isMatingGainReason(reason);
                if (fromMating && giveCapGift(serverPlayer)) {
                    // Only counted after the item was really handed over.
                    boolean firstEver = !relation.isCapGiftGiven();
                    relation.setCapGiftGiven(true);
                    relation.addTokenReceived();
                    FriendshipTokenFeedback.sent(serverPlayer, creature);
                    RelationshipBond.add(creature, player, 0.1);
                    // Logged because "did an escape hand out a token?" cannot be answered from an inventory
                    // snapshot in the log, and this is the only place a token is created.
                    NonCreatureAffinity.LOGGER.info(
                            "[NoN Affinity] friendship token handed to {} for {}: reason={} tokens={}"
                                    + " (first={})",
                            serverPlayer.getName().getString(), creature.getName().getString(), reason,
                            relation.getTokensReceived(), firstEver);
                    // EVERY capped mating owes an announcement -- only the WORDING depends on whether this is
                    // the first token. Deferred so a second reward from the same settlement can merge into it.
                    oweCapGiftAnnouncement(creature, firstEver);
                } else if (!fromMating && relation.getAffinity() >= config.maxAffinity
                        && !net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(serverPlayer,
                                com.nonid.affinity.net.AffinityIncreasePayload.ID)) {
                    // Peaks and other gains still report the cap plainly.
                    serverPlayer.sendMessage(Text.translatable(KEY + "affinity_capped",
                                    creature.getName())
                            .formatted(Formatting.GOLD), false);
                }
            }
            if (applied == 0) return 0;
        }
        checkFriendThreshold(creature, player, relation, config);
        if (config.notifyOnAffinityChange && player instanceof ServerPlayerEntity serverPlayer) {
            notifyAffinityChange(serverPlayer, creature, relation, before, applied, reason);
        }
        return applied;
    }

    public static void applyRecordedAttackLoss(net.minecraft.entity.mob.MobEntity creature,
                                              ServerPlayerEntity player, PlayerAffinity relation, int loss) {
        var cfg = NonCreatureAffinity.getConfig();
        int before = relation.getAffinity();
        int applied = relation.addAffinity(-loss, cfg.minAffinity, cfg.maxAffinity);
        CompanionArchive.stageChanged(creature, player, before, relation.getAffinity(), cfg, "friend_attack");
        if (applied != 0 && creature.isAlive() && player.isAlive()
                && player.getEntityWorld().getServer().getPlayerManager().getPlayer(player.getUuid()) == player
                && getRelation(creature, player) == relation) {
            checkFriendThreshold(creature, player, relation, cfg);
            if (cfg.notifyOnAffinityChange) notifyAffinityChange(player, creature, relation, before, applied, "friend_attack");
        }
    }
    public static double addPreciseAffinity(Entity creature, ServerPlayerEntity player, double delta) {
        if (!Double.isFinite(delta) || delta <= 0 || ReplayIsolation.protects(creature.getUuid(), player.getUuid())) return 0;
        var cfg = NonCreatureAffinity.getConfig(); var relation = getRelation(creature, player);
        if (relation == null) return 0;
        int before = relation.getAffinity();
        double applied = relation.addPreciseAffinity(delta, cfg.minAffinity, cfg.maxAffinity);
        checkFriendThreshold(creature, player, relation, cfg);
        CompanionArchive.stageChanged(creature, player, before, relation.getAffinity(), cfg, "reunion_heal");
        if (cfg.notifyOnAffinityChange && relation.getAffinity() != before)
            notifyAffinityChange(player, creature, relation, before, relation.getAffinity() - before, "reunion_heal");
        return applied;
    }

    /**
     * Hand one friendship token to the player.
     *
     * <p>Inserted into the inventory when there is room; otherwise dropped at the player's feet.
     * Exactly one of the two happens, so the item is never duplicated.
     *
     * @return whether the item was handed over
     */
    private static boolean giveCapGift(ServerPlayerEntity player) {
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(
                com.nonid.affinity.item.NonAffinityItems.FRIENDSHIP_TOKEN);
        if (stack.isEmpty()) {
            // The item is not registered (should not happen), so nothing can be given.
            return false;
        }
        if (player.getInventory().insertStack(stack) && stack.isEmpty()) {
            return true;   // fully placed in the inventory
        }
        // No room: drop the remainder rather than silently discarding it.
        return player.dropItem(stack, false) != null;
    }

    private static void notifyAffinityChange(ServerPlayerEntity player, Entity creature,
                                             PlayerAffinity relation, int before, int delta, String reason) {
        if (delta > 0) {
            var id = com.nonid.affinity.net.AffinityIncreasePayload.ID;
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, id)) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                        new com.nonid.affinity.net.AffinityIncreasePayload(creature.getUuid(),
                                creature.getEntityWorld().getRegistryKey().getValue(),
                                before, relation.getAffinity(),
                                NonCreatureAffinity.getConfig().maxAffinity));
                return;
            }
            // Older clients retain the existing notice; no gameplay depends on this optional channel.
        }
        if(delta<0&&GameplayFeedback.decrease(player,creature,before,relation.getAffinity(),NonCreatureAffinity.getConfig().maxAffinity,reason))return;
        Formatting color = delta > 0 ? Formatting.GREEN : Formatting.RED;
        String arrow = delta > 0 ? "▲" : "▼";
        String strengthKey = delta > 0
                ? "message.noncreatureaffinity.affinity_up"
                : "message.noncreatureaffinity.affinity_down";
        AffinityConfig config = NonCreatureAffinity.getConfig();
        // Chat (not the action bar): the action bar carries the detector readout, which refreshes
        // periodically and would overwrite this notice. Numbers are replaced by arrows plus a block
        // bar because digit glyphs render as boxes on this setup.
        player.sendMessage(Text.literal("♥ " + creature.getName().getString()
                        + "  " + arrow + " " + Text.translatable(strengthKey).getString()
                        + "  " + AffinityBars.bar(relation.getAffinity(), config.maxAffinity,
                                AffinityBars.DEFAULT_BLOCKS)
                        + "  " + Text.translatable(
                                RelationshipStages.key(relation.getAffinity(), config)).getString()
                        + "  (" + Text.translatable(REASON + reason).getString() + ")")
                .formatted(color), false);
    }

    /**
     * Promote the creature to a friend once the threshold is crossed.
     *
     * <p>The friend status and the companion bond are established through {@link RelationshipSync}, the SAME
     * entry vanilla taming and the old-save backfill use. This method previously did its own version of both
     * steps, which is how the two entrances could have drifted apart.
     *
     * <p>The vanilla tame (when the creature supports it and the configuration asks for it) happens BEFORE
     * the sync, so the sync sees the completed state and does not have to re-derive anything.
     */
    public static void checkFriendThreshold(Entity creature, PlayerEntity player,
                                            PlayerAffinity relation, AffinityConfig config) {
        if (creature instanceof MobEntity controlled && RelationshipStages.controls(relation.getAffinity(), config)) {
            CompanionService.bindOnBefriend(controlled, player.getUuid());
        }
        if (relation.isBefriended()) {
            return;
        }
        if (!RelationshipStages.friend(relation.getAffinity(), config)) {
            return;
        }
        if (!(creature instanceof MobEntity mob) || !(player instanceof ServerPlayerEntity serverPlayer)) {
            // Without both a mob and a real server player there is no companion to bind; the plain
            // threshold promotion still applies so the relation is not left half-promoted.
            relation.setBefriended(true);
            return;
        }
        // Validate before taming: a failed sync cannot undo a vanilla owner already written here.
        RelationshipRules.BondOutcome eligibility = RelationshipSync.eligibility(mob, serverPlayer.getUuid());
        if (!RelationshipRules.isBonded(eligibility)) {
            if (config.debugLogging) {
                NonCreatureAffinity.LOGGER.info("[NoN Affinity] refusing threshold taming of {}: {}",
                        mob.getUuid(), eligibility);
            }
            return;
        }
        boolean tameAttempted = false;
        boolean tamed = false;
        if (config.autoTameOnFriend) {
            tameAttempted = true;
            tamed = tameCreature(creature, player);
            if (tamed) {
                relation.setTamedByUs(true);
            }
        }
        RelationshipSync.SyncResult result =
                RelationshipSync.sync(mob, serverPlayer.getUuid(), serverPlayer, "affinity_threshold");
        if (RelationshipRules.isBonded(result.outcome())) {
            GameplayFeedback.relationship(serverPlayer,creature,Text.translatable(
                    KEY + (tamed ? "tamed" : "befriended"), creature.getName())
                    .formatted(Formatting.GOLD), false);
        } else if (config.debugLogging) {
            NonCreatureAffinity.LOGGER.info(
                    "[NoN Affinity] {} reached the friend threshold but was not bonded: {} (tameAttempted={})",
                    mob.getName().getString(), result.outcome(), tameAttempted);
        }
    }

    // ------------------------------------------------------------------
    // Mating settlement
    // ------------------------------------------------------------------

    /** Record one mating and grant affinity to the participating creature. */
    public static void recordMating(ServerWorld world, ServerPlayerEntity player, Entity creature,
                                    boolean playerInitiated, AffinityConfig config) {
        if (ReplayIsolation.protects(creature == null ? null : creature.getUuid(), player == null ? null : player.getUuid())) return;
        PlayerAffinity relation = relationOrCreate(creature, player, config);
        if (relation == null) {
            return;
        }
        // Every settlement starts with a clean announcement state, so a previous one that threw (or a gift with
        // nothing to announce) cannot leave a stale pending flag behind.
        beginSettlement();
        boolean friendBeforeCompletion = RelationshipStages.friend(relation.getAffinity(), config);
        relation.setLastMatingTick(world.getTime());
        relation.incrementMatingCount();
        RelationshipBond.add(creature, player, 0.3);
        int gain = CompletionRewardRules.completionGain(config.affinityPerMating,
                config.affinityBonusPlayerInitiated, playerInitiated);
        // The applied delta is logged with the resulting value, so a settlement can be checked against what
        // the player actually sees rather than against the configured gain (the range clamp can reduce it).
        int applied = addAffinity(creature, player, gain,
                playerInitiated ? PLAYER_INITIATED_REASON : MATING_REASON);
        NonCreatureAffinity.LOGGER.info(
                "[NoN Affinity] mating settled for {} + {}: playerInitiated={} requested={} applied={}"
                        + " affinity={} count={}",
                player.getName().getString(), creature.getName().getString(), playerInitiated, gain,
                applied, relation.getAffinity(), relation.getMatingCount());
        if (creature instanceof net.minecraft.entity.passive.VillagerEntity villager) {
            // The villager's ware gift merges the pending token line into its own sentence when both happen, so
            // one completion never looks like two rewards.
            boolean wareGiven = VillagerRelationshipBenefits.giveCompletionGift(villager, player,
                    playerInitiated, friendBeforeCompletion, isCapGiftReportPending());
            if (wareGiven) {
                clearCapGiftReportPending();
            }
        }
        if (isCapGiftReportPending()) {
            // No ware to merge into, so the token gets its own line. The message was built where the token was
            // awarded, so a later capped mating announces it too -- only the wording differs.
            if (!FriendshipTokenFeedback.supported(player)) player.sendMessage(pendingCapGiftMessage(), false);
            clearCapGiftReportPending();
        }
    }

    /** Record a rejected request. */
    public static void recordRejection(ServerWorld world, ServerPlayerEntity player, Entity creature,
                                       AffinityConfig config) {
        if (ReplayIsolation.protects(creature == null ? null : creature.getUuid(), player == null ? null : player.getUuid())) return;
        PlayerAffinity relation = relationOrCreate(creature, player, config);
        if (relation == null) {
            return;
        }
        relation.incrementRejectionCount();
        CompanionArchive.event(creature, player, com.nonid.affinity.data.CompanionArchiveData.Kind.REJECTED,
                "rejected:" + java.util.UUID.randomUUID(), "", -1, -1);
        RelationshipBond.add(creature, player, -0.05);
        relation.setRequestCooldownTicks(config.requestCooldownTicks);
        // The configured penalty is applied here and ONLY here. Breaking out of a forced mating later applies a
        // separate, explicitly configured loss (see recordForcedEscape); it is not a second rejection charge.
        int applied = config.affinityLossOnReject > 0
                ? addAffinity(creature, player, -config.affinityLossOnReject, REJECT_REASON)
                : 0;
        NonCreatureAffinity.LOGGER.info(
                "[NoN Affinity] request rejected by {} to {}: configuredPenalty={} applied={} affinity={}"
                        + " rejections={}",
                player.getName().getString(), creature.getName().getString(), config.affinityLossOnReject,
                applied, relation.getAffinity(), relation.getRejectionCount());
    }

    /**
     * Record that the player broke out of a FORCED mating (they fought the creature off).
     *
     * <p>Charges the configured escape loss and NOTHING ELSE: no mating count, no reward, and no friendship
     * token. The token is handed out only for a positive mating gain at the affinity cap (see
     * {@link #isMatingGainReason}), and this path passes {@link #ESCAPE_REASON} with a negative delta, so a
     * successful struggle can never produce one.
     *
     * @param loss the affinity to remove, as a positive magnitude; 0 means nothing is charged
     * @return the change that was actually applied (negative, or 0 when nothing was charged)
     */
    public static int recordForcedEscape(ServerWorld world, ServerPlayerEntity player, Entity creature,
                                         int loss, AffinityConfig config) {
        if (ReplayIsolation.protects(creature == null ? null : creature.getUuid(), player == null ? null : player.getUuid())) return 0;
        PlayerAffinity relation = relationOrCreate(creature, player, config);
        if (relation == null) {
            return 0;
        }
        int applied = loss > 0 ? addAffinity(creature, player, -loss, ESCAPE_REASON) : 0;
        NonCreatureAffinity.LOGGER.info(
                "[NoN Affinity] forced mating escaped by {} + {}: configuredEscapeLoss={} applied={}"
                        + " affinity={} (no reward, no friendship token)",
                player.getName().getString(), creature.getName().getString(), loss, applied,
                relation.getAffinity());
        return applied;
    }

    // ------------------------------------------------------------------
    // Lookup helpers
    // ------------------------------------------------------------------

    /** Nearest creature with the highest arousal, used by the request key. */
    @Nullable
    public static LivingEntity findBestPartner(ServerPlayerEntity player, AffinityConfig config, double range) {
        ServerWorld world = player.getEntityWorld();
        Box box = player.getBoundingBox().expand(range, range / 2.0, range);
        LivingEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (LivingEntity candidate : world.getEntitiesByClass(LivingEntity.class, box,
                entity -> entity != player && entity.isAlive() && !entity.isSpectator())) {
            if (NonBridge.isActorBusy(world, candidate)) {
                continue;
            }
            double distance = player.squaredDistanceTo(candidate);
            int energy = NonBridge.getEnergy(candidate);
            double score = energy - distance * 2.0;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /** Pick the bonded player a creature should approach for a request. */
    @Nullable
    public static ServerPlayerEntity findRequesterTarget(MobEntity mob, AffinityConfig config) {
        if (!(mob.getEntityWorld() instanceof ServerWorld world)) {
            return null;
        }
        List<ServerPlayerEntity> players = world.getEntitiesByClass(ServerPlayerEntity.class,
                mob.getBoundingBox().expand(config.requestRange),
                player -> player.isAlive() && !player.isSpectator());
        ServerPlayerEntity best = null;
        int bestAffinity = Integer.MIN_VALUE;
        for (ServerPlayerEntity player : players) {
            if (mob.squaredDistanceTo(player) > config.requestRange * config.requestRange) continue;
            PlayerAffinity relation = getRelation(mob, player);
            if (relation == null || !RequestSystem.relationshipAllowsRequest(mob, player, relation, config)) {
                continue;
            }
            if (relation.getRequestCooldownTicks() > 0) {
                continue;
            }
            if (relation.getAffinity() > bestAffinity) {
                bestAffinity = relation.getAffinity();
                best = player;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Offspring
    // ------------------------------------------------------------------

    /** Bind a newborn creature to its parent player. */
    public static void bindOffspring(ServerWorld world, ServerPlayerEntity parent, MobEntity child,
                                     AffinityConfig config, int parentGeneration) {
        CreatureAffinityData data = child instanceof AffinityHolder holder ? holder.affinityData() : null;
        if (data == null) {
            return;
        }
        if (config.offspringInheritGeneration) {
            data.setGeneration(OffspringAffinityRules.nextGeneration(parentGeneration));
        }
        int value = OffspringAffinityRules.initial(config.offspringBefriended, config.friendThreshold,
                config.initialAffinity, null, config.offspringAffinityPercentOfParent,
                config.minAffinity, config.maxAffinity);
        PlayerAffinity relation = data.getOrCreate(parent.getUuid(), value);
        relation.setAffinity(value, config.minAffinity, config.maxAffinity);
        relation.setBefriended(config.offspringBefriended);
        if (config.offspringBefriended) {
            relation.setBefriended(true);
            if (tameCreature(child, parent)) {
                relation.setTamedByUs(true);
            }
            // Same guarded entry as every other "becomes a friend" path.
            CompanionService.bindOnBefriend(child, parent.getUuid());
        }
        if (config.debugLogging) {
            NonCreatureAffinity.LOGGER.info("[NoN Affinity] offspring {} bound to {} (affinity {})",
                    child.getName().getString(), parent.getName().getString(), value);
        }
    }

    /**
     * Tame a creature, covering both creature families Minecraft uses for this:
     * {@link TameableEntity} (wolf, cat, parrot, ...) and {@link AbstractHorseEntity}
     * (horse, donkey, mule, llama, camel, ...). Horses are NOT TameableEntity, which is why they
     * were not being tamed before.
     *
     * <p>Never transfers ownership: taming an already-owned pet would steal it from its owner.
     *
     * @return whether the creature counts as tamed by us
     */
    public static boolean tameCreature(Entity creature, PlayerEntity owner) {
        if (owner == null) return false;
        if (creature instanceof MobEntity mob
                && !RelationshipRules.isBonded(RelationshipSync.eligibility(mob, owner.getUuid()))) return false;
        if (creature instanceof TameableEntity tameable) {
            if (tameable.isTamed()) {
                // An already-tamed creature keeps whatever owner it has. A missing or unresolvable
                // reference means "no confirmed owner", NOT "free to claim".
                LivingEntity existing = resolveOwner(tameable.getOwnerReference(), creature.getEntityWorld());
                return existing instanceof PlayerEntity player && player.getUuid().equals(owner.getUuid());
            }
            tameable.setTamed(true, true);
            tameable.setOwner(owner);
            return true;
        }
        if (creature instanceof AbstractHorseEntity horse) {
            if (horse.isTame()) {
                // Same rule as above: never reassign ownership from a failed lookup.
                LivingEntity existing = resolveOwner(horse.getOwnerReference(), creature.getEntityWorld());
                return existing instanceof PlayerEntity player && player.getUuid().equals(owner.getUuid());
            }
            horse.setTame(true);
            horse.setOwner(owner);
            return true;
        }
        return false;
    }

    /**
     * Resolve a lazily stored owner without ever dereferencing a missing reference.
     *
     * <p>Package-visible so the null-reference path is covered by a unit test.
     *
     * @return the owner, or null when the reference is missing or cannot be resolved
     */
    @Nullable
    static LivingEntity resolveOwner(@Nullable net.minecraft.entity.LazyEntityReference<LivingEntity> reference,
                                     net.minecraft.world.World world) {
        return reference == null ? null : reference.getEntityByClass(world, LivingEntity.class);
    }

    /** Whether a creature is already tamed, across both families. */
    public static boolean isTamedCreature(Entity creature) {
        if (creature instanceof TameableEntity tameable) {
            return tameable.isTamed();
        }
        if (creature instanceof AbstractHorseEntity horse) {
            return horse.isTame();
        }
        return false;
    }

    /** Whether the mob should act as a defender for its bonded player. */
    public static boolean isDefender(MobEntity mob, AffinityConfig config) {
        if (!config.friendlyMobsDefendPlayer) {
            return false;
        }
        if (!(mob instanceof AffinityHolder holder)) {
            return false;
        }
        CreatureAffinityData data = holder.peekAffinityData();
        return data != null && data.entries().values().stream()
                .anyMatch(relation -> RelationshipStages.defends(relation.getAffinity(), config));
    }

    /** Short uuid text used in logs. */
    public static String shortId(UUID uuid) {
        String value = uuid.toString();
        return value.substring(0, 8);
    }
}

