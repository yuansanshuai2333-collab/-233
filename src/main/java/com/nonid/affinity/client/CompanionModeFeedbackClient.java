package com.nonid.affinity.client;

import com.nonid.affinity.net.CompanionModeFeedbackPayload;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Bounded, replace-in-place display for server-confirmed control changes. */
@Environment(EnvType.CLIENT)
public final class CompanionModeFeedbackClient {
    private static final long DISPLAY_MS = 1500L;
    private static final int MAX_ENTRIES = 3;
    private static final Map<UUID, Entry> ENTRIES = new LinkedHashMap<>();
    private static Identifier dimension;

    private record Entry(CompanionModeFeedbackPayload payload, long started) {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CompanionModeFeedbackPayload.ID,
                (payload, context) -> context.client().execute(() -> accept(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
        HudElementRegistry.addLast(Identifier.of("noncreatureaffinity", "companion_mode_feedback"),
                CompanionModeFeedbackClient::render);
    }

    private static void accept(CompanionModeFeedbackPayload payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null
                || !payload.dimension().equals(client.world.getRegistryKey().getValue())) return;
        if (!payload.dimension().equals(dimension)) {
            clear();
            dimension = payload.dimension();
        }
        ENTRIES.put(payload.target(), new Entry(payload, System.currentTimeMillis()));
        while (ENTRIES.size() > MAX_ENTRIES) ENTRIES.remove(ENTRIES.keySet().iterator().next());
    }

    private static void tick(MinecraftClient client) {
        if (ClientHudCleanup.displaySuppressed(client)) {
            clear();
            return;
        }
        if (client.world == null || client.player == null || !client.player.isAlive()
                || !client.world.getRegistryKey().getValue().equals(dimension)) {
            clear();
            if (client.world != null) dimension = client.world.getRegistryKey().getValue();
            return;
        }
        long now = System.currentTimeMillis();
        ENTRIES.entrySet().removeIf(entry -> now - entry.getValue().started() >= DISPLAY_MS);
    }

    private static void clear() {
        ENTRIES.clear();
        dimension = null;
    }

    private static void render(DrawContext context, RenderTickCounter tick) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (ENTRIES.isEmpty() || client.world == null || client.player == null || client.currentScreen != null
                || ClientHudCleanup.displaySuppressed(client)) return;
        int width = client.getWindow().getScaledWidth();
        int y = 8;
        int boxWidth = Math.min(260, Math.max(150, width / 3));
        for (Entry entry : ENTRIES.values()) {
            long age = System.currentTimeMillis() - entry.started();
            if (age >= DISPLAY_MS) continue;
            float fade = age < 1100 ? 1f : 1f - (age - 1100f) / 400f;
            int alpha = Math.max(0, Math.min(255, Math.round(fade * 220)));
            int x = width - boxWidth - 8;
            context.fill(x, y, x + boxWidth, y + 34, (alpha << 24) | 0x24182c);
            context.drawText(client.textRenderer, entry.payload().name(), x + 8, y + 6,
                    (alpha << 24) | 0xf8edf2, false);
            context.drawText(client.textRenderer, entry.payload().mode(), x + 8, y + 19,
                    (alpha << 24) | 0xefb8d3, false);
            y += 38;
        }
    }

    private CompanionModeFeedbackClient() {}
}
