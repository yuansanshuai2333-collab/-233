package com.nonid.affinity.mixin;
import com.nonid.affinity.service.FriendRetention;
import net.minecraft.entity.mob.EndermiteEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(EndermiteEntity.class)
public abstract class FriendEndermiteLifetimeMixin {
    @WrapOperation(method="tickMovement",at=@At(value="INVOKE",target="Lnet/minecraft/entity/mob/EndermiteEntity;isPersistent()Z"))
    private boolean affinity$pauseLifetime(EndermiteEntity self, Operation<Boolean> original) {
        return original.call(self) || FriendRetention.protects(self);
    }
}
