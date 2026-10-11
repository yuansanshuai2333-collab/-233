package com.nonid.affinity.mixin;

import com.nonid.affinity.service.CompanionCombatController;
import com.nonid.affinity.service.TargetRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Binds an engagement generation to a projectile at the moment it is bound to its shooter.
 *
 * <p>{@code setOwner} is the real launch path: the ranged-attack goal creates the projectile and then
 * assigns the shooter, so this is where "who fired this, and in which fight" becomes knowable. The
 * stamp is stored per PROJECTILE, because a projectile outlives the tick it was fired in and several can
 * be in the air at once.
 *
 * <p><b>Why the generation must be captured here.</b> The alternative -- reading the generation when the
 * arrow lands -- reads whatever fight is current at impact, which is precisely the value the later check
 * compares against, so it would verify nothing. Capturing it at launch is what makes a hit from a
 * finished fight detectable.
 *
 * <p>Scope of the support, stated plainly: this covers projectiles that receive an owner through
 * {@code ProjectileEntity#setOwner}. The species this module currently declares as combat-capable are
 * hostile mobs, wolves and cats; the concrete ranged case is a <b>skeleton's arrow</b>, which is an
 * {@code ArrowEntity} extending {@code PersistentProjectileEntity}, and therefore ALSO overrides
 * {@code setOwner} -- {@code PersistentProjectileEntityMixin} covers that override. Both are needed:
 * an injection into a parent method does not run for a child's override.
 *
 * <p>A projectile whose owner is never assigned through these methods (a splash potion thrown by a
 * different code path, a trident's thrown form, a modded projectile with its own launcher) gets no stamp
 * and therefore does not renew a fight. That is the safe direction, and it is a limitation rather than a
 * claim of full ranged support.
 */
@Mixin(ProjectileEntity.class)
public abstract class ProjectileLaunchStampMixin {

    @Inject(method = "setOwner(Lnet/minecraft/entity/Entity;)V", at = @At("TAIL"))
    private void noncreatureaffinity$stampProjectileLaunch(Entity owner, CallbackInfo ci) {
        ProjectileEntity self = (ProjectileEntity) (Object) this;
        // A null (or non-mob) owner is passed through as null on purpose: it must be recorded as a
        // terminal, non-attributable state so that handing the projectile to a companion later cannot
        // revive it. Dropping the record instead would make it look like a projectile never seen before.
        MobEntity companion = owner instanceof MobEntity mob ? mob : null;
        LivingEntity aimed = companion == null ? null : companion.getTarget();
        CompanionCombatController.onCompanionProjectileOwnerAssigned(companion, self, self.getUuid(),
                aimed);
    }
}
