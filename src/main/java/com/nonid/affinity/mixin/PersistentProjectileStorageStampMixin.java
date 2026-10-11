package com.nonid.affinity.mixin;

import com.nonid.affinity.service.CompanionCombatController;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.storage.ReadView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The same storage-read invalidation for persistent projectiles.
 *
 * <p><b>Corrected fact.</b> An earlier version of this class claimed that
 * {@code PersistentProjectileEntity} does NOT call its superclass implementation, and that a base hook
 * would therefore silently miss arrows. Disassembly shows the opposite:
 * {@code PersistentProjectileEntity#readCustomData} begins with
 * {@code invokespecial ProjectileEntity.readCustomData}, so BOTH hooks fire for an arrow.
 *
 * <p>This class is kept anyway, and the duplicate firing is harmless because
 * {@code onProjectileLoadedFromStorage} is idempotent -- it writes the same terminal state and the same
 * storage mark however many times it runs. Keeping the subclass hook also means the invalidation does not
 * depend on the delegation continuing to exist; if a future version stops calling the superclass, the
 * arrow path is still covered. The contract test asserts both declarations and the delegation.
 *
 * <p>{"Reading custom data"} being covered is NOT the same as the whole dimension-change flow being
 * verified: the ordering between the new entity's read and the old entity's removal is what the store has
 * to survive, and that is asserted separately by the cross-dimension lifecycle test.
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileStorageStampMixin {

    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void noncreatureaffinity$invalidatePersistentStampFromStorage(ReadView view,
                                                                         CallbackInfo ci) {
        CompanionCombatController.onProjectileLoadedFromStorage(this,
                ((PersistentProjectileEntity) (Object) this).getUuid());
    }
}
