package com.nonid.affinity.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Per-instance state of every mating animation, so a settlement is decided from facts about THAT animation.
 *
 * <p>Replaces three separate structures that could not answer the questions settlement needs ({@code ACTIVE}
 * only knew animations this module started, {@code ESCAPED} only held instance ids, and
 * {@code SETTLED_INSTANCES} only remembered autonomous ones). Keeping them apart is what allowed three
 * distinct defects: an autonomous animation was settled as a completion, an escape could only mark animations
 * in {@code ACTIVE}, and a duplicate stop for a module-started animation fell through to the autonomous path
 * and paid twice.
 *
 * <p>Two rules hold here by construction:
 * <ul>
 *   <li><b>a terminal instance never settles again.</b> Taking a session records it as handled, and the
 *       handled set is consulted before any decision, so a repeated stop notification -- or a stop arriving
 *       after the running record was already consumed -- cannot pay out twice.</li>
 *   <li><b>an interruption reaches every animation of that player and creature</b>, including ones this module
 *       did not start, because the player's escape is evidence about the animation, not about who started
 *       it.</li>
 * </ul>
 *
 * <p>Pure Java on purpose: these are the facts the settlement went wrong on in play, and they have to be
 * testable without a running game.
 */
public final class MatingSessions {

    /** A running animation. */
    public static final class Session {
        private final UUID instanceId;
        private final UUID playerId;
        private final UUID creatureId;
        private boolean startedByMod;
        private boolean forced;
        private final long startedTick;
        private boolean interrupted;
        private boolean escapedByPlayer;
        private EscapeAttackSettlement escapeFeedback;
        public void trackEscapeFeedback(EscapeAttackSettlement feedback) { escapeFeedback = feedback; }
        public void settleEscapeFeedback(boolean paid) {
            if (escapeFeedback != null) { var feedback = escapeFeedback; escapeFeedback = null;
                feedback.settled(instanceId, paid); }
        }

        private Session(UUID instanceId, UUID playerId, UUID creatureId, boolean startedByMod, boolean forced,
                        long startedTick) {
            this.instanceId = instanceId;
            this.playerId = playerId;
            this.creatureId = creatureId;
            this.startedByMod = startedByMod;
            this.forced = forced;
            this.startedTick = startedTick;
        }

        public UUID instanceId() {
            return this.instanceId;
        }

        public UUID playerId() {
            return this.playerId;
        }

        public UUID creatureId() {
            return this.creatureId;
        }

        /** Whether this module called the start API for this animation. */
        public boolean startedByMod() {
            return this.startedByMod;
        }

        /** Whether this module started it as a forced mating after a rejected request. */
        public boolean forced() {
            return this.forced;
        }

        public long startedTick() {
            return this.startedTick;
        }

        public boolean interrupted() {
            return this.interrupted;
        }

        /**
         * Whether the PLAYER broke out of it (they attacked the creature), as opposed to the animation being
         * interrupted by something else. Only this earns the extra loss for escaping a forced mating.
         */
        public boolean escapedByPlayer() {
            return this.escapedByPlayer;
        }

        /** Record that this module started this animation; the forced flag is authoritative. */
        private void promoteToOurStart(boolean ourForced) {
            this.startedByMod = true;
            this.forced = ourForced;
        }
    }

    private static final int MAX_TRACKED = 1024;

    private final Map<UUID, Session> running = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> handled = new ConcurrentHashMap<>();

    /** Begin tracking an animation. Returns the session, or null when the identity is incomplete. */
    @Nullable
    public Session open(UUID instanceId, UUID playerId, UUID creatureId, boolean startedByMod, boolean forced,
                        long nowTick) {
        if (instanceId == null || playerId == null || creatureId == null || isHandled(instanceId)) {
            return null;
        }
        Session existing = this.running.get(instanceId);
        if (existing != null) {
            // A start event for an instance we already recorded through our own start call must not downgrade
            // it: that call is the authority on "we started this" and on the forced flag, and losing either
            // would quietly change how the mating is settled.
            if (!existing.startedByMod && startedByMod) {
                existing.promoteToOurStart(forced);
            }
            return existing;
        }
        Session session = new Session(instanceId, playerId, creatureId, startedByMod, forced, nowTick);
        this.running.put(instanceId, session);
        trimIfNeeded();
        return session;
    }

    /**
     * Record that this module started an animation, upgrading whatever was opened from the start event.
     *
     * <p>NeedsOfNature may report the start before or after {@code NonAnimationApi.start} returns, so both
     * orders have to work and neither may lose the forced flag: it decides whether the player-initiated bonus
     * applies.
     */
    public void recordOurStart(UUID instanceId, UUID playerId, UUID creatureId, boolean forced, long nowTick) {
        if (instanceId == null || playerId == null || creatureId == null || isHandled(instanceId)) {
            return;
        }
        Session existing = this.running.get(instanceId);
        if (existing != null) {
            existing.promoteToOurStart(forced);
            return;
        }
        this.running.put(instanceId, new Session(instanceId, playerId, creatureId, true, forced, nowTick));
        trimIfNeeded();
    }

