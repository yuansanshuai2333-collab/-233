package com.nonid.affinity.data;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

/**
 * The player-side half of the close-friend free-trade feature: a counter that only ever goes up, bumped when the
 * player NEWLY acquires the hero-of-the-village effect.
 *
 * <p>Why a counter and not a boolean: the allowance is refreshed by "an event this relation has not applied yet",
 * and "yet" needs an ordering. A boolean cannot distinguish "the same effect, still running" from "the effect was
 * acquired again after it ended" -- which are exactly the two cases the rules treat differently. The counter is the
 * persisted event number the relation's {@code lastAppliedHeroEpoch} is compared against.
 *
 * <p><b>Bumped only on a new acquisition</b>, never on an extension or an upgrade of a running effect: those must
 * not refresh anything, and the callback that dispatches them is a different one.
 *
 * <p>It lives on the player, {@code copyOnDeath}, so it survives death, respawn and dimension changes. Using a
 * world time or a wall-clock value instead would be wrong for a different reason: those move without an event, and
 * comparing them would make the allowance refresh for no reason at all.
 */
public final class FreeTradeHeroEpoch {

    public static final Identifier ID = Identifier.of("noncreatureaffinity", "hero_epoch");

    public static final AttachmentType<Long> HERO_EPOCH = AttachmentRegistry.create(ID, builder -> builder
            .initializer(() -> 0L)
            .persistent(NbtCompoundCodec.of(FreeTradeHeroEpoch::encode, FreeTradeHeroEpoch::decode))
            .copyOnDeath());

    /** The player's current epoch; 0 means the feature has never seen a hero event for them. */
    public static long of(PlayerEntity player) {
        Long value = player.getAttached(HERO_EPOCH);
        return value == null ? 0L : value;
    }

    /** Record a NEW hero acquisition and return the new epoch. */
    public static long bump(PlayerEntity player) {
        long next = of(player) + 1L;
        player.setAttached(HERO_EPOCH, next);
        return next;
    }

    /** Force class initialisation so the attachment is registered before any player loads. */
    public static void init() {
    }

    private static NbtCompound encode(Long value) {
        NbtCompound tag = new NbtCompound();
        tag.putLong("epoch", value == null ? 0L : value);
        return tag;
    }

    private static Long decode(NbtCompound tag) {
        return tag.getLong("epoch", 0L);
    }

    private FreeTradeHeroEpoch() {
    }
}
