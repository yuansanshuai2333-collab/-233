package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.config.AffinityConfig;
import com.nonid.affinity.data.AffinityHolder;
import com.nonid.affinity.data.CompanionMode;
import com.nonid.affinity.data.CreatureAffinityData;
import com.nonid.affinity.data.PlayerAffinity;
import java.util.UUID;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The ONE place a creature becomes a friend and a companion, whatever started it.
 *
 * <p>There are three legitimate ways to reach that state, and they must not develop three slightly
 * different sets of rules:
 * <ul>
 *   <li><b>vanilla taming succeeded</b> -- a bone tames a wolf, a horse is broken in. The actual owner is
 *       known at that moment;</li>
 *   <li><b>the affinity threshold was crossed</b> by the mod's own interactions; and</li>
 *   <li><b>an old save is loaded</b> with a pet that predates the companion system at all.</li>
 * </ul>
 *
 * <p>Two properties are load-bearing and are enforced here rather than at each call site:
 * <ul>
 *   <li><b>idempotent.</b> Running it twice changes nothing the second time: no second reward, no
 *       duplicated chat message, no reset of a mode the player already chose. This is what makes it safe
 *       to run on every entity load.</li>
 *   <li><b>never steals.</b> Ownership rules live in {@link RelationshipRules}; a creature that belongs to
 *       another player is left alone, and a creature whose records contradict each other is reported as a
 *       conflict rather than resolved by picking a side.</li>
 * </ul>
 */
public final class RelationshipSync {

    private RelationshipSync() {
    }

    /**
     * Result of a synchronisation, so callers can report accurately and tests can assert idempotence.
     *
     * @param outcome      whether the creature is this player's companion afterwards
     * @param friendAdded  this call created the friend status
     * @param companionAdded this call created the companion bond
     * @param affinityRaised this call raised the affinity to the friend threshold
     */
    public record SyncResult(RelationshipRules.BondOutcome outcome, boolean friendAdded,
                             boolean companionAdded, boolean affinityRaised) {

        public boolean changed() {
            return this.friendAdded || this.companionAdded || this.affinityRaised;
        }
    }

    /**
     * Make {@code playerId} a friend and companion of this creature, without taking it from anyone else.
     *
     * <p>Deliberately takes the owner's <b>UUID</b> and only an optional live player:
     * <ul>
     *   <li>the entry points know the real owner as a UUID (vanilla taming stores it, and an old save may
     *       hold a player who is offline), so requiring the entity would make the offline case silently
     *       skipped;</li>
     *   <li>the player is used ONLY to decide whether to send feedback. It never decides ownership, which is
     *       why "the nearest player" can never be claimed by mistake.</li>
     * </ul>
     *
     * @param mob      the creature
     * @param playerId the actual owner; must not be null
     * @param online   the owner if they are present, for feedback only; may be null
     * @param source   short tag for the log, e.g. {@code vanilla_tame}
     */
    public static RelationshipRules.BondOutcome eligibility(MobEntity mob, UUID playerId) {
        return RelationshipRules.evaluate(existingCompanionOwner(mob),
                FriendlyDefenderSystem.vanillaOwnerUuid(mob), playerId,
                mob instanceof AffinityHolder holder && holder.affinityData() != null,
                AffinityService.isTamedCreature(mob));
    }

    public static SyncResult sync(MobEntity mob, UUID playerId, @Nullable ServerPlayerEntity online,
                                  String source) {
        return sync(mob, playerId, online, source, null);
    }

    /**
     * As {@link #sync(MobEntity, UUID, ServerPlayerEntity, String)}, but choosing the mode a creature with
     * no recorded mode starts in.
     *
     * <p>The preference is passed in rather than written by the caller beforehand, because a caller cannot
     * write it before the ownership rules have had their say: doing that would attach data to a creature that
     * is then refused, and it silently did nothing at all for a creature that had no data yet -- which is
     * exactly the old-save case the preference exists for.
     *
     * @param initialMode the mode to use when nothing is recorded, or null for the default (FOLLOW)
     */
    public static SyncResult sync(MobEntity mob, UUID playerId, @Nullable ServerPlayerEntity online,
                                  String source, @Nullable CompanionMode initialMode) {
        AffinityConfig config = NonCreatureAffinity.getConfig();
        CreatureAffinityData data = mob instanceof AffinityHolder holder ? holder.affinityData() : null;
        RelationshipRules.BondOutcome outcome = RelationshipRules.evaluate(
                existingCompanionOwner(mob), FriendlyDefenderSystem.vanillaOwnerUuid(mob), playerId,
                data != null, AffinityService.isTamedCreature(mob));
        if (outcome != RelationshipRules.BondOutcome.BOUND) {
            report(mob, playerId, outcome, source);
            return new SyncResult(outcome, false, false, false);
        }
        if (data == null) {
            // evaluate() already reports UNSUPPORTED_CREATURE, so this is unreachable; kept as a guard
            // rather than a null dereference.
            return new SyncResult(RelationshipRules.BondOutcome.UNSUPPORTED_CREATURE, false, false, false);
        }

        boolean friendAdded = false;
        boolean affinityRaised = false;
        PlayerAffinity relation = data.get(playerId);
        if (relation == null) {
            relation = data.getOrCreate(playerId,
                    RelationshipRules.initialFriendAffinity(config.friendThreshold));
            friendAdded = true;
        } else {
            int raised = RelationshipRules.affinityRaisedTo(relation.getAffinity(), config.friendThreshold);
            if (raised != relation.getAffinity()) {
                relation.setAffinity(raised, config.minAffinity, config.maxAffinity);
                affinityRaised = true;
            }
        }
        if (!relation.isBefriended()) {
            relation.setBefriended(true);
            friendAdded = true;
        }

        boolean companionAdded = false;
        UUID bondOwner = data.getCompanionOwner();
        if (bondOwner == null) {
            data.setCompanionOwner(playerId);
            // The mode is resolved against what is actually recorded, not against the default: a mode the
            // player chose is kept, and a creature that never had one may take the mode its own state
            // implies (a sitting pet stays put).
            data.setCompanionMode(RelationshipRules.resolveInitialMode(data.chosenMode(), initialMode));
            companionAdded = true;
        }

        if (changed(friendAdded, companionAdded, affinityRaised)) {
            NonCreatureAffinity.LOGGER.info(
                    "[NoN Affinity] {} is now a friend and companion of {} (source={}, affinity={})",
                    mob.getName().getString(), CompanionService.shortOwner(playerId), source,
                    relation.getAffinity());
            if (online != null) {
                GameplayFeedback.relationship(online,mob,net.minecraft.text.Text.translatable(
                        "message.noncreatureaffinity.unified_relationship", mob.getName())
                        .formatted(net.minecraft.util.Formatting.GOLD), false);
            }
        }
        return new SyncResult(RelationshipRules.BondOutcome.BOUND, friendAdded, companionAdded,
                affinityRaised);
    }

