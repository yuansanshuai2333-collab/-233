package com.nonid.affinity.service;

/** Birth-time values only. Existing children never pass through this rule again. */
public final class OffspringAffinityRules {
    private OffspringAffinityRules() {}
    public static int nextGeneration(int parentGeneration) {
        return parentGeneration == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, parentGeneration) + 1;
    }
    public static int initial(boolean autoFriend, int threshold, int fallback, Integer parentAffinity,
                              int percent, int min, int max) {
        int value = autoFriend ? threshold : parentAffinity == null ? fallback
                : (int) Math.round(parentAffinity * (Math.max(0, Math.min(100, percent)) / 100.0));
        return Math.max(min, Math.min(max, value));
    }
}
