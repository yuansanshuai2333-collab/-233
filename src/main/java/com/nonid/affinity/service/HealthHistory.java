package com.nonid.affinity.service;

import java.util.UUID;
import com.nonid.affinity.data.AffinityAttachments;
import com.nonid.affinity.data.HealthDebt;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

/** Observes actual health changes; never cancels or alters another mod's damage/healing. */
public final class HealthHistory {
    private static final class Recovery {
        final MobEntity creature; final UUID user; double eligible;
        Recovery(MobEntity creature, UUID user) { this.creature = creature; this.user = user; }
    }
    private static final ThreadLocal<Recovery> RECOVERY = new ThreadLocal<>();
    private static final ThreadLocal<java.util.Set<MobEntity>> READING = new ThreadLocal<>();
    private HealthHistory() {}
    public static void changed(MobEntity creature, float before, float after, DamageSource source, boolean countAttack) {
        if (creature.getEntityWorld().isClient() || READING.get() != null && READING.get().contains(creature)) return;
        var debt = creature.getAttached(AffinityAttachments.HEALTH_DEBT);
        if (debt == null && after >= before) return;
        if (debt == null) { debt = new HealthDebt(); creature.setAttached(AffinityAttachments.HEALTH_DEBT, debt); }
        var recovery = RECOVERY.get();
        UUID user = recovery != null && recovery.creature == creature ? recovery.user : null;
        UUID author = source != null && source.getAttacker() != null ? source.getAttacker().getUuid() : null;
        if (author != null && ReplayIsolation.protects(creature.getUuid(), author)) author = null;
        double eligible = debt.change(before, after, creature.getMaxHealth(), author, user);
        if (user != null) recovery.eligible += eligible;
        if (after < before && countAttack) FriendAttackFeedback.damaged(creature, source);
    }
    public static double recover(MobEntity creature, ServerPlayerEntity player, Runnable action) {
        var previous = RECOVERY.get(); var recovery = new Recovery(creature, player.getUuid());
        float before = creature.getHealth(); RECOVERY.set(recovery);
        try { action.run(); return Math.min(Math.max(0, creature.getHealth() - before), recovery.eligible); }
        finally { if (previous == null) RECOVERY.remove(); else RECOVERY.set(previous); }
    }
    /** Loading stored health is not healing, damage, or a new source observation. */
    public static void readEntity(net.minecraft.entity.Entity entity, Runnable action) {
        if (!(entity instanceof MobEntity creature)) { action.run(); return; }
        var previous = READING.get();
        var active = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<MobEntity, Boolean>());
        if (previous != null) active.addAll(previous); active.add(creature); READING.set(active);
        try { action.run(); }
        finally { if (previous == null) READING.remove(); else READING.set(previous); }
    }
}
