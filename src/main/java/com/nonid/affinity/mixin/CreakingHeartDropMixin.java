package com.nonid.affinity.mixin;

import com.nonid.affinity.service.CreakingMemory;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.CreakingHeartBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootWorldContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(AbstractBlock.class)
public abstract class CreakingHeartDropMixin {
    @Inject(method = "getDroppedStacks", at = @At("RETURN"))
    private void affinity$drops(BlockState state, LootWorldContext.Builder builder, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (builder.getOptional(LootContextParameters.BLOCK_ENTITY) instanceof CreakingHeartBlockEntity heart)
            CreakingMemory.decorateDrops(heart, builder.getWorld(), cir.getReturnValue());
    }
}
