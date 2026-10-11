package com.nonid.affinity.gift;
import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class GiftRepairTest {
 static GiftContent.Reward r(String id,int n){return new GiftContent.Reward(id,n);}
 static GiftEnvelope opening(List<GiftContent.Reward> rewards,List<GiftContent.Reward> fallback){
  var c=new GiftContent(UUID.randomUUID(),UUID.randomUUID(),"entity:"+UUID.randomUUID(),"test","minecraft:sheep",0,0,0,rewards,fallback);
  var g=new GiftEnvelope(c);var drop=UUID.randomUUID();assertTrue(g.reserve(c.viewer(),drop,false));assertTrue(g.issued(drop,0));assertTrue(g.startOpen(g.token(),rewards,false));return g;
 }
 @Test void sheepAllColorsAndTiersUseOnlyConfirmedItemsAndIndividualRanges(){
  for(String color:List.of("white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"))for(int tier=0;tier<4;tier++){
   var counts=new HashMap<String,Integer>();var random=new Random(1600+tier){public double nextDouble(){return .999999;}};
   for(int i=0;i<1600;i++){
    var gift=GiftProductionTable.draw("minecraft:sheep","normal","none",tier,GiftProductionTable.sheepFeature(color),r->true,r->false,random);
    assertEquals(1,gift.size());var reward=gift.getFirst();counts.merge(reward.item(),1,Integer::sum);
    int max=reward.item().endsWith("_wool")||reward.item().equals("minecraft:wheat")?6:3;assertTrue(reward.count()>=1&&reward.count()<=max);
   }
   assertEquals(Set.of("minecraft:"+color+"_wool","minecraft:wheat","minecraft:dandelion","minecraft:poppy"),counts.keySet());
   for(int count:counts.values())assertTrue(count>320&&count<480,"All four materials are equally likely");
  }
 }
 @Test void alreadyDeliveredPrefixIsNeverReconstructed(){
  var g=opening(List.of(r("minecraft:diamond",2),r("removed:item",1),r("minecraft:gold_ingot",1)),List.of(r("minecraft:paper",1)));
  assertTrue(g.delivered(2));assertTrue(g.repairRemaining(x->!x.item().startsWith("removed:")));
  assertEquals(List.of(r("minecraft:gold_ingot",1),r("minecraft:paper",1)),g.remaining());assertTrue(g.fallbackApplied());
 }
 @Test void partiallyDeliveredHeadKeepsOnlyUnpaidQuantity(){
  var g=opening(List.of(r("minecraft:diamond",6),r("removed:item",1)),List.of(r("minecraft:paper",1)));assertTrue(g.delivered(4));
  g.repairRemaining(x->!x.item().startsWith("removed:"));assertEquals(List.of(r("minecraft:diamond",2),r("minecraft:paper",1)),g.remaining());
 }
 @Test void multipleMissingItemsAppendFallbackOnlyOnceAndPersist(){
  var g=opening(List.of(r("removed:a",1),r("removed:b",1),r("minecraft:diamond",1)),List.of(r("minecraft:paper",1)));
  g.repairRemaining(x->!x.item().startsWith("removed:"));g=GiftEnvelope.fromNbt(g.toNbt());
  assertTrue(g.fallbackApplied());assertFalse(g.repairRemaining(x->!x.item().startsWith("removed:")));assertEquals(2,g.remaining().size());
  assertTrue(g.delivered(1));assertTrue(g.repairRemaining(x->false));assertEquals(GiftEnvelope.State.OPENED,g.state());assertTrue(g.remaining().isEmpty());
 }
 @Test void deliveredFallbackPrefixCannotBeIssuedAgainAfterLaterLoss(){
  var g=opening(List.of(r("removed:a",1)),List.of(r("minecraft:paper",1),r("minecraft:gold_ingot",1)));
  g.repairRemaining(x->!x.item().startsWith("removed:"));assertTrue(g.delivered(1));g=GiftEnvelope.fromNbt(g.toNbt());
  assertTrue(g.repairRemaining(x->false));assertTrue(g.remaining().isEmpty());assertEquals(GiftEnvelope.State.OPENED,g.state());
 }
 @Test void activeReceiptIsNotClearedOrChangedByRepair(){
  var g=opening(List.of(r("minecraft:diamond",3),r("removed:item",1)),List.of(r("minecraft:paper",1)));var drop=UUID.randomUUID();assertTrue(g.reserveReward(drop,2));
  g.rewardLocation(new GiftEnvelope.Location("minecraft:overworld",1,2,3));var before=g.toNbt();assertFalse(g.repairRemaining(x->!x.item().startsWith("removed:")));assertEquals(before,g.toNbt());
  g=GiftEnvelope.fromNbt(g.toNbt());assertEquals(drop,g.rewardDrop());assertTrue(g.rewardSpawned(drop));g.repairRemaining(x->!x.item().startsWith("removed:"));
  assertEquals(List.of(r("minecraft:diamond",1),r("minecraft:paper",1)),g.remaining());
 }
 @Test void oldOpeningWithProvenOriginalSuffixCanUseOneFallback(){
  var g=opening(List.of(r("minecraft:diamond",2),r("removed:item",1)),List.of(r("minecraft:paper",1)));g.delivered(2);var n=g.toNbt();n.remove("fallback_applied");g=GiftEnvelope.fromNbt(n);
  assertFalse(g.fallbackApplied());g.repairRemaining(x->!x.item().startsWith("removed:"));assertEquals(List.of(r("minecraft:paper",1)),g.remaining());
 }
 @Test void ambiguousOldFallbackTailCannotBeDuplicated(){
  var g=opening(List.of(r("removed:item",1),r("minecraft:paper",1)),List.of(r("minecraft:paper",1)));var n=g.toNbt();n.remove("fallback_applied");g=GiftEnvelope.fromNbt(n);
  assertTrue(g.fallbackApplied());g.repairRemaining(x->!x.item().startsWith("removed:"));assertEquals(List.of(r("minecraft:paper",1)),g.remaining());
 }
 @Test void initialFallbackFlagSurvivesPartialDeliveryAndRestart(){
  var g=opening(List.of(r("minecraft:diamond",1)),List.of(r("minecraft:paper",1)));var n=g.toNbt();n.putBoolean("fallback_applied",true);g=GiftEnvelope.fromNbt(n);
  assertTrue(g.fallbackApplied());g.repairRemaining(x->false);assertEquals(GiftEnvelope.State.OPENED,g.state());
 }
 @Test void noAvailableFallbackCompletesOnlyTheMissingRemainder(){
  var g=opening(List.of(r("minecraft:diamond",1),r("removed:a",1)),List.of());g.delivered(1);assertTrue(g.repairRemaining(x->false));assertEquals(GiftEnvelope.State.OPENED,g.state());
 }
}
