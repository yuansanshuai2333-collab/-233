package com.nonid.affinity.mixin;

import com.nonid.affinity.service.RelationshipBehavior;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.WardenEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WardenEntity.class)
public abstract class RelationshipWardenMixin {
    @Inject(method = "mobTick", at = @At("HEAD"))
    private void affinity$keepDigCooldown(net.minecraft.server.world.ServerWorld world, CallbackInfo ci) {
        WardenEntity self = (WardenEntity)(Object)this;
        if (com.nonid.affinity.service.FriendRetention.protects(self)) {
            // Also recover an old save loaded in the digging pose without a running DigTask.
            if (self.getPose() == net.minecraft.entity.EntityPose.DIGGING)
                self.setPose(net.minecraft.entity.EntityPose.STANDING);
            self.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.DIG_COOLDOWN,
                    net.minecraft.util.Unit.INSTANCE, 1200L);
        }
    }
    @Inject(method = "isValidTarget", at = @At("HEAD"), cancellable = true)
    private void affinity$valid(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof LivingEntity living && RelationshipBehavior.blocksInitiation((WardenEntity)(Object)this, living))
            cir.setReturnValue(false);
    }
    @Inject(method = "updateAttackTarget", at = @At("HEAD"), cancellable = true)
    private void affinity$target(LivingEntity target, CallbackInfo ci) {
        if (RelationshipBehavior.blocksInitiation((WardenEntity)(Object)this, target)) ci.cancel();
    }
}
