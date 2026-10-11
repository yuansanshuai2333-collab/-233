package com.nonid.affinity.mixin;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.service.AffinityService;
import com.nonid.affinity.service.RelationshipStages;
import com.nonid.affinity.service.ReplayIsolation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Filter the actual aura recipient list, including the elder guardian's subsequent visual packet. */
@Mixin(StatusEffectUtil.class)
public abstract class RelationshipAreaEffectMixin {
    @Inject(method = "method_42145", at = @At("HEAD"), cancellable = true)
    private static void affinity$excludeFriend(Entity source, Vec3d origin, double radius,
            RegistryEntry<StatusEffect> type, StatusEffectInstance effect, int refreshThreshold,
            ServerPlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (!(source instanceof MobEntity mob)
                || type.value().getCategory() != StatusEffectCategory.HARMFUL
                || ReplayIsolation.protects(mob.getUuid(), player.getUuid())) return;
        var relation = AffinityService.getRelation(mob, player);
        if (relation != null && RelationshipStages.friend(relation.getAffinity(), NonCreatureAffinity.getConfig()))
            cir.setReturnValue(false);
    }
}
