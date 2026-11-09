package net.godwand.client;

import net.godwand.client.GateDraw.Ctx;
import net.godwand.skill.SilentGateSequence;
import net.minecraft.world.phys.Vec3;

import static net.godwand.client.GateDraw.clamp01;
import static net.godwand.client.GateDraw.smooth;

/** Effects of the gate: the light slit it climbs out of, wind streaks and dust of the descent, the shockwave, pressure rings and the light spilling from the open doors. */
final class GateFx {
    private GateFx() {}
    private static final double TAU = Math.PI * 2;
    static final int HOT = 0xEAFBFF, BLUE = 0x7FE8FF, DUST = 0xB8C8E0;
    private static final int OPENING_Y0 = 15;

    /** Gate frame: foot position and the unit axes right / up / forward (forward points away from the caster). */
    record Pose(Vec3 foot, Vec3 r, Vec3 u, Vec3 d, double s) {
        static Pose of(Vec3 foot, float yaw) {
            double rad = Math.toRadians(yaw);
            return new Pose(foot, new Vec3(Math.cos(rad), 0, Math.sin(rad)), new Vec3(0, 1, 0), new Vec3(-Math.sin(rad), 0, Math.cos(rad)), SilentGateSequence.SCALE);
        }
        /** Local design units (x right, y up, z forward) to world: everything is {@code s} times larger. */
        Vec3 at(double x, double y, double z) { return foot.add(r.scale(x * s)).add(u.scale(y * s)).add(d.scale(z * s)); }
        Vec3 R(double x) { return r.scale(x * s); }
        Vec3 U(double x) { return u.scale(x * s); }
        Vec3 D(double x) { return d.scale(x * s); }
    }

    private static double hash(int i, int salt) { double x = Math.sin(i * 127.1 + salt * 311.7) * 43758.5453; return x - Math.floor(x); }

    /** Vertical lens of light {@code length} tall, {@code k} 0..1 open: a white core with a blue halo, drawn in two crossed planes. */
    static void slit(Ctx c, Pose p, double k, double length) {
        if (k <= 0 || !c.glow()) return;
        int bands = 10;
        for (int plane = 0; plane < 2; plane++) {
            Vec3 w = plane == 0 ? p.R(1) : p.D(1);
            double scale = plane == 0 ? 1 : .5;
            for (int j = 0; j < bands; j++) {
                double t0 = (double) j / bands, t1 = (double) (j + 1) / bands;
                double w0 = lens(t0) * k * scale, w1 = lens(t1) * k * scale;
                for (int halo = 0; halo < 2; halo++) {
                    double m = halo == 0 ? 1 : 3.2, alpha = (halo == 0 ? .95 : .32) * Math.min(1, k * 1.5);
                    int color = halo == 0 ? HOT : BLUE;
                    for (int side = -1; side <= 1; side += 2) {
                        Vec3 a = p.foot().add(p.U(t0 * length)).add(w.scale(side * w0 * m)), b = p.foot().add(p.U(t0 * length));
                        Vec3 d = p.foot().add(p.U(t1 * length)), e = d.add(w.scale(side * w1 * m));
                        double a0 = alpha * taper(t0), a1 = alpha * taper(t1);
                        GateDraw.gradient(c, a, b, d, e, color, 0, a0, a1, 0);
                    }
                }
            }
        }
        GateDraw.sprite(c, p.at(0, length / 2, 0), (16 * k + 2) * p.s(), .5 * k, HOT);
    }
    private static double lens(double t) { return 3.4 * Math.pow(Math.sin(Math.PI * t), .75); }
    private static double taper(double t) { return Math.pow(Math.sin(Math.PI * clamp01(t)), .5); }

