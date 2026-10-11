package com.nonid.affinity.data;

/**
 * How a bonded companion moves relative to its owner.
 *
 * <p>Stored on the creature, not on a per-player relation: one creature can have relations with
 * several players but can only follow one, so a per-relation flag would allow contradictory states.
 *
 * <p>WANDER means "does not actively follow". It does NOT mean the bond is gone, and it does not
 * disable protection.
 */
public enum CompanionMode {

    /** Follows the owner. This is the default for a newly bonded companion. */
    FOLLOW,

    /**
     * Stays near the spot where the order was given.
     *
     * <p>Not frozen: the creature may still flee danger and defend itself, it simply does not
     * wander off on its own.
     */
    WAIT,

    /** Keeps native roaming without following or recalling to the owner. */
    WANDER,

    /**
     * Actively engages creatures that are hostile toward the owner.
     *
     * <p>Deliberately narrow: the companion only fights creatures that have taken the owner as their
     * target, or that recently hurt the owner. It never goes looking for a fight, and with no such
     * threat it follows the owner instead.
     */
    ATTACK;

    /** Parse a stored name, falling back to {@link #FOLLOW} for missing or unknown values. */
    public static CompanionMode parse(String name) {
        if (name == null || name.isEmpty()) {
            return FOLLOW;
        }
        for (CompanionMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return FOLLOW;
    }

    /**
     * Translation key for this mode's display name.
     *
     * <p>Shared by the action-bar feedback and the detector panel so the two can never disagree;
     * an earlier version had the feedback only distinguish FOLLOW from everything else, which
     * reported WAIT and ATTACK as "wander".
     */
    public String translationKey() {
        return switch (this) {
            case FOLLOW -> "message.noncreatureaffinity.companion_follow";
            case WAIT -> "message.noncreatureaffinity.companion_wait";
            case WANDER -> "message.noncreatureaffinity.companion_wander";
            case ATTACK -> "message.noncreatureaffinity.companion_attack";
        };
    }

    /**
     * The next mode in the cycle: follow, wander, wait, attack, follow, ...
     *
     * <p>Delegated to the handover policy so the cycle cannot drift between the two, and so the
     * order the user confirmed is defined in exactly one place.
     */
    public CompanionMode next() {
        return valueOf(com.nonid.affinity.service.policy.CompanionCombatPolicy
                .nextMode(com.nonid.affinity.service.policy.CompanionCombatPolicy.Mode
                        .valueOf(name()))
                .name());
    }
}
