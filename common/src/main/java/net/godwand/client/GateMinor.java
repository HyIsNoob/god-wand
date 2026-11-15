package net.godwand.client;

import net.godwand.client.GateDraw.Ctx;
import net.godwand.skill.SilentGateSequence;
import net.minecraft.world.phys.Vec3;

import static net.godwand.client.GateDraw.clamp01;
import static net.godwand.client.GateDraw.smooth;

/** The small circles: sky "mushroom" stacks, the upright ground arc behind the caster, and the activation wave. */
final class GateMinor {
    private GateMinor() {}
    private static final double TAU = Math.PI * 2;
    static final int JADE = 0x5CFFC8, GREEN = 0x63FF9E, GOLD = 0xFFC94A;
    /** Blocks per tick the activation wave travels from the gate. */
    static final double WAVE_SPEED = 8 * SilentGateSequence.SCALE, WAVE_REACH = 135 * SilentGateSequence.SCALE,
            FAR = 260, BIG = 6, FORM = SilentGateSequence.FORM_TICKS;
    /** A circle's beam leaves its lowest tier, this far under the cap. */
    static final double NOZZLE = 1.9 * BIG;
    /** Ticks before a circle is born that its seed of light falls out of the sky, and the height it falls from. */
    static final double SEED_TICKS = SilentGateSequence.SEED_TICKS, SEED_FALL = 90;

    /** 0..1..0 pulse while the wave front is within 12 blocks past the circle. */
    static double flare(Vec3 gate, Vec3 at, double wave) {
        if (wave < 0) return 0;
        double x = (wave - gate.distanceTo(at)) / 36;
        return x > 0 && x < 1 ? Math.sin(Math.PI * x) : 0;
    }

    /** How far circle {@code index} is written, 0..1: over FORM ticks from its birth; in the ending ({@code e} ticks after the barrage, -1 before) it is unwritten the same way
     *  backwards, where it stands, each circle at its own random moment within 25..90. */
    static double formed(int index, double age, double e) {
        double f = clamp01((age - SilentGateSequence.circleBorn(index)) / FORM);
        if (e >= 0) f = Math.min(f, 1 - clamp01((e - unwriteStart(index)) / FORM));
        return f;
    }
    static double unwriteStart(int index) { return SilentGateSequence.END_RETRACT_FROM + (SilentGateSequence.END_RETRACT_TO - SilentGateSequence.END_RETRACT_FROM - FORM) * hash(index); }
    private static double hash(int i) { double h = Math.sin(i * 127.1 + 17.3) * 43758.5453; return h - Math.floor(h); }
    /** The stages of the writing, each 0..1 of the formation {@code f}: two points of light draw the cap's rim (first half), the seal turns into place inside it,
     *  then the tiers drop out of the cap one after the other ({@code tier} 1, 2). */
    static double rim(double f) { return smooth(f / .5); }
    static double seal(double f) { return smooth((f - .3) / .35); }
    static double drop(double f, int tier) { return smooth((f - .4 - .15 * (tier - 1)) / .45); }

