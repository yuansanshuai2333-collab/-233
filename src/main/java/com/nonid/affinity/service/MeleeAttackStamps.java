package com.nonid.affinity.service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * The attack launch stamps that make a late hit rejectable.
 *
 * <p>Why a stamp at all: a landed hit renews a fight, but damage arrives when it RESOLVES. For melee
 * that is the same tick as the attempt, so nothing can change in between. For anything delayed -- a
 * projectile in flight -- many ticks can pass, during which the fight can end and a new one begin
 * against the same creature. The victim id alone cannot tell those apart, so each attack is stamped
 * with the generation of the fight it was launched in and the stamp is checked when damage lands.
 *
 * <p><b>Lifecycle, and why the scope matters.</b> A stamp is only meaningful while the attack call that
 * created it is still running, because anything landing after that call is by definition NOT that
 * attack. The stamp is therefore removed when the call returns, and a hit that finds no stamp is
 * refused. Without that boundary the stamp would sit there for a whole combat round and any later
 * damage on the same target would renew the fight claiming to be that melee swing.
 *
 * <p>Consequence, stated plainly rather than implied: pending damage with no boundary -- a projectile
 * fired by the companion, whose launch does not call {@code tryAttack} -- has NO stamp, so it does not
 * renew a fight at all. That is the safe direction (a fight is abandoned slightly too eagerly rather
 * than being kept alive by a hit that may not be current), but it is a real limitation, not a solved
 * case.
 *
 * <p>Pure and Minecraft-free, so the whole lifecycle is unit-testable.
 */
final class MeleeAttackStamps {

    /**
     * One launched attack.
     *
     * @param companionId  who launched it
     * @param targetId     what it was aimed at
     * @param generation   the engagement generation at launch; the field that makes staleness visible
     * @param launchedTick server tick of the attempt, for diagnostics and for tests that pin ordering
     */
    record Stamp(UUID companionId, UUID targetId, long generation, long launchedTick) {
        Stamp {
            Objects.requireNonNull(companionId, "companionId");
            Objects.requireNonNull(targetId, "targetId");
        }
    }

    /** companion uuid -> the most recent launch. */
    private static final Map<UUID, Stamp> STAMPS = new ConcurrentHashMap<>();

    /**
     * Every launch also gets a unique token, kept alongside the stamp.
     *
     * <p>Why a separate token: the mixin that brackets an attack lives in another package, and handing
     * it a stamp would mean making this class and its record public -- exposing an internal type to the
     * outside just to clear a line later. The token is an ordinary opaque object; the mixin passes it
     * back untouched and never inspects it.
     */
    private static final Map<UUID, Object> TOKENS = new ConcurrentHashMap<>();

    private MeleeAttackStamps() {
    }

    /**
     * Record a launch, discarding whatever that companion had before.
     *
     * <p>Discarding is deliberate: only one attack call can be in progress at a time for one creature,
     * and a stamp left over from a previous attempt must never be able to authorise a later hit.
     *
     * @param companionId   who is attacking
     * @param targetId      what is being attacked
     * @param generation    the engagement generation at launch
     * @param launchedTick  current server tick
     * @return the stamp that was issued
     */
    static Stamp record(UUID companionId, UUID targetId, long generation, long launchedTick) {
        Stamp stamp = new Stamp(companionId, targetId, generation, launchedTick);
        STAMPS.put(companionId, stamp);
        return stamp;
    }

    /**
     * Issue a token bound to a fresh stamp, for callers outside this package.
     *
     * @return the opaque token to hand back to {@link #clearToken}
     */
    static Object recordToken(UUID companionId, UUID targetId, long generation, long launchedTick) {
        record(companionId, targetId, generation, launchedTick);
        Object token = new Object();
        TOKENS.put(companionId, token);
        return token;
    }

    /**
     * Clear this companion's stamp, but only if the given token is still the one bound to it.
     *
     * <p>The identity check is what makes signalling the end of an attack call safe: if a newer attempt
     * has already replaced the stamp, the returning older call must not delete the newer one.
     *
     * @return whether a stamp was removed
     */
    static boolean clearToken(UUID companionId, Object token) {
        if (companionId == null || token == null) {
            return false;
        }
        if (!TOKENS.remove(companionId, token)) {
            return false;
        }
        return STAMPS.remove(companionId) != null;
    }

    /** Remove this companion's stamp if it is still the one given. */
    static boolean clearIfCurrent(UUID companionId, Stamp stamp) {
        if (companionId == null || stamp == null) {
            return false;
        }
        return STAMPS.remove(companionId, stamp);
    }

    /**
     * The end-of-attack cleanup, reachable by id rather than by entity.
     *
     * <p>Exists because a plain JUnit process cannot link {@code MobEntity}, so
     * {@code CompanionCombatController.onCompanionAttackFinished(mob, token)} is unreachable from a test
     * even though the bookkeeping behind it is pure and is exactly what the exception path exercises.
     * Delegates to {@link #clearToken}, so there is one implementation of the cleanup and not two.
     *
     * @return whether a stamp was removed
     */
    static boolean clearOnAttackFinished(UUID companionId, Object token) {
        return clearToken(companionId, token);
    }

    /** Remove whatever stamp and token this companion has, unconditionally. */
    static void clear(UUID companionId) {
        if (companionId != null) {
            STAMPS.remove(companionId);
            TOKENS.remove(companionId);
        }
    }

    /** The stamp this companion is currently holding, or null. */
    @Nullable
    static Stamp current(UUID companionId) {
        return companionId == null ? null : STAMPS.get(companionId);
    }

    /**
     * Whether a landed hit may be credited to a stamp.
     *
     * <p>Four checks, all required:
     * <ul>
     *   <li>the stamp must exist -- an orphaned stamp, whose attack call has already returned, must not
     *       authorise anything;</li>
     *   <li>the target must match -- a hit on something else says nothing about this fight;</li>
     *   <li>the generation must still be the live one -- a stamp from a fight that has ended must not
     *       renew the fight that replaced it; and</li>
     *   <li>the stamp must have been issued on the CURRENT tick -- melee damage resolves in the same tick
     *       as the attempt, so a stamp from an earlier tick is by definition not the attack that just
     *       landed.</li>
     * </ul>
     *
     * <p>The tick bound is what makes the design robust rather than merely correct: the cleanup injection
     * runs on the method's normal return, so a throwable propagating out of a nested call would strand a
     * stamp. With this bound, a stranded stamp stops being usable on the very next tick instead of
     * waiting for a cleanup signal that may never come.
     */
    static boolean credits(Stamp stamp, UUID victimId, long liveGeneration, long nowTick) {
        if (stamp == null || victimId == null) {
            return false;
        }
        if (!stamp.targetId().equals(victimId)) {
            return false;
        }
        if (stamp.generation() != liveGeneration) {
            return false;
        }
        return stamp.launchedTick() == nowTick;
    }

    /** How many companions currently hold a stamp; for diagnostics. */
    static int heldCount() {
        return STAMPS.size();
    }

    /** Drop every stamp AND every token; only for a full server stop. */
    static void clearAll() {
        STAMPS.clear();
        // The token table is cleared too. Leaving tokens behind kept this map growing across server
        // stops inside one JVM (a dev client, or an integrated server restarted in place), and a
        // surviving token could still match a later clearToken call for the same companion id.
        TOKENS.clear();
    }

    /** How many companions currently hold a token; diagnostics, and the counterpart of heldCount(). */
    static int tokenCount() {
        return TOKENS.size();
    }
}
