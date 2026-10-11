package com.nonid.affinity.client;

import com.nonid.affinity.NonCreatureAffinity;
import net.minecraft.client.MinecraftClient;

/** Shared, side-effect-free display suppression contract for client HUDs. */
public final class ClientHudCleanup {
    private ClientHudCleanup() {}

    /** Matches the existing HUD-wide hidden/config gate without changing any state or timing. */
    public static boolean displaySuppressed(MinecraftClient client) {
        return displaySuppressed(client.options.hudHidden, NonCreatureAffinity.getConfig().hudEnabled);
    }

    static boolean displaySuppressed(boolean hudHidden, boolean hudEnabled) {
        return hudHidden || !hudEnabled;
    }
}