    /** A sky circle: the cap (a seal inside a rune rim of radius 3.2) with two smaller rune rings hanging under it; the beam leaves the lowest (references 8, 9).
     *  Always level, never moving. It is written, not scaled: see {@link #formed}. Beyond {@value #FAR} blocks the runes become plain strokes. */
    static void sky(Ctx c, Vec3 at, int index, double age, Vec3 gate, double wave, double e) {
        double since = age - SilentGateSequence.circleBorn(index);
        if (e < 0 && since > -SEED_TICKS && since < 4) seed(c, at, since);
        double f = formed(index, age, e);
        if (f <= 0) return;
        double dist = c.eye.distanceTo(at), flare = flare(gate, at, wave), s = BIG * (1 + .1 * flare), alpha = 1 + .6 * flare;
        int tint = index % 2 == 0 ? JADE : GateCircles.WHITE_BLUE, color = mix(tint, 0xFFFFFF, .7 * flare);
        double spin = age * .012 * (index % 2 == 0 ? 1 : -1) + index;
        // The gate stays the brightest thing in its part of the sky: a circle that overlaps its silhouette from here is dimmed, not hidden (it still fires).
        if (e < 70) alpha *= .3 + .7 * gateClear(c.eye, at, gate, 4 * s);
        double rim = rim(f), seal = seal(f), d1 = drop(f, 1), d2 = drop(f, 2);
        boolean far = dist > FAR;
        // Segments by distance: a circle that fills the frame must not show its corners.
        int fine = dist < 150 ? 2 : far ? 0 : 1, nRim = new int[]{10, 20, 32}[fine], n1 = new int[]{6, 14, 24}[fine], n2 = new int[]{4, 10, 16}[fine];
        Vec3 t1 = at.add(0, -.95 * s * d1, 0), t2 = at.add(0, -1.9 * s * d2, 0);
        if (c.body()) {
            // Far strokes are heavier so they stay at least ~1.5 px wide.
            double thick = far ? Math.min(3.2, dist / 110) : 1;
            // The same glyphs and seal far off as close by (owner, 2026-11-11: "the texture goes missing"); only the segment count drops with distance.
            GateAtlas.Region runes = GateAtlas.RUNES;
            ring(c, at, 3.2 * s - .8 * s * Math.min(1.6, thick), 3.2 * s, spin, runes, far ? 1 : 2, nRim, Math.min(1, .95 * alpha), color, rim);
            // The seal turns into place the way it will go on turning: fast, then gliding into its slow turn, never back.
            if (seal > 0) seal(c, at.add(0, -.1, 0), 2.35 * s * (.55 + .45 * seal), spin - (index % 2 == 0 ? 1 : -1) * 2.5 * (1 - seal) * (1 - seal), color, .85 * alpha * seal);
            if (d1 > 0) ring(c, t1, (2.2 - .7 * Math.min(1.5, thick)) * s * d1, 2.2 * s * (.4 + .6 * d1), -spin * 1.3, runes, far ? 1 : 2, n1, Math.min(1, .9 * alpha * d1), color, 1);
            if (d2 > 0) ring(c, t2, (1.3 - .65 * Math.min(1.5, thick)) * s * d2, 1.3 * s * (.4 + .6 * d2), spin * 1.6, runes, 1, n2, .9 * alpha * d2, color, 1);
        }
        if (c.glow()) {
            // Soft luminous fill under each tier, tinted by the circle.
            disc(c, at.add(0, -.05, 0), 4.2 * s * (.3 + .7 * rim), .46 * alpha * rim, tint);
            if (d1 > 0 && !far) disc(c, t1, 2.9 * s * d1, .3 * alpha * d1, tint);
            if (d2 > 0 && !far) disc(c, t2, 1.8 * s * d2, .34 * alpha * d2, tint);
            GateDraw.sprite(c, at.add(0, -1.1 * s, 0), 4.6 * s, .2 * alpha * f, tint == JADE ? JADE : GateCircles.GLOW_BLUE);
            if (d2 > 0) {
                // The nozzle: a hot point under the lowest tier and a short stem of light hanging from it, the stalk of the "mushroom" (references 8, 9).
                Vec3 nozzle = at.add(0, -NOZZLE * d2, 0), tip = nozzle.add(0, -2.2 * s, 0), side = new Vec3(0, 1, 0).cross(nozzle.subtract(c.eye));
                GateDraw.sprite(c, nozzle, 1.2 * s, .8 * Math.min(1, alpha) * d2, GateCircles.HOT);
                if (side.lengthSqr() > 1e-8 && !far) {
                    side = side.normalize().scale(.5 * s);
                    double st = .5 * Math.min(1, alpha) * d2;
                    GateDraw.gradient(c, nozzle.subtract(side), nozzle, tip, tip.subtract(side.scale(.3)), GateCircles.HOT, 0, st, 0, 0);
                    GateDraw.gradient(c, nozzle, nozzle.add(side), tip.add(side.scale(.3)), tip, GateCircles.HOT, st, 0, 0, 0);
                }
            }
            // The two points of light that draw the rim, and the flash when the last tier locks.
            if (rim > 0 && rim < 1) for (int side = -1; side <= 1; side += 2)
                GateDraw.sprite(c, pt(at, 2.8 * s, spin + side * rim * Math.PI), .9 * s, .95, 0xFFFFFF, GateAtlas.STAR, age * .3);
            double lock = e < 0 ? (since - (FORM - 3)) / 10 : -1;
            if (lock > 0 && lock < 1) GateDraw.sprite(c, at.add(0, -1.1 * s, 0), (3 + 4 * lock) * BIG, .5 * Math.sin(Math.PI * lock), GateCircles.HOT);
            if (flare > 0) { GateDraw.sprite(c, at.add(0, -1.1 * s, 0), 7 * BIG, .6 * flare, GateCircles.HOT); GateDraw.sprite(c, at.add(0, -NOZZLE, 0), 2.6 * BIG, .95 * flare, 0xFFFFFF); }
        }
    }

