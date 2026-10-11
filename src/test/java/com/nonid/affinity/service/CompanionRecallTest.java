package com.nonid.affinity.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Distance boundary only. Landing safety and network synchronisation need in-game tests. */
class CompanionRecallTest {
    @Test void exactlyTenBlocksDoesNotRecall() {
        assertFalse(CompanionRecall.needed(100, 10.0));
        assertFalse(CompanionRecall.needed(9.9 * 9.9, 10.0));
    }
    @Test void greaterThanTenBlocksRecalls() {
        assertTrue(CompanionRecall.needed(10.1 * 10.1, 10.0));
        assertTrue(CompanionRecall.needed(400, 10.0));
    }
}
