package com.nonid.affinity.mixin;

import com.nonid.affinity.service.FriendRetention;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.ai.brain.task.DigTask;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DigTask.class)
public abstract class FriendWardenDigMixin {
    @Inject(method="shouldRun(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/mob/WardenEntity;)Z",at=@At("HEAD"),cancellable=true)
    private void affinity$stay(ServerWorld world,WardenEntity mob,CallbackInfoReturnable<Boolean> cir) {
        if(FriendRetention.protects(mob)) cir.setReturnValue(false);
    }
    @Inject(method="finishRunning(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/mob/WardenEntity;J)V",at=@At("HEAD"),cancellable=true)
    private void affinity$finishSafely(ServerWorld world,WardenEntity mob,long time,CallbackInfo ci) {
        if(FriendRetention.protects(mob)) {
            if(mob.getPose()==EntityPose.DIGGING) mob.setPose(EntityPose.STANDING);
            ci.cancel();
        }
    }
}
