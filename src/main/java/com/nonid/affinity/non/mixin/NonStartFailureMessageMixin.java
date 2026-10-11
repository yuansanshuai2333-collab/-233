package com.nonid.affinity.non.mixin;

import com.nonid.affinity.service.InteractionStartFeedback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reads the engine's own refusal key; never cancels, retries, or overrides a start. */
@Mixin(targets = "com.nonid.internal.animation.server.ServerAnimationController", remap = false)
public abstract class NonStartFailureMessageMixin {
    @Inject(method = "dbg", at = @At("HEAD"), require = 0, remap = false)
    private static void noncreatureaffinity$captureStartReason(String key, Object[] args,
                                                              CallbackInfoReturnable<?> cir) {
        InteractionStartFeedback.engineMessage(key);
    }
}
