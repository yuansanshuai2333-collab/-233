package com.nonid.affinity.data;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BondPrecisionTest {
    @Test void oldSavesHaveZeroBondAndRetainIntegerValue() {
        var old = new NbtCompound(); old.putInt("affinity", 74);
        var value = PlayerAffinity.fromNbt(old);
        assertEquals(74, value.getExactAffinity()); assertEquals(0, value.getBond());
    }
    @Test void thousandFractionalRewardsSurviveRepeatedSerializationWithoutDrift() {
        var value = new PlayerAffinity(74);
        for (int i = 0; i < 1000; i++) {
            value.addPreciseAffinity(.001, -200, 200); value.addBond(.0001);
            value = PlayerAffinity.fromNbt(value.toNbt());
        }
        assertEquals(75, value.getAffinity()); assertEquals(75, value.getExactAffinity()); assertEquals(.1, value.getBond());
    }
    @Test void integerChangesKeepFractionButExplicitSetAndCapRemoveIt() {
        var value = new PlayerAffinity(74); value.addPreciseAffinity(.6, -200, 200);
        value.addAffinity(-1, -200, 200); assertEquals(73.6, value.getExactAffinity());
        value.setAffinity(199, -200, 200); value.addPreciseAffinity(.8, -200, 200);
        assertEquals(.2, value.addPreciseAffinity(1, -200, 200), 1e-9); assertEquals(200, value.getExactAffinity());
        value.addBond(.5); assertEquals(.5, value.getBond());
    }
    @Test void negativeFractionUsesFloorAndInvalidInputsNeverPoisonSavedState() {
        var value = new PlayerAffinity(-1); value.addPreciseAffinity(.5, -200, 200);
        assertEquals(-1, value.getAffinity()); assertEquals(-.5, value.getExactAffinity());
        value.addBond(Double.NaN); value.addBond(Double.POSITIVE_INFINITY); value.addBond(-.5);
        assertEquals(0, value.getBond()); assertEquals(0, value.addPreciseAffinity(Double.NaN, -200, 200));
        value.addBond(Double.MAX_VALUE); value.addBond(Double.MAX_VALUE);
        assertTrue(Double.isFinite(value.getBond())); value.addBond(-Double.MAX_VALUE); assertEquals(0, value.getBond());
    }
    @Test void malformedSavedValuesAreClippedWithoutReconstructingHistory() {
        var tag = new NbtCompound(); tag.putLong("relationship_bond", -99); tag.putLong("affinity_fraction", Long.MAX_VALUE);
        var value = PlayerAffinity.fromNbt(tag); assertEquals(0, value.getBond()); assertTrue(value.getExactAffinity() < 1);
    }
}
