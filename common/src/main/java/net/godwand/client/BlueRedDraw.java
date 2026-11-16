package net.godwand.client;

import net.godwand.skill.BlueRedRules;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

import static net.godwand.client.GateDraw.*;

/**
 * Geometry of Blue, Red and Purple (owner references in docs/ideas/refs-1.6.0). Most of the look is computed per pixel by the godwand_bluered shader
 * (see BlueRedShader): fx* quads carry an effect id in their UV, so orbs churn, ribbons stream and flames rise every frame instead of being pasted
 * pictures. Saturated colours (deep blue, pure red, ink) go in the alpha pass so they hold in daylight; light goes in the additive pass, and bloom
 * spreads what is near white.
 */
final class BlueRedDraw {
    static final int DEEP_BLUE = 0x0C24E8, ROYAL = 0x1E46FF, INDIGO = 0x0A12B4, CYAN = 0x46D2FF, ICE = 0xEAF8FF, SMOKE_WHITE = 0xE4EEFA;
    static final int RED = 0xFF0A28, RED_BODY = 0xD4001A, RED_DEEP = 0x8A0010, RED_LIGHT = 0xFF4A60, RED_CORE = 0xFFD6DC, RED_DARK = 0x16000A;
    static final int VIOLET = 0x9A2CFF, ORCHID = 0xF064FF, PALE = 0xF6D8FF, NIGHT = 0x2A0650, VOID = 0x07020F, INK = 0x050002;
    /** Shader effects (godwand_bluered.fsh). ORB seeds: % 4 == 0 blue fire, 1 red plasma, 2 purple storm. */
    static final int ORB = 0, FLOW = 1, FLAME = 2, SMOKE = 3, SHOCK = 4, BOLT = 5, SPARK = 6, RAYS = 7, NEBULA = 8, SPEEDLINES = 9, DEBRIS = 10;

    private BlueRedDraw() {}

    // ---- Blue ----

    static void blue(Ctx c, Vec3 at, boolean ready, double age, double sinceReady) {
        double r = BlueRedVisual.blueRadius(ready ? BlueRedRules.BlueState.READY : BlueRedRules.BlueState.CHARGING, age);
        double appear = smooth(age / 6), beat = ready ? BlueRedVisual.heartbeat(age) : 0, flash = BlueRedVisual.flash(sinceReady);
        at = at.add(jitter(age, ready ? .06 : .03, 11));
        // The sphere itself is the shader ORB (blue energy: bright folds, crackled rim, lens flare); level 1 once ready.
        if (c.body()) {
            orb(c, at, r, 0, ready ? 1 : 0, 0x1A6CFF, appear);
            debris(c, at, r * 1.3, age, ready ? 22 : 14, 0x3A4660, 600, appear, true);
        }
        if (!c.glow()) return;
        glow(c, at, r * 3, (.22 + .12 * beat) * appear * flicker(age, 3), ROYAL);
        streaks(c, at, r * .9, r * 4.5, age, ready ? 64 : 40, CYAN, 620, 1.2, appear, true);
        streaks(c, at, r * .9, r * 3, age, 16, ICE, 690, 1.7, appear, true);
        sparks(c, at, r * 1.4, age, ready ? 26 : 16, ICE, 60);
        if (ready) {
            Random rand = new Random((long) Math.floor(age / 3) * 7919L);
            for (int b = 0; b < 4; b++) {
                Vec3 d = gauss(rand).normalize();
                bolt(c, rand, at.add(d.scale(r * .95)), d, r * (.5 + .5 * rand.nextDouble()), 5, .3, .025, CYAN, .9, 1);
            }
        }
        if (flash > 0) { glow(c, at, r * 6, flash, 0xFFFFFF); glow(c, at, r * 2.5, flash, 0xFFFFFF); }
    }

    // ---- Red ----

