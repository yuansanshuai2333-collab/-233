package com.nonid.affinity.non.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nonid.affinity.data.BirthVariantStorage;
import com.nonid.entity.PregnancyEggEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;

/** The egg overrides entity IO and writes its fields after super returns. */
@Mixin(PregnancyEggEntity.class)
public abstract class NonEggVariantStorageMixin {
    @WrapMethod(method="writeData")
    private void noncreatureaffinity$writeEgg(WriteView view,Operation<Void> original){
        original.call(BirthVariantStorage.writer(view));
    }
    @WrapMethod(method="readData")
    private void noncreatureaffinity$readEgg(ReadView view,Operation<Void> original){
        original.call(BirthVariantStorage.reader(view));
    }
}
