package com.nonid.affinity.gift;

import com.google.gson.*;
import com.nonid.affinity.NonCreatureAffinity;
import java.io.IOException;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;

/** Separate server lifetime setting; no unfinished setting row is inserted into the existing screen. */
public final class GiftSettings {
    private GiftSettings() {}
    public static int expiryDays() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("noncreatureaffinity-gifts.json");
        if (!Files.exists(path)) return 60;
        try (var reader = Files.newBufferedReader(path)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject(); int days = json.get("bequestExpiryDays").getAsInt();
            if (days < 1 || days > Integer.MAX_VALUE / 24000) throw new IllegalArgumentException("Invalid bequestExpiryDays"); return days;
        } catch (IOException | RuntimeException invalid) {
            NonCreatureAffinity.LOGGER.warn("Cannot read gift lifetime {}; retaining confirmed 60-day default", path, invalid); return 60;
        }
    }
}
