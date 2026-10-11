package com.nonid.affinity.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What is known about why each mating animation stopped, as reported by the integration that observed the
 * actual stop.
 *
 * <p><b>Why this exists.</b> NeedsOfNature's public stop event carries no reason, so an earlier attempt
 * inferred one from elapsed time. That was rejected on review and rightly so: a time estimate cannot tell a
 * natural end from a late escape. NON does keep a reason internally
 * ({@code ServerAnimationController.StopReason}: {@code NATURAL_END}, {@code DAMAGE_STOP},
 * {@code EXTERNAL_STOP}, {@code ACTOR_UNAVAILABLE}, {@code PLACEMENT_FAILED}, ...), but the reason-carrying
 * stop method is private and its reason parameter type is package-private, so it cannot be read from here.
 * This store therefore carries something it CAN rely on: the two code paths from which NON itself ends an
 * animation at its final stage. Both are observed by the optional mixin, and each records exactly one thing --
 * "this animation reached its own end".
 *
 * <p><b>What is deliberately NOT a signal.</b> Being "inside the advance call" is not evidence of anything.
 * NON's explicit advance entry is used both to step to the NEXT stage and to end the animation at the final
 * one, and the stages in between are ordinary running states that third-party extensions may react to (NON
 * fires its stage-advance event synchronously from that same code). An animation that some other listener
 * stops while a request happens to be stepping a stage is an interruption, not a completion. Completion is
 * therefore recorded only where the final stage really ends, never for the duration of a call.
 *
 * <p>Pure Java, so the classification rules can be tested without the game or the other mod.
 */
public final class MatingStopEvidence {

    /** What is known about one animation's stop. */
    public enum Kind {
        /** The stop came from NON's TICK-DRIVEN completion of the final stage: the animation ran out on its own. */
        NATURAL_END,
        /**
         * The animation stopped at its FINAL stage because the end was REQUESTED: NON's "next stage" control
         * (the debug keybind {@code key.needsofnature.animation.next_stage_debug}) or a third-party API call to
         * the same advance entry.
         *
         * <p>NON stops such an animation with its own {@code NATURAL_END} reason, and the stop happens at the
         * animation's last stage, so this is its own end state and the settlement treats it as a completion --
         * which is exactly what a player pressing that control one stage at a time expects.
         *
         * <p>It is recorded ONLY from that final-stage branch. Stepping to an intermediate stage records
         * nothing, so an animation that a third-party listener stops in the middle of a step is classified as
         * an interruption. The one caller that ends an animation here without meaning to complete it is this
         * module's break-free action, which marks the player's escape BEFORE it advances; the settlement
         * refuses an interrupted session whatever the evidence says, which is why a break-out is not rewarded
         * through this kind.
         */
        MANUAL_ADVANCE,
        /** The adapter is active and the stop came from some other path, so the animation was interrupted. */
        INTERRUPTED,
        /** The adapter is not active (or this instance was never seen): nothing can be said. Never a
         *  completion. */
        UNKNOWN
    }

    private static final int MAX_TRACKED = 2048;

    /** The store the integration writes to and the settlement reads. */
    private static final MatingStopEvidence SHARED = new MatingStopEvidence();

    /**
     * Whether the optional adapter that observes NON's completion paths is active, and why not when it is not.
     *
     * <p>Reported once at startup. Without the adapter nothing is observed, every settlement refuses, and the
     * player must be able to find out why from the log rather than by guessing.
     */
    private static volatile String adapterStatus = "not initialised";

    /** Whether the adapter is watching the completion paths; decides "interrupted" versus "unknown". */
    private static volatile boolean adapterActive;

    /** Record the adapter's state for the startup report. */
    public static void setAdapterStatus(String status) {
        adapterStatus = status == null ? "unknown" : status;
        // Whether the adapter is watching at all decides whether an unmarked stop means "interrupted" or
        // "unknown". Conflating the two would turn "we cannot tell" into a claim about the animation.
        adapterActive = status != null && status.startsWith("enabled");
    }

    /** The adapter's state, e.g. {@code enabled} or the reason it is off. */
    public static String adapterStatus() {
        return adapterStatus;
    }

    /** Whether the observed-completion adapter is actually watching. */
    public static boolean adapterActive() {
        return adapterActive;
    }

    /** Record that the animation stopped at its FINAL stage inside NON's natural-completion path. */
    public static void recordNaturalEnd(UUID instanceId) {
        SHARED.recordNaturalEndOn(instanceId);
    }

    /**
     * Record a stop reason string, for the case where a caller can read one.
     *
     * <p>Kept public because the classification below is written to consume a reason if one ever becomes
     * readable, and because the rules are driven from tests. NO adapter calls it in production: the reason lives
     * behind a package-private parameter type, so what is observed in game is the completion PATH, and the
     * reason reported in the settlement log is therefore {@code null} for every real instance.
     */
    public static void recordReason(UUID instanceId, String reasonName) {
        SHARED.recordReasonOn(instanceId, reasonName);
    }

    /**
     * Record that the animation stopped at its FINAL stage because the end was requested through NON's explicit
     * advance entry (the "next stage" debug control, or a third-party API call).
     *
     * <p>Called from the one instruction in {@code advanceStage} that ends the final stage -- NOT on entry to
     * that method, so a request that merely steps a stage records nothing.
     */
    public static void recordFinalStageAdvance(UUID instanceId) {
        SHARED.recordFinalStageAdvanceOn(instanceId);
    }

