package net.godwand.client;

import net.godwand.client.GateDraw.Ctx;
import net.minecraft.world.phys.Vec3;

import static net.godwand.client.GateDraw.clamp01;
import static net.godwand.client.GateDraw.smooth;

/** Silent Gate beams: camera-facing ribbons with a white core and a fibre-textured sheath, muzzle flash and ground impact. */
final class GateBeams {
    private GateBeams() {}
    private static final double TAU = Math.PI * 2, FIBRE_LENGTH = 48, B = 2;
    static final double LIFE = 16, MUZZLE_TICKS = 6, FALL_TICKS = 2.5;
    /** The kill beam's colour: warm white-gold, so the decisive shot is told from the rain at a glance. */
    static final int KILL = 0xFFE3A6;

    /** 1 tick up, held to 8, 8 to die away. */
    static double envelope(double life) {
        if (life < 0 || life >= LIFE) return 0;
        return life < 1 ? life : life < 8 ? 1 : 1 - (life - 8) / 8;
    }
    private static double hash(long seed, int i) { double x = Math.sin(seed * 12.9898 + i * 78.233) * 43758.5453; return x - Math.floor(x); }

    /** A hit this close to the camera is the subject of a close-up: its beam and impact only dissolve within {@value #CLOSE_NEAR_MAX} blocks of the lens. */
    static final double CLOSE_RANGE = 14, CLOSE_NEAR_MIN = .4, CLOSE_NEAR_MAX = 2;

    static void shot(Ctx c, Vec3 from, Vec3 hit, double life, int color, long seed, double now, double crowd) { shot(c, from, hit, life, color, seed, now, crowd, 1); }

    /** {@code heavy} 1 for an ordinary beam, 2 for the kill beam (twice as thick, a brighter core, a stronger impact). */
    static void shot(Ctx c, Vec3 from, Vec3 hit, double life, int color, long seed, double now, double crowd, double heavy) {
        double env = envelope(life);
        if (env <= 0) return;
        boolean close = hit.distanceTo(c.eye) < CLOSE_RANGE;
        if (close) { c.nearMin = CLOSE_NEAR_MIN; c.nearMax = CLOSE_NEAR_MAX; }
        try { draw(c, from, hit, life, env, color, seed, now, crowd, heavy); }
        finally { c.nearMin = GateDraw.NEAR_MIN; c.nearMax = GateDraw.NEAR_MAX; }
    }

    private static void draw(Ctx c, Vec3 from, Vec3 hit, double life, double env, int color, long seed, double now, double crowd, double heavy) {
        // The beam is shot down: it takes FALL_TICKS to reach the ground, a hot head leading it, and only then does it strike.
        double reach = Math.min(1, life / FALL_TICKS);
        Vec3 tip = from.add(hit.subtract(from).scale(reach));
        // No two beams alike (owner, 2026-11-15: the rain read as even lines): each has its own width and brightness, one in five is a white needle.
        double width = heavy > 1 ? 1 : .55 + .9 * hash(seed, 600);
        boolean needle = heavy == 1 && hash(seed, 601) < .2;
        if (needle) { width = .35; color = 0xFFFFFF; }
        beam(c, from, tip, env * (needle ? 1.3 : .75 + .5 * hash(seed, 602)), color, now, heavy, width);
        if (!c.glow()) return;
        if (heavy > 1) spiral(c, from, tip, env, now);
        muzzle(c, from, life, color);
        if (reach < 1) GateDraw.sprite(c, tip, 2.6 * B * heavy, .95, 0xFFFFFF, GateAtlas.STAR, now * .4);
        else impact(c, hit, life - FALL_TICKS, color, seed, crowd, heavy);
    }

