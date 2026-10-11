package com.nonid.affinity.client;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RelationshipStageDisplayTest {
    @AfterEach void clear() { ClientRequestState.clearDetector(); }
    @Test void missingRelationClearsOldLabelsAndAllowsFreshLabels() {
        UUID target = UUID.randomUUID();
        ClientRequestState.setRequestedTarget(target);
        ClientRequestState.updateDetector(target, true, 80, 100, 1, true, "FOLLOW", 75, 1);
        ClientRequestState.updateStage(target, "friend");
        ClientRequestState.updateFamily(target, "son");
        ClientRequestState.updateDetector(target, false, 0, 100, 0, false, "", 75, 0);
        assertEquals("", ClientRequestState.stageKey());
        assertEquals("", ClientRequestState.familyLabel());
        assertFalse(ClientRequestState.dataFound());
        ClientRequestState.updateStage(target, "new");
        assertEquals("stage.noncreatureaffinity.new", ClientRequestState.stageKey());
        ClientRequestState.updateDetector(target, true, 25, 100, 1, false, "", 75, 1);
        ClientRequestState.updateStage(target, "familiar");
        ClientRequestState.updateFamily(target, "son");
        assertEquals("stage.noncreatureaffinity.familiar", ClientRequestState.stageKey());
        assertEquals("son", ClientRequestState.familyLabel());
    }
    @Test void staleMissingReplyDoesNotClearCurrentTargetsLabels() {
        UUID target = UUID.randomUUID();
        ClientRequestState.setRequestedTarget(target);
        ClientRequestState.updateStage(target, "friend");
        ClientRequestState.updateFamily(target, "daughter");
        ClientRequestState.updateDetector(UUID.randomUUID(), false, 0, 100, 0, false, "", 75, 0);
        assertEquals("stage.noncreatureaffinity.friend", ClientRequestState.stageKey());
        assertEquals("daughter", ClientRequestState.familyLabel());
    }
    @Test void serverStageIsTargetScopedAndClearsOnTargetChange() {
        UUID one=UUID.randomUUID(), two=UUID.randomUUID();
        ClientRequestState.setRequestedTarget(one);
        ClientRequestState.updateStage(one,"friend");
        assertEquals("stage.noncreatureaffinity.friend",ClientRequestState.stageKey());
        ClientRequestState.setRequestedTarget(two);
        assertEquals("",ClientRequestState.stageKey());
        ClientRequestState.updateStage(one,"friend");
        assertEquals("",ClientRequestState.stageKey());
        ClientRequestState.updateStage(two,"familiar");
        assertEquals("stage.noncreatureaffinity.familiar",ClientRequestState.stageKey());
        ClientRequestState.clearDetector();
        assertEquals("",ClientRequestState.stageKey());
    }
}
