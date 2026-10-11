package com.nonid.affinity.mixin;

import com.nonid.affinity.reunion.ReunionCraftingGuard;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also guard each quick-move iteration, including direct calls by inventory utilities. */
@Mixin({CraftingScreenHandler.class, PlayerScreenHandler.class})
public abstract class ReunionCraftingQuickMoveMixin {
    @Inject(method = "quickMove", at = @At("HEAD"), cancellable = true)
    private void noncreatureaffinity$validateRecharge(PlayerEntity player, int slot,
                                                      CallbackInfoReturnable<ItemStack> cir) {
        var batch=com.nonid.affinity.reunion.ReunionBatchCrafting.quickMove((ScreenHandler)(Object)this,player,slot);
        if(batch!=null){cir.setReturnValue(batch);return;}
        if (slot == 0 && !ReunionCraftingGuard.allow(((ScreenHandler)(Object)this).getSlot(0), player))
            cir.setReturnValue(ItemStack.EMPTY);
    }
}
