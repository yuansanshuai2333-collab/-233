package com.nonid.affinity.mixin;
import com.nonid.affinity.service.FriendRetention;
import net.minecraft.entity.passive.WanderingTraderEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WanderingTraderEntity.class)
public abstract class FriendTraderLifetimeMixin {
    @Inject(method="tickDespawnDelay",at=@At("HEAD"),cancellable=true)
    private void affinity$pauseLifetime(CallbackInfo ci) {
        if(FriendRetention.protects((WanderingTraderEntity)(Object)this)) ci.cancel();
    }
}
