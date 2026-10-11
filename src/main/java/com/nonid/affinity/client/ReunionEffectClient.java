package com.nonid.affinity.client;

import com.nonid.affinity.item.NonAffinityItems;
import com.nonid.affinity.net.ReunionEffectPayload;
import com.nonid.affinity.net.ReunionResultPayload;
import com.nonid.affinity.reunion.ReunionAnimation;
import com.nonid.affinity.reunion.ReunionData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.storage.NbtReadView;
import net.minecraft.util.Arm;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/** Local-only display entities: never dropped, saved, picked up or sent back to the server. */
@Environment(EnvType.CLIENT)
public final class ReunionEffectClient {
    private static final Map<UUID, Effect> EFFECTS = new LinkedHashMap<>();
    private static final SoundEvent SOUND = SoundEvent.of(ReunionData.id("reunion_revive"));
    private static final int SOUND_END = 185; // The supplied clip is nine seconds; let its quiet tail finish.
    private static int nextVisualId = -1_000_000;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ReunionEffectPayload.ID,
                (payload, context) -> context.client().execute(() -> start(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ReunionResultPayload.ID,
                (payload, context) -> context.client().execute(() -> finish(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ReunionEffectClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear(client));
    }

    private static void finish(ReunionResultPayload payload) {
        var effect = EFFECTS.get(payload.player());
        if (effect == null || !effect.bond.equals(payload.bond())) return;
        if (!payload.success()) {
            EFFECTS.remove(payload.player()); effect.close(MinecraftClient.getInstance(), true);
        } else {
            effect.confirmed = true;
            effect.age = Math.max(effect.age, ReunionAnimation.TURN_END);
            effect.update();
        }
    }

    public static void start(ReunionEffectPayload payload) {
        var client = MinecraftClient.getInstance();
        var world = client.world;
        if (world == null || client.player == null || !world.getRegistryKey().getValue().equals(payload.dimension())
                || payload.bond().isEmpty() || !finite(payload.revivedAt())
                || !Float.isFinite(payload.creatureHeight()) || payload.creatureHeight() < 0) return;
        var player = world.getPlayerByUuid(payload.player());
        if (player == null || !player.isAlive() || player.squaredDistanceTo(client.player) > 32 * 32) return;
        var previous = EFFECTS.remove(payload.player());
        if (previous != null) previous.close(client, true);
        if (EFFECTS.size() >= 16) {
            var oldest = EFFECTS.entrySet().iterator(); var old = oldest.next();
            old.getValue().close(client, true); oldest.remove();
        }
        var effect = new Effect(world, player, payload);
        EFFECTS.put(payload.player(), effect);
        if (com.nonid.affinity.NonCreatureAffinity.getConfig().reunionReviveSound) client.getSoundManager().play(effect.sound);
        // A modest burst around the revived creature, not an all-screen flash.
        var p = payload.revivedAt();
        for (int i = 0; i < 60; i++) {
            double angle = i * Math.PI * 2 / 60;
            point(p.x + Math.cos(angle) * .5, p.y + .45 + (i % 6) * .16,
                    p.z + Math.sin(angle) * .5, Math.cos(angle) * .018, .025, Math.sin(angle) * .018, .2f);
        }
    }

    private static void point(double x, double y, double z, double vx, double vy, double vz, float scale) {
        var client = MinecraftClient.getInstance();
        var particle = client.particleManager.addParticle(ParticleTypes.END_ROD, x, y, z, vx, vy, vz);
        if (particle != null) { particle.scale(scale); particle.setMaxAge(18); }
    }

    private static boolean finite(Vec3d p) { return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) { clear(client); return; }
        if (client.isPaused()) return;
        var iterator = EFFECTS.values().iterator();
        while (iterator.hasNext()) {
            var effect = iterator.next();
            if (!com.nonid.affinity.NonCreatureAffinity.getConfig().reunionReviveSound) client.getSoundManager().stop(effect.sound);
            if (effect.world != client.world || !effect.player.isAlive() || effect.player.isRemoved()
                    || effect.player.squaredDistanceTo(client.player) > 40 * 40
                    || (effect.age < ReunionAnimation.END && !effect.matches(effect.player.getStackInHand(effect.hand)))) {
                effect.close(client, true); iterator.remove(); continue;
            }
            if (++effect.elapsed >= 20 * 30) { effect.close(client, true); iterator.remove(); continue; }
            effect.age = effect.confirmed ? effect.age + 1 : Math.min(effect.age + 1, ReunionAnimation.TURN_END - 1);
            if (effect.age >= SOUND_END) { effect.close(client, false); iterator.remove(); continue; }
            if (effect.age >= ReunionAnimation.END) { effect.removeGem(); continue; }
            effect.update();
        }
    }

    public static boolean hides(LivingEntity player, Hand hand, ItemStack stack) {
        var effect = EFFECTS.get(player.getUuid());
        return effect != null && effect.age < ReunionAnimation.END && effect.world == player.getEntityWorld()
                && hand == effect.hand && effect.matches(stack);
    }

    private static void clear(MinecraftClient client) {
        EFFECTS.values().forEach(effect -> effect.close(client, true)); EFFECTS.clear();
    }

    private static final class Effect {
        final ClientWorld world;
        final PlayerEntity player;
        final String bond;
        final Hand hand;
        final ItemDisplayEntity gem;
        final PositionedSoundInstance sound;
        final Vec3d departure;
        final Vec3d destination;
        final float facingYaw;
        int age;
        int elapsed;
        boolean dark;
        boolean confirmed;

        Effect(ClientWorld world, PlayerEntity player, ReunionEffectPayload payload) {
            this.world = world; this.player = player; bond = payload.bond();
            hand = payload.offHand() ? Hand.OFF_HAND : Hand.MAIN_HAND;
            departure = handPosition();
            // Same spawn column, just above the creature so its body does not hide the gem.
            destination = payload.revivedAt().add(0, Math.max(1, payload.creatureHeight() + .35), 0);
            facingYaw = player.getYaw();
            gem = new ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
            // Server IDs are positive. Still check for another client mod's local display ID.
            while (world.getEntityById(nextVisualId) != null) nextVisualId--;
            gem.setId(nextVisualId--);
            var data = new NbtCompound();
            data.putString("item_display", "fixed");
            var brightness = new NbtCompound(); brightness.putInt("block", 15); brightness.putInt("sky", 15);
            data.put("brightness", brightness); data.putFloat("width", 1); data.putFloat("height", 1);
            data.putFloat("shadow_radius", 0); data.putFloat("view_range", 1);
            var transform = new NbtCompound();
            transform.put("translation", floats(0, 0, 0)); transform.put("scale", floats(.45f, .45f, .45f));
            transform.put("left_rotation", floats(0, 0, 0, 1)); transform.put("right_rotation", floats(0, 0, 0, 1));
            data.put("transformation", transform);
            gem.readData(NbtReadView.create(ErrorReporter.EMPTY, world.getRegistryManager(), data));
            setModel(false); update(); gem.lastRenderX = gem.getX(); gem.lastRenderY = gem.getY(); gem.lastRenderZ = gem.getZ();
            world.addEntity(gem);
            sound = new PositionedSoundInstance(SOUND, SoundCategory.PLAYERS, .525f, 1f, Random.create(),
                    player.getX(), player.getEyeY(), player.getZ());
        }

        boolean matches(ItemStack stack) {
            return stack.isOf(NonAffinityItems.REUNION_WISH) && bond.equals(ReunionData.item(stack).getString("bond", ""));
        }

        private static NbtList floats(float... values) {
            var list = new NbtList(); for (float value : values) list.add(NbtFloat.of(value)); return list;
        }

        void setModel(boolean dim) {
            var model = new ItemStack(NonAffinityItems.REUNION_WISH);
            model.set(DataComponentTypes.ITEM_MODEL, ReunionData.id(dim ? "reunion_dim" : "reunion_charged"));
            gem.getStackReference(0).set(model);
        }

        Vec3d handPosition() {
            double yaw = Math.toRadians(player.getYaw());
            var forward = player.getRotationVec(1f);
            var right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
            var up = right.crossProduct(forward);
            Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
            double side = arm == Arm.RIGHT ? .32 : -.32;
            return player.getEyePos().add(up.multiply(-.55)).add(forward.multiply(.4)).add(right.multiply(side));
        }

        void update() {
            // Spin at the server-confirmed spawn column. Only the return endpoint follows the moving hand.
            var pos = ReunionAnimation.position(age, departure, destination, handPosition());
            gem.setPosition(pos);
            gem.setYaw(facingYaw + (float) ReunionAnimation.degrees(age));
            gem.setPitch(0);
            if (!dark && ReunionAnimation.dim(age)) {
                dark = true; setModel(true);
                for (int i = 0; i < 36; i++) {
                    double a = i * Math.PI * 2 / 36;
                    point(pos.x + Math.cos(a) * .45, pos.y + (i % 5 - 2) * .14, pos.z + Math.sin(a) * .45,
                            Math.cos(a) * .025, .018, Math.sin(a) * .025, .16f);
                }
            }
            if (age > 0 && age < ReunionAnimation.TURN_END && age % 2 == 0) {
                for (int i = 0; i < 3; i++) {
                    double a = age * .45 + i * Math.PI * 2 / 3;
                    point(pos.x + Math.cos(a) * .3, pos.y + (i - 1) * .14, pos.z + Math.sin(a) * .3,
                            Math.cos(a) * .009, .018, Math.sin(a) * .009, .12f);
                }
            }
        }

        void removeGem() {
            if (world.getEntityById(gem.getId()) == gem) world.removeEntity(gem.getId(), Entity.RemovalReason.DISCARDED);
        }
        void close(MinecraftClient client, boolean stopSound) { removeGem(); if (stopSound) client.getSoundManager().stop(sound); }
    }
    private ReunionEffectClient() { }
}
