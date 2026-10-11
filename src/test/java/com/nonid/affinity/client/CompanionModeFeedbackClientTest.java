package com.nonid.affinity.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Contract checks for the notification lifecycle's display suppression boundary. */
class CompanionModeFeedbackClientTest {
    @Test
    void hiddenHudClearsPendingFeedbackBeforeItCanReappear() {
        assertTrue(ClientHudCleanup.displaySuppressed(true, true));
    }

    @Test
    void disabledHudClearsPendingFeedbackBeforeItCanReappear() {
        assertTrue(ClientHudCleanup.displaySuppressed(false, false));
    }
}
