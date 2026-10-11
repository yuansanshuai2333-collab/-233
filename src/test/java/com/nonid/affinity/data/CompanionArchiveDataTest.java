package com.nonid.affinity.data;

import com.nonid.affinity.net.*;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompanionArchiveDataTest {
    private final UUID a = new UUID(0, 1), b = new UUID(0, 2), mob = new UUID(0, 3);
    private final String id = "entity:" + mob;
    private CompanionRecord snapshot(int affinity) {
        var q = new CompanionInfoQuery(new UUID(0, 0), 0, mob, "minecraft:overworld");
        var info = new CompanionInfoData(q, "ready", "伙伴", "minecraft:pig", "friend", affinity, 100, 75,
                10, 20, 0, 0, "self", "custom", "follow", "", "", 0, 2, 0, 1, 1, 0);
        return new CompanionRecord(id, 10, "live", info);
    }
    private CompanionArchiveData.Death death() { return new CompanionArchiveData.Death(20, "minecraft:overworld", 1, 2, 3, "minecraft:fall"); }
    @Test void readUnknownDoesNotInitializeAnythingOrModifyStorage() {
        var data = new CompanionArchiveData(); var before = data.toNbt();
        assertNull(data.get(a, id)); assertNull(data.family(a)); assertTrue(data.ids(a).isEmpty());
        assertEquals(before, data.toNbt());
    }
    @Test void formerFriendAndFactsSurviveDropAndRoundTrip() {
        var data = new CompanionArchiveData(); data.remember(a, snapshot(75), true);
        data.event(a, id, "accepted:real-session", CompanionArchiveData.Kind.ACCEPTED, 11, "minecraft:overworld", "", -1, -1);
        data.remember(a, snapshot(0), false);
        var restored = CompanionArchiveData.fromNbt(data.toNbt()); var record = restored.get(a, id);
        assertTrue(record.wasFriend()); assertEquals(0, record.snapshot().info().affinity());
        assertEquals(1, record.eventCount()); assertFalse(record.dead());
        assertEquals("historical", record.snapshot().availability());
    }
    @Test void duplicateSuccessfulEventRemainsDeduplicatedAfterReload() {
        var data = new CompanionArchiveData(); data.remember(a, snapshot(75), true);
        assertTrue(data.event(a, id, "completed:session", CompanionArchiveData.Kind.COMPLETED, 11, "minecraft:overworld", "", -1, -1));
        var restored = CompanionArchiveData.fromNbt(data.toNbt());
        assertFalse(restored.event(a, id, "completed:session", CompanionArchiveData.Kind.COMPLETED, 12, "minecraft:overworld", "", -1, -1));
        assertEquals(1, restored.get(a, id).eventCount());
        assertFalse(restored.event(b, id, "completed:session", CompanionArchiveData.Kind.COMPLETED, 12, "minecraft:overworld", "", -1, -1));
    }
    @Test void hideOnlyOwnConfirmedDeadFriendAndRevivalRestoresItWithoutDeletingFacts() {
        var data = new CompanionArchiveData(); data.remember(a, snapshot(75), true); data.remember(b, snapshot(75), true);
        assertFalse(data.hide(a, id)); assertTrue(data.died(a, id, death())); assertTrue(data.died(b, id, death()));
        assertTrue(data.hide(a, id)); assertFalse(data.get(b, id).hidden());
        var restored = CompanionArchiveData.fromNbt(data.toNbt()); assertTrue(restored.get(a, id).hidden());
        assertFalse(restored.died(a, id, death())); assertEquals(1, restored.get(a, id).eventCount());
        assertTrue(restored.revived(a, id, 30, "minecraft:the_nether")); assertFalse(restored.get(a, id).hidden());
        assertFalse(restored.get(a, id).dead()); assertTrue(restored.get(b, id).dead());
        assertEquals(death(), restored.get(a, id).death()); assertEquals(2, restored.get(a, id).eventCount());
        assertFalse(restored.revived(a, id, 31, "minecraft:overworld"));
        assertTrue(restored.died(a, id, new CompanionArchiveData.Death(40, "minecraft:the_nether", 0, 0, 0, "")));
        assertEquals(3, restored.get(a, id).eventCount());
    }
    @Test void sameNameAndUnloadSnapshotCannotConfirmDeathOrRevival() {
        var data = new CompanionArchiveData(); data.remember(a, snapshot(75), true); data.died(a, id, death()); data.hide(a, id);
        data.remember(a, snapshot(75), true); assertTrue(data.get(a, id).dead()); assertTrue(data.get(a, id).hidden());
        assertFalse(data.revived(a, "entity:" + UUID.randomUUID(), 30, "minecraft:overworld"));
        assertTrue(data.get(a, id).dead());
    }
    @Test void historyPagingReturnsAllEventsWithoutRetentionCropping() {
        var data = new CompanionArchiveData(); data.remember(a, snapshot(75), true);
        for (int i = 0; i < 2100; i++) data.event(a, id, "rejected:" + i, CompanionArchiveData.Kind.REJECTED, i, "minecraft:overworld", "", -1, -1);
        var restored = CompanionArchiveData.fromNbt(data.toNbt()); var record = restored.get(a, id);
        int count = 0; long cursor = Long.MAX_VALUE;
        while (true) { var page = record.events(cursor, 64); if (page.isEmpty()) break; count += page.size(); cursor = page.getLast().sequence(); }
        assertEquals(2100, count); assertEquals(2100, record.eventCount());
    }
    @Test void missingHistoryStaysEmptyAndFamiliesAreViewerScoped() {
        var data = new CompanionArchiveData(); data.remember(a, snapshot(75), true);
        data.recordFamily(a).put(new FamilyGraph.Node(id, "伙伴", "minecraft:pig", 0));
        var restored = CompanionArchiveData.fromNbt(data.toNbt());
        assertEquals(0, restored.get(a, id).eventCount()); assertNull(restored.family(b)); assertNull(restored.get(b, id));
        assertTrue(CompanionArchiveData.fromNbt(new NbtCompound()).ids(a).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> restored.get(a, id).events(0, 64));
    }
    @Test void memorialCooldownSurvivesSaveWithoutBecomingInteractionHistory() {
        var data = new CompanionArchiveData(); data.remember(a, snapshot(75), true);
        assertFalse(data.reminded(a, id, 100)); data.died(a, id, death()); assertTrue(data.reminded(a, id, 100));
        var restored = CompanionArchiveData.fromNbt(data.toNbt());
        assertEquals(100, restored.get(a, id).lastMemorialTime()); assertEquals(1, restored.get(a, id).eventCount());
        assertEquals(0, restored.get(b, id) == null ? 0 : restored.get(b, id).lastMemorialTime());
    }

    @Test void eventStageSurvivesLaterAffinityAndSaveReload() {
        var data = new CompanionArchiveData(); data.remember(a, snapshot(75), true);
        data.event(a, id, "accepted:new", CompanionArchiveData.Kind.ACCEPTED, 11, "minecraft:overworld", "", -1, -1, 2);
        data.remember(a, snapshot(100), true);
        assertEquals(2, CompanionArchiveData.fromNbt(data.toNbt()).get(a, id).events(Long.MAX_VALUE, 64).getFirst().eventStage());
    }
    @Test void legacyMissingStageOnlyUsesReliableTransitionNotCurrentSnapshot() {
        var data = new CompanionArchiveData(); data.remember(a, snapshot(100), true);
        data.event(a, id, "accepted:old", CompanionArchiveData.Kind.ACCEPTED, 11, "minecraft:overworld", "", -1, -1);
        data.event(a, id, "stage:old", CompanionArchiveData.Kind.STAGE_CHANGED, 12, "minecraft:overworld", "", 2, 3);
        var n = data.toNbt(); var events=n.getCompoundOrEmpty("players").getCompoundOrEmpty(a.toString()).getCompoundOrEmpty("records").getCompoundOrEmpty(id).getCompoundOrEmpty("events");
        events.getCompoundOrEmpty("2").remove("event_stage");
        var saved=CompanionArchiveData.fromNbt(n).get(a,id).events(Long.MAX_VALUE,64);
        assertEquals(3,saved.get(0).eventStage()); assertEquals(-1,saved.get(1).eventStage());
    }
    @Test void invalidStageRejectedAndDeathRevivalStayNeutral() {
        assertThrows(IllegalArgumentException.class,()->new CompanionArchiveData.Event(1,"x",CompanionArchiveData.Kind.COMPLETED,1,"","",-1,-1,5));
        var data=new CompanionArchiveData();data.remember(a,snapshot(75),true);data.died(a,id,death());data.revived(a,id,30,"minecraft:overworld");
        assertTrue(data.get(a,id).events(Long.MAX_VALUE,64).stream().allMatch(e->e.eventStage()==-1));
    }
}
