package com.nonid.affinity.client;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.net.MatingRequestPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Client entry point: registers the "request mating" key and sends a request packet.
 *
 * <p>Default keys: G requests mating, K opens the config screen (both rebindable in
 * Options - Controls - Key Binds, under the "NoN Creature Affinity" category).
 */
@Environment(EnvType.CLIENT)
public class NonCreatureAffinityClient implements ClientModInitializer {

    /** Key binding category. Created exactly once: the category registry rejects duplicates. */
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of(NonCreatureAffinity.MOD_ID, "main"));

    private static KeyBinding requestMatingKey;
    private static KeyBinding openConfigKey;
    private static KeyBinding breakFreeKey;

    @Override
    public void onInitializeClient() {
        AffinityClientNetworking.registerReceivers();
        CompanionInfoClient.register();
        CompanionMemorialClient.register();
        CompanionArchiveClient.register();
        ReunionEffectClient.register();
        ReunionChargeClient.register();
        com.nonid.affinity.client.ReunionSuccessClient.register();
        GiftHudClient.register();
        AffinityHud.register();
        AffinityIncreaseClient.register();
        FriendshipTokenClient.register();
        VillagerGiftClient.register();
        ReunionNoticeClient.register();
        CompanionModeFeedbackClient.register();
        GiftOpeningClient.register();
        AffinityLabelRenderer.register();

        requestMatingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.noncreatureaffinity.request_mating",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                CATEGORY));

        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.noncreatureaffinity.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                CATEGORY));

        // Deliberately UNBOUND: the mod must not take a key the player already uses, and this action only
        // matters while a forced mating is running. The player binds it under Controls if they want it.
        breakFreeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.noncreatureaffinity.break_free",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY));

        // Forget cached detector values when leaving the world, so a stale reading is never shown.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> {
                    ClientRequestState.disconnect();
                    ClientRequestState.clearDetector();
                    FreeTradeWindowClient.onDisconnected();
                });

        // The trade window: opened, closed, and kept in step with the recipe the player actually moves to. The
        // remove event is per screen, so it is registered for each screen as it is initialised -- there is no global
        // "a screen was closed" event in this API.
        //
        // Both the window NUMBER and the REQUEST ID are captured here, at open time, and only those values are used
        // by the removal callback. Reading them from the store at close time would read whatever window is current
        // then, and since vanilla reuses syncIds a late callback could clear and close a newer window.
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register(
                (client, screen, width, height) -> {
                    net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.afterKeyPress(screen)
                            .register((opened, key) -> {
                                if (!breakFreeKey.isUnbound() && breakFreeKey.matchesKey(key)
                                        && ClientPlayNetworking.canSend(com.nonid.affinity.net.BreakFreePayload.ID)) {
                                    ClientPlayNetworking.send(new com.nonid.affinity.net.BreakFreePayload());
                                }
                            });
                    int openedWindow = FreeTradeWindowClient.onScreenOpened(screen);
                    long openedRequest = FreeTradeWindowClient.openedRequestId(openedWindow);
                    net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.remove(screen)
                            .register(removed -> FreeTradeWindowClient.onScreenClosed(openedWindow, openedRequest));
                    if (openedWindow >= 0) {
                        // Follow the recipe the screen is showing, every tick it is open. Without this the client
                        // keeps waiting for the recipe the window opened on, refuses the server's status for the
                        // recipe the player switched to, and shows "quote updating" for the rest of the session.
                        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.beforeTick(screen)
                                .register(FreeTradeWindowClient::followScreenSelection);
                    }
                });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientRequestState.tickRequest();
            if (client.player == null) {
                return;
            }
            while (breakFreeKey.wasPressed()) {
                if (ClientPlayNetworking.canSend(com.nonid.affinity.net.BreakFreePayload.ID)) {
                    ClientPlayNetworking.send(new com.nonid.affinity.net.BreakFreePayload());
                }
            }
            if (client.currentScreen != null) return;
            // Advance the data age every tick; the readout only reads it. Counting per readout
            // call made a 40-tick threshold behave like 80 seconds.
            ClientRequestState.ageData();
            reportAimedTarget(client);
            while (openConfigKey.wasPressed()) {
                client.setScreen(new AffinityConfigScreen(client.currentScreen));
            }
            while (requestMatingKey.wasPressed()) {
                if (ClientPlayNetworking.canSend(
                        com.nonid.affinity.net.AnimationMenuRequestPayload.ID)) {
                    // Ask the server for the list so the player can choose an animation.
                    ClientPlayNetworking.send(
                            new com.nonid.affinity.net.AnimationMenuRequestPayload());
                } else {
                    NonCreatureAffinity.LOGGER.warn(
                            "[NoN Affinity] server does not have this mod installed; key ignored.");
                }
            }
        });
    }

    /**
     * Shows the aimed creature's affinity on the action bar.
     *
     * <p>This uses the chat render path on purpose: the crosshair HUD path draws the digits as
     * missing-glyph boxes on this setup, while chat text renders them correctly.
     */
    private static void reportAimedTarget(MinecraftClient client) {
        if (!AffinityDetectorState.isEnabled(client.player.getUuid())) {
            return;
        }
        // Runs every tick so a fresh server reply is shown immediately; the query itself is rate
        // limited inside sendChatReadout. The old 2-second timer delayed visible updates by up to
        // two seconds even when the data was already in hand.
        // The detector renderer owns its always-ticking lease, including hand/scan changes.
    }


    /** Exposed for HUD or debug use. */
    public static KeyBinding requestMatingKey() {
        return requestMatingKey;
    }
}

