package com.nonid.affinity.service;

import com.nonid.affinity.service.MatingStopEvidence.Kind;

/**
 * Whether a finished mating animation may be settled as a completed mating.
 *
 * <p><b>What changed and why.</b> The previous version tried to infer completion from elapsed time, because
 * NeedsOfNature's public stop event carries no reason. That was rejected on review, correctly: a time estimate
 * cannot tell a natural end from a late escape, and widening the estimate until normal completions pass lets
 * escapes through with them. This version uses the REAL stop reason, read out of NON by the optional
 * integration adapter, and treats "we could not find out why it stopped" as a refusal -- never as a
 * completion.
 *
 * <p>So the decision is now:
 * <ol>
 *   <li>already settled, or the player broke out -&gt; nothing;</li>
 *   <li>no observed evidence at all (adapter off, unknown NON version, a path we do not hook) -&gt; nothing,
 *       and the caller reports the limitation;</li>
 *   <li>an observed reason other than NON's natural end -&gt; nothing;</li>
 *   <li>an observed completion path -&gt; settle. The bonus for a player-initiated mating applies only when THIS
 *       module started the animation <b>and it was not forced</b>: a forced animation is still "started by this
 *       module", so using that flag alone handed the bonus to a mating the player never chose.</li>
 * </ol>
 *
 * <p>Nothing here ever punishes. A refusal to settle is silent in game terms; the rejection penalty belongs to
 * the reject path alone ({@code AffinityService.recordRejection}), so a player who rejects a request and then
 * breaks out of the forced mating is charged exactly once.
 */
public final class MatingSettlementRules {

    private MatingSettlementRules() {
    }

    /** What the settlement of one finished animation should do. */
    public enum Decision {
        /** Settle nothing: no affinity, no mating count, no message. */
        NONE,
        /** The animation completed and was not started by this module's own voluntary player key. */
        REWARD_VOLUNTARY,
        /** The animation completed and the player started it voluntarily: it earns the player-initiated bonus. */
        REWARD_PLAYER_INITIATED
    }

    /**
     * Everything the decision depends on.
     *
     * @param alreadyHandled this instance id was already settled or refused
     * @param interrupted    the player broke out of it (escape evidence, independent of the reason)
     * @param evidence       the real stop reason as observed by the integration adapter
     * @param startedByMod   this module called the start API for this animation
     * @param forced         this module started it as a FORCED mating after a rejected request
     */
    public record Facts(boolean alreadyHandled, boolean interrupted, Kind evidence, boolean startedByMod,
                        boolean forced) {
    }

    /**
     * Decide the settlement of one stopped animation. Every "we cannot tell" answer is {@link Decision#NONE}.
     *
     * <p>Both observed completion paths count, and nothing else does:
     * <ul>
     *   <li>{@link Kind#NATURAL_END} -- NON's tick-driven completion of the final stage;</li>
     *   <li>{@link Kind#MANUAL_ADVANCE} -- NON's explicit advance entry ({@code advanceStage}) stopped the
     *       animation at its FINAL stage. That is the animation's own end state, reached because someone
     *       requested it: NON's "next stage" control, which is a debug keybind, or a third-party API call.
     *       A player who plays a mating through to its end with that control gets the same settlement as one
     *       who lets the last stage run out.</li>
     * </ul>
     *
     * <p>{@link Kind#MANUAL_ADVANCE} used to settle to nothing, on the reasoning that a manual advance could
     * have skipped stages that had not finished. It was refused because of ONE path that shares the advance
     * entry: this module's own break-free action, which advances a FORCED mating past its final stage to end
     * it. That path marks the session as a player escape BEFORE it advances, and the {@code interrupted}
     * guard above refuses an escaped animation outright -- whatever the stop evidence says. The guard, not
     * the refusal of manual advances, is what keeps a break-out from paying, so admitting manual advances
     * here cannot reward one.
     */
    public static Decision decide(Facts facts) {
        if (facts.alreadyHandled() || facts.interrupted()) {
            return Decision.NONE;
        }
        if (!observedCompletion(facts.evidence())) {
            // UNKNOWN (not knowing why it stopped) and INTERRUPTED (any other stop path).
            return Decision.NONE;
        }
        return playerInitiated(facts) ? Decision.REWARD_PLAYER_INITIATED : Decision.REWARD_VOLUNTARY;
    }

    /**
     * Whether the observed stop evidence says the animation reached its final stage.
     *
     * <p>Written as an ON switch over the completion members rather than a "not an interruption" test, so a
     * {@link Kind} added later is refused until someone decides what it means. Anything this method does not
     * name is not a completion.
     */
    public static boolean observedCompletion(Kind evidence) {
        return evidence == Kind.NATURAL_END || evidence == Kind.MANUAL_ADVANCE;
    }

    /**
     * Whether the player-initiated bonus applies.
     *
     * <p>Requires BOTH facts: this module started the animation, and it was a voluntary one. A forced mating is
     * started by this module too, so {@code startedByMod} alone would reward a mating the player rejected.
     */
    public static boolean playerInitiated(Facts facts) {
        return facts.startedByMod() && !facts.forced();
    }

    /**
     * The extra affinity the player loses for breaking out of a FORCED mating, as a positive magnitude.
     *
     * <p>Separate from the rejection penalty, which was already charged when the request was turned down: this
     * is the cost of having to fight the creature off, and the player asked for it explicitly. It applies only
     * when all three facts hold - the player broke out, the mating was forced on them, and the configured loss
     * is positive - so a voluntary mating that the player abandons costs nothing extra, and setting the option
     * to 0 restores the older behaviour.
     *
     * <p>This function decides the SIZE only; whether the penalty may be charged at all is decided per
     * animation instance by the session registry, which settles each instance once.
     */
    public static int escapeLoss(boolean escapedByPlayer, boolean forced, int configuredLoss) {
        if (!escapedByPlayer || !forced || configuredLoss <= 0) {
            return 0;
        }
        return configuredLoss;
    }

    /** Whether a {@link Decision} grants anything. */
    public static boolean grants(Decision decision) {
        return decision != Decision.NONE;
    }
}
