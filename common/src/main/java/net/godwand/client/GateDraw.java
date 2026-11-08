package net.godwand.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Quad emitter shared by the Silent Gate geometry: budget, camera-relative vertices, proximity fade and layer selection. */
final class GateDraw {
    private GateDraw() {}

    /** {@code layer}: -1 emits both layers (tests), 0 the alpha-blended body, 1 the additive glow. */
    static final class Ctx {
        VertexConsumer v; Matrix4f m; Vec3 eye; double fade = 1, gain = 1; int layer = -1, quads;
        /** Only the opaque gate body: drawn into the depth buffer alone so additive light behind it cannot shine through. */
        boolean solidOnly;
        boolean body() { return layer <= 0; }
        boolean glow() { return layer != 0; }
    }
    /** Circle geometry is invisible closer than {@value #NEAR_MIN} blocks to the camera and fully there from {@value #NEAR_MAX}. */
    static final double NEAR_MIN = 3, NEAR_MAX = 12;

    static double clamp01(double x) { return Math.max(0, Math.min(1, x)); }
    static double smooth(double x) { x = clamp01(x); return x * x * (3 - 2 * x); }

    /** Opacity factor from the distance of the quad's nearest corner or centre to the camera. */
    static double proximity(Ctx c, Vec3 a, Vec3 b, Vec3 d, Vec3 e) {
        double lo = Math.min(Math.min(a.distanceToSqr(c.eye), b.distanceToSqr(c.eye)), Math.min(d.distanceToSqr(c.eye), e.distanceToSqr(c.eye)));
        lo = Math.min(lo, a.add(b).add(d).add(e).scale(.25).distanceToSqr(c.eye));
        if (lo >= NEAR_MAX * NEAR_MAX) return 1;
        return smooth((Math.sqrt(lo) - NEAR_MIN) / (NEAR_MAX - NEAR_MIN));
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
        if (!room(c)) return;
        double lo = Double.MAX_VALUE;
        for (Vec3 q : p) lo = Math.min(lo, q.distanceToSqr(c.eye));
        double k = c.fade * (lo >= SOLID_NEAR_MAX * SOLID_NEAR_MAX ? 1 : smooth((Math.sqrt(lo) - SOLID_NEAR_MIN) / (SOLID_NEAR_MAX - SOLID_NEAR_MIN)));
        if (k <= 0) return;
        float[] uv = r == null ? new float[]{SOLID_U, SOLID_V, SOLID_U, SOLID_V, SOLID_U, SOLID_V, SOLID_U, SOLID_V}
                : new float[]{r.u(u0), r.v(v0), r.u(u1), r.v(v0), r.u(u1), r.v(v1), r.u(u0), r.v(v1)};
        for (int i = 0; i < 4; i++) vertex(c, p[i], color[i], alpha[i] * k, uv[2 * i], uv[2 * i + 1]);
        c.quads++;
    }
    private static final float SOLID_U = GateAtlas.SOLID_U, SOLID_V = GateAtlas.SOLID_V;

    private static void vertex(Ctx c, Vec3 p, int color, double alpha, float u, float v) {
        Vec3 q = p.subtract(c.eye);
        int opacity = (int) (clamp01(alpha) * 255);
        c.v.addVertex(c.m, (float) q.x, (float) q.y, (float) q.z).setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, opacity).setUv(u, v);
    }

    /** True while another quad fits the budget; callers check before emitting so a vertex run is never cut short. */
    static boolean room(Ctx c) { return c.quads < SilentGateRenderer.QUAD_BUDGET; }
}