    /**
     * Mark every running animation of this player and creature as interrupted.
     *
     * @return how many animations were marked
     */
    public int markInterrupted(UUID playerId, UUID creatureId) {
        if (playerId == null || creatureId == null) {
            return 0;
        }
        int marked = 0;
        for (Session session : this.running.values()) {
            if (session.playerId.equals(playerId) && session.creatureId.equals(creatureId)) {
                session.interrupted = true;
                marked++;
            }
        }
        return marked;
    }

    /**
     * Mark ONE instance as broken out of by the player, by instance id.
     *
     * <p>Used when the escape is announced per instance (NON's own struggle completion) rather than through the
     * damage path. Unknown instance ids are ignored: this module only ever settles animations it knows about.
     *
     * @return whether a running animation of this module was marked
     */
    public boolean markPlayerEscapeForInstance(UUID instanceId) {
        Session session = instanceId == null ? null : this.running.get(instanceId);
        if (session == null) {
            return false;
        }
        session.interrupted = true;
        session.escapedByPlayer = true;
        return true;
    }

    /** Whether this instance is currently marked as interrupted. */
    public boolean isInterrupted(UUID instanceId) {
        Session session = instanceId == null ? null : this.running.get(instanceId);
        return session != null && session.interrupted;
    }

    /**
     * The FORCED mating this player is currently in, or null.
     *
     * <p>Used by the break-free action: the client only says "I pressed it", and the server decides whether the
     * player is really inside one of this module's forced matings, so a client cannot end anything else.
     */
    @Nullable
    public Session findForcedForPlayer(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        for (Session session : this.running.values()) {
            if (session.forced && session.playerId.equals(playerId)) {
                return session;
            }
        }
        return null;
    }

    /**
     * Mark every running animation of this player and creature as broken out of BY THE PLAYER.
     *
     * <p>Called from the damage path, i.e. when the player attacks the creature it is engaged with. Kept apart
     * from a generic interruption because only a player escape costs affinity: an animation that ended because
     * an actor died or unloaded must not be charged to the player.
     *
     * @return how many animations were marked
     */
    public int markPlayerEscape(UUID playerId, UUID creatureId) {
        if (playerId == null || creatureId == null) {
            return 0;
        }
        int marked = 0;
        for (Session session : this.running.values()) {
            if (session.playerId.equals(playerId) && session.creatureId.equals(creatureId)) {
                session.interrupted = true;
                session.escapedByPlayer = true;
                marked++;
            }
        }
        return marked;
    }

    /**
     * Take the session for a finished animation and remember that it has been handled.
     *
     * <p>Returns null for an unknown instance AND records it as handled, so an animation we never saw start can
     * never be settled as a completion later.
     */
    @Nullable
    public Session take(UUID instanceId) {
        if (instanceId == null) {
            return null;
        }
        markHandled(instanceId);
        return this.running.remove(instanceId);
    }

    /** Whether this instance id has already been settled or refused. */
    public boolean isHandled(UUID instanceId) {
        return instanceId != null && this.handled.containsKey(instanceId);
    }

    /** Remember an instance as terminal without taking a session (a duplicate stop, or an unknown stop). */
    public void markHandled(UUID instanceId) {
        if (instanceId == null) {
            return;
        }
        this.handled.put(instanceId, Boolean.TRUE);
        trimIfNeeded();
    }

    /** How many animations are currently tracked. */
    /** Read-only lookup; a stopped/interrupted session never grants overlap protection. */
    @Nullable
    public Session findActivePair(UUID player, UUID creature) {
        for (Session session : running.values()) {
            if (!session.interrupted && !session.escapedByPlayer
                    && session.playerId.equals(player) && session.creatureId.equals(creature)) return session;
        }
        return null;
    }

    /** A generic interruption does not cancel the existing forced escape rule. */
    public Session findUnescapedForcedPair(UUID player, UUID creature) {
        for (var session : running.values()) {
            if (session.forced && !session.escapedByPlayer
                    && session.playerId.equals(player) && session.creatureId.equals(creature)) return session;
        }
        return null;
    }

    public int runningCount() {
        return this.running.size();
    }

    /** How many instance ids are remembered as terminal. */
    public int handledCount() {
        return this.handled.size();
    }

    /** Drop all state, for a server stop or a world reload. */
    public void clear() {
        this.running.clear();
        this.handled.clear();
    }

    private void trimIfNeeded() {
        if (this.handled.size() > MAX_TRACKED) {
            // Terminal ids only matter while a duplicate stop could still arrive; dropping the whole set is
            // safe at this size and keeps the memory bounded on a long-running server.
            this.handled.clear();
        }
    }
}
