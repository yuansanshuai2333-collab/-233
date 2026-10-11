package com.nonid.affinity.mixin;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.screen.slot.CraftingResultSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CraftingResultSlot.class)
public interface ReunionCraftingInputAccessor {
    @Accessor("input")
    RecipeInputInventory noncreatureaffinity$reunionInput();
}
