package com.nonid.affinity.client;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DetectorThresholdTest {
    @AfterEach void clear() { ClientRequestState.clearDetector(); }

    @Test void zeroThresholdIsNotReplacedWithDefault() { verifyThreshold(0); }
    @Test void negativeThresholdIsNotReplacedWithDefault() { verifyThreshold(-20); }
    @Test void positiveCustomThresholdRemainsIndependentOfMaximum() { verifyThreshold(60); }

    private void verifyThreshold(int threshold) {
        UUID target = UUID.randomUUID();
        ClientRequestState.clearDetector();
        ClientRequestState.setRequestedTarget(target);
        ClientRequestState.updateDetector(target, true, 0, 200, 0, false, "", threshold, 0);
        assertEquals(threshold, ClientRequestState.serverFriendThreshold());
        assertEquals(200, ClientRequestState.serverMaxAffinity());
        ClientRequestState.updateDetector(UUID.randomUUID(), true, 0, 100, 0, false, "", 100, 0);
        assertEquals(threshold, ClientRequestState.serverFriendThreshold(), "ignore stale target replies");
    }
}
