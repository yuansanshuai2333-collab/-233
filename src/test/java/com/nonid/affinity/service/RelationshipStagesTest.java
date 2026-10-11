package com.nonid.affinity.service;

import com.nonid.affinity.config.AffinityConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RelationshipStagesTest {
    @Test void firstMeetingCannotAcquirePermissionsThroughANonpositiveThreshold() {
        var c = new AffinityConfig(); c.friendThreshold = -5; c.sanitize();
        assertEquals(1, c.friendThreshold);
        assertTrue(c.familiarThreshold <= c.controlThreshold && c.controlThreshold <= c.friendThreshold);
        assertFalse(RelationshipStages.defends(0,c));
        assertFalse(RelationshipStages.controls(0,c));
        assertFalse(RelationshipStages.friend(0,c));
    }
    @Test void defaultBoundariesMatchAllFiveStages() {
        var c = new AffinityConfig();
        int[] values = {-10,0,1,24,25,49,50,74,75,99,100};
        var expected = new RelationshipStages.Stage[]{
            RelationshipStages.Stage.NEW, RelationshipStages.Stage.NEW,
            RelationshipStages.Stage.STRANGER, RelationshipStages.Stage.STRANGER,
            RelationshipStages.Stage.FAMILIAR, RelationshipStages.Stage.FAMILIAR,
            RelationshipStages.Stage.CLOSE, RelationshipStages.Stage.CLOSE,
            RelationshipStages.Stage.FRIEND, RelationshipStages.Stage.FRIEND, RelationshipStages.Stage.FRIEND};
        for (int i = 0; i < values.length; i++) assertEquals(expected[i], RelationshipStages.of(values[i],c));
    }
    @Test void permissionsDropWithCurrentValueAndDoNotNeedHistoricalPromotion() {
        var c = new AffinityConfig();
        for(int value : new int[]{100,75,74,50,49,25,24,1,0}) {
            assertEquals(value > 0, RelationshipStages.protects(value));
            assertEquals(value >= 25, RelationshipStages.defends(value,c));
            assertEquals(value >= 50, RelationshipStages.controls(value,c));
            assertEquals(value >= 75, RelationshipStages.friend(value,c));
        }
    }
    @Test void friendThresholdControlsRewardIndependentlyOfCap() {
        var c = new AffinityConfig(); c.maxAffinity=200; c.friendThreshold=80;
        assertFalse(RelationshipStages.friend(79,c));
        assertTrue(RelationshipStages.friend(80,c));
        assertTrue(CompletionRewardRules.earnsToken(10,80,c.friendThreshold,true));
        assertFalse(CompletionRewardRules.earnsToken(10,80,c.friendThreshold,false));
        assertTrue(CompletionRewardRules.earnsToken(0,80,c.friendThreshold,true));
        assertEquals(RelationshipStages.key(80,c),RelationshipStages.key(200,c));
    }
    @Test void lowerBoundariesFollowExplicitConfiguration() {
        var c = new AffinityConfig(); c.familiarThreshold=10; c.controlThreshold=30; c.friendThreshold=60; c.sanitize();
        assertFalse(RelationshipStages.defends(9,c)); assertTrue(RelationshipStages.defends(10,c));
        assertFalse(RelationshipStages.controls(29,c)); assertTrue(RelationshipStages.controls(30,c));
        assertFalse(RelationshipStages.friend(59,c)); assertTrue(RelationshipStages.friend(60,c));
    }
    @Test void loweringFriendThresholdCannotLeaveControlAboveIt() {
        var c = new AffinityConfig(); c.friendThreshold=20; c.sanitize();
        assertTrue(c.familiarThreshold <= c.controlThreshold && c.controlThreshold <= c.friendThreshold);
        assertTrue(RelationshipStages.controls(20,c));
        assertFalse(RelationshipStages.protects(0));
        assertEquals(RelationshipStages.Stage.NEW,RelationshipStages.of(0,c));
    }
}
