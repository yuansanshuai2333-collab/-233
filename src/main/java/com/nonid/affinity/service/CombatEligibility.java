package com.nonid.affinity.service;

import com.nonid.affinity.data.PlayerAffinity;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Who a companion or defender may attack, and who it must never attack.
 *
 * <p>Single source of truth. Previously the defender and ATTACK mode each carried their own rules
 * and they disagreed: the defender counted "an enemy is targeting ANY player" as a threat to its
 * owner, and neither side consistently excluded tamed creatures, so a player's pet could be picked
 * as a target.
 */
public final class CombatEligibility {

    private CombatEligibility() {
    }

    /** Identity-based protection also works while the owner is offline. */
    public static boolean isProtectedForOwner(LivingEntity candidate, UUID ownerId) {
        if (candidate == null || ownerId == null || candidate.getUuid().equals(ownerId)
                || candidate instanceof net.minecraft.entity.player.PlayerEntity) return true;
        if (!(candidate instanceof MobEntity mob)) return false;
        if (ownerId.equals(CompanionService.companionOwner(mob)) || AffinityService.isTamedCreature(mob)) return true;
        if (mob instanceof com.nonid.affinity.data.AffinityHolder holder && holder.peekAffinityData() != null) {
            PlayerAffinity relation = holder.peekAffinityData().get(ownerId);
            return relation != null && RelationshipStages.friend(relation.getAffinity(),
                    com.nonid.affinity.NonCreatureAffinity.getConfig());
        }
        return false;
    }

    /**
     * Whether this creature is protected and must never be chosen as a target.
     *
     * <p>Checked before any hostility test, so a friendly creature that accidentally damages the
     * owner cannot start a retaliation chain.
     */
    public static boolean isProtected(LivingEntity candidate, ServerPlayerEntity owner) {
        if (candidate == null || owner == null || candidate == owner) {
            return true;
        }
        if (candidate instanceof ServerPlayerEntity) {
            // Players are never auto-attacked: whether PvP happens is a server decision.
            return true;
        }
        if (!(candidate instanceof MobEntity mob)) {
            return false;
        }
        // Another companion of the same owner.
        UUID companionOwner = CompanionService.companionOwner(mob);
        if (companionOwner != null && companionOwner.equals(owner.getUuid())) {
            return true;
        }
        // Tamed by anyone, including the owner: pets are not valid targets.
        if (AffinityService.isTamedCreature(mob)) {
            return true;
        }
        // Befriended through this module.
        PlayerAffinity relation = AffinityService.getRelation(mob, owner);
        return relation != null && RelationshipStages.friend(relation.getAffinity(),
                com.nonid.affinity.NonCreatureAffinity.getConfig());
    }

    /**
     * Whether the creature advertises native combat support.
     *
     * <p>Shared with familiar-stage protection so unlocking controls cannot revoke native combat.
     * Target selection relies on the creature's existing AI; it does not install attack goals.
     * A modded entity advertising combat without implementing an attack may still remain passive.
     */
    public static boolean hasCombatCapability(net.minecraft.entity.mob.MobEntity mob) {
        // Use the same native-capability gate before and after control unlocks.
        // Setting a target never installs an attack goal or adds a damage attribute.
        return mob instanceof net.minecraft.entity.mob.Monster
                || mob instanceof net.minecraft.entity.ai.RangedAttackMob
                || hasNativeMelee(mob);
    }

    public static boolean hasNativeMelee(MobEntity mob) {
        return mob.getAttributes().hasAttribute(net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE);
    }

    /**
     * Whether this creature is hostile toward the owner specifically.
     *
     * <p>Two conditions only: it has taken the owner as its target, or it recently attacked the
     * owner. Being a hostile mob type is not enough, and targeting some OTHER player is not a threat
     * to this owner.
     */
    public static boolean isThreatTo(LivingEntity candidate, ServerPlayerEntity owner) {
        if (isProtected(candidate, owner)) {
            return false;
        }
        if (candidate instanceof MobEntity mob) {
            if (mob.getTarget() == owner) {
                return true;
            }
        }
        return owner.getAttacker() == candidate
                && owner.age - owner.getLastAttackedTime() >= 0
                && owner.age - owner.getLastAttackedTime() <= 100;
    }
}
