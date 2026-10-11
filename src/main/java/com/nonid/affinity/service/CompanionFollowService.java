package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.config.AffinityConfig;
import com.nonid.affinity.data.AffinityHolder;
import com.nonid.affinity.data.CompanionMode;
import com.nonid.affinity.data.CreatureAffinityData;
import com.nonid.affinity.non.NonBridge;
import com.nonid.affinity.service.policy.RecallFlow;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/** Companion navigation; target selection belongs exclusively to CompanionCombatController.
 * FOLLOW/ATTACK recall beyond ten blocks; WAIT holds its anchor; WANDER keeps native movement.
 * A movement goal arbitrates with native AI. Occupied entities always yield.
 */
public final class CompanionFollowService {

    /**
     * Companions this module is currently driving.
     *
     * <p>Tracked so that control can be released precisely, instead of calling stop() on creatures
     * whose movement this module never took over.
     */
    private static final Set<UUID> DRIVING = ConcurrentHashMap.newKeySet();
    private static final java.util.Map<UUID, net.minecraft.entity.ai.pathing.Path> OWN_PATHS = new ConcurrentHashMap<>();

    /**
     * Creatures whose path request last failed.
     *
     * <p>Used to log a failure only when the state changes, instead of repeating the same message
     * every evaluation interval.
     */
    private static final Set<UUID> PATH_FAILED = ConcurrentHashMap.newKeySet();

    /** Diagnostic records may be dropped; active navigation ownership must never be evicted. */
    private static final int MAX_DIAGNOSTIC_RECORDS = 512;

    private CompanionFollowService() {
    }

