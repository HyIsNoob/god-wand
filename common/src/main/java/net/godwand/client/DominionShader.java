package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.godwand.GodWandMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Custom GLSL core shader & TRUE 3D volumetric mesh emitters for Block Dominion
 * (assets/minecraft/shaders/core/godwand_dominion.*).
 * Builds real 3D Fresnel shells (Mach cones, curved kinetic domes), world-space ground light pools,
 * seam-free aerodynamic contrail ribbons, fine electric filaments, and atmospheric micro-particles —
 * zero flat PNG stickers or rigid 2D floating rings.
 */
public final class DominionShader {
    public static final int FX_SMOKE = 0;
    public static final int FX_PLASMA = 1;
    public static final int FX_BRUSH = 2;
    public static final int FX_BOLT = 3;
    public static final int FX_SPARK = 4;
    public static final int FX_GLOW = 5;
    public static final int FX_HEX_MIST = 6;
    public static final int FX_IMPACT_FRAME = 7;
    public static final int FX_SHELL = 8;
    public static final int FX_GROUND_POOL = 9;

    private static ShaderInstance shader;
    private static boolean failed;

    private DominionShader() {}

    public static boolean isAvailable() {
        return shader != null && !failed;
    }

    public static void bind(float seconds) {
        if (shader == null && !failed) {
            try {
                shader = new ShaderInstance(
                        Minecraft.getInstance().getResourceManager(),
                        "godwand_dominion",
                        DefaultVertexFormat.POSITION_TEX_COLOR
                );
            } catch (Exception error) {
                failed = true;
                GodWandMod.LOGGER.warn("God Wand: Block Dominion custom shader unavailable, falling back to vanilla", error);
            }
        }
        if (shader == null) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            return;
        }
        shader.safeGetUniform("VfxTime").set(seconds);
        RenderSystem.setShader(() -> shader);
    }

    public static void prewarm() {
        bind(0f);
        if (shader == null) return;
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < 4; i++) {
            buffer.addVertex(0, 0, 0).setUv(0.5f, 2.5f).setColor(0, 0, 0, 0);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        try {
            var mesh = buffer.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    // ---- Deterministic smooth noise ----

    public static double hash(long n) {
        n = (n ^ (n >>> 33)) * 0xff51afd7ed558ccdL;
        n = (n ^ (n >>> 33)) * 0xc4ceb9fe1a85ec53L;
        n ^= n >>> 33;
        return (n >>> 11) * 0x1.0p-53;
    }

    public static double vnoise(long seed, double x) {
        long i = (long) Math.floor(x);
        double f = x - i;
        f = f * f * (3.0 - 2.0 * f);
        return hash(seed * 0x9E3779B97F4A7C15L + i) * (1.0 - f)
                + hash(seed * 0x9E3779B97F4A7C15L + i + 1) * f;
    }

    public static double snoise(long seed, double x) {
        return vnoise(seed, x) * 2.0 - 1.0;
    }

    public static Vec3 nvec(long seed, double x) {
        return new Vec3(snoise(seed, x), snoise(seed + 7, x), snoise(seed + 13, x));
    }

    public static Vec3[] basis(Vec3 normal) {
        Vec3 n = normal == null || normal.lengthSqr() < 1e-5 ? new Vec3(0, 1, 0) : normal.normalize();
        Vec3 side = n.cross(Math.abs(n.y) < 0.94 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 up = n.cross(side).normalize();
        return new Vec3[]{side, up};
    }

    // ---- Primitive & True 3D Volumetric Emitters ----

    public static void emitQuad(VertexConsumer v, Matrix4f m, Vec3 center, Vec3 right, Vec3 up,
                                double halfW, double halfH, int effectId, int seed,
                                int r, int g, int b, int a) {
        if (a <= 2) return;
        float u0 = seed + 0.002f, u1 = seed + 0.998f;
        float v0 = (effectId + 2) + 0.002f, v1 = (effectId + 2) + 0.998f;
        Vec3 rx = right.scale(halfW);
        Vec3 uy = up.scale(halfH);
        Vec3 p0 = center.subtract(rx).subtract(uy);
        Vec3 p1 = center.add(rx).subtract(uy);
        Vec3 p2 = center.add(rx).add(uy);
        Vec3 p3 = center.subtract(rx).add(uy);
        v.addVertex(m, (float) p0.x, (float) p0.y, (float) p0.z).setUv(u0, v0).setColor(r, g, b, a);
        v.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setUv(u1, v0).setColor(r, g, b, a);
        v.addVertex(m, (float) p2.x, (float) p2.y, (float) p2.z).setUv(u1, v1).setColor(r, g, b, a);
        v.addVertex(m, (float) p3.x, (float) p3.y, (float) p3.z).setUv(u0, v1).setColor(r, g, b, a);
    }

    public static void emitBillboard(VertexConsumer v, Matrix4f m, Vec3 center, Vec3 camRight, Vec3 camUp,
                                     double radius, double rotRad, int effectId, int seed,
                                     int r, int g, int b, int a) {
        if (a <= 2 || radius <= 0.004) return;
        double c = Math.cos(rotRad), s = Math.sin(rotRad);
        Vec3 rx = camRight.scale(c).add(camUp.scale(s));
        Vec3 uy = camUp.scale(c).subtract(camRight.scale(s));
        emitQuad(v, m, center, rx, uy, radius, radius, effectId, seed, r, g, b, a);
    }

    /** Emits a velocity-aligned thin aerodynamic needle/spark streak in 3D space (camera-relative coords). */
    public static void emitVelocityStreak(VertexConsumer v, Matrix4f m, Vec3 center, Vec3 velocity,
                                          double halfLen, double halfWidth, int seed,
                                          int r, int g, int b, int a) {
        if (a <= 2 || halfLen <= 0.01) return;
        Vec3 dir = velocity.lengthSqr() > 1e-6 ? velocity.normalize() : new Vec3(0, 1, 0);
        Vec3 toCam = center.lengthSqr() > 1e-6 ? center.scale(-1.0).normalize() : new Vec3(0, 1, 0);
        Vec3 side = dir.cross(toCam);
        if (side.lengthSqr() < 1e-5) side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-5) side = new Vec3(1, 0, 0);
        else side = side.normalize();
        emitQuad(v, m, center, dir, side, halfLen, halfWidth, FX_SPARK, (seed * 4) + 1, r, g, b, a);
    }

    /**
     * Projects a soft, noise-dappled ground light pool directly onto the terrain surface (like BlueRedDraw.mapLight),
     * tinting real grass/stone beneath low-flying wings, Earthen Surge, Air Stairs, or Auto Mode.
     */
    public static void emitGroundPool(VertexConsumer v, Matrix4f m, Vec3 groundCenter, double radius,
                                      int seed, int r, int g, int b, int a) {
        if (a <= 2 || radius <= 0.05) return;
        emitQuad(v, m, groundCenter, new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                radius, radius, FX_GROUND_POOL, seed, r, g, b, a);
    }

    /**
     * True 3D Volumetric Aerodynamic Mach Cone / Slipstream Shell (camera-relative coords).
     * Builds a real multi-ring curved 3D paraboloid open behind {@code nose} along {@code -axis},
     * shading every vertex with true 3D view-angle Fresnel so the center stays clear while the curved
     * 3D air-compression silhouette shimmers authentically from every angle!
     */
    public static void emitFresnelCone(VertexConsumer v, Matrix4f m, Vec3 nose, Vec3 axis,
                                       double length, double maxRadius, int slices, int rings,
                                       int seed, int r, int g, int b, int masterAlpha) {
        if (masterAlpha <= 2 || length <= 0.05 || maxRadius <= 0.05) return;
        Vec3 fwd = axis.lengthSqr() > 1e-5 ? axis.normalize() : new Vec3(0, 0, 1);
        Vec3[] uw = basis(fwd);
        Vec3 right = uw[0], up = uw[1];

        Vec3[][] pos = new Vec3[rings + 1][slices + 1];
        int[][] alphas = new int[rings + 1][slices + 1];

        for (int j = 0; j <= rings; j++) {
            double vT = j / (double) rings; // 0 = nose, 1 = trailing rim
            double rad = maxRadius * Math.pow(vT, 0.62);
            double zOff = -vT * length;
            double endEnv = Math.sin(Math.PI * Math.min(1.0, vT * 0.92 + 0.06));
            // Outward slope of paraboloid normal
            double drdv = 0.62 * maxRadius * Math.pow(Math.max(0.04, vT), -0.38);
            for (int i = 0; i <= slices; i++) {
                double uT = i / (double) slices;
                double ang = uT * Math.PI * 2.0;
                double ca = Math.cos(ang), sa = Math.sin(ang);
                Vec3 radial = right.scale(ca).add(up.scale(sa));
                Vec3 p = nose.add(fwd.scale(zOff)).add(radial.scale(rad));
                pos[j][i] = p;

                Vec3 surfNormal = radial.scale(length).add(fwd.scale(drdv)).normalize();
                Vec3 viewDir = p.lengthSqr() > 1e-6 ? p.scale(-1.0).normalize() : fwd;
                double ndotv = Math.abs(surfNormal.dot(viewDir));
                double fresnel = Math.pow(1.0 - Mth.clamp(ndotv, 0.0, 1.0), 1.85);
                double wAlpha = (0.14 + 0.86 * fresnel) * endEnv;
                alphas[j][i] = Mth.clamp((int) (masterAlpha * wAlpha), 0, 255);
            }
        }

        int fxBase = FX_SHELL + 2;
        for (int j = 0; j < rings; j++) {
            float v0 = fxBase + 0.002f + (j / (float) rings) * 0.994f;
            float v1 = fxBase + 0.002f + ((j + 1) / (float) rings) * 0.994f;
            for (int i = 0; i < slices; i++) {
                float u0 = seed + 0.002f + (i / (float) slices) * 0.994f;
                float u1 = seed + 0.002f + ((i + 1) / (float) slices) * 0.994f;
                Vec3 p00 = pos[j][i], p10 = pos[j][i + 1], p11 = pos[j + 1][i + 1], p01 = pos[j + 1][i];
                v.addVertex(m, (float) p00.x, (float) p00.y, (float) p00.z).setUv(u0, v0).setColor(r, g, b, alphas[j][i]);
                v.addVertex(m, (float) p10.x, (float) p10.y, (float) p10.z).setUv(u1, v0).setColor(r, g, b, alphas[j][i + 1]);
                v.addVertex(m, (float) p11.x, (float) p11.y, (float) p11.z).setUv(u1, v1).setColor(r, g, b, alphas[j + 1][i + 1]);
                v.addVertex(m, (float) p01.x, (float) p01.y, (float) p01.z).setUv(u0, v1).setColor(r, g, b, alphas[j + 1][i]);
            }
        }
    }

    /**
     * True 3D Curved Spherical-Cap / Dome Shell with per-vertex 3D Fresnel shading (camera-relative coords).
     * Used for directional shields ({@code maxPhiRad ~ 0.65}), Rain Umbrella canopy ({@code maxPhiRad ~ 0.55}),
     * and full 360 protective domes ({@code maxPhiRad ~ PI}) — looks genuinely 3D, never a pasted flat disc!
     */
    public static void emitFresnelDome(VertexConsumer v, Matrix4f m, Vec3 center, Vec3 poleDir,
                                       double radius, double maxPhiRad, int slices, int rings,
                                       int effectId, int seed, int r, int g, int b, int masterAlpha) {
        if (masterAlpha <= 2 || radius <= 0.05) return;
        Vec3 pole = poleDir.lengthSqr() > 1e-5 ? poleDir.normalize() : new Vec3(0, 1, 0);
        Vec3[] uw = basis(pole);
        Vec3 right = uw[0], up = uw[1];

        Vec3[][] pos = new Vec3[rings + 1][slices + 1];
        int[][] alphas = new int[rings + 1][slices + 1];

        for (int j = 0; j <= rings; j++) {
            double vT = j / (double) rings;
            double phi = vT * maxPhiRad;
            double sinPhi = Math.sin(phi), cosPhi = Math.cos(phi);
            double rimFade = maxPhiRad < Math.PI * 0.95 ? (1.0 - Math.pow(vT, 3.0)) : 1.0;
            for (int i = 0; i <= slices; i++) {
                double uT = i / (double) slices;
                double theta = uT * Math.PI * 2.0;
                Vec3 surfNormal = right.scale(Math.cos(theta) * sinPhi)
                        .add(up.scale(Math.sin(theta) * sinPhi))
                        .add(pole.scale(cosPhi));
                Vec3 p = center.add(surfNormal.scale(radius));
                pos[j][i] = p;

                Vec3 viewDir = p.lengthSqr() > 1e-6 ? p.scale(-1.0).normalize() : pole;
                double ndotv = Math.abs(surfNormal.dot(viewDir));
                double fresnel = Math.pow(1.0 - Mth.clamp(ndotv, 0.0, 1.0), 2.1);
                double wAlpha = (0.10 + 0.90 * fresnel) * rimFade;
                alphas[j][i] = Mth.clamp((int) (masterAlpha * wAlpha), 0, 255);
            }
        }

        int fxBase = effectId + 2;
        for (int j = 0; j < rings; j++) {
            float v0 = fxBase + 0.002f + (j / (float) rings) * 0.994f;
            float v1 = fxBase + 0.002f + ((j + 1) / (float) rings) * 0.994f;
            for (int i = 0; i < slices; i++) {
                float u0 = seed + 0.002f + (i / (float) slices) * 0.994f;
                float u1 = seed + 0.002f + ((i + 1) / (float) slices) * 0.994f;
                Vec3 p00 = pos[j][i], p10 = pos[j][i + 1], p11 = pos[j + 1][i + 1], p01 = pos[j + 1][i];
                v.addVertex(m, (float) p00.x, (float) p00.y, (float) p00.z).setUv(u0, v0).setColor(r, g, b, alphas[j][i]);
                v.addVertex(m, (float) p10.x, (float) p10.y, (float) p10.z).setUv(u1, v0).setColor(r, g, b, alphas[j][i + 1]);
                v.addVertex(m, (float) p11.x, (float) p11.y, (float) p11.z).setUv(u1, v1).setColor(r, g, b, alphas[j + 1][i + 1]);
                v.addVertex(m, (float) p01.x, (float) p01.y, (float) p01.z).setUv(u0, v1).setColor(r, g, b, alphas[j + 1][i]);
            }
        }
    }

    /**
     * Emits a continuous, seam-free camera-facing curved ribbon with per-vertex averaged normals.
     */
    public static void emitCurvedRibbon(VertexConsumer v, Matrix4f m, Vec3[] points, double[] halfWidths,
                                        double[] alphaWeights, int effectId, int seed,
                                        int r, int g, int b, int masterAlpha) {
        if (points == null || points.length < 2 || masterAlpha <= 2) return;
        int n = points.length;
        Vec3[] leftPts = new Vec3[n];
        Vec3[] rightPts = new Vec3[n];
        int[] alphas = new int[n];
        float v0 = (effectId + 2) + 0.002f, v1 = (effectId + 2) + 0.998f;

        Vec3 prevSide = new Vec3(0, 1, 0);
        for (int i = 0; i < n; i++) {
            Vec3 p = points[i];
            Vec3 tang = i == 0 ? points[1].subtract(p)
                    : (i == n - 1 ? p.subtract(points[n - 2]) : points[i + 1].subtract(points[i - 1]));
            Vec3 toCam = p.lengthSqr() > 1e-6 ? p.scale(-1.0).normalize() : new Vec3(0, 1, 0);
            Vec3 side = tang.lengthSqr() > 1e-6 ? tang.cross(toCam) : prevSide;
            if (side.lengthSqr() < 1e-6) side = tang.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1e-6) side = prevSide;
            else side = side.normalize();
            if (i > 0 && side.dot(prevSide) < 0) side = side.scale(-1.0);
            prevSide = side;

            double w = Math.max(0.0, halfWidths[i]);
            leftPts[i] = p.subtract(side.scale(w));
            rightPts[i] = p.add(side.scale(w));
            alphas[i] = Mth.clamp((int) (masterAlpha * (alphaWeights == null ? 1.0 : alphaWeights[i])), 0, 255);
        }

        for (int i = 0; i < n - 1; i++) {
            if ((halfWidths[i] <= 1e-4 && halfWidths[i + 1] <= 1e-4) || (alphas[i] <= 1 && alphas[i + 1] <= 1)) continue;
            float t0 = i / (float) (n - 1);
            float t1 = (i + 1) / (float) (n - 1);
            float u0 = seed + 0.002f + t0 * 0.994f;
            float u1 = seed + 0.002f + t1 * 0.994f;
            Vec3 p0 = leftPts[i], p1 = leftPts[i + 1], p2 = rightPts[i + 1], p3 = rightPts[i];
            v.addVertex(m, (float) p0.x, (float) p0.y, (float) p0.z).setUv(u0, v0).setColor(r, g, b, alphas[i]);
            v.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setUv(u1, v0).setColor(r, g, b, alphas[i + 1]);
            v.addVertex(m, (float) p2.x, (float) p2.y, (float) p2.z).setUv(u1, v1).setColor(r, g, b, alphas[i + 1]);
            v.addVertex(m, (float) p3.x, (float) p3.y, (float) p3.z).setUv(u0, v1).setColor(r, g, b, alphas[i]);
        }
    }

    /** Emits a delicate, fine crackling live electric filament between {@code from} and {@code to}. */
    public static void emitLiveBolt(VertexConsumer v, Matrix4f m, Vec3 from, Vec3 to,
                                    int segments, double jitter, double halfWidth,
                                    long seed, double time, int r, int g, int b, int alpha) {
        if (alpha <= 2 || segments < 2) return;
        Vec3 span = to.subtract(from);
        double len = span.length();
        if (len < 0.05) return;
        Vec3[] uw = basis(span);
        Vec3[] pts = new Vec3[segments + 1];
        double[] ws = new double[segments + 1];
        double snap = Math.floor(time * 0.85);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double env = Math.sin(Math.PI * t);
            double jx = snoise(seed + i * 19L, snap * 0.9 + t * 3.4) * jitter * env;
            double jy = snoise(seed + i * 31L + 7, snap * 0.9 + t * 3.4) * jitter * env;
            pts[i] = from.lerp(to, t).add(uw[0].scale(jx)).add(uw[1].scale(jy));
            ws[i] = halfWidth * (0.35 + 0.65 * env);
        }
        emitCurvedRibbon(v, m, pts, ws, null, FX_BOLT, (int) (Math.abs(seed) % 97), r, g, b, alpha);
    }

    /** Emits a tapering camera-facing slipstream ribbon along {@code points} (0=tail -> last=head). */
    public static void emitTrail(VertexConsumer v, Matrix4f m, Vec3[] points, double headWidth,
                                 int effectId, int seed, int r, int g, int b, int a) {
        if (points == null || points.length < 2 || a <= 2) return;
        int n = points.length;
        double[] ws = new double[n];
        double[] als = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            ws[i] = headWidth * (0.08 + 0.92 * Math.pow(t, 0.75));
            als[i] = t * t * Math.sin(Math.PI * Math.min(1.0, t * 0.96 + 0.04));
        }
        emitCurvedRibbon(v, m, points, ws, als, effectId, seed, r, g, b, a);
    }
}
