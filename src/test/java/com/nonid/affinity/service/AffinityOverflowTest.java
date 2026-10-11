package com.nonid.affinity.service;

import com.nonid.affinity.data.PlayerAffinity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AffinityOverflowTest {
    @Test void largePositiveCompletionMustReachCapInsteadOfLosingAffinity() {
        PlayerAffinity relation = new PlayerAffinity(95);
        int gain = CompletionRewardRules.completionGain(5, Integer.MAX_VALUE, true);
        assertEquals(Integer.MAX_VALUE, gain);
        assertEquals(5, relation.addAffinity(gain, 0, 100));
        assertEquals(100, relation.getAffinity());
        assertTrue(CompletionRewardRules.earnsToken(gain, relation.getAffinity(), 100, true));
    }
    @Test void veryLargeLossCannotWrapAroundToMaximumAffinity() {
        PlayerAffinity relation = new PlayerAffinity(-50);
        assertEquals(-50, relation.addAffinity(Integer.MIN_VALUE, -100, 100));
        assertEquals(-100, relation.getAffinity());
    }
    @Test void ordinaryGainsAndAutonomousCompletionsKeepTheirValues() {
        assertEquals(5, CompletionRewardRules.completionGain(5, 20, false));
        PlayerAffinity relation = new PlayerAffinity(10);
        assertEquals(25, relation.addAffinity(CompletionRewardRules.completionGain(5, 20, true), 0, 100));
        assertEquals(35, relation.getAffinity());
    }
}
