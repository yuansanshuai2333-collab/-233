package com.nonid.affinity.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroupMatingSessionsTest {
    @Test void forcedEscapeLookupScansAllActiveInstancesAndExcludesMarkedOrDepartedPairs() {
        var state = new GroupMatingSessions(); var player = UUID.randomUUID(); var creature = UUID.randomUUID();
        var voluntary = UUID.randomUUID(); var forced = UUID.randomUUID();
        state.recordOurStart(voluntary, player, creature, false, 0);
        assertFalse(state.hasForcedPair(player, creature));
        state.recordOurStart(forced, player, creature, true, 0);
        assertTrue(state.hasForcedPair(player, creature));
        assertFalse(state.hasForcedPair(UUID.randomUUID(), creature));
        state.markPlayerEscape(player, creature);
        assertFalse(state.hasForcedPair(player, creature));
        var departed = UUID.randomUUID(); state.recordOurStart(departed, player, creature, true, 1);
        state.observe(departed, List.of(), List.of(creature), 2);
        assertFalse(state.hasForcedPair(player, creature));
    }
    UUID id=UUID.randomUUID(),p=UUID.randomUUID(),q=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
    GroupMatingSessions groups=new GroupMatingSessions();
    @Test void allPlayersTimesAllCreaturesOnce() {
        groups.begin(id);groups.observe(id,List.of(p,q,p),List.of(a,b,a),0);
        var result=groups.takeAll(id);assertEquals(4,result.size());assertTrue(result.stream().noneMatch(MatingSessions.Session::interrupted));
        assertTrue(groups.takeAll(id).isEmpty());groups.begin(id);groups.observe(id,List.of(p),List.of(a),1);assertTrue(groups.takeAll(id).isEmpty());
    }
    @Test void multiPlayerAndMultiCreatureEachWork() {
        groups.begin(id);groups.observe(id,List.of(p,q),List.of(a),0);assertEquals(2,groups.takeAll(id).size());
        var second=UUID.randomUUID();groups.begin(second);groups.observe(second,List.of(p),List.of(a,b),0);assertEquals(2,groups.takeAll(second).size());
    }
    @Test void joinsGainBaseEligibilityButNeverInheritExplicitBonusOrForce() {
        groups.recordOurStart(id,p,a,true,0);groups.observe(id,List.of(p,q),List.of(a,b),2);
        var result=groups.takeAll(id);assertEquals(4,result.size());
        assertEquals(1,result.stream().filter(MatingSessions.Session::startedByMod).count());
        assertEquals(1,result.stream().filter(MatingSessions.Session::forced).count());
        assertTrue(result.stream().noneMatch(MatingSessions.Session::interrupted));
    }
    @Test void departedPairStaysInterruptedAndOthersRemainEligible() {
        groups.begin(id);groups.observe(id,List.of(p,q),List.of(a,b),0);
        groups.observe(id,List.of(q),List.of(a,b),1);groups.observe(id,List.of(p,q),List.of(a,b),2);
        var result=groups.takeAll(id);assertEquals(2,result.stream().filter(MatingSessions.Session::interrupted).count());
        assertTrue(result.stream().filter(s->s.playerId().equals(q)).noneMatch(MatingSessions.Session::interrupted));
    }
    @Test void escapingPlayerDoesNotMarkOtherPlayer() {
        groups.begin(id);groups.observe(id,List.of(p,q),List.of(a,b),0);groups.markPlayerEscapeForInstance(id,p);
        var result=groups.takeAll(id);assertEquals(2,result.stream().filter(MatingSessions.Session::escapedByPlayer).count());
        assertTrue(result.stream().filter(s->s.playerId().equals(q)).noneMatch(MatingSessions.Session::interrupted));
    }
    @Test void unknownInstanceAndReplacementCannotInheritRecords() {
        groups.observe(id,List.of(p),List.of(a),0);assertTrue(groups.takeAll(id).isEmpty());
        var replacement=UUID.randomUUID();groups.begin(replacement);groups.observe(replacement,List.of(p,q),List.of(a),1);
        assertEquals(2,groups.takeAll(replacement).size());assertTrue(groups.takeAll(id).isEmpty());
    }
    @Test void normalCompletionEvidenceRemainsRequiredForEveryPair() {
        groups.begin(id);groups.observe(id,List.of(p,q),List.of(a,b),0);
        for(var session:groups.takeAll(id)) {
            var unknown=new MatingSettlementRules.Facts(false,session.interrupted(),MatingStopEvidence.Kind.UNKNOWN,session.startedByMod(),session.forced());
            assertFalse(MatingSettlementRules.grants(MatingSettlementRules.decide(unknown)));
        }
    }
}
