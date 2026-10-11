package com.nonid.affinity.service;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FamilyLabelsTest {
    private final UUID parent = UUID.randomUUID();
    @Test void exactParentOnly() {
        assertEquals("", FamilyLabels.label(null, parent, 1));
        assertEquals("", FamilyLabels.label(parent, UUID.randomUUID(), 2));
        assertEquals("son", FamilyLabels.label(parent, parent, 1));
        assertEquals("daughter", FamilyLabels.label(parent, parent, 2));
    }
    @Test void ambiguousGenderIsNotGuessed() {
        for (int mask : new int[]{0, 3, -1, 9}) {
            assertEquals("child", FamilyLabels.label(parent, parent, mask));
        }
    }
    @Test void reliableGenerationHasGenderFallbackAndFitsExistingChannel() {
        assertEquals("grandchild", FamilyLabels.relative(2, 3));
        assertEquals("granddaughter", FamilyLabels.relative(2, 2));
        assertEquals("grandfather", FamilyLabels.relative(-2, 1));
        assertEquals("descendant", FamilyLabels.relative(2500, 1));
        assertEquals("ancestor", FamilyLabels.relative(-2500, 2));
        assertEquals("", FamilyLabels.relative(0, 1));
        for (int depth = -3; depth <= 3; depth++) for (int gender = 0; gender <= 3; gender++)
            assertTrue(FamilyLabels.relative(depth, gender).length() <= 16);
    }
}
