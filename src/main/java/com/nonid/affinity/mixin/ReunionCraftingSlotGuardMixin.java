package com.nonid.affinity.mixin;

import com.nonid.affinity.reunion.ReunionCraftingGuard;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class ReunionCraftingSlotGuardMixin {
    @Inject(method = "canTakeItems", at = @At("HEAD"), cancellable = true)
    private void noncreatureaffinity$validateRecharge(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (!ReunionCraftingGuard.allow((Slot)(Object)this, player)) cir.setReturnValue(false);
    }
}