    /** Red's orb: churning red plasma, red and ink energy ribbons whirling round it, sparks sucked in. */
    static void redOrb(Ctx c, Vec3 at, double size, double age) {
        // The shader ORB (red: dark interior, flakes spiralling into a white core, a thin rim, small flames licking it).
        at = at.add(jitter(age, size * .08, 23));
        if (c.body()) {
            orb(c, at, size, 1, 0, RED, 1);
            debris(c, at, size * 2, age * 1.4, 10, INK, 700, 1, true);
            vortex(c, at, size * .9, size * 2.9, age * 1.2, 5, INK, 740, .7);
        }
        if (!c.glow()) return;
        glow(c, at, size * 2.4, .3 * flicker(age, 7), RED);
        streaks(c, at, size * .9, size * 6, age, 48, RED_LIGHT, 720, 1.6, 1, true);
        vortex(c, at, size * .9, size * 2.6, age, 6, RED_LIGHT, 730, .55);
        sparks(c, at, size * 2.4, age * 1.6, 18, RED_LIGHT, 100);
    }

    /** Red in flight: the orb at the head, energy ribbons spiralling round the path behind it (two red, one ink), a hot core streak, sparks. */
    static void redFlight(Ctx c, Vec3 origin, Vec3 dir, Vec3 head, double age, double fade, long seed, boolean flying) {
        if (fade <= 0) return;
        double tailLength = Math.min(head.distanceTo(origin), 24);
        Vec3[] uw = basis(dir);
        // Two thin spiralling threads of red light, tight at the head and loosening behind it, and a fine hot streak.
        for (int h = 0; h < 2 && c.glow(); h++) {
            int n = 30;
            Vec3[] pts = new Vec3[n + 1];
            double[] widths = new double[n + 1];
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n, back = (1 - t) * tailLength, turn = h * Math.PI - age * .9 + back * .6;
                double radius = .15 + .7 * (1 - t);
                pts[i] = head.subtract(dir.scale(back)).add(uw[0].scale(Math.cos(turn) * radius)).add(uw[1].scale(Math.sin(turn) * radius));
                widths[i] = .025 + .045 * t;
            }
            fxRibbon(c, pts, widths, BOLT, 120 + h, RED_LIGHT, fade);
        }
        if (c.glow()) {
            fxBeam(c, head.subtract(dir.scale(Math.min(tailLength, 12))), head, .02, .18, BOLT, 130, RED, fade);
            // Sonic rings left behind it, widening as they fall back.
            for (int k = 1; k <= 6; k++) {
                double back = k * 3.5;
                if (back > tailLength) break;
                double since = back / BlueRedRules.RED_SPEED;
                orbit(c, head.subtract(dir.scale(back)), dir, .35 + since * .22, .05, RED_LIGHT, .75 * fade * (1 - k / 7.0));
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
        }
        if (c.body()) debris(c, at, 1 + age * .9, age, 16, blue ? 0x22305A : INK, blue ? 740 : 760, fade, false);
        if (!c.glow()) return;
        int color = blue ? CYAN : RED_LIGHT;
        streaks(c, at, 1, 3 + age * 1.6, age, 40, color, blue ? 780 : 800, 2.2, fade, false);
        fxSprite(c, at, 2 + age * 1.3, SHOCK, 171, blue ? ICE : RED_LIGHT, fade, 0);
        fxSprite(c, at, 3 + age * 1.6, RAYS, 172, color, fade, age * .05);
        glow(c, at, 1 + age * .35, fade, blue ? ICE : RED_CORE);
        for (int i = 0; i < 6; i++) {
            Vec3[] uw = basis(new Vec3(Math.sin(i * 1.3), Math.cos(i * 2.1), Math.sin(i * .7 + 1)).normalize());
            fxArc(c, at, uw[0], uw[1], 1.5 + age * 1.3, i * 1.05 + age * .2, 1.8, .06, BOLT, 180 + i, color, fade);
        }
    }

    // ---- Purple ----

