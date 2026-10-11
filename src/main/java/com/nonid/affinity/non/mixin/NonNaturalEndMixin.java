package com.nonid.affinity.non.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.nonid.affinity.non.compat.CompletionMarkScope;
import com.nonid.affinity.service.MatingStopEvidence;
import java.util.UUID;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * Observes the two code paths in NeedsOfNature that end a mating animation NATURALLY.
 *
 * <p><b>Why an adapter into another mod at all.</b> NON's public stop event
 * ({@code NonAnimationEvents.StoppedContext}) carries the world, the instance id, the animation id, the actors
 * and the stage list -- and no reason. Inferring completion from elapsed time was tried and rejected on review:
 * a time estimate cannot separate a natural end from a late escape, and widening it to admit normal completions
 * admits escapes with them. NON does keep the reason internally
 * ({@code ServerAnimationController$StopReason}: {@code NATURAL_END}, {@code DAMAGE_STOP},
 * {@code EXTERNAL_STOP}, {@code ACTOR_UNAVAILABLE}, ...), but it is passed only to
 * {@code stopInstance(ServerWorld, UUID, boolean, StopReason)}, whose {@code StopReason} parameter type is
 * package-private. Injecting into that method by its full descriptor was attempted and **could not be matched**:
 * Mixin reported {@code Scanned 0 target(s)} for the exact runtime descriptor, in both the mapped and the
 * intermediary spelling, while the same class accepted the two injections below without complaint. Reading the
 * reason is therefore not a reliable adaptation point for this version, and this adapter does not pretend
 * otherwise.
 *
 * <p><b>What is used instead, and why it answers the same question.</b> NON ends an animation at its final
 * stage from exactly two places, and each is observed AT THE INSTRUCTION THAT ENDS IT -- never for the whole
 * duration of a call, because "some request is running" is not evidence that an animation completed:
 *
 * <ol>
 *   <li>{@code maybeAdvanceStages(...)} -- the tick-driven path. When the LAST stage's own duration is up it
 *       stops the animation INLINE (sends the stop payload and fires the stop event) without passing a reason.
 *       The injection is anchored on the mount-restore scheduling call that only that branch performs, so the
 *       "advance to the next stage" branch cannot reach it.</li>
 *   <li>{@code advanceStage(ServerWorld, UUID)} -- the explicit advance used by NON's "next stage" control
 *       (registered as a <em>debug</em> keybind: {@code key.needsofnature.animation.next_stage_debug}) and by
 *       third-party API calls. Its FINAL-stage branch stops the animation with {@code NATURAL_END}, and that is
 *       the animation's own end state, so the settlement counts it: playing a mating through to its end pays
 *       what letting it run out pays. The same method ALSO steps to an intermediate stage, and that branch is
 *       deliberately not marked -- NON fires its stage-advance event synchronously from it, and a third-party
 *       listener may stop the animation while it runs, which must not read as a completion. The injection is
 *       therefore anchored on the final-stage stop instruction itself. A live session once proved the
 *       distinction matters in the other direction too: four "next stage requested" messages were followed by a
 *       forced mating being settled as a completion and paying out. The reason that payout was wrong was the
 *       FORCED mating, not the path: this module's break-free action ends a forced mating through this very
 *       call, and marks the player's escape before it does, so an escaped instance is refused whatever the
 *       evidence says.</li>
 * </ol>
 *
 * <p>Every other stop -- damage, an external stop, an unavailable actor, a placement failure, a join
 * replacement, a world clear -- travels through the reason-carrying funnel and is therefore classified as an
 * interruption. If a future NON version completes an animation from somewhere else, the result is a refused
 * reward (safe), never an undeserved one. The reverse mistake is the one this mixin must also avoid: an
 * animation that really did reach its end through NON's own next-stage control is a completion, not an
 * interruption, and both paths are therefore recorded as completions.
 *
 * <p><b>Isolation and version limits.</b> This class is the only place that touches NON internals. It lives in
 * its own mixin configuration ({@code noncreatureaffinity.non.mixins.json}, {@code required: false}) gated by
 * {@link NonIntegrationMixinPlugin}, which disables it unless a verified NON version is loaded and reports
 * whether the members below were actually found in the class Mixin is about to transform. When it is off,
 * {@link MatingStopEvidence} records nothing, the settlement refuses to reward, and the startup log names the
 * reason -- nothing is guessed and nothing fails silently.
 *
 * <p>The injections are {@code require = 0} deliberately: this is an optional integration, and a NON release
 * that moves an internal method must not crash the game. The plugin's member check is what catches that case,
 * and it reports it at ERROR level; the consequence is the safe one (no automatic mating affinity).
 *
 * <p>Both targets are {@code static} and return a boolean, so the handlers are static and take a
 * {@code CallbackInfoReturnable}. Getting either of those wrong is rejected at prepare time, and with
 * {@code require = 0} a rejection is silent -- which is exactly why the injected bytecode was inspected rather
 * than assumed.
 */
@Mixin(targets = "com.nonid.internal.animation.server.ServerAnimationController")
public abstract class NonNaturalEndMixin {

