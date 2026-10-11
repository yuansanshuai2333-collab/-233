package com.nonid.affinity.mixin.client;
import com.nonid.affinity.client.CompanionHighlightClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(MinecraftClient.class)
public abstract class CompanionHighlightMixin {
    @Inject(method="hasOutline",at=@At("RETURN"),cancellable=true)
    private void affinity$privateHighlight(Entity entity, CallbackInfoReturnable<Boolean> result){
        if(!result.getReturnValueZ()&&CompanionHighlightClient.outlines(entity))result.setReturnValue(true);
    }
}
