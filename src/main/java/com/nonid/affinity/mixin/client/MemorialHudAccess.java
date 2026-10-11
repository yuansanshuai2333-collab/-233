package com.nonid.affinity.mixin.client;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(InGameHud.class)
public interface MemorialHudAccess {
    @Accessor("overlayRemaining") int affinity$overlayRemaining();
}
