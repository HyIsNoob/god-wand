package net.godwand.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;

public final class ArcaneOrbModel {

    public static void render(Matrix4f matrix, float scale, float time, float charge) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        try {
            // 1. Lõi năng lượng trắng xanh siêu sáng ở trung tâm (Inner Core)
            float coreScale = scale * 0.46f;
            renderSphere(matrix, coreScale, time * 24.0f, time * 18.0f, 0.0f, 220, 255, 255, (int) (200 + charge * 55));

            // 2. Lớp vỏ cầu năng lượng Cyan đặc trưng của Singularity / Arcane Orb (Outer Energy Shell)
            float shellScale = scale * 0.88f;
            renderSphere(matrix, shellScale, -time * 16.0f, 0.0f, time * 22.0f, 0, 240, 255, (int) (110 + charge * 80));

            // 3. Lớp màng plasma ngoài cùng (Plasma Halo)
            float haloScale = scale * 1.12f;
            renderSphere(matrix, haloScale, time * 10.0f, -time * 12.0f, time * 8.0f, 40, 160, 255, (int) (45 + charge * 50));

            // 4. Các vành đai gia tốc hạt xoay quỹ đạo quanh quả cầu (Orbiting Accretion Rings)
            // Vành 1: Nghiêng góc 45 độ quanh trục X
            renderEnergyRing(matrix, scale * 1.32f, scale * 0.06f, 45.0f, 0.0f, time * 38.0f, 0, 255, 255, (int) (140 + charge * 100));

            // Vành 2: Nghiêng góc -55 độ quanh trục Z
            renderEnergyRing(matrix, scale * 1.48f, scale * 0.05f, 0.0f, -55.0f, -time * 46.0f, 80, 200, 255, (int) (130 + charge * 90));

            // Vành 3: Nghiêng 70 độ (kích hoạt khi đạt cấp tụ lực 2 trở lên)
            if (charge >= 0.35f) {
                float ring3Fade = Math.min(1.0f, (charge - 0.35f) / 0.3f);
                renderEnergyRing(matrix, scale * 1.62f, scale * 0.045f, 70.0f, 35.0f, time * 58.0f, 175, 95, 255, (int) (ring3Fade * 170));
            }

            // 5. Các dải năng lượng xoắn ốc hút từ bên ngoài vào tâm quả cầu (Inward Spiral Streams)
            renderInwardSpirals(matrix, scale, time, charge);

        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
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

            var buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            for (int j = 0; j <= segments; j++) {
                float phi = (float) Math.PI * 2.0f * j / segments;
                float cosPhi = (float) Math.cos(phi);
                float sinPhi = (float) Math.sin(phi);

                buffer.vertex(local, r1 * cosPhi, y1, r1 * sinPhi).color(r, g, b, a);
                buffer.vertex(local, r2 * cosPhi, y2, r2 * sinPhi).color(r, g, b, a);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
    }

    private static void renderEnergyRing(Matrix4f matrix, float radius, float halfWidth, float tiltX, float tiltZ, float spinAngle, int r, int g, int b, int a) {
        Matrix4f local = new Matrix4f(matrix);
        local.rotateX((float) Math.toRadians(tiltX));
        local.rotateZ((float) Math.toRadians(tiltZ));
        local.rotateY((float) Math.toRadians(spinAngle));

        int steps = 24;
        var buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= steps; i++) {
            float phi = (float) Math.PI * 2.0f * i / steps;
            float cos = (float) Math.cos(phi);
            float sin = (float) Math.sin(phi);

            float rInner = radius - halfWidth;
            float rOuter = radius + halfWidth;

            buffer.vertex(local, rInner * cos, 0, rInner * sin).color(r, g, b, a);
            buffer.vertex(local, rOuter * cos, 0, rOuter * sin).color(r, g, b, a / 2);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void renderInwardSpirals(Matrix4f matrix, float scale, float time, float charge) {
        int arms = 3;
        int steps = 20;
        for (int arm = 0; arm < arms; arm++) {
            float armOffset = arm * (float) Math.PI * 2.0f / arms;
            var buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            for (int step = 0; step <= steps; step++) {
                float t = step / (float) steps; // 0 (outer) -> 1 (center)
                float spiralRadius = scale * (2.2f - t * 1.5f);
                float angle = armOffset + (1.0f - t) * 6.0f - time * 0.28f;
                float height = (1.0f - t) * (arm % 2 == 0 ? 0.35f : -0.35f);

                float width = scale * 0.05f * (float) Math.sin(t * Math.PI);
                int alpha = (int) (Math.sin(t * Math.PI) * (70 + charge * 80));

                float x = spiralRadius * (float) Math.cos(angle);
                float z = spiralRadius * (float) Math.sin(angle);

                buffer.vertex(matrix, x, height - width, z).color(0, 245, 255, alpha);
                buffer.vertex(matrix, x, height + width, z).color(120, 210, 255, alpha);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
    }
}
