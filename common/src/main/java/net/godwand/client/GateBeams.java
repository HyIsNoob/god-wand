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

    /** A hit this close to the camera is the subject of a close-up: its beam and impact only dissolve within {@value #CLOSE_NEAR_MAX} blocks of the lens. */
    static final double CLOSE_RANGE = 14, CLOSE_NEAR_MIN = .4, CLOSE_NEAR_MAX = 2;

    static void shot(Ctx c, Vec3 from, Vec3 hit, double life, int color, long seed, double now, double crowd) {
        double env = envelope(life);
        if (env <= 0) return;
        boolean close = hit.distanceTo(c.eye) < CLOSE_RANGE;
        if (close) { c.nearMin = CLOSE_NEAR_MIN; c.nearMax = CLOSE_NEAR_MAX; }
        try { draw(c, from, hit, life, env, color, seed, now, crowd); }
        finally { c.nearMin = GateDraw.NEAR_MIN; c.nearMax = GateDraw.NEAR_MAX; }
    }

    private static void draw(Ctx c, Vec3 from, Vec3 hit, double life, double env, int color, long seed, double now, double crowd) {
        beam(c, from, hit, env, color, now);
        if (c.glow()) { muzzle(c, from, life, color); impact(c, hit, life, color, seed, crowd); }
    }

    private static void beam(Ctx c, Vec3 from, Vec3 to, double env, int color, double now) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1e-3) return;
        Vec3 n = d.scale(1 / len);
        // A beam passing close to the camera dissolves, like every other circle element.
        double along = Math.max(0, Math.min(len, c.eye.subtract(from).dot(n)));
        env *= smooth((from.add(n.scale(along)).distanceTo(c.eye) - c.nearMin) / (c.nearMax - c.nearMin));
        if (env <= 0) return;
        Vec3 look = from.add(to).scale(.5).subtract(c.eye);
        Vec3 side = n.cross(look);
        side = side.lengthSqr() < 1e-8 ? n.cross(new Vec3(0, 1, 0)).normalize() : side.normalize();
        // Near the lens a beam must not whiten the frame: its additive light is scaled by how close the beam passes, and every ribbon
        // is tapered to the width allowed at each end's distance (core thinner, sheath at most CLOSE_WIDTH wide within CLOSE_RADIUS blocks).
        double pass = from.add(n.scale(along)).distanceTo(c.eye), dim = CLOSE_DIM + (1 - CLOSE_DIM) * smooth((pass - CLOSE_RADIUS) / CLOSE_RADIUS);
        if (c.body()) {
            Vec3 wa = side.scale(allowed(c, from, .2 * B, CLOSE_CORE)), wb = side.scale(allowed(c, to, .2 * B, CLOSE_CORE));
            GateDraw.quad(c, from.subtract(wa), from.add(wa), to.add(wb), to.subtract(wb), GateCircles.HOT, .95 * env * (.5 + .5 * dim));
        }
        if (!c.glow()) return;
        env *= dim;
        // Fibre sheath: energy runs from the circle to the target; each piece spans whole texture periods so nothing wraps inside a quad.
        double scroll = (now * .22) % 1, t0 = 0;
        for (int guard = 0; guard < 40 && t0 < len - 1e-6; guard++) {
            double ph0 = (((t0 / FIBRE_LENGTH - scroll) % 1) + 1) % 1, t1 = Math.min(len, t0 + (1 - ph0) * FIBRE_LENGTH), ph1 = ph0 + (t1 - t0) / FIBRE_LENGTH;
            Vec3 a = from.add(n.scale(t0)), b = from.add(n.scale(t1));
            Vec3 ha = side.scale(allowed(c, a, 1.1 * B, CLOSE_SHEATH)), hb = side.scale(allowed(c, b, 1.1 * B, CLOSE_SHEATH));
            GateDraw.tex(c, a.subtract(ha), b.subtract(hb), b.add(hb), a.add(ha), GateAtlas.FIBRE, ph0, Math.min(1, ph1), 0, 1, color, .55 * env, .55 * env, .55 * env, .55 * env);
            t0 = t1;
        }
        // Soft outer glow, brightest on the axis.
        Vec3 wf = side.scale(allowed(c, from, 2.2 * B, CLOSE_SHEATH)), wt = side.scale(allowed(c, to, 2.2 * B, CLOSE_SHEATH));
        double g = .16 * env;
        GateDraw.gradient(c, from.subtract(wf), from, to, to.subtract(wt), color, 0, g, g, 0);
        GateDraw.gradient(c, from, from.add(wf), to.add(wt), to, color, g, 0, 0, g);
    }

    /** Within {@value #CLOSE_RADIUS} blocks of the camera a ribbon is at most 2 * these half widths across (sheath 1.5 blocks); it grows back to full size over the next {@value #CLOSE_RADIUS}. */
    static final double CLOSE_RADIUS = 20, CLOSE_SHEATH = .75, CLOSE_CORE = .1, CLOSE_DIM = .3;

    /** Half width of a ribbon of nominal half width {@code full} at {@code p}, limited near the camera to {@code close}. */
    static double allowed(Ctx c, Vec3 p, double full, double close) {
        return Math.min(full, close + (full - close) * smooth((p.distanceTo(c.eye) - CLOSE_RADIUS) / CLOSE_RADIUS));
    }

    private static void muzzle(Ctx c, Vec3 at, double life, int color) {
        if (life >= MUZZLE_TICKS) return;
        double k = life / MUZZLE_TICKS, a = (1 - k) * .8;
        GateDraw.sprite(c, at, (2 + 5 * k) * B, a, GateCircles.HOT);
        ring(c, at, (1 + 7 * k) * B, 1.1 * B, .6 * (1 - k), color, 8);
    }

    /** Impact scale: twice the old size, for the 3x world and the 5 block close-up. */
    private static final double I = 2 * B;

    /** {@code crowd}: 1 for a lone hit, less when many hits stack on one spot (a boss under the whole barrage), so the sum stays readable instead of white. */
    private static void impact(Ctx c, Vec3 hit, double life, int color, long seed, double crowd) {
        double k = life / LIFE, fade = (1 - k * k) * crowd;
        // An impact filling the lens (close-up) keeps its flash but its wide additive pieces shrink and dim.
        double near = .35 + .65 * smooth((hit.distanceTo(c.eye) - 4) / 24);
        Vec3 o = hit.add(0, .12, 0);
        fan(c, o, (3 + 4 * smooth(life / 2)) * I * near, color, .35 * fade * near, 12);
        // Short bright flash on the first ticks, and a column of light where the beam lands.
        if (life < 2.5) GateDraw.sprite(c, hit.add(0, 1.5, 0), (1 + 2 * (1 - life / 2.5)) * B * (.5 + .5 * near), .7 * crowd * (1 - life / 2.5), GateCircles.HOT);
        GateDraw.sprite(c, hit.add(0, 1.2, 0), (.9 + .7 * (1 - k)) * I * near, .7 * fade * near, GateCircles.HOT);
        column(c, hit, .8 * B * (1 - .5 * k) * near, 18 * B, color, .3 * fade * near);
        ring(c, o, (.8 + 8 * smooth(life / LIFE)) * I * near, 1.3 * I * near, .6 * fade * near, GateCircles.HOT, 12);
        int sparks = 16 + (int) (hash(seed, 99) * 8);
        for (int i = 0; i < sparks; i++) {
            double az = hash(seed, i) * TAU, speed = 3 + 5 * hash(seed, i + 100), up = 4 + 6 * hash(seed, i + 200);
            Vec3 p = hit.add(Math.cos(az) * speed * life * .9, Math.max(.1, up * life * .9 - 1.4 * life * life), Math.sin(az) * speed * life * .9);
            GateDraw.sprite(c, p, .55 * (1 - k * .6), .95 * (1 - k), i % 3 == 0 ? GateCircles.HOT : color);
        }
    }

    /** Camera-facing column of light standing on {@code base}, fading upwards. */
    private static void column(Ctx c, Vec3 base, double halfWidth, double height, int color, double alpha) {
        Vec3 side = new Vec3(0, 1, 0).cross(base.subtract(c.eye));
        if (side.lengthSqr() < 1e-8 || alpha <= 0) return;
        side = side.normalize().scale(halfWidth);
        Vec3 top = base.add(0, height, 0);
        GateDraw.gradient(c, base.subtract(side), base, top, top.subtract(side), color, 0, alpha, 0, 0);
        GateDraw.gradient(c, base, base.add(side), top.add(side), top, color, alpha, 0, 0, 0);
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
