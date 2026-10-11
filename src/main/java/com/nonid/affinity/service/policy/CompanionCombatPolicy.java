package com.nonid.affinity.service.policy;

import java.util.Objects;
import java.util.UUID;

/** Pure candidate policy. No Minecraft objects, navigation, damage, or target writes. */
public final class CompanionCombatPolicy {
    private CompanionCombatPolicy() {}

    // Map the project's persisted names with Mode.valueOf(existingMode.name()).
    public enum Mode { FOLLOW, WANDER, WAIT, ATTACK }
    public enum Trigger { OWNER_ATTACK, OWNER_HURT, HOSTILE_TO_OWNER, SELF_DEFENCE }
    public enum Action { IGNORE, ENGAGE, DEFEND_IN_PLACE }
    public enum Movement { FOLLOW_OWNER, NATURAL_ROAM, HOLD_POSITION }
    public enum Reason {
        ALLOWED, OWNER_UNAVAILABLE, OCCUPIED, UNSUPPORTED_COMBAT,
        INVALID_TARGET, PROTECTED_TARGET, WRONG_PRINCIPAL, WRONG_WORLD,
        STALE_EVENT, BEFORE_MODE_ENTRY, OUT_OF_RANGE, MODE_REJECTED,
        NO_CURRENT_HOSTILITY, OUT_OF_STATIONARY_REACH
    }
    public record Decision(Action action, Reason reason) {}

    /** principalId = owner for owner events/scans, companion for SELF_DEFENCE. */
    public record Evidence(Trigger trigger, UUID principalId, UUID targetId,
                           String worldId, long tick, long sequence,
                           boolean hostileBeforeAttack, boolean currentlyHostileToOwner) {
        public Evidence {
            Objects.requireNonNull(trigger);
            Objects.requireNonNull(principalId);
            Objects.requireNonNull(targetId);
            Objects.requireNonNull(worldId);
            if (tick < 0 || sequence < 0) throw new IllegalArgumentException("negative event clock");
        }
    }
    /** modeEntrySequence is an exclusive watermark: older commands are not replayed. */
    public record Context(Mode mode, UUID ownerId, UUID companionId, String worldId,
                          long nowTick, long modeEntrySequence, long maxAgeTicks,
                          boolean ownerAvailable, boolean externallyOccupied) {
        public Context {
            Objects.requireNonNull(mode);
            Objects.requireNonNull(ownerId);
            Objects.requireNonNull(companionId);
            Objects.requireNonNull(worldId);
            if (nowTick < 0 || modeEntrySequence < 0 || maxAgeTicks < 0)
                throw new IllegalArgumentException("negative policy clock");
        }
    }
    /** Computed on the server for THIS evidence's target, in the current tick. */
    public record Eligibility(boolean alive, boolean sameWorld, boolean protectedTarget,
                              boolean supportedCombat, boolean withinResponseRange,
                              boolean withinPursuitLimit, boolean withinStationaryReach) {}

    public static Movement defaultMovement(Mode mode) {
        return switch (Objects.requireNonNull(mode)) {
            case FOLLOW, ATTACK -> Movement.FOLLOW_OWNER;
            case WANDER -> Movement.NATURAL_ROAM;
            case WAIT -> Movement.HOLD_POSITION;
        };
    }

    public static Mode nextMode(Mode mode) {
        return switch (Objects.requireNonNull(mode)) {
            case FOLLOW -> Mode.WANDER;
            case WANDER -> Mode.WAIT;
            case WAIT -> Mode.ATTACK;
            case ATTACK -> Mode.FOLLOW;
        };
    }

    public static Decision decide(Context c, Evidence e, Eligibility t) {
        Objects.requireNonNull(c);
        Objects.requireNonNull(e);
        Objects.requireNonNull(t);
        if (c.externallyOccupied()) return ignore(Reason.OCCUPIED);
        // An absent owner prevents assistance, but not the companion's own self-defence.
        if (!c.ownerAvailable() && e.trigger() != Trigger.SELF_DEFENCE)
            return ignore(Reason.OWNER_UNAVAILABLE);
        if (!t.alive()) return ignore(Reason.INVALID_TARGET);
        if (!t.sameWorld() || !c.worldId().equals(e.worldId())) return ignore(Reason.WRONG_WORLD);
        if (t.protectedTarget() || e.targetId().equals(c.ownerId())
                || e.targetId().equals(c.companionId())) return ignore(Reason.PROTECTED_TARGET);
        if (!t.supportedCombat()) return ignore(Reason.UNSUPPORTED_COMBAT);
        UUID expected = e.trigger() == Trigger.SELF_DEFENCE ? c.companionId() : c.ownerId();
        if (!expected.equals(e.principalId())) return ignore(Reason.WRONG_PRINCIPAL);
        if (e.tick() > c.nowTick() || c.nowTick() - e.tick() > c.maxAgeTicks())
            return ignore(Reason.STALE_EVENT);
        if (e.sequence() <= c.modeEntrySequence()) return ignore(Reason.BEFORE_MODE_ENTRY);

        if (c.mode() == Mode.WAIT) {
            if (e.trigger() != Trigger.SELF_DEFENCE) return ignore(Reason.MODE_REJECTED);
            // Deliberately independent of OWNER response/leash distance.
            if (!t.withinStationaryReach()) return ignore(Reason.OUT_OF_STATIONARY_REACH);
            return new Decision(Action.DEFEND_IN_PLACE, Reason.ALLOWED);
        }
        if (!t.withinResponseRange() || !t.withinPursuitLimit()) return ignore(Reason.OUT_OF_RANGE);
        boolean allowed = switch (e.trigger()) {
            case SELF_DEFENCE, OWNER_HURT -> true;
            case OWNER_ATTACK -> c.mode() == Mode.FOLLOW || c.mode() == Mode.ATTACK
                    || (c.mode() == Mode.WANDER && e.hostileBeforeAttack());
            case HOSTILE_TO_OWNER -> c.mode() == Mode.ATTACK && e.currentlyHostileToOwner();
        };
        if (!allowed) {
            return ignore(e.trigger() == Trigger.HOSTILE_TO_OWNER
                    && c.mode() == Mode.ATTACK && !e.currentlyHostileToOwner()
                    ? Reason.NO_CURRENT_HOSTILITY : Reason.MODE_REJECTED);
        }
        return new Decision(Action.ENGAGE, Reason.ALLOWED);
    }

    private static Decision ignore(Reason reason) {
        return new Decision(Action.IGNORE, reason);
    }
}
