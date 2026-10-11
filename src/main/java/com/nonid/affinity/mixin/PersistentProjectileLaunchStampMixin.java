package com.nonid.affinity.mixin;

import com.nonid.affinity.service.CompanionCombatController;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Covers the ranged case where the projectile's owner is assigned on the PERSISTENT path.
 *
 * <p>Disassembly of the mapped jar shows {@code PersistentProjectileEntity#setOwner(Entity)} calls
 * {@code ProjectileEntity#setOwner(Entity)} as its first action, and then only reads the owner again to
 * decide its pickup rule. So for an arrow BOTH injections fire: the superclass one first, then this one.
 *
 * <p>That is deliberately left in place rather than removed, and it is safe because the stamp is
 * idempotent for the same companion and generation: the second call keeps the record the first one made,
 * including its original launch tick. {@code ProjectileStamps#record} owns that rule, so this class does
 * not need to know whether the superclass call happened.
 *
 * <p>Why keep it at all if the superclass path already covers arrows: a hook on the DECLARING class is
 * what survives a change in which method the arrow path actually calls. If a future version stops
 * delegating to the superclass, removing this would silently lose the stamp, and the verification below
 * would say so. It is also the anchor that {@code ProjectileStampMixinContractTest} asserts on.
 *
 * <p>The only real hazard would be a stamp with a DIFFERENT generation arriving second and overwriting
 * the first. That cannot drive the new generation in: both calls sample
 * {@code runtime.progress.generation()} within the same tick, and they receive the same companion. If a
 * projectile is ever handed to a DIFFERENT companion, {@code record} drops the stamp rather than
 * rebinding it, because a projectile changing hands is not a launch.
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileLaunchStampMixin {

    @Inject(method = "setOwner(Lnet/minecraft/entity/Entity;)V", at = @At("TAIL"))
    private void noncreatureaffinity$stampPersistentProjectileLaunch(Entity owner, CallbackInfo ci) {
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        // Passing null through for a non-companion owner is deliberate: it must be recorded as terminal,
        // so a projectile handed to a player and later back to a companion cannot be revived. See
        // ProjectileStamps#observeOwnerAssignment.
        MobEntity companion = owner instanceof MobEntity mob ? mob : null;
        LivingEntity aimed = companion == null ? null : companion.getTarget();
        CompanionCombatController.onCompanionProjectileOwnerAssigned(companion, self, self.getUuid(),
                aimed);
    }
}
