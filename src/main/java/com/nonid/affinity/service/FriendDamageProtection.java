package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Pair-specific protection; never treats another player's friendship as the attacker's. */
public final class FriendDamageProtection {
    private FriendDamageProtection() {}

    public static boolean blocks(LivingEntity victim, ServerPlayerEntity player) {
        var config = NonCreatureAffinity.getConfig();
        if (!config.preventFriendDamage || !(victim instanceof MobEntity mob)
                || ReplayIsolation.protects(victim.getUuid(), player.getUuid())) return false;
        var relation = AffinityService.getRelation(mob, player);
        if (relation == null || !RelationshipStages.friend(relation.getAffinity(), config)) return false;
        // Do not use CombatEligibility.isThreatTo: it excludes friends by design.
        if (mob.getTarget() == player) return false;
        int elapsed = player.age - player.getLastAttackedTime();
        return !(player.getAttacker() == mob && elapsed >= 0 && elapsed <= 100);
    }
}
