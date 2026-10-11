package com.nonid.affinity.non.mixin;

import com.nonid.NeedsOfNature.PregnancyVariantData;
import java.util.UUID;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exact immutable hatch snapshot, including cross-species births that discard appearance. */
@Mixin(targets = "com.nonid.NeedsOfNature$PregnancyHatchState", remap = false)
public interface NonPregnancyHatchAccess {
    @Accessor("playerUuid") UUID noncreatureaffinity$mother();
    @Accessor("sourceEntityTypeId") Identifier noncreatureaffinity$source();
    @Accessor("variantData") PregnancyVariantData noncreatureaffinity$variant();
}
