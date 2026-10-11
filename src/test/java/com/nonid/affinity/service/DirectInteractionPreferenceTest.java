package com.nonid.affinity.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DirectInteractionPreferenceTest {
    @Test void optInIsRequiredEvenAtHighAffinity() {
        assertFalse(DirectInteractionPreference.permits(false,100));
        assertTrue(DirectInteractionPreference.permits(true,1));
    }
    @Test void zeroAndNegativeDoNotQualify() {
        assertFalse(DirectInteractionPreference.permits(true,0));
        assertFalse(DirectInteractionPreference.permits(true,-1));
    }
}