    /** The evidence for one instance, {@link Kind#UNKNOWN} when nothing was observed. */
    public static Kind observedKind(UUID instanceId) {
        return SHARED.kindOf(instanceId);
    }

    /** The reason string NON reported for one instance, or null when none was observed (diagnostics). */
    public static String observedReasonName(UUID instanceId) {
        return SHARED.reasonNameOf(instanceId);
    }

    /** Forget one instance, once its settlement has been decided. */
    public static void forget(UUID instanceId) {
        SHARED.forgetInstance(instanceId);
    }

    /**
     * Take back a completion mark that no stop event claimed.
     *
     * <p>Used by the wrapper around the two calls that END an animation: the mark has to be set before the call
     * (the settlement reads it inside the stop event that call fires), and if the call does not return normally
     * no event arrived, so the mark must not survive to be read by a later, unrelated stop.
     *
     * <p>Narrower than {@link #forget(UUID)} on purpose: it drops the completion classification but keeps any
     * recorded reason, so this cleanup cannot erase evidence that something ELSE already established. Clearing
     * an unmarked instance is a no-op, which is what makes it safe to call after the settlement has already
     * consumed the mark.
     */
    public static void clearCompletion(UUID instanceId) {
        SHARED.clearCompletionOn(instanceId);
    }

    /** How many instances currently carry evidence (diagnostics and tests). */
    public static int observedCount() {
        return SHARED.size();
    }

    /** Drop all evidence, for a server stop or a reload. */
    public static void clearAll() {
        SHARED.clear();
    }

    private final Map<UUID, Kind> observations = new ConcurrentHashMap<>();
    private final Map<UUID, String> reasonNames = new ConcurrentHashMap<>();

    /**
     * Record the reason string NON passed to its own stop call, where a caller can read one.
     *
     * <p>No adapter currently reads it: the stop reason lives behind a package-private parameter type, so the
     * mixin observes the completion PATHS instead and this map stays empty in production. It is kept because
     * the classification below is written to consume a reason if one ever becomes readable, and because the
     * settlement logs the value -- which is therefore {@code null} for every in-game instance, not a reason
     * name.
     *
     * <p>Only the single value that means "the animation reached its end" counts as a completion; anything
     * else -- including a reason string this version has never heard of -- is an interruption. Erring that way
     * means a future NON release cannot silently make an interrupted animation pay out.
     */
    void recordReasonOn(UUID instanceId, String reasonName) {
        if (instanceId == null) {
            return;
        }
        String name = reasonName == null ? "" : reasonName;
        this.reasonNames.put(instanceId, name);
        this.observations.put(instanceId, "NATURAL_END".equals(name) ? Kind.NATURAL_END : Kind.INTERRUPTED);
        trimIfNeeded();
    }

    /** Record that the animation stopped from NON's tick-driven completion path, which passes no reason. */
    void recordNaturalEndOn(UUID instanceId) {
        if (instanceId == null) {
            return;
        }
        // An already-recorded reason wins: an explicit DAMAGE_STOP must not be overwritten by a later marker.
        this.observations.putIfAbsent(instanceId, Kind.NATURAL_END);
        trimIfNeeded();
    }

    /** Record that the explicit advance entry stopped the animation AT ITS FINAL STAGE. */
    void recordFinalStageAdvanceOn(UUID instanceId) {
        if (instanceId == null) {
            return;
        }
        // putIfAbsent for the same reason as the natural-end mark: a stop already classified from a reason is
        // never rewritten by a path marker that arrives afterwards.
        this.observations.putIfAbsent(instanceId, Kind.MANUAL_ADVANCE);
        trimIfNeeded();
    }

    /**
     * The evidence for one instance.
     *
     * <p>Only a recorded completion is a completion: the two paths above are observed where NON really ends an
     * animation at its final stage, and nothing else marks this store. When the adapter is watching and no
     * completion was recorded, the stop came from a path that cannot complete an animation, so it is an
     * interruption. When the adapter is NOT watching, nothing may be claimed: the answer stays
     * {@link Kind#UNKNOWN}.
     */
    Kind kindOf(UUID instanceId) {
        if (instanceId == null) {
            return Kind.UNKNOWN;
        }
        Kind observed = this.observations.get(instanceId);
        if (observed != null) {
            return observed;
        }
        return adapterActive ? Kind.INTERRUPTED : Kind.UNKNOWN;
    }

    /** The reason string NON reported, or null when none was observed. Diagnostics only. */
    String reasonNameOf(UUID instanceId) {
        return instanceId == null ? null : this.reasonNames.get(instanceId);
    }

    /** Forget one instance, once its settlement has been decided. */
    void forgetInstance(UUID instanceId) {
        if (instanceId == null) {
            return;
        }
        this.observations.remove(instanceId);
        this.reasonNames.remove(instanceId);
    }

    /**
     * Drop the completion classification and nothing else.
     *
     * <p>{@link #clearCompletionOn} is the only caller; see its public wrapper for why the reason map is left
     * alone.
     */
    void clearCompletionOn(UUID instanceId) {
        if (instanceId == null) {
            return;
        }
        this.observations.remove(instanceId);
    }

    int size() {
        return this.observations.size();
    }

    void clear() {
        this.observations.clear();
        this.reasonNames.clear();
    }

    private void trimIfNeeded() {
        if (this.observations.size() > MAX_TRACKED) {
            this.observations.clear();
            this.reasonNames.clear();
        }
    }
}