    /** Purple in the world: grows from the merged orbs into a huge sphere of violet storm, dense with streaming ribbons and lightning, then detonates. */
    static void purple(Ctx c, Vec3 at, double age, Vec3 eye, Vec3 look) {
        int detonate = BlueRedRules.PURPLE_DETONATE, end = BlueRedRules.PURPLE_CINEMATIC, merge = BlueRedRules.PURPLE_MERGE;
        double blast = clamp01((age - detonate) / 18.0);
        double fade = 1 - clamp01((age - detonate - 18) / (end - detonate - 18.0));
        if (fade <= 0) return;
        double tint = smooth((age - 10) / 30) * fade * clamp01(1 - eye.distanceTo(at) / 120);
        if (c.body() && tint > 0) sprite(c, eye.add(look.scale(1)), 6, .4 * tint, NIGHT, BlueRedAtlas.SOLID);
        double rush = smooth((age - (detonate - 30)) / 30.0) * (1 - clamp01((age - detonate - 10) / 30.0)) * clamp01(1 - eye.distanceTo(at) / 90);
        speedLines(c, eye, look, .85 * rush, PALE);
        if (age < merge) {
            // What bystanders see: the two orbs spiralling into one.
            double a = age * .35, d = 3.2 * (1 - smooth(age / merge));
            Vec3 off = new Vec3(Math.cos(a) * d, Math.sin(a * .5) * d * .3, Math.sin(a) * d);
            blue(c, at.add(off), true, age + 40, -1);
            redOrb(c, at.subtract(off), 1.4, age);
            return;
        }
        Random rand = new Random((long) Math.floor(age / 2) * 104729L);
        if (age < detonate) {
            double grow = smooth((age - merge) / (double) (detonate - merge)), s = 2.5 + 9.5 * grow;
            double tremble = .05 + .35 * grow * grow;
            at = at.add(jitter(age, tremble, 37));
            if (c.body()) {
                fxSprite(c, at, s * 1.9, SHOCK, 205, PALE, .18 + .1 * grow, 0);
                orb(c, at, s, 2, grow > .6 ? 1 : 0, VIOLET, 1);
                debris(c, at, s * 1.4, age, 34, 0x1A0A26, 820, 1, true);
            }
            if (c.glow()) {
                // A disc of shockwaves spun off the equator, a new one every 9 ticks.
                for (int k = 0; k < 3; k++) {
                    double ring = ((age + k * 3) % 9) / 9.0;
                    fxFlat(c, at, s * (1.1 + 1.6 * ring), SHOCK, 840 + k, ORCHID, .55 * (1 - ring));
                }
                streaks(c, at, s * .95, s * 4, age, 110, ORCHID, 860, 1.1, 1, true);
                streaks(c, at, s * .95, s * 3, age, 40, PALE, 990, 1.6, 1, true);
                stormRibbons(c, at, s, age, 14, .8);
                glow(c, at, s * 2.6, .3, VIOLET);
                sparks(c, at, s * 1.5, age, 60, PALE, 200);
                for (int b = 0; b < 3 + (int) (5 * grow); b++) {
                    Vec3 d = gauss(rand).normalize();
                    bolt(c, rand, at.add(d.scale(s)), d, s * (.8 + rand.nextDouble()), 9, .45, .05, ORCHID, 1, 2);
                }
            }
            return;
        }
        // Detonation: the storm sphere bursting out, a shockwave on the ground, rays, ribbons flung outward, lightning hurled to the horizon.
        double dome = 12 + 42 * Math.sqrt(blast);
        if (c.body()) {
            orb(c, at, dome * .6, 2, 2, VIOLET, (1 - blast * .85) * fade);
            debris(c, at, dome * (.5 + blast), age, 40, 0x1A0A26, 1240, fade, false);
        }
        if (c.glow()) {
            fxFlat(c, at.add(0, -1, 0), dome * 1.8, SHOCK, 210, PALE, (1 - blast) * fade);
            fxSprite(c, at, dome * 2.4, RAYS, 211, PALE, (1 - blast * .7) * fade, age * .01);
            fxSprite(c, at, dome * 1.3, SHOCK, 212, ORCHID, (1 - blast) * fade, 0);
            stormRibbons(c, at, dome * .7, age, 12, fade);
            streaks(c, at, dome * .4, dome * 2.2, age, 140, PALE, 1100, 2.6, fade, false);
            glow(c, at, dome * .7, (1 - blast * .6) * fade, 0xFFFFFF);
            sparks(c, at, dome, age, 80, PALE, 220);
            Random fixed = new Random(424242L + (long) Math.floor(age / 3));
            for (int b = 0; b < 22; b++) {
                double a = b * Math.PI * 2 / 22 + fixed.nextGaussian() * .12, up = .05 + .55 * fixed.nextDouble();
                Vec3 d = new Vec3(Math.cos(a), up, Math.sin(a)).normalize();
                double reach = (25 + 90 * smooth(blast)) * (.6 + .4 * fixed.nextDouble());
                bolt(c, fixed, at.add(d.scale(dome * .6)), d, reach, 18, .3, .2, ORCHID, fade, 2);
            }
        }
        double white = 1 - clamp01((age - detonate) / 4.0);
        if (white > 0 && c.body()) sprite(c, eye.add(look.scale(.8)), 6, .85 * white * white, 0xFFFFFF, BlueRedAtlas.SOLID);
    }

