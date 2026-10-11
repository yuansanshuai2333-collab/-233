package com.nonid.affinity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.config.AffinityConfig;
import com.nonid.affinity.data.AffinityHolder;
import com.nonid.affinity.data.CreatureAffinityData;
import com.nonid.affinity.data.PlayerAffinity;
import com.nonid.affinity.non.NonBridge;
import net.minecraft.util.Identifier;
import net.minecraft.entity.LivingEntity;
import com.nonid.affinity.service.AffinityService;
import com.nonid.affinity.service.RequestSystem;
import com.nonid.affinity.service.RelationshipSync;
import com.nonid.affinity.service.RelationshipRules;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Command: /nonaffinity ...
 *
 * <ul>
 *   <li>accept / reject - answer a creature request (HUD buttons use the same path)</li>
 *   <li>info - show the affinity of the creature you are looking at</li>
 *   <li>set &lt;value&gt; - set affinity (OP)</li>
 *   <li>friend - promote the target to friend (OP)</li>
 *   <li>reload - reload the config (OP)</li>
 * </ul>
 */
public final class AffinityCommand {

    private static final String KEY = "message.noncreatureaffinity.";

    private AffinityCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("nonaffinity")
                .then(CommandManager.literal("accept")
                        .executes(context -> accept(context, null, 0L))
                        .then(CommandManager.literal("id")
                                .then(CommandManager.argument("token", LongArgumentType.longArg())
                                        .executes(context -> accept(context, null,
                                                LongArgumentType.getLong(context, "token")))))
                        .then(CommandManager.literal("mob")
                                .then(CommandManager.argument("mob", EntityArgumentType.entity())
                                        .executes(context -> accept(context,
                                                EntityArgumentType.getEntity(context, "mob"), 0L)))))
                .then(CommandManager.literal("reject")
                        .executes(context -> reject(context, null, 0L))
                        .then(CommandManager.literal("id")
                                .then(CommandManager.argument("token", LongArgumentType.longArg())
                                        .executes(context -> reject(context, null,
                                                LongArgumentType.getLong(context, "token")))))
                        .then(CommandManager.literal("mob")
                                .then(CommandManager.argument("mob", EntityArgumentType.entity())
                                        .executes(context -> reject(context,
                                                EntityArgumentType.getEntity(context, "mob"), 0L)))))
                .then(CommandManager.literal("info")
                        .executes(AffinityCommand::info))
                .then(CommandManager.literal("bond")
                        .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                        .then(CommandManager.argument("entity", EntityArgumentType.entity())
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(context -> {
                                            var entity = EntityArgumentType.getEntity(context, "entity");
                                            var player = EntityArgumentType.getPlayer(context, "player");
                                            var relation = AffinityService.getRelation(entity, player);
                                            context.getSource().sendFeedback(() -> Text.translatable(KEY + "bond_query",
                                                    entity.getName(), player.getName(),
                                                    String.format(java.util.Locale.ROOT, "%.6f", relation == null ? 0 : relation.getBond())), false);
                                            return 1;
                                        }))))
                .then(CommandManager.literal("bondgrowth")
                        .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                        .then(CommandManager.argument("percent", IntegerArgumentType.integer(25, 400))
                                .executes(context -> {
                                    var config = NonCreatureAffinity.getConfig().copy();
                                    config.bondGrowthPercent = IntegerArgumentType.getInteger(context, "percent");
                                    if (!config.trySave()) {
                                        context.getSource().sendError(Text.translatable(KEY + "bond_growth_failed")); return 0;
                                    }
                                    NonCreatureAffinity.applyConfig(config);
                                    context.getSource().sendFeedback(() -> Text.translatable(KEY + "bond_growth_set", config.bondGrowthPercent), true);
                                    return 1;
                                })))
                .then(CommandManager.literal("set")
                        .requires(source -> CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK).test(source))
                        .then(CommandManager.argument("value", IntegerArgumentType.integer())
                                .executes(context -> set(context, null))
                                .then(CommandManager.argument("entity", EntityArgumentType.entity())
                                        .executes(context -> set(context,
                                                EntityArgumentType.getEntity(context, "entity"))))))
                .then(CommandManager.literal("friend")
                        .requires(source -> CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK).test(source))
                        .executes(context -> forceFriend(context, null))
                        .then(CommandManager.argument("entity", EntityArgumentType.entity())
                                .executes(context -> forceFriend(context,
                                        EntityArgumentType.getEntity(context, "entity")))))
                .then(CommandManager.literal("reload")
                        .requires(source -> CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK).test(source))
                        .executes(AffinityCommand::reload))
                .then(CommandManager.literal("diagnose")
                        .executes(AffinityCommand::diagnose))
                .then(CommandManager.literal("companion")
                        .then(CommandManager.literal("info")
                                .executes(context -> companionInfo(context, null))
                                .then(CommandManager.argument("entity", EntityArgumentType.entity())
                                        .executes(context -> companionInfo(context,
                                                EntityArgumentType.getEntity(context, "entity")))))
                        .then(CommandManager.literal("clear")
                                .requires(CommandManager.requirePermissionLevel(
                                        CommandManager.GAMEMASTERS_CHECK))
                                .executes(context -> companionClear(context, null))
                                .then(CommandManager.argument("entity", EntityArgumentType.entity())
                                        .executes(context -> companionClear(context,
                                                EntityArgumentType.getEntity(context, "entity"))))))
                .then(CommandManager.literal("stampcounts")
                        .executes(AffinityCommand::stampCounts))
                .then(CommandManager.literal("list")
                        .executes(AffinityCommand::listAnimations))
                .then(CommandManager.literal("play")
                        .then(CommandManager.argument("index", IntegerArgumentType.integer(1))
                                .executes(AffinityCommand::playAnimation))));
    }

    // ------------------------------------------------------------------
    // Subcommands
    // ------------------------------------------------------------------

    private static int accept(CommandContext<ServerCommandSource> context, @Nullable Entity explicit,
                              long token) {
        try(var feedbackScope=com.nonid.affinity.service.GameplayFeedback.command()){
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.translatable(KEY + "player_only"));
            return 0;
        }
        // The pending request determines the target: with no explicit entity, RequestSystem looks
        // up this player's own request instead of guessing from whatever is nearby.
        if (explicit != null && !(explicit instanceof MobEntity)) {
            context.getSource().sendError(Text.translatable(KEY + "no_pending"));
            return 0;
        }
        MobEntity mob = explicit instanceof MobEntity target ? target : null;
        if (!RequestSystem.accept(player, mob, token)) {
            context.getSource().sendError(Text.translatable(KEY + "no_pending"));
            return 0;
        }
        return 1;
    
        }
    }

    private static int reject(CommandContext<ServerCommandSource> context, @Nullable Entity explicit,
                              long token) {
        try(var feedbackScope=com.nonid.affinity.service.GameplayFeedback.command()){
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.translatable(KEY + "player_only"));
            return 0;
        }
        // Same as accept: the pending request is the source of truth for the target.
        if (explicit != null && !(explicit instanceof MobEntity)) {
            context.getSource().sendError(Text.translatable(KEY + "no_pending"));
            return 0;
        }
        MobEntity mob = explicit instanceof MobEntity target ? target : null;
        if (!RequestSystem.reject(player, mob, token)) {
            context.getSource().sendError(Text.translatable(KEY + "no_pending"));
            return 0;
        }
        return 1;
    
        }
    }

    private static int info(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.translatable(KEY + "player_only"));
            return 0;
        }
        MobEntity mob = resolveAimedMob(player, null);
        if (mob == null) {
            context.getSource().sendError(Text.translatable(KEY + "look_at_creature"));
            return 0;
        }
        CreatureAffinityData data = NonCreatureAffinity.dataOf(mob);
        if (data == null) {
            context.getSource().sendError(Text.translatable(KEY + "no_data"));
            return 0;
        }
        AffinityConfig config = NonCreatureAffinity.getConfig();
        PlayerAffinity relation = data.get(player.getUuid());
        int affinity = relation == null ? config.initialAffinity : relation.getAffinity();
        boolean friend = com.nonid.affinity.service.RelationshipStageQuery.friend(relation, config);

        context.getSource().sendFeedback(() -> Text.translatable(KEY + "info_header")
                .formatted(Formatting.GOLD), false);
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "info_creature", mob.getName()), false);
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "info_affinity",
                affinity, config.friendThreshold).formatted(Formatting.GREEN), false);
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "info_status",
                        Text.translatable(com.nonid.affinity.service.RelationshipStageQuery.key(relation, config)))
                .formatted(friend ? Formatting.LIGHT_PURPLE : Formatting.GRAY), false);
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "info_mating_count",
                relation == null ? 0 : relation.getMatingCount()).formatted(Formatting.AQUA), false);
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "info_arousal",
                        NonBridge.getEnergy(mob), NonBridge.getMaxEnergy(mob))
                .formatted(Formatting.RED), false);
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "info_generation",
                data.getGeneration()).formatted(Formatting.DARK_AQUA), false);
        return 1;
    }

    private static int set(CommandContext<ServerCommandSource> context, @Nullable Entity explicit) {
        try(var feedbackScope=com.nonid.affinity.service.GameplayFeedback.command()){
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.translatable(KEY + "player_only"));
            return 0;
        }
        MobEntity mob = resolveAimedMob(player, explicit);
        if (mob == null) {
            context.getSource().sendError(Text.translatable(KEY + "cmd_no_target_modify"));
            return 0;
        }
        int value = IntegerArgumentType.getInteger(context, "value");
        AffinityConfig config = NonCreatureAffinity.getConfig();
        PlayerAffinity relation = AffinityService.relationOrCreate(mob, player, config);
        if (relation == null) {
            context.getSource().sendError(Text.translatable(KEY + "no_relation"));
            return 0;
        }
        relation.setAffinity(value, config.minAffinity, config.maxAffinity);
        AffinityService.checkFriendThreshold(mob, player, relation, config);
        // Echo the creature and its uuid so the operator can confirm what was modified.
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "set_done_detail",
                mob.getName(), mob.getUuid().toString(), relation.getAffinity())
                .formatted(Formatting.GREEN), false);
        return 1;
    
        }
    }

    private static int forceFriend(CommandContext<ServerCommandSource> context,
                                   @Nullable Entity explicit) {
        try(var feedbackScope=com.nonid.affinity.service.GameplayFeedback.command()){
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.translatable(KEY + "player_only"));
            return 0;
        }
        MobEntity mob = resolveAimedMob(player, explicit);
        if (mob == null) {
            context.getSource().sendError(Text.translatable(KEY + "cmd_no_target_modify"));
            return 0;
        }
        AffinityConfig config = NonCreatureAffinity.getConfig();
        RelationshipRules.BondOutcome eligibility = RelationshipSync.eligibility(mob, player.getUuid());
        if (eligibility != RelationshipRules.BondOutcome.BOUND) {
            context.getSource().sendError(Text.translatable(KEY + "friend_failed_ownership", mob.getName()));
            return 0;
        }
        if (config.minAffinity > config.maxAffinity || config.friendThreshold > config.maxAffinity) {
            context.getSource().sendError(Text.translatable(KEY + "friend_failed_config"));
            return 0;
        }
        PlayerAffinity relation = AffinityService.relationOrCreate(mob, player, config);
        if (relation == null) {
            context.getSource().sendError(Text.translatable(KEY + "no_relation"));
            return 0;
        }
        relation.setAffinity(config.friendThreshold, config.minAffinity, config.maxAffinity);
        relation.setBefriended(false);
        AffinityService.checkFriendThreshold(mob, player, relation, config);
        if (!relation.isBefriended()
                || !(mob instanceof AffinityHolder holder)
                || !player.getUuid().equals(holder.affinityData().getCompanionOwner())) {
            context.getSource().sendError(Text.translatable(KEY + "friend_failed_binding", mob.getName()));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "friend_done_detail",
                mob.getName(), mob.getUuid().toString())
                .formatted(Formatting.GREEN), false);
        return 1;
    
        }
    }

    private static int reload(CommandContext<ServerCommandSource> context) {
        if (!NonCreatureAffinity.tryReloadConfig()) {
            context.getSource().sendError(Text.translatable(KEY + "reload_failed"));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "reloaded")
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    /**
     * Diagnose why a mating request might do nothing: shows how many animation definitions are
     * loaded, whether they have the shape this mod needs, and what it can match right now.
     */
    private static int diagnose(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.translatable(KEY + "player_only"));
            return 0;
        }
        int count = NonBridge.loadedDefinitionCount();
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "diag_header")
                .formatted(Formatting.GOLD), false);
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "diag_non",
                NonBridge.isAvailable() ? "OK" : "MISSING").formatted(
                        NonBridge.isAvailable() ? Formatting.GREEN : Formatting.RED), false);
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "diag_definitions", count)
                .formatted(count > 0 ? Formatting.GREEN : Formatting.RED), false);
        if (count == 0) {
            context.getSource().sendFeedback(() -> Text.translatable(KEY + "diag_no_pack")
                    .formatted(Formatting.YELLOW), false);
            return 1;
        }
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "diag_shape",
                NonBridge.hasPlayerCreatureDefinition() ? "OK" : "NO").formatted(
                        NonBridge.hasPlayerCreatureDefinition() ? Formatting.GREEN : Formatting.RED), false);

        LivingEntity target = AffinityService.findBestPartner(player, NonCreatureAffinity.getConfig(), 8.0);
        if (target == null) {
            context.getSource().sendFeedback(() -> Text.translatable(KEY + "diag_no_target")
                    .formatted(Formatting.GRAY), false);
            return 1;
        }
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "diag_target",
                target.getName(), AffinityService.getAffinity(target, player),
                NonBridge.getEnergy(target)).formatted(Formatting.AQUA), false);
        boolean busy = NonBridge.isActorBusy(player.getEntityWorld(), player)
                || NonBridge.isActorBusy(player.getEntityWorld(), target);
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "diag_busy",
                busy ? "YES" : "no").formatted(busy ? Formatting.RED : Formatting.GREEN), false);
        return 1;
    }

    /** Cache of the last listed candidates per player, so "play <n>" resolves reliably. */
    private static final java.util.Map<java.util.UUID, java.util.List<String>> LAST_CANDIDATES =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void clearPlayerState(java.util.UUID player) {
        LAST_CANDIDATES.remove(player);
    }

    public static void clearRuntimeState() {
        LAST_CANDIDATES.clear();
    }

    /** List every animation that matches you and the creature you are looking at. */
    private static int listAnimations(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.translatable(KEY + "player_only"));
            return 0;
        }
        MobEntity mob = resolveAimedMob(player, null);
        if (mob == null) {
            context.getSource().sendError(Text.translatable(KEY + "look_at_creature"));
            return 0;
        }
        java.util.List<String> candidates = NonBridge.candidateIds(player.getEntityWorld(), player, mob);
        LAST_CANDIDATES.put(player.getUuid(), candidates);
        if (candidates.isEmpty()) {
            context.getSource().sendError(Text.translatable(KEY + "list_empty", mob.getName()));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "cmd_list_header", mob.getName(), candidates.size()).formatted(Formatting.GOLD), false);
        for (int i = 0; i < candidates.size(); i++) {
            String[] parts = candidates.get(i).split("\\|", 3);
            String id = parts[0];
            String display = parts.length > 1 ? parts[1] : id;
            boolean eligible = parts.length > 2 && Boolean.parseBoolean(parts[2]);
            int number = i + 1;
            context.getSource().sendFeedback(() -> Text.literal("  " + number + ". ")
                    .append(Text.literal(display).formatted(eligible ? Formatting.GREEN : Formatting.DARK_GRAY))
                    .append(Text.literal("  (" + id + ")").formatted(Formatting.DARK_GRAY))
                    .append(eligible ? Text.literal("") : Text.literal(" [conditions unmet]")
                            .formatted(Formatting.RED)), false);
        }
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "cmd_list_hint").formatted(Formatting.YELLOW), false);
        return 1;
    }

    /**
     * Report the launch-accounting counts on demand.
     *
     * <p>Read-only, and deliberately a command rather than a per-tick log line: the point is to check that
     * records are RELEASED after projectiles are consumed, unloaded or destroyed, and a memory curve or a
     * quiet log cannot show that. Run it before and after a scenario and compare.
     */
    private static int stampCounts(CommandContext<ServerCommandSource> context) {
        context.getSource().sendFeedback(
                () -> Text.literal("[NoN Affinity] stamps "
                        + com.nonid.affinity.service.CompanionCombatController.stampCounts()),
                false);
        return 1;
    }

    /** Start the animation previously listed under this index. */
    private static int playAnimation(CommandContext<ServerCommandSource> context) {
        try(var feedbackScope=com.nonid.affinity.service.GameplayFeedback.command()){
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.translatable(KEY + "player_only"));
            return 0;
        }
        MobEntity mob = resolveAimedMob(player, null);
        if (mob == null) {
            context.getSource().sendError(Text.translatable(KEY + "look_at_creature"));
            return 0;
        }
        java.util.List<String> candidates = LAST_CANDIDATES.get(player.getUuid());
        if (candidates == null || candidates.isEmpty()) {
            context.getSource().sendError(Text.translatable(KEY + "cmd_play_need_list"));
            return 0;
        }
        int index = IntegerArgumentType.getInteger(context, "index") - 1;
        if (index < 0 || index >= candidates.size()) {
            context.getSource().sendError(Text.translatable(KEY + "cmd_play_out_of_range", candidates.size()));
            return 0;
        }
        String id = candidates.get(index).split("\\|", 2)[0];
        Identifier animationId = Identifier.tryParse(id);
        boolean started = NonBridge.startChosen(player.getEntityWorld(), player, mob, animationId);
        if (!started) {
            context.getSource().sendError(Text.translatable(KEY + "cmd_play_unavailable"));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "cmd_play_started", id.toString())
                .formatted(Formatting.GREEN), false);
        return 1;
    
        }
    }

    /**
     * Show the ownership picture for the aimed creature.
     *
     * <p>Both owner ids are printed because a stale bond from an older version is only diagnosable
     * by seeing them side by side.
     */
    private static int companionInfo(CommandContext<ServerCommandSource> context,
                                     @Nullable Entity explicit) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.translatable(KEY + "player_only"));
            return 0;
        }
        MobEntity mob = resolveAimedMob(player, explicit);
        if (mob == null) {
            // No fallback: report rather than inspecting some other creature.
            context.getSource().sendError(Text.translatable(KEY + "cmd_companion_no_target_info"));
            return 0;
        }
        java.util.UUID companion = com.nonid.affinity.service.CompanionService.companionOwner(mob);
        java.util.UUID vanilla = com.nonid.affinity.service.CompanionService.vanillaOwner(mob);
        com.nonid.affinity.data.CompanionMode mode =
                com.nonid.affinity.service.CompanionService.modeOf(mob);

        context.getSource().sendFeedback(() -> Text.translatable(KEY + "cmd_companion_header")
                .formatted(Formatting.GOLD), false);
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "cmd_companion_creature", mob.getName(), mob.getUuid().toString()), false);
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "cmd_companion_vanilla_owner", vanilla == null
                    ? Text.translatable(KEY + "cmd_companion_none").getString()
                    : vanilla.toString()), false);
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "cmd_companion_bond", companion == null
                    ? Text.translatable(KEY + "cmd_companion_none").getString()
                    : companion.toString(), mode), false);
        if (vanilla != null && companion != null && !vanilla.equals(companion)) {
            context.getSource().sendFeedback(() -> Text.translatable(KEY + "cmd_companion_conflict")
                    .formatted(Formatting.RED), false);
        }
        return 1;
    }

    /**
     * Clear the companion bond of the aimed creature.
     *
     * <p>Only repairs the companion record: the vanilla owner, affinity, inventory and health are
     * left untouched, and nothing is reassigned automatically.
     */
    private static int companionClear(CommandContext<ServerCommandSource> context,
                                      @Nullable Entity explicit) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.translatable(KEY + "player_only"));
            return 0;
        }
        MobEntity mob = resolveAimedMob(player, explicit);
        if (mob == null) {
            // Deliberately no nearby-creature fallback: clearing the wrong companion would be worse
            // than refusing. The player is told how to name the target explicitly.
            context.getSource().sendError(Text.translatable(KEY + "cmd_companion_no_target_clear"));
            return 0;
        }
        java.util.UUID previous = com.nonid.affinity.service.CompanionService.clearBond(mob);
        if (previous == null) {
            context.getSource().sendError(Text.translatable(KEY + "cmd_companion_no_bond"));
            return 0;
        }
        // Echo both uuids so the operator can verify the right creature was touched.
        context.getSource().sendFeedback(() -> Text.translatable(KEY + "cmd_companion_cleared",
                mob.getName(), mob.getUuid().toString(), previous.toString())
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    /** Maximum distance for command target selection, in blocks. */
    private static final double TARGET_RANGE = 8.0;

    /**
     * Resolve a target that is either given explicitly or genuinely aimed at.
     *
     * <p>This performs a real ENTITY ray cast. The previous attempt used {@code Entity#raycast},
     * which ray-casts for BLOCKS and returns a {@code BlockHitResult}: an
     * {@code instanceof EntityHitResult} test against it can never succeed, so the aimed branch was
     * dead code and every command without an explicit entity reported "no target".
     *
     * <p>There is deliberately no "nearest creature" fallback. For commands that modify data,
     * acting on a creature the player is not pointing at is worse than refusing.
     *
     * @return the aimed mob, or null when nothing valid is targeted
     */
    @Nullable
    private static MobEntity resolveAimedMob(ServerPlayerEntity player, @Nullable Entity explicit) {
        if (explicit != null) {
            // An explicitly named non-mob is rejected, never silently replaced by something else.
            return explicit instanceof MobEntity mob && mob.isAlive() ? mob : null;
        }
        net.minecraft.server.world.ServerWorld world = player.getEntityWorld();
        net.minecraft.util.math.Vec3d start = player.getCameraPosVec(1.0F);
        net.minecraft.util.math.Vec3d direction = player.getRotationVec(1.0F);
        net.minecraft.util.math.Vec3d end = start.add(direction.multiply(TARGET_RANGE));
        net.minecraft.util.math.Box searchBox =
                player.getBoundingBox().stretch(direction.multiply(TARGET_RANGE)).expand(1.0);

        EntityHitResult hit = net.minecraft.entity.projectile.ProjectileUtil.raycast(
                player, start, end, searchBox,
                entity -> entity instanceof MobEntity && entity != player
                        && entity.isAlive() && !entity.isSpectator(),
                TARGET_RANGE * TARGET_RANGE);
        if (hit == null) {
            return null;
        }
        // Refuse a target that is behind a wall.
        net.minecraft.util.hit.BlockHitResult blockHit = world.raycast(
                new net.minecraft.world.RaycastContext(start, hit.getPos(),
                        net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                        net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            return null;
        }
        return hit.getEntity() instanceof MobEntity mob ? mob : null;
    }

    /** Debug helper: number of entities carrying affinity data around the player. */
    public static int countTracked(ServerPlayerEntity player) {
        int count = 0;
        for (Entity entity : player.getEntityWorld().iterateEntities()) {
            // Count what is there; do not create it. The creating accessor made this diagnostic itself
            // attach an empty record to every entity it looked at.
            if (entity instanceof AffinityHolder holder) {
                var data = holder.peekAffinityData();
                if (data != null && !data.isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }
}
