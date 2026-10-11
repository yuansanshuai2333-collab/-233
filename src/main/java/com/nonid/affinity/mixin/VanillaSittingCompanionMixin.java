package com.nonid.affinity.mixin;
import com.nonid.affinity.service.CompanionService;
import net.minecraft.entity.passive.TameableEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Preserve the original sitting operation and mirror an authorized owner's actual order. */
@Mixin(TameableEntity.class)
public abstract class VanillaSittingCompanionMixin {
    @Inject(method="setSitting",at=@At("TAIL"))
    private void affinity$mirrorSit(boolean sitting, CallbackInfo ci) {
        CompanionService.vanillaSittingChanged((TameableEntity)(Object)this, sitting);
    }
}
