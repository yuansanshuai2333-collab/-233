package com.nonid.affinity.reunion;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReunionSnapshotTest {
    private static void sanitize(NbtCompound entity) {
        ReunionService.sanitize(entity, id -> id.toString().equals("minecraft:diamond"));
    }
    private static NbtCompound stack() {
        var n = new NbtCompound(); n.putString("id", "minecraft:diamond"); n.putInt("count", 64); return n;
    }
    @Test void discardedEquipmentCannotReturn() {
        var entity = new NbtCompound(); entity.put("equipment", stack()); entity.put("Inventory", new NbtList());
        entity.putString("CustomName", "Friend"); entity.putInt("Age", -100);
        sanitize(entity);
        assertFalse(entity.contains("equipment")); assertFalse(entity.contains("Inventory"));
        assertEquals("Friend", entity.getString("CustomName", "")); assertEquals(-100,entity.getInt("Age",0));
    }
    @Test void nestedModInventoryStacksAreRemoved() {
        var entity = new NbtCompound(); var external = new NbtCompound(); var list = new NbtList();
        list.add(stack()); external.put("slots", list); external.put("held", stack()); external.putString("personality","friendly");
        entity.put("example_mod",external); sanitize(entity);
        assertTrue(list.isEmpty()); assertFalse(external.contains("held")); assertEquals("friendly",external.getString("personality",""));
    }
    @Test void tradeTemplatesAreNotPossessions() {
        var entity = new NbtCompound(); var offers = new NbtCompound(); offers.put("buy", stack());
        entity.put("Offers", offers); sanitize(entity); assertEquals(stack(),offers.getCompoundOrEmpty("buy"));
    }
    @Test void unsafeTransientStatesAreNotRestored() {
        var entity = new NbtCompound();
        for(var key : new String[]{"Passengers","Leash","Brain","Pos","Motion","Fire","Health","DeathTime"}) entity.putInt(key,5);
        sanitize(entity); assertTrue(entity.isEmpty());
    }
}
