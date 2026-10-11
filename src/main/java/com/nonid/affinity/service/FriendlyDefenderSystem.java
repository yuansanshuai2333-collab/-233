package com.nonid.affinity.service;

import com.nonid.affinity.config.AffinityConfig;
import com.nonid.affinity.data.AffinityHolder;
import com.nonid.affinity.data.CreatureAffinityData;
import com.nonid.affinity.data.PlayerAffinity;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LazyEntityReference;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * Makes befriended (non-tameable) creatures defend their player.
 *
 * <p>Runs on a low-frequency server tick. It only sets a vanilla target on the mob,
 * so no vanilla method is overwritten and no Mixin conflict is introduced.
 */
public final class FriendlyDefenderSystem {

    private FriendlyDefenderSystem() {
    }

    public static void clear() {
        // Targets live in the shared registry, so clear them here as well.
        TargetRegistry.clear();
    }

    /** Low-frequency scan: let friendly creatures guard their bonded player. */
    public static void tick(ServerWorld world, AffinityConfig config) {
        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof MobEntity mob) || !mob.isAlive()) {
                continue;
            }
            if ((CompanionService.usesNativeControl(mob) && !CreakingMemory.bound(mob)) || !AffinityService.isDefender(mob, config)
                    || !hasNativeCombat(mob)) {
                // No longer qualifies (unbound, unfriended): end any chase this module started.
                TargetRegistry.releaseIfOwned(mob, TargetRegistry.TargetSource.LEGACY_DEFENDER);
                continue;
            }
            // A creature bound to a companion is managed entirely by the companion controller. This
            // service must not write its target: doing so registered it as LEGACY_DEFENDER, which
            // the companion service then treated as foreign and yielded to -- so the companion's own
            // leash and mode rules stopped governing that chase. Release what we still own and hand
            // over, rather than skipping and leaving our old target behind.
            if (!CreakingMemory.bound(mob) && RelationshipBehavior.controls(mob, CompanionService.companionOwner(mob))) {
                TargetRegistry.releaseIfOwned(mob, TargetRegistry.TargetSource.LEGACY_DEFENDER);
                continue;
            }
            // The same occupancy conditions the companion service honours: never write a target for
            // a creature that is riding, leashed, sitting or busy in an animation.
            if (mob.hasVehicle() || mob.hasPassengers() || mob.isLeashed()
                    || (mob instanceof net.minecraft.entity.passive.TameableEntity tameable
                        && tameable.isSitting())
                    || com.nonid.affinity.non.NonBridge.isActorBusy(world, mob)) {
                TargetRegistry.releaseIfOwned(mob, TargetRegistry.TargetSource.LEGACY_DEFENDER);
                continue;
            }
            ServerPlayerEntity owner = findOwner(world, mob);
            if (owner == null || !RelationshipBehavior.defends(mob, owner.getUuid()) || !CreakingMemory.inHomeRange(mob, owner)) {
                // Owner offline, dead or in another dimension: stop the chase we began rather than
                // leaving the creature running after a stale target.
                if (TargetRegistry.hasRecord(mob)) {
                    TargetRegistry.releaseIfOwned(mob, TargetRegistry.TargetSource.LEGACY_DEFENDER);
                }
                continue;
            }
            // Yield to any live target this service does not own. Without this branch the defender
            // re-read the target, decided it was not the one it wanted, and overwrote it with its
            // own -- silently stealing a target from another system.
            LivingEntity current = mob.getTarget();
            if (current != null && current.isAlive()
                    && !TargetRegistry.owns(mob, TargetRegistry.TargetSource.LEGACY_DEFENDER)) {
                // Revoke a stale record of ours if the target was replaced or cleared externally.
                TargetRegistry.revokeIfStale(mob);
                continue;
            }
            // Keep the existing target only when it is still a genuine threat. Previously any live
            // target was kept, so a friendly creature chasing something unrelated would ignore an
            // enemy that started attacking the player.
            if (current != null && current.isAlive() && isThreat(current, owner) && CreakingMemory.inHomeRange(mob, current)) {
                continue;
            }
            // The old target is dead, turned friendly, or stopped being a threat. Clear it only when
            // this module owns it; a target set by another system is left alone.
            if (TargetRegistry.recordedSource(mob) == TargetRegistry.TargetSource.LEGACY_DEFENDER) {
                TargetRegistry.releaseIfOwned(mob, TargetRegistry.TargetSource.LEGACY_DEFENDER);
                // Re-read rather than assuming the target is now null: another system may have set
                // one in the meantime, and blindly overwriting it would be the same defect again.
                current = mob.getTarget();
            }
            LivingEntity threat = findThreat(world, mob, owner, config);
            if (!CreakingMemory.inHomeRange(mob, threat)) threat = null;
            if (threat != null && threat != current) {
                TargetRegistry.claim(mob, threat, owner.getUuid(), TargetRegistry.TargetSource.LEGACY_DEFENDER,
                        world.getTime());
                RelationshipBehavior.writeTarget(mob, threat);
                if (config.debugLogging) {
                    com.nonid.affinity.NonCreatureAffinity.LOGGER.info(
                            "[NoN Affinity] friendly {} guards {} against {} (previous target: {})",
                            mob.getName().getString(), owner.getName().getString(),
                            threat.getName().getString(),
                            current == null ? "none" : current.getName().getString());
                }
            } else if (config.debugLogging && threat == null) {
                // Distinguishes "no threat found" from "target set but the AI dropped it".
                com.nonid.affinity.NonCreatureAffinity.LOGGER.info(
                        "[NoN Affinity] friendly {} near {} found no threat to engage",
                        mob.getName().getString(), owner.getName().getString());
            }
        }
    }

    /**
     * Release every target this service still owns, without touching anyone else's.
     *
     * <p>Called while the feature is disabled, because the normal tick does not run then and a
     * target set before the switch was flipped would otherwise be left in place forever.
     */
    public static void releaseAllOwned(ServerWorld world) {
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof MobEntity mob) {
                // Scoped to LEGACY_DEFENDER: an ATTACK-mode target is not this service's to clear.
                TargetRegistry.releaseIfOwned(mob, TargetRegistry.TargetSource.LEGACY_DEFENDER);
            }
        }
    }

    /** Resolve the player this creature is bonded to. */
    @Nullable
    private static ServerPlayerEntity findOwner(ServerWorld world, MobEntity mob) {
        // Vanilla-tamed creature: use its own owner directly (horses included).
        // Ownership is decided by the same rules everywhere, in priority order. The previous
        // version ignored the companion bond entirely and picked the nearby player with the highest
        // affinity, so a creature bonded to A could guard B instead.
        //
        // A contradictory record (two different owners) is not guessed at: guard nobody, exactly
        // like the interaction path refuses both sides.
        if (CompanionService.hasOwnershipConflict(mob)) {
            return null;
        }
        //
        // 1. An explicit companion bond decides the owner.
        java.util.UUID companionOwner = CompanionService.companionOwner(mob);
        if (companionOwner != null) {
            // If that player is offline, dead or in another world, this creature guards nobody for
            // now. It must NOT silently adopt a different player.
            return availablePlayer(world, companionOwner);
        }

        // 2. A vanilla owner decides when there is no companion bond.
        java.util.UUID vanillaOwner = vanillaOwnerUuid(mob);
        if (vanillaOwner != null) {
            return availablePlayer(world, vanillaOwner);
        }

        // 3. Legacy data: no explicit owner recorded, only affinity relations. This keeps the
        //    pre-companion behaviour for saves that predate bonding, and is the only case where a
        //    nearby player is chosen by affinity.
        if (!(mob instanceof AffinityHolder holder) || holder.peekAffinityData() == null) {
            return null;
        }
        CreatureAffinityData data = holder.peekAffinityData();
        List<ServerPlayerEntity> players = world.getEntitiesByClass(ServerPlayerEntity.class,
                mob.getBoundingBox().expand(24.0), player -> player.isAlive() && !player.isSpectator());
        ServerPlayerEntity best = null;
        int bestAffinity = Integer.MIN_VALUE;
        for (ServerPlayerEntity player : players) {
            PlayerAffinity relation = data.get(player.getUuid());
            if (relation == null || !RelationshipBehavior.defends(mob, player.getUuid())
                    || findThreat(world, mob, player, com.nonid.affinity.NonCreatureAffinity.getConfig()) == null) {
                continue;
            }
            if (relation.getAffinity() > bestAffinity) {
                bestAffinity = relation.getAffinity();
                best = player;
            }
        }
        return best;
    }

    /**
     * The given player, but only when they are actually present in this world.
     *
     * @return the player, or null when they are offline, dead, spectating or in another dimension
     */
    @Nullable
    private static ServerPlayerEntity availablePlayer(ServerWorld world, java.util.UUID uuid) {
        // getPlayerByUuid returns PlayerEntity, so the server-side type is checked explicitly.
        net.minecraft.entity.player.PlayerEntity found = world.getPlayerByUuid(uuid);
        if (!(found instanceof ServerPlayerEntity player) || !player.isAlive() || player.isSpectator()) {
            return null;
        }
        return player;
    }

    /** Owner of a vanilla-tamed creature, across both tameable families. */
    @Nullable
    static LivingEntity resolveVanillaOwner(ServerWorld world, MobEntity mob) {
        // isTamed()/isTame() being true does NOT guarantee that an owner reference exists: this was
        // the assumption behind the crash in resolveVanillaOwner. The reference is therefore always
        // checked before use.
        if (mob instanceof TameableEntity tameable && tameable.isTamed()) {
            return resolveOwner(tameable.getOwnerReference(), world);
        }
        if (mob instanceof AbstractHorseEntity horse && horse.isTame()) {
            return resolveOwner(horse.getOwnerReference(), world);
        }
        return null;
    }

    /**
     * The persistent uuid of a creature's vanilla owner, or null when it has none.
     *
     * <p>Reads the stored reference directly instead of resolving it to a live entity. The owner
     * being offline, in another dimension, or otherwise unloaded must NOT be mistaken for "no
     * owner" -- that mistake let another player claim someone else's pet.
     */
    @Nullable
    public static java.util.UUID vanillaOwnerUuid(MobEntity mob) {
        LazyEntityReference<LivingEntity> reference = null;
        if (mob instanceof TameableEntity tameable && tameable.isTamed()) {
            reference = tameable.getOwnerReference();
        } else if (mob instanceof AbstractHorseEntity horse && horse.isTame()) {
            reference = horse.getOwnerReference();
        }
        return reference == null ? null : reference.getUuid();
    }

    /**
     * Resolve a lazily stored owner.
     *
     * @return the owner, or null when the reference is missing or the owner cannot be resolved
     *         (offline, other world); callers must treat null as "no owner available"
     */
    @Nullable
    static LivingEntity resolveOwner(@Nullable LazyEntityReference<LivingEntity> reference,
                                     ServerWorld world) {
        return reference == null ? null : reference.getEntityByClass(world, LivingEntity.class);
    }

    /** Find a hostile creature threatening the bonded player. */
    @Nullable
    private static LivingEntity findThreat(ServerWorld world, MobEntity defender, ServerPlayerEntity owner,
                                           AffinityConfig config) {
        TargetPredicate predicate = TargetPredicate.createAttackable()
                .setBaseMaxDistance(config.friendlyDefendRadius)
                .ignoreVisibility();
        List<LivingEntity> candidates = world.getEntitiesByClass(LivingEntity.class,
                defender.getBoundingBox().expand(config.friendlyDefendRadius),
                entity -> entity != defender
                        && entity != owner
                        && entity.isAlive()
                        && isThreat(entity, owner));
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            if (!RelationshipBehavior.testDefenceCandidate(defender, candidate,
                    () -> predicate.test(world, defender, candidate))) {
                continue;
            }
            double distance = defender.squaredDistanceTo(candidate);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = candidate;
            }
        }
        return closest;
    }

    /**
     * Whether this creature is a threat to the owner.
     *
     * <p>Delegates to the shared rules. The previous version treated "a hostile mob is targeting
     * ANY player" as a threat to this owner, and did not exclude tamed creatures, so it disagreed
     * with ATTACK mode and could pick a player's pet as a target.
     */
    private static boolean isThreat(LivingEntity entity, ServerPlayerEntity owner) {
        if (!entity.isAlive()) {
            return false;
        }
        if (entity == owner.getVehicle()) {
            return false;
        }
        if (CombatEligibility.isProtected(entity, owner)) return false;
        long now = owner.getEntityWorld().getTime();
        String dimension = owner.getEntityWorld().getRegistryKey().getValue().toString();
        return CompanionEventLog.ownerFacts(owner.getUuid()).stream().anyMatch(event ->
                event.trigger() == com.nonid.affinity.service.policy.CompanionCombatPolicy.Trigger.OWNER_HURT
                && event.targetId().equals(entity.getUuid()) && event.worldId().equals(dimension)
                && event.tick() <= now && now - event.tick()
                <= com.nonid.affinity.NonCreatureAffinity.getConfig().companionEventMaxAgeTicks);
    }

    /** Select a target for existing combat AI; never add attacks or damage attributes to passive mobs. */
    private static boolean hasNativeCombat(MobEntity mob) {
        return CombatEligibility.hasCombatCapability(mob);
    }
}

