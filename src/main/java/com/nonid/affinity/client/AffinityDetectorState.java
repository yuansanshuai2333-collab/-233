package com.nonid.affinity.client;

import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

/** Client-owned scan switches, shared by all detector items and persisted per player. */
@Environment(EnvType.CLIENT)
public final class AffinityDetectorState {
    private static final DetectorStateStore STATE = new DetectorStateStore(FabricLoader.getInstance()
            .getConfigDir().resolve("noncreatureaffinity_client_state.json"));

    private AffinityDetectorState() {}

    /** An integrated server also has client classes: require the actual client thread. */
    public static boolean isActive() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return false;
        var client = net.minecraft.client.MinecraftClient.getInstance();
        return client != null && client.isOnThread();
    }

    public static boolean isEnabled(UUID player) { return STATE.isEnabled(player); }
    public static boolean toggle(UUID player) { return STATE.toggle(player); }
    public static int enabledCount() { return STATE.enabledCount(); }
    static boolean isDirty() { return STATE.isDirty(); }
}
