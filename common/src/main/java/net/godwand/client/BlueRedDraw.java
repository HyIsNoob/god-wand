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
    static final int VIOLET = 0x9A2CFF, ORCHID = 0xF064FF, PALE = 0xF6D8FF, NIGHT = 0x2A0650, VOID = 0x07020F, INK = 0x050002;
    /** Shader effects (godwand_bluered.fsh). ORB seeds: variant (0 blue, 1 red, 2 purple) + 4 * level. */
    static final int ORB = 0, FLOW = 1, FLAME = 2, SMOKE = 3, SHOCK = 4, BOLT = 5, SPARK = 6, RAYS = 7, NEBULA = 8, SPEEDLINES = 9, DEBRIS = 10, RING = 11, BRUSH = 12, PLASMA = 13;
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

    static void blue(Ctx c, Vec3 at, boolean ready, double age, double sinceReady) { blue(c, at, ready, age, sinceReady, new Vec3[0], new double[0]); }

    /** Blue (owner's third direction, 2026-11-18): a whole, heavy, stable sphere of energy, a bright cyan-white core inside a clear blue body,
     *  with only a few bands going round it for depth (the parts behind it hidden). Steered, it carries its momentum (the server's inertia, see
     *  BlueRedRules.follow); its bands are dragged back from the way it moves and it leaves short-lived, blotchy blue smears behind it, longer and
     *  stronger the faster it goes or the harder it turns. {@code trail}: where it has been, oldest first; {@code ago}: how many ticks ago. */
    static void blue(Ctx c, Vec3 at, boolean ready, double age, double sinceReady, Vec3[] trail, double[] ago) {
        double r = BlueRedVisual.blueRadius(ready ? BlueRedRules.BlueState.READY : BlueRedRules.BlueState.CHARGING, age);
        double appear = smooth(age / 6), beat = ready ? BlueRedVisual.heartbeat(age) : 0, flash = BlueRedVisual.flash(sinceReady);
        Vec3 vel = velocity(trail, ago);
        double coreD = at.distanceTo(c.eye), sphere = r * .78;
        if (c.body()) {
            smears(c, at, r, age, trail, ago, appear);
            bands(c, at, r, age, ready, vel, appear, coreD, false);
            orb(c, at, sphere, 0, ready ? 1 : 0, BLUE_TINT, appear);
            bands(c, at, r, age, ready, vel, appear, coreD, true);
            pressure(c, at, r, age, vel, ready, appear);
        }
        if (!c.glow()) return;
        glow(c, at, r * .5, .9 * appear, 0xFFFFFF);
        glow(c, at, r * 1.0, (.5 + .2 * beat) * appear, CYAN);
        glow(c, at, r * 2.1, (.2 + .1 * beat) * appear, ROYAL);
        if (flash > 0) { glow(c, at, r * 6, flash, 0xFFFFFF); glow(c, at, r * 2.5, flash, 0xFFFFFF); }
    }

    /** Blue's velocity per tick, continuous in time: the last two segments of its history, blended by how long ago the newest point came in, so
     *  it does not jump every time a new position arrives (that made the bands, the smears in front and the trail twitch while it moved). */
    static Vec3 velocity(Vec3[] trail, double[] ago) {
        int n = trail.length;
        if (n < 2) return Vec3.ZERO;
        Vec3 newer = trail[n - 1].subtract(trail[n - 2]).scale(1 / Math.max(.5, ago[n - 2] - ago[n - 1]));
        if (n < 3) return newer;
        Vec3 older = trail[n - 2].subtract(trail[n - 3]).scale(1 / Math.max(.5, ago[n - 3] - ago[n - 2]));
        double f = clamp01(ago[n - 1] / Math.max(.5, ago[n - 2] - ago[n - 1]));
        return older.add(newer.subtract(older).scale(f));
    }

    /** Soft bands of blue light round the sphere (owner's restore after round 22): a few broad, soft strokes rooted inside the rim on the
     *  camera side, each sweeping round the edge in its own sector, see-through and light-coloured so they melt into the glow; each lives a
     *  while (swells, sweeps, fades) and is reborn in the same sector. Moving drags them back. No sharp brushwork: Blue stays calm. */
    private static void pressure(Ctx c, Vec3 at, double r, double age, Vec3 vel, boolean ready, double alpha) {
        Vec3 toEye = c.eye.subtract(at);
        if (toEye.lengthSqr() < 1e-6) return;
        toEye = toEye.normalize();
        Vec3[] uw = basis(toEye);
        int count = ready ? 5 : 4;
        for (int i = 0; i < count; i++) {
            long sd = 2100 + i * 83L;
            double life = 18 + 10 * hash(sd), ph = age / life + hash(sd + 1);
            long cyc = (long) Math.floor(ph);
            double u = ph - cyc;
            long cs = sd + cyc * 7919L;
            double env = smooth(u / .25) * (1 - smooth((u - .45) / .55)) * alpha;
            if (env <= 0) continue;
            double a0 = (i + .8 * hash(cs)) / count * Math.PI * 2, span = (1.4 + .8 * hash(cs + 1)) * (.7 + .3 * u), sign = hash(cs + 2) < .5 ? 1 : -1;
            double out = r * (.3 + .4 * hash(cs + 3)) * (.5 + .5 * u), wid = r * (.42 + .28 * hash(cs + 4)) * (.8 + .4 * u);
            int k = 20;
            Vec3[] pts = new Vec3[k + 1];
            double[] w = new double[k + 1];
            for (int j = 0; j <= k; j++) {
                double t = (double) j / k, a = a0 + sign * span * t, rr = r * .65 + out * t * (1 + .1 * snoise(cs + 5, t * 3 + age * .1));
                pts[j] = at.add(toEye.scale(r * .45)).add(uw[0].scale(Math.cos(a) * rr)).add(uw[1].scale(Math.sin(a) * rr)).subtract(vel.scale(1.5 * t));
                w[j] = wid * Math.pow(Math.sin(Math.PI * (.15 + .85 * t)), .6) * (.6 + .5 * vnoise(cs + 6, t * 3 + u * 2));
            }
            int color = i % 2 == 0 ? 0x2A8CFF : ready ? CYAN : 0x4AA8FF;
            fxRibbon(c, pts, w, PLASMA, (int) (cs % 997), color, env * .4);
        }
    }

    /** A few bands going round the sphere on tilted circles, slowly (faster when ready or moving); the part on the trailing side is dragged back
     *  against the motion. Drawn in two halves: behind the sphere ({@code front} false) and in front of it. */
    private static void bands(Ctx c, Vec3 at, double r, double age, boolean ready, Vec3 vel, double alpha, double coreD, boolean front) {
        double speed = vel.length();
        Vec3 vhat = speed > 1e-4 ? vel.scale(1 / speed) : Vec3.ZERO;
        for (int i = 0; i < (ready ? 5 : 3); i++) {
            long sd = 1300 + i * 97L;
            Vec3 axis = nvec(sd, age * .004).add(0, .5, 0).normalize();
            Vec3[] uw = basis(axis);
            double rad = r * (.92 + .18 * hash(sd + 1)), arc = 2.2 + 1.4 * hash(sd + 2);
            double phase = hash(sd + 3) * Math.PI * 2 + age * (.05 + .04 * hash(sd + 4)) * (ready ? 1.6 : 1) * (1 + Math.min(2, speed * 1.5));
            double wmax = r * (.11 + .09 * hash(sd + 5));
            int k = 24;
            Vec3[] pts = new Vec3[k + 1];
            double[] w = new double[k + 1];
            for (int j = 0; j <= k; j++) {
                double t = (double) j / k, a = phase + arc * t;
                Vec3 radial = uw[0].scale(Math.cos(a)).add(uw[1].scale(Math.sin(a)));
                double drag = .5 + .5 * Math.max(0, -radial.dot(vhat));
                Vec3 p = at.add(radial.scale(rad * (1 + .05 * snoise(sd + 6, t * 4 + age * .08)))).subtract(vel.scale(2.2 * drag));
                pts[j] = p;
                double near = smooth(.5 - (p.distanceTo(c.eye) - coreD) / (r * .4));
                w[j] = wmax * Math.pow(Math.sin(Math.PI * t), .6) * (.6 + .6 * vnoise(sd + 7, t * 3 - age * .1)) * (front ? near : 1 - near);
            }
            fxRibbon(c, pts, w, PLASMA, (int) (sd % 997), i % 3 == 0 ? ICE : i % 3 == 1 ? CYAN : 0x3A9BFF, alpha * .62);
        }
    }

    /** The trail while it moves (owner's review of round 15): one ragged brush stroke of blue lying along the path it really took, its head dense
     *  where Blue is, its tail torn and fading. Only the last stretch of the path is used, longer the faster it went (so a hard turn leaves a
     *  long sweep, but never one straight spear back to an old position); the gaps in it are fixed to the paint, not to Blue, and it all fades as
     *  the path ages, so it dissolves after Blue stops. A wash under it and a bright streak along it. */
    private static void smears(Ctx c, Vec3 at, double r, double age, Vec3[] trail, double[] ago, double alpha) {
        int n = trail.length;
        if (n < 1) return;
        List<Vec3> path = new ArrayList<>();
        List<Double> when = new ArrayList<>();
        path.add(at); when.add(0.0);
        double len = 0, first = velocity(trail, ago).length();
        double maxLen = r * (2 + 4 * Math.min(1, first / .8));
        for (int i = n - 1; i >= 0 && len < maxLen && ago[i] <= 16; i--) {
            len += trail[i].distanceTo(path.get(path.size() - 1));
            path.add(trail[i]); when.add(ago[i]);
        }
        double tailAgo = when.get(when.size() - 1), strength = smooth((len / Math.max(.5, tailAgo) - .03) / .25) * alpha;
        if (path.size() < 2 || len < r * .3 || strength <= .02) return;
        double[] cum = new double[path.size()];
        for (int i = 1; i < cum.length; i++) cum[i] = cum[i - 1] + path.get(i).distanceTo(path.get(i - 1));
        int k = 24;
        Vec3[] base = new Vec3[k + 1];
        double[] laid = new double[k + 1], wash = new double[k + 1], main = new double[k + 1], streak = new double[k + 1];
        for (int j = 0; j <= k; j++) {
            double t = (double) j / k, d = Math.min(len, cum[cum.length - 1]) * (1 - t);
            int i = 1;
            while (i < cum.length - 1 && cum[i] < d) i++;
            double f = (d - cum[i - 1]) / Math.max(1e-6, cum[i] - cum[i - 1]);
            base[j] = path.get(i - 1).add(path.get(i).subtract(path.get(i - 1)).scale(f));
            double w0 = when.get(i - 1) + (when.get(i) - when.get(i - 1)) * f;
            laid[j] = age - w0;
            // Smooth and full (owner, 2026-11-18): broad, no sharp streak; it only frays near the very end of the tail.
            double fade = 1 - smooth(w0 / 16), torn = smooth((vnoise(1520, laid[j] * .5) - .5 * Math.pow(1 - t, 2)) / .15);
            // The tail does not narrow to a point (that read as a cone): it stays broad and dissolves (fade, erosion), while two washes fan
            // apart under it, so the end spreads out like paint thinning on a wet sheet.
            // Broad all along (a bulge in the middle), thinning only a little with age: the tail dissolves instead of narrowing into a cone.
            main[j] = r * .95 * (.7 + .3 * Math.sin(Math.PI * t)) * (.45 + .55 * fade) * torn * (.75 + .35 * vnoise(1521, laid[j] * .5)) * smooth(fade / .15);
            wash[j] = r * .95 * (.6 + .4 * t) * fade * (.6 + .5 * vnoise(1523, laid[j] * .4));
            streak[j] = wash[j];
        }
        Vec3[] washPts = new Vec3[k + 1], streakPts = new Vec3[k + 1];
        for (int j = 0; j <= k; j++) {
            double t = (double) j / k;
            Vec3 spread = nvec(1530, laid[j] * .2).normalize().scale(r * .55 * (1 - t) * (1 - t));
            washPts[j] = base[j].add(spread);
            streakPts[j] = base[j].subtract(spread);
        }
        // One soft body with two washes fanning apart at its tail (round 19's look, owner's restore after round 22): no cloud of pieces.
        fxRibbon(c, washPts, wash, PLASMA, 1525, DEEP_BLUE, strength * .45);
        fxRibbon(c, streakPts, streak, PLASMA, 1527, 0x1E8CFF, strength * .4);
        fxRibbon(c, base, main, PLASMA, 1526, ROYAL, strength * .75);
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
            orb(c, at, r * .78, p.variant(), 1, p.tint(), 1);
        }
        if (!c.glow()) return;
        glow(c, at, r * .9, .5 + .3 * surge, p.hot());
        glow(c, at, r * (2 + surge), .2 + .2 * surge, p.halo());
        if (surge <= 0) return;
        // Pulses of pressure going out, one after another.
        for (int k = 0; k < 2; k++) {
            double ring = ((age + k * 5) / 10.0) % 1;
            fxSprite(c, at, r * (1 + 1.8 * ring), SHOCK, (int) (5300 + seedOff % 97 + k), p.light(), .5 * (1 - ring) * surge, 0);
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
        double spin = charged ? 1.4 : 1;
        List<Arm> arms = new ArrayList<>();
        for (int i = 0; i < (charged ? 12 : 9); i++) {
            long sd = 900 + seedOff + i * 131L;
            arms.add(plasmaArm(c, at, r, age, sd, spin, 0));
            if (hash(sd + 8) < .45) arms.add(plasmaArm(c, at, r, age, sd + 50000, spin, 1));
        }
        for (int i = 0; i < (charged ? 7 : 5); i++) arms.add(plasmaArm(c, at, r, age, 3900 + seedOff + i * 151L, spin, 1));
        for (int i = 0; i < (charged ? 8 : 6); i++) arms.add(plasmaArm(c, at, r, age, 4700 + seedOff + i * 157L, spin, 2));
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
            orb(c, at, s * .6, 1, 0, RED, 1);
            redMass(c, at, s, age, coreD, true);
            inkTorn(c, at, s, age, back, reach);
        }
        // Short flares: the wave strokes, kept short (the smears carry the big shapes).
        for (Wave w : waves) strokes(c, at, s, w, reach * .6, 1);
        if (!c.glow()) return;
        glow(c, at, s * .4, .45, 0xFFFFFF);
        glow(c, at, s * 1.3, .75, RED_CORE);
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
    static void redFlight(Ctx c, Vec3 origin, Vec3 dir, Vec3 head, double age, double fade, long seed, boolean flying) {
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
        if (flying) redOrb(c, head, .75, age, dir.scale(-1));
    }

    /** The release, {@code age} ticks after the shot: flash, two shock rings racing forward, big red smears and ink bursting forward in a cone. */
    private static void release(Ctx c, Vec3 origin, Vec3 dir, double age, double fade) {
        // Owner's review of round 25: a pressure mass let go, not sword slashes. Short, wide, soft masses of red heave forward and outward,
        // swelling and churning, their tails tearing and dissolving; ink is ripped off with them and breaks up fast.
        pressureBlast(c, origin, dir, age, 14, 1.2, 7700, fade);
        if (!c.glow()) return;
        // Flashes are capped by how big they look (owner's review of round 27: never a white-out in first person).
        double far = origin.distanceTo(c.eye);
        if (age < 4) { glow(c, origin, Math.min(4.5, far * .12) * (1 - age / 4), 1 - age / 4, 0xFFFFFF); glow(c, origin, Math.min(9, far * .3) * (1 - age / 5), .85 * (1 - age / 4), RED); }
        if (age < 6) fxSprite(c, origin, Math.min(3 + age * .6, far * .3), RAYS, 7790, 0xFF7090, .45 * (1 - age / 6) * fade, age * .03);
        glow(c, origin.add(dir.scale(2)), 7, .35 * (1 - age / 14) * fade, 0xFF5A78);
        for (int r = 0; r < 2; r++) {
            double ra = age - r * 2.5;
            if (ra < 0 || ra > 10) continue;
            pressureWave(c, origin.add(dir.scale(.5 + ra * 1.1)), dir, .4 + ra * .55, .25 * (1 - ra / 10), ra, 7720 + r, (1 - ra / 10) * fade);
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
        glow(c, at, 1 + age * .35, fade, blue ? ICE : RED_CORE);
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
            glow(c, at, Math.min(2.4, far * .1) * (1 - .6 * squeeze), 1, 0xFFFFFF);
            glow(c, at, Math.min(4, far * .22) * (1 - .5 * squeeze), .9, RED);
            // The peak (owner's review of round 28): for about a tick and a half, a tight red-white star on the target; compact, so the
            // camera is never washed out.
            if (age < 1.5) {
                double peak = 1 - age / 1.5;
                glow(c, at, Math.min(1.6, far * .07), peak, 0xFFFFFF);
                glow(c, at, Math.min(3, far * .14), peak, RED_CORE);
                fxSprite(c, at, Math.min(3.5, far * .17), RAYS, 8895, 0xFFE0E6, peak, 0);
            }
        }
        if (b < 0) return;
        double out = 1 - Math.pow(1 - Math.min(1, b / 10), 2.5);
        pressureBlast(c, at, dir, b, 20, 1.7, 8800, 1, true);
        if (c.body()) {
            // Ground wave: ragged and uneven, not a perfect ring.
            if (b < 12) pressureWave(c, at.add(0, -.9, 0), new Vec3(0, 1, 0), 2 + b * 1.4, .6 * (1 - b / 12), b, 8860, .6 * (1 - b / 12));
            fxFlat(c, at.add(0, -.95, 0), 3 + 2.5 * out, SMOKE, 171, RED_BODY, .5 * fade);
            splatter(c, at, b, dir);
        }
        if (!c.glow()) return;
        double far = at.distanceTo(c.eye);
        if (b < 2) glow(c, at, Math.min(6, far * .2) * (1 - b / 2), .9 * (1 - b / 2), RED_CORE);
        if (b < 6) fxSprite(c, at, Math.min(3 + b * .7, far * .3), RAYS, 8890, 0xFF7090, .35 * (1 - b / 6), b * .02);
        glow(c, at.add(dir.scale(2 * out)), 6, .3 * fade, 0xFF5A78);
        if (b < 12) pressureWave(c, at.add(dir.scale(b * .4)), dir, 1 + b * 1.1, .5 * (1 - b / 12), b, 8870, 1 - b / 12);
    }

    /** A pressure wave: not a drawn circle but a ragged, broken band of hot red air round {@code n}: its radius bulges and dents, its width
     *  swells and thins, and it is torn open in places. */
    private static void pressureWave(Ctx c, Vec3 at, Vec3 n, double radius, double width, double age, long seed, double alpha) {
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
        fxRibbon(c, pts, w, PLASMA, (int) (seed % 977), RED_LIGHT, alpha * .8);
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
        // The blast bursts out fast and slows (ease-out), without the infinite first step of a square root.
        if (age > det) s += 72 * (1 - Math.pow(1 - clamp01((age - det) / 26.0), 2));
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
        if (c.body() && tint > 0) sprite(c, eye.add(look.scale(1)), 6, .4 * tint, NIGHT, BlueRedAtlas.SOLID);
        double rush = smooth((age - (detonate - 30)) / 30.0) * (1 - clamp01((age - detonate - 10) / 30.0)) * clamp01(1 - eye.distanceTo(at) / 90);
        speedLines(c, eye, look, .85 * rush, PALE);
        double blink = 1 - clamp01(age / BlueRedRules.PURPLE_BLINK);
        if (blink > 0 && c.body() && eye.distanceTo(at) < 60) sprite(c, eye.add(look.scale(.8)), 6, blink, 0xFFFFFF, BlueRedAtlas.SOLID);
        if (age < merge) {
            converge(c, base, age, eye, look);
            return;
        }
        double s = purpleSize(age), grow = smooth((age - merge) / (double) (detonate - merge)), pre = 1 - blast;
        double breathe = 1 + .05 * Math.sin(age * .31) + .035 * Math.sin(age * .77 + 1) - .08 * grow * grow * smooth((age - detonate + 12) / 12.0) * pre;
        double tremble = (.05 + .35 * grow * grow) * pre;
        at = at.add(jitter(age, tremble, 37));
        double bodyAlpha = (1 - blast * .85) * fade;
        if (c.body()) {
            fxSprite(c, at, s * 1.9, SHOCK, 205, PALE, (.18 + .1 * grow) * pre * fade, 0);
            orb(c, at, s * breathe, 2, age >= detonate ? 2 : grow > .6 ? 1 : 0, VIOLET, bodyAlpha);
            debris(c, at, s * 1.4, age, 34, 0x1A0A26, 820, pre * fade, true);
            for (int b = 0; b < 8; b++) {
                Vec3 d = nvec(2300 + b, age * .03 + b).normalize(), along = d.cross(nvec(2310 + b, age * .04)).normalize();
                liveBolt(c, at.add(d.scale(s * 1.01)), at.add(d.add(along.scale(.7)).normalize().scale(s * 1.03)), 7, .3, .03, INK, .8 * pre * fade, 2320 + b * 17, age, .8);
            }
            fall(c, at, s, age, (int) (30 * blast), 1240, 0x1A0A26, blast * fade, false);
        }
        if (!c.glow()) return;
        // A disc of shockwaves spun off the equator, continuous: each ring is born, widens and fades on its own clock.
        for (int k = 0; k < 3; k++) {
            double ring = ((age + k * 3) / 9.0) % 1;
            fxFlat(c, at, s * (1.1 + 1.6 * ring), SHOCK, 840 + k, ORCHID, .55 * (1 - ring) * pre * fade);
        }
        stormBands(c, at, s, age, 16, fade);
        streaks(c, at, s * .95, s * 4, age, (int) (110 * pre), ORCHID, 860, 1.1, pre * fade, true);
        streaks(c, at, s * .4, s * 2.2, age, (int) (140 * blast), PALE, 1100, 2.6, blast * fade, false);
        // After the burst the haze thins fast and the white core stays bounded, so the column and the shockwaves read instead of a white-out.
        double haze = 1 - .8 * blast;
        glow(c, at, s * 2.6, .4 * fade * haze, VIOLET);
        glow(c, at, s * 1.3, .35 * fade * haze, ORCHID);
        glow(c, at, Math.min(s * .55 * breathe * (1 + 2 * blast), 22), (.7 + .3 * blast) * fade * (1 - .5 * blast), 0xFFFFFF);
        sparks(c, at, s * 1.4, age, 70, PALE, 200);
        // Lightning that never pops: each bolt keeps its identity, its anchor drifts over the sphere and its shape crawls with time.
        // Owner, 2026-11-18: much more lightning on the surface.
        int crawl = 16 + (int) (14 * grow);
        for (int b = 0; b < crawl; b++) {
            Vec3 d = nvec(900 + b, age * .02 + b).normalize(), along = d.cross(nvec(950 + b, age * .03)).normalize();
            Vec3 from = at.add(d.scale(s));
            liveBolt(c, from, at.add(d.add(along.scale(.9)).normalize().scale(s * 1.02)), 7, .3, .09 + .06 * grow, b % 3 == 0 ? 0xFFFFFF : b % 3 == 1 ? PALE : ORCHID, pre * fade, 1000 + b * 17, age, .8);
        }
        int arcs = 3 + (int) (7 * grow + 16 * blast);
        for (int b = 0; b < arcs; b++) {
            Vec3 d = nvec(1500 + b, age * .015 + b * 3).normalize();
            double reach = s * (.9 + .6 * vnoise(1600 + b, age * .05)) + blast * (40 + 60 * hash(1700 + b));
            Vec3 from = at.add(d.scale(s * .95));
            liveBolt(c, from, from.add(d.scale(reach)), 10, .35, .1 + .2 * blast, b % 2 == 0 ? PALE : ORCHID, fade, 1800 + b * 23, age, .6);
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
                liveBolt(c, from, g.add(nvec(1950 + k * 3 + j, 0).scale(.6)), 14, .45, (j == 0 ? .3 : .12) * (.5 + .5 * a), j == 0 ? 0xFFFFFF : ORCHID, a, 1960 + k * 41 + j * 7, age, .9);
            glow(c, g, 3 + 5 * (1 - a), a, 0xFFFFFF);
            glow(c, g, 7, .6 * a, ORCHID);
            fxFlat(c, g.add(0, .1, 0), 1.5 + since * 1.2, SHOCK, 1990 + k, PALE, .8 * a);
            sparks(c, g, 1 + since * .5, age, 24, PALE, 2000 + k * 5);
        }
        if (blast > 0) {
            fxFlat(c, at.add(0, -1, 0), s * 1.8, SHOCK, 210, PALE, (1 - blast) * fade);
            fxSprite(c, at, s * 2.4, RAYS, 211, PALE, (1 - blast * .7) * fade, age * .01);
            // A second, wider shock and a ground ring for a bigger, prettier blast.
            fxFlat(c, at.add(0, -1, 0), s * 2.6 * blast, SHOCK, 213, ORCHID, (1 - blast) * .8 * fade);
            fxSprite(c, at, s * 3 * blast, SHOCK, 214, PALE, (1 - blast) * .6 * fade, 0);
            fxSprite(c, at, s * 1.3, SHOCK, 212, ORCHID, (1 - blast) * fade, 0);
        }
        if (age >= detonate) {
            // Shockwaves rolling out over the map one after another (the column was dropped, owner round 37).
            double after = age - detonate, ground = Double.isNaN(groundY) ? at.y - BlueRedRules.PURPLE_LIFT - 2 : groundY;
            Vec3 foot = new Vec3(at.x, ground + .15, at.z);
            for (int r = 0; r < 7; r++) {
                double ra = after - r * 5;
                if (ra < 0 || ra > 34) continue;
                pressureWave(c, foot, new Vec3(0, 1, 0), 5 + ra * 2.8, 4.5 * (1 - ra / 34), ra, 2130 + r, (1 - ra / 34) * fade);
            }
        }
        double white = age < detonate ? 0 : 1 - clamp01((age - detonate) / 4.0);
        if (white > 0) sprite(c, eye.add(look.scale(.8)), 6, .85 * white * white, 0xFFFFFF, BlueRedAtlas.SOLID);
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
        if (age < ct) return .3 + 2.1 * (1 - Math.pow(1 - clamp01((age - b) / (double) (ct - b)), 2));
        double spinning = 2.4 + .8 * smooth((age - ct) / (double) (le - ct)) + .1 * Math.sin((age - ct) * .17);
        return spinning + (FACE_GAP - spinning) * smooth((age - le) / (double) (se + 8 - le));
    }

    /** How far apart they hold at the face-off. */
    static final double FACE_GAP = 4.2;

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
        if (red.distanceTo(c.eye) > blue.distanceTo(c.eye)) {
            twinOrb(c, red, redR, age, RED_P, vr, 77777, surge);
            twinOrb(c, blue, 1.1, age, BLUE_P, vb, 0, surge);
        } else {
            twinOrb(c, blue, 1.1, age, BLUE_P, vb, 0, surge);
            twinOrb(c, red, redR, age, RED_P, vr, 77777, surge);
        }
        // Small, bright purple lightning in the middle while they face off.
        if (c.glow() && surge > 0) {
            double flick = .6 + .4 * Math.abs(Math.sin(age * 2.3));
            glow(c, center, .4 + .3 * flick, .5 * surge * flick, 0xFFFFFF);
            glow(c, center, 1.4, .5 * surge, ORCHID);
            for (int b = 0; b < 6; b++) {
                Vec3 d = nvec(3880 + b, age * .35).normalize();
                liveBolt(c, center, center.add(d.scale(.7 + .9 * vnoise(3890 + b, age * .5))), 5, .5, .018, b % 2 == 0 ? PALE : ORCHID, surge, 3895 + b * 11, age, 1);
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
        int bolts = (int) (3 + 4 * contact + 7 * build + 6 * surge);
        for (int j = 0; j < bolts; j++) {
            Vec3 from = red.add(nvec(3000 + j, age * .06).scale(.5)), to = blue.add(nvec(3100 + j, age * .06).scale(.5));
            // Thinner and coloured: red from Red's side, blue from Blue's, violet between; dimmer so the colour shows.
            liveBolt(c, from, to, 9, .3, .014 + .025 * build + .02 * surge, j % 3 == 0 ? RED_LIGHT : j % 3 == 1 ? CYAN : ORCHID, .6, 3200 + j * 29, age, .9);
        }
        // Seen up close at the face-off, so kept from burning the frame white.
        fxBeam(c, red, blue, .2 + .25 * link, .2 + .25 * link, FLOW, 3290, ORCHID, .22 * link);
        for (int j = 0; j < 4; j++) sparks(c, red.add(blue.subtract(red).scale(.2 + .2 * j)), .8 + .6 * link, age * 1.5, (int) (8 + 22 * link), j % 2 == 0 ? PALE : ORCHID, 3300 + j * 7);
        if (contact > 0 && !Double.isNaN(groundY)) {
            double k = 1 - contact;
            pressureWave(c, new Vec3(center.x, groundY + .1, center.z), new Vec3(0, 1, 0), 2 + 22 * k, 2.6 * contact, age, 3960, contact);
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
        glow(c, center, .6 + 1.2 * k, .4 * k, 0xFFFFFF);
        glow(c, center, 2 + 3 * k, .45 * k, ORCHID);
        for (int b = 0; b < (int) (2 + 6 * k); b++) {
            // Short and all round (owner, 2026-11-18): a ball of crackling, not spikes up and down.
            Vec3 d = nvec(3700 + b, age * .2).normalize();
            liveBolt(c, center, center.add(d.scale(.7 + .9 * k * vnoise(3720 + b, age * .3))), 5, .45, .02 + .04 * k, b % 3 == 0 ? PALE : ORCHID, k, 3730 + b * 19, age, 1);
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
        double push = smooth((v - 14) / (span * .78)), absorb = smooth((v - span * .74) / (span * .26));
        return smooth((push - .2) / .75) * (1 - absorb);
    }

    /** The tick the 2D spheres touch and Purple starts forming. */
    static int voidTouch() { return BlueRedRules.PURPLE_SWIRL_END + (int) Math.round((BlueRedRules.PURPLE_MERGE - BlueRedRules.PURPLE_SWIRL_END) * .66); }

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
        double push = smooth((v - 14) / (span * .78)), form = smooth((v - span * .66) / (span * .24)), absorb = smooth((v - span * .74) / (span * .26));
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
        double gapD = 4.8 * (1 - .8 * push) + .15 * (1 - absorb), r = 2.5 * (1 - .65 * absorb);
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
                for (int j = 0; j < 7; j++) {
                    long sd = 4600 + side * 50 + j;
                    double d = r * (.35 + .5 * j) * (1 + .7 * close) + r * .3 * snoise(sd, age * .05), size = r * (1.55 - .1 * j) * (1 + .12 * snoise(sd + 1, age * .07));
                    Vec3 q = p.add(outward.scale(d)).add(up.scale(r * .25 * snoise(sd + 2, age * .04)));
                    fxSprite(c, q, size, SMOKE, (int) (sd % 977), j % 3 == 0 ? pal.mid() : j % 3 == 1 ? pal.deep() : pal.light(), (.26 - .025 * j) * orbs, age * .02 * (j % 2 == 0 ? 1 : -1) + j);
                }
                for (int j = 0; j < 30 + (int) (40 * close); j++) {
                    long sd = 4700 + side * 100 + j;
                    double life = (16 + 10 * hash(sd)) * (1 - .5 * close), ph = age / life + hash(sd + 1);
                    long cyc = (long) Math.floor(ph);
                    double u = ph - cyc;
                    long cs = sd + cyc * 7919L;
                    double ang = (hash(cs) - .5) * 2.4;
                    Vec3 dir = outward.scale(Math.cos(ang)).add(up.scale(Math.sin(ang))).normalize();
                    Vec3 q = p.add(dir.scale(r * (.85 + 3.2 * u))).add(view.scale(.2));
                    fxSprite(c, q, r * (.05 + .07 * hash(cs + 2)) * (1 + 1.5 * u), SMOKE, (int) (cs % 991), j % 2 == 0 ? pal.light() : pal.hot(), smooth(u / .1) * (1 - u) * .8 * orbs, u * 3);
                }
                // Screen-aligned, so it stays a true circle even near the edge of the view.
                orbFacing(c, p, r, 3, side, tint, orbs, look);
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
                glow(c, p, r * 1.6, .12 * orbs, tint);
                // The backdrop lit by each sphere: reddish round Red, bluish round Blue (owner, 2026-11-18).
                glow(c, p.subtract(view.scale(2)), r * 4.5, .22 * orbs, side == 0 ? RED : 0x2A6CFF);
                // Its own lightning crackling round it, more as they close.
                for (int b = 0; b < 2 + (int) (6 * close); b++) {
                    Vec3 d0 = nvec(4900 + side * 40 + b, age * .3).normalize(), d1 = d0.add(nvec(4920 + side * 40 + b, age * .35).scale(.9)).normalize();
                    liveBolt(c, p.add(d0.scale(r * .9)), p.add(d1.scale(r * (1.6 + 1.2 * vnoise(4940 + b, age * .4)))), 7, .5, .07 + .07 * close,
                            b % 2 == 0 ? pal.light() : pal.hot(), orbs * (.5 + .5 * close), 4960 + side * 100 + b * 13, age, 1);
                }
            }
        }
        // Lightning crackling on Red's edge facing Blue (ref 42), growing as they close.
        if (c.glow() && orbs > .02) {
            Vec3 face = red.add(dirRed.scale(-r * .95));
            for (int j = 0; j < (int) (2 + 4 * close); j++) {
                Vec3 to = face.add(nvec(3000 + j, age * .15).scale(r * .7)).subtract(dirRed.scale(r * .3));
                liveBolt(c, face.add(nvec(3050 + j, age * .1).scale(r * .4)), to, 6, .5, .03 + .03 * close, j % 2 == 0 ? ORCHID : PALE, orbs, 3100 + j * 17, age, 1);
            }
            for (int j = 0; j < (int) (12 * close); j++) {
                Vec3 from = red.add(nvec(3200 + j, age * .06).scale(.6)), to = blue.add(nvec(3300 + j, age * .06).scale(.6));
                liveBolt(c, from, to, 8, .3, .08 + .1 * close, j % 3 == 0 ? PALE : ORCHID, .95 * orbs, 3400 + j * 29, age, .9);
            }
        }
        if (c.glow() && close > 0) {
            // Chaos where they meet, from the two facing edges (owner, 2026-11-18): sparks and lightning thick and bright off both rims.
            Vec3 redFace = red.subtract(dirRed.scale(r * .95)), blueFace = blue.add(dirRed.scale(r * .95));
            for (int e = 0; e < 2; e++) {
                Vec3 f = e == 0 ? redFace : blueFace, out = e == 0 ? dirRed.scale(-1) : dirRed;
                sparks(c, f, .6 + 2.5 * close, age * 1.6, (int) (70 * close), e == 0 ? RED_CORE : ICE, 4980 + e * 5);
                for (int b = 0; b < (int) (2 + 8 * close); b++) {
                    Vec3 d = out.add(nvec(5010 + e * 40 + b, age * .3).scale(1.1)).normalize();
                    liveBolt(c, f.add(up.scale((hash(5000 + b) - .5) * r)), f.add(d.scale(r * (.9 + 1.6 * vnoise(5060 + b, age * .4)))), 8, .5, .1 + .12 * close,
                            b % 3 == 0 ? 0xFFFFFF : b % 3 == 1 ? PALE : ORCHID, close, 5100 + e * 100 + b * 13, age, 1);
                }
                glow(c, f, r * (.6 + .8 * close), .7 * close, e == 0 ? RED_CORE : ICE);
            }
            speedLines(c, eye, look, .55 * close, PALE);
        }
        // Birth of Hollow Purple between them: the centre flickers, lightning crackles out of it, sparks are pulled into it.
        double birth = smooth((push - .55) / .45) * (1 - form) * back;
        if (c.glow() && birth > 0) {
            double flick = .5 + .5 * Math.abs(Math.sin(age * 1.7) * Math.sin(age * 2.9 + 1));
            glow(c, at, .5 + 1.8 * birth * flick, birth * flick, 0xFFFFFF);
            glow(c, at, 1.5 + 3 * birth, .6 * birth, ORCHID);
            for (int b = 0; b < (int) (3 + 7 * birth); b++) {
                Vec3 d = nvec(3900 + b, age * .3).normalize();
                liveBolt(c, at, at.add(d.scale(1 + 2.5 * birth * vnoise(3910 + b, age * .4))), 6, .5, .02 + .03 * birth, b % 2 == 0 ? PALE : ORCHID, birth * flick, 3920 + b * 13, age, 1);
            }
            streaks(c, at, .3, 2 + 4 * birth, age, (int) (40 * birth), PALE, 3950, 2.4, birth, true);
        }
        if (form > 0) {
            double s = (1.2 + 4.8 * form) * (1 + .1 * Math.sin(age * .31));
            if (c.body()) orb(c, at, s, 2, 1, VIOLET, form * back);
            if (c.glow()) {
                stormBands(c, at, s, age, 12, form * back);
                streaks(c, at, s * .95, s * 3.5, age, 60, PALE, 1450, 1.6, form * back, true);
                fxSprite(c, at, 4 + 14 * form, RAYS, 300, PALE, form * back, age * .02);
                for (int b = 0; b < 8; b++) {
                    Vec3 d = nvec(3600 + b, age * .02 + b).normalize();
                    liveBolt(c, at.add(d.scale(s)), at.add(d.scale(s * (1.6 + .6 * vnoise(3700 + b, age * .05)))), 8, .35, .04, ORCHID, form * back, 3800 + b * 31, age, .7);
                }
                sparks(c, at, s * 1.3, age, 50, PALE, 310);
            }
        }
        if (c.glow()) for (int i = 0; i < 14; i++) {
            double y = ((i * .29 + age * .006) % 1) * 6 - 3;
            sprite(c, at.add(axis.scale(Math.sin(i * 1.7) * 7)).add(0, y, 0).subtract(view.scale(1)), .14, .6 * show, PALE, BlueRedAtlas.BUBBLE);
        }
    }

    /** Red's impact frames (owner, 2026-11-16): black, red, black, then a fading red wash, with a burst of ink rays from the impact. */
    static int impactColor(int frame) { return frame % 2 == 0 ? INK : RED_BODY; }
    static double impactAlpha(int frame) { return frame < 3 ? .9 - .12 * frame : frame < 6 ? .35 * (6 - frame) / 3.0 : 0; }

    static void impact(Ctx c, Vec3 at, int frame, Vec3 eye, Vec3 look) { impact(c, at, frame, eye, look, false); }

    static void impact(Ctx c, Vec3 at, int frame, Vec3 eye, Vec3 look, boolean purple) {
        double a = impactAlpha(frame);
        if (a <= 0 || !c.body()) return;
        if (!purple) {
            // Owner's review of round 26: the hit must stay visible. Two frames of a light red tint and a hot flash at the contact point only.
            if (frame > 1) return;
            sprite(c, eye.add(look.scale(.5)), 4, .22 * (1 - frame * .5), RED_BODY, BlueRedAtlas.SOLID);
            sprite(c, at, Math.max(1, at.distanceTo(eye)) * .1, .9 * (1 - frame * .5), RED_CORE, BlueRedAtlas.GLOW);
            return;
        }
        int light = purple ? PALE : RED_BODY;
        int back = frame < 3 ? (frame % 2 == 0 ? INK : light) : light, stroke = back == INK ? light : INK;
        sprite(c, eye.add(look.scale(.5)), 4, a, back, BlueRedAtlas.SOLID);
        double dist = Math.max(1, at.distanceTo(eye));
        fxSprite(c, at, dist * .9, RAYS, 320 + frame, stroke, a, frame * .7);
        Vec3[] uw = basis(look);
        for (int i = 0; i < 10; i++)
            if (!purple) fxArc(c, at, uw[0], uw[1], dist * (.12 + .05 * i), i * .63 + frame * .4, 1.4, dist * .012, FLOW, 330 + i, stroke, a);
        // Purple's frames: one big hard circle round it, and a thinner one inside (owner, round 37).
        if (purple) {
            fxArc(c, at, uw[0], uw[1], dist * (.3 + .03 * frame), 0, Math.PI * 2.01, dist * .03, FLOW, 345, stroke, a);
            fxArc(c, at, uw[0], uw[1], dist * (.2 + .02 * frame), 0, Math.PI * 2.01, dist * .01, FLOW, 346, stroke, a);
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
        Vec3 prev = a;
        for (int s = 1; s <= segments; s++) {
            double t = (double) s / segments, env = s == segments ? 0 : Math.sin(Math.PI * t);
            double x = age * crackle;
            Vec3 off = uw[0].scale(snoise(seed + s * 3L, x) + .4 * snoise(seed + s * 3L + 1, x * 3.1))
                    .add(uw[1].scale(snoise(seed + s * 3L + 2, x) + .4 * snoise(seed + s * 3L + 50, x * 2.7)));
            Vec3 q = a.add(d.scale(t)).add(off.scale(jitter * len * env / Math.sqrt(segments)));
            double k = alpha * (1 - .4 * t) * clamp01((Math.min(prev.distanceTo(c.eye), q.distanceTo(c.eye)) - 2) / 3);
            fxBeam(c, prev, q, width * 4, width * 4, BOLT, (int) ((seed + s) % 997), color, k);
            prev = q;
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
            fxSprite(c, p, .05 + .08 * rand.nextDouble(), SPARK, seed + i, i % 3 == 0 ? 0xFFFFFF : color, .95, i);
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
