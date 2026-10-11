package com.nonid.affinity.data;

import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BirthIdentityRepairTest {
    private final UUID receiver=new UUID(0,1);
    private FamilyGraph.Node mother(UUID id){return new FamilyGraph.Node("player:"+id,"Mother","minecraft:player",2);}
    private FamilyGraph.Node father(){return new FamilyGraph.Node("entity:"+new UUID(0,2),"Father","minecraft:pig",1);}
    @Test void malformedBirthTypesAndMissingStructureAreRejected(){
        for(String value:List.of("\"false\"","0","null","[]","{}")){
            var n=BirthIdentity.toNbt(new BirthIdentity.Birth(mother(receiver),null,true));
            var j=JsonParser.parseString(n.getString("identity","")).getAsJsonObject();
            j.add("isolated",JsonParser.parseString(value));n.putString("identity",j.toString());
            assertNull(BirthIdentity.fromNbt(n),value);
        }
        var n=BirthIdentity.toNbt(new BirthIdentity.Birth(mother(receiver),null,true));
        var j=JsonParser.parseString(n.getString("identity","")).getAsJsonObject();j.remove("isolated");n.putString("identity",j.toString());assertNull(BirthIdentity.fromNbt(n));
    }
    @Test void largeSetsRoundTripWithoutDroppingAnyReceiverOrAffectingOthers(){
        for(int count:List.of(400,500,1000,10000)){
            var ids=new HashSet<UUID>();ids.add(receiver);for(int i=0;i<count;i++)ids.add(new UUID(12,i));
            var donor=new BirthIdentity.Donor(father(),ids);String encoded=BirthIdentity.encodeDonor(donor);
            assertEquals(donor,BirthIdentity.decodeDonor(encoded));
            var birth=BirthIdentity.birth(mother(receiver),"minecraft:pig",BirthIdentity.decodeDonor(encoded));
            assertTrue(BirthIdentity.fromNbt(BirthIdentity.toNbt(birth)).isolated());
            assertEquals(father(),BirthIdentity.birth(mother(new UUID(99,99)),"minecraft:pig",BirthIdentity.decodeDonor(encoded)).father());
        }
    }
    @Test void malformedOrOversizedBlocksNeverReturnPartialEvidence(){
        var ids=new HashSet<UUID>();for(int i=0;i<1000;i++)ids.add(new UUID(0,i));
        String encoded=BirthIdentity.encodeDonor(new BirthIdentity.Donor(father(),ids));assertTrue(encoded.startsWith("NR2\n"));
        for(String bad:List.of(encoded+"garbage",encoded+"A".repeat(3000)+"\n",encoded+"AAAA\n",encoded.substring(0,encoded.length()-1)))assertNull(BirthIdentity.decodeDonor(bad));
        assertNull(BirthIdentity.decodeDonor(encoded.substring(0,encoded.lastIndexOf('\n',encoded.length()-2)+1)),"Missing a complete final block is still corruption");
    }
    @Test void mismatchedSpeciesLosesFatherButKeepsOnlyCorrespondingIsolation(){
        var donor=new BirthIdentity.Donor(father(),Set.of(receiver));
        var protectedBirth=BirthIdentity.birth(mother(receiver),"minecraft:cow",donor);
        assertTrue(protectedBirth.isolated());assertNull(protectedBirth.father());
        var other=BirthIdentity.birth(mother(new UUID(9,9)),"minecraft:cow",donor);
        assertFalse(other.isolated());assertNull(other.father());
    }
}
