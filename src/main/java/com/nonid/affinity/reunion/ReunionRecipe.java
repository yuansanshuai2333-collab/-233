package com.nonid.affinity.reunion;

import com.mojang.serialization.MapCodec;
import com.nonid.affinity.item.NonAffinityItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** Shapeless creation and refilling. No dependency on NoN classes at startup or in the recipe codec. */
public final class ReunionRecipe extends SpecialCraftingRecipe {
    public static final ReunionRecipe INSTANCE = new ReunionRecipe();
    public static final RecipeSerializer<ReunionRecipe> SERIALIZER = new RecipeSerializer<>() {
        public MapCodec<ReunionRecipe> codec() { return MapCodec.unit(INSTANCE); }
        public PacketCodec<RegistryByteBuf, ReunionRecipe> packetCodec() { return PacketCodec.unit(INSTANCE); }
    };
    private ReunionRecipe() { super(CraftingRecipeCategory.MISC); }
    public static void register() { Registry.register(Registries.RECIPE_SERIALIZER, ReunionData.id("reunion_wish"), SERIALIZER); MixedLiquidRecipe.register(); }
    public static boolean bottle(ItemStack stack) {
        if (!stack.isOf(Items.POTION)) return false;
        var potion = stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT).potion();
        if (potion.isEmpty() || !potion.get().matchesKey(net.minecraft.registry.RegistryKey.of(
                net.minecraft.registry.RegistryKeys.POTION, Identifier.of("needsofnature", "liquid")))) return false;
        var data=stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();
        if (!data.contains("liquid_entity")) return true; // NoN's generic/mixed representation.
        if (!(data.get("liquid_entity") instanceof net.minecraft.nbt.NbtString)) return false;
        String source=data.getString("liquid_entity", "");
        if (source.isEmpty()) return true;
        var id=Identifier.tryParse(source);
        return id!=null && id.toString().equals(source) && Registries.ENTITY_TYPE.containsId(id);
    }
    public static String species(ItemStack stack) {
        if (!bottle(stack)) return "";
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt()
                .getString("liquid_entity", "");
    }
    private ItemStack wish(CraftingRecipeInput input) {
        for (var stack : input.getStacks()) if (stack.isOf(NonAffinityItems.REUNION_WISH)) return stack;
        return ItemStack.EMPTY;
    }
    private int refillAmount(CraftingRecipeInput input) {
        int amount = 0;
        for (var stack : input.getStacks())
            if (!stack.isEmpty() && stack.isOf(NonAffinityItems.FRIENDSHIP_TOKEN)) amount++;
        return amount;
    }
    private boolean valid(CraftingRecipeInput input) {
        int tokens=0, bottles=0, diamonds=0, wishes=0;
        ItemStack wish=wish(input);
        String species=wish.isEmpty() ? "" : ReunionData.item(wish).getString("type", "");
        for (var stack:input.getStacks()) {
            if(stack.isEmpty()) continue;
            if(stack.isOf(NonAffinityItems.FRIENDSHIP_TOKEN)) tokens++;
            else if(stack.isOf(Items.DIAMOND)) diamonds++;
            else if(stack.isOf(NonAffinityItems.REUNION_WISH)) wishes++;
            else if(bottle(stack)) {
                bottles++;
                if(!wish.isEmpty() && !species(stack).isEmpty() && !species.equals(species(stack))) return false;
            } else return false;
        }
        if(wishes==0) return tokens==3 && bottles==3 && diamonds==1;
        var data=ReunionData.item(wish);
        int energy=data.getInt("energy",data.getBoolean("dim",false)?0:3);
        boolean refill = tokens==bottles && (tokens==1 || tokens==2 && energy==1);
        return wishes==1 && refill && diamonds==0
                && data.contains("mob") && !species.isEmpty() && energy>=0 && energy<3
                && ReunionData.fuel(data)>0 && ReunionData.fuel(data)<=Long.MAX_VALUE-tokens;
    }
    @Override public boolean matches(CraftingRecipeInput input, World world) {
        if(!valid(input)) return false;
        var stack=wish(input);
        if(stack.isEmpty() || !(world instanceof net.minecraft.server.world.ServerWorld sw)) return true;
        var item=ReunionData.item(stack);
        var record=ReunionData.record(sw.getServer(),item.getString("mob",""));
        // Stale copies must be refreshed in the inventory first; never spend ingredients on stale receipts.
        return ReunionData.authentic(item,record)
                && ReunionEnergy.canRecharge(ReunionData.fuel(item),ReunionData.spent(record))
                && item.getInt("energy",item.getBoolean("dim",false)?0:3)==ReunionData.energy(item,record)
                && (refillAmount(input)==1 || ReunionData.energy(item,record)==1);
    }
    @Override public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        if (!valid(input)) return ItemStack.EMPTY;
        ItemStack original=wish(input);
        if(original.isEmpty()) return new ItemStack(NonAffinityItems.REUNION_WISH);
        ItemStack result=original.copyWithCount(1);
        var data=ReunionData.item(result);
        int amount=refillAmount(input);
        data.putLong("fuel",ReunionData.fuel(data)+amount);
        data.putInt("energy",data.getInt("energy",data.getBoolean("dim",false)?0:3)+amount);
        data.putBoolean("dim",false);
        ReunionData.item(result,data);
        return result;
    }
    @Override public net.minecraft.util.collection.DefaultedList<ItemStack> getRecipeRemainders(CraftingRecipeInput input) {
        var result=net.minecraft.util.collection.DefaultedList.ofSize(input.size(),ItemStack.EMPTY);
        for(int i=0;i<input.size();i++) if(bottle(input.getStackInSlot(i))) result.set(i,new ItemStack(Items.GLASS_BOTTLE));
        return result;
    }
    @Override public RecipeSerializer<ReunionRecipe> getSerializer() { return SERIALIZER; }
}
