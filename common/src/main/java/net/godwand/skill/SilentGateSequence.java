package net.godwand.skill;

import net.minecraft.world.phys.Vec3;

import java.util.SplittableRandom;

/** Silent Gate clocks, layout and barrage schedule. Pure functions shared by server and client. */
public final class SilentGateSequence {
    public static final int BUILD_TICKS = 360, ACTIVATE_TICKS = 12, ENDING_TICKS = 60, RETURN_TICKS = 15;
    public static final int BEAT_EYES = 0, BEAT_RAISE = 20, BEAT_SHUSH = 45, BEAT_CIRCLE = 80, BEAT_GATE = 140;
    /** Gate timeline (ticks from click 1): slit 130, descent 150..188 then a settle to 203, shockwave 204, stuck doors 215..228,
     *  slow separation 228..270, heavy stop 270..285, then the circles bloom while the camera flies out (S9, 290..330). */
    public static final int DESCENT_START = 150, DESCENT_END = 188, SETTLE_END = 203, SHOCK = 204, OPEN_START = 215, STUCK_END = 228,
            OPEN_FAST_END = 270, OPEN_END = 285, FLIGHT_START = 290, FLIGHT_END = 330;
    /** Extra circles ringed around the gate body, on top of the configured sky circles. */
    public static final int GATE_RING = 24;
    /** Everything in the sky is built at SCALE times its design size, to stand next to Fuga and Atomic. */
    public static final double SCALE = 3;
    /** Gate design units (multiply by SCALE for blocks): frame 64 tall (finials reach 74), 36 wide over the outer columns, 22x44 door opening. */
    public static final int GATE_HEIGHT = 64, GATE_WIDTH = 36, GATE_INNER_WIDTH = 22, GATE_INNER_HEIGHT = 44, GATE_DROP = 40,
            CIRCLE_RADIUS = 168, GROUND_RING = 8;
    /** Great circle altitude above the anchor: 300, lowered near the world's build limit so it still fits. */
    public static final int CIRCLE_ALTITUDE = 300, WORLD_TOP = 320;
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

    /** Circles that are not the gate ring: indices come in fours: near the gate (0, 1), wide around the caster (2), lined along the S9 flight lane (3). */
    public static int skyTotal(int count) { return count + GATE_RING; }

    /** Door centre of the gate once it has landed. */
    public static Vec3 doorCenter(Vec3 anchor, float yaw) { return gateFoot(anchor, yaw, 0).add(0, 37 * SCALE, 0); }

    /** Camera lane of shot S9: leaves the door gap and sweeps back over the caster (u 0..1, 180 blocks, weaving a little, descending 62). */
    public static Vec3 flight(Vec3 anchor, float yaw, double u) {
        double[] d = dir(yaw, 0);
        double along = 4 + 180 * u, lateral = 14 * Math.sin(2 * Math.PI * .75 * u), y = -62 * u + 7 * Math.sin(2 * Math.PI * u);
        Vec3 c = doorCenter(anchor, yaw);
        return new Vec3(c.x - d[0] * along + d[1] * lateral, c.y + y, c.z - d[1] * along - d[0] * lateral);
    }

    /** Sky circle {@code index} of {@code count}; indices from {@code count} on are the {@link #GATE_RING} circles around the gate body. */
    public static Vec3 skyCircle(Vec3 anchor, float yaw, long seed, int index, int count) {
        Vec3 c = gateCenter(anchor, yaw);
        SplittableRandom r = rng(seed, index);
        if (index >= count) {
            double a = (index - count) * 2 * Math.PI / GATE_RING;
            return new Vec3(c.x + 78 * Math.cos(a), anchor.y + gateBase(anchor.y) + 40 + 112 * r.nextDouble(), c.z + 78 * Math.sin(a));
        }
        int group = index / 4, kind = index % 4;
        double h = anchor.y + 60 + 200 * r.nextDouble(), a = group * GOLDEN_ANGLE + kind;
        if (kind == 2) {
            double rad = 40 + 180 * Math.sqrt((group + .5) / Math.max(1, count / 4.0));
            return new Vec3(anchor.x + rad * Math.cos(a), h, anchor.z + rad * Math.sin(a));
        }
        if (kind == 3) {
            double u = laneU(index, count), side = a, dist = 3 + 5 * r.nextDouble();
            Vec3 p = flight(anchor, yaw, u), right = new Vec3(Math.cos(Math.toRadians(yaw)), 0, Math.sin(Math.toRadians(yaw)));
            return p.add(right.scale(Math.cos(side) * dist)).add(0, Math.sin(side) * dist, 0);
        }
        double rad = 20 + 100 * Math.sqrt((index / 4 * 2 + kind + .5) / Math.max(1, count / 2.0));
        return new Vec3(c.x + rad * Math.cos(a), h, c.z + rad * Math.sin(a));
    }

    /** Where along the flight lane a lane circle (index % 4 == 3) sits, 0..1 in index order. */
    public static double laneU(int index, int count) { return (index / 4 + .5) / Math.max(1, count / 4); }

    /** Flight progress (0..1) at tick {@code t}: starts from rest and accelerates until the cut (u = k squared). */
    public static double flightU(double t) { double k = Math.max(0, Math.min(1, (t - FLIGHT_START) / (double) (FLIGHT_END - FLIGHT_START))); return k * k; }

