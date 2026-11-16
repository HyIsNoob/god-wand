package net.godwand.client;

import net.godwand.skill.BlueRedRules;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

import static net.godwand.client.GateDraw.*;

/**
 * Geometry of Blue, Red and Purple (owner references, 2026-11-16).
 * Blue: saturated blue flame licks (alpha pass, so they stay deep blue in daylight) round a white-cyan glittering core, a white smoke swirl.
 * Red: pure red (never orange), bright and dark curved brush strokes whirling round a small core, glowing and dark orbs, many particles.
 * Purple: a violet-white sphere crawling with lightning that swells, then a white dome, lightning thrown to the horizon and a whiteout.
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
        // Flame licks: saturated blue blobs that rise and curl round the sphere and fade, refreshed continuously.
        int flames = ready ? 40 : 28;
        for (int i = 0; i < flames; i++) {
            double life = 22 + (i % 5) * 3, phase = (age * speed / life + i * .618034) % 1;
            double a = i * 2.39996 + age * .05 * speed, tilt = Math.sin(i * 1.7) * .9;
            Vec3 dir = new Vec3(Math.cos(a) * Math.cos(tilt), Math.sin(tilt), Math.sin(a) * Math.cos(tilt));
            Vec3 p = at.add(dir.scale(r * (.7 + .65 * phase))).add(0, phase * r * .8, 0);
            double size = r * (.5 + .45 * Math.sin(Math.PI * phase)) * (ready ? 1.15 : 1);
            int color = i % 3 == 0 ? DEEP_BLUE : i % 3 == 1 ? ROYAL : INDIGO;
            if (c.body()) sprite(c, p, size, .82 * Math.sin(Math.PI * phase) * appear, color, GateAtlas.SMOKE, a + phase * 2.5);
            if (c.glow() && i % 2 == 0) sprite(c, p, size * .7, .18 * Math.sin(Math.PI * phase) * appear, CYAN, GateAtlas.SMOKE, -a);
        }
        // White slashes whirling round it like a vortex (owner refs 13, 14).
        for (int i = 0; i < (ready ? 9 : 6); i++) {
            double tilt = .25 + .35 * Math.sin(i * 1.9), spin = age * (.32 + .04 * i) * speed + i * 1.7;
            Vec3 n = new Vec3(Math.sin(i * 2.3) * tilt, 1, Math.cos(i * 2.3) * tilt).normalize();
            Vec3[] uw = basis(n);
            double rad = r * (1.25 + .22 * (i % 4));
            if (c.body()) arc(c, at.add(0, (i % 3 - 1) * r * .25, 0), uw[0], uw[1], rad, spin, 1.9, r * .2, 0xF4FAFF, .75 * appear, GateAtlas.FIBRE);
            if (c.glow()) arc(c, at.add(0, (i % 3 - 1) * r * .25, 0), uw[0], uw[1], rad, spin, 1.9, r * .06, CYAN, .7 * appear, GateAtlas.SOLID);
        }
        if (c.glow()) ring(c, at, r * 1.08, .12, ICE, .45 * appear);
        // A white smoke swirl wrapping round its waist.
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14 + age * .09 * speed, wobble = Math.sin(age * .07 + i) * .25;
            Vec3 p = at.add(Math.cos(a) * r * 1.55, -r * .35 + wobble, Math.sin(a) * r * 1.55);
            if (c.body()) sprite(c, p, r * .55, .35 * appear, SMOKE_WHITE, GateAtlas.SMOKE, a * 2);
        }
        if (c.glow()) {
            halo(c, at, r * 3.4, (.32 + .15 * beat) * appear, ROYAL);
            halo(c, at, r * 1.9, (.7 + .2 * beat) * appear, CYAN);
            halo(c, at, r * (ready ? 1.15 : .95), (ready ? 1 : .9) * appear, ICE);
            sparkles(c, at, r * 1.1, age, ready ? 44 : 30, ICE, CYAN, 7);
            motes(c, at, r, age, CYAN, 36);
            if (ready) {
                for (int k = 0; k < 2; k++) {
                    double spin = age * (.07 + .03 * k) + k * 2.1;
                    Vec3 n = new Vec3(Math.cos(spin), .6 * Math.sin(spin * .7 + k), Math.sin(spin)).normalize();
                    orbit(c, at, n, r * (1.3 + .15 * k), .06, ICE, .75 * appear);
                }
                Random rand = new Random((long) Math.floor(age / 3) * 7919L);
                for (int b = 0; b < 4; b++) {
                    Vec3 d = gauss(rand).normalize();
                    bolt(c, rand, at.add(d.scale(r * .9)), d, r * (.6 + .5 * rand.nextDouble()), 4, .3, .05, CYAN, .9, 0);
                }
            }
            if (flash > 0) { halo(c, at, r * 7, flash, 0xFFFFFF); halo(c, at, r * 3, flash, 0xFFFFFF); }
        }
    }

    // ---- Red ----

    /** Red's orb (charging in front of the caster, or flying): a small hot core, bright and dark brush strokes whirling round it. */
    static void redOrb(Ctx c, Vec3 at, double size, double age) {
        double reach = size * 3.2;
        for (int i = 0; i < 7; i++) {
            double spin = age * (.28 + .05 * i) * (i % 2 == 0 ? 1 : -1) + i * 1.05;
            Vec3 n = new Vec3(Math.sin(i * 1.9 + age * .02), Math.cos(i * 1.3), Math.sin(i * .7 + 1)).normalize();
            Vec3[] uw = basis(n);
            double radius = reach * (.75 + .3 * Math.sin(i * 2.3 + age * .1));
            boolean dark = i % 3 == 2;
            if (c.body()) arc(c, at, uw[0], uw[1], radius * (dark ? 1.1 : 1), spin + (dark ? 1.4 : 0), dark ? 1.5 : 1.8, size * (dark ? .5 : .5), dark ? RED_DARK : RED_BODY, .9, GateAtlas.FIBRE);
            if (!dark && c.glow()) arc(c, at, uw[0], uw[1], radius, spin, 1.8, size * .12, RED_CORE, .55, GateAtlas.SOLID);
        }
        Random rand = new Random(31);
        for (int i = 0; i < 16; i++) {
            double a = i * 2.39996 + age * (.06 + .02 * (i % 3)), rr = reach * (.8 + .9 * rand.nextDouble()), y = (rand.nextDouble() - .5) * reach * 1.4;
            Vec3 p = at.add(Math.cos(a) * rr, y, Math.sin(a) * rr);
            if (p.distanceTo(c.eye) < 1.2) continue;
            boolean dark = i % 3 == 0;
            if (c.body()) sprite(c, p, size * (dark ? .2 : .26), .9, dark ? RED_DARK : RED_BODY, GateAtlas.GLOW);
            if (!dark && c.glow()) halo(c, p, size * .1, .9, RED_CORE);
        }
        if (c.body()) {
            sprite(c, at, size * 4, .22, RED_DEEP, GateAtlas.GLOW);
            sprite(c, at, size * 1.9, .85, RED_BODY, GateAtlas.GLOW);
            sprite(c, at, size * .5, .9, RED_DARK, GateAtlas.GLOW);
            motes(c, at, reach * .7, age * 2.4, RED_BODY, 56);
        }
        if (!c.glow()) return;
        halo(c, at, size * .9, .5, RED);
        halo(c, at, size * .3, .9, RED_CORE);
    }

    /** Red in flight: the orb at the head, spiralling red and dark ribbons along the path behind it, sparks. */
    static void redFlight(Ctx c, Vec3 origin, Vec3 dir, Vec3 head, double age, double fade, long seed, boolean flying) {
        if (fade <= 0) return;
        double travelled = head.distanceTo(origin), tailLength = Math.min(travelled, 22);
        Vec3[] uw = basis(dir);
        if (c.body()) shaft(c, head.subtract(dir.scale(tailLength)), head, .03, .55, RED_BODY, .15 * fade, .95 * fade, age);
        if (c.glow()) shaft(c, head.subtract(dir.scale(Math.min(travelled, 8))), head, .02, .14, RED_CORE, .2 * fade, .8 * fade, age * 1.7);
        // Three helices twist round the path, tight near the head and loosening behind it.
        for (int h = 0; h < 3; h++) {
            boolean dark = h == 2;
            if (!c.body()) continue;
            Vec3 prev = null;
            int n = 28;
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n, back = t * tailLength, turn = h * 2.0944 - age * .9 + back * .55;
                double radius = (.25 + .9 * t) * (dark ? 1.25 : 1);
                Vec3 p = head.subtract(dir.scale(back)).add(uw[0].scale(Math.cos(turn) * radius)).add(uw[1].scale(Math.sin(turn) * radius));
                if (prev != null) {
                    double a = (1 - t) * fade;
                    if (dark) shaft(c, prev, p, .14 * (1 - t), .14 * (1 - t), RED_DARK, .7 * a, .7 * a, 0);
                    else shaft(c, prev, p, .16 * (1 - t) + .02, .16 * (1 - t) + .02, h == 0 ? RED_BODY : RED_DEEP, a, a, age + i);
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
            if (p.distanceTo(c.eye) > 1.2) sprite(c, p, .05 + .05 * rand.nextDouble(), .9 * fade * (1 - t * .6), i % 4 == 0 ? RED_CORE : RED, GateAtlas.STAR, rand.nextDouble() * 6);
        }
    }

    // ---- bursts ----

    static void burst(Ctx c, Vec3 at, double age, boolean blue) {
        double k = clamp01(age / (blue ? 12 : 14)), fade = 1 - k;
        if (fade <= 0) return;
        if (blue && c.body()) {
            // The shattered Blue's flames are flung outward and fade.
            for (int i = 0; i < 18; i++) {
                double a = i * 2.39996, tilt = Math.sin(i * 1.7);
                Vec3 d = new Vec3(Math.cos(a) * Math.cos(tilt), Math.sin(tilt), Math.sin(a) * Math.cos(tilt));
                sprite(c, at.add(d.scale(1.5 + age * .9)), 1 + age * .08, .8 * fade, i % 2 == 0 ? DEEP_BLUE : ROYAL, GateAtlas.SMOKE, a + age * .2);
            }
        }
        if (!blue && c.body()) {
            Random rand = new Random(97);
            for (int i = 0; i < 16; i++) {
                Vec3 d = gauss(rand).normalize();
                sprite(c, at.add(d.scale(1 + age * (.6 + .5 * rand.nextDouble()))), .5 + rand.nextDouble() * .6, .9 * fade, INK, GateAtlas.SMOKE, i);
            }
            band(c, at.add(0, -.9, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 1 + age * .9, 1.2 + age * .2, RED_BODY, .55 * fade);
        }
        if (!c.glow()) return;
        int color = blue ? CYAN : RED;
        halo(c, at, 2 + age * (blue ? .8 : 1), fade, color);
        halo(c, at, 1 + age * .35, fade, blue ? ICE : RED_CORE);
        ring(c, at, 1 + age * (blue ? 1.2 : 1.5), .4, blue ? ICE : RED_LIGHT, .9 * fade);
        if (!blue) for (int i = 0; i < 5; i++) {
            Vec3[] uw = basis(new Vec3(Math.sin(i * 1.3), Math.cos(i * 2.1), Math.sin(i * .7 + 1)).normalize());
            arc(c, at, uw[0], uw[1], 1.5 + age * 1.3, i * 1.26 + age * .2, 1.4, .5 * fade, RED, fade, GateAtlas.FIBRE);
        }
    }

    // ---- Purple ----

    static void purple(Ctx c, Vec3 at, double age, Vec3 eye, Vec3 look) {
        int detonate = BlueRedRules.PURPLE_DETONATE, end = BlueRedRules.PURPLE_CINEMATIC;
        double merge = clamp01(age / BlueRedRules.PURPLE_MERGE), blast = clamp01((age - detonate) / 18.0);
        double fade = 1 - clamp01((age - detonate - 18) / (end - detonate - 18.0));
        if (fade <= 0) return;
        // The world darkens to violet round the participants while it grows, and stays tinted through the blast.
        double tint = smooth((age - 10) / 30) * fade * clamp01(1 - eye.distanceTo(at) / 120);
        if (c.body() && tint > 0) sprite(c, eye.add(look.scale(1)), 6, .38 * tint, NIGHT, GateAtlas.SOLID);
        if (age < BlueRedRules.PURPLE_MERGE) {
            double a = age * .55, d = 3.2 * (1 - merge);
            Vec3 off = new Vec3(Math.cos(a) * d, Math.sin(a * .5) * d * .3, Math.sin(a) * d);
            blue(c, at.add(off), false, age + 40, -1);
            redOrb(c, at.subtract(off), .55, age);
            return;
        }
        Random rand = new Random((long) Math.floor(age / 2) * 104729L);
        if (age < detonate) {
            double s = 4 + 8 * smooth((age - BlueRedRules.PURPLE_MERGE) / (double) (detonate - BlueRedRules.PURPLE_MERGE));
            if (c.glow()) {
                halo(c, at, s * 3, .5, VIOLET);
                halo(c, at, s * 1.7, .75, ORCHID);
                halo(c, at, s * 1.05, .95, PALE);
                halo(c, at, s * .6, 1, 0xFFFFFF);
                ring(c, at, s * 1.02, .2 + s * .03, PALE, .7);
                sparkles(c, at, s, age, 90, PALE, ORCHID, 11);
                // Lightning crawling over the surface, and bolts striking out from it.
                for (int b = 0; b < 8; b++) {
                    Vec3 d = gauss(rand).normalize();
                    bolt(c, rand, at.add(d.scale(s * .95)), d.cross(gauss(rand)).normalize(), s * .9, 5, .35, .07, ORCHID, .95, 0);
                }
                for (int b = 0; b < 5; b++) {
                    Vec3 d = gauss(rand).normalize();
                    bolt(c, rand, at.add(d.scale(s)), d, s * (1.2 + rand.nextDouble()), 9, .45, .16, ORCHID, 1, 2);
                }
            }
            if (c.body()) sprite(c, at, s * .45, .5, NIGHT, GateAtlas.GLOW);
            return;
        }
        // Detonation: a white dome swelling out, lightning hurled to the horizon, a whiteout before the eyes.
        double dome = 12 + 40 * Math.sqrt(blast);
        if (c.glow()) {
            halo(c, at, dome * 2, .5 * fade, VIOLET);
            halo(c, at, dome * 1.2, .9 * fade, ORCHID);
            halo(c, at, dome * .8, (1 - blast * .6) * fade, 0xFFFFFF);
            sparkles(c, at, dome * .8, age, 70, PALE, ORCHID, 13);
            int bolts = 22;
            Random fixed = new Random(424242L + (long) Math.floor(age / 3));
            for (int b = 0; b < bolts; b++) {
                double a = b * Math.PI * 2 / bolts + fixed.nextGaussian() * .12, up = .05 + .55 * fixed.nextDouble();
                Vec3 d = new Vec3(Math.cos(a), up, Math.sin(a)).normalize();
                double reach = (25 + 90 * smooth(blast)) * (.6 + .4 * fixed.nextDouble());
                bolt(c, fixed, at.add(d.scale(dome * .6)), d, reach, 18, .3, .55, ORCHID, fade, 2);
            }
        }
        double white = 1 - clamp01((age - detonate) / 6.0);
        if (white > 0 && c.body()) sprite(c, eye.add(look.scale(.8)), 6, .92 * white, 0xFFFFFF, GateAtlas.SOLID);
        if (white > 0 && c.glow()) sprite(c, eye.add(look.scale(.8)), 6, .6 * white, PALE, GateAtlas.SOLID);
    }

    /** Purple's first act for its two players: the orbs face each other against black, purple smoke streams past, lightning leaps between, they meet. */
    static void purpleVoid(Ctx c, Vec3 at, double age, Vec3 eye, Vec3 look) {
        int end = BlueRedRules.PURPLE_VOID_END;
        Vec3 view = new Vec3(eye.x - at.x, 0, eye.z - at.z);
        view = view.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : view.normalize();
        Vec3 axis = view.cross(new Vec3(0, 1, 0)).normalize();
        double k = smooth(age / (end - 12.0)), d = 5.5 * (1 - k), meet = smooth((age - (end - 14)) / 14.0);
        if (c.body()) {
            sprite(c, eye.add(look.scale(.5)), 4, 1, VOID, GateAtlas.SOLID);
            for (int i = 0; i < 20; i++) {
                double x = ((i * .37 + age * .012 * (1 + i % 3)) % 1) * 2 - 1, y = Math.sin(i * 2.1 + age * .03) * 2.5;
                int color = x < -.3 ? 0x3A0A2E : x > .3 ? 0x101A66 : 0x3A0E66;
                sprite(c, at.subtract(view.scale(3 + i % 4)).add(axis.scale(x * 14)).add(0, y, 0), 3 + (i % 3), .55, color, GateAtlas.SMOKE, i + age * .01);
            }
        }
        Vec3 red = at.subtract(axis.scale(d)), blue = at.add(axis.scale(d));
        if (meet < 1) {
            if (c.body()) for (int j = 0; j < 7; j++) {
                double fade = .5 * (1 - j / 7.0) * (1 - meet);
                sprite(c, red.subtract(axis.scale(j * .9 + .6)), 1.6 - j * .15, fade, RED_DEEP, GateAtlas.SMOKE, j + age * .05);
                sprite(c, blue.add(axis.scale(j * .9 + .6)), 1.8 - j * .15, fade, INDIGO, GateAtlas.SMOKE, -j - age * .05);
            }
            if (c.body()) sprite(c, red, 2.1, .8 * (1 - meet), RED_BODY, GateAtlas.SMOKE, age * .05);
            if (c.glow()) { halo(c, red, 2.6, .55 * (1 - meet), RED); halo(c, red, 1.2, .8 * (1 - meet), RED_CORE); }
            redOrb(c, red, 1.5 * (1 - meet * .6), age);
            blue(c, blue, true, age + 40, -1);
        }
        Random rand = new Random((long) Math.floor(age / 2) * 7717L);
        if (c.glow() && k > .35) {
            int bolts = (int) (2 + 8 * k);
            for (int b = 0; b < bolts; b++)
                bolt(c, rand, red.add(axis.scale(.8)), axis.add(gauss(rand).scale(.25)).normalize(), Math.max(.5, d * 2 - 1.6), 7, .4, .06, ORCHID, .9, 1);
        }
        if (c.glow()) {
            for (int i = 0; i < 12; i++) {
                double y = ((i * .29 + age * .006) % 1) * 6 - 3;
                ring(c, at.add(axis.scale(Math.sin(i * 1.7) * 7)).add(0, y, 0).subtract(view.scale(1)), .15, .03, ORCHID, .6);
            }
            if (meet > 0) {
                halo(c, at, 4 + 22 * meet, .6 * meet, VIOLET);
                halo(c, at, 2 + 12 * meet, meet, ORCHID);
                halo(c, at, 1 + 6 * meet, meet, 0xFFFFFF);
                for (int b = 0; b < 12; b++) {
                    Vec3 dir = gauss(rand).normalize();
                    bolt(c, rand, at.add(dir.scale(1 + 3 * meet)), dir, 3 + 9 * meet, 8, .4, .1, ORCHID, meet, 1);
                }
                sparkles(c, at, 2 + 6 * meet, age, 50, PALE, ORCHID, 17);
            }
        }
        double white = clamp01((age - (end - 4)) / 4.0);
        if (white > 0 && c.body()) sprite(c, eye.add(look.scale(.45)), 4, white, 0xFFFFFF, GateAtlas.SOLID);
    }

    /** Red's impact frames (owner, 2026-11-16): black, red, black, then a fading red wash, with radial ink strokes from the impact. */
    static int impactColor(int frame) { return frame % 2 == 0 ? INK : RED_BODY; }
    static double impactAlpha(int frame) { return frame < 3 ? .9 - .12 * frame : frame < 6 ? .35 * (6 - frame) / 3.0 : 0; }

    static void impact(Ctx c, Vec3 at, int frame, Vec3 eye, Vec3 look) {
        double a = impactAlpha(frame);
        if (a <= 0 || !c.body()) return;
        int back = frame < 3 ? impactColor(frame) : RED_BODY, stroke = back == INK ? RED_BODY : INK;
        sprite(c, eye.add(look.scale(.5)), 4, a, back, GateAtlas.SOLID);
        Vec3[] uw = basis(look);
        Random rand = new Random(frame * 131L + 5);
        for (int i = 0; i < 18; i++) {
            double ang = i * Math.PI * 2 / 18 + rand.nextGaussian() * .1, len = 4 + rand.nextDouble() * 10;
            Vec3 dir = uw[0].scale(Math.cos(ang)).add(uw[1].scale(Math.sin(ang)));
            shaft(c, at.add(dir.scale(.6)), at.add(dir.scale(len)), .25 + rand.nextDouble() * .3, .02, stroke, a, a * .6, 0);
        }
        if (frame < 3) sprite(c, at, 1.4, a, stroke, GateAtlas.GLOW);
    }

    // ---- shapes ----

    /** A brush stroke along an arc of the circle (u, w) round center: thin at its tail, full at its head, camera-facing. */
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
            side = side.normalize().scale(width * Math.sin(Math.PI * t) * (.35 + .65 * t));
            double k = alpha * (.25 + .75 * t);
            Vec3 l = p.subtract(side), r = p.add(side);
            if (pl != null) tex(c, pl, l, r, pr, tex, t - 1.0 / n, t, 0, 1, color, prevAlpha, k, k, prevAlpha);
            pl = l; pr = r; prevAlpha = k;
        }
    }

    /** A jagged lightning bolt with a soft glow and a white-hot core; it may fork (depth) into thinner bolts. */
    static void bolt(Ctx c, Random rand, Vec3 from, Vec3 dir, double length, int segments, double jitter, double width, int color, double alpha, int depth) {
        Vec3 p = from, d = dir;
        double step = length / segments;
        for (int s = 0; s < segments; s++) {
            Vec3 q = p.add(d.scale(step)).add(gauss(rand).scale(jitter * step));
            double k = alpha * (1 - .5 * s / segments) * clamp01((Math.min(p.distanceTo(c.eye), q.distanceTo(c.eye)) - 2) / 3);
            shaft(c, p, q, width * 3, width * 3, color, k * .45, k * .45, 0);
            shaft(c, p, q, width * .7, width * .7, 0xFFFFFF, k, k, 0);
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
            sprite(c, p, .06 + .1 * rand.nextDouble(), .9 * tw, i % 3 == 0 ? tint : bright, GateAtlas.STAR, i);
        }
    }

    /** Pixel motes spiralling into the core from up to radius * 2.2 (debris and energy being swallowed). */
    static void motes(Ctx c, Vec3 at, double radius, double age, int color, int count) {
        for (int i = 0; i < count; i++) {
            double phase = (i * .618034 + age * (.018 + .006 * (i % 4))) % 1;
            double r = radius * (2.2 - 1.9 * phase), a = i * 2.39996 + phase * 7;
            Vec3[] uw = basis(new Vec3(Math.sin(i * 1.3), Math.cos(i * 2.1), Math.sin(i * .7 + 1)).normalize());
            Vec3 p = at.add(uw[0].scale(Math.cos(a) * r)).add(uw[1].scale(Math.sin(a) * r));
            sprite(c, p, .04 + .05 * (1 - phase), .35 + .6 * phase, i % 5 == 0 ? 0xFFFFFF : color, GateAtlas.SOLID, a);
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
