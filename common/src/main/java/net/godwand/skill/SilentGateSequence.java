package net.godwand.skill;

import net.minecraft.world.phys.Vec3;

import java.util.SplittableRandom;

/** Silent Gate clocks, layout and barrage schedule. Pure functions shared by server and client. */
public final class SilentGateSequence {
    public static final int BUILD_TICKS = 480, ACTIVATE_TICKS = 12, ENDING_TICKS = 290, RETURN_TICKS = 15;
    /** Build beats (ticks from click 1), script v7: the caster raises a hand to the wand (0..70); the great circle is written 70..142 (tiers from the centre out);
     *  the sky splits 142..158; the gate comes out and down 158..220 (slowly: it is heavy; the view shakes, the gate does not), settles to 230, shockwave and wings 236; back on the caster 258..340 (the "shh", the wand
     *  spun, the discs behind); doors stuck 340..365, pushed apart 365..412 (heavy stop 412); the small circles bloom only 420..470. */
    public static final int CIRCLE_START = 70, CIRCLE_FULL = 142, SLIT_START = 142, BEAT_GATE = 158;
    public static final int DESCENT_START = 158, DESCENT_END = 220, SETTLE_END = 230, SHOCK = 236, OPEN_START = 340, STUCK_END = 365,
            OPEN_FAST_END = 395, OPEN_END = 412, BLOOM_START = 420, BLOOM_END = 470;
    /** Ending (ticks after the barrage): doors close 0..70 (owner, 2026-11-15: slower), small circles shrink in place 55..120, the gate climbs slowly into the
     *  great circle 110..240 (owner, 2026-11-15, twice: "slower"), the great circle dissolves tier by tier 230..290 (each tier shrinks to its centre while it keeps turning). */
    public static final int END_CLOSE = 70, END_RETRACT_FROM = 55, END_RETRACT_TO = 120, END_RISE_FROM = 110, END_RISE_TO = 240, END_UNWRITE_FROM = 230;
    /** Everything in the sky is built at SCALE times its design size, to stand next to Fuga and Atomic. */
    public static final double SCALE = 4;
    /** Gate design units (multiply by SCALE for blocks): frame 64 tall (finials reach 74), 36 wide over the outer columns, 22x44 door opening. */
    public static final int GATE_HEIGHT = 64, GATE_WIDTH = 36, GATE_INNER_WIDTH = 22, GATE_INNER_HEIGHT = 44, GATE_DROP = 92,
            CIRCLE_RADIUS = 168, GROUND_RING = 8;
    /** The gate hangs far off and high in the sky (reference 4): blocks from the caster to it, and its foot above the anchor. */
    public static final double GATE_AHEAD = 64 * SCALE, GATE_BASE = 170;
    /** Great circle altitude above the anchor (80 design units over the gate's foot). It is light, not blocks: it may hang above the world's build limit. */
    public static final int CIRCLE_ALTITUDE = 490;
    public static final int MAX_TASKS_PER_WORLD = 4;
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

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

    /** Door centre of the gate once it has landed. */
    public static Vec3 doorCenter(Vec3 anchor, float yaw) { return gateFoot(anchor, yaw, 0).add(0, 37 * SCALE, 0); }

    /** The canopy's footprint round the caster in blocks: this far to either side, behind and ahead; and the heights of its five level tiers above the anchor. */
    public static final double FIELD_SIDE = 250, FIELD_BACK = 210, FIELD_AHEAD = 370;
    public static final double[] TIER_HEIGHTS = {105, 135, 165, 195, 225};
    /** Most circles a cast can have. */
    public static final int MAX_CIRCLES = 256;
    /** Ground positions {along, lateral} and tier of the circles in caster space: one low-discrepancy (R2) sequence over the whole footprint, so any number of circles
     *  covers it evenly and no two share a spot; points in the gate's airspace or on the caster's line of sight to it are skipped. Each circle takes the tier in which its
     *  nearest earlier neighbour is farthest, so circles on one level never overlap. */
    private static final double[][] LAYOUT = layout();
    private static double[][] layout() {
        double[][] out = new double[MAX_CIRCLES][];
        for (int n = 0, g = 0; n < MAX_CIRCLES; g++) {
            double lateral = (frac(.5 + .7548776662466927 * g) * 2 - 1) * FIELD_SIDE, along = -FIELD_BACK + (FIELD_BACK + FIELD_AHEAD) * frac(.5 + .5698402909980532 * g);
            if (!clearOfGate(along, lateral)) continue;
            int tier = 0; double best = -1;
            for (int t = 0; t < TIER_HEIGHTS.length; t++) {
                double near = Double.MAX_VALUE;
                for (int i = 0; i < n; i++) if (out[i][2] == t) near = Math.min(near, Math.hypot(out[i][0] - along, out[i][1] - lateral));
                if (near > best) { best = near; tier = t; }
            }
            out[n++] = new double[]{along, lateral, tier};
        }
        return out;
    }
    /** Canopy tier (0 lowest .. 3) of circle {@code index}. */
    public static int tier(int index) { return (int) LAYOUT[Math.floorMod(index, MAX_CIRCLES)][2]; }
    private static double frac(double x) { return x - Math.floor(x); }

