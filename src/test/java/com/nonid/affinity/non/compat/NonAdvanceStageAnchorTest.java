package com.nonid.affinity.non.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.nonid.affinity.service.MatingStopEvidence;
import com.nonid.affinity.service.MatingStopEvidence.Kind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The adapter's two Mixin anchors, checked against NON's REAL compiled class -- and against lookalikes that
 * must be refused.
 *
 * <p>Written because of what a wrong anchor costs. The injections use {@code require = 0} so a NeedsOfNature
 * update cannot break a player's game, and that same flag makes a missing anchor SILENT: the next-stage key
 * would stop granting affinity, which is exactly the defect the adapter exists to fix, with nothing in the log
 * to say so. The plugin's anchor check closes that hole, and these tests keep the check honest -- including the
 * cases where a NAIVE check would wrongly report success.
 *
 * <p>The class is read with ASM only: nothing is loaded, initialised or resolved, so no Minecraft or
 * NeedsOfNature class enters the test JVM.
 */
class NonAdvanceStageAnchorTest {
    @Test void namedDevelopmentNamespaceContainsTheSameAnchors() throws Exception {
        ClassNode controller = readControllerFromJar();
        for (MethodNode method : controller.methods) {
            method.desc = method.desc.replace("Lnet/minecraft/class_3218;", "Lnet/minecraft/server/world/ServerWorld;");
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    call.desc = call.desc.replace("Lnet/minecraft/class_3218;", "Lnet/minecraft/server/world/ServerWorld;");
                }
            }
        }
        assertTrue(ServerAnimationControllerBytecode.hasAdvanceMethod(controller));
        assertTrue(ServerAnimationControllerBytecode.containsStopInstanceInvocation(
                ServerAnimationControllerBytecode.advanceMethod(controller)));
        assertTrue(ServerAnimationControllerBytecode.hasNaturalCompletionAnchor(controller));
    }

    private static final String NON_JAR = "libs/needsofnature-1.5.11.21.11.jar";
    private static final String ANOTHER_OWNER = "com/example/other/SomeOtherController";

    private static ClassNode readControllerFromJar() throws Exception {
        Path jar = Path.of(NON_JAR);
        assumeTrue(Files.isRegularFile(jar), "NeedsOfNature jar not present: " + NON_JAR);
        try (java.util.jar.JarFile file = new java.util.jar.JarFile(jar.toFile())) {
            java.util.jar.JarEntry entry =
                    file.getJarEntry(ServerAnimationControllerBytecode.CONTROLLER_OWNER + ".class");
            assumeTrue(entry != null, "controller class not found in " + NON_JAR);
            ClassNode node = new ClassNode();
            new ClassReader(file.getInputStream(entry)).accept(node, 0);
            return node;
        }
    }

    private static ClassNode syntheticController(MethodNode... methods) {
        ClassNode node = new ClassNode();
        for (MethodNode method : methods) {
            node.methods.add(method);
        }
        return node;
    }

    private static MethodNode method(String name, String descriptor) {
        return new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, descriptor, null, null);
    }

    // ------------------------------------------------------------------
    // The real class: both anchors must be found
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the real NON class contains BOTH anchors, with the signatures and argument layout we rely on")
    void theRealControllerContainsBothAnchors() throws Exception {
        ClassNode controller = readControllerFromJar();

        assertTrue(ServerAnimationControllerBytecode.hasNaturalCompletionMethod(controller),
                ServerAnimationControllerBytecode.NATURAL_COMPLETION_METHOD + "(...) must exist: it is the"
                        + " tick-driven completion path");
        assertTrue(ServerAnimationControllerBytecode.hasInstanceIdAtExpectedArgumentIndex(controller),
                "argument " + ServerAnimationControllerBytecode.INSTANCE_ID_ARGUMENT_INDEX + " of that method"
                        + " must still be the instance id: the handler reads it with"
                        + " @Local(argsOnly = true, index = 1), so a changed layout would fail silently");
        MethodNode natural = ServerAnimationControllerBytecode.findMethod(controller,
                ServerAnimationControllerBytecode.NATURAL_COMPLETION_METHOD);
        assertNotNull(natural);
        assertEquals(ServerAnimationControllerBytecode.NATURAL_COMPLETION_METHOD_DESCRIPTOR, natural.desc,
                "the recorded signature of the completion path must match the real class (update the constant"
                        + " and the handler's @Local index together if NON changes it)");
        assertTrue(ServerAnimationControllerBytecode.hasNaturalCompletionAnchor(controller),
                "that branch must still dispatch its stop event through " + ServerAnimationControllerBytecode
                        .STOP_EVENT_METHOD + ServerAnimationControllerBytecode
                        .NATURAL_COMPLETION_ANCHOR_DESCRIPTOR
                        + ": it is the LAST step of the branch before the settlement runs, and the first"
                        + " injection wraps it. An earlier anchor in the same branch would clear the mark before"
                        + " the event could read it");

        assertTrue(ServerAnimationControllerBytecode.hasAdvanceMethod(controller),
                ServerAnimationControllerBytecode.ADVANCE_METHOD
                        + ServerAnimationControllerBytecode.ADVANCE_METHOD_DESCRIPTOR
                        + " must exist: it is the entry the next-stage debug control uses, and the mixin"
                        + " targets that exact signature");
        MethodNode advance = ServerAnimationControllerBytecode.advanceMethod(controller);
        assertNotNull(advance);
        assertTrue(ServerAnimationControllerBytecode.containsStopInstanceInvocation(advance),
                "advanceStage(...) must call NON's own stopInstance with descriptor "
                        + ServerAnimationControllerBytecode.FINAL_STAGE_STOP_DESCRIPTOR
                        + "; without it the adapter cannot tell a requested END from a requested STEP, and the"
                        + " next-stage key would grant nothing again");
    }

    // ------------------------------------------------------------------
    // Lookalikes that a naive check would accept
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the advance anchor refuses a call on ANOTHER class, the wrong overload, and a missing method")
    void theAdvanceAnchorRefusesLookalikes() {
        // Same name, same descriptor, DIFFERENT owning class. The injection target names the owner, so a call
        // here would not be injected at all -- a check that only compared name and descriptor would report a
        // healthy anchor and be wrong.
        MethodNode wrongOwner = method("advanceStage",
                ServerAnimationControllerBytecode.ADVANCE_METHOD_DESCRIPTOR);
        InsnList wrongOwnerBody = new InsnList();
        wrongOwnerBody.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ANOTHER_OWNER, "stopInstance",
                ServerAnimationControllerBytecode.FINAL_STAGE_STOP_DESCRIPTOR, false));
        wrongOwner.instructions = wrongOwnerBody;
        assertFalse(ServerAnimationControllerBytecode.containsStopInstanceInvocation(wrongOwner),
                "the owning class is part of the anchor: a lookalike elsewhere is not injected");

        // The right owner and name with the REASON-LESS overload: harmless in NON, but not the end of an
        // animation, and not what the injection anchors on.
        MethodNode wrongOverload = method("advanceStage",
                ServerAnimationControllerBytecode.ADVANCE_METHOD_DESCRIPTOR);
        InsnList wrongOverloadBody = new InsnList();
        wrongOverloadBody.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                ServerAnimationControllerBytecode.CONTROLLER_OWNER, "stopInstance",
                "(Lnet/minecraft/class_3218;Ljava/util/UUID;Z)Z", false));
        wrongOverload.instructions = wrongOverloadBody;
        assertFalse(ServerAnimationControllerBytecode.containsStopInstanceInvocation(wrongOverload),
                "only the reason-carrying, final-stage stop may match");

        // The plain step call must never match either: stepping is an ordinary running state.
        MethodNode stepOnly = method("advanceStage",
                ServerAnimationControllerBytecode.ADVANCE_METHOD_DESCRIPTOR);
        InsnList stepBody = new InsnList();
        stepBody.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                ServerAnimationControllerBytecode.CONTROLLER_OWNER, "advanceToStageIndex",
                "(Lnet/minecraft/class_3218;Ljava/util/UUID;"
                        + "Lcom/nonid/internal/animation/server/ServerAnimationController$ActiveInstance;I)Z",
                false));
        stepOnly.instructions = stepBody;
        assertFalse(ServerAnimationControllerBytecode.containsStopInstanceInvocation(stepOnly),
                "a stage step is not a completion");

        // The pinned descriptor itself must still match the shape it exists to find.
        MethodNode real = method("advanceStage", ServerAnimationControllerBytecode.ADVANCE_METHOD_DESCRIPTOR);
        InsnList realBody = new InsnList();
        realBody.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                ServerAnimationControllerBytecode.CONTROLLER_OWNER, "stopInstance",
                ServerAnimationControllerBytecode.FINAL_STAGE_STOP_DESCRIPTOR, false));
        real.instructions = realBody;
        assertTrue(ServerAnimationControllerBytecode.containsStopInstanceInvocation(real),
                "the pinned descriptor must match the call shape it exists to find");

        // A null or body-less method is refused rather than throwing: this runs during mixin preparation,
        // where an exception would abort the adapter for the whole session.
        assertFalse(ServerAnimationControllerBytecode.containsStopInstanceInvocation(null),
                "a missing method is not a match");
    }

    @Test
    @DisplayName("the natural-completion anchor refuses a lost call, and refuses a changed argument layout")
    void theNaturalCompletionAnchorRefusesAMissingCallOrALayoutChange() {
        // A future NON could keep the method name and move the stop-event dispatch elsewhere. The first
        // injection wraps that dispatch, so the check must fail rather than report a working path.
        MethodNode withoutCall = method(ServerAnimationControllerBytecode.NATURAL_COMPLETION_METHOD,
                ServerAnimationControllerBytecode.NATURAL_COMPLETION_METHOD_DESCRIPTOR);
        withoutCall.instructions = new InsnList();
        ClassNode controller = syntheticController(withoutCall);
        assertTrue(ServerAnimationControllerBytecode.hasNaturalCompletionMethod(controller),
                "the method is there, by name");
        assertFalse(ServerAnimationControllerBytecode.hasNaturalCompletionAnchor(controller),
                "but the dispatch the injection needs is gone, so the completion path is NOT usable");
        assertFalse(ServerAnimationControllerBytecode.hasNaturalCompletionAnchor(syntheticController()),
                "a controller with no such method has no anchor either");

        // The regression that shipped once, as a check: an EARLIER call in the same branch must not satisfy the
        // anchor. Anchoring on it made the wrapper clear the mark before the event could read it, so every
        // natural completion settled to nothing while this test would have reported a healthy anchor.
        MethodNode earlierCallOnly = method(ServerAnimationControllerBytecode.NATURAL_COMPLETION_METHOD,
                ServerAnimationControllerBytecode.NATURAL_COMPLETION_METHOD_DESCRIPTOR);
        InsnList earlyBody = new InsnList();
        earlyBody.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                ServerAnimationControllerBytecode.CONTROLLER_OWNER, "scheduleMobMountRestores",
                "(Lnet/minecraft/class_3218;Ljava/util/Collection;J)V", false));
        earlierCallOnly.instructions = earlyBody;
        assertFalse(ServerAnimationControllerBytecode.hasNaturalCompletionAnchor(
                        syntheticController(earlierCallOnly)),
                "the mount-restore call runs BEFORE the stop event, so anchoring there loses the completion");

        // A layout change the name-only check cannot see: the instance id is no longer at the index the handler
        // reads. Reporting success here is exactly the false pass this check exists to prevent.
        MethodNode shifted = method(ServerAnimationControllerBytecode.NATURAL_COMPLETION_METHOD,
                "(Lnet/minecraft/class_3218;JLjava/util/UUID;Ljava/util/List;)Z");
        assertTrue(ServerAnimationControllerBytecode.hasNaturalCompletionMethod(
                        syntheticController(shifted)),
                "the name is still there");
        assertFalse(ServerAnimationControllerBytecode.hasInstanceIdAtExpectedArgumentIndex(
                        syntheticController(shifted)),
                "but the instance id moved, so @Local(index = 1) would read the wrong value");
        assertTrue(ServerAnimationControllerBytecode.hasInstanceIdAtExpectedArgumentIndex(
                        syntheticController(method(ServerAnimationControllerBytecode
                                        .NATURAL_COMPLETION_METHOD,
                                ServerAnimationControllerBytecode.NATURAL_COMPLETION_METHOD_DESCRIPTOR))),
                "the expected layout is accepted");

        // The parameter walker must not be fooled by arrays or nested object types.
        assertEquals("Ljava/util/UUID;",
                ServerAnimationControllerBytecode.argumentType("(IJLjava/util/UUID;Z)V", 2));
        assertEquals("[[Ljava/lang/String;",
                ServerAnimationControllerBytecode.argumentType("([[Ljava/lang/String;IJ)V", 0));
        assertEquals("J", ServerAnimationControllerBytecode.argumentType("(Ljava/util/UUID;J)V", 1));
        assertEquals("", ServerAnimationControllerBytecode.argumentType("(Ljava/util/UUID;)V", 1),
                "an index past the end reports nothing rather than guessing");
        assertEquals("", ServerAnimationControllerBytecode.argumentType("not-a-descriptor", 0));
        assertEquals("", ServerAnimationControllerBytecode.argumentType(null, 0));

        // The same call on another class must not satisfy the anchor either.
        MethodNode wrongOwner = method(ServerAnimationControllerBytecode.NATURAL_COMPLETION_METHOD,
                ServerAnimationControllerBytecode.NATURAL_COMPLETION_METHOD_DESCRIPTOR);
        InsnList otherOwnerBody = new InsnList();
        otherOwnerBody.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ANOTHER_OWNER,
                ServerAnimationControllerBytecode.STOP_EVENT_METHOD,
                ServerAnimationControllerBytecode.NATURAL_COMPLETION_ANCHOR_DESCRIPTOR, false));
        wrongOwner.instructions = otherOwnerBody;
        assertFalse(ServerAnimationControllerBytecode.hasNaturalCompletionAnchor(
                        syntheticController(wrongOwner)),
                "the owning class is part of that anchor too");
    }

    @Test
    @DisplayName("the advance entry itself is matched by its FULL signature, not by name")
    void theAdvanceEntryIsMatchedBySignature() {
        ClassNode wrongSignature = syntheticController(method("advanceStage", "(I)V"));
        assertFalse(ServerAnimationControllerBytecode.hasAdvanceMethod(wrongSignature),
                "a same-named method with another signature is not the entry the mixin targets");
        ClassNode rightSignature = syntheticController(
                method("advanceStage", ServerAnimationControllerBytecode.ADVANCE_METHOD_DESCRIPTOR));
        assertTrue(ServerAnimationControllerBytecode.hasAdvanceMethod(rightSignature));
    }

    // ------------------------------------------------------------------
    // The mark's lifetime: what the wrappers' finally actually relies on
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the mark is still readable WHILE the stop event runs, and only then is it cleared")
    void theMarkIsReadableWhileTheStopEventRuns() {
        // The regression this pins down, reproduced against the previous delivery: the natural branch wrapped a
        // call that runs BEFORE the stop event, so the finally cleared the mark first and every normally
        // finished animation settled to NONE. Asserting "the mark is gone afterwards" does not catch that --
        // the cleanup was working perfectly, just too early. What has to hold is this: DURING the wrapped
        // action, which is exactly where the settlement runs in game, the mark is still there.
        MatingStopEvidence.clearAll();
        MatingStopEvidence.setAdapterStatus("enabled for NeedsOfNature 1.5.1+1.21.11; settlement uses the"
                + " real completion paths");

        // The natural path: record, then the wrapped action IS the stop-event dispatch.
        UUID natural = UUID.randomUUID();
        MatingStopEvidence.recordNaturalEnd(natural);
        java.util.concurrent.atomic.AtomicReference<Kind> seenByTheEvent =
                new java.util.concurrent.atomic.AtomicReference<>();
        CompletionMarkScope.run(natural, () -> {
            // This is the settlement's read, inside the stop event.
            seenByTheEvent.set(MatingStopEvidence.observedKind(natural));
            return null;
        });
        assertEquals(Kind.NATURAL_END, seenByTheEvent.get(),
                "the stop event must see the completion mark: a mark cleared before the event is a lost"
                        + " completion");
        assertEquals(Kind.INTERRUPTED, MatingStopEvidence.observedKind(natural),
                "and once the event has run, the mark is gone so no later stop can read it");

        // The requested-end path, same property.
        UUID requested = UUID.randomUUID();
        MatingStopEvidence.recordFinalStageAdvance(requested);
        java.util.concurrent.atomic.AtomicReference<Kind> seenByTheStop =
                new java.util.concurrent.atomic.AtomicReference<>();
        CompletionMarkScope.run(requested, () -> {
            seenByTheStop.set(MatingStopEvidence.observedKind(requested));
            return null;
        });
        assertEquals(Kind.MANUAL_ADVANCE, seenByTheStop.get(),
                "the advance entry's stop is dispatched inside the wrapped call, so the event sees the mark");
        assertEquals(Kind.INTERRUPTED, MatingStopEvidence.observedKind(requested));

        MatingStopEvidence.clearAll();
        MatingStopEvidence.setAdapterStatus("not initialised");
    }

    @Test
    @DisplayName("the scope clears the mark after a normal return, a failed return AND a thrown exception")
    void theScopeClearsTheMarkOnEveryOutcome() {
        // This is the test the previous delivery was missing: calling "record then forget" only proved that the
        // clearing method works. What matters is the WRAPPER's behaviour, because that is what runs in game --
        // and a wrapper that only cleaned up on the success path would leave exactly the stale mark the review
        // found, and would also have hidden the missing @Coerce on the handler's signature.
        MatingStopEvidence.clearAll();
        MatingStopEvidence.setAdapterStatus("enabled for NeedsOfNature 1.5.1+1.21.11; settlement uses the"
                + " real completion paths");

        // 1. Normal return: the action ran once, and no mark survives.
        UUID normal = UUID.randomUUID();
        AtomicInteger runs = new AtomicInteger();
        MatingStopEvidence.recordFinalStageAdvance(normal);
        Boolean result = CompletionMarkScope.run(normal, () -> {
            runs.incrementAndGet();
            // The settlement reads it here, in the real stop event; in this test the mark is simply left in
            // place, which is the strictest case: the scope must clear it anyway.
            assertEquals(Kind.MANUAL_ADVANCE, MatingStopEvidence.observedKind(normal));
            return Boolean.TRUE;
        });
        assertEquals(Boolean.TRUE, result, "the action's value must be passed through");
        assertEquals(1, runs.get(), "the action must run exactly once");
        assertEquals(Kind.INTERRUPTED, MatingStopEvidence.observedKind(normal),
                "after a normal return the mark is gone (the settlement had read it)");

        // 2. "Failed return": the action reports failure WITHOUT throwing. The mark must still be gone, because
        //    the settlement -- not the return value -- is what consumes a mark.
        UUID failed = UUID.randomUUID();
        MatingStopEvidence.recordFinalStageAdvance(failed);
        Boolean failedResult = CompletionMarkScope.run(failed, () -> Boolean.FALSE);
        assertEquals(Boolean.FALSE, failedResult);
        assertEquals(Kind.INTERRUPTED, MatingStopEvidence.observedKind(failed),
                "a false return must not leave a completion mark behind");

        // 3. Thrown exception: nothing fired a stop event, so nothing may be owed -- and the exception must
        //    still reach the caller (NON's own error handling must see it unchanged).
        UUID thrown = UUID.randomUUID();
        MatingStopEvidence.recordFinalStageAdvance(thrown);
        IllegalStateException boom = new IllegalStateException("stop failed");
        IllegalStateException caught = assertThrows(IllegalStateException.class,
                () -> CompletionMarkScope.run(thrown, () -> {
                    throw boom;
                }));
        assertSame(boom, caught, "the original exception must propagate unchanged");
        assertEquals(Kind.INTERRUPTED, MatingStopEvidence.observedKind(thrown),
                "a throw before the stop event must not leave a mark for a later stop to read");

        // A natural-end mark is bounded the same way.
        UUID natural = UUID.randomUUID();
        MatingStopEvidence.recordNaturalEnd(natural);
        CompletionMarkScope.run(natural, () -> null);
        assertEquals(Kind.INTERRUPTED, MatingStopEvidence.observedKind(natural));

        // Clearing an instance that carries nothing is a no-op, which is what makes it safe to run after the
        // settlement already consumed the mark.
        UUID unknown = UUID.randomUUID();
        CompletionMarkScope.run(unknown, () -> null);
        assertEquals(Kind.INTERRUPTED, MatingStopEvidence.observedKind(unknown));
        CompletionMarkScope.run(null, () -> null);

        // The narrow clear must not erase a reason some other path already recorded.
        UUID withReason = UUID.randomUUID();
        MatingStopEvidence.recordReason(withReason, "DAMAGE_STOP");
        MatingStopEvidence.clearCompletion(withReason);
        assertEquals("DAMAGE_STOP", MatingStopEvidence.observedReasonName(withReason),
                "the cleanup drops the completion classification, not other evidence");

        MatingStopEvidence.clearAll();
        MatingStopEvidence.setAdapterStatus("not initialised");
    }

    @Test
    @DisplayName("forget drops everything for an instance, which is what a decided settlement relies on")
    void forgettingDropsTheMarkToo() {
        MatingStopEvidence.clearAll();
        MatingStopEvidence.setAdapterStatus("enabled for NeedsOfNature 1.5.1+1.21.11; settlement uses the"
                + " real completion paths");
        UUID instanceId = UUID.randomUUID();
        MatingStopEvidence.recordFinalStageAdvance(instanceId);
        assertEquals(Kind.MANUAL_ADVANCE, MatingStopEvidence.observedKind(instanceId));

        MatingStopEvidence.forget(instanceId);
        assertEquals(Kind.INTERRUPTED, MatingStopEvidence.observedKind(instanceId),
                "with the mark gone, a stop that was never reported is an interruption again");

        UUID natural = UUID.randomUUID();
        MatingStopEvidence.recordNaturalEnd(natural);
        assertEquals(Kind.NATURAL_END, MatingStopEvidence.observedKind(natural));
        MatingStopEvidence.forget(natural);
        assertEquals(Kind.INTERRUPTED, MatingStopEvidence.observedKind(natural));

        MatingStopEvidence.clearAll();
        MatingStopEvidence.setAdapterStatus("not initialised");
    }
}
