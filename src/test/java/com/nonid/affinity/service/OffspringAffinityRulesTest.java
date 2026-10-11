package com.nonid.affinity.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OffspringAffinityRulesTest {
    @Test void generationAdvancesWithoutWrappingAndNormalizesInvalidParents() {
        assertEquals(1, OffspringAffinityRules.nextGeneration(0));
        assertEquals(8, OffspringAffinityRules.nextGeneration(7));
        assertEquals(Integer.MAX_VALUE, OffspringAffinityRules.nextGeneration(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, OffspringAffinityRules.nextGeneration(Integer.MAX_VALUE - 1));
        assertEquals(1, OffspringAffinityRules.nextGeneration(Integer.MIN_VALUE));
    }
    @Test void autoFriendUsesConfiguredThresholdRatherThanCapOrParentsMaximum() {
        assertEquals(60, OffspringAffinityRules.initial(true, 60, 5, 200, 100, 0, 200));
        assertEquals(35, OffspringAffinityRules.initial(true, 35, 5, null, 100, 0, 200));
    }
    @Test void disablingAutoFriendDoesNotInventAFatherForPlayerBirth() {
        assertEquals(5, OffspringAffinityRules.initial(false, 60, 5, null, 100, 0, 200));
    }
    @Test void knownReferenceParentUsesPercentageWhenAutomaticFriendshipIsOff() {
        assertEquals(50, OffspringAffinityRules.initial(false, 60, 5, 200, 25, 0, 200));
        assertEquals(0, OffspringAffinityRules.initial(false, 60, 5, 200, 0, 0, 200));
    }
    @Test void editedBoundsAreRespected() {
        assertEquals(40, OffspringAffinityRules.initial(true, 60, 5, null, 100, 0, 40));
        assertEquals(10, OffspringAffinityRules.initial(false, 60, 5, null, 100, 10, 200));
    }
}