    /** Tick at which a sky circle starts to bloom: lane circles a few ticks before the camera passes, ring circles when the doors stop, the rest spread over S9. */
    public static double circleBorn(int index, int count, long seed) {
        if (index >= count) return OPEN_END + 1 + (index - count) * .8;
        if (index % 4 == 3) return FLIGHT_START + (FLIGHT_END - FLIGHT_START) * Math.sqrt(laneU(index, count)) - 4;
        return FLIGHT_START + 2 + 44 * rng(seed ^ 0x5DEECE66DL, index).nextDouble();
    }

    /** Disc centres: half ring of radius 6 BEHIND the caster, 3 blocks above the anchor (reference image 10). */
    public static Vec3 groundCircle(Vec3 anchor, float yaw, int index) {
        double[] d = dir(yaw, Math.toRadians(90 + 180.0 * index / (GROUND_RING - 1)));
        return new Vec3(anchor.x + 6 * d[0], anchor.y + 3, anchor.z + 6 * d[1]);
    }

    public static boolean groundCircleGold(int index) { return index % 2 == 0; }

    public static Vec3 gateCenter(Vec3 anchor, float yaw) {
        double[] d = dir(yaw, 0);
        return new Vec3(anchor.x + 48 * SCALE * d[0], anchor.y + 45 * SCALE, anchor.z + 48 * SCALE * d[1]);
    }

    /** Altitude of the great circle above an anchor at height {@code anchorY}: 300, or lower so its top stays under the build limit. */
    public static double circleAltitude(double anchorY) { return Math.max(130, Math.min(CIRCLE_ALTITUDE, WORLD_TOP - 16 - anchorY)); }

    /** Height of the gate's foot above the anchor: the gate stands 240 blocks under the circle (finials 18 clear of it). */
    public static double gateBase(double anchorY) { return Math.max(0, circleAltitude(anchorY) - 240); }

    /** Foot of the gate: gateBase above the anchor, GATE_DROP (scaled) higher until it has descended. */
    public static Vec3 gateFoot(Vec3 anchor, float yaw, double descent) {
        Vec3 c = gateCenter(anchor, yaw);
        return new Vec3(c.x, anchor.y + gateBase(anchor.y) + GATE_DROP * SCALE * descent, c.z);
    }

    /** 1 until 150, eases to 0 by 188, then a small bounce below zero that settles at 203. */
    public static double gateDescent(double t) {
        if (t <= DESCENT_START) return 1;
        if (t < DESCENT_END) return 1 - smooth((t - DESCENT_START) / (double) (DESCENT_END - DESCENT_START));
        if (t < SETTLE_END) return -0.06 * Math.sin(Math.PI * (t - DESCENT_END) / (SETTLE_END - DESCENT_END));
        return 0;
    }

    /**
     * Heavy doors: stuck (a hair of 2% by 228, the speed eases to 0 first), then the speed grows linearly (pushing a huge mass)
     * until 270 where 81% is open, then the speed dies off quadratically so they meet the stops at 285 with a long tail.
     */
    public static double gateOpen(double t) {
        if (t <= OPEN_START) return 0;
        if (t < STUCK_END) return .02 * smooth((t - OPEN_START) / (STUCK_END - OPEN_START));
        if (t >= OPEN_END) return 1;
        double top = .98 / 26, a = (t - STUCK_END) / (OPEN_FAST_END - STUCK_END);
        if (t < OPEN_FAST_END) return .02 + top * 21 * a * a;
        double u = (t - OPEN_FAST_END) / (OPEN_END - OPEN_FAST_END);
        return .02 + top * 21 + top * (OPEN_END - OPEN_FAST_END) * (1 - Math.pow(1 - u, 3)) / 3;
    }

    /** Rattle of the leaves in blocks: a tremble while stuck, a steady shudder while they push apart, nothing once they stop. */
    public static double gateShake(double t) {
        if (t <= OPEN_START || t >= OPEN_END) return 0;
        if (t < STUCK_END) return .25 * smooth((t - OPEN_START) / 3.0);
        if (t < OPEN_FAST_END) return .12 + .1 * smooth((t - STUCK_END) / 42.0);
        return .22 * (1 - smooth((t - OPEN_FAST_END) / 15.0));
    }

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

    /** The featured target is withheld from the beams until this barrage age, then {@link #VOLLEY_BEAMS} beams land within {@link #VOLLEY_TICKS} ticks
     *  (authored as command tick 75 of a 200-tick barrage). */
    public static final int VOLLEY_TICKS = 3, VOLLEY_BEAMS = 6;
    public static int killBarrageAge(int barrageTicks) { return shotTick(75, barrageTicks) - ACTIVATE_TICKS; }

    /** The featured target is spared by the ordinary beams until the volley has landed. */
    public static boolean withheld(int barrageAge, int killAge) { return barrageAge < killAge + VOLLEY_TICKS; }

    /** Sky circles that fire at the featured target on this barrage age (two per tick, spread over the spiral); empty outside the volley. */
    public static int[] volley(int barrageAge, int killAge, long seed, int skyCircles) {
        int o = barrageAge - killAge;
        if (o < 0 || o >= VOLLEY_TICKS) return new int[0];
        int slot = skyCircles / VOLLEY_BEAMS, shift = (int) Math.floorMod(seed, (long) Math.max(1, slot));
        int per = VOLLEY_BEAMS / VOLLEY_TICKS;
        int[] out = new int[per];
        for (int j = 0; j < per; j++) out[j] = Math.min(skyCircles - 1, (o * per + j) * slot + shift);
        return out;
    }

    public static int endTick(int barrageTicks) { return ACTIVATE_TICKS + barrageTicks + ENDING_TICKS; }
}
