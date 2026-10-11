package com.nonid.affinity.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MatingSessionLifecycleTest {
    private final Object server = new Object(), world = new Object(), oldWorld = new Object();
    private final Set<UUID> live = new HashSet<>();
    private final GroupMatingSessions sessions = new GroupMatingSessions();
    private final MatingSessionLifecycle<Object,Object> lifecycle = new MatingSessionLifecycle<>(sessions,
            (s,w) -> s == server && w == world, (w,id) -> w == world && live.contains(id));
    private final UUID player=UUID.randomUUID(), creature=UUID.randomUUID();
    private UUID running() {
        lifecycle.start(server); UUID id=UUID.randomUUID(); live.add(id);
        lifecycle.recordOurStart(world,id,player,creature,true,0); return id;
    }
    @Test void allCreationRequiresCurrentWorldAndActualInstance() {
        lifecycle.start(server); UUID id=UUID.randomUUID();
        assertFalse(lifecycle.begin(world,id)); live.add(id);
        lifecycle.recordOurStart(oldWorld,id,player,creature,false,0);
        assertTrue(sessions.runningIds().isEmpty());
        assertTrue(lifecycle.begin(world,id));
        lifecycle.recordOurStart(world,id,player,creature,true,0);
        assertTrue(sessions.findActivePair(player,creature).forced());
    }
    @Test void stopWhileEngineStillLiveRejectsReentrantStartAndStop() {
        UUID id=running(); var batch=lifecycle.take(world,id); assertEquals(1,batch.size());
        assertNull(lifecycle.take(world,id)); assertFalse(lifecycle.begin(world,id));
        lifecycle.recordOurStart(world,id,player,creature,false,1);
        lifecycle.sweep(); assertEquals(1,lifecycle.terminalCount());
        assertTrue(sessions.runningIds().isEmpty()); lifecycle.finish(id);
        lifecycle.sweep(); assertEquals(1,lifecycle.terminalCount());
    }
    @Test void engineRemovalDuringSettlementDoesNotReleasePinnedTerminal() {
        UUID id=running(); lifecycle.take(world,id); live.remove(id); lifecycle.sweep();
        assertEquals(1,lifecycle.pinnedCount()); assertTrue(sessions.isHandled(id));
        lifecycle.finish(id); lifecycle.sweep(); assertEquals(0,lifecycle.terminalCount());
        assertFalse(sessions.isHandled(id)); assertFalse(lifecycle.begin(world,id));
        assertNull(lifecycle.take(world,id));
    }
    @Test void unknownDeadStopsDoNotAccumulateButUnknownLiveStopBlocksLateStart() {
        lifecycle.start(server);
        for(int i=0;i<200_000;i++) assertNull(lifecycle.take(world,UUID.randomUUID()));
        assertEquals(0,lifecycle.terminalCount());
        UUID id=UUID.randomUUID();live.add(id);assertEquals(List.of(),lifecycle.take(world,id));
        lifecycle.finish(id); assertFalse(lifecycle.begin(world,id));
        live.remove(id);lifecycle.sweep();assertFalse(lifecycle.begin(world,id));
    }
    @Test void manyNormalEndsLeaveNoHistoryAndNewIdsStillSettle() {
        lifecycle.start(server);
        for(int i=0;i<10_000;i++) {
            UUID id=UUID.randomUUID();live.add(id);lifecycle.recordOurStart(world,id,player,creature,false,i);
            assertEquals(1,lifecycle.take(world,id).size());lifecycle.finish(id);live.remove(id);lifecycle.sweep();
        }
        assertEquals(0,lifecycle.terminalCount());assertTrue(sessions.runningIds().isEmpty());
    }
    @Test void discardAndWrongWorldPreserveBoundaries() {
        UUID id=running();assertNull(lifecycle.take(oldWorld,id));assertTrue(sessions.runningIds().contains(id));
        lifecycle.discard(world,id); assertFalse(lifecycle.begin(world,id));
        live.remove(id);lifecycle.sweep();assertEquals(0,lifecycle.terminalCount());
    }
    @Test void closeBeforeRestartRejectsOldWorldAndOldReturn() {
        UUID id=running();lifecycle.close(); assertFalse(lifecycle.begin(world,id));
        lifecycle.start(server);live.remove(id);lifecycle.recordOurStart(oldWorld,id,player,creature,false,2);
        lifecycle.recordOurStart(world,id,player,creature,false,2);assertTrue(sessions.runningIds().isEmpty());
        UUID next=UUID.randomUUID();live.add(next);assertTrue(lifecycle.begin(world,next));
    }
    @Test void membershipDepartureRemainsIneligibleAndAllPairsTakenOnce() {
        UUID id=running();UUID second=UUID.randomUUID();
        lifecycle.observe(world,id,List.of(player,second),List.of(creature),1);
        lifecycle.observe(world,id,List.of(second),List.of(creature),2);
        var batch=lifecycle.take(world,id);assertEquals(2,batch.size());
        assertTrue(batch.stream().filter(p->p.playerId().equals(player)).findFirst().orElseThrow().interrupted());
        assertNull(lifecycle.take(world,id));lifecycle.finish(id);
    }
    @Test void probeFailureNeverPretendsEngineDisappeared() {
        var broken=new MatingSessionLifecycle<Object,Object>(sessions,(s,w)->true,(w,id)->{throw new IllegalStateException("engine failure");});
        broken.start(server);assertThrows(IllegalStateException.class,()->broken.begin(world,UUID.randomUUID()));
        assertTrue(sessions.runningIds().isEmpty());
    }
}
