package com.nonid.affinity.mixin.client;

import com.nonid.affinity.client.AffinityHud;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class RequestBubbleMouseMixin {
    @Unique private boolean affinity$consumed;
    @Unique private boolean affinity$gift;
    @Inject(method="onMouseButton",at=@At("HEAD"),cancellable=true)
    private void affinity$answer(long window, MouseInput input, int action, CallbackInfo ci) {
        if(window!=net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle() || input.button()!=GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        if(action==GLFW.GLFW_PRESS) {
            affinity$consumed=false;
            affinity$gift=false;
            if(AffinityHud.tryAnswer()){affinity$consumed=true;ci.cancel();}
            else if(com.nonid.affinity.client.GiftHudClient.press()){affinity$consumed=true;affinity$gift=true;ci.cancel();}
        }
        else if(action==GLFW.GLFW_RELEASE && affinity$consumed) {affinity$consumed=false;if(affinity$gift)com.nonid.affinity.client.GiftHudClient.release();affinity$gift=false;ci.cancel();}
    }
}
