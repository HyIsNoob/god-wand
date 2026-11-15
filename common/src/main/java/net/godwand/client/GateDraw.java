package net.godwand.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Quad emitter shared by the Silent Gate geometry: budget, camera-relative vertices, proximity fade and layer selection. */
final class GateDraw {
    private GateDraw() {}

    /** {@code layer}: -1 emits every layer (tests), 0 the alpha-blended body, 1 the additive glow, 2 the shade: soft darkening (smoke, storm cloud, the dark of the
     *  sky). The shade is drawn like the glow (see {@link #premultiplied}) but multiplies what is behind it down, so it has no alpha cut-off and no hard edge;
     *  its "colour" is what it takes away (white darkens evenly, a warm colour leaves the blue). */
    static final class Ctx {
        VertexConsumer v; Matrix4f m; Vec3 eye; double fade = 1, gain = 1, nearMin = NEAR_MIN, nearMax = NEAR_MAX; int layer = -1, quads;
        /** Only the opaque gate body: drawn into the depth buffer alone so additive light behind it cannot shine through. */
        boolean solidOnly;
        /** The stamp drawn before the body (so the body shows only its nearest faces and does not look hollow): a fading gate is left out of it. */
        boolean beforeBody;
        /** World height above which nothing is drawn yet (the gate coming down out of the crack in the sky), fading in over {@value #CLIP_BAND} blocks under it. */
        double clipY = Double.MAX_VALUE;
        boolean body() { return layer <= 0; }
        boolean glow() { return layer == 1 || layer < 0; }
        boolean shade() { return layer == 2 || layer < 0; }
    }
    /** Circle geometry is invisible closer than {@value #NEAR_MIN} blocks to the camera and fully there from {@value #NEAR_MAX}. */
    static final double NEAR_MIN = 3, NEAR_MAX = 12;

    static double clamp01(double x) { return Math.max(0, Math.min(1, x)); }
    static double smooth(double x) { x = clamp01(x); return x * x * (3 - 2 * x); }

    /** Opacity factor from the distance of the quad's nearest corner or centre to the camera. */
    static double proximity(Ctx c, Vec3 a, Vec3 b, Vec3 d, Vec3 e) {
        double lo = Math.min(Math.min(a.distanceToSqr(c.eye), b.distanceToSqr(c.eye)), Math.min(d.distanceToSqr(c.eye), e.distanceToSqr(c.eye)));
        lo = Math.min(lo, a.add(b).add(d).add(e).scale(.25).distanceToSqr(c.eye));
        if (lo >= c.nearMax * c.nearMax) return 1;
        return smooth((Math.sqrt(lo) - c.nearMin) / (c.nearMax - c.nearMin));
    }

