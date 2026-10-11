package com.nonid.affinity.non.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nonid.affinity.non.compat.DebugStartScope;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Marks only NON's explicit debug selection, including cleanup if the engine throws. */
@Mixin(targets = "com.nonid.NonDebugAnimationSelectionSystem", remap = false)
public abstract class NonDebugStartMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method = "createSession",
            at = @org.spongepowered.asm.mixin.injection.At("RETURN"), require = 1)
    private static void noncreatureaffinity$remember(
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Object> cir) {
        com.nonid.affinity.non.compat.NativeSelection.remember(cir.getReturnValue());
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "canStartImmediately",
            at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true, require = 1)
    private static void noncreatureaffinity$alwaysShow(@Coerce Object session,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (com.nonid.affinity.non.compat.NativeSelection.isAddonMenu(session)) cir.setReturnValue(false);
    }

    @WrapMethod(method = "executeChoice", require = 0)
    private static void noncreatureaffinity$debugChoice(ServerWorld world, ServerPlayerEntity requester,
            @Coerce Object session, @Coerce Object choice, Operation<Void> original) {
        if (com.nonid.affinity.non.compat.NativeSelection.allow(session, requester)) {
            DebugStartScope.runAs(requester == null ? null : requester.getUuid(),
                    () -> original.call(world, requester, session, choice));
        }
    }
}
