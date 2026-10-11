package com.nonid.affinity.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FreeTradeCompatibilityTest {
    @Test void nestedClickRestoresOuterFreeTradeBarrier() {
        FreeTradeShiftBatch.run(true, () -> {
            FreeTradeShiftBatch.noteFreeTradeSpent();
            FreeTradeShiftBatch.run(true, () -> assertFalse(FreeTradeShiftBatch.mustStopPaidContinuation(false)));
            assertTrue(FreeTradeShiftBatch.mustStopPaidContinuation(false));
        });
        assertFalse(FreeTradeShiftBatch.mustStopPaidContinuation(false));
    }
    @Test void failedClickReleasesStateAndPreservesException() {
        var failure = new IllegalStateException("third-party click failure");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> FreeTradeShiftBatch.run(true, () -> {
            FreeTradeShiftBatch.noteFreeTradeSpent();
            throw failure;
        })));
        assertFalse(FreeTradeShiftBatch.mustStopPaidContinuation(false));
    }
    @Test void failedNestedClickCannotEraseOuterState() {
        FreeTradeShiftBatch.run(true, () -> {
            FreeTradeShiftBatch.noteFreeTradeSpent();
            assertThrows(IllegalArgumentException.class, () -> FreeTradeShiftBatch.run(true, () -> {
                throw new IllegalArgumentException();
            }));
            assertTrue(FreeTradeShiftBatch.mustStopPaidContinuation(false));
        });
    }
    @Test void otherThreadCannotResetOrInheritBatch() throws Exception {
        FreeTradeShiftBatch.begin(true);
        try {
            FreeTradeShiftBatch.noteFreeTradeSpent();
            var result = new java.util.concurrent.atomic.AtomicBoolean(false);
            var thread = new Thread(() -> {
                result.set(!FreeTradeShiftBatch.mustStopPaidContinuation(false));
                FreeTradeShiftBatch.run(true, FreeTradeShiftBatch::noteFreeTradeSpent);
            });
            thread.start(); thread.join();
            assertTrue(result.get());
            assertTrue(FreeTradeShiftBatch.mustStopPaidContinuation(false));
        } finally { FreeTradeShiftBatch.end(true); }
    }
    @Test void clientInvocationStillRunsWithoutDisturbingServerFrame() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        FreeTradeShiftBatch.run(true, () -> {
            FreeTradeShiftBatch.noteFreeTradeSpent();
            FreeTradeShiftBatch.run(false, calls::incrementAndGet);
            assertTrue(FreeTradeShiftBatch.mustStopPaidContinuation(false));
        });
        assertEquals(1,calls.get());
    }
}
