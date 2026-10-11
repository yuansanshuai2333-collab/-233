package com.nonid.affinity.service;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Launch attribution for PROJECTILES, with an explicit INVALIDATED state.
 *
 * <p>Why projectiles need their own store, separate from {@link MeleeAttackStamps}: a melee swing
 * resolves in the same tick as the attempt, so its stamp only has to survive one call. A projectile
 * survives many ticks in flight, and several can be in the air at once. Binding the generation to the
 * PROJECTILE is what makes "a hit from a previous fight" detectable -- reading the generation at impact
 * would read whatever fight is current then and verify nothing.
 *
 * <p><b>"Never recorded" and "no longer attributable" are different states.</b> An earlier version DELETED
 * a projectile's record when it changed hands or when its hit was consumed. A deleted record looks exactly
 * like a projectile that was never seen, so the next {@code setOwner} treated the old arrow as a fresh one
 * and gave it the CURRENT fight's generation. That is why the terminal state exists and why nothing in the
 * cleanup path deletes an entry it has not identified.
 *
 * <h2>Identity: the UUID is not the identity</h2>
 *
 * A dimension change creates a NEW entity and copies the OLD entity's uuid onto it, so for a moment two
 * live entities carry the same id -- and the removal of the old one arrives after the new one has already
 * read its data. A store keyed on the UUID cannot tell those two apart, and a cleanup keyed on the UUID
 * would erase the new entity's state. Entries are therefore keyed by the ENTITY INSTANCE
 * ({@link IdentityHashMap}), and the uuid is only used to look an entry up on the damage path.
 *
 * <p>The observable uuid is checked when looking up by uuid, so a recycled id cannot match a stale record.
 *
 * <h2>Lifetime</h2>
 *
 * Entries are released at {@code Entity#setRemoved}, which is the ONE place every removal funnels through.
 * That matters and was the second defect here: the earlier hook sat on {@code remove}, but
 * {@code removeFromDimension} calls {@code setRemoved} directly and the chunk-unload path in
 * {@code ServerEntityManager} does the same -- so an unloaded projectile never reached the hook and its
 * entry was never released, while a genuinely destroyed one was. An entry is released on the FIRST
 * {@code setRemoved} of its instance, so a projectile that is read and then simply destroyed releases its
 * state immediately; nothing waits for a second removal.
 *
 * <p>Storage is a plain strong map with that explicit release, so entries do not depend on garbage
 * collection timing.
 *
 * <h2>What survives storage, and what deliberately does not</h2>
 *
 * Three situations, which must not be conflated:
 *
 * <ol>
 *   <li><b>Within one server run</b> -- an attribution, once established, is protected: a new fight, a
 *       consumed hit or a re-assignment all terminate it rather than remove it, and a terminated
 *       projectile can never be attributed again.</li>
 *   <li><b>Chunk unload and reload</b> -- NOT preserved. Entities are serialized by UUID
 *       ({@code ChunkSerializer.write} stores {@code entity.getUuid()}), so an instance-keyed record cannot
 *       follow a projectile through storage. A projectile that came back from storage is placed in the
 *       TERMINAL state when it is read, so it can never be credited and a later {@code setOwner} can never
 *       revive it as a new arrow.</li>
 *   <li><b>Server restart</b> -- same terminal treatment, and necessary on its own terms: engagement
 *       generations start over after a restart, so a generation recorded before it would be meaningless
 *       afterwards and could even collide with a fresh one.</li>
 * </ol>
 *
 * <p><b>The cost, stated plainly and not hidden:</b> an arrow fired before a reload no longer contributes
 * combat progress after it. That is the deliberate trade -- a missed renewal lets a fight be abandoned
 * slightly early, while a WRONG renewal keeps a finished fight alive -- and it is why the "keep the
 * pre-reload attribution" alternative was rejected: it would require persisting the state AND solving the
 * generation-restart problem, not merely replacing a weak map with a strong one.
 */
final class ProjectileStamps {

    /** Who currently claims a projectile, as far as this module is concerned. */
    enum OwnerKind {
        /** A companion this module is managing. */
        MANAGED_COMPANION,
        /** Any other entity: another mod's mob, an unmanaged creature, or a player. */
        OTHER_ENTITY
    }

    /**
     * One projectile's launch state.
     *
     * @param observedUuid the uuid the entity reported, so a recycled id cannot match a stale record
     * @param ownerId     the companion that fired it, or null when it is not attributable
     * @param targetId    the target at launch, kept so a stamp that names another creature is rejected
     * @param generation  the engagement generation AT LAUNCH; the field that makes staleness visible
     * @param launchedTick server tick of the launch
     * @param valid       false once the projectile can never be credited again
     */
    record State(UUID observedUuid, @Nullable UUID ownerId, @Nullable UUID targetId, long generation,
                 long launchedTick, boolean valid) {

        static State attributed(UUID observedUuid, UUID ownerId, UUID targetId, long generation,
                                long launchedTick) {
            return new State(observedUuid, ownerId, targetId, generation, launchedTick, true);
        }

        /** Terminal: this projectile may not be credited, now or ever. */
        static State invalidated(UUID observedUuid) {
            return new State(observedUuid, null, null, 0L, 0L, false);
        }
    }

    /**
     * State per projectile ENTITY INSTANCE.
     *
     * <p>Identity-keyed on purpose: two live entities can share a uuid across a dimension change, and the
     * cleanup must not confuse them. The key is the entity object, held strongly and released at
     * {@code setRemoved}, so nothing depends on when the garbage collector runs.
     */
    private static final Map<Object, State> BY_INSTANCE = new IdentityHashMap<>();

    /** Instance -> its uuid, so the damage path (which only knows the uuid) can find the entry. */
    private static final Map<Object, UUID> INSTANCE_UUID = new IdentityHashMap<>();

    /** Uuid -> the instance currently holding an entry for it. */
    private static final Map<UUID, Object> LIVE_BY_UUID = new HashMap<>();

    private ProjectileStamps() {
    }

    /**
     * The single place a projectile's owner assignment is interpreted.
     *
     * <p>Both injected hooks call this, and it is idempotent, which is what makes two hooks per arrow safe:
     * vanilla's {@code PersistentProjectileEntity#setOwner} delegates to the superclass one, so both fire
     * for an arrow, and any number of repeats must leave the state exactly as it is.
     *
     * @param instance     the projectile entity instance, the identity used for cleanup
     * @param observedUuid that instance's uuid
     * @param kind         whether the new owner is a companion we manage
     * @param ownerId      the owner's id, or null when it is not a managed companion
     * @param targetId     the fight's target at this moment, used only for a FIRST attribution
     * @param generation   the generation to record for a FIRST attribution
     * @param launchedTick the tick to record for a FIRST attribution
     * @return whether the projectile is currently attributed to a managed companion
     */
    static boolean observeOwnerAssignment(Object instance, UUID observedUuid, OwnerKind kind,
                                          @Nullable UUID ownerId, @Nullable UUID targetId,
                                          long generation, long launchedTick) {
        if (instance == null || observedUuid == null || kind == null) {
            return false;
        }
        synchronized (BY_INSTANCE) {
            State existing = BY_INSTANCE.get(instance);
            if (existing != null && !existing.valid()) {
                // Already terminal. With the record DELETED this projectile would have been
                // indistinguishable from a fresh one and re-attributed with the current generation.
                return false;
            }
            if (kind != OwnerKind.MANAGED_COMPANION || ownerId == null) {
                // A player, another mod's mob or an unmanaged creature. Not a launch by us, and it must
                // not be revivable later by handing the projectile back to a companion.
                put(instance, observedUuid, State.invalidated(observedUuid));
                return false;
            }
            if (existing == null) {
                if (targetId == null) {
                    // Managed companion, but it is not fighting: there is no generation worth binding,
                    // and a stamp with no target could only be misused. Invalidate rather than delete, so
                    // a later assignment cannot quietly create an attribution for a fight it never
                    // belonged to.
                    put(instance, observedUuid, State.invalidated(observedUuid));
                    return false;
                }
                put(instance, observedUuid,
                        State.attributed(observedUuid, ownerId, targetId, generation, launchedTick));
                return true;
            }
            if (!existing.ownerId().equals(ownerId)) {
                // Changed hands, which is not a launch at all. Terminal, so it cannot be re-attributed.
                put(instance, observedUuid, State.invalidated(observedUuid));
                return false;
            }
            // Same companion, already attributed: keep the original record untouched, whatever generation
            // and tick are offered now. Those describe the present, not this projectile's launch.
            return true;
        }
    }

    /** Store an entry and keep the two index maps in step. */
    private static void put(Object instance, UUID observedUuid, State state) {
        BY_INSTANCE.put(instance, state);
        INSTANCE_UUID.put(instance, observedUuid);
        // Do not let a new instance's entry be shadowed by an old instance that shared the id: the
        // lookup map always points at the newest holder.
        LIVE_BY_UUID.put(observedUuid, instance);
    }

    /**
     * The attribution for this uuid, or null when it is not attributed to a companion.
     *
     * <p>Looked up through the instance that currently holds the id, and the recorded uuid must match, so a
     * recycled or stale id cannot be credited.
     */
    @Nullable
    static State attributed(UUID observedUuid) {
        if (observedUuid == null) {
            return null;
        }
        synchronized (BY_INSTANCE) {
            Object instance = LIVE_BY_UUID.get(observedUuid);
            if (instance == null) {
                return null;
            }
            State state = BY_INSTANCE.get(instance);
            if (state == null || !state.valid() || !observedUuid.equals(state.observedUuid())) {
                return null;
            }
            return state;
        }
    }

    /** The raw state for one instance, valid or not; for tests and diagnostics. */
    @Nullable
    static State stateOfInstance(Object instance) {
        if (instance == null) {
            return null;
        }
        synchronized (BY_INSTANCE) {
            return BY_INSTANCE.get(instance);
        }
    }

    /** The raw state for a uuid, valid or not; for tests and diagnostics. */
    @Nullable
    static State stateOf(UUID observedUuid) {
        if (observedUuid == null) {
            return null;
        }
        synchronized (BY_INSTANCE) {
            Object instance = LIVE_BY_UUID.get(observedUuid);
            return instance == null ? null : BY_INSTANCE.get(instance);
        }
    }

    /**
     * Mark a projectile read back from STORAGE as permanently non-attributable.
     *
     * <p>An explicit terminal state rather than merely dropping the record, so a later {@code setOwner}
     * cannot revive the projectile as a new arrow.
     */
    static void markLoadedFromStorage(Object instance, UUID observedUuid) {
        if (instance == null || observedUuid == null) {
            return;
        }
        synchronized (BY_INSTANCE) {
            put(instance, observedUuid, State.invalidated(observedUuid));
        }
    }

    /**
     * Mark this projectile as no longer attributable, permanently.
     *
     * <p>Used once a hit has been consumed: one projectile authorises at most one hit, and re-owning it
     * afterwards must not authorise another.
     */
    static void invalidate(UUID observedUuid) {
        if (observedUuid == null) {
            return;
        }
        synchronized (BY_INSTANCE) {
            Object instance = LIVE_BY_UUID.get(observedUuid);
            if (instance != null) {
                put(instance, observedUuid, State.invalidated(observedUuid));
            }
        }
    }

    /**
     * Release a projectile's entry because its entity was removed.
     *
     * <p>Called from {@code Entity#setRemoved}, which every removal funnels through -- destroy, chunk
     * unload and dimension change alike. Keyed on the INSTANCE, so the removal of an old entity that
     * shared an id with a new one cannot release the new entity's entry.
     *
     * <p><b>Idempotent, and it has to be.</b> {@code setRemoved} is {@code final}, which says nothing
     * about how many times it may be CALLED -- an earlier comment here claimed "once per instance" and
     * that was an assumption, not a fact. This method therefore has to be safe to call repeatedly, and it
     * is: the first call removes the entry and the uuid index, and every later call finds nothing and does
     * nothing. No counter, no flag and no exception depends on a single invocation.
     *
     * <p>One call is enough, so a projectile that was read from storage and then simply destroyed is
     * released immediately -- nothing waits for a second removal.
     */
    static void releaseInstance(Object instance) {
        if (instance == null) {
            return;
        }
        synchronized (BY_INSTANCE) {
            State removed = BY_INSTANCE.remove(instance);
            UUID observedUuid = INSTANCE_UUID.remove(instance);
            if (observedUuid == null && removed != null) {
                observedUuid = removed.observedUuid();
            }
            if (observedUuid != null && LIVE_BY_UUID.get(observedUuid) == instance) {
                // Only drop the lookup if this instance is still the one holding the id; a newer entity
                // that reused the id keeps its own entry.
                LIVE_BY_UUID.remove(observedUuid);
            }
        }
    }

    /**
     * Mark every live attribution belonging to one companion as no longer attributable, because its
     * engagement has ended.
     *
     * <p><b>This must INVALIDATE, never delete.</b> An earlier version removed the entries. A deleted
     * record is indistinguishable from a projectile that was never seen, so a still-airborne arrow became
     * re-attributable and took the CURRENT fight's generation on its next assignment.
     *
     * <p>Terminating rather than keeping the original attribution is the stronger of the two safe
     * options: it does not depend on a generation comparison happening at all, so the record cannot be
     * credited again even by a fight that happens to reuse the same generation.
     *
     * <p>Entries that are ALREADY terminal are left untouched. Removing them would throw away the
     * protection they carry against being re-established by a later {@code setOwner}.
     *
     * @return how many live records were terminated
     */
    static int markEngagementEnded(UUID companionId) {
        if (companionId == null) {
            return 0;
        }
        synchronized (BY_INSTANCE) {
            int terminated = 0;
            for (Map.Entry<Object, State> entry : BY_INSTANCE.entrySet()) {
                State state = entry.getValue();
                if (state.valid() && companionId.equals(state.ownerId())) {
                    entry.setValue(State.invalidated(state.observedUuid()));
                    terminated++;
                }
            }
            return terminated;
        }
    }

    /** The instance currently holding an entry for this uuid, or null. */
    @Nullable
    static Object instanceOf(UUID observedUuid) {
        if (observedUuid == null) {
            return null;
        }
        synchronized (BY_INSTANCE) {
            return LIVE_BY_UUID.get(observedUuid);
        }
    }

    /** How many projectiles currently hold state; for diagnostics. */
    static int heldCount() {
        synchronized (BY_INSTANCE) {
            return BY_INSTANCE.size();
        }
    }

    /** How many uuid lookups are live; for diagnostics and for leak checks in tests. */
    static int uuidIndexCount() {
        synchronized (BY_INSTANCE) {
            return LIVE_BY_UUID.size();
        }
    }

    /** Drop everything; only for a full server stop. */
    static void clearAll() {
        synchronized (BY_INSTANCE) {
            BY_INSTANCE.clear();
            INSTANCE_UUID.clear();
            LIVE_BY_UUID.clear();
        }
    }
}