    /** False where a circle would crowd the gate: in its own airspace, or on the narrow avenue straight from the caster to it. Both are narrow enough that every ground
     *  point under them still has a circle within 50 blocks, so nothing there is out of the beams' reach. */
    public static boolean clearOfGate(double along, double lateral) {
        if (Math.abs(along - GATE_AHEAD) < 34 && Math.abs(lateral) < 96) return false;
        return !(along > 40 && along < GATE_AHEAD && Math.abs(lateral) < 10 + .12 * along);
    }

    /** Sky circle {@code index}: where it is written and where it stays, through the wait, the barrage and the ending.
     *  {@code count} is not needed for the place (every prefix of the layout is even); it stays in the signature for the callers that iterate a cast's circles. */
    public static Vec3 skyCircle(Vec3 anchor, float yaw, long seed, int index, int count) {
        double[] d = dir(yaw, 0), p = LAYOUT[Math.floorMod(index, MAX_CIRCLES)];
        double y = anchor.y + TIER_HEIGHTS[(int) p[2]] + 8 * rng(seed, index).nextDouble() - 4;
        return new Vec3(anchor.x + d[0] * p[0] + d[1] * p[1], y, anchor.z + d[1] * p[0] - d[0] * p[1]);
    }

    /** Ticks a circle takes to write itself (and, in the ending, to unwrite), and ticks before that in which its seed of light falls out of the sky. */
    public static final int FORM_TICKS = 18, SEED_TICKS = 10;
    /** Tick at which sky circle {@code index} starts to write itself: a wave that leaves the gate and has crossed the whole canopy 22 ticks later. The first seed falls
     *  at BLOOM_START and with {@link #FORM_TICKS} the last circle is complete at BLOOM_END. */
    public static double circleBorn(int index) {
        double[] p = LAYOUT[Math.floorMod(index, MAX_CIRCLES)];
        return BLOOM_START + SEED_TICKS + 22 * Math.min(1, Math.hypot(p[0] - GATE_AHEAD, p[1]) / Math.hypot(FIELD_BACK + GATE_AHEAD, FIELD_SIDE));
    }

    /** Disc centres: half ring of radius 6 BEHIND the caster, 3 blocks above the anchor (reference image 10). */
    public static Vec3 groundCircle(Vec3 anchor, float yaw, int index) {
        double[] d = dir(yaw, Math.toRadians(90 + 180.0 * index / (GROUND_RING - 1)));
        return new Vec3(anchor.x + 6 * d[0], anchor.y + 3, anchor.z + 6 * d[1]);
    }

    public static boolean groundCircleGold(int index) { return index % 2 == 0; }

    /** Centre of the gate's doors once it has landed (the same point as {@link #doorCenter}). */
    public static Vec3 gateCenter(Vec3 anchor, float yaw) {
        double[] d = dir(yaw, 0);
        return new Vec3(anchor.x + GATE_AHEAD * d[0], anchor.y + GATE_BASE + 37 * SCALE, anchor.z + GATE_AHEAD * d[1]);
    }

    /** Altitude of the great circle above the anchor. */
    public static double circleAltitude(double anchorY) { return CIRCLE_ALTITUDE; }

    /** Foot of the gate: GATE_BASE above the anchor, GATE_DROP (scaled) higher until it has descended. */
    public static Vec3 gateFoot(Vec3 anchor, float yaw, double descent) {
        Vec3 c = gateCenter(anchor, yaw);
        return new Vec3(c.x, anchor.y + GATE_BASE + GATE_DROP * SCALE * descent, c.z);
    }

