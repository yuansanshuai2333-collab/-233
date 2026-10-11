package com.nonid.affinity.non.mixin;

import com.nonid.affinity.service.FamilyService;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the exact newborn and parent passed by the birth provider, before entity load. */
@Mixin(targets = "com.nonid.NeedsOfNature")
public abstract class NonBirthFamilyMixin {
    @Inject(method = "applyPregnancyMobFlagsForEgg", at = @At("TAIL"), require = 1)
    private static void noncreatureaffinity$recordFamily(MobEntity child, ServerPlayerEntity parent,
                                                        CallbackInfo ci) {
        FamilyService.recordBirth(child, parent, com.nonid.affinity.non.NonBirthIdentity.current());
    }
}
