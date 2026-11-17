package net.godwand.client;

import net.godwand.skill.BlueRedRules;
import net.minecraft.world.phys.Vec3;

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
    static final int ORB = 0, FLOW = 1, FLAME = 2, SMOKE = 3, SHOCK = 4, BOLT = 5, SPARK = 6, RAYS = 7, NEBULA = 8, SPEEDLINES = 9, DEBRIS = 10, RING = 11;
    static final int BLUE_TINT = 0x1A6CFF;

    private BlueRedDraw() {}

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

    static void blue(Ctx c, Vec3 at, boolean ready, double age, double sinceReady) {
        double r = BlueRedVisual.blueRadius(ready ? BlueRedRules.BlueState.READY : BlueRedRules.BlueState.CHARGING, age);
        double appear = smooth(age / 6), beat = ready ? BlueRedVisual.heartbeat(age) : 0, flash = BlueRedVisual.flash(sinceReady);
        at = at.add(jitter(age, ready ? .06 : .03, 11));
        // A small, dense core: no shell. The shape is made by what falls into it.
        if (c.body()) {
            orb(c, at, r * .8, 0, ready ? 1 : 0, BLUE_TINT, appear);
            infall(c, at, r, age, ready ? 34 : 24, 600, 0x2A3550, appear);
            // Blue fire: tongues licking up off the core and bending into the pull.
            for (int i = 0; i < (ready ? 14 : 10); i++) {
                double life = 16 + 10 * hash(5000 + i), ph = age / life + hash(5100 + i), p = ph - Math.floor(ph), grow = Math.sin(Math.PI * p);
                double a = hash(5200 + i) * Math.PI * 2 + age * .05, tilt = (hash(5300 + i) - .5) * 1.2;
                Vec3 out = new Vec3(Math.cos(a) * Math.cos(tilt), Math.sin(tilt) * .6 + .3, Math.sin(a) * Math.cos(tilt)).normalize();
                Vec3 dir = out.add(0, .9, 0).add(new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(.5)).normalize();
                fxTongue(c, at.add(out.scale(r * .65)), dir, r * (.7 + .9 * grow), r * .35, FLAME, 5400 + i, i % 2 == 0 ? DEEP_BLUE : ROYAL, .9 * grow * appear);
            }
        }
        if (!c.glow()) return;
        glow(c, at, r * 1.7, (.3 + .15 * beat) * appear * flicker(age, 3), ROYAL);
        radiate(c, at, r, age * (ready ? 1.4 : 1), ready ? 16 : 11, 640, appear);
        streaks(c, at, r * .5, r * 5, age, ready ? 48 : 32, CYAN, 620, 1.2, appear, true);
        sparks(c, at, r * .9, age, ready ? 18 : 10, ICE, 60);
        if (ready) for (int b = 0; b < 4; b++) {
            Vec3 d = nvec(660 + b, age * .03).normalize();
            Vec3 from = at.add(d.scale(r * .6));
            liveBolt(c, from, from.add(d.add(nvec(670 + b, age * .05).scale(.6)).normalize().scale(r * .8)), 5, .25, .025, CYAN, .9, 680 + b * 10, age, .9);
        }
        if (flash > 0) { glow(c, at, r * 6, flash, 0xFFFFFF); glow(c, at, r * 2.5, flash, 0xFFFFFF); }
    }

    /** Blue's energy radiating outward (owner, 2026-11-18: the energy spreads out while the sphere pulls things in): spiral arms streaming out of
     *  the core, unwinding and thinning as they leave, each on its own plane with its own reach, width and brightness; the gaps along each arm
     *  travel outward. */
    static void radiate(Ctx c, Vec3 at, double r, double age, int count, long seed, double alpha) {
        for (int i = 0; i < count; i++) {
            long sd = seed + i * 97L;
            Vec3 n = new Vec3(.2, 1, .15).add(nvec(sd, i * 1.7 + age * .003).scale(1.1)).normalize();
            Vec3[] uw = basis(n);
            double dirSign = hash(sd + 1) < .75 ? 1 : -1;
            double rOut = r * (1.8 + 1.8 * hash(sd + 2)), turns = (.8 + 1.1 * hash(sd + 3)) * Math.PI * dirSign;
            double phase = hash(sd + 4) * Math.PI * 2 + age * (.03 + .03 * hash(sd + 5)) * dirSign;
            double wmax = r * (.05 + .12 * hash(sd + 6) * hash(sd + 6)), bright = .35 + .5 * hash(sd + 7);
            int k = 28;
            Vec3[] pts = new Vec3[k + 1];
            double[] widths = new double[k + 1];
            for (int j = 0; j <= k; j++) {
                double t = (double) j / k, rad = r * .6 + (rOut - r * .6) * Math.pow(t, .8), a = phase + turns * Math.sqrt(t);
                Vec3 off = n.scale(r * .25 * snoise(sd + 8, t * 3 + age * .05) * t);
                pts[j] = at.add(uw[0].scale(Math.cos(a) * rad)).add(uw[1].scale(Math.sin(a) * rad)).add(off);
                double gap = smooth((vnoise(sd + 9, t * 4 + age * .12) - .28) / .12);
                widths[j] = wmax * (1 - .85 * t) * smooth(t / .06) * smooth((1 - t) / .3) * gap;
            }
            int color = i % 4 == 0 ? ICE : i % 2 == 1 ? CYAN : 0x5E9BFF;
            fxRibbon(c, pts, widths, FLOW, (int) (sd % 997), color, alpha * bright);
        }
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

    /** Red's orb: a small, overloaded core whose energy blows out behind it (owner, 2026-11-18), towards {@code back}: volleys of sharp strokes,
     *  torn fragments and sparks streaming that way, like a comet's tail or a flame in a gale. */
    static void redOrb(Ctx c, Vec3 at, double size, double age, Vec3 back) {
        back = back.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : back.normalize();
        if (c.body()) {
            orb(c, at, size * .75, 1, 0, RED, 1);
            fall(c, at, size * 1.1, age * 1.6, 20, 700, INK, .95, false);
        }
        ruptures(c, at, size, age, 730, 1, back);
        if (!c.glow()) return;
        glow(c, at, size * 1.6, .35 * flicker(age, 7), RED);
        streaks(c, at, size * .8, size * 6, age, 44, RED_LIGHT, 720, 1.8, 1, false);
        sparks(c, at, size * 2.8, age * 1.6, 34, RED_LIGHT, 100);
        fall(c, at, size * .9, age * 2.2, 16, 770, RED_CORE, .9, false);
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

    /** Red in flight: the orb at the head, two thin spiralling threads of light behind it, a hot streak, sonic rings, sparks. */
    static void redFlight(Ctx c, Vec3 origin, Vec3 dir, Vec3 head, double age, double fade, long seed, boolean flying) {
        if (fade <= 0) return;
        double tailLength = Math.min(head.distanceTo(origin), 24);
        Vec3[] uw = basis(dir);
        for (int h = 0; h < 2 && c.glow(); h++) {
            int n = 30;
            Vec3[] pts = new Vec3[n + 1];
            double[] widths = new double[n + 1];
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n, back = (1 - t) * tailLength, turn = h * Math.PI - age * .9 + back * .6;
                double radius = (.15 + .7 * (1 - t)) * (1 + .3 * snoise(seed + h, back * .3 + age * .1));
                pts[i] = head.subtract(dir.scale(back)).add(uw[0].scale(Math.cos(turn) * radius)).add(uw[1].scale(Math.sin(turn) * radius));
                widths[i] = .025 + .045 * t;
            }
            fxRibbon(c, pts, widths, BOLT, 120 + h, RED_LIGHT, fade);
        }
        if (c.glow()) {
            fxBeam(c, head.subtract(dir.scale(Math.min(tailLength, 12))), head, .02, .18, BOLT, 130, RED, fade);
            for (int k = 1; k <= 6; k++) {
                double back = k * 3.5;
                if (back > tailLength) break;
                orbit(c, head.subtract(dir.scale(back)), dir, .35 + back / BlueRedRules.RED_SPEED * .22, .05, RED_LIGHT, .75 * fade * (1 - k / 7.0));
            }
        }
        if (flying) redOrb(c, head, .5, age, dir.scale(-1));
        if (!c.glow()) return;
        Random rand = new Random(seed);
        for (int i = 0; i < 22; i++) {
            double t = rand.nextDouble(), drift = Math.max(0, age - t * 6) * .07;
            Vec3 p = head.subtract(dir.scale(t * tailLength)).add(gauss(rand).scale(drift + .2));
            if (p.distanceTo(c.eye) > 1.2) fxSprite(c, p, .06 + .05 * rand.nextDouble(), SPARK, 140 + i, i % 4 == 0 ? RED_CORE : RED, .9 * fade * (1 - t * .6), 0);
        }
    }

    // ---- bursts ----

    static void burst(Ctx c, Vec3 at, double age, boolean blue) {
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

    // ---- Purple: compression ----

    /** Radius of the world Purple: picks up from the size the void left it at, swells to its full size, then the blast carries it on. */
    static double purpleSize(double age) {
        int merge = BlueRedRules.PURPLE_MERGE, det = BlueRedRules.PURPLE_DETONATE;
        double grow = smooth((age - merge) / (double) (det - merge));
        double s = 6 + 6 * grow;
        // The blast bursts out fast and slows (ease-out), without the infinite first step of a square root.
        if (age > det) s += 36 * (1 - Math.pow(1 - clamp01((age - det) / 18.0), 3));
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
        glow(c, at, s * 2.6, .3 * fade, VIOLET);
        glow(c, at, s * .45 * breathe * (1 + 2 * blast), (.55 + .35 * blast) * fade, 0xFFFFFF);
        sparks(c, at, s * 1.4, age, 70, PALE, 200);
        // Lightning that never pops: each bolt keeps its identity, its anchor drifts over the sphere and its shape crawls with time.
        int crawl = 6 + (int) (6 * grow);
        for (int b = 0; b < crawl; b++) {
            Vec3 d = nvec(900 + b, age * .02 + b).normalize(), along = d.cross(nvec(950 + b, age * .03)).normalize();
            Vec3 from = at.add(d.scale(s));
            liveBolt(c, from, at.add(d.add(along.scale(.9)).normalize().scale(s)), 7, .3, .05 + .03 * grow, ORCHID, .9 * pre * fade, 1000 + b * 17, age, .8);
        }
        int arcs = 3 + (int) (7 * grow + 16 * blast);
        for (int b = 0; b < arcs; b++) {
            Vec3 d = nvec(1500 + b, age * .015 + b * 3).normalize();
            double reach = s * (.9 + .6 * vnoise(1600 + b, age * .05)) + blast * (40 + 60 * hash(1700 + b));
            Vec3 from = at.add(d.scale(s * .95));
            liveBolt(c, from, from.add(d.scale(reach)), 10, .35, .06 + .12 * blast, ORCHID, fade, 1800 + b * 23, age, .6);
        }
        if (blast > 0) {
            fxFlat(c, at.add(0, -1, 0), s * 1.8, SHOCK, 210, PALE, (1 - blast) * fade);
            fxSprite(c, at, s * 2.4, RAYS, 211, PALE, (1 - blast * .7) * fade, age * .01);
            fxSprite(c, at, s * 1.3, SHOCK, 212, ORCHID, (1 - blast) * fade, 0);
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

    // ---- Convergence (owner, 2026-11-17): contact, playful orbit, lift and tighten, compression ----

    /** Distance between the two orbs: they bounce apart on contact, chase each other wide, are drawn in as they lift, then crushed to a point. */
    static double separation(double age) {
        int b = BlueRedRules.PURPLE_BLINK, ct = BlueRedRules.PURPLE_CONTACT, pl = BlueRedRules.PURPLE_PLAY, le = BlueRedRules.PURPLE_LIFT_END, se = BlueRedRules.PURPLE_SWIRL_END;
        // Wide while they chase (owner's ref 40), so their rings circle well clear of the orbs instead of hiding behind them.
        if (age < ct) return .3 + 4.9 * (1 - Math.pow(1 - clamp01((age - b) / (double) (ct - b)), 2));
        double play = 5.2 + .7 * Math.sin((Math.min(age, pl) - ct) * .21);
        if (age < pl) return play;
        if (age < le) return play + (.9 - play) * smooth((age - pl) / (double) (le - pl));
        return .05 + .9 * (1 - smooth((age - le) / (double) (se - le)));
    }

    /** Orbit speed in radians per tick: easy while they play, rising as they tighten, fastest in the compression. */
    static double spin(double age) {
        int pl = BlueRedRules.PURPLE_PLAY, le = BlueRedRules.PURPLE_LIFT_END, se = BlueRedRules.PURPLE_SWIRL_END;
        return .09 + .36 * smooth((age - pl) / (double) (le - pl)) + .25 * smooth((age - le) / (double) (se - le));
    }

    /** Orbit angle: the integral of spin (half-tick steps, deterministic). */
    static double orbitAngle(double age) {
        double a = 0;
        for (double t = 0; t < age; t += .5) a += spin(t) * Math.min(.5, age - t);
        return a;
    }

    /** {blue, red} offsets from the pair's centre: an orbit on a precessing plane with unequal, breathing radii; the gap between them wanders while
     *  they play (they chase, nearly catch, fall back) and settles to opposite sides as they are drawn in. */
    static Vec3[] convergeOffsets(double age) {
        double th = orbitAngle(age), d = separation(age);
        double settle = smooth((age - BlueRedRules.PURPLE_PLAY) / (double) (BlueRedRules.PURPLE_LIFT_END - BlueRedRules.PURPLE_PLAY));
        Vec3 n = new Vec3(Math.sin(age * .031) * .45, 1, Math.cos(age * .023) * .45).normalize();
        Vec3[] uw = basis(n);
        double gap = Math.PI + .9 * Math.sin(age * .07) * (1 - settle);
        double rb = d * .45 * (1 + .15 * Math.sin(age * .05)), rr = d * .55 * (1 + .2 * Math.sin(age * .06 + 1));
        Vec3 blue = uw[0].scale(Math.cos(th) * rb).add(uw[1].scale(Math.sin(th) * rb * .85)).add(n.scale(Math.sin(age * .09) * d * .12));
        Vec3 red = uw[0].scale(Math.cos(th + gap) * rr).add(uw[1].scale(Math.sin(th + gap) * rr * .85)).add(n.scale(Math.sin(age * .08 + 2) * d * .15));
        return new Vec3[]{blue, red};
    }

    /** The convergence in the world, from the stored base (where Blue was): each orb true to itself, trailing its real past path (sampled from the same
     *  motion, so the trail is exactly where it has been), contact sparks, lightning thickening as they tighten, an imploding ring and a violet point
     *  in the compression; after it (bystanders only) that point grows into the Purple that takes over at MERGE. */
    static void converge(Ctx c, Vec3 base, double age, Vec3 eye, Vec3 look) {
        int ct = BlueRedRules.PURPLE_CONTACT, pl = BlueRedRules.PURPLE_PLAY, le = BlueRedRules.PURPLE_LIFT_END, se = BlueRedRules.PURPLE_SWIRL_END;
        Vec3 center = base.add(0, BlueRedRules.purpleRise(age), 0);
        double comp = smooth((age - le) / (double) (se - le));
        if (age >= se) {
            double born = smooth((age - se) / (double) (BlueRedRules.PURPLE_MERGE - se));
            if (c.body()) orb(c, center, .3 + 5.7 * born, 2, 1, VIOLET, 1);
            if (c.glow()) { glow(c, center, 2 + 10 * born, .7, VIOLET); stormBands(c, center, .3 + 5.7 * born, age, 10, born); }
            return;
        }
        Vec3[] o = convergeOffsets(age);
        Vec3 blue = center.add(o[0]), red = center.add(o[1]);
        if (age > ct) {
            double in = smooth((age - ct) / 8.0);
            trail(c, base, age, 0, c.body() ? 0x2A7CFF : CYAN, in);
            // The body ramp darkens pure red to wine, so Red's ring uses a lighter red to read as red like Blue's reads as blue.
            trail(c, base, age, 1, c.body() ? RED_BODY : RED, in);
        }
        double f0 = c.fade;
        c.fade = f0 * (1 - .7 * comp);
        blue(c, blue, true, age + 40, -1);
        redOrb(c, red, 1.4 * (1 - .4 * comp), age, red.subtract(blue));
        c.fade = f0;
        if (!c.glow()) return;
        double contact = 1 - clamp01((age - BlueRedRules.PURPLE_BLINK) / (double) (ct - BlueRedRules.PURPLE_BLINK));
        if (contact > 0) {
            fxSprite(c, center, 1 + 5 * (1 - contact), SHOCK, 3900, PALE, contact, 0);
            sparks(c, center, 1 + 3 * (1 - contact), age, 40, ORCHID, 3910);
        }
        double tight = smooth((age - pl) / (double) (le - pl));
        int bolts = (int) (1 + 4 * (1 - contact) + 10 * tight + 6 * comp);
        for (int j = 0; j < bolts; j++) {
            Vec3 from = red.add(nvec(3000 + j, age * .06).scale(.5)), to = blue.add(nvec(3100 + j, age * .06).scale(.5));
            liveBolt(c, from, to, 8, .3, .015 + .06 * tight + .05 * comp, j % 3 == 0 ? PALE : ORCHID, .95, 3200 + j * 29, age, .9);
        }
        if (tight > 0) streaks(c, center, .3, separation(age) + 4, age, (int) (50 * tight), ORCHID, 3400, 2, tight, true);
        if (comp > 0) {
            fxSprite(c, center, .5 + 7 * (1 - comp), SHOCK, 3950, ORCHID, comp, age * .1);
            orb(c, center, .2 + 1.2 * comp, 2, 1, VIOLET, comp);
            glow(c, center, 1 + 6 * comp, comp, 0xFFFFFF);
        }
    }

    /** One orb's trail (owner's ref 40): a long arc along its real past path, about two thirds of a turn whatever the speed, so the pair draws
     *  round red and blue rings as they chase. Thick and dark-coloured on the body layer, a thinner bright core on the glow layer; it narrows and
     *  fades towards the tail. Positions relative to the moving centre, so the rings stay round while the pair lifts. */
    private static void trail(Ctx c, Vec3 base, double age, int which, int color, double alpha) {
        double span = Math.min(40, 4.2 / spin(age));
        Vec3 now = base.add(0, BlueRedRules.purpleRise(age), 0);
        int n = 28;
        Vec3[] pts = new Vec3[n + 1];
        double[] widths = new double[n + 1];
        // Additive glow over the ribbon burns it white, so the glow is only a thin, faint core and the colour comes from the body.
        double w = c.body() ? .4 : .08;
        for (int j = 0; j <= n; j++) {
            double t = (double) j / n, at = Math.max(0, age - span * (1 - t));
            pts[j] = now.add(convergeOffsets(at)[which]);
            widths[j] = w * (.35 + .65 * t) * (1 + .15 * Math.sin(t * 9 - age * .4));
        }
        fxRibbon(c, pts, widths, c.body() ? RING : FLOW, 3500 + which, color, alpha * (c.body() ? .9 : .35));
    }

    /** Black over the world for Purple's two players: a quick fade in after the swirl, a fade out over the world Purple. */
    static double voidAlpha(double age) {
        int in = BlueRedRules.PURPLE_SWIRL_END, merge = BlueRedRules.PURPLE_MERGE, end = BlueRedRules.PURPLE_VOID_END;
        if (age < in || age >= end) return 0;
        if (age < merge) return smooth((age - in) / 6);
        return 1 - smooth((age - merge) / (end - merge));
    }

    /** The 2D act: against a drifting nebula the big Blue and the big Red, each true to itself, wheel round each other on interfering orbits while
     *  being pushed together; Purple forms over them, they sink into it trailing their energy into its centre, and it takes their place. */
    static void purpleVoid(Ctx c, Vec3 at, double age, Vec3 eye, Vec3 look) {
        double back = voidAlpha(age);
        if (back <= 0) return;
        int in = BlueRedRules.PURPLE_SWIRL_END, merge = BlueRedRules.PURPLE_MERGE;
        double v = age - in, span = merge - in;
        Vec3 view = new Vec3(eye.x - at.x, 0, eye.z - at.z);
        view = view.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : view.normalize();
        Vec3 axis = view.cross(new Vec3(0, 1, 0)).normalize(), swing = new Vec3(0, 1, 0).scale(.6).add(view.scale(.4)).normalize();
        double push = smooth(v / (span * .8)), form = smooth((v - span * .45) / (span * .35)), absorb = smooth((v - span * .6) / (span * .4));
        if (c.body()) {
            Vec3[] uw = basis(look);
            Vec3 o = eye.add(look.scale(.5));
            fxQuad(c, o.subtract(uw[0].scale(4)).subtract(uw[1].scale(4)), o.add(uw[0].scale(4)).subtract(uw[1].scale(4)), o.add(uw[0].scale(4)).add(uw[1].scale(4)),
                    o.subtract(uw[0].scale(4)).add(uw[1].scale(4)), NEBULA, 250, 0x2E2640, back, back, back, back, 0, 1);
            for (int i = 0; i < 18; i++) {
                double x = ((i * .37 + age * .012 * (1 + i % 3)) % 1) * 2 - 1, y = Math.sin(i * 2.1 + age * .03) * 2.5;
                // Red haze on Red's side (+axis is screen left), blue on Blue's, a violet smear between; dark, like smoke lit from inside.
                int color = x > .3 ? 0x3A0A1E : x < -.3 ? 0x101A5A : 0x2E0E5A;
                fxSprite(c, at.subtract(view.scale(3 + i % 4)).add(axis.scale(x * 14)).add(0, y, 0), 3 + (i % 3), SMOKE, 260 + i, color, .35 * back, i);
            }
        }
        double orbs = (1 - absorb) * (age < merge ? smooth((back - .55) / .45) : 1);
        // Red on the left, Blue on the right, gliding straight at each other: big textured spheres whose substance smears out behind them.
        double gapD = 5.6 * (1 - push) + .15 * (1 - absorb), r = 2.4 * (1 - .65 * absorb);
        // view x up points to screen left, so Red goes on +axis to sit on the left like the owner's frame.
        Vec3 red = at.add(axis.scale(gapD)), blue = at.subtract(axis.scale(gapD));
        if (orbs > .02) for (int side = 0; side < 2; side++) {
            Vec3 p = side == 0 ? red : blue, outward = side == 0 ? axis : axis.scale(-1);
            int tint = side == 0 ? RED : BLUE_TINT, smear = side == 0 ? RED_DEEP : ROYAL;
            if (c.body()) {
                for (int j = 0; j < 7; j++) {
                    double y = (hash(4000 + side * 10 + j) - .5) * r * 1.6, len = r * (1.5 + 2.5 * hash(4100 + side * 10 + j));
                    Vec3 from = p.add(0, y, 0).add(outward.scale(r * .3)), to = from.add(outward.scale(len));
                    fxBeam(c, from, to, r * (.25 + .2 * hash(4200 + j)), r * .05, FLOW, 4300 + side * 10 + j, smear, .55 * orbs);
                }
                orb(c, p, r, 3, side, tint, orbs);
            }
            if (c.glow()) glow(c, p, r * 1.8, .16 * orbs, tint);
        }
        if (c.glow() && push > .35) {
            double close = smooth((push - .35) / .65) * (1 - absorb);
            for (int j = 0; j < (int) (1 + 8 * close); j++) {
                Vec3 from = red.add(nvec(3000 + j, age * .06).scale(.6)), to = blue.add(nvec(3100 + j, age * .06).scale(.6));
                liveBolt(c, from, to, 8, .3, .02 + .06 * close, j % 3 == 0 ? PALE : ORCHID, .95 * back, 3200 + j * 29, age, .9);
            }
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
            sprite(c, at.add(axis.scale(Math.sin(i * 1.7) * 7)).add(0, y, 0).subtract(view.scale(1)), .2, .7 * back, ORCHID, BlueRedAtlas.BUBBLE);
        }
    }

    /** Red's impact frames (owner, 2026-11-16): black, red, black, then a fading red wash, with a burst of ink rays from the impact. */
    static int impactColor(int frame) { return frame % 2 == 0 ? INK : RED_BODY; }
    static double impactAlpha(int frame) { return frame < 3 ? .9 - .12 * frame : frame < 6 ? .35 * (6 - frame) / 3.0 : 0; }

    static void impact(Ctx c, Vec3 at, int frame, Vec3 eye, Vec3 look) { impact(c, at, frame, eye, look, false); }

    static void impact(Ctx c, Vec3 at, int frame, Vec3 eye, Vec3 look, boolean purple) {
        double a = impactAlpha(frame);
        if (a <= 0 || !c.body()) return;
        int light = purple ? PALE : RED_BODY;
        int back = frame < 3 ? (frame % 2 == 0 ? INK : light) : light, stroke = back == INK ? light : INK;
        sprite(c, eye.add(look.scale(.5)), 4, a, back, BlueRedAtlas.SOLID);
        double dist = Math.max(1, at.distanceTo(eye));
        fxSprite(c, at, dist * .9, RAYS, 320 + frame, stroke, a, frame * .7);
        Vec3[] uw = basis(look);
        for (int i = 0; i < 10; i++)
            fxArc(c, at, uw[0], uw[1], dist * (.12 + .05 * i), i * .63 + frame * .4, 1.4, dist * .012, FLOW, 330 + i, stroke, a);
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
    static void fxRibbon(Ctx c, Vec3[] pts, double[] widths, int effect, int seed, int color, double alpha) {
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
                fxQuad(c, pl, l, r, pr, effect, seed, color, alpha, alpha, alpha, alpha, (i - 1) / (double) n, i / (double) n);
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
