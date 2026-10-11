package com.nonid.affinity.mixin;

import com.nonid.affinity.service.FriendDamageProtection;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.CreakingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreakingEntity.class)
public abstract class CreakingFriendDamageMixin {
    @Inject(method = "damage", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/entity/mob/CreakingEntity;becomeAngryAndGetPlayer(Lnet/minecraft/entity/damage/DamageSource;)Lnet/minecraft/entity/player/PlayerEntity;"), cancellable = true)
    private void affinity$protectHeart(ServerWorld world, DamageSource source, float amount,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (source.getAttacker() instanceof ServerPlayerEntity player
                && FriendDamageProtection.blocks((CreakingEntity)(Object)this, player)) cir.setReturnValue(false);
    }
}
