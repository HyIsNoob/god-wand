package net.godwand.client;

import net.godwand.client.compat.GodWandGeometry;


import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.godwand.client.compat.PrimitiveTopology;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public final class ArcaneOrbModel {

    public static void render(Matrix4f matrix, float scale, float time, float charge) {
        GodWandGeometry.enableBlend();
        GodWandGeometry.additiveBlend();
        GodWandGeometry.enableDepthTest();
        GodWandGeometry.depthMask(false);
        GodWandGeometry.disableCull();
        GodWandGeometry.colored();

        try {
            // 1. Lõi năng lượng trắng xanh siêu sáng ở trung tâm (Inner Core) - quay tít nhanh dần theo charge
            float coreScale = scale * 0.46f;
            float coreSpin = time * (32.0f + charge * 140.0f);
            renderSphere(matrix, coreScale, coreSpin, coreSpin * 0.8f, 0.0f, 220, 255, 255, (int) (200 + charge * 55));

            // 2. Lớp vỏ cầu năng lượng Cyan đặc trưng (Outer Energy Shell) - quay ngược chiều tăng tốc
            float shellScale = scale * 0.88f;
            float shellSpin = -time * (24.0f + charge * 110.0f);
            renderSphere(matrix, shellScale, shellSpin, 0.0f, time * (28.0f + charge * 120.0f), 0, 240, 255, (int) (110 + charge * 80));

            // 3. Lớp màng plasma ngoài cùng (Plasma Halo)
            float haloScale = scale * 1.12f;
            float haloSpin = time * (16.0f + charge * 80.0f);
            renderSphere(matrix, haloScale, haloSpin, -time * 14.0f, time * 12.0f, 40, 160, 255, (int) (45 + charge * 50));

            // 4. Các vành đai gia tốc hạt xoay quỹ đạo quanh quả cầu (Orbiting Accretion Rings)
            // Vành 1: Nghiêng góc 45 độ quanh trục X
            renderEnergyRing(matrix, scale * 1.32f, scale * 0.05f, 45.0f, 0.0f, time * (45.0f + charge * 240.0f), 0, 255, 255, (int) (140 + charge * 100));

            // Vành 2: Nghiêng góc -55 độ quanh trục Z
            renderEnergyRing(matrix, scale * 1.48f, scale * 0.045f, 0.0f, -55.0f, -time * (55.0f + charge * 270.0f), 80, 200, 255, (int) (130 + charge * 90));

            // Vành 3: Nghiêng 70 độ (kích hoạt khi đạt cấp tụ lực 2 trở lên)
            if (charge >= 0.35f) {
                float ring3Fade = Math.min(1.0f, (charge - 0.35f) / 0.3f);
                renderEnergyRing(matrix, scale * 1.62f, scale * 0.04f, 70.0f, 35.0f, time * (75.0f + charge * 340.0f), 175, 95, 255, (int) (ring3Fade * 170));
            }

            // 5. Các dải năng lượng xoắn ốc hút từ bên ngoài vào tâm quả cầu (Inward Spiral Streams) - xoay cuộn xoáy vào tâm
            renderInwardSpirals(matrix, scale, time, charge);

        } finally {
            GodWandGeometry.depthMask(true);
            GodWandGeometry.enableCull();
            GodWandGeometry.defaultBlendFunc();
            GodWandGeometry.disableBlend();
        }
    }

    private static void renderSphere(Matrix4f matrix, float radius, float rotY, float rotX, float rotZ, int r, int g, int b, int a) {
        Matrix4f local = new Matrix4f(matrix);
        local.rotateY((float) Math.toRadians(rotY));
        local.rotateX((float) Math.toRadians(rotX));
        local.rotateZ((float) Math.toRadians(rotZ));

        int rings = 8;
        int segments = 12;

        for (int i = 0; i < rings; i++) {
            float theta1 = - (float) Math.PI * 0.5f + (float) Math.PI * i / rings;
            float theta2 = - (float) Math.PI * 0.5f + (float) Math.PI * (i + 1) / rings;
            float y1 = radius * (float) Math.sin(theta1);
            float y2 = radius * (float) Math.sin(theta2);
            float r1 = radius * (float) Math.cos(theta1);
            float r2 = radius * (float) Math.cos(theta2);

            BufferBuilder buffer = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int j = 0; j <= segments; j++) {
                float phi = (float) Math.PI * 2.0f * j / segments;
                float cosPhi = (float) Math.cos(phi);
                float sinPhi = (float) Math.sin(phi);

                buffer.addVertex(local, r1 * cosPhi, y1, r1 * sinPhi).setColor(r, g, b, a);
                buffer.addVertex(local, r2 * cosPhi, y2, r2 * sinPhi).setColor(r, g, b, a);
            }
            GodWandGeometry.draw(buffer.buildOrThrow());
        }
    }

    private static void renderEnergyRing(Matrix4f matrix, float radius, float halfWidth, float tiltX, float tiltZ, float spinAngle, int r, int g, int b, int a) {
        Matrix4f local = new Matrix4f(matrix);
        local.rotateX((float) Math.toRadians(tiltX));
        local.rotateZ((float) Math.toRadians(tiltZ));
        local.rotateY((float) Math.toRadians(spinAngle));

        int steps = 24;
        BufferBuilder buffer = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= steps; i++) {
            float phi = (float) Math.PI * 2.0f * i / steps;
            float cos = (float) Math.cos(phi);
            float sin = (float) Math.sin(phi);

            float rInner = radius - halfWidth;
            float rOuter = radius + halfWidth;

            buffer.addVertex(local, rInner * cos, 0, rInner * sin).setColor(r, g, b, a);
            buffer.addVertex(local, rOuter * cos, 0, rOuter * sin).setColor(r, g, b, a / 2);
        }
        GodWandGeometry.draw(buffer.buildOrThrow());
    }

    private static void renderInwardSpirals(Matrix4f matrix, float scale, float time, float charge) {
        int arms = 3;
        int steps = 22;
        float spiralSpin = time * (0.8f + charge * 3.4f);
        for (int arm = 0; arm < arms; arm++) {
            float armOffset = arm * (float) Math.PI * 2.0f / arms;
            BufferBuilder buffer = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int step = 0; step <= steps; step++) {
                float t = step / (float) steps;
                float spiralRadius = scale * (2.4f - t * 1.6f);
                float angle = armOffset + (1.0f - t) * 6.5f - spiralSpin;
                float height = (1.0f - t) * (arm % 2 == 0 ? 0.32f : -0.32f);

                float width = scale * 0.05f * (float) Math.sin(t * Math.PI);
                int alpha = (int) (Math.sin(t * Math.PI) * (70 + charge * 80));

                float x = spiralRadius * (float) Math.cos(angle);
                float z = spiralRadius * (float) Math.sin(angle);

                buffer.addVertex(matrix, x, height - width, z).setColor(0, 245, 255, alpha);
                buffer.addVertex(matrix, x, height + width, z).setColor(120, 210, 255, alpha);
            }
            GodWandGeometry.draw(buffer.buildOrThrow());
        }
    }
}

