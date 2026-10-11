package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import com.nonid.affinity.service.InteractionStartFeedback.Reason;

class InteractionStartFeedbackTest {
    @Test void refusalFallbacksNeverInventSpecificPlacementFailure() {
        String[] refusals = {"BUSY", "NO_ANIMATION", "ERROR", "CONDITIONS", null};
        Reason[] reasons = {Reason.BUSY, Reason.NO_MATCH, Reason.ENGINE_ERROR, Reason.REQUIREMENTS, Reason.UNKNOWN};
        for (int i = 0; i < refusals.length; i++) {
            try (var attempt = InteractionStartFeedback.begin()) {
                InteractionStartFeedback.reportFallback(refusals[i]);
                assertEquals(reasons[i], attempt.reason());
            }
        }
        try (var attempt = InteractionStartFeedback.begin()) {
            InteractionStartFeedback.observeEngine(true);
            InteractionStartFeedback.engineMessage("start_cancelled_not_enough_space");
            InteractionStartFeedback.reportFallback("CONDITIONS");
            assertEquals(Reason.SPACE, attempt.reason());
        }
    }
    @Test void candidateEnumerationDoesNotContaminateSelectedStart() {
        try (var attempt = InteractionStartFeedback.begin()) {
            InteractionStartFeedback.engineMessage("start_cancelled_not_enough_space");
            assertEquals(Reason.UNKNOWN, attempt.reason());
            InteractionStartFeedback.observeEngine(true);
            InteractionStartFeedback.engineMessage("start_cancelled_no_valid_wall");
            assertEquals(Reason.WALL, attempt.reason());
        }
    }

    @Test void unknownEngineReasonDoesNotInventSpaceFailure() {
        try (var attempt = InteractionStartFeedback.begin()) {
            InteractionStartFeedback.observeEngine(true);
            InteractionStartFeedback.engineMessage("a_future_engine_reason");
            assertEquals(Reason.UNKNOWN, attempt.reason());
        }
    }

    @Test void selectedSpaceFailureIsPreservedOverGenericFallback() {
        try (var attempt = InteractionStartFeedback.begin()) {
            InteractionStartFeedback.observeEngine(true);
            InteractionStartFeedback.engineMessage("start_cancelled_not_enough_space");
            InteractionStartFeedback.report(Reason.REQUIREMENTS);
            assertEquals(Reason.SPACE, attempt.reason());
        }
    }

    @Test void exceptionAndNextAttemptCannotReusePreviousFailure() {
        try {
            try (var attempt = InteractionStartFeedback.begin()) {
                InteractionStartFeedback.report(Reason.BUSY);
                throw new IllegalStateException("test");
            }
        } catch (IllegalStateException expected) { }
        try (var attempt = InteractionStartFeedback.begin()) {
            assertEquals(Reason.UNKNOWN, attempt.reason());
        }
    }

    @Test void nestedAttemptRestoresOuterObservation() {
        try (var outer = InteractionStartFeedback.begin()) {
            InteractionStartFeedback.observeEngine(true);
            try (var inner = InteractionStartFeedback.begin()) {
                InteractionStartFeedback.report(Reason.BUSY);
                assertEquals(Reason.BUSY, inner.reason());
            }
            InteractionStartFeedback.engineMessage("start_cancelled_not_enough_space");
            assertEquals(Reason.SPACE, outer.reason());
        }
    }
}
