package com.nonid.affinity.mixin;

import com.nonid.affinity.data.AffinityAttachments;
import com.nonid.affinity.data.AffinityHolder;
import com.nonid.affinity.data.CreatureAffinityData;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * Makes every {@link Entity} an {@link AffinityHolder}.
 *
 * <p>Adds the interface only: no vanilla method is overwritten. That matters because NeedsOfNature
 * also injects into entity classes with a strict mixin configuration, and overwriting shared
 * methods would risk a conflict at startup.
 *
 * <p>Storage is the Fabric data attachment registered in {@link AffinityAttachments}, so the data
 * is persisted with the entity and survives saving, reloading and dimension changes.
 *
 * <p>The attachment is the single source of truth, including after NBT reloads, replacement or
 * removal by another system. Reads never create data for unrelated entities.
 */
@Mixin(Entity.class)
public abstract class EntityAffinityMixin implements AffinityHolder {
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "readData", require = 1)
    private void noncreatureaffinity$readHealthHistory(net.minecraft.storage.ReadView view,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        com.nonid.affinity.service.HealthHistory.readEntity((Entity) (Object) this, () -> original.call(view));
    }
    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void noncreatureaffinity$forgetRemovedTarget(Entity.RemovalReason reason,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!self.getEntityWorld().isClient()) {
            if (self instanceof net.minecraft.entity.mob.CreakingEntity creaking
                    && creaking.getHomePos() != null && self.getEntityWorld().isChunkLoaded(creaking.getHomePos())
                    && self.getEntityWorld().getBlockEntity(creaking.getHomePos())
                        instanceof net.minecraft.block.entity.CreakingHeartBlockEntity heart
                    && heart.isPuppet(creaking)) {
                com.nonid.affinity.service.CreakingMemory.capture(heart, creaking);
            }
            com.nonid.affinity.service.TargetRegistry.forgetEntity(self.getUuid());
        }
    }

    /**
     * The stored data, or {@code null} when this entity has none.
     *
     * <p>Deliberately does not create anything: bulk queries rely on {@code null} meaning "no
     * relations" rather than "an empty relation set was just attached".
     */
    @Override
    public CreatureAffinityData affinityDataRaw() {
        Entity self = (Entity) (Object) this;
        return self.getAttached(AffinityAttachments.AFFINITY);
    }

    /** Replace the stored data; passing null removes the attachment. */
    @Override
    public void setAffinityDataRaw(CreatureAffinityData data) {
        Entity self = (Entity) (Object) this;
        self.setAttached(AffinityAttachments.AFFINITY, data);
    }

    /**
     * Backfill the companion relation for a pet that predates it, once the entity is fully loaded.
     *
     * <p><b>Why {@code readData} and not {@code readCustomData}.</b> The first attempt injected into
     * {@code readCustomData} and the dedicated-server boot rejected it outright:
     * {@code Entity#readCustomData} is ABSTRACT, so it has no body and therefore no RETURN for
     * {@code @At("TAIL")} to find. {@code Entity#readData(ReadView)} is the concrete method that dispatches
     * to it, and it returns only after the entity's persisted data has been restored -- which is the moment
     * the tame flag and the owner are the real saved values rather than defaults.
     *
     * <p>The hook is a no-op for everything that is not a tamed pet with a confirmed player owner, and
     * {@code backfillOnLoad} returns before touching anything in that case, so there are no per-entity
     * writes and no per-entity log lines on load.
     *
     * <p>Running it repeatedly is safe by construction: the rules are idempotent, so a second load neither
     * re-rewards the player nor resets a mode they chose.
     */
    @Inject(method = "readData", at = @At("TAIL"))
    private void noncreatureaffinity$backfillRelationshipOnLoad(
            net.minecraft.storage.ReadView view,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof net.minecraft.entity.mob.MobEntity mob && !self.getEntityWorld().isClient()) {
            // The config gate is read here and passed in, so the decision stays in one place.
            com.nonid.affinity.service.RelationshipSync.backfillOnLoad(mob,
                    com.nonid.affinity.NonCreatureAffinity.getConfig().allowVanillaTamedCreatures);
        }
    }
}
