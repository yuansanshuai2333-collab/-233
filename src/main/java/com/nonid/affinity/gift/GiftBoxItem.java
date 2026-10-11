package com.nonid.affinity.gift;

import com.nonid.affinity.NonCreatureAffinity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** No recipe/creative-tab/default award path: unfinished art is not substituted with a fake finished box. */
public final class GiftBoxItem extends Item {
    public static final Identifier ID = Identifier.of(NonCreatureAffinity.MOD_ID, "gift_box");
    public static GiftBoxItem ITEM;
    public GiftBoxItem(Settings settings) { super(settings); }
    public static void register() { ITEM = Registry.register(Registries.ITEM, ID, new GiftBoxItem(new Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM, ID)).maxCount(1))); }
    public static ItemStack box(GiftEnvelope gift) {
        var stack = new ItemStack(ITEM); var n = new NbtCompound(); n.putString("gift", gift.content().id().toString()); n.putString("token", gift.token().toString());
        n.putString("name", gift.content().giverName()); n.putString("type", gift.content().giverType()); n.putInt("wrapping", gift.content().wrapping());
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, root -> root.put("natural_gift", n));
        stack.set(DataComponentTypes.ITEM_MODEL,Identifier.of(NonCreatureAffinity.MOD_ID,"gift_box_"+(gift.content().wrapping()+1)));
        return stack;
    }
    public static NbtCompound ticket(ItemStack stack) { return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getCompoundOrEmpty("natural_gift"); }
    @Override public Text getName(ItemStack stack) {
        var n = ticket(stack); String name = n.getString("name", "");
        var type = Identifier.tryParse(n.getString("type", ""));
        Text giver = name.isBlank() ? type == null ? Text.translatable("message.noncreatureaffinity.gift_unknown_giver")
                : Registries.ENTITY_TYPE.getOptionalValue(type).map(entity -> entity.getName()).orElse(Text.translatable("message.noncreatureaffinity.gift_unknown_giver")) : Text.literal(name);
        return Text.translatable("item.noncreatureaffinity.gift_box", giver);
    }
    @Override public ActionResult use(World world, net.minecraft.entity.player.PlayerEntity player, Hand hand) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            String result = GiftService.open(serverPlayer, hand);
            if (!result.equals("opened")) com.nonid.affinity.service.GameplayFeedback.send(serverPlayer,Text.translatable("message.noncreatureaffinity.gift_" + result), true);
        }
        return ActionResult.SUCCESS;
    }
}
