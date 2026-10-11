package com.nonid.affinity.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side mirror of a pending mating request, fed by {@code request_state} packets.
 *
 * <p>Values are only used for HUD display; all gameplay decisions stay on the server.
 */
@Environment(EnvType.CLIENT)
public final class ClientRequestState {

    private static String mobName;
    private static java.util.UUID requestMob;
    private static long expireTick;
    private static long businessToken, lastSoundToken;
    private static String requestDimension = "";
    private static int desireStage;
    private static com.nonid.affinity.service.RequestEnergy repression = com.nonid.affinity.service.RequestEnergy.UNKNOWN;
    private static boolean submitted;
    public static void update(com.nonid.affinity.net.RequestStatePayload p) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (p.expireTick() > 0 && (client.world == null || p.token() <= 0
                || !client.world.getRegistryKey().getValue().toString().equals(p.dimension())
                || client.world.getTime() >= p.expireTick())) return;
        if (!LEASE.receive(p.requestId(), p.expireTick() > 0)) return;
        if (p.expireTick() <= 0) {
            if (java.util.Objects.equals(requestMob, p.mobUuid())) clear();
            return;
        }
        boolean changed = businessToken != p.token() || !java.util.Objects.equals(requestMob, p.mobUuid());
        requestMob = p.mobUuid(); mobName = p.mobName(); expireTick = p.expireTick();
        affinity = p.affinity(); threshold = p.threshold(); requestWorld = client.world;
        businessToken = p.token(); requestDimension = p.dimension(); desireStage = p.desireStage(); repression = p.repression();
        if (changed) submitted = false;
        if (isActive() && businessToken > lastSoundToken) {
            lastSoundToken = businessToken;
            client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.ui(
                    net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.5f, .25f));
        }
    }
    public static net.minecraft.entity.LivingEntity requestEntity() {
        if (requestWorld == null || requestMob == null) return null;
        for (var e : requestWorld.getEntities())
            if (e instanceof net.minecraft.entity.LivingEntity living && e.getUuid().equals(requestMob)) return living;
        return null;
    }
    public static long token() { return businessToken; }
    public static com.nonid.affinity.service.RequestEnergy repression() { return repression; }
    public static int desireStage() { return desireStage; }
    public static boolean submitted() { return submitted; }
    public static boolean answer(long renderedToken, boolean accept) {
        if (!isActive() || submitted || renderedToken != businessToken || businessToken <= 0
                || !net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
                        com.nonid.affinity.net.RequestAnswerPayload.ID)) return false;
        submitted = true;
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.nonid.affinity.net.RequestAnswerPayload(requestMob, requestDimension, businessToken, expireTick, accept));
        return true;
    }
    private static int affinity;
    private static int threshold;
    private static net.minecraft.client.world.ClientWorld requestWorld;
    private static final RequestHudLease LEASE = new RequestHudLease();

    private ClientRequestState() {
    }

    /** Store a new request, or clear it when {@code newExpireTick} is 0. */
    public static void update(java.util.UUID mobUuid, String newMobName, long newExpireTick,
                              int newAffinity, int newThreshold, long requestId) {
        if (!LEASE.receive(requestId, newExpireTick > 0)) return;
        if (newExpireTick <= 0L) {
            if (java.util.Objects.equals(requestMob, mobUuid)) clear();
            return;
        }
        requestMob = mobUuid;
        mobName = newMobName;
        expireTick = newExpireTick;
        affinity = newAffinity;
        threshold = newThreshold;
        requestWorld = net.minecraft.client.MinecraftClient.getInstance().world;
    }

    public static void clear() {
        LEASE.close();
        clearDisplay();
    }

    private static void clearDisplay() {
        businessToken = 0; requestDimension = ""; desireStage = 0; submitted = false; repression = com.nonid.affinity.service.RequestEnergy.UNKNOWN;
        requestMob = null;
        mobName = null;
        expireTick = 0L;
        affinity = 0;
        threshold = 0;
        requestWorld = null;
    }

    public static void disconnect() { clear(); LEASE.reset(); lastSoundToken = 0; }
    public static void tickRequest() {
        if (net.minecraft.client.MinecraftClient.getInstance().isPaused()) return;
        // Silence hides the banner, but an authoritative refresh may recover from network lag.
        // Explicit cancellation, death and world changes still permanently close that identity.
        if (!LEASE.tick()) clearDisplay();
        else isActive();
    }

    // ---- detector values, pushed by the server ----

    private static boolean hasData;
    private static String familyLabel = "";
    private static String stage = "";
    public static void updateStage(java.util.UUID target, String value) {
        if (target == null || !target.toString().equals(requestedTargetKey)) return;
        stage = switch (value) {
            case "new", "stranger", "familiar", "close", "friend" -> value;
            default -> "";
        };
    }
    public static String stageKey() { return stage.isEmpty() ? "" : "stage.noncreatureaffinity." + stage; }

    public static void updateFamily(java.util.UUID targetUuid, String label) {
        if (targetUuid == null || !targetUuid.toString().equals(requestedTargetKey)) return;
        familyLabel = switch (label) {
            case "son", "daughter", "child", "grandson", "granddaughter", "grandchild", "descendant",
                    "father", "mother", "parent", "grandfather", "grandmother", "grandparent", "ancestor" -> label;
            default -> "";
        };
    }

    public static String familyLabel() { return familyLabel; }
    private static int serverAffinity;
    private static int serverMaxAffinity = 100;
    private static int serverMatings;
    private static boolean serverFriend;

    /** Store the authoritative relation values for the aimed creature. */
    /**
     * Store the authoritative values.
     *
     * @param targetUuid the creature these belong to; replies for any other target are discarded
     */
    public static void updateDetector(java.util.UUID targetUuid, boolean found, int affinityValue,
                                      int maxAffinity, int matings, boolean friend,
                                      String companionMode, int friendThreshold, int tokensReceived) {
        if (targetUuid == null || !targetUuid.toString().equals(requestedTargetKey)) {
            // A late reply for a previous target: ignore it rather than showing stale numbers.
            return;
        }
        if (!hasData) {
            // Values were cleared because the target changed; only accept a reply that matches.
            serverFriend = false;
        }
        hasData = true;
        dataFound = found;
        // Invalid/missing replies do not necessarily have companion label packets.
        // Drop labels from the previous successful query of this same creature.
        if (!found) {
            stage = "";
            familyLabel = "";
        }
        dataAgeTicks = 0L;
        serverAffinity = affinityValue;
        serverMaxAffinity = Math.max(1, maxAffinity);
        serverMatings = matings;
        serverFriend = friend;
        serverCompanionMode = companionMode == null ? "" : companionMode;
        serverFriendThreshold = friendThreshold;
        serverTokensReceived = Math.max(0, tokensReceived);
        // Marks the readout as "needs redisplay", which a value-based signature could not express.
        dirty = true;
    }

    /** Forget the cached values (target lost, world change, disconnect). */
    public static void clearDetector() {
        stage = "";
        familyLabel = "";
        hasData = false;
        dirty = false;
        dataFound = false;
        requestedTargetKey = null;
        dataAgeTicks = 0L;
        serverCompanionMode = "";
        serverTokensReceived = 0;
        serverAffinity = 0;
        serverMaxAffinity = 100;
        serverMatings = 0;
        serverFriend = false;
    }

    private static boolean dirty;
    private static boolean dataFound;
    /** Target of the outstanding query, as a string so identity is unambiguous. */
    private static String requestedTargetKey;
    /** Tick at which the stored values were received, used to refresh stale data. */
    private static long dataAgeTicks;

    /**
     * Point the readout at a creature.
     *
     * @return true when the target actually changed (the caller should re-query)
     */
    public static boolean setRequestedTarget(java.util.UUID targetUuid) {
        String key = targetUuid == null ? null : targetUuid.toString();
        if (key == null) {
            return false;
        }
        if (key.equals(requestedTargetKey)) {
            // Same creature: keep whatever we already have, so the display does not flash.
            return false;
        }
        // Genuinely a different creature: every cached value must be dropped, otherwise the new
        // creature can briefly show the previous one's marker or numbers.
        requestedTargetKey = key;
        stage = "";
        familyLabel = "";
        hasData = false;
        dirty = false;
        dataFound = false;
        serverAffinity = 0;
        serverMaxAffinity = 100;
        serverMatings = 0;
        serverFriend = false;
        serverCompanionMode = "";
        serverTokensReceived = 0;
        return true;
    }

    /** Age the stored values one tick so the caller can refresh them periodically. */
    public static void ageData() {
        dataAgeTicks++;
        ticksSinceQuery++;
    }

    /** Ticks since the last query was sent, used to pace outgoing packets. */
    public static long ticksSinceQuery() {
        return ticksSinceQuery;
    }

    /** Record that a query was just sent. */
    public static void markQuerySent() {
        ticksSinceQuery = 0L;
    }

    private static long ticksSinceQuery;

    public static long dataAgeTicks() {
        return dataAgeTicks;
    }

    public static void resetDataAge() {
        dataAgeTicks = 0L;
    }

    /** Whether the newly received values still need to be displayed. */
    public static boolean consumeDirty() {
        if (!dirty) {
            return false;
        }
        dirty = false;
        return true;
    }

    /**
     * The creature the current values belong to, or null when nothing is targeted.
     *
     * <p>The renderer uses this so the name shown and the values shown always describe the same
     * creature.
     */
    public static java.util.UUID currentTargetUuid() {
        if (requestedTargetKey == null) {
            return null;
        }
        try {
            return java.util.UUID.fromString(requestedTargetKey);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String serverCompanionMode = "";

    /** Affinity needed to become a friend; the panel's bar is scaled against this. */
    private static int serverFriendThreshold = 100;

    public static int serverFriendThreshold() {
        return serverFriendThreshold;
    }

    /** Tokens this player actually received from the current target. */
    private static int serverTokensReceived;

    public static int serverTokensReceived() {
        return serverTokensReceived;
    }

    /** "FOLLOW", "WANDER", or "" when this player is not the companion owner. */
    public static String serverCompanionMode() {
        return serverCompanionMode;
    }

    /** Whether the server reported an existing relation. */
    public static boolean dataFound() {
        return dataFound;
    }

    public static boolean hasDetectorData() {
        return hasData;
    }

    public static int serverAffinity() {
        return serverAffinity;
    }

    public static int serverMaxAffinity() {
        return serverMaxAffinity;
    }

    public static int serverMatings() {
        return serverMatings;
    }

    public static boolean serverFriend() {
        return serverFriend;
    }

    public static boolean isActive() {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        var world = client.world;
        if (client.player == null || !client.player.isAlive()
                || world == null || world != requestWorld || world.getTime() >= expireTick) clear();
        if (mobName != null && businessToken > 0) {
            var entity = requestEntity();
            if (entity == null || !entity.isAlive() || entity.isRemoved() || client.player.isSpectator()
                    || entity.squaredDistanceTo(client.player) > 256) clear();
        }
        return mobName != null && expireTick > 0L;
    }

    @Nullable
    public static String mobName() {
        return mobName;
    }

    public static long expireTick() {
        return expireTick;
    }

    public static int affinity() {
        return affinity;
    }

    public static int threshold() {
        return threshold;
    }
}
