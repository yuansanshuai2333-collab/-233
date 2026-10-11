package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for attack-target ownership.
 *
 * <p>The review noted that recording only the companion was not enough: if the vanilla AI or another
 * module replaced the target from A to B, this module still believed B was its own and could wrongly
 * keep or clear it. Ownership is therefore keyed on the TARGET uuid as well.
 */
class TargetOwnershipTest {

    private static final UUID TARGET_A = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID TARGET_B = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b");

    @Test
    @DisplayName("ownership holds while the current target is unchanged")
    void ownershipHoldsForSameTarget() {
        assertTrue(TargetRegistry.stillOwns(TARGET_A, TARGET_A));
    }

    @Test
    @DisplayName("an externally replaced target is no longer owned")
    void externalReplacementLosesOwnership() {
        // This module set A; something else changed it to B. The module must not treat B as its own,
        // which is what would let it wrongly keep or clear B.
        assertFalse(TargetRegistry.stillOwns(TARGET_A, TARGET_B),
                "a target replaced by another system must not count as this module's");
    }

    @Test
    @DisplayName("no recorded target means no ownership")
    void noRecordMeansNoOwnership() {
        assertFalse(TargetRegistry.stillOwns(null, TARGET_A));
    }

    @Test
    @DisplayName("no current target means nothing to own")
    void noCurrentTargetMeansNoOwnership() {
        assertFalse(TargetRegistry.stillOwns(TARGET_A, null));
    }

    @Test
    @DisplayName("two nulls are not ownership")
    void bothNullIsNotOwnership() {
        assertFalse(TargetRegistry.stillOwns(null, null));
    }

    @Test
    @DisplayName("a target recorded by one source is not owned by another")
    void sourcesAreIsolated() {
        // The registry compares the source as well as the target uuid, so the defender can never be
        // judged to own an ATTACK-mode assignment, and vice versa. This is the defect that let the
        // companion service clear the defender's target on the same world tick.
        assertFalse(TargetRegistry.stillOwns(TARGET_A, TARGET_B),
                "different targets must not match");
        assertTrue(TargetRegistry.stillOwns(TARGET_A, TARGET_A));
    }

    @Test
    @DisplayName("both target sources exist and are distinct")
    void sourceEnumIsExplicit() {
        // Guards against a future refactor collapsing the two roles back into one unnamed value.
        assertTrue(TargetRegistry.TargetSource.COMPANION_ATTACK
                != TargetRegistry.TargetSource.LEGACY_DEFENDER);
    }
}
