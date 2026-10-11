package com.nonid.affinity.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RequestPresentationTest {
 @Test void desireUsesActualCapAndExactBoundaries() {
  assertEquals(1,RequestPresentation.desireStage(49,200,true));
  assertEquals(2,RequestPresentation.desireStage(50,200,true));
  assertEquals(2,RequestPresentation.desireStage(99,200,true));
  assertEquals(3,RequestPresentation.desireStage(100,200,true));
  assertEquals(3,RequestPresentation.desireStage(149,200,true));
  assertEquals(4,RequestPresentation.desireStage(150,200,true));
  assertEquals(4,RequestPresentation.desireStage(250,200,true));
 }
 @Test void unknownIsDistinctFromRealZero() {
  assertEquals(0,RequestPresentation.desireStage(0,100,false));
  assertEquals(0,RequestPresentation.desireStage(20,0,true));
  assertEquals(0,RequestPresentation.desireStage(-1,100,true));
  assertEquals(1,RequestPresentation.desireStage(0,100,true));
 }
 @Test void progressUsesThresholdAndClampsOnlyDisplay() {
  assertEquals(0,RequestPresentation.progressHearts(-8,25));
  assertEquals(0,RequestPresentation.progressHearts(75,0));
  assertEquals(2,RequestPresentation.progressHearts(25,100));
  assertEquals(7,RequestPresentation.progressHearts(24,25));
  assertEquals(8,RequestPresentation.progressHearts(75,25));
 }
 @Test void exactSixteenAllowedButFurtherAndInvalidAreNot() {
  assertTrue(RequestAnswerRules.allowed(true,true,true,256,RequestPresentation.ANSWER_RANGE));
  assertFalse(RequestAnswerRules.allowed(true,true,true,256.001,RequestPresentation.ANSWER_RANGE));
  assertFalse(RequestAnswerRules.allowed(true,true,false,0,RequestPresentation.ANSWER_RANGE));
  assertFalse(RequestAnswerRules.allowed(true,true,true,Double.NaN,RequestPresentation.ANSWER_RANGE));
 }
}