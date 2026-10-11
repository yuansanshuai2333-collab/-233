package com.nonid.affinity.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nonid.affinity.service.HealthHistory;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntity.class)
public abstract class RelationshipHealthMixin {
    @Unique private DamageSource affinity$damageSource;
    @Unique private boolean affinity$feedbackSent;
    @WrapMethod(method = "damage", require = 1)
    private boolean affinity$damage(ServerWorld world, DamageSource source, float amount, Operation<Boolean> original) {
        var previous = affinity$damageSource; boolean previousFeedback = affinity$feedbackSent;
        affinity$damageSource = source; affinity$feedbackSent = false;
        try { return original.call(world, source, amount); }
        finally { affinity$damageSource = previous; affinity$feedbackSent = previousFeedback; }
    }
    @WrapMethod(method = "setHealth", require = 1)
    private void affinity$health(float value, Operation<Void> original) {
        var self = (LivingEntity) (Object) this; float before = self.getHealth();
        original.call(value);
        if (self instanceof MobEntity creature) {
            boolean countAttack = affinity$damageSource != null && self.getHealth() < before && !affinity$feedbackSent;
            if (countAttack) affinity$feedbackSent = true;
            HealthHistory.changed(creature, before, self.getHealth(), affinity$damageSource, countAttack);
        }
    }
}
