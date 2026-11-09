package net.godwand.skill;

import net.minecraft.world.phys.Vec3;

import java.util.SplittableRandom;

/** Silent Gate clocks, layout and barrage schedule. Pure functions shared by server and client. */
public final class SilentGateSequence {
    public static final int BUILD_TICKS = 480, ACTIVATE_TICKS = 12, ENDING_TICKS = 160, RETURN_TICKS = 15;
    public static final int BEAT_EYES = 0, BEAT_RAISE = 20, BEAT_SHUSH = 45, BEAT_CIRCLE = 80, BEAT_GATE = 140;
    /** Gate timeline (ticks from click 1, BUILD = 480): circle written 120..168, slit 166, descent 210..248 then a settle to 266,
     *  shockwave 274, stuck doors 292..316, a slow push apart 316..366, a long heavy stop 366..392, the circles bloom while the camera
     *  flies out (S9, 380..430). */
    public static final int DESCENT_START = 210, DESCENT_END = 248, SETTLE_END = 266, SHOCK = 274, OPEN_START = 292, STUCK_END = 316,
            OPEN_FAST_END = 366, OPEN_END = 392, FLIGHT_START = 405, FLIGHT_END = 480, OVERVIEW_START = 380;
    /** Ending (ticks after the barrage): doors close 0..40, small circles retract 20..85, the gate climbs into the great circle 75..125,
     *  the great circle unwrites tier by tier 108..160. */
    public static final int END_CLOSE = 40, END_RETRACT_FROM = 20, END_RETRACT_TO = 85, END_RISE_FROM = 75, END_RISE_TO = 125, END_UNWRITE_FROM = 108;
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

    /** Circles that are not the gate ring: indices come in fours: near the gate (0, 1), wide around the caster (2), over the flight lane (3). */
    public static int skyTotal(int count) { return count + GATE_RING; }

    /** Door centre of the gate once it has landed. */
    public static Vec3 doorCenter(Vec3 anchor, float yaw) { return gateFoot(anchor, yaw, 0).add(0, 37 * SCALE, 0); }

    /** Camera lane of the bloom sequence: low over the ground, one direction (toward the caster), from 140 blocks ahead of the caster to the waiting frame
     *  (6.2 ahead, 3.2 to its right, 1.1 up); u 0..1. The lane circles hang 35..75 blocks over it, so the camera always looks up at them. */
    public static Vec3 flight(Vec3 anchor, float yaw, double u) {
        double[] d = dir(yaw, 0);
        double along = 140 - 133.8 * u, lateral = 3.2 * u * u * u, y = 6 - 4.9 * u * u;
        // Rig-right of a caster is -(cos yaw, sin yaw).
        return new Vec3(anchor.x + d[0] * along - Math.cos(Math.toRadians(yaw)) * lateral, anchor.y + y, anchor.z + d[1] * along - Math.sin(Math.toRadians(yaw)) * lateral);
    }

    /** Sky circle {@code index} of {@code count}; indices from {@code count} on are the {@link #GATE_RING} circles at the sides of the gate body.
     *  Index % 4: 0 and 1 near the gate (kept out of its silhouette), 2 wide round the caster, 3 over the camera's flight lane. */
    public static Vec3 skyCircle(Vec3 anchor, float yaw, long seed, int index, int count) {
        Vec3 c = gateCenter(anchor, yaw);
        SplittableRandom r = rng(seed, index);
        double[] d = dir(yaw, 0);
        if (index >= count) {
            // Two arcs, left and right of the gate (50..130 degrees off the view axis), never in front of or behind its face.
            int k = index - count, half = GATE_RING / 2;
            double a = Math.toRadians(50 + 80.0 * (k % half) / (half - 1)) * (k < half ? 1 : -1);
            double along = 90 * Math.cos(a), lateral = 90 * Math.sin(a);
            return new Vec3(c.x + d[0] * along + d[1] * lateral, anchor.y + gateBase(anchor.y) + 40 + 112 * r.nextDouble(), c.z + d[1] * along - d[0] * lateral);
        }
        int group = index / 4, kind = index % 4;
        if (kind == 3) {
            // Overhead the lane: 35..75 above the camera, up to 14 to either side.
            double u = laneU(index, count);
            Vec3 p = flight(anchor, yaw, u), right = new Vec3(Math.cos(Math.toRadians(yaw)), 0, Math.sin(Math.toRadians(yaw)));
            return p.add(right.scale((r.nextDouble() * 2 - 1) * 14)).add(0, 35 + 40 * r.nextDouble(), 0);
        }
        // The canopy over the battlefield: three height tiers (kind 0, 1, 2), each an even low-discrepancy spread (R2 sequence) over the ground in front of the
        // caster (10..190 ahead, 130 to either side: the target field and the gate); one group in three fills the ground behind (-110..10) so the sky above and
        // behind the caster is packed as well. Every circle stays where it blooms, through the wait and the barrage.
        double g = group + 13 * kind, fx = frac(.5 + .7548776662466927 * g), fy = frac(.5 + .5698402909980532 * g);
        double lateral = (fx * 2 - 1) * 130, along = group % 3 == 2 ? -110 + 120 * fy : 10 + 180 * fy;
        double y = anchor.y + (kind == 0 ? 55 + 35 * r.nextDouble() : kind == 1 ? 115 + 45 * r.nextDouble() : 175 + 40 * r.nextDouble());
        return new Vec3(anchor.x + d[0] * along + d[1] * lateral, y, anchor.z + d[1] * along - d[0] * lateral);
    }
    private static double frac(double x) { return x - Math.floor(x); }

