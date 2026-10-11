package com.nonid.affinity.reunion;

import java.util.function.Consumer;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public final class ReunionItem extends Item {
    public ReunionItem(Settings settings) { super(settings); }
    @Override public Text getName(ItemStack stack) {
        var data = ReunionData.item(stack);
        if (!data.contains("mob")) return Text.translatable("item.noncreatureaffinity.reunion_wish");
        Text name = data.getBoolean("custom_name", false) ? Text.literal(data.getString("name", "?"))
                : Text.translatable(data.getString("name", "entity.minecraft.pig"));
        return Text.translatable(data.getBoolean("dim", false)
                ? "item.noncreatureaffinity.reunion_dim" : "item.noncreatureaffinity.reunion_bound", name);
    }
    @Override public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (user instanceof ServerPlayerEntity player) {
            if(user.isSneaking()) ReunionRecall.request(player,user.getStackInHand(hand),hand);
            else ReunionService.revive(player,user.getStackInHand(hand));
        }
        return ActionResult.SUCCESS;
    }
    @Override public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        if (entity.age % 20 == 0) ReunionService.refresh(stack, world);
    }
    @Override public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent display,
                                        Consumer<Text> tooltip, TooltipType type) {
        var data = ReunionData.item(stack);
        if(data.contains("mob")) {
            tooltip.accept(Text.translatable("tooltip.noncreatureaffinity.reunion_energy",data.getInt("energy",data.getBoolean("dim",false)?0:3)));
            tooltip.accept(Text.translatable("tooltip.noncreatureaffinity.reunion_charge"));
        } else {
            tooltip.accept(Text.translatable("tooltip.noncreatureaffinity.reunion_lore"));
            tooltip.accept(Text.translatable("tooltip.noncreatureaffinity.reunion_bind"));
        }
    }
}
