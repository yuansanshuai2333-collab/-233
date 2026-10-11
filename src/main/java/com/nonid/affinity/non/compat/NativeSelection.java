package com.nonid.affinity.non.compat;

import com.nonid.affinity.NonCreatureAffinity;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.HashMap;
import com.nonid.affinity.non.mixin.NativeSelectionSessionAccessor;
import com.nonid.affinity.non.mixin.NativeSelectionChoiceAccessor;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/** Associates only addon-opened native menus with their original target. Debug menus stay independent. */
public final class NativeSelection {
    private record Target(ServerWorld world, UUID player, UUID creature, int openedTick) {}
    private static final class Menu {
        final Target target;
        final Object session;
        boolean consumed;
        Menu(Target target, Object session) { this.target = target; this.session = session; }
    }
    private static final ThreadLocal<Target> OPENING = new ThreadLocal<>();
    private static final Map<Object, Target> MENUS = new WeakHashMap<>();
    private static final Map<UUID, Menu> REPLIES = new HashMap<>();
    // Match NON's existing 600-tick native selection lifetime.
    private static final int TIMEOUT = 600;
    private NativeSelection() {}

    public static void clear() {
        MENUS.clear();
        REPLIES.clear();
        OPENING.remove();
    }

    public static void open(ServerPlayerEntity player, LivingEntity creature, Runnable action) {
        Target previous = OPENING.get();
        forget(player.getUuid());
        OPENING.set(new Target(player.getEntityWorld(), player.getUuid(), creature.getUuid(),
                player.getEntityWorld().getServer().getTicks()));
        try { action.run(); }
        finally {
            if (previous == null) OPENING.remove(); else OPENING.set(previous);
        }
    }

    public static void remember(Object session) {
        Target target = OPENING.get();
        if (target == null || !(session instanceof NativeSelectionSessionAccessor access)) return;
        if (!target.player().equals(access.noncreatureaffinity$requester())
                || access.noncreatureaffinity$actors().size() != 2
                || !access.noncreatureaffinity$actors().containsAll(java.util.List.of(target.player(), target.creature())))
            throw new IllegalStateException("Native selection actors do not match the addon request");
        MENUS.put(session, target);
        REPLIES.put(access.noncreatureaffinity$id(), new Menu(target, session));
    }

    public static void forget(UUID player) {
        REPLIES.values().removeIf(menu -> menu.target.player().equals(player));
        // Keep weak classification until the native session is collected: it must never become a debug menu.
    }

    public static void expire(int now) {
        REPLIES.values().removeIf(menu -> now - menu.target.openedTick() > TIMEOUT);
    }

    /** True means the packet belongs to this addon and must not enter the debug handler. */
    public static boolean reply(ServerPlayerEntity player, UUID id, Integer choice) {
        Menu menu = REPLIES.get(id);
        if (menu == null) return false;
        if (!menu.target.player().equals(player.getUuid())) return true;
        if (menu.consumed) return true;
        menu.consumed = true; // One use, including invalid/stale choices and cancellation.
        boolean valid = valid(menu.target, player);
        if (choice == null || !valid) {
            com.nonid.NonDebugAnimationSelectionSystem.cancel(player, id);
            return true;
        }
        var session = (NativeSelectionSessionAccessor) menu.session;
        boolean offered = session.noncreatureaffinity$choices().stream()
                .map(value -> (NativeSelectionChoiceAccessor) value)
                .anyMatch(value -> value.noncreatureaffinity$index() == choice && value.noncreatureaffinity$selectable());
        if (!offered) {
            com.nonid.NonDebugAnimationSelectionSystem.cancel(player, id);
            return true;
        }
        // Native choose rechecks its current session, owner, dimension, expiry, choice and engine eligibility.
        com.nonid.NonDebugAnimationSelectionSystem.choose(player, id, choice);
        return true;
    }

    public static boolean isAddonMenu(Object session) { return MENUS.containsKey(session); }

    public static boolean allow(Object session, ServerPlayerEntity player) {
        Target target = MENUS.get(session);
        if (target == null) return true; // Native debug: no addon distance/cooldown restriction.
        return valid(target, player);
    }

    private static boolean valid(Target target, ServerPlayerEntity player) {
        var entity = target.world().getEntity(target.creature());
        double range = NonCreatureAffinity.getConfig().interactRange;
        boolean valid = target.player().equals(player.getUuid()) && player.isAlive()
                && player.getEntityWorld().getServer().getTicks() - target.openedTick() <= TIMEOUT
                && !player.isSpectator() && player.getEntityWorld() == target.world()
                && entity instanceof LivingEntity living && living.isAlive() && !living.isBaby()
                && entity.squaredDistanceTo(player) <= range * range;
        if (!valid) com.nonid.affinity.service.GameplayFeedback.send(player,Text.translatable("message.noncreatureaffinity.target_gone"), false);
        return valid;
    }
}
