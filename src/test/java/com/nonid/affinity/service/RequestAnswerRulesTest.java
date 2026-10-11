package com.nonid.affinity.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestAnswerRulesTest {
    @Test void movementBetweenPromptAndAnswerMustNotStartARemoteAnimation() {
        assertTrue(RequestAnswerRules.allowed(true, true, true, 36, 6));
        assertFalse(RequestAnswerRules.allowed(true, true, true, 36.01, 6));
        assertFalse(RequestAnswerRules.allowed(true, true, true, 50, 6));
    }
    @Test void unavailableActorsAndDifferentWorldsInvalidateAnAnswer() {
        assertFalse(RequestAnswerRules.allowed(false, true, true, 1, 6));
        assertFalse(RequestAnswerRules.allowed(true, false, true, 1, 6));
        assertFalse(RequestAnswerRules.allowed(true, true, false, 1, 6));
    }
    @Test void malformedGeometryCannotDisableTheDistanceLimit() {
        assertFalse(RequestAnswerRules.allowed(true, true, true, Double.NaN, 6));
        assertFalse(RequestAnswerRules.allowed(true, true, true, 1, Double.POSITIVE_INFINITY));
        assertFalse(RequestAnswerRules.allowed(true, true, true, 1, Double.NaN));
    }
    @Test void expiryBoundaryAgreesWithTheWorldTickAndHud() {
        assertFalse(RequestAnswerRules.expired(99, 100));
        assertTrue(RequestAnswerRules.expired(100, 100));
        assertTrue(RequestAnswerRules.expired(101, 100));
        assertTrue(RequestAnswerRules.expired(10, -1));
    }
}
