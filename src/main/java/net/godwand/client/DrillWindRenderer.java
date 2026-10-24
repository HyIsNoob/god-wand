package net.godwand.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.godwand.network.DrillWindPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import java.util.HashMap;
import java.util.Map;

public final class DrillWindRenderer {
    private record Wind(DrillWindPayload payload, long tick) {}
    private static final Map<Long, Wind> WINDS = new HashMap<>();
    private static ClientWorld world;

    public static void receive(DrillWindPayload payload) {
        ClientWorld current = MinecraftClient.getInstance().world;
        if (current != world) { reset(); world = current; }
        if (current != null) WINDS.put(payload.id(), new Wind(payload, current.getTime()));
    }

    public static void reset() { WINDS.clear(); world = null; }

    public static void render(WorldRenderContext context) {
        if (context.world() != world) { reset(); return; }
        long now = world.getTime();
        WINDS.values().removeIf(w -> now - w.tick() > 4);
        var camera = context.camera().getPos();
        var matrices = context.matrixStack();
        for (Wind wind : WINDS.values()) {
            var p = wind.payload();
            if (camera.squaredDistanceTo(p.x(), p.y(), p.z()) > 128 * 128) continue;
            float elapsed = now - wind.tick() + context.tickCounter().getTickDelta(false);
            matrices.push();
            matrices.translate(p.x() - camera.x, p.y() - camera.y, p.z() - camera.z);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            float reveal = net.godwand.skill.RatioCollapseManager.drillWindVisibility(p.speed());
            if (reveal <= 0) { matrices.pop(); continue; }
            float heat = net.godwand.skill.RatioCollapseManager.drillHeat(p.speed()) * reveal;
            draw(matrices.peek().getPositionMatrix(), p.angle() + elapsed * p.speed(),
                    (p.radius() * 1.18f + 0.7f) * (0.7f + reveal * 0.3f), p.radius() * 3.2f, (0.45f + heat * 0.55f) * reveal, heat);
            drawCloud(matrices.peek().getPositionMatrix(), p.angle() + elapsed * p.speed(), p.radius() * 1.45f * (0.7f + reveal * 0.3f), heat, reveal);
            matrices.pop();
        }
    }

