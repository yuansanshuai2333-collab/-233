package com.nonid.affinity.non;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

/** Pin the optional adapter to the actual NoN bytecode without loading game registries. */
class NativeSelectionContractTest {
    private ClassNode read(String name) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, "The verified NoN dependency is required for this contract test");
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    @Test void selectionHandlersStillHaveTheVerifiedSignatureAndDebugGate() throws Exception {
        var engine = read("com/nonid/internal/animation/AnimationEngine");
        for (var spec : List.of(new String[]{"lambda$initialize$10", "DebugAnimationChoiceC2SPayload", "choose"},
                new String[]{"lambda$initialize$12", "DebugAnimationCancelC2SPayload", "cancel"})) {
            var method = engine.methods.stream().filter(m -> m.name.equals(spec[0])).findFirst().orElseThrow();
            assertEquals("(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;Lcom/nonid/internal/animation/network/"
                    + spec[1] + ";)V", method.desc);
            int permission = -1, dispatch = -1;
            for (int i = 0; i < method.instructions.size(); i++) {
                if (method.instructions.get(i) instanceof MethodInsnNode call) {
                    if (call.name.equals("requireDebugControlPermission")) permission = i;
                    if (call.owner.equals("com/nonid/NonDebugAnimationSelectionSystem") && call.name.equals(spec[2])) dispatch = i;
                }
            }
            assertTrue(permission >= 0 && dispatch > permission, "Native debug traffic must retain its permission gate");
        }
    }

    @Test void nativeSessionsRetainIdentityActorsAndServerOfferedChoices() throws Exception {
        var session = read("com/nonid/NonDebugAnimationSelectionSystem$Session");
        for (String field : List.of("id", "requesterUuid", "actorUuids", "choices", "worldKey", "expiresTick"))
            assertTrue(session.fields.stream().anyMatch(f -> f.name.equals(field)), field);
        var choice = read("com/nonid/NonDebugAnimationSelectionSystem$Choice");
        assertTrue(choice.fields.stream().anyMatch(f -> f.name.equals("index") && f.desc.equals("I")));
        assertTrue(choice.fields.stream().anyMatch(f -> f.name.equals("selectable") && f.desc.equals("Z")));
    }
}
