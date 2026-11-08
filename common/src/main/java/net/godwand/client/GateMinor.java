package net.godwand.client;

import net.godwand.client.GateDraw.Ctx;
import net.minecraft.world.phys.Vec3;

import static net.godwand.client.GateDraw.clamp01;
import static net.godwand.client.GateDraw.smooth;

/** The small circles: sky "mushroom" stacks, the upright ground arc behind the caster, and the activation wave. */
final class GateMinor {
    private GateMinor() {}
    private static final double TAU = Math.PI * 2;
    static final int JADE = 0x5CFFC8, GREEN = 0x63FF9E, GOLD = 0xFFC94A;
    /** Blocks per tick the activation wave travels from the gate. */
    static final double WAVE_SPEED = 6 * net.godwand.skill.SilentGateSequence.SCALE, WAVE_REACH = 95 * net.godwand.skill.SilentGateSequence.SCALE,
            FAR = 160, BLOOM_TICKS = 8, BIG = 2;
    private static final int SEGS = 12;

    /** 0..1..0 pulse while the wave front is within 12 blocks past the circle. */
    static double flare(Vec3 gate, Vec3 at, double wave) {
        if (wave < 0) return 0;
        double x = (wave - gate.distanceTo(at)) / 36;
        return x > 0 && x < 1 ? Math.sin(Math.PI * x) : 0;
    }
    /** Grows to 1.15 and settles to 1 over {@link #BLOOM_TICKS}; 0 before the circle is born. */
    static double bloom(double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        return t < .6 ? 1.15 * smooth(t / .6) : 1.15 - .15 * smooth((t - .6) / .4);
    }

    /** Three rune rings of radius 3.2 / 2.2 / 1.2, each .8 higher, over a seal disc; one plain ring and no runes beyond 80 blocks. */
    static void sky(Ctx c, Vec3 at, int index, double age, double born, Vec3 gate, double wave) {
        double t = (age - born) / BLOOM_TICKS, k = bloom(t);
        if (k <= 0) return;
        double flare = flare(gate, at, wave), s = k * BIG * (1 + .1 * flare), alpha = smooth(t * 2) * (1 + .6 * flare);
        int tint = index % 2 == 0 ? JADE : GateCircles.WHITE_BLUE, color = mix(tint, 0xFFFFFF, .7 * flare);
        double spin = age * .012 * (index % 2 == 0 ? 1 : -1) + index;
        double dist = c.eye.distanceTo(at);
        if (c.body()) {
            if (dist > FAR) {
                disc(c, at, 3.6 * s, .22 * alpha, tint);
                ring(c, at, 2.4 * s, 3.2 * s, 0, spin, GateAtlas.RIM, 1, 12, .9 * alpha, color);
            }
            else {
                // Soft luminous fill under every tier, tinted by the circle.
                disc(c, at.add(0, -.05, 0), 4.2 * s, .27 * alpha, tint);
                disc(c, at.add(0, 1.6 * s, 0), 2.8 * s, .22 * alpha, tint);
                disc(c, at.add(0, 2.4 * s, 0), 1.7 * s, .25 * alpha, tint);
                seal(c, at.add(0, -.1, 0), 3.2 * s, spin, color, .85 * alpha);
                ring(c, at.add(0, .8 * s, 0), 2.4 * s, 3.2 * s, 0, spin, GateAtlas.RUNES, 2, 12, .9 * alpha, color);
                ring(c, at.add(0, 1.6 * s, 0), 1.5 * s, 2.2 * s, 0, -spin * 1.3, GateAtlas.RUNES, 2, 10, .9 * alpha, color);
                ring(c, at.add(0, 2.4 * s, 0), .6 * s, 1.2 * s, 0, spin * 1.6, GateAtlas.RUNES, 1, 8, .9 * alpha, color);
            }
        }
        if (c.glow()) {
            double since = age - born, flash = since < 14 ? Math.sin(Math.PI * since / 14) : 0;
            GateDraw.sprite(c, at.add(0, 1.2 * s, 0), 4.6 * s, .16 * alpha, tint == JADE ? JADE : GateCircles.GLOW_BLUE);
            GateDraw.sprite(c, at.add(0, 2.6 * s, 0), .9 * s + .4, .75 * Math.min(1, alpha), GateCircles.HOT);
            if (flash > 0) GateDraw.sprite(c, at.add(0, 1.2 * s, 0), (3 + 4 * since / 14) * BIG, .5 * flash, GateCircles.HOT);
            if (flare > 0) { GateDraw.sprite(c, at.add(0, 1.2 * s, 0), 7 * BIG, .6 * flare, GateCircles.HOT); GateDraw.sprite(c, at.add(0, 2.4 * s, 0), 2.6 * BIG, .95 * flare, 0xFFFFFF); }
        }
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

    /** Horizontal textured annulus of {@value #SEGS} segments. */
    private static void ring(Ctx c, Vec3 o, double r0, double r1, double v0, double spin, GateAtlas.Region region, int repeats, int segs, double alpha, int color) {
        int per = segs / repeats;
        double step = TAU / segs;
        for (int s = 0; s < segs; s++) {
            double a0 = spin + s * step, a1 = a0 + step, p0 = (double) (s % per) / per, p1 = (double) (s % per + 1) / per;
            GateDraw.tex(c, pt(o, r0, a0), pt(o, r0, a1), pt(o, r1, a1), pt(o, r1, a0), region, p0, p1, 0, 1, color, alpha, alpha, alpha, alpha);
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

    /** Upright disc facing the caster: seal in the middle, a strip ring round it, spinning slowly, with a faint column of light above. */
    static void ground(Ctx c, Vec3 at, Vec3 toward, int index, double age, boolean gold, Vec3 gate, double wave) {
        double t = smooth((age - 200 - 4.0 * index) / 20);
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
            GateDraw.sprite(c, at, radius * 1.5, .12 * alpha, color);
            // Faint column rising from the top of the disc: a crossed pair of ribbons, soft at the edges.
            Vec3 base = at.add(0, radius, 0), top = base.add(0, 14, 0);
            double al = .09 * alpha;
            for (Vec3 w : new Vec3[]{side, n.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : n.normalize()}) for (int sgn = -1; sgn <= 1; sgn += 2)
                GateDraw.gradient(c, base, base.add(w.scale(.9 * sgn)), top.add(w.scale(.3 * sgn)), top, color, al, 0, 0, 0);
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
