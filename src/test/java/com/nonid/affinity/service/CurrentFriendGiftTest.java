package com.nonid.affinity.service;

import com.nonid.affinity.data.PlayerAffinity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CurrentFriendGiftTest {
    @Test void historicalFriendFlagDoesNotQualifyBelowCurrentThreshold() {
        PlayerAffinity relation = new PlayerAffinity(30);
        relation.setBefriended(true);
        boolean before = RelationshipRules.meetsFriendThreshold(relation.getAffinity(), 60);
        relation.addAffinity(5, 0, 200);
        assertFalse(VillagerRelationshipBenefits.qualifiesForCompletionGift(true, before, false,
                () -> RelationshipRules.meetsFriendThreshold(relation.getAffinity(), 60)));
    }
    @Test void friendshipGiftDoesNotRequireMaximumAffinity() {
        assertTrue(VillagerRelationshipBenefits.qualifiesForCompletionGift(true,
                RelationshipRules.meetsFriendThreshold(60, 60), false,
                () -> RelationshipRules.meetsFriendThreshold(65, 60)));
    }
    @Test void merelyCrossingThresholdDuringCompletionRetainsExistingNextCompletionRule() {
        assertFalse(VillagerRelationshipBenefits.qualifiesForCompletionGift(true,
                RelationshipRules.meetsFriendThreshold(55, 60), false,
                () -> RelationshipRules.meetsFriendThreshold(65, 60)));
    }
}
