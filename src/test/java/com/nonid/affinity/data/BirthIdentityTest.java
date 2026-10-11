package com.nonid.affinity.data;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BirthIdentityTest {
    private final UUID motherId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private FamilyGraph.Node mother() { return new FamilyGraph.Node("player:"+motherId, "Mother", "minecraft:player", 2, FamilyGraph.NameKind.PLAYER); }
    private FamilyGraph.Node father(int number) { return new FamilyGraph.Node("entity:"+new UUID(0,number), "供体"+number, "minecraft:pig", 1, FamilyGraph.NameKind.CUSTOM); }
    @Test void selectedDonorSurvivesSerializationAndSameSpeciesDoesNotMergeIdentity() {
        var first = new BirthIdentity.Donor(father(2), Set.of());
        var second = new BirthIdentity.Donor(father(3), Set.of());
        var restored = BirthIdentity.decodeDonor(BirthIdentity.encodeDonor(second));
        assertEquals(second, restored); assertNotEquals(first, restored);
        var birth = BirthIdentity.birth(mother(), "minecraft:pig", restored);
        assertEquals(father(3), BirthIdentity.fromNbt(BirthIdentity.toNbt(birth)).father());
    }
    @Test void unknownAndMismatchedSourcesNeverInventFather() {
        assertNull(BirthIdentity.birth(mother(), "minecraft:pig", null).father());
        assertNull(BirthIdentity.birth(mother(), "minecraft:cow", new BirthIdentity.Donor(father(2), Set.of())).father());
        assertNull(BirthIdentity.decodeDonor("{}")); assertNull(BirthIdentity.decodeDonor("invalid"));
        assertNull(BirthIdentity.decodeDonor(" ".repeat(17000)));
    }
    @Test void replaySuppressionFollowsOnlyTheCapturedReceiverAcrossPersistence() {
        var exact = new BirthIdentity.Donor(father(2), Set.of(motherId));
        var birth = BirthIdentity.birth(mother(), "minecraft:pig", BirthIdentity.decodeDonor(BirthIdentity.encodeDonor(exact)));
        assertTrue(birth.isolated()); assertNull(birth.father());
        assertTrue(BirthIdentity.fromNbt(BirthIdentity.toNbt(birth)).isolated());
        var other = new FamilyGraph.Node("player:"+new UUID(0,4), "Other", "minecraft:player", 2);
        assertFalse(BirthIdentity.birth(other,"minecraft:pig",exact).isolated());
        assertEquals(father(2),BirthIdentity.birth(other,"minecraft:pig",exact).father());
    }
    @Test void oldEggMotherRemainsExactWithoutInventedNameGenderOrFather() {
        var unknown = new FamilyGraph.Node("player:"+motherId, "", "minecraft:player", 0);
        var birth = BirthIdentity.fromNbt(BirthIdentity.toNbt(new BirthIdentity.Birth(unknown,null,false)));
        assertEquals(motherId,birth.viewer()); assertEquals(0,birth.mother().gender()); assertNull(birth.father());
    }
}
