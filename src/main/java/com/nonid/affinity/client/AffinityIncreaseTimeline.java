package com.nonid.affinity.client;

/** Millisecond timeline of the approved 2.5 second feedback; never writes game state. */
public final class AffinityIncreaseTimeline {
    public static final long DURATION = 2500;
    private AffinityIncreaseTimeline() {}
    public static boolean active(long elapsed) { return elapsed >= 0 && elapsed < DURATION; }
    public static double opacity(long elapsed) {
        if (!active(elapsed)) return 0;
        return Math.min(1, Math.min(elapsed / 200.0, (DURATION - elapsed) / 500.0));
    }
    public static double ratio(int value, int maximum) {
        return maximum <= 0 ? 0 : Math.clamp(value / (double)maximum, 0, 1);
    }
    public static double shownRatio(int before, int after, int maximum, long elapsed) {
        double progress = Math.clamp((elapsed - 200) / 800.0, 0, 1);
        progress = progress * progress * (3 - 2 * progress);
        return ratio(before, maximum) + (ratio(after, maximum) - ratio(before, maximum)) * progress;
    }
}
