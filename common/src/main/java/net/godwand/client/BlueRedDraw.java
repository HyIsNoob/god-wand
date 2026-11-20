package net.godwand.client;

import net.godwand.skill.BlueRedRules;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static net.godwand.client.GateDraw.*;

/**
 * Geometry of Blue, Red and Purple (owner references in docs/ideas/refs-1.6.0). Per-pixel looks come from the godwand_bluered shader (BlueRedShader);
 * this class decides shapes, paths and motion. Each effect has its own motion language (owner, 2026-11-16 polish brief):
 *  - Blue pulls: irregular tendrils winding round it, debris falling in and accelerating, a rim that is never a clean circle.
 *  - Red repels: long sharp brush strokes of red and ink flung outward on bent paths, never on concentric ellipses.
 *  - Purple compresses: warped vortex bands at different speeds, lightning that crawls continuously, a breathing core, continuous phases.
 * Everything that varies is a function of (seed, time) through smooth value noise or seeded per-cycle parameters, so nothing is regenerated per frame.
 */
final class BlueRedDraw {
    static final int DEEP_BLUE = 0x0C24E8, ROYAL = 0x1E46FF, INDIGO = 0x0A12B4, CYAN = 0x46D2FF, ICE = 0xEAF8FF, SMOKE_WHITE = 0xE4EEFA;
    static final int RED = 0xFF0A28, RED_BODY = 0xD4001A, RED_DEEP = 0x8A0010, RED_LIGHT = 0xFF4A60, RED_CORE = 0xFFD6DC, RED_DARK = 0x16000A;
    /** The merge's shockwave (owner, 2026-11-19: white when Red meets Blue; Red's own are red, Blue's blue). */
    static final int SHOCK_WHITE = 0xFFF4F4;
    static final int VIOLET = 0x9A2CFF, ORCHID = 0xF064FF, PALE = 0xF6D8FF, NIGHT = 0x2A0650, VOID = 0x07020F, INK = 0x050002;
    /** Shader effects (godwand_bluered.fsh). ORB seeds: variant (0 blue, 1 red, 2 purple) + 4 * level. */
    static final int ORB = 0, FLOW = 1, FLAME = 2, SMOKE = 3, SHOCK = 4, BOLT = 5, SPARK = 6, RAYS = 7, NEBULA = 8, SPEEDLINES = 9, DEBRIS = 10, RING = 11, BRUSH = 12, PLASMA = 13, CHALK = 14, GALAXY = 15, SOLID_FX = 16;
    static final int BLUE_TINT = 0x1A6CFF;

    private BlueRedDraw() {}

    /** The ground height under the Purple being drawn (set by the renderer each frame; NaN when unknown, e.g. in tests). */
    static double groundY = Double.NaN;
    /** Ground height at (x, z), set by the renderer while it draws (null in tests). */
    static java.util.function.DoubleBinaryOperator groundAt = null;

    /** Coloured light cast on the map (owner, 2026-11-18): a soft pool of {@code color} on the ground under {@code orb}, fading with height,
     *  and a wide faint wash round the orb itself, so the world near Blue turns bluish and near Red reddish. Glow pass only. */
    static void mapLight(Ctx c, Vec3 orb, double radius, int color, double alpha) {
        if (!c.glow() || alpha <= 0) return;
        glow(c, orb, radius, .12 * alpha, color);
        if (Double.isNaN(groundY)) return;
        double y = groundY + .06, h = orb.y - y;
        if (h < -1 || h > 30) return;
        double a = alpha * (1 - Math.max(0, h) / 30), R = radius * (1 + h / 20);
        GateDraw.tex(c, new Vec3(orb.x - R, y, orb.z - R), new Vec3(orb.x + R, y, orb.z - R), new Vec3(orb.x + R, y, orb.z + R), new Vec3(orb.x - R, y, orb.z + R),
                BlueRedAtlas.GLOW, 0, 1, 0, 1, color, a, a, a, a);
    }

    // ---- deterministic noise ----

    /** With impact frames turned off (the player's flash setting), the big white flashes are dimmed too (owner, 2026-11-20). */
    static double flashScale() { return net.godwand.config.GodWandConfig.CLIENT.impactFrames ? 1 : .3; }

    static double hash(long n) {
        n = (n ^ (n >>> 33)) * 0xff51afd7ed558ccdL;
        n = (n ^ (n >>> 33)) * 0xc4ceb9fe1a85ec53L;
        n ^= n >>> 33;
        return (n >>> 11) * 0x1.0p-53;
    }

    /** Smooth 1D value noise in 0..1: the same (seed, x) always gives the same value, and nearby x give nearby values. */
    static double vnoise(long seed, double x) {
        long i = (long) Math.floor(x);
        double f = x - i;
        f = f * f * (3 - 2 * f);
        return hash(seed * 0x9E3779B97F4A7C15L + i) * (1 - f) + hash(seed * 0x9E3779B97F4A7C15L + i + 1) * f;
    }

    static double snoise(long seed, double x) { return vnoise(seed, x) * 2 - 1; }

    static Vec3 nvec(long seed, double x) { return new Vec3(snoise(seed, x), snoise(seed + 7, x), snoise(seed + 13, x)); }

    // ---- Blue: a source of pull ----

    static void blue(Ctx c, Vec3 at, boolean ready, double age, double sinceReady) { blue(c, at, ready, age, sinceReady, List.of(), age, -1); }

    /** Motes drawn in from farther out (3 to 6 radii), spiralling into the sphere, quicker as they near; glow pass. */
    static void inflow(Ctx c, Vec3 at, double r, double age, int count, int colA, int colB, long seed, double alpha) {
        if (!c.glow() || alpha <= 0) return;
        // Smaller, brighter and a few more (owner, 2026-11-20): every other one has a white-hot centre.
        for (int i = 0; i < count * 7 / 5; i++) {
            long sd = seed + i * 41L;
            double life = 14 + 10 * hash(sd), ph = age / life + hash(sd + 1);
            long cyc = (long) Math.floor(ph);
            double u = ph - cyc;
            long cs = sd + cyc * 7919L;
            Vec3 dir = new Vec3(hash(cs) * 2 - 1, hash(cs + 1) * 2 - 1, hash(cs + 2) * 2 - 1).normalize();
            double r0 = r * (3 + 3 * hash(cs + 3)), rad = r0 - (r0 - r * .8) * u * u, turn = 2.5 * u * u * (hash(cs + 4) < .5 ? 1 : -1);
            Vec3 q = at.add(new Vec3(dir.x * Math.cos(turn) - dir.z * Math.sin(turn), dir.y, dir.x * Math.sin(turn) + dir.z * Math.cos(turn)).scale(rad));
            double size = (.035 + .04 * hash(cs + 5)) * Math.max(.6, r), a = smooth(u / .15) * (1 - smooth((u - .85) / .15)) * alpha;
            glow(c, q, size, a, i % 2 == 0 ? colA : colB);
            if (i % 2 == 0) glow(c, q, size * .45, a, 0xFFFFFF);
        }
    }

