package com.nonid.affinity.mixin;

import com.nonid.affinity.service.CompanionCombatController;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.storage.ReadView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Places a projectile that came back from STORAGE into the terminal, non-attributable state.
 *
 * <p><b>Why this is needed at all.</b> Entities are serialized by UUID: {@code ChunkSerializer.write}
 * stores {@code entity.getUuid()} rather than the entity object, so an id-keyed attribution cannot follow
 * a projectile through storage. Without this hook a reloaded arrow would be indistinguishable from one
 * that was never seen, and the next {@code setOwner} could attribute it to whatever fight was current
 * then -- the same class of defect this module has already fixed twice in other forms.
 *
 * <p>An explicit terminal state rather than merely dropping the record, so a later {@code setOwner} cannot
 * revive the projectile as a new arrow. The service also records that this id is currently protected by a
 * storage read, because {@code Entity#teleportCrossDimension} removes the OLD entity AFTER the new one has
 * read its data and both share the uuid -- without that, the old entity's removal would erase the new
 * entity's state.
 *
 * <p><b>Both this hook and the subclass one fire for an arrow:</b> the subclass implementation begins with
 * {@code invokespecial ProjectileEntity.readCustomData}. That is harmless because the service call is
 * idempotent, and it is asserted as such.
 *
 * <p><b>Why {@code readCustomData}.</b> In this version the old {@code readNbt} is gone; loading runs
 * through the View-based storage API, and {@code Entity.readData} dispatches to the abstract
 * {@code readCustomData}. Reading custom data happens when an entity is restored from storage, which is the
 * one point that covers every way a projectile can come back: a chunk reload, a world reload, a dimension
 * change and a server restart. A restart also needs it on its own terms, because engagement generations
 * start over, so a generation recorded before the restart would be meaningless afterwards and could
 * collide with a fresh one.
 *
 * <p><b>Cost, stated plainly:</b> an arrow fired before a reload no longer contributes combat progress
 * after it. See {@code ProjectileStamps} for why preserving the pre-reload attribution was rejected rather
 * than approximated -- it would require persisting the state and solving the generation-restart problem,
 * not merely swapping a weak map for a strong one.
 */
@Mixin(ProjectileEntity.class)
public abstract class ProjectileStorageStampMixin {

    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void noncreatureaffinity$invalidateStampFromStorage(ReadView view, CallbackInfo ci) {
        CompanionCombatController.onProjectileLoadedFromStorage(this, ((ProjectileEntity) (Object) this).getUuid());
    }
}
