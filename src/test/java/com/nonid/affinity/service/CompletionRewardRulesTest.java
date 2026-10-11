package com.nonid.affinity.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompletionRewardRulesTest {
    @Test void reachingCapAndAlreadyAtCapBothEarnOneToken() {
        assertTrue(CompletionRewardRules.earnsToken(13, 100, 100, true));
        assertTrue(CompletionRewardRules.earnsToken(10, 100, 100, true));
        assertFalse(CompletionRewardRules.earnsToken(13, 99, 100, true));
    }
    @Test void completedZeroGainStillEarnsButPeaksAndRejectionsDoNot() {
        assertFalse(CompletionRewardRules.earnsToken(5, 100, 100, false));
        assertFalse(CompletionRewardRules.earnsToken(-15, 100, 100, true));
        assertTrue(CompletionRewardRules.earnsToken(0, 100, 100, true));
    }
    @Test void zeroGainStillRequiresCompletionAndConfiguredFriendship() {
        assertFalse(CompletionRewardRules.earnsToken(0, 100, 75, false));
        assertFalse(CompletionRewardRules.earnsToken(0, 89, 90, true));
        assertTrue(CompletionRewardRules.earnsToken(0, 90, 90, true));
        assertFalse(CompletionRewardRules.earnsToken(0, 0, 0, true));
        assertEquals(0, CompletionRewardRules.completionGain(0, 0, true));
    }
}
