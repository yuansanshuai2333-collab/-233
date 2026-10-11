package com.nonid.affinity.mixin;

import com.nonid.affinity.gift.GiftDropProtection;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class GiftHopperProtectionMixin {
    @Inject(method = "extract(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/entity/ItemEntity;)Z", at = @At("HEAD"), cancellable = true)
    private static void affinity$giftHopper(Inventory inventory, ItemEntity item, CallbackInfoReturnable<Boolean> ci) { if (GiftDropProtection.privatePickup(item, null)) ci.setReturnValue(false); }
}
