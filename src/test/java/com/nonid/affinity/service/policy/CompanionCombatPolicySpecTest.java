package com.nonid.affinity.service.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Runs the handover assertion harness from the test task.
 *
 * <p>Deliberately a single JUnit case wrapping 70 handover assertions, rather than 70 JUnit cases.
 * The handover README requires the assertion count to stay separate from this project's own cases:
 * they verify the pure policy only, and say nothing about game integration. Splitting them would
 * also invite the two counts to be added together, which the README forbids.
 */
class CompanionCombatPolicySpecTest {

    @Test
    @DisplayName("handover policy harness: 70 assertions (pure policy only)")
    void handoverAssertions() {
        // Throws AssertionError on the first mismatch, so a failure is reported by name.
        CompanionCombatPolicyAssertions.main(new String[0]);
    }
}
