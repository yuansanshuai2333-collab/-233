package com.nonid.affinity.non.mixin;

import com.nonid.affinity.non.NonBridge;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records that the player WON a NeedsOfNature struggle, so the settlement knows this was an escape.
 *
 * <p>When a forced mating is started as an attack escape (see the config option
 * {@code nonStruggleScreen}), the player breaks free by mashing A/D on NON's own screen. That path never goes
 * through damage -- attacks cannot be made during an animation -- so the damage-based escape mark would never
 * fire and the escaped mating would look like a plain interruption. NON announces the win by calling
 * {@code completeAttackEscape(world, player, instanceId, attackerUuid)}, whose parameters are all public types,
 * so the instance is matched by name and the instance id does the rest.
 *
 * <p>The mark is scoped to that one instance, and to this module's own sessions: a NON-native attack escape
 * marks nothing of ours, and an instance we never started settles nothing at all.
 *
 * <p>This lives in the optional integration's own configuration, which is switched off unless a verified NON
 * version is loaded, so an incompatible NON build degrades to "no automatic escape mark" rather than failing.
 */
@Mixin(targets = "com.nonid.NeedsOfNature")
public abstract class NonAttackEscapeMixin {

    @Inject(method = "completeAttackEscape", at = @At("HEAD"), require = 0)
    private static void noncreatureaffinity$onAttackEscapeCompleted(
            ServerWorld world, ServerPlayerEntity player, UUID instanceId, UUID attackerUuid,
            CallbackInfo ci) {
        if (player != null) NonBridge.markPlayerEscapeForInstance(instanceId, player.getUuid());
    }
}
