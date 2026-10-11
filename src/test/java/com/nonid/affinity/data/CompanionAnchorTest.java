package com.nonid.affinity.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Persistence tests for companion anchors.
 *
 * <p>The requirement is that the mode and its anchor survive a save and reload, so the anchor's
 * dimension and coordinates are round-tripped here rather than assumed. A position without its
 * dimension would be ambiguous, so both are checked.
 */
class CompanionAnchorTest {

    @Test
    @DisplayName("anchor and dimension survive an NBT round trip")
    void anchorSurvivesRoundTrip() {
        CreatureAffinityData data = new CreatureAffinityData();
        data.setCompanionOwner(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        data.setCompanionMode(CompanionMode.WAIT);
        data.setAnchor("minecraft:overworld", 12.5, 64.0, -3.25);

        CreatureAffinityData restored = CreatureAffinityData.fromNbt(data.toNbt());

        assertTrue(restored.hasAnchor(), "the anchor must survive saving");
        assertEquals(CompanionMode.WAIT, restored.getCompanionMode(), "the mode must survive saving");
        assertTrue(restored.anchorIn("minecraft:overworld"));
        assertEquals(12.5, restored.anchorX(), 0.0001);
        assertEquals(64.0, restored.anchorY(), 0.0001);
        assertEquals(-3.25, restored.anchorZ(), 0.0001);
    }

    @Test
    @DisplayName("an anchor from another dimension is recognised as such")
    void anchorKnowsItsDimension() {
        CreatureAffinityData data = new CreatureAffinityData();
        data.setAnchor("minecraft:the_nether", 1.0, 2.0, 3.0);

        assertTrue(data.anchorIn("minecraft:the_nether"));
        assertFalse(data.anchorIn("minecraft:overworld"),
                "an anchor must not be treated as usable from another dimension");
    }

    @Test
    @DisplayName("saves without an anchor report none rather than a guessed position")
    void missingAnchorIsReportedAsNone() {
        // Stands in for a save written before anchors existed.
        CreatureAffinityData restored = CreatureAffinityData.fromNbt(new NbtCompound());

        assertFalse(restored.hasAnchor(), "a missing anchor must not be invented");
        assertFalse(restored.anchorIn("minecraft:overworld"));
    }

    @Test
    @DisplayName("clearing the anchor removes it from the saved data")
    void clearedAnchorIsNotSaved() {
        CreatureAffinityData data = new CreatureAffinityData();
        data.setAnchor("minecraft:overworld", 5.0, 6.0, 7.0);
        data.clearAnchor();

        assertFalse(data.hasAnchor());
        CreatureAffinityData restored = CreatureAffinityData.fromNbt(data.toNbt());
        assertFalse(restored.hasAnchor(), "a cleared anchor must not reappear after a reload");
    }

    @Test
    @DisplayName("the mode cycle is follow, wander, wait, attack and back")
    void modeCycleOrder() {
        // Confirmed order. WANDER and WAIT were swapped in the earlier implementation.
        assertEquals(CompanionMode.WANDER, CompanionMode.FOLLOW.next());
        assertEquals(CompanionMode.WAIT, CompanionMode.WANDER.next());
        assertEquals(CompanionMode.ATTACK, CompanionMode.WAIT.next());
        assertEquals(CompanionMode.FOLLOW, CompanionMode.ATTACK.next());
    }

    @Test
    @DisplayName("the project enum and the handover policy enum agree on every name")
    void policyEnumNamesMatch() {
        // The cycle is delegated to the handover policy, so a name mismatch would silently change
        // which mode a persisted save maps to. Checked explicitly for that reason.
        for (CompanionMode mode : CompanionMode.values()) {
            assertEquals(mode.name(),
                    com.nonid.affinity.service.policy.CompanionCombatPolicy.Mode
                            .valueOf(mode.name()).name(),
                    "mode names must match between the project and the handover policy");
        }
    }

    @Test
    @DisplayName("the project cycle matches the handover policy cycle")
    void projectCycleMatchesPolicy() {
        for (CompanionMode mode : CompanionMode.values()) {
            String fromPolicy = com.nonid.affinity.service.policy.CompanionCombatPolicy
                    .nextMode(com.nonid.affinity.service.policy.CompanionCombatPolicy.Mode
                            .valueOf(mode.name()))
                    .name();
            assertEquals(fromPolicy, mode.next().name(),
                    "the two cycle definitions must not drift apart");
        }
    }
}