    /** Flat-colour quad (solid atlas texel). */
    static void quad(Ctx c, Vec3 a, Vec3 b, Vec3 d, Vec3 e, int color, double alpha) {
        quad(c, a, b, d, e, color, alpha, false);
    }
    static void quad(Ctx c, Vec3 a, Vec3 b, Vec3 d, Vec3 e, int color, double alpha, boolean near) {
        if (!room(c)) return;
        double k = alpha * c.fade * c.gain * (near ? proximity(c, a, b, d, e) : 1);
        vertex(c, a, color, k, GateAtlas.SOLID_U, GateAtlas.SOLID_V); vertex(c, b, color, k, GateAtlas.SOLID_U, GateAtlas.SOLID_V);
        vertex(c, d, color, k, GateAtlas.SOLID_U, GateAtlas.SOLID_V); vertex(c, e, color, k, GateAtlas.SOLID_U, GateAtlas.SOLID_V);
        c.quads++;
    }
    /** Quad with an alpha per corner on the solid texel: gradients (halos, cones). Always proximity-faded. */
    /** A soft halo of light round a point (owner, 2026-11-15: circles and particles "need more glow", with or without a shaderpack, which turns the bloom off). */
    static void halo(Ctx c, Vec3 at, double size, double alpha, int color) { sprite(c, at, size, alpha, color, GateAtlas.GLOW, 0); }
    static void gradient(Ctx c, Vec3 a, Vec3 b, Vec3 d, Vec3 e, int color, double aa, double ab, double ad, double ae) {
        if (!room(c)) return;
        double k = c.fade * proximity(c, a, b, d, e);
        vertex(c, a, color, aa * k, GateAtlas.SOLID_U, GateAtlas.SOLID_V); vertex(c, b, color, ab * k, GateAtlas.SOLID_U, GateAtlas.SOLID_V);
        vertex(c, d, color, ad * k, GateAtlas.SOLID_U, GateAtlas.SOLID_V); vertex(c, e, color, ae * k, GateAtlas.SOLID_U, GateAtlas.SOLID_V);
        c.quads++;
    }
    /**
     * Textured quad: corners a (u0,v0), b (u1,v0), d (u1,v1), e (u0,v1) of {@code r}, per-corner alpha, proximity-faded.
     * Phase and band coordinates are 0..1 inside the region.
     */
    static void tex(Ctx c, Vec3 a, Vec3 b, Vec3 d, Vec3 e, GateAtlas.Region r, double u0, double u1, double v0, double v1,
                    int color, double aa, double ab, double ad, double ae) {
        if (!room(c)) return;
        double k = c.fade * proximity(c, a, b, d, e);
        float fu0 = r.u(u0), fu1 = r.u(u1), fv0 = r.v(v0), fv1 = r.v(v1);
        vertex(c, a, color, aa * k, fu0, fv0); vertex(c, b, color, ab * k, fu1, fv0);
        vertex(c, d, color, ad * k, fu1, fv1); vertex(c, e, color, ae * k, fu0, fv1);
        c.quads++;
    }
    /** A shaft of light from {@code a} to {@code b}, camera-facing, {@code wa}/{@code wb} half wide at its ends: a soft body, brightest on the axis, and over it a
     *  faint layer of fine rays (the streak tile, drifting sideways with {@code phase}). The rays are kept faint: strong, they read as a pasted picture of a
     *  light pillar (owner, 2026-11-14). */
    static void shaft(Ctx c, Vec3 a, Vec3 b, double wa, double wb, int color, double alphaA, double alphaB, double phase) {
        Vec3 side = b.subtract(a).cross(a.add(b).scale(.5).subtract(c.eye));
        if (side.lengthSqr() < 1e-8 || alphaA <= 0 && alphaB <= 0) return;
        side = side.normalize();
        Vec3 sa = side.scale(wa), sb = side.scale(wb);
        gradient(c, a.subtract(sa), a, b, b.subtract(sb), color, 0, .7 * alphaA, .7 * alphaB, 0);
        gradient(c, a, a.add(sa), b.add(sb), b, color, .7 * alphaA, 0, 0, .7 * alphaB);
        double v0 = .12 * Math.sin(phase), v1 = 1 - .12 + v0;
        tex(c, a.subtract(sa), a.add(sa), b.add(sb), b.subtract(sb), GateAtlas.RAYS, Math.max(0, v0), Math.min(1, v1), 0, 1, color, .3 * alphaA, .3 * alphaA, .3 * alphaB, .3 * alphaB);
    }

