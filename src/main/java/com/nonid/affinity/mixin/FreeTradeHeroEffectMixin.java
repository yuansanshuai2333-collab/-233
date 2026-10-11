package com.nonid.affinity.mixin;

import com.nonid.affinity.data.FreeTradeHeroEpoch;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bumps the hero epoch the moment a player NEWLY acquires the hero-of-the-village effect.
 *
 * <p><b>One hook, on the callback itself.</b> Vanilla dispatches effect changes from two places --
 * {@code addStatusEffect(instance, source)} and {@code setStatusEffect(instance, source)} -- and both call
 * {@code onStatusEffectApplied} / {@code onStatusEffectUpgraded}. Hooking the callback means neither caller can be
 * missed and, more importantly, the two cannot both be counted for one acquisition. An earlier reading of this
 * module assumed {@code setStatusEffect} was the only dispatch point; it is not.
 *
 * <p><b>Which callback matters.</b> Only {@code onStatusEffectApplied} means "acquired". An extension or an
 * upgrade of an effect that is already running goes to {@code onStatusEffectUpgraded}, which is deliberately NOT
 * hooked: extending a hero effect must not refresh the allowance, and neither must re-applying it every tick.
 *
 * <p>Nothing is walked and nothing is granted here: the epoch is a number, and each relation applies it lazily the
 * next time it quotes. Villagers that are not loaded, and villagers the player has not met yet, are handled
 * correctly by that design rather than by a world scan at acquisition time.
 */
@Mixin(LivingEntity.class)
public abstract class FreeTradeHeroEffectMixin {

    @Inject(method = "onStatusEffectApplied", at = @At("HEAD"))
    private void noncreatureaffinity$bumpHeroEpoch(StatusEffectInstance effect, net.minecraft.entity.Entity source,
                                                   CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayerEntity player)) {
            return;
        }
        if (!effect.getEffectType().equals(StatusEffects.HERO_OF_THE_VILLAGE)) {
            return;
        }
        FreeTradeHeroEpoch.bump(player);
    }
}
