package com.nonid.affinity.non.mixin;

import java.util.List;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the server-created session; no client-supplied actors or choices are trusted. */
@Mixin(targets = "com.nonid.NonDebugAnimationSelectionSystem$Session", remap = false)
public interface NativeSelectionSessionAccessor {
    @Accessor("id") UUID noncreatureaffinity$id();
    @Accessor("requesterUuid") UUID noncreatureaffinity$requester();
    @Accessor("actorUuids") List<UUID> noncreatureaffinity$actors();
    @Accessor("choices") List<?> noncreatureaffinity$choices();
}