    /** Called on the configured interval from the world tick. */
    public static void tick(ServerWorld world, AffinityConfig config) {
        // DRIVING is shared across dimensions. Keep every active follower until release/forget:
        // entity unload already removes its record, and server stop clears the remainder.
        // Clearing by size loses the ownership needed to stop our navigation later.
        // Failed paths can accumulate even when no navigation request succeeds.
        // Bound diagnostic records independently so they cannot reset active followers.
        if (PATH_FAILED.size() > MAX_DIAGNOSTIC_RECORDS) {
            PATH_FAILED.clear();
        }
        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof MobEntity mob)) {
                continue;
            }
            if (CompanionService.usesNativeControl(mob)) continue;
            if (!mob.isAlive()) {
                // Dead: drop the records, but do not touch navigation or targets on a dead entity.
                disengage(mob, false);
                continue;
            }
            UUID ownerUuid = CompanionService.companionOwner(mob);
            if (ownerUuid == null || !RelationshipBehavior.controls(mob, ownerUuid)) {
                // Unbound: drop everything this module was controlling.
                disengage(mob, true);
                continue;
            }
            // A contradictory ownership record must not drive movement, matching the interaction
            // path which refuses both sides.
            if (CompanionService.hasOwnershipConflict(mob)) {
                disengage(mob, true);
                continue;
            }
            CompanionMode mode = CompanionService.modeOf(mob);

            // External occupancy makes every mode step aside.
            if (isExternallyOccupied(mob, world)) {
                disengage(mob, true);
                continue;
            }

            CompanionMovementGoal.install(mob);

            if (CompanionMovementGoal.mustYieldForAir(mob)) {
                // The world-tick driver must yield just like the movement goal. Otherwise WAIT
                // replaces the vanilla surfacing path with a path back to the submerged anchor.
                // Forget our navigation bookkeeping without stopping a breathing goal's path.
                DRIVING.remove(mob.getUuid());
                OWN_PATHS.remove(mob.getUuid());
                PATH_FAILED.remove(mob.getUuid());
                continue;
            }

            if (mode == CompanionMode.WANDER) {
                // The user's decision: WANDER keeps the creature's own natural movement and is NOT
                // bound to the spot where the order was given. This service therefore does not steer
                // it at all -- any target handling happens in the combat controller.
                release(mob);
                continue;
            }
            if (mode == CompanionMode.WAIT) {
                driveWait(world, mob, config);
                continue;
            }

            PlayerEntity found = world.getPlayerByUuid(ownerUuid);
            if (!(found instanceof ServerPlayerEntity owner) || !owner.isAlive()
                    || owner.isSpectator()) {
                // Owner offline, dead or elsewhere: stop, and end any engagement this module began.
                // Never pick a different player.
                disengage(mob, true);
                continue;
            }
            // Recall precedes combat yielding: a fighting follower must also return beyond 10 blocks.
            // The eligibility rule, the attempt and the outcome routing all live in RecallFlow, behind a
            // one-method adapter, so the call chain can be tested without a world. This lambda is the
            // ONLY production implementation: it measures the landing position, so the returning window
            // is decided from the state that actually exists after the teleport.
            RecallFlow.RecallContext recallContext = new RecallFlow.RecallContext(
                    mob.getUuid(), ownerUuid, true, Math.sqrt(mob.squaredDistanceTo(owner)),
                    mob.getEntityWorld() == owner.getEntityWorld(),
                    config.companionRecallDistance, world.getTime(),
                    config.companionReleaseSuppressTicks, config.companionReturnTicks);
            RecallFlow.RecallOutcome outcome = RecallFlow.attempt(
                    CompanionCombatController.progressOf(mob), recallContext, context -> {
                        // The eligibility rule is called through the flow's own single implementation; it
                        // is not re-derived here, so "attempt it" and "should it be attempted" cannot
                        // disagree.
                        var landed = CompanionRecall.tryRecall(world, mob, owner,
                                context.recallDistance());
                        if (landed == null) {
                            return RecallFlow.RecallOutcome.notRecalled();
                        }
                        // The distance to the owner measured from the position the teleport was GIVEN,
                        // not from a second read of the creature. A second read would depend on the
                        // position having been published already, which is exactly the kind of timing
                        // assumption that made the old pre-attempt distance wrong in the first place.
                        double distanceAfter = Math.sqrt(landed.squaredDistanceTo(owner.getX(),
                                owner.getY(), owner.getZ()));
                        return RecallFlow.RecallOutcome.recalledAt(distanceAfter);
                    });
            if (outcome.recalled()) {
                // The recall actually happened, so end the engagement here. Filtering old events alone
                // would not stop the keep-alive branch from restoring the abandoned target.
                CompanionCombatController.onRecalled(mob);
                DRIVING.remove(mob.getUuid());
                OWN_PATHS.remove(mob.getUuid());
                PATH_FAILED.remove(mob.getUuid());
                continue;
            }
            if (hasCombatTarget(mob)) {
                // Other modes step aside while the creature fights; movement is the combat AI's job.
                release(mob);
                continue;
            }
            driveFollow(mob, owner, config, mode.name());
        }
    }

    /** Walk toward the owner, using the start/stop thresholds. */
    static void driveFollow(MobEntity mob, ServerPlayerEntity owner, AffinityConfig config,
                                    String mode) {
        double distanceSq = mob.squaredDistanceTo(owner);
        double startSq = config.companionFollowStartDistance * config.companionFollowStartDistance;
        double stopSq = config.companionFollowStopDistance * config.companionFollowStopDistance;

        boolean alreadyDriving = DRIVING.contains(mob.getUuid());
        if (alreadyDriving) {
            // Already following. Keep going until the STOP distance is reached: the start threshold
            // must not double as the update threshold, or a creature whose path ends inside the
            // middle band stalls there while the owner keeps walking.
            if (distanceSq <= stopSq) {
                if (DRIVING.contains(mob.getUuid()) && config.debugLogging) {
                    // Distinct from PATH_ACCEPTED: this is the state the behaviour actually wants.
                    NonCreatureAffinity.LOGGER.info(
                            "[NoN Affinity] ARRIVED at owner for {} uuid={} at {}",
                            mob.getName().getString(), mob.getUuid(),
                            describePlacement(mob, owner, mode, config));
                }
                release(mob);
            } else if (mob.getNavigation().isIdle() || mob.getEntityWorld().getTime() % 10L == 0L) {
                requestPath(mob, owner, config, mode);
            }
        } else if (distanceSq > startSq) {
            requestPath(mob, owner, config, mode);
        }
    }

    /**
     * Hold a WAIT companion at the spot where the order was given.
     *
     * <p>Movement only: the companion returns if it has been pushed or drifted outside the WAIT
     * radius. It never chases. Fighting back is the combat controller's job, and it is bounded to
     * what can be reached from this spot.
     */
    static void driveWait(ServerWorld world, MobEntity mob, AffinityConfig config) {
        driveAnchor(world, mob, CompanionMode.WAIT, config);
    }

    /**
     * Keep a WAIT companion near its anchor.
     *
     * <p>Only intervenes when the creature has strayed too far: inside the radius its own AI is left
     * alone, which is what makes WANDER look like roaming rather than a leash. WAIT uses a tighter
     * radius, and neither freezes the creature, so it can still flee and fight.
     */
    private static void driveAnchor(ServerWorld world, MobEntity mob, CompanionMode mode,
                                    AffinityConfig config) {
        CreatureAffinityData data = mob instanceof AffinityHolder holder ? holder.peekAffinityData() : null;
        if (data == null || !data.hasAnchor()) {
            // No anchor recorded (for example a save from before anchors existed): do not invent one.
            release(mob);
            return;
        }
        String dimension = world.getRegistryKey().getValue().toString();
        if (!data.anchorIn(dimension)) {
            // The anchor is in another dimension. This stage does not teleport or load far chunks.
            release(mob);
            return;
        }
        // The radius comes from the config so the option actually does something. It used to be the
        // hardcoded 0.5 below while the config screen offered an editable "wait radius", so the slider
        // moved a number that no code read.
        double radius = config.companionWaitRadius;
        double dx = mob.getX() - data.anchorX();
        double dy = mob.getY() - data.anchorY();
        double dz = mob.getZ() - data.anchorZ();
        double distanceSq = dx * dx + dy * dy + dz * dz;

        if (distanceSq > radius * radius) {
            boolean started = mob.getNavigation().startMovingTo(data.anchorX(), data.anchorY(),
                    data.anchorZ(), config.companionFollowSpeed);
            if (started) {
                DRIVING.add(mob.getUuid());
            if (mob.getNavigation().getCurrentPath() != null) OWN_PATHS.put(mob.getUuid(), mob.getNavigation().getCurrentPath());
                PATH_FAILED.remove(mob.getUuid());
            } else if (PATH_FAILED.add(mob.getUuid())) {
                // Same once-per-state-change logging as the follow path.
                NonCreatureAffinity.LOGGER.info(
                        "[NoN Affinity] no path back to anchor for {} yet; will retry",
                        mob.getName().getString());
            }
        } else {
            // The hold goal prevents idle wandering and melee pursuit at the order position.
            releaseNavigation(mob);
        }
    }

    /**
     * Whether something outside this module currently owns the creature's movement.
     *
     * <p>These conditions make every mode step aside. Note that "is fighting" is NOT one of them:
     * see {@link #hasCombatTarget}. Treating a live target as occupancy is what previously stopped
     * ATTACK mode from ever validating its own target.
     */
    static boolean isExternallyOccupied(MobEntity mob, ServerWorld world) {
        if (mob.hasVehicle() || mob.hasPassengers()) {
            return true;   // riding something, or being ridden
        }
        if (mob.isLeashed()) {
            return true;   // a lead already decides where it goes
        }
        if (mob instanceof TameableEntity tameable && tameable.isSitting()) {
            return true;   // explicitly told to sit
        }
        // NeedsOfNature animations own movement while they run.
        return NonBridge.isActorBusy(world, mob);
    }

    /** Whether the creature is currently fighting something. */
    private static boolean hasCombatTarget(MobEntity mob) {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    /**
     * Ask the navigation to walk to the owner and record the outcome.
     *
     * <p>The result matters: claiming to drive a creature whose path failed would leave it recorded
     * as following while it stands still, and the record would never be cleared.
     */
    private static void requestPath(MobEntity mob, ServerPlayerEntity owner, AffinityConfig config,
                                    String mode) {
        boolean accepted = mob.getNavigation().startMovingTo(owner, config.companionFollowSpeed);
        if (accepted) {
            DRIVING.add(mob.getUuid());
            if (mob.getNavigation().getCurrentPath() != null) OWN_PATHS.put(mob.getUuid(), mob.getNavigation().getCurrentPath());
            if (PATH_FAILED.remove(mob.getUuid()) && config.debugLogging) {
                // PATH_ACCEPTED, not "RECOVERED": this only means the navigation took the request.
                // Whether the creature actually closes the distance is a separate question, and the
                // previous wording read as a behaviour claim the log could not support.
                NonCreatureAffinity.LOGGER.info(
                        "[NoN Affinity] PATH_ACCEPTED for {} uuid={} after earlier failures at {}",
                        mob.getName().getString(), mob.getUuid(),
                        describePlacement(mob, owner, mode, config));
            }
            return;
        }
        // No path right now (wall, height change, unloaded chunk, unreachable). Do not claim the
        // navigation; a later interval retries. Logged once per failure episode, not every interval, and only
        // under debugLogging: this fires every follow interval for a companion that cannot reach its owner and
        // was drowning out the settlement lines an operator has to read.
        if (PATH_FAILED.add(mob.getUuid()) && config.debugLogging) {
            NonCreatureAffinity.LOGGER.info(
                    "[NoN Affinity] PATH_REJECTED for {} uuid={} at {}",
                    mob.getName().getString(), mob.getUuid(),
                    describePlacement(mob, owner, mode, config));
        }
    }

    /**
     * Context for a navigation outcome.
     *
     * <p>Without identity, placement and navigation state a failure cannot be attributed: the same
     * name can belong to several creatures, and "no path" looks identical whether the cause is a
     * wall, a height change, distance or an entity still occupied by something else.
     */
    private static String describePlacement(MobEntity mob, ServerPlayerEntity owner, String mode,
                                            AffinityConfig config) {
        double dx = mob.getX() - owner.getX();
        double dy = mob.getY() - owner.getY();
        double dz = mob.getZ() - owner.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return String.format(java.util.Locale.ROOT,
                "mode=%s dim=%s mob=(%.1f,%.1f,%.1f) owner=(%.1f,%.1f,%.1f) dist=%.2f "
                        + "start=%.1f stop=%.1f navIdle=%s occupied=%s",
                mode,
                mob.getEntityWorld().getRegistryKey().getValue(),
                mob.getX(), mob.getY(), mob.getZ(),
                owner.getX(), owner.getY(), owner.getZ(),
                distance,
                config.companionFollowStartDistance, config.companionFollowStopDistance,
                mob.getNavigation().isIdle(),
                isExternallyOccupied(mob, (net.minecraft.server.world.ServerWorld) mob.getEntityWorld()));
    }

    /** Stop driving this creature, if this module was driving it. */
    /**
     * Give up everything this module controls for one creature.
     *
     * <p>Used by every exit path -- mode change, unbind, ownership conflict, owner unavailable,
     * external occupancy -- so that leaving ATTACK mode cannot strand a target with the vanilla
     * combat AI. Only a target this module still owns is cleared; a target owned by another system
     * is left alone.
     *
     * @param clearTarget false when the entity must not be touched at all (for example when dead)
     */
    private static void disengage(MobEntity mob, boolean clearTarget) {
        if (clearTarget) {
            // Only a target this service still owns is cleared; an external one is left alone.
            TargetRegistry.releaseIfOwned(mob, TargetRegistry.TargetSource.COMPANION_ATTACK);
        } else {
            TargetRegistry.forget(mob, TargetRegistry.TargetSource.COMPANION_ATTACK);
        }
        release(mob);
    }

    /** Only revoke a path that still belongs to this module. */
    public static void releaseNavigation(MobEntity mob) {
        DRIVING.remove(mob.getUuid());
        var path = OWN_PATHS.remove(mob.getUuid());
        if (path != null && mob.getNavigation().getCurrentPath() == path) mob.getNavigation().stop();
    }
    private static void release(MobEntity mob) {
        releaseNavigation(mob);
        PATH_FAILED.remove(mob.getUuid());
    }

    /** Forget all runtime state; used on server stop or world unload. */
    public static void clear() {
        DRIVING.clear();
        OWN_PATHS.clear();
        PATH_FAILED.clear();
        TargetRegistry.clear();
    }

    public static void forget(MobEntity mob) {
        DRIVING.remove(mob.getUuid());
        OWN_PATHS.remove(mob.getUuid());
        PATH_FAILED.remove(mob.getUuid());
        TargetRegistry.forget(mob, TargetRegistry.TargetSource.COMPANION_ATTACK);
    }
}
