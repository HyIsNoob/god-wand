package net.godwand.client;

import net.godwand.skill.BlueRedRules;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

import static net.godwand.client.GateDraw.*;

/**
 * Geometry of Blue, Red and Purple (owner references in docs/ideas/refs-1.6.0). Everything samples BlueRedAtlas.
 * Saturated colours (deep blue flames, pure red, ink) go in the alpha pass so they hold their colour in daylight; light goes in the additive pass.
 * Orbs are real 3D spheres with a scrolling plasma surface and a fresnel rim, so they read as glowing glass balls rather than flat discs.
 */
final class BlueRedDraw {
    static final int DEEP_BLUE = 0x0C24E8, ROYAL = 0x1E46FF, INDIGO = 0x0A12B4, CYAN = 0x46D2FF, ICE = 0xEAF8FF, SMOKE_WHITE = 0xE4EEFA;
    /** Red is laid down in the alpha pass (RED_BODY): additive red over a bright sky turns pink. Only thin hot cores are additive. */
    static final int RED = 0xFF0A28, RED_BODY = 0xD4001A, RED_DEEP = 0x8A0010, RED_LIGHT = 0xFF4A60, RED_CORE = 0xFFD6DC, RED_DARK = 0x16000A;
    static final int VIOLET = 0x9A2CFF, ORCHID = 0xF064FF, PALE = 0xF6D8FF, NIGHT = 0x2A0650, VOID = 0x07020F, INK = 0x050002;

    private BlueRedDraw() {}

    // ---- Blue ----

