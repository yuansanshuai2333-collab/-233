package com.nonid.affinity.service;

import com.nonid.affinity.data.PlayerAffinity;
import java.util.Map;
import java.util.UUID;

/** Select a valid nearby reference relation without crossing an existing ownership boundary. */
public final class InheritanceSelection {
    public record Reference(UUID playerUuid, int affinity) {}
    private InheritanceSelection() {}

    public static Reference choose(Map<String, PlayerAffinity> relations, UUID companionOwner,
                                   UUID vanillaOwner, boolean unconfirmedTamedOwner,
                                   double squaredDistance, double radius) {
        if (unconfirmedTamedOwner || !Double.isFinite(squaredDistance) || squaredDistance < 0
                || !Double.isFinite(radius) || radius <= 0 || squaredDistance > radius * radius) return null;
        Reference best = null;
        for (var entry : relations.entrySet()) {
            PlayerAffinity relation = entry.getValue();
            if (relation == null || !relation.isBefriended()) continue;
            UUID player;
            try { player = UUID.fromString(entry.getKey()); }
            catch (IllegalArgumentException | NullPointerException invalid) { continue; }
            if (!RelationshipRules.isBonded(RelationshipRules.evaluate(companionOwner, vanillaOwner, player, true))) continue;
            if (best == null || relation.getAffinity() > best.affinity()) {
                best = new Reference(player, relation.getAffinity());
            }
        }
        return best;
    }
}
