package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.config.AffinityConfig;
import com.nonid.affinity.data.CompanionMode;
import com.nonid.affinity.non.NonBridge;
import com.nonid.affinity.service.policy.CompanionCombatPolicy;
import com.nonid.affinity.service.policy.CompanionCombatPolicy.Action;
import com.nonid.affinity.service.policy.CompanionCombatPolicy.Context;
import com.nonid.affinity.service.policy.CompanionCombatPolicy.Decision;
import com.nonid.affinity.service.policy.CompanionCombatPolicy.Eligibility;
import com.nonid.affinity.service.policy.CompanionCombatPolicy.Evidence;
import com.nonid.affinity.service.policy.CompanionCombatPolicy.Mode;
import com.nonid.affinity.service.policy.EngagementRules;
import com.nonid.affinity.service.policy.EngagementState;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * The single place where a companion's attack target is decided and written.
 *
 * <p>The pure {@link CompanionCombatPolicy} decides; this class only gathers facts, computes the
 * per-target eligibility values it needs, and applies the result once. No damage callback writes a
 * target, and no second loop competes for the same creature.
 *
 * <p>Three outcomes:
 * <ul>
 *   <li>{@code ENGAGE} -- normal combat; the creature's own attack AI owns movement.</li>
 *   <li>{@code DEFEND_IN_PLACE} -- WAIT self-defence. The target is set, but movement is suppressed,
 *       so the creature answers from where it stands and stops when the attacker leaves reach. It
 *       must not chase, and returning to the hold point is separate navigation that never targets
 *       the attacker's position.</li>
 *   <li>{@code IGNORE} -- this candidate is not allowed. It does NOT clear other systems' targets;
 *       only a target this module owns is released, and only once no candidate remains valid.</li>
 * </ul>
 */
public final class CompanionCombatController {

    /** Per-companion runtime state. Never persisted; rebuilt from the save on load. */
    private static final class Runtime {
        /** Which companion this state belongs to, so launch stamps can be dropped with it. */
        final UUID companionId;

        Runtime(UUID companionId) {
            this.companionId = companionId;
        }

        long modeEntrySequence;
        CompanionMode lastMode;
        UUID ownerId;
        /** True while the creature is answering from a fixed spot (WAIT self-defence). */
        boolean defendingInPlace;
        long nextStationaryAttack;

        /**
         * The fight this companion is already in, independent of the evidence that started it.
         *
         * <p>Evidence expires after {@code companionEventMaxAgeTicks}. Choosing only from evidence
         * meant that once the initiating fact aged out the controller returned IGNORE and released a
         * perfectly healthy fight. The engagement is therefore remembered here and re-validated on
         * its own terms each round.
         */
        UUID engagedTarget;
        boolean engagementWasSelfDefence;
        /** Sequence of the fact that began the engagement, for diagnostics and re-validation. */
        long engagedSequence;

        /**
         * Progress bookkeeping for this fight.
         *
         * <p>Shared with the tests, so the production path and the tested path are the same
         * transitions rather than two copies that can drift.
         */
        final EngagementState progress = new EngagementState();
        /** Distance baseline for detecting an approach. Timing lives in {@link #progress}. */
        double lastDistanceToTarget = Double.MAX_VALUE;

        /**
         * Whether the most recent exit from this companion's fight was a STALL rather than a recall.
         *
         * <p>Diagnostics and attribution only: the decision itself reads the suppression window. Kept off
         * the engagement record so that attributing an exit can never make a released fight look live.
         */
        boolean lastEngagementExitWasStall;
    }

    /**
     * A chosen action plus the fact that produced it.
     *
     * <p>The trigger and sequence are carried through rather than re-derived later: an earlier version
     * hardcoded a self-defence flag at the point of use, so every engagement was recorded as
     * self-defence regardless of what had actually started it.
     */
    private record Selection(Decision decision, LivingEntity target,
                             CompanionCombatPolicy.Trigger trigger, long sequence) {

        static Selection ignored(Decision decision) {
            return new Selection(decision, null, null, -1L);
        }
    }

    private static final Map<UUID, Runtime> RUNTIME = new ConcurrentHashMap<>();

    private CompanionCombatController() {
    }

    /** Stamp a command immediately, rather than discarding hits before the next periodic tick. */
    public static void onModeCommand(MobEntity mob) {
        if (CompanionService.usesNativeControl(mob)) return;
        disengage(mob);
        CompanionFollowService.releaseNavigation(mob);
        CompanionFollowService.forget(mob);
        Runtime state = new Runtime(mob.getUuid());
        state.lastMode = CompanionService.modeOf(mob);
        state.ownerId = CompanionService.companionOwner(mob);
        state.modeEntrySequence = CompanionEventLog.peekSequence();
        RUNTIME.put(mob.getUuid(), state);
        if (!isExternallyOccupied(mob, (ServerWorld) mob.getEntityWorld())
                && !CompanionMovementGoal.mustYieldForAir(mob)) {
            CompanionFollowService.releaseNavigation(mob);
            TargetRegistry.releaseIfOwned(mob, TargetRegistry.TargetSource.COMPANION_ATTACK);
            TargetRegistry.revokeIfStale(mob);
        }
    }

