package com.nonid.affinity.reunion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReunionEnergyTest {
 @Test void corruptedReceiptsCannotConsumeRechargeIngredients(){assertFalse(ReunionEnergy.canRecharge(100,1));assertFalse(ReunionEnergy.canRecharge(3,-1));assertFalse(ReunionEnergy.canRecharge(-1,0));assertFalse(ReunionEnergy.canRecharge(0,0));}
 @Test void rechargeOnlyAcceptsCurrentNonFullReceipts(){assertTrue(ReunionEnergy.canRecharge(3,3));assertTrue(ReunionEnergy.canRecharge(3,2));assertTrue(ReunionEnergy.canRecharge(3,1));assertFalse(ReunionEnergy.canRecharge(3,0));assertFalse(ReunionEnergy.canRecharge(3,4));}
 @Test void rechargeCannotOverflowCumulativeReceipt(){assertFalse(ReunionEnergy.canRecharge(Long.MAX_VALUE,Long.MAX_VALUE));assertTrue(ReunionEnergy.canRecharge(Long.MAX_VALUE-1,Long.MAX_VALUE-1));assertFalse(ReunionEnergy.canRecharge(Long.MAX_VALUE-1,0));}
 @Test void legacyReceiptsKeepUsedAndUnusedGenerations(){assertEquals(3,ReunionEnergy.remaining(ReunionEnergy.legacy(1),ReunionEnergy.legacy(0)));assertEquals(0,ReunionEnergy.remaining(ReunionEnergy.legacy(1),ReunionEnergy.legacy(1)));}
 @Test void sharedSpendingInvalidatesCopies(){long receipt=3;for(int used=0;used<=3;used++)assertEquals(3-used,ReunionEnergy.remaining(receipt,used));assertEquals(0,ReunionEnergy.remaining(receipt,4));}
 @Test void oneIngredientPairRestoresOneCharge(){assertEquals(1,ReunionEnergy.remaining(4,3));assertEquals(2,ReunionEnergy.remaining(5,3));assertEquals(3,ReunionEnergy.remaining(6,3));}
 @Test void invalidOrOverflowingDataCannotGrantCharges(){assertEquals(-1,ReunionEnergy.legacy(Long.MAX_VALUE));assertEquals(0,ReunionEnergy.remaining(-1,0));assertEquals(0,ReunionEnergy.remaining(3,-1));assertEquals(0,ReunionEnergy.remaining(Long.MAX_VALUE,0));assertEquals(0,ReunionEnergy.remaining(4,0));assertEquals(3,ReunionEnergy.remaining(Long.MAX_VALUE,Long.MAX_VALUE-3));}
}
