package com.nonid.affinity.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;

/** Bounded identity evidence, never a request to create, tame, or select a parent. */
public final class BirthIdentity {
    public static final String COMPONENT = "noncreatureaffinity:birth_source_v1";
    private static final Gson JSON = new Gson();
    private static final int MAX_JSON = 16384;
    private BirthIdentity() {}

    public record Donor(FamilyGraph.Node node, Set<UUID> isolatedReceivers) {
        public Donor {
            if (node == null || isolatedReceivers == null) throw new IllegalArgumentException("Missing donor evidence");
            isolatedReceivers = Set.copyOf(isolatedReceivers);
        }
    }
    public record Birth(FamilyGraph.Node mother, FamilyGraph.Node father, boolean isolated) {
        public Birth {
            if (mother == null || !mother.player()) throw new IllegalArgumentException("Missing exact mother");
            if (father != null && father.id().equals(mother.id())) throw new IllegalArgumentException("Duplicate parent");
        }
        public UUID viewer() { return UUID.fromString(mother.id().substring(7)); }
    }
    /** Small legacy records remain readable. Larger sets use bounded 128-UUID blocks,
     * not one oversized JSON tree. No participant is truncated or globally isolated. */
    public static String encodeDonor(Donor donor) {
        String legacy = JSON.toJson(donor);
        if (legacy.length() <= MAX_JSON) return legacy;
        var result = new StringBuilder("NR2\n").append(JSON.toJson(donor.node())).append('\n')
                .append(donor.isolatedReceivers().size()).append('\n');
        var block = ByteBuffer.allocate(128 * 16);
        for (UUID id : new java.util.TreeSet<>(donor.isolatedReceivers())) {
            block.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
            if (!block.hasRemaining()) { appendBlock(result, block); block.clear(); }
        }
        if (block.position() > 0) appendBlock(result, block);
        return result.toString();
    }
    private static void appendBlock(StringBuilder out, ByteBuffer block) {
        out.append(Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(block.array(), block.position()))).append('\n');
    }
    public static Donor decodeDonor(String value) {
        if (value == null) return null;
        if (!value.startsWith("NR2\n")) return decode(value, Donor.class);
        try {
            int end = value.indexOf('\n', 4);
            if (end < 0 || end - 4 > MAX_JSON) return null;
            var nodeJson = object(value.substring(4, end));
            validateNode(nodeJson);
            var node = JSON.fromJson(nodeJson, FamilyGraph.Node.class);
            var receivers = new HashSet<UUID>();
            int start = end + 1;
            end=value.indexOf('\n',start);
            if(end<0 || end-start>10 || !value.substring(start,end).matches("[1-9][0-9]*")) return null;
            int count=Integer.parseInt(value.substring(start,end));start=end+1;
            while (start < value.length()) {
                end = value.indexOf('\n', start);
                if (end < 0 || end == start || end - start > 2732) return null;
                String encoded = value.substring(start, end);
                byte[] bytes = Base64.getDecoder().decode(encoded);
                if (bytes.length == 0 || bytes.length > 2048 || bytes.length % 16 != 0
                        || !Base64.getEncoder().encodeToString(bytes).equals(encoded)) return null;
                var block = ByteBuffer.wrap(bytes);
                while (block.hasRemaining()) {
                    if(!receivers.add(new UUID(block.getLong(), block.getLong())) || receivers.size()>count) return null;
                }
                start = end + 1;
            }
            return receivers.size()==count ? new Donor(node, receivers) : null;
        } catch (RuntimeException invalid) { return null; }
    }
    public static Birth birth(FamilyGraph.Node mother, String sourceType, Donor donor) {
        boolean isolated = donor != null && donor.isolatedReceivers().contains(UUID.fromString(mother.id().substring(7)));
        var father = donor != null && donor.node().type().equals(sourceType) && !isolated ? donor.node() : null;
        return new Birth(mother, father, isolated);
    }
    public static NbtCompound toNbt(Birth birth) {
        var nbt = new NbtCompound(); nbt.putString("identity", JSON.toJson(birth)); return nbt;
    }
    public static Birth fromNbt(NbtCompound nbt) {
        return nbt == null ? null : decode(nbt.getString("identity", ""), Birth.class);
    }
    private static <T> T decode(String value, Class<T> type) {
        if (value == null || value.isBlank() || value.length() > MAX_JSON) return null;
        try {
            var json = object(value);
            if (type == Birth.class) {
                validateNode(json.get("mother"));
                if (json.has("father") && !json.get("father").isJsonNull()) validateNode(json.get("father"));
                var flag = json.get("isolated");
                if (flag == null || !flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean()) return null;
            } else {
                validateNode(json.get("node"));
                var receivers = json.get("isolatedReceivers");
                if (receivers == null || !receivers.isJsonArray()) return null;
                for (var id : receivers.getAsJsonArray()) {
                    String s = string(id, 36);
                    if (!UUID.fromString(s).toString().equals(s)) return null;
                }
            }
            return JSON.fromJson(json, type);
        }
        catch (RuntimeException malformedEvidence) { return null; } // Invalid/old metadata supplies no identity.
    }
    private static JsonObject object(String value) {
        // Bound nesting before Gson builds a tree, including unrecognized extension fields.
        boolean quoted=false, escaped=false; int depth=0;
        for (int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if (quoted) { if(escaped) escaped=false; else if(c=='\\') escaped=true; else if(c=='"') quoted=false; }
            else if(c=='"') quoted=true;
            else if(c=='{' || c=='[') { if(++depth>8) throw new IllegalArgumentException("Deep identity"); }
            else if(c=='}' || c==']') depth--;
        }
        return JsonParser.parseString(value).getAsJsonObject();
    }
    private static String string(JsonElement value, int max) {
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected string");
        String result=value.getAsString(); if(result.length()>max) throw new IllegalArgumentException("Long identity field"); return result;
    }
    private static void validateNode(JsonElement value) {
        if(value==null || !value.isJsonObject()) throw new IllegalArgumentException("Missing node");
        var node=value.getAsJsonObject();
        if(!FamilyGraph.validId(string(node.get("id"),80))) throw new IllegalArgumentException("Invalid identity");
        string(node.get("name"),256); string(node.get("type"),256);
        var gender=node.get("gender");
        if(gender==null || !gender.isJsonPrimitive() || !gender.getAsJsonPrimitive().isNumber()
                || !gender.getAsString().matches("[012]")) throw new IllegalArgumentException("Invalid gender");
        if(node.has("nameKind")) FamilyGraph.NameKind.valueOf(string(node.get("nameKind"),32));
    }
}
