package com.nonid.affinity.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MemorialVisitsTest {
    @Test void stayDoesNotRepeatAndEquipmentToggleDoesNotCreateNewVisit() {
        var visits = new MemorialVisits(); var near = Map.of("dead", 0L);
        assertEquals(List.of("dead"), visits.update(near, true, 100));
        assertTrue(visits.update(Map.of("dead", 100L), false, 200).isEmpty());
        assertTrue(visits.update(Map.of("dead", 100L), true, 400_000).isEmpty());
    }
    @Test void reentryRequiresFiveMinutesAndWaitingInsideCannotBypassIt() {
        var visits = new MemorialVisits(); visits.update(Map.of("dead", 0L), true, 100);
        visits.update(Map.of(), true, 200);
        assertTrue(visits.update(Map.of("dead", 100L), true, 299_999).isEmpty());
        assertTrue(visits.update(Map.of("dead", 100L), true, 500_000).isEmpty());
        visits.update(Map.of(), true, 500_001);
        assertEquals(List.of("dead"), visits.update(Map.of("dead", 100L), true, 500_002));
    }
    @Test void holdingDetectorAfterEnteringCanOfferFirstNoticeOnlyOnce() {
        var visits = new MemorialVisits(); assertTrue(visits.update(Map.of("dead", 0L), false, 100).isEmpty());
        assertEquals(List.of("dead"), visits.update(Map.of("dead", 0L), true, 200));
        assertTrue(visits.update(Map.of("dead", 0L), true, 201).isEmpty());
    }
    @Test void clockRollbackCannotProduceRepeatAndDifferentTargetsAreIndependent() {
        var visits = new MemorialVisits(); assertTrue(visits.update(Map.of("dead", 1000L), true, 500).isEmpty());
        assertEquals(List.of("other"), visits.update(Map.of("dead", 1000L, "other", 0L), true, 501));
        visits.update(Map.of(), true, 1001);
        assertEquals(List.of("dead", "other"), visits.update(Map.of("dead", 1000L, "other", 0L), true, 301000));
    }
}
