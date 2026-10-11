package com.nonid.affinity.data;

import com.nonid.affinity.NonCreatureAffinity;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.util.Identifier;

/**
 * Registers the entity attachment that holds affinity data.
 *
 * <p>Uses the Fabric Data Attachment API, the recommended way to persist per-entity
 * custom data on modern Minecraft. This avoids mixing into an entity's NBT read/write
 * methods and therefore keeps the risk of conflicting with NeedsOfNature Mixins low.
 */
public final class AffinityAttachments {
    /** Exact birth evidence on eggs and newborns; no default, ownership, or relation creation. */
    public static final AttachmentType<net.minecraft.nbt.NbtCompound> BIRTH_IDENTITY = AttachmentRegistry.create(
            Identifier.of("noncreatureaffinity", "birth_identity"),
            builder -> builder.persistent(NbtCompoundCodec.of(n -> n.copy(), n -> n.copy())));
    /** Injury accounting only; does not create player relationships or survive resurrection. */
    public static final AttachmentType<HealthDebt> HEALTH_DEBT = AttachmentRegistry.create(
            Identifier.of("noncreatureaffinity", "health_debt"),
            builder -> builder.persistent(NbtCompoundCodec.of(HealthDebt::toNbt, HealthDebt::fromNbt)));
    /** Stored on the originating projectile/cloud, so its continuing damage is one attack per pair. */
    public static final AttachmentType<net.minecraft.nbt.NbtCompound> FRIEND_HIT_PAIRS = AttachmentRegistry.create(
            Identifier.of("noncreatureaffinity", "friend_hit_pairs"),
            builder -> builder.persistent(NbtCompoundCodec.of(n -> n.copy(), n -> n.copy())));

    /** Player-owned opt-in. Missing data means disabled; reading never attaches a default. */
    public static final AttachmentType<Boolean> DIRECT_INTERACTION = AttachmentRegistry.create(
            Identifier.of("noncreatureaffinity", "direct_interaction"),
            builder -> builder.persistent(com.mojang.serialization.Codec.BOOL).copyOnDeath());

    /** Explicit player × stable creature choices. Legacy global attachment is not consulted. */
    public static final AttachmentType<net.minecraft.nbt.NbtCompound> CREATURE_DIRECT_INTERACTION = AttachmentRegistry.create(
            Identifier.of("noncreatureaffinity", "creature_direct_interaction"),
            builder -> builder.persistent(NbtCompoundCodec.of(n -> n.copy(), n -> n.copy())).copyOnDeath());

    /** Attachment id: {@code noncreatureaffinity:affinity}. */
    public static final Identifier AFFINITY_ID =
            Identifier.of("noncreatureaffinity", "affinity");

    /** Affinity data attachment: persisted to entity NBT and kept through death. */
    public static final AttachmentType<CreatureAffinityData> AFFINITY = AttachmentRegistry.create(
            AFFINITY_ID,
            builder -> builder
                    .initializer(CreatureAffinityData::new)
                    .persistent(NbtCompoundCodec.of(CreatureAffinityData::toNbt, CreatureAffinityData::fromNbt))
                    .copyOnDeath());

    private AffinityAttachments() {
    }

    /** Force class initialization so the attachment is registered before entities load NBT. */
    public static void init() {
        NonCreatureAffinity.LOGGER.debug("[NoN Affinity] registered attachment {}", AFFINITY_ID);
    }
}

