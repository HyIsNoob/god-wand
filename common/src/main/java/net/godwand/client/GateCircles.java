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
    /** Inner to outer; speed in degrees per tick: signs alternate and every magnitude differs. The last tier lies level at CIRCLE_FULL (72 ticks after CIRCLE_START). */
    private static final Tier[] TIERS = {
            new Tier(GateAtlas.RUNES, null, 12, 16, .8, 3, SilentGateSequence.CIRCLE_START + 6, 38, -.20, WHITE_BLUE),
            new Tier(GateAtlas.SWIRL, null, 20, 30, 1.8, 1, SilentGateSequence.CIRCLE_START + 14, 38, .12, WHITE_BLUE),
            new Tier(GateAtlas.RUNES, null, 34, 42, 2.6, 4, SilentGateSequence.CIRCLE_START + 24, 38, -.25, TEAL),
            new Tier(GateAtlas.DASH, GateAtlas.RIM, 50, 56, 3.2, 12, SilentGateSequence.CIRCLE_START + 34, 38, .40, WHITE_BLUE)};
    static final int SEAL_START = SilentGateSequence.CIRCLE_START, SEAL_GROW = 34;
    /** How a ring arrives (owner, 2026-11-14: "not pretty, make it another way"): not blown up from the centre but as a hoop of light at its own size, steeply
     *  tilted (degrees, per ring, alternate ways) and tumbling like a coin; it settles level, inner rings first, and rings out once as it locks. */
    private static final double[] TILT = {58, -50, 44, -38};
    static final double SEAL_RADIUS = 9 * S, SEAL_SPEED = .35;
    /** Tiers including the seal (index 0 = seal, 1..4 = the rings from the inside out). */
    static final int TIER_COUNT = TIERS.length + 1;

    /** Turning speed of tier {@code i} (0 seal, 1..4 rings) in degrees per tick. */
    static double speed(int i) { return i == 0 ? SEAL_SPEED : TIERS[i - 1].speed(); }
    /** Rotation of tier {@code i} in radians at cast age {@code age}: unbroken from the first tick of the circle to the last. */
    static double angle(int i, double age) { return Math.toRadians(speed(i) * age); }
    private static int start(int i) { return i == 0 ? SEAL_START : TIERS[i - 1].start(); }
    private static int grow(int i) { return i == 0 ? SEAL_GROW : TIERS[i - 1].grow(); }
    /** 0..1: how far tier {@code i} has settled into the circle (inner tiers start first). */
    static double form(int i, double age) { return clamp01((age - start(i)) / grow(i)); }
    /** Size of tier {@code i}, 0 before it appears: it arrives a little small and eases out to its own size (the seal from a third), and in the ending
     *  ({@code e} ticks after the barrage, -1 before) shrinks back to the centre, outer tiers first. */
    static double scale(int i, double age, double e) {
        double f = form(i, age), k = 1 - f, from = i == 0 ? .35 : .86;
        return f <= 0 ? 0 : Math.min(from + (1 - from) * (1 - k * k * k), 1 - unwrite(e, TIER_COUNT - 1 - i));
    }
    /** 0..1 light of tier {@code i} as it appears (a fade, not a pop). */
    static double appear(int i, double age) { return smooth(form(i, age) / .2); }
    /** Tilt of ring {@code i} (1..4) out of the circle's plane in radians: steep as it appears, level (and at rest) once it has formed. */
    static double tilt(int i, double age) { double k = 1 - form(i, age); return Math.toRadians(TILT[i - 1]) * k * k; }
    /** Direction of the horizontal axis ring {@code i} is tilted about: it sweeps round as the ring settles (the tumble of a coin), slower and slower. */
    static double tiltAxis(int i, double age) { double k = 1 - form(i, age); return i * 1.9 + Math.signum(TILT[i - 1]) * 5.5 * k * k; }
    /** Extra turn of tier {@code i} while it forms, in its own direction: it spins up fast and glides into its steady turn, so it never slows to a stop or turns back. */
    static double spinUp(int i, double age) { double k = 1 - form(i, age); return -Math.signum(speed(i)) * 2.6 * k * k * k; }

    /** How far round tier {@code i} is written, 0..1, while it forms: points of light leave three spots of the ring and draw it both ways round until they meet,
     *  in the first half of its settling. In the ending the tiers shrink whole, so this stays 1. */
    static double written(int i, double age) { return smooth(form(i, age) / .55); }
    private static final int WRITERS = 3;
    /** Ticks before the seal appears at which a thread of light leaves the caster for the sky, and after it at which the thread has faded. */
    static final int IGNITE_LEAD = 9, IGNITE_FADE = 26;

    /** Altitude the circle hangs at relative to the anchor; shared by every layer. */
    static Vec3 center(Vec3 anchor, Vec3 gate) { return new Vec3(gate.x, anchor.y + SilentGateSequence.circleAltitude(anchor.y), gate.z); }

    /** {@code e}: ticks after the barrage (-1 before): the tiers dissolve from the outside in, the seal last, and the light fades with them. */
    static void great(Ctx c, Vec3 anchor, Vec3 gate, double age, double e) {
        ignite(c, anchor.add(0, 2.4, 0), center(anchor, gate), age);
        if (age < SEAL_START) return;
        double endK = e < 0 ? 0 : smooth((e - 100) / 60);
        Vec3 ctr = center(anchor, gate);
        double breath = (1 + .1 * Math.sin(age * .09));
        seal(c, ctr, age, breath * appear(0, age), scale(0, age, e));
        for (int i = 0; i < TIERS.length; i++) tier(c, ctr, TIERS[i], i, age, breath * appear(i + 1, age), scale(i + 1, age, e), e < 0 ? written(i + 1, age) : 1);
        double whole = appear(TIER_COUNT - 1, age);
        if (c.glow()) {
            // The seal is lit: a burst where the caster's thread of light arrives.
            double lit = (age - SEAL_START) / 14;
            if (lit < 1) GateDraw.sprite(c, ctr, (6 + 22 * lit) * S, .8 * Math.sin(Math.PI * lit), HOT);
            // Wide faint disc of light above the circle, and the shafts it throws down once the gate arrives.
            fan(c, ctr.add(0, 6 * S, 0), 70 * S * scale(TIER_COUNT - 1, age, e), GLOW_BLUE, .22 * whole * breath * (1 - endK));
            shafts(c, ctr, age, Math.min(1 - endK, scale(TIER_COUNT - 1, age, e)));
        }
        if (c.body()) underlay(c, ctr, 58 * S * scale(TIER_COUNT - 1, age, e), whole * (1 - endK));
    }

    /** A ring's plane: tilted by {@code tilt} radians about the horizontal axis at angle {@code axis}; angles on it are the level circle's own when the tilt is 0. */
    private record Plane(Vec3 o, double axis, Vec3 u, Vec3 w) {
        static Plane of(Vec3 o, double tilt, double axis) {
            double cs = Math.cos(axis), sn = Math.sin(axis);
            return new Plane(o, axis, new Vec3(cs, 0, sn), new Vec3(-sn * Math.cos(tilt), Math.sin(tilt), cs * Math.cos(tilt)));
        }
        Vec3 pt(double r, double a) { return o.add(u.scale(r * Math.cos(a - axis))).add(w.scale(r * Math.sin(a - axis))); }
    }

    /** The thread of light that lights the circle: it leaves the caster IGNITE_LEAD ticks before the seal appears, its head reaches the sky exactly then, and it fades. */
    private static void ignite(Ctx c, Vec3 from, Vec3 to, double age) {
        double t = age - (SEAL_START - IGNITE_LEAD);
        if (t <= 0 || t >= IGNITE_LEAD + IGNITE_FADE || !c.glow()) return;
        double head = smooth(t / IGNITE_LEAD), alpha = t < IGNITE_LEAD ? 1 : 1 - (t - IGNITE_LEAD) / IGNITE_FADE;
        Vec3 tip = from.lerp(to, head), side = to.subtract(from).cross(from.lerp(tip, .5).subtract(c.eye));
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize();
        // Hair-thin at the caster, two blocks wide where it meets the sky 550 blocks away, so it reads as one line from any shot.
        Vec3 w0 = side.scale(.3), w1 = side.scale(.3 + 4 * head * S / 4);
        GateDraw.gradient(c, from.subtract(w0), from, tip, tip.subtract(w1), HOT, 0, .9 * alpha, .9 * alpha, 0);
        GateDraw.gradient(c, from, from.add(w0), tip.add(w1), tip, HOT, .9 * alpha, 0, 0, .9 * alpha);
        // A beam, not a hair: a rayed body round the core, and sparks winding up it from the caster.
        GateDraw.shaft(c, from, tip, 1.1, 1.1 + 9 * head * S / 4, GLOW_BLUE, .6 * alpha, .5 * alpha, age * .2);
        Vec3 axis = tip.subtract(from), u1 = side, u2 = axis.normalize().cross(side);
        for (int i = 0; i < 90; i++) {
            double h = ((i * .6180339 + age * .03) % 1), r = .3 + (1.2 + 5 * h) * (i % 3) / 2.0, a = i * 2.4 + age * .35 + h * 20;
            Vec3 at = from.add(axis.scale(h)).add(u1.scale(Math.cos(a) * r)).add(u2.scale(Math.sin(a) * r));
            GateDraw.sprite(c, at, .12 + .6 * h * S / 4, .9 * alpha * (1 - .5 * h), i % 4 == 0 ? 0xFFFFFF : GLOW_BLUE, GateAtlas.STAR, age * .2 + i);
        }
        GateBeams.ring(c, from.add(0, -2.2, 0), 1 + 1.2 * t, 1.4, .8 * alpha, HOT, 40);
        if (t < IGNITE_LEAD) GateDraw.sprite(c, tip, 1 + 5 * head * S / 4, .95, 0xFFFFFF, GateAtlas.STAR, age * .4);
    }

    /** 0..1: how far the k-th ring from the outside (0) to the seal (4) has shrunk {@code e} ticks into the ending. */
    private static double unwrite(double e, int k) { return e < 0 ? 0 : smooth((e - SilentGateSequence.END_UNWRITE_FROM - 6 * k) / 26); }

    private static void seal(Ctx c, Vec3 ctr, double age, double breath, double scale) {
        if (scale <= 0) return;
        double spin = angle(0, age) + spinUp(0, age), k = clamp01(scale * 3) * breath;
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

    /** Draws one ring at {@code scale} of its radii (the whole annulus grows out of the centre) and {@code written} of its way round (see {@link #written}). */
    private static void tier(Ctx c, Vec3 ctr, Tier t, int index, double age, double breath, double scale, double written) {
        if (scale <= 0) return;
        int i = index + 1, arc = SEGMENTS / WRITERS;
        double all = clamp01(scale * 3) * breath, spin = angle(i, age) + spinUp(i, age), step = TAU / SEGMENTS, reach = written * arc / 2;
        double r0 = t.r0() * S * scale, r1 = t.r1() * S * scale, mid = (r0 + r1) / 2, hw = ((t.r1() - t.r0()) * .8 + 1.2 * S) * scale;
        // The ring rings out as it locks level: its halo blazes for a third of a second.
        double locked = age - start(i) - grow(i), haloGain = (index == 3 ? 1.7 : 1) * (1 + (locked >= 0 ? 2.2 * Math.exp(-locked / 7) : 0));
        Plane o = Plane.of(ctr.add(0, t.lift() * S, 0), tilt(i, age), tiltAxis(i, age));
        int per = SEGMENTS / t.repeats();
        double split = t.second() == null ? r1 : r0 + (r1 - r0) * .55;
        // The points of light that write the ring: two from each of three spots, running apart until they meet.
        if (c.glow() && written > 0 && written < 1) for (int w = 0; w < WRITERS; w++) for (int side = -1; side <= 1; side += 2)
            GateDraw.sprite(c, o.pt(mid, spin + (w + side * written * .5) * TAU / WRITERS), (2.5 + (t.r1() - t.r0()) * .5) * S * Math.max(.3, scale), .95, 0xFFFFFF, GateAtlas.STAR, age * .3);
        for (int s = 0; s < SEGMENTS; s++) {
            double k = all * clamp01(reach - Math.min(s % arc, arc - 1 - s % arc));
            if (k <= 0) continue;
            double th0 = s * step, th1 = th0 + step;
            double p0 = (double) (s % per) / per, p1 = (double) (s % per + 1) / per;
            double w0 = th0 + spin, w1 = th1 + spin;
            if (c.body()) {
                band(c, o, r0, split, w0, w1, t.region(), p0, p1, 0, 1, t.color(), .9 * k, .9 * k);
                if (t.second() != null) band(c, o, split, r1, w0, w1, t.second(), p0, p1, 0, 1, t.color(), .9 * k, .9 * k);
            }
            if (c.glow()) {
                double g = Math.min(1, .72 * k);
                band(c, o, r0, split, w0, w1, t.region(), p0, p1, 0, 1, GLOW_BLUE, g, g);
                if (t.second() != null) band(c, o, split, r1, w0, w1, t.second(), p0, p1, 0, 1, GLOW_BLUE, g, g);
                // Soft halo: light that spills past both edges of the band.
                double m = Math.min(.7, .26 * haloGain * k);
                GateDraw.gradient(c, o.pt(r0 - hw, w0), o.pt(r0 - hw, w1), o.pt(mid, w1), o.pt(mid, w0), GLOW_BLUE, 0, 0, m, m);
                GateDraw.gradient(c, o.pt(mid, w0), o.pt(mid, w1), o.pt(r1 + hw, w1), o.pt(r1 + hw, w0), GLOW_BLUE, m, m, 0, 0);
            }
        }
    }

    private static Vec3 pt(Vec3 o, double r, double a) { return o.add(Math.cos(a) * r, 0, Math.sin(a) * r); }

    private static void band(Ctx c, Plane o, double r0, double r1, double a0, double a1, GateAtlas.Region region, double p0, double p1,
                             double v0, double v1, int color, double k0, double k1) {
        GateDraw.tex(c, o.pt(r0, a0), o.pt(r0, a1), o.pt(r1, a1), o.pt(r1, a0), region, p0, p1, v0, v1, color, k0, k1, k1, k0);
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
    /** Slowly turning shafts of light falling from the circle: rayed, soft, widening and fading toward the ground. */
    private static void shafts(Ctx c, Vec3 ctr, double age, double shrink) {
        double on = smooth((age - SilentGateSequence.BEAT_GATE) / 30);
        if (on <= 0) return;
        for (int i = 0; i < 9; i++) {
            double r = (8 + i * 6) * S * shrink, a = i * 2.4 + age * .004 * (i % 2 == 0 ? 1 : -1);
            // They go with the circle: shrunk to its centre at full strength they stood on as a lone pillar after it was gone.
            double al = (.16 + .08 * ((i * 5) % 3) / 2.0) * on * (1 + .15 * Math.sin(age * .05 + i)) * Math.min(1, shrink);
            Vec3 top = pt(ctr, r, a);
            Vec3 bottom = top.add(0, -(70 + 12 * (i % 4)) * S, 0);
            GateDraw.shaft(c, top, bottom, (3 + i % 3) * S, (5.5 + 2 * (i % 3)) * S, GLOW_BLUE, al, 0, age * .011 + i * 1.9);
            // A wide soft cone round each: light in the air, not a strip.
            GateDraw.shaft(c, top, bottom, (7 + 2 * (i % 3)) * S, (13 + 4 * (i % 3)) * S, GLOW_BLUE, .45 * al, 0, age * .007 + i);
            // Dust of light sinking down each shaft.
            for (int m = 0; m < 13; m++) {
                double life = ((i * 7 + m) * .381966 + age * (.004 + .002 * (m % 3))) % 1, w = (3 + i % 3) * S * (.3 + .5 * life);
                Vec3 at = top.add(Math.cos(m * 2.4 + i) * w, -life * 60 * S, Math.sin(m * 2.4 + i) * w);
                GateDraw.sprite(c, at, (.5 + .4 * (m % 2)) * S, 2.2 * al * Math.sin(Math.PI * life), m % 3 == 0 ? 0xFFFFFF : GLOW_BLUE, GateAtlas.STAR, age * .02 + m);
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
