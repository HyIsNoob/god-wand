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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Custom GLSL core shader for Block Dominion (assets/minecraft/shaders/core/godwand_dominion.*).
 * Provides animated procedural rune rings, hexagonal shields, flowing plasma ribbons,
 * Mach shockwaves, custom 3D GPU spark/ember particles, and anime impact frames without
 * touching Minecraft's vanilla shaders.
 */
public final class DominionShader {
    public static final int FX_RUNE_RING = 0;
    public static final int FX_HEX_SHIELD = 1;
    public static final int FX_ENERGY_RIBBON = 2;
    public static final int FX_MACH_SHOCK = 3;
    public static final int FX_SPARK_PARTICLE = 4;
    public static final int FX_IMPACT_FRAME = 5;

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

    /** Emits a single oriented square quad mapped to procedural effect {@code effectId} with {@code seed}. */
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

    /** Emits a planar quad centered at {@code center} facing {@code normal}. */
    public static void emitDisc(VertexConsumer v, Matrix4f m, Vec3 center, Vec3 normal,
                                double radius, int effectId, int seed,
                                int r, int g, int b, int a) {
        if (a <= 2 || radius <= 0.01) return;
        Vec3 n = normal == null || normal.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : normal.normalize();
        Vec3 side = n.cross(Math.abs(n.y) < 0.94 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 up = n.cross(side).normalize();
        emitQuad(v, m, center, side, up, radius, radius, effectId, seed, r, g, b, a);
    }

    /** Emits a crossed-plane energy ribbon from {@code from} (tail u=0) to {@code to} (head u=1). */
    public static void emitBeam(VertexConsumer v, Matrix4f m, Vec3 from, Vec3 to, double halfWidth,
                                int seed, int r, int g, int b, int a) {
        if (a <= 2) return;
        Vec3 axis = to.subtract(from);
        if (axis.lengthSqr() < 1e-5) return;
        Vec3 dir = axis.normalize();
        Vec3 s1 = dir.cross(Math.abs(dir.y) < 0.92 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize().scale(halfWidth);
        Vec3 s2 = dir.cross(s1).normalize().scale(halfWidth);
        float u0 = seed + 0.002f, u1 = seed + 0.998f;
        float v0 = (FX_ENERGY_RIBBON + 2) + 0.002f, v1 = (FX_ENERGY_RIBBON + 2) + 0.998f;
        for (Vec3 s : new Vec3[]{s1, s2}) {
            Vec3 p0 = from.subtract(s), p1 = to.subtract(s), p2 = to.add(s), p3 = from.add(s);
            v.addVertex(m, (float) p0.x, (float) p0.y, (float) p0.z).setUv(u0, v0).setColor(r, g, b, a);
            v.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setUv(u1, v0).setColor(r, g, b, a);
            v.addVertex(m, (float) p2.x, (float) p2.y, (float) p2.z).setUv(u1, v1).setColor(r, g, b, a);
            v.addVertex(m, (float) p3.x, (float) p3.y, (float) p3.z).setUv(u0, v1).setColor(r, g, b, a);
        }
    }

    /** Emits a camera-facing multi-segment trail ribbon along {@code points} (index 0 = tail, last = head). */
    public static void emitTrail(VertexConsumer v, Matrix4f m, Vec3[] points, double headWidth,
                                 int seed, int r, int g, int b, int a) {
        if (points == null || points.length < 2 || a <= 2) return;
        int n = points.length;
        float v0 = (FX_ENERGY_RIBBON + 2) + 0.002f, v1 = (FX_ENERGY_RIBBON + 2) + 0.998f;
        for (int i = 0; i < n - 1; i++) {
            Vec3 a0 = points[i];
            Vec3 a1 = points[i + 1];
            Vec3 seg = a1.subtract(a0);
            if (seg.lengthSqr() < 1e-6) continue;
            // Camera-facing ribbon normal (points are already camera-relative)
            Vec3 mid = a0.add(a1).scale(0.5);
            Vec3 toCam = mid.lengthSqr() > 1e-6 ? mid.scale(-1.0).normalize() : new Vec3(0, 1, 0);
            Vec3 side = seg.cross(toCam);
            if (side.lengthSqr() < 1e-6) side = seg.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1e-6) continue;
            side = side.normalize();

            float t0 = i / (float) (n - 1);
            float t1 = (i + 1) / (float) (n - 1);
            double w0 = headWidth * (0.15 + 0.85 * t0);
            double w1 = headWidth * (0.15 + 0.85 * t1);
            int al0 = (int) (a * t0);
            int al1 = (int) (a * t1);
            float u0 = seed + 0.002f + t0 * 0.994f;
            float u1 = seed + 0.002f + t1 * 0.994f;

            Vec3 p0 = a0.subtract(side.scale(w0));
            Vec3 p1 = a1.subtract(side.scale(w1));
            Vec3 p2 = a1.add(side.scale(w1));
            Vec3 p3 = a0.add(side.scale(w0));
            v.addVertex(m, (float) p0.x, (float) p0.y, (float) p0.z).setUv(u0, v0).setColor(r, g, b, al0);
            v.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setUv(u1, v0).setColor(r, g, b, al1);
            v.addVertex(m, (float) p2.x, (float) p2.y, (float) p2.z).setUv(u1, v1).setColor(r, g, b, al1);
            v.addVertex(m, (float) p3.x, (float) p3.y, (float) p3.z).setUv(u0, v1).setColor(r, g, b, al0);
        }
    }
}
