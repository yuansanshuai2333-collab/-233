package com.nonid.affinity.mixin;
import com.nonid.affinity.service.FriendRetention;
import net.minecraft.entity.mob.VexEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(VexEntity.class)
public abstract class FriendVexLifetimeMixin {
    @WrapOperation(method="tick",at=@At(value="FIELD",target="Lnet/minecraft/entity/mob/VexEntity;alive:Z",opcode=180))
    private boolean affinity$pauseLifetime(VexEntity self, Operation<Boolean> original) {
        return original.call(self) && (self.getEntityWorld().isClient() || !FriendRetention.protects(self));
    }
}
