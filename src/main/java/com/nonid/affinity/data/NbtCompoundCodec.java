package com.nonid.affinity.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.function.Function;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;

/**
 * Adapts a plain "object to NbtCompound" pair into a DataFixerUpper Codec, which is what the
 * Fabric attachment API expects for persistence.
 *
 * <p>The actual serialization logic stays in the data classes' own toNbt/fromNbt methods.
 */
public final class NbtCompoundCodec {

    private NbtCompoundCodec() {
    }

    /** Codec backed by NbtCompound conversion. */
    public static <T> Codec<T> of(Function<T, NbtCompound> encoder,
                                  Function<NbtCompound, T> decoder) {
        return Codec.PASSTHROUGH.comapFlatMap(
                dynamic -> {
                    NbtElement element = dynamic.convert(NbtOps.INSTANCE).getValue();
                    if (!(element instanceof NbtCompound compound)) {
                        return DataResult.error(() -> "expected an NBT compound but found " + element);
                    }
                    return DataResult.success(decoder.apply(compound));
                },
                value -> new Dynamic<>(NbtOps.INSTANCE, encoder.apply(value)));
    }

    /** Debug helper. */
    public static String describe(DynamicOps<?> ops) {
        return String.valueOf(ops);
    }
}
