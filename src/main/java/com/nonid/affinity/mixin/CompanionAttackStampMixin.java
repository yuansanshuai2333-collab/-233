package com.nonid.affinity.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nonid.affinity.service.AttackStampHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Wraps the WHOLE melee attack with the launch stamp.
 *
 * <p><b>Why the target class is {@code MobEntity}, not {@code LivingEntity}.</b> The first version mixed
 * into {@code LivingEntity#tryAttack}. Disassembling the mapped jar shows that method is a no-op stub --
 * it calls {@code onAttacking} and returns false -- while the real attack body is the OVERRIDE in
 * {@code MobEntity#tryAttack}. A mixin on the base-class stub never ran for any mob, so no stamp was ever
 * issued and the late-hit check was inert while every unit test still passed.
 *
 * <p><b>Why wrapping the entire method, and not a redirect inside it.</b> The previous version redirected
 * a single call at the top of the body ({@code target.getVelocity()}) through the stamp handler. That did
 * not wrap the attack: the {@code finally} ran as soon as the velocity read finished, so the stamp was
 * released BEFORE any damage was calculated or applied and could never authorise a melee hit. Two
 * {@code @Inject}s at HEAD and RETURN were the version before that, and they cannot cover a throwable
 * propagating out of a nested call. {@link WrapMethod} is the mechanism that actually brackets the whole
 * method: the body runs exactly once, inside the wrapper's {@code try}, and the {@code finally} runs on
 * every exit path -- normal return, {@code false} return, or a throwable.
 *
 * <p>The original method is invoked through {@code original.call(...)} and NOT by re-entering
 * {@code tryAttack}, so there is no recursion and the original body still executes exactly once.
 *
 * <p>No exception is caught, wrapped or swallowed: MixinExtras rethrows it unchanged, and the cleanup in
 * {@link AttackStampHandler} happens on the way out. The verified behaviour is asserted by
 * {@code AttackStampExceptionPathTest}, which drives the same handler.
 */
@Mixin(MobEntity.class)
public abstract class CompanionAttackStampMixin {

    @WrapMethod(method = "tryAttack")
    private boolean noncreatureaffinity$withAttackStamp(ServerWorld world, Entity target,
                                                        Operation<Boolean> original) {
        MobEntity self = (MobEntity) (Object) this;
        if (!(target instanceof LivingEntity victim)) {
            // Not an attackable target: nothing to stamp, and the original still runs exactly once.
            return original.call(world, target);
        }
        return AttackStampHandler.runWithStamp(self, victim, () -> original.call(world, target));
    }
}