    private static void drawCloud(Matrix4f matrix, float phase, float radius, float heat, float reveal) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        try {
            for (int ring = 0; ring < 6; ring++) {
                var buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
                for (int step = 0; step <= 72; step++) {
                    float t = step / 72.0f;
                    float angle = phase * 0.6f + ring * 1.3f + t * 5.2f;
                    float distance = radius * (1.4f + ring * 0.48f + t * 0.4f);
                    float width = radius * 0.24f * (float) Math.sin(t * Math.PI);
                    float z = -radius * (2.8f + ring * 0.12f) + (float) Math.sin(angle * 2) * radius * 0.08f;
                    int alpha = (int) ((50 + heat * 65) * Math.sin(t * Math.PI) * reveal);
                    float x = (float) Math.cos(angle), y = (float) Math.sin(angle);
                    buffer.vertex(matrix, x * (distance - width), y * (distance - width), z).color(180, 155, 150, alpha);
                    buffer.vertex(matrix, x * (distance + width), y * (distance + width), z).color(230, 215, 205, alpha);
                }
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            }
        } finally {
            RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.disableBlend();
        }
    }

    // Local +Z is the nose: thin bright cores with wider translucent shear trails.
    public static void draw(Matrix4f matrix, float phase, float radius, float length, float strength) {
        draw(matrix, phase, radius, length, strength, 0);
    }

    private static void draw(Matrix4f matrix, float phase, float radius, float length, float strength, float heat) {
        int green = (int) (240 - heat * 155), blue = (int) (255 - heat * 225);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        try {
            for (int ribbon = 0; ribbon < 5; ribbon++) {
                for (int layer = 0; layer < 2; layer++) {
                    BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
                    for (int step = 0; step <= 48; step++) {
                        float t = step / 48.0f;
                        float angle = phase + ribbon * (float) (Math.PI * 2 / 5) + t * 8.8f;
                        float r = 0.12f + radius * (float) Math.pow(t, 0.72);
                        float z = (0.62f - t) * length;
                        float taper = (float) Math.sin(Math.PI * t);
                        float width = radius * (layer == 0 ? 0.10f : 0.018f) * taper;
                        int alpha = (int) (taper * strength * (layer == 0 ? 65 : 220));
                        float x = (float) Math.cos(angle), y = (float) Math.sin(angle);
                        buffer.vertex(matrix, x * r - y * width, y * r + x * width, z).color((int) (205 + heat * 50), green, blue, alpha);
                        buffer.vertex(matrix, x * r + y * width, y * r - x * width, z).color((int) (205 + heat * 50), green, blue, alpha);
                    }
                    BufferRenderer.drawWithGlobalProgram(buffer.end());
                }
            }
            if (heat > 0.01f) {
                var seams = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                for (int strand = 0; strand < 7; strand++) for (int step = 0; step < 32; step++) {
                    for (int corner = 0; corner < 4; corner++) {
                        float t = (step + (corner == 1 || corner == 2 ? 1 : 0)) / 32f;
                        float angle = phase + strand * 0.8976f + t * 8.8f;
                        float r = (0.12f + radius * (float)Math.pow(t, 0.72)) * 0.84f;
                        float width = (corner < 2 ? -1 : 1) * radius * 0.012f;
                        float z = (0.62f - t) * length;
                        int alpha = (int)(heat * 210 * Math.sin(t * Math.PI));
                        seams.vertex(matrix, (float)Math.cos(angle) * r - (float)Math.sin(angle) * width,
                                (float)Math.sin(angle) * r + (float)Math.cos(angle) * width, z)
                                .color(255, (int)(65 + heat * 110), 25, alpha);
                    }
                }
                for (int ember = 0; ember < 18; ember++) for (int segment = 0; segment < 8; segment++) {
                    float h = (ember + 0.5f) / 18f;
                    for (int corner = 0; corner < 4; corner++) {
                        float trail = (segment + (corner == 1 || corner == 2 ? 1 : 0)) / 8f;
                        float angle = phase + ember * 2.4f + h * 8.8f - trail * 0.55f;
                        float r = radius * (0.2f + h * 1.15f + trail * 0.1f);
                        float width = (corner < 2 ? -1 : 1) * radius * 0.009f;
                        seams.vertex(matrix, (float)Math.cos(angle) * r - (float)Math.sin(angle) * width,
                                (float)Math.sin(angle) * r + (float)Math.cos(angle) * width,
                                (0.62f - h - trail * 0.08f) * length)
                                .color(255, 145, 35, (int)(heat * 210 * (1 - trail)));
                    }
                }
                BufferRenderer.drawWithGlobalProgram(seams.end());
            }
            // Expanding broken pressure rings carry the spin out into the surrounding air.
            for (int ring = 0; ring < 3; ring++) {
                float expansion = (phase * 0.08f + ring / 3.0f) % 1.0f;
                float r = radius * (0.65f + expansion * 1.7f);
                float z = ((heat > 0 ? 0.35f : 0.52f) - expansion * (heat > 0 ? 1.7f : 0.8f)) * length;
                BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
                for (int step = 0; step <= 32; step++) {
                    float t = step / 32.0f;
                    float angle = phase + ring * 2.094f + t * 4.4f;
                    float width = radius * 0.045f * (float) Math.sin(Math.PI * t);
                    int alpha = (int) (180 * strength * (1 - expansion) * Math.sin(Math.PI * t));
                    float x = (float) Math.cos(angle), y = (float) Math.sin(angle);
                    buffer.vertex(matrix, x * (r - width), y * (r - width), z).color(225, 245, 255, alpha);
                    buffer.vertex(matrix, x * (r + width), y * (r + width), z).color(225, 245, 255, alpha);
                }
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }
}
