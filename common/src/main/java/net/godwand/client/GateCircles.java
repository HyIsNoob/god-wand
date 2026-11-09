package net.godwand.client;

import net.godwand.client.GateDraw.Ctx;
import net.godwand.skill.SilentGateSequence;
import net.minecraft.world.phys.Vec3;

import static net.godwand.client.GateDraw.clamp01;
import static net.godwand.client.GateDraw.smooth;

/** The great circle (script v3): five tiers (seal, then four rings) that each grow from the centre outwards, inner tiers first, while they turn, every tier its own
 *  direction and speed, never still from the moment it appears until it has dissolved; at the end each tier shrinks back to the centre, outer tiers first. */
final class GateCircles {
    private GateCircles() {}
    private static final double TAU = Math.PI * 2, S = SilentGateSequence.SCALE;
    static final int SEGMENTS = 96;
    static final int WHITE_BLUE = 0xD8F4FF, GLOW_BLUE = 0x7FE8FF, TEAL = 0x9FF0F0, HOT = 0xEAFBFF;

    /** One annulus of the circle. {@code region} null means the seal disc. */
    private record Tier(GateAtlas.Region region, GateAtlas.Region second, double r0, double r1, double lift, int repeats,
                        int start, int grow, double speed, int color) {}
    /** Inner to outer; speed in degrees per tick: signs alternate and every magnitude differs. The last tier is fully grown at 232 (CIRCLE_FULL). */
    private static final Tier[] TIERS = {
            new Tier(GateAtlas.RUNES, null, 12, 16, .8, 3, 168, 30, -.20, WHITE_BLUE),
            new Tier(GateAtlas.SWIRL, null, 20, 30, 1.8, 1, 178, 32, .12, WHITE_BLUE),
            new Tier(GateAtlas.RUNES, null, 34, 42, 2.6, 4, 190, 34, -.25, TEAL),
            new Tier(GateAtlas.DASH, GateAtlas.RIM, 50, 56, 3.2, 12, 200, 32, .40, WHITE_BLUE)};
    static final int SEAL_START = SilentGateSequence.CIRCLE_START, SEAL_GROW = 30;
    static final double SEAL_RADIUS = 9 * S, SEAL_SPEED = .35;
    /** Tiers including the seal (index 0 = seal, 1..4 = the rings from the inside out). */
    static final int TIER_COUNT = TIERS.length + 1;

    /** Turning speed of tier {@code i} (0 seal, 1..4 rings) in degrees per tick. */
    static double speed(int i) { return i == 0 ? SEAL_SPEED : TIERS[i - 1].speed(); }
    /** Rotation of tier {@code i} in radians at cast age {@code age}: unbroken from the first tick of the circle to the last. */
    static double angle(int i, double age) { return Math.toRadians(speed(i) * age); }
    private static int start(int i) { return i == 0 ? SEAL_START : TIERS[i - 1].start(); }
    private static int grow(int i) { return i == 0 ? SEAL_GROW : TIERS[i - 1].grow(); }
    /** 0..1 size of tier {@code i}: grows from the centre outwards (inner tiers start first), and in the ending ({@code e} ticks after the barrage, -1 before) shrinks back to the centre, outer tiers first. */
    static double scale(int i, double age, double e) { return Math.min(smooth((age - start(i)) / grow(i)), 1 - unwrite(e, TIER_COUNT - 1 - i)); }

    /** Altitude the circle hangs at relative to the anchor; shared by every layer. */
    static Vec3 center(Vec3 anchor, Vec3 gate) { return new Vec3(gate.x, anchor.y + SilentGateSequence.circleAltitude(anchor.y), gate.z); }

    /** {@code e}: ticks after the barrage (-1 before): the tiers dissolve from the outside in, the seal last, and the light fades with them. */
    static void great(Ctx c, Vec3 anchor, Vec3 gate, double age, double e) {
        if (age < SEAL_START) return;
        double endK = e < 0 ? 0 : smooth((e - 100) / 60);
        Vec3 ctr = center(anchor, gate);
        double breath = (1 + .1 * Math.sin(age * .09));
        seal(c, ctr, age, breath, scale(0, age, e));
        for (int i = 0; i < TIERS.length; i++) tier(c, ctr, TIERS[i], i, age, breath, scale(i + 1, age, e));
        if (c.glow()) {
            // Wide faint disc of light above the circle, and the shafts it throws down once the gate arrives.
            fan(c, ctr.add(0, 6 * S, 0), 70 * S * scale(TIER_COUNT - 1, age, e), GLOW_BLUE, .15 * smooth((age - SEAL_START) / 40) * breath * (1 - endK));
            shafts(c, ctr, age, 1 - endK);
        }
        if (c.body()) underlay(c, ctr, 58 * S * scale(TIER_COUNT - 1, age, e), smooth((age - SEAL_START) / 60) * (1 - endK));
    }

