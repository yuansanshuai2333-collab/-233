package com.nonid.affinity.mixin;

import com.nonid.affinity.service.CreakingMemory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.CreakingHeartBlockEntity;
import net.minecraft.entity.mob.CreakingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.Optional;

@Mixin(CreakingHeartBlockEntity.class)
public abstract class CreakingHeartMemoryMixin implements CreakingMemory.Heart {
    @Unique private NbtCompound affinity$memory = new NbtCompound();
    @Unique private boolean affinity$sourceRemoved;
    @Shadow private com.mojang.datafixers.util.Either<CreakingEntity, java.util.UUID> creakingPuppet;
    public boolean relationshipSourceRemoved() { return affinity$sourceRemoved; }
    @Unique private CreakingEntity affinity$peekPuppet() {
        if (creakingPuppet == null) return null;
        return creakingPuppet.map(entity -> entity.isRemoved() ? null : entity, id -> {
            var world = ((CreakingHeartBlockEntity)(Object)this).getWorld();
            return world instanceof ServerWorld server && server.getEntity(id) instanceof CreakingEntity entity
                    && !entity.isRemoved() ? entity : null;
        });
    }
    public NbtCompound relationshipMemory() { return affinity$memory; }
    public void relationshipMemory(NbtCompound value) {
        if (!value.equals(affinity$memory)) {
            affinity$memory = value.copy();
            ((CreakingHeartBlockEntity)(Object)this).markDirty();
        }
    }
    @Inject(method = "readData", at = @At("TAIL"))
    private void affinity$read(ReadView view, CallbackInfo ci) {
        affinity$memory = view.read("noncreatureaffinity_memory", NbtCompound.CODEC).orElseGet(NbtCompound::new);
    }
    @Inject(method = "writeData", at = @At("HEAD"))
    private void affinity$write(WriteView view, CallbackInfo ci) {
        var self = (CreakingHeartBlockEntity)(Object)this;
        if (self.getWorld() instanceof ServerWorld)
            CreakingMemory.capture(self, affinity$peekPuppet());
        if (!affinity$memory.isEmpty()) view.put("noncreatureaffinity_memory", NbtCompound.CODEC, affinity$memory);
    }
    @Inject(method = "tick", at = @At("HEAD"))
    private static void affinity$tick(World world, BlockPos pos, BlockState state, CreakingHeartBlockEntity heart, CallbackInfo ci) {
        if (!world.isClient()) {
            var access = (CreakingHeartMemoryMixin)(Object)heart;
            CreakingMemory.capture(heart, access.affinity$peekPuppet());
        }
    }
    @Inject(method = "killPuppet", at = @At("HEAD"))
    private void affinity$retire(net.minecraft.entity.damage.DamageSource source, CallbackInfo ci) {
        CreakingMemory.capture((CreakingHeartBlockEntity)(Object)this, affinity$peekPuppet());
    }
    @Inject(method = "onBlockReplaced", at = @At("HEAD"))
    private void affinity$removed(BlockPos pos, BlockState state, CallbackInfo ci) {
        affinity$sourceRemoved = true;
        var self = (CreakingHeartBlockEntity)(Object)this;
        CreakingMemory.capture(self, affinity$peekPuppet());
        CreakingMemory.sourceRemoved(self);
    }
    @Inject(method = "toInitialChunkDataNbt", at = @At("RETURN"))
    private void affinity$clientData(net.minecraft.registry.RegistryWrapper.WrapperLookup registries,
            CallbackInfoReturnable<NbtCompound> cir) {
        cir.getReturnValue().remove("noncreatureaffinity_memory");
    }
    @Inject(method = "spawnCreakingPuppet", at = @At("RETURN"))
    private static void affinity$spawn(ServerWorld world, CreakingHeartBlockEntity heart, CallbackInfoReturnable<CreakingEntity> cir) {
        CreakingMemory.restore(heart, cir.getReturnValue());
    }
}
