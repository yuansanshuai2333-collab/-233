package com.nonid.affinity.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/** Valid file settings may exceed the editor's ranges; the handle must remain on its track. */
class ConfigSliderBoundsTest {
    @Test void largeCustomIntegerStaysAtTrackEnd() {
        assertEquals(1.0, AffinityConfigScreen.sliderPosition(1000, 10, 500));
        assertEquals(1.0, AffinityConfigScreen.sliderPosition(Integer.MAX_VALUE, 10, 500));
    }
    @Test void negativeCustomAffinityStaysAtTrackStart() {
        assertEquals(0.0, AffinityConfigScreen.sliderPosition(-50, 0, 200));
        assertEquals(0.0, AffinityConfigScreen.sliderPosition(Integer.MIN_VALUE, 0, 200));
    }
    @Test void customRadiusStaysAtTrackEnd() {
        assertEquals(1.0, AffinityConfigScreen.sliderPosition(96.0, 1.0, 48.0));
    }
    @Test void ordinaryValuesRetainTheirPosition() {
        assertEquals(0.5, AffinityConfigScreen.sliderPosition(255, 10, 500));
        assertEquals(0.0, AffinityConfigScreen.sliderPosition(20L, 20L, 1200L));
        assertEquals(1.0, AffinityConfigScreen.sliderPosition(1200L, 20L, 1200L));
    }
}
