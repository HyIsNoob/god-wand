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
 * Custom GLSL core shader & organic procedural geometry helpers for Block Dominion
 * (assets/minecraft/shaders/core/godwand_dominion.*).
 * Mirrors BlueRedDraw/Purple's two-pass (BODY alpha + GLOW additive) volumetric language:
 * billowing self-shaded smoke, curved multi-point plasma/brush ribbons, crackling live lightning,
 * soft radial volumetric glows, dissolving hex mist, custom GPU 3D particles, and ink-gold impact frames.
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

    /** Compiles and warms up the shader program on world join to eliminate first-cast stutter. */
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

    // ---- Deterministic smooth noise (matches BlueRedDraw so nothing jitters per frame) ----

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

    // ---- Primitive emitters ----

    /** Emits a single oriented quad mapped to procedural effect {@code effectId} with {@code seed}. */
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

    /** Emits a camera-facing rotated billboard sprite (used for smoke puffs, soft glows, particles). */
    public static void emitBillboard(VertexConsumer v, Matrix4f m, Vec3 center, Vec3 camRight, Vec3 camUp,
                                     double radius, double rotRad, int effectId, int seed,
                                     int r, int g, int b, int a) {
        if (a <= 2 || radius <= 0.005) return;
        double c = Math.cos(rotRad), s = Math.sin(rotRad);
        Vec3 rx = camRight.scale(c).add(camUp.scale(s));
        Vec3 uy = camUp.scale(c).subtract(camRight.scale(s));
        emitQuad(v, m, center, rx, uy, radius, radius, effectId, seed, r, g, b, a);
    }

    /** Emits a planar quad centered at {@code center} facing {@code normal}. */
    public static void emitDisc(VertexConsumer v, Matrix4f m, Vec3 center, Vec3 normal,
                                double radius, int effectId, int seed,
                                int r, int g, int b, int a) {
        if (a <= 2 || radius <= 0.01) return;
        Vec3[] uw = basis(normal);
        emitQuad(v, m, center, uw[0], uw[1], radius, radius, effectId, seed, r, g, b, a);
    }

    /**
     * Emits a continuous, seam-free camera-facing curved ribbon with per-vertex averaged normals
     * (Purple/BlueRedDraw style — zero quad-joint seams even on tight curves!).
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
            // Keep consistent ribbon winding along the whole curve (no sudden 180-deg twist)
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

    /** Emits a jagged crackling live energy bolt between {@code from} and {@code to} (camera-relative coords). */
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
        double snap = Math.floor(time * 0.75);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double env = Math.sin(Math.PI * t);
            double jx = snoise(seed + i * 19L, snap * 0.9 + t * 3.0) * jitter * env;
            double jy = snoise(seed + i * 31L + 7, snap * 0.9 + t * 3.0) * jitter * env;
            pts[i] = from.lerp(to, t).add(uw[0].scale(jx)).add(uw[1].scale(jy));
            ws[i] = halfWidth * (0.45 + 0.65 * env);
        }
        emitCurvedRibbon(v, m, pts, ws, null, FX_BOLT, (int) (Math.abs(seed) % 97), r, g, b, alpha);
    }

    /**
     * Emits a ragged, organic broken pressure wave around {@code center} with normal {@code normal}
     * (Purple/Red style — noise-wobbled radius and variable width with torn gaps, never a stiff flat circle!).
     */
    public static void emitOrganicWave(VertexConsumer v, Matrix4f m, Vec3 center, Vec3 normal,
                                       double radius, double halfWidth, double time, long seed,
                                       int effectId, int r, int g, int b, int alpha) {
        if (alpha <= 2 || radius <= 0.05) return;
        Vec3[] uw = basis(normal);
        int segs = 28;
        Vec3[] pts = new Vec3[segs + 1];
        double[] ws = new double[segs + 1];
        double[] als = new double[segs + 1];
        double baseAng = hash(seed) * Math.PI * 2.0 + time * 0.04;
        for (int i = 0; i <= segs; i++) {
            double t = i / (double) segs;
            double ang = baseAng + t * Math.PI * 2.0;
            double wob = 1.0 + 0.14 * snoise(seed + 1, t * 5.0 + time * 0.14);
            double rr = radius * wob;
            double lift = radius * 0.06 * snoise(seed + 4, t * 4.0 - time * 0.12);
            pts[i] = center.add(uw[0].scale(Math.cos(ang) * rr))
                    .add(uw[1].scale(Math.sin(ang) * rr))
                    .add(normal.normalize().scale(lift));
            double gap = Mth.clamp((vnoise(seed + 2, t * 6.0 + time * 0.10) - 0.20) / 0.25, 0.0, 1.0);
            ws[i] = halfWidth * (0.40 + 0.85 * vnoise(seed + 3, t * 7.0 - time * 0.18)) * gap;
            als[i] = gap;
        }
        emitCurvedRibbon(v, m, pts, ws, als, effectId, (int) (Math.abs(seed) % 97), r, g, b, alpha);
    }

    /** Emits a crossed-plane energy ribbon from {@code from} (tail) to {@code to} (head). */
    public static void emitBeam(VertexConsumer v, Matrix4f m, Vec3 from, Vec3 to, double halfWidth,
                                int seed, int r, int g, int b, int a) {
        if (a <= 2) return;
        Vec3 axis = to.subtract(from);
        if (axis.lengthSqr() < 1e-5) return;
        Vec3 dir = axis.normalize();
        Vec3 s1 = dir.cross(Math.abs(dir.y) < 0.92 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize().scale(halfWidth);
        Vec3 s2 = dir.cross(s1).normalize().scale(halfWidth);
        float u0 = seed + 0.002f, u1 = seed + 0.998f;
        float v0 = (FX_PLASMA + 2) + 0.002f, v1 = (FX_PLASMA + 2) + 0.998f;
        for (Vec3 s : new Vec3[]{s1, s2}) {
            Vec3 p0 = from.subtract(s), p1 = to.subtract(s), p2 = to.add(s), p3 = from.add(s);
            v.addVertex(m, (float) p0.x, (float) p0.y, (float) p0.z).setUv(u0, v0).setColor(r, g, b, a);
            v.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setUv(u1, v0).setColor(r, g, b, a);
            v.addVertex(m, (float) p2.x, (float) p2.y, (float) p2.z).setUv(u1, v1).setColor(r, g, b, a);
            v.addVertex(m, (float) p3.x, (float) p3.y, (float) p3.z).setUv(u0, v1).setColor(r, g, b, a);
        }
    }

    /** Emits a tapering camera-facing trail ribbon along {@code points} (0=tail -> last=head). */
    public static void emitTrail(VertexConsumer v, Matrix4f m, Vec3[] points, double headWidth,
                                 int effectId, int seed, int r, int g, int b, int a) {
        if (points == null || points.length < 2 || a <= 2) return;
        int n = points.length;
        double[] ws = new double[n];
        double[] als = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            ws[i] = headWidth * (0.12 + 0.88 * Math.pow(t, 0.7));
            als[i] = t * t;
        }
        emitCurvedRibbon(v, m, points, ws, als, effectId, seed, r, g, b, a);
    }
}
