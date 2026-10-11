package com.nonid.affinity.mixin;
import com.nonid.affinity.gift.GiftService;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ServerWorld.class)
public abstract class GiftWorldTimeMixin {
    @WrapMethod(method="setTimeOfDay")
    private void naturalrelationship$giftTime(long time, Operation<Void> original) {
        var world=(ServerWorld)(Object)this; long before=world.getTimeOfDay();
        original.call(time);
        GiftService.timeChanged(world,before,world.getTimeOfDay());
    }
}
