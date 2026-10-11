package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.config.AffinityConfig;
import com.nonid.affinity.data.AffinityHolder;
import com.nonid.affinity.data.CompanionMode;
import com.nonid.affinity.data.CreatureAffinityData;
import com.nonid.affinity.data.PlayerAffinity;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Companion bonding and movement mode.
 *
 * <p>Ownership lives on the creature, because a creature can hold relations with several players
 * while following only one.
 *
 * <p>A bond is created either by an explicit interaction (empty-hand right click) or
 * automatically when a creature becomes this player's friend ({@code bindOnBefriend}).
 * Ownership is never inferred from an affinity number alone.
 *
 * <p>Rules kept deliberately conservative:
 * <ul>
 *   <li>an existing bond is never silently transferred to another player;</li>
 *   <li>a vanilla-owned creature answers only to its vanilla owner;</li>
 *   <li>eligibility is the real {@code befriended} flag, not the affinity number.</li>
 * </ul>
 */
public final class CompanionService {

    /** Outcome of a companion interaction, so the caller can report something specific. */
    public enum Result {
        /** The creature became this player's companion just now. */
        BOUND,
        /** The movement mode was switched. */
        TOGGLED,
        /** No established friendly relation with this player. */
        NOT_ELIGIBLE,
        /** Bonded to someone else, or vanilla-owned by someone else. */
        NOT_OWNER
    }

    private CompanionService() {
    }

    /** The player this creature follows, or null when unbound. */
    @Nullable
    public static UUID companionOwner(MobEntity mob) {
        CreatureAffinityData data = dataOf(mob);
        return data == null ? null : data.getCompanionOwner();
    }

    /** The vanilla owner uuid, or null when the creature has none. Used for reporting. */
    @Nullable
    public static UUID vanillaOwner(MobEntity mob) {
        return FriendlyDefenderSystem.vanillaOwnerUuid(mob);
    }

    /** Recognized vanilla pet types only; third-party ownership is outside this change. */
    public static boolean knownVanillaPet(MobEntity mob) {
        return (mob instanceof net.minecraft.entity.passive.TameableEntity
                || mob instanceof net.minecraft.entity.passive.AbstractHorseEntity)
                && "minecraft".equals(net.minecraft.registry.Registries.ENTITY_TYPE.getId(mob.getType()).getNamespace());
    }

    /** Native mechanisms remain intact; an authorized close companion may also use our movement. */
    private static boolean nativeMechanismOnly(MobEntity mob) {
        if (CreakingMemory.bound(mob)) return true;
        boolean petType = mob instanceof net.minecraft.entity.passive.TameableEntity
                || mob instanceof net.minecraft.entity.passive.AbstractHorseEntity;
        return petType && (!knownVanillaPet(mob)
                || (AffinityService.isTamedCreature(mob) && vanillaOwner(mob) == null));
    }

    public static boolean usesNativeControl(MobEntity mob) {
        if (nativeMechanismOnly(mob)) return true;
        if (!knownVanillaPet(mob)) return false;
        UUID owner = companionOwner(mob);
        return owner == null || !RelationshipBehavior.controls(mob, owner)
                || !mayControl(owner, vanillaOwner(mob), owner);
    }

    private static final ThreadLocal<Boolean> APPLYING_ORDER = ThreadLocal.withInitial(() -> false);

    /** Observe an actual vanilla sit command without creating relationships or changing ownership. */
    public static void vanillaSittingChanged(net.minecraft.entity.passive.TameableEntity pet, boolean sitting) {
        if (APPLYING_ORDER.get() || !(pet.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld)
                || !knownVanillaPet(pet) || !pet.isTamed()) return;
        UUID owner = vanillaOwner(pet);
        CreatureAffinityData data = dataOf(pet);
        if (owner == null || data == null || !RelationshipBehavior.controls(pet, owner)
                || !mayControl(data.getCompanionOwner(), owner, owner)) return;
        if (data.getCompanionOwner() == null) data.setCompanionOwner(owner);
        if (sitting) {
            applyModeOrder(pet, data, CompanionMode.WAIT);
        } else if (data.getCompanionMode() == CompanionMode.WAIT) {
            applyModeOrder(pet, data, CompanionMode.FOLLOW);
        }
        CompanionCombatController.onModeCommand(pet);
    }

