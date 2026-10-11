package com.nonid.affinity.non;

import org.jetbrains.annotations.Nullable;

/**
 * Why a close interaction refused to start, exposed to callers outside the {@code non} package.
 *
 * <p>The integration class itself is package-private, so its enum cannot be referenced directly. This
 * mirror keeps the reason available to the networking layer while leaving the integration surface closed,
 * and it is the single place that maps "the engine said no" onto what the player is told.
 */
public enum MatingRefusal {
    /** No animation definition matches this player and creature at all. */
    NO_ANIMATION,
    /** Definitions match, but none is startable right now (space, blocks, water, ...). */
    CONDITIONS,
    /** One of the two participants is already occupied. */
    BUSY,
    /** The animation engine threw while starting. */
    ERROR;

    /**
     * The current refusal as seen through the bridge, or null when the last attempt started (or none has
     * been made).
     *
     * <p>Read from the live bridge rather than cached, so a stale reason can never be reported for a later
     * attempt.
     */
    @Nullable
    public static MatingRefusal current() {
        NonIntegration.MatingRefusal internal = NonBridge.lastMatingRefusal();
        if (internal == null) {
            return null;
        }
        return switch (internal) {
            case NO_ANIMATION -> NO_ANIMATION;
            case CONDITIONS -> CONDITIONS;
            case BUSY -> BUSY;
            case ERROR -> ERROR;
            case NONE -> null;
        };
    }
}