    /** 0..1: how far the k-th ring from the outside (0) to the seal (4) has shrunk {@code e} ticks into the ending. */
    private static double unwrite(double e, int k) { return e < 0 ? 0 : smooth((e - SilentGateSequence.END_UNWRITE_FROM - 6 * k) / 26); }

    private static void seal(Ctx c, Vec3 ctr, double age, double breath, double scale) {
        if (scale <= 0) return;
        double spin = angle(0, age), k = clamp01(scale * 3) * breath;
        int n = 6;
        double half = SEAL_RADIUS * scale;
        for (int gx = 0; gx < n; gx++) for (int gy = 0; gy < n; gy++) {
            double x0 = -1 + 2.0 * gx / n, x1 = -1 + 2.0 * (gx + 1) / n, y0 = -1 + 2.0 * gy / n, y1 = -1 + 2.0 * (gy + 1) / n;
            double mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
            if (Math.hypot(Math.max(Math.abs(mx) - 1.0 / n, 0), Math.max(Math.abs(my) - 1.0 / n, 0)) > 1) continue;
            Vec3 a = at(ctr, half, x0, y0, spin), b = at(ctr, half, x1, y0, spin), d = at(ctr, half, x1, y1, spin), e = at(ctr, half, x0, y1, spin);
            double u0 = (x0 + 1) / 2, u1 = (x1 + 1) / 2, v0 = (y0 + 1) / 2, v1 = (y1 + 1) / 2;
            if (c.body()) GateDraw.tex(c, a, b, d, e, GateAtlas.SEAL, u0, u1, v0, v1, WHITE_BLUE, .9 * k, .9 * k, .9 * k, .9 * k);
            if (c.glow()) GateDraw.tex(c, a, b, d, e, GateAtlas.SEAL, u0, u1, v0, v1, GLOW_BLUE, .5 * k, .5 * k, .5 * k, .5 * k);
        }
    }
    private static Vec3 at(Vec3 ctr, double half, double x, double z, double spin) {
        double cs = Math.cos(spin), sn = Math.sin(spin);
        return ctr.add(half * (x * cs - z * sn), 0, half * (x * sn + z * cs));
    }

    /** Draws one ring at {@code scale} of its radii (the whole annulus grows out of the centre). */
    private static void tier(Ctx c, Vec3 ctr, Tier t, int index, double age, double breath, double scale) {
        if (scale <= 0) return;
        double k = clamp01(scale * 3) * breath, spin = angle(index + 1, age), step = TAU / SEGMENTS;
        double r0 = t.r0() * S * scale, r1 = t.r1() * S * scale, mid = (r0 + r1) / 2, hw = ((t.r1() - t.r0()) * .8 + 1.2 * S) * scale, haloGain = index == 3 ? 1.7 : 1;
        Vec3 o = ctr.add(0, t.lift() * S, 0);
        int per = SEGMENTS / t.repeats();
        double split = t.second() == null ? r1 : r0 + (r1 - r0) * .55;
        for (int s = 0; s < SEGMENTS; s++) {
            double th0 = s * step, th1 = th0 + step;
            double p0 = (double) (s % per) / per, p1 = (double) (s % per + 1) / per;
            double w0 = th0 + spin, w1 = th1 + spin;
            if (c.body()) {
                band(c, o, r0, split, w0, w1, t.region(), p0, p1, 0, 1, t.color(), .9 * k, .9 * k);
                if (t.second() != null) band(c, o, split, r1, w0, w1, t.second(), p0, p1, 0, 1, t.color(), .9 * k, .9 * k);
            }
            if (c.glow()) {
                double g = Math.min(.8, .5 * k);
                band(c, o, r0, split, w0, w1, t.region(), p0, p1, 0, 1, GLOW_BLUE, g, g);
                if (t.second() != null) band(c, o, split, r1, w0, w1, t.second(), p0, p1, 0, 1, GLOW_BLUE, g, g);
                // Soft halo: light that spills past both edges of the band.
                double m = Math.min(.5, .17 * haloGain * k);
                GateDraw.gradient(c, pt(o, r0 - hw, w0), pt(o, r0 - hw, w1), pt(o, mid, w1), pt(o, mid, w0), GLOW_BLUE, 0, 0, m, m);
                GateDraw.gradient(c, pt(o, mid, w0), pt(o, mid, w1), pt(o, r1 + hw, w1), pt(o, r1 + hw, w0), GLOW_BLUE, m, m, 0, 0);
            }
        }
    }