    public static CompanionMode modeOf(MobEntity mob) {
        CreatureAffinityData data = dataOf(mob);
        return data == null ? CompanionMode.FOLLOW : data.getCompanionMode();
    }

    /**
     * Toggle the movement mode for this player's companion, binding it on first use.
     *
     * <p>Binding may also happen automatically when a creature becomes a friend
     * ({@code bindOnBefriend}); both routes go through {@code mayControl} so the ownership rules are
     * identical.
     */
    public static Result toggle(ServerPlayerEntity player, MobEntity mob, AffinityConfig config) {
        if (nativeMechanismOnly(mob)) return Result.NOT_ELIGIBLE;
        CreatureAffinityData data = dataOf(mob);
        if (data == null) {
            return Result.NOT_ELIGIBLE;
        }
        PlayerAffinity relation = data.get(player.getUuid());
        if (relation == null || !RelationshipStages.controls(relation.getAffinity(), config)) {
            // The real bond flag, not "affinity is above zero".
            return Result.NOT_ELIGIBLE;
        }

        // Ownership is decided from the stored uuid, never from resolving a live entity.
        UUID current = data.getCompanionOwner();
        if (!mayControl(current, FriendlyDefenderSystem.vanillaOwnerUuid(mob), player.getUuid())) {
            return Result.NOT_OWNER;
        }
        if (current == null) {
            data.setCompanionOwner(player.getUuid());
            applyModeOrder(mob, data, CompanionMode.FOLLOW);
            // Neutral wording: the mode is recorded here, movement is a later stage.
            NonCreatureAffinity.LOGGER.info("[NoN Affinity] companion bound: {} -> {} (mode {})",
                    mob.getName().getString(), player.getName().getString(), data.getCompanionMode());
            return Result.BOUND;
        }
        if (!current.equals(player.getUuid())) {
            return Result.NOT_OWNER;
        }
        // Cycle follow -> wander -> wait -> attack -> follow.
        CompanionMode mode = data.getCompanionMode().next();
        applyModeOrder(mob, data, mode);
        NonCreatureAffinity.LOGGER.info("[NoN Affinity] {} movement mode -> {}",
                mob.getName().getString(), mode);
        return Result.TOGGLED;
    }

    /** Apply an explicit, already-authorized owner command. Automatic binding does not wake pets. */
    private static void applyModeOrder(MobEntity mob, CreatureAffinityData data, CompanionMode mode) {
        if (nativeMechanismOnly(mob)) return;
        data.setCompanionMode(mode);
        if (mode == CompanionMode.WAIT
                && mob.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld world) {
            data.setAnchor(world.getRegistryKey().getValue().toString(),
                    mob.getX(), mob.getY(), mob.getZ());
        } else {
            // Free roaming and the two following modes have no stationary order position.
            data.clearAnchor();
        }
        if (mode != CompanionMode.WAIT
                && mob instanceof net.minecraft.entity.passive.TameableEntity tameable) {
            // A mobile command supersedes an earlier sit command. Without both updates the mode
            // changes on screen while the vanilla sitting gate still prevents any movement.
            boolean previous = APPLYING_ORDER.get();
            APPLYING_ORDER.set(true);
            try { tameable.setSitting(false); tameable.setInSittingPose(false); }
            finally { if (previous) APPLYING_ORDER.set(true); else APPLYING_ORDER.remove(); }
        }
    }
    /**
     * Bind a creature that has just become this player's friend.
     *
     * <p>The original request is "follows after becoming a friend", so a newly befriended creature
     * binds automatically. Existing bonds and other players' vanilla pets are still refused.
     *
     * @return whether a new bond was created
     */
    public static boolean bindOnBefriend(MobEntity mob, UUID player) {
        if (CreakingMemory.bound(mob)) return false;
        CreatureAffinityData data = dataOf(mob);
        if (data == null) {
            return false;
        }
        if (!RelationshipRules.isBonded(RelationshipSync.eligibility(mob, player))) {
            return false;
        }
        if (data.getCompanionOwner() != null) {
            // Already bonded to this player; nothing to do.
            return false;
        }
        data.setCompanionOwner(player);
        data.setCompanionMode(CompanionMode.FOLLOW);
        NonCreatureAffinity.LOGGER.info("[NoN Affinity] {} companion bond established with {}",
                mob.getName().getString(), player);
        return true;
    }

