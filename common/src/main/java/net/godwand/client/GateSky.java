package net.godwand.client;

import net.godwand.client.GateDraw.Ctx;
import net.godwand.skill.SilentGateSequence;
import net.minecraft.world.phys.Vec3;

import static net.godwand.client.GateDraw.clamp01;
import static net.godwand.client.GateDraw.smooth;

/** Storm sky around the great circle: a dark dome around the camera and a vortex of clouds turning about the rim. */
final class GateSky {
    private GateSky() {}
    private static final double TAU = Math.PI * 2;
    static final int STORM = 0x0B1424, CLOUD_BODY = 0x4A5F80, CLOUD_DARK = 0x2A3850, CLOUD_LIT = 0xBFEFFF, CLOUD_RIM = 0x7FE8FF, CLOUDS = 40;
    private static final int RINGS = 6, SEGS = 24;
    private static final double DOME_RADIUS = 180, DOME_ALPHA = .45, S = net.godwand.skill.SilentGateSequence.SCALE;

    /** Hemisphere about the camera, darkening the sky from 80 to 120 without a shader and melting into the horizon. */
    static void dome(Ctx c, Vec3 gate, double age) {
        if (!c.body()) return;
        double k = smooth((age - 80) / 40) * (1 - smooth((c.eye.distanceTo(gate) - 150 * S) / (80 * S)));
        if (k <= 0) return;
        for (int j = 0; j < RINGS; j++) for (int s = 0; s < SEGS; s++) {
            double e0 = Math.PI / 2 * j / RINGS, e1 = Math.PI / 2 * (j + 1) / RINGS, a0 = s * TAU / SEGS, a1 = (s + 1) * TAU / SEGS;
            double k0 = DOME_ALPHA * k * smooth(e0 / 1.0), k1 = DOME_ALPHA * k * smooth(e1 / 1.0);
            GateDraw.gradient(c, at(c.eye, e0, a0), at(c.eye, e0, a1), at(c.eye, e1, a1), at(c.eye, e1, a0), STORM, k0, k0, k1, k1);
        }
    }
    private static Vec3 at(Vec3 o, double elevation, double azimuth) {
        double h = Math.cos(elevation) * DOME_RADIUS;
        return o.add(Math.cos(azimuth) * h, Math.sin(elevation) * DOME_RADIUS, Math.sin(azimuth) * h);
    }

    private static double hash(int i, int salt) { double x = Math.sin(i * 127.1 + salt * 311.7) * 43758.5453; return x - Math.floor(x); }

    /** Cloud billows spiralling in from r 90 to the rim at r 56, quicker near the centre, lit cyan on the circle's side. */
    static void clouds(Ctx c, Vec3 ctr, double age, double endK) {
        double on = smooth((age - 80) / 40);
        if (on <= 0) return;
        for (int i = 0; i < CLOUDS; i++) {
            // The shockwave at 204 shoves the vortex outwards for one beat.
            double push = smooth((age - 204) / 5) * (1 - smooth((age - 209) / 40));
            double life = (hash(i, 1) + age * .0011) % 1, r = S * (90 - 34 * life) * (1 + .8 * smooth(endK) + .3 * push);
            double a = i * 2.399963 + age * .0032 * (1 + (90 - r) / 50) + hash(i, 2) * .6;
            double envelope = Math.pow(Math.sin(Math.PI * life), .6) * on;
            double size = S * (13 + 9 * hash(i, 3));
            Vec3 p = ctr.add(Math.cos(a) * r, ((hash(i, 4) - .5) * 18 - 4) * S, Math.sin(a) * r);
            Vec3 toward = ctr.subtract(p).multiply(1, 0, 1).normalize();
            // A billow is a handful of overlapping soft puffs at slight offsets, each turned its own way.
            int n = 3 + (i % 3);
            for (int k = 0; k < n; k++) {
                double ox = (hash(i * 7 + k, 5) - .5) * size * 1.3, oy = (hash(i * 7 + k, 6) - .5) * size * .6, oz = (hash(i * 7 + k, 7) - .5) * size * 1.3;
                double sz = size * (.6 + .5 * hash(i * 7 + k, 8)), roll = hash(i * 7 + k, 9) * TAU + age * .002 * (k % 2 == 0 ? 1 : -1);
                Vec3 q = p.add(ox, oy, oz);
                if (c.body()) GateDraw.sprite(c, q, sz, .24 * envelope, i % 3 == 0 ? CLOUD_BODY : CLOUD_DARK, GateAtlas.CLOUD, roll);
                // Light from the circle: the side facing it glows cyan-white.
                if (c.glow() && k < 3) GateDraw.sprite(c, q.add(toward.scale(sz * .5)), sz * .75, .13 * envelope, k == 0 ? CLOUD_LIT : CLOUD_RIM, GateAtlas.CLOUD, roll + 1.7);
            }
        }
    }
}
