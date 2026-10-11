package com.nonid.affinity.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CompanionHighlightPaletteTest {
    @Test void relationPresenceAndInvalidCapsStayDistinct() {
        assertEquals(0xFFFFFF, CompanionHighlightPalette.color(false,0,100));
        assertEquals(0xD9CCD7, CompanionHighlightPalette.color(true,-20,100));
        assertEquals(0xD9CCD7, CompanionHighlightPalette.color(true,75,0));
        assertEquals(0xD9CCD7, CompanionHighlightPalette.color(true,0,100));
    }
    @Test void realCapControlsColorAndGradientNeverResetsAtStages() {
        assertEquals(0xF098C3, CompanionHighlightPalette.color(true,100,200));
        assertEquals(0xFF6BB2, CompanionHighlightPalette.color(true,Integer.MAX_VALUE,100));
        int previous=CompanionHighlightPalette.color(true,0,500);
        for(int affinity=1;affinity<=500;affinity++) {
            int next=CompanionHighlightPalette.color(true,affinity,500);
            assertTrue((next>>16)>=(previous>>16));
            assertTrue(((next>>8)&255)<=((previous>>8)&255));
            assertTrue((next&255)<=(previous&255));
            previous=next;
        }
    }
}
