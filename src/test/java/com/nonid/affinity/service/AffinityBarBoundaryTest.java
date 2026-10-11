package com.nonid.affinity.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AffinityBarBoundaryTest {
    @Test void largeConfiguredAffinityCannotOverflowIntoANegativeWidth() {
        assertEquals(100, AffinityBars.filledPixels(Integer.MAX_VALUE, Integer.MAX_VALUE, 100));
        assertEquals(50, AffinityBars.filledPixels(1_000_000_000, 2_000_000_000, 100));
    }
    @Test void normalProgressKeepsItsFloorRounding() {
        assertEquals(33, AffinityBars.filledPixels(1, 3, 100));
        assertEquals(0, AffinityBars.filledPixels(-1, 100, 100));
        assertEquals(100, AffinityBars.filledPixels(150, 100, 100));
    }
    @Test void zeroAndNegativeThresholdsHaveADefinedFilledState() {
        assertEquals(100, AffinityBars.filledPixels(0, 0, 100));
        assertEquals(0, AffinityBars.filledPixels(-1, 0, 100));
        assertEquals(100, AffinityBars.filledPixels(-20, -20, 100));
        assertEquals(0, AffinityBars.filledPixels(-21, -20, 100));
    }
    @Test void geometryRemainsBoundedEvenAtIntegerLimits() {
        assertEquals(Integer.MAX_VALUE,
                AffinityBars.filledPixels(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(0, AffinityBars.filledPixels(50, 100, -1));
    }
    @Test void textualBarAtHugeAffinityIsFullInsteadOfOverflowing() {
        assertEquals("██████████", AffinityBars.bar(Integer.MAX_VALUE, 1, 10));
        assertEquals("░░░░░░░░░░", AffinityBars.bar(Integer.MIN_VALUE, 1, 10));
    }
}
