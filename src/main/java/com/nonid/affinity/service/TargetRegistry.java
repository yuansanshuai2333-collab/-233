package com.nonid.affinity.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Attack targets that this mod set, and which service owns each one.
 *
 * <p>Two services write targets: ATTACK mode ({@link TargetSource#COMPANION_ATTACK}) and the older
 * defender system ({@link TargetSource#LEGACY_DEFENDER}). They run on the same world tick, the
 * defender first.
 *
 * <p>An earlier version keyed only on the creature, with no notion of origin. Because the companion
 * service released "any recorded target" whenever it was not in ATTACK mode, it silently cleared
 * targets the defender had just set -- and the same in reverse. Every operation here is therefore
 * scoped to a source: a service may only inspect and clear its own records.
 *
 * <p>The record also keeps the target uuid, because if the vanilla AI or another mod replaces the
 * target, this mod must stop believing the new one is its own.
 */
public final class TargetRegistry {

    /** Which service assigned a target. */
    public enum TargetSource {
        /** Set by companion ATTACK mode. */
        COMPANION_ATTACK,
        /** Set by the older defender system, which also covers creatures that are not companions. */
        LEGACY_DEFENDER
    }

    /**
     * Who assigned what.
     *
     * @param targetId    the target this source chose
     * @param ownerId     the player being protected, which may be null for legacy records
     * @param source      which service made the assignment
     * @param assignedTick server tick of the assignment, for diagnostics only
     */
    private record Assignment(UUID targetId, @Nullable UUID ownerId, TargetSource source,
                              long assignedTick) {
    }

    /** creature uuid -> assignment. */
    private static final Map<UUID, Assignment> ASSIGNED = new ConcurrentHashMap<>();

    private TargetRegistry() {
    }

    /**
     * Whether a recorded assignment still matches the creature's current target.
     *
     * <p>Pure, so the external-replacement case is unit-testable: A recorded, B current, no longer
     * owned.
     */
    static boolean stillOwns(@Nullable UUID registered, @Nullable UUID current) {
        return registered != null && current != null && registered.equals(current);
    }

    /** Record that {@code source} chose {@code target} for {@code mob}. */
    public static void claim(MobEntity mob, LivingEntity target, @Nullable UUID owner,
                             TargetSource source, long tick) {
        ASSIGNED.put(mob.getUuid(), new Assignment(target.getUuid(), owner, source, tick));
    }

    /** The recorded target uuid for this creature, regardless of source, or null. */
    @Nullable
    public static UUID recordedTarget(MobEntity mob) {
        Assignment assignment = ASSIGNED.get(mob.getUuid());
        return assignment == null ? null : assignment.targetId();
    }

    /** Recheck allegiance at selection/impact time, before the next controller scan can retire a stale fight. */
    public static boolean protectsAssignedTarget(MobEntity mob, LivingEntity target) {
        Assignment assignment = ASSIGNED.get(mob.getUuid());
        return assignment != null && assignment.ownerId() != null && target != null
                && assignment.targetId().equals(target.getUuid())
                && CombatEligibility.isProtectedForOwner(target, assignment.ownerId());
    }

    /** Which source owns the current record, or null when there is none. */
    @Nullable
    public static TargetSource recordedSource(MobEntity mob) {
        Assignment assignment = ASSIGNED.get(mob.getUuid());
        return assignment == null ? null : assignment.source();
    }

    /** Whether this creature has any record at all. */
    public static boolean hasRecord(MobEntity mob) {
        return ASSIGNED.containsKey(mob.getUuid());
    }

    /**
     * Whether {@code source} still owns the creature's current target.
     *
     * <p>Requires the source to match as well as the target uuid, so one service can never act on
     * another's assignment.
     */
    public static boolean owns(MobEntity mob, TargetSource source) {
        Assignment assignment = ASSIGNED.get(mob.getUuid());
        if (assignment == null || assignment.source() != source) {
            return false;
        }
        LivingEntity current = mob.getTarget();
        return stillOwns(assignment.targetId(), current == null ? null : current.getUuid());
    }

    /**
     * Clear the creature's target, but only when {@code source} still owns it.
     *
     * <p>If another system replaced the target, the stale record is dropped and the live target is
     * left untouched.
     *
     * @return whether a target was actually cleared
     */
    public static boolean releaseIfOwned(MobEntity mob, TargetSource source) {
        Assignment assignment = ASSIGNED.get(mob.getUuid());
        if (assignment == null || assignment.source() != source) {
            // Not ours at all: leave both the record and the creature alone.
            return false;
        }
        if (!owns(mob, source)) {
            // Ours, but externally replaced: revoke the stale record immediately and step aside.
            ASSIGNED.remove(mob.getUuid());
            return false;
        }
        RelationshipBehavior.writeTarget(mob, null);
        ASSIGNED.remove(mob.getUuid());
        return true;
    }

    /**
     * Forget the record, but only if it belongs to {@code source}.
     *
     * <p>Never touches the creature's live target.
     */
    public static void forget(MobEntity mob, TargetSource source) {
        Assignment assignment = ASSIGNED.get(mob.getUuid());
        if (assignment != null && assignment.source() == source) {
            ASSIGNED.remove(mob.getUuid());
        }
    }

    /**
     * Revoke a record that no longer matches reality, whoever owns it.
     *
     * <p>Used when a creature's target was replaced or cleared externally: the record is stale and
     * must not be kept for a later match.
     */
    public static void revokeIfStale(MobEntity mob) {
        Assignment assignment = ASSIGNED.get(mob.getUuid());
        if (assignment == null) {
            return;
        }
        LivingEntity current = mob.getTarget();
        if (!stillOwns(assignment.targetId(), current == null ? null : current.getUuid())) {
            ASSIGNED.remove(mob.getUuid());
        }
    }

    /** Drop every record; only for a full server stop. */
    public static void clear() {
        ASSIGNED.clear();
    }

    /** Entity unload: discard bookkeeping only, without touching any live AI target. */
    public static void forgetEntity(UUID entityId) {
        ASSIGNED.remove(entityId);
    }
}

