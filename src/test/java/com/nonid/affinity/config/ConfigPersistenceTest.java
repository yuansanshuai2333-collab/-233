package com.nonid.affinity.config;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigPersistenceTest {
    @TempDir Path directory;

    @Test void oldConfigGetsSafeFeatureDefaultsWithoutChangingThreshold() throws Exception {
        Path path = directory.resolve("settings.json");
        Files.writeString(path, "{\"friendThreshold\":85}");
        var config = AffinityConfig.load(path);
        assertTrue(config.flyingCompanionRecall);
        assertFalse(config.preventFriendDamage);
        assertEquals(85, config.friendThreshold);
    }

    @Test void newFeatureSwitchesSurviveSaveAndReload() throws Exception {
        Path path = directory.resolve("settings.json");
        var config = new AffinityConfig();
        config.flyingCompanionRecall = false;
        config.preventFriendDamage = true;
        assertTrue(config.trySave(path));
        var restored = AffinityConfig.load(path);
        assertFalse(restored.flyingCompanionRecall);
        assertTrue(restored.preventFriendDamage);
    }

    @Test void removedExtraGainSettingDoesNotResetExistingConfiguration() throws Exception {
        Path path = directory.resolve("settings.json");
        Files.writeString(path, "{\"affinityPerPeak\":99,\"friendThreshold\":80,\"affinityPerMating\":12,\"affinityBonusPlayerInitiated\":4,\"affinityLossOnReject\":7,\"affinityLossOnTimeout\":8,\"affinityLossOnEscape\":9}");
        var config = AffinityConfig.load(path);
        assertEquals(80, config.friendThreshold);
        assertEquals(12, config.affinityPerMating);
        assertEquals(4, config.affinityBonusPlayerInitiated);
        assertEquals(7, config.affinityLossOnReject);
        assertEquals(8, config.affinityLossOnTimeout);
        assertEquals(9, config.affinityLossOnEscape);
        assertFalse(Files.readString(path).contains("affinityPerPeak"));
        assertOnlyTargetRemains();
    }

    @Test void existingExplicitFriendThresholdSurvivesStageUpgrade() throws Exception {
        Path path = directory.resolve("settings.json");
        Files.writeString(path, "{\"friendThreshold\":100,\"maxAffinity\":200}");
        var config = AffinityConfig.load(path);
        assertEquals(100, config.friendThreshold);
        assertEquals(25, config.familiarThreshold);
        assertEquals(50, config.controlThreshold);
        assertEquals(200, config.maxAffinity);
    }

    @Test void successfulCommitReplacesCompleteUtf8Contents() throws Exception {
        Path path = directory.resolve("settings.json");
        Files.writeString(path, "old");
        AtomicConfigFile.write(path, "{\"说明\":\"新配置\"}");
        assertEquals("{\"说明\":\"新配置\"}", Files.readString(path));
        assertOnlyTargetRemains();
    }

    @Test void failedCommitPreservesOriginalAndRemovesStagingFile() throws Exception {
        Path path = directory.resolve("settings.json");
        Files.writeString(path, "original");
        assertThrows(IOException.class, () -> AtomicConfigFile.write(path, "new", (staged, target) -> {
            assertEquals("new", Files.readString(staged));
            throw new IOException("simulated locked destination");
        }));
        assertEquals("original", Files.readString(path));
        assertOnlyTargetRemains();
    }

    @Test void unsupportedAtomicCommitDoesNotFallBackToTruncatingOriginal() throws Exception {
        Path path = directory.resolve("settings.json");
        Files.writeString(path, "original");
        assertThrows(AtomicMoveNotSupportedException.class, () -> AtomicConfigFile.write(path, "new",
                (staged, target) -> { throw new AtomicMoveNotSupportedException(staged.toString(), target.toString(), "test"); }));
        assertEquals("original", Files.readString(path));
        assertOnlyTargetRemains();
    }

    @Test void serializationFailureIsReportedWithoutTouchingOriginal() throws Exception {
        Path path = directory.resolve("settings.json");
        Files.writeString(path, "original");
        AffinityConfig config = new AffinityConfig();
        config.requestRange = Double.NaN;
        assertFalse(config.trySave(path));
        assertEquals("original", Files.readString(path));
        assertOnlyTargetRemains();
    }

    @Test void readIoFailureKeepsActiveSettingsAndDoesNotRenameThePath() throws Exception {
        Path path = Files.createDirectory(directory.resolve("settings.json"));
        AffinityConfig active = new AffinityConfig();
        active.friendThreshold = 42;
        assertSame(active, AffinityConfig.load(path, active));
        assertEquals(42, active.friendThreshold);
        assertTrue(Files.isDirectory(path));
        assertOnlyTargetRemains();
    }

    @Test void corruptJsonIsBackedUpBeforeDefaultsAreWritten() throws Exception {
        Path path = directory.resolve("settings.json");
        String broken = "{ not valid json";
        Files.writeString(path, broken);
        assertEquals(75, AffinityConfig.load(path).friendThreshold);
        try (var entries = Files.list(directory)) {
            var backups = entries.filter(p -> p.getFileName().toString().contains(".invalid-")).toList();
            assertEquals(1, backups.size());
            assertEquals(broken, Files.readString(backups.getFirst()));
        }
        assertTrue(Files.readString(path).contains("friendThreshold"));
    }

    @Test void nullJsonIsPreservedAsInvalidInsteadOfSilentlyOverwritten() throws Exception {
        Path path = directory.resolve("settings.json");
        Files.writeString(path, "null");
        AffinityConfig.load(path);
        try (var entries = Files.list(directory)) {
            Path backup = entries.filter(p -> p.getFileName().toString().contains(".invalid-")).findFirst().orElseThrow();
            assertEquals("null", Files.readString(backup));
        }
    }

    @Test void validConfigurationSurvivesReadAndWriteBack() throws Exception {
        Path path = directory.resolve("settings.json");
        Files.writeString(path, "{\"maxAffinity\":200,\"friendThreshold\":42}");
        AffinityConfig loaded = AffinityConfig.load(path);
        assertEquals(200, loaded.maxAffinity);
        assertEquals(42, loaded.friendThreshold);
        assertEquals(42, AffinityConfig.load(path).friendThreshold);
        assertOnlyTargetRemains();
    }

    private void assertOnlyTargetRemains() throws IOException {
        try (var entries = Files.list(directory)) { assertEquals(1, entries.count()); }
    }
}

