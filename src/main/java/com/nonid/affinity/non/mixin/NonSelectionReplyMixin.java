package com.nonid.affinity.non.mixin;

import com.nonid.affinity.non.compat.NativeSelection;
import com.nonid.internal.animation.network.DebugAnimationChoiceC2SPayload;
import com.nonid.internal.animation.network.DebugAnimationCancelC2SPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Route only an exact addon-issued session before NON's debug-only permission gate. */
@Mixin(targets = "com.nonid.internal.animation.AnimationEngine", remap = false)
public abstract class NonSelectionReplyMixin {
    // Compiler-generated lambda ordinals changed in 1.5.2. Match the verified inner
    // server-thread signature, never the outer (payload, context) receiver with the old name.
    @Inject(method = {
            "lambda$initialize$10(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;Lcom/nonid/internal/animation/network/DebugAnimationChoiceC2SPayload;)V",
            "lambda$initialize$11(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;Lcom/nonid/internal/animation/network/DebugAnimationChoiceC2SPayload;)V"
    }, at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void noncreatureaffinity$choose(ServerPlayNetworking.Context context,
            DebugAnimationChoiceC2SPayload payload, CallbackInfo ci) {
        if (NativeSelection.reply(context.player(), payload.requestId(), payload.choiceIndex())) ci.cancel();
    }

    @Inject(method = {
            "lambda$initialize$12(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;Lcom/nonid/internal/animation/network/DebugAnimationCancelC2SPayload;)V",
            "lambda$initialize$13(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;Lcom/nonid/internal/animation/network/DebugAnimationCancelC2SPayload;)V"
    }, at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void noncreatureaffinity$cancel(ServerPlayNetworking.Context context,
            DebugAnimationCancelC2SPayload payload, CallbackInfo ci) {
        if (NativeSelection.reply(context.player(), payload.requestId(), null)) ci.cancel();
    }
}
