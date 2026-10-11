package com.nonid.affinity.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nonid.affinity.service.CompanionMovementGoal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.EndermanEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Covers a goal already running when WAIT is selected, without changing target selection or damage handling. */
@Mixin(targets="net.minecraft.entity.mob.EndermanEntity$TeleportTowardsPlayerGoal")
public abstract class CompanionEndermanTargetTeleportMixin {
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/entity/mob/EndermanEntity;teleportRandomly()Z"))
    private boolean affinity$holdGazeTeleport(EndermanEntity entity,Operation<Boolean> original){
        return !CompanionMovementGoal.holdsPosition(entity)&&original.call(entity);
    }
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/entity/mob/EndermanEntity;teleportTo(Lnet/minecraft/entity/Entity;)Z"))
    private boolean affinity$holdPursuitTeleport(EndermanEntity entity,Entity target,Operation<Boolean> original){
        return !CompanionMovementGoal.holdsPosition(entity)&&original.call(entity,target);
    }
}
