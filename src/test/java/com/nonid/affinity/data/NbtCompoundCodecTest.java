package com.nonid.affinity.data;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NbtCompoundCodecTest {
    @Test
    void nonCompoundPayloadReturnsTypeError() {
        Codec<NbtCompound> codec = NbtCompoundCodec.of(NbtCompound::copy, NbtCompound::copy);

        var result = codec.parse(NbtOps.INSTANCE, NbtString.of("wrong-type"));

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("expected an NBT compound"));
    }
}
