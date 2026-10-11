package com.nonid.affinity.service;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/** Inspect compiled hooks without initializing Minecraft or depending on Recall Room. */
class ReplayIsolationWiringTest {
    @Test void allRewardAndPenaltyEntriesRefuseBeforeCreatingRelations() throws Exception {
        ClassNode type = read("service/AffinityService");
        for (String name : new String[]{"addAffinity", "recordMating", "recordRejection", "recordForcedEscape"}) {
            MethodNode method = type.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
            assertExitBefore(method, "protects", "relationOrCreate");
        }
    }
    @Test void replayStartIsExcludedBeforeOpeningASettlement() throws Exception {
        MethodNode hook = findHook("cancelForReplay");
        assertExitBefore(hook, "begin", "refreshParticipants");
        assertTrue(calls(hook, "cancelForReplay") >= 0);
    }
    @Test void replayStopReturnsBeforeSettlementEvenIfNaturalCompletionWasObserved() throws Exception {
        assertExitBefore(findHook("end"), "end", "settle");
    }
    @Test void replayBypassesOnlyTheRequestGate() throws Exception {
        assertExitBefore(findHook("requestNow"), "isReplay", "requestNow");
    }
    @Test void requestCleanupDoesNotChangeAffinityOrOtherPlayers() throws Exception {
        MethodNode method = read("service/RequestSystem").methods.stream()
                .filter(m -> m.name.equals("cancelForReplay")).findFirst().orElseThrow();
        assertTrue(calls(method, "isActor") >= 0);
        assertTrue(calls(method, "clearRequestState") >= 0);
        assertEquals(-1, calls(method, "addAffinity"));
        assertEquals(-1, calls(method, "recordRejection"));
    }
    private static MethodNode findHook(String marker) throws Exception {
        return read("non/NonIntegration").methods.stream().filter(m -> m.name.startsWith("lambda$"))
                .filter(m -> calls(m, marker) >= 0).findFirst().orElseThrow();
    }
    private static int calls(MethodNode method, String name) {
        for (int i = 0; i < method.instructions.size(); i++) {
            if (method.instructions.get(i) instanceof MethodInsnNode call && call.name.equals(name)) return i;
        }
        return -1;
    }
    private static void assertExitBefore(MethodNode method, String guard, String mutation) {
        int first = calls(method, guard), last = calls(method, mutation);
        assertTrue(first >= 0 && last > first, method.name + ": " + guard + " before " + mutation);
        boolean returns = false;
        for (int i = first + 1; i < last; i++) {
            int opcode = method.instructions.get(i).getOpcode();
            returns |= opcode == Opcodes.RETURN || opcode == Opcodes.IRETURN;
        }
        assertTrue(returns, "replay branch must return before side effects");
    }
    private static ClassNode read(String name) throws Exception {
        try (var input = ReplayIsolationWiringTest.class.getResourceAsStream("/com/nonid/affinity/" + name + ".class")) {
            assertNotNull(input);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
}

