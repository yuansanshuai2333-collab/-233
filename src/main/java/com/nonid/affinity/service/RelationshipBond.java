package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/** Hidden, server-owned pair rewards; never reconstructs missing history. */
public final class RelationshipBond {
    private RelationshipBond() {}
    public static void add(Entity creature, PlayerEntity player, double delta) {
        if (creature == null || player == null || creature.getEntityWorld().isClient()
                || ReplayIsolation.protects(creature.getUuid(), player.getUuid())) return;
        var relation = AffinityService.getRelation(creature, player);
        if (relation != null) relation.addBond(delta > 0
                ? delta * NonCreatureAffinity.getConfig().bondGrowthPercent / 100.0 : delta);
    }
    /** Future gift eligibility only; no gift system or daily numeric presentation. */
    public static int tier(double bond, int affinity) {
        var cfg = NonCreatureAffinity.getConfig();
        if (!RelationshipStages.defends(affinity, cfg)) return -1;
        if (bond >= 80 && RelationshipStages.friend(affinity, cfg)) return 3;
        if (bond >= 30 && RelationshipStages.friend(affinity, cfg)) return 2;
        if (bond >= 10 && affinity >= cfg.controlThreshold) return 1;
        return 0;
    }
}
