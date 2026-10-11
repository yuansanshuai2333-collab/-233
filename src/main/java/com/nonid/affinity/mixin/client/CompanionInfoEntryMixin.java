package com.nonid.affinity.mixin.client;
import com.nonid.affinity.client.CompanionInfoClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** The detector's sneak action takes priority over opening a page or interacting with the aimed target. */
@Mixin(MinecraftClient.class)
public abstract class CompanionInfoEntryMixin {
    @Shadow private int itemUseCooldown;
    @Inject(method="doItemUse",at=@At("HEAD"),cancellable=true)
    private void affinity$detectorHighlight(CallbackInfo ci){
        if(CompanionInfoClient.tryDetectorHighlight()){itemUseCooldown=4;ci.cancel();}
    }
}
