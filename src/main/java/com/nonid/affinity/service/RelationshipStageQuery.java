package com.nonid.affinity.service;

import com.nonid.affinity.config.AffinityConfig;
import com.nonid.affinity.data.PlayerAffinity;
import org.jetbrains.annotations.Nullable;

/** Read-only adapter for display paths; it never creates or changes a relation. */
public final class RelationshipStageQuery {
    private RelationshipStageQuery() {}

    public static int affinityOrZero(@Nullable PlayerAffinity relation) {
        return relation == null ? 0 : relation.getAffinity();
    }

    public static RelationshipStages.Stage stage(@Nullable PlayerAffinity relation, AffinityConfig config) {
        return RelationshipStages.of(affinityOrZero(relation), config);
    }

    public static boolean friend(@Nullable PlayerAffinity relation, AffinityConfig config) {
        return stage(relation, config) == RelationshipStages.Stage.FRIEND;
    }

    public static String key(@Nullable PlayerAffinity relation, AffinityConfig config) {
        return "stage.noncreatureaffinity." + stage(relation, config).name().toLowerCase(java.util.Locale.ROOT);
    }
}
