package com.nonid.affinity.service;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import com.nonid.affinity.non.NonBridge;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Records successful damage, with hostility sampled before the damage is applied. */
public final class CompanionDamageEvents {
    private record Before(UUID attacker, long tick, boolean hostile, EscapeAttackSettlement escape, boolean friend) {}
    private static final Map<LivingEntity, Before> BEFORE = new WeakHashMap<>();
    private CompanionDamageEvents() {}
    public static void clear() { BEFORE.clear(); }
    public static boolean markedEscape(LivingEntity victim, ServerPlayerEntity player) {
        var before = BEFORE.get(victim);
        return before != null && before.escape() != null && before.attacker().equals(player.getUuid())
                && before.tick() == victim.getEntityWorld().getTime();
    }
    public static EscapeAttackSettlement escapeSettlement(LivingEntity victim, ServerPlayerEntity player) {
        var before = BEFORE.get(victim);
        return markedEscape(victim, player) ? before.escape() : null;
    }

    public static boolean wasFriend(LivingEntity victim, ServerPlayerEntity player) {
        var before = BEFORE.get(victim);
        return before != null && before.friend() && before.attacker().equals(player.getUuid())
                && before.tick() == victim.getEntityWorld().getTime();
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
            // Also covers sonic attacks and projectiles fired before the relationship changed.
            if (source.getAttacker() instanceof MobEntity actor
                    && RelationshipBehavior.blocksInitiation(actor, victim)) return false;
            if (source.getAttacker() instanceof ServerPlayerEntity owner) {
                if (FriendDamageProtection.blocks(victim, owner)) return false;
                boolean hostile = CombatEligibility.isThreatTo(victim, owner);
                // Marked here rather than after the damage resolves: an attack absorbed by armour or
                // invulnerability frames deals no damage, and previously skipped the marking
                // entirely, so the escape still paid out.
                var relation = victim instanceof MobEntity mob ? AffinityService.getRelation(mob, owner) : null;
                boolean friend = relation != null && RelationshipStages.friend(relation.getAffinity(),
                        com.nonid.affinity.NonCreatureAffinity.getConfig());
                var escape = NonBridge.prepareEscapeFeedback(owner, victim);
                NonBridge.markEscaped(owner, victim);
                BEFORE.put(victim, new Before(owner.getUuid(), victim.getEntityWorld().getTime(), hostile, escape, friend));
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
            Before before = BEFORE.remove(victim);
            if (taken <= 0 || !(source.getAttacker() instanceof LivingEntity attacker)) return;
            if (attacker instanceof ServerPlayerEntity owner) {
                boolean wasHostile = before != null && before.attacker().equals(owner.getUuid())
                        && before.tick() == victim.getEntityWorld().getTime() && before.hostile();
                CompanionEventLog.recordOwnerAttack(owner, victim, wasHostile);
            }
            if (victim instanceof ServerPlayerEntity owner) {
                CompanionEventLog.recordOwnerHurt(owner, attacker, true);
            }
            if (victim instanceof MobEntity companion && CompanionService.companionOwner(companion) != null) {
                CompanionEventLog.recordSelfDefence(companion, attacker);
            }
            // A landing hit by a companion renews its fight ONLY when it can be attributed to an attack
            // this module recorded.
            //
            // Direct melee: the damage source's own entity is the companion, and the stamp bracketing the
            // attack call must still be live.
            //
            // Ranged: the damage came from a PROJECTILE the companion fired. The generation is read from
            // the stamp bound to that projectile at launch, never from the fight that happens to be
            // current at impact -- that difference is the entire point, because an arrow fired during an
            // earlier fight lands on the same creature.
            if (attacker instanceof MobEntity companion && victim instanceof LivingEntity target) {
                if (source.getSource() == attacker) {
                    CompanionCombatController.onCompanionHit(companion, target, true);
                } else if (source.getSource() instanceof ProjectileEntity projectile
                        && projectile.getOwner() == attacker) {
                    // Owned by this companion: credited only if the projectile carries a stamp from a
                    // fight that is still the live one.
                    CompanionCombatController.onCompanionProjectileHit(companion, projectile.getUuid(),
                            target);
                }
                // Anything else (another system's projectile, an explosion, a summoned entity) is not
                // evidence about this companion's fight and is deliberately not credited.
            }

        });
    }
}
