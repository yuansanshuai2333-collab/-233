package com.nonid.affinity.reunion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReunionAnimationTest {
    @Test void spinsAtSpawnEvenWhenThePlayerMovesThenReturnsToNewHandPosition() {
        var start = new net.minecraft.util.math.Vec3d(0, 1, 0);
        var target = new net.minecraft.util.math.Vec3d(0, 1, 3.5);
        var movedHand = new net.minecraft.util.math.Vec3d(2, 1, 0);
        assertEquals(start, ReunionAnimation.position(0, start, target, movedHand));
        assertEquals(start.lerp(target, .5), ReunionAnimation.position(8, start, target, movedHand));
        for (int tick = 16; tick <= 64; tick++) assertEquals(target, ReunionAnimation.position(tick, start, target, movedHand));
        assertEquals(target.lerp(movedHand, .5), ReunionAnimation.position(72, start, target, movedHand));
        assertEquals(movedHand, ReunionAnimation.position(80, start, target, movedHand));
    }
    @Test void leavesAndReturnsToHandWithoutOvershoot() {
        assertEquals(0, ReunionAnimation.lift(0)); assertEquals(0, ReunionAnimation.lift(80));
        assertEquals(1, ReunionAnimation.lift(16)); assertEquals(1, ReunionAnimation.lift(64));
        for (double t = -10; t < 100; t += .125) {
            assertTrue(ReunionAnimation.lift(t) >= 0 && ReunionAnimation.lift(t) <= 1);
        }
    }
    @Test void exactlyOneTurnAndOnlyThenDim() {
        double prior = 0;
        for (double t = 0; t <= 80; t += .125) {
            double angle = ReunionAnimation.degrees(t);
            assertTrue(angle >= prior && angle <= 360);
            if (ReunionAnimation.dim(t)) assertEquals(360, angle);
            prior = angle;
        }
        assertEquals(0, ReunionAnimation.degrees(16)); assertEquals(360, ReunionAnimation.degrees(60));
        assertFalse(ReunionAnimation.dim(59.99)); assertTrue(ReunionAnimation.dim(60));
    }
    @Test void phaseBoundariesAreContinuous() {
        for (double t : new double[]{16, 60, 64, 80}) {
            assertEquals(ReunionAnimation.lift(t - .0001), ReunionAnimation.lift(t + .0001), .0001);
            assertEquals(ReunionAnimation.degrees(t - .0001), ReunionAnimation.degrees(t + .0001), .0001);
        }
    }
}
