package com.nonid.affinity.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DetectorStateStoreTest {
    @TempDir Path directory;

    @Test void failedInitialReadIsRetriedBeforeSavingAndKeepsOtherPlayers() throws Exception {
        Path path = Files.createDirectory(directory.resolve("state.json"));
        var store = new DetectorStateStore(path);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertFalse(store.isEnabled(first)); // A directory cannot be read as a UTF-8 file.
        Files.delete(path);
        Files.writeString(path, "# existing records\n" + first + "\n");
        assertTrue(store.toggle(second));
        var restored = new DetectorStateStore(path);
        assertTrue(restored.isEnabled(first), "an earlier read failure must not erase another player's switch");
        assertTrue(restored.isEnabled(second));
    }

    @Test void unreadableStateIsNotReplacedAndRenderingDoesNotRetryEachFrame() throws Exception {
        Path path = Files.createDirectory(directory.resolve("state.json"));
        var store = new DetectorStateStore(path);
        UUID player = UUID.randomUUID();
        assertFalse(store.isEnabled(player));
        assertFalse(store.toggle(player), "refuse to save or toggle against an unknown on-disk state");
        assertTrue(Files.isDirectory(path));
        Files.delete(path);
        Files.writeString(path, player + "\n");
        for (int i = 0; i < 100; i++) assertFalse(store.isEnabled(player));
        assertFalse(store.toggle(player), "explicit action retries the read and toggles the saved ON to OFF");
        assertFalse(new DetectorStateStore(path).isEnabled(player));
    }

    @Test void normalTogglesPersistWithoutDroppingOtherPlayers() throws Exception {
        Path path = directory.resolve("nested/state.json");
        var store = new DetectorStateStore(path);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertTrue(store.toggle(first));
        assertTrue(store.toggle(second));
        assertFalse(store.toggle(first));
        var restored = new DetectorStateStore(path);
        assertFalse(restored.isEnabled(first));
        assertTrue(restored.isEnabled(second));
        assertEquals(1, restored.enabledCount());
        assertFalse(store.isDirty());
    }

    @Test void invalidUtf8IsPreservedInsteadOfOverwritten() throws Exception {
        Path path = directory.resolve("state.json");
        byte[] broken = {(byte) 0xC3, (byte) 0x28};
        Files.write(path, broken);
        var store = new DetectorStateStore(path);
        assertFalse(store.toggle(UUID.randomUUID()));
        assertArrayEquals(broken, Files.readAllBytes(path));
    }
}
