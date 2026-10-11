package com.nonid.affinity.non.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nonid.affinity.data.BirthVariantStorage;
import com.nonid.entity.PregnancyEggEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;

/** Wrap only actual carrier owners; vanilla NBT string operations are untouched. */
@Mixin(Entity.class)
public abstract class NonVariantStorageMixin {
    @WrapMethod(method="writeData")
    private void noncreatureaffinity$writeVariants(WriteView view,Operation<Void> original){
        Object self=this;
        original.call(self instanceof PlayerEntity || self instanceof PregnancyEggEntity ? BirthVariantStorage.writer(view):view);
    }
    @WrapMethod(method="readData")
    private void noncreatureaffinity$readVariants(ReadView view,Operation<Void> original){
        Object self=this;
        original.call(self instanceof PlayerEntity || self instanceof PregnancyEggEntity ? BirthVariantStorage.reader(view):view);
    }
}
