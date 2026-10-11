package com.nonid.affinity.service;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalGoalTest {
    private static final class NativeGoal extends Goal {
        int starts, stops, ticks;
        NativeGoal() { setControls(EnumSet.of(Control.MOVE)); }
        @Override public boolean canStart() { return true; }
        @Override public boolean shouldContinue() { return true; }
        @Override public boolean shouldRunEveryTick() { return true; }
        @Override public void start() { starts++; }
        @Override public void stop() { stops++; }
        @Override public void tick() { ticks++; }
    }

    @Test void blockedNativeFollowLetsLowerPriorityWanderRun() {
        NativeGoal follow = new NativeGoal(), wander = new NativeGoal();
        GoalSelector selector = new GoalSelector();
        selector.add(6, new ConditionalGoal(follow, () -> false));
        selector.add(8, wander);
        selector.tick();
        assertEquals(0, follow.starts);
        assertEquals(1, wander.starts);
    }

    @Test void disablingRunningNativeGoalStopsItAndFreesMovement() {
        AtomicBoolean permitted = new AtomicBoolean(true);
        NativeGoal follow = new NativeGoal(), wander = new NativeGoal();
        GoalSelector selector = new GoalSelector();
        selector.add(6, new ConditionalGoal(follow, permitted::get));
        selector.add(8, wander);
        selector.tick();
        assertEquals(1, follow.starts);
        permitted.set(false);
        selector.tick();
        assertEquals(1, follow.stops);
        assertEquals(1, wander.starts);
    }

    @Test void unbindingRestoresTheOriginalGoalAndPriority() {
        AtomicBoolean permitted = new AtomicBoolean(false);
        NativeGoal follow = new NativeGoal(), wander = new NativeGoal();
        GoalSelector selector = new GoalSelector();
        selector.add(6, new ConditionalGoal(follow, permitted::get));
        selector.add(8, wander);
        selector.tick();
        permitted.set(true);
        selector.tick();
        assertEquals(1, follow.starts);
        assertEquals(1, wander.stops);
    }

    @Test void blockedGoalCannotTickBetweenSelectorEvaluations() {
        AtomicBoolean permitted = new AtomicBoolean(true);
        NativeGoal nativeGoal = new NativeGoal();
        ConditionalGoal goal = new ConditionalGoal(nativeGoal, permitted::get);
        goal.start();
        goal.tick();
        permitted.set(false);
        goal.tick();
        assertEquals(1, nativeGoal.ticks);
        assertFalse(goal.shouldContinue());
    }

    @Test void controlsAreCopiedAndTickCadencePreserved() {
        NativeGoal nativeGoal = new NativeGoal();
        ConditionalGoal goal = new ConditionalGoal(nativeGoal, () -> true);
        assertEquals(EnumSet.of(Goal.Control.MOVE), goal.getControls());
        assertTrue(goal.shouldRunEveryTick());
        nativeGoal.setControls(EnumSet.of(Goal.Control.LOOK));
        assertEquals(EnumSet.of(Goal.Control.MOVE), goal.getControls());
    }
}
