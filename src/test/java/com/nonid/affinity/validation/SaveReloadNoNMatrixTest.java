package com.nonid.affinity.validation;

import com.nonid.affinity.data.BirthIdentity;
import com.nonid.affinity.data.BirthVariantStorage;
import com.nonid.affinity.data.CreatureAffinityData;
import com.nonid.affinity.data.PlayerAffinity;
import com.nonid.affinity.gift.GiftLedger;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.storage.ReadView;
import net.minecraft.util.ErrorReporter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.math.BigInteger;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal save/reload matrix for data-only paths. A running server is intentionally not assumed here:
 * these tests prove the payload survives an actual NBT encode/decode boundary, while the report records
 * the remaining Attachment/world lifecycle gap.
 */
class SaveReloadNoNMatrixTest {
    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String VARIANT_KEY = "NeedsOfNaturePregnancyVariantData";

    private static NbtCompound roundTrip(NbtCompound input) throws Exception {
        var bytes = new ByteArrayOutputStream();
        NbtIo.writeCompound(input, new DataOutputStream(bytes));
        return NbtIo.readCompound(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }

    private static ReadView readView(NbtCompound input) {
        return BirthVariantStorage.reader(NbtReadView.create(
                ErrorReporter.EMPTY,
                RegistryWrapper.WrapperLookup.of(java.util.stream.Stream.empty()),
                input));
    }

    private static String longVariant() {
        return "{\"" + BirthIdentity.COMPONENT + "\":\"" + "中".repeat(22000) + "\"}";
    }

    @Test
    void playerPayloadSurvivesSaveReloadWithoutChangingQuotaBoundary() throws Exception {
        var original = new PlayerAffinity(42);
        original.setBefriended(true);
        original.setQuotaInitialized(true);
        original.setRemainingFreeTrades(2);
        original.setObservedCureEpoch(9L);
        original.setLastCureSourceId("conversion-1");

        var restored = PlayerAffinity.fromNbt(roundTrip(original.toNbt()));

        assertEquals(42, restored.getAffinity());
        assertTrue(restored.isBefriended());
        assertTrue(restored.isQuotaInitialized());
        assertEquals(2, restored.getRemainingFreeTrades());
        assertEquals(9L, restored.getObservedCureEpoch());
        assertEquals("conversion-1", restored.getLastCureSourceId());
    }

    @Test
    void entityPayloadKeepsTwoPlayersIndependentAcrossReloadAndWriteback() throws Exception {
        var original = new CreatureAffinityData();
        original.getOrCreate(ALICE, 20).setBefriended(true);
        original.getOrCreate(BOB, 30).setAffinity(70, 0, 100);

        var restored = CreatureAffinityData.fromNbt(roundTrip(original.toNbt()));

        assertEquals(2, restored.size());
        assertTrue(restored.get(ALICE).isBefriended());
        assertEquals(70, restored.get(BOB).getAffinity());
        restored.get(ALICE).setAffinity(1, 0, 100);
        assertEquals(70, restored.get(BOB).getAffinity(), "a write for Alice must not change Bob's relation");
    }

    @Test
    void worldLedgerPayloadSurvivesReloadWithoutLoadedPlayersOrMobs() throws Exception {
        UUID viewer = UUID.randomUUID();
        String creature = "entity:" + UUID.randomUUID();
        var original = new GiftLedger();
        original.tick();
        original.track(viewer, creature).creditExact(BigInteger.valueOf(123), true, true);

        var restored = GiftLedger.fromNbt(roundTrip(original.toNbt()));

        assertEquals(1L, restored.clock());
        assertEquals("123", restored.get(viewer, creature).exactProgress());
    }

    @Test
    void sidecarPresentAndMissingAreDistinctAndCorruptionDoesNotAffectUnrelatedKey() throws Exception {
        String longValue = longVariant();
        var write = NbtWriteView.create(ErrorReporter.EMPTY);
        BirthVariantStorage.writer(write).putString(VARIANT_KEY, longValue);
        BirthVariantStorage.writer(write).putString("unrelated", "kept");
        var saved = roundTrip(write.getNbt());

        assertEquals(BirthVariantStorage.MARKER, saved.getString(VARIANT_KEY, ""));
        assertTrue(saved.getCompound(BirthVariantStorage.sidecar(VARIANT_KEY)).isPresent());
        assertEquals(longValue, readView(saved).getOptionalString(VARIANT_KEY).orElseThrow());

        saved.getCompound(BirthVariantStorage.sidecar(VARIANT_KEY)).orElseThrow().putInt("chars", 1);
        var corrupted = readView(saved);
        assertThrows(IllegalStateException.class, () -> corrupted.getOptionalString(VARIANT_KEY));
        assertEquals("kept", corrupted.getOptionalString("unrelated").orElseThrow());

        var missing = roundTrip(write.getNbt());
        missing.remove(BirthVariantStorage.sidecar(VARIANT_KEY));
        assertThrows(IllegalStateException.class, () -> readView(missing).getOptionalString(VARIANT_KEY));
    }

    @Test
    void ordinaryNoNSaveHasNoSidecarAndMissingOptionalFieldRemainsAbsent() throws Exception {
        String shortValue = "{\"" + BirthIdentity.COMPONENT + "\":\"short\"}";
        var write = NbtWriteView.create(ErrorReporter.EMPTY);
        BirthVariantStorage.writer(write).putString(VARIANT_KEY, shortValue);
        var saved = roundTrip(write.getNbt());

        assertEquals(shortValue, saved.getString(VARIANT_KEY, ""));
        assertFalse(saved.contains(BirthVariantStorage.sidecar(VARIANT_KEY)));
        var read = readView(saved);
        assertTrue(read.getOptionalString("NoNSidecarThatWasNeverWritten").isEmpty());
    }
}


