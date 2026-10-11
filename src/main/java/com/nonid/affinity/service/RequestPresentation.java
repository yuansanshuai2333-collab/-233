package com.nonid.affinity.service;

/** Display rules independent of request eligibility, costs and cooldowns. */
public final class RequestPresentation {
    public static final double ANSWER_RANGE = 16.0;
    private RequestPresentation() {}
    public static int desireStage(int energy, int maximum, boolean initialized) {
        if (!initialized || maximum <= 0 || energy < 0) return 0;
        double ratio = (double) energy / maximum;
        return ratio < .25 ? 1 : ratio < .5 ? 2 : ratio < .75 ? 3 : 4;
    }
    public static double progress(int affinity, int threshold) {
        return threshold <= 0 ? 0 : Math.clamp((double) affinity / threshold, 0, 1);
    }
    public static int progressHearts(int affinity, int threshold) {
        return (int) Math.floor(progress(affinity, threshold) * 8);
    }
}
