package com.nonid.affinity.reunion;

import com.nonid.affinity.item.NonAffinityItems;
import com.nonid.affinity.mixin.ReunionCraftingInputAccessor;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;

/** Validate cached recharge results before vanilla moves them or computes recipe remainders. */
public final class ReunionCraftingGuard {
    public static boolean allow(Slot output, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)
                || !(output instanceof ReunionCraftingInputAccessor access)
                || !output.getStack().isOf(NonAffinityItems.REUNION_WISH)) return true;
        if (!(output.inventory instanceof CraftingResultInventory result)
                || result.getLastRecipe() == null
                || !(result.getLastRecipe().value() instanceof ReunionRecipe)) return true;
        var input = access.noncreatureaffinity$reunionInput().createRecipeInput();
        // First creation and other mods' recipes without a bound wish are not recharge transactions.
        boolean recharge = input.getStacks().stream().anyMatch(stack ->
                stack.isOf(NonAffinityItems.REUNION_WISH) && ReunionData.item(stack).contains("mob"));
        if (!recharge) return true;
        var world = serverPlayer.getEntityWorld();
        if (ReunionRecipe.INSTANCE.matches(input, world)) return true;
        output.setStack(ItemStack.EMPTY);
        return false;
    }
    private ReunionCraftingGuard() {}
}
