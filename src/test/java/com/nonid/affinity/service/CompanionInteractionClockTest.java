package com.nonid.affinity.service;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompanionInteractionClockTest {
    private final UUID player = UUID.randomUUID(), mob = UUID.randomUUID();
    @BeforeEach @AfterEach void reset() { CompanionInteractions.clearState(); }

    @Test void interleavedDimensionsDoNotRemoveSameServerTickGuard() {
        CompanionInteractions.rememberSwitch("slow_world", 20, player, mob);
        assertFalse(CompanionInteractions.alreadySwitched("fast_world", 20, player, mob));
        CompanionInteractions.rememberSwitch("fast_world", 20, player, mob);
        assertTrue(CompanionInteractions.alreadySwitched("slow_world", 20, player, mob));
        assertTrue(CompanionInteractions.alreadySwitched("fast_world", 20, player, mob));
    }

    @Test void independentPlayersAndTargetsAreNotSuppressed() {
        CompanionInteractions.rememberSwitch("world", 20, player, mob);
        assertFalse(CompanionInteractions.alreadySwitched("world", 20, UUID.randomUUID(), mob));
        assertFalse(CompanionInteractions.alreadySwitched("world", 20, player, UUID.randomUUID()));
        assertTrue(CompanionInteractions.alreadySwitched("world", 20, player, mob));
    }

    @Test void nextTickAndUncommittedAttemptsRemainUsable() {
        assertFalse(CompanionInteractions.alreadySwitched("world", 20, player, mob));
        assertFalse(CompanionInteractions.alreadySwitched("world", 20, player, mob));
        CompanionInteractions.rememberSwitch("world", 20, player, mob);
        assertFalse(CompanionInteractions.alreadySwitched("world", 21, player, mob));
    }

    @Test void counterWrapAndShutdownDoNotCarryOldGuards() {
        CompanionInteractions.rememberSwitch("world", Integer.MAX_VALUE, player, mob);
        assertFalse(CompanionInteractions.alreadySwitched("world", Integer.MIN_VALUE, player, mob));
        CompanionInteractions.rememberSwitch("world", Integer.MIN_VALUE, player, mob);
        assertTrue(CompanionInteractions.alreadySwitched("world", Integer.MIN_VALUE, player, mob));
        CompanionInteractions.clearState();
        assertFalse(CompanionInteractions.alreadySwitched("world", Integer.MIN_VALUE, player, mob));
    }
}
