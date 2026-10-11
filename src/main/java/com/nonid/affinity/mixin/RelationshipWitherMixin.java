package com.nonid.affinity.mixin;

import com.nonid.affinity.service.RelationshipBehavior;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WitherEntity.class)
public abstract class RelationshipWitherMixin {

    @Inject(method = "shootSkullAt(ILnet/minecraft/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private void affinity$aim(int head, LivingEntity target, CallbackInfo ci) {
        if (RelationshipBehavior.blocksInitiation((WitherEntity)(Object)this, target)) ci.cancel();
    }

    // Only the autonomous, random coordinate shot in mobTick; aimed combat shots retain their native behavior.
    @WrapOperation(method = "mobTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/boss/WitherEntity;shootSkullAt(IDDDZ)V"))
    private void affinity$randomShot(WitherEntity self, int head, double x, double y, double z, boolean charged,
                                    Operation<Void> original) {
        if (!RelationshipBehavior.isPeacefulFriend(self)) original.call(self, head, x, y, z, charged);
    }
}
