package com.nonid.affinity.service;

import java.util.Set;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Safe air recall for loaded followers. Does not load chunks or change native pet control. */
public final class AerialRecall {
    private static final TagKey<EntityType<?>> FLYERS = TagKey.of(RegistryKeys.ENTITY_TYPE,
            Identifier.of("noncreatureaffinity", "aerial_companions"));

    private AerialRecall() {}

    public static boolean canFly(MobEntity mob) {
        // No-gravity alone is insufficient: animations also disable gravity on ground mobs.
        return mob.getNavigation() instanceof BirdNavigation
                || mob.getMoveControl() instanceof FlightMoveControl || mob.getType().isIn(FLYERS);
    }

    static Vec3d tryRecall(ServerWorld world, MobEntity mob, ServerPlayerEntity owner, double radius) {
        double yaw = Math.toRadians(owner.getYaw());
        double forwardX = -Math.sin(yaw), forwardZ = Math.cos(yaw);
        // A legal two-block recall radius must not reject every candidate solely because
        // the preferred rear offset is 2.5. Keep body clearance, with a small rounding margin
        // inside the radius; genuinely oversized companions still cannot be squeezed in.
        double clearance = Math.max(Math.min(2.5, radius - 0.05),
                (mob.getWidth() + owner.getWidth()) / 2.0 + 1.0);
        // Horizontal yaw keeps "behind" stable even when looking straight up or down.
        for (double back : new double[]{clearance, clearance + 1.5, clearance + 3.0}) {
            for (double side : new double[]{0, 1.5, -1.5, 3, -3}) {
                for (double dy : new double[]{0, 1, -1, 2, -2}) {
                    double x = owner.getX() - forwardX * back + forwardZ * side;
                    double y = owner.getY() + dy;
                    double z = owner.getZ() - forwardZ * back - forwardX * side;
                    if (CompanionRecall.needed(owner.squaredDistanceTo(x, y, z), radius)) continue;
                    Box box = mob.getBoundingBox().offset(x - mob.getX(), y - mob.getY(), z - mob.getZ());
                    if (!isClear(world, mob, box)) continue;
                    if (mob.teleport(world, x, y, z, Set.of(), mob.getYaw(), mob.getPitch(), false)) {
                        mob.getNavigation().stop();
                        mob.setVelocity(Vec3d.ZERO);
                        mob.fallDistance = 0;
                        TargetRegistry.releaseIfOwned(mob, TargetRegistry.TargetSource.COMPANION_ATTACK);
                        return new Vec3d(x, y, z);
                    }
                }
            }
        }
        return null;
    }

    static boolean isClear(ServerWorld world, MobEntity mob, Box box) {
        if (box.minY < world.getBottomY() || box.maxY > world.getTopYInclusive() + 1.0
                || !world.getWorldBorder().contains(box)) return false;
        for (BlockPos cell : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ),
                BlockPos.ofFloored(Math.nextDown(box.maxX), Math.nextDown(box.maxY), Math.nextDown(box.maxZ)))) {
            // Require air throughout the actual body, including non-colliding hazards and liquids.
            if (!world.isChunkLoaded(cell) || !world.getBlockState(cell).isAir()) return false;
        }
        return world.isSpaceEmpty(mob, box) && world.getOtherEntities(mob, box).isEmpty();
    }
}
