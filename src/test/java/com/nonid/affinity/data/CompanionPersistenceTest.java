package com.nonid.affinity.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Persistence tests for companion bonding.
 *
 * <p>The review for this feature warned specifically that adding fields without covering
 * {@code toNbt}/{@code fromNbt} would leave the data silently unpersisted, so the round trip is
 * tested directly rather than assumed.
 */
class CompanionPersistenceTest {

    @Test
    @DisplayName("companion owner and movement mode survive an NBT round trip")
    void companionSurvivesRoundTrip() {
        CreatureAffinityData data = new CreatureAffinityData();
        UUID owner = UUID.fromString("11111111-2222-3333-4444-555555555555");
        data.setCompanionOwner(owner);
        data.setCompanionMode(CompanionMode.WANDER);

        CreatureAffinityData restored = CreatureAffinityData.fromNbt(data.toNbt());

        assertEquals(owner, restored.getCompanionOwner(), "the bonded owner must survive saving");
        assertEquals(CompanionMode.WANDER, restored.getCompanionMode(), "the mode must survive saving");
        assertTrue(restored.isCompanionOf(owner));
    }

    @Test
    @DisplayName("saves written before companion support load as unbound with FOLLOW")
    void oldSavesLoadAsUnbound() {
        // An empty compound stands in for a save written before these fields existed.
        CreatureAffinityData restored = CreatureAffinityData.fromNbt(new NbtCompound());

        assertNull(restored.getCompanionOwner(), "a missing field must mean unbound, not a guessed owner");
        assertEquals(CompanionMode.FOLLOW, restored.getCompanionMode(), "missing mode must default to FOLLOW");
        assertFalse(restored.isCompanionOf(UUID.randomUUID()));
    }

    @Test
    @DisplayName("an unbound creature writes no companion keys")
    void unboundWritesNoCompanionKeys() {
        CreatureAffinityData data = new CreatureAffinityData();
        NbtCompound nbt = data.toNbt();

        assertFalse(nbt.contains("companion_owner"),
                "unbound creatures must not write companion keys, so old saves stay unchanged");
    }

    @Test
    @DisplayName("an unknown stored mode falls back to FOLLOW instead of throwing")
    void unknownModeFallsBack() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("companion_owner", "11111111-2222-3333-4444-555555555555");
        nbt.putString("companion_mode", "NOT_A_REAL_MODE");

        CreatureAffinityData restored = CreatureAffinityData.fromNbt(nbt);

        assertEquals(CompanionMode.FOLLOW, restored.getCompanionMode());
    }

    @Test
    @DisplayName("a mode that was never chosen is not the same thing as a chosen FOLLOW")
    void unsetModeIsDistinguishableFromChosenFollow() {
        // The difference decides whether the load backfill may give a sitting pet WAIT, so it has to survive
        // saving. While the field held FOLLOW by default and the getter coerced null to FOLLOW, "no mode was
        // ever chosen" could not be represented and every check for it was dead code.
        CreatureAffinityData unset = new CreatureAffinityData();
        unset.setCompanionOwner(UUID.fromString("11111111-2222-3333-4444-555555555555"));

        CreatureAffinityData restoredUnset = CreatureAffinityData.fromNbt(unset.toNbt());
        assertNull(restoredUnset.chosenMode(), "an undecided creature must not come back as decided");
        assertEquals(CompanionMode.FOLLOW, restoredUnset.getCompanionMode(),
                "an undecided creature still behaves as a follower");

        CreatureAffinityData chosen = new CreatureAffinityData();
        chosen.setCompanionOwner(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        chosen.setCompanionMode(CompanionMode.FOLLOW);

        CreatureAffinityData restoredChosen = CreatureAffinityData.fromNbt(chosen.toNbt());
        assertEquals(CompanionMode.FOLLOW, restoredChosen.chosenMode(),
                "a mode the player picked must come back as picked, or it may be overwritten later");
    }
}
