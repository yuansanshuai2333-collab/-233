package com.nonid.affinity.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.Set;
import java.security.MessageDigest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;

/** Entity-local persistence adapter for the four NoN identity-bearing fields.
 * Long identity-bearing JSON is stored as an inline NBT byte array, never a reference
 * or an oversized NBT String. NoN receives the exact original Java string on read. */
public final class BirthVariantStorage {
    public static final String MARKER="noncreatureaffinity:variant_bytes_v1";
    private static final Set<String> KEYS=Set.of("NeedsOfNaturePregnancyVariantData",
            "NeedsOfNatureLiquidDonorVariantData","NeedsOfNatureLiquidDonorHistory","NeedsOfNatureEggVariantData");
    private BirthVariantStorage() {}
    public static String sidecar(String key){return "noncreatureaffinity:"+key;}
    public static boolean needsStructured(String key,String value){
        if(!KEYS.contains(key)||value==null||!value.contains("\""+BirthIdentity.COMPONENT+"\""))return false;
        int bytes=0;
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);bytes+=c>=1&&c<=127?1:c<=2047?2:3;
            if(bytes>65535)return true;
        }
        return false;
    }
    public static NbtCompound pack(String value){
        // Two bytes per Java code unit preserves even unpaired surrogates exactly.
        byte[] bytes=new byte[Math.multiplyExact(value.length(),2)];
        for(int i=0;i<value.length();i++){bytes[2*i]=(byte)(value.charAt(i)>>>8);bytes[2*i+1]=(byte)value.charAt(i);}
        var n=new NbtCompound();n.putInt("version",1);n.putInt("chars",value.length());
        n.putByteArray("data",bytes);n.putByteArray("sha256",digest(bytes));return n;
    }
    public static String unpack(NbtCompound n){
        byte[] bytes=n.getByteArray("data").orElseThrow(()->corrupt("missing bytes"));
        byte[] hash=n.getByteArray("sha256").orElseThrow(()->corrupt("missing digest"));
        int chars=n.getInt("chars",-1);
        if(n.getInt("version",-1)!=1 || chars<0 || (long)chars*2!=bytes.length
                || hash.length!=32 || !MessageDigest.isEqual(hash,digest(bytes)))throw corrupt("version, length or digest mismatch");
        char[] value=new char[chars];for(int i=0;i<chars;i++)value[i]=(char)((bytes[2*i]&255)<<8|(bytes[2*i+1]&255));
        return new String(value);
    }
    private static IllegalStateException corrupt(String reason){return new IllegalStateException("Invalid Natural Relationships inline variant storage: "+reason);}
    private static byte[] digest(byte[] bytes){
        try{return MessageDigest.getInstance("SHA-256").digest(bytes);}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    public static WriteView writer(WriteView view){return view instanceof Writer?view:new Writer(view);}
    public static ReadView reader(ReadView view){return view instanceof Reader?view:new Reader(view);}
    private record Writer(WriteView delegate) implements WriteView {
        @Override public void putString(String key,String value){
            if(needsStructured(key,value)){
                delegate.put(sidecar(key),NbtCompound.CODEC,pack(value));delegate.putString(key,MARKER);
            }else{
                delegate.putString(key,value);if(KEYS.contains(key))delegate.remove(sidecar(key));
            }
        }
        public <T> void put(String k,Codec<T> c,T v){delegate.put(k,c,v);}
        public <T> void putNullable(String k,Codec<T> c,T v){delegate.putNullable(k,c,v);}
        public <T> void put(MapCodec<T> c,T v){delegate.put(c,v);}
        public void putBoolean(String k,boolean v){delegate.putBoolean(k,v);}
        public void putByte(String k,byte v){delegate.putByte(k,v);}
        public void putShort(String k,short v){delegate.putShort(k,v);}
        public void putInt(String k,int v){delegate.putInt(k,v);}
        public void putLong(String k,long v){delegate.putLong(k,v);}
        public void putFloat(String k,float v){delegate.putFloat(k,v);}
        public void putDouble(String k,double v){delegate.putDouble(k,v);}
        public void putIntArray(String k,int[] v){delegate.putIntArray(k,v);}
        public WriteView get(String k){return writer(delegate.get(k));}
        public ListView getList(String k){return delegate.getList(k);}
        public <T> ListAppender<T> getListAppender(String k,Codec<T> c){return delegate.getListAppender(k,c);}
        public void remove(String k){delegate.remove(k);if(KEYS.contains(k))delegate.remove(sidecar(k));}
        public boolean isEmpty(){return delegate.isEmpty();}
    }
    private record Reader(ReadView delegate) implements ReadView {
        @Override public Optional<String> getOptionalString(String key){
            var original=delegate.getOptionalString(key);
            if(!KEYS.contains(key)||!original.filter(MARKER::equals).isPresent())return original;
            // A damaged marker/sidecar must not silently become a normal unprotected birth.
            return Optional.of(unpack(delegate.read(sidecar(key),NbtCompound.CODEC)
                    .orElseThrow(()->corrupt("missing sidecar for "+key))));
        }
        @Override public String getString(String k,String d){return getOptionalString(k).orElse(d);}
        public <T> Optional<T> read(String k,Codec<T> c){return delegate.read(k,c);}
        public <T> Optional<T> read(MapCodec<T> c){return delegate.read(c);}
        public Optional<ReadView> getOptionalReadView(String k){return delegate.getOptionalReadView(k).map(BirthVariantStorage::reader);}
        public ReadView getReadView(String k){return reader(delegate.getReadView(k));}
        public Optional<ListReadView> getOptionalListReadView(String k){return delegate.getOptionalListReadView(k);}
        public ListReadView getListReadView(String k){return delegate.getListReadView(k);}
        public <T> Optional<TypedListReadView<T>> getOptionalTypedListView(String k,Codec<T> c){return delegate.getOptionalTypedListView(k,c);}
        public <T> TypedListReadView<T> getTypedListView(String k,Codec<T> c){return delegate.getTypedListView(k,c);}
        public boolean getBoolean(String k,boolean d){return delegate.getBoolean(k,d);}
        public byte getByte(String k,byte d){return delegate.getByte(k,d);}
        public int getShort(String k,short d){return delegate.getShort(k,d);}
        public Optional<Integer> getOptionalInt(String k){return delegate.getOptionalInt(k);}
        public int getInt(String k,int d){return delegate.getInt(k,d);}
        public long getLong(String k,long d){return delegate.getLong(k,d);}
        public Optional<Long> getOptionalLong(String k){return delegate.getOptionalLong(k);}
        public float getFloat(String k,float d){return delegate.getFloat(k,d);}
        public double getDouble(String k,double d){return delegate.getDouble(k,d);}
        public Optional<int[]> getOptionalIntArray(String k){return delegate.getOptionalIntArray(k);}
        public RegistryWrapper.WrapperLookup getRegistries(){return delegate.getRegistries();}
    }
}
