package com.nonid.affinity.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Stage complete contents beside the destination; a failed write must not truncate the old config. */
public final class AtomicConfigFile {
    @FunctionalInterface interface Commit { void move(Path staged, Path target) throws IOException; }
    private AtomicConfigFile() {}

    public static void write(Path path, String contents) throws IOException {
        write(path, contents, (staged, target) -> Files.move(staged, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    static void write(Path path, String contents, Commit commit) throws IOException {
        Path target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path staged = Files.createTempFile(target.getParent(), "affinity-", ".tmp");
        try {
            Files.writeString(staged, contents, StandardCharsets.UTF_8);
            commit.move(staged, target);
        } finally {
            Files.deleteIfExists(staged);
        }
    }
}
