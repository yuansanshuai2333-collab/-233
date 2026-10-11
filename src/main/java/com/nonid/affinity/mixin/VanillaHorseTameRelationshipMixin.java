package com.nonid.affinity.mixin;

import com.nonid.affinity.service.RelationshipRules;
import com.nonid.affinity.service.RelationshipSync;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.LazyEntityReference;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The same unification for the horse family, which is NOT a {@code TameableEntity}.
 *
 * <p><b>Why the anchor is {@code bondWithPlayer} and not {@code setOwner}.</b> Disassembling
 * {@code AbstractHorseEntity.bondWithPlayer(PlayerEntity)} on 1.21.11 gives exactly one return, and the
 * writes are ordered:
 *
 * <pre>
 *   offset  2: invokevirtual setOwner(PlayerEntity)   // owner recorded
 *   offset  7: invokevirtual setTame(true)            // tame flag set
 *   offset 39: iconst_1; ireturn                      // single, successful return
 * </pre>
 *
 * <p>The first version of this mixin hooked {@code setOwner}'s TAIL and asked {@code isTame()} there. At that
 * instant the flag is still <b>false</b> on a first taming, so the guard returned early every time and the
 * normal taming path never produced a {@code vanilla_tame_horse} bond. The guard was right and the anchor was
 * wrong: this now sits at the successful return, where the owner and the flag are both set.
 *
 * <p><b>Coverage.</b> {@code bondWithPlayer} is declared ONCE, on {@code AbstractHorseEntity}; checked with
 * javap, none of {@code HorseEntity}, {@code AbstractDonkeyEntity}, {@code DonkeyEntity}, {@code MuleEntity},
 * {@code LlamaEntity}, {@code TraderLlamaEntity}, {@code CamelEntity}, {@code SkeletonHorseEntity} or
 * {@code ZombieHorseEntity} overrides it, {@code setOwner} or {@code setTame}. Injecting into the parent
 * therefore covers the whole family. This is also the only vanilla taming entry: the sole caller is
 * {@code HorseBondWithPlayerGoal}, which runs while a player rides an untamed horse.
 *
 * <p><b>The three guards are kept on purpose.</b> {@link RelationshipRules#isTameCompleted} requires the tame
 * flag, a persistent owner reference and the logical server, so an ownership write that is not a tame (a
 * tracker update, another mod writing an owner, a restored save) cannot be credited. Restored saves are
 * handled independently by the {@code Entity#readData} backfill, which does not depend on this hook at all.
 */
@Mixin(AbstractHorseEntity.class)
public abstract class VanillaHorseTameRelationshipMixin {

    @Inject(method = "bondWithPlayer(Lnet/minecraft/entity/player/PlayerEntity;)Z", at = @At("TAIL"))
    private void noncreatureaffinity$afterHorseBonded(PlayerEntity player,
                                                      CallbackInfoReturnable<Boolean> cir) {
        // Leave vanilla taming intact when automatic relationship integration is disabled.
        if (!com.nonid.affinity.NonCreatureAffinity.getConfig().allowVanillaTamedCreatures) return;
        AbstractHorseEntity self = (AbstractHorseEntity) (Object) this;
        // A failed bond must not be credited. The current method has a single successful return, but the
        // guard is written out rather than assumed, so a future early "false" return stays uncredited.
        if (!cir.getReturnValueZ()) {
            return;
        }
        if (self.getEntityWorld().isClient()) {
            return;
        }
        // The ACTUAL owner, read from the stored reference: never "the player who happens to be here".
        LazyEntityReference<LivingEntity> reference = self.getOwnerReference();
        UUID ownerId = reference == null ? null : reference.getUuid();
        if (!RelationshipRules.isTameCompleted(self.isTame(), ownerId, true)) {
            return;
        }
        // Feedback only, and only when the online player really is the owner.
        ServerPlayerEntity online = player instanceof ServerPlayerEntity serverPlayer
                && ownerId.equals(serverPlayer.getUuid()) ? serverPlayer : null;
        RelationshipSync.sync((MobEntity) (Object) this, ownerId, online, "vanilla_tame_horse");
    }
}
