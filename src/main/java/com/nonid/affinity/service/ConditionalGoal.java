package com.nonid.affinity.service;

import java.util.function.BooleanSupplier;
import net.minecraft.entity.ai.goal.Goal;

/** Keeps the original goal instance and state, but permits it only outside companion control. */
final class ConditionalGoal extends Goal {
    private final Goal delegate;
    private final BooleanSupplier permitted;
    ConditionalGoal(Goal delegate, BooleanSupplier permitted) {
        this.delegate = delegate;
        this.permitted = permitted;
        setControls(delegate.getControls().clone());
    }
    @Override public boolean canStart() { return permitted.getAsBoolean() && delegate.canStart(); }
    @Override public boolean shouldContinue() { return permitted.getAsBoolean() && delegate.shouldContinue(); }
    @Override public boolean canStop() { return !permitted.getAsBoolean() || delegate.canStop(); }
    @Override public void start() { delegate.start(); }
    @Override public void stop() { delegate.stop(); }
    @Override public boolean shouldRunEveryTick() { return delegate.shouldRunEveryTick(); }
    @Override public void tick() { if (permitted.getAsBoolean()) delegate.tick(); }
}