    /**
     * The tick-driven natural completion of the final stage.
     *
     * <p>Anchored on {@code fireStopEvent(...)}, which in this branch is the LAST thing that happens before the
     * settlement runs: the stop event is dispatched synchronously from inside that call, and the settlement
     * reads this mark from its handler. Wrapping the event dispatch is therefore the only anchor whose scope
     * contains the read.
     *
     * <p><b>Why not the mount-restore call.</b> An earlier delivery wrapped
     * {@code scheduleMobMountRestores(...)} on this path, which fires EARLIER in the same branch -- and its
     * {@code finally} then cleared the mark before the event was dispatched, so a natural completion was
     * recorded and immediately dropped. The result was that an animation which ran to its end normally settled
     * to nothing (the reviewer reproduced it against the shipped jar: natural end -&gt; INTERRUPTED -&gt; NONE,
     * while the requested end still paid). The wrap has to cover the event, not a step before it.
     */
    @WrapOperation(method = "maybeAdvanceStages", at = @At(value = "INVOKE",
            target = "Lcom/nonid/internal/animation/server/ServerAnimationController;"
                    + "fireStopEvent(Ljava/util/UUID;"
                    + "Lcom/nonid/internal/animation/server/ServerAnimationController$ActiveInstance;)V"),
            require = 0)
    private static void noncreatureaffinity$recordNaturalEnd(
            UUID instanceId, @Coerce Object activeInstance,
            Operation<Void> original,
            @Local(argsOnly = true, index = 1) UUID currentInstanceId) {
        UUID id = currentInstanceId != null ? currentInstanceId : instanceId;
        MatingStopEvidence.recordNaturalEnd(id);
        CompletionMarkScope.run(id, () -> {
            original.call(instanceId, activeInstance);
            return null;
        });
    }

    /**
     * The ONE instruction in the explicit advance entry that ENDS the animation: the stop call NON makes when
     * the current stage is already the last one.
     *
     * <p>Anchored on that instruction rather than on entry to {@code advanceStage}, because the same method
     * also STEPS to the next stage, and a step is an ordinary running state: NON fires its stage-advance event
     * from it, and a third-party listener may stop the animation from there. Marking the whole call would
     * record those stops as completions. Here, only the final-stage end is marked.
     *
     * <p><b>Wrapped, not merely injected before.</b> This handler must run BEFORE the stop call, because the
     * stop call is what fires the settlement's stop event, and inside that event the mark is read. It must also
     * run AFTER it, to take the mark back if the call does not complete: the mark exists to describe a stop
     * event, and if the call throws before firing one, an unclaimed mark would sit on the instance and could be
     * read as a completion by a LATER, unrelated stop. {@code @WrapOperation} gives exactly that -- the handler
     * surrounds the original call, and the mark is cleared in a {@code finally}. A plain {@code @Inject} before
     * the call cannot express it: the mark would stay behind.
     *
     * <p><b>The reason argument is coerced.</b> NON's stop reason is a package-private enum this addon must not
     * touch, so it is received as {@code Object} and marked {@link Coerce}: without that annotation Mixin would
     * judge the handler's parameter types against the target's and reject the handler outright, which under
     * {@code require = 0} is silent. The coercion is on the type only -- the value is passed straight back to
     * the original call, so no enum constant is ever read or named here.
     *
     * <p>{@code require = 0} keeps this an optional adapter, and the descriptor plus owner are pinned so the
     * injection can only ever land on NON's own stop taking the world, the instance id, a restore flag and its
     * own stop reason. If that instruction moves in a future NON release, the plugin's anchor check reports it
     * and the consequence is the safe one -- this path stops granting affinity (never a wrongly granted
     * reward).
     */
    @WrapOperation(method = "advanceStage",
            at = @At(value = "INVOKE",
                    target = "Lcom/nonid/internal/animation/server/ServerAnimationController;"
                            + "stopInstance(Lnet/minecraft/server/world/ServerWorld;Ljava/util/UUID;Z"
                            + "Lcom/nonid/internal/animation/server/ServerAnimationController$StopReason;)Z"),
            require = 0)
    private static boolean noncreatureaffinity$recordFinalStageAdvance(
            ServerWorld world, UUID instanceId, boolean restoreTransforms, @Coerce Object stopReason,
            Operation<Boolean> original,
            @Local(argsOnly = true, index = 1) UUID currentInstanceId) {
        UUID id = currentInstanceId != null ? currentInstanceId : instanceId;
        MatingStopEvidence.recordFinalStageAdvance(id);
        return CompletionMarkScope.run(id, () -> {
            // Logged inside the scope, on every occurrence, deliberately: a handler that Mixin could not place
            // (allow-failure is on) would otherwise be invisible, and "the next-stage key grants nothing" would
            // look identical to "the integration is not installed". One line per requested end is not a flood,
            // and it is the only direct proof that this injection is live in a real session.
            org.slf4j.LoggerFactory.getLogger("noncreatureaffinity").info(
                    "[NoN Affinity] NON's advance entry is ending the FINAL stage of instance {}; it will be"
                            + " settled as a completion (the escape check still runs first)", id);
            return original.call(world, instanceId, restoreTransforms, stopReason);
        });
    }
}