    /** Streaming violet, orchid and white ribbons on many orbits, inside and around a sphere of radius s: the storm swirling in Purple. */
    static void stormRibbons(Ctx c, Vec3 at, double s, double age, int count, double alpha) {
        for (int i = 0; i < count; i++) {
            double drift = age * (.008 + .004 * (i % 3));
            Vec3 n = new Vec3(Math.sin(i * 1.91 + drift), Math.cos(i * 2.37 - drift), Math.sin(i * .73 + 1 + drift)).normalize();
            Vec3[] uw = basis(n);
            double rad = s * (1 + .18 * ((i * .37) % 1));
            double spin = age * (.16 + .05 * (i % 4)) * (i % 2 == 0 ? 1 : -1) + i * 2.1;
            int color = i % 5 == 0 ? PALE : i % 2 == 0 ? ORCHID : VIOLET;
            fxArc(c, at, uw[0], uw[1], rad * 1.05, spin, 2 + (i % 3) * .5, s * (.012 + .008 * (i % 3)), BOLT, 230 + i, color, (i % 5 == 0 ? .6 : .8) * alpha);
        }
    }

    /** Black over the world for Purple's two players: fades in as Red strikes Blue, holds, fades out into the world after the merge. */
    static double voidAlpha(double age) {
        int end = BlueRedRules.PURPLE_VOID_END;
        if (age < end) return smooth(age / 14);
        return 1 - smooth((age - end) / 12);
    }

