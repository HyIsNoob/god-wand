package net.godwand.client;

import net.godwand.client.GateDraw.Ctx;
import net.godwand.skill.SilentGateSequence;
import net.minecraft.world.phys.Vec3;

import static net.godwand.client.GateDraw.clamp01;
import static net.godwand.client.GateDraw.smooth;

/** The great circle: five tiers of line art that write themselves in from the inside out, with halos, light shafts and a closing flash. */
final class GateCircles {
    private GateCircles() {}
    private static final double TAU = Math.PI * 2, EDGE = .14;
    static final int SEGMENTS = 96;
    static final int WHITE_BLUE = 0xD8F4FF, GLOW_BLUE = 0x7FE8FF, TEAL = 0x9FF0F0, HOT = 0xEAFBFF;

    /** One annulus of the circle. {@code region} null means the seal disc. */
    private record Tier(GateAtlas.Region region, GateAtlas.Region second, double r0, double r1, double lift, int repeats,
                        int start, double speed, int color) {}
    // Inner to outer; speed in degrees per tick, sign alternates so neighbours turn against each other.
    private static final Tier[] TIERS = {
            new Tier(GateAtlas.RUNES, null, 12, 16, .8, 3, 88, -.15, WHITE_BLUE),
            new Tier(GateAtlas.SWIRL, null, 20, 30, 1.8, 1, 96, .10, WHITE_BLUE),
            new Tier(GateAtlas.RUNES, null, 34, 42, 2.6, 4, 104, -.07, TEAL),
            new Tier(GateAtlas.DASH, GateAtlas.RIM, 50, 56, 3.2, 12, 112, .05, WHITE_BLUE)};
    private static final int SEAL_START = 80, WRITE_TICKS = 32;
    static final double SEAL_RADIUS = 9, SEAL_SPEED = .2;

    /** Altitude the circle hangs at relative to the anchor; shared by every layer. */
    static Vec3 center(Vec3 anchor, Vec3 gate) { return new Vec3(gate.x, anchor.y + SilentGateSequence.CIRCLE_ALTITUDE, gate.z); }

    static void great(Ctx c, Vec3 anchor, Vec3 gate, double age, double endK) {
        if (age < SEAL_START) return;
        Vec3 ctr = center(anchor, gate);
        double shrink = 1 - .92 * smooth(endK), breath = 1 + .1 * Math.sin(age * .09);
        double lastWrite = 0;
        seal(c, ctr, age, shrink, breath);
        for (int i = 0; i < TIERS.length; i++) lastWrite = Math.max(lastWrite, tier(c, ctr, TIERS[i], i, age, shrink, breath));
        if (c.glow()) {
            // Wide faint disc of light above the circle, and the shafts it throws down once the gate arrives.
            fan(c, ctr.add(0, 6, 0), 70 * shrink, GLOW_BLUE, .15 * smooth((age - SEAL_START) / 40) * breath);
            shafts(c, ctr, age, shrink);
        }
        if (c.body()) underlay(c, ctr, 58 * shrink, smooth((age - SEAL_START) / 60));
        flash(c, ctr, endK);
    }

    /** Fresh-ink brightness: strong at the writing head, gone a moment after the stroke has passed. */
    private static double ink(double head, double theta, double live) { return live * Math.exp(-(head - theta) / .45); }

    private static void seal(Ctx c, Vec3 ctr, double age, double shrink, double breath) {
        double reveal = smooth((age - SEAL_START) / WRITE_TICKS);
        if (reveal <= 0) return;
        double head = reveal * (TAU + .6), spin = Math.toRadians(SEAL_SPEED * age), live = 1 - smooth((reveal - .92) / .08);
        int n = 6;
        double half = SEAL_RADIUS * shrink;
        for (int gx = 0; gx < n; gx++) for (int gy = 0; gy < n; gy++) {
            double x0 = -1 + 2.0 * gx / n, x1 = -1 + 2.0 * (gx + 1) / n, y0 = -1 + 2.0 * gy / n, y1 = -1 + 2.0 * (gy + 1) / n;
            double mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
            if (Math.hypot(Math.max(Math.abs(mx) - 1.0 / n, 0), Math.max(Math.abs(my) - 1.0 / n, 0)) > 1) continue;
            double theta = (Math.atan2(my, mx) + Math.PI) % TAU, k = clamp01((head - theta) / .5) * breath;
            double hot = 1 + 2.2 * ink(head, theta, live);
            Vec3 a = at(ctr, half, x0, y0, spin), b = at(ctr, half, x1, y0, spin), d = at(ctr, half, x1, y1, spin), e = at(ctr, half, x0, y1, spin);
            double u0 = (x0 + 1) / 2, u1 = (x1 + 1) / 2, v0 = (y0 + 1) / 2, v1 = (y1 + 1) / 2;
            if (c.body()) GateDraw.tex(c, a, b, d, e, GateAtlas.SEAL, u0, u1, v0, v1, WHITE_BLUE, .9 * k, .9 * k, .9 * k, .9 * k);
            if (c.glow()) GateDraw.tex(c, a, b, d, e, GateAtlas.SEAL, u0, u1, v0, v1, GLOW_BLUE, Math.min(.8, .5 * k * hot), Math.min(.8, .5 * k * hot),
                    Math.min(.8, .5 * k * hot), Math.min(.8, .5 * k * hot));
        }
        if (reveal < 1 && c.glow()) GateDraw.sprite(c, at(ctr, half, Math.cos(head) * .85, Math.sin(head) * .85, 0), 1.6 * shrink + .4, .8 * live, HOT);
    }
    private static Vec3 at(Vec3 ctr, double half, double x, double z, double spin) {
        double cs = Math.cos(spin), sn = Math.sin(spin);
        return ctr.add(half * (x * cs - z * sn), 0, half * (x * sn + z * cs));
    }

