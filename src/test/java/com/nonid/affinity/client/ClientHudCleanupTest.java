package com.nonid.affinity.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ClientHudCleanupTest {
    @Test
    void hiddenHudUsesTheSharedSuppressionContract() {
        assertTrue(ClientHudCleanup.displaySuppressed(true, true));
    }

    @Test
    void disabledHudUsesTheSharedSuppressionContract() {
        assertTrue(ClientHudCleanup.displaySuppressed(false, false));
    }

    @Test
    void enabledVisibleHudIsNotSuppressed() {
        assertFalse(ClientHudCleanup.displaySuppressed(false, true));
    }
}
