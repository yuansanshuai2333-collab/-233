package com.nonid.affinity.non.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nonid.NonConfig;
import com.nonid.NeedsOfNature.PregnancyVariantData;
import com.nonid.affinity.data.AffinityAttachments;
import com.nonid.affinity.data.BirthIdentity;
import com.nonid.affinity.non.NonBirthIdentity;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.nonid.entity.PregnancyEggEntity")
public abstract class NonPregnancyEggIdentityMixin {
    @Shadow private UUID ownerUuid;
    @Shadow private PregnancyVariantData variantData;
    @Shadow public abstract Identifier getSourceEntityTypeId();

    @Inject(method = "configure", at = @At("TAIL"), require = 1)
    private void noncreatureaffinity$persist(UUID mother, Identifier source, Identifier target, Identifier block,
            String mode, long hatchAt, PregnancyVariantData variant, NonConfig.EggProfile profile, CallbackInfo ci) {
        var entity = (Entity)(Object)this;
        if (!(entity.getEntityWorld() instanceof ServerWorld world)) return;
        var birth = NonBirthIdentity.eggEvidence(entity, world, mother, source, variant);
        if (birth != null) entity.setAttached(AffinityAttachments.BIRTH_IDENTITY, BirthIdentity.toNbt(birth));
        else entity.removeAttached(AffinityAttachments.BIRTH_IDENTITY);
    }
    @WrapMethod(method = "hatch")
    private void noncreatureaffinity$hatch(ServerWorld world, Identifier target, Operation<Void> original) {
        var birth = BirthIdentity.fromNbt(((Entity)(Object)this).getAttached(AffinityAttachments.BIRTH_IDENTITY));
        if (birth == null || !birth.viewer().equals(ownerUuid))
            birth = NonBirthIdentity.evidence(world, ownerUuid, getSourceEntityTypeId(), variantData);
        try (var scope = NonBirthIdentity.enter(birth)) { original.call(world, target); }
    }
}
