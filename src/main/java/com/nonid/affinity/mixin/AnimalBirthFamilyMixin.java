package com.nonid.affinity.mixin;

import com.nonid.affinity.service.CompanionArchive;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact breed arguments are evidence; commit only after the child is actually loaded alive. */
@Mixin(AnimalEntity.class)
public abstract class AnimalBirthFamilyMixin {
    @Inject(method = "breed(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/AnimalEntity;Lnet/minecraft/entity/passive/PassiveEntity;)V", at = @At("HEAD"))
    private void affinity$stageBirth(ServerWorld world, AnimalEntity other, PassiveEntity child, CallbackInfo ci) {
        CompanionArchive.stageBirth(child, (AnimalEntity)(Object)this, other);
    }
}
