package com.nonid.affinity.mixin;

import com.nonid.affinity.gift.GiftDropProtection;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ItemEntity.class)
public abstract class GiftItemProtectionMixin {
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void affinity$giftDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        if (GiftDropProtection.active((ItemEntity)(Object)this) && (source.isIn(DamageTypeTags.IS_FIRE) || source.isIn(DamageTypeTags.IS_EXPLOSION) || source.isOf(DamageTypes.CACTUS))) ci.setReturnValue(false);
    }
    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void affinity$giftPickup(PlayerEntity player, CallbackInfo ci) { if (GiftDropProtection.privatePickup((ItemEntity)(Object)this, player.getUuid())) ci.cancel(); }
    @Inject(method = "tryMerge(Lnet/minecraft/entity/ItemEntity;)V", at = @At("HEAD"), cancellable = true)
    private void affinity$giftMerge(ItemEntity other, CallbackInfo ci) { if (GiftDropProtection.active((ItemEntity)(Object)this) || GiftDropProtection.active(other)) ci.cancel(); }
}
