package net.godwand.client;

import net.godwand.client.compat.GodWandGeometry;


import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Shared pointed, fluted stone for the charge preview and the flying projectile. */
public final class StoneCannonModel {
    public static Vector3f chargeWindPoint(float t, float phase, float scale, float charge) {
        float angle = phase - t * (7 + charge * 3);
        float radius = 0.08f + (float)Math.sin(t * Math.PI * 0.5) * (1.65f - charge * 0.35f);
        float nose = 2.45f * scale;
        return new Vector3f((float)Math.cos(angle) * radius, (float)Math.sin(angle) * radius,
                nose + t * (-4.0f - charge * 2.5f - nose));
    }

    public static float chargeWindVisibility(float time) {
        float fade = Math.max(0, Math.min(1, (time - 20) / 24));
        return fade * fade * (3 - 2 * fade);
    }

    public static float chargeDrillRotation(float time) {
        float ramp = Math.min(time, 80);
        return time * 62 + ramp * ramp * 0.45f + Math.max(0, time - 80) * 72;
    }

    public static float chargeWindRotation(float time) {
        float elapsed = Math.max(0, time - 20);
        float ramp = Math.min(elapsed, 60);
        return elapsed * 0.84f + ramp * ramp * 0.0056f + Math.max(0, elapsed - 60) * 0.672f;
    }

    public static void renderChargeWind(Matrix4f matrix, float scale, float time, float charge) {
        float visibility = chargeWindVisibility(time);
        if (visibility <= 0) return;
        GodWandGeometry.enableBlend();
        GodWandGeometry.additiveBlend();
        GodWandGeometry.enableDepthTest(); GodWandGeometry.disableCull(); GodWandGeometry.depthMask(false);
        GodWandGeometry.colored();
        float rotation = chargeWindRotation(time);
        try {
            for (int arm = 0; arm < 2; arm++) {
                var buffer = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
                for (int step = 0; step <= 80; step++) {
                    float t = step / 80f;
                    float phase = arm * (float)(Math.PI) + rotation;
                    Vector3f point = chargeWindPoint(t, phase, scale, charge);
                    float angle = phase - t * (7 + charge * 3);
                    float taper = (float)Math.pow(Math.sin(t * Math.PI), 0.75);
                    float width = (0.12f + charge * 0.22f) * taper;
                    // Keep the sweep soft where it crosses the first-person camera plane.
                    float eyeFade = 1 - 0.65f * (float)Math.exp(-Math.pow((point.z + 1.85f) / 0.65f, 2));
                    int alpha = (int)((58 + charge * 42) * taper * eyeFade * visibility);
                    for (int sign : new int[]{-1, 1}) {
                        buffer.addVertex(matrix, point.x - sign * (float)Math.sin(angle) * width,
                                point.y + sign * (float)Math.cos(angle) * width, point.z).setColor(220, 242, 255, alpha);
                    }
                }
                GodWandGeometry.draw(buffer.buildOrThrow());
            }
        } finally {
            GodWandGeometry.depthMask(true); GodWandGeometry.enableCull();
            GodWandGeometry.defaultBlendFunc(); GodWandGeometry.disableBlend();
        }
    }

    public static Vector3f surface(float t, float angle) {
        float radius = (float) Math.pow(1 - t, 0.8) * (0.40f + 0.065f * (float) Math.cos(angle * 3 - t * 18));
        return new Vector3f((float) Math.cos(angle) * radius, (float) Math.sin(angle) * radius, -0.75f + t * 3.2f);
    }

    public static void render(Matrix4f matrix, float scale, float time, float charge) {
        GodWandGeometry.enableDepthTest();
        GodWandGeometry.disableBlend();
        GodWandGeometry.depthMask(true);
        GodWandGeometry.colored();
        GodWandGeometry.disableCull();
        try {
            var stone = GodWandGeometry.begin(PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (int ring = 0; ring < 16; ring++) {
                for (int side = 0; side < 24; side++) {
                    float t = ring / 16f, next = (ring + 1) / 16f;
                    float a = side * (float) Math.PI / 12, b = (side + 1) * (float) Math.PI / 12;
                    Vector3f p = surface(t, a), q = surface(t, b), r = surface(next, b), s = surface(next, a);
                    float shade = 0.6f + 0.3f * (float) Math.cos(a - 0.7) + 0.08f * (float) Math.sin(ring * 2.3 + side * 4.1);
                    int red = (int) (160 * shade), green = (int) (124 * shade), blue = (int) (83 * shade);
                    for (Vector3f point : new Vector3f[]{p, q, r, p, r, s}) {
                        stone.addVertex(matrix, point.x * scale, point.y * scale, point.z * scale).setColor(red, green, blue, 255);
                    }
                }
            }
            // Close the broad rear face, visible when charging in first person.
            for (int side = 0; side < 24; side++) {
                Vector3f p = surface(0, side * (float) Math.PI / 12), q = surface(0, (side + 1) * (float) Math.PI / 12);
                stone.addVertex(matrix, 0, 0, -0.75f * scale).setColor(100, 72, 44, 255);
                stone.addVertex(matrix, p.x * scale, p.y * scale, p.z * scale).setColor(130, 95, 56, 255);
                stone.addVertex(matrix, q.x * scale, q.y * scale, q.z * scale).setColor(130, 95, 56, 255);
            }
            GodWandGeometry.draw(stone.buildOrThrow());
            GodWandGeometry.enableBlend();
            GodWandGeometry.additiveBlend();
            GodWandGeometry.depthMask(false);
            // A continuous gold helix hugs the rifling, with a narrow white-hot core.
            for (int layer = 0; layer < 2; layer++) {
                var glow = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
                for (int step = 0; step <= 144; step++) {
                    float t = step / 144f;
                    float angle = t * 24 - time * 0.16f;
                    float radius = (0.47f * (1 - t) + 0.04f) * scale;
                    float width = (layer == 0 ? 0.085f : 0.018f) * scale;
                    int alpha = (int) (Math.sin(t * Math.PI) * (layer == 0 ? 100 : 240) * charge);
                    for (int sign : new int[]{-1, 1}) {
                        float r = radius + sign * width;
                        glow.addVertex(matrix, (float) Math.cos(angle) * r, (float) Math.sin(angle) * r,
                                (-0.75f + t * 3.2f) * scale).setColor(255, layer == 0 ? 130 : 235, layer == 0 ? 12 : 145, alpha);
                    }
                }
                GodWandGeometry.draw(glow.buildOrThrow());
            }
        } finally {
            GodWandGeometry.depthMask(true);
            GodWandGeometry.enableCull();
            GodWandGeometry.defaultBlendFunc();
            GodWandGeometry.disableBlend();
        }
    }
}