    /**
     * Backfill for a creature that is already tamed in vanilla but predates the companion system.
     *
     * <p>Runs on entity load, so it must be cheap and silent when there is nothing to do: it returns
     * immediately unless the creature is genuinely tamed AND has a confirmed player owner. It never
     * messages anyone, because the owner may be offline and there is no event to react to.
     *
     * @param allowVanillaTamed whether the configuration lets vanilla-tamed creatures into the system
     * @return the result, or null when there was nothing to backfill
     */
    @Nullable
    public static SyncResult backfillOnLoad(MobEntity mob, boolean allowVanillaTamed) {
        if (!allowVanillaTamed) {
            return null;
        }
        UUID vanillaOwner = FriendlyDefenderSystem.vanillaOwnerUuid(mob);
        if (vanillaOwner == null) {
            // No confirmed owner -- offline, absent or unreadable. Deliberately NOT claimed.
            return null;
        }
        if (!AffinityService.isTamedCreature(mob)) {
            // Not actually tamed: nothing to backfill.
            return null;
        }
        CreatureAffinityData data = mob instanceof AffinityHolder holder ? holder.peekAffinityData() : null;
        if (data != null) {
            // Exact birth configuration must survive reload, including auto-friend being disabled.
            if (data.getBirthParent() != null && data.get(vanillaOwner) != null) return null;
            PlayerAffinity relation = data.get(vanillaOwner);
            boolean alreadyWhole = relation != null && relation.isBefriended()
                    && vanillaOwner.equals(data.getCompanionOwner());
            if (alreadyWhole) {
                // The common case on every load after the first: return before touching anything, so this
                // never becomes a per-load write or a per-load log line.
                return null;
            }
        }
        // A sitting pet is a pet the player told to stay, so WAIT is the honest mapping; the preference is
        // handed to sync() so it applies to a creature that has no data yet -- the old-save case -- and is
        // dropped for one whose mode the player already chose.
        return sync(mob, vanillaOwner, null, "load_backfill",
                RelationshipRules.initialMode(isSitting(mob)));
    }

    /** Whether the creature is currently sitting, across the families that support it. */
    private static boolean isSitting(MobEntity mob) {
        return mob instanceof TameableEntity tameable && tameable.isSitting();
    }

    private static boolean changed(boolean friendAdded, boolean companionAdded, boolean affinityRaised) {
        return friendAdded || companionAdded || affinityRaised;
    }

    @Nullable
    private static UUID existingCompanionOwner(MobEntity mob) {
        CreatureAffinityData data = mob instanceof AffinityHolder holder ? holder.peekAffinityData() : null;
        return data == null ? null : data.getCompanionOwner();
    }

    /** Report a refusal once, at a level that stays quiet in normal play. */
    private static void report(MobEntity mob, UUID playerId, RelationshipRules.BondOutcome outcome,
                               String source) {
        boolean conflict = outcome == RelationshipRules.BondOutcome.OWNERSHIP_CONFLICT;
        if (conflict) {
            // Always worth a line: this state cannot be produced by normal play and needs an operator to
            // look at it, so it is not gated on debug logging.
            NonCreatureAffinity.LOGGER.warn(
                    "[NoN Affinity] {} has contradictory ownership (companion={} vanilla={}); refusing to "
                            + "bond it to {} (source={}). Use /nonaffinity companion info then clear.",
                    mob.getName().getString(),
                    CompanionService.shortOwner(existingCompanionOwner(mob)),
                    CompanionService.shortOwner(FriendlyDefenderSystem.vanillaOwnerUuid(mob)),
                    CompanionService.shortOwner(playerId), source);
            return;
        }
        if (NonCreatureAffinity.getConfig().debugLogging) {
            NonCreatureAffinity.LOGGER.info(
                    "[NoN Affinity] not bonding {} to {}: {} (source={})",
                    mob.getName().getString(), CompanionService.shortOwner(playerId), outcome, source);
        }
    }
}
