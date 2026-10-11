package com.nonid.affinity.gift;

import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GiftProductionTest {
    @Test void separateChannelsMayAllHitWithoutReplacingFeature() {
        var all = new Random() { public double nextDouble(){return 0;} public int nextInt(int bound){return 0;} };
        var result = GiftProductionTable.draw("minecraft:villager","cave","ancient_city",0,r->true,r->r.item().equals("minecraft:elytra"),all);
        assertEquals(5,result.size()); assertEquals("minecraft:wheat",result.getFirst().item());
        assertEquals("minecraft:elytra",result.getLast().item()); assertEquals(1,result.getLast().count());
        var none = new Random() { public double nextDouble(){return .999999;} };
        assertEquals(1,GiftProductionTable.draw("minecraft:cow","normal","none",0,r->true,r->false,none).size());
    }
    @Test void quantitiesFollowApprovedBandsAtEveryTierAndRange() {
        int[][] expected={{70,25,5},{55,35,10},{40,40,20},{25,45,30}};
        for(int tier=0;tier<4;tier++)for(int maximum:List.of(3,6,12)) {
            int[] bands=new int[3],values=new int[maximum];var random=new Random(120+maximum+tier);
            for(int i=0;i<60000;i++){int count=GiftProductionTable.quantity(tier,maximum,random);bands[(count-1)/(maximum/3)]++;values[count-1]++;}
            for(int band=0;band<3;band++) {
                assertEquals(expected[tier][band]/100.0,bands[band]/60000.0,.012);
                for(int value=band*(maximum/3);value<(band+1)*(maximum/3);value++)assertEquals(bands[band]/(60000.0*(maximum/3)),values[value]/60000.0,.009);
            }
            assertTrue(values[0]>0,"Minimum stays one in every tier");
        }
    }
    @Test void ultimateHas35DistinctEntriesAndEqualChanceForBooksAndEquipment() {
        var entries=GiftProductionTable.ultimate();assertEquals(35,new HashSet<>(entries).size());
        assertEquals(16,entries.stream().filter(r->!r.enchantment().isEmpty()).count());
        assertFalse(entries.stream().anyMatch(r->Set.of("minecraft:nether_star","minecraft:dragon_egg").contains(r.item())));
        int[] hits=new int[35];var random=new Random(31){public double nextDouble(){return 0;}};
        for(int i=0;i<35000;i++){var rewards=GiftProductionTable.draw("minecraft:villager","cave","ancient_city",3,r->true,r->false,random);hits[entries.indexOf(rewards.getLast().withCount(1))]++;}
        for(int hit:hits)assertTrue(hit>850&&hit<1150);
        assertEquals(.00005,GiftProductionTable.chance(0,3));assertEquals(.0005,GiftProductionTable.chance(3,3));
    }
    @Test void habitsPreventHerbivoreSelfMeatAndStructureTemplates() {
        Set<String> self=Set.of("minecraft:beef","minecraft:mutton","minecraft:porkchop","minecraft:rabbit","minecraft:leather","minecraft:rabbit_hide");
        for(var s:GiftProductionTable.species().values())if(s.habit().equals("grazer"))assertTrue(s.items().stream().noneMatch(r->self.contains(r.item())));
        for(String structure:GiftProductionTable.STRUCTURES)assertTrue(GiftProductionTable.precious("minecraft:sheep","grazer","cave",structure).isEmpty());
        assertEquals(3,GiftProductionTable.precious("minecraft:villager","collector","cave","ancient_city").size());
        assertEquals(2,GiftProductionTable.precious("minecraft:dolphin","aquatic","ocean","monument").size());
        assertThrows(IllegalArgumentException.class,()->GiftProductionTable.draw("othermod:cow","normal","none",0,r->true,r->false,new Random()));
    }
    @Test void missingEntriesAreSkippedWithoutBlockingOtherChannels() {
        var all=new Random(){public double nextDouble(){return 0;}public int nextInt(int bound){return 0;}};
        var result=GiftProductionTable.draw("minecraft:cow","normal","none",3,r->r.item().equals("minecraft:paper"),r->false,all);
        assertEquals(2,result.size());assertTrue(result.stream().allMatch(r->r.item().equals("minecraft:paper")));
    }
    @Test void adaptationPersistsShortTripsAndOnlyChangesFutureContent() {
        var h=new GiftHabitat();String plains="minecraft:overworld|plains|none",forest="minecraft:overworld|forest|none",desert="minecraft:overworld|desert|none";
        h.advance(plains,BigInteger.valueOf(72000));assertEquals(plains,h.stable());
        h.advance(forest,BigInteger.valueOf(24000));h.advance(desert,BigInteger.ONE);h=GiftHabitat.fromNbt(h.toNbt());
        assertEquals(24000,h.progress(forest));assertEquals(plains,h.stable());
        h.advance(forest,BigInteger.ZERO);assertEquals(24000,h.progress(forest));
        h.advance(forest,BigInteger.valueOf(47999));assertEquals(plains,h.stable());h.advance(forest,BigInteger.ONE);assertEquals(forest,h.stable());
        h.advance("minecraft:the_nether|nether|none",new BigInteger("18446744073709551615"));assertEquals("minecraft:the_nether|nether|none",h.stable());
    }
    @Test void oldPlainRewardsAndSpecificEnchantmentSurviveNbtAndTransactions() {
        var book=new GiftContent.Reward("minecraft:enchanted_book",1,"minecraft:mending",1);
        assertEquals(book,GiftContent.Reward.fromNbt(book.toNbt()));
        assertEquals(new GiftContent.Reward("minecraft:paper",3),GiftContent.Reward.fromNbt(new GiftContent.Reward("minecraft:paper",3).toNbt()));
        assertThrows(IllegalArgumentException.class,()->book.withCount(2));
        assertThrows(IllegalArgumentException.class,()->new GiftContent.Reward("minecraft:paper",1,"minecraft:mending",1));
        var content=new GiftContent(UUID.randomUUID(),UUID.randomUUID(),"entity:"+UUID.randomUUID(),"伙伴","minecraft:villager",3,3,5,List.of(book),List.of());
        assertEquals(content,GiftContent.fromNbt(content.toNbt()));
        var ledger=new GiftLedger();ledger.habitat(content.creature()).advance("minecraft:overworld|forest|none",BigInteger.valueOf(12345));
        assertEquals(12345,GiftLedger.fromNbt(ledger.toNbt()).habitat(content.creature()).progress("minecraft:overworld|forest|none"));
    }
}
