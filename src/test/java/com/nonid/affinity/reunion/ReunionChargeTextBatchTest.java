package com.nonid.affinity.reunion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReunionChargeTextBatchTest {
    private record Item(String bond) {}
    @Test void completedThenCraftedCountsOneAndUsesLatestEnergyDespiteIterationOrder(){
        var source=new Item("same");var result=new Item("same");var b=new ReunionChargeTextBatch<Item>();
        b.inherit(source,result);b.add(result,1,2,2);b.add(source,1,1,1);
        assertEquals(1,b.itemCount());assertEquals(1,b.totalItemCount());assertEquals(2,b.gained());assertEquals(2,b.singleAfter());
    }
    @Test void equalBondOnDifferentPhysicalItemsNeverMerges(){
        var first=new Item("same");var second=new Item("same");var result=new Item("same");var b=new ReunionChargeTextBatch<Item>();
        b.inherit(first,result);b.add(first,1,1,1);b.add(result,1,2,2);b.add(second,1,1,3);
        assertEquals(2,b.itemCount());assertEquals(2,b.totalItemCount());assertEquals(3,b.gained());
    }
    @Test void chainedOrdinaryOrShiftReplacementsKeepOnePhysicalIdentity(){
        var a=new Object();var b=new Object();var c=new Object();var text=new ReunionChargeTextBatch<Object>();
        text.inherit(a,b);text.inherit(b,c);text.add(c,1,3,3);text.add(a,1,1,1);text.add(b,1,2,2);
        assertEquals(1,text.itemCount());assertEquals(3,text.gained());assertEquals(3,text.singleAfter());
    }
    @Test void animatedItemsDoNotAddTextButRemainDistinctFromInventoryFallback(){
        var a=new Object();var replacement=new Object();var other=new Object();var text=new ReunionChargeTextBatch<Object>();
        text.inherit(a,replacement);text.track(replacement);text.add(a,1,1,1);
        assertEquals(1,text.itemCount());assertEquals(1,text.totalItemCount());assertEquals(1,text.singleAfter());
        text.track(other);assertEquals(2,text.totalItemCount());assertEquals(1,text.gained());
    }
}
