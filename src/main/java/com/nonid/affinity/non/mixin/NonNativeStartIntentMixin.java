package com.nonid.affinity.non.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nonid.affinity.non.compat.NativeStartIntent;
import com.nonid.api.animation.NonAnimationDamageBehavior;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.internal.animation.server.AnimationStartContext;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import java.util.*;
@Mixin(targets="com.nonid.internal.animation.server.ServerAnimationController",remap=false)
public abstract class NonNativeStartIntentMixin {
    @WrapMethod(method="startNowWithContext",require=1)
    private static UUID noncreatureaffinity$capture(ServerWorld world,ServerPlayerEntity requester,
            Identifier animation,List<Entity> actors,List<String> keys,List<AnimationStageInfo> stages,
            NonAnimationDamageBehavior damage,boolean ignoreAttackers,UUID anchor,
            AnimationStartContext context,boolean debug,boolean multiple,Operation<UUID> original){
        if(world==null||animation==null||actors==null||actors.stream().anyMatch(Objects::isNull)
                || keys!=null&&keys.stream().anyMatch(Objects::isNull)
                || stages!=null&&stages.stream().anyMatch(Objects::isNull))
            return original.call(world,requester,animation,actors,keys,stages,damage,ignoreAttackers,anchor,context,debug,multiple);
        var intent=new NativeStartIntent(world,requester,animation,actors,keys,stages,damage,ignoreAttackers,anchor,context,debug,multiple);
        return intent.observe(()->original.call(world,requester,animation,actors,keys,stages,damage,ignoreAttackers,anchor,context,debug,multiple));
    }
}
