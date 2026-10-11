package com.nonid.affinity.data;
import java.util.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.*;
import net.minecraft.storage.*;
import net.minecraft.util.ErrorReporter;
import net.minecraft.registry.RegistryWrapper;

class BirthVariantStorageTest {
    private static final String KEY="NeedsOfNaturePregnancyVariantData";
    private static NbtCompound roundtrip(NbtCompound n)throws IOException{
        var bytes=new ByteArrayOutputStream();NbtIo.writeCompound(n,new DataOutputStream(bytes));
        return NbtIo.readCompound(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }
    private static ReadView reader(NbtCompound n){return BirthVariantStorage.reader(NbtReadView.create(ErrorReporter.EMPTY,RegistryWrapper.WrapperLookup.of(java.util.stream.Stream.empty()),n));}
    private static String value(int count){
        var ids=new HashSet<UUID>();var random=new Random(33);while(ids.size()<count)ids.add(new UUID(random.nextLong(),random.nextLong()));
        var donor=new BirthIdentity.Donor(new FamilyGraph.Node("entity:"+new UUID(1,1),"父源","minecraft:pig",1),ids);
        return new com.google.gson.Gson().toJson(Map.of("components",Map.of(BirthIdentity.COMPONENT,BirthIdentity.encodeDonor(donor),"third:appearance","kept"),"horseMarkingIndex",2));
    }
    @Test void largeCarrierSurvivesActualNbtStorageInsteadOfOversizedString()throws Exception{
        for(int count:new int[]{500,3000,10000})for(String key:List.of(KEY,"NeedsOfNatureLiquidDonorVariantData","NeedsOfNatureEggVariantData")){
            String raw=value(count);var storage=NbtWriteView.create(ErrorReporter.EMPTY);BirthVariantStorage.writer(storage).putString(key,raw);
            assertEquals(raw,reader(roundtrip(storage.getNbt())).getOptionalString(key).orElseThrow());
            if(count==10000){assertEquals(BirthVariantStorage.MARKER,storage.getNbt().getString(key,""));assertTrue(storage.getNbt().getCompound(BirthVariantStorage.sidecar(key)).isPresent());}
            else assertEquals(raw,storage.getNbt().getString(key,""));
        }
    }
    @Test void normalAndUnrelatedWritesStayStringsAndReplacementRemovesSidecar(){
        var n=NbtWriteView.create(ErrorReporter.EMPTY);var w=BirthVariantStorage.writer(n);String large=value(10000);
        w.putString(KEY,large);w.putString(KEY,"{\"components\":{}}");assertFalse(n.getNbt().contains(BirthVariantStorage.sidecar(KEY)));
        w.putString("unrelated",large);assertEquals(large,n.getNbt().getString("unrelated",""));
        w.putString(KEY,large);w.remove(KEY);assertFalse(n.getNbt().contains(KEY));assertFalse(n.getNbt().contains(BirthVariantStorage.sidecar(KEY)));
    }
    @Test void missingOrCorruptBytesNeverSilentlyReturnAnUnprotectedVariant(){
        var n=NbtWriteView.create(ErrorReporter.EMPTY);BirthVariantStorage.writer(n).putString(KEY,value(10000));
        var root=n.getNbt();var packed=root.getCompound(BirthVariantStorage.sidecar(KEY)).orElseThrow();
        packed.putInt("chars",1);assertThrows(IllegalStateException.class,()->reader(root).getOptionalString(KEY));
        root.remove(BirthVariantStorage.sidecar(KEY));assertThrows(IllegalStateException.class,()->reader(root).getOptionalString(KEY));
    }
    @Test void codecPreservesAllJavaCodeUnitsAndChecksDigest()throws Exception{
        String raw="\u0000\ud800x\udc00中文";var packed=roundtrip(BirthVariantStorage.pack(raw));assertEquals(raw,BirthVariantStorage.unpack(packed));
        var data=packed.getByteArray("data").orElseThrow();data[0]^=1;packed.putByteArray("data",data);assertThrows(IllegalStateException.class,()->BirthVariantStorage.unpack(packed));
    }
    @Test void boundaryUsesModifiedUtfBytesRatherThanCharacters(){
        String marker="\""+BirthIdentity.COMPONENT+"\"";
        assertFalse(BirthVariantStorage.needsStructured(KEY,marker+"a".repeat(60000)));
        assertTrue(BirthVariantStorage.needsStructured(KEY,marker+"中".repeat(22000)));
        assertFalse(BirthVariantStorage.needsStructured("unrelated",marker+"中".repeat(22000)));
        assertFalse(BirthVariantStorage.needsStructured(KEY,"x".repeat(100000)));
    }
    @Test void entityCustomDataChildUsesTheSameStorageContract()throws Exception{
        String raw=value(10000);var n=NbtWriteView.create(ErrorReporter.EMPTY);
        BirthVariantStorage.writer(n).get("data").putString("NeedsOfNatureEggVariantData",raw);
        var read=reader(roundtrip(n.getNbt()));
        assertEquals(raw,read.getReadView("data").getString("NeedsOfNatureEggVariantData",""));
        assertEquals(raw,read.getOptionalReadView("data").orElseThrow().getOptionalString("NeedsOfNatureEggVariantData").orElseThrow());
    }
    @Test void aggregateDonorHistorySurvivesEvenWhenIndividualVariantsFit()throws Exception{
        var gson=new com.google.gson.Gson();
        var variant=gson.fromJson(value(1500),com.google.gson.JsonObject.class);
        var entry=Map.of("entityTypeId","minecraft:pig","variantData",variant);
        String raw=gson.toJson(List.of(entry,entry,entry));
        assertFalse(BirthVariantStorage.needsStructured(KEY,value(1500)));
        String key="NeedsOfNatureLiquidDonorHistory";
        assertTrue(BirthVariantStorage.needsStructured(key,raw));
        var n=NbtWriteView.create(ErrorReporter.EMPTY);BirthVariantStorage.writer(n).putString(key,raw);
        String restored=reader(roundtrip(n.getNbt())).getString(key,"");
        assertEquals(raw,restored);assertEquals(3,com.google.gson.JsonParser.parseString(restored).getAsJsonArray().size());
    }
    @Test
    void corruptSidecarIsIsolatedFromUnrelatedStoredValue() throws Exception {
        var n = NbtWriteView.create(ErrorReporter.EMPTY);
        var writer = BirthVariantStorage.writer(n);
        String large = value(10000);
        writer.putString(KEY, large);
        writer.putString("unrelated", "kept");
        var root = n.getNbt();
        root.getCompound(BirthVariantStorage.sidecar(KEY)).orElseThrow().putInt("chars", 1);

        var read = reader(roundtrip(root));

        assertThrows(IllegalStateException.class, () -> read.getOptionalString(KEY));
        assertEquals("kept", read.getOptionalString("unrelated").orElseThrow());
    }
}