    /** Draws one ring; returns its reveal so the caller can tell whether anything is still being written. */
    private static double tier(Ctx c, Vec3 ctr, Tier t, int index, double age, double shrink, double breath) {
        double reveal = smooth((age - t.start()) / WRITE_TICKS);
        if (reveal <= 0) return 0;
        double head = reveal * (TAU + EDGE), live = 1 - smooth((reveal - .92) / .08);
        double spin = Math.toRadians(t.speed() * age), step = TAU / SEGMENTS;
        double r0 = t.r0() * shrink, r1 = t.r1() * shrink, mid = (r0 + r1) / 2, hw = (r1 - r0) * .8 + 1.2, haloGain = index == 3 ? 1.7 : 1;
        Vec3 o = ctr.add(0, t.lift(), 0);
        int per = SEGMENTS / t.repeats();
        double split = t.second() == null ? r1 : r0 + (r1 - r0) * .55;
        for (int s = 0; s < SEGMENTS; s++) {
            double th0 = s * step, th1 = th0 + step;
            if (th0 >= head) break;
            double k0 = clamp01((head - th0) / EDGE) * breath, k1 = clamp01((head - th1) / EDGE) * breath;
            double h0 = 1 + 2.2 * ink(head, th0, live), h1 = 1 + 2.2 * ink(head, th1, live);
            double p0 = (double) (s % per) / per, p1 = (double) (s % per + 1) / per;
            double w0 = th0 + spin, w1 = th1 + spin;
            if (c.body()) {
                band(c, o, r0, split, w0, w1, t.region(), p0, p1, 0, 1, t.color(), .9 * k0, .9 * k1);
                if (t.second() != null) band(c, o, split, r1, w0, w1, t.second(), p0, p1, 0, 1, t.color(), .9 * k0, .9 * k1);
            }
            if (c.glow()) {
                double g0 = Math.min(.8, .5 * k0 * h0), g1 = Math.min(.8, .5 * k1 * h1);
                band(c, o, r0, split, w0, w1, t.region(), p0, p1, 0, 1, GLOW_BLUE, g0, g1);
                if (t.second() != null) band(c, o, split, r1, w0, w1, t.second(), p0, p1, 0, 1, GLOW_BLUE, g0, g1);
                // Soft halo: light that spills past both edges of the band.
                double m0 = Math.min(.5, .17 * haloGain * k0 * (1 + .8 * (h0 - 1))), m1 = Math.min(.5, .17 * haloGain * k1 * (1 + .8 * (h1 - 1)));
                GateDraw.gradient(c, pt(o, r0 - hw, w0), pt(o, r0 - hw, w1), pt(o, mid, w1), pt(o, mid, w0), GLOW_BLUE, 0, 0, m1, m0);
                GateDraw.gradient(c, pt(o, mid, w0), pt(o, mid, w1), pt(o, r1 + hw, w1), pt(o, r1 + hw, w0), GLOW_BLUE, m0, m1, 0, 0);
            }
        }
        if (reveal < 1 && c.glow()) {
            Vec3 headAt = pt(o, mid, head + spin);
            double pulse = 1 - smooth((reveal - .85) / .15);
            GateDraw.sprite(c, headAt, (r1 - r0) * .9 + 1.4, .75 * pulse, GLOW_BLUE);
            GateDraw.sprite(c, headAt, (r1 - r0) * .35 + .5, .9 * pulse, HOT);
        }
        return reveal;
    }

    private static Vec3 pt(Vec3 o, double r, double a) { return o.add(Math.cos(a) * r, 0, Math.sin(a) * r); }

    private static void band(Ctx c, Vec3 o, double r0, double r1, double a0, double a1, GateAtlas.Region region, double p0, double p1,
                             double v0, double v1, int color, double k0, double k1) {
        GateDraw.tex(c, pt(o, r0, a0), pt(o, r0, a1), pt(o, r1, a1), pt(o, r1, a0), region, p0, p1, v0, v1, color, k0, k1, k1, k0);
    }

    /** Pale luminous plate behind the tiers (ref 1): white-cyan, brightest at the centre and gone at the rim. */
    private static void underlay(Ctx c, Vec3 ctr, double radius, double k) {
        if (k <= 0) return;
        Vec3 o = ctr.add(0, -.3, 0);
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
            double r = (8 + i * 7) * shrink, a = i * 2.4 + age * .004 * (i % 2 == 0 ? 1 : -1);
            double al = (.08 + .04 * ((i * 5) % 3) / 2.0) * on * (1 + .15 * Math.sin(age * .05 + i));
            Vec3 top = pt(ctr, r, a), bottom = top.add(0, -90, 0);
            for (int cross = 0; cross < 2; cross++) {
                double d = a + cross * Math.PI / 2;
                Vec3 w = new Vec3(Math.cos(d), 0, Math.sin(d));
                for (int side = -1; side <= 1; side += 2) {
                    // Half ribbon from the centre line (full alpha) to an edge (zero alpha), tapering 4.5 -> 1.2 blocks.
                    GateDraw.gradient(c, top, top.add(w.scale(4.5 * side)), bottom.add(w.scale(1.2 * side)), bottom, GLOW_BLUE, al, 0, 0, 0);
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
        fan(c, ctr, 6 + 26 * t, HOT, .32 * Math.sin(Math.PI * t) * .8);
        fan(c, ctr, 14 + 40 * t, GLOW_BLUE, .16 * Math.sin(Math.PI * t));
        GateDraw.sprite(c, ctr, 10 + 30 * t, .6 * Math.sin(Math.PI * t), HOT);
        c.fade = save;
    }
}
