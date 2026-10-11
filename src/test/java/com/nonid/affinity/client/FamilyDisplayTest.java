package com.nonid.affinity.client;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FamilyDisplayTest {
    @Test void targetSwitchAndDisconnectClearFamilyAndRejectLateReplies() {
        UUID first = UUID.randomUUID(), next = UUID.randomUUID();
        ClientRequestState.clearDetector();
        ClientRequestState.setRequestedTarget(first);
        ClientRequestState.updateFamily(first, "son");
        assertEquals("son", ClientRequestState.familyLabel());
        ClientRequestState.setRequestedTarget(next);
        ClientRequestState.updateFamily(first, "daughter");
        assertEquals("", ClientRequestState.familyLabel());
        ClientRequestState.updateFamily(next, "daughter");
        assertEquals("daughter", ClientRequestState.familyLabel());
        ClientRequestState.clearDetector();
        assertEquals("", ClientRequestState.familyLabel());
    }
}
