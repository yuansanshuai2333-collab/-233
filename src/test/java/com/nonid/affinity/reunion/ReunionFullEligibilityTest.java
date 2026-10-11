package com.nonid.affinity.reunion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReunionFullEligibilityTest {
 @Test void exactAndSpeciesFullDisplaysRemainZeroGains(){
  assertTrue(ReunionRechargeRules.fullCompletion(true,false,9,6));
  assertTrue(ReunionRechargeRules.fullCompletion(false,true,9,6));
  assertFalse(ReunionRechargeRules.fullCompletion(false,false,9,6));
  assertEquals(0,ReunionRechargeRules.completion(true,true,3));
  assertEquals(0,ReunionRechargeRules.materials(3,9,9));
 }
 @Test void invalidSpentFuelAndNonFullStatesStaySilent(){
  for(long[] v:new long[][]{{0,0},{2,0},{4,0},{3,-1},{3,4},{Long.MAX_VALUE,Long.MAX_VALUE-3},{-1,-4}})
   assertFalse(ReunionRechargeRules.fullCompletion(true,true,v[0],v[1]));
  assertTrue(ReunionRechargeRules.fullCompletion(true,true,Long.MAX_VALUE-1,Long.MAX_VALUE-4));
 }
}
