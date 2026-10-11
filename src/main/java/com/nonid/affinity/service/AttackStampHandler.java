package com.nonid.affinity.service;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;

/**
 * Brackets one melee attack with the launch stamp, with real {@code finally} semantics.
 *
 * <p>Exists as its own class, outside the mixin package, precisely so the {@code finally} can be
 * exercised by a test. In the mixin it could only be observed by launching a game.
 *
 * <p>Why a wrapper rather than two injections at HEAD and RETURN: those injection points sit at the
 * method's return INSTRUCTIONS, which are normal control flow. A throwable propagating out of a nested
 * call -- {@code damage()}, {@code postHit}, an enchantment hook -- never reaches them, so the stamp
 * would be left behind. An earlier version of this code claimed the opposite in a comment; it was wrong.
 *
 * <p>Nothing here catches or swallows an exception: the original throwable propagates unchanged and is
 * not wrapped. Only cleanup happens on the way out.
 *
 * <p>The mixin calls this, so the {@code finally} that actually runs in game is the same one the test
 * exercises -- not a parallel implementation.
 */
public final class AttackStampHandler {

    private AttackStampHandler() {
    }

    /**
     * Run the attack body with the launch stamp issued before it and released after it.
     *
     * @param companion the attacking creature
     * @param target    what it is attacking; a non-living target gets no stamp
     * @param body      the original attack
     * @return whatever the body returned
     */
    public static <T> T runWithStamp(MobEntity companion, LivingEntity target,
                                     java.util.function.Supplier<T> body) {
        Object token = CompanionCombatController.onCompanionAttackLaunched(companion, target);
        try {
            return body.get();
        } finally {
            // Every exit path: normal return, a false return, or a throwable. The token identity check
            // inside makes a stamp that a newer attempt already replaced safe to signal.
            CompanionCombatController.onCompanionAttackFinished(companion, token);
        }
    }
}
