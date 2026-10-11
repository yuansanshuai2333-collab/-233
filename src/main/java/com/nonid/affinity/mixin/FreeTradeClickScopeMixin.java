package com.nonid.affinity.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nonid.affinity.service.FreeTradeShiftBatch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Keep batch scope intact even when another mod cancels, nests or throws from a slot click. */
@Mixin(ScreenHandler.class)
public abstract class FreeTradeClickScopeMixin {
    @WrapMethod(method = "onSlotClick")
    private void noncreatureaffinity$clickScope(int slotIndex, int button, SlotActionType actionType,
                                               PlayerEntity player, Operation<Void> original) {
        FreeTradeShiftBatch.run(player instanceof ServerPlayerEntity,
                () -> original.call(slotIndex, button, actionType, player));
    }
}