    /** Streaks of white racing up past the gate faster as it falls faster, with specks of dust in the same flow. */
    static void wind(Ctx c, Pose p, double age, double speed) {
        double on = clamp01(speed / .8);
        if (on <= .02 || !c.glow()) return;
        double len = (6 + speed * 14) * p.s();
        for (int i = 0; i < 36; i++) {
            double x = (hash(i, 1) < .5 ? -1 : 1) * (10 + 14 * hash(i, 2)), z = (hash(i, 3) - .5) * 12;
            double span = 150, y = ((hash(i, 4) * span + age * (7 + speed * 9)) % span) - 30;
            Vec3 base = p.at(x, y, z);
            Vec3 look = p.foot().add(p.U(y)).subtract(c.eye);
            Vec3 side = p.u().cross(look);
            if (side.lengthSqr() < 1e-8) continue;
            side = side.normalize().scale((.22 + .25 * hash(i, 5)) * p.s());
            double fade = on * (.18 + .3 * hash(i, 6)) * smooth(Math.min(y + 30, span - 30 - y + 30) / 20);
            Vec3 mid = base.add(p.U(len * .45)), top = base.add(p.U(len));
            GateDraw.gradient(c, base.subtract(side.scale(.3)), base.add(side.scale(.3)), mid.add(side), mid.subtract(side), HOT, 0, 0, fade, fade);
            GateDraw.gradient(c, mid.subtract(side), mid.add(side), top.add(side.scale(.3)), top.subtract(side.scale(.3)), HOT, fade, fade, 0, 0);
        }
        for (int i = 0; i < 24; i++) {
            double x = (hash(i, 11) - .5) * 44, z = (hash(i, 12) - .5) * 14, span = 130;
            double y = ((hash(i, 13) * span + age * (5 + speed * 12)) % span) - 20;
            GateDraw.sprite(c, p.at(x, y, z), (.25 + .45 * hash(i, 14)) * p.s(), on * .55 * smooth(y / 10 + 1) * (1 - clamp01(y / span)), HOT);
        }
    }

    /** Ring of dust puffs thrown outwards when the gate comes to rest (188..210) and, smaller, when the doors stop (285..307). */
    static void dust(Ctx c, Pose p, double age, int t0, double gain) {
        double t = (age - t0) / 22;
        if (t <= 0 || t >= 1 || !c.body()) return;
        for (int i = 0; i < 20; i++) {
            double a = i * TAU / 20 + hash(i, 21) * .3, r = p.s() * (6 + (30 + 8 * hash(i, 22)) * gain * (1 - (1 - t) * (1 - t)));
            Vec3 at = p.foot().add(Math.cos(a) * r, p.s() * (2 + 5 * t * hash(i, 23)), Math.sin(a) * r);
            GateDraw.sprite(c, at, (3.5 + 6 * t) * p.s(), .26 * Math.min(1, gain + .3) * (1 - t) * smooth(t * 8), DUST, GateAtlas.CLOUD, hash(i, 24) * TAU);
        }
    }

    /** Horizontal ring of light that races outwards from the gate at tick {@code t0} (204: the gate lands; 285: the doors meet their stops). */
    static void shockwave(Ctx c, Pose p, double age, int t0, double reach, double height, double gain) {
        double t = (age - t0) / 28;
        if (t <= 0 || t >= 1 || !c.glow()) return;
        double radius = p.s() * (4 + reach * (1 - Math.pow(1 - t, 2.2))), wd = p.s() * (2.5 + 9 * t) * gain, alpha = .8 * gain * Math.pow(1 - t, 1.4);
        Vec3 o = p.foot().add(p.U(height));
        int segs = 48;
        for (int s = 0; s < segs; s++) {
            double a0 = s * TAU / segs, a1 = (s + 1) * TAU / segs;
            Vec3 i0 = ring(o, radius - wd, a0), i1 = ring(o, radius - wd, a1), m0 = ring(o, radius, a0), m1 = ring(o, radius, a1),
                    o0 = ring(o, radius + wd * .45, a0), o1 = ring(o, radius + wd * .45, a1);
            GateDraw.gradient(c, i0, i1, m1, m0, BLUE, 0, 0, alpha, alpha);
            GateDraw.gradient(c, m0, m1, o1, o0, HOT, alpha, alpha, 0, 0);
        }
        if (t < .3) GateDraw.sprite(c, o, (50 * gain * (1 - t / .3) + 8) * p.s(), .7 * gain * (1 - t / .3), HOT);
    }
    private static Vec3 ring(Vec3 o, double r, double a) { return o.add(Math.cos(a) * r, 0, Math.sin(a) * r); }

