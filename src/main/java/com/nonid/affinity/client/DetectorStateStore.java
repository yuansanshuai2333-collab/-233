package com.nonid.affinity.client;

import com.nonid.affinity.NonCreatureAffinity;
import java.io.IOException;
import com.nonid.affinity.config.AtomicConfigFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which players have the affinity scan switched on.
 *
 * <p>The switch belongs to the <b>player</b>, not to a particular detector item, so any detector
 * can be used to turn it on or off. The set is kept in a small file under the config directory so
 * the scan survives a restart.
 */

final class DetectorStateStore {

    private final Path path;
    private final Set<String> enabled = new LinkedHashSet<>();

    private boolean loaded;
    private boolean loadAttempted;
    private boolean dirty;

    DetectorStateStore(Path path) {
        this.path = path;
    }

    /**
     * Whether the scan is currently on for this player.
     *
     * <p>Deliberately SILENT, even under verbose logging. This is called from the panel's render path, so
     * a diagnostic line here runs once per frame: with logging on, one session produced over thirteen
     * thousand identical lines, which buries the behaviour evidence the logging exists to collect. The
     * state itself is reported by {@link #toggle} when it actually CHANGES, which is the useful moment.
     */
    public boolean isEnabled(UUID player) {
        if (player == null) {
            return false;
        }
        ensureLoaded(false);
        return enabled.contains(player.toString());
    }

    /** Flip the switch and persist it. */
    public boolean toggle(UUID player) {
        if (player == null) {
            return false;
        }
        // Never overwrite a file whose previous contents are unknown. Explicit actions may retry;
        // render-time reads may not, otherwise a locked file would be retried every frame.
        if (!ensureLoaded(true)) return enabled.contains(player.toString());
        String key = player.toString();
        boolean nowEnabled;
        if (enabled.remove(key)) {
            nowEnabled = false;
        } else {
            enabled.add(key);
            nowEnabled = true;
        }
        save();
        // Logged here, on the transition, rather than on every read.
        if (NonCreatureAffinity.LOG_VERBOSE) {
            NonCreatureAffinity.LOGGER.info("[NoN Affinity] scan state for {} = {}", player, nowEnabled);
        }
        return nowEnabled;
    }

    private boolean ensureLoaded(boolean retry) {
        if (loaded) return true;
        if (loadAttempted && !retry) return false;
        loadAttempted = true;
        if (Files.notExists(path)) {
            loaded = true;
            return true;
        }
        try {
            String contents = Files.readString(path, StandardCharsets.UTF_8);
            // Deliberately dependency free: the file is a flat list of uuids, one per line.
            for (String line : contents.split("\\R")) {
                String value = line.trim();
                if (!value.isEmpty() && !value.startsWith("#")) {
                    enabled.add(value);
                }
            }
            loaded = true;
            return true;
        } catch (IOException exception) {
            NonCreatureAffinity.LOGGER.warn("[NoN Affinity] could not read detector state: {}",
                    exception.toString());
            return false;
        }
    }

    private void save() {
        dirty = true;
        try {
            StringBuilder contents = new StringBuilder(
                    "# noncreatureaffinity: players with the affinity scan enabled\n");
            for (String value : enabled) {
                contents.append(value).append('\n');
            }
            AtomicConfigFile.write(path, contents.toString());
            dirty = false;
        } catch (IOException exception) {
            NonCreatureAffinity.LOGGER.warn("[NoN Affinity] could not save detector state: {}",
                    exception.toString());
        }
    }

    /** For diagnostics. */
    public int enabledCount() {
        ensureLoaded(false);
        return enabled.size();
    }

    boolean isDirty() {
        return dirty;
    }
}


