package com.nonid.affinity.mixin;

import java.util.UUID;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the player who is curing this zombie villager.
 *
 * <p>The field is private and has no getter in vanilla, and the only other way to reach it is the NBT key
 * {@code ConversionPlayer} -- which is written on save and therefore absent, or stale, exactly while the cure is
 * in progress. Reading the field itself is what makes the recorded cure the ACTUAL converter.
 *
 * <p>Read at conversion time, from the entity being converted: the survivor is a different entity and never
 * carries this value, while the converting one still holds it until it is discarded.
 */
@Mixin(ZombieVillagerEntity.class)
public interface ZombieVillagerCureAccessor {

    /** The curing player's UUID, or null when nothing is being cured. */
    @Accessor("converter")
    UUID noncreatureaffinity$converter();
}
