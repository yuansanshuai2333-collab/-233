package com.nonid.affinity.service;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

class TamingOwnershipRegressionTest {
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test void untamedCreatureBoundToAliceCannotBeTamedForBob() {
        assertEquals(RelationshipRules.BondOutcome.OTHER_PLAYER_COMPANION,
                RelationshipRules.evaluate(alice, null, bob, true, false));
    }
    @Test void existingCompanionMayBeTamedByItsOwnOwner() {
        assertEquals(RelationshipRules.BondOutcome.BOUND,
                RelationshipRules.evaluate(alice, null, alice, true, false));
    }
    @Test void missingOwnerOnAnAlreadyTamedCreatureDoesNotMeanUnowned() {
        assertEquals(RelationshipRules.BondOutcome.UNKNOWN_VANILLA_OWNER,
                RelationshipRules.evaluate(null, null, alice, true, true));
        assertEquals(RelationshipRules.BondOutcome.UNKNOWN_VANILLA_OWNER,
                RelationshipRules.evaluate(alice, null, alice, true, true));
    }
    @Test void genuinelyUntamedUnownedCreatureStillAcceptsItsFirstOwner() {
        assertEquals(RelationshipRules.BondOutcome.BOUND,
                RelationshipRules.evaluate(null, null, alice, true, false));
    }
    @Test void offlineOwnerUuidStillProtectsThePet() {
        assertEquals(RelationshipRules.BondOutcome.OTHER_PLAYER_VANILLA_OWNER,
                RelationshipRules.evaluate(null, alice, bob, true, true));
        assertEquals(RelationshipRules.BondOutcome.BOUND,
                RelationshipRules.evaluate(null, alice, alice, true, true));
    }

    @Test void thresholdPathHasARefusalReturnBeforeAnyTamingCall() throws Exception {
        MethodNode method = method("AffinityService", "checkFriendThreshold");
        assertGuardBefore(method, "tameCreature", Opcodes.RETURN);
    }
    @Test void sharedTamingHelperChecksOwnershipBeforeWritingVanillaState() throws Exception {
        MethodNode method = method("AffinityService", "tameCreature");
        assertGuardBefore(method, "setTamed", Opcodes.IRETURN);
        assertGuardBefore(method, "setTame", Opcodes.IRETURN);
        assertGuardBefore(method, "setOwner", Opcodes.IRETURN);
    }
    @Test void companionBindingAlsoUsesTheSameEligibilityCheck() throws Exception {
        assertGuardBefore(method("CompanionService", "bindOnBefriend"), "setCompanionOwner", Opcodes.IRETURN);
    }

    private static void assertGuardBefore(MethodNode method, String mutation, int returnOpcode) {
        int guard = -1;
        int write = -1;
        for (int i = 0; i < method.instructions.size(); i++) {
            if (!(method.instructions.get(i) instanceof MethodInsnNode call)) continue;
            if (call.owner.equals("com/nonid/affinity/service/RelationshipSync") && call.name.equals("eligibility")) guard = i;
            if (call.name.equals(mutation) && write < 0) write = i;
        }
        assertTrue(guard >= 0 && write > guard, method.name + ": check must precede " + mutation);
        boolean refusalReturn = false;
        for (int i = guard + 1; i < write; i++) {
            if (method.instructions.get(i).getOpcode() == returnOpcode) refusalReturn = true;
        }
        assertTrue(refusalReturn, method.name + ": refusal must be able to exit before mutation");
    }

    private static MethodNode method(String type, String name) throws Exception {
        try (var input = TamingOwnershipRegressionTest.class.getResourceAsStream(
                "/com/nonid/affinity/service/" + type + ".class")) {
            assertNotNull(input);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
        }
    }
}
