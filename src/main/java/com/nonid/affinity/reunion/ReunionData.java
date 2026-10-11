package com.nonid.affinity.reunion;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.data.NbtCompoundCodec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

/** The overworld owns the ledger, regardless of player presence or the item's container. */
public final class ReunionData {
    public static final AttachmentType<NbtCompound> LEDGER = AttachmentRegistry.create(
            id("reunion_ledger"), b -> b.initializer(NbtCompound::new)
                    .persistent(NbtCompoundCodec.of(NbtCompound::copy, NbtCompound::copy)));
    public static Identifier id(String path) { return Identifier.of(NonCreatureAffinity.MOD_ID, path); }
    public static void init() { }
    public static NbtCompound ledger(MinecraftServer server) {
        var value = server.getOverworld().getAttached(LEDGER);
        return value == null ? new NbtCompound() : value.copy();
    }
    public static NbtCompound record(MinecraftServer server, String mob) {
        var ledger = server.getOverworld().getAttached(LEDGER);
        return ledger == null ? new NbtCompound() : ledger.getCompoundOrEmpty(mob).copy();
    }
    public static void save(MinecraftServer server, String mob, NbtCompound record) {
        var all = ledger(server); all.put(mob, record.copy());
        server.getOverworld().setAttached(LEDGER, all);
    }
    public static NbtCompound item(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .copyNbt().getCompoundOrEmpty("reunion").copy();
    }
    public static void item(ItemStack stack, NbtCompound data) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, n -> n.put("reunion", data.copy()));
        int energy = data.getInt("energy", data.getBoolean("dim", false) ? 0 : 3);
        String state = !data.contains("mob") ? "unbound" : energy <= 0 ? "dim" : energy == 1 ? "one" : energy == 2 ? "two" : "charged";
        stack.set(DataComponentTypes.ITEM_MODEL, id("reunion_" + state));
    }
    /** An item copy can never spend the same generation twice. */
    public static boolean charged(NbtCompound item, NbtCompound record) {
        return energy(item, record) == 3;
    }
    public static long fuel(NbtCompound item) {
        return item.getLong("fuel", ReunionEnergy.legacy(item.getLong("charge", 0)));
    }
    public static long spent(NbtCompound record) {
        return record.getLong("spentFuel", ReunionEnergy.legacy(record.getLong("spent", 0)));
    }
    public static int energy(NbtCompound item, NbtCompound record) {
        return ReunionEnergy.remaining(fuel(item), spent(record));
    }
    public static boolean authentic(NbtCompound item, NbtCompound record) {
        String bond = record.getString("bond", "");
        return !bond.isEmpty() && bond.equals(item.getString("bond", ""));
    }
    private ReunionData() { }
}
