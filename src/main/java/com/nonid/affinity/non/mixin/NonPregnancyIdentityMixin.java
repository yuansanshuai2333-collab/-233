package com.nonid.affinity.non.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nonid.NonConfig;
import com.nonid.entity.PregnancyEggEntity;
import com.nonid.NeedsOfNature.PregnancyVariantData;
import com.nonid.affinity.non.NonBirthIdentity;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.nonid.NeedsOfNature")
public abstract class NonPregnancyIdentityMixin {
    @Shadow @Final private static Map<UUID, Object> PREGNANCY_HATCH_BY_INSTANCE;

    @ModifyReturnValue(method = "capturePregnancyVariantData", at = @At("RETURN"), require = 1)
    private static PregnancyVariantData noncreatureaffinity$capture(PregnancyVariantData original, ServerWorld world, Entity donor) {
        return NonBirthIdentity.capture(world, donor, original);
    }
    private static NonBirthIdentity.Scope noncreatureaffinity$scope(ServerWorld world, UUID instance) {
        Object snapshot = PREGNANCY_HATCH_BY_INSTANCE.get(instance);
        if (!(snapshot instanceof NonPregnancyHatchAccess hatch)) return NonBirthIdentity.enter(null);
        return NonBirthIdentity.enterSnapshot(world, instance, hatch.noncreatureaffinity$source(),
                NonBirthIdentity.evidence(world, hatch.noncreatureaffinity$mother(),
                    hatch.noncreatureaffinity$source(), hatch.noncreatureaffinity$variant()));
    }
    @WrapMethod(method = "handlePregnancyBirthCue")
    private static void noncreatureaffinity$cue(ServerWorld world, UUID instance, Operation<Void> original) {
        try (var scope = noncreatureaffinity$scope(world, instance)) { original.call(world, instance); }
    }
    @WrapMethod(method = "finalizePregnancyHatch")
    private static void noncreatureaffinity$fallback(ServerWorld world, UUID instance, boolean cue, Operation<Void> original) {
        try (var scope = noncreatureaffinity$scope(world, instance)) { original.call(world, instance, cue); }
    }
    @WrapMethod(method = "spawnPregnancyOutcome")
    private static int noncreatureaffinity$outcome(ServerWorld world, UUID instance, ServerPlayerEntity player,
            Identifier source, Identifier target, Identifier item, Identifier block, String mode, int count,
            PregnancyVariantData variant, Operation<Integer> original) {
        try (var scope=NonBirthIdentity.enterOutcome(world,instance,player==null?null:player.getUuid(),source,variant)) {
            return original.call(world,instance,player,source,target,item,block,mode,count,variant);
        }
    }
    @WrapOperation(method = "spawnPregnancyEggs", at = @At(value = "INVOKE", target = "Lcom/nonid/entity/PregnancyEggEntity;configure(Ljava/util/UUID;Lnet/minecraft/util/Identifier;Lnet/minecraft/util/Identifier;Lnet/minecraft/util/Identifier;Ljava/lang/String;JLcom/nonid/NeedsOfNature$PregnancyVariantData;Lcom/nonid/NonConfig$EggProfile;)V"), require = 1)
    private static void noncreatureaffinity$egg(PregnancyEggEntity egg, UUID mother, Identifier source,
            Identifier target, Identifier block, String mode, long hatchAt, PregnancyVariantData variant,
            NonConfig.EggProfile profile, Operation<Void> original) {
        try(var scope=NonBirthIdentity.grantEgg(egg,mother,source)) {
            original.call(egg,mother,source,target,block,mode,hatchAt,variant,profile);
        }
    }
}
