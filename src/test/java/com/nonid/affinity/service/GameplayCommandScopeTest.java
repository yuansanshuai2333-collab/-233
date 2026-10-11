package com.nonid.affinity.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GameplayCommandScopeTest {
 @Test void nestedScopeAndExceptionAlwaysRestoreCommandOrigin(){
  assertFalse(GameplayFeedback.commandActive());
  try(var outer=GameplayFeedback.command()){
   assertTrue(GameplayFeedback.commandActive());
   assertThrows(IllegalStateException.class,()->{try(var inner=GameplayFeedback.command()){throw new IllegalStateException("QA");}});
   assertTrue(GameplayFeedback.commandActive());
  }
  assertFalse(GameplayFeedback.commandActive());
 }
}
