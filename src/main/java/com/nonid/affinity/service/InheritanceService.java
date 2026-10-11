package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.config.AffinityConfig;
import com.nonid.affinity.data.AffinityHolder;
import com.nonid.affinity.data.CreatureAffinityData;
import com.nonid.affinity.data.PlayerAffinity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Offspring inheritance: binds a newborn creature to a nearby adult of the same type
 * that already has a bonded player, mirroring affinity and ownership.
 *
 * <p>NeedsOfNature spawns offspring entities directly without a callback, so this hooks
 * entity load instead and resolves the parent by proximity. Works for naturally spawned
 * babies and for player-born offspring alike.
 */
public final class InheritanceService {

    private static final String KEY = "message.noncreatureaffinity.";

    private InheritanceService() {
    }

    /** Try to bind a freshly loaded baby to a parent relation. */
    public static void tryInheritFromParents(ServerWorld world, MobEntity child, AffinityConfig config) {
        if (!(child instanceof AffinityHolder holder)) {
            return;
        }
        CreatureAffinityData childData = holder.affinityData();
        if (!childData.isEmpty()) {
            return;
        }
        MobEntity parent = findBestParent(world, child, config);
        if (parent == null) {
            return;
        }
        InheritanceSelection.Reference inherited = pickReference(parent, child, config);
        if (inherited == null) {
            return;
        }
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(inherited.playerUuid());
        if (player == null) {
            return;
        }
        int inheritedAffinity = OffspringAffinityRules.initial(config.offspringBefriended,
                config.friendThreshold, config.initialAffinity, inherited.affinity(),
                config.offspringAffinityPercentOfParent, config.minAffinity, config.maxAffinity);

        PlayerAffinity relation = childData.getOrCreate(player.getUuid(), inheritedAffinity);
        relation.setAffinity(inheritedAffinity, config.minAffinity, config.maxAffinity);
        if (config.offspringInheritGeneration) {
            int parentGeneration = parent instanceof AffinityHolder parentHolder
                    ? parentHolder.affinityData().getGeneration()
                    : 0;
            childData.setGeneration(OffspringAffinityRules.nextGeneration(parentGeneration));
        }
        if (!config.offspringBefriended) {
            return;
        }
        relation.setBefriended(true);
        // Route through the shared helper: it also covers AbstractHorseEntity and refuses to take
        // over a pet that already belongs to someone else.
        if (AffinityService.tameCreature(child, player)) {
            relation.setTamedByUs(true);
        }
        // Becoming a friend must also create the companion bond, through the SAME guarded entry the
        // threshold path uses. Doing it here rather than duplicating the rules is what keeps the two
        // paths from drifting apart.
        CompanionService.bindOnBefriend(child, player.getUuid());
        if (config.debugLogging) {
            NonCreatureAffinity.LOGGER.info(
                    "[NoN Affinity] baby {} inherited affinity {} from {} (generation {})",
                    child.getName().getString(), inheritedAffinity,
                    player.getName().getString(), childData.getGeneration());
        }
        GameplayFeedback.relationship(player,child,Text.translatable(KEY + "offspring_born", child.getName())
                .formatted(Formatting.GOLD), false);
    }

    @Nullable
    private static MobEntity findBestParent(ServerWorld world, MobEntity child, AffinityConfig config) {
        EntityType<?> type = child.getType();
        List<MobEntity> candidates = world.getEntitiesByClass(MobEntity.class,
                child.getBoundingBox().expand(config.offspringParentSearchRadius),
                entity -> entity != child
                        && entity.getType() == type
                        && !entity.isBaby() && entity.isAlive()
                        && entity instanceof AffinityHolder holder
                        && holder.peekAffinityData() != null
                        && holder.peekAffinityData().hasAnyBefriended());
        MobEntity best = null;
        int bestAffinity = Integer.MIN_VALUE;
        for (MobEntity candidate : candidates) {
            InheritanceSelection.Reference highest = pickReference(candidate, child, config);
            if (highest != null && highest.affinity() > bestAffinity) {
                bestAffinity = highest.affinity();
                best = candidate;
            }
        }
        return best;
    }

    @Nullable
    private static InheritanceSelection.Reference pickReference(MobEntity parent, MobEntity child,
                                                                AffinityConfig config) {
        if (!(parent instanceof AffinityHolder holder)) return null;
        var data = holder.peekAffinityData();
        if (data == null) return null;
        UUID vanillaOwner = FriendlyDefenderSystem.vanillaOwnerUuid(child);
        return InheritanceSelection.choose(data.entries(), CompanionService.companionOwner(child),
                vanillaOwner, AffinityService.isTamedCreature(child) && vanillaOwner == null,
                parent.squaredDistanceTo(child), config.offspringParentSearchRadius);
    }
}