    /**
     * Whether a player may control this creature.
     *
     * <p>Pure so the rules can be unit tested without a live world -- in particular the case that
     * matters most: a vanilla owner who is offline or in another dimension must still block
     * everyone else. Resolving the owner to a loaded entity returned null there, which previously
     * let another player take the pet.
     *
     * <p>When both owners are recorded and disagree, NEITHER side may act: that state can only
     * come from an older version or an external change, and guessing which one is right would
     * silently transfer control. An operator repairs it with
     * {@code /nonaffinity companion info} and {@code companion clear}.
     *
     * @param companionOwner the existing companion bond, or null
     * @param vanillaOwner   the vanilla owner uuid, or null when the creature has none
     * @param player         the player asking for control
     */
    static boolean mayControl(@Nullable UUID companionOwner, @Nullable UUID vanillaOwner, UUID player) {
        if (companionOwner != null && vanillaOwner != null
                && !companionOwner.equals(vanillaOwner)) {
            // Conflict: refuse both sides rather than picking a winner.
            return false;
        }
        if (vanillaOwner != null && !vanillaOwner.equals(player)) {
            return false;
        }
        return companionOwner == null || companionOwner.equals(player);
    }

    /**
     * Whether this creature's recorded ownership is self-contradictory.
     *
     * <p>Used by the defender so it guards nobody in that state instead of following a different
     * rule than the interaction path.
     */
    public static boolean hasOwnershipConflict(MobEntity mob) {
        UUID companion = companionOwner(mob);
        UUID vanilla = vanillaOwner(mob);
        return companion != null && vanilla != null && !companion.equals(vanilla);
    }

    /**
     * Clear a companion bond.
     *
     * <p>Exists only to repair a stale bond written by an older version: in that state the wrong
     * player is refused by the vanilla owner check and the real owner is refused by the companion
     * check, so neither can switch the mode. Nothing is corrected automatically -- an operator
     * inspects the creature and clears the bad record, after which the legitimate player binds
     * normally. The vanilla owner, affinity, inventory and health are never touched.
     *
     * @return the owner whose bond was removed, or null when there was none
     */
    @Nullable
    public static UUID clearBond(MobEntity mob) {
        CreatureAffinityData data = dataOf(mob);
        if (data == null || data.getCompanionOwner() == null) {
            return null;
        }
        UUID previous = data.getCompanionOwner();
        NonCreatureAffinity.LOGGER.info("[NoN Affinity] companion bond cleared on {} (was {})",
                mob.getName().getString(), previous);
        data.setCompanionOwner(null);
        data.setCompanionMode(CompanionMode.FOLLOW);
        data.clearAnchor();
        return previous;
    }

    /**
     * Short, safe rendering of an owner id for logs.
     *
     * <p>Never prints the full UUID and never dereferences anything: logs about ownership may concern a
     * player who is offline, and a log line must not be the thing that resolves an entity.
     */
    public static String shortOwner(@Nullable UUID owner) {
        if (owner == null) {
            return "(none)";
        }
        String value = owner.toString();
        return value.length() >= 8 ? value.substring(0, 8) : value;
    }

    @Nullable
    private static CreatureAffinityData dataOf(MobEntity mob) {
        if (!(mob instanceof AffinityHolder holder)) {
            return null;
        }
        // Read-only access: an interaction query must not create data for unrelated creatures.
        return holder.peekAffinityData();
    }
}
