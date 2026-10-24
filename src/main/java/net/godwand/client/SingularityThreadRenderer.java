package net.godwand.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.godwand.network.SingularityThreadsPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;

public final class SingularityThreadRenderer {
    private record Thread(long received, int recallTicks) {}
    private static final Map<Integer, Thread> THREADS = new HashMap<>();
    private static ClientWorld world;

    public static void reset() { THREADS.clear(); world = null; }
    public static void receive(SingularityThreadsPayload payload) {
        var current = MinecraftClient.getInstance().world;
        if (current != world) { reset(); world = current; }
        if (current == null) return;
        for (int id : payload.beamIds()) THREADS.put(id, new Thread(current.getTime(), payload.recallTicks()));
    }

    public static float pulseFraction(float time, int seed, float recallProgress) {
        float phase = Math.floorMod(seed * 31, 101) / 101f;
        if (recallProgress >= 0) return 1 - Math.clamp((recallProgress + phase * 0.12f) / 0.85f, 0, 1);
        float cycle = time * 0.032f + phase;
        return 1 - (cycle - (float) Math.floor(cycle));
    }

    public static void render(WorldRenderContext context) {
        if (context.world() != world) { reset(); return; }
        if (THREADS.isEmpty()) return;
        long now = world.getTime();
        THREADS.values().removeIf(t -> now - t.received > (t.recallTicks > 0 ? t.recallTicks + 2 : 160));
        if (THREADS.isEmpty()) return;
        float delta = context.tickCounter().getTickDelta(false);
        var camera = context.camera().getPos();
        var matrix = context.matrixStack().peek().getPositionMatrix();
        var buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        try {
            for (var entry : THREADS.entrySet()) {
                var entity = world.getEntityById(entry.getKey());
                if (!(entity instanceof BlockDisplayEntity beam) || beam.squaredDistanceTo(camera) > 96 * 96) continue;
                var state = beam.getRenderState();
                if (state == null) continue;
                var transform = state.transformation().interpolate(beam.getLerpProgress(delta)).getMatrix();
                Vector3f start = transform.transformPosition(new Vector3f(0.5f, 0.5f, 0));
                Vector3f end = transform.transformPosition(new Vector3f(0.5f, 0.5f, 1));
                Vec3d origin = beam.getLerpedPos(delta).subtract(camera).add(start.x, start.y, start.z);
                Vec3d direction = new Vec3d(end.x - start.x, end.y - start.y, end.z - start.z);
                if (direction.lengthSquared() < 0.01) continue;
                Vec3d side = direction.crossProduct(origin);
                if (side.lengthSquared() < 0.00001) side = direction.crossProduct(new Vec3d(0, 1, 0));
                side = side.normalize();
                Thread thread = entry.getValue();
                float recall = thread.recallTicks > 0 ? (now - thread.received + delta) / thread.recallTicks : -1;
                float fade = recall >= 0 ? Math.clamp((1 - recall) * 4, 0, 1) : 1;
                for (int pulse = 0; pulse < 2; pulse++) {
                    float fraction = pulseFraction(now % 100000 + delta, entry.getKey() + pulse * 47, recall);
                    Vec3d head = origin.add(direction.multiply(fraction));
                    if (head.lengthSquared() < 1.0) continue;
                    float threadFade = fade;
                    if (head.lengthSquared() < 4.0) {
                        threadFade *= (float) Math.clamp((head.length() - 1.0) / 1.0, 0.0, 1.0);
                    }
                    double tailLength = Math.min(0.13, 0.9 / direction.length());
                    Vec3d tail = origin.add(direction.multiply(Math.min(1, fraction + tailLength)));
                    for (int layer = 0; layer < 2; layer++) {
                        Vec3d width = side.multiply(layer == 0 ? 0.026 : 0.006);
                        int alpha = (int) ((layer == 0 ? 65 : 210) * threadFade);
                        Vec3d[] corners = {head.subtract(width), head.add(width), tail.add(width), tail.subtract(width)};
                        for (int v = 0; v < 4; v++) {
                            Vec3d point = corners[v];
                            buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                                    .color(layer == 0 ? 95 : 210, 220, 255, v < 2 ? alpha : 0);
                        }
                    }
                }
            }
            var built = buffer.endNullable();
            if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        } finally {
            RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
        }
    }
}