    /** A streamer of vapour or light from {@code a} to {@code b}: pointed at both ends, {@code half} wide at its middle, camera-facing, drawn with the wisp tile;
     *  {@code bend} bows it sideways (in half widths). */
    static void streamer(Ctx c, Vec3 a, Vec3 b, double half, int color, double alpha, double bend) {
        Vec3 axis = b.subtract(a), side = axis.cross(a.add(b).scale(.5).subtract(c.eye));
        if (side.lengthSqr() < 1e-8 || alpha <= 0) return;
        side = side.normalize();
        int n = 5;
        Vec3 pl = null, pr = null;
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n, w = half * Math.pow(Math.sin(Math.PI * t), .8);
            Vec3 m = a.add(axis.scale(t)).add(side.scale(bend * half * Math.sin(Math.PI * t)));
            Vec3 l = m.subtract(side.scale(w)), r = m.add(side.scale(w));
            if (i > 0) tex(c, pl, l, r, pr, GateAtlas.WISP, t - 1.0 / n, t, 0, 1, color, alpha, alpha, alpha, alpha);
            pl = l; pr = r;
        }
    }

    /** Camera-facing soft sprite from the glow tile. */
    static void sprite(Ctx c, Vec3 at, double size, double alpha, int color) { sprite(c, at, size, alpha, color, GateAtlas.GLOW); }
    static void sprite(Ctx c, Vec3 at, double size, double alpha, int color, GateAtlas.Region region) { sprite(c, at, size, alpha, color, region, 0); }
    static void sprite(Ctx c, Vec3 at, double size, double alpha, int color, GateAtlas.Region region, double roll) {
        Vec3 look = at.subtract(c.eye);
        if (look.lengthSqr() < 1e-6 || alpha <= 0) return;
        Vec3 right = look.cross(new Vec3(0, 1, 0)), up;
        right = (right.lengthSqr() < 1e-8 ? new Vec3(1, 0, 0) : right.normalize()).scale(size);
        up = right.cross(look).normalize().scale(size);
        if (roll != 0) { Vec3 r0 = right; right = r0.scale(Math.cos(roll)).add(up.scale(Math.sin(roll))); up = up.scale(Math.cos(roll)).subtract(r0.scale(Math.sin(roll))); }
        tex(c, at.subtract(right).subtract(up), at.add(right).subtract(up), at.add(right).add(up), at.subtract(right).add(up),
                region, 0, 1, 0, 1, color, alpha, alpha, alpha, alpha);
    }

    /** Big solid pieces (the gate) only fade when the camera is almost inside them: {@value #SOLID_NEAR_MIN}..{@value #SOLID_NEAR_MAX} blocks. */
    static final double SOLID_NEAR_MIN = 1.2, SOLID_NEAR_MAX = 5;

    /**
     * Quad with a colour and alpha per corner (fake shading, rim light); textured from {@code r} when it is not null.
     * Corners a (u0,v0), b (u1,v0), d (u1,v1), e (u0,v1).
     */
    static void shaded(Ctx c, Vec3[] p, int[] color, double[] alpha, GateAtlas.Region r, double u0, double u1, double v0, double v1) {
        float[] uv = r == null ? new float[]{SOLID_U, SOLID_V, SOLID_U, SOLID_V, SOLID_U, SOLID_V, SOLID_U, SOLID_V}
                : new float[]{r.u(u0), r.v(v0), r.u(u1), r.v(v0), r.u(u1), r.v(v1), r.u(u0), r.v(v1)};
        shaded(c, p, color, alpha, uv);
    }

    /** As above with explicit texture coordinates (u, v per corner): the pixel-art tiles of the gate. */
    static void shaded(Ctx c, Vec3[] p, int[] color, double[] alpha, float[] uv) {
        if (!room(c)) return;
        double lo = Double.MAX_VALUE;
        for (Vec3 q : p) lo = Math.min(lo, q.distanceToSqr(c.eye));
        double k = c.fade * (lo >= SOLID_NEAR_MAX * SOLID_NEAR_MAX ? 1 : smooth((Math.sqrt(lo) - SOLID_NEAR_MIN) / (SOLID_NEAR_MAX - SOLID_NEAR_MIN)));
        if (k <= 0) return;
        for (int i = 0; i < 4; i++) vertex(c, p[i], color[i], alpha[i] * k, uv[2 * i], uv[2 * i + 1]);
        c.quads++;
    }
    private static final float SOLID_U = GateAtlas.SOLID_U, SOLID_V = GateAtlas.SOLID_V;
    static final double CLIP_BAND = 6;

    /** Set by the renderer for its additive pass: the vanilla position_tex_color shader (up to 1.21.1) discards every fragment under alpha 0.1, which
     *  cut each soft glow into a hard-edged disc. With this on, the alpha goes into the colour and the vertex is opaque (drawn over {@link GateAtlas#bindGlow}). */
    static boolean premultiplied;

    /** Far plane of the view in blocks (set by the renderer). The gate and its circle reach 700 blocks out; at a short render distance that is past the far plane. */
    static double farPlane = Double.MAX_VALUE;
    /** A camera-relative point beyond 70% of the far plane is pulled in along its own line of sight to just inside it: it stays on the same pixel and in the same
     *  depth order, so the picture is unchanged and nothing is clipped. */
    static Vec3 pull(Vec3 q, double far) {
        double d = q.length(), keep = .7 * far, span = .28 * far;
        return d <= keep ? q : q.scale((keep + span * (1 - Math.exp(-(d - keep) / span))) / d);
    }

    private static void vertex(Ctx c, Vec3 p, int color, double alpha, float u, float v) {
        Vec3 q = pull(p.subtract(c.eye), farPlane);
        // Above the crack in the sky nothing of the gate exists yet: stone, glow, vapour and feathers alike fade out over CLIP_BAND blocks under it.
        if (c.clipY != Double.MAX_VALUE) alpha *= clamp01((c.clipY - p.y) / CLIP_BAND);
        double a = clamp01(alpha), k = premultiplied ? a : 1;
        c.v.addVertex(c.m, (float) q.x, (float) q.y, (float) q.z)
                .setColor((int) (((color >> 16) & 255) * k), (int) (((color >> 8) & 255) * k), (int) ((color & 255) * k), premultiplied ? 255 : (int) (a * 255)).setUv(u, v);
    }

    /** True while another quad fits the budget; callers check before emitting so a vertex run is never cut short. */
    static boolean room(Ctx c) { return c.quads < SilentGateRenderer.QUAD_BUDGET; }
}
