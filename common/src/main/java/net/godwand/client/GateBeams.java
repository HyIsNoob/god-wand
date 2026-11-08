package net.godwand.client;

import net.godwand.client.GateDraw.Ctx;
import net.minecraft.world.phys.Vec3;

import static net.godwand.client.GateDraw.clamp01;
import static net.godwand.client.GateDraw.smooth;

/** Silent Gate beams: camera-facing ribbons with a white core and a fibre-textured sheath, muzzle flash and ground impact. */
final class GateBeams {
    private GateBeams() {}
    private static final double TAU = Math.PI * 2, FIBRE_LENGTH = 28, B = 2;
    static final double LIFE = 8, MUZZLE_TICKS = 6;

    /** 1 tick up, 2 hold, 5 decay. */
    static double envelope(double life) {
        if (life < 0 || life >= LIFE) return 0;
        return life < 1 ? life : life < 3 ? 1 : 1 - (life - 3) / 5;
    }
    private static double hash(long seed, int i) { double x = Math.sin(seed * 12.9898 + i * 78.233) * 43758.5453; return x - Math.floor(x); }

    static void shot(Ctx c, Vec3 from, Vec3 hit, double life, int color, long seed, double now) {
        double env = envelope(life);
        if (env <= 0) return;
        beam(c, from, hit, env, color, now);
        if (c.glow()) { muzzle(c, from, life, color); impact(c, hit, life, color, seed); }
    }

    private static void beam(Ctx c, Vec3 from, Vec3 to, double env, int color, double now) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1e-3) return;
        Vec3 n = d.scale(1 / len);
        // A beam passing close to the camera dissolves, like every other circle element.
        double along = Math.max(0, Math.min(len, c.eye.subtract(from).dot(n)));
        env *= smooth((from.add(n.scale(along)).distanceTo(c.eye) - GateDraw.NEAR_MIN) / (GateDraw.NEAR_MAX - GateDraw.NEAR_MIN));
        if (env <= 0) return;
        Vec3 look = from.add(to).scale(.5).subtract(c.eye);
        Vec3 side = n.cross(look);
        side = side.lengthSqr() < 1e-8 ? n.cross(new Vec3(0, 1, 0)).normalize() : side.normalize();
        if (c.body()) {
            Vec3 w = side.scale(.125 * B);
            GateDraw.quad(c, from.subtract(w), from.add(w), to.add(w), to.subtract(w), GateCircles.HOT, .95 * env);
        }
        if (!c.glow()) return;
        Vec3 half = side.scale(.8 * B);
        // Fibre sheath: energy runs from the circle to the target; each piece spans whole texture periods so nothing wraps inside a quad.
        double scroll = (now * .22) % 1, t0 = 0;
        for (int guard = 0; guard < 40 && t0 < len - 1e-6; guard++) {
            double ph0 = (((t0 / FIBRE_LENGTH - scroll) % 1) + 1) % 1, t1 = Math.min(len, t0 + (1 - ph0) * FIBRE_LENGTH), ph1 = ph0 + (t1 - t0) / FIBRE_LENGTH;
            Vec3 a = from.add(n.scale(t0)), b = from.add(n.scale(t1));
            GateDraw.tex(c, a.subtract(half), b.subtract(half), b.add(half), a.add(half), GateAtlas.FIBRE, ph0, Math.min(1, ph1), 0, 1, color, .55 * env, .55 * env, .55 * env, .55 * env);
            t0 = t1;
        }
        // Soft outer glow, brightest on the axis.
        Vec3 wide = side.scale(1.8 * B);
        double g = .16 * env;
        GateDraw.gradient(c, from.subtract(wide), from, to, to.subtract(wide), color, 0, g, g, 0);
        GateDraw.gradient(c, from, from.add(wide), to.add(wide), to, color, g, 0, 0, g);
    }

    private static void muzzle(Ctx c, Vec3 at, double life, int color) {
        if (life >= MUZZLE_TICKS) return;
        double k = life / MUZZLE_TICKS, a = (1 - k) * .8;
        GateDraw.sprite(c, at, (2 + 5 * k) * B, a, GateCircles.HOT);
        ring(c, at, (1 + 7 * k) * B, 1.1 * B, .6 * (1 - k), color, 8);
    }

    private static void impact(Ctx c, Vec3 hit, double life, int color, long seed) {
        double k = life / LIFE, fade = 1 - k * k;
        Vec3 o = hit.add(0, .12, 0);
        fan(c, o, (3 + 4 * smooth(life / 2)) * B, color, .4 * fade, 8);
        GateDraw.sprite(c, hit.add(0, .6, 0), (1.6 + 1.2 * (1 - k)) * B, .7 * fade, GateCircles.HOT);
        ring(c, o, (.8 + 8 * smooth(life / LIFE)) * B, 1.3 * B, .55 * fade, GateCircles.HOT, 10);
        int sparks = 8 + (int) (hash(seed, 99) * 5);
        for (int i = 0; i < sparks; i++) {
            double az = hash(seed, i) * TAU, speed = 3 + 5 * hash(seed, i + 100), up = 4 + 6 * hash(seed, i + 200);
            Vec3 p = hit.add(Math.cos(az) * speed * life * .5, Math.max(.1, up * life * .5 - 1.4 * life * life), Math.sin(az) * speed * life * .5);
            GateDraw.sprite(c, p, .28 * (1 - k * .6), .9 * (1 - k), i % 3 == 0 ? GateCircles.HOT : color);
        }
    }

    private static void fan(Ctx c, Vec3 o, double radius, int color, double alpha, int n) {
        for (int i = 0; i < n; i++) {
            double a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
            GateDraw.gradient(c, o, o, o.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius), o.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), color, alpha, alpha, 0, 0);
        }
    }
    /** Horizontal soft ring of the given radius and width. */
    static void ring(Ctx c, Vec3 o, double radius, double width, double alpha, int color, int n) {
        if (alpha <= 0) return;
        for (int i = 0; i < n; i++) {
            double a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
            Vec3 i0 = o.add(Math.cos(a0) * (radius - width), 0, Math.sin(a0) * (radius - width)), i1 = o.add(Math.cos(a1) * (radius - width), 0, Math.sin(a1) * (radius - width));
            Vec3 m0 = o.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), m1 = o.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
            Vec3 o0 = o.add(Math.cos(a0) * (radius + width), 0, Math.sin(a0) * (radius + width)), o1 = o.add(Math.cos(a1) * (radius + width), 0, Math.sin(a1) * (radius + width));
            GateDraw.gradient(c, i0, i1, m1, m0, color, 0, 0, alpha, alpha);
            GateDraw.gradient(c, m0, m1, o1, o0, color, alpha, alpha, 0, 0);
        }
    }
}
