package com.nonid.affinity.mixin;

import com.nonid.affinity.service.RelationshipBehavior;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.SlimeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Slime contact attacks bypass ordinary target predicates. */
@Mixin(SlimeEntity.class)
public abstract class RelationshipSlimeMixin {
    @Inject(method = "damage(Lnet/minecraft/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private void affinity$contactAttack(LivingEntity target, CallbackInfo ci) {
        if (RelationshipBehavior.blocksInitiation((LivingEntity)(Object)this, target)) ci.cancel();
    }
}
