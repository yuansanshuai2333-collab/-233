package com.nonid.affinity.service;

import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * The decision rules for establishing a companion bond, with no Minecraft types.
 *
 * <p>Split out so the OWNERSHIP rules can be tested directly. The version that first tried to cover them
 * through {@code MobEntity} could not run in a test process at all -- {@code MobEntity} does not link
 * there -- so the rules that matter most (never steal another player's pet, never transfer a bond, treat a
 * missing owner reference as "unknown" rather than "free") would have gone unverified.
 *
 * <p>Every entry point into "become a companion" uses these, so vanilla taming, the affinity threshold and
 * the old-save backfill cannot drift apart.
 */
public final class RelationshipRules {

    private RelationshipRules() {
    }

    /** Current numeric status, distinct from the persistent history of having befriended a creature. */
    public static boolean meetsFriendThreshold(int affinity, int threshold) {
        return affinity >= threshold;
    }

    /**
     * Why a bond could not be established.
     *
     * <p>Named outcomes rather than a boolean, because the operator-facing diagnosis must distinguish
     * "this is somebody else's pet" from "this pet's records contradict each other".
     */
    public enum BondOutcome {
        /** The bond was established (or already existed with the same owner). */
        BOUND,
        /** Another player already has this creature as a companion. */
        OTHER_PLAYER_COMPANION,
        /** A vanilla owner exists and is a different player. */
        OTHER_PLAYER_VANILLA_OWNER,
        /** The creature is already tamed, but its owner reference is missing. Never infer a replacement. */
        UNKNOWN_VANILLA_OWNER,
        /** The vanilla owner and the companion owner contradict each other; neither side may act. */
        OWNERSHIP_CONFLICT,
        /** The creature cannot hold affinity data. */
        UNSUPPORTED_CREATURE
    }

    /** Whether the outcome means the creature is (now) this player's companion. */
    public static boolean isBonded(BondOutcome outcome) {
        return Objects.requireNonNull(outcome) == BondOutcome.BOUND;
    }

    /**
     * Decide whether {@code player} may be recorded as this creature's companion owner.
     *
     * <p>Rules, in order:
     * <ol>
     *   <li>the creature must be able to hold affinity data at all;</li>
     *   <li>a companion owner of a DIFFERENT player is never overwritten -- a bond is never transferred;</li>
     *   <li>a vanilla owner of a DIFFERENT player blocks the bond outright;</li>
     *   <li>when both records exist and disagree, NEITHER side may act, which is reported as a conflict so
     *       it can be diagnosed rather than silently resolved by picking a winner.</li>
     * </ol>
     *
     * <p>A MISSING vanilla owner reference is treated as "unknown", not as "free to claim".
     */
    public static BondOutcome evaluate(@Nullable UUID companionOwner, @Nullable UUID vanillaOwner,
                                       UUID player, boolean supported) {
        return evaluate(companionOwner, vanillaOwner, player, supported, false);
    }

    /** The tamed flag distinguishes a genuinely unowned creature from a missing owner record. */
    public static BondOutcome evaluate(@Nullable UUID companionOwner, @Nullable UUID vanillaOwner,
                                       UUID player, boolean supported, boolean vanillaTamed) {
        Objects.requireNonNull(player, "player");
        if (!supported) {
            return BondOutcome.UNSUPPORTED_CREATURE;
        }
        if (vanillaTamed && vanillaOwner == null) {
            return BondOutcome.UNKNOWN_VANILLA_OWNER;
        }
        if (companionOwner != null && vanillaOwner != null && !companionOwner.equals(vanillaOwner)) {
            return BondOutcome.OWNERSHIP_CONFLICT;
        }
        if (companionOwner != null && !companionOwner.equals(player)) {
            return BondOutcome.OTHER_PLAYER_COMPANION;
        }
        if (vanillaOwner != null && !vanillaOwner.equals(player)) {
            return BondOutcome.OTHER_PLAYER_VANILLA_OWNER;
        }
        return BondOutcome.BOUND;
    }

    /**
     * The affinity a NEW friend record should start at.
     *
     * <p>The threshold, so "becomes a friend" and "has friend-level affinity" cannot disagree. An existing
     * record is never lowered: this value is only used when creating the relation.
     */
    public static int initialFriendAffinity(int friendThreshold) {
        return Math.max(0, friendThreshold);
    }

    /**
     * The mode a creature starts in when no mode has ever been recorded for it.
     *
     * <p>A sitting pet is a pet its owner explicitly told to stay, so WAIT is the honest mapping: it keeps
     * the behaviour the player already chose instead of silently turning the pet into a follower. Anything
     * else follows.
     *
     * <p>This is only ever used when no mode is stored. A mode the player picked is never rewritten, which
     * is why the decision is expressed as a rule rather than as a write.
     */
    public static com.nonid.affinity.data.CompanionMode initialMode(boolean sitting) {
        return sitting ? com.nonid.affinity.data.CompanionMode.WAIT
                : com.nonid.affinity.data.CompanionMode.FOLLOW;
    }

    /**
     * The mode to store for a creature that is being bonded, given what is already recorded.
     *
     * <p>A mode the player has already chosen always wins, including when it differs from what the
     * creature's own state suggests -- the player's order is the authority. Only when nothing was ever
     * chosen does the preferred mode apply, falling back to FOLLOW.
     *
     * @param chosen    the mode already recorded, or null when none ever was
     * @param preferred the mode the caller would start with, or null for the default
     */
    public static com.nonid.affinity.data.CompanionMode resolveInitialMode(
            @Nullable com.nonid.affinity.data.CompanionMode chosen,
            @Nullable com.nonid.affinity.data.CompanionMode preferred) {
        if (chosen != null) {
            return chosen;
        }
        return preferred == null ? com.nonid.affinity.data.CompanionMode.FOLLOW : preferred;
    }

    /**
     * Whether a taming event may be credited to a player.
     *
     * <p>Deliberately requires ALL THREE: the creature is tamed, a persistent owner reference exists, and the
     * event happened on the logical server. An owner being written is not a tame -- vanilla's horse path
     * writes the owner BEFORE it sets the tame flag, so a check placed on the owner write sees
     * {@code tamed == false} and the tame is missed. The flag is therefore never dropped from this rule; the
     * anchor has to be the point where both writes are done.
     *
     * @param tamed      the creature's tame flag at the moment of the decision
     * @param ownerId    the persistent owner uuid, or null when the reference is missing/unreadable
     * @param serverSide whether this side owns the world state (client-side calls must never bond)
     */
    public static boolean isTameCompleted(boolean tamed, @Nullable UUID ownerId, boolean serverSide) {
        return serverSide && tamed && ownerId != null;
    }

    /**
     * The affinity an existing record should be raised to, if any.
     *
     * <p>Returns the current value when it is already at or above the threshold, so a caller can write the
     * result back unconditionally without ever LOWERING a relation. Raising is deliberate: a creature that
     * becomes the player's friend through vanilla taming must present the same affinity a friend has.
     */
    public static int affinityRaisedTo(int current, int friendThreshold) {
        return Math.max(current, initialFriendAffinity(friendThreshold));
    }
}
