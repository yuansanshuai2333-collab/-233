package com.nonid.affinity.data;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IndependentNr2AuditTest {
 private BirthIdentity.Donor donor(int n){var ids=new HashSet<UUID>();for(int i=0;i<n;i++)ids.add(new UUID(7,i));return new BirthIdentity.Donor(new FamilyGraph.Node("entity:"+new UUID(8,1),"父源","minecraft:pig",1),ids);}
 @Test void boundariesRetainEveryUuid(){for(int n:new int[]{0,1,127,128,129,399,400,401,511,512,513,1024}){var d=donor(n);assertEquals(d,BirthIdentity.decodeDonor(BirthIdentity.encodeDonor(d)),"count="+n);}}
 @Test void declaredCountCannotHideMissingOrExtraData(){String s=BirthIdentity.encodeDonor(donor(513));String[] lines=s.split("\n");for(String count:List.of("0","-1","0513","512","514","2147483648","99999999999")){var t=lines.clone();t[2]=count;assertNull(BirthIdentity.decodeDonor(String.join("\n",t)+"\n"),count);}}
 @Test void duplicateAndMalformedBlocksRejectWholeRecord(){String s=BirthIdentity.encodeDonor(donor(513));var l=s.split("\n");assertNull(BirthIdentity.decodeDonor(s+l[3]+"\n"));var t=l.clone();t[4]=t[3];assertNull(BirthIdentity.decodeDonor(String.join("\n",t)+"\n"));for(String block:List.of("", "!", "AAAA", "A".repeat(2733))){t=l.clone();t[3]=block;assertNull(BirthIdentity.decodeDonor(String.join("\n",t)+"\n"));}}
 @Test void damagedNodeAndDeepUnknownExtensionsReject(){String s=BirthIdentity.encodeDonor(donor(513));var l=s.split("\n");for(String node:List.of("null","[]","{}",l[1].replace("\"gender\":1","\"gender\":\"1\""),"{"+"\"x\":"+"[".repeat(10)+"0"+"]".repeat(10)+"}")){var t=l.clone();t[1]=node;assertNull(BirthIdentity.decodeDonor(String.join("\n",t)+"\n"));}}
 @Test void blockOrderDoesNotAlterIsolationSet(){String s=BirthIdentity.encodeDonor(donor(513));var l=s.split("\n");String tmp=l[3];l[3]=l[4];l[4]=tmp;assertEquals(donor(513),BirthIdentity.decodeDonor(String.join("\n",l)+"\n"));}

 // Original raw-string reproduction preserved in evidence; replacement storage is exercised by BirthVariantStorageTest.
}
