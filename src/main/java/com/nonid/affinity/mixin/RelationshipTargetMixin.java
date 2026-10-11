package com.nonid.affinity.mixin;

import com.nonid.affinity.service.RelationshipBehavior;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class RelationshipTargetMixin {
    @Inject(method = "canTarget(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void affinity$allowTarget(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (RelationshipBehavior.blocksInitiation((LivingEntity)(Object)this, target)) cir.setReturnValue(false);
    }
}