    /** The seed of a circle: a glint that falls out of the sky along a thread of light and bursts where the circle is about to be written. */
    private static void seed(Ctx c, Vec3 at, double since) {
        if (!c.glow()) return;
        if (since >= 0) { GateDraw.sprite(c, at, (1 + 1.5 * since) * BIG, .8 * (1 - since / 4), GateCircles.HOT); return; }
        double k = 1 + since / SEED_TICKS, fall = SEED_FALL * (1 - k) * (1 - k);
        Vec3 head = at.add(0, fall, 0), side = new Vec3(0, 1, 0).cross(head.subtract(c.eye));
        GateDraw.sprite(c, head, .5 * BIG, .9 * k, 0xFFFFFF, GateAtlas.STAR, since);
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(.22 * BIG);
        Vec3 tail = head.add(0, 6 + 30 * (1 - k), 0);
        GateDraw.gradient(c, head.subtract(side), head, tail, tail.subtract(side), GateCircles.HOT, 0, .7 * k, 0, 0);
        GateDraw.gradient(c, head, head.add(side), tail.add(side), tail, GateCircles.HOT, .7 * k, 0, 0, 0);
    }

    /** The caster's own circle on the ground: written as the wand settles (84..116), turning slowly, with glints rising from it; unwritten in the ending. */
    static void aura(Ctx c, Vec3 feet, double age, double e) {
        double f = clamp01((age - 30) / 32);
        if (e >= 0) f = Math.min(f, 1 - clamp01((e - 100) / 32));
        if (f <= 0) return;
        double nMin = c.nearMin, nMax = c.nearMax;
        c.nearMin = .4; c.nearMax = 1.8;
        try {
            Vec3 o = feet.add(0, .09, 0);
            double rim = rim(f), seal = seal(f), outer = drop(f, 2), spin = auraSpin(age), grown = auraGrown(age);
            for (int layer = 0; layer < 2; layer++) {
                if (layer == 0 ? !c.body() : !c.glow()) continue;
                int color = layer == 0 ? GateCircles.WHITE_BLUE : GateCircles.GLOW_BLUE;
                double a = layer == 0 ? .9 : .45;
                ring(c, o, 2.9, 3.7, spin * .7, GateAtlas.RUNES, 3, 48, a, color, rim);
                if (seal > 0) seal(c, o, 2.7 * (.5 + .5 * grown), spin, color, a * seal);
                if (outer > 0) ring(c, o, 4.0, 4.25, spin * .45, GateAtlas.RIM, 1, 48, a * outer, color, 1);
            }
            if (!c.glow()) return;
            disc(c, o, 5.2, .28 * f, GateCircles.GLOW_BLUE);
            if (rim > 0 && rim < 1) for (int side = -1; side <= 1; side += 2) GateDraw.sprite(c, pt(o, 3.3, spin * .7 + side * rim * Math.PI), .5, .95, 0xFFFFFF, GateAtlas.STAR, age * .3);
            for (int i = 0; i < 18; i++) {
                double life = (hash(i + 40) + age * (.008 + .006 * hash(i + 80))) % 1, ang = hash(i + 120) * TAU + age * .01, r = 1 + 3 * hash(i + 160);
                GateDraw.sprite(c, o.add(Math.cos(ang) * r, 3.4 * life, Math.sin(ang) * r), .07 + .07 * hash(i + 200), .9 * f * Math.sin(Math.PI * life), i % 3 == 0 ? 0xFFFFFF : GateCircles.GLOW_BLUE, GateAtlas.STAR, age * .05 + i);
            }
        } finally { c.nearMin = nMin; c.nearMax = nMax; }
    }

    /** The caster's circle spins up fast as it appears and glides down into its slow turn over {@value #AURA_GLIDE} ticks: one direction from the first frame, and
     *  the speed only ever falls toward the slow turn, so nothing stops, hitches or turns back (owner, 2026-11-14). Radians; every ring of it turns this way. */
    static double auraSpin(double age) { double k = 1 - auraK(age); return age * .01 - 4.5 * k * k * k; }
    /** 0..1 size of the caster's seal: it grows on the same long glide as the spin settles. */
    static double auraGrown(double age) { double k = 1 - auraK(age); return 1 - k * k * k; }
    static final double AURA_FROM = 30, AURA_GLIDE = 75;
    private static double auraK(double age) { return clamp01((age - AURA_FROM) / AURA_GLIDE); }

