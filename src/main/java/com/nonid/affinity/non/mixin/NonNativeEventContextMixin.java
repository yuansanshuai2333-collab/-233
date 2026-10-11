package com.nonid.affinity.non.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nonid.affinity.non.compat.NativeEventContext;
import com.nonid.api.animation.NonAnimationDamageBehavior;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.internal.animation.server.AnimationStartContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import java.util.*;
@Mixin(targets="com.nonid.internal.animation.server.ServerAnimationController",remap=false)
public abstract class NonNativeEventContextMixin {
    @WrapMethod(method="startAndBroadcast(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Ljava/util/List;Ljava/util/List;Ljava/util/List;Lcom/nonid/api/animation/NonAnimationDamageBehavior;ZLcom/nonid/internal/animation/server/AnimationStartContext;ZLjava/util/UUID;Lcom/nonid/internal/animation/server/ServerAnimationController$BlockPlacement;Ljava/lang/Float;)Ljava/util/UUID;",require=1)
    private static UUID noncreatureaffinity$context(ServerWorld world,ServerPlayerEntity requester,
            Identifier animation,List<UUID> actors,List<String> keys,List<AnimationStageInfo> stages,
            NonAnimationDamageBehavior damage,boolean ignoreAttackers,AnimationStartContext context,
            boolean debug,UUID anchor,@Coerce Object placement,Float yaw,Operation<UUID> original){
        return NativeEventContext.run(context,()->original.call(world,requester,animation,actors,keys,stages,
                damage,ignoreAttackers,context,debug,anchor,placement,yaw));
    }
}
