package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.data.AffinityHolder;
import net.minecraft.entity.mob.MobEntity;

/** Current friendship, not an online-player search or a permanent immortality flag. */
public final class FriendRetention {
    public static boolean protects(MobEntity mob) {
        if (!mob.isAlive() || CreakingMemory.bound(mob) || !(mob instanceof AffinityHolder holder)) return false;
        var data = holder.peekAffinityData();
        if (data == null) return false;
        for (var entry : data.entries().entrySet())
            if (!ReplayIsolation.protectsKey(mob.getUuid(), entry.getKey())
                    && RelationshipStages.friend(entry.getValue().getAffinity(), NonCreatureAffinity.getConfig())) return true;
        return false;
    }
    private FriendRetention() { }
}