    /** 0 while a circle of radius {@code r} in front of the gate lies on the gate's silhouette (22 design units round its door centre {@code gate}) as seen from {@code eye}, 1 once clear of it. */
    static double gateClear(Vec3 eye, Vec3 at, Vec3 gate, double r) {
        Vec3 vg = gate.subtract(eye), vc = at.subtract(eye);
        double dg = vg.length(), dc = vc.length();
        if (dg < 1 || dc < 1 || dc >= dg) return 1;
        double theta = Math.acos(Math.max(-1, Math.min(1, vg.dot(vc) / (dg * dc)))), rho = Math.atan2(22 * SilentGateSequence.SCALE, dg), sigma = Math.atan2(r, dc);
        return smooth((theta - rho) / Math.max(.03, sigma + .5 * rho));
    }

    private static int mix(int a, int b, double t) {
        t = clamp01(t);
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }
    /** Flat soft glow disc from the glow tile: a luminous fill under line art. */
    private static void disc(Ctx c, Vec3 at, double radius, double alpha, int color) {
        GateDraw.tex(c, at.add(-radius, 0, -radius), at.add(radius, 0, -radius), at.add(radius, 0, radius), at.add(-radius, 0, radius),
                GateAtlas.GLOW, 0, 1, 0, 1, color, alpha, alpha, alpha, alpha);
    }

    /** Horizontal textured annulus. {@code sweep} 0..1 draws only what lies within sweep * 180 degrees of its start angle on either side: two arcs that grow round and meet opposite the start. */
    private static void ring(Ctx c, Vec3 o, double r0, double r1, double spin, GateAtlas.Region region, int repeats, int segs, double alpha, int color, double sweep) {
        int per = segs / repeats;
        double step = TAU / segs, reach = sweep * segs / 2;
        for (int s = 0; s < segs; s++) {
            double k = alpha * clamp01(reach - Math.min(s, segs - 1 - s));
            if (k <= 0) continue;
            double a0 = spin + s * step, a1 = a0 + step, p0 = (double) (s % per) / per, p1 = (double) (s % per + 1) / per;
            GateDraw.tex(c, pt(o, r0, a0), pt(o, r0, a1), pt(o, r1, a1), pt(o, r1, a0), region, p0, p1, 0, 1, color, k, k, k, k);
        }
    }
    private static Vec3 pt(Vec3 o, double r, double a) { return o.add(Math.cos(a) * r, 0, Math.sin(a) * r); }

    /** Flat seal disc: one quad of the seal tile. */
    private static void seal(Ctx c, Vec3 o, double radius, double spin, int color, double alpha) {
        double h = radius * 128 / 122.0, cs = Math.cos(spin), sn = Math.sin(spin);
        GateDraw.tex(c, rot(o, h, -1, -1, cs, sn), rot(o, h, 1, -1, cs, sn), rot(o, h, 1, 1, cs, sn), rot(o, h, -1, 1, cs, sn),
                GateAtlas.SEAL, 0, 1, 0, 1, color, alpha, alpha, alpha, alpha);
    }
    private static Vec3 rot(Vec3 o, double h, double x, double z, double cs, double sn) { return o.add(h * (x * cs - z * sn), 0, h * (x * sn + z * cs)); }

