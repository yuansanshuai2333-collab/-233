package com.nonid.affinity.mixin.client;
import com.nonid.affinity.client.ScopedItemFade;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.ItemGuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GuiRenderState.class)
public class ScopedItemFadeCaptureMixin {
 @Inject(method="addItem",at=@At("HEAD"))
 private void affinity$capture(ItemGuiElementRenderState state,CallbackInfo ci){ScopedItemFade.remember(state);}
}
