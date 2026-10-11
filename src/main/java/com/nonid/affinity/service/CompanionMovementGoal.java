package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.data.CompanionMode;
import com.nonid.affinity.mixin.MobGoalAccess;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Owns MOVE only while holding or following, so vanilla wander cannot replace our path. */
public final class CompanionMovementGoal extends Goal {
    private static final Set<MobEntity> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());
    private final MobEntity mob;
    private CompanionMovementGoal(MobEntity mob) {
        this.mob = mob;
        setControls(EnumSet.of(Control.MOVE, Control.JUMP));
    }
    public static void install(MobEntity mob) {
        if (CompanionService.usesNativeControl(mob)) return;
        if (INSTALLED.add(mob)) {
            MobGoalAccess access = (MobGoalAccess) mob;
            gate(access.affinity$getGoals(), mob, true);
            gate(access.affinity$getTargets(), mob, false);
            access.affinity$getGoals().add(0, new CompanionMovementGoal(mob));
        }
    }
    /**
     * Whether this module is managing the creature at all -- the condition the GATES depend on.
     *
     * <p>Delegates to {@code AirSupply.isManaged}, which takes no air input at all. That is deliberate:
     * the gates that suppress vanilla follow and retaliation must never be driven by the air rule. When
     * they were, a drowning companion silently un-gated the whole vanilla AI and started following its
     * owner and picking its own enemies again.
     */
    private static boolean isManagedCompanion(MobEntity mob) {
        if (CompanionService.usesNativeControl(mob)) return false;
        if (!RelationshipBehavior.controls(mob, CompanionService.companionOwner(mob))) return false;
        // Sampled once, in the order the rule documents, so the occupancy check (which asks another mod)
        // is not run twice per query.
        boolean serverSide = mob.getEntityWorld() instanceof ServerWorld;
        boolean alive = mob.isAlive();
        boolean hasOwner = CompanionService.companionOwner(mob) != null;
        boolean conflict = CompanionService.hasOwnershipConflict(mob);
        boolean occupied = serverSide
                && CompanionFollowService.isExternallyOccupied(mob, (ServerWorld) mob.getEntityWorld());
        return AirSupply.isManaged(serverSide, alive, hasOwner, conflict, occupied);
    }

    /**
     * Whether this goal may take and hold movement this tick.
     *
     * <p>False while the creature urgently needs air. This goal claims MOVE and JUMP, the same two
     * controls vanilla's SwimGoal (JUMP) and BreatheAirGoal (MOVE) need, so holding them would stop it
     * surfacing. Standing down releases ONLY movement: the gates stay in force, because they are keyed on
     * {@link #isManagedCompanion} instead. WAIT still does not chase and WANDER still does not follow the
     * owner while the creature gets the movement it needs to breathe.
     */
    private static boolean controlsCompanion(MobEntity mob) {
        return AirSupply.mayControlMovement(isManagedCompanion(mob), mustYieldForAir(mob));
    }

    /** Whether this creature needs to surface, and must be given its own movement to do it. */
    static boolean mustYieldForAir(MobEntity mob) {
        return AirSupply.mustYieldForAir(mob.isSubmergedInWater(), mob.getAir(), mob.getMaxAir(),
                mob.canBreatheInWater());
    }
    private static void gate(GoalSelector selector, MobEntity mob, boolean movement) {
        for (var entry : java.util.List.copyOf(selector.getGoals())) {
            Goal original = entry.getGoal();
            if (movement ? original instanceof FollowOwnerGoal : original instanceof TrackTargetGoal) {
                selector.remove(original);
                // Gated on isManagedCompanion, NOT on controlsCompanion: the air yield must not reopen
                // vanilla owner-following or retaliation. See isManagedCompanion.
                selector.add(entry.getPriority(), new ConditionalGoal(original, () -> !isManagedCompanion(mob)));
            }
        }
    }
    /** Read the live order on each call; air yielding must not reopen autonomous pursuit. */
    public static boolean holdsPosition(MobEntity mob) {
        return CompanionService.modeOf(mob)==CompanionMode.WAIT && isManagedCompanion(mob);
    }
    public static void clear() { INSTALLED.clear(); }
    @Override public boolean canStart() {
        if (!controlsCompanion(mob)) return false;
        ServerWorld world = (ServerWorld) mob.getEntityWorld();
        CompanionMode mode = CompanionService.modeOf(mob);
        if (mode == CompanionMode.WAIT) return true;
        if (mode == CompanionMode.WANDER || (mob.getTarget() != null && mob.getTarget().isAlive())) return false;
        var owner = world.getPlayerByUuid(CompanionService.companionOwner(mob));
        return owner != null && owner.isAlive() && !owner.isSpectator();
    }
    @Override public boolean shouldContinue() { return canStart(); }
    @Override public boolean shouldRunEveryTick() { return true; }
    @Override public void tick() {
        // Checked here as well, not only in canStart(). While the creature is being given movement to
        // surface, this goal must not also steer it: an explicit guard makes that independent of whether
        // the selector happens to call shouldContinue before tick.
        if (!controlsCompanion(mob)) {
            return;
        }
        ServerWorld world = (ServerWorld) mob.getEntityWorld();
        var config = NonCreatureAffinity.getConfig();
        if (CompanionService.modeOf(mob) == CompanionMode.WAIT) {
            CompanionFollowService.driveWait(world, mob, config);
        } else if (world.getPlayerByUuid(CompanionService.companionOwner(mob)) instanceof ServerPlayerEntity owner) {
            CompanionFollowService.driveFollow(mob, owner, config, CompanionService.modeOf(mob).name());
        }
    }
    @Override public void start() { CompanionFollowService.releaseNavigation(mob); }

    /**
     * Stop steering when the goal ends.
     *
     * <p>Only stops the navigation this goal itself started. During an air yield the goal never started,
     * so nothing of ours is cancelled -- and stopping unconditionally here would fight the creature's own
     * surfacing movement at exactly the wrong moment.
     */
    @Override public void stop() { CompanionFollowService.releaseNavigation(mob); }
}

