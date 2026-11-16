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
    static final int ORB = 0, FLOW = 1, FLAME = 2, SMOKE = 3, SHOCK = 4, BOLT = 5, SPARK = 6, RAYS = 7, NEBULA = 8;

    private BlueRedDraw() {}

    // ---- Blue ----

    static void blue(Ctx c, Vec3 at, boolean ready, double age, double sinceReady) {
        double r = BlueRedVisual.blueRadius(ready ? BlueRedRules.BlueState.READY : BlueRedRules.BlueState.CHARGING, age);
        double appear = smooth(age / 6), beat = ready ? BlueRedVisual.heartbeat(age) : 0, flash = BlueRedVisual.flash(sinceReady);
        double speed = ready ? 1.8 : 1;
        if (c.body()) {
            // Living blue fire: churning plasma, its corona licking upward (shader ORB, blue fire).
            fxSprite(c, at, r * 1.6 * (1.15 + .1 * beat), ORB, 0, ROYAL, appear, 0);
            // Tongues of deep blue flame rising and curling round it.
            for (int i = 0; i < (ready ? 20 : 16); i++) {
                double life = 20 + (i % 5) * 4, phase = (age * speed / life + i * .618034) % 1, grow = Math.sin(Math.PI * phase);
                double a = i * 2.39996 + age * .04 * speed, tilt = Math.sin(i * 1.7) * .7;
                Vec3 out = new Vec3(Math.cos(a) * Math.cos(tilt), Math.sin(tilt), Math.sin(a) * Math.cos(tilt));
                Vec3 dir = out.scale(.55).add(0, .8, 0).add(new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(.45)).normalize();
                fxTongue(c, at.add(out.scale(r * .8)), dir, r * (1.1 + 1.3 * grow), r * .65, FLAME, i, i % 2 == 0 ? DEEP_BLUE : ROYAL, .95 * grow * appear);
            }
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI * 2 / 10 + age * .09 * speed;
                fxSprite(c, at.add(Math.cos(a) * r * 1.5, -r * .5 + Math.sin(age * .07 + i) * .25, Math.sin(a) * r * 1.5), r * .7, SMOKE, 20 + i, SMOKE_WHITE, .45 * appear, a);
            }
        }
        if (!c.glow()) return;
        // The vortex: white-cyan energy ribbons streaming round it (refs 13, 14), inner ones crossing its face.
        for (int i = 0; i < (ready ? 12 : 8); i++) {
            double tilt = .2 + .45 * Math.sin(i * 1.9), spin = age * (.3 + .05 * (i % 5)) * speed * (i % 3 == 0 ? -1 : 1) + i * 1.7;
            Vec3[] uw = basis(new Vec3(Math.sin(i * 2.3) * tilt, 1, Math.cos(i * 2.3) * tilt).normalize());
            double rad = r * (i % 4 == 0 ? .75 : 1.2 + .25 * (i % 3));
            fxArc(c, at.add(0, (i % 3 - 1) * r * .2, 0), uw[0], uw[1], rad, spin, 2.4, r * (.18 + .08 * (i % 2)), FLOW, 40 + i, i % 3 == 0 ? ICE : CYAN, (i % 3 == 0 ? .45 : .65) * appear);
        }
        glow(c, at, r * 3.4, (.25 + .15 * beat) * appear, ROYAL);
        glow(c, at, r * .7, (.5 + .2 * beat) * appear, ICE);
        sparks(c, at, r * 1.3, age, ready ? 44 : 30, ICE, 60);
        if (ready) {
            for (int k = 0; k < 2; k++) {
                double spin = age * (.07 + .03 * k) + k * 2.1;
                orbit(c, at, new Vec3(Math.cos(spin), .6 * Math.sin(spin * .7 + k), Math.sin(spin)).normalize(), r * (1.25 + .15 * k), .05, ICE, .7 * appear);
            }
            Random rand = new Random((long) Math.floor(age / 3) * 7919L);
            for (int b = 0; b < 6; b++) {
                Vec3 d = gauss(rand).normalize();
                bolt(c, rand, at.add(d.scale(r * .9)), d, r * (.6 + .6 * rand.nextDouble()), 5, .3, .07, CYAN, .95, 1);
            }
        }
        if (flash > 0) { glow(c, at, r * 7, flash, 0xFFFFFF); glow(c, at, r * 3, flash, 0xFFFFFF); }
    }

    // ---- Red ----

    /** Red's orb: churning red plasma, red and ink energy ribbons whirling round it, sparks sucked in. */
    static void redOrb(Ctx c, Vec3 at, double size, double age) {
        double reach = size * 2.8;
        if (c.body()) {
            fxSprite(c, at, size * 1.6, ORB, 1, RED_BODY, 1, 0);
            for (int i = 0; i < 8; i++) {
                double spin = age * (.3 + .05 * i) * (i % 2 == 0 ? 1 : -1) + i * 1.05;
                Vec3[] uw = basis(new Vec3(Math.sin(i * 1.9 + age * .02), Math.cos(i * 1.3), Math.sin(i * .7 + 1)).normalize());
                double radius = reach * (.75 + .3 * Math.sin(i * 2.3 + age * .1));
                boolean dark = i % 3 == 2;
                fxArc(c, at, uw[0], uw[1], radius * (dark ? 1.1 : 1), spin + (dark ? 1.4 : 0), dark ? 1.7 : 2.1, size * (dark ? .6 : .5), FLOW, 70 + i, dark ? INK : RED_BODY, .95);
            }
            for (int i = 0; i < 8; i++) {
                double a = i * 2.39996 + age * .07;
                Vec3 p = at.add(Math.cos(a) * reach * 1.1, Math.sin(a * 1.4) * reach * .6, Math.sin(a) * reach * 1.1);
                if (p.distanceTo(c.eye) > 1.2) fxSprite(c, p, size * .35, SMOKE, 80 + i, INK, .9, a);
            }
        }
        if (!c.glow()) return;
        for (int i = 0; i < 4; i++) {
            Vec3[] uw = basis(new Vec3(Math.sin(i * 1.9 + age * .02), Math.cos(i * 1.3), Math.sin(i * .7 + 1)).normalize());
            fxArc(c, at, uw[0], uw[1], reach * .8, age * .4 * (i % 2 == 0 ? 1 : -1) + i * 1.6, 2.2, size * .2, FLOW, 90 + i, RED_LIGHT, .35);
        }
        glow(c, at, size * 1.2, .5, RED);
        glow(c, at, size * .4, .95, RED_CORE);
        sparks(c, at, reach, age * 1.6, 36, RED_LIGHT, 100);
    }

    /** Red in flight: the orb at the head, energy ribbons spiralling round the path behind it (two red, one ink), a hot core streak, sparks. */
    static void redFlight(Ctx c, Vec3 origin, Vec3 dir, Vec3 head, double age, double fade, long seed, boolean flying) {
        if (fade <= 0) return;
        double tailLength = Math.min(head.distanceTo(origin), 24);
        Vec3[] uw = basis(dir);
        for (int h = 0; h < 4; h++) {
            boolean dark = h == 3;
            if (h == 2 ? !c.glow() : !c.body()) continue;
            int n = 26;
            Vec3[] pts = new Vec3[n + 1];
            double[] widths = new double[n + 1];
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n, back = (1 - t) * tailLength, turn = h * 1.5708 - age * .9 + back * .5;
                double radius = (.2 + .9 * (1 - t)) * (dark ? 1.3 : 1);
                pts[i] = head.subtract(dir.scale(back)).add(uw[0].scale(Math.cos(turn) * radius)).add(uw[1].scale(Math.sin(turn) * radius));
                widths[i] = .12 + .3 * t;
            }
            fxRibbon(c, pts, widths, FLOW, 120 + h, dark ? INK : h == 2 ? RED_LIGHT : RED_BODY, fade);
        }
        if (c.glow()) fxBeam(c, head.subtract(dir.scale(Math.min(tailLength, 10))), head, .06, .4, BOLT, 130, RED_LIGHT, fade);
        if (flying) redOrb(c, head, .55, age);
        if (!c.glow()) return;
        Random rand = new Random(seed);
        for (int i = 0; i < 34; i++) {
            double t = rand.nextDouble(), drift = Math.max(0, age - t * 6) * .07;
            Vec3 p = head.subtract(dir.scale(t * tailLength)).add(gauss(rand).scale(drift + .2));
            if (p.distanceTo(c.eye) > 1.2) fxSprite(c, p, .12 + .1 * rand.nextDouble(), SPARK, 140 + i, i % 4 == 0 ? RED_CORE : RED, .9 * fade * (1 - t * .6), 0);
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
        if (!c.glow()) return;
        int color = blue ? CYAN : RED_LIGHT;
        fxSprite(c, at, 2 + age * 1.3, SHOCK, 171, blue ? ICE : RED_LIGHT, fade, 0);
        fxSprite(c, at, 3 + age * 1.6, RAYS, 172, color, fade, age * .05);
        glow(c, at, 1 + age * .35, fade, blue ? ICE : RED_CORE);
        for (int i = 0; i < 6; i++) {
            Vec3[] uw = basis(new Vec3(Math.sin(i * 1.3), Math.cos(i * 2.1), Math.sin(i * .7 + 1)).normalize());
            fxArc(c, at, uw[0], uw[1], 1.5 + age * 1.3, i * 1.05 + age * .2, 1.8, .7 * fade, FLOW, 180 + i, color, fade);
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
            if (c.body()) {
                fxSprite(c, at, s * 1.6 * 1.2, ORB, 2, VIOLET, 1, 0);
                fxSprite(c, at, s * 1.6 * .62, ORB, 6, ORCHID, .9, 1.3);
            }
            if (c.glow()) {
                stormRibbons(c, at, s, age, 26, 1);
                glow(c, at, s * 3, .4, VIOLET);
                glow(c, at, s * .45, .55, 0xFFFFFF);
                sparks(c, at, s * 1.2, age, 90, PALE, 200);
                for (int b = 0; b < 8; b++) {
                    Vec3 d = gauss(rand).normalize();
                    bolt(c, rand, at.add(d.scale(s * .98)), d.cross(gauss(rand)).normalize(), s * .9, 6, .35, .1 + .05 * grow, ORCHID, .95, 1);
                }
                for (int b = 0; b < 4 + (int) (6 * grow); b++) {
                    Vec3 d = gauss(rand).normalize();
                    bolt(c, rand, at.add(d.scale(s)), d, s * (1 + rand.nextDouble()), 9, .45, .16, ORCHID, 1, 2);
                }
            }
            return;
        }
        // Detonation: the storm sphere bursting out, a shockwave on the ground, rays, ribbons flung outward, lightning hurled to the horizon.
        double dome = 12 + 42 * Math.sqrt(blast);
        if (c.body()) fxSprite(c, at, dome * 1.6, ORB, 2, VIOLET, (1 - blast * .8) * fade, 0);
        if (c.glow()) {
            fxFlat(c, at.add(0, -1, 0), dome * 1.8, SHOCK, 210, PALE, (1 - blast) * fade);
            fxSprite(c, at, dome * 2.4, RAYS, 211, PALE, (1 - blast * .7) * fade, age * .01);
            fxSprite(c, at, dome * 1.3, SHOCK, 212, ORCHID, (1 - blast) * fade, 0);
            stormRibbons(c, at, dome * .9, age, 20, fade);
            glow(c, at, dome * .7, (1 - blast * .6) * fade, 0xFFFFFF);
            sparks(c, at, dome, age, 80, PALE, 220);
            Random fixed = new Random(424242L + (long) Math.floor(age / 3));
            for (int b = 0; b < 22; b++) {
                double a = b * Math.PI * 2 / 22 + fixed.nextGaussian() * .12, up = .05 + .55 * fixed.nextDouble();
                Vec3 d = new Vec3(Math.cos(a), up, Math.sin(a)).normalize();
                double reach = (25 + 90 * smooth(blast)) * (.6 + .4 * fixed.nextDouble());
                bolt(c, fixed, at.add(d.scale(dome * .6)), d, reach, 18, .3, .6, ORCHID, fade, 2);
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
            double rad = s * (i % 3 == 0 ? .45 + .2 * Math.sin(i) : .85 + .35 * ((i * .37) % 1));
            double spin = age * (.16 + .05 * (i % 4)) * (i % 2 == 0 ? 1 : -1) + i * 2.1;
            int color = i % 5 == 0 ? PALE : i % 2 == 0 ? ORCHID : VIOLET;
            fxArc(c, at, uw[0], uw[1], rad, spin, 2 + (i % 3) * .5, s * (.07 + .04 * (i % 3)), FLOW, 230 + i, color, (i % 5 == 0 ? .5 : .75) * alpha);
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
        double recoil = smooth(age / 14), approach = smooth((age - 14) / 44.0), meet = smooth((age - (end - 14)) / 14.0);
        double d = 5.2 * recoil * (1 - approach) + .2, r = 2;
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
        Vec3 red = at.subtract(axis.scale(d)), blue = at.add(axis.scale(d));
        double orbs = (1 - meet) * clamp01(back * 1.5);
        if (orbs > .02) {
            if (c.body()) for (int j = 0; j < 8; j++) {
                double f = .6 * (1 - j / 8.0) * orbs;
                fxSprite(c, red.subtract(axis.scale(j * 1.1 + 1)), 2 - j * .15, SMOKE, 280 + j, RED_BODY, f, j);
                fxSprite(c, blue.add(axis.scale(j * 1.1 + 1)), 2.2 - j * .15, SMOKE, 290 + j, ROYAL, f, -j);
            }
            redOrb(c, red, r, age);
            blue(c, blue, true, age + 40, -1);
        }
        Random rand = new Random((long) Math.floor(age / 2) * 7717L);
        if (c.glow()) {
            if (approach > .3) {
                int bolts = (int) (2 + 9 * approach);
                for (int b = 0; b < bolts; b++)
                    bolt(c, rand, red.add(axis.scale(r)), axis.add(gauss(rand).scale(.25)).normalize(), Math.max(.6, d * 2 - r * 2 + .4), 7, .4, .08, ORCHID, .95 * back, 1);
            }
            for (int i = 0; i < 14; i++) {
                double y = ((i * .29 + age * .006) % 1) * 6 - 3;
                sprite(c, at.add(axis.scale(Math.sin(i * 1.7) * 7)).add(0, y, 0).subtract(view.scale(1)), .2, .7 * back, ORCHID, BlueRedAtlas.BUBBLE);
            }
            if (meet > 0) {
                fxSprite(c, at, (1.5 + 4.5 * meet) * 1.6, ORB, 2, ORCHID, meet, 0);
                stormRibbons(c, at, 2 + 5 * meet, age, 18, meet);
                fxSprite(c, at, 6 + 18 * meet, RAYS, 300, PALE, meet, age * .02);
                glow(c, at, 1 + 6 * meet, meet, 0xFFFFFF);
                for (int b = 0; b < 12; b++) {
                    Vec3 dir = gauss(rand).normalize();
                    bolt(c, rand, at.add(dir.scale(1 + 3 * meet)), dir, 3 + 9 * meet, 8, .4, .12, ORCHID, meet, 1);
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

    static void impact(Ctx c, Vec3 at, int frame, Vec3 eye, Vec3 look) {
        double a = impactAlpha(frame);
        if (a <= 0 || !c.body()) return;
        int back = frame < 3 ? impactColor(frame) : RED_BODY, stroke = back == INK ? RED_BODY : INK;
        sprite(c, eye.add(look.scale(.5)), 4, a, back, BlueRedAtlas.SOLID);
        double dist = Math.max(1, at.distanceTo(eye));
        fxSprite(c, at, dist * .9, RAYS, 320 + frame, stroke, a, frame * .7);
        Vec3[] uw = basis(look);
        for (int i = 0; i < 10; i++)
            fxArc(c, at, uw[0], uw[1], dist * (.12 + .05 * i), i * .63 + frame * .4, 1.4, dist * .05, FLOW, 330 + i, stroke, a);
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
            fxSprite(c, p, .12 + .16 * rand.nextDouble(), SPARK, seed + i, i % 3 == 0 ? 0xFFFFFF : color, .95, i);
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
