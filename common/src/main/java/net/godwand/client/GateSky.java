package net.godwand.client;

import net.godwand.client.GateDraw.Ctx;
import net.godwand.skill.SilentGateSequence;
import net.minecraft.world.phys.Vec3;

import static net.godwand.client.GateDraw.clamp01;
import static net.godwand.client.GateDraw.smooth;

/** Storm sky around the great circle: a dark dome around the camera, a vortex of clouds turning about the rim, and what makes the whole sky the spell's:
 *  spiral arms of storm reaching far out, lightning crawling through them, and curtains of aurora hanging all round. */
final class GateSky {
    private GateSky() {}
    private static final double TAU = Math.PI * 2;
    /** What the storm takes out of the light behind it (shade layer): most of the red, less of the blue. */
    static final int ABSORB = 0xFFEACC;
    static final int CLOUD_BODY = 0x4A5F80, CLOUD_LIT = 0xBFEFFF, CLOUD_RIM = 0x7FE8FF, CLOUDS = 64;
    private static final int RINGS = 6, SEGS = 24;
    /** Far behind the gate and every circle: the dome darkens the sky, not the porcelain in front of it (the gate has stamped its depth). */
    private static final double DOME_RADIUS = 1500, DOME_ALPHA = .6, S = net.godwand.skill.SilentGateSequence.SCALE;

    /** Hemisphere about the camera that darkens the sky (shade layer: no cut-off, so it melts into the horizon without a line), deepest overhead. */
    static void dome(Ctx c, Vec3 gate, double age) {
        if (!c.shade()) return;
        double k = smooth((age - 30) / 50) * (1 - smooth((c.eye.distanceTo(gate) - 150 * S) / (80 * S)));
        if (k <= 0) return;
        for (int j = 0; j < RINGS; j++) for (int s = 0; s < SEGS; s++) {
            double e0 = Math.PI / 2 * j / RINGS, e1 = Math.PI / 2 * (j + 1) / RINGS, a0 = s * TAU / SEGS, a1 = (s + 1) * TAU / SEGS;
            double k0 = DOME_ALPHA * k * smooth(e0 / .7), k1 = DOME_ALPHA * k * smooth(e1 / .7);
            GateDraw.gradient(c, at(c.eye, e0, a0), at(c.eye, e0, a1), at(c.eye, e1, a1), at(c.eye, e1, a0), ABSORB, k0, k0, k1, k1);
        }
        // A camera high over the ground looks down at the edge of the loaded world and the bright fog beyond it: a lower bowl of dark, deepening away from the horizon, hides both.
        double high = k * smooth((c.eye.y - (gate.y - SilentGateSequence.GATE_BASE - 37 * S) - 40) / 80);
        if (high > 0) for (int j = 0; j < 3; j++) for (int s = 0; s < SEGS; s++) {
            double e0 = -Math.PI / 2 * j / 3, e1 = -Math.PI / 2 * (j + 1) / 3, a0 = s * TAU / SEGS, a1 = (s + 1) * TAU / SEGS;
            double k0 = high * .9 * smooth(j / 1.5), k1 = high * .9 * smooth((j + 1) / 1.5);
            GateDraw.gradient(c, at(c.eye, e0, a0), at(c.eye, e0, a1), at(c.eye, e1, a1), at(c.eye, e1, a0), 0xFFFFFF, k0, k0, k1, k1);
        }
    }
    private static Vec3 at(Vec3 o, double elevation, double azimuth) {
        double h = Math.cos(elevation) * DOME_RADIUS;
        return o.add(Math.cos(azimuth) * h, Math.sin(elevation) * DOME_RADIUS, Math.sin(azimuth) * h);
    }

    private static double hash(int i, int salt) { double x = Math.sin(i * 127.1 + salt * 311.7) * 43758.5453; return x - Math.floor(x); }

