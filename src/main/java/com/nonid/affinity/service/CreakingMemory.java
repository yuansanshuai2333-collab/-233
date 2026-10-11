package com.nonid.affinity.service;

import com.nonid.affinity.data.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.block.entity.CreakingHeartBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.mob.CreakingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.List;
import java.util.UUID;

/** Relationships belong to the heart; vanilla remains responsible for the puppet's lifetime. */
public final class CreakingMemory {
    private static final ThreadLocal<MobEntity> GAZE = new ThreadLocal<>();
    public static boolean isGazeCheck(MobEntity mob) { return GAZE.get() == mob; }
    public static boolean gazeCanTarget(CreakingEntity mob, net.minecraft.entity.LivingEntity player) {
        return gazeCanTarget(mob, () -> mob.canTarget(player));
    }
    /** Preserve the complete operation chain supplied by other mixins while bypassing only our friendship gate. */
    public static boolean gazeCanTarget(CreakingEntity mob, java.util.function.BooleanSupplier original) {
        var previous = GAZE.get();
        GAZE.set(mob);
        try { return original.getAsBoolean(); }
        finally { if (previous == null) GAZE.remove(); else GAZE.set(previous); }
    }
    public static boolean inHomeRange(MobEntity mob, net.minecraft.entity.LivingEntity target) {
        if (!bound(mob)) return true;
        return target != null && target.getEntityWorld() == mob.getEntityWorld()
                && ((CreakingEntity)mob).getHomePos().getSquaredDistance(target.getBlockPos()) < 32 * 32;
    }
    public interface Heart {
        NbtCompound relationshipMemory();
        void relationshipMemory(NbtCompound value);
        boolean relationshipSourceRemoved();
    }
    private static final String ITEM_KEY = "noncreatureaffinity_creaking_memory";
    public static final AttachmentType<NbtCompound> TRANSFERS = AttachmentRegistry.create(
            Identifier.of("noncreatureaffinity", "creaking_transfers"), b -> b.initializer(NbtCompound::new)
                    .persistent(NbtCompoundCodec.of(NbtCompound::copy, NbtCompound::copy)));
    public static void init() { }
    public static boolean bound(MobEntity mob) {
        return mob instanceof CreakingEntity creaking && creaking.getHomePos() != null;
    }
    public static void capture(CreakingHeartBlockEntity heart, CreakingEntity puppet) {
        if (puppet == null || !(puppet instanceof AffinityHolder holder)) return;
        var data = holder.peekAffinityData();
        NbtCompound memory = ((Heart) heart).relationshipMemory().copy();
        if (data == null && !memory.contains("affinity")) return;
        boolean replay = ReplayIsolation.isActor(puppet.getUuid());
        var source = data == null ? new CreatureAffinityData() : data;
        var saved = replay ? CreatureAffinityData.fromNbt(memory.getCompoundOrEmpty("affinity").copy())
                : CreatureAffinityData.fromNbt(source.toNbt());
        if (replay) {
            // Preserve only the replay participants' previous records. Other players remain live.
            saved.entries().keySet().removeIf(key -> !ReplayIsolation.protectsKey(puppet.getUuid(), key)
                    && !source.entries().containsKey(key));
            source.entries().forEach((key, relation) -> {
                if (!ReplayIsolation.protectsKey(puppet.getUuid(), key))
                    saved.entries().put(key, PlayerAffinity.fromNbt(relation.toNbt()));
            });
        }
        if (saved.isEmpty()) memory.remove("affinity");
        else memory.put("affinity", saved.toNbt());
        if (replay) {
            ((Heart) heart).relationshipMemory(memory);
            return;
        }
        int gender = com.nonid.affinity.non.NonBridge.rememberedGender(puppet);
        if (gender != 0) memory.putInt("non_gender", gender);
        else memory.remove("non_gender"); // NONE/unknown retains native generation, including old saves.
        memory.putBoolean("persistent", puppet.isPersistent());
        if (puppet.getCustomName() != null) memory.putString("name", puppet.getCustomName().getString());
        else memory.remove("name");
        memory.remove("custom_name");
        if (puppet.getCustomName() != null) net.minecraft.text.TextCodecs.CODEC
                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, puppet.getCustomName()).result()
                .ifPresent(value -> memory.put("custom_name", value));
        ((Heart) heart).relationshipMemory(memory);
    }
    public static void restore(CreakingHeartBlockEntity heart, CreakingEntity puppet) {
        if (puppet == null || ReplayIsolation.isActor(puppet.getUuid())) return;
        var memory = ((Heart) heart).relationshipMemory();
        com.nonid.affinity.non.NonBridge.restoreRememberedGender(puppet, memory.getInt("non_gender", 0));
        if (memory.getBoolean("persistent", false)) puppet.setPersistent();
        if (memory.contains("affinity")) {
            var data = CreatureAffinityData.fromNbt(memory.getCompoundOrEmpty("affinity").copy());
            // Old stationary commands refer to the former location, not the relocated heart.
            data.clearAnchor();
            ((AffinityHolder) puppet).setAffinityDataRaw(data);
        }
        if (memory.contains("name")) puppet.setCustomName(Text.literal(memory.getString("name", "")));
        if (memory.contains("custom_name")) net.minecraft.text.TextCodecs.CODEC
                .parse(net.minecraft.nbt.NbtOps.INSTANCE, memory.get("custom_name")).result().ifPresent(puppet::setCustomName);
    }
    public static void decorateDrops(CreakingHeartBlockEntity heart, ServerWorld world, List<ItemStack> drops) {
        var memory = ((Heart) heart).relationshipMemory();
        if (!memory.contains("affinity")) return;
        // Never add drops or bypass Silk Touch. Only decorate an actual vanilla heart drop.
        for (ItemStack stack : drops) {
            if (!stack.isOf(Items.CREAKING_HEART) || stack.isEmpty()) continue;
            String ticket = memory.getString("transfer_ticket", "");
            if (ticket.isEmpty()) {
                ticket = UUID.randomUUID().toString();
                memory = memory.copy();
                memory.putString("transfer_ticket", ticket);
                ((Heart)heart).relationshipMemory(memory);
            }
            var ledger = world.getServer().getOverworld().getAttachedOrCreate(TRANSFERS).copy();
            if (ledger.contains(ticket) && ledger.getCompoundOrEmpty(ticket).isEmpty()) return;
            var record = memory.copy();
            record.putBoolean("awaiting_removal", !((Heart)heart).relationshipSourceRemoved());
            ledger.put(ticket, record);
            world.getServer().getOverworld().setAttached(TRANSFERS, ledger);
            NbtCompound transfer = new NbtCompound();
            transfer.putString("ticket", ticket);
            NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, n -> n.put(ITEM_KEY, transfer));
            break;
        }
    }
    public static void place(CreakingHeartBlockEntity heart, ServerWorld world, ItemStack stack) {
        String ticket = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .copyNbt().getCompoundOrEmpty(ITEM_KEY).getString("ticket", "");
        if (ticket.isEmpty()) return;
        var stored = world.getServer().getOverworld().getAttached(TRANSFERS);
        if (stored == null || stored.getCompoundOrEmpty(ticket).isEmpty()) return;
        if (stored.getCompoundOrEmpty(ticket).getBoolean("awaiting_removal", false)) return;
        var ledger = stored.copy();
        var memory = ledger.getCompoundOrEmpty(ticket).copy();
        memory.remove("transfer_ticket");
        memory.remove("awaiting_removal");
        ((Heart) heart).relationshipMemory(memory);
        ledger.put(ticket, new NbtCompound()); // Tombstone: a stale copy cannot issue this ticket again.
        world.getServer().getOverworld().setAttached(TRANSFERS, ledger);
    }
    /** Only a real block replacement releases a transfer, never a loot preview or chunk unload. */
    public static void sourceRemoved(CreakingHeartBlockEntity heart) {
        if (!(heart.getWorld() instanceof ServerWorld world)) return;
        var memory = ((Heart)heart).relationshipMemory();
        String ticket = memory.getString("transfer_ticket", "");
        var stored = world.getServer().getOverworld().getAttached(TRANSFERS);
        if (ticket.isEmpty() || stored == null || stored.getCompoundOrEmpty(ticket).isEmpty()) return;
        var ledger = stored.copy();
        var record = memory.copy();
        record.putBoolean("awaiting_removal", false);
        ledger.put(ticket, record);
        world.getServer().getOverworld().setAttached(TRANSFERS, ledger);
    }
    private CreakingMemory() { }
}