    /** Where along the flight lane a lane circle (odd index) sits, 0..1 in index order. */
    public static double laneU(int index, int count) { return (index / 4 + .5) / Math.max(1, count / 4); }

    /** Flight progress (0..1) at tick {@code t}: from rest, quick gain, a long tail that comes to rest on the waiting frame (u = 1 - (1-k)^3 (1+3k)). */
    public static double flightU(double t) {
        double k = Math.max(0, Math.min(1, (t - FLIGHT_START) / (double) (FLIGHT_END - FLIGHT_START)));
        return 1 - Math.pow(1 - k, 3) * (1 + 3 * k);
    }
    /** Tick at which the camera is at lane position {@code u} (inverse of {@link #flightU}). */
    public static double flightTime(double u) {
        double lo = 0, hi = 1;
        for (int i = 0; i < 40; i++) { double mid = (lo + hi) / 2; if (flightU(FLIGHT_START + mid * (FLIGHT_END - FLIGHT_START)) < u) lo = mid; else hi = mid; }
        return FLIGHT_START + (lo + hi) / 2 * (FLIGHT_END - FLIGHT_START);
    }

    /** Tick at which a sky circle starts to bloom: lane circles a few ticks before the camera passes, ring circles when the doors stop, the rest spread over S9. */
    public static double circleBorn(int index, int count, long seed) {
        if (index >= count) return OPEN_END + 1 + (index - count) * .8;
        if (index % 4 == 3) return flightTime(laneU(index, count)) - 14;
        return OVERVIEW_START + 4 + 36 * rng(seed ^ 0x5DEECE66DL, index).nextDouble();
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
     * Heavy doors: stuck (2% by STUCK_END, the speed eases to 0 first), then the speed grows linearly (pushing a huge mass) until
     * OPEN_FAST_END, then the speed dies off quadratically so they meet the stops at OPEN_END with a long tail.
     */
    public static double gateOpen(double t) {
        if (t <= OPEN_START) return 0;
        if (t < STUCK_END) return .02 * smooth((t - OPEN_START) / (STUCK_END - OPEN_START));
        if (t >= OPEN_END) return 1;
        double l1 = OPEN_FAST_END - STUCK_END, l2 = OPEN_END - OPEN_FAST_END, v1 = .98 / (l1 / 2 + l2 / 3);
        if (t < OPEN_FAST_END) { double a = (t - STUCK_END) / l1; return .02 + v1 * l1 / 2 * a * a; }
        double u = (t - OPEN_FAST_END) / l2;
        return .02 + v1 * l1 / 2 + v1 * l2 * (1 - Math.pow(1 - u, 3)) / 3;
    }

    /** Rattle of the leaves in blocks: a tremble while stuck, a steady shudder while they push apart, nothing once they stop. */
    public static double gateShake(double t) {
        if (t <= OPEN_START || t >= OPEN_END) return 0;
        if (t < STUCK_END) return .25 * smooth((t - OPEN_START) / 3.0);
        if (t < OPEN_FAST_END) return .12 + .1 * smooth((t - STUCK_END) / (OPEN_FAST_END - STUCK_END));
        return .22 * (1 - smooth((t - OPEN_FAST_END) / (OPEN_END - OPEN_FAST_END)));
    }

    /** Ending, {@code e} ticks after the barrage: the doors close, the reverse of the opening (slow start, a creep into the stops). */
    public static double closingOpen(double e) {
        return gateOpen(OPEN_END - Math.max(0, Math.min(1, e / END_CLOSE)) * (OPEN_END - OPEN_START));
    }

    /** Ending: how far above its landed place the gate has climbed, in descent units (2 = at the great circle's plane); starts slowly. */
    public static double gateRise(double e) {
        return 2.2 * smooth((e - END_RISE_FROM) / (END_RISE_TO - END_RISE_FROM));
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

    /** The decisive kill beam: its circle glides over the featured target for HEAVY_GLIDE ticks, then one beam lands for HEAVY_DAMAGE_MULT times the damage of an
     *  ordinary beam (authored as command tick 100 of a 200-tick barrage; the kill shot starts 16 ticks earlier so the camera sees the circle line up). */
    public static final int HEAVY_GLIDE = 16, HEAVY_DAMAGE_MULT = 6, BEAM_TICKS = 8, GLIDE_BACK = 12;
    public static int killBarrageAge(int barrageTicks) { return shotTick(100, barrageTicks) - ACTIVATE_TICKS; }

    /** The featured target is spared by the ordinary beams until the kill beam has landed. */
    public static boolean withheld(int barrageAge, int killAge) { return barrageAge <= killAge; }

    /** No beam leans more than this from vertical. */
    public static final double MAX_TILT_DEGREES = 8;
    /** Degrees a beam from {@code from} to {@code hit} leans from vertical. */
    public static double tilt(Vec3 from, Vec3 hit) { return Math.toDegrees(Math.atan2(Math.hypot(from.x - hit.x, from.z - hit.z), Math.max(1e-6, from.y - hit.y))); }
    /** Longest a circle may slide sideways to line a beam up with its target (blocks); the canopy is dense enough that most beams need less. */
    public static final double MAX_SLIDE = 15, HEAVY_MAX_SLIDE = 20;
    /**
     * The short local slide of a circle at {@code home} so its beam onto {@code hit} leans no more than 8 degrees: horizontally toward the target, only as far as needed, at most
     * {@link #MAX_SLIDE} (the kill beam slides until it is directly above, at most {@link #HEAVY_MAX_SLIDE}). Zero when no slide helps (the beam already stands, or the target is outside the canopy).
     */
    public static Vec3 slide(Vec3 home, Vec3 hit, boolean heavy) {
        double h = Math.hypot(hit.x - home.x, hit.z - home.z), reach = heavy ? 0 : Math.max(0, home.y - hit.y) * Math.tan(Math.toRadians(MAX_TILT_DEGREES));
        double need = h - reach, cap = heavy ? HEAVY_MAX_SLIDE : MAX_SLIDE;
        if (need <= 1e-9 || !heavy && need > cap || h < 1e-9) return Vec3.ZERO;
        double d = Math.min(need, cap);
        return new Vec3((hit.x - home.x) / h * d, 0, (hit.z - home.z) / h * d);
    }
    /** Ticks of the slide before an ordinary beam fires: none without a slide, else 6..11 (a longer slide takes longer). */
    public static int glideTicks(Vec3 home, Vec3 hit) {
        Vec3 s = slide(home, hit, false);
        return s == Vec3.ZERO || s.lengthSqr() == 0 ? 0 : 6 + (int) (s.length() / 3);
    }
    /** Glide offset 0..1 of a circle {@code since} ticks after the shot was announced: eased over {@code glide} ticks, held while the beam lives, eased back. */
    public static double glideK(double since, int glide) {
        if (glide <= 0 || since <= 0) return 0;
        if (since < glide) return smooth(since / glide);
        if (since < glide + BEAM_TICKS) return 1;
        return 1 - smooth((since - glide - BEAM_TICKS) / GLIDE_BACK);
    }
    /** The sky circle (home position) horizontally nearest to {@code target} among those {@code free} accepts, or -1. */
    public static int nearestCircle(Vec3 anchor, float yaw, long seed, int count, Vec3 target, java.util.function.IntPredicate free) {
        int best = -1; double bd = Double.MAX_VALUE;
        for (int i = 0; i < skyTotal(count); i++) {
            if (!free.test(i)) continue;
            Vec3 p = skyCircle(anchor, yaw, seed, i, count);
            double d = Math.hypot(p.x - target.x, p.z - target.z);
            if (p.y > target.y && d < bd) { bd = d; best = i; }
        }
        return best;
    }

    public static int endTick(int barrageTicks) { return ACTIVATE_TICKS + barrageTicks + ENDING_TICKS; }
}