    /** Light winding down the kill beam: two strands of sparks spiralling round its shaft, turning as they fall. */
    private static void spiral(Ctx c, Vec3 from, Vec3 to, double env, double now) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1) return;
        Vec3 n = d.scale(1 / len), u = n.cross(new Vec3(1, 0, 0)).normalize(), v = n.cross(u);
        for (int strand = 0; strand < 2; strand++) for (int i = 0; i < 48; i++) {
            double t = ((i + now * .9) % 48) / 48, a = t * len * .09 - now * .5 + strand * Math.PI, r = (2.6 + 1.2 * Math.sin(t * 9 + now * .2)) * B;
            Vec3 p = from.add(n.scale(t * len)).add(u.scale(Math.cos(a) * r)).add(v.scale(Math.sin(a) * r));
            GateDraw.sprite(c, p, (.5 + .4 * (i % 3)) * B * .5, .9 * env * Math.sin(Math.PI * t), i % 2 == 0 ? 0xFFFFFF : KILL, GateAtlas.STAR, a);
        }
    }

    private static void beam(Ctx c, Vec3 from, Vec3 to, double env, int color, double now, double heavy, double width) {
        boolean kill = heavy > 1;
        heavy *= width;   // every width below scales with the beam's own
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
            Vec3 wa = side.scale(allowed(c, from, .2 * B * heavy, CLOSE_CORE)), wb = side.scale(allowed(c, to, .2 * B * heavy, CLOSE_CORE));
            GateDraw.quad(c, from.subtract(wa), from.add(wa), to.add(wb), to.subtract(wb), GateCircles.HOT, .95 * env * (.5 + .5 * dim));
        }
        if (!c.glow()) return;
        if (kill) {
            // The kill beam: a second, wider white layer round the core, bright on the axis and gone at its edges (a flat quad read as a grey slab).
            Vec3 xa = side.scale(allowed(c, from, .8 * B * heavy, CLOSE_SHEATH)), xb = side.scale(allowed(c, to, .8 * B * heavy, CLOSE_SHEATH));
            double w = .8 * env * (.5 + .5 * dim);
            GateDraw.gradient(c, from.subtract(xa), from, to, to.subtract(xb), GateCircles.HOT, 0, w, w, 0);
            GateDraw.gradient(c, from, from.add(xa), to.add(xb), to, GateCircles.HOT, w, 0, 0, w);
        }
        env *= dim;
        // A body of light round the core, brightest on the axis, and fine rays running down it: the beam is a shaft, not a line. It thins as it dies.
        double full = .45 + .55 * env;
        Vec3 ba = side.scale(allowed(c, from, .6 * B * heavy * full, CLOSE_SHEATH)), bb = side.scale(allowed(c, to, .6 * B * heavy * full, CLOSE_SHEATH));
        GateDraw.gradient(c, from.subtract(ba), from, to, to.subtract(bb), 0xFFFFFF, 0, .42 * env, .42 * env, 0);
        GateDraw.gradient(c, from, from.add(ba), to.add(bb), to, 0xFFFFFF, .42 * env, 0, 0, .42 * env);
        GateDraw.tex(c, from.subtract(ba.scale(2.2)), from.add(ba.scale(2.2)), to.add(bb.scale(2.2)), to.subtract(bb.scale(2.2)), GateAtlas.RAYS, 0, 1, (now * .05) % .5, (now * .05) % .5 + .5,
                color, .4 * env, .4 * env, .4 * env, .4 * env);
        // Fibre sheath: energy runs from the circle to the target; each piece spans whole texture periods so nothing wraps inside a quad.
        double scroll = (now * .22) % 1, t0 = 0;
        // The fibres are fine detail: beyond 200 blocks they cannot be seen.
        for (int guard = 0; guard < 40 && t0 < len - 1e-6 && pass < 200; guard++) {
            double ph0 = (((t0 / FIBRE_LENGTH - scroll) % 1) + 1) % 1, t1 = Math.min(len, t0 + (1 - ph0) * FIBRE_LENGTH), ph1 = ph0 + (t1 - t0) / FIBRE_LENGTH;
            Vec3 a = from.add(n.scale(t0)), b = from.add(n.scale(t1));
            Vec3 ha = side.scale(allowed(c, a, 1.1 * B * heavy, CLOSE_SHEATH)), hb = side.scale(allowed(c, b, 1.1 * B * heavy, CLOSE_SHEATH));
            GateDraw.tex(c, a.subtract(ha), b.subtract(hb), b.add(hb), a.add(ha), GateAtlas.FIBRE, ph0, Math.min(1, ph1), 0, 1, color, .55 * env, .55 * env, .55 * env, .55 * env);
            t0 = t1;
        }
        // Soft outer glow, brightest on the axis.
        Vec3 wf = side.scale(allowed(c, from, 2.2 * B * heavy, CLOSE_SHEATH)), wt = side.scale(allowed(c, to, 2.2 * B * heavy, CLOSE_SHEATH));
        double g = .2 * env;
        GateDraw.gradient(c, from.subtract(wf), from, to, to.subtract(wt), color, 0, g, g, 0);
        GateDraw.gradient(c, from, from.add(wf), to.add(wt), to, color, g, 0, 0, g);
    }

    /** Within {@value #CLOSE_RADIUS} blocks of the camera a ribbon is at most 2 * these half widths across (sheath 1.5 blocks); it grows back to full size over the next {@value #CLOSE_RADIUS}. */
    static final double CLOSE_RADIUS = 20, CLOSE_SHEATH = .75, CLOSE_CORE = .1, CLOSE_DIM = .3;

    /** Half width of a ribbon of nominal half width {@code full} at {@code p}, limited near the camera to {@code close}. */
    static double allowed(Ctx c, Vec3 p, double full, double close) {
        return Math.min(full, close + (full - close) * smooth((p.distanceTo(c.eye) - CLOSE_RADIUS) / CLOSE_RADIUS));
    }

    /** The kill beam's circle gathering light, {@code k} 0..1: a ring that closes in, glints drawn to the nozzle, and a core that swells and whitens. */
    static void charge(Ctx c, Vec3 at, double k) {
        if (!c.glow() || k <= 0) return;
        GateDraw.sprite(c, at, (1.5 + 6 * k) * B, .2 + .6 * k, KILL);
        GateDraw.sprite(c, at, (.8 + 2.2 * k) * B, .95, 0xFFFFFF, GateAtlas.STAR, k * 4);
        ring(c, at, (10 - 9 * k) * B, 1.2 * B, .7 * k, KILL, 16);
        for (int i = 0; i < 12; i++) {
            double in = (hash(7, i) + k * 2.5) % 1, ang = hash(11, i) * TAU, r = (1 - in) * 9 * B;
            GateDraw.sprite(c, at.add(Math.cos(ang) * r, (hash(13, i) - .5) * r * .6, Math.sin(ang) * r), .5 * B * (.4 + .6 * in), .9 * in, GateCircles.HOT, GateAtlas.STAR, ang);
        }
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
    private static void impact(Ctx c, Vec3 hit, double life, int color, long seed, double crowd, double heavy) {
        double k = life / LIFE, fade = (1 - k * k) * crowd;
        // An impact filling the lens (close-up) keeps its flash but its wide additive pieces shrink and dim.
        double near = (.35 + .65 * smooth((hit.distanceTo(c.eye) - 4) / 24)) * (1 + .6 * (heavy - 1));
        Vec3 o = hit.add(0, .12, 0);
        fan(c, o, (3 + 4 * smooth(life / 2)) * I * near, color, .35 * fade * near, 12);
        // A seal written on the ground where the beam lands: it opens out, turning, and fades.
        double sr = (.7 + .9 * smooth(life / 3)) * I * near * (128 / 122.0), sa = hash(seed, 77) * TAU + life * .12, cs = Math.cos(sa) * sr, sn = Math.sin(sa) * sr;
        GateDraw.tex(c, o.add(-cs + sn, .05, -sn - cs), o.add(cs + sn, .05, sn - cs), o.add(cs - sn, .05, sn + cs), o.add(-cs - sn, .05, -sn + cs),
                GateAtlas.SEAL, 0, 1, 0, 1, heavy > 1 ? GateCircles.HOT : color, .9 * fade, .9 * fade, .9 * fade, .9 * fade);
        // Short bright flash on the first ticks, and a column of light where the beam lands.
        if (life < 2.5) GateDraw.sprite(c, hit.add(0, 1.5, 0), (1 + 2 * (1 - life / 2.5)) * B * (.5 + .5 * near), .7 * crowd * (1 - life / 2.5), GateCircles.HOT);
        GateDraw.sprite(c, hit.add(0, 1.2, 0), (.9 + .7 * (1 - k)) * I * near, .7 * fade * near, GateCircles.HOT);
        column(c, hit, .8 * B * (1 - .5 * k) * near, 18 * B, color, .3 * fade * near);
        ring(c, o, (.8 + 8 * smooth(life / LIFE)) * I * near, 1.3 * I * near, .6 * fade * near, GateCircles.HOT, 12);
        // The kill beam's blow runs out over the ground as a wide ring of gold.
        if (heavy > 1) ring(c, o.add(0, .1, 0), (2 + 30 * smooth(life / 10)) * I, 1.8 * I, .8 * (1 - k), KILL, 48);
        int sparks = 16 + (int) (hash(seed, 99) * 8);
        for (int i = 0; i < sparks; i++) {
            double az = hash(seed, i) * TAU, speed = 3 + 5 * hash(seed, i + 100), up = 4 + 6 * hash(seed, i + 200);
            Vec3 p = hit.add(Math.cos(az) * speed * life * .9, Math.max(.1, up * life * .9 - 1.4 * life * life), Math.sin(az) * speed * life * .9);
            GateDraw.sprite(c, p, .55 * (1 - k * .6), .95 * (1 - k), i % 3 == 0 ? GateCircles.HOT : color);
        }
    }

    /** Camera-facing column of light standing on {@code base}, rayed, fading upwards. */
    private static void column(Ctx c, Vec3 base, double halfWidth, double height, int color, double alpha) {
        GateDraw.shaft(c, base, base.add(0, height, 0), halfWidth, halfWidth * 1.4, color, 1.3 * alpha, 0, base.x + base.z);
    }

    /** Sparks thrown up where a beam strikes: a handful of streaks on ballistic arcs. */
    static final double SPARK_TICKS = 14;
    static void sparks(Ctx c, Vec3 hit, double life, int color, long seed, double heavy) {
        if (life < 0 || life >= SPARK_TICKS || !c.glow()) return;
        int n = heavy > 1 ? 12 : 4;
        for (int i = 0; i < n; i++) {
            double az = hash(seed, 400 + i) * TAU, up = .35 + .65 * hash(seed, 420 + i), v = (.3 + .34 * hash(seed, 440 + i)) * B * heavy;
            GateDraw.shaft(c, arc(hit, az, up, v, life), arc(hit, az, up, v, Math.max(0, life - 1.8)), .1 * B, .02, i % 2 == 0 ? 0xFFFFFF : color, .9 * (1 - life / SPARK_TICKS), 0, i);
        }
    }
    private static Vec3 arc(Vec3 hit, double az, double up, double v, double t) {
        double flat = Math.sqrt(1 - .6 * up * up) * v * t;
        return hit.add(Math.cos(az) * flat, up * v * t - .03 * t * t, Math.sin(az) * flat);
    }

    /** How long the smoke of an impact hangs in the air after the beam is gone. */
    static final double SMOKE_TICKS = 40;
    /** Smoke rising where a beam struck, {@code life} ticks after it fired: a few puffs that swell, drift up and thin out; dark at first, lit by the beam's colour. */
    static void smoke(Ctx c, Vec3 hit, double life, int color, long seed, double heavy) {
        if (life < 1 || life >= SMOKE_TICKS || !c.glow() && !c.shade()) return;
        double k = life / SMOKE_TICKS, fade = Math.sin(Math.PI * Math.pow(k, .6));
        for (int i = 0; i < 3; i++) {
            double az = hash(seed, 300 + i) * TAU, out = (.5 + 1.6 * k) * B * heavy;
            Vec3 at = hit.add(Math.cos(az) * out, .6 + (2 + 5 * hash(seed, 310 + i)) * k * B * .7, Math.sin(az) * out);
            double size = (1.4 + 3.2 * k) * B * heavy, roll = hash(seed, 320 + i) * TAU + k * 1.5;
            if (c.shade()) GateDraw.sprite(c, at, size, .4 * fade, 0xFFFFFF, GateAtlas.SMOKE, roll);
            if (c.glow()) GateDraw.sprite(c, at, size * .9, .1 * fade * (1 - k), color, GateAtlas.SMOKE, roll);
        }
    }

    /** How long the ground stays lit and marked where a beam struck (within the client's {@link SilentGateRenderer#SHOT_MEMORY_TICKS}). */
    static final double POOL_TICKS = 42;
    /** The ground where a beam struck (owner, 2026-11-15: the land stayed dark and flat): a wide pool of the beam's light thrown over it that flares and ebbs,
     *  a smouldering glow at its heart that flickers down slowly, and a burnt mark (shade layer) that darkens the ground round it, so the field flickers with
     *  the rain and keeps its scars for a while. {@code life} ticks since the beam reached the ground. */
    static void pool(Ctx c, Vec3 hit, double life, int color, long seed, double heavy) {
        if (life < 0 || life >= POOL_TICKS) return;
        // Far off a pool is a few pixels: it spreads wider with distance, so the whole field is seen to flicker from the shots that look across it.
        double k = life / POOL_TICKS, s = (heavy > 1 ? 2.2 : 1) * (1 + clamp01((hit.distanceTo(c.eye) - 40) / 160) * 1.5);
        Vec3 o = hit.add(0, .1 + .02 * hash(seed, 500), 0);
        if (c.glow()) {
            // The pool: it opens in two ticks to its full width and ebbs over a second, white at first and the beam's colour as it fades.
            double spread = (7 + 7 * smooth(life / 2)) * s * B, flare = Math.exp(-life / 9) * (life < 1 ? life : 1);
            disc(c, o, spread, color, flare);
            disc(c, o, spread * .45, 0xFFFFFF, .8 * flare * Math.exp(-life / 4));
            // Seen from low down a flat pool is a thin line: the air just over the ground is lit too, a low haze standing on the strike.
            GateDraw.sprite(c, o.add(0, 1.2 * s * B, 0), 4.5 * s * B, 1.4 * flare, color);
            // The ember: small, hot, flickering, and slow to go out; a glow over it so it reads from the side.
            double flicker = .75 + .25 * Math.sin(life * (1.7 + hash(seed, 501)) + hash(seed, 502) * TAU), ember = (1 - k) * (1 - k) * flicker;
            disc(c, o, 1.6 * s * B, GateCircles.HOT, .8 * ember);
            GateDraw.sprite(c, o.add(0, .4 * s * B, 0), 1.3 * s * B, 1.2 * ember, GateCircles.HOT);
        }
        // The scorch: a dark ring of burnt ground that spreads with the blast and stays until the pool has gone.
        if (c.shade()) disc(c, o.add(0, -.02, 0), (1.2 + 1.2 * smooth(life / 3)) * s * B, 0xFFFFFF, .45 * smooth(life / 2) * (1 - smooth((k - .6) / .4)));
    }
    /** A flat soft disc of light (the glow tile) lying on the ground. */
    private static void disc(Ctx c, Vec3 o, double radius, int color, double alpha) {
        if (alpha <= .005) return;
        GateDraw.tex(c, o.add(-radius, 0, -radius), o.add(radius, 0, -radius), o.add(radius, 0, radius), o.add(-radius, 0, radius),
                GateAtlas.GLOW, 0, 1, 0, 1, color, alpha, alpha, alpha, alpha);
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
