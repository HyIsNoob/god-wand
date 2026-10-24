package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.godwand.network.CannonPressurePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

public final class CannonPressureRenderer {
    private record Burst(CannonPressurePayload payload, long tick) {}
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static ClientWorld world;
    public static void reset() { BURSTS.clear(); world = null; }
    public static void receive(CannonPressurePayload payload) {
        var current = MinecraftClient.getInstance().world;
        if (current != world) { reset(); world = current; }
        if (world == null || payload.direction().lengthSquared() < 0.001) return;
        if (BURSTS.size() >= 32) BURSTS.removeFirst();
        BURSTS.add(new Burst(payload, world.getTime()));
    }

    public static Vec3d conePoint(float t, float angle, float expansion, int level) {
        double radius = t * (1.1 + level * 0.3) * expansion;
        return new Vec3d(Math.cos(angle) * radius, Math.sin(angle) * radius,
                -0.12 - t * (1.4 + level * 0.35) * expansion);
    }

    public static void render(WorldRenderContext context) {
        if (context.world() != world) { reset(); return; }
        if (BURSTS.isEmpty()) return;
        long now = world.getTime();
        BURSTS.removeIf(b -> now - b.tick >= 8);
        if (BURSTS.isEmpty()) return;
        float delta = context.tickCounter().getTickDelta(false);
        var matrix = context.matrixStack().peek().getPositionMatrix();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        try {
            for (Burst burst : BURSTS) {
                var p = burst.payload;
                Vec3d forward = p.direction().normalize();
                Vec3d side = forward.crossProduct(Math.abs(forward.y) < 0.99 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0)).normalize();
                Vec3d up = forward.crossProduct(side);
                Vec3d origin = p.center().subtract(context.camera().getPos());
                float age = (now - burst.tick + delta) / 8f;
                float expansion = 0.15f + 0.85f * (1 - (1 - age) * (1 - age));
                for (int band = 0; band < 3; band++) {
                    var buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
                    for (int step = 0; step <= 64; step++) {
                        float angle = step * (float)Math.PI / 32;
                        for (int edge = 0; edge < 2; edge++) {
                            float t = 0.25f + band * 0.3f + edge * 0.12f;
                            Vec3d local = conePoint(t, angle, expansion, p.level());
                            Vec3d point = origin.add(side.multiply(local.x)).add(up.multiply(local.y)).add(forward.multiply(local.z));
                            int alpha = (int)(170 * (1 - age) * (1 - age) * (edge == 0 ? 1 : 0.15));
                            buffer.vertex(matrix, (float)point.x, (float)point.y, (float)point.z).color(220, 240, 255, alpha);
                        }
                    }
                    BufferRenderer.drawWithGlobalProgram(buffer.end());
                }
            }
        } finally {
            RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
        }
    }
}
