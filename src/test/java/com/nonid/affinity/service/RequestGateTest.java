package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.nonid.affinity.service.RequestSystem.RequestVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * When a creature is allowed to ask the player for a close interaction.
 *
 * <p>This exists because the "no animation" case is invisible in play if it is got wrong. A creature whose species
 * has no interaction for this player would raise a request, the player would accept, and nothing would happen -- which
 * looks exactly like an interaction that failed for a ground or space reason. The player is then offered a choice
 * that cannot be honoured, and has no way to tell the two situations apart.
 *
 * <p>The rule is therefore stated once, as a pure decision, and driven directly here.
 */
class RequestGateTest {

    @Test
    @DisplayName("a creature with no interaction animation for this player must not ask")
    void noAnimationMeansNoRequest() {
        assertEquals(RequestVerdict.NO_ANIMATION, RequestSystem.requestVerdict(false, true),
                "nothing can be started, so no request may be raised -- even when every other rule allows one");
        assertEquals(RequestVerdict.NO_ANIMATION, RequestSystem.requestVerdict(false, false),
                "and the reason stays 'no animation' rather than being folded into the generic refusal");
    }

    @Test
    @DisplayName("the no-animation answer is distinguishable from every other refusal")
    void noAnimationIsItsOwnAnswer() {
        // The distinction matters for how the situation is described and for how long it lasts: the other refusals
        // clear on their own, this one only changes with the content or the pair.
        assertNotEquals(RequestVerdict.REFUSED, RequestSystem.requestVerdict(false, true));
        assertNotEquals(RequestVerdict.ALLOWED, RequestSystem.requestVerdict(false, true));
    }

    @Test
    @DisplayName("having an animation is necessary but not sufficient: the other request rules still apply")
    void havingAnAnimationIsNotEnoughOnItsOwn() {
        assertEquals(RequestVerdict.ALLOWED, RequestSystem.requestVerdict(true, true));
        assertEquals(RequestVerdict.REFUSED, RequestSystem.requestVerdict(true, false),
                "a creature that is waiting, on cooldown, or whose player already has a pending request is refused "
                        + "for that reason -- not because it lacks an animation");
    }

    @Test
    @DisplayName("start eligibility is NOT the request gate: an animation blocked by the ground still permits asking")
    void blockedBySurroundingsStillPermitsAsking() {
        // The rule the parameter naming has to keep straight. A pair standing somewhere the animation cannot start
        // from still HAS the interaction, so the creature may ask and the player may accept -- the failure is then
        // explained by its real reason (space, support, wall, water). Gating requests on start eligibility instead
        // would silently forbid asking whenever the ground was awkward, which is not what was asked for and would be
        // hard to notice.
        assertEquals(RequestVerdict.ALLOWED, RequestSystem.requestVerdict(true, true),
                "the gate takes 'has any animation', never 'could start right now'");
    }
}
