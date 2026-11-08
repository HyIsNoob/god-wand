package net.godwand.skill;

import net.minecraft.world.phys.Vec3;

import java.util.SplittableRandom;

/** Silent Gate clocks, layout and barrage schedule. Pure functions shared by server and client. */
public final class SilentGateSequence {
    public static final int BUILD_TICKS = 300, ACTIVATE_TICKS = 12, ENDING_TICKS = 60, RETURN_TICKS = 15;
    public static final int BEAT_EYES = 0, BEAT_RAISE = 20, BEAT_SHUSH = 45, BEAT_CIRCLE = 80, BEAT_GATE = 140,
            BEAT_OPEN = 200, BEAT_ARRAY = 250;
    public static final int GATE_HEIGHT = 40, GATE_WIDTH = 24, CIRCLE_RADIUS = 56, CIRCLE_ALTITUDE = 100, GROUND_RING = 8;
    public static final int MAX_TASKS_PER_WORLD = 4;
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    /** Ordinal is the network code. */
    public enum Phase { BUILD, WAIT, BARRAGE, END }

    private SilentGateSequence() {}

    private static double smooth(double x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

    private static SplittableRandom rng(long seed, int i) { return new SplittableRandom(seed ^ i * GOLDEN); }

    /** Minecraft yaw 0 faces +Z; returns (x, z) of the forward unit vector rotated by extra radians. */
    private static double[] dir(float yaw, double extra) {
        double a = Math.toRadians(yaw) + extra;
        return new double[] {-Math.sin(a), Math.cos(a)};
    }

    /** Fermat spiral (golden angle) around the gate centre; y is anchor.y + 30..70. */
    public static Vec3 skyCircle(Vec3 anchor, float yaw, long seed, int index, int count) {
        Vec3 c = gateCenter(anchor, yaw);
        double r = 8 + 54 * Math.sqrt((index + 0.5) / count), a = index * GOLDEN_ANGLE;
        return new Vec3(c.x + r * Math.cos(a), anchor.y + 30 + 40 * rng(seed, index).nextDouble(), c.z + r * Math.sin(a));
    }

    /** Disc centres: half ring of radius 6 BEHIND the caster, 3 blocks above the anchor (reference image 10). */
    public static Vec3 groundCircle(Vec3 anchor, float yaw, int index) {
        double[] d = dir(yaw, Math.toRadians(90 + 180.0 * index / (GROUND_RING - 1)));
        return new Vec3(anchor.x + 6 * d[0], anchor.y + 3, anchor.z + 6 * d[1]);
    }

    public static boolean groundCircleGold(int index) { return index % 2 == 0; }

    public static Vec3 gateCenter(Vec3 anchor, float yaw) {
        double[] d = dir(yaw, 0);
        return new Vec3(anchor.x + 48 * d[0], anchor.y + 45, anchor.z + 48 * d[1]);
    }

    /** 1 until 140, eases to 0 by 185, then a small bounce below zero that settles at 200. */
    public static double gateDescent(double t) {
        if (t <= BEAT_GATE) return 1;
        if (t < 185) return 1 - smooth((t - BEAT_GATE) / 45);
        if (t < BEAT_OPEN) return -0.06 * Math.sin(Math.PI * (t - 185) / 15);
        return 0;
    }

    public static double gateOpen(double t) { return smooth((t - 206) / 44); }

    /** Each circle fires every 25..36 ticks with its own phase, so the sky never pulses in unison. */
    public static boolean fires(int circle, int barrageAge, int barrageTicks, long seed) {
        if (barrageAge < 0 || barrageAge >= barrageTicks) return false;
        SplittableRandom r = rng(seed, circle);
        int p = 25 + r.nextInt(12), offset = r.nextInt(p);
        return (barrageAge + offset) % p == 0;
    }

    /** Camera shot ticks are authored for a 200-tick barrage that starts at ACTIVATE_TICKS. */
    public static int shotTick(int baseTick, int barrageTicks) {
        return ACTIVATE_TICKS + Math.round((baseTick - ACTIVATE_TICKS) * barrageTicks / 200f);
    }

    public static int endTick(int barrageTicks) { return ACTIVATE_TICKS + barrageTicks + ENDING_TICKS; }
}
