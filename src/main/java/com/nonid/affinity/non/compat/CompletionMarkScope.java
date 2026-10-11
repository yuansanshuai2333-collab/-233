package com.nonid.affinity.non.compat;

import com.nonid.affinity.service.MatingStopEvidence;
import java.util.UUID;

/**
 * Runs the action a completion mark describes, and guarantees the mark is gone afterwards -- whatever the
 * action does.
 *
 * <p><b>Why this is a separate, testable class.</b> The two Mixin handlers that record a completion set the
 * mark immediately before the call that ends the animation, because that call is what fires the settlement's
 * stop event and the mark has to be readable by then. The mark therefore has to be taken back:
 *
 * <ul>
 *   <li>if the call returns normally, the settlement has already read and cleared it (nothing is left, and
 *       clearing again is a no-op);</li>
 *   <li>if the call throws before firing a stop event, NOBODY read it, and leaving it behind would let a
 *       LATER, unrelated stop on the same instance read a completion that never happened.</li>
 * </ul>
 *
 * <p>The {@code finally} below is that guarantee, and it is deliberately in a plain class rather than inline in
 * the mixin: a handler's cleanup cannot be executed by a unit test (the handler only runs inside a transformed
 * game class), while this helper can be driven directly with a normal return, a failed return and a thrown
 * exception. The mixin keeps only the part that must live there -- reading the target's arguments.
 *
 * <p>Lives outside the mixin package on purpose; see {@link ServerAnimationControllerBytecode}.
 */
public final class CompletionMarkScope {

    /** The action the mark describes. A null result is fine; only throwing matters here. */
    @FunctionalInterface
    public interface Body<T> {
        T run();
    }

    private CompletionMarkScope() {
    }

    /**
     * Run {@code body}, then clear this instance's completion mark unconditionally.
     *
     * @return whatever {@code body} returned
     * @throws RuntimeException the one {@code body} threw, after the mark has been cleared
     */
    public static <T> T run(UUID instanceId, Body<T> body) {
        try {
            return body.run();
        } finally {
            MatingStopEvidence.clearCompletion(instanceId);
        }
    }
}
