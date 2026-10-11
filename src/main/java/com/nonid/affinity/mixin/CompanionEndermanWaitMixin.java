package com.nonid.affinity.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nonid.affinity.service.CompanionMovementGoal;
import net.minecraft.entity.mob.EndermanEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Only the autonomous daylight attempt. Damage avoidance and explicit teleports retain their original paths. */
@Mixin(EndermanEntity.class)
public abstract class CompanionEndermanWaitMixin {
    @WrapOperation(method="mobTick",at=@At(value="INVOKE",target="Lnet/minecraft/entity/mob/EndermanEntity;teleportRandomly()Z"))
    private boolean affinity$holdAutonomousTeleport(EndermanEntity entity,Operation<Boolean> original){
        return !CompanionMovementGoal.holdsPosition(entity)&&original.call(entity);
    }
}
