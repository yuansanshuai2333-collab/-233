package com.nonid.affinity.service;

import java.util.Set;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Same-world recall into loaded safe ground, or clear air behind the owner for flying companions. */
public final class CompanionRecall {
    private CompanionRecall() {}
    /**
     * Whether the companion is far enough from its owner to be recalled.
     *
     * <p>The threshold comes from the config so there is ONE source. It used to be hardcoded to 100.0
     * (ten blocks squared) while the config carried a separate value, so the two could disagree.
     *
     * <p>Strictly greater than the configured distance: exactly ten blocks is not yet a recall.
     */
    public static boolean needed(double squaredDistance, double recallDistance) {
        return squaredDistance > recallDistance * recallDistance;
    }

    /**
     * Attempt a recall, returning WHERE the companion landed.
     *
     * <p>Returns a position rather than a boolean because the caller has to decide whether the companion
     * is now back beside its owner, and that question can only be answered from the POST-teleport state.
     * An earlier version returned only success, so the caller fell back to the pre-teleport distance --
     * which is by definition outside the recall distance, since that is what made the recall due. The
     * "already back" case was therefore unreachable in production and only a test double could produce
     * it.
     *
     * @return the landing position, or null when no safe spot was found and nothing moved
     */
    public static Vec3d tryRecall(ServerWorld world, MobEntity mob, ServerPlayerEntity owner,
                                  double recallDistance) {
        if (!needed(mob.squaredDistanceTo(owner), recallDistance)
                || mob.getEntityWorld() != owner.getEntityWorld()) return null;
        if (com.nonid.affinity.NonCreatureAffinity.getConfig().flyingCompanionRecall
                && AerialRecall.canFly(mob)) {
            return AerialRecall.tryRecall(world, mob, owner, recallDistance);
        }
        BlockPos origin = owner.getBlockPos();
        for (int dy : new int[]{0, 1, -1, 2, -2}) {
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) < 2) continue;
                BlockPos pos = origin.add(dx, dy, dz);
                if (!world.isChunkLoaded(pos) || !world.isChunkLoaded(pos.down())) continue;
                var floor = world.getBlockState(pos.down());
                if (!floor.isSideSolidFullSquare(world, pos.down(), Direction.UP)
                        || floor.isOf(Blocks.MAGMA_BLOCK) || floor.isOf(Blocks.CACTUS)
                        || floor.isOf(Blocks.CAMPFIRE) || floor.isOf(Blocks.SOUL_CAMPFIRE)) continue;
                double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
                // A successful recall must end inside the same radius that triggered it.
                // The fixed search ring can otherwise land outside a small configured radius
                // and immediately trigger another recall, even while the owner stands still.
                if (needed(owner.squaredDistanceTo(x, y, z), recallDistance)) continue;
                Box box = mob.getBoundingBox().offset(x - mob.getX(), y - mob.getY(), z - mob.getZ());
                if (!world.getWorldBorder().contains(box)) continue;
                boolean safe = true;
                for (BlockPos cell : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ),
                        BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
                    if (!world.isChunkLoaded(cell)) { safe = false; break; }
                    var state = world.getBlockState(cell);
                    if (!state.getFluidState().isEmpty() || state.isOf(Blocks.FIRE)
                            || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.POWDER_SNOW)
                            || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.WITHER_ROSE)) {
                        safe = false; break;
                    }
                }
                if (!safe || !world.isSpaceEmpty(mob, box) || !world.getOtherEntities(mob, box).isEmpty()) continue;
                if (mob.teleport(world, x, y, z, Set.of(), mob.getYaw(), mob.getPitch(), false)) {
                    mob.getNavigation().stop();
                    mob.setVelocity(Vec3d.ZERO);
                    mob.fallDistance = 0;
                    TargetRegistry.releaseIfOwned(mob, TargetRegistry.TargetSource.COMPANION_ATTACK);
                    // The same position the teleport was given, so the caller measures the state that
                    // actually exists rather than re-deriving it.
                    return new Vec3d(x, y, z);
                }
            }
        }
        return null;
    }
}
