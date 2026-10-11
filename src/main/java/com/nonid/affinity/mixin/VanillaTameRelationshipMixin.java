package com.nonid.affinity.mixin;

import com.nonid.affinity.service.RelationshipSync;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla taming makes the creature a friend and companion of its <b>actual</b> owner.
 *
 * <p><b>Why this anchor.</b> Disassembly shows {@code TameableEntity.setTamedBy(Player)} calls
 * {@code setTamed(true, true)} at offset 3 and then {@code setOwner(owner)} at offset 8. Injecting at the TAIL
 * of {@code setOwner} therefore runs only after taming has genuinely SUCCEEDED and the owner is already
 * recorded, which is the moment the requirement describes. Feeding a bone, or merely starting the tame
 * attempt, does not reach it -- so "the player fed it" can never be mistaken for "taming succeeded".
 *
 * <p><b>The horse family uses a DIFFERENT anchor, because its order is the other way round.</b>
 * {@code AbstractHorseEntity.bondWithPlayer} writes the owner FIRST (offset 2) and sets the tame flag
 * afterwards (offset 7), so the same {@code setOwner} anchor would observe {@code isTamed() == false} there.
 * See {@link VanillaHorseTameRelationshipMixin}: it anchors on that method's successful return instead. The
 * two families are deliberately not unified on one anchor, because one anchor cannot be correct for both
 * orders.
 *
 * <p>The owner used is the one the game just stored. Nothing here looks up the nearest player, which is
 * what keeps a tame from being credited to whoever happens to be standing nearby.
 */
@Mixin(TameableEntity.class)
public abstract class VanillaTameRelationshipMixin {

    @Inject(method = "setOwner(Lnet/minecraft/entity/LivingEntity;)V", at = @At("TAIL"))
    private void noncreatureaffinity$onVanillaTame(LivingEntity owner, CallbackInfo ci) {
        // The same opt-in governs new vanilla tames and old-save backfill.
        if (!com.nonid.affinity.NonCreatureAffinity.getConfig().allowVanillaTamedCreatures) return;
        if (!(owner instanceof ServerPlayerEntity serverPlayer)) {
            // A non-player owner is not something this module models; the bond is left untouched.
            return;
        }
        TameableEntity self = (TameableEntity) (Object) this;
        if (!self.isTamed()) {
            // The owner was set without the tamed flag (a save being restored, a tracker update): not a
            // completed tame, so nothing is claimed here. The load backfill covers restored saves.
            return;
        }
        RelationshipSync.sync((MobEntity) (Object) this, serverPlayer.getUuid(), serverPlayer,
                "vanilla_tame");
    }
}
