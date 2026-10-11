package com.nonid.affinity.service;

import com.nonid.affinity.config.AffinityConfig;

/** Current numeric permissions. History and ownership are deliberately not inputs. */
public final class RelationshipStages {
    public enum Stage { NEW, STRANGER, FAMILIAR, CLOSE, FRIEND }
    private RelationshipStages() {}
    public static Stage of(int value, AffinityConfig config) {
        if (value <= 0) return Stage.NEW;
        if (value >= config.friendThreshold) return Stage.FRIEND;
        if (value >= config.controlThreshold) return Stage.CLOSE;
        if (value >= config.familiarThreshold) return Stage.FAMILIAR;
        return Stage.STRANGER;
    }
    public static boolean protects(int value) { return value > 0; }
    public static boolean defends(int value, AffinityConfig config) {
        return of(value, config).ordinal() >= Stage.FAMILIAR.ordinal();
    }
    public static boolean controls(int value, AffinityConfig config) {
        return of(value, config).ordinal() >= Stage.CLOSE.ordinal();
    }
    public static boolean friend(int value, AffinityConfig config) { return of(value, config) == Stage.FRIEND; }
    public static String key(int value, AffinityConfig config) {
        return "stage.noncreatureaffinity." + of(value, config).name().toLowerCase(java.util.Locale.ROOT);
    }
}