    /** Called from the world tick after the defender scan. */
    public static void tick(ServerWorld world, AffinityConfig config) {
        long now = world.getTime();
        CompanionEventLog.prune(world.getRegistryKey().getValue().toString(), now,
                config.companionEventMaxAgeTicks);

        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof MobEntity mob) || !mob.isAlive()) {
                RUNTIME.remove(entity.getUuid());
                continue;
            }
            UUID ownerId = CompanionService.companionOwner(mob);
            if (CompanionService.usesNativeControl(mob)) continue;
            if (ownerId == null || !RelationshipBehavior.controls(mob, ownerId)) {
                // Unbound: hand back anything this module was controlling.
                disengage(mob);
                Runtime runtime = RUNTIME.remove(mob.getUuid());
                if (runtime != null && runtime.defendingInPlace) {
                    TargetRegistry.releaseIfOwned(mob, TargetRegistry.TargetSource.COMPANION_ATTACK);
                }
                continue;
            }
            if (CompanionService.hasOwnershipConflict(mob)) {
                disengage(mob);
                continue;
            }

            CompanionMode mode = CompanionService.modeOf(mob);
            // Install gates before selecting a target: stopping an old native goal can clear it.
            CompanionMovementGoal.install(mob);
            Runtime runtime = RUNTIME.computeIfAbsent(mob.getUuid(), Runtime::new);
            if (runtime.lastMode != mode || !ownerId.equals(runtime.ownerId)) {
                // Entering a mode raises the watermark: commands issued before the switch must not be
                // replayed, so a WANDER companion cannot keep hitting a target FOLLOW was allowed to
                // attack, and WAIT must stop an existing chase at once.
                runtime.modeEntrySequence = CompanionEventLog.nextSequence();
                runtime.lastMode = mode;
                runtime.ownerId = ownerId;
                runtime.defendingInPlace = false;
                // Switching mode or changing owner invalidates the fight that was running: its
                // justification (mode, protected owner) may no longer hold.
                endEngagement(runtime);
            }

            if (CompanionMovementGoal.mustYieldForAir(mob)) {
                // Releasing MOVE alone is insufficient: WAIT's in-place defence also stops
                // navigation, and an active attack goal could pursue while the hold goal yields.
                // End only our engagement and preserve the breathing goal's current path.
                disengage(mob);
                continue;
            }

            ServerPlayerEntity owner = resolveOwner(world, ownerId);
            boolean ownerAvailable = owner != null && owner.isAlive() && !owner.isSpectator();
            boolean occupied = isExternallyOccupied(mob, world);

            Context context = new Context(Mode.valueOf(mode.name()), ownerId, mob.getUuid(),
                    world.getRegistryKey().getValue().toString(), now,
                    runtime.modeEntrySequence, config.companionEventMaxAgeTicks,
                    ownerAvailable, occupied);

            Selection decision = choose(context, mob, owner, world, config);
            apply(mob, runtime, decision, owner, config);
        }
    }

    /**
     * Evaluate every candidate fact, newest first, and return the first allowed action.
     *
     * <p>Newest first matters: a later fact (for example the target turning friendly) must be able to
     * supersede an older one. Eligibility is recomputed for each candidate's own target, never
     * reused across targets.
     */
    private static Selection choose(Context context, MobEntity mob, ServerPlayerEntity owner,
                                   ServerWorld world, AffinityConfig config) {
        List<Evidence> candidates = new java.util.ArrayList<>();
        candidates.addAll(CompanionEventLog.selfFacts(mob.getUuid()));
        candidates.addAll(CompanionEventLog.ownerFacts(context.ownerId()));
        // Newest first, so the most recent fact wins.
        candidates.sort((a, b) -> Long.compare(b.sequence(), a.sequence()));

        Decision last = new Decision(Action.IGNORE,
                CompanionCombatPolicy.Reason.NO_CURRENT_HOSTILITY);
        for (Evidence evidence : candidates) {
            LivingEntity target = resolveTarget(world, evidence.targetId());
            // Checked for EVERY trigger, before anything reads a property of the target. A creature
            // that unloaded leaves a fact behind, and reading its position would throw.
            if (target == null) {
                continue;
            }
            if (evidence.trigger() == CompanionCombatPolicy.Trigger.HOSTILE_TO_OWNER
                    && (owner == null || !CombatEligibility.isThreatTo(target, owner))) {
                continue;
            }
            // The shared re-engagement rule. A brand-new fact must not bypass the returning state or a
            // suppression window: a fresh sequence number is not permission to resume the chase.
            Runtime candidateRuntime = RUNTIME.get(mob.getUuid());
            // The SAME mode decision the ongoing-engagement check uses. Two copies would drift, and a
            // WANDER companion would be allowed to start a fight it is then told to abandon.
            boolean ownerRangeApplies = candidateRuntime != null
                    && (candidateRuntime.lastMode == CompanionMode.FOLLOW
                        || candidateRuntime.lastMode == CompanionMode.ATTACK);
            if (candidateRuntime != null && owner != null) {
                double companionToOwner = Math.sqrt(mob.squaredDistanceTo(owner));
                // The TARGET's own distance to the owner, kept separate from the companion's. Without
                // this a companion standing beside its owner would charge an enemy fourteen blocks
                // away merely because its own distance was small.
                double targetToOwner = Math.sqrt(target.squaredDistanceTo(owner));
                if (!candidateRuntime.progress.canEngageUnderMode(evidence.targetId(),
                        mob.getEntityWorld().getTime(), evidence.sequence(), ownerRangeApplies,
                        companionToOwner, targetToOwner, config.companionRecallDistance,
                        config.companionReengageMargin)) {
                    continue;
                }
            } else if (candidateRuntime != null) {
                // No owner in the world: only an unbounded mode (WAIT / WANDER) may act.
                if (ownerRangeApplies
                        || !candidateRuntime.progress.canEngageUnderMode(evidence.targetId(),
                        mob.getEntityWorld().getTime(), evidence.sequence(), false,
                        Double.MAX_VALUE, Double.MAX_VALUE, config.companionRecallDistance,
                        config.companionReengageMargin)) {
                    continue;
                }
            }
            Eligibility eligibility = eligibilityOf(mob, owner, target, world, config, evidence);
            Decision decision = CompanionCombatPolicy.decide(context, evidence, eligibility);
            if (decision.action() != Action.IGNORE) {
                return new Selection(decision, target, evidence.trigger(), evidence.sequence());
            }
            last = decision;
        }
        return Selection.ignored(last);
    }

    /** Apply exactly one decision, and only ever touch a target this module owns. */
    private static void apply(MobEntity mob, Runtime runtime, Selection selection,
                              ServerPlayerEntity owner, AffinityConfig config) {
        Decision decision = selection.decision();
        LivingEntity current = mob.getTarget();
        boolean owned = TargetRegistry.owns(mob, TargetRegistry.TargetSource.COMPANION_ATTACK);
        if (!owned && current != null && current.isAlive()) {
            TargetRegistry.revokeIfStale(mob);
            endEngagement(runtime);
            runtime.defendingInPlace = false;
            return;
        }

        // Stall detection. It runs on BOTH paths: while a valid action was chosen this round (the
        // companion keeps receiving fresh evidence but cannot reach the target) and in the IGNORE
        // branch (evidence expired but the module still records a fight). Previously it ran only on
        // the action path, so a companion whose evidence aged out was never judged stalled.
        //
        // On detecting a stall it takes the FULL exit: end the engagement, remember the abandoned
        // target and its event watermark, establish a suppression window, and drop the Selection made
        // this round if that Selection is the same target. Ending the fight alone was not enough --
        // choose() runs BEFORE this and had already resolved the same enemy, so the target was written
        // straight back and the stall exit achieved nothing.
        LivingEntity stalledTarget = null;
        if (runtime.engagedTarget != null
                && !isExternallyOccupied(mob, (ServerWorld) mob.getEntityWorld())) {
            stalledTarget = detectStall(mob, runtime, config);
        }
        if (stalledTarget != null) {
            selection = runStallExit(mob, runtime, selection, stalledTarget, config);
            decision = selection.decision();
        }

        if (decision.action() == Action.IGNORE) {
            // Evidence is short-lived by design, so an empty candidate list is normal mid-fight.
            // Before releasing, re-validate the engagement actually in progress: it ends only for a
            // real reason, never merely because the initiating fact expired.
            // Driven by OUR record, not by `owned`: `owned` also requires the physical target to
            // still match, so a target cleared by the vanilla AI would skip this block entirely and
            // abandon a fight that is still recorded as in progress.
            // External occupancy wins: while the creature is ridden, leashed, sitting or animating,
            // nothing here may set a target. Give up our own control only.
            if (runtime.engagedTarget != null
                    && isExternallyOccupied(mob, (ServerWorld) mob.getEntityWorld())) {
                endEngagement(runtime);
                TargetRegistry.releaseIfOwned(mob, TargetRegistry.TargetSource.COMPANION_ATTACK);
                return;
            }
            if (runtime.engagedTarget != null) {
                LivingEntity engaged = resolveTarget((ServerWorld) mob.getEntityWorld(),
                        runtime.engagedTarget);
                if (engaged != null) {
                    double fromOwner = owner == null ? 0.0
                            : Math.sqrt(mob.squaredDistanceTo(owner));
                    double toTarget = Math.sqrt(mob.squaredDistanceTo(engaged));
                    // Progress is measured on the distance to the target, so a fight that keeps
                    // closing counts as progressing no matter how long it lasts.
                    if (toTarget < runtime.lastDistanceToTarget - 0.5) {
                        runtime.lastDistanceToTarget = toTarget;
                        runtime.progress.recordApproach(mob.getEntityWorld().getTime());
                    }
                    // Stall detection reads the state, so a landed hit renews the fight even while
                    // the companion stands still and never closes the distance.
                    long stalled = runtime.progress.isStalled(mob.getEntityWorld().getTime(),
                            config.companionEngageStallTicks) ? Long.MAX_VALUE : 0L;
                    // Mode-aware: only FOLLOW/ATTACK are bounded by the owner. WANDER must keep fighting after
        // its evidence expires even if the owner has moved off, and WAIT keeps its own rules.
        boolean ownerRangeForEngagement = runtime.lastMode == CompanionMode.FOLLOW
                || runtime.lastMode == CompanionMode.ATTACK;
        EngagementRules.Outcome outcome = EngagementRules.evaluate(
                            engaged.isAlive(),
                            engaged.getEntityWorld() == mob.getEntityWorld(),
                            // Keyed on the owner UUID, not the entity: with the entity form the
                            // whole check was skipped while the owner was offline, so a companion
                            // could keep attacking a protected creature.
                            CombatEligibility.isProtectedForOwner(engaged, runtime.ownerId),
                            owner != null && owner.isAlive() && !owner.isSpectator(),
                            runtime.lastMode == CompanionMode.WAIT,
                            runtime.engagementWasSelfDefence,
                            ownerRangeForEngagement && fromOwner > config.companionRecallDistance,
                            toTarget <= config.companionAttackLeashRadius,
                            stalled,
                            config.companionEngageStallTicks);
                    if (EngagementRules.shouldReassert(runtime.engagedTarget != null, outcome)) {
                        // Still a real fight. Re-assert the target: the physical target may have been
                        // cleared by the vanilla AI, and simply returning would leave the companion
                        // standing idle while the module still believed it was engaged.
                        LivingEntity physical = mob.getTarget();
                        boolean holdingNothing = physical == null;
                        boolean holdingOurs = physical == engaged;
                        if (EngagementRules.shouldRestore(holdingNothing, holdingOurs)) {
                            // The vanilla AI cleared it: restore the fight we still hold a record for.
                            TargetRegistry.claim(mob, engaged, CompanionService.companionOwner(mob),
                                    TargetRegistry.TargetSource.COMPANION_ATTACK,
                                    mob.getEntityWorld().getTime());
                            RelationshipBehavior.writeTarget(mob, engaged);
                            if (config.debugLogging) {
                                NonCreatureAffinity.LOGGER.info(
                                        "[NoN Affinity] {} restored cleared target {}",
                                        mob.getName().getString(), engaged.getName().getString());
                            }
                        } else if (holdingOurs) {
                            // Already in place; nothing to do.
                        } else if (EngagementRules.shouldYield(holdingNothing, holdingOurs)) {
                            // Another system holds a LIVE target. Restoring ours would overwrite it,
                            // which is exactly the ownership fault this module keeps fixing. Let it go.
                            if (config.debugLogging) {
                                NonCreatureAffinity.LOGGER.info(
                                        "[NoN Affinity] {} holding external target {}; not restoring {}",
                                        mob.getName().getString(), physical.getName().getString(),
                                        engaged.getName().getString());
                            }
                            endEngagement(runtime);
                            return;
                        }
                        return;
                    }
                    if (config.debugLogging) {
                        NonCreatureAffinity.LOGGER.info(
                                "[NoN Affinity] {} engagement with {} ended: {}",
                                mob.getName().getString(), engaged.getName().getString(), outcome);
                    }
                }
                // The engagement is over: forget it so it is not re-validated next round.
                endEngagement(runtime);
            }
            if (owned) {
                // Our own engagement is no longer allowed: release it.
                TargetRegistry.releaseIfOwned(mob, TargetRegistry.TargetSource.COMPANION_ATTACK);
            }
            if (runtime.defendingInPlace) {
                runtime.defendingInPlace = false;
                // Stop the in-place answer, but never steer anywhere.
                CompanionFollowService.releaseNavigation(mob);
            }
            if (runtime.lastMode == CompanionMode.WAIT
                    && !isExternallyOccupied(mob, (ServerWorld) mob.getEntityWorld())) {
                // An explicit hold order also cancels native retaliation/owner-assist targets.
                TargetRegistry.releaseIfOwned(mob, TargetRegistry.TargetSource.COMPANION_ATTACK);
                TargetRegistry.revokeIfStale(mob);
            }
            return;
        }

        LivingEntity wanted = selection.target();
        if (!owned) {
            // A live target we do not own belongs to another system: yield rather than overwrite.
            if (current != null && current.isAlive() && runtime.lastMode != CompanionMode.WAIT) {
                TargetRegistry.revokeIfStale(mob);
                return;
            }
        }

        if (wanted == null || !wanted.isAlive()) return;
        if (runtime.engagedTarget == null || !runtime.engagedTarget.equals(wanted.getUuid())) {
            // A new fight begins: remember it, and remember what actually started it.
            runtime.engagedTarget = wanted.getUuid();
            runtime.engagementWasSelfDefence =
                    selection.trigger() == CompanionCombatPolicy.Trigger.SELF_DEFENCE;
            runtime.engagedSequence = selection.sequence();
            // A genuinely new fight, so any previous exit reason no longer describes this record.
            runtime.lastEngagementExitWasStall = false;
            // A real start only: this branch runs when the target actually changed, so the clock is
            // not reset every round, which would defeat stall detection entirely.
            runtime.progress.begin(wanted.getUuid(), mob.getEntityWorld().getTime(),
                    selection.sequence());
            runtime.lastDistanceToTarget = Math.sqrt(mob.squaredDistanceTo(wanted));
        }
        if (current != wanted || !owned) {
            TargetRegistry.claim(mob, wanted, CompanionService.companionOwner(mob),
                    TargetRegistry.TargetSource.COMPANION_ATTACK, mob.getEntityWorld().getTime());
            RelationshipBehavior.writeTarget(mob, wanted);
        }

        if (decision.action() == Action.DEFEND_IN_PLACE) {
            runtime.defendingInPlace = true;
            // Deliberately no navigation order: staying put is the whole point, and moving to reach
            // the attacker is exactly what the user ruled out.
            CompanionFollowService.releaseNavigation(mob);
            long now = mob.getEntityWorld().getTime();
            // The hold goal excludes vanilla MOVE goals, including melee pursuit.
            // Only a visible target in normal melee reach may be hit, at most once per second.
            if (CombatEligibility.hasNativeMelee(mob) && now >= runtime.nextStationaryAttack
                    && mob.isInAttackRange(wanted) && mob.canSee(wanted)) {
                mob.tryAttack((ServerWorld) mob.getEntityWorld(), wanted);
                runtime.nextStationaryAttack = now + 20;
            }
        } else {
            runtime.defendingInPlace = false;
        }
    }

    /**
     * Take the complete stall exit: end the fight, remember the abandoned target, suppress it, and
     * re-validate the Selection this round already made.
     *
     * <p>Extracted from {@link #apply} so the ORDER is testable. Every part of this used to be missing
     * something: the fight ended with no suppression (so the next round re-selected the same enemy),
     * and the Selection was left standing (so the same round wrote it straight back). A rule test
     * cannot see either fault, because both live in how the controller sequences its calls.
     *
     * @param selection the decision already produced for this round
     * @return the selection to carry on with; the blocked one when it named the suppressed target
     */
    static Selection runStallExit(MobEntity mob, Runtime runtime, Selection selection,
                                  LivingEntity stalledTarget, AffinityConfig config) {
        UUID abandonedId = stalledTarget.getUuid();
        long now = mob.getEntityWorld().getTime();
        // The single production implementation of the state changes. A separate copy for the tests
        // would be a copy that can drift, which is the defect this whole round is about.
        markStallExit(runtime, abandonedId, now, config);
        TargetRegistry.releaseIfOwned(mob, TargetRegistry.TargetSource.COMPANION_ATTACK);
        if (config.debugLogging) {
            NonCreatureAffinity.LOGGER.info(
                    "[NoN Affinity] {} engagement stalled; {} suppressed for {} ticks",
                    mob.getName().getString(), stalledTarget.getName().getString(),
                    config.companionStallSuppressTicks);
        }
        // The candidate was evaluated BEFORE this exit and carries no knowledge of it. Materialised
        // before the check because Selection is a record holding a live entity reference.
        LivingEntity chosen = selection.target();
        UUID wanted = chosen == null ? null : chosen.getUuid();
        // The re-validation rule itself is a separate static method with no entity in its signature, so
        // the wiring test can call exactly this check instead of re-implementing it.
        if (selectionSurvivesRun(runtime.progress, wanted, now)) {
            return selection;
        }
        // Only the suppressed target is dropped: another threat chosen this round still applies, which
        // is why this returns a blocked selection instead of aborting the round.
        return Selection.ignored(new Decision(Action.IGNORE,
                CompanionCombatPolicy.Reason.NO_CURRENT_HOSTILITY));
    }

    /**
     * Whether a Selection may still be used after a stall exit ran in the same round.
     *
     * <p>The single rule, and free of Minecraft types, so the wiring test drives the production
     * predicate rather than a copy of it. A selection with no target always survives: ending a stale
     * fight must never cancel a decision about something else.
     *
     * @param progress the engagement state, after the exit has recorded its suppression
     * @param wantedId the target the selection named, or null for an IGNORE decision
     * @param nowTick  current server tick
     */
    static boolean selectionSurvivesRun(EngagementState progress, @Nullable UUID wantedId, long now) {
        Objects.requireNonNull(progress, "progress");
        if (wantedId == null) {
            return true;
        }
        return !progress.isSuppressedAt(wantedId, now);
    }

    /**
     * The state transitions of the stall exit, with no entity and no log in the signature.
     *
     * <p>Both the tick and the wiring test call exactly this, so the record keeping cannot drift between
     * the code under test and the code in production. What the tick adds is only the entity-facing side:
     * releasing the target on the creature and the debug line.
     *
     * <p>The engagement record is deliberately re-created carrying the stall reason, so it is visible
     * WHICH exit released the fight -- including the case where the evidence window had already closed
     * and choose() returned IGNORE, the branch with no Selection to protect.
     */
    static void markStallExit(Runtime runtime, UUID abandonedId, long now, AffinityConfig config) {
        endEngagement(runtime);
        runtime.progress.suppressStalledTarget(abandonedId, now + config.companionStallSuppressTicks);
        // Record WHICH exit released the fight, so the reason is attributable -- including the case where
        // the evidence window had already closed and choose() returned IGNORE, the branch with no
        // Selection to protect.
        //
        // A separate field on purpose. Re-creating a live engagement here instead (which an earlier
        // version did) was worse than useless: begin() stamps the progress clock, so the fused record
        // looked like a fresh, already-stalled fight, and the keep-alive branch tests exactly that field.
        // Nothing here may leave the engagement record looking alive.
        runtime.lastEngagementExitWasStall = true;
    }

    /**
     * Bring a companion's runtime record to the state of an established fight.
     *
     * <p>Keyed by id and free of Minecraft types, so a test can reach the same starting point the tick
     * reaches without a live world. It writes the same fields the ENGAGE branch writes, in the same
     * order. Taking the {@code Runtime} type as a parameter would drag a private nested type into the
     * signature, so this looks the record up itself.
     */
    static void runtimeBeginEngagement(UUID companionId, UUID targetId, long nowTick, long sequence) {
        Runtime runtime = runtimeFor(companionId);
        runtime.engagedTarget = targetId;
        runtime.engagedSequence = sequence;
        runtime.engagementWasSelfDefence = false;
        runtime.lastEngagementExitWasStall = false;
        runtime.progress.begin(targetId, nowTick, sequence);
        runtime.lastDistanceToTarget = Double.MAX_VALUE;
    }

    /** Run {@link #markStallExit} for a companion by id; the wiring test's entry point. */
    static void markStallExitFor(UUID companionId, UUID abandonedId, long now, AffinityConfig config) {
        markStallExit(runtimeFor(companionId), abandonedId, now, config);
    }

    /**
     * A one-line read-only snapshot of the launch-accounting tables.
     *
     * <p>Exists so the attribution stores can be verified from a running game ON DEMAND rather than by
     * reading it off a memory curve: neither a flat curve nor the absence of ERROR proves that records are
     * being released. This is a query, not a tick hook, so nothing is logged per frame.
     *
     * @return counts of melee stamps, projectile entries, and the projectile uuid index
     */
    public static String stampCounts() {
        return "melee=" + MeleeAttackStamps.heldCount()
                + " projectile=" + ProjectileStamps.heldCount()
                + " projectileUuidIndex=" + ProjectileStamps.uuidIndexCount();
    }

    /** The engagement state for a companion by id, or null when it has no runtime record. */
    @Nullable
    static EngagementState progressFor(UUID companionId) {
        Runtime runtime = RUNTIME.get(companionId);
        return runtime == null ? null : runtime.progress;
    }

    /** The target this companion's record names, for tests observing an exit. */
    @Nullable
    static UUID engagedTargetFor(UUID companionId) {
        Runtime runtime = RUNTIME.get(companionId);
        return runtime == null ? null : runtime.engagedTarget;
    }

    /** Whether the exit that most recently released this companion's fight was a stall. */
    static boolean lastExitWasStall(UUID companionId) {
        Runtime runtime = RUNTIME.get(companionId);
        return runtime != null && runtime.lastEngagementExitWasStall;
    }

    /**
     * Update the progress baseline and report whether the recorded fight has stalled.
     *
     * <p>Progress has exactly two sources, and this method runs one of them: closing distance on the
     * target. A landed hit is recorded by {@link #onCompanionHit} and renews the same state, so a
     * companion standing still while hitting is NOT judged stalled. There is deliberately no
     * "refresh every round" source, which is what makes an unreachable or invulnerable target
     * eventually give up.
     *
     * @return the stalled target, or null when the fight is still making progress (or its entity is
     *         no longer loaded, in which case there is nothing to measure)
     */
    @Nullable
    private static LivingEntity detectStall(MobEntity mob, Runtime runtime, AffinityConfig config) {
        ServerWorld world = (ServerWorld) mob.getEntityWorld();
        LivingEntity currentTarget = resolveTarget(world, runtime.engagedTarget);
        if (currentTarget == null) {
            return null;
        }
        double approachDistance = Math.sqrt(mob.squaredDistanceTo(currentTarget));
        if (approachDistance < runtime.lastDistanceToTarget - 0.5) {
            runtime.lastDistanceToTarget = approachDistance;
            runtime.progress.recordApproach(world.getTime());
        }
        return runtime.progress.isStalled(world.getTime(), config.companionEngageStallTicks)
                ? currentTarget : null;
    }

    /**
     * Look for creatures that are actually hostile to an owner right now and record that as a fact.
     *
     * <p>"Hostile mob type" is not hostility: the creature must currently target the owner, or have
     * attacked them recently. Without this the ATTACK mode would need a fresh damage event to act at
     * all, and would ignore an enemy that is already closing in.
     */
    public static void scanHostility(ServerWorld world, AffinityConfig config) {
        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof ServerPlayerEntity owner)) {
                continue;
            }
            double radius = config.companionAttackRadius;
            net.minecraft.util.math.Box box =
                    owner.getBoundingBox().expand(radius, radius / 2.0, radius);
            for (MobEntity candidate : world.getEntitiesByClass(MobEntity.class, box,
                    e -> e.isAlive() && !e.isSpectator())) {
                if (CombatEligibility.isProtected(candidate, owner)) {
                    continue;
                }
                if (!CombatEligibility.isThreatTo(candidate, owner)) {
                    continue;
                }
                CompanionEventLog.recordHostileToOwner(owner, candidate);
            }
        }
    }

    /**
     * Record that this companion LANDED a hit on its current target.
     *
     * <p>Called only after damage was actually applied, so a miss or a fully blocked hit never
     * reaches here. Resolving the attacker from the damage source means a ranged attack is credited to
     * whoever fired it, and damage caused by anything else is not credited at all.
     *
     * <p>The hit must match a launch this companion actually made, generation included, and that stamp
     * must still be live. Without the generation an arrow fired during an earlier fight would renew the
     * current one; without the lifecycle check any later damage on the same target would be credited to
     * a melee swing that had already finished.
     *
     * @param directAttack whether the damage source was the companion itself rather than something it
     *                     sent (a projectile, a summoned entity). A delayed hit is not the attack call
     *                     being stamped, so it is not credited.
     * @return whether the hit renewed the current fight
     */
    public static boolean onCompanionHit(MobEntity companion, LivingEntity victim,
                                         boolean directAttack) {
        Runtime runtime = RUNTIME.get(companion.getUuid());
        if (runtime == null) {
            return false;
        }
        long now = companion.getEntityWorld().getTime();
        if (!directAttack) {
            // Damage whose source entity is not the companion itself: not the attack call that was
            // stamped, so it must not renew anything.
            MeleeAttackStamps.clear(companion.getUuid());
            return false;
        }
        MeleeAttackStamps.Stamp stamp = MeleeAttackStamps.current(companion.getUuid());
        boolean credited = directAttack
                && meleeHitCredits(runtime.progress, stamp, victim.getUuid(), now);
        if (!credited) {
            // Either the damage was not the direct melee attack, or there is no live launch for it.
            // Neither is evidence about the fight in progress, so neither may renew it. The stamp is
            // consumed either way, so one attempt can authorise at most one hit.
            MeleeAttackStamps.clear(companion.getUuid());
            return false;
        }
        // Consume before recording: the stamp describes one attempt, and a second hit arriving from it
        // must not be credited again.
        MeleeAttackStamps.clear(companion.getUuid());
        // The stamp's OWN generation is verified inside recordHitAtGeneration; passing the live one here
        // would compare a value against itself and check nothing.
        boolean renewed = runtime.progress.recordHitAtGeneration(victim.getUuid(), stamp.generation(), now);
        if (NonCreatureAffinity.getConfig().debugLogging) {
            // One line per LANDED hit, which is the event the stall rule is about. Without it a battle that
            // ends because the target died cannot be distinguished from one that was merely approached,
            // so "hits refresh progress" would stay unverifiable from a real session.
            NonCreatureAffinity.LOGGER.info(
                    "[NoN Affinity] {} landed a melee hit on {} -> progress renewed={}",
                    companion.getName().getString(), victim.getName().getString(), renewed);
        }
        return renewed;
    }

    /**
     * Whether a landed melee hit may be credited to a launch stamp.
     *
     * <p>Extracted, and free of Minecraft types, so the attribution can be driven by the wiring test at
     * the exact moment real damage would arrive. That matters: the assertion "the stamp still exists while
     * the attack body runs" is only meaningful if the check performed at that moment is the SAME one
     * production performs, including the same-tick rule.
     *
     * <p>Three checks, all required: the stamp must exist, the victim must be the target it was aimed at,
     * and {@link MeleeAttackStamps#credits} must accept it -- which covers the fight generation and the
     * requirement that the launch happened on this tick.
     */
    static boolean meleeHitCredits(EngagementState progress, @Nullable Object stamp, UUID victimId,
                                   long nowTick) {
        if (progress == null || !(stamp instanceof MeleeAttackStamps.Stamp launch) || victimId == null) {
            return false;
        }
        return MeleeAttackStamps.credits(launch, victimId, progress.generation(), nowTick);
    }

    /**
     * Record that this companion is LAUNCHING an attack, stamping the engagement generation.
     *
     * <p>Called from the attack hook before the attack executes, because the generation can only be
     * captured at launch. Reading it when the hit lands would read whatever generation is current then
     * and verify nothing.
     *
     * <p>Any stamp this companion already held is discarded: only one attack call can be in progress for
     * one creature, and a stale stamp must never authorise a later hit.
     *
     * @param target the creature being attacked
     * @return an OPAQUE token to hand back when the attack call ends, or null when no stamp was issued.
     *         Opaque because the caller lives in the mixin package: handing out the stamp itself would
     *         mean exposing an internal type purely to clear a line later.
     */
    @Nullable
    public static Object onCompanionAttackLaunched(MobEntity companion, LivingEntity target) {
        Runtime runtime = RUNTIME.get(companion.getUuid());
        if (runtime == null) {
            return null;
        }
        if (runtime.engagedTarget != null && !runtime.engagedTarget.equals(target.getUuid())) {
            // An attack on something other than the fight this module is running. Drop any stamp, so the
            // unrelated swing cannot authorise a later hit on the real target, and issue none.
            MeleeAttackStamps.clear(companion.getUuid());
            return null;
        }
        return MeleeAttackStamps.recordToken(companion.getUuid(), target.getUuid(),
                runtime.progress.generation(), companion.getEntityWorld().getTime());
    }

    /**
     * Interpret an owner assignment on a projectile.
     *
     * <p>Called by BOTH projectile hooks -- the base {@code ProjectileEntity#setOwner} and the override in
     * {@code PersistentProjectileEntity}, which vanilla's arrow path reaches first. All of the state
     * machine lives in {@link ProjectileStamps#observeOwnerAssignment}, which is idempotent, so two hooks
     * firing for one arrow cannot double-establish or overwrite anything.
     *
     * <p>The generation is captured HERE, at launch, because the whole point is to know which fight the
     * projectile belonged to when it eventually lands. Reading the generation at impact would read
     * whatever fight is current then.
     *
     * @param companion  the firing companion, or null when the new owner is not one we manage
     * @param projectile the projectile entity identifier
     * @param target     the fight's target, or null to fall back to the companion's current target
     * @return whether the projectile is attributed to this companion
     */
    public static boolean onCompanionProjectileOwnerAssigned(@Nullable MobEntity companion,
                                                             Object projectileInstance,
                                                             UUID projectileUuid,
                                                             @Nullable LivingEntity target) {
        if (companion == null) {
            // A player, another mod's mob, or an unmanaged creature. Recorded as terminal so handing the
            // projectile back to a companion later cannot revive it.
            ProjectileStamps.observeOwnerAssignment(projectileInstance, projectileUuid,
                    ProjectileStamps.OwnerKind.OTHER_ENTITY, null, null, 0L, 0L);
            return false;
        }
        Runtime runtime = RUNTIME.get(companion.getUuid());
        if (runtime == null) {
            // A mob, but not one this module drives. Same treatment: not a launch by us.
            ProjectileStamps.observeOwnerAssignment(projectileInstance, projectileUuid,
                    ProjectileStamps.OwnerKind.OTHER_ENTITY, null, null, 0L, 0L);
            return false;
        }
        UUID aimedAt = target != null ? target.getUuid() : runtime.engagedTarget;
        return ProjectileStamps.observeOwnerAssignment(projectileInstance, projectileUuid,
                ProjectileStamps.OwnerKind.MANAGED_COMPANION, companion.getUuid(), aimedAt,
                runtime.progress.generation(), companion.getEntityWorld().getTime());
    }

    /**
     * Record that a projectile FIRED BY this companion landed on something.
     *
     * <p>The attribution is read from the projectile, so the generation compared here is the one captured
     * when it was fired -- not the generation that happens to be live at impact. That difference is the
     * whole reason this path exists: without it, an arrow fired during an earlier fight would renew the
     * fight that replaced it, because the victim is the same creature either way.
     *
     * @param companion  the companion that fired it
     * @param projectile the projectile that landed
     * @param victim     what it hit
     * @return whether the hit renewed the current fight
     */
    public static boolean onCompanionProjectileHit(MobEntity companion, UUID projectile,
                                                   LivingEntity victim) {
        Runtime runtime = RUNTIME.get(companion.getUuid());
        if (runtime == null) {
            return false;
        }
        ProjectileStamps.State state = ProjectileStamps.attributed(projectile);
        if (state == null) {
            // Not attributable: never recorded by us, or already invalidated. Either way it is not
            // evidence about the fight in progress.
            return false;
        }
        // One projectile authorises at most one hit, whatever the outcome. INVALIDATED, not removed, so a
        // later re-assignment cannot turn the same arrow into a fresh attribution.
        ProjectileStamps.invalidate(projectile);
        if (!projectileStampCredits(state, companion.getUuid(), victim.getUuid())) {
            return false;
        }
        // The recorded launch generation is what gets verified, and it is what the fight compares against.
        boolean renewed = runtime.progress.recordHitAtGeneration(victim.getUuid(), state.generation(),
                companion.getEntityWorld().getTime());
        if (NonCreatureAffinity.getConfig().debugLogging) {
            // Same reasoning as the melee line: without a per-landed-hit record, a ranged battle that ends
            // when the target dies cannot be told apart from one that was never credited.
            NonCreatureAffinity.LOGGER.info(
                    "[NoN Affinity] {} landed a projectile hit on {} -> progress renewed={}",
                    companion.getName().getString(), victim.getName().getString(), renewed);
        }
        return renewed;
    }

    /**
     * Mark a projectile read back from STORAGE as permanently non-attributable.
     *
     * <p>Called from the storage-read seam. Entities are serialized by UUID, so a record keyed on the
     * instance cannot survive storage; without this, a reloaded arrow would look like one that was never
     * seen and the next {@code setOwner} could attribute it to whatever fight was current then. A server
     * restart needs the same treatment on its own terms, because engagement generations start over.
     *
     * @param projectileInstance the entity instance doing the read
     * @param projectileUuid     that instance's uuid
     */
    public static void onProjectileLoadedFromStorage(Object projectileInstance, UUID projectileUuid) {
        ProjectileStamps.markLoadedFromStorage(projectileInstance, projectileUuid);
    }

    /**
     * Release a projectile's entry because its entity was removed.
     *
     * <p>Called from {@code Entity#setRemoved}, which is the ONE place every removal funnels through:
     * destroy, chunk unload ({@code ServerEntityManager} calls {@code setRemoved(UNLOADED_TO_CHUNK)}) and
     * dimension change ({@code Entity#removeFromDimension} calls {@code setRemoved(CHANGED_DIMENSION)}).
     * A hook on {@code remove} covered only the first of the three, which is what left unloaded entries
     * unreleased.
     *
     * <p>Keyed on the ENTITY INSTANCE, because a dimension change creates a new entity carrying the old
     * entity's uuid: releasing by uuid would erase the new entity's state.
     *
     * <p>One call releases the entry, so a projectile that was read and then simply destroyed is released
     * immediately -- nothing waits for a second removal.
     *
     * @param projectileInstance the entity instance being removed
     */
    public static void onProjectileInstanceRemoved(Object projectileInstance) {
        ProjectileStamps.releaseInstance(projectileInstance);
    }

    /**
     * Whether a projectile's launch attribution may be credited to this companion and victim.
     *
     * <p>Extracted, and free of Minecraft types, so the ranged attribution rule is testable without an
     * entity. Two conditions, both required:
     * <ul>
     *   <li>the projectile was fired by THIS companion -- another companion's arrow says nothing about
     *       this one's fight, and two companions must not disturb each other; and</li>
     *   <li>it hit the creature the fight was aimed at -- a stray shot that clipped something else is not
     *       progress.</li>
     * </ul>
     *
     * <p>The GENERATION is deliberately not checked here. It is checked by
     * {@link EngagementState#recordHitAtGeneration} against the generation captured at launch, which is
     * the single place that decides whether a hit belongs to the fight in progress.
     */
    static boolean projectileStampCredits(@Nullable ProjectileStamps.State state, UUID companionId,
                                          UUID victimId) {
        if (state == null || companionId == null || victimId == null) {
            return false;
        }
        return companionId.equals(state.ownerId()) && victimId.equals(state.targetId());
    }

    /**
     * The attack call that issued this token has returned.
     *
     * <p>Whatever lands after this point is not that attack. Clearing here is what keeps the stamp's
     * scope to the attack itself, and it is the fix for "a failed melee leaves a stamp behind".
     *
     * @param token the value returned by {@link #onCompanionAttackLaunched}, passed back untouched
     */
    public static void onCompanionAttackFinished(MobEntity companion, @Nullable Object token) {
        if (companion != null && token != null) {
            // Guarded by token identity inside: if a newer attempt already replaced the stamp, this
            // older call must not delete the newer one.
            MeleeAttackStamps.clearToken(companion.getUuid(), token);
        }
    }

    /**
     * This companion's engagement state, or null when it has no runtime record yet.
     *
     * <p>Exposed so the recall stage can hand the SAME state object the controller uses into
     * {@code RecallFlow}. A copy would be pointless: the suppression and returning window have to land
     * on the object the target decision reads.
     */
    @Nullable
    public static EngagementState progressOf(MobEntity mob) {
        Runtime runtime = RUNTIME.get(mob.getUuid());
        return runtime == null ? null : runtime.progress;
    }

    /**
     * This companion's per-companion runtime record, creating it when absent.
     *
     * <p>The only remaining world-dependent step in setting up a companion's state is the
     * {@code MobEntity} argument itself, and it is used for nothing but the key. Handing the new key in
     * as a {@link UUID} therefore lets a test build up exactly the production record instead of a
     * parallel structure that could drift: the state transitions that follow are the same ones the
     * tick performs.
     */
    private static Runtime runtimeFor(UUID companionId) {
        return RUNTIME.computeIfAbsent(Objects.requireNonNull(companionId), Runtime::new);
    }

    /**
     * Whether the exit that most recently cleared this companion's engagement was a stall.
     *
     * <p>Read from the state's own flag rather than a controller-side field, so it stays true when the
     * fight was ended by the stall exit AND when no Selection existed at the time.
     */
    static boolean lastExitWasStall(Runtime runtime) {
        return runtime.progress.suppressedByStall();
    }

    /**
     * Forget the engagement record after a recall.
     *
     * <p>Essential: filtering the old events is not enough, because the keep-alive branch re-validates
     * the module's own record and would put the abandoned target straight back. Clearing the record is
     * what actually ends the fight.
     *
     * <p>The engagement STATE transition is deliberately NOT done here any more. It belongs to
     * {@code RecallFlow}, which runs it on the success branch only, before this is called. Doing it in
     * both places ran the success path twice; doing it only here meant the state transition was
     * unreachable from a test. This method now owns one thing: giving up this module's target.
     */
    public static void onRecalled(MobEntity mob) {
        Runtime runtime = RUNTIME.get(mob.getUuid());
        if (runtime != null) {
            endEngagement(runtime);
        }
        TargetRegistry.releaseIfOwned(mob, TargetRegistry.TargetSource.COMPANION_ATTACK);
    }

    /**
     * End the current engagement, on every exit path.
     *
     * <p>One helper rather than repeated field resets, so a future exit path cannot forget to advance
     * the generation and leave a stale report able to renew a fight that is already over.
     */
    private static void endEngagement(Runtime runtime) {
        // Plain field resets. This method must NEVER call itself: the generated form that did was an
        // unconditional infinite recursion.
        runtime.engagedTarget = null;
        runtime.engagedSequence = 0L;
        runtime.engagementWasSelfDefence = false;
        runtime.lastDistanceToTarget = Double.MAX_VALUE;
        runtime.progress.end();
        // Any launch still in flight belonged to the fight that just ended. Leaving it in place would
        // let that hit be matched against the NEXT fight, which is the very thing the stamp exists to
        // prevent. Projectiles in the air are the same problem with a longer flight time, so they are
        // TERMINATED too -- invalidated, never deleted, because a deleted record looks like a projectile
        // that was never seen and would be re-attributed by the next setOwner.
        MeleeAttackStamps.clear(runtime.companionId);
        ProjectileStamps.markEngagementEnded(runtime.companionId);
    }

    /** Stop this module's control of a creature without touching other systems' targets. */
    private static void disengage(MobEntity mob) {
        TargetRegistry.releaseIfOwned(mob, TargetRegistry.TargetSource.COMPANION_ATTACK);
        Runtime runtime = RUNTIME.get(mob.getUuid());
        if (runtime != null) {
            // Unbinding, an ownership conflict or a stop all route through here, so the engagement
            // must be ended as well -- otherwise a stale record survives the loss of the companion.
            endEngagement(runtime);
        }
        if (runtime != null && runtime.defendingInPlace) {
            if (!CompanionMovementGoal.mustYieldForAir(mob)) {
                CompanionFollowService.releaseNavigation(mob);
            }
            runtime.defendingInPlace = false;
        }
    }

    /**
     * Eligibility for one evidence's own target, computed in the current tick.
     *
     * <p>Never cached across targets: an earlier target's range result must not decide a later one.
     */
    private static Eligibility eligibilityOf(MobEntity mob, ServerPlayerEntity owner,
                                             LivingEntity target, ServerWorld world,
                                             AffinityConfig config, Evidence evidence) {
        if (target == null) {
            return new Eligibility(false, false, true, false, false, false, false);
        }
        boolean sameWorld = target.getEntityWorld() == world;
        boolean protectedTarget = CombatEligibility.isProtectedForOwner(target, CompanionService.companionOwner(mob));
        boolean supported = CombatEligibility.hasCombatCapability(mob);
        boolean selfDefence = evidence.trigger()
                == CompanionCombatPolicy.Trigger.SELF_DEFENCE;
        // Response range is measured from the principal: the owner for owner events, the companion
        // itself for self-defence, which must also work while the owner is away.
        double anchorX = selfDefence || owner == null ? mob.getX() : owner.getX();
        double anchorY = selfDefence || owner == null ? mob.getY() : owner.getY();
        double anchorZ = selfDefence || owner == null ? mob.getZ() : owner.getZ();
        double distance = Math.sqrt(target.squaredDistanceTo(anchorX, anchorY, anchorZ));
        boolean withinResponse = distance <= config.companionResponseRange;
        boolean withinPursuit = distance <= config.companionAttackLeashRadius;
        // From where the creature stands, with its normal reach: a target merely inside the search
        // radius is not necessarily attackable.
        double stationReach = config.companionStationaryReach;
        boolean withinStationary = mob.squaredDistanceTo(target) <= stationReach * stationReach;
        return new Eligibility(target.isAlive() && !target.isSpectator(), sameWorld, protectedTarget, supported,
                withinResponse, withinPursuit, withinStationary);
    }

    private static boolean isExternallyOccupied(MobEntity mob, ServerWorld world) {
        if (mob.hasVehicle() || mob.hasPassengers() || mob.isLeashed()) {
            return true;
        }
        if (mob instanceof TameableEntity tameable && tameable.isSitting()) {
            return true;
        }
        return NonBridge.isActorBusy(world, mob);
    }

    private static ServerPlayerEntity resolveOwner(ServerWorld world, UUID ownerId) {
        var found = world.getPlayerByUuid(ownerId);
        return found instanceof ServerPlayerEntity player ? player : null;
    }

    private static LivingEntity resolveTarget(ServerWorld world, UUID targetId) {
        Entity entity = world.getEntity(targetId);
        return entity instanceof LivingEntity living ? living : null;
    }

    /** Drop runtime state for a full server stop. */
    public static void clear() {
        RUNTIME.clear();
        MeleeAttackStamps.clearAll();
        ProjectileStamps.clearAll();
    }

    public static void forget(UUID id) {
        RUNTIME.remove(id);
        MeleeAttackStamps.clear(id);
        // Terminated, not deleted: a companion is forgotten here when it is unbound or on a mode
        // handover, and its arrows can still be in the air. Deleting would make them look like
        // projectiles that were never seen.
        ProjectileStamps.markEngagementEnded(id);
    }
}