    /** The discs behind the caster are made once the finger is at the lips, one after the other (reference 4: owner, 2026-11-11). */
    static final double DISCS_FROM = 306;
    /** Upright disc facing the caster: seal in the middle, a strip ring round it, spinning slowly, with a faint column of light above. */
    static void ground(Ctx c, Vec3 at, Vec3 toward, int index, double age, boolean gold, Vec3 gate, double wave, double e) {
        double t = smooth((age - DISCS_FROM - 2.2 * index) / 16);
        if (e >= 0) t *= 1 - smooth((e - 20 - 3.0 * index) / 20);
        if (t <= 0) return;
        double flare = flare(gate, at, wave), radius = (gold ? 2.4 : 1.5) * (1 + .1 * flare) * (.6 + .4 * t);
        double alpha = t * (1 + .3 * flare);
        int color = gold ? GOLD : GREEN;
        Vec3 n = new Vec3(toward.x - at.x, 0, toward.z - at.z);
        Vec3 side = n.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : new Vec3(-n.z, 0, n.x).normalize(), up = new Vec3(0, 1, 0);
        double spin = age * (gold ? .015 : -.02) + index;
        if (c.body()) {
            double sealR = radius * .8, h = sealR * 128 / 122.0, cs = Math.cos(spin), sn = Math.sin(spin);
            int g = 4;
            for (int gx = 0; gx < g; gx++) for (int gy = 0; gy < g; gy++) {
                double x0 = -1 + 2.0 * gx / g, x1 = -1 + 2.0 * (gx + 1) / g, y0 = -1 + 2.0 * gy / g, y1 = -1 + 2.0 * (gy + 1) / g;
                if (Math.hypot(Math.max(Math.abs((x0 + x1) / 2) - .25, 0), Math.max(Math.abs((y0 + y1) / 2) - .25, 0)) > 1) continue;
                GateDraw.tex(c, v(at, side, up, h, x0, y0, cs, sn), v(at, side, up, h, x1, y0, cs, sn), v(at, side, up, h, x1, y1, cs, sn), v(at, side, up, h, x0, y1, cs, sn),
                        GateAtlas.SEAL, (x0 + 1) / 2, (x1 + 1) / 2, (y0 + 1) / 2, (y1 + 1) / 2, color, .85 * alpha, .85 * alpha, .85 * alpha, .85 * alpha);
            }
            GateAtlas.Region strip = gold ? GateAtlas.FILIGREE : GateAtlas.RIM;
            int segs = 24, repeats = gold ? 3 : 2, per = segs / repeats;
            for (int s = 0; s < segs; s++) {
                double a0 = spin * -.5 + s * TAU / segs, a1 = a0 + TAU / segs, p0 = (double) (s % per) / per, p1 = (double) (s % per + 1) / per;
                GateDraw.tex(c, v(at, side, up, radius * .82, Math.cos(a0), Math.sin(a0), 1, 0), v(at, side, up, radius * .82, Math.cos(a1), Math.sin(a1), 1, 0),
                        v(at, side, up, radius, Math.cos(a1), Math.sin(a1), 1, 0), v(at, side, up, radius, Math.cos(a0), Math.sin(a0), 1, 0),
                        strip, p0, p1, 0, 1, color, .9 * alpha, .9 * alpha, .9 * alpha, .9 * alpha);
            }
        }
        if (c.glow()) {
            GateDraw.sprite(c, at, radius * 1.25, .34 * alpha, color);
            // A pillar of light standing on the disc (reference 10): rayed, fading upwards.
            GateDraw.shaft(c, at.add(0, radius * .4, 0), at.add(0, radius * .4 + 20, 0), radius * .6, radius * .9, color, .16 * alpha, 0, age * .012 + index);
        }
    }
    private static Vec3 v(Vec3 o, Vec3 side, Vec3 up, double h, double x, double y, double cs, double sn) {
        return o.add(side.scale(h * (x * cs - y * sn))).add(up.scale(h * (x * sn + y * cs)));
    }

    /** Luminous cyan-white band travelling out from the gate (one flat, one upright), bright at the front with a soft trailing afterglow. */
    static void wave(Ctx c, Vec3 gate, float yaw, double radius) {
        if (radius <= 0 || radius > WAVE_REACH || !c.glow()) return;
        double fall = 1 - .6 * radius / WAVE_REACH, rad = Math.toRadians(yaw);
        Vec3 right = new Vec3(Math.cos(rad), 0, Math.sin(rad)), fwd = new Vec3(-Math.sin(rad), 0, Math.cos(rad)), up = new Vec3(0, 1, 0);
        int n = 40;
        double front = .9 * fall, trail = .3 * fall;
        for (int plane = 0; plane < 2; plane++) for (int i = 0; i < n; i++) {
            double a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
            Vec3 d0 = dir(plane, right, fwd, up, a0), d1 = dir(plane, right, fwd, up, a1);
            double sc = net.godwand.skill.SilentGateSequence.SCALE;
            double[] r = {radius - 8 * sc, radius - 1.8 * sc, radius, radius + 1.5 * sc}, al = {0, trail, front, 0};
            for (int k = 0; k < 3; k++)
                GateDraw.gradient(c, gate.add(d0.scale(r[k])), gate.add(d1.scale(r[k])), gate.add(d1.scale(r[k + 1])), gate.add(d0.scale(r[k + 1])),
                        0xC8F8FF, al[k], al[k], al[k + 1], al[k + 1]);
        }
    }
    private static Vec3 dir(int plane, Vec3 right, Vec3 fwd, Vec3 up, double a) {
        return plane == 0 ? right.scale(Math.cos(a)).add(fwd.scale(Math.sin(a))) : right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
    }
}