    private static Vec3 pt(Vec3 o, double r, double a) { return o.add(Math.cos(a) * r, 0, Math.sin(a) * r); }

    private static void band(Ctx c, Vec3 o, double r0, double r1, double a0, double a1, GateAtlas.Region region, double p0, double p1,
                             double v0, double v1, int color, double k0, double k1) {
        GateDraw.tex(c, pt(o, r0, a0), pt(o, r0, a1), pt(o, r1, a1), pt(o, r1, a0), region, p0, p1, v0, v1, color, k0, k1, k1, k0);
    }

    /** Pale luminous plate behind the tiers (ref 1): white-cyan, brightest at the centre and gone at the rim. */
    private static void underlay(Ctx c, Vec3 ctr, double radius, double k) {
        if (k <= 0) return;
        Vec3 o = ctr.add(0, -.3 * S, 0);
        int n = 48, rings = 3;
        for (int i = 0; i < n; i++) {
            double a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
            for (int j = 0; j < rings; j++) {
                double r0 = radius * j / rings, r1 = radius * (j + 1) / rings;
                double f0 = .18 * (1 - (double) j / rings), f1 = .18 * (1 - (double) (j + 1) / rings);
                GateDraw.gradient(c, pt(o, r0, a0), pt(o, r0, a1), pt(o, r1, a1), pt(o, r1, a0), WHITE_BLUE, f0 * k, f0 * k, f1 * k, f1 * k);
            }
        }
    }
    /** Slowly turning shafts of light hanging from the circle: tapered crossed ribbons, soft at the edges and gone at the bottom. */
    private static void shafts(Ctx c, Vec3 ctr, double age, double shrink) {
        double on = smooth((age - SilentGateSequence.BEAT_GATE) / 30);
        if (on <= 0) return;
        for (int i = 0; i < 7; i++) {
            double r = (8 + i * 7) * S * shrink, a = i * 2.4 + age * .004 * (i % 2 == 0 ? 1 : -1);
            double al = (.08 + .04 * ((i * 5) % 3) / 2.0) * on * (1 + .15 * Math.sin(age * .05 + i));
            Vec3 top = pt(ctr, r, a), bottom = top.add(0, -90 * S, 0);
            for (int cross = 0; cross < 2; cross++) {
                double d = a + cross * Math.PI / 2;
                Vec3 w = new Vec3(Math.cos(d), 0, Math.sin(d));
                for (int side = -1; side <= 1; side += 2) {
                    // Half ribbon from the centre line (full alpha) to an edge (zero alpha), tapering 4.5 -> 1.2 blocks.
                    GateDraw.gradient(c, top, top.add(w.scale(4.5 * S * side)), bottom.add(w.scale(1.2 * S * side)), bottom, GLOW_BLUE, al, 0, 0, 0);
                }
            }
        }
    }
    private static void fan(Ctx c, Vec3 o, double radius, int color, double alpha) {
        if (alpha <= 0 || radius <= 0) return;
        for (int i = 0; i < 32; i++) {
            double a0 = i * TAU / 32, a1 = (i + 1) * TAU / 32;
            GateDraw.gradient(c, o, o, pt(o, radius, a1), pt(o, radius, a0), color, alpha, alpha, 0, 0);
        }
    }
    /** The circle closes in on itself and blazes once: a short additive flash that outlives the fade. */
    private static void flash(Ctx c, Vec3 ctr, double endK) {
        double t = clamp01((endK - .5) / .5);
        if (t <= 0 || !c.glow()) return;
        double save = c.fade; c.fade = 1;
        fan(c, ctr, (6 + 26 * t) * S, HOT, .32 * Math.sin(Math.PI * t) * .8);
        fan(c, ctr, (14 + 40 * t) * S, GLOW_BLUE, .16 * Math.sin(Math.PI * t));
        GateDraw.sprite(c, ctr, (10 + 30 * t) * S, .6 * Math.sin(Math.PI * t), HOT);
        c.fade = save;
    }
}