    /** Cloud billows spiralling in from r 90 to the rim at r 56, quicker near the centre. Each billow is a heap of smoke puffs: dark where it stands against the sky
     *  (shade), lit cyan on the side of the circle and from within when the circle flares (glow). */
    static void clouds(Ctx c, Vec3 ctr, double age, double endK) {
        double on = smooth((age - 30) / 50);
        if (on <= 0 || !c.shade() && !c.glow()) return;
        for (int i = 0; i < CLOUDS; i++) {
            // The shockwave shoves the vortex outwards for one beat.
            double push = smooth((age - net.godwand.skill.SilentGateSequence.SHOCK) / 5) * (1 - smooth((age - net.godwand.skill.SilentGateSequence.SHOCK - 5) / 40));
            // The deck spreads: every billow is born at the rim and drifts outward over the sky, growing and thinning (owner, 2026-11-12: spreading, not turning).
            double life = (hash(i, 1) + age * .0009) % 1, r = S * (52 + 160 * life) * (1 + .5 * smooth(endK) + .12 * push);
            double a = i * 2.399963 + hash(i, 2) * .6;
            double envelope = Math.pow(Math.sin(Math.PI * life), .6) * on;
            double size = S * (13 + 9 * hash(i, 3)) * (.75 + 1.1 * life);
            Vec3 p = ctr.add(Math.cos(a) * r, ((hash(i, 4) - .5) * 18 - 4) * S, Math.sin(a) * r);
            Vec3 toward = ctr.subtract(p).multiply(1, 0, 1).normalize();
            int n = 4 + (i % 3);
            for (int k = 0; k < n; k++) {
                double ox = (hash(i * 7 + k, 5) - .5) * size * 1.5, oy = (hash(i * 7 + k, 6) - .5) * size * .7, oz = (hash(i * 7 + k, 7) - .5) * size * 1.5;
                double sz = size * (.55 + .6 * hash(i * 7 + k, 8)), roll = hash(i * 7 + k, 9) * TAU + age * .002 * (k % 2 == 0 ? 1 : -1);
                Vec3 q = p.add(ox, oy, oz);
                if (c.shade()) GateDraw.sprite(c, q, sz * 1.15, .55 * envelope, ABSORB, GateAtlas.SMOKE, roll);
                if (c.glow()) {
                    GateDraw.sprite(c, q, sz, .1 * envelope, CLOUD_BODY, GateAtlas.SMOKE, roll);
                    // Light from the circle: the side facing it glows cyan-white.
                    if (k < 3) GateDraw.sprite(c, q.add(toward.scale(sz * .45)), sz * .7, .12 * envelope, k == 0 ? CLOUD_LIT : CLOUD_RIM, GateAtlas.SMOKE, roll + 1.7);
                }
            }
        }
    }

    /** The storm beyond the vortex: three spiral arms of cloud reaching 195 design units out from the circle and turning slowly, so the whole sky belongs to the
     *  spell: dark masses (shade) with a faint light on their undersides (glow). */
    static void arms(Ctx c, Vec3 ctr, double age, double endK) {
        double on = smooth((age - 60) / 80) * (1 - smooth(endK / .8));
        if (on <= 0 || !c.shade() && !c.glow()) return;
        for (int i = 0; i < 60; i++) {
            int arm = i % 3, m = i / 3;
            double r = S * (96 + 5 * m + 5 * hash(i, 11)), a = arm * TAU / 3 + m * .16 + hash(i, 12) * .25;
            double size = S * (20 + 14 * hash(i, 13)), fade = on * (1 - .5 * m / 20.0), roll = hash(i, 15) * TAU + age * .0015;
            Vec3 p = ctr.add(Math.cos(a) * r, (-6 - 12 * hash(i, 14)) * S, Math.sin(a) * r);
            if (c.shade()) GateDraw.sprite(c, p, size * 1.2, .5 * fade, ABSORB, GateAtlas.SMOKE, roll);
            if (c.glow()) {
                GateDraw.sprite(c, p, size, .08 * fade, CLOUD_BODY, GateAtlas.SMOKE, roll);
                GateDraw.sprite(c, p.add(size * .3, -size * .25, -size * .25), size * .7, .07 * fade, CLOUD_RIM, GateAtlas.SMOKE, roll + 1.7);
            }
        }
    }

