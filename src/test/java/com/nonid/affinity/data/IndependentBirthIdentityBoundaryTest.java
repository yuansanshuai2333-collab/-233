package com.nonid.affinity.data;
import java.util.*;import com.google.gson.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class IndependentBirthIdentityBoundaryTest {
 static final UUID M=new UUID(0,1),F=new UUID(0,2);
 FamilyGraph.Node mother(){return new FamilyGraph.Node("player:"+M,"mother","minecraft:player",2,FamilyGraph.NameKind.PLAYER);}
 FamilyGraph.Node father(){return new FamilyGraph.Node("entity:"+F,"father","minecraft:pig",1,FamilyGraph.NameKind.CUSTOM);}
 String donor(){return BirthIdentity.encodeDonor(new BirthIdentity.Donor(father(),Set.of(M)));}
 JsonObject object(String s){return JsonParser.parseString(s).getAsJsonObject();}
 @Test void rejectMissingAndNullDonorParts(){for(String s:List.of("{}","null","[]","42","{\"node\":null,\"isolatedReceivers\":[]}","{\"isolatedReceivers\":[]}"))assertNull(BirthIdentity.decodeDonor(s),s);}
 @Test void invalidCanonicalUuidAndLongNamesSupplyNoIdentity(){var j=object(donor());j.getAsJsonObject("node").addProperty("id","entity:0-0-0-0-2");assertNull(BirthIdentity.decodeDonor(j.toString()));j=object(donor());j.getAsJsonObject("node").addProperty("name","x".repeat(257));assertNull(BirthIdentity.decodeDonor(j.toString()));}
 @Test void isolatedReceiverNullAndMalformedUuidSupplyNoIdentity(){var j=object(donor());j.add("isolatedReceivers",JsonParser.parseString("[null]"));assertNull(BirthIdentity.decodeDonor(j.toString()));j.add("isolatedReceivers",JsonParser.parseString("[\"not_uuid\"]"));assertNull(BirthIdentity.decodeDonor(j.toString()));}
 @Test void trailingCorruptionAndOversizeSupplyNoIdentity(){assertNull(BirthIdentity.decodeDonor(donor()+"garbage"));assertNull(BirthIdentity.decodeDonor(donor()+" ".repeat(16384)));}
 @Test void duplicateReceiversRemainDistinctSetAndUnknownKeysPreserve(){var j=object(donor());j.add("isolatedReceivers",JsonParser.parseString("[\""+M+"\",\""+M+"\"]"));j.addProperty("otherMod:unused","x");assertEquals(Set.of(M),BirthIdentity.decodeDonor(j.toString()).isolatedReceivers());}
 @Test void malformedIsolationBooleanMustNotTurnProtectedEvidenceIntoUnprotectedBirth(){var b=new BirthIdentity.Birth(mother(),null,true);var n=BirthIdentity.toNbt(b);var j=object(n.getString("identity",""));j.addProperty("isolated","not-a-boolean");n.putString("identity",j.toString());assertNull(BirthIdentity.fromNbt(n),"A malformed isolation field must supply no birth identity");}
 @Test void ordinaryProtectedAndOtherReceiverRemainIndependent(){var d=BirthIdentity.decodeDonor(donor());assertTrue(BirthIdentity.birth(mother(),"minecraft:pig",d).isolated());var other=new FamilyGraph.Node("player:"+new UUID(0,3),"other","minecraft:player",2);assertEquals(father(),BirthIdentity.birth(other,"minecraft:pig",d).father());}
 @Test void namespaceAndSourceMismatchDegradeWithoutInventingFather(){assertEquals("noncreatureaffinity:birth_source_v1",BirthIdentity.COMPONENT);assertNull(BirthIdentity.birth(mother(),"minecraft:cow",BirthIdentity.decodeDonor(donor())).father());}
}
