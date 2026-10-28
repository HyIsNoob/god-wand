package net.godwand.client;

import net.godwand.client.compat.GodWandGeometry;


import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;

import net.godwand.client.compat.PrimitiveTopology;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.godwand.network.SingularityThreadsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.Display.RenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class SingularityThreadRenderer {
    private record Thread(long received, int recallTicks) {}
    private static final Map<Integer, Thread> THREADS = new HashMap<>();
    private static ClientLevel world;

    public static void reset() { THREADS.clear(); world = null; }
    public static void receive(SingularityThreadsPayload payload) {
        var current = Minecraft.getInstance().level;
        if (current != world) { reset(); world = current; }
        if (current == null) return;
        for (int id : payload.beamIds()) THREADS.put(id, new Thread(current.getGameTime(), payload.recallTicks()));
    }

    public static float pulseFraction(float time, int seed, float recallProgress) {
        float phase = Math.floorMod(seed * 31, 101) / 101f;
        if (recallProgress >= 0) return 1 - Math.clamp((recallProgress + phase * 0.12f) / 0.85f, 0, 1);
        float cycle = time * 0.032f + phase;
        return 1 - (cycle - (float) Math.floor(cycle));
    }

    public static void render(PoseStack matrices, Camera cameraObj, float tickDelta) {
        ClientLevel current = Minecraft.getInstance().level;
        if (current != world) { reset(); world = current; }
        if (world == null || THREADS.isEmpty()) return;
        long now = world.getGameTime();
        THREADS.values().removeIf(t -> now - t.received > (t.recallTicks > 0 ? t.recallTicks + 2 : 160));
        if (THREADS.isEmpty()) return;
        float delta = tickDelta;
        var camera = cameraObj.position();
        var matrix = matrices.last().pose();
        var buffer = GodWandGeometry.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
        GodWandGeometry.enableBlend();
        GodWandGeometry.additiveBlend();
        GodWandGeometry.enableDepthTest(); GodWandGeometry.depthMask(false); GodWandGeometry.disableCull();
        GodWandGeometry.colored();
        try {
            for (var entry : THREADS.entrySet()) {
                var entity = world.getEntity(entry.getKey());
                if (!(entity instanceof BlockDisplay beam) || beam.distanceToSqr(camera) > 96 * 96) continue;
                var state = beam.renderState();
                if (state == null) continue;
                var transform = state.transformation().get(beam.calculateInterpolationProgress(delta)).getMatrix();
                Vector3f start = transform.transformPosition(new Vector3f(0.5f, 0.5f, 0));
                Vector3f end = transform.transformPosition(new Vector3f(0.5f, 0.5f, 1));
                Vec3 origin = beam.getPosition(delta).subtract(camera).add(start.x, start.y, start.z);
                Vec3 direction = new Vec3(end.x - start.x, end.y - start.y, end.z - start.z);
                if (direction.lengthSqr() < 0.01) continue;
                Vec3 side = direction.cross(origin);
                if (side.lengthSqr() < 0.00001) side = direction.cross(new Vec3(0, 1, 0));
                side = side.normalize();
                Thread thread = entry.getValue();
                float recall = thread.recallTicks > 0 ? (now - thread.received + delta) / thread.recallTicks : -1;
                float fade = recall >= 0 ? Math.clamp((1 - recall) * 4, 0, 1) : 1;
                for (int pulse = 0; pulse < 2; pulse++) {
                    float fraction = pulseFraction(now % 100000 + delta, entry.getKey() + pulse * 47, recall);
                    Vec3 head = origin.add(direction.scale(fraction));
                    if (head.lengthSqr() < 1.0) continue;
                    float threadFade = fade;
                    if (head.lengthSqr() < 4.0) {
                        threadFade *= (float) Math.clamp((head.length() - 1.0) / 1.0, 0.0, 1.0);
                    }
                    double tailLength = Math.min(0.13, 0.9 / direction.length());
                    Vec3 tail = origin.add(direction.scale(Math.min(1, fraction + tailLength)));
                    for (int layer = 0; layer < 2; layer++) {
                        Vec3 width = side.scale(layer == 0 ? 0.026 : 0.006);
                        int alpha = (int) ((layer == 0 ? 65 : 210) * threadFade);
                        Vec3[] corners = {head.subtract(width), head.add(width), tail.add(width), tail.subtract(width)};
                        for (int v = 0; v < 4; v++) {
                            Vec3 point = corners[v];
                            buffer.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                                    .setColor(layer == 0 ? 95 : 210, 220, 255, v < 2 ? alpha : 0);
                        }
                    }
                }
            }
            var built = buffer.build();
            if (built != null) GodWandGeometry.draw(built);
        } finally {
            GodWandGeometry.depthMask(true); GodWandGeometry.enableCull(); GodWandGeometry.defaultBlendFunc(); GodWandGeometry.disableBlend();
        }
    }
}