    /** After the shockwave, rings of pressure leave the gate on a 20 tick beat, and a faint shell of light breathes around it. */
    static void pressure(Ctx c, Pose p, double age, double endK) {
        if (age < SilentGateSequence.SHOCK + 10 || !c.glow()) return;
        double fadeOut = 1 - smooth(endK / .4);
        if (fadeOut <= 0) return;
        Vec3 ctr = p.at(0, 34, 0);
        int last = (int) Math.floor((age - SilentGateSequence.SHOCK - 10) / 20);
        for (int n = Math.max(0, last - 2); n <= last; n++) {
            double life = (age - SilentGateSequence.SHOCK - 10 - 20.0 * n) / 46;
            if (life < 0 || life >= 1) continue;
            double radius = 24 + 42 * life, wd = 1.6 + 2.2 * life, alpha = .32 * (1 - life) * (1 - life) * smooth(life * 10) * fadeOut;
            int segs = 28;
            for (int s = 0; s < segs; s++) {
                double a0 = s * TAU / segs, a1 = (s + 1) * TAU / segs;
                Vec3 i0 = flat(p, ctr, radius - wd, a0), i1 = flat(p, ctr, radius - wd, a1), m0 = flat(p, ctr, radius, a0), m1 = flat(p, ctr, radius, a1),
                        o0 = flat(p, ctr, radius + wd, a0), o1 = flat(p, ctr, radius + wd, a1);
                GateDraw.gradient(c, i0, i1, m1, m0, BLUE, 0, 0, alpha, alpha);
                GateDraw.gradient(c, m0, m1, o1, o0, BLUE, alpha, alpha, 0, 0);
            }
        }
        double beat = .5 + .5 * Math.sin((age - SilentGateSequence.SHOCK - 10) * TAU / 20), shell = smooth((age - SilentGateSequence.SHOCK - 10) / 20) * fadeOut;
        GateDraw.sprite(c, ctr, (58 + 3 * beat) * p.s(), (.1 + .04 * beat) * shell, BLUE);
        GateDraw.sprite(c, ctr, 34 * p.s(), .09 * shell, HOT);
    }
    /** Point on a circle in the gate plane (right/up axes). */
    private static Vec3 flat(Pose p, Vec3 ctr, double r, double a) { return ctr.add(p.R(Math.cos(a) * r)).add(p.U(Math.sin(a) * r)); }

    /** Half width of the visible gap between the two leaves: they hinge on the jambs and swing back, so the gap grows as 1 - cos. */
    static double gap(double angle) { return SilentGateSequence.GATE_INNER_WIDTH / 2.0 * .9 * (1 - Math.cos(angle)); }

    /** A hairline of light between the leaves while they are stuck (at least .3 wide), with a soft halo, flickering; {@code leak} 0..1. */
    static void crack(Ctx c, Pose p, double angle, double leak, double age) {
        if (leak <= 0) return;
        double w = Math.max(.3, gap(angle)) * (1 + .25 * Math.sin(age * .9)), y0 = OPENING_Y0 + 1, y1 = OPENING_Y0 + SilentGateSequence.GATE_INNER_HEIGHT - 1;
        int rows = 8;
        for (int j = 0; j < rows; j++) {
            double ya = y0 + (y1 - y0) * j / rows, yb = y0 + (y1 - y0) * (j + 1) / rows, ta = taper((double) j / rows), tb = taper((j + 1.0) / rows);
            for (int side = -1; side <= 1; side += 2) for (int halo = 0; halo < 2; halo++) {
                double m = halo == 0 ? 1 : 6, al = (halo == 0 ? .95 : .3) * leak;
                GateDraw.gradient(c, p.at(side * w * m, ya, .35), p.at(0, ya, .35), p.at(0, yb, .35), p.at(side * w * m, yb, .35),
                        halo == 0 ? HOT : BLUE, 0, al * ta, al * tb, 0);
            }
        }
    }

