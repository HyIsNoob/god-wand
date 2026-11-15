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

    /** The crack in the sky: a lens of light {@code half} blocks to either side of {@code centre} along {@code r}, {@code k} 0..1 open: a white core with a blue halo. */
    static void slit(Ctx c, Vec3 centre, Vec3 r, Vec3 d, double k, double half) {
        if (k <= 0 || !c.glow()) return;
        int bands = 12;
        Vec3 up = new Vec3(0, 1, 0);
        // The crack lies in the great circle's plane across the gate's width (flat, and a low wall of light standing in it); the gate comes down through it.
        for (int plane = 0; plane < 2; plane++) {
            Vec3 w = plane == 0 ? d : up;
            double scale = plane == 0 ? 1 : .5;
            for (int j = 0; j < bands; j++) {
                double t0 = (double) j / bands, t1 = (double) (j + 1) / bands;
                double w0 = lens(t0) * k * scale * 5, w1 = lens(t1) * k * scale * 5;
                for (int halo = 0; halo < 2; halo++) {
                    double m = halo == 0 ? 1 : 3.2, alpha = (halo == 0 ? .95 : .32) * Math.min(1, k * 1.5);
                    int color = halo == 0 ? HOT : BLUE;
                    for (int side = -1; side <= 1; side += 2) {
                        Vec3 b = centre.add(r.scale((2 * t0 - 1) * half)), e0 = centre.add(r.scale((2 * t1 - 1) * half));
                        GateDraw.gradient(c, b.add(w.scale(side * w0 * m)), b, e0, e0.add(w.scale(side * w1 * m)), color, 0, alpha * taper(t0), alpha * taper(t1), 0);
                    }
                }
            }
        }
        // Light falls out of it.
        for (int i = 0; i < 9; i++) {
            Vec3 o = centre.add(r.scale((i / 8.0 * 2 - 1) * half * .8));
            GateDraw.shaft(c, o, o.add(0, -(70 + 50 * hash(i, 181)), 0), 5, 16, i % 2 == 0 ? HOT : BLUE, .3 * k, 0, i * 1.3);
        }
        GateDraw.sprite(c, centre, half * (.5 + .5 * k), .5 * k, HOT);
        GateDraw.sprite(c, centre, half * 1.3, .4 * k, 0xFFFFFF);
        // A sheet of light across the mouth: what comes through is born out of white.
        Vec3 a = centre.add(r.scale(-half * .82)).add(d.scale(-22 * k)), b = centre.add(r.scale(half * .82)).add(d.scale(-22 * k));
        Vec3 e = centre.add(r.scale(half * .82)).add(d.scale(22 * k)), g = centre.add(r.scale(-half * .82)).add(d.scale(22 * k));
        GateDraw.tex(c, a, b, e, g, GateAtlas.GLOW, 0, 1, 0, 1, 0xFFFFFF, k, k, k, k);
    }
    /** The sky strains as the gate is dragged down through the crack (owner, 2026-11-14: "weight, and something to see there"): every lurch of the descent throws
     *  a ring of light out through the great circle's plane, and sparks grind off the lips of the crack and fall past the gate. {@code half}: half the crack's length. */
    static void strain(Ctx c, Vec3 crack, Vec3 r, Vec3 d, double age, double half) {
        double span = SilentGateSequence.DESCENT_END - SilentGateSequence.DESCENT_START, t = age - SilentGateSequence.DESCENT_START;
        if (t <= 0 || t > span + 24 || !c.glow()) return;
        double beat = span / SilentGateSequence.LURCHES;
        for (int n = Math.min(SilentGateSequence.LURCHES - 1, (int) (t / beat)), back = 0; back < 3 && n >= 0; n--, back++) {
            double since = t - n * beat;
            if (since > 24) break;
            double env = .35 + .65 * Math.sin(Math.PI * (n + .5) / SilentGateSequence.LURCHES), grow = 1 - Math.exp(-since / 9);
            GateBeams.ring(c, crack, half * (.5 + 2.4 * grow), half * (.1 + .25 * grow), .55 * env * (1 - since / 24), n % 2 == 0 ? HOT : BLUE, 64);
        }
        double on = clamp01(t / 8) * (1 - clamp01((t - span) / 20));
        for (int i = 0; i < 90; i++) {
            double life = (hash(i, 231) + age * (.022 + .02 * hash(i, 232))) % 1, x = (hash(i, 233) * 2 - 1) * half * .9;
            Vec3 at = crack.add(r.scale(x)).add(d.scale((i % 2 == 0 ? 1 : -1) * (5 + 22 * life * hash(i, 234)))).add(0, -6 - 150 * life * life, 0);
            GateDraw.sprite(c, at, (.8 + 1.6 * hash(i, 235)) * (1 - .5 * life), .9 * on * (1 - life), i % 3 == 0 ? 0xFFFFFF : i % 3 == 1 ? HOT : BLUE, GateAtlas.STAR, age * .15 + i);
        }
    }
    private static double lens(double t) { return 3.4 * Math.pow(Math.sin(Math.PI * t), .75); }
    private static double taper(double t) { return Math.pow(Math.sin(Math.PI * clamp01(t)), .5); }

    /** Streaks of white racing up past the gate faster as it falls faster, with specks of dust in the same flow. */
    /** {@code travel}: design units the gate has fallen so far. What streams past it moves with that, not with age times the speed (which jerked as the speed changed). */
    static void wind(Ctx c, Pose p, double age, double speed, double travel) {
        // The vapour hangs on for a few seconds after the gate has stopped.
        double on = Math.max(clamp01(speed / .8), age > SilentGateSequence.DESCENT_START + 8 ? .7 * (1 - smooth((age - SilentGateSequence.DESCENT_END) / 60)) : 0);
        if (on <= .02 || !c.glow()) return;
        // Streamers of vapour torn off the falling gate (reference 1): long, pointed, leaning, sliding up past it.
        for (int i = 0; i < 18; i++) {
            double x = (hash(i, 1) < .5 ? -1 : 1) * (4 + 16 * hash(i, 2)), z = (hash(i, 3) - .5) * 14 - 2, span = 120;
            double y = ((hash(i, 4) * span + age * 1.2 + travel * 1.5) % span) - 20, len = 34 + 30 * hash(i, 5), lean = (hash(i, 6) - .5) * 14;
            double fade = on * (.4 + .3 * hash(i, 7)) * smooth(Math.min(y + 20, span - 20 - y) / 18);
            GateDraw.streamer(c, p.at(x, y, z), p.at(x + lean, y + len, z + lean * .3), (1.1 + 1.2 * hash(i, 8)) * p.s(), 0xFFFFFF, fade, (hash(i, 9) - .5) * 3);
        }
        // The air it tears through: a storm of specks streaming up past the falling gate.
        // And what its weight throws out when it stops: a ring of sparks flung from under it, slowing as they go.
        double stop = age - SilentGateSequence.DESCENT_END;
        if (stop > 0 && stop < 50) for (int i = 0; i < 140; i++) {
            double a = hash(i, 211) * TAU, r = (12 + 70 * hash(i, 212)) * (1 - Math.exp(-stop / 12)), up = (hash(i, 213) - .3) * 26 * (1 - Math.exp(-stop / 9)) - .01 * stop * stop;
            GateDraw.sprite(c, p.at(Math.cos(a) * r, -4 + up, Math.sin(a) * r * .5), (.2 + .4 * hash(i, 214)) * p.s(), .9 * (1 - stop / 50), i % 3 == 0 ? 0xFFFFFF : BLUE, GateAtlas.STAR, stop * .1 + i);
        }
        for (int i = 0; i < 150; i++) {
            double x = (hash(i, 11) - .5) * 52, z = (hash(i, 12) - .5) * 20, span = 130;
            double y = ((hash(i, 13) * span + age * 2 + travel * 2.4) % span) - 20;
            GateDraw.sprite(c, p.at(x, y, z), (.12 + .3 * hash(i, 14)) * p.s(), on * .6 * smooth(y / 10 + 1) * (1 - clamp01(y / span)), i % 3 == 0 ? 0xFFFFFF : HOT, i % 4 == 0 ? GateAtlas.STAR : GateAtlas.GLOW, i);
        }
    }

    /** Ring of dust puffs thrown outwards when the gate comes to rest (188..210) and, smaller, when the doors stop (285..307). */
    static void dust(Ctx c, Pose p, double age, int t0, double gain) {
        double t = (age - t0) / 22;
        if (t <= 0 || t >= 1 || !c.glow() && !c.shade()) return;
        for (int i = 0; i < 36; i++) {
            // Two rings of puffs thrown outwards, the second slower and higher, each puff rolling as it goes.
            int ring = i % 2;
            double a = i * TAU / 36 + hash(i, 21) * .3, r = p.s() * (6 + (30 + 8 * hash(i, 22)) * gain * (1 - .35 * ring) * (1 - (1 - t) * (1 - t)));
            Vec3 at = p.foot().add(Math.cos(a) * r, p.s() * (2 + (5 + 6 * ring) * t * hash(i, 23)), Math.sin(a) * r);
            double size = (3.5 + 7 * t) * p.s(), al = Math.min(1, gain + .3) * (1 - t) * smooth(t * 8), roll = hash(i, 24) * TAU + t * (1.2 + hash(i, 25));
            if (c.shade()) GateDraw.sprite(c, at, size * 1.1, .5 * al, 0xFFFFFF, GateAtlas.SMOKE, roll);
            if (c.glow()) GateDraw.sprite(c, at, size, .16 * al, DUST, GateAtlas.SMOKE, roll);
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

    static final int GOLD = 0xFFE9B0;

    /** Divine light once the gate stands ({@code k} 0..1): a nimbus behind it, shafts falling from the great circle at {@code circleY} to the ground at {@code groundY},
     *  and slow motes drifting down around the gate and the caster. */
    static void grace(Ctx c, Pose p, Vec3 caster, double circleY, double groundY, double age, double k) {
        if (k <= 0 || !c.glow()) return;
        // Nimbus: a thin still ring in the gate's plane, just behind it, with fine rays that turn very slowly.
        Vec3 ctr = p.at(0, 37, 1.5);
        double r = 31, a = .5 * k;
        int segs = 48;
        for (int s = 0; s < segs; s++) {
            double a0 = s * TAU / segs, a1 = (s + 1) * TAU / segs;
            GateDraw.gradient(c, flat(p, ctr, r - .9, a0), flat(p, ctr, r - .9, a1), flat(p, ctr, r, a1), flat(p, ctr, r, a0), GOLD, 0, 0, a, a);
            GateDraw.gradient(c, flat(p, ctr, r, a0), flat(p, ctr, r, a1), flat(p, ctr, r + .9, a1), flat(p, ctr, r + .9, a0), GOLD, a, a, 0, 0);
        }
        for (int i = 0; i < 36; i++) {
            double ang = i * TAU / 36 + age * .0015, w = .011, r1 = r + 1.8, r2 = r1 + 3 + 3.5 * (i % 3), al = (.22 + .12 * Math.sin(age * .05 + i * 2.1)) * k;
            GateDraw.gradient(c, flat(p, ctr, r1, ang - w), flat(p, ctr, r1, ang + w), flat(p, ctr, r2, ang + w * .3), flat(p, ctr, r2, ang - w * .3), i % 3 == 0 ? HOT : GOLD, al, al, 0, 0);
        }
        GateDraw.sprite(c, ctr, 40 * p.s(), .1 * k, GOLD);
        // Shafts of light from the great circle to the ground, camera-facing, bright at the top and gone at the ground, each breathing on its own.
        Vec3 axis = new Vec3(p.foot().x, 0, p.foot().z);
        for (int i = 0; i < 12; i++) {
            double ang = hash(i, 51) * TAU + age * .0006, rad = (8 + 44 * hash(i, 52)) * p.s(), half = (2 + 3 * hash(i, 53)) * p.s();
            Vec3 top = new Vec3(axis.x + Math.cos(ang) * rad, circleY, axis.z + Math.sin(ang) * rad), base = new Vec3(top.x, groundY, top.z);
            double al = (.16 + .1 * hash(i, 54)) * (.6 + .4 * Math.sin(age * .021 + i * 1.3)) * k;
            GateDraw.shaft(c, top, base, half * .6, half * 1.5, i % 3 == 0 ? GOLD : HOT, al, 0, age * .009 + i * 2.3);
        }
        // Motes: glints sinking slowly through the air round the gate (large, seen from afar) and round the caster (small, close).
        double gateTop = p.foot().y + 70 * p.s();
        for (int i = 0; i < 72; i++) {
            boolean near = i >= 48;
            double ang = hash(i, 61) * TAU, rad = near ? 3 + 13 * hash(i, 62) : 30 * p.s() * Math.sqrt(hash(i, 62)), life = (hash(i, 63) + age * (near ? .004 : .0022) * (.6 + .8 * hash(i, 64))) % 1;
            double y = near ? caster.y + 11 - 11 * life : gateTop - (gateTop - groundY) * life, sway = Math.sin(age * .03 + i) * (near ? .4 : 2.5);
            Vec3 at = near ? new Vec3(caster.x + Math.cos(ang) * rad + sway, y, caster.z + Math.sin(ang) * rad) : new Vec3(axis.x + Math.cos(ang) * rad + sway, y, axis.z + Math.sin(ang) * rad);
            double twinkle = .55 + .45 * Math.sin(age * .17 + i * 3.7);
            GateDraw.sprite(c, at, (near ? .16 + .14 * hash(i, 65) : 1.3 + 1.5 * hash(i, 65)), .8 * k * twinkle * Math.sin(Math.PI * life), i % 4 == 0 ? GOLD : HOT, GateAtlas.STAR);
        }
    }

    /** Smoke of the gate itself. While it comes down, wisps are torn up along its columns; once it stands, a slow mist hangs under it; and when the doors open, a bank of
     *  luminous fog pours out of the doorway toward the caster and sinks. Dark where it is thick (shade), lit by the gate (glow). */
    static void smoke(Ctx c, Pose p, double age, double speed, double travel, double open, double endK) {
        if (!c.glow() && !c.shade()) return;
        double fall = clamp01(speed / .8);
        if (fall > .02) for (int i = 0; i < 28; i++) {
            double x = (hash(i, 71) < .5 ? -1 : 1) * (11 + 5 * hash(i, 72)), z = (hash(i, 73) - .5) * 9, span = 110;
            double y = (hash(i, 74) * span + age * 1.5 + travel * 1.3) % span, k = fall * Math.sin(Math.PI * y / span);
            Vec3 a = p.at(x, y, z), b = p.at(x * (1 + .15 * y / span), y + 16 + 10 * hash(i, 75), z);
            wispQuad(c, a, b, (2.6 + 2 * hash(i, 76)) * p.s(), k, i);
        }
        double stand = smooth((age - SilentGateSequence.SHOCK) / 40) * (1 - smooth(endK / .5));
        if (stand > 0) for (int i = 0; i < 22; i++) {
            double life = (hash(i, 81) + age * (.0016 + .001 * hash(i, 82))) % 1, a = hash(i, 83) * TAU + age * .002, r = (10 + 26 * hash(i, 84)) * p.s();
            Vec3 at = p.foot().add(Math.cos(a) * r, (-9 + 5 * hash(i, 85) - 6 * life) * p.s(), Math.sin(a) * r * .5);
            double size = (9 + 8 * hash(i, 86)) * p.s(), al = stand * Math.sin(Math.PI * life), roll = hash(i, 87) * TAU + age * .003;
            if (c.shade()) GateDraw.sprite(c, at, size, .32 * al, 0xFFFFFF, GateAtlas.SMOKE, roll);
            if (c.glow()) GateDraw.sprite(c, at, size * .9, .07 * al, BLUE, GateAtlas.SMOKE, roll + 1);
        }
        double pour = smooth(open * 1.4) * (1 - smooth(endK / .4));
        if (pour > 0 && c.glow()) for (int i = 0; i < 30; i++) {
            double life = (hash(i, 91) + age * (.005 + .004 * hash(i, 92))) % 1, x = (hash(i, 93) * 2 - 1) * gap(open * 1.45) * (1 + 1.5 * life);
            double y = OPENING_Y0 + SilentGateSequence.GATE_INNER_HEIGHT * (.1 + .8 * hash(i, 94)) - 26 * life * life, z = -2 - 46 * life;
            GateDraw.sprite(c, p.at(x, y, z), (5 + 13 * life) * p.s(), .16 * pour * Math.sin(Math.PI * Math.pow(life, .7)), i % 3 == 0 ? HOT : BLUE, GateAtlas.SMOKE, hash(i, 95) * TAU + age * .004);
        }
    }
    /** Feathers of light shed by the wings: a flurry thrown out as they burst open, then a slow fall of them under the spread wings for as long as they are open. */
    static void feathers(Ctx c, Pose p, double age, double spread, double endK) {
        if (!c.glow() || spread <= 0) return;
        double burst = age - SilentGateSequence.SHOCK;
        if (burst > 0 && burst < 80) for (int i = 0; i < 40; i++) {
            int side = i % 2 == 0 ? 1 : -1;
            double a = (-.3 + 1.5 * hash(i, 151)) * side, reach = (20 + 44 * hash(i, 152)) * (1 - Math.exp(-burst / 16));
            Vec3 at = p.at(side * 15 + Math.sin(a) * reach, 54 + Math.cos(a) * reach * .8 - .006 * burst * burst, 3 + (hash(i, 153) - .5) * 14);
            GateDraw.sprite(c, at, (1.6 + 1.6 * hash(i, 154)) * p.s(), .8 * (1 - burst / 80), i % 3 == 0 ? BLUE : 0xFFFFFF, GateAtlas.FEATHER, hash(i, 155) * TAU + burst * .05 * side);
        }
        double fall = Math.min(1, spread) * (1 - smooth(endK / .5));
        if (fall > 0) for (int i = 0; i < 44; i++) {
            int side = i % 2 == 0 ? 1 : -1;
            double life = (hash(i, 161) + age * (.0035 + .002 * hash(i, 162))) % 1, sway = Math.sin(life * 11 + i);
            Vec3 at = p.at(side * (15 + 36 * hash(i, 163)) + 5 * sway, 76 * hash(i, 164) + 8 - 70 * life, 3 + (hash(i, 165) - .5) * 20 - 14 * life);
            GateDraw.sprite(c, at, (1.3 + 1.3 * hash(i, 166)) * p.s(), .55 * fall * Math.sin(Math.PI * life), i % 4 == 0 ? BLUE : 0xFFFFFF, GateAtlas.FEATHER, 1.2 * sway + i);
        }
    }

    /** The blow of the wings opening runs out over the land: a ring of light along the ground from under the gate, {@code t} ticks after the shockwave, reaching far
     *  past the caster, with a low wall of dust in its wake. */
    static void groundWave(Ctx c, Vec3 centre, double t) {
        if (t <= 0 || t > 70) return;
        double radius = 16 * t, fade = 1 - t / 70;
        if (c.glow()) {
            GateBeams.ring(c, centre.add(0, 1.5, 0), radius, 26, .7 * fade, HOT, 72);
            GateBeams.ring(c, centre.add(0, 1.5, 0), radius * .93, 60, .25 * fade, BLUE, 72);
        }
        for (int i = 0; i < 40; i++) {
            double a = i * TAU / 40 + hash(i, 191), r = radius * (.9 - .12 * hash(i, 192)), size = 14 + 18 * hash(i, 193);
            Vec3 at = centre.add(Math.cos(a) * r, 5 + 8 * hash(i, 194), Math.sin(a) * r);
            if (c.shade()) GateDraw.sprite(c, at, size, .3 * fade, 0xFFFFFF, GateAtlas.SMOKE, a);
            if (c.glow()) GateDraw.sprite(c, at, size * .9, .08 * fade, DUST, GateAtlas.SMOKE, a + 1);
        }
    }

    /** A click made visible (owner, 2026-11-14: "it starts with nothing"): rings of light run out along the ground from the caster, a column of light stands for
     *  an instant, sparks fly up. {@code t} ticks since the click. */
    static void castBurst(Ctx c, Vec3 feet, double t, double scale) {
        if (t <= 0 || t > 30 || !c.glow()) return;
        double fade = 1 - t / 30, nMin = c.nearMin, nMax = c.nearMax;
        c.nearMin = .4; c.nearMax = 1.8;
        try {
            for (int k = 0; k < 2; k++) {
                double tt = t - 5 * k;
                if (tt > 0) GateBeams.ring(c, feet.add(0, .15, 0), (1 + 1.3 * tt) * scale, (1.2 + .12 * tt) * scale, .8 * fade * (k == 0 ? 1 : .6), k == 0 ? 0xFFFFFF : BLUE, 48);
            }
            // Spokes of light along the ground, and a fountain of sparks: no pillar (the click itself is an impact frame).
            for (int i = 0; i < 18; i++) {
                double a = i * TAU / 18 + hash(i, 205), r0 = (.6 + 1.1 * t) * scale, r1 = r0 + (2.5 + 3 * hash(i, 206)) * scale, w = .16 * scale;
                Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a)), side = new Vec3(-dir.z, 0, dir.x).scale(w), p0 = feet.add(dir.scale(r0)).add(0, .12, 0), p1 = feet.add(dir.scale(r1)).add(0, .12, 0);
                GateDraw.gradient(c, p0.subtract(side), p0.add(side), p1.add(side.scale(.3)), p1.subtract(side.scale(.3)), HOT, .8 * fade, .8 * fade, 0, 0);
            }
            for (int i = 0; i < 130; i++) {
                double a = hash(i, 201) * TAU, sp = (.25 + .5 * hash(i, 202)) * scale, up = .2 + .55 * hash(i, 203);
                Vec3 at = feet.add(Math.cos(a) * sp * t, .2 + up * t * scale - .012 * t * t, Math.sin(a) * sp * t);
                GateDraw.sprite(c, at, (.06 + .08 * hash(i, 204)) * scale, .9 * fade, i % 3 == 0 ? 0xFFFFFF : BLUE, GateAtlas.STAR, t * .2 + i);
            }
        } finally { c.nearMin = nMin; c.nearMax = nMax; }
    }

    /** Motes of light in the air round the camera, wherever it is: a box of them that wraps as the camera moves, each rising and twinkling. {@code k} 0..1. */
    static void motes(Ctx c, double age, double k) {
        if (k <= 0 || !c.glow()) return;
        double box = 44, nMin = c.nearMin, nMax = c.nearMax;
        c.nearMin = 1.5; c.nearMax = 5;
        try {
            for (int i = 0; i < 170; i++) {
                double x = wrap(hash(i, 141) * box + .6 * Math.sin(age * .011 + i) - c.eye.x, box), y = wrap(hash(i, 142) * box + age * (.012 + .02 * hash(i, 143)) - c.eye.y, box),
                        z = wrap(hash(i, 144) * box + .6 * Math.cos(age * .009 + i * 1.3) - c.eye.z, box);
                // Fading toward the faces of the box, so a mote that wraps never pops.
                double edge = 1 - smooth((Math.sqrt(x * x + y * y + z * z) - box * .32) / (box * .16)), twinkle = .5 + .5 * Math.sin(age * (.06 + .05 * hash(i, 145)) + i * 2.1);
                GateDraw.sprite(c, c.eye.add(x, y, z), .1 + .16 * hash(i, 146), .85 * k * edge * twinkle, i % 4 == 0 ? 0xFFE9B0 : i % 2 == 0 ? HOT : BLUE,
                        i % 3 == 0 ? GateAtlas.STAR : GateAtlas.GLOW, age * .02 + i);
                if (i % 2 == 0) GateDraw.halo(c, c.eye.add(x, y, z), .6 + .5 * hash(i, 147), .3 * k * edge * twinkle, i % 4 == 0 ? 0xFFE9B0 : HOT);
            }
        } finally { c.nearMin = nMin; c.nearMax = nMax; }
    }
    private static double wrap(double v, double b) { return v - Math.floor(v / b + .5) * b; }

    /** One wisp of smoke from {@code a} to {@code b}, camera-facing. */
    private static void wispQuad(Ctx c, Vec3 a, Vec3 b, double half, double k, int i) {
        Vec3 side = b.subtract(a).cross(a.subtract(c.eye));
        if (side.lengthSqr() < 1e-8 || k <= 0) return;
        side = side.normalize().scale(half);
        if (c.shade()) GateDraw.tex(c, a.subtract(side), b.subtract(side), b.add(side), a.add(side), GateAtlas.WISP, 0, 1, 0, 1, 0xFFFFFF, .4 * k, .4 * k, .4 * k, .4 * k);
        if (c.glow()) GateDraw.tex(c, a.subtract(side), b.subtract(side), b.add(side), a.add(side), GateAtlas.WISP, 0, 1, 0, 1, i % 2 == 0 ? HOT : BLUE, .14 * k, .14 * k, .14 * k, .14 * k);
    }

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
        double settle = age > SilentGateSequence.OPEN_END ? .38 + .62 * Math.exp(-(age - SilentGateSequence.OPEN_END) / 30) : 1;
        double k = smooth(open * 1.25) * settle, leak = smooth((age - SilentGateSequence.OPEN_START - 2) / 14) * (1 - smooth((open - .05) / .3));
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
            // Inset only at the sill and the lintel: an inset where the two rows meet notched the core into a bow-tie.
            Vec3 a2 = p.at(side * g * .35, ys[row] + (row == 0 ? 2 : 0), .35), d2 = p.at(side * g * .35, ys[row + 1] - (row == 1 ? 2 : 0), .35);
            double core = .45 * k;
            GateDraw.gradient(c, a2, b, d, d2, HOT, 0, core * ya[row], core * ya[row + 1], 0);
        }
        Vec3 aperture = p.at(0, mid, 0);
        // The glare of the doorway: it is brighter than anything in the sky and spills far past the frame.
        GateDraw.sprite(c, aperture, (46 + 14 * k) * p.s(), .3 * k, BLUE);
        GateDraw.sprite(c, aperture, (24 + 8 * k) * p.s(), .34 * k, HOT);
        GateDraw.sprite(c, aperture, 90 * p.s(), .12 * k, BLUE);
        // The flood (reference 5): the light does not stay in the doorway, it bursts out over the whole face of the gate, and streamers of it fly outward.
        GateDraw.sprite(c, p.at(0, mid, -3), (34 + 26 * k) * p.s(), .75 * k, 0xFFFFFF);
        GateDraw.sprite(c, p.at(0, mid, -3), (60 + 20 * k) * p.s(), .4 * k, HOT);
        for (int i = 0; i < 30; i++) {
            double life = (hash(i, 171) + age * (.012 + .012 * hash(i, 172))) % 1, a = hash(i, 173) * TAU, r0 = 6 + 52 * life, len = 14 + 24 * hash(i, 174);
            double zz = -3 - 30 * life * (.4 + .6 * hash(i, 175));
            GateDraw.streamer(c, p.at(Math.cos(a) * r0, mid + Math.sin(a) * r0 * 1.2, zz), p.at(Math.cos(a) * (r0 + len), mid + Math.sin(a) * (r0 + len) * 1.2, zz - 6),
                    (1 + 1.4 * hash(i, 176)) * p.s(), i % 3 == 0 ? HOT : 0xFFFFFF, .7 * k * Math.sin(Math.PI * life), (hash(i, 177) - .5) * 2);
        }
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
            // A ray aimed down the lens would show as a flat bow-tie: it fades out as it turns toward (or away from) the camera.
            double facing = Math.abs(dir.dot(c.eye.subtract(o).normalize()));
            double shimmer = .55 + .45 * Math.sin(age * .045 + i * 1.7), al = (fwd < 0 ? .24 : .15) * rays * shimmer * (1 - smooth((facing - .75) / .2));
            GateDraw.shaft(c, o, o.add(dir.scale(len)), w0, w1, i % 2 == 0 ? HOT : BLUE, 1.6 * al, 0, age * .013 + i * 1.1);
        }
    }
}
