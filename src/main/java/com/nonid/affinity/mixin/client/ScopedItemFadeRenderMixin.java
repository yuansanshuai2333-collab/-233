package com.nonid.affinity.mixin.client;
import com.nonid.affinity.client.ScopedItemFade;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.ItemGuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
@Mixin(GuiRenderer.class)
public class ScopedItemFadeRenderMixin {
 @ModifyArg(method="prepareItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/render/state/TexturedQuadGuiElementRenderState;<init>(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/texture/TextureSetup;Lorg/joml/Matrix3x2f;IIIIFFFFILnet/minecraft/client/gui/ScreenRect;Lnet/minecraft/client/gui/ScreenRect;)V"),index=11,require=1)
 private int affinity$alpha(int original,@Local(argsOnly=true)ItemGuiElementRenderState state){return ScopedItemFade.color(state,original);}
}
