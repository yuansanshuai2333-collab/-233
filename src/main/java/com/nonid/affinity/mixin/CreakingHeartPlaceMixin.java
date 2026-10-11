package com.nonid.affinity.mixin;

import com.nonid.affinity.service.CreakingMemory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.CreakingHeartBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class CreakingHeartPlaceMixin {
    @Inject(method = "onPlaced", at = @At("TAIL"))
    private void affinity$place(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        if (world instanceof ServerWorld server && world.getBlockEntity(pos) instanceof CreakingHeartBlockEntity heart)
            CreakingMemory.place(heart, server, stack);
    }
}
