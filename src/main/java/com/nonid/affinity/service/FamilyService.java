package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.data.AffinityHolder;
import com.nonid.affinity.data.AffinityAttachments;
import com.nonid.affinity.data.BirthIdentity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Records birth metadata only; never starts an animation or changes birth mechanics. */
public final class FamilyService {
    private FamilyService() {}

    public static void recordBirth(MobEntity child, ServerPlayerEntity parent) {
        if (child == null || parent == null || !(child.getEntityWorld() instanceof ServerWorld world)
                || !(child instanceof AffinityHolder holder)) return;
        var data = holder.affinityData();
        if (!data.recordBirthParent(parent.getUuid())) return;
        CompanionArchive.stageBirth(child, parent);
        AffinityService.bindOffspring(world, parent, child, NonCreatureAffinity.getConfig(), 0);
    }

    /** Exact provider snapshot also works when the egg's mother is offline. */
    public static void recordBirth(MobEntity child, ServerPlayerEntity onlineParent, BirthIdentity.Birth birth) {
        if (birth == null) { recordBirth(child, onlineParent); return; }
        if (birth.isolated() || child == null || !(child.getEntityWorld() instanceof ServerWorld world)
                || !(child instanceof AffinityHolder holder)) return;
        if (onlineParent != null && !birth.viewer().equals(onlineParent.getUuid())) return;
        if (child.getAttached(AffinityAttachments.BIRTH_IDENTITY) != null) return;
        var data = holder.affinityData();
        if (data.getBirthParent() != null && !data.getBirthParent().equals(birth.viewer())) return;
        boolean first = data.recordBirthParent(birth.viewer());
        child.setAttached(AffinityAttachments.BIRTH_IDENTITY, BirthIdentity.toNbt(birth));
        // Preserve the provider's existing online-only relationship/ownership behavior.
        if (first && onlineParent != null)
            AffinityService.bindOffspring(world, onlineParent, child, NonCreatureAffinity.getConfig(), 0);
    }

    public static String label(Entity child, ServerPlayerEntity viewer) {
        if (!(child instanceof AffinityHolder holder)) return "";
        var data = holder.peekAffinityData();
        if (data != null && data.getBirthParent() != null) {
            String direct = FamilyLabels.label(data.getBirthParent(), viewer.getUuid(), gender(child));
            if (!direct.isEmpty()) return direct;
        }
        var archive = CompanionArchive.peek(viewer.getEntityWorld().getServer());
        var graph = archive == null ? null : archive.family(viewer.getUuid());
        String id = child instanceof net.minecraft.entity.LivingEntity living ? CompanionDirectory.identityOf(living) : null;
        if (graph == null || id == null) return "";
        var depth = graph.generations("player:" + viewer.getUuid(), id);
        return depth == null ? "" : FamilyLabels.relative(depth, gender(child));
    }

    public static int gender(Entity child) {
        int gender = 0;
        try {
            Class<?> type = Class.forName("com.nonid.GenderHolder");
            if (type.isInstance(child)) gender = (Integer) type.getMethod("getGenderMask").invoke(child);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Unknown gender is displayed as child, never guessed from appearance.
        }
        return gender == 1 || gender == 2 ? gender : 0;
    }
}
