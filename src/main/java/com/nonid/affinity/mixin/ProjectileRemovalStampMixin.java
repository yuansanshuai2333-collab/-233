package com.nonid.affinity.mixin;

import com.nonid.affinity.service.CompanionCombatController;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Releases a projectile's launch attribution when its entity is removed, from the ONE place every removal
 * funnels through.
 *
 * <p><b>Why {@code setRemoved} and not {@code remove}.</b> The first version hooked {@code remove}. That
 * covers only a genuine destroy: disassembly shows {@code Entity#removeFromDimension} calls
 * {@code setRemoved(CHANGED_DIMENSION)} directly, and the chunk-unload path in
 * {@code ServerEntityManager} calls {@code setRemoved(UNLOADED_TO_CHUNK)} directly as well. Neither goes
 * through {@code remove}, so an unloaded projectile's entry was never released while a destroyed one's was
 * -- the exact opposite of what the code claimed. {@code setRemoved} is {@code final} and is the common
 * funnel for all three, which is why it is the anchor.
 *
 * <p><b>{@code final} does not mean "runs once".</b> It only means subclasses cannot override it. The hook
 * must therefore survive being called more than once for the same entity, and the cleanup it calls is
 * idempotent: the first call releases the entry and every later call is a no-op.
 *
 * <p><b>Why the identity passed is the entity instance.</b> A dimension change creates a NEW entity and
 * copies the old entity's uuid onto it, so for a moment two live entities share an id. Releasing "the entry
 * for this uuid" would erase the new entity's terminal state when the OLD entity is removed. The store is
 * keyed on the instance for exactly this reason.
 *
 * <p>One call is enough, so a projectile that was read from storage and then simply destroyed is released
 * immediately: nothing waits for a second removal, and no protection state is skipped on a guess about the
 * call order.
 */
@Mixin(Entity.class)
public abstract class ProjectileRemovalStampMixin {

    @Inject(method = "setRemoved(Lnet/minecraft/entity/Entity$RemovalReason;)V", at = @At("HEAD"))
    private void noncreatureaffinity$releaseProjectileStamp(Entity.RemovalReason reason,
                                                            CallbackInfo ci) {
        if ((Object) this instanceof ProjectileEntity) {
            CompanionCombatController.onProjectileInstanceRemoved(this);
        }
    }
}