    /** Purple's first act: struck, the two orbs (the same size) recoil apart against a drifting nebula, hang facing each other as smoke streams past,
     *  lightning leaps between them, they rush together and merge in a white burst. */
    static void purpleVoid(Ctx c, Vec3 at, double age, Vec3 eye, Vec3 look) {
        int end = BlueRedRules.PURPLE_VOID_END;
        double back = voidAlpha(age);
        if (back <= 0) return;
        Vec3 view = new Vec3(eye.x - at.x, 0, eye.z - at.z);
        view = view.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : view.normalize();
        Vec3 axis = view.cross(new Vec3(0, 1, 0)).normalize();
        double recoil = smooth(age / 10), approach = smooth((age - 8) / (end - 16.0)), meet = smooth((age - (end - 10)) / 10.0);
        double d = 5.4 * recoil * (1 - approach) + .15, r = 2;
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
        // The pair circles its centre ever faster on a tightening spiral, tilted towards the camera so the orbit reads.
        double close = 1 - d / 5.4, theta = age * (.06 + .22 * close * close) + age * age * .0025;
        Vec3 up = new Vec3(0, 1, 0), swing = up.scale(.55).add(view.scale(.45)).normalize();
        Vec3 off = axis.scale(Math.cos(theta) * d).add(swing.scale(Math.sin(theta) * d * .6));
        Vec3 red = at.subtract(off), blue = at.add(off);
        double orbs = (1 - meet) * clamp01(back * 1.5);
        if (orbs > .02) {
            if (c.body()) for (int j = 0; j < 8; j++) {
                // Each drags a smoke trail round its orbit.
                double lag = theta - j * .18;
                Vec3 o = axis.scale(Math.cos(lag) * d).add(swing.scale(Math.sin(lag) * d * .6));
                double f = .55 * (1 - j / 8.0) * orbs;
                fxSprite(c, at.subtract(o), 1.9 - j * .14, SMOKE, 280 + j, RED_BODY, f, j);
                fxSprite(c, at.add(o), 2.1 - j * .14, SMOKE, 290 + j, ROYAL, f, -j);
            }
            redOrb(c, red, r, age);
            blue(c, blue, true, age + 40, -1);
        }
        Random rand = new Random((long) Math.floor(age / 2) * 7717L);
        if (c.glow()) {
            // Purple lightning between them: more and thicker the closer they are, a violet glow building where it meets.
            if (approach > .05) {
                int bolts = (int) (1 + 16 * close * close);
                Vec3 gap = blue.subtract(red);
                double span = gap.length();
                for (int k = 0; k < bolts && span > .2; k++) {
                    Vec3 from = red.add(gauss(rand).scale(r * .4)), to = blue.add(gauss(rand).scale(r * .4));
                    Vec3 dirB = to.subtract(from);
                    bolt(c, rand, from, dirB.normalize(), dirB.length(), 8, .35, .015 + .09 * close * close, k % 3 == 0 ? PALE : ORCHID, .95 * back, 1);
                }
                fxSprite(c, red.add(gap.scale(.5)), (.5 + 3 * close) * 1.2, SHOCK, 295, ORCHID, close * .8 * back, theta);
                glow(c, red.add(gap.scale(.5)), 1 + 6 * close, close * close * back, VIOLET);
            }
            for (int i = 0; i < 14; i++) {
                double y = ((i * .29 + age * .006) % 1) * 6 - 3;
                sprite(c, at.add(axis.scale(Math.sin(i * 1.7) * 7)).add(0, y, 0).subtract(view.scale(1)), .2, .7 * back, ORCHID, BlueRedAtlas.BUBBLE);
            }
            if (meet > 0) {
                orb(c, at, 1.5 + 4.5 * meet, 2, 1, VIOLET, meet);
                stormRibbons(c, at, 1.5 + 4.5 * meet, age, 12, meet);
                streaks(c, at, 1.5, 9, age, 70, PALE, 1400, 2.4, meet, false);
                fxSprite(c, at, 6 + 18 * meet, RAYS, 300, PALE, meet, age * .02);
                glow(c, at, 1 + 6 * meet, meet, 0xFFFFFF);
                for (int bb = 0; bb < 12; bb++) {
                    Vec3 dir = gauss(rand).normalize();
                    bolt(c, rand, at.add(dir.scale(1 + 3 * meet)), dir, 3 + 9 * meet, 8, .4, .04, ORCHID, meet, 1);
                }
                sparks(c, at, 2 + 6 * meet, age, 50, PALE, 310);
            }
        }
        // The burst whites the screen out at the meeting; the white then thins with the black, uncovering the world.
        double white = age < end ? smooth((age - (end - 3)) / 3.0) : 1 - smooth((age - end) / 5.0);
        if (white > 0 && c.body()) sprite(c, eye.add(look.scale(.45)), 4, white, 0xFFFFFF, BlueRedAtlas.SOLID);
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

    /** A shader quad lying flat on the ground (shockwaves). */
    static void fxFlat(Ctx c, Vec3 at, double half, int effect, int seed, int color, double alpha) {
        if (alpha <= 0) return;
        fxQuad(c, at.add(-half, 0, -half), at.add(half, 0, -half), at.add(half, 0, half), at.add(-half, 0, half), effect, seed, color, alpha, alpha, alpha, alpha, 0, 1);
    }

    /** A flame from base along dir, camera-facing sideways: the FLAME effect's base (local y 1) at base, tip (y 0) at the far end. */
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

    /** A camera-facing shader ribbon through points (tail first), local x running 0..1 along it. */
    static void fxRibbon(Ctx c, Vec3[] pts, double[] widths, int effect, int seed, int color, double alpha) {
        int n = pts.length - 1;
        Vec3 pl = null, pr = null;
        for (int i = 0; i <= n; i++) {
            Vec3 tangent = pts[Math.min(n, i + 1)].subtract(pts[Math.max(0, i - 1)]);
            Vec3 side = tangent.cross(pts[i].subtract(c.eye));
            if (side.lengthSqr() < 1e-10) continue;
            side = side.normalize().scale(widths[i]);
            Vec3 l = pts[i].subtract(side), r = pts[i].add(side);
            if (pl != null) fxQuad(c, pl, l, r, pr, effect, seed, color, alpha, alpha, alpha, alpha, (i - 1) / (double) n, i / (double) n);
            pl = l; pr = r;
        }
    }

    /** A ribbon along an arc of the circle (u, w) round center, camera-facing. */
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

    /** Thin streaks of light rushing in to the core (inward) or flung out from it, each on its own curling path and pace. */
    static void streaks(Ctx c, Vec3 at, double rIn, double rOut, double age, int count, int color, int seed, double speed, double alpha, boolean inward) {
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

    /** Jagged shards tumbling round the core on chaotic orbits (inward: held in orbit, swallowed and replaced; outward: flung away). */
    static void debris(Ctx c, Vec3 at, double r, double age, int count, int color, int seed, double alpha, boolean orbiting) {
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

    /** Vortex bands: spiral ribbons wound in from rOut to the core, each on its own tilted plane, turning and streaming inward. */
    static void vortex(Ctx c, Vec3 at, double rIn, double rOut, double age, int count, int color, int seed, double alpha) {
        Random rand = new Random(seed);
        for (int b = 0; b < count; b++) {
            Vec3[] uw = basis(new Vec3(rand.nextGaussian() * .5, 1, rand.nextGaussian() * .5).normalize());
            double spin = age * (.18 + .1 * rand.nextDouble()) + rand.nextDouble() * 7, turns = 1.2 + rand.nextDouble() * .9;
            double width = rIn * (.035 + .05 * rand.nextDouble());
            int n = 48;
            Vec3[] pts = new Vec3[n + 1];
            double[] widths = new double[n + 1];
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n, rad = rOut * Math.pow(rIn / rOut, t), a = spin + t * turns * Math.PI * 2;
                pts[i] = at.add(uw[0].scale(Math.cos(a) * rad)).add(uw[1].scale(Math.sin(a) * rad));
                widths[i] = width * (.4 + .6 * Math.sin(Math.PI * Math.min(1, t * 1.2)));
            }
            fxRibbon(c, pts, widths, FLOW, seed + b, color, alpha);
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

    /** A jagged lightning bolt on the shader's flickering BOLT strip; it may fork (depth). Fades out right against the eye. */
    static void bolt(Ctx c, Random rand, Vec3 from, Vec3 dir, double length, int segments, double jitter, double width, int color, double alpha, int depth) {
        Vec3 p = from, d = dir;
        double step = length / segments;
        int seed = 400 + rand.nextInt(200);
        for (int s = 0; s < segments; s++) {
            Vec3 q = p.add(d.scale(step)).add(gauss(rand).scale(jitter * step));
            double k = alpha * (1 - .5 * s / segments) * clamp01((Math.min(p.distanceTo(c.eye), q.distanceTo(c.eye)) - 2) / 3);
            fxBeam(c, p, q, width * 4, width * 4, BOLT, seed + s, color, k);
            if (depth > 0 && rand.nextDouble() < .3)
                bolt(c, rand, q, d.add(gauss(rand).scale(.8)).normalize(), length * .4, Math.max(2, segments / 2), jitter, width * .6, color, alpha * .8, depth - 1);
            d = d.add(gauss(rand).scale(.2)).normalize();
            p = q;
        }
    }

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
