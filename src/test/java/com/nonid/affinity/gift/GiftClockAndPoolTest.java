package com.nonid.affinity.gift;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GiftClockAndPoolTest {
    @Test void naturalSleepAndCommandTimeCountOnce() {
        var c = new GiftDayClock(); c.begin(12000); assertEquals(1,c.end(12001)); assertEquals(0,c.end(12001));
        c.begin(13000); assertEquals(12000,c.end(24001));
        c.begin(100000); assertEquals(76000,c.end(100001));
    }
    @Test void backtrackDoesNotSubtractAndNextPositiveAdvanceCounts() {
        var c = new GiftDayClock(); c.begin(500); assertEquals(99500,c.end(100000));
        c.begin(100000); assertEquals(0,c.end(0)); c.begin(0); assertEquals(24001,c.end(24001));
        c.reset(); c.begin(900000); assertEquals(1,c.end(900001));
    }
    @Test void multipleCommandsRetainEveryPositiveAdvanceAndDoNotDoubleCreditSetter() {
        var c=new GiftDayClock(); c.begin(100); c.changed(100,300); c.changed(300,50); c.changed(50,500);
        assertEquals(650,c.end(500)); assertEquals(0,c.end(500)); c.begin(500); c.changed(500,501); assertEquals(1,c.end(501));
    }
    @Test void fullSignedTimeSpanIsKeptExactlyWithoutOverflow() {
        var c=new GiftDayClock(); c.begin(Long.MIN_VALUE); c.changed(Long.MIN_VALUE,Long.MAX_VALUE);
        assertEquals(new java.math.BigInteger("18446744073709551615"),c.endExact(Long.MAX_VALUE));
    }
    @Test void setterCreditsAndNaturalRemainderAreSeparateAndNeverDoubleCounted() {
        var c=new GiftDayClock(); c.begin(100); assertEquals(java.math.BigInteger.valueOf(200),c.changed(101,301));
        assertEquals(java.math.BigInteger.valueOf(2),c.endUnobserved(302)); assertEquals(0,c.end(302));
    }
    @Test void ordinaryPoolRequiresExplicitProbabilitiesAndQuantityWeights() {
        assertThrows(IllegalArgumentException.class, () -> new GiftPoolTable.Selection(GiftPoolTable.Pool.FEATURE, 1, List.of("minecraft:stick"), List.of()));
        var quantity = Collections.nCopies(12, 1.0); var selection = new GiftPoolTable.Selection(GiftPoolTable.Pool.FEATURE, 1, List.of("minecraft:diamond", "minecraft:dirt"), quantity);
        var random = new Random(27); int diamonds = 0;
        for (int i = 0; i < 12000; i++) { var reward = GiftPoolTable.draw(List.of(selection), 0, id -> true, random).orElseThrow(); assertTrue(reward.count() >= 1 && reward.count() <= 12); if (reward.item().equals("minecraft:diamond")) diamonds++; }
        assertTrue(diamonds > 5700 && diamonds < 6300, "High-value and common items have equal pool probability");
    }
    @Test void excludesNetherStarAndUnapprovedUltimateProbability() {
        assertThrows(IllegalArgumentException.class, () -> new GiftPoolTable.Selection(GiftPoolTable.Pool.ULTIMATE, .00005, List.of("minecraft:nether_star"), List.of(1.0, 1.0, 1.0)));
        var selection = new GiftPoolTable.Selection(GiftPoolTable.Pool.ULTIMATE, .00005, List.of("minecraft:diamond"), List.of(1.0, 1.0, 1.0));
        assertDoesNotThrow(() -> GiftPoolTable.draw(List.of(selection), 0, id -> true, new Random(1)));
        assertThrows(IllegalArgumentException.class, () -> GiftPoolTable.draw(List.of(selection), 3, id -> true, new Random(1)));
    }
    @Test void missingRewardsRetainValidContentsAndUseOnlyFrozenOrdinaryFallback() {
        var gift = new GiftContent(UUID.randomUUID(), UUID.randomUUID(), "entity:" + UUID.randomUUID(), "test", "minecraft:pig", 0, 0, 0,
                List.of(new GiftContent.Reward("minecraft:diamond", 2), new GiftContent.Reward("example:removed", 1)), List.of(new GiftContent.Reward("minecraft:stick", 1)));
        var resolved = GiftPoolTable.resolve(gift, id -> !id.startsWith("example:")); assertEquals(List.of(new GiftContent.Reward("minecraft:diamond", 2), new GiftContent.Reward("minecraft:stick", 1)), resolved);
        assertEquals(gift, GiftContent.fromNbt(gift.toNbt()));
    }
    @Test void missingRegistryNeverProducesAnUnconfiguredAlternative() {
        var selection = new GiftPoolTable.Selection(GiftPoolTable.Pool.PRECIOUS, 1, List.of("example:missing"), List.of(1.0, 1.0, 1.0));
        assertTrue(GiftPoolTable.draw(List.of(selection), 0, id -> false, new Random(1)).isEmpty());
    }
    private GiftPlacement.Result completeSearch(GiftEnvelope.Location death,java.util.function.Function<net.minecraft.util.math.BlockPos,GiftPlacement.Probe> probe){
        var result=GiftPlacement.search(death,probe);int batches=1;
        while(result.status()==GiftPlacement.Status.SEARCHING){assertTrue(result.checked()<=4096);assertTrue(batches++<10);result=GiftPlacement.search(death,probe,result.cursor(),result.unloaded(),4096);}
        return result;
    }
    @Test void uncheckedOuterSphereCannotBePrematurelyLostAndSearchSurvivesRestart(){
        var death=new GiftEnvelope.Location("minecraft:overworld",.1,200,.1);
        java.util.function.Function<net.minecraft.util.math.BlockPos,GiftPlacement.Probe> probe=pos->pos.getX()==15&&pos.getY()==200&&pos.getZ()==0?GiftPlacement.Probe.SAFE:GiftPlacement.Probe.UNSAFE;
        var result=GiftPlacement.search(death,probe);assertEquals(GiftPlacement.Status.SEARCHING,result.status());
        var content=new GiftContent(UUID.randomUUID(),UUID.randomUUID(),"entity:"+UUID.randomUUID(),"test","minecraft:pig",0,0,0,List.of(new GiftContent.Reward("minecraft:stick",1)),List.of());
        var gift=new GiftEnvelope(content);gift.died(death,"test",null);gift.searchProgress(result);gift=GiftEnvelope.fromNbt(gift.toNbt());assertEquals(result.cursor(),gift.searchCursor());
        int batches=0;while(result.status()==GiftPlacement.Status.SEARCHING){assertTrue(batches++<10);result=GiftPlacement.search(death,probe,gift.searchCursor(),gift.searchUnloaded(),4096);gift.searchProgress(result);}
        assertEquals(GiftPlacement.Status.PLACED,result.status());assertEquals(15.5,result.location().x());
    }
    @Test void safePlacementIsFiniteAndUnloadedStaysPending() {
        var death = new GiftEnvelope.Location("minecraft:overworld", .1, 200, .1);
        var waiting = completeSearch(death,pos -> GiftPlacement.Probe.UNLOADED); assertEquals(GiftPlacement.Status.WAITING_LOAD, waiting.status()); assertTrue(waiting.checked() <= 4096);
        var safe = GiftPlacement.search(death, pos -> pos.getY() == 200 ? GiftPlacement.Probe.SAFE : GiftPlacement.Probe.UNSAFE); assertEquals(GiftPlacement.Status.PLACED, safe.status());
        double x = death.x() - safe.location().x(), y = death.y() - safe.location().y(), z = death.z() - safe.location().z(); assertTrue(x*x + y*y + z*z <= 256);
        assertEquals(GiftPlacement.Status.LOST, completeSearch(death,pos -> GiftPlacement.Probe.UNSAFE).status());
    }
}
