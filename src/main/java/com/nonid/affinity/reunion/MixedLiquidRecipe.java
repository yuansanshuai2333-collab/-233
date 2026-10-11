package com.nonid.affinity.reunion;

import com.mojang.serialization.MapCodec;
import com.nonid.affinity.non.NoNLiquidBottles;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import java.util.HashSet;

/** N source bottles become N mixed bottles; no glass is created or lost. */
public final class MixedLiquidRecipe extends SpecialCraftingRecipe {
    public static final MixedLiquidRecipe INSTANCE = new MixedLiquidRecipe();
    public static final RecipeSerializer<MixedLiquidRecipe> SERIALIZER = new RecipeSerializer<>() {
        public MapCodec<MixedLiquidRecipe> codec() { return MapCodec.unit(INSTANCE); }
        public PacketCodec<RegistryByteBuf,MixedLiquidRecipe> packetCodec() { return PacketCodec.unit(INSTANCE); }
    };
    private MixedLiquidRecipe() { super(CraftingRecipeCategory.MISC); }
    public static void register() { Registry.register(Registries.RECIPE_SERIALIZER,ReunionData.id("mixed_liquid"),SERIALIZER); }
    private int count(CraftingRecipeInput input) {
        var sources = new HashSet<String>(); int count=0;
        for (var stack:input.getStacks()) {
            if(stack.isEmpty()) continue;
            if(!ReunionRecipe.bottle(stack)) return 0;
            String source=ReunionRecipe.species(stack);
            if(source.isEmpty()) return 0;
            sources.add(source);count++;
        }
        return count>=2 && count<=9 && sources.size()>=2 ? count : 0;
    }
    @Override public boolean matches(CraftingRecipeInput input, World world) {
        int count=count(input);return count>0 && !NoNLiquidBottles.mixed(count).isEmpty();
    }
    @Override public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        int count=count(input);return count==0?ItemStack.EMPTY:NoNLiquidBottles.mixed(count);
    }
    @Override public net.minecraft.util.collection.DefaultedList<ItemStack> getRecipeRemainders(CraftingRecipeInput input) {
        return net.minecraft.util.collection.DefaultedList.ofSize(input.size(),ItemStack.EMPTY);
    }
    @Override public RecipeSerializer<MixedLiquidRecipe> getSerializer() { return SERIALIZER; }
}