    static void blue(Ctx c, Vec3 at, boolean ready, double age, double sinceReady) {
        double r = BlueRedVisual.blueRadius(ready ? BlueRedRules.BlueState.READY : BlueRedRules.BlueState.CHARGING, age);
        double appear = smooth(age / 6), beat = ready ? BlueRedVisual.heartbeat(age) : 0, flash = BlueRedVisual.flash(sinceReady);
        double speed = ready ? 1.8 : 1;
        // The orb itself: a deep blue glass ball of churning plasma with a bright cyan rim.
        if (c.body()) sphere(c, at, r * .92, DEEP_BLUE, .45 * appear, .95 * appear, age * .006 * speed, true);
        if (c.glow()) sphere(c, at, r * .95, CYAN, .12 * appear, (.85 + .15 * beat) * appear, -age * .009 * speed, false);
        // Flame tongues licking up and round it, saturated blue, a lighter cyan heart in the glow pass.
        int tongues = ready ? 30 : 22;
        for (int i = 0; i < tongues; i++) {
            double life = 18 + (i % 5) * 3, phase = (age * speed / life + i * .618034) % 1;
            double a = i * 2.39996 + age * .04 * speed, tilt = Math.sin(i * 1.7) * .8;
            Vec3 out = new Vec3(Math.cos(a) * Math.cos(tilt), Math.sin(tilt), Math.sin(a) * Math.cos(tilt));
            Vec3 swirl = new Vec3(-Math.sin(a), 0, Math.cos(a));
            Vec3 dir = out.scale(.6).add(0, .55, 0).add(swirl.scale(.9)).normalize();
            double grow = Math.sin(Math.PI * phase), len = r * (.45 + .75 * grow) * (ready ? 1.15 : 1);
            Vec3 base = at.add(out.scale(r * .9));
            GateAtlas.Region tex = BlueRedAtlas.FLAME[i % 4];
            int deep = i % 3 == 0 ? DEEP_BLUE : i % 3 == 1 ? ROYAL : INDIGO;
            if (c.body()) {
                tongue(c, base, dir, len, r * .5 * (.6 + .4 * grow), tex, deep, .85 * grow * appear);
                // A billow of blue fire riding each tongue, so the mass reads as flame rather than spikes.
                sprite(c, base.add(dir.scale(len * (.3 + .5 * phase))), r * .8 * (.5 + .5 * grow), .8 * grow * appear, deep, BlueRedAtlas.SMOKE[i % 4], a + phase * 3);
            }
            if (c.glow()) tongue(c, base, dir, len * .8, r * .25, tex, CYAN, .3 * grow * appear);
        }
        // White slashes whirling round it like a vortex (refs 13, 14), and white smoke churning at its waist.
        for (int i = 0; i < (ready ? 8 : 5); i++) {
            double tilt = .25 + .35 * Math.sin(i * 1.9), spin = age * (.32 + .04 * i) * speed + i * 1.7;
            Vec3[] uw = basis(new Vec3(Math.sin(i * 2.3) * tilt, 1, Math.cos(i * 2.3) * tilt).normalize());
            Vec3 centre = at.add(0, (i % 3 - 1) * r * .25, 0);
            double rad = r * (1.3 + .22 * (i % 4));
            if (c.body()) arc(c, centre, uw[0], uw[1], rad, spin, 2.1, r * .22, 0xF4FAFF, .8 * appear, BlueRedAtlas.SLASH[i % 2]);
            if (c.glow()) arc(c, centre, uw[0], uw[1], rad, spin, 2.1, r * .1, CYAN, .8 * appear, BlueRedAtlas.SLASH[i % 2]);
        }
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12 + age * .09 * speed, wobble = Math.sin(age * .07 + i) * .25;
            if (c.body()) sprite(c, at.add(Math.cos(a) * r * 1.5, -r * .45 + wobble, Math.sin(a) * r * 1.5), r * .55, .3 * appear, SMOKE_WHITE, BlueRedAtlas.SMOKE[i % 4], a * 2);
        }
        if (!c.glow()) return;
        glow(c, at, r * 3.6, (.28 + .15 * beat) * appear, ROYAL);
        glow(c, at, r * 1.9, (.4 + .2 * beat) * appear, CYAN);
        glow(c, at, r * (ready ? 1.05 : .85), appear, ICE);
        sparkles(c, at, r * 1.15, age, ready ? 40 : 26, ICE, CYAN, 7);
        motes(c, at, r, age, CYAN, 30);
        if (ready) {
            for (int k = 0; k < 2; k++) {
                double spin = age * (.07 + .03 * k) + k * 2.1;
                orbit(c, at, new Vec3(Math.cos(spin), .6 * Math.sin(spin * .7 + k), Math.sin(spin)).normalize(), r * (1.25 + .15 * k), .05, ICE, .7 * appear);
            }
            Random rand = new Random((long) Math.floor(age / 3) * 7919L);
            for (int b = 0; b < 5; b++) {
                Vec3 d = gauss(rand).normalize();
                bolt(c, rand, at.add(d.scale(r * .9)), d, r * (.6 + .6 * rand.nextDouble()), 5, .3, .06, CYAN, .95, 1);
            }
        }
        if (flash > 0) { glow(c, at, r * 7, flash, 0xFFFFFF); glow(c, at, r * 3, flash, 0xFFFFFF); }
    }

    // ---- Red ----

    /** Red's orb: a small sphere of pure red plasma, bright red and black ink strokes whirling round it, glowing and dark orbs, particles sucked in. */
    static void redOrb(Ctx c, Vec3 at, double size, double age) {
        double reach = size * 3.2;
        if (c.body()) sphere(c, at, size, RED_BODY, .7, 1, age * .02, true);
        if (c.glow()) sphere(c, at, size * 1.02, RED_LIGHT, .15, .8, -age * .03, false);
        for (int i = 0; i < 8; i++) {
            double spin = age * (.28 + .05 * i) * (i % 2 == 0 ? 1 : -1) + i * 1.05;
            Vec3[] uw = basis(new Vec3(Math.sin(i * 1.9 + age * .02), Math.cos(i * 1.3), Math.sin(i * .7 + 1)).normalize());
            double radius = reach * (.75 + .3 * Math.sin(i * 2.3 + age * .1));
            boolean dark = i % 3 == 2;
            GateAtlas.Region ink = BlueRedAtlas.INK[i % 4];
            if (c.body()) arc(c, at, uw[0], uw[1], radius * (dark ? 1.1 : 1), spin + (dark ? 1.4 : 0), dark ? 1.6 : 1.9, size * .55, dark ? RED_DARK : RED_BODY, .95, ink);
            if (!dark && c.glow()) arc(c, at, uw[0], uw[1], radius, spin, 1.9, size * .2, RED_CORE, .45, ink);
        }
        Random rand = new Random(31);
        for (int i = 0; i < 16; i++) {
            double a = i * 2.39996 + age * (.06 + .02 * (i % 3)), rr = reach * (.8 + .9 * rand.nextDouble()), y = (rand.nextDouble() - .5) * reach * 1.4;
            Vec3 p = at.add(Math.cos(a) * rr, y, Math.sin(a) * rr);
            if (p.distanceTo(c.eye) < 1.2) continue;
            boolean dark = i % 3 == 0;
            if (c.body()) sprite(c, p, size * (dark ? .2 : .24), .95, dark ? RED_DARK : RED_BODY, BlueRedAtlas.GLOW);
            if (!dark && c.glow()) glow(c, p, size * .12, .9, RED_CORE);
        }
        if (c.body()) {
            sprite(c, at, size * 4.5, .2, RED_DEEP, BlueRedAtlas.GLOW);
            motes(c, at, reach * .7, age * 2.4, RED_BODY, 50);
        }
        if (!c.glow()) return;
        glow(c, at, size * 1.3, .55, RED);
        glow(c, at, size * .45, .95, RED_CORE);
        for (int i = 0; i < 10; i++) {
            double a = i * 2.1 + age * .3;
            spark(c, at.add(new Vec3(Math.cos(a), Math.sin(a * 1.3), Math.sin(a)).scale(reach * .9)), size * .25, .8, RED_LIGHT, a);
        }
    }

    /** Red in flight: the orb at the head, spiralling red and ink ribbons along the path behind it, sparks. */
    static void redFlight(Ctx c, Vec3 origin, Vec3 dir, Vec3 head, double age, double fade, long seed, boolean flying) {
        if (fade <= 0) return;
        double travelled = head.distanceTo(origin), tailLength = Math.min(travelled, 22);
        Vec3[] uw = basis(dir);
        if (c.body()) beam(c, head.subtract(dir.scale(tailLength)), head, .04, .6, RED_BODY, .1 * fade, .95 * fade);
        if (c.glow()) beam(c, head.subtract(dir.scale(Math.min(travelled, 9))), head, .03, .25, RED_CORE, .15 * fade, .85 * fade);
        // Three helices twist round the path, tight near the head and loosening behind it: two red, one ink.
        for (int h = 0; h < 3; h++) {
            if (!c.body()) continue;
            boolean dark = h == 2;
            Vec3 prev = null;
            int n = 28;
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n, back = t * tailLength, turn = h * 2.0944 - age * .9 + back * .55;
                double radius = (.25 + .9 * t) * (dark ? 1.25 : 1);
                Vec3 p = head.subtract(dir.scale(back)).add(uw[0].scale(Math.cos(turn) * radius)).add(uw[1].scale(Math.sin(turn) * radius));
                if (prev != null) {
                    double a = (1 - t) * fade, w = .18 * (1 - t) + .03;
                    beam(c, prev, p, w, w, dark ? RED_DARK : h == 0 ? RED_BODY : RED_DEEP, a, a);
                }
                prev = p;
            }
        }
        if (flying) redOrb(c, head, .5, age);
        if (!c.glow()) return;
        Random rand = new Random(seed);
        for (int i = 0; i < 30; i++) {
            double t = rand.nextDouble(), drift = Math.max(0, age - t * 6) * .07;
            Vec3 p = head.subtract(dir.scale(t * tailLength)).add(gauss(rand).scale(drift + .2));
            if (p.distanceTo(c.eye) > 1.2) spark(c, p, .08 + .07 * rand.nextDouble(), .9 * fade * (1 - t * .6), i % 4 == 0 ? RED_CORE : RED, rand.nextDouble() * 6);
        }
    }

    // ---- bursts ----

    static void burst(Ctx c, Vec3 at, double age, boolean blue) {
        double k = clamp01(age / (blue ? 12 : 16)), fade = 1 - k;
        if (fade <= 0) return;
        Random rand = new Random(blue ? 91 : 97);
        if (c.body()) {
            // Blue's flames, or Red's ink, flung outward.
            for (int i = 0; i < 18; i++) {
                Vec3 d = gauss(rand).normalize();
                Vec3 p = at.add(d.scale(1 + age * (.6 + .5 * rand.nextDouble())));
                if (blue) sprite(c, p, 1 + age * .08, .8 * fade, i % 2 == 0 ? DEEP_BLUE : ROYAL, BlueRedAtlas.SMOKE[i % 4], i + age * .2);
                else sprite(c, p, .5 + rand.nextDouble() * .7, .95 * fade, INK, BlueRedAtlas.SMOKE[i % 4], i);
            }
            if (!blue) flat(c, at.add(0, -.9, 0), 2 + age * .9, BlueRedAtlas.SHOCK, RED_BODY, .7 * fade);
        }
        if (!c.glow()) return;
        int color = blue ? CYAN : RED;
        glow(c, at, 2 + age * (blue ? .8 : 1), fade, color);
        glow(c, at, 1 + age * .35, fade, blue ? ICE : RED_CORE);
        sprite(c, at, 2 + age * 1.2, fade, blue ? ICE : RED_LIGHT, BlueRedAtlas.SHOCK, age * .05);
        if (!blue) {
            sprite(c, at, 3 + age * 1.5, .8 * fade, RED_LIGHT, BlueRedAtlas.RAYS, age * .03);
            for (int i = 0; i < 6; i++) {
                Vec3[] uw = basis(new Vec3(Math.sin(i * 1.3), Math.cos(i * 2.1), Math.sin(i * .7 + 1)).normalize());
                arc(c, at, uw[0], uw[1], 1.5 + age * 1.3, i * 1.05 + age * .2, 1.6, .6 * fade, RED, fade, BlueRedAtlas.INK[i % 4]);
            }
        }
    }

    // ---- Purple ----

    /** Purple in the world: grows from the merged orbs into a huge sphere of violet plasma crawling with lightning, then detonates. */
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
            if (c.body()) sphere(c, at, s, VIOLET, .55, .95, age * .008, true);
            if (c.glow()) {
                sphere(c, at, s * 1.01, ORCHID, .15, .9, -age * .012, false);
                glow(c, at, s * 3, .45, VIOLET);
                glow(c, at, s * 1.4, .6, ORCHID);
                glow(c, at, s * .7, 1, 0xFFFFFF);
                sparkles(c, at, s, age, 90, PALE, ORCHID, 11);
                for (int b = 0; b < 9; b++) {
                    Vec3 d = gauss(rand).normalize();
                    bolt(c, rand, at.add(d.scale(s * .98)), d.cross(gauss(rand)).normalize(), s * .9, 6, .35, .08 + .04 * grow, ORCHID, .95, 1);
                }
                for (int b = 0; b < 4 + (int) (6 * grow); b++) {
                    Vec3 d = gauss(rand).normalize();
                    bolt(c, rand, at.add(d.scale(s)), d, s * (1 + rand.nextDouble()), 9, .45, .14, ORCHID, 1, 2);
                }
            }
            return;
        }
        // Detonation: a white dome swelling out, a shockwave on the ground, rays, lightning hurled to the horizon.
        double dome = 12 + 42 * Math.sqrt(blast);
        if (c.body()) flat(c, at.add(0, -1, 0), dome * 1.6, BlueRedAtlas.SHOCK, PALE, .6 * (1 - blast));
        if (c.glow()) {
            sphere(c, at, dome, PALE, .25 * (1 - blast * .7) * fade, .95 * fade, age * .01, false);
            glow(c, at, dome * 2, .5 * fade, VIOLET);
            glow(c, at, dome * 1.1, .9 * fade, ORCHID);
            glow(c, at, dome * .7, (1 - blast * .6) * fade, 0xFFFFFF);
            sprite(c, at, dome * 2.2, (1 - blast) * fade, PALE, BlueRedAtlas.RAYS, age * .01);
            sparkles(c, at, dome * .8, age, 70, PALE, ORCHID, 13);
            Random fixed = new Random(424242L + (long) Math.floor(age / 3));
            for (int b = 0; b < 22; b++) {
                double a = b * Math.PI * 2 / 22 + fixed.nextGaussian() * .12, up = .05 + .55 * fixed.nextDouble();
                Vec3 d = new Vec3(Math.cos(a), up, Math.sin(a)).normalize();
                double reach = (25 + 90 * smooth(blast)) * (.6 + .4 * fixed.nextDouble());
                bolt(c, fixed, at.add(d.scale(dome * .6)), d, reach, 18, .3, .6, ORCHID, fade, 2);
            }
        }
        double white = 1 - clamp01((age - detonate) / 6.0);
        if (white > 0 && c.body()) sprite(c, eye.add(look.scale(.8)), 6, .92 * white, 0xFFFFFF, BlueRedAtlas.SOLID);
        if (white > 0 && c.glow()) sprite(c, eye.add(look.scale(.8)), 6, .6 * white, PALE, BlueRedAtlas.SOLID);
    }

    /** Black over the world for Purple's two players: fades in as Red strikes Blue, holds, fades out into the world after the merge. */
    static double voidAlpha(double age) {
        int end = BlueRedRules.PURPLE_VOID_END;
        if (age < end) return smooth(age / 14);
        return 1 - smooth((age - end) / 12);
    }

    /** Purple's first act: struck, the two orbs (the same size) recoil apart against black, hang facing each other as purple smoke streams past,
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
            sprite(c, eye.add(look.scale(.5)), 4, back, VOID, BlueRedAtlas.SOLID);
            for (int i = 0; i < 22; i++) {
                double x = ((i * .37 + age * .012 * (1 + i % 3)) % 1) * 2 - 1, y = Math.sin(i * 2.1 + age * .03) * 2.5;
                int color = x < -.3 ? 0x2E0818 : x > .3 ? 0x0C1650 : 0x320C5C;
                sprite(c, at.subtract(view.scale(3 + i % 4)).add(axis.scale(x * 14)).add(0, y, 0), 3 + (i % 3), .6 * back, color, BlueRedAtlas.SMOKE[i % 4], i + age * .01);
            }
        }
        Vec3 red = at.subtract(axis.scale(d)), blue = at.add(axis.scale(d));
        double orbs = (1 - meet) * clamp01(back * 1.5);
        if (orbs > .02) {
            if (c.body()) for (int j = 0; j < 8; j++) {
                double f = .55 * (1 - j / 8.0) * orbs;
                sprite(c, red.subtract(axis.scale(j * 1.1 + .8)), 2 - j * .15, f, RED_DEEP, BlueRedAtlas.SMOKE[j % 4], j + age * .05);
                sprite(c, blue.add(axis.scale(j * 1.1 + .8)), 2.2 - j * .15, f, INDIGO, BlueRedAtlas.SMOKE[j % 4], -j - age * .05);
            }
            redOrb(c, red, r, age);
            blue(c, blue, true, age + 40, -1);
        }
        Random rand = new Random((long) Math.floor(age / 2) * 7717L);
        if (c.glow()) {
            if (approach > .3) {
                int bolts = (int) (2 + 9 * approach);
                for (int b = 0; b < bolts; b++)
                    bolt(c, rand, red.add(axis.scale(r)), axis.add(gauss(rand).scale(.25)).normalize(), Math.max(.6, d * 2 - r * 2 + .4), 7, .4, .07, ORCHID, .95 * back, 1);
            }
            for (int i = 0; i < 14; i++) {
                double y = ((i * .29 + age * .006) % 1) * 6 - 3;
                sprite(c, at.add(axis.scale(Math.sin(i * 1.7) * 7)).add(0, y, 0).subtract(view.scale(1)), .2, .7 * back, ORCHID, BlueRedAtlas.BUBBLE);
            }
            if (meet > 0) {
                sphere(c, at, 1.5 + 4 * meet, ORCHID, .2 * meet, meet, age * .02, false);
                glow(c, at, 4 + 22 * meet, .6 * meet, VIOLET);
                glow(c, at, 2 + 12 * meet, meet, ORCHID);
                glow(c, at, 1 + 6 * meet, meet, 0xFFFFFF);
                sprite(c, at, 6 + 16 * meet, meet, PALE, BlueRedAtlas.RAYS, age * .02);
                for (int b = 0; b < 12; b++) {
                    Vec3 dir = gauss(rand).normalize();
                    bolt(c, rand, at.add(dir.scale(1 + 3 * meet)), dir, 3 + 9 * meet, 8, .4, .12, ORCHID, meet, 1);
                }
                sparkles(c, at, 2 + 6 * meet, age, 50, PALE, ORCHID, 17);
            }
        }
        // The burst whites the screen out at the meeting; the white then thins with the black, uncovering the world.
        double white = age < end ? smooth((age - (end - 5)) / 5.0) : 1 - smooth((age - end) / 10.0);
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
        sprite(c, at, dist * .9, a, stroke, BlueRedAtlas.RAYS, frame * .7);
        Vec3[] uw = basis(look);
        for (int i = 0; i < 10; i++)
            arc(c, at, uw[0], uw[1], dist * (.12 + .05 * i), i * .63 + frame * .4, 1.3, dist * .05, stroke, a, BlueRedAtlas.INK[i % 4]);
        if (frame < 3) sprite(c, at, dist * .12, a, stroke, BlueRedAtlas.GLOW);
    }

    // ---- shapes ----

    static void glow(Ctx c, Vec3 at, double size, double alpha, int color) { sprite(c, at, size, alpha, color, BlueRedAtlas.GLOW, 0); }
    static void spark(Ctx c, Vec3 at, double size, double alpha, int color, double roll) { sprite(c, at, size, alpha, color, BlueRedAtlas.STAR, roll); }

    /** A sphere of plasma: latitude/longitude quads over the SURFACE tile scrolling round it, alpha rising from core to rim (fresnel). frontOnly keeps
     *  the alpha pass from darkening it twice. */
    static void sphere(Ctx c, Vec3 at, double radius, int color, double core, double rim, double scroll, boolean frontOnly) {
        int lat = 10, lon = 20;
        GateAtlas.Region tex = BlueRedAtlas.SURFACE;
        double s = scroll - Math.floor(scroll);
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                Vec3 n00 = unit(t0, p0), n01 = unit(t0, p1), n11 = unit(t1, p1), n10 = unit(t1, p0);
                Vec3 mid = n00.add(n11).normalize(), toEye = c.eye.subtract(at.add(mid.scale(radius))).normalize();
                if (frontOnly && mid.dot(toEye) < 0) continue;
                double u0 = (j / (double) lon + s) * .5, u1 = u0 + .5 / lon;
                if (u1 > 1) { u0 -= .5; u1 -= .5; }
                tex(c, at.add(n00.scale(radius)), at.add(n01.scale(radius)), at.add(n11.scale(radius)), at.add(n10.scale(radius)), tex,
                        u0, u1, i / (double) lat, (i + 1) / (double) lat, color,
                        fresnel(c, at, n00, radius, core, rim), fresnel(c, at, n01, radius, core, rim), fresnel(c, at, n11, radius, core, rim), fresnel(c, at, n10, radius, core, rim));
            }
        }
    }

    private static Vec3 unit(double theta, double phi) { return new Vec3(Math.sin(theta) * Math.cos(phi), Math.cos(theta), Math.sin(theta) * Math.sin(phi)); }

    private static double fresnel(Ctx c, Vec3 at, Vec3 n, double radius, double core, double rim) {
        Vec3 p = at.add(n.scale(radius));
        double f = 1 - Math.abs(n.dot(c.eye.subtract(p).normalize()));
        return core + (rim - core) * f * f;
    }

    /** A flame tongue from base along dir, camera-facing, textured base (V 1) to tip (V 0). */
    static void tongue(Ctx c, Vec3 base, Vec3 dir, double length, double width, GateAtlas.Region tex, int color, double alpha) {
        if (alpha <= 0) return;
        Vec3 tip = base.add(dir.scale(length));
        Vec3 side = dir.cross(base.add(tip).scale(.5).subtract(c.eye));
        if (side.lengthSqr() < 1e-10) return;
        side = side.normalize().scale(width);
        tex(c, tip.subtract(side), tip.add(side), base.add(side), base.subtract(side), tex, 0, 1, 0, 1, color, alpha, alpha, alpha, alpha);
    }

    /** A camera-facing beam from a to b, wa/wb half wide at its ends, on the LIGHT strip: sharp core, soft edges. */
    static void beam(Ctx c, Vec3 a, Vec3 b, double wa, double wb, int color, double alphaA, double alphaB) {
        Vec3 side = b.subtract(a).cross(a.add(b).scale(.5).subtract(c.eye));
        if (side.lengthSqr() < 1e-10 || alphaA <= 0 && alphaB <= 0) return;
        side = side.normalize();
        tex(c, a.subtract(side.scale(wa)), b.subtract(side.scale(wb)), b.add(side.scale(wb)), a.add(side.scale(wa)), BlueRedAtlas.LIGHT, 0, 1, 0, 1,
                color, alphaA, alphaB, alphaB, alphaA);
    }

    /** A flat textured quad lying on the ground (shockwaves, the red glow spreading under an explosion). */
    static void flat(Ctx c, Vec3 at, double half, GateAtlas.Region tex, int color, double alpha) {
        if (alpha <= 0) return;
        tex(c, at.add(-half, 0, -half), at.add(half, 0, -half), at.add(half, 0, half), at.add(-half, 0, half), tex, 0, 1, 0, 1, color, alpha, alpha, alpha, alpha);
    }

    /** A brush stroke along an arc of the circle (u, w) round center: thin at its tail, full at its head, camera-facing, U along the stroke. */
    static void arc(Ctx c, Vec3 center, Vec3 u, Vec3 w, double radius, double a0, double sweep, double width, int color, double alpha, GateAtlas.Region tex) {
        int n = 14;
        Vec3 pl = null, pr = null;
        double prevAlpha = 0;
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n, a = a0 + sweep * t;
            Vec3 p = center.add(u.scale(Math.cos(a) * radius)).add(w.scale(Math.sin(a) * radius));
            Vec3 tangent = w.scale(Math.cos(a)).subtract(u.scale(Math.sin(a)));
            Vec3 side = tangent.cross(p.subtract(c.eye));
            if (side.lengthSqr() < 1e-10) continue;
            side = side.normalize().scale(width * (.35 + .65 * Math.sin(Math.PI * Math.min(1, t * 1.15))));
            double k = alpha * (.3 + .7 * t);
            Vec3 l = p.subtract(side), r = p.add(side);
            if (pl != null) tex(c, pl, l, r, pr, tex, t - 1.0 / n, t, 0, 1, color, prevAlpha, k, k, prevAlpha);
            pl = l; pr = r; prevAlpha = k;
        }
    }

    /** A jagged lightning bolt: a soft wide glow and a white-hot core on the LIGHT strip; it may fork (depth). Fades out right against the eye. */
    static void bolt(Ctx c, Random rand, Vec3 from, Vec3 dir, double length, int segments, double jitter, double width, int color, double alpha, int depth) {
        Vec3 p = from, d = dir;
        double step = length / segments;
        for (int s = 0; s < segments; s++) {
            Vec3 q = p.add(d.scale(step)).add(gauss(rand).scale(jitter * step));
            double k = alpha * (1 - .5 * s / segments) * clamp01((Math.min(p.distanceTo(c.eye), q.distanceTo(c.eye)) - 2) / 3);
            beam(c, p, q, width * 4, width * 4, color, k * .6, k * .6);
            beam(c, p, q, width, width, 0xFFFFFF, k, k);
            if (depth > 0 && rand.nextDouble() < .3)
                bolt(c, rand, q, d.add(gauss(rand).scale(.8)).normalize(), length * .4, Math.max(2, segments / 2), jitter, width * .6, color, alpha * .8, depth - 1);
            d = d.add(gauss(rand).scale(.2)).normalize();
            p = q;
        }
    }

    /** Glittering four-point stars inside a sphere, twinkling. */
    static void sparkles(Ctx c, Vec3 at, double radius, double age, int count, int bright, int tint, long seed) {
        Random rand = new Random(seed);
        for (int i = 0; i < count; i++) {
            Vec3 d = gauss(rand).normalize();
            double rr = radius * Math.cbrt(rand.nextDouble()), spin = age * (.02 + .03 * rand.nextDouble());
            Vec3 p = at.add(new Vec3(d.x * Math.cos(spin) - d.z * Math.sin(spin), d.y, d.x * Math.sin(spin) + d.z * Math.cos(spin)).scale(rr));
            double tw = .5 + .5 * Math.sin(age * (.4 + rand.nextDouble()) + i);
            spark(c, p, .1 + .14 * rand.nextDouble(), .9 * tw, i % 3 == 0 ? tint : bright, i);
        }
    }

    /** Pixel motes spiralling into the core from up to radius * 2.2 (debris and energy being swallowed). */
    static void motes(Ctx c, Vec3 at, double radius, double age, int color, int count) {
        for (int i = 0; i < count; i++) {
            double phase = (i * .618034 + age * (.018 + .006 * (i % 4))) % 1;
            double r = radius * (2.2 - 1.9 * phase), a = i * 2.39996 + phase * 7;
            Vec3[] uw = basis(new Vec3(Math.sin(i * 1.3), Math.cos(i * 2.1), Math.sin(i * .7 + 1)).normalize());
            Vec3 p = at.add(uw[0].scale(Math.cos(a) * r)).add(uw[1].scale(Math.sin(a) * r));
            if (p.distanceTo(c.eye) < 1.2) continue;
            sprite(c, p, .04 + .05 * (1 - phase), .35 + .6 * phase, i % 5 == 0 ? 0xFFFFFF : color, BlueRedAtlas.SOLID, a);
        }
    }

    /** Camera-facing ring with soft edges. */
    static void ring(Ctx c, Vec3 at, double radius, double width, int color, double alpha) {
        Vec3 look = at.subtract(c.eye);
        if (look.lengthSqr() < 1e-6) return;
        Vec3[] uw = basis(look.normalize());
        band(c, at, uw[0], uw[1], radius, width, color, alpha);
    }

    static void orbit(Ctx c, Vec3 at, Vec3 n, double radius, double width, int color, double alpha) {
        Vec3[] uw = basis(n);
        band(c, at, uw[0], uw[1], radius, width, color, alpha);
    }

    static void band(Ctx c, Vec3 at, Vec3 u, Vec3 w, double radius, double width, int color, double alpha) {
        int n = 40;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            Vec3 d0 = u.scale(Math.cos(a0)).add(w.scale(Math.sin(a0))), d1 = u.scale(Math.cos(a1)).add(w.scale(Math.sin(a1)));
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
