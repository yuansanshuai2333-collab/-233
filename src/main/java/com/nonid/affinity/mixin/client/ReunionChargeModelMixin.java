package com.nonid.affinity.mixin.client;
import com.nonid.affinity.client.ReunionChargeClient;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.HeldItemContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Composable local argument substitution; never edits a held stack or cancels vanilla rendering. */
@Mixin(ItemModelManager.class)
public class ReunionChargeModelMixin {
 @ModifyVariable(method="update",at=@At("HEAD"),argsOnly=true,ordinal=0)
 private ItemStack affinity$charge(ItemStack stack,@Local(argsOnly=true)ItemDisplayContext display,@Local(argsOnly=true)HeldItemContext holder){
  return ReunionChargeClient.renderCopy(stack,display,holder);
 }
}
