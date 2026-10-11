package com.nonid.affinity.mixin;
import com.nonid.affinity.service.FriendRetention;
import net.minecraft.entity.passive.TraderLlamaEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TraderLlamaEntity.class)
public abstract class FriendTraderLlamaLifetimeMixin {
    @Inject(method="tryDespawn",at=@At("HEAD"),cancellable=true)
    private void affinity$pauseLifetime(CallbackInfo ci) {
        if(FriendRetention.protects((TraderLlamaEntity)(Object)this)) ci.cancel();
    }
}
