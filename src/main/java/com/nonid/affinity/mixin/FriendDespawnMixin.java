package com.nonid.affinity.mixin;
import com.nonid.affinity.service.FriendRetention;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class FriendDespawnMixin {
    @Inject(method="checkDespawn",at=@At("HEAD"),cancellable=true)
    private void affinity$naturalDespawn(CallbackInfo ci) {
        MobEntity self=(MobEntity)(Object)this;
        if (self.getEntityWorld().isClient()) return;
        if (self.getEntityWorld().getDifficulty()==Difficulty.PEACEFUL && !self.getType().isAllowedInPeaceful()) return;
        if (FriendRetention.protects(self)) ci.cancel();
    }
}
