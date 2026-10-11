package com.nonid.affinity.integration.jei;

import com.nonid.affinity.item.NonAffinityItems;
import com.nonid.affinity.reunion.ReunionRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;

/** Display-only, discovered by JEI. No common or client mod initializer references this class. */
@JeiPlugin
@Environment(EnvType.CLIENT)
public final class NaturalRelationshipsJeiPlugin implements IModPlugin {
    @Override public Identifier getPluginUid() { return id("jei"); }
    static Identifier id(String path) { return Identifier.of("noncreatureaffinity", path); }
    static Text text(String key) { return Text.translatable("jei.noncreatureaffinity." + key); }

    @Override public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        // Include the real defaults even when an item was not added to a creative tab.
        registration.addExtraItemStacks(List.of(new ItemStack(NonAffinityItems.REUNION_WISH),
                new ItemStack(NonAffinityItems.FRIENDSHIP_TOKEN), new ItemStack(NonAffinityItems.AFFINITY_DETECTOR)));
    }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ReunionJeiCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new MixedLiquidJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        registration.addItemStackInfo(new ItemStack(NonAffinityItems.FRIENDSHIP_TOKEN), text("token_info"));
        registration.addItemStackInfo(new ItemStack(NonAffinityItems.AFFINITY_DETECTOR), text("detector_info"));
        registration.addItemStackInfo(new ItemStack(NonAffinityItems.REUNION_WISH), text("wish_info"));

        // Actual registered potion only. With NoN absent, there is no obtainable ingredient:
        // show an explanation, not a fabricated water-bottle recipe.
        var potion = Registries.POTION.getEntry(Identifier.of("needsofnature", "liquid"));
        if (potion.isEmpty()) {
            registration.addItemStackInfo(new ItemStack(NonAffinityItems.REUNION_WISH), text("missing_liquid"));
            return;
        }
        List<ItemStack> bottles = new ArrayList<>();
        for (ItemStack stack : registration.getIngredientManager().getAllItemStacks()) {
            if (ReunionRecipe.bottle(stack)) bottles.add(stack.copyWithCount(1));
        }
        if (bottles.isEmpty()) {
            ItemStack bottle = new ItemStack(Items.POTION);
            bottle.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(potion.get()));
            bottles.add(bottle);
        }
        var mixed=com.nonid.affinity.non.NoNLiquidBottles.mixed(1);
        if (!mixed.isEmpty()) bottles.add(mixed);
        var pig=com.nonid.affinity.non.NoNLiquidBottles.source(Identifier.ofVanilla("pig"));
        var cow=com.nonid.affinity.non.NoNLiquidBottles.source(Identifier.ofVanilla("cow"));
        if(!pig.isEmpty() && !cow.isEmpty() && !mixed.isEmpty()) {
            var guides=new ArrayList<MixedLiquidJeiCategory.Guide>();
            for(int n=2;n<=9;n++) {
                var inputs=new ArrayList<ItemStack>();
                for(int i=0;i<n;i++) inputs.add((i%2==0?pig:cow).copy());
                guides.add(new MixedLiquidJeiCategory.Guide(List.copyOf(inputs),mixed.copyWithCount(n)));
            }
            registration.addRecipes(MixedLiquidJeiCategory.TYPE,guides);
        }
        // These are usage guides, not registered Minecraft recipes or authenticated bound items.
        registration.addRecipes(ReunionJeiCategory.TYPE, List.of(
                new ReunionJeiCategory.Guide("create", 3, false, List.copyOf(bottles)),
                new ReunionJeiCategory.Guide("single", 1, true, List.copyOf(bottles)),
                new ReunionJeiCategory.Guide("double", 2, true, List.copyOf(bottles))));
    }
}