    /** 1 until DESCENT_START, eases to 0 by DESCENT_END, then a small bounce below zero that settles at SETTLE_END. The gate itself never shakes (owner,
     *  2026-11-15): its weight is felt in the view, in {@value #LURCHES} heavy beats ({@link #descentStrain}). */
    public static final int LURCHES = 7;
    public static double gateDescent(double t) {
        if (t <= DESCENT_START) return 1;
        if (t < DESCENT_END) return 1 - smooth((t - DESCENT_START) / (double) (DESCENT_END - DESCENT_START));
        if (t < SETTLE_END) return -0.06 * Math.sin(Math.PI * (t - DESCENT_END) / (SETTLE_END - DESCENT_END));
        return 0;
    }
    /** 0..1..0 over the descent: how hard the view shudders as the gate is dragged down through the sky, in beats (1 as a notch gives, dying before the next). */
    public static double descentStrain(double t) {
        if (t <= DESCENT_START || t >= DESCENT_END) return 0;
        double k = (t - DESCENT_START) / (double) (DESCENT_END - DESCENT_START), beat = k * LURCHES % 1;
        return Math.sin(Math.PI * k) * (.35 + .65 * Math.exp(-4 * beat));
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

    /** Ending: the crack in the sky, open from just before the gate climbs until it has gone in (owner, 2026-11-15: it closed before the gate was taken). */
    public static double endSlit(double e) {
        // At a rise of 1 the gate is back where it came out of the crack: wholly above it.
        return smooth((e - END_RISE_FROM + 18) / 16) * (1 - smooth((gateRise(e) - 1) / .18));
    }

    /** Ending: how far above its landed place the gate has climbed, in descent units (1 = back above the crack it came out of); starts slowly. It climbs
     *  only just past the crack, so the whole climb is seen (owner, 2026-11-15: it went up too fast). */
    public static double gateRise(double e) {
        return 1.2 * smooth((e - END_RISE_FROM) / (END_RISE_TO - END_RISE_FROM));
    }

    /** Each circle fires every 25..36 ticks with its own phase, so the sky never pulses in unison. */
    public static boolean fires(int circle, int barrageAge, int barrageTicks, long seed) {
        if (barrageAge < 0 || barrageAge >= barrageTicks) return false;
        SplittableRandom r = rng(seed, circle);
        int p = 26 + r.nextInt(12), offset = r.nextInt(p);
        return (barrageAge + offset) % p == 0;
    }

    /** Camera shot ticks are authored for a 200-tick barrage that starts at ACTIVATE_TICKS. */
    public static int shotTick(int baseTick, int barrageTicks) {
        return ACTIVATE_TICKS + Math.round((baseTick - ACTIVATE_TICKS) * barrageTicks / 200f);
    }

    /** The decisive kill beam: the circle nearest the featured target gathers light for HEAVY_CHARGE ticks, then one beam lands for HEAVY_DAMAGE_MULT times the damage of an
     *  ordinary beam (authored as command tick 100 of a 200-tick barrage; the kill shot starts 16 ticks earlier so the camera sees the charge). */
    public static final int HEAVY_CHARGE = 16, HEAVY_DAMAGE_MULT = 6, BEAM_TICKS = 16;
    public static int killBarrageAge(int barrageTicks) { return shotTick(KILL_TICK, barrageTicks) - ACTIVATE_TICKS; }
    /** Authored command tick of the kill beam: inside the low shot over the ground, so its impact frame falls on the land (owner, 2026-11-15). */
    public static final int KILL_TICK = 136;

    /** The featured target is spared by the ordinary beams until the kill beam has landed. */
    public static boolean withheld(int barrageAge, int killAge) { return barrageAge <= killAge; }

    /** Degrees a beam from {@code from} to {@code hit} leans from vertical. */
    public static double lean(Vec3 from, Vec3 hit) { return Math.toDegrees(Math.atan2(Math.hypot(from.x - hit.x, from.z - hit.z), Math.max(1e-6, from.y - hit.y))); }

    /** How much farther than the circle nearest a target a firing circle may be (blocks, horizontally). */
    public static final double MAX_DETOUR = 25;
    /** The circle that fires at {@code target} this tick: the nearest free one, unless that is more than {@link #MAX_DETOUR} farther off than the nearest of all (which is
     *  merely busy). Then nothing fires (-1) and the target waits a few ticks for a circle overhead, so beams rain down steeply and never cross the sky in long diagonals. */
    public static int beamCircle(Vec3 anchor, float yaw, long seed, int count, Vec3 target, java.util.function.IntPredicate free) {
        int c = nearestCircle(anchor, yaw, seed, count, target, free), nearest = nearestCircle(anchor, yaw, seed, count, target, i -> true);
        if (c < 0 || c == nearest) return c;
        Vec3 a = skyCircle(anchor, yaw, seed, c, count), b = skyCircle(anchor, yaw, seed, nearest, count);
        return Math.hypot(a.x - target.x, a.z - target.z) > Math.hypot(b.x - target.x, b.z - target.z) + MAX_DETOUR ? -1 : c;
    }

    /** The sky circle (home position) horizontally nearest to {@code target} among those {@code free} accepts, or -1. */
    public static int nearestCircle(Vec3 anchor, float yaw, long seed, int count, Vec3 target, java.util.function.IntPredicate free) {
        int best = -1; double bd = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            if (!free.test(i)) continue;
            Vec3 p = skyCircle(anchor, yaw, seed, i, count);
            double d = Math.hypot(p.x - target.x, p.z - target.z);
            if (p.y > target.y && d < bd) { bd = d; best = i; }
        }
        return best;
    }

    public static int endTick(int barrageTicks) { return ACTIVATE_TICKS + barrageTicks + ENDING_TICKS; }
}
