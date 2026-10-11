package com.nonid.affinity.non.compat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DebugStartScopeTest {
    @Test void nestedScopesAndThrowsCannotLeakBypassToAutonomousStarts() {
        assertFalse(DebugStartScope.active());
        assertThrows(IllegalStateException.class, () -> DebugStartScope.run(() -> {
            assertTrue(DebugStartScope.active());
            DebugStartScope.run(() -> assertTrue(DebugStartScope.active()));
            assertTrue(DebugStartScope.active());
            throw new IllegalStateException("engine failure");
        }));
        assertFalse(DebugStartScope.active());
        DebugStartScope.run(() -> assertTrue(DebugStartScope.active()));
        assertFalse(DebugStartScope.active());
    }
}
