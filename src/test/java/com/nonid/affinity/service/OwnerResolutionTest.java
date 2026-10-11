package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the owner-reference crash.
 *
 * <p>{@code isTamed()} / {@code isTame()} being true does not guarantee that an owner reference
 * exists. Accessing {@code getOwnerReference().getEntityByClass(...)} directly therefore threw a
 * NullPointerException that took down the whole server world tick.
 *
 * <p>These tests call the production helpers with a missing reference, so the null path is covered
 * rather than only the happy path.
 */
class OwnerResolutionTest {

    @Test
    @DisplayName("tameCreature's owner lookup tolerates a missing reference")
    void affinityServiceHandlesMissingOwnerReference() {
        assertNull(AffinityService.resolveOwner(null, null),
                "a missing owner reference must resolve to null instead of throwing");
    }

    @Test
    @DisplayName("defender's owner lookup tolerates a missing reference")
    void friendlyDefenderHandlesMissingOwnerReference() {
        assertNull(FriendlyDefenderSystem.resolveOwner(null, null),
                "a missing owner reference must resolve to null instead of throwing");
    }
}
