package com.nonid.affinity.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestHudLeaseTest {
    @Test void oldCloseCannotCloseReplacementAndClosedRequestCannotReopen() {
        var lease = new RequestHudLease();
        assertTrue(lease.receive(1, true));
        assertTrue(lease.receive(2, true));
        assertFalse(lease.receive(1, false));
        assertTrue(lease.tick());
        assertTrue(lease.receive(2, false));
        assertFalse(lease.receive(2, true));
        assertFalse(lease.tick());
    }
    @Test void delayedHeartbeatRecoversFromSilenceButNotFromCancellation() {
        var lease = new RequestHudLease();
        lease.receive(4, true);
        for (int tick = 0; tick < 100; tick++) lease.tick();
        assertTrue(lease.receive(4, true));
        assertTrue(lease.tick());
        lease.receive(4, false);
        assertFalse(lease.receive(4, true));
    }
    @Test void heartbeatsKeepLeaseAliveAndSilenceExpiresIt() {
        var lease = new RequestHudLease();
        assertTrue(lease.receive(3, true));
        for (int cycle = 0; cycle < 10; cycle++) {
            for (int tick = 0; tick < 20; tick++) assertTrue(lease.tick());
            assertTrue(lease.receive(3, true));
        }
        for (int tick = 0; tick < 99; tick++) assertTrue(lease.tick());
        assertFalse(lease.tick());
    }
    @Test void disconnectAllowsFreshServerSequence() {
        var lease = new RequestHudLease();
        lease.receive(100, true);
        lease.reset();
        assertTrue(lease.receive(1, true));
        assertFalse(lease.receive(0, true));
    }
}
