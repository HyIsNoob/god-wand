package net.godwand.client;

import net.godwand.client.compat.GodWandGeometry;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.godwand.client.compat.PrimitiveTopology;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.godwand.network.CannonPressurePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;

public final class CannonPressureRenderer {
    private record Burst(CannonPressurePayload payload, long tick) {}
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static ClientLevel world;
    public static void reset() { BURSTS.clear(); world = null; }
    public static void receive(CannonPressurePayload payload) {
        var current = Minecraft.getInstance().level;
        if (current != world) { reset(); world = current; }
        if (world == null || payload.direction().lengthSqr() < 0.001) return;
        if (BURSTS.size() >= 32) BURSTS.removeFirst();
        BURSTS.add(new Burst(payload, world.getGameTime()));
    }

    public static Vec3 conePoint(float t, float angle, float expansion, int level) {
        double radius = t * (1.1 + level * 0.3) * expansion;
        return new Vec3(Math.cos(angle) * radius, Math.sin(angle) * radius,
                -0.12 - t * (1.4 + level * 0.35) * expansion);
    }

    public static void render(PoseStack matrices, Camera camera, float delta) {
        ClientLevel current = Minecraft.getInstance().level;
        if (current != world) { reset(); world = current; }
        if (world == null || BURSTS.isEmpty()) return;
        long now = world.getGameTime();
        BURSTS.removeIf(b -> now - b.tick >= 8);
        if (BURSTS.isEmpty()) return;
        var matrix = matrices.last().pose();
        GodWandGeometry.enableBlend(); GodWandGeometry.defaultBlendFunc();
        GodWandGeometry.enableDepthTest(); GodWandGeometry.depthMask(false); GodWandGeometry.disableCull();
        GodWandGeometry.colored();
        try {
            for (Burst burst : BURSTS) {
                var p = burst.payload;
                Vec3 forward = p.direction().normalize();
                Vec3 side = forward.cross(Math.abs(forward.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
                Vec3 up = forward.cross(side);
                Vec3 origin = p.center().subtract(camera.position());
                float age = (now - burst.tick + delta) / 8f;
                float expansion = 0.15f + 0.85f * (1 - (1 - age) * (1 - age));
                for (int band = 0; band < 3; band++) {
                    var buffer = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
                    for (int step = 0; step <= 64; step++) {
                        float angle = step * (float)Math.PI / 32;
                        for (int edge = 0; edge < 2; edge++) {
                            float t = 0.25f + band * 0.3f + edge * 0.12f;
                            Vec3 local = conePoint(t, angle, expansion, p.level());
                            Vec3 point = origin.add(side.scale(local.x)).add(up.scale(local.y)).add(forward.scale(local.z));
                            int alpha = (int)(170 * (1 - age) * (1 - age) * (edge == 0 ? 1 : 0.15));
                            buffer.addVertex(matrix, (float)point.x, (float)point.y, (float)point.z).setColor(220, 240, 255, alpha);
                        }
                    }
                    GodWandGeometry.draw(buffer.buildOrThrow());
                }
            }
        } finally {
            GodWandGeometry.depthMask(true); GodWandGeometry.enableCull(); GodWandGeometry.defaultBlendFunc(); GodWandGeometry.disableBlend();
        }
    }
}

