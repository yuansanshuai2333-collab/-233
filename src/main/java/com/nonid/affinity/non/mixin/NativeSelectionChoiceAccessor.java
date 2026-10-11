package com.nonid.affinity.non.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.nonid.NonDebugAnimationSelectionSystem$Choice", remap = false)
public interface NativeSelectionChoiceAccessor {
    @Accessor("index") int noncreatureaffinity$index();
    @Accessor("selectable") boolean noncreatureaffinity$selectable();
}
