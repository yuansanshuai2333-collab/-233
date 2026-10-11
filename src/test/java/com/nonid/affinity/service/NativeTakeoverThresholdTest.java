package com.nonid.affinity.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeTakeoverThresholdTest {
    @Test void defaultBoundary() {
        for (int value : new int[]{-1, 0, 1, 24})
            assertFalse(RequestSystem.reachesTakeoverThreshold(value, 25));
        for (int value : new int[]{25, 26, 100})
            assertTrue(RequestSystem.reachesTakeoverThreshold(value, 25));
    }
    @Test void configuredBoundaryAndCrossings() {
        for (int threshold : new int[]{1, 15, 40, 50}) {
            assertFalse(RequestSystem.reachesTakeoverThreshold(threshold - 1, threshold));
            assertTrue(RequestSystem.reachesTakeoverThreshold(threshold, threshold));
            assertTrue(RequestSystem.reachesTakeoverThreshold(threshold + 1, threshold));
            assertFalse(RequestSystem.reachesTakeoverThreshold(threshold - 1, threshold));
        }
    }
}
