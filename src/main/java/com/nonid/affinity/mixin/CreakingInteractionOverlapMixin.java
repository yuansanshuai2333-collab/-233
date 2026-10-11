package com.nonid.affinity.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.nonid.affinity.non.NonBridge;
import net.minecraft.entity.mob.CreakingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Ignore only the active partner's animation overlap in vanilla's stuck test.
 * Vanilla still checks other players, resets its counter, and retains all heart removal rules. */
@Mixin(CreakingEntity.class)
public abstract class CreakingInteractionOverlapMixin {
    @WrapOperation(method = "isStuckWithPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/math/Box;contains(Lnet/minecraft/util/math/Vec3d;)Z"))
    private boolean affinity$animationOverlap(Box box, Vec3d eye, Operation<Boolean> original,
                                             @Local PlayerEntity player) {
        if (!original.call(box, eye)) return false;
        var creaking = (CreakingEntity)(Object)this;
        return !(creaking.getEntityWorld() instanceof ServerWorld world
                && player instanceof ServerPlayerEntity serverPlayer
                && NonBridge.hasActiveInteraction(world, creaking, serverPlayer));
    }
}
