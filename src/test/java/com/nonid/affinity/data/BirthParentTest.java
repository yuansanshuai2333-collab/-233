package com.nonid.affinity.data;

import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BirthParentTest {
    @Test void identitySurvivesReloadAndCannotBeOverwritten() {
        UUID parent = UUID.randomUUID();
        var data = new CreatureAffinityData();
        assertTrue(data.recordBirthParent(parent));
        var restored = CreatureAffinityData.fromNbt(data.toNbt());
        assertEquals(parent, restored.getBirthParent());
        assertFalse(restored.recordBirthParent(parent));
        assertFalse(restored.recordBirthParent(UUID.randomUUID()));
        assertEquals(parent, restored.getBirthParent());
    }
    @Test void legacyAndMalformedDataNeverInventParentage() {
        assertNull(CreatureAffinityData.fromNbt(new NbtCompound()).getBirthParent());
        var nbt = new NbtCompound();
        nbt.putString("birth_parent", "broken");
        assertNull(CreatureAffinityData.fromNbt(nbt).getBirthParent());
        assertFalse(new CreatureAffinityData().recordBirthParent(null));
    }
}
