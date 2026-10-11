package com.nonid.affinity.mixin;

import com.nonid.affinity.service.CreakingMemory;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreakingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

/** Friendship prevents attacks, but does not change vanilla gaze freezing. */
@Mixin(CreakingEntity.class)
public abstract class CreakingGazeMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method = "tryAttack", at = @At("HEAD"), cancellable = true)
    private void affinity$attack(net.minecraft.server.world.ServerWorld world, net.minecraft.entity.Entity target,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof LivingEntity living && com.nonid.affinity.service.RelationshipBehavior.blocksInitiation(
                (CreakingEntity)(Object)this, living)) cir.setReturnValue(false);
    }
    @org.spongepowered.asm.mixin.injection.Inject(method = "mobTick", at = @At("HEAD"))
    private void affinity$target(net.minecraft.server.world.ServerWorld world,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        CreakingEntity self = (CreakingEntity)(Object)this;
        if (com.nonid.affinity.service.RelationshipBehavior.blocksInitiation(self, self.getTarget()))
            com.nonid.affinity.service.RelationshipBehavior.writeTarget(self, null);
    }
    @WrapOperation(method = "shouldBeUnrooted", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/mob/CreakingEntity;canTarget(Lnet/minecraft/entity/LivingEntity;)Z"))
    private boolean affinity$gaze(CreakingEntity self, LivingEntity target, Operation<Boolean> original) {
        return CreakingMemory.gazeCanTarget(self, () -> original.call(self, target));
    }
}
