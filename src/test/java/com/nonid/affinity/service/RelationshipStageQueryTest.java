package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.*;

import com.nonid.affinity.config.AffinityConfig;
import com.nonid.affinity.data.PlayerAffinity;
import org.junit.jupiter.api.Test;

class RelationshipStageQueryTest {
    @Test
    void missingRelationKeepsNullAsZeroAndNewStage() {
        var config = new AffinityConfig();
        assertEquals(0, RelationshipStageQuery.affinityOrZero(null));
        assertEquals(RelationshipStages.Stage.NEW, RelationshipStageQuery.stage(null, config));
        assertFalse(RelationshipStageQuery.friend(null, config));
        assertEquals("stage.noncreatureaffinity.new", RelationshipStageQuery.key(null, config));
    }

    @Test
    void relationUsesExistingConfiguredFriendBoundary() {
        var config = new AffinityConfig();
        var relation = new PlayerAffinity(config.friendThreshold);
        assertEquals(config.friendThreshold, RelationshipStageQuery.affinityOrZero(relation));
        assertEquals(RelationshipStages.Stage.FRIEND, RelationshipStageQuery.stage(relation, config));
        assertTrue(RelationshipStageQuery.friend(relation, config));
    }
}
