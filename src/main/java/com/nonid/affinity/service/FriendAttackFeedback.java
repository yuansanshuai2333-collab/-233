package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.data.AffinityAttachments;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

/** Only actual damage to an existing friend-started pair; no unrelated relationship creation. */
public final class FriendAttackFeedback {
    private FriendAttackFeedback() {}
    public static void damaged(MobEntity creature, DamageSource source) {
        if (source == null || !(source.getAttacker() instanceof ServerPlayerEntity player)
                || ReplayIsolation.protects(creature.getUuid(), player.getUuid())) return;
        var relation = AffinityService.getRelation(creature, player);
        if (relation == null) return;
        var origin = source.getSource();
        if (origin != null && origin != player) {
            var pairs = origin.getAttached(AffinityAttachments.FRIEND_HIT_PAIRS);
            String key = creature.getUuid() + ":" + player.getUuid();
            if (pairs != null && pairs.getBoolean(key, false)) return;
            if (pairs == null) { pairs = new net.minecraft.nbt.NbtCompound(); origin.setAttached(AffinityAttachments.FRIEND_HIT_PAIRS, pairs); }
            pairs.putBoolean(key, true);
        }
        var cfg = NonCreatureAffinity.getConfig();
        int loss = relation.friendAttacks().attack(player.getEntityWorld().getServer().getOverworld().getTime(),
                CompanionDamageEvents.wasFriend(creature, player)
                        || RelationshipStages.friend(relation.getAffinity(), cfg));
        if (loss > 0) {
            var escape = CompanionDamageEvents.escapeSettlement(creature, player);
            if (escape != null) escape.record(loss);
            else AffinityService.addAffinity(creature, player, -loss, "friend_attack");
            RelationshipBond.add(creature, player, loss == 1 ? -0.05 : -0.1);
        }
    }
}