    /** The white-blue plane behind the doors, its god rays and a bloom, growing with the opening. */
    static void light(Ctx c, Pose p, double open, double angle, double age) {
        double k = smooth(open * 1.25), leak = smooth((age - SilentGateSequence.OPEN_START - 2) / 14) * (1 - smooth((open - .05) / .3));
        if ((k <= 0.01 && leak <= 0) || !c.glow()) return;
        crack(c, p, angle, leak, age);
        double rays = Math.max(k, .8 * leak);
        double g = gap(angle), top = OPENING_Y0 + SilentGateSequence.GATE_INNER_HEIGHT, mid = (OPENING_Y0 + top) / 2;
        double[] ys = {OPENING_Y0, mid, top}, ya = {.6, 1, .8};
        // Layered glow: a wide cyan plane that falls off to nothing at the jambs (so the leaf edges stay dark and readable),
        // and a soft white core a third as wide, never brighter than .45.
        for (int row = 0; row < 2; row++) for (int side = -1; side <= 1; side += 2) {
            Vec3 a = p.at(side * g, ys[row], .3), b = p.at(0, ys[row], .3), d = p.at(0, ys[row + 1], .3), e = p.at(side * g, ys[row + 1], .3);
            double wide = .62 * k;
            GateDraw.gradient(c, a, b, d, e, BLUE, .08 * k * ya[row], wide * ya[row], wide * ya[row + 1], .08 * k * ya[row + 1]);
            Vec3 a2 = p.at(side * g * .35, ys[row] + 2, .35), d2 = p.at(side * g * .35, ys[row + 1] - 2, .35);
            double core = .45 * k;
            GateDraw.gradient(c, a2, b, d, d2, HOT, 0, core * ya[row], core * ya[row + 1], 0);
        }
        Vec3 aperture = p.at(0, mid, 0);
        GateDraw.sprite(c, aperture, (30 + 12 * k) * p.s(), .12 * k, BLUE);
        GateDraw.sprite(c, aperture, (12 + 6 * k) * p.s(), .1 * k, HOT);
        // Motes of light drifting out of the doorway on both sides.
        for (int i = 0; i < 16; i++) {
            double fwd = i % 2 == 0 ? 1 : -1, life = (hash(i, 41) + age * (.004 + .003 * hash(i, 42))) % 1;
            double x = (hash(i, 43) * 2 - 1) * Math.max(g, 1) * (1 + 2 * life), y = mid + (hash(i, 44) - .5) * 40 + life * 8;
            GateDraw.sprite(c, p.at(x, y, fwd * (1 + 30 * life)), (.5 + .7 * hash(i, 45)) * p.s(), .6 * rays * Math.sin(Math.PI * life), i % 3 == 0 ? BLUE : HOT);
        }
        for (int i = 0; i < 30; i++) {
            double fwd = i % 3 == 0 ? 1 : -1;
            double ox = (hash(i, 31) * 2 - 1) * Math.max(g, 1), oy = mid + (hash(i, 32) - .5) * 38;
            Vec3 o = p.at(ox, oy, .5 * fwd);
            Vec3 dir = p.D(fwd).add(p.R((hash(i, 33) - .5) * .9)).add(p.U((hash(i, 34) - .5) * .5)).normalize();
            Vec3 side = dir.cross(c.eye.subtract(o));
            if (side.lengthSqr() < 1e-8) continue;
            double len = (70 + 60 * hash(i, 35)) * p.s(), w0 = .8 * p.s(), w1 = (5 + 4 * hash(i, 36)) * p.s();
            side = side.normalize();
            double shimmer = .55 + .45 * Math.sin(age * .045 + i * 1.7), al = (fwd < 0 ? .16 : .1) * rays * shimmer;
            Vec3 tip = o.add(dir.scale(len));
            for (int s = -1; s <= 1; s += 2)
                GateDraw.gradient(c, o, o.add(side.scale(s * w0)), tip.add(side.scale(s * w1)), tip, i % 2 == 0 ? HOT : BLUE, al, 0, 0, 0);
        }
    }
}
