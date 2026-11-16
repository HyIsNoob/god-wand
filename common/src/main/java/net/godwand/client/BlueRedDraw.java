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
    static final int ORB = 0, FLOW = 1, FLAME = 2, SMOKE = 3, SHOCK = 4, BOLT = 5, SPARK = 6, RAYS = 7, NEBULA = 8, SPEEDLINES = 9, DEBRIS = 10;
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
        if (c.body()) {
            orb(c, at, r, 0, ready ? 1 : 0, BLUE_TINT, appear);
            infall(c, at, r, age, ready ? 26 : 18, 600, 0x2A3550, appear);
        }
        if (!c.glow()) return;
        glow(c, at, r * 3, (.22 + .12 * beat) * appear * flicker(age, 3), ROYAL);
        tendrils(c, at, r, age * (ready ? 1.5 : 1), ready ? 28 : 20, 640, appear);
        streaks(c, at, r * .9, r * 4.5, age, ready ? 48 : 32, CYAN, 620, 1.2, appear, true);
        sparks(c, at, r * 1.4, age, ready ? 24 : 14, ICE, 60);
        if (ready) for (int b = 0; b < 4; b++) {
            Vec3 d = nvec(660 + b, age * .03).normalize();
            Vec3 from = at.add(d.scale(r * .95));
            liveBolt(c, from, from.add(d.add(nvec(670 + b, age * .05).scale(.6)).normalize().scale(r * .9)), 5, .25, .025, CYAN, .9, 680 + b * 10, age, .9);
        }
        if (flash > 0) { glow(c, at, r * 6, flash, 0xFFFFFF); glow(c, at, r * 2.5, flash, 0xFFFFFF); }
    }

    /** Blue's energy lines: each winds round the core on its own drifting plane, with its own length, thickness, brightness and pace;
     *  its radius wanders, it breaks into pieces, and about a third of them trail far out. */
    static void tendrils(Ctx c, Vec3 at, double r, double age, int count, long seed, double alpha) {
        for (int i = 0; i < count; i++) {
            long sd = seed + i * 97L;
            Vec3 axis = new Vec3(.25, 1, .1).add(nvec(sd, age * .006 + i).scale(.9)).normalize();
            Vec3[] uw = basis(axis);
            double dirSign = hash(sd + 1) < .55 ? 1 : -1, speed = (.035 + .06 * hash(sd + 2)) * dirSign, sweep = 1.4 + 3 * hash(sd + 3);
            double width = r * (.025 + .09 * Math.pow(hash(sd + 4), 2)), bright = .45 + .55 * hash(sd + 5);
            boolean reach = hash(sd + 6) < .33;
            double phase = hash(sd + 7) * Math.PI * 2 + age * speed;
            int n = 26;
            Vec3[] pts = new Vec3[n + 1];
            double[] widths = new double[n + 1];
            for (int k = 0; k <= n; k++) {
                double s = (double) k / n, a = phase + s * sweep * dirSign;
                double rad = r * (1.02 + .3 * snoise(sd + 8, s * 2.2 + age * .05) + (reach ? s * s * 2.2 : 0));
                Vec3 off = axis.scale(r * .35 * snoise(sd + 9, s * 2.7 + age * .06));
                pts[k] = at.add(uw[0].scale(Math.cos(a) * rad)).add(uw[1].scale(Math.sin(a) * rad)).add(off);
                double gap = smooth((vnoise(sd + 10, s * 3.5 + age * .08) - .3) / .15);
                widths[k] = width * Math.pow(Math.sin(Math.PI * s), .6) * gap * (reach ? 1 - .6 * s : 1);
            }
            int color = i % 4 == 0 ? ICE : i % 4 == 1 ? 0x6FB4FF : CYAN;
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
            Vec3 dir = new Vec3(hash(cs) * 2 - 1, (hash(cs + 1) * 2 - 1) * .7, hash(cs + 2) * 2 - 1).normalize();
            double far = r * (2.6 + 2.6 * hash(cs + 3)), near = r * .55, swirl = (hash(cs + 4) < .5 ? 1 : -1) * (1.5 + 2 * hash(cs + 5));
            double ease = inward ? p * p * p : 1 - Math.pow(1 - p, 3);
            double rad = inward ? far - (far - near) * ease : near + (far * 1.4 - near) * ease;
            double turn = swirl * ease;
            Vec3 d = new Vec3(dir.x * Math.cos(turn) - dir.z * Math.sin(turn), dir.y, dir.x * Math.sin(turn) + dir.z * Math.cos(turn));
            Vec3 pos = at.add(d.scale(rad));
            double size = (.07 + .26 * hash(cs + 6)) * (inward ? 1 - .7 * ease : 1 - .4 * ease);
            double a = alpha * smooth(p / .12) * smooth((1 - p) / .08);
            if (pos.distanceTo(c.eye) < 1.5 || a <= 0) continue;
            fxSprite(c, pos, size, DEBRIS, (int) (cs % 991), color, a, age * (hash(cs + 7) - .5) * .8 + i);
        }
    }

    // ---- Red: violent, unstable, repelling ----

    /** Red's orb: the red shader orb, sharp brush strokes of red and ink torn off and flung outward, ink fragments thrown, sparks sucked in. */
    static void redOrb(Ctx c, Vec3 at, double size, double age) {
        at = at.add(jitter(age, size * .1, 23));
        if (c.body()) {
            orb(c, at, size, 1, 0, RED, 1);
            fall(c, at, size * 1.2, age * 1.3, 10, 700, INK, .9, false);
        }
        slashes(c, at, size, age, 16, 730, 1);
        if (!c.glow()) return;
        glow(c, at, size * 2.4, .3 * flicker(age, 7), RED);
        streaks(c, at, size * .9, size * 5, age, 26, RED_LIGHT, 720, 1.6, 1, true);
        sparks(c, at, size * 2.4, age * 1.6, 18, RED_LIGHT, 100);
    }

    /** Long, sharp, curved strokes, each launched from the core on its own bent path and thrown out fast then slowing: red ones in the alpha pass
     *  with a hot spine in the glow pass, dark ink ones, smoke puffing off their tails, some broken. Asymmetric by construction. */
    static void slashes(Ctx c, Vec3 at, double size, double age, int count, long seed, double alpha) {
        for (int i = 0; i < count; i++) {
            long sd = seed + i * 173L;
            double life = 12 + 14 * hash(sd), ph = age / life + hash(sd + 1);
            long cyc = (long) Math.floor(ph);
            double p = ph - cyc;
            long cs = sd + cyc * 104729L;
            Vec3 normal = new Vec3(hash(cs) * 2 - 1, hash(cs + 1) * 2 - 1, hash(cs + 2) * 2 - 1).normalize();
            Vec3[] uw = basis(normal);
            double a0 = hash(cs + 3) * Math.PI * 2, sweep = (.7 + 1.9 * hash(cs + 4)) * (hash(cs + 5) < .5 ? 1 : -1);
            double r0 = size * (.85 + .4 * hash(cs + 6)), r1 = size * (2.8 + 4.5 * hash(cs + 7)), bend = (hash(cs + 8) - .5) * size * 1.6;
            double head = 1 - Math.pow(1 - p, 2.2), tail = Math.max(0, head - (.35 + .25 * hash(cs + 9)));
            double env = smooth(p / .1) * smooth((1 - p) / .3) * alpha;
            int kind = (int) (hash(cs + 10) * 3);
            boolean ink = kind == 2, broken = hash(cs + 11) < .35;
            if (env <= 0 || (ink ? !c.body() : !(c.body() || c.glow()))) continue;
            int n = 18;
            Vec3[] pts = new Vec3[n + 1];
            double[] widths = new double[n + 1];
            double width = size * (.09 + .28 * hash(cs + 12));
            for (int k = 0; k <= n; k++) {
                double s = (double) k / n, u = tail + (head - tail) * s, a = a0 + sweep * u;
                double rad = r0 + (r1 - r0) * Math.pow(u, 1.25);
                pts[k] = at.add(uw[0].scale(Math.cos(a) * rad)).add(uw[1].scale(Math.sin(a) * rad)).add(normal.scale(bend * Math.sin(Math.PI * u)));
                double cutout = broken ? smooth((vnoise(cs + 13, s * 4) - .35) / .1) : 1;
                widths[k] = width * Math.pow(s, .45) * (1 - .35 * s * s) * cutout;
            }
            if (c.body()) fxRibbon(c, pts, widths, FLOW, (int) (cs % 977), ink ? INK : kind == 0 ? RED_BODY : RED_DEEP, env);
            if (!ink && c.glow()) {
                double[] spine = new double[n + 1];
                for (int k = 0; k <= n; k++) spine[k] = widths[k] * .22;
                fxRibbon(c, pts, spine, BOLT, (int) (cs % 977) + 1, kind == 0 ? RED_CORE : RED_LIGHT, env * .7);
            }
            if (c.body() && p > .2) fxSprite(c, pts[0], size * (.4 + .6 * p), SMOKE, (int) (cs % 953), ink ? INK : RED_DEEP, .5 * env * (1 - p), age * .05 + i);
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
        if (flying) redOrb(c, head, .5, age);
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
        if (!blue) slashes(c, at, 1.2 + age * .15, age + 20, 10, 790, fade);
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
            pair(c, at, age, smooth(age / merge), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 1.4, 1, 1);
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

    /** The two orbs drawn as themselves (Blue pulling, Red repelling) on asymmetric, interfering orbits round a common centre that tighten with k
     *  (0 far apart, 1 merged). Lightning and energy stretched toward the centre thicken as they close. a/b: the plane of the orbit. */
    static void pair(Ctx c, Vec3 at, double age, double k, Vec3 a, Vec3 b, double rr, double alpha, double orbs) {
        double d = 3.4 * (1 - k) + .15;
        double ta = age * (.09 + .2 * k), tb = age * (.07 + .26 * k) + 2.4;
        Vec3 blueOff = a.scale(Math.cos(ta) * d * (1 + .2 * Math.sin(age * .05))).add(b.scale(Math.sin(ta) * d * .8)).add(0, Math.sin(age * .09) * d * .3, 0);
        Vec3 redOff = a.scale(Math.cos(tb) * d * (1.1 + .25 * Math.sin(age * .04 + 1))).add(b.scale(Math.sin(tb) * d * .7)).add(0, Math.sin(age * .07 + 2) * d * .35, 0);
        Vec3 blue = at.add(blueOff), red = at.subtract(redOff);
        if (orbs > .02) {
            // They sink into Purple: faded as a whole (c.fade scales every vertex they emit), never removed in one frame.
            double f0 = c.fade;
            c.fade = f0 * orbs;
            blue(c, blue, true, age + 40, -1);
            redOrb(c, red, rr, age);
            c.fade = f0;
        }
        if (!c.glow()) return;
        double close = k * k;
        int bolts = (int) (2 + 12 * close);
        for (int j = 0; j < bolts; j++) {
            Vec3 from = red.add(nvec(3000 + j, age * .06).scale(rr * .4)), to = blue.add(nvec(3100 + j, age * .06).scale(rr * .4));
            liveBolt(c, from, to, 8, .3, .015 + .08 * close, j % 3 == 0 ? PALE : ORCHID, .95 * alpha, 3200 + j * 29, age, .9);
        }
        // Energy pulled from both towards the centre, longer as they close.
        if (k > .3) {
            streaks(c, at, .3, d * 1.2 + rr, age, (int) (40 * k), ORCHID, 3400, 1.8, k * alpha, true);
            glow(c, at, 1 + 5 * k, .5 * close * alpha, VIOLET);
        }
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
                    o.subtract(uw[0].scale(4)).add(uw[1].scale(4)), NEBULA, 250, 0xFFFFFF, back, back, back, back, 0, 1);
            for (int i = 0; i < 18; i++) {
                double x = ((i * .37 + age * .012 * (1 + i % 3)) % 1) * 2 - 1, y = Math.sin(i * 2.1 + age * .03) * 2.5;
                int color = x < -.3 ? 0x5A1030 : x > .3 ? 0x182A90 : 0x4A1888;
                fxSprite(c, at.subtract(view.scale(3 + i % 4)).add(axis.scale(x * 14)).add(0, y, 0), 3 + (i % 3), SMOKE, 260 + i, color, .55 * back, i);
            }
        }
        double orbs = (1 - absorb) * (age < merge ? smooth((back - .55) / .45) : 1);
        // The orbs keep closing through the absorption (k past the meeting point is clamped by pair's minimum gap), shrinking as they sink in.
        pair(c, at, age, Math.min(1, push * .85 + absorb * .15), axis, swing, 1.9 * (1 - .65 * absorb), back, orbs);
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
