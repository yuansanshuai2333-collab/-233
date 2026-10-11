package com.nonid.affinity.mixin.client;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nonid.affinity.client.CompanionHighlightClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Changes only this render state's local outline color, preserving other providers' existing color. */
@Mixin(EntityRenderer.class)
public abstract class CompanionHighlightColorMixin {
    @WrapOperation(method="updateRenderState",at=@At(value="INVOKE",target="Lnet/minecraft/entity/Entity;getTeamColorValue()I"))
    private int affinity$privateColor(Entity entity,Operation<Integer> original){return CompanionHighlightClient.color(entity,original.call(entity));}
}
