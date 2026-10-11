package com.nonid.affinity.client;
import com.nonid.affinity.net.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GiftViewStateTest {
 private GiftViewPayload.Row row(UUID id){return new GiftViewPayload.Row(id,new UUID(32,1),false,0,1,2,"真实伙伴","minecraft:pig",2,1);}
 private GiftViewPayload reply(GiftQueryPayload q,List<GiftViewPayload.Row> rows){return new GiftViewPayload(q.session(),q.sequence(),"minecraft:overworld",rows);}
 @Test void oldBeforeNewAndDuplicatesCannotReplaceTheCurrentSnapshot(){
  var state=new GiftViewState();state.reset("minecraft:overworld");var old=state.query();var fresh=state.query();var id=UUID.randomUUID();
  assertFalse(state.accept(reply(old,List.of(row(UUID.randomUUID())))));assertTrue(state.accept(reply(fresh,List.of(row(id)))));
  assertFalse(state.accept(reply(old,List.of())));assertFalse(state.accept(reply(fresh,List.of())));assertEquals(id,state.rows().getFirst().gift());
 }
 @Test void reconnectWithSameSequenceAndDimensionCannotAdoptOldSession(){
  var state=new GiftViewState();state.reset("minecraft:overworld");var old=state.query();state.reset("minecraft:overworld");var fresh=state.query();assertEquals(old.sequence(),fresh.sequence());assertFalse(state.accept(reply(old,List.of(row(UUID.randomUUID())))));assertTrue(state.accept(reply(fresh,List.of())));
 }
 @Test void dimensionMismatchDoesNotConsumeThePendingRequest(){
  var state=new GiftViewState();state.reset("minecraft:overworld");var q=state.query();assertFalse(state.accept(new GiftViewPayload(q.session(),q.sequence(),"minecraft:the_nether",List.of())));assertTrue(state.accept(reply(q,List.of())));
 }
 @Test void actualClaimSuccessCannotReappearFromAnEarlierInFlightSnapshot(){
  var state=new GiftViewState();state.reset("minecraft:overworld");var q=state.query();var id=UUID.randomUUID();assertTrue(state.accept(reply(q,List.of(row(id)))));q=state.query();
  assertFalse(state.settled(new GiftResultPayload(state.session(),id,"spawn_failed")));assertEquals(1,state.rows().size());
  assertTrue(state.settled(new GiftResultPayload(state.session(),id,"claimed")));assertTrue(state.rows().isEmpty());assertTrue(state.accept(reply(q,List.of(row(id)))));assertTrue(state.rows().isEmpty());
 }
 @Test void expiryAndWrongSessionNeverPretendAnItemWasDelivered(){
  var state=new GiftViewState();state.reset("minecraft:overworld");var id=UUID.randomUUID();assertTrue(state.accept(reply(state.query(),List.of(row(id)))));assertFalse(state.settled(new GiftResultPayload(UUID.randomUUID(),id,"claimed")));assertEquals(1,state.rows().size());state.expire();assertTrue(state.rows().isEmpty());
 }
}
