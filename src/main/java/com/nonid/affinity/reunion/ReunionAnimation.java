package com.nonid.affinity.reunion;

/** Shared, deterministic timing. No entity, inventory or save data is changed by this animation. */
public final class ReunionAnimation {
    public static final int RISE_END = 16;
    public static final int TURN_END = 60;
    public static final int RETURN_START = 64;
    public static final int END = 80;
    public static double smooth(double t) { t = Math.clamp(t, 0, 1); return t * t * (3 - 2 * t); }
    public static double lift(double tick) {
        return tick < RETURN_START ? smooth(tick / RISE_END) : 1 - smooth((tick - RETURN_START) / (END - RETURN_START));
    }
    public static double degrees(double tick) { return 360 * smooth((tick - RISE_END) / (TURN_END - RISE_END)); }
    public static boolean dim(double tick) { return tick >= TURN_END; }
    public static net.minecraft.util.math.Vec3d position(double tick, net.minecraft.util.math.Vec3d departure,
            net.minecraft.util.math.Vec3d destination, net.minecraft.util.math.Vec3d currentHand) {
        if (tick < RISE_END) return departure.lerp(destination, smooth(tick / RISE_END));
        if (tick < RETURN_START) return destination;
        return destination.lerp(currentHand, smooth((tick - RETURN_START) / (END - RETURN_START)));
    }
    private ReunionAnimation() { }
}
