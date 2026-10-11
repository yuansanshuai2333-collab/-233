package com.nonid.affinity.mixin;

import com.nonid.affinity.service.RelationshipBehavior;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class RelationshipMobTargetMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void affinity$setTarget(LivingEntity target, CallbackInfo ci) {
        if (RelationshipBehavior.blocksInitiation((MobEntity)(Object)this, target)) ci.cancel();
    }
    @Inject(method = "tick", at = @At("HEAD"))
    private void affinity$revalidate(CallbackInfo ci) {
        MobEntity mob = (MobEntity)(Object)this;
        if (!(mob.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld)) return;
        if (RelationshipBehavior.blocksInitiation(mob, mob.getTarget())) RelationshipBehavior.writeTarget(mob, null);
        var brain = mob.getBrain();
        // Goal-based mobs such as zombies do not register this Brain memory at all.
        if (!brain.hasMemoryModule(MemoryModuleType.ATTACK_TARGET)) return;
        var target = brain.getOptionalRegisteredMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (RelationshipBehavior.blocksInitiation(mob, target)) {
            brain.forget(MemoryModuleType.ATTACK_TARGET);
            brain.forget(MemoryModuleType.ANGRY_AT);
        }
        if (brain.hasMemoryModule(MemoryModuleType.ROAR_TARGET)) {
            var roar = brain.getOptionalRegisteredMemory(MemoryModuleType.ROAR_TARGET).orElse(null);
            if (RelationshipBehavior.blocksInitiation(mob, roar)) brain.forget(MemoryModuleType.ROAR_TARGET);
        }
    }
}
