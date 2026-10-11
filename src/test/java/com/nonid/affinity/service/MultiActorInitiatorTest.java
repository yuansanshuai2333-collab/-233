package com.nonid.affinity.service;
import com.nonid.affinity.non.compat.DebugStartScope;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MultiActorInitiatorTest {
    @Test void initiatorIsScopedAndRestoredAfterNestedFailure() {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        DebugStartScope.runAs(first,()->{
            assertTrue(DebugStartScope.active());assertEquals(first,DebugStartScope.initiator());
            assertThrows(IllegalStateException.class,()->DebugStartScope.runAs(second,()->{
                assertEquals(second,DebugStartScope.initiator());throw new IllegalStateException();
            }));
            assertEquals(first,DebugStartScope.initiator());
        });
        assertFalse(DebugStartScope.active());assertNull(DebugStartScope.initiator());
    }
}
