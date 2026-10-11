package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.data.AffinityHolder;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Entity adapters for the shared current-stage policy. Never creates relations while reading. */
public final class RelationshipBehavior {
    private record Candidate(MobEntity actor, LivingEntity target) {}
    private static final ThreadLocal<Candidate> DEFENCE_CHECK = new ThreadLocal<>();
    private RelationshipBehavior() {}
    public static int value(MobEntity mob, UUID player) {
        if (player == null || !(mob instanceof AffinityHolder holder) || holder.peekAffinityData() == null) return 0;
        var relation = holder.peekAffinityData().get(player);
        return relation == null ? 0 : relation.getAffinity();
    }
    public static boolean controls(MobEntity mob, UUID player) {
        return RelationshipStages.controls(value(mob, player), NonCreatureAffinity.getConfig());
    }
    public static boolean defends(MobEntity mob, UUID player) {
        return RelationshipStages.defends(value(mob, player), NonCreatureAffinity.getConfig());
    }
    /** Positive affinity suppresses initiation, but a recent attack still permits vanilla retaliation. */
    public static boolean blocksInitiation(LivingEntity actor, LivingEntity target) {
        if (!(actor instanceof MobEntity mob) || target == null
                || !(mob.getEntityWorld() instanceof ServerWorld)) return false;
        if (CreakingMemory.isGazeCheck(mob)) return false;
        // A target can be befriended or tamed while an earlier authorized attack is still in flight.
        // Use the assignment's owner, including for familiar defenders and offline players.
        if (TargetRegistry.protectsAssignedTarget(mob, target)) return true;
        boolean retaliation = mob.getAttacker() == target && mob.age - mob.getLastAttackedTime() >= 0
                && mob.age - mob.getLastAttackedTime() < 100;
        if (target instanceof ServerPlayerEntity player) {
            int affinity = value(mob, player.getUuid());
            if (RelationshipStages.friend(affinity, NonCreatureAffinity.getConfig())) return true;
            if (mob instanceof net.minecraft.entity.mob.Monster && RelationshipStages.protects(affinity))
                return !retaliation;
        }
        if (!isPeacefulFriend(mob)) return false;
        var candidate = DEFENCE_CHECK.get();
        if (candidate != null && candidate.actor() == mob && candidate.target() == target) return false;
        return !retaliation && !target.getUuid().equals(TargetRegistry.recordedTarget(mob));
    }

    /** Only for candidates already approved by the defender's evidence/protection rules. */
    public static boolean testDefenceCandidate(MobEntity mob, LivingEntity target, java.util.function.BooleanSupplier nativeTest) {
        var previous = DEFENCE_CHECK.get();
        DEFENCE_CHECK.set(new Candidate(mob, target));
        try { return nativeTest.getAsBoolean(); }
        finally {
            if (previous == null) DEFENCE_CHECK.remove(); else DEFENCE_CHECK.set(previous);
        }
    }

    /** Friendship curbs autonomous aggression; native pets keep their native control rules. */
    public static boolean isPeacefulFriend(MobEntity mob) {
        if (CompanionService.usesNativeControl(mob) || !(mob instanceof AffinityHolder holder)
                || holder.peekAffinityData() == null) return false;
        for (var relation : holder.peekAffinityData().entries().values())
            if (RelationshipStages.friend(relation.getAffinity(), NonCreatureAffinity.getConfig())) return true;
        return false;
    }

    /** Brain-based wardens do not use MobEntity's ordinary target field. */
    public static void writeTarget(MobEntity mob, LivingEntity target) {
        if (mob instanceof net.minecraft.entity.mob.CreakingEntity creaking) {
            if (target == null) creaking.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.ATTACK_TARGET);
            else creaking.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.ATTACK_TARGET, target);
        } else if (mob instanceof net.minecraft.entity.mob.WardenEntity warden) {
            if (target == null) warden.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.ATTACK_TARGET);
            else warden.updateAttackTarget(target);
        } else mob.setTarget(target);
    }
    /** Runs on the server; supports existing saves and live threshold changes without rewriting history. */
    public static void reconcile(ServerWorld world) {
        for (var entity : world.iterateEntities()) {
            if (!(entity instanceof MobEntity mob) || !mob.isAlive()) continue;
            if (blocksInitiation(mob, mob.getTarget())) writeTarget(mob, null);
            if (CompanionService.usesNativeControl(mob) || CompanionService.companionOwner(mob) != null
                    || !(mob instanceof AffinityHolder holder) || holder.peekAffinityData() == null) continue;
            for (var player : world.getPlayers()) {
                if (controls(mob, player.getUuid())) {
                    CompanionService.bindOnBefriend(mob, player.getUuid());
                    if (CompanionService.companionOwner(mob) != null) break;
                }
            }
        }
    }
}