    /** The blur a mob leaves while Blue draws it in (owner, 2026-11-20): a smear along its last few ticks, thin and faint at the old end,
     *  as wide as the mob at the new one; only when it is really moving. */
    static void pullBlur(Ctx c, Vec3[] pts, double width, int seed) {
        double travelled = 0;
        for (int i = 1; i < pts.length; i++) travelled += pts[i].distanceTo(pts[i - 1]);
        double a = clamp01((travelled - .6) / 2.5);
        if (a <= 0) return;
        int n = pts.length;
        double[] w = new double[n], al = new double[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1);
            w[i] = width * .55 * t;
            al[i] = t * t * nearFade(c, pts[i]);
        }
        if (c.body()) fxRibbon(c, pts, w, al, SMOKE, seed % 977, 0x5A8CFF, .55 * a);
        else {
            double[] core = new double[n];
            for (int i = 0; i < n; i++) core[i] = w[i] * .35;
            fxRibbon(c, pts, core, al, FLOW, seed % 977 + 1, CYAN, .65 * a);
        }
    }

    /** A puff of dust Blue shed: it stays where it was shed (drifting a little), so a moving Blue leaves a trail of them. */
    record Puff(Vec3 pos, Vec3 drift, double born, int seed) {}
    static final double PUFF_LIFE = 18, VANISH_TICKS = 12;

    /** Blue (owner, 2026-11-19): a heavy, clear blue sphere with a bright core that is always pouring deep-blue dust off its skin. The dust it
     *  sheds ({@code puffs}, world-space) stays behind, so its trail is simply the dust it left. It forms with an overshoot, a ring of pressure
     *  and dust gathering in; {@code vanishAge} >= 0: it is going, everything sucked back into it, shrinking, and a last pop. */
    static void blue(Ctx c, Vec3 at, boolean ready, double age, double sinceReady, List<Puff> puffs, double now, double vanishAge) {
        double r = BlueRedVisual.blueRadius(ready ? BlueRedRules.BlueState.READY : BlueRedRules.BlueState.CHARGING, age);
        double beat = ready ? BlueRedVisual.heartbeat(age) : 0, flash = BlueRedVisual.flash(sinceReady);
        double appear = smooth(age / 8), gone = vanishAge < 0 ? 0 : clamp01(vanishAge / VANISH_TICKS);
        double t = clamp01(age / 10) - 1, grow = 1 + 2.7 * t * t * t + 1.7 * t * t;
        double rr = r * grow * (1 - Math.pow(gone, 1.6)), live = appear * (1 - gone);
        if (c.body()) {
            for (Puff p : puffs) {
                double life = now - p.born(), k = life / PUFF_LIFE;
                if (k < 0 || k >= 1) continue;
                Vec3 q = p.pos().add(p.drift().scale(life));
                if (gone > 0) q = q.add(at.subtract(q).scale(.9 * smooth(gone)));
                double a = smooth(k / .12) * Math.pow(1 - k, 1.3) * .55 * (1 - .5 * gone);
                int col = p.seed() % 3 == 0 ? DEEP_BLUE : p.seed() % 3 == 1 ? INDIGO : ROYAL;
                fxSprite(c, q, r * (.35 + .85 * k) * (1 - .6 * gone), SMOKE, p.seed() % 977, col, a, k * 2 + p.seed());
            }
            // Its own breath close to the skin, always there even standing still.
            for (int i = 0; i < 14; i++) {
                long sd = 7400 + i * 61L;
                double life = 9 + 4 * hash(sd), ph = age / life + hash(sd + 1);
                long cyc = (long) Math.floor(ph);
                double u = ph - cyc;
                long cs = sd + cyc * 7919L;
                Vec3 dir = new Vec3(hash(cs) * 2 - 1, hash(cs + 1) * 2 - 1, hash(cs + 2) * 2 - 1).normalize();
                fxSprite(c, at.add(dir.scale(rr * (.75 + .5 * u))), r * (.3 + .25 * u), SMOKE, (int) (cs % 991), i % 2 == 0 ? DEEP_BLUE : ROYAL,
                        smooth(u / .2) * (1 - u) * .5 * live, age * .04 + i);
            }
            // Its own texture rushes into its centre (owner, 2026-11-19: it looked static): level + 4.
            if (rr > .02) orb(c, at, rr * .78, 0, (ready ? 1 : 0) + 4, BLUE_TINT, appear);
            // Forming: dust gathering in from all round.
            if (age < 12) for (int i = 0; i < 18; i++) {
                Vec3 d = nvec(7500 + i, 0).normalize();
                double k = smooth(age / 10);
                fxSprite(c, at.add(d.scale(r * (4 - 3.2 * k))), r * .4, SMOKE, 7510 + i, i % 2 == 0 ? ROYAL : DEEP_BLUE, .6 * (1 - age / 12), i);
            }
        }
        if (!c.glow()) return;
        // Drawn in (owner, 2026-11-19): blue motes from farther out spiralling into it, quicker as they near, and thin streaks pulled in.
        if (gone <= 0) {
            inflow(c, at, r, age, 80, CYAN, ROYAL, 7600, live);
            streaks(c, at, r * .85, r * 5, age, 48, CYAN, 7650, 1.4, .5 * live, true);
        }
        glow(c, at, rr * .5, .9 * live, 0xFFFFFF);
        glow(c, at, rr * 1.0, (.5 + .2 * beat) * live, CYAN);
        glow(c, at, rr * 2.1, (.2 + .1 * beat) * live, ROYAL);
        if (flash > 0) { double f = flash * flashScale(); glow(c, at, r * 6, f, 0xFFFFFF); glow(c, at, r * 2.5, f, 0xFFFFFF); }
        if (age < 12) {
            double k = age / 12;
            fxSprite(c, at, r * (1 + 3 * k), SHOCK, 7520, CYAN, .8 * (1 - k), 0);
            glow(c, at, r * 2 * (1 - k) + .5, 1 - k, ICE);
            sparks(c, at, r * (1 + 2 * k), age, (int) (30 * (1 - k)), ICE, 7530);
        }
        if (gone > 0) {
            // Sucked into itself, then a last pop.
            streaks(c, at, rr * .3, r * (4 - 2 * gone), age, (int) (70 * (1 - gone)), CYAN, 7540, 2.4, 1 - gone * .5, true);
            glow(c, at, r * (1.6 - gone), 2.4 * gone * (1 - gone), ICE);
            if (gone > .8) {
                double k = (gone - .8) / .2;
                fxSprite(c, at, r * (.5 + 3 * k), SHOCK, 7550, ICE, 1 - k, 0);
                glow(c, at, r * 1.5 * (1 - k) + .3, 1 - k, 0xFFFFFF);
            }
        }
    }

    /** One of the twin orbs of the merge (owner, 2026-11-18): the sphere, and brush strokes of its own colours born on its surface. At rest they
     *  radiate out all round it, one after another; moving, they are swept back behind it by its inertia, longer the faster it goes. */
    static void twinOrb(Ctx c, Vec3 at, double r, double age, Palette p, Vec3 vel, long seedOff) { twinOrb(c, at, r, age, p, vel, seedOff, 0); }

    /** As above; {@code surge} (0..1, the face-off): more and stronger energy pouring out, pulses, and lightning of its colour round it. */
    static void twinOrb(Ctx c, Vec3 at, double r, double age, Palette p, Vec3 vel, long seedOff, double surge) {
        // Owner, 2026-11-18: a dusty smear of its colours, not long strokes. Soft puffs leave the surface; at rest they drift out all round,
        // moving they are swept back a short way; they spread and fade fast.
        double speed = vel.length();
        Vec3 back = speed > 1e-4 ? vel.scale(-1 / speed) : Vec3.ZERO;
        double sweep = Math.min(1, speed / 1.2);
        if (c.body()) {
            for (int i = 0; i < 18 + (int) (22 * surge); i++) {
                long sd = 5200 + seedOff + i * 61L;
                double life = 8 + 5 * hash(sd), ph = age / life + hash(sd + 1);
                long cyc = (long) Math.floor(ph);
                double u = ph - cyc;
                long cs = sd + cyc * 7919L;
                Vec3 rnd = new Vec3(hash(cs) * 2 - 1, hash(cs + 1) * 2 - 1, hash(cs + 2) * 2 - 1).normalize();
                Vec3 dir = rnd.scale(1 - .7 * sweep).add(back.scale(1.5 * sweep)).normalize();
                double dist = r * (.7 + (.5 + .9 * sweep + 1.3 * surge) * (1 - Math.pow(1 - u, 2)));
                double size = r * (.22 + .2 * hash(cs + 3)) * (1 + 1.3 * u) * (1 + .4 * surge), a = smooth(u / .15) * (1 - u) * (.7 + .25 * surge);
                int col = i % 3 == 0 ? p.light() : i % 3 == 1 ? p.mid() : p.deep();
                fxSprite(c, at.add(dir.scale(dist)), size, SMOKE, (int) (cs % 991), col, a, age * .05 + i);
            }
            // Calm, glassy energy (owner, 2026-11-20: the swirl looked busy in the 3D act).
            orb(c, at, r * .78, p.variant(), 1 + 8, p.tint(), 1);
        }
        if (!c.glow()) return;
        // Kept low at the stop, where the two nearly touch and their light would pile up white (owner, round 42).
        glow(c, at, r * .9, .5 - .15 * surge, p.hot());
        glow(c, at, r * (2 + .4 * surge), .2, p.halo());
        if (surge <= 0) return;
        // Pulses of pressure going out, one after another.
        for (int k = 0; k < 2; k++) {
            double ring = ((age + k * 5) / 10.0) % 1;
            fxSprite(c, at, r * (1 + 1.8 * ring), SHOCK, (int) (5300 + seedOff % 97 + k), p.light(), .25 * (1 - ring) * surge, 0);
        }
        // Its own lightning (red round Red, blue round Blue), crackling all the time.
        for (int b = 0; b < 2 + (int) (6 * surge); b++) {
            Vec3 d = nvec(5400 + seedOff + b, age * .25).normalize(), d2 = d.add(nvec(5450 + seedOff + b, age * .3).scale(.8)).normalize();
            liveBolt(c, at.add(d.scale(r * .8)), at.add(d2.scale(r * (1.5 + .9 * vnoise(5480 + seedOff + b, age * .4)))), 6, .45, .025 + .03 * surge,
                    b % 2 == 0 ? p.light() : p.hot(), surge, 5500 + seedOff + b * 13, age, 1);
        }
    }

    /** Colours of a swirling orb: its ORB variant and tint, the plasma tiers (deep, mid, light), the under-layer, the halo and the hot core. */
    record Palette(int variant, int tint, int deep, int mid, int light, int under, int halo, int hot) {}
    static final Palette BLUE_P = new Palette(0, BLUE_TINT, DEEP_BLUE, ROYAL, 0x3AA8FF, 0x061450, ROYAL, ICE);
    static final Palette RED_P = new Palette(1, RED, RED_DEEP, RED, RED_LIGHT, 0x2A0008, RED, RED_CORE);

    /** A big sphere with three tiers of liquid plasma winding round it, mostly horizontally, fast (about a turn a second): broad main currents,
     *  thinner secondaries (some split off a main one) and short faint wisps. Each current keeps deforming as it goes (its sweep, radius and
     *  height breathe, waves run along it), so it churns instead of turning as a rigid shape. The parts behind the sphere are drawn before it and
     *  the parts in front after it, so the currents really wrap round it. Blue's look; in the convergence Red and Blue both use it, same size,
     *  only the palette differs ({@code seedOff} keeps the two from being copies). */
    static void swirlOrb(Ctx c, Vec3 at, double r, double age, boolean charged, Palette p, double appear, double beat, long seedOff) {
        List<Arm> arms = currents(c, at, r, age, charged, seedOff);
        if (c.body()) {
            double coreD = at.distanceTo(c.eye);
            for (Arm a : arms) drawArm(c, a, appear, charged, p, at, r, coreD, false);
            orb(c, at, r * .72, p.variant(), charged ? 1 : 0, p.tint(), appear);
            for (Arm a : arms) drawArm(c, a, appear, charged, p, at, r, coreD, true);
        }
        if (!c.glow()) return;
        glow(c, at, r * 1.1, (.5 + .25 * beat) * appear, p.hot());
        glow(c, at, r * .45, .8 * appear, 0xFFFFFF);
        glow(c, at, r * 2.2, (.18 + .1 * beat) * appear, p.halo());
    }

    /** The plasma currents winding round a sphere of radius {@code r} (more and faster when {@code charged}). */
    static List<Arm> currents(Ctx c, Vec3 at, double r, double age, boolean charged, long seedOff) {
        double spin = charged ? 1.4 : 1;
        List<Arm> arms = new ArrayList<>();
        for (int i = 0; i < (charged ? 12 : 9); i++) {
            long sd = 900 + seedOff + i * 131L;
            arms.add(plasmaArm(c, at, r, age, sd, spin, 0));
            if (hash(sd + 8) < .45) arms.add(plasmaArm(c, at, r, age, sd + 50000, spin, 1));
        }
        for (int i = 0; i < (charged ? 7 : 5); i++) arms.add(plasmaArm(c, at, r, age, 3900 + seedOff + i * 151L, spin, 1));
        for (int i = 0; i < (charged ? 8 : 6); i++) arms.add(plasmaArm(c, at, r, age, 4700 + seedOff + i * 157L, spin, 2));
        return arms;
    }

    /** One current: its centreline from the outer tail (index 0) inward, its half-widths, seed, distance from the eye and tier (0 main,
     *  1 secondary, 2 wisp). */
    record Arm(Vec3[] pts, double[] w, long seed, double depth, int tier) {
        Arm(Vec3[] pts, double[] w, long seed, double depth) { this(pts, w, seed, depth, 0); }
    }

    /** A current on a near-horizontal plane that wobbles: it sweeps a long way round the sphere (most the same way, a few against) and sinks
     *  onto its surface. Its sweep, radius, height and width all keep changing, fast. Main currents are the broadest; secondaries thinner and
     *  wider-flung; wisps short, thin and faint, fading out before they land. */
    static Arm plasmaArm(Ctx c, Vec3 at, double r, double age, long sd, double spin, int tier) {
        Vec3 n = new Vec3(0, 1, 0).add(nvec(sd, age * .01).scale(.35)).normalize();
        Vec3[] uw = basis(n);
        double rOut = r * (tier == 0 ? 1.05 + .55 * hash(sd + 1) : tier == 1 ? 1.1 + .8 * hash(sd + 1) : 1.2 + .8 * hash(sd + 1)), rIn = r * (.75 + .1 * hash(sd + 2));
        double sign = hash(sd + 9) < .85 ? 1 : -1, end = tier == 0 ? 1 : tier == 1 ? .85 : .75;
        double turn = (2.5 + 2 * hash(sd + 3)) * sign * (1 + .25 * snoise(sd + 12, age * .06));
        double phase = hash(sd + 4) * Math.PI * 2 + age * (.16 + .12 * hash(sd + 5)) * spin * sign;
        double wmax = r * (tier == 0 ? .2 + .14 * hash(sd + 6) : tier == 1 ? .09 + .07 * hash(sd + 6) : .05 + .04 * hash(sd + 6));
        int k = 28;
        Vec3[] pts = new Vec3[k + 1];
        double[] w = new double[k + 1];
        for (int j = 0; j <= k; j++) {
            double t = (double) j / k * end;
            double rad = (rIn + (rOut - rIn) * Math.pow(1 - t, 1.2)) * (1 + .12 * snoise(sd + 13, t * 3 - age * .2));
            double a = phase + turn * Math.pow(t, 1.3) + .35 * snoise(sd + 14, t * 1.5 - age * .12);
            Vec3 lift = n.scale(r * .25 * snoise(sd + 6, t * 2 + age * .06));
            pts[j] = at.add(uw[0].scale(Math.cos(a) * rad)).add(uw[1].scale(Math.sin(a) * rad)).add(lift);
            double prof = Math.pow(Math.sin(Math.PI * Math.pow(t / end, .75)), .5);
            w[j] = wmax * prof * (.55 + .7 * vnoise(sd + 7, t * 2.5 - age * .25));
        }
        return new Arm(pts, w, sd, pts[k / 2].distanceTo(c.eye), tier);
    }

    /** One current as liquid light, either its part behind the sphere ({@code front} false, drawn before the sphere) or in front of it (after):
     *  the width fades out across the sphere's depth so the two parts meet. A soft under-layer on main currents; wisps paler and fainter. */
    private static void drawArm(Ctx c, Arm a, double alpha, boolean charged, Palette p, Vec3 at, double r, double coreD, boolean front) {
        int n = a.w().length, seed = (int) (a.seed() % 997);
        double[] w = new double[n];
        for (int j = 0; j < n; j++) {
            double near = smooth(.5 - (a.pts()[j].distanceTo(c.eye) - coreD) / (r * .5));
            w[j] = a.w()[j] * (front ? near : 1 - near);
        }
        double pick = hash(a.seed() + 10);
        if (a.tier() == 0) {
            double[] under = new double[n];
            for (int j = 0; j < n; j++) under[j] = w[j] * 1.15;
            fxRibbon(c, a.pts(), under, PLASMA, seed, p.under(), alpha * .55);
        }
        int color = switch (a.tier()) {
            case 0 -> pick < .5 ? p.deep() : p.mid();
            case 1 -> pick < .5 ? p.mid() : p.light();
            default -> charged && pick < .5 ? p.hot() : p.light();
        };
        double[] top = new double[n];
        for (int j = 0; j < n; j++) top[j] = w[j] * .85;
        fxRibbon(c, a.pts(), top, PLASMA, seed + 1, color, alpha * (a.tier() == 2 ? .5 : 1));
    }

    /** Debris falling into (inward) or flung out of (outward) a core: each piece starts far out on a seeded path, accelerates as it nears the core,
     *  spirals, shrinks and is swallowed; then it is reborn elsewhere. The cycle index picks the path, so nothing jumps mid-flight. */
    static void infall(Ctx c, Vec3 at, double r, double age, int count, long seed, int color, double alpha) { fall(c, at, r, age, count, seed, color, alpha, true); }

    static void fall(Ctx c, Vec3 at, double r, double age, int count, long seed, int color, double alpha, boolean inward) {
        for (int i = 0; i < count; i++) {
            long sd = seed + i * 131L;
            double life = 28 + 34 * hash(sd), ph = age / life + hash(sd + 1);
            long cyc = (long) Math.floor(ph);
            double p = ph - cyc;
            long cs = sd + cyc * 7919L;
            Vec3 pos = fallAt(at, cs, r, p, inward);
            double ease = inward ? p * p * p : 1 - Math.pow(1 - p, 3);
            double size = (.07 + .26 * hash(cs + 6)) * (inward ? 1 - .7 * ease : 1 - .4 * ease);
            double a = alpha * smooth(p / .12) * smooth((1 - p) / .08);
            if (pos.distanceTo(c.eye) < 1.5 || a <= 0) continue;
            fxSprite(c, pos, size, DEBRIS, (int) (cs % 991), color, a, age * (hash(cs + 7) - .5) * .8 + i);
            // A streak behind it, longer the faster it goes: the acceleration shows in a still frame.
            Vec3 back = fallAt(at, cs, r, Math.max(0, p - .08), inward);
            if (back.distanceTo(pos) > size) fxBeam(c, back, pos, .005, size * .35, BOLT, (int) (cs % 991) + 1, inward ? 0x9DBBFF : RED_LIGHT, a * .6);
        }
    }

    /** Where piece cs is at progress p: inward, it starts far out and accelerates into the core on a tightening spiral; outward, it is thrown off. */
    private static Vec3 fallAt(Vec3 at, long cs, double r, double p, boolean inward) {
        Vec3 dir = new Vec3(hash(cs) * 2 - 1, (hash(cs + 1) * 2 - 1) * .7, hash(cs + 2) * 2 - 1).normalize();
        double far = r * (2.6 + 2.6 * hash(cs + 3)), near = r * .3, swirl = (hash(cs + 4) < .5 ? 1 : -1) * (1.5 + 2 * hash(cs + 5));
        double ease = inward ? p * p * p : 1 - Math.pow(1 - p, 3);
        double rad = inward ? far - (far - near) * ease : near + (far * 1.4 - near) * ease, turn = swirl * ease * (inward ? 1 : .2);
        Vec3 d = new Vec3(dir.x * Math.cos(turn) - dir.z * Math.sin(turn), dir.y, dir.x * Math.sin(turn) + dir.z * Math.cos(turn));
        return at.add(d.scale(rad));
    }

    // ---- Red: violent, unstable, repelling ----

    /** Red's orb with its energy blown back up and away (a default for the charge and the tests). */
    static void redOrb(Ctx c, Vec3 at, double size, double age) { redOrb(c, at, size, age, new Vec3(0, 1, 0)); }

    /** Red (owner's reset, 2026-11-18, refs 07/15/16/36): a small overloaded core inside a vortex of black ink edged with red, torn, stretched
     *  and dragged towards {@code back}; around it, sharp red brush strokes are flung out in that direction: thick heads, warped bodies, tails that
     *  taper or break. Different from Blue in shape, not just colour: Blue's masses fall in, Red's ink whirls and its strokes fly out. */
    static void redOrb(Ctx c, Vec3 at, double size, double age, Vec3 back) { redOrb(c, at, size, age, back, 1); }

    /** Red (owner, 2026-11-18): one red-black mass under pressure. A big hot red body swirls round a white-hot core; black ink is torn off its
     *  surface by the pressure, each shred stretched back and ripped apart with red light burning through it; strong hot red strokes break out
     *  of it in waves. The whole silhouette leans back like a comet (the recoil, towards {@code back}). While charging ({@code reach} < 1) it is
     *  all tight and the waves replace each other faster. */
    static void redOrb(Ctx c, Vec3 at, double size, double age, Vec3 back, double reach) {
        // Owner's new direction (2026-11-18): a hot red mass under pressure. Red dominates (body, halo, smears), black is only torn ash, the white
        // heart is small. The mass breathes (the pressure rising), short flares break out of it now and then, and smears stream off it towards
        // {@code back} (behind it in flight), so it reads as cursed energy surging forward.
        back = back.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : back.normalize();
        double breath = 1 + .07 * Math.sin(age * 1.3) + .04 * Math.sin(age * 2.9 + 1);
        double s = size * breath;
        double period = reach < 1 ? 9 : 12;
        Wave[] waves = {wave(age, 0, back, period), wave(age, 1, back, period)};
        if (c.body()) {
            double coreD = at.distanceTo(c.eye);
            redSmears(c, at, s, age, back, reach);
            // While charging (reach < 1): the scribbled ink vortex of ref 16, winding into the core.
            if (reach < 1) inkVortex(c, at, s, age);
            redMass(c, at, s, age, coreD, false);
            orb(c, at, s * .6, 1, 4, RED, 1);
            redMass(c, at, s, age, coreD, true);
            inkTorn(c, at, s, age, back, reach);
        }
        // Short flares: the wave strokes, kept short (the smears carry the big shapes).
        for (Wave w : waves) strokes(c, at, s, w, reach * .6, 1);
        if (!c.glow()) return;
        // A red heart, never white (owner, 2026-11-19).
        glow(c, at, s * .4, .5, RED);
        glow(c, at, s * 1.3, .75, RED);
        glow(c, at, s * 2.4, .6 * flicker(age, 7), RED_LIGHT);
        glow(c, at, s * 4, .45, RED);
        // The pink-red haze of the Roblox refs: wide and soft, stronger while charging.
        glow(c, at, s * (reach < 1 ? 9 : 6), (reach < 1 ? .4 : .25) * flicker(age, 11), 0xFF5A78);
    }

    /** Smears streaming off the mass towards {@code back}: five broad, soft strokes of red, rooted inside the mass, flowing and swelling (their
     *  shape keeps changing, nothing pops), longer with more reach. */
    private static void redSmears(Ctx c, Vec3 at, double size, double age, Vec3 back, double reach) {
        Vec3[] uw = basis(back);
        for (int i = 0; i < 5; i++) {
            long sd = 7400 + i * 59L;
            double ang = (i + .6 * hash(sd)) / 5 * Math.PI * 2 + age * .03, off = size * (.25 + .35 * hash(sd + 1));
            Vec3 root = at.add(uw[0].scale(Math.cos(ang) * off)).add(uw[1].scale(Math.sin(ang) * off));
            double L = size * reach * (1.6 + 1.6 * vnoise(sd + 2, age * .08)), w0 = size * (.35 + .25 * hash(sd + 3));
            int k = 14;
            Vec3[] pts = new Vec3[k + 1];
            double[] w = new double[k + 1];
            for (int j = 0; j <= k; j++) {
                double t = (double) j / k;
                pts[j] = root.add(back.scale(L * t)).add(nvec(sd + 4, t * 1.5 - age * .1).scale(size * .35 * t));
                w[j] = w0 * Math.pow(1 - t, .7) * smooth(t / .08 + .4) * (.6 + .6 * vnoise(sd + 5, t * 3 - age * .2));
            }
            fxRibbon(c, pts, w, PLASMA, (int) (sd % 997), i % 3 == 0 ? RED_LIGHT : i % 3 == 1 ? RED : RED_BODY, .9);
        }
    }

    /** The hot red body: thick red plasma currents swirling fast and tight round the core on tumbling planes, their width surging; together they
     *  read as one glowing mass. Front half drawn a little see-through. */
    private static void redMass(Ctx c, Vec3 at, double size, double age, double coreD, boolean front) {
        for (int i = 0; i < 6; i++) {
            long sd = 7600 + i * 89L;
            Vec3[] uw = basis(nvec(sd, age * .03).normalize());
            double rad = size * (.55 + .55 * hash(sd + 1)), arc = 2.4 + 1.6 * hash(sd + 2), phase = hash(sd + 3) * Math.PI * 2 + age * (.18 + .1 * hash(sd + 4));
            int k = 18;
            Vec3[] pts = new Vec3[k + 1];
            double[] w = new double[k + 1];
            for (int j = 0; j <= k; j++) {
                double t = (double) j / k, a = phase + arc * t;
                pts[j] = at.add(uw[0].scale(Math.cos(a) * rad).add(uw[1].scale(Math.sin(a) * rad)).scale(1 + .2 * snoise(sd + 5, t * 3 - age * .2)));
                double near = smooth(.5 - (pts[j].distanceTo(c.eye) - coreD) / (size * .4));
                w[j] = size * (.35 + .2 * hash(sd + 6)) * Math.pow(Math.sin(Math.PI * t), .5) * (.55 + .6 * vnoise(sd + 7, t * 3 - age * .25)) * (front ? near : 1 - near);
            }
            fxRibbon(c, pts, w, PLASMA, (int) (sd % 997), i % 3 == 0 ? RED_LIGHT : i % 3 == 1 ? RED : RED_BODY, front ? .75 : 1);
        }
    }

    /** Ink torn off the red body by the pressure: each shred starts as a thick blot of ink on the surface, is stretched outward and back (curling
     *  a little round the core as it goes), thins and rips apart with red light burning through it, and is gone; then another tears off
     *  elsewhere. Its root stays on the surface, so ink and red read as one mass. */
    private static void inkTorn(Ctx c, Vec3 at, double size, double age, Vec3 back, double reach) {
        for (int i = 0; i < 13; i++) {
            long sd = 7100 + i * 137L;
            double life = 10 + 6 * hash(sd), ph = age / life + hash(sd + 1);
            long cyc = (long) Math.floor(ph);
            double u = ph - cyc;
            long cs = sd + cyc * 104729L;
            double env = smooth(u / .08) * (1 - smooth((u - .55) / .45));
            if (env <= 0) continue;
            Vec3 rnd = new Vec3(hash(cs) * 2 - 1, hash(cs + 1) * 2 - 1, hash(cs + 2) * 2 - 1).normalize();
            // Torn off the surface and swept along with the motion (owner, 2026-11-18): ash, not armour.
            Vec3 dir = rnd.add(back.scale(.8)).normalize(), side = dir.cross(rnd.cross(back).lengthSqr() > 1e-6 ? rnd.cross(back) : new Vec3(0, 1, 0)).normalize();
            double L = size * reach * (.3 + (.4 + .6 * hash(cs + 3)) * smooth(u / .7)), w0 = size * (.14 + .1 * hash(cs + 4)) * (1 - .45 * u);
            double curl = (hash(cs + 5) - .5) * 1.6;
            int k = 12;
            Vec3[] pts = new Vec3[k + 1];
            double[] w = new double[k + 1];
            for (int j = 0; j <= k; j++) {
                double t = (double) j / k;
                pts[j] = at.add(dir.scale(size * .55 + L * t)).add(side.scale(curl * L * t * .8)).add(back.scale(L * .2 * t * t));
                double rip = smooth((vnoise(cs + 6, t * 4 + u * 3) - .1 - .7 * u * t) / .1);
                w[j] = w0 * Math.pow(1 - t, .6) * smooth(t / .05 + .4) * rip * (.6 + .6 * vnoise(cs + 7, t * 3 + u * 2));
            }
            fxRibbon(c, pts, w, BRUSH, (int) (cs % 997), INK, env);
        }
    }

    /** One release: which way it goes, how far through its life it is (0..1), its seed. */
    record Wave(Vec3 dir, double u, long seed) {
        double env() { return smooth(u / .04) * (1 - smooth((u - .6) / .4)); }
    }

    /** The wave on track 0 or 1 at this age: a new one every {@code period} ticks per track, the tracks half a period apart; each goes a new way. */
    static Wave wave(double age, int track, Vec3 back, double period) {
        double ph = age / period + track * .5;
        long idx = (long) Math.floor(ph), cs = 7900 + track * 31L + idx * 104729L;
        Vec3 rnd = new Vec3(hash(cs) * 2 - 1, hash(cs + 1) * 2 - 1, hash(cs + 2) * 2 - 1).normalize();
        // Owner, 2026-11-18: Red recoils: its force goes backwards, so the waves stay in a cone behind it.
        return new Wave(back.scale(2).add(rnd.scale(.6)).normalize(), ph - idx, cs);
    }

    /** One wave's strokes: a big main stroke along the wave's direction and a few small ones fanned round it, starting a moment later. Each is
     *  rooted in the ink (its tail starts at the ink current, drawn as ink for its first stretch), unfurls outward, stretches; its tail
     *  disintegrates (gaps open from the tail and spread forward as it ages) while the head stays whole, then it thins and fades. */
    static void strokes(Ctx c, Vec3 at, double size, Wave wv, double reach, double alpha) {
        if (wv.env() <= 0) return;
        long seed = wv.seed();
        // Few radial strokes: they give the recoil its direction; the pressure itself is in the bursts.
        int count = 1 + (int) Math.round((1 + hash(seed + 1)) * Math.min(1, reach));
        Vec3[] uw = basis(wv.dir());
        for (int j = 0; j < count; j++) {
            long sd = seed + j * 53L;
            boolean main = j == 0;
            double delay = main ? 0 : .04 + .12 * hash(sd), u = clamp01((wv.u() - delay) / (1 - delay));
            double env = smooth(u / .05) * (1 - smooth((u - .55) / .45)) * alpha;
            if (env <= 0) continue;
            double rot = hash(sd + 2) * Math.PI * 2, spread = main ? 0 : (hash(sd + 1) - .5) * 1.1;
            Vec3 off = uw[0].scale(Math.cos(rot)).add(uw[1].scale(Math.sin(rot)));
            Vec3 dir = wv.dir().add(off.scale(Math.tan(spread))).normalize(), side = dir.cross(off).normalize(), side2 = dir.cross(side);
            double L = size * reach * (main ? 6.5 : 3.5 + 1.5 * hash(sd + 3)), w0 = size * Math.sqrt(reach) * (main ? .9 : .22 + .1 * hash(sd + 4));
            double root = size * 1.0, headD = root + L * (1 - Math.pow(1 - u, 3)), tailD = root + L * .85 * smooth((u - .25) / .75);
            double curl = (hash(sd + 5) - .5) * (main ? 1.2 : 2.2) * Math.pow(1 - u, 1.5);
            int k = main ? 18 : 12;
            Vec3[] pts = new Vec3[k + 1];
            double[] w = new double[k + 1], ink = new double[k + 1], spine = new double[k + 1];
            for (int i = 0; i <= k; i++) {
                double t = (double) i / k, d = tailD + (headD - tailD) * t;
                pts[i] = at.add(dir.scale(d)).add(side.scale(curl * L * (1 - t) * (1 - t) * .5)).add(side2.scale(size * .12 * snoise(sd + 6, t * 3 + u * 4)));
                // The tail dissolves mostly by thinning and fading; only a few breaks open in it, so it never shatters into shards.
                double gap = smooth((vnoise(sd + 7, t * 4 + u * 3) - .1 - .45 * u * Math.pow(1 - t, 1.5)) / .15);
                w[i] = w0 * Math.pow(t, .9 + 1.2 * u) * smooth((1 - t) / .05 + .3) * gap * (.6 + .6 * vnoise(sd + 8, t * 3 + u * 2)) * (1 - .4 * u);
                ink[i] = w[i] * 1.3 * (1 - smooth((t - .15) / .25)) * (1 - u);
                spine[i] = w[i] * .35 * smooth((vnoise(sd + 10, t * 2 + u * 3) - .4) / .15);
            }
            if (c.body()) {
                fxRibbon(c, pts, ink, BRUSH, (int) (sd % 977) + 3, INK, env);
                fxRibbon(c, pts, w, BRUSH, (int) (sd % 977), main || j % 2 == 0 ? RED : RED_BODY, env);
                // Hot core inside the stroke where the energy runs strongest.
                double[] hot = new double[k + 1];
                for (int i = 0; i <= k; i++) hot[i] = spine[i] * 1.4;
                fxRibbon(c, pts, hot, BRUSH, (int) (sd % 977) + 4, RED_LIGHT, env);
            }
            if (c.glow()) fxRibbon(c, pts, spine, FLOW, (int) (sd % 977) + 2, RED_CORE, env);
        }
    }

    /** Red's ruptures: in volleys (two drifting rhythms) the core spits long, sharp, nearly straight strokes outward, longest on the side the pressure
     *  is venting from; each shoots out, stretches, then tears into pieces and smoke. Red and deep red in the alpha pass with a hot spine; ink ones. */
    static void ruptures(Ctx c, Vec3 at, double size, double age, long seed, double alpha) { ruptures(c, at, size, age, seed, alpha, nvec(seed, age * .02).normalize()); }

    /** As above, venting mostly towards {@code bias}: the strokes stream that way in a ragged cone. */
    static void ruptures(Ctx c, Vec3 at, double size, double age, long seed, double alpha, Vec3 bias) {
        for (int i = 0; i < 24; i++) {
            long sd = seed + i * 173L;
            double period = i % 2 == 0 ? 9 : 13, ph = age / period + hash(sd + 1) * .18;
            long cyc = (long) Math.floor(ph);
            double q = (ph - cyc) / .85;
            if (q > 1) continue;
            long cs = sd + cyc * 104729L;
            Vec3 rnd = new Vec3(hash(cs) * 2 - 1, hash(cs + 1) * 2 - 1, hash(cs + 2) * 2 - 1).normalize();
            Vec3 dir = rnd.scale(.55).add(bias.scale(1.4)).normalize();
            Vec3[] uw = basis(dir);
            double turn = hash(cs + 3) * Math.PI * 2;
            Vec3 side = uw[0].scale(Math.cos(turn)).add(uw[1].scale(Math.sin(turn)));
            double len = size * (3 + 7 * hash(cs + 4)) * (.7 + .5 * Math.max(0, dir.dot(bias)));
            double head = 1 - Math.pow(1 - q, 3), tail = Math.max(0, head - (.25 + .35 * hash(cs + 5)) - .3 * q * q);
            double env = smooth(q / .06) * smooth((1 - q) / .35) * alpha, torn = smooth((q - .45) / .2);
            double bend = (hash(cs + 6) - .5) * .5, wmax = size * (.12 + .3 * hash(cs + 7));
            int kind = (int) (hash(cs + 8) * 3);
            boolean ink = kind == 2;
            if (env <= 0) continue;
            int n = 12;
            Vec3[] pts = new Vec3[n + 1];
            double[] widths = new double[n + 1];
            for (int k = 0; k <= n; k++) {
                double t = (double) k / n, u = tail + (head - tail) * t;
                pts[k] = at.add(dir.scale(size * .6 + len * u)).add(side.scale(bend * len * u * u));
                double gap = smooth((vnoise(cs + 9, t * 5 + q * 3) - .2 - .45 * torn) / .08);
                widths[k] = wmax * Math.pow(1 - t, .7) * smooth(t / .08 + .3) * gap;
            }
            if (c.body()) fxRibbon(c, pts, widths, FLOW, (int) (cs % 977), ink ? INK : kind == 0 ? RED_BODY : RED_DEEP, env);
            if (!ink && c.glow()) {
                double[] spine = new double[n + 1];
                for (int k = 0; k <= n; k++) spine[k] = widths[k] * .25;
                fxRibbon(c, pts, spine, BOLT, (int) (cs % 977) + 1, kind == 0 ? RED_CORE : RED_LIGHT, env * .75);
            }
            if (c.body() && torn > 0) fxSprite(c, pts[0], size * (.3 + .5 * q), SMOKE, (int) (cs % 953), ink ? INK : RED_DEEP, .55 * env * torn, age * .05 + i);
        }
    }

    /** Red released and in flight (owner, 2026-11-18). Release: a white-red flash at the muzzle, two shock rings racing forward along the shot,
     *  and big red smears bursting forward in a cone, with ink flung along them; all gone in about fourteen ticks. Flight: the hot red mass at the
     *  head (its smears streaming behind), and every other tick it leaves a broad red smear on its path that stays, spreads and fades within
     *  about twenty ticks, with a small ink scrap now and then; a broken, fading trail, never a beam. */
    static void redFlight(Ctx c, Vec3 origin, Vec3 dir, Vec3 head, double age, double fade, long seed, boolean flying) { redFlight(c, origin, dir, head, age, fade, seed, flying, 1); }

    static void redFlight(Ctx c, Vec3 origin, Vec3 dir, Vec3 head, double age, double fade, long seed, boolean flying, double power) {
        if (fade <= 0) return;
        double total = head.distanceTo(origin);
        Vec3[] uw = basis(dir);
        if (age < 14) release(c, origin, dir, age, fade);
        for (int n = 0; n < 10; n++) {
            double laid = Math.floor(age / 2) * 2 - 2 * n, old = age - laid;
            if (laid < 0) break;
            double env = (1 - smooth(old / 20)) * fade;
            if (env <= 0) continue;
            long sd = seed * 31 + (long) laid * 7;
            Vec3 at0 = origin.add(dir.scale(Math.min(total, laid * BlueRedRules.RED_SPEED)));
            double turn = hash(sd) * Math.PI * 2;
            Vec3 side = uw[0].scale(Math.cos(turn)).add(uw[1].scale(Math.sin(turn)));
            double len = 3.5 + 1.5 * hash(sd + 1);
            int k = 12;
            Vec3[] pts = new Vec3[k + 1];
            double[] w = new double[k + 1];
            for (int j = 0; j <= k; j++) {
                double t = (double) j / k;
                pts[j] = at0.subtract(dir.scale(len * (1 - t))).add(side.scale((.1 + .06 * old) * Math.sin(Math.PI * t) + .08 * snoise(sd + 2, t * 3 + old * .2)));
                w[j] = (.32 + .02 * old) * Math.pow(t, .6) * (1 - .3 * t) * (.6 + .5 * vnoise(sd + 4, t * 3 + old * .2));
            }
            if (c.body()) {
                double[] near = new double[k + 1];
                for (int j = 0; j <= k; j++) near[j] = nearFade(c, pts[j]);
                fxRibbon(c, pts, w, near, PLASMA, (int) (sd % 977), n % 2 == 0 ? RED : RED_BODY, env * .9);
                if (n % 3 == 1) {
                    // A scrap of ink left behind, torn and fading.
                    Vec3[] ip = {pts[3], pts[5], pts[7]};
                    double[] iw = {.04, .1 * (1 - old / 20), .03};
                    fxRibbon(c, ip, iw, BRUSH, (int) (sd % 977) + 3, INK, env);
                }
            }
        }
        if (flying) {
            // A big mass of energy on the move (owner, 2026-11-19): the head, a tapering trail of it behind, black-core red lightning along it.
            double hs = .75 * (.7 + .6 * power), trail = Math.min(total, 6 + 8 * power);
            int k = 16;
            Vec3[] pts = new Vec3[k + 1];
            double[] w = new double[k + 1];
            for (int j = 0; j <= k; j++) {
                double t = (double) j / k;
                pts[j] = head.subtract(dir.scale(trail * t)).add(uw[0].scale(hs * .25 * snoise(seed + 11, t * 3 - age * .3))).add(uw[1].scale(hs * .25 * snoise(seed + 12, t * 3 - age * .3)));
                w[j] = hs * 1.5 * Math.pow(1 - t, .9) * (.75 + .35 * vnoise(seed + 13, t * 4 - age * .4));
            }
            if (c.body()) fxRibbon(c, pts, w, PLASMA, (int) (seed % 977) + 5, RED_BODY, .85 * fade);
            if (c.glow()) fxRibbon(c, pts, w, FLOW, (int) (seed % 977) + 6, RED, .35 * fade);
            for (int b = 0; b < 4 + (int) (3 * power); b++) {
                double back = trail * (.35 + .65 * vnoise(seed + 20 + b, age * .3));
                Vec3 to = head.subtract(dir.scale(back)).add(uw[0].scale(hs * 1.6 * snoise(seed + 30 + b, age * .4))).add(uw[1].scale(hs * 1.6 * snoise(seed + 40 + b, age * .4)));
                Vec3 from = head.add(nvec(seed + 50 + b, age * .5).scale(hs * .5));
                if (c.body()) liveBolt(c, from, to, 8, .45, .05 * (.6 + .4 * power), INK, .9 * fade, seed * 7 + b * 13, age, 1);
                else liveBolt(c, from, to, 8, .45, .02 * (.6 + .4 * power), RED_LIGHT, .8 * fade, seed * 7 + b * 13, age, 1);
            }
            chargeStreaks(c, head, hs, age, 1, dir.scale(-1));
            chargeMotes(c, head, hs, age, 1, dir.scale(-1));
            Vec3 h0 = head;
            redOrb(c, h0, hs, age, dir.scale(-1));
        }
    }

    /** Red gathering (owner's refs, 2026-11-19): long scratchy brush streaks of red with a hot, pale core, flung out of the hand and swept
     *  back past the user in a widening fan, each one lashing out, holding and fading while the next comes; more and longer with charge. */
    private static void chargeStreaks(Ctx c, Vec3 at, double s, double age, double charge, Vec3 back) {
        Vec3[] uw = basis(back);
        // Watched from behind (third person, or anyone else), they would sweep through the camera: kept short of it.
        double far = at.distanceTo(c.eye), reachCap = far > 3 ? far * .45 : Double.MAX_VALUE;
        int count = 6 + (int) (8 * charge);
        for (int i = 0; i < count; i++) {
            long sd = 7900 + i * 53L;
            double life = 14 + 8 * hash(sd), ph = age / life + hash(sd + 1);
            long cyc = (long) Math.floor(ph);
            double u = ph - cyc;
            long cs = sd + cyc * 7919L;
            double ang = hash(cs) * Math.PI * 2, spread = .35 + .45 * hash(cs + 1), L = Math.min(reachCap, (3 + 5 * hash(cs + 2)) * (.6 + .6 * charge));
            double head = smooth(u / .3), tail = smooth((u - .55) / .45), al = smooth(u / .08) * (1 - tail);
            if (al <= .01 || head - tail < .03) continue;
            Vec3 side = uw[0].scale(Math.cos(ang)).add(uw[1].scale(Math.sin(ang)));
            // Rooted on its rim, not its centre (owner, round 49).
            Vec3 root = at.add(side.scale(.85).add(back.scale(.5)).normalize().scale(s * .66));
            int k = 14;
            Vec3[] pts = new Vec3[k + 1];
            double[] w = new double[k + 1], a = new double[k + 1];
            for (int j = 0; j <= k; j++) {
                double t = tail + (head - tail) * j / k, curl = t * (1.2 + hash(cs + 3)) * (hash(cs + 4) < .5 ? 1 : -1);
                Vec3 sw = side.scale(Math.cos(curl)).add(side.cross(back).scale(Math.sin(curl)));
                pts[j] = root.add(back.scale(L * t)).add(sw.scale(L * t * spread)).add(nvec(cs + 5, t * 3 - age * .2).scale(s * .15 * t));
                w[j] = s * (.25 + .3 * hash(cs + 6)) * Math.sin(Math.PI * Math.min(1, .15 + .85 * (double) j / k)) * (1 + .8 * t);
                a[j] = smooth((pts[j].distanceTo(c.eye) - .8) / 1.8);
            }
            // Faint (owner, round 49): the player must still see through them.
            // Chalky and deep red (owner, round 49).
            // Black edged red (owner, round 49): a red chalk stroke with a black one inside it.
            if (c.body()) {
                double[] core = new double[k + 1];
                for (int j = 0; j <= k; j++) core[j] = w[j] * .55;
                fxRibbon(c, pts, w, a, CHALK, (int) (cs % 977), i % 3 == 0 ? RED_DEEP : RED_BODY, .55 * al);
                fxRibbon(c, pts, core, a, CHALK, (int) (cs % 977) + 2, RED_DARK, .7 * al);
            } else fxRibbon(c, pts, w, a, CHALK, (int) (cs % 977) + 1, RED, .18 * al);
        }
    }

    /** Motes pouring out of Red (owner, round 49): many small hot sparks leaving its skin every way and dragged back with the streaks,
     *  faster the farther they go, fading out behind. */
    private static void chargeMotes(Ctx c, Vec3 at, double s, double age, double charge, Vec3 back) {
        if (!c.glow()) return;
        for (int i = 0; i < 95 + (int) (120 * charge); i++) {
            long sd = 8100 + i * 37L;
            double life = 8 + 8 * hash(sd), ph = age / life + hash(sd + 1);
            long cyc = (long) Math.floor(ph);
            double u = ph - cyc;
            long cs = sd + cyc * 7919L;
            Vec3 d = new Vec3(hash(cs) * 2 - 1, hash(cs + 1) * 2 - 1, hash(cs + 2) * 2 - 1).normalize();
            double reach = (2 + 4 * hash(cs + 3)) * (.6 + .6 * charge);
            Vec3 q = at.add(d.scale(s * (.66 + .8 * u))).add(back.scale(reach * u * u)).add(nvec(cs + 4, u * 2).scale(.15 * u));
            double near = smooth((q.distanceTo(c.eye) - .6) / 1.2);
            double size = .035 + .045 * hash(cs + 5) * (1 - u), a = smooth(u / .1) * (1 - u) * near;
            glow(c, q, size, a, i % 2 == 0 ? RED_LIGHT : RED);
            if (i % 3 == 0) glow(c, q, size * .45, a, 0xFFE0E4);
        }
    }

    /** Dark smoke round whoever gathers Red (owner's Roblox ref, then 2026-11-19: front to back, not round them): translucent black strokes
     *  rushing past from in front of them to behind, all round the line of sight, with round red embers carried the same way. Fades near the
     *  camera so the shooter still sees. */
    static void darkSwirl(Ctx c, Vec3 at, double age, double charge) { darkSwirl(c, at, age, charge, new Vec3(0, 0, 1)); }

    static void darkSwirl(Ctx c, Vec3 at, double age, double charge, Vec3 forward) {
        forward = forward.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : forward.normalize();
        Vec3[] uw = basis(forward);
        if (c.body()) for (int i = 0; i < 8; i++) {
            long sd = 8300 + i * 47L;
            double life = 10 + 6 * hash(sd), ph = age / life + hash(sd + 1);
            long cyc = (long) Math.floor(ph);
            double u = ph - cyc;
            long cs = sd + cyc * 7919L;
            double ang = hash(cs) * Math.PI * 2, rad = 1.1 + .9 * hash(cs + 1);
            Vec3 side = uw[0].scale(Math.cos(ang)).add(uw[1].scale(Math.sin(ang)));
            int k = 12;
            Vec3[] pts = new Vec3[k + 1];
            double[] w = new double[k + 1], a = new double[k + 1];
            double head = 2.5 - 5.5 * u;
            for (int j = 0; j <= k; j++) {
                double t = (double) j / k, along = head - 1.8 * t;
                pts[j] = at.add(forward.scale(along)).add(side.scale(rad * (1 + .15 * t))).add(nvec(cs + 5, t * 2 + age * .05).scale(.1));
                w[j] = (.22 + .18 * hash(cs + 2)) * Math.sin(Math.PI * t);
                a[j] = smooth((pts[j].distanceTo(c.eye) - .8) / 1.5);
            }
            fxRibbon(c, pts, w, a, SMOKE, (int) (cs % 977), INK, (.18 + .2 * charge) * Math.sin(Math.PI * u));
        }
        if (!c.glow()) return;
        for (int i = 0; i < 34; i++) {
            long sd = 8400 + i * 31L;
            double life = 12 + 8 * hash(sd), ph = age / life + hash(sd + 1);
            long cyc = (long) Math.floor(ph);
            double u = ph - cyc;
            long cs = sd + cyc * 7919L;
            double an = hash(cs) * Math.PI * 2, rr = .9 + 1.8 * hash(cs + 1);
            Vec3 q = at.add(forward.scale(2.5 - 5 * u)).add(uw[0].scale(Math.cos(an) * rr)).add(uw[1].scale(Math.sin(an) * rr));
            glow(c, q, .04 + .03 * hash(cs + 3), Math.sin(Math.PI * u) * smooth((q.distanceTo(c.eye) - .6) / 1.2), i % 2 == 0 ? RED : RED_LIGHT);
        }
    }

    /** Red called off (owner, 2026-11-19: sneak or a tap): the gathered red collapses, its motes scatter and a puff of dark red smoke
     *  rolls off, all gone in about twelve ticks. */
    static void redFizzle(Ctx c, Vec3 at, double t) {
        double k = clamp01(t / 12);
        if (k >= 1) return;
        if (c.body()) {
            orb(c, at, .35 * (1 - k) * (1 - k), 1, 0, RED, 1 - k);
            for (int i = 0; i < 8; i++) {
                Vec3 d = nvec(8500 + i, 0).normalize();
                fxSprite(c, at.add(d.scale(.2 + .9 * k)), .25 + .5 * k, SMOKE, 8510 + i, i % 2 == 0 ? RED_DARK : RED_DEEP, .45 * (1 - k), k + i);
            }
        }
        if (!c.glow()) return;
        glow(c, at, .6 * (1 - k) + .2, (1 - k), RED);
        for (int i = 0; i < 30; i++) {
            Vec3 d = nvec(8550 + i, 0).normalize();
            glow(c, at.add(d.scale(.2 + 1.8 * (1 - (1 - k) * (1 - k)))), .05 + .04 * hash(8560 + i), (1 - k) * .9, i % 2 == 0 ? RED : RED_LIGHT);
        }
    }

    /** Red gathering in the hand (owner, 2026-11-19): a red heart (never white) inside a churning red mass, energy pouring in from all round,
     *  rings of it tightening, black-core red lightning crackling off it; {@code charge} 0..1 grows all of it; {@code full}: it pulses. */
    static void redCharge(Ctx c, Vec3 at, double s, double age, double charge, boolean full) { redCharge(c, at, s, age, charge, full, new Vec3(0, 1, 0)); }

    /** {@code back}: where its pressure is thrown, behind it (owner, 2026-11-19). */
    static void redCharge(Ctx c, Vec3 at, double s, double age, double charge, boolean full, Vec3 back) { redCharge(c, at, s, age, charge, full, back, -1e9); }

    /** {@code fullAt}: the age it filled at (for the one-shot burst). */
    static void redCharge(Ctx c, Vec3 at, double s, double age, double charge, boolean full, Vec3 back, double fullAt) {
        back = back.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : back.normalize();
        chargeStreaks(c, at, s, age, charge, back);
        // Fuller still once it is full (owner, 2026-11-19).
        chargeMotes(c, at, s, age, full ? 1.8 : charge, back);
        if (c.body()) {
            double coreD = at.distanceTo(c.eye);
            redMass(c, at, s * .9, age, coreD, false);
            // Only the mass behind it: in front it hid the vortex (owner, 2026-11-19).
            orb(c, at, s * .62, 1, (full ? 1 : 0) + 4, RED, 1);
        }
        for (int b = 0; b < 3 + (int) (5 * charge) + (full ? 3 : 0); b++) {
            Vec3 d = nvec(7800 + b, age * .3).normalize(), d2 = d.add(nvec(7810 + b, age * .35).scale(.9)).normalize();
            Vec3 from = at.add(d.scale(s * .6)), to = at.add(d2.scale(s * (1.3 + 1.2 * charge + (full ? .6 : 0))));
            if (c.body()) liveBolt(c, from, to, 6, .45, .02 + .025 * charge, INK, .9, 7820 + b * 13, age, 1);
            else liveBolt(c, from, to, 6, .45, .008 + .01 * charge, RED_LIGHT, .9, 7820 + b * 13, age, 1);
        }
        if (!c.glow()) return;
        // Small and bright, all red, no white heart (owner, round 49): pure red light only, which never burns to white.
        glow(c, at, s * .7, .9, RED);
        glow(c, at, s * 1.6, .6, RED);
        glow(c, at, s * 3.5, .3 + .25 * charge, RED_BODY);
        if (full) {
            // The instant it fills (owner, 2026-11-19: make it hit): a big red shock thrown out once, rays and a flash.
            double since = age - fullAt;
            if (since >= 0 && since < 12) {
                double k = since / 12;
                fxSprite(c, at, s * (1.5 + 9 * k), SHOCK, 7870, RED, .9 * (1 - k), 0);
                fxSprite(c, at, s * (1 + 5 * k), SHOCK, 7871, RED_LIGHT, .7 * (1 - k), 1);
                fxSprite(c, at, s * (3 + 4 * k), RAYS, 7872, RED, .6 * (1 - k), k);
                glow(c, at, s * (4 - 2 * k), 1 - k, RED);
            }
            double ring = (age % 10) / 10;
            fxSprite(c, at, s * (1 + 3 * ring), SHOCK, 7860, RED_LIGHT, .7 * (1 - ring), 0);
            glow(c, at, s * 2.2, .3 + .2 * flicker(age, 5), RED);
        }
    }

    /** The release, {@code age} ticks after the shot: flash, two shock rings racing forward, big red smears and ink bursting forward in a cone. */
    private static void release(Ctx c, Vec3 origin, Vec3 dir, double age, double fade) {
        // Owner's review of round 25: a pressure mass let go, not sword slashes. Short, wide, soft masses of red heave forward and outward,
        // swelling and churning, their tails tearing and dissolving; ink is ripped off with them and breaks up fast.
        pressureBlast(c, origin, dir, age, 14, 1.2, 7700, fade);
        if (!c.glow()) return;
        // Flashes are capped by how big they look (owner's review of round 27: never a white-out in first person).
        double far = origin.distanceTo(c.eye);
        if (age < 4) { glow(c, origin, Math.min(4.5, far * .12) * (1 - age / 4), 1 - age / 4, RED_LIGHT); glow(c, origin, Math.min(9, far * .3) * (1 - age / 5), .85 * (1 - age / 4), RED); }
        if (age < 6) fxSprite(c, origin, Math.min(3 + age * .6, far * .3), RAYS, 7790, 0xFF7090, .45 * (1 - age / 6) * fade, age * .03);
        glow(c, origin.add(dir.scale(2)), 7, .35 * (1 - age / 14) * fade, 0xFF5A78);
        for (int r = 0; r < 2; r++) {
            double ra = age - r * 2.5;
            if (ra < 0 || ra > 10) continue;
            pressureWave(c, origin.add(dir.scale(.5 + ra * 1.1)), dir, .4 + ra * .55, .25 * (1 - ra / 10), ra, 7720 + r, (1 - ra / 10) * fade, RED);
        }
    }

    /** How much of a piece of Red's energy shows at {@code p}: fades out within a few blocks of the eye, so a smear or mass passing by the
     *  camera never fills the view as a solid wedge (owner's review of round 28). */
    static double nearFade(Ctx c, Vec3 p) { return smooth((p.distanceTo(c.eye) - 1.5) / 4); }

    /** A blast of released pressure along {@code dir}, {@code age} ticks in, lasting {@code life} ticks, sized by {@code scale}: a few big soft
     *  red masses heaving out in a cone (fast, then slowing; swelling, then torn from the tail and dissolving), wide red smears lagging behind
     *  them, and ink ripped off and flung, breaking up quickly. Shared by Red's release and its impact. */
    private static void pressureBlast(Ctx c, Vec3 at, Vec3 dir, double age, double life, double scale, long seed, double alpha) {
        pressureBlast(c, at, dir, age, life, scale, seed, alpha, false);
    }

    /** As above; {@code thinNear}: fade out near the camera (the impact; the release keeps its full body in front of the shooter). */
    private static void pressureBlast(Ctx c, Vec3 at, Vec3 dir, double age, double life, double scale, long seed, double alpha, boolean thinNear) {
        double k = clamp01(age / life), fade = (1 - smooth((k - .35) / .65)) * alpha;
        if (fade <= 0 || !c.body()) return;
        double out = 1 - Math.pow(1 - Math.min(1, age / (life * .55)), 2.5);
        Vec3[] uw = basis(dir);
        for (int i = 0; i < 9; i++) {
            long sd = seed + i * 53L;
            double a = hash(sd) * Math.PI * 2, spread = (.1 + .75 * hash(sd + 1)) * (i == 0 ? .3 : 1);
            Vec3 d = dir.add(uw[0].scale(Math.cos(a) * spread)).add(uw[1].scale(Math.sin(a) * spread)).normalize();
            Vec3 side = d.cross(uw[0]).lengthSqr() > 1e-6 ? d.cross(uw[0]).normalize() : uw[1];
            boolean ink = i % 3 == 2;
            // Masses: short and wide (length about 2-3x width), the leading edge full, the tail tearing apart as they age.
            double L = scale * (ink ? 1.4 + 1.0 * hash(sd + 2) : 1.6 + 1.6 * hash(sd + 2)) * (.4 + .6 * out);
            double w0 = scale * (ink ? .35 : (i == 0 ? 1.4 : .9 + .6 * hash(sd + 3))) * (.6 + .6 * out) * (1 - .45 * k);
            double travel = scale * (1 + 3 * hash(sd + 4)) * out, bend = (hash(sd + 5) - .5) * 1.2;
            // Owner's review of round 27: no straight-edged plates. More points, gaps that open gradually, the tail fading point by point as it
            // ages (head stays dense), and the tail breaking into a few thin strands.
            int n = 22;
            Vec3[] pts = new Vec3[n + 1];
            double[] w = new double[n + 1], al = new double[n + 1];
            for (int j = 0; j <= n; j++) {
                double t = (double) j / n;
                pts[j] = at.add(d.scale(travel + L * (t - .4))).add(side.scale(bend * L * (1 - t) * (1 - t) * .5)).add(nvec(sd + 6, t * 2 + age * .25).scale(scale * .3));
                double tear = smooth((vnoise(sd + 7, t * 4 + age * .35) - .05 - .85 * k * Math.pow(1 - t, 1.2)) / .25);
                // Ragged outline (fine, fast width wobble) and patchy transparency, so no edge runs straight.
                w[j] = w0 * Math.pow(Math.sin(Math.PI * (.08 + .84 * t)), .35) * tear * (.5 + .7 * vnoise(sd + 8, t * 2.5 + age * .3))
                        * (.7 + .5 * vnoise(sd + 9, t * 9 + age * .6));
                al[j] = (1 - k * (1 - smooth(t / .55))) * (.55 + .45 * vnoise(sd + 10, t * 4 - age * .3)) * (thinNear ? nearFade(c, pts[j]) : 1);
            }
            int col = ink ? INK : i % 3 == 0 ? RED_LIGHT : i % 3 == 1 ? RED : RED_BODY;
            fxRibbon(c, pts, w, al, ink ? BRUSH : PLASMA, (int) (sd % 977), col, fade);
            if (!ink) for (int h = 0; h < 2; h++) {
                int m = 10;
                Vec3[] sp = new Vec3[m + 1];
                double[] sw = new double[m + 1], sa = new double[m + 1];
                for (int j = 0; j <= m; j++) {
                    double t = .55 * j / m;
                    int q = (int) Math.round(t * n);
                    sp[j] = pts[q].add(side.scale((h == 0 ? 1 : -1) * w0 * (.3 + .5 * (1 - t)) * (.5 + k)));
                    sw[j] = w0 * .12 * Math.sin(Math.PI * j / m) * (.5 + .8 * vnoise(sd + 20 + h, t * 6 + age * .4));
                    sa[j] = (j / (double) m) * (.4 + .6 * k);
                }
                fxRibbon(c, sp, sw, sa, BRUSH, (int) (sd % 977) + 30 + h, col, fade);
            }
        }
    }

    // ---- bursts ----

    static void burst(Ctx c, Vec3 at, double age, boolean blue) { burst(c, at, age, blue, Vec3.ZERO); }

    static void burst(Ctx c, Vec3 at, double age, boolean blue, Vec3 dir) {
        if (!blue) { redImpact(c, at, age, dir.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : dir.normalize()); return; }
        double k = clamp01(age / (blue ? 12 : 16)), fade = 1 - k;
        if (fade <= 0) return;
        Random rand = new Random(blue ? 91 : 97);
        if (c.body()) {
            for (int i = 0; i < 18; i++) {
                Vec3 d = gauss(rand).normalize();
                Vec3 p = at.add(d.scale(1 + age * (.6 + .5 * rand.nextDouble())));
                if (blue) fxSprite(c, p, 1 + age * .1, FLAME, 150 + i, i % 2 == 0 ? DEEP_BLUE : ROYAL, .85 * fade, i);
                else fxSprite(c, p, .6 + rand.nextDouble() * .7, SMOKE, 150 + i, INK, .95 * fade, i);
            }
            if (!blue) fxFlat(c, at.add(0, -.9, 0), 2.5 + age * 1.1, SHOCK, 170, RED_BODY, .8 * fade);
            debris(c, at, 1 + age * .9, age, 16, blue ? 0x22305A : INK, blue ? 740 : 760, fade, false);
        }
        if (!blue) ruptures(c, at, 1.2 + age * .15, age + 20, 790, fade);
        if (!c.glow()) return;
        int color = blue ? CYAN : RED_LIGHT;
        streaks(c, at, 1, 3 + age * 1.6, age, 40, color, blue ? 780 : 800, 2.2, fade, false);
        fxSprite(c, at, 2 + age * 1.3, SHOCK, 171, blue ? ICE : RED_LIGHT, fade, 0);
        fxSprite(c, at, 3 + age * 1.6, RAYS, 172, color, fade, age * .05);
        glow(c, at, 1 + age * .35, fade, blue ? ICE : RED);
    }

    /** Red's impact (owner's reviews of rounds 25-26, ref 19), in the world at the hit, in four beats. Contact (0-1.5 ticks): a small red-white
     *  flash on the target. Squeeze (to 1.5): the flash pulls in to a hot point. Burst (from 1.5): a red pressure mass blasts on along the hit
     *  (soft thick masses and smears, ink torn off and flung, soft paint drops), a ragged pressure wave and a ground wave go out with it.
     *  Residue: what is left thins, tears and is gone within about a second; the target stays visible throughout. */
    private static void redImpact(Ctx c, Vec3 at, double age, Vec3 dir) {
        double b = age - 1.5, k = clamp01(age / 24), fade = 1 - k;
        if (fade <= 0) return;
        if (c.glow() && age < 2) {
            double squeeze = clamp01(age / 1.5);
            double far = at.distanceTo(c.eye);
            glow(c, at, Math.min(2.4, far * .1) * (1 - .6 * squeeze), 1, RED_LIGHT);
            glow(c, at, Math.min(4, far * .22) * (1 - .5 * squeeze), .9, RED);
            // The peak (owner's review of round 28): for about a tick and a half, a tight red-white star on the target; compact, so the
            // camera is never washed out.
            if (age < 1.5) {
                double peak = 1 - age / 1.5;
                glow(c, at, Math.min(1.6, far * .07), peak, RED_LIGHT);
                glow(c, at, Math.min(3, far * .14), peak, RED);
                fxSprite(c, at, Math.min(3.5, far * .17), RAYS, 8895, RED_LIGHT, peak, 0);
            }
        }
        if (b < 0) return;
        double out = 1 - Math.pow(1 - Math.min(1, b / 10), 2.5);
        pressureBlast(c, at, dir, b, 20, 1.7, 8800, 1, true);
        if (c.body()) {
            // Ground wave: ragged and uneven, not a perfect ring.
            if (b < 12) pressureWave(c, at.add(0, -.9, 0), new Vec3(0, 1, 0), 2 + b * 1.4, .6 * (1 - b / 12), b, 8860, .6 * (1 - b / 12), RED);
            fxFlat(c, at.add(0, -.95, 0), 3 + 2.5 * out, SMOKE, 171, RED_BODY, .5 * fade);
            splatter(c, at, b, dir);
        }
        if (!c.glow()) return;
        double far = at.distanceTo(c.eye);
        if (b < 2) glow(c, at, Math.min(6, far * .2) * (1 - b / 2), .9 * (1 - b / 2), RED);
        if (b < 6) fxSprite(c, at, Math.min(3 + b * .7, far * .3), RAYS, 8890, 0xFF7090, .35 * (1 - b / 6), b * .02);
        glow(c, at.add(dir.scale(2 * out)), 6, .3 * fade, 0xFF5A78);
        if (b < 12) pressureWave(c, at.add(dir.scale(b * .4)), dir, 1 + b * 1.1, .5 * (1 - b / 12), b, 8870, 1 - b / 12, RED);
    }

    /** A pressure wave: not a drawn circle but a ragged, broken band of hot red air round {@code n}: its radius bulges and dents, its width
     *  swells and thins, and it is torn open in places. */
    private static void pressureWave(Ctx c, Vec3 at, Vec3 n, double radius, double width, double age, long seed, double alpha) {
        pressureWave(c, at, n, radius, width, age, seed, alpha, RED_LIGHT);
    }

    private static void pressureWave(Ctx c, Vec3 at, Vec3 n, double radius, double width, double age, long seed, double alpha, int color) {
        if (alpha <= 0) return;
        Vec3[] uw = basis(n);
        int k = 36;
        Vec3[] pts = new Vec3[k + 1];
        double[] w = new double[k + 1];
        for (int j = 0; j <= k; j++) {
            double t = (double) j / k, a = t * Math.PI * 2 + hash(seed) * 6;
            double rr = radius * (1 + .18 * snoise(seed + 1, t * 6 + age * .2));
            pts[j] = at.add(uw[0].scale(Math.cos(a) * rr)).add(uw[1].scale(Math.sin(a) * rr));
            w[j] = width * (.3 + .9 * vnoise(seed + 2, t * 7 + age * .3)) * smooth((vnoise(seed + 3, t * 5) - .25) / .1);
        }
        fxRibbon(c, pts, w, PLASMA, (int) (seed % 977), color, alpha * .8);
    }

    /** Red paint and ink sprayed along the hit (ref 19): short fat drops flung out in a cone, fast then slowing, falling a little, smearing
     *  out along their flight and fading. */
    private static void splatter(Ctx c, Vec3 at, double age, Vec3 dir) {
        double fade = 1 - clamp01(age / 20);
        if (fade <= 0) return;
        Vec3[] uw = basis(dir);
        for (int i = 0; i < 22; i++) {
            long sd = 9100 + i * 37L;
            double a = hash(sd) * Math.PI * 2, spread = .15 + 1.1 * hash(sd + 1);
            Vec3 d = dir.add(uw[0].scale(Math.cos(a) * spread)).add(uw[1].scale(Math.sin(a) * spread)).normalize();
            double v = 3 + 6 * hash(sd + 2), dist = v * (1 - Math.pow(1 - Math.min(1, age / 12), 2));
            Vec3 head = at.add(d.scale(dist)).add(0, -.04 * age * age * hash(sd + 3), 0), tail = at.add(d.scale(dist * .55));
            // Soft round drops smeared along their flight (no three-point wedges): full in the middle, rounded at both ends.
            double w0 = .14 + .2 * hash(sd + 4);
            int n = 6;
            Vec3[] pts = new Vec3[n];
            double[] w = new double[n];
            for (int j = 0; j < n; j++) {
                double t = j / (n - 1.0);
                pts[j] = tail.add(head.subtract(tail).scale(t));
                w[j] = w0 * Math.pow(Math.sin(Math.PI * (.1 + .8 * t)), .5) * (.3 + .7 * t);
            }
            boolean ink = i % 3 == 0;
            fxRibbon(c, pts, w, ink ? BRUSH : PLASMA, (int) (sd % 977), ink ? INK : i % 3 == 1 ? RED : RED_BODY, fade);
        }
    }

    /** The scribbled ink vortex while Red charges (ref 16): many thin, rough ink lines wound round the core, spiralling inward and turning fast;
     *  a few are dark red. */
    private static void inkVortex(Ctx c, Vec3 at, double size, double age) {
        Vec3 toEye = c.eye.subtract(at);
        Vec3[] uw = basis(toEye.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : toEye.normalize());
        for (int i = 0; i < 12; i++) {
            long sd = 9300 + i * 41L;
            // Each line its own length (partial spirals, short scraps to long winds) and weight (hair-thin to thick strokes).
            double rOut = size * (1.4 + 2.8 * hash(sd)), turns = .5 + 1.6 * hash(sd + 1), weight = .4 + 1.8 * hash(sd + 10) * hash(sd + 10), phase = hash(sd + 2) * Math.PI * 2 - age * (.35 + .2 * hash(sd + 3));
            double in = (age * (.08 + .05 * hash(sd + 4)) + hash(sd + 5)) % 1;
            int k = 40;
            Vec3[] pts = new Vec3[k + 1];
            double[] w = new double[k + 1];
            for (int j = 0; j <= k; j++) {
                // Scribbled, not drawn with a compass: the radius jerks along the line, it wanders off its plane, and it breaks into dashes.
                double t = (double) j / k, a = phase + turns * Math.PI * 2 * t;
                double rr = (rOut * Math.pow(1 - t, 1.3) * (1 - .4 * in) + size * .5) * (1 + .35 * snoise(sd + 8, t * 7 + age * .25));
                pts[j] = at.add(uw[0].scale(Math.cos(a) * rr)).add(uw[1].scale(Math.sin(a) * rr)).add(toEye.normalize().scale(size * .8 * snoise(sd + 6, t * 4 + age * .1)));
                double dash = smooth((vnoise(sd + 9, t * 9 + age * .4) - .3) / .08);
                w[j] = size * weight * (.02 + .07 * vnoise(sd + 7, t * 6 + age * .3)) * Math.sin(Math.PI * t) * dash;
            }
            fxRibbon(c, pts, w, BRUSH, (int) (sd % 977), i % 4 == 3 ? RED_DEEP : INK, .9 * Math.sin(Math.PI * in));
        }
    }

    // ---- Purple: compression ----

    /** Radius of the world Purple: picks up from the size the void left it at, swells to its full size, then the blast carries it on. */
    static double purpleSize(double age) {
        int merge = BlueRedRules.PURPLE_MERGE, det = BlueRedRules.PURPLE_DETONATE;
        double grow = smooth((age - merge) / (double) (det - merge));
        double s = 6 + 6 * grow;
        // The blast bursts out fast and slows (ease-out), without the infinite first step of a square root; then keeps swelling slowly.
        if (age > det) s += 130 * (1 - Math.pow(1 - clamp01((age - det) / 45.0), 2)) + Math.max(0, age - det - 45) * .3;
        return s;
    }

    /** Purple in the world. Before MERGE: the two orbs swirling into one (all the bystanders see of the merge). Then one continuous body that swells,
     *  compresses and bursts: nothing switches shape or size at a phase boundary. */
    static void purple(Ctx c, Vec3 at, double age, Vec3 eye, Vec3 look) {
        Vec3 base = at;
        at = at.add(0, BlueRedRules.purpleRise(age), 0);
        int detonate = BlueRedRules.PURPLE_DETONATE, end = BlueRedRules.PURPLE_CINEMATIC, merge = BlueRedRules.PURPLE_MERGE;
        double blast = clamp01((age - detonate) / 18.0);
        double fade = 1 - clamp01((age - detonate - 18) / (end - detonate - 18.0));
        if (fade <= 0) return;
        double tint = smooth((age - 10) / 30) * fade * clamp01(1 - eye.distanceTo(at) / 120);
        // The world darkens while it holds its breath (owner, round 42).
        double crush = smooth((age - (detonate - 18)) / 16.0) * (1 - blast);
        if (c.body() && tint > 0) sprite(c, eye.add(look.scale(1)), 6, (.4 + .4 * crush) * tint, NIGHT, BlueRedAtlas.SOLID);
        // After the burst the whole world turns violet, however far the camera has been thrown (owner, round 42: "map tím").
        double world = age < detonate ? 0 : smooth((age - detonate) / 6.0) * fade;
        if (c.body() && world > 0) sprite(c, eye.add(look.scale(1.1)), 6, .42 * world, 0x3A0C78, BlueRedAtlas.SOLID);
        double rush = smooth((age - (detonate - 30)) / 30.0) * (1 - clamp01((age - detonate - 10) / 30.0)) * clamp01(1 - eye.distanceTo(at) / 90);
        speedLines(c, eye, look, .85 * rush, PALE);
        double blink = 1 - clamp01(age / BlueRedRules.PURPLE_BLINK);
        if (blink > 0 && c.body() && eye.distanceTo(at) < 60) sprite(c, eye.add(look.scale(.8)), 6, blink * flashScale(), 0xFFFFFF, BlueRedAtlas.SOLID);
        if (age < merge) {
            converge(c, base, age, eye, look);
            return;
        }
        double s = purpleSize(age), grow = smooth((age - merge) / (double) (detonate - merge)), pre = 1 - blast;
        double breathe = 1 + .05 * Math.sin(age * .31) + .035 * Math.sin(age * .77 + 1) - .08 * grow * grow * smooth((age - detonate + 12) / 12.0) * pre;
        double tremble = (.05 + .35 * grow * grow) * pre;
        at = at.add(jitter(age, tremble, 37));
        // The dome stays in view after the burst, translucent (owner, round 42).
        // Once the camera is inside the swelling sphere its skin, rings and bands fade out (owner, 2026-11-20: they whited the screen out).
        double outside = smooth((eye.distanceTo(at) - s * .85) / (s * .35));
        double clear = smooth((age - detonate - 12) / 20.0), bodyAlpha = (1 - blast * .6) * fade * (1 - clear) * outside, still = 1 - .8 * crush;
        if (c.body()) {
            // A softer skin before the blast so the lilac heart shows through (owner's refs).
            // Bright and solid (owner, 2026-11-19).
            // Its texture's detail level blends from one step to the next instead of switching at once (owner, 2026-11-19).
            double w1 = smooth((grow - .5) / .2), w2 = smooth((age - detonate) / 6.0);
            double l0 = bodyAlpha * (1 - w1) * (1 - w2), l1 = bodyAlpha * w1 * (1 - w2), l2 = bodyAlpha * w2;
            if (l0 > .01) orb(c, at, s * breathe, 2, 0, VIOLET, l0);
            if (l1 > .01) orb(c, at, s * breathe, 2, 1, VIOLET, l1);
            if (l2 > .01) orb(c, at, s * breathe, 2, 2, VIOLET, l2);
            debris(c, at, s * 1.4, age, 34, 0x1A0A26, 820, pre * fade, true);
            for (int b = 0; b < 8; b++) {
                Vec3 d = nvec(2300 + b, age * .03 + b).normalize(), along = d.cross(nvec(2310 + b, age * .04)).normalize();
                liveBolt(c, at.add(d.scale(s * 1.01)), at.add(d.add(along.scale(.7)).normalize().scale(s * 1.03)), 7, .3, .03, INK, .8 * pre * fade, 2320 + b * 17, age, .8);
            }
            fall(c, at, s, age, (int) (30 * blast), 1240, 0x1A0A26, blast * fade, false);
            if (age >= detonate) purpleSmoke(c, at, s, age - detonate, fade);
        }
        if (!c.glow()) return;
        // A disc of shockwaves spun off the equator, continuous: each ring is born, widens and fades on its own clock.
        for (int k = 0; k < 3; k++) {
            double ring = ((age + k * 3) / 9.0) % 1;
            fxFlat(c, at, s * (1.1 + 1.6 * ring), SHOCK, 840 + k, ORCHID, .2 * (1 - ring) * pre * fade);
        }
        stormBands(c, at, s, age, 16, fade * blast * (1 - clear) * outside);
        // A thin horizontal line straight through it, far out either side (owner's refs: the 'cosmic' line).
        streaks(c, at, s * .95, s * 4, age, (int) (40 * pre), ORCHID, 860, 1.1, .5 * pre * fade, true);
        streaks(c, at, s * .4, s * 2.2, age, (int) (140 * blast), ORCHID, 1100, 2.6, blast * fade * (1 - clamp01((age - detonate - 10) / 30.0)), false);
        // After the burst the haze thins fast and the white core stays bounded, so the column and the shockwaves read instead of a white-out.
        double haze = 1 - blast;
        // Kept inside the black rim so the ring reads (owner's refs).
        glow(c, at, s * 2.6, (.08 + .12 * blast) * fade * haze, VIOLET);
        // A bright lilac heart inside the dark rim (owner's refs).
        // Bright violet body, white heart (owner, 2026-11-19).
        glow(c, at, s * 1.15, .7 * fade * haze, 0xA040FF);
        glow(c, at, s * .9, .8 * fade * haze, 0xD090FF);
        glow(c, at, s * .5, .9 * fade * haze, 0xFFFFFF);
        // Violet smoke drifting round the rim.
        for (int i = 0; i < 10; i++) {
            Vec3 d = nvec(2500 + i, age * .008 + i).normalize();
            fxSprite(c, at.add(d.scale(s * (1.25 + .5 * hash(2510 + i)))), s * (.6 + .3 * hash(2520 + i)), SMOKE, 2530 + i, 0xC8A0FF, .18 * pre * fade, age * .01 + i);
        }
        // Big glowing bolts flung off the ring (owner's refs): a wide violet glow round a thin bright core.
        for (int b = 0; b < 6 + (int) (4 * grow); b++) {
            Vec3 d = nvec(2600 + b, age * .01 + b * 2).normalize();
            Vec3 from = at.add(d.scale(s * 1.05)), to = from.add(d.add(nvec(2620 + b, age * .02).scale(.6)).normalize().scale(s * (1.2 + 1.5 * hash(2640 + b))));
            liveBolt(c, from, to, 12, .45, s * .05, VIOLET, .35 * pre * fade, 2660 + b * 19, age, .7);
            liveBolt(c, from, to, 12, .45, s * .014, PALE, .9 * pre * fade, 2660 + b * 19, age, .7);
        }
        // Only violet (owner, round 42): the core is pale violet, not white.
        glow(c, at, Math.min(s * .55 * breathe * (1 + 2 * blast), 22), (.7 + .3 * blast) * fade * (1 - .5 * blast), PALE);
        sparks(c, at, s * 1.4, age, 30, PALE, 200);
        // Lightning that never pops: each bolt keeps its identity, its anchor drifts over the sphere and its shape crawls with time.
        // Owner, 2026-11-18: much more lightning on the surface.
        int crawl = 8 + (int) (6 * grow);
        for (int b = 0; b < crawl; b++) {
            Vec3 d = nvec(900 + b, age * .02 + b).normalize(), along = d.cross(nvec(950 + b, age * .03)).normalize();
            Vec3 from = at.add(d.scale(s));
            liveBolt(c, from, at.add(d.add(along.scale(.9)).normalize().scale(s * 1.02)), 7, .3, .09 + .06 * grow, b % 3 == 0 ? PALE : b % 3 == 1 ? ORCHID : VIOLET, pre * fade * still, 1000 + b * 17, age, .8);
        }
        int arcs = 3 + (int) (7 * grow + 16 * blast);
        for (int b = 0; b < arcs; b++) {
            Vec3 d = nvec(1500 + b, age * .015 + b * 3).normalize();
            double reach = s * (.9 + .6 * vnoise(1600 + b, age * .05)) + blast * (40 + 60 * hash(1700 + b));
            Vec3 from = at.add(d.scale(s * .95));
            liveBolt(c, from, from.add(d.scale(reach)), 10, .35, .1 + .12 * blast, b % 2 == 0 ? ORCHID : VIOLET, fade * (blast > 0 ? 1 - clamp01((age - detonate - 8) / 40.0) : still), 1800 + b * 23, age, .6);
        }
        // Purple lightning crawling out over the ground from under it (owner, 2026-11-20), hugging the terrain, from MERGE to just after the blast.
        double creep = smooth((age - merge) / 30.0) * (1 - clamp01((age - detonate - 6) / 14.0)) * fade;
        if (creep > 0 && groundAt != null) groundCrawl(c, at, age, creep);
        // Charged chunks of ground floating round it (owner, 2026-11-19): each crackles down to the ground it tore from, and to a neighbour.
        for (int i = 0; i < CHARGED.size() && i < 40; i++) {
            Vec3 q = CHARGED.get(i);
            double gy = groundAt == null ? q.y - 3 : groundAt.applyAsDouble(q.x, q.z);
            double flick = .45 + .55 * Math.abs(Math.sin(age * 1.9 + i * 2.3));
            liveBolt(c, q, new Vec3(q.x + .4 * snoise(7300 + i, age * .2), gy + .1, q.z + .4 * snoise(7310 + i, age * .2)), 9, .5, .06, i % 2 == 0 ? ORCHID : VIOLET, flick * pre * fade, 7320 + i * 13, age, 1);
            if (i > 0 && q.distanceTo(CHARGED.get(i - 1)) < 9)
                liveBolt(c, q, CHARGED.get(i - 1), 6, .45, .02, PALE, .6 * flick * pre * fade, 7330 + i * 17, age, 1);
            glow(c, q, 2, .5 * flick * pre * fade, VIOLET);
            if (!Double.isNaN(gy)) glow(c, new Vec3(q.x, gy + .3, q.z), 2.5, .35 * flick * pre * fade, ORCHID);
        }
        // Lightning hammering the ground round it, each strike blowing out a pit (the server carves it on the same tick).
        if (groundAt != null) for (int k = 0; k < BlueRedRules.PURPLE_STRIKES; k++) {
            double since = age - BlueRedRules.strikeTick(k);
            if (since < 0 || since > 9) continue;
            double[] o = BlueRedRules.strikeOffset(k);
            double gx = Math.floor(at.x + o[0]) + .5, gz = Math.floor(at.z + o[1]) + .5;
            Vec3 g = new Vec3(gx, groundAt.applyAsDouble(gx, gz), gz), from = at.add(g.subtract(at).normalize().scale(s));
            double a = 1 - since / 9;
            for (int j = 0; j < 3; j++)
                liveBolt(c, from, g.add(nvec(1950 + k * 3 + j, 0).scale(.6)), 14, .45, (j == 0 ? .3 : .12) * (.5 + .5 * a), j == 0 ? PALE : ORCHID, a, 1960 + k * 41 + j * 7, age, .9);
            glow(c, g, 3 + 5 * (1 - a), a, PALE);
            glow(c, g, 7, .6 * a, ORCHID);
            fxFlat(c, g.add(0, .1, 0), 1.5 + since * 1.2, SHOCK, 1990 + k, PALE, .8 * a);
            sparks(c, g, 1 + since * .5, age, 24, PALE, 2000 + k * 5);
        }
        if (blast > 0) {
            double ring = (1 - blast) * fade * outside;
            fxFlat(c, at.add(0, -1, 0), s * 1.8, SHOCK, 210, ORCHID, ring);
            fxSprite(c, at, s * 2.4, RAYS, 211, ORCHID, ring, age * .01);
            // A second, wider shock and a ground ring for a bigger, prettier blast.
            fxFlat(c, at.add(0, -1, 0), s * 2.6 * blast, SHOCK, 213, ORCHID, ring * .8);
            fxSprite(c, at, s * 3 * blast, SHOCK, 214, ORCHID, ring * .6, 0);
            fxSprite(c, at, s * 1.3, SHOCK, 212, ORCHID, ring, 0);
        }
        if (age >= detonate) purpleBurst(c, at, age - detonate, fade);
        if (age >= detonate) {
            // Shockwaves rolling out over the map one after another (the column was dropped, owner round 37).
            double after = age - detonate, ground = Double.isNaN(groundY) ? at.y - BlueRedRules.PURPLE_LIFT - 2 : groundY;
            Vec3 foot = new Vec3(at.x, ground + .15, at.z);
            for (int r = 0; r < 7; r++) {
                double ra = after - r * 5;
                if (ra < 0 || ra > 34) continue;
                pressureWave(c, foot, new Vec3(0, 1, 0), 5 + ra * 2.8, 4.5 * (1 - ra / 34), ra, 2130 + r, (1 - ra / 34) * fade, ORCHID);
            }
        }
        double white = age < detonate ? 0 : 1 - clamp01((age - detonate) / 4.0);
        if (white > 0) sprite(c, eye.add(look.scale(.8)), 6, .85 * white * white * flashScale(), PALE, BlueRedAtlas.SOLID);
    }

    /** The burst itself (owner, 2026-11-20: polish the blast), {@code after} ticks past the detonation: a flash at the heart; spikes of light
     *  shot out every way, fast, thinning and gone in about half a second; and a shower of small bright sparks flung out that arc down and
     *  wink out over a few seconds. */
    static void purpleBurst(Ctx c, Vec3 at, double after, double fade) {
        if (after < 4) {
            double k = after / 4;
            glow(c, at, 18 + 50 * k, (1 - k) * fade * flashScale(), PALE);
            glow(c, at, 8 + 20 * k, (1 - k) * fade * flashScale(), 0xFFFFFF);
        }
        double spike = 1 - clamp01(after / 14);
        if (spike > 0) for (int i = 0; i < 30; i++) {
            long sd = 3100 + i * 37L;
            Vec3 d = new Vec3(hash(sd) * 2 - 1, (hash(sd + 1) * 2 - 1) * .55, hash(sd + 2) * 2 - 1).normalize();
            double len = (50 + 130 * hash(sd + 3)) * (1 - Math.pow(1 - clamp01(after / 8), 3)), w = (.6 + 1.4 * hash(sd + 4)) * spike;
            Vec3 from = at.add(d.scale(6 + after * 2));
            fxBeam(c, from, from.add(d.scale(len)), w, w * .05, BOLT, (int) (3150 + i), i % 3 == 0 ? 0xFFFFFF : i % 3 == 1 ? PALE : ORCHID, Math.pow(spike, 1.5) * fade);
        }
        for (int i = 0; i < 320; i++) {
            long sd = 3400 + i * 29L;
            double life = 30 + 45 * hash(sd), t = after - 2 * hash(sd + 1);
            if (t < 0 || t > life) continue;
            Vec3 d = new Vec3(hash(sd + 2) * 2 - 1, hash(sd + 3) * 1.4 - .2, hash(sd + 4) * 2 - 1).normalize();
            double v = 1.2 + 3.2 * hash(sd + 5), drag = (1 - Math.exp(-t / 12)) * 12;
            Vec3 q = at.add(d.scale(10 + v * drag)).add(0, -.012 * t * t, 0);
            if (q.distanceTo(c.eye) < 2) continue;
            double a = (1 - t / life) * fade, size = .22 + .22 * hash(sd + 6);
            glow(c, q, size, a, i % 3 == 0 ? ORCHID : PALE);
            glow(c, q, size * .4, a, 0xFFFFFF);
        }
    }

    /** Lightning crawling over the ground from the point under {@code at}: each arc strikes out along the terrain, holds a few ticks, fades
     *  and strikes again somewhere else. */
    static void groundCrawl(Ctx c, Vec3 at, double age, double alpha) {
        for (int i = 0; i < 9; i++) {
            long sd = 4900 + i * 53L;
            double life = 6 + 5 * hash(sd), ph = age / life + hash(sd + 1);
            long cyc = (long) Math.floor(ph);
            double u = ph - cyc;
            long cs = sd + cyc * 7919L;
            double flash = u < .15 ? 1 : 1 - smooth((u - .15) / .85), ang = hash(cs) * Math.PI * 2, len = 10 + 28 * hash(cs + 1);
            int hops = 6;
            Vec3 prev = null;
            for (int k = 0; k <= hops; k++) {
                double d = 2 + len * k / hops, a = ang + .35 * snoise(cs + 2, k * .7);
                double x = at.x + Math.cos(a) * d, z = at.z + Math.sin(a) * d, y = groundAt.applyAsDouble(x, z);
                if (Double.isNaN(y)) break;
                Vec3 q = new Vec3(x, y + .25, z);
                if (prev != null) {
                    double w = (k < 2 ? .12 : .07) * (1 - .5 * k / hops);
                    liveBolt(c, prev, q, 5, .5, w, PALE, alpha * flash, cs * 7 + k * 13, age, 1);
                    liveBolt(c, prev, q, 5, .5, w * 3, VIOLET, .35 * alpha * flash, cs * 7 + k * 13, age, 1);
                }
                prev = q;
            }
        }
    }

    /** After the blast (owner, 2026-11-20): violet rocks rain down round the crater for a few seconds, each rolling as it falls and puffing
     *  sparks where it lands, and small lightning keeps flickering over the crater floor. {@code base}: Purple's base point. */
    /** How long the aftermath plays after the blast (owner, 2026-11-20: "longer, it's pretty"): about twenty seconds. */
    static final int AFTERMATH_TICKS = 420;

    static void purpleAftermath(Ctx c, Vec3 base, double after) {
        if (groundAt == null || after > AFTERMATH_TICKS) return;
        double ground0 = groundAt.applyAsDouble(base.x, base.z);
        if (Double.isNaN(ground0)) return;
        double r = net.godwand.config.GodWandConfig.SERVER.purpleRadius;
        // Most fall in the first seconds, then fewer and fewer, the last ones well after the dust has settled.
        for (int i = 0; i < 200; i++) {
            long sd = 5600 + i * 31L;
            double t = after - (20 + (AFTERMATH_TICKS - 80) * Math.pow(hash(sd), 1.6));
            if (t < 0) continue;
            double a = hash(sd + 1) * Math.PI * 2, d = r * (.2 + 1.1 * Math.sqrt(hash(sd + 2)));
            double x = base.x + Math.cos(a) * d, z = base.z + Math.sin(a) * d, gy = groundAt.applyAsDouble(x, z);
            if (Double.isNaN(gy)) continue;
            double y0 = gy + 25 + 35 * hash(sd + 3), y = y0 - .5 * .05 * t * t, size = .35 + .55 * hash(sd + 4);
            Vec3 q = new Vec3(x + .02 * t * snoise(sd + 5, 0), Math.max(gy + size * .5, y), z);
            if (y > gy) {
                if (c.body()) fxSprite(c, q, size, DEBRIS, (int) (sd % 977), i % 3 == 0 ? 0x2A0E50 : i % 3 == 1 ? 0x5A2A9A : 0x8A4AD0, .95, t * (.15 + .2 * hash(sd + 6)));
                else glow(c, q.add(0, size * 1.5, 0), size * .9, .25, ORCHID);
            } else if (c.glow()) {
                double land = Math.sqrt(2 * (y0 - gy) / .05), since = t - land;
                if (since < 8) {
                    glow(c, q, .6 + .15 * since, 1 - since / 8, PALE);
                    sparks(c, q, .3 + .15 * since, after, 6, ORCHID, (int) (5700 + i));
                }
            }
        }
        if (!c.glow()) return;
        double fade = 1 - clamp01((after - (AFTERMATH_TICKS - 80)) / 80.0);
        for (int i = 0; i < 12; i++) {
            long sd = 5900 + i * 43L;
            double life = 8 + 14 * hash(sd), ph = after / life + hash(sd + 1);
            long cyc = (long) Math.floor(ph);
            double u = ph - cyc;
            if (u > .3) continue;
            long cs = sd + cyc * 7919L;
            double a = hash(cs) * Math.PI * 2, d = r * .85 * Math.sqrt(hash(cs + 1));
            double x = base.x + Math.cos(a) * d, z = base.z + Math.sin(a) * d, gy = groundAt.applyAsDouble(x, z);
            if (Double.isNaN(gy)) continue;
            Vec3 p0 = new Vec3(x, gy + .2, z), p1 = p0.add(new Vec3(snoise(cs + 2, 0), 0, snoise(cs + 3, 0)).normalize().scale(2 + 4 * hash(cs + 4)));
            double y1 = groundAt.applyAsDouble(p1.x, p1.z);
            if (!Double.isNaN(y1)) p1 = new Vec3(p1.x, y1 + .2, p1.z);
            double fl = (1 - u / .3) * fade;
            liveBolt(c, p0, p1, 6, .5, .05, PALE, fl, cs * 3, after, 1);
            liveBolt(c, p0, p1, 6, .5, .16, VIOLET, .4 * fl, cs * 3, after, 1);
            glow(c, p0, .8, .5 * fl, ORCHID);
        }
    }

    /** The small Purple (Red met an unready Blue, owner 2026-11-20): no cutscene, no tear, no storm bands. A flick of blue and red snapping
     *  together; then for 2.5 s a violet sphere swirling into its white core swells to 4-5 blocks, throbbing, lightning crackling thicker
     *  round it and crawling over the ground; it squeezes for a beat and bursts: a flash, a wide shock ring, rays, spikes of light, a
     *  shower of sparks, violet smoke. {@code power} 0.4..0.8 sizes it. */
    static void smallPurple(Ctx c, Vec3 at, double age, double power, Vec3 look) {
        int det = BlueRedRules.SMALL_DETONATE;
        double s0 = 2.4 + 3 * power;
        if (age < det) {
            double form = smooth(age / 12.0), charge = clamp01(age / det), squeeze = smooth((age - (det - 8)) / 8.0);
            double s = s0 * (.3 + .7 * form) * (1 + .06 * Math.sin(age * .9) * charge) * (1 - .22 * squeeze);
            if (c.glow() && age < 6) {
                Vec3 side = look.cross(new Vec3(0, 1, 0));
                side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
                double k = age / 6;
                glow(c, at.add(side.scale(2 * (1 - k))), 1, 1 - k, CYAN);
                glow(c, at.subtract(side.scale(2 * (1 - k))), 1, 1 - k, RED);
            }
            if (c.body()) {
                orb(c, at, s, 2, charge > .6 ? 5 : 4, VIOLET, form);
                for (int i = 0; i < 6; i++) {
                    Vec3 d = nvec(9690 + i, age * .02 + i).normalize();
                    fxSprite(c, at.add(d.scale(s * (1.15 + .3 * hash(9695 + i)))), s * .5, SMOKE, 9698 + i, i % 2 == 0 ? 0x4A1A80 : 0x2A0650, .3 * form, age * .02 + i);
                }
            }
            inflow(c, at, s, age, 30 + (int) (50 * charge), ORCHID, PALE, 9600, form);
            if (groundAt != null) groundCrawl(c, at, age, .3 + .7 * charge);
            if (!c.glow()) return;
            glow(c, at, s * .5, .9 * form, 0xFFFFFF);
            glow(c, at, s * 1.7, (.35 + .25 * charge) * form, VIOLET);
            for (int b = 0; b < 4 + (int) (10 * charge) + (int) (6 * squeeze); b++) {
                Vec3 d = nvec(9650 + b, age * .3).normalize(), d2 = d.add(nvec(9660 + b, age * .35).scale(.8)).normalize();
                liveBolt(c, at.add(d.scale(s * .92)), at.add(d2.scale(s * (1.3 + 1.1 * vnoise(9670 + b, age * .4) + squeeze))), 7, .45, .04 + .05 * charge,
                        b % 3 == 0 ? 0xFFFFFF : b % 3 == 1 ? PALE : ORCHID, form, 9680 + b * 13, age, 1);
            }
            sparks(c, at, s * 1.6, age, (int) (20 + 30 * charge), PALE, 9690);
            return;
        }
        double t = age - det, k = clamp01(t / 18), fade = 1 - clamp01((t - 18) / 30);
        if (c.body()) for (int i = 0; i < 16; i++) {
            Vec3 d = nvec(9700 + i, 0).normalize();
            fxSprite(c, at.add(d.scale(s0 * (.6 + 3.5 * k))), s0 * (.8 + 1.6 * k), SMOKE, 9710 + i, i % 3 == 0 ? 0x4A1A80 : i % 3 == 1 ? 0x7A3AC0 : NIGHT, .5 * fade, k + i);
        }
        if (!c.glow()) return;
        if (t < 4) {
            glow(c, at, s0 * (2 + 4 * t), (1 - t / 4) * flashScale(), 0xFFFFFF);
            glow(c, at, s0 * (3 + 5 * t), (1 - t / 4) * flashScale(), PALE);
        }
        fxSprite(c, at, s0 * (1.5 + 10 * k), SHOCK, 9720, ORCHID, .9 * (1 - k), 0);
        fxSprite(c, at, s0 * (1 + 6 * k), SHOCK, 9722, PALE, .6 * (1 - k), 0);
        fxSprite(c, at, s0 * (1.5 + 5 * k), RAYS, 9721, PALE, .8 * (1 - k), k);
        glow(c, at, s0 * (2.5 - k), .7 * (1 - k), VIOLET);
        double spike = 1 - clamp01(t / 12);
        if (spike > 0) for (int i = 0; i < 16; i++) {
            long sd = 9760 + i * 37L;
            Vec3 d = new Vec3(hash(sd) * 2 - 1, (hash(sd + 1) * 2 - 1) * .6, hash(sd + 2) * 2 - 1).normalize();
            double len = (12 + 30 * hash(sd + 3)) * (1 - Math.pow(1 - clamp01(t / 6), 3)), w = (.3 + .6 * hash(sd + 4)) * spike;
            Vec3 from = at.add(d.scale(s0 * .6));
            fxBeam(c, from, from.add(d.scale(len)), w, w * .05, BOLT, 9770 + i, i % 2 == 0 ? 0xFFFFFF : PALE, Math.pow(spike, 1.5) * fade);
        }
        for (int i = 0; i < 120; i++) {
            long sd = 9800 + i * 29L;
            double life = 18 + 22 * hash(sd), u = t / life;
            if (u >= 1) continue;
            Vec3 d = new Vec3(hash(sd + 2) * 2 - 1, hash(sd + 3) * 1.4 - .2, hash(sd + 4) * 2 - 1).normalize();
            Vec3 q = at.add(d.scale(s0 * .8 + (.8 + 1.6 * hash(sd + 5)) * (1 - Math.exp(-t / 8)) * 8)).add(0, -.01 * t * t, 0);
            if (q.distanceTo(c.eye) < 2) continue;
            glow(c, q, .15 + .12 * hash(sd + 6), 1 - u, i % 3 == 0 ? ORCHID : PALE);
        }
        for (int b = 0; b < 8; b++) {
            Vec3 d = nvec(9740 + b, 0).normalize();
            liveBolt(c, at.add(d.scale(s0 * .5)), at.add(d.scale(s0 * (2 + 5 * k))), 8, .45, .06 * (1 - k), b % 2 == 0 ? PALE : ORCHID, 1 - k, 9750 + b * 11, age, 1);
        }
    }

    /** Charged chunks floating round Purple this frame (their centres), set by the renderer. */
    static final List<Vec3> CHARGED = new ArrayList<>();

    /** Violet smoke after the burst (owner, round 42): a ring of heavy clouds rolling out over the ground far past the sphere, and billows
     *  heaving off the sphere itself. Dark and mid violets only. */
    static void purpleSmoke(Ctx c, Vec3 at, double s, double after, double fade) {
        double ground = Double.isNaN(groundY) ? at.y - BlueRedRules.PURPLE_LIFT - 2 : groundY;
        for (int i = 0; i < 40; i++) {
            long sd = 6100 + i;
            double a = i * 2.39996 + hash(sd) * .5, k = clamp01(after / (55 + 35 * hash(sd + 2)));
            double rad = 6 + (120 + 50 * hash(sd + 1)) * (1 - (1 - k) * (1 - k));
            double y = ground + 2 + 9 * hash(sd + 3) + after * .1 * hash(sd + 4), size = 9 + 16 * hash(sd + 5) + after * .3;
            double al = smooth(after / 5) * (1 - clamp01((after - 50) / 60)) * .5 * fade;
            fxSprite(c, new Vec3(at.x + Math.cos(a) * rad, y, at.z + Math.sin(a) * rad), size, SMOKE, (int) (sd % 977),
                    i % 3 == 0 ? NIGHT : i % 3 == 1 ? 0x4A1A80 : 0x7A3AC0, al, after * .008 + i);
        }
        // Dust thrown up behind the front (owner, round 45): violet plumes rising and leaning outward as the shock passes.
        double front = 200 * (1 - Math.pow(1 - clamp01(after / 60), 2));
        for (int i = 0; i < 40; i++) {
            long sd = 6500 + i;
            double a = i * 2.39996 + hash(sd) * .7, r = Math.max(6, front * (.25 + .7 * hash(sd + 1))), life = clamp01((after - r / 160 * 40) / 50);
            if (life <= 0 || life >= 1) continue;
            double y = ground + 1 + 22 * life * (.5 + hash(sd + 2)), size = 4 + 14 * life + 6 * hash(sd + 3);
            Vec3 q = new Vec3(at.x + Math.cos(a) * (r + 8 * life), y, at.z + Math.sin(a) * (r + 8 * life));
            fxSprite(c, q, size, SMOKE, (int) (sd % 977), i % 3 == 0 ? 0x6A2AB0 : i % 3 == 1 ? 0x4A1A80 : 0x8A4AD0, .45 * Math.sin(Math.PI * life) * fade, after * .01 + i);
        }
        for (int i = 0; i < 16; i++) {
            long sd = 6300 + i;
            Vec3 d = nvec(sd, 0).normalize();
            double grow = 1 - Math.pow(1 - clamp01(after / 40), 2);
            fxSprite(c, at.add(d.scale(s * (.85 + .35 * grow * hash(sd + 1)))), s * (.3 + .25 * hash(sd + 2)) * (1 + .6 * grow), SMOKE, (int) (sd % 977),
                    i % 2 == 0 ? 0x5A2096 : NIGHT, .35 * smooth(after / 4) * (1 - clamp01((after - 30) / 50)) * fade, after * .01 + i);
        }
    }

    /** Cinematic black bars, {@code amount} of the view height each (top and bottom), as camera-facing black quads half a block out. */
    static void blackBars(Ctx c, Vec3 eye, Vec3 look, double amount, double tanHalf) {
        if (amount <= 0) return;
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look).normalize(), o = eye.add(look.scale(.5));
        double h = .5 * tanHalf, w = h * 5;
        for (int e = -1; e <= 1; e += 2) {
            Vec3 inner = o.add(up.scale(e * h * (1 - 2 * amount))), outer = o.add(up.scale(e * h * 3));
            fxQuad(c, inner.subtract(right.scale(w)), inner.add(right.scale(w)), outer.add(right.scale(w)), outer.subtract(right.scale(w)), SOLID_FX, 0, 0x000000, 1, 1, 1, 1, 0, 1);
        }
    }

    /** Purple's vortex bands: each on its own slowly tumbling plane and turning at its own speed, its radius breathing and its outline warped along
     *  its length, so the bands never sit on clean concentric ellipses. */
    static void stormBands(Ctx c, Vec3 at, double s, double age, int count, double alpha) {
        for (int i = 0; i < count; i++) {
            long sd = 2000 + i * 61L;
            Vec3 n = nvec(sd, age * .004 + i).add(new Vec3(0, .6, 0)).normalize();
            Vec3[] uw = basis(n);
            double speed = (.06 + .14 * hash(sd + 1)) * (hash(sd + 2) < .5 ? 1 : -1), sweep = 1.6 + 2.6 * hash(sd + 3);
            double base = s * (.95 + .35 * hash(sd + 4)) * (1 + .12 * Math.sin(age * (.03 + .03 * hash(sd + 5)) + i));
            double width = s * (.01 + .022 * hash(sd + 6));
            int k = 30;
            Vec3[] pts = new Vec3[k + 1];
            double[] widths = new double[k + 1];
            double a0 = hash(sd + 7) * Math.PI * 2 + age * speed;
            for (int j = 0; j <= k; j++) {
                double t = (double) j / k, a = a0 + t * sweep * Math.signum(speed);
                double rad = base * (1 + .14 * snoise(sd + 8, t * 3 + age * .05));
                pts[j] = at.add(uw[0].scale(Math.cos(a) * rad)).add(uw[1].scale(Math.sin(a) * rad)).add(n.scale(s * .12 * snoise(sd + 9, t * 2 + age * .04)));
                widths[j] = width * Math.sin(Math.PI * t);
            }
            int color = i % 5 == 0 ? PALE : i % 2 == 0 ? ORCHID : VIOLET;
            fxRibbon(c, pts, widths, BOLT, (int) (sd % 991), color, (i % 5 == 0 ? .6 : .8) * alpha);
        }
    }

    // ---- Convergence (owner, 2026-11-18): contact, orbit, build-up, face-off ----

    /** Distance between the two orbs: they knock apart on contact, chase each other wide, tighten as the spin climbs, then part to the face-off. */
    static double separation(double age) {
        int b = BlueRedRules.PURPLE_BLINK, ct = BlueRedRules.PURPLE_CONTACT, pl = BlueRedRules.PURPLE_PLAY, le = BlueRedRules.PURPLE_LIFT_END, fa = BlueRedRules.PURPLE_FACE;
        // Owner, 2026-11-18: knocked apart on contact, then held close while they spin, and drifting apart again as they slow down (the fade to
        // the 2D act starts while they part).
        // Round 37 (owner): close at first, slowly drifting apart, and still parting when the black falls over them.
        int se = BlueRedRules.PURPLE_SWIRL_END;
        // Round 40 (owner): knocked well apart on contact, then drawing in gradually as the spin builds.
        if (age < ct) return .3 + 5.2 * (1 - Math.pow(1 - clamp01((age - b) / (double) (ct - b)), 2));
        double spinning = 5.5 - 2.3 * smooth((age - ct) / (double) (le - ct)) + .1 * Math.sin((age - ct) * .17);
        return spinning + (FACE_GAP - spinning) * smooth((age - le) / (double) (se + 8 - le));
    }

    /** How far apart they hold at the face-off. */
    // Closer at the stop (owner, round 41): the surfaces nearly touch before the cut to 2D.
    static final double FACE_GAP = 3.0;

    /** Slow motion before they meet (owner, 2026-11-20): the clock the things round the orbs run on. Real time until tick 94, then easing
     *  down to a fifth of real speed by 104 and held there through the face-off to the end of the 3D act, as if time stood still round them. */
    static double slowAge(double age) {
        double a = 94, b = 104, end = BlueRedRules.PURPLE_SWIRL_END, slow = .2;
        if (age <= a) return age;
        if (age <= b) return a + (age - a) - (1 - slow) * (age - a) * (age - a) / (2 * (b - a));
        double atB = a + (b - a) * (1 + slow) / 2;
        return atB + slow * (Math.min(age, end) - b) + Math.max(0, age - end);
    }

    /** Orbit speed in radians per tick: a moderate start, climbing hard through the build-up (about 3.5 turns a second at the peak), braked to
     *  a stop for the face-off. */
    static double spin(double age) {
        int ct = BlueRedRules.PURPLE_CONTACT, pl = BlueRedRules.PURPLE_PLAY, le = BlueRedRules.PURPLE_LIFT_END, fa = BlueRedRules.PURPLE_FACE;
        // Slower than before (owner, 2026-11-18): about 2.5 turns a second at the peak.
        double up = .08 + .16 * smooth((age - ct) / (double) (pl - ct)) + .46 * Math.pow(clamp01((age - pl) / (double) (le - pl)), 1.5);
        // Inertia (owner, round 37): the brake bites hardest at once and the pair coasts on ever slower, still turning a little as the black
        // falls; it only comes to rest after the fade (FACE_REST), never a sudden stop.
        double k = clamp01((age - le) / (double) (FACE_REST - le));
        return up * Math.pow(1 - k, 2.4);
    }

    /** Orbit angle: the integral of spin (half-tick steps, deterministic). */
    static double orbitAngle(double age) {
        double a = 0;
        for (double t = 0; t < age; t += .5) a += spin(t) * Math.min(.5, age - t);
        return a;
    }

    /** {blue, red} on the orbit: a precessing, tilted plane, unequal breathing radii, a gap that wanders while they chase and settles to
     *  opposite sides as the spin climbs. */
    /** The orbit plane's normal at this age: tilted and slowly precessing. */
    // Owner, 2026-11-18: level, never tilted.
    static Vec3 orbitNormal(double age) { return new Vec3(0, 1, 0); }

    static Vec3[] orbitOffsets(double age) {
        double th = orbitAngle(age), d = separation(age);
        double settle = smooth((age - BlueRedRules.PURPLE_PLAY) / (double) (BlueRedRules.PURPLE_LIFT_END - BlueRedRules.PURPLE_PLAY));
        Vec3 n = orbitNormal(age);
        Vec3[] uw = basis(n);
        // Both ride the same circle (the electric orbit), opposite each other; early on the gap wanders a little (they chase).
        // Owner, 2026-11-18: Blue holds still at the centre (rising with the pair) while Red circles it, slow then faster; through the build-up
        // Blue is drawn out onto the orbit too, until the two chase each other on opposite sides. Their distance stays d throughout.
        double gap = Math.PI + .3 * Math.sin(age * .07) * (1 - settle), join = settle;
        // Red circles close round Blue (owner, 2026-11-18: not far away), then both settle on opposite sides.
        double rb = d * .5 * join, rr = d * .5 * (1 + .35 * (1 - join));
        Vec3 blue = uw[0].scale(Math.cos(th) * rb).add(uw[1].scale(Math.sin(th) * rb));
        Vec3 red = uw[0].scale(Math.cos(th + gap) * rr).add(uw[1].scale(Math.sin(th + gap) * rr));
        return new Vec3[]{yaw(blue, orbitPhase), yaw(red, orbitPhase)};
    }

    /** Turns the whole orbit about the vertical so Red opens on the watching player's side (set by PurpleCinematic.start). */
    static double orbitPhase;

    static Vec3 yaw(Vec3 v, double a) {
        return new Vec3(v.x * Math.cos(a) - v.z * Math.sin(a), v.y, v.x * Math.sin(a) + v.z * Math.cos(a));
    }

    /** The face-off axis: where Red's orbit leaves it when the spin stops, made level. Red ends on +axis, Blue on -axis. */
    /** Where the coasting orbit finally rests (after the fade to black). */
    static final int FACE_REST = BlueRedRules.PURPLE_SWIRL_END + 12;

    static Vec3 faceAxis() {
        Vec3 r = orbitOffsets(FACE_REST)[1];
        Vec3 h = new Vec3(r.x, 0, r.z);
        return h.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : h.normalize();
    }

    /** {blue, red} offsets from the pair's centre: on the orbit, then, while the spin brakes, eased onto the level face-off line (from where the
     *  orbit is already taking them, so nothing jumps), held there breathing. */
    static Vec3[] convergeOffsets(double age) {
        return orbitOffsets(age);
    }

    /** The convergence in the world, from the stored base (where Blue was): the twin orbs, each trailing its real past path (sampled from the
     *  same motion) that grows into a ring as they speed up and fades as they brake; contact flash and sparks; lightning between them,
     *  thickening with the spin, crackling thin across the gap in the face-off. After SWIRL_END (bystanders only) the two glide together into
     *  a violet point that grows into the Purple that takes over at MERGE. */
    static void converge(Ctx c, Vec3 base, double age, Vec3 eye, Vec3 look) {
        int ct = BlueRedRules.PURPLE_CONTACT, pl = BlueRedRules.PURPLE_PLAY, le = BlueRedRules.PURPLE_LIFT_END, fa = BlueRedRules.PURPLE_FACE, se = BlueRedRules.PURPLE_SWIRL_END;
        Vec3 center = base.add(0, BlueRedRules.purpleRise(age), 0);
        if (age >= se + 12) {
            double born = smooth((age - se) / (double) (BlueRedRules.PURPLE_MERGE - se));
            Vec3 a = faceAxis().scale(FACE_GAP / 2 * (1 - born));
            double f0 = c.fade;
            c.fade = f0 * (1 - born);
            twinOrb(c, center.subtract(a), 1.5 * (1 - .5 * born), age, BLUE_P, Vec3.ZERO, 0, 1 - born);
            twinOrb(c, center.add(a), 1.5 * (1 - .5 * born), age, RED_P, Vec3.ZERO, 77777, 1 - born);
            c.fade = f0;
            if (c.body()) orb(c, center, .3 + 5.7 * born, 2, 1, VIOLET, born);
            if (c.glow()) { glow(c, center, 2 + 10 * born, .7 * born, VIOLET); stormBands(c, center, .3 + 5.7 * born, age, 10, born); }
            return;
        }
        Vec3[] o = convergeOffsets(age);
        Vec3 blue = center.add(o[0]), red = center.add(o[1]);
        if (age > ct) {
            double in = smooth((age - ct) / 8.0);
            double ring = in * (1 - smooth((age - le) / (double) (fa - le + 10)));
            spinBand(c, base, age, 0, ring);
            spinBand(c, base, age, 1, ring);
            coreStorm(c, center, age, in);
            drag(c, base, age, 0, BLUE_P, in);
            drag(c, base, age, 1, RED_P, in);
        }
        // Owner, 2026-11-18: once they meet they are the same kind of orb, the same size, the same effect; only the colour differs.
        Vec3[] o0 = convergeOffsets(Math.max(0, age - .5));
        Vec3 c0 = base.add(0, BlueRedRules.purpleRise(Math.max(0, age - .5)), 0);
        double surge = smooth((age - le) / (double) (fa - le));
        mapLight(c, blue, 9 + 3 * surge, 0x2A7CFF, .5 + .3 * surge);
        mapLight(c, red, 9 + 3 * surge, RED, .5 + .3 * surge);
        // Red starts small and grows (owner, round 37); the farther orb is drawn first, so the nearer one covers it.
        double redR = .4 + .7 * smooth((age - ct) / (double) (le - ct));
        if (age > ct) { orbTrail(c, base, age, 0, BLUE_P, 1.1); orbTrail(c, base, age, 1, RED_P, redR); }
        Vec3 vb = blue.subtract(c0.add(o0[0])).scale(2), vr = red.subtract(c0.add(o0[1])).scale(2);
        // Each pulls in motes of its own colour (owner, 2026-11-19): red into Red, blue into Blue.
        if (age > ct) {
            double pull = smooth((age - ct) / 8.0);
            inflow(c, blue, 1.1, slowAge(age), 45, CYAN, ROYAL, 7700, pull);
            inflow(c, red, redR, slowAge(age), 45, RED_LIGHT, RED, 7800, pull);
        }
        // Their smoke and crackle run on the slowed clock too; only the orbs themselves keep moving at speed.
        double amb = slowAge(age);
        if (red.distanceTo(c.eye) > blue.distanceTo(c.eye)) {
            twinOrb(c, red, redR, amb, RED_P, vr, 77777, surge);
            twinOrb(c, blue, 1.1, amb, BLUE_P, vb, 0, surge);
        } else {
            twinOrb(c, blue, 1.1, amb, BLUE_P, vb, 0, surge);
            twinOrb(c, red, redR, amb, RED_P, vr, 77777, surge);
        }
        // Small, bright purple lightning in the middle while they face off.
        if (c.glow() && surge > 0) {
            double flick = .6 + .4 * Math.abs(Math.sin(age * 2.3));
            // Less white where they nearly touch at the stop (owner, round 42).
            glow(c, center, .4 + .3 * flick, .3 * surge * flick, ORCHID);
            glow(c, center, 1.4, .3 * surge, VIOLET);
            for (int b = 0; b < 6; b++) {
                Vec3 d = nvec(3880 + b, age * .35).normalize();
                liveBolt(c, center, center.add(d.scale(.7 + .9 * vnoise(3890 + b, age * .5))), 5, .5, .018, b % 2 == 0 ? ORCHID : VIOLET, surge, 3895 + b * 11, age, 1);
            }
        }
        double spinNow = spin(age) / .7;
        if (c.body()) {
            // Black flash (owner, round 38): the link's lightning has a black core, its colour only at the edges.
            int dark = (int) (2 + 4 * Math.max(spinNow, surge));
            for (int j = 0; j < dark; j++) {
                Vec3 from = red.add(nvec(3000 + j, age * .06).scale(.5)), to = blue.add(nvec(3100 + j, age * .06).scale(.5));
                liveBolt(c, from, to, 9, .3, .008 + .01 * spinNow, INK, .9, 3200 + j * 29, age, .9);
            }
        }
        if (!c.glow()) return;
        double contact = 1 - clamp01((age - BlueRedRules.PURPLE_BLINK) / (double) (ct - BlueRedRules.PURPLE_BLINK));
        if (contact > 0) {
            fxSprite(c, center, 1 + 5 * (1 - contact), SHOCK, 3900, PALE, contact, 0);
            sparks(c, center, 1 + 3 * (1 - contact), age, 40, ORCHID, 3910);
        }
        double build = spin(age) / .7, hold = smooth((age - fa) / 8.0);
        // The link between them (owner, 2026-11-18): thick, bright and crowded with lightning, sparks along it.
        double link = Math.max(contact, Math.max(build, surge));
        int bolts = (int) (3 + 4 * contact + 7 * build + 2 * surge);
        for (int j = 0; j < bolts; j++) {
            Vec3 from = red.add(nvec(3000 + j, age * .06).scale(.5)), to = blue.add(nvec(3100 + j, age * .06).scale(.5));
            // Thinner and coloured: red from Red's side, blue from Blue's, violet between; dimmer so the colour shows.
            liveBolt(c, from, to, 9, .3, .014 + .025 * build + .02 * surge, j % 3 == 0 ? RED_LIGHT : j % 3 == 1 ? CYAN : ORCHID, .6 - .2 * surge, 3200 + j * 29, age, .9);
        }
        // Seen up close at the face-off, so kept from burning the frame white.
        fxBeam(c, red, blue, .2 + .25 * link, .2 + .25 * link, FLOW, 3290, ORCHID, (.22 - .1 * surge) * link);
        for (int j = 0; j < 4; j++) sparks(c, red.add(blue.subtract(red).scale(.2 + .2 * j)), .8 + .6 * link, age * 1.5, (int) (8 + 22 * link - 14 * surge), j % 2 == 0 ? ORCHID : VIOLET, 3300 + j * 7);
        if (contact > 0 && !Double.isNaN(groundY)) {
            double k = 1 - contact;
            pressureWave(c, new Vec3(center.x, groundY + .1, center.z), new Vec3(0, 1, 0), 2 + 22 * k, 2.6 * contact, age, 3960, contact, SHOCK_WHITE);
        }
        if (build > .2) streaks(c, center, .3, separation(age) + 4, age, (int) (50 * build), ORCHID, 3400, 2, build, true);
    }

    /** A trail behind one orb (owner, round 38, added to everything else): a soft ribbon along its real path over the last few ticks, as wide
     *  as the orb at its head and thinning behind, in its own colours, fading at the tail. */
    private static void orbTrail(Ctx c, Vec3 base, double age, int which, Palette p, double r) {
        if (!c.body()) return;
        double span = Math.min(10, 1.6 / Math.max(.05, spin(age)));
        Vec3 now = base.add(0, BlueRedRules.purpleRise(age), 0);
        int n = 20;
        Vec3[] pts = new Vec3[n + 1];
        double[] w = new double[n + 1], al = new double[n + 1];
        for (int j = 0; j <= n; j++) {
            double t = (double) j / n, at = Math.max(0, age - span * (1 - t));
            pts[j] = now.add(convergeOffsets(at)[which]);
            w[j] = r * (.15 + .7 * t);
            al[j] = smooth(t / .6);
        }
        fxRibbon(c, pts, w, al, PLASMA, 3600 + which, p.mid(), .55);
        double[] core = new double[n + 1];
        for (int j = 0; j <= n; j++) core[j] = w[j] * .4;
        fxRibbon(c, pts, core, al, PLASMA, 3602 + which, p.light(), .5);
    }

    /** The ring the spin makes (owner's refs, 2026-11-18): a thick, soft band along the orb's real path (red behind Red, pale electric blue
     *  behind Blue), lengthening as the spin climbs until the two halves close into one ring; lightning runs along it, and it fades as they
     *  brake. A thin bright core rides on it in the glow pass. */
    private static void spinBand(Ctx c, Vec3 base, double age, int which, double alpha) {
        if (alpha <= 0) return;
        double sp = Math.max(.06, spin(age)), build = clamp01(sp / .7);
        double arc = 2.4 + 1.8 * build, span = Math.min(30, arc / sp);
        Vec3 now = base.add(0, BlueRedRules.purpleRise(age), 0);
        int n = 36;
        Vec3[] pts = new Vec3[n + 1];
        double[] w = new double[n + 1], al = new double[n + 1];
        for (int j = 0; j <= n; j++) {
            double t = (double) j / n, at = Math.max(0, age - span * (1 - t));
            pts[j] = now.add(convergeOffsets(at)[which]);
            // A flat band of even width (owner, round 37: horizontal, not a pointed trail), fading at its tail rather than narrowing.
            w[j] = (.5 + .25 * build) * smooth(t / .08 + .2) * smooth((1 - t) / .04 + .3) * (1 + .08 * Math.sin(t * 11 - age * .5));
            al[j] = smooth(t / .5);
        }
        if (c.body()) {
            fxBand(c, pts, w, al, new Vec3(0, 1, 0), PLASMA, 3550 + which, which == 1 ? RED : 0x7AC8FF, alpha * (.55 + .35 * build));
            return;
        }
        double[] core = new double[n + 1];
        for (int j = 0; j <= n; j++) core[j] = w[j] * .25;
        fxBand(c, pts, core, al, new Vec3(0, 1, 0), FLOW, 3560 + which, which == 1 ? RED_LIGHT : ICE, alpha * .5 * build);
        // Lightning running in the band: a few short live arcs between points along it.
        for (int b = 0; b < (int) (2 + 5 * build); b++) {
            int i0 = 6 + (int) (hash(3570 + b * 7 + which) * (n - 12)), i1 = Math.min(n, i0 + 4 + (int) (hash(3571 + b * 7) * 5));
            liveBolt(c, pts[i0], pts[i1], 5, .4, .02 + .03 * build, b % 2 == 0 ? PALE : ORCHID, alpha * build, 3580 + b * 13 + which * 101, age, .9);
        }
    }

    /** The core between them (owner's refs): a white-pink light at the centre of the ring, swelling with the spin, with lightning crackling out
     *  of it, mostly up and down, flickering as the bolts move. */
    private static void coreStorm(Ctx c, Vec3 center, double age, double alpha) {
        if (!c.glow() || alpha <= 0) return;
        double build = clamp01(spin(age) / .7), hold = smooth((age - BlueRedRules.PURPLE_FACE) / 8.0), k = Math.max(build, smooth((age - BlueRedRules.PURPLE_LIFT_END) / 10.0)) * alpha;
        if (k <= .02) return;
        // Violet, not white (owner, round 44): this ball sat right between the orbs at the stop.
        glow(c, center, .6 + 1.2 * k, .25 * k, ORCHID);
        glow(c, center, 2 + 3 * k, .3 * k, VIOLET);
        for (int b = 0; b < (int) (2 + 6 * k); b++) {
            // Short and all round (owner, 2026-11-18): a ball of crackling, not spikes up and down.
            Vec3 d = nvec(3700 + b, age * .2).normalize();
            liveBolt(c, center, center.add(d.scale(.7 + .9 * k * vnoise(3720 + b, age * .3))), 5, .45, .02 + .03 * k, b % 2 == 0 ? ORCHID : VIOLET, k, 3730 + b * 19, age, 1);
        }
    }

    /** What the spin drags behind an orb (owner, 2026-11-18: inertia): three broad strokes of its own colours streaming back along the path it
     *  really took, full at the orb and torn, thinning and fading behind it; longer the faster it goes. */
    private static void drag(Ctx c, Vec3 base, double age, int which, Palette p, double alpha) {
        // Owner, 2026-11-18: dust, not long strokes: puffs of its colours left where it passed, each spreading and gone within about eight ticks.
        if (!c.body() || alpha <= 0) return;
        for (int n = 0; n < 8; n++) {
            double laid = Math.floor(age) - n, old = age - laid;
            if (laid < BlueRedRules.PURPLE_CONTACT) break;
            Vec3 at = base.add(0, BlueRedRules.purpleRise(laid), 0).add(convergeOffsets(laid)[which]);
            long sd = 3540 + which * 1000 + (long) laid * 7;
            double a = (1 - old / 8) * .3 * alpha;
            if (a <= 0) continue;
            for (int h = 0; h < 2; h++)
                fxSprite(c, at.add(nvec(sd + h, 0).scale(.4 + .15 * old)), .75 + .25 * old, SMOKE, (int) ((sd + h) % 991), h == 0 ? p.mid() : p.light(), a, laid * .3 + h);
        }
    }

    /** Black over the world for Purple's two players: a quick fade in after the swirl, a fade out over the world Purple. */
    static double voidAlpha(double age) {
        int in = BlueRedRules.PURPLE_SWIRL_END, merge = BlueRedRules.PURPLE_MERGE, end = BlueRedRules.PURPLE_VOID_END;
        if (age < in || age >= end) return 0;
        if (age < merge) return smooth((age - in) / 8);
        return 1 - smooth((age - merge) / (end - merge));
    }

    /** How close the 2D spheres are to touching (0..1) at this age: drives the shake. */
    static double voidClose(double age) {
        double v = age - BlueRedRules.PURPLE_SWIRL_END, span = BlueRedRules.PURPLE_MERGE - BlueRedRules.PURPLE_SWIRL_END;
        double push = voidPush(v), absorb = v >= TOUCH_V ? 1 : 0;
        return smooth((push - .2) / .75) * (1 - absorb);
    }

    /** Owner, 2026-11-19: the 2D spheres drift in slowly and never overlap; the instant their rims touch, Purple (and the impact frames). */
    // Owner, 2026-11-19: the touch is the cut, two ticks before MERGE: violet circular frames and Purple is there, whole (no growing).
    static final double TOUCH_V = BlueRedRules.PURPLE_MERGE - BlueRedRules.PURPLE_SWIRL_END - 2, VOID_R = 3.7, VOID_GAP = 5.8;
    /** The drawn sphere's rim glow reaches this far past its radius: the touch is rim on rim. */
    // Measured on QA frames (2026-11-19): the drawn sphere's edge is r, so 1.0 is rim on rim at the hit.
    static final double VOID_RIM = 1.0;

    /** How far they have come (0..1) v ticks into the act: 1 exactly when their rims touch. */
    // Slowing as they close (owner, 2026-11-19), but still moving when the rims meet, so the touch and the impact frame are one instant (an
    // ease that stops dead made them sit touching before the hit).
    // One continuous ease (owner, 2026-11-19: no pause and lunge): fast at first, slower and slower, still moving when the rims meet.
    static double voidPush(double v) { double k = clamp01((v - 14) / (TOUCH_V - 14)); return .6 * (1 - (1 - k) * (1 - k)) + .4 * k; }

    /** The tick the 2D spheres touch and Purple is born. */
    static int voidTouch() { return BlueRedRules.PURPLE_SWIRL_END + (int) Math.round(TOUCH_V); }

    /** The 2D act (owner, 2026-11-18, ref 42). The world fades to black, then the act fades in: dark smoke streaked pink and violet; big Red
     *  lower left and big Blue upper right, churning white-hot spheres, each streaming 2D brush strokes of its colours behind it; lightning
     *  crackles on Red's edge facing Blue; they are pushed together along the diagonal, Purple forms over them and takes their place. */
    static void purpleVoid(Ctx c, Vec3 at, double age, Vec3 eye, Vec3 look) {
        double back = voidAlpha(age);
        if (back <= 0) return;
        // The 2D act is framed by the camera, not by the world: always 9 blocks in front of it, whatever the 3D camera did.
        at = eye.add(look.scale(7));
        int in = BlueRedRules.PURPLE_SWIRL_END, merge = BlueRedRules.PURPLE_MERGE;
        double v = age - in, span = merge - in;
        // Black first (8 ticks), then the act fades in over it.
        double show = back * (age < merge ? smooth((v - 8) / 10) : 1);
        Vec3 view = new Vec3(eye.x - at.x, 0, eye.z - at.z);
        view = view.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : view.normalize();
        Vec3 axis = view.cross(new Vec3(0, 1, 0)).normalize(), up = new Vec3(0, 1, 0);
        // Slow approach (owner, 2026-11-18): they drift in over most of the act; Purple is born between them near the end.
        double push = voidPush(v), form = v >= TOUCH_V ? 1 : 0, absorb = v >= TOUCH_V ? 1 : 0;
        // A moderate shake of the whole act, none at first and building as they draw together (owner, round 40b). Camera trauma can't show
        // here because the act is framed by the camera itself.
        Vec3[] sh = basis(look);
        // Heavy (owner, 2026-11-19): a slow rumble growing as they close, fine jitter over it, and a hard kick fading after the touch.
        // Long and hard after the touch, on through the cut to Purple (owner, 2026-11-19).
        double kick = v >= TOUCH_V ? Math.exp(-(v - TOUCH_V) / 6.0) : 0;
        // Owner, 2026-11-19: much stronger at the end, and a real jolt at the touch.
        double rumble = .75 * Math.pow(push, 3) * (1 - absorb) + 1.4 * kick, fine = .15 * push * push * (1 - absorb) + .35 * kick;
        at = at.add(sh[0].scale(rumble * snoise(4990, age * .7) + fine * snoise(4992, age * 3.1)))
               .add(sh[1].scale(rumble * snoise(4991, age * .7) + fine * snoise(4993, age * 3.3)));
        if (c.body()) {
            Vec3[] uw = basis(look);
            Vec3 o = eye.add(look.scale(.5));
            sprite(c, o, 4, back, 0x000000, BlueRedAtlas.SOLID);
            // 1.6 blocks half-size at .5 in front: a little past the screen edges at normal FOVs, so the shader's q spans the view.
            double H = 1.6;
            fxQuad(c, o.subtract(uw[0].scale(H)).subtract(uw[1].scale(H)), o.add(uw[0].scale(H)).subtract(uw[1].scale(H)), o.add(uw[0].scale(H)).add(uw[1].scale(H)),
                    o.subtract(uw[0].scale(H)).add(uw[1].scale(H)), NEBULA, 250, 0x2E2640, show, show, show, show, 0, 1);
        }
        double orbs = (1 - absorb) * show, close = smooth((push - .2) / .75) * (1 - absorb);
        // Centre to centre 2·gapD: from VOID_GAP each side down to exactly one radius, so their rims meet at the middle and never overlap.
        double gapD = VOID_R * VOID_RIM + (VOID_GAP - VOID_R * VOID_RIM) * (1 - push), r = VOID_R * (1 - .65 * absorb);
        // view x up points to screen left: Red on +axis (left), Blue on -axis (right), level (owner: never tilted).
        Vec3 dirRed = axis;
        Vec3 red = at.add(dirRed.scale(gapD)), blue = at.subtract(dirRed.scale(gapD));
        if (orbs > .02) for (int side = 0; side < 2; side++) {
            Vec3 p = side == 0 ? red : blue, outward = side == 0 ? dirRed : dirRed.scale(-1);
            Palette pal = side == 0 ? RED_P : BLUE_P;
            // A little darker than the world orbs (owner, 2026-11-18).
            int tint = side == 0 ? 0xB80C24 : 0x1448C8;
            if (c.body()) {
                // Owner, 2026-11-18: the trail is as wide as the sphere itself, flat, shapeless and soft (overlapping smoke behind it), and dust
                // flies off it backwards, away from the other sphere.
                // Owner, 2026-11-19: little at first, then more and faster as they close, over a wider area.
                for (int j = 0; j < 5 + (int) (6 * push); j++) {
                    long sd = 4600 + side * 50 + j;
                    double d = r * (.35 + .6 * j) * (1 + 1.2 * push) + r * .3 * snoise(sd, age * .05), size = r * (1.7 - .08 * j) * (1 + .4 * push) * (1 + .12 * snoise(sd + 1, age * .07));
                    Vec3 q = p.add(outward.scale(d)).add(up.scale(r * .35 * snoise(sd + 2, age * .04)));
                    fxSprite(c, q, size, SMOKE, (int) (sd % 977), j % 3 == 0 ? pal.mid() : j % 3 == 1 ? pal.deep() : pal.light(), Math.max(.04, .22 + .1 * push - .02 * j) * orbs, age * .02 * (j % 2 == 0 ? 1 : -1) + j);
                }
                for (int j = 0; j < 15 + (int) (85 * push); j++) {
                    long sd = 4700 + side * 100 + j;
                    double life = 14 + 8 * hash(sd), ph = age / life + hash(sd + 1);
                    long cyc = (long) Math.floor(ph);
                    double u = ph - cyc;
                    long cs = sd + cyc * 7919L;
                    double ang = (hash(cs) - .5) * 3.0;
                    Vec3 dir = outward.scale(Math.cos(ang)).add(up.scale(Math.sin(ang))).normalize();
                    Vec3 q = p.add(dir.scale(r * (.85 + (3 + 5 * push) * u))).add(view.scale(.2));
                    fxSprite(c, q, r * (.06 + .09 * hash(cs + 2)) * (1 + 2 * u) * (1 + .5 * push), SMOKE, (int) (cs % 991), j % 2 == 0 ? pal.light() : pal.hot(), smooth(u / .1) * (1 - u) * .8 * orbs, u * 3);
                }
                // Screen-aligned, so it stays a true circle even near the edge of the view.
                orbFacing(c, p, r, 3, side, tint, orbs, look);
                // Over its face (owner, 2026-11-19): smoke and dust born on the side facing the other sphere, streaming across the surface and
                // off behind it; a few at first, then more and faster as they close.
                Vec3 across = up.cross(view).normalize();
                for (int j = 0; j < 10 + (int) (60 * push); j++) {
                    long sd = 4750 + side * 100 + j;
                    double life = 12 + 8 * hash(sd), ph = age / life + hash(sd + 1);
                    long cyc = (long) Math.floor(ph);
                    double u = ph - cyc;
                    long cs = sd + cyc * 7919L;
                    double yy = (hash(cs) * 2 - 1) * .85, start = -.8 + .5 * hash(cs + 1);
                    double xx = start + (1.6 + 2.5 * push) * u;
                    Vec3 q = p.add(outward.scale(r * xx)).add(up.scale(r * yy * Math.sqrt(Math.max(0, 1 - Math.min(1, xx * xx) * .6)))).add(view.scale(r * .35));
                    double big = j % 4 == 0 ? 2.2 : 1;
                    fxSprite(c, q, r * (.05 + .06 * hash(cs + 2)) * big * (1 + 1.5 * u), SMOKE, (int) (cs % 991), j % 2 == 0 ? pal.light() : pal.mid(),
                            smooth(u / .12) * (1 - u) * (j % 4 == 0 ? .35 : .7) * orbs, u * 3);
                }
                // Brush bands from its front edge across it and out behind (ref 43).
                for (int j = 0; j < 5; j++) {
                    long sd = 4800 + side * 30 + j;
                    double y = (hash(sd) - .5) * r * 1.5, L = r * (2.2 + 1.6 * hash(sd + 1)), drift = (age * .04 + hash(sd + 2)) % 1;
                    // Dust, like the 3D trails (owner, round 38): soft puffs along the band drifting back, not a torn brush stroke.
                    for (int q = 0; q < 10; q++) {
                        double t = ((q / 10.0) + drift) % 1;
                        Vec3 pq = p.add(up.scale(y + r * .12 * snoise(sd + 3, t * 3 - age * .1))).add(outward.scale(-r * .85 + L * t)).add(view.scale(.15));
                        fxSprite(c, pq, r * (.16 + .1 * hash(sd + q)) * (.8 + .6 * t), SMOKE, (int) ((sd * 13 + q) % 977), j % 2 == 0 ? pal.light() : pal.deep(),
                                .3 * orbs * Math.sin(Math.PI * t), age * .03 + q);
                    }
                }
            }
            if (c.glow()) {
                glow(c, p, r * 1.5, .2 * orbs, tint);
                // The backdrop lit by each sphere: reddish round Red, bluish round Blue (owner, 2026-11-18).
                glow(c, p.subtract(view.scale(2)), r * 3.6, .13 * orbs, side == 0 ? RED : 0x2A6CFF);
            }
            // Tendrils crawling along its rim on the side facing the other (owner's refs, 2026-11-19).
            Vec3 toward = side == 0 ? dirRed.scale(-1) : dirRed;
            for (int b = 0; b < 1 + (int) (3 * close); b++) {
                Vec3 d0 = toward.add(up.scale(1.3 * snoise(4900 + side * 40 + b, age * .03))).add(view.scale(.4)).normalize();
                Vec3 d1 = d0.add(up.scale(.7 * snoise(4920 + side * 40 + b, age * .04))).normalize();
                softBolt(c, p.add(d0.scale(r * 1.01)), p.add(d1.scale(r * 1.03)), .015 + .02 * close, 0xFF50E8, orbs * (.4 + .4 * close), 4960 + side * 100 + b * 13, age, 0);
            }
        }
        // Lightning crackling on Red's edge facing Blue (ref 42), growing as they close.
        if (orbs > .02) {
            // A few soft bolts leaping rim to rim across the gap (owner's refs, 2026-11-19), not a tangle through the middle.
            // Owner, 2026-11-19: at the two facing rims only; thin and few at first, more and thicker as they close, gathering at the point
            // where they will touch.
            double cc = close * close;
            for (int j = 0; j < 1 + (int) (5 * cc); j++) {
                double spread = r * (.6 - .45 * close);
                Vec3 off = up.scale(spread * snoise(3200 + j, age * .04)).add(view.scale(spread * .3 * snoise(3210 + j, age * .05)));
                Vec3 from = red.subtract(dirRed.scale(r * .97)).add(off), to = blue.add(dirRed.scale(r * .97)).add(off.scale(.8));
                softBolt(c, from, to, .02 + .06 * cc, 0xFF50E8, (.4 + .5 * close) * orbs, 3400 + j * 29, age, close > .5 ? 2 : 1);
            }
        }
        if (close > 0) {
            // Chaos where they meet, from the two facing edges (owner, 2026-11-18): sparks and lightning thick and bright off both rims.
            Vec3 redFace = red.subtract(dirRed.scale(r * .95)), blueFace = blue.add(dirRed.scale(r * .95));
            for (int e = 0; e < 2; e++) {
                Vec3 f = e == 0 ? redFace : blueFace, out = e == 0 ? dirRed.scale(-1) : dirRed;
                for (int b = 0; b < (int) (1 + 2 * close); b++) {
                    Vec3 d = out.add(nvec(5010 + e * 40 + b, age * .08).scale(1.1)).normalize();
                    softBolt(c, f.add(up.scale((hash(5000 + b) - .5) * r * .8)), f.add(d.scale(r * (.6 + .8 * vnoise(5060 + b, age * .1)))), .035 + .03 * close,
                            0xFF50E8, .7 * close, 5100 + e * 100 + b * 13, age, 1);
                }
            }
            if (c.glow()) speedLines(c, eye, look, .55 * close, PALE);
        }
        // Birth of Hollow Purple between them: the centre flickers, lightning crackles out of it, sparks are pulled into it.
        double birth = smooth((push - .55) / .45) * (1 - form) * back;
        if (c.glow() && birth > 0) {
            double flick = .5 + .5 * Math.abs(Math.sin(age * 1.7) * Math.sin(age * 2.9 + 1));
            // Restrained before the touch, so the touch itself is the hit (owner, 2026-11-19).
            glow(c, at, .4 + .8 * birth * flick, .35 * birth * flick, PALE);
            glow(c, at, 1 + 1.5 * birth, .3 * birth, ORCHID);
            streaks(c, at, .3, 2 + 4 * birth, age, (int) (40 * birth), PALE, 3950, 2.4, birth, true);
        }
        if (form > 0) {
            double s = (1.2 + 4.8 * form) * (1 + .1 * Math.sin(age * .31));
            if (c.body()) orb(c, at, s, 2, 1, VIOLET, form * back);
            if (c.glow()) {
                stormBands(c, at, s, age, 12, form * back);
                streaks(c, at, s * .95, s * 3.5, age, 60, PALE, 1450, 1.6, form * back, true);
                fxSprite(c, at, 4 + 14 * form, RAYS, 300, PALE, form * back, age * .02);
                sparks(c, at, s * 1.3, age, 50, PALE, 310);
            }
            // Born in a burst of lightning (owner, 2026-11-19): violet, black-cored, kinked, flung out every way.
            for (int b = 0; b < 10; b++) {
                Vec3 d = nvec(3600 + b, b * 1.7).normalize();
                softBolt(c, at.add(d.scale(s * .9)), at.add(d.scale(s * (2.2 + 1.2 * hash(3700 + b)))), .08, ORCHID, form * back, 3800 + b * 31, age, 2);
            }
        }
        if (c.glow()) for (int i = 0; i < 14; i++) {
            double y = ((i * .29 + age * .006) % 1) * 6 - 3;
            sprite(c, at.add(axis.scale(Math.sin(i * 1.7) * 7)).add(0, y, 0).subtract(view.scale(1)), .14, .6 * show, PALE, BlueRedAtlas.BUBBLE);
        }
    }

    /** Blue's charge-complete wave (owner, 2026-11-19): how far the front has gone at t ticks after it turns ready. */
    static double readyWave(double t) { return t < 0 ? 0 : 1.5 + 30 * (1 - Math.pow(1 - clamp01(t / 14.0), 2)); }

    /** That wave: a hard blue shell of pressure bursting out once, a flat ring through it, a flash and sparks at the core. */
    static void readyWave(Ctx c, Vec3 at, double t) {
        double k = clamp01(t / 16.0), a = Math.pow(1 - k, 1.4), r = readyWave(t);
        if (a <= 0 || !c.glow()) return;
        fxSprite(c, at, r, SHOCK, 7100, CYAN, .9 * a, 0);
        fxSprite(c, at, r * .8, SHOCK, 7101, ROYAL, .6 * a, 1);
        fxFlat(c, at, r * 1.05, SHOCK, 7102, CYAN, .7 * a);
        glow(c, at, 2 + r * .25, .8 * a * (1 - k), 0x2A7CFF);
        glow(c, at, 1.5, a, ICE);
        sparks(c, at, 1 + r * .6, t * 2, (int) (40 * a), ICE, 7110);
    }

    /** Red's impact frames (owner, 2026-11-16): black, red, black, then a fading red wash, with a burst of ink rays from the impact. */
    static int impactColor(int frame) { return frame % 2 == 0 ? INK : RED_BODY; }
    static double impactAlpha(int frame) { return frame < 3 ? .9 - .12 * frame : frame < 6 ? .35 * (6 - frame) / 3.0 : 0; }

    static void impact(Ctx c, Vec3 at, int frame, Vec3 eye, Vec3 look) { impact(c, at, frame, eye, look, false); }

    static void impact(Ctx c, Vec3 at, int frame, Vec3 eye, Vec3 look, boolean purple) { impact(c, at, frame, eye, look, purple, false); }

    /** hard: Red's shot (owner, 2026-11-19), black and red full frames for a few ticks; otherwise Red's hit keeps the light tint. */
    static void impact(Ctx c, Vec3 at, int frame, Vec3 eye, Vec3 look, boolean purple, boolean hard) {
        double a = impactAlpha(frame);
        if (a <= 0 || !c.body() || hard && frame > 3) return;
        if (!purple && !hard) {
            // Owner's review of round 26: the hit must stay visible. Two frames of a light red tint and a hot flash at the contact point only.
            if (frame > 1) return;
            sprite(c, eye.add(look.scale(.5)), 4, .22 * (1 - frame * .5), RED_BODY, BlueRedAtlas.SOLID);
            sprite(c, at, Math.max(1, at.distanceTo(eye)) * .1, .9 * (1 - frame * .5), RED_CORE, BlueRedAtlas.GLOW);
            return;
        }
        int light = purple ? 0xC070FF : RED_BODY;
        int back = frame < 3 ? (frame % 2 == 0 ? INK : light) : light, stroke = back == INK ? light : INK;
        sprite(c, eye.add(look.scale(.5)), 4, a, back, BlueRedAtlas.SOLID);
        double dist = Math.max(1, at.distanceTo(eye));
        fxSprite(c, at, dist * (purple ? 2.4 : .9), RAYS, 320 + frame, stroke, a, frame * .7);
        // Purple's burst fills the frame (owner, 2026-11-19: it read small, a few rays at the centre).
        if (purple) fxSprite(c, at, dist * (.9 + .25 * frame), SHOCK, 344, stroke, a, 0);
        Vec3[] uw = basis(look);
        for (int i = 0; i < 10; i++)
            if (!purple) fxArc(c, at, uw[0], uw[1], dist * (.12 + .05 * i), i * .63 + frame * .4, 1.4, dist * .012, FLOW, 330 + i, stroke, a);
        // Purple's frames: one big hard circle round it, and a thinner one inside (owner, round 37).
        if (purple) {
            fxArc(c, at, uw[0], uw[1], dist * (.75 + .08 * frame), 0, Math.PI * 2.01, dist * .05, FLOW, 345, stroke, a);
            fxArc(c, at, uw[0], uw[1], dist * (.55 + .05 * frame), 0, Math.PI * 2.01, dist * .02, FLOW, 346, stroke, a);
            fxArc(c, at, uw[0], uw[1], dist * (.38 + .03 * frame), 0, Math.PI * 2.01, dist * .012, FLOW, 347, stroke, a);
        }
        if (frame < 3) sprite(c, at, dist * .12, a, stroke, BlueRedAtlas.GLOW);
    }

    // ---- shader quads ----

    /** One quad of a shader effect: corners a (0,0), b (1,0), d (1,1), e (0,1) in the effect's local space; u0..u1 is the run along it. */
    static void fxQuad(Ctx c, Vec3 a, Vec3 b, Vec3 d, Vec3 e, int effect, int seed, int color, double aa, double ab, double ad, double ae, double u0, double u1) {
        if (!room(c)) return;
        float U0 = seed + .001f + (float) (u0 * .998), U1 = seed + .001f + (float) (u1 * .998);
        float V0 = 2 + effect + .001f, V1 = 2 + effect + .999f;
        vertex(c, a, color, aa, U0, V0); vertex(c, b, color, ab, U1, V0);
        vertex(c, d, color, ad, U1, V1); vertex(c, e, color, ae, U0, V1);
        c.quads++;
    }

    private static void vertex(Ctx c, Vec3 p, int color, double alpha, float u, float v) {
        Vec3 q = pull(p.subtract(c.eye), farPlane);
        int k = (int) Math.round(clamp01(alpha * c.fade) * 255);
        c.v.addVertex(c.m, (float) q.x, (float) q.y, (float) q.z).setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, k).setUv(u, v);
    }

    /** An ORB quad parallel to the screen (facing along {@code look}), so the sphere projects as a circle anywhere in the view. */
    static void orbFacing(Ctx c, Vec3 at, double r, int variant, int level, int color, double alpha, Vec3 look) {
        if (alpha <= 0) return;
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = (right.lengthSqr() < 1e-8 ? new Vec3(1, 0, 0) : right.normalize()).scale(r * 1.55);
        Vec3 up = right.cross(look).normalize().scale(r * 1.55);
        fxQuad(c, at.subtract(right).add(up), at.add(right).add(up), at.add(right).subtract(up), at.subtract(right).subtract(up), ORB, variant + 4 * level, color, alpha, alpha, alpha, alpha, 0, 1);
    }

    /** A ribbon lying flat in the plane with normal {@code n} (its width runs across the path inside that plane), with an alpha per point. */
    static void fxBand(Ctx c, Vec3[] pts, double[] widths, double[] alphas, Vec3 n, int effect, int seed, int color, double alpha) {
        if (alpha <= 0) return;
        int k = pts.length - 1;
        Vec3 pl = null, pr = null;
        for (int i = 0; i <= k; i++) {
            Vec3 side = pts[Math.min(k, i + 1)].subtract(pts[Math.max(0, i - 1)]).cross(n);
            if (side.lengthSqr() < 1e-10) continue;
            side = side.normalize().scale(widths[i]);
            Vec3 l = pts[i].subtract(side), r = pts[i].add(side);
            if (pl != null) fxQuad(c, pl, l, r, pr, effect, seed, color, alpha * alphas[i - 1], alpha * alphas[i], alpha * alphas[i], alpha * alphas[i - 1], (i - 1) / (double) k, i / (double) k);
            pl = l; pr = r;
        }
    }

    /** A camera-facing shader quad (rolled), half size {@code size}. */
    static void fxSprite(Ctx c, Vec3 at, double size, int effect, int seed, int color, double alpha, double roll) {
        Vec3 look = at.subtract(c.eye);
        if (look.lengthSqr() < 1e-6 || alpha <= 0) return;
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = (right.lengthSqr() < 1e-8 ? new Vec3(1, 0, 0) : right.normalize()).scale(size);
        Vec3 up = right.cross(look).normalize().scale(size);
        if (roll != 0) { Vec3 r0 = right; right = r0.scale(Math.cos(roll)).add(up.scale(Math.sin(roll))); up = up.scale(Math.cos(roll)).subtract(r0.scale(Math.sin(roll))); }
        fxQuad(c, at.subtract(right).add(up), at.add(right).add(up), at.add(right).subtract(up), at.subtract(right).subtract(up), effect, seed, color, alpha, alpha, alpha, alpha, 0, 1);
    }

    /** A shader quad lying flat (shockwave discs). */
    static void fxFlat(Ctx c, Vec3 at, double half, int effect, int seed, int color, double alpha) {
        if (alpha <= 0) return;
        fxQuad(c, at.add(-half, 0, -half), at.add(half, 0, -half), at.add(half, 0, half), at.add(-half, 0, half), effect, seed, color, alpha, alpha, alpha, alpha, 0, 1);
    }

    /** A flame from base along dir, camera-facing sideways: the FLAME effect's base (local y 1) at base, its tip at the far end. */
    static void fxTongue(Ctx c, Vec3 base, Vec3 dir, double length, double width, int effect, int seed, int color, double alpha) {
        if (alpha <= 0) return;
        Vec3 tip = base.add(dir.scale(length));
        Vec3 side = dir.cross(base.add(tip).scale(.5).subtract(c.eye));
        if (side.lengthSqr() < 1e-10) return;
        side = side.normalize().scale(width);
        fxQuad(c, tip.subtract(side), tip.add(side), base.add(side), base.subtract(side), effect, seed, color, alpha, alpha, alpha, alpha, 0, 1);
    }

    /** A camera-facing shader strip from a to b (local x along, y across). */
    static void fxBeam(Ctx c, Vec3 a, Vec3 b, double wa, double wb, int effect, int seed, int color, double alpha) {
        Vec3 side = b.subtract(a).cross(a.add(b).scale(.5).subtract(c.eye));
        if (side.lengthSqr() < 1e-10 || alpha <= 0) return;
        side = side.normalize();
        fxQuad(c, a.subtract(side.scale(wa)), b.subtract(side.scale(wb)), b.add(side.scale(wb)), a.add(side.scale(wa)), effect, seed, color, alpha, alpha, alpha, alpha, 0, 1);
    }

    /** A camera-facing shader ribbon through points (tail first), local x running 0..1 along it; a width of 0 leaves a gap. */
    static void fxRibbon(Ctx c, Vec3[] pts, double[] widths, int effect, int seed, int color, double alpha) { fxRibbon(c, pts, widths, null, effect, seed, color, alpha); }

    /** As above, with an alpha per point ({@code alphas}, times {@code alpha}; null for none), so parts of a stroke can fade on their own. */
    static void fxRibbon(Ctx c, Vec3[] pts, double[] widths, double[] alphas, int effect, int seed, int color, double alpha) {
        if (alpha <= 0) return;
        int n = pts.length - 1;
        Vec3 pl = null, pr = null;
        double pw = 0;
        for (int i = 0; i <= n; i++) {
            Vec3 tangent = pts[Math.min(n, i + 1)].subtract(pts[Math.max(0, i - 1)]);
            Vec3 side = tangent.cross(pts[i].subtract(c.eye));
            if (side.lengthSqr() < 1e-10) continue;
            side = side.normalize().scale(widths[i]);
            Vec3 l = pts[i].subtract(side), r = pts[i].add(side);
            if (pl != null && (pw > 1e-4 || widths[i] > 1e-4))
            {
                double a0 = alphas == null ? alpha : alpha * alphas[i - 1], a1 = alphas == null ? alpha : alpha * alphas[i];
                fxQuad(c, pl, l, r, pr, effect, seed, color, a0, a1, a1, a0, (i - 1) / (double) n, i / (double) n);
            }
            pl = l; pr = r; pw = widths[i];
        }
    }

    /** A ribbon along an arc of the circle (u, w) round center, camera-facing (impact frames). */
    static void fxArc(Ctx c, Vec3 center, Vec3 u, Vec3 w, double radius, double a0, double sweep, double width, int effect, int seed, int color, double alpha) {
        int n = 16;
        Vec3[] pts = new Vec3[n + 1];
        double[] widths = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            double a = a0 + sweep * i / n;
            pts[i] = center.add(u.scale(Math.cos(a) * radius)).add(w.scale(Math.sin(a) * radius));
            widths[i] = width;
        }
        fxRibbon(c, pts, widths, effect, seed, color, alpha);
    }

    /** One shader ORB of sphere radius r: variant 0 blue, 1 red, 2 purple; level 0 calm, 1 charged, 2 bursting. */
    static void orb(Ctx c, Vec3 at, double r, int variant, int level, int color, double alpha) {
        fxSprite(c, at, r * 1.55, ORB, variant + 4 * level, color, alpha, 0);
    }

    // ---- chaos ----

    /** A smooth, never-repeating tremble: sums of incommensurate sines. */
    static Vec3 jitter(double age, double amp, long seed) {
        double s = seed * 1.618;
        return new Vec3(Math.sin(age * 1.13 + s) + .5 * Math.sin(age * 2.71 + s * 2),
                Math.sin(age * 1.37 + s * 3) + .5 * Math.sin(age * 3.07 + s),
                Math.sin(age * 1.21 + s * 5) + .5 * Math.sin(age * 2.39 + s * 4)).scale(amp * .66);
    }

    /** Irregular brightness, 0.6 .. 1.15. */
    static double flicker(double age, long seed) {
        return .87 + .16 * Math.sin(age * 1.9 + seed) + .12 * Math.sin(age * 4.3 + seed * 2.3);
    }

    /** Lightning that crawls instead of popping: a fixed number of segments from a to b, each joint displaced by smooth noise of (seed, time), so the
     *  bolt keeps its identity frame to frame while its shape writhes; crackle sets how fast. Fades right against the eye. */
    static void liveBolt(Ctx c, Vec3 a, Vec3 b, int segments, double jitter, double width, int color, double alpha, long seed, double age, double crackle) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-3 || alpha <= 0) return;
        Vec3[] uw = basis(d.normalize());
        // One unbroken ribbon through every joint (owner, 2026-11-20: drawn as separate pieces, the joints showed square corners).
        Vec3[] pts = new Vec3[segments + 1];
        double[] w = new double[segments + 1], al = new double[segments + 1];
        pts[0] = a;
        for (int s = 1; s <= segments; s++) {
            double t = (double) s / segments, env = s == segments ? 0 : Math.sin(Math.PI * t);
            double x = age * crackle;
            Vec3 off = uw[0].scale(snoise(seed + s * 3L, x) + .4 * snoise(seed + s * 3L + 1, x * 3.1))
                    .add(uw[1].scale(snoise(seed + s * 3L + 2, x) + .4 * snoise(seed + s * 3L + 50, x * 2.7)));
            pts[s] = a.add(d.scale(t)).add(off.scale(jitter * len * env / Math.sqrt(segments)));
        }
        for (int s = 0; s <= segments; s++) {
            double t = (double) s / segments;
            w[s] = width * 4 * (.55 + .45 * Math.sin(Math.PI * Math.min(1, .15 + .85 * t)));
            al[s] = (1 - .4 * t) * clamp01((pts[s].distanceTo(c.eye) - 2) / 3);
        }
        fxRibbon(c, pts, w, al, BOLT, (int) (seed % 997), color, alpha);
    }

    /** Soft, continuous lightning (owner's 2D refs, 2026-11-19): one unbroken stroke with fine kinks on a few large bends, a wide violet halo,
     *  a narrower glow and a pale hot core, tapering at both ends; it crawls (with small snaps) instead of re-rolling, and sheds a few thin
     *  branches. Glow pass only. */
    static void softBolt(Ctx c, Vec3 a, Vec3 b, double width, int color, double alpha, long seed, double age, int branches) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-3 || alpha <= 0 || !c.glow()) return;
        Vec3[] uw = basis(d.normalize());
        int k = 32;
        Vec3[] pts = new Vec3[k + 1];
        double[] halo = new double[k + 1], mid = new double[k + 1], core = new double[k + 1];
        // Owner, 2026-11-19: lightning strikes, it does not squirm. Each bolt has one fixed shape: it flashes in, holds a few ticks, fades,
        // and the next strike takes a new shape. A thin pink-white core inside a wide magenta glow; thinner towards its ends.
        double life = 6 + 5 * hash(seed + 90), ph = age / life + hash(seed + 91);
        long strike = (long) Math.floor(ph);
        double u = ph - strike;
        alpha *= (u < .08 ? 1.3 : 1) * (1 - smooth((u - .4) / .5));
        if (alpha <= .01) return;
        seed = seed + strike * 7919L;
        double x = hash(seed + 92) * 50;
        // Real lightning (owner, 2026-11-19): midpoint displacement. Each level halves the segments and kinks every new midpoint sideways by
        // a random amount that shrinks level by level, so a few big bends carry many tiny crinkles. Fixed for the whole strike.
        double[] ox = new double[k + 1], oy = new double[k + 1];
        double amp = .22;
        for (int step = k / 2; step >= 1; step /= 2, amp *= .55)
            for (int j = step; j < k; j += 2 * step) {
                ox[j] = (ox[j - step] + ox[j + step]) / 2 + amp * (hash(seed + j * 31L + step * 7L) * 2 - 1);
                oy[j] = (oy[j - step] + oy[j + step]) / 2 + amp * (hash(seed + j * 37L + step * 11L + 5) * 2 - 1);
            }
        for (int j = 0; j <= k; j++) {
            double t = (double) j / k, env = Math.sin(Math.PI * t);
            pts[j] = a.add(d.scale(t)).add(uw[0].scale(ox[j] * len)).add(uw[1].scale(oy[j] * len));
            double w = width * (.4 + .6 * Math.pow(env, .5));
            halo[j] = w * 8; mid[j] = w * 2.2; core[j] = w * .7;
        }
        int sd = (int) Math.floorMod(seed, 977);
        // BOLT, the smooth beam texture (FLOW's streaks read as a comb across a short, wide stroke).
        // Violet with a white core (owner, 2026-11-19).
        fxRibbon(c, pts, halo, BOLT, sd, 0x8A28FF, alpha * .5);
        fxRibbon(c, pts, mid, BOLT, sd + 1, 0xB050FF, alpha * .9);
        fxRibbon(c, pts, core, BOLT, sd + 2, 0xFFFFFF, alpha * .95);
        for (int i = 0; i < branches; i++) {
            int at = (int) (k * (.25 + .5 * hash(seed + 10 + i)));
            Vec3 dir = d.normalize().add(uw[0].scale(snoise(seed + 20 + i, x * .5) * 1.4)).add(uw[1].scale(snoise(seed + 30 + i, x * .5) * 1.4)).normalize();
            softBolt(c, pts[at], pts[at].add(dir.scale(len * (.18 + .2 * hash(seed + 40 + i)))), width * .55, color, alpha * .7, seed * 7 + 101 + i, age, 0);
        }
    }

    /** Thin streaks of light rushing in to the core (inward) or flung out from it, each on its own curling path and pace. */
    static void streaks(Ctx c, Vec3 at, double rIn, double rOut, double age, int count, int color, int seed, double speed, double alpha, boolean inward) {
        if (count <= 0 || alpha <= 0) return;
        Random rand = new Random(seed);
        for (int i = 0; i < count; i++) {
            double life = 9 + rand.nextDouble() * 14, off = rand.nextDouble(), curl = (rand.nextDouble() - .5) * 3.2, lift = (rand.nextDouble() - .5) * 1.4;
            Vec3 d0 = gauss(rand).normalize();
            double width = .008 + .022 * rand.nextDouble();
            double p = (age * speed / life + off) % 1;
            Vec3 head = streakAt(at, d0, rIn, rOut, inward ? p : 1 - p, curl, lift);
            Vec3 tail = streakAt(at, d0, rIn, rOut, inward ? Math.max(0, p - .16) : Math.min(1, 1 - p + .16), curl, lift);
            double k = Math.sin(Math.PI * p) * alpha;
            if (head.distanceTo(c.eye) > 1.5) fxBeam(c, tail, head, width * .3, width, BOLT, seed + i, i % 5 == 0 ? 0xFFFFFF : color, k);
        }
    }

    /** Where a streak is at progress p (0 far out, 1 at the core): falling in on an accelerating spiral. */
    private static Vec3 streakAt(Vec3 at, Vec3 d0, double rIn, double rOut, double p, double curl, double lift) {
        double radius = rIn + (rOut - rIn) * Math.pow(1 - p, 1.6), turn = curl * p * Math.PI;
        Vec3 d = new Vec3(d0.x * Math.cos(turn) - d0.z * Math.sin(turn), d0.y + lift * p * (1 - p), d0.x * Math.sin(turn) + d0.z * Math.cos(turn)).normalize();
        return at.add(d.scale(radius));
    }

    /** Jagged shards tumbling round a core on chaotic orbits (orbiting), or flung outward (not orbiting). */
    static void debris(Ctx c, Vec3 at, double r, double age, int count, int color, int seed, double alpha, boolean orbiting) {
        if (alpha <= 0) return;
        Random rand = new Random(seed);
        for (int i = 0; i < count; i++) {
            Vec3 axis = gauss(rand).normalize();
            Vec3[] uw = basis(axis);
            double radius = r * (.8 + .9 * rand.nextDouble()), speed = (.04 + .1 * rand.nextDouble()) * (rand.nextBoolean() ? 1 : -1), phase = rand.nextDouble() * 7;
            double size = .07 + .28 * Math.pow(rand.nextDouble(), 2), tumble = (rand.nextDouble() - .5) * .6;
            double a = phase + age * speed;
            double rr = orbiting ? radius * (1 + .15 * Math.sin(age * .07 + i)) : radius;
            Vec3 p = at.add(uw[0].scale(Math.cos(a) * rr)).add(uw[1].scale(Math.sin(a) * rr)).add(jitter(age, size, seed + i));
            if (p.distanceTo(c.eye) > 1.5) fxSprite(c, p, size, DEBRIS, seed + i, color, alpha, age * tumble + i);
        }
    }

    /** Full-view anime speed lines and a closing vignette, right before the eye. */
    static void speedLines(Ctx c, Vec3 eye, Vec3 look, double alpha, int color) {
        if (alpha <= 0 || !c.body()) return;
        Vec3[] uw = basis(look);
        Vec3 o = eye.add(look.scale(.5));
        double h = 1.6;
        fxQuad(c, o.subtract(uw[0].scale(h)).add(uw[1].scale(h)), o.add(uw[0].scale(h)).add(uw[1].scale(h)), o.add(uw[0].scale(h)).subtract(uw[1].scale(h)),
                o.subtract(uw[0].scale(h)).subtract(uw[1].scale(h)), SPEEDLINES, 1300, color, alpha, alpha, alpha, alpha, 0, 1);
    }

    // ---- other shapes ----

    static void glow(Ctx c, Vec3 at, double size, double alpha, int color) { sprite(c, at, size, alpha, color, BlueRedAtlas.GLOW, 0); }

    /** Twinkling shader stars scattered through a sphere. */
    static void sparks(Ctx c, Vec3 at, double radius, double age, int count, int color, int seed) {
        Random rand = new Random(seed);
        for (int i = 0; i < count; i++) {
            Vec3 d = gauss(rand).normalize();
            double rr = radius * Math.cbrt(rand.nextDouble()), spin = age * (.02 + .03 * rand.nextDouble());
            Vec3 p = at.add(new Vec3(d.x * Math.cos(spin) - d.z * Math.sin(spin), d.y + Math.sin(age * .05 + i) * .1, d.x * Math.sin(spin) + d.z * Math.cos(spin)).scale(rr));
            if (p.distanceTo(c.eye) < 1.2) continue;
            fxSprite(c, p, .03 + .05 * rand.nextDouble(), SPARK, seed + i, i % 3 == 0 ? 0xFFFFFF : color, 1, i);
        }
    }

    /** A thin ring in the plane normal to n (Red's sonic rings). */
    static void orbit(Ctx c, Vec3 at, Vec3 n, double radius, double width, int color, double alpha) {
        Vec3[] uw = basis(n);
        int segs = 40;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
            Vec3 d0 = uw[0].scale(Math.cos(a0)).add(uw[1].scale(Math.sin(a0))), d1 = uw[0].scale(Math.cos(a1)).add(uw[1].scale(Math.sin(a1)));
            gradient(c, at.add(d0.scale(radius - width)), at.add(d1.scale(radius - width)), at.add(d1.scale(radius)), at.add(d0.scale(radius)), color, 0, 0, alpha, alpha);
            gradient(c, at.add(d0.scale(radius)), at.add(d1.scale(radius)), at.add(d1.scale(radius + width)), at.add(d0.scale(radius + width)), color, alpha, alpha, 0, 0);
        }
    }

    /** Two unit vectors perpendicular to n and to each other. */
    static Vec3[] basis(Vec3 n) {
        Vec3 u = n.cross(new Vec3(0, 1, 0));
        u = u.lengthSqr() < 1e-8 ? new Vec3(1, 0, 0) : u.normalize();
        return new Vec3[]{u, n.cross(u).normalize()};
    }

    private static Vec3 gauss(Random rand) { return new Vec3(rand.nextGaussian(), rand.nextGaussian(), rand.nextGaussian()); }
}
