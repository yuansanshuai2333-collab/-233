package com.nonid.affinity.mixin.client;

import com.nonid.affinity.client.ReunionEffectClient;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmedEntityRenderState.class)
public abstract class ReunionArmedStateMixin {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private static void reunionHide(LivingEntity entity, ArmedEntityRenderState state,
                                    ItemModelManager models, float tickProgress, CallbackInfo ci) {
        for (Hand hand : Hand.values()) {
            if (!ReunionEffectClient.hides(entity, hand, entity.getStackInHand(hand))) continue;
            Arm arm = hand == Hand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
            if (arm == Arm.RIGHT) { state.rightHandItemState.clear(); state.rightHandItem = ItemStack.EMPTY; }
            else { state.leftHandItemState.clear(); state.leftHandItem = ItemStack.EMPTY; }
        }
    }
}
