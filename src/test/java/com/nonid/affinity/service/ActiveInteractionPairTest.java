package com.nonid.affinity.service;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ActiveInteractionPairTest {
 @Test void onlyTheRunningPairQualifiesAndStopRemovesIt() {
  var sessions=new MatingSessions();var player=UUID.randomUUID();var creature=UUID.randomUUID();var id=UUID.randomUUID();
  assertNull(sessions.findActivePair(player,creature));
  sessions.open(id,player,creature,false,false,0);
  assertNotNull(sessions.findActivePair(player,creature));
  assertNull(sessions.findActivePair(UUID.randomUUID(),creature));
  assertNull(sessions.findActivePair(player,UUID.randomUUID()));
  sessions.take(id);assertNull(sessions.findActivePair(player,creature));
 }
 @Test void interruptedOrEscapedSessionDoesNotQualify() {
  var sessions=new MatingSessions();var player=UUID.randomUUID();var creature=UUID.randomUUID();var id=UUID.randomUUID();
  sessions.open(id,player,creature,true,true,0);
  sessions.markInterrupted(player,creature);assertNull(sessions.findActivePair(player,creature));
  sessions.clear();sessions.open(id,player,creature,true,true,0);
  sessions.markPlayerEscapeForInstance(id);assertNull(sessions.findActivePair(player,creature));
 }
}