    /** Lightning in the storm: every 19 ticks a spot of the cloud deck may light up for a few ticks (always while the gate comes down), with a crawler of light
     *  running through the cloud from it. A local glow, never a flash of the whole screen. */
    static void lightning(Ctx c, Vec3 ctr, double age, double endK, boolean storm) {
        if (age < 80 || endK > .7 || !c.glow()) return;
        // Always while the gate comes down, and all through the barrage: the sky answers what is done under it.
        boolean descent = storm || age > SilentGateSequence.DESCENT_START - 20 && age < SilentGateSequence.SHOCK + 30;
        for (int back = 0; back < 2; back++) {
            int slot = (int) Math.floor(age / 19) - back;
            double t = age - slot * 19.0 - hash(slot, 21) * 8;
            if (t < 0 || t > 7 || !descent && hash(slot, 22) > .5) continue;
            double env = (t < 1 ? t : Math.exp(-(t - 1) / 2.2)) * (.65 + .35 * Math.sin(t * 5.1));
            double a = hash(slot, 23) * TAU, r = S * (62 + 110 * hash(slot, 24)), dir = a + 1.3 + hash(slot, 25) * 2;
            Vec3 at = ctr.add(Math.cos(a) * r, -8 * S, Math.sin(a) * r);
            GateDraw.sprite(c, at, 46 * S, .5 * env, CLOUD_LIT);
            GateDraw.sprite(c, at, 16 * S, .6 * env, 0xFFFFFF);
            for (int j = 0; j < 7; j++) {
                dir += (hash(slot * 9 + j, 26) - .5) * 1.4;
                Vec3 next = at.add(Math.cos(dir) * 9 * S, (hash(slot * 9 + j, 27) - .6) * 5 * S, Math.sin(dir) * 9 * S), side = next.subtract(at).cross(at.subtract(c.eye));
                if (side.lengthSqr() > 1e-8) {
                    side = side.normalize().scale(.9 * S);
                    double al = .9 * env * (1 - j / 8.0);
                    GateDraw.gradient(c, at.subtract(side), at, next, next.subtract(side), 0xFFFFFF, 0, al, al, 0);
                    GateDraw.gradient(c, at, at.add(side), next.add(side), next, 0xFFFFFF, al, 0, 0, al);
                }
                at = next;
            }
        }
    }

    private static final int[] AURORA = {0x5CFFC8, 0x7FE8FF, 0x9C8CFF};
    /** Curtains of light hanging in the sky all round the great circle, swaying, rayed like an aurora: the spell's light spilling over the whole sky. {@code k} 0..1. */
    static void aurora(Ctx c, Vec3 ctr, double age, double k) {
        if (k <= 0 || !c.glow()) return;
        int n = 22;
        for (int j = 0; j < 12; j++) {
            // Six curtains round the circle and six more far out and low, so the aurora reaches the horizon all round.
            boolean far = j >= 6;
            double mid = j * TAU / 6 + (far ? .5 : 0) + .5 * Math.sin(j * 2.1) + age * .0006, span = .9 + .5 * hash(j, 31), radius = S * ((far ? 215 : 118) + (far ? 70 : 55) * hash(j, 32));
            double base = ctr.y - ((far ? 96 : 58) + 30 * hash(j, 33)) * S, height = ((far ? 52 : 34) + 26 * hash(j, 34)) * S, prevA = 0;
            Vec3 prevB = null, prevT = null;
            for (int s = 0; s <= n; s++) {
                double u = (double) s / n, phi = mid + (u - .5) * span;
                double r = radius + S * 7 * Math.sin(u * 9 + age * .017 + j * 1.3), y = base + S * 5 * Math.sin(u * 6 - age * .013 + j);
                Vec3 b = new Vec3(ctr.x + Math.cos(phi) * r, y, ctr.z + Math.sin(phi) * r), t = b.add(0, height * (.75 + .25 * Math.sin(u * 14 + age * .03 + j * 2)), 0);
                double al = .32 * k * Math.sin(Math.PI * u) * (.55 + .45 * Math.sin(u * 37 + age * .045 + j * 5));
                if (prevB != null) {
                    // A short skirt fades in from below (no hard lower edge), then the curtain: a soft body and fine rays running up it.
                    Vec3 skirt = new Vec3(0, -.22 * height, 0);
                    GateDraw.gradient(c, prevB.add(skirt), b.add(skirt), b, prevB, AURORA[j % 3], 0, 0, .6 * al, .6 * prevA);
                    GateDraw.gradient(c, prevB, b, t, prevT, AURORA[j % 3], .6 * prevA, .6 * al, 0, 0);
                    double p0 = (u - 1.0 / n) * 2 % 1, p1 = p0 + 2.0 / n;
                    if (p1 <= 1.001) GateDraw.tex(c, prevB, b, t, prevT, GateAtlas.RAYS, p0, Math.min(1, p1), 0, 1, AURORA[(j + 1) % 3], prevA, al, 0, 0);
                }
                prevB = b; prevT = t; prevA = al;
            }
        }
    }
}
