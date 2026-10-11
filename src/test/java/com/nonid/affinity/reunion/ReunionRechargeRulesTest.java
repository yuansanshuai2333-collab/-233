package com.nonid.affinity.reunion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReunionRechargeRulesTest {
 @Test void exactAndSpeciesAreMutuallyExclusiveAndCapAtThree(){for(int energy=0;energy<=3;energy++){assertEquals(3-energy,ReunionRechargeRules.completion(true,true,energy));assertEquals(Math.min(1,3-energy),ReunionRechargeRules.completion(false,true,energy));assertEquals(0,ReunionRechargeRules.completion(false,false,energy));}}
 @Test void materialsConsumeOnlyMissingPairs(){assertEquals(2,ReunionRechargeRules.materials(1,9,9));assertEquals(1,ReunionRechargeRules.materials(2,9,9));assertEquals(3,ReunionRechargeRules.materials(0,9,9));assertEquals(1,ReunionRechargeRules.materials(0,1,9));assertEquals(1,ReunionRechargeRules.materials(0,9,1));assertEquals(0,ReunionRechargeRules.materials(3,9,9));}
 @Test void invalidStateAndMissingMaterialsCannotCharge(){for(int energy:new int[]{-1,4,Integer.MAX_VALUE}){assertEquals(0,ReunionRechargeRules.completion(true,true,energy));assertEquals(0,ReunionRechargeRules.materials(energy,9,9));}assertEquals(0,ReunionRechargeRules.materials(0,0,9));assertEquals(0,ReunionRechargeRules.materials(0,9,0));}
}
