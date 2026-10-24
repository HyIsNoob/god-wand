package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.godwand.network.SpatialAuraPayload;
import net.godwand.spatial.SpatialMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.HashMap;
import java.util.Map;

public final class SpatialAuraRenderer {
    private static final Map<Integer, Long> ACTIVE = new HashMap<>();
    private record Hit(int playerId, Vec3 normal, long tick) {}
    private static final java.util.List<Hit> HITS = new java.util.ArrayList<>();
    private static ClientLevel world;
    private static final float[] EDGES = {-1, -0.28f, 0.28f, 1};

    public static void reset() { ACTIVE.clear(); HITS.clear(); world = null; }

    public static void receive(SpatialAuraPayload payload) {
        var current = Minecraft.getInstance().level;
        if (current != world) { reset(); world = current; }
        if (current == null) return;
        if (payload.active()) ACTIVE.put(payload.playerId(), current.getGameTime() + 40);
        else {
            ACTIVE.remove(payload.playerId());
            HITS.removeIf(h -> h.playerId == payload.playerId());
        }
        if (payload.active() && payload.hitNormal().lengthSqr() > 0.01) {
            if (HITS.size() >= 32) HITS.removeFirst();
            HITS.add(new Hit(payload.playerId(), payload.hitNormal().normalize(), current.getGameTime()));
        }
    }

    public static void render(PoseStack matrices, Camera cameraObj, float tickDelta) {
        ClientLevel current = Minecraft.getInstance().level;
        if (current != world) { reset(); world = current; }
        if (world == null) return;
        ACTIVE.values().removeIf(expiry -> expiry < world.getGameTime());
        HITS.removeIf(h -> world.getGameTime() - h.tick >= 18);
        if (ACTIVE.isEmpty()) return;
        Vec3 camera = cameraObj.getPosition();
        float delta = tickDelta;
        double time = world.getGameTime() + delta;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            for (int id : ACTIVE.keySet()) {
                var entity = world.getEntity(id);
                if (entity == null || entity.distanceToSqr(camera) > 96 * 96) continue;
                Vec3 center = entity.getPosition(delta).add(0, 1.5, 0);
                matrices.pushPose();
                try {
                    matrices.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
                    var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                    boolean firstPerson = !cameraObj.isDetached() && cameraObj.getEntity() == entity;
                    emitField(buffer, matrices.last().pose(), entity.getViewVector(delta), time, firstPerson);
                    for (Hit hit : HITS) if (hit.playerId == id) {
                        emitHit(buffer, matrices.last().pose(), hit.normal, (time - hit.tick) / 18,
                                entity.getViewVector(delta), firstPerson);
                    }
                    BufferUploader.drawWithShader(buffer.buildOrThrow());
                } finally {
                    matrices.popPose();
                }
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    static void emitField(VertexConsumer vertices, Matrix4f matrix, Vec3 look, double time, boolean firstPerson) {
        for (int strand = 0; strand < 6; strand++) {
            emitRibbon(vertices, matrix, look, time, firstPerson, strand, true);
        }
        for (int arc = 0; arc < 2; arc++) {
            emitRibbon(vertices, matrix, look, time, firstPerson, arc, false);
        }
    }

    private static void emitRibbon(VertexConsumer vertices, Matrix4f matrix, Vec3 look, double time,
                                   boolean firstPerson, int strand, boolean rising) {
        double phase = time * (rising ? 0.006 : 0.004) + strand * (rising ? 0.618034 : 0.5);
        phase -= Math.floor(phase);
        double lifetimeFade = smooth(0, 0.12, phase) * (1 - smooth(0.86, 1, phase));
        double elevation = -0.42 + phase * 1.8;
        double rotation = strand * 2.399963 + time * (rising ? 0.009 : -0.012);
        for (int segment = 0; segment < 36; segment++) {
            for (int lane = 0; lane < 3; lane++) {
                for (int corner = 0; corner < 4; corner++) {
                    double t = (segment + (corner == 1 || corner == 2 ? 1 : 0)) / 36.0;
                    float edge = EDGES[lane + (corner >= 2 ? 1 : 0)];
                    double phi = rising ? elevation + (t - 0.5) * 1.05
                            : elevation + Math.sin(t * Math.PI) * 0.055 + edge * 0.042;
                    double theta = rising ? rotation + phi * 1.6 + edge * 0.058
                            : rotation + (t - 0.5) * 2.35;
                    Vec3 normal = new Vec3(Math.cos(phi) * Math.cos(theta), Math.sin(phi), Math.cos(phi) * Math.sin(theta));
                    double radius = 3.3 + Math.sin(phi * 5 - theta * 2 + time * 0.025) * 0.018;
                    double front = normal.dot(look);
                    double visibility = firstPerson ? 1 - smooth(0.55, 0.92, front) : 1;
                    double ends = Math.pow(Math.sin(t * Math.PI), 1.3);
                    double heightFade = smooth(-0.46, -0.32, phi) * (1 - smooth(1.12, 1.48, phi));
                    double feather = Math.abs(edge) > 0.9 ? 0 : 1;
                    double flow = 0.7 + 0.3 * Math.sin(t * Math.PI);
                    int alpha = (int) ((rising ? 205 : 165) * visibility * ends * heightFade * feather * flow * lifetimeFade);
                    vertices.addVertex(matrix, (float) (normal.x * radius), (float) (normal.y * radius), (float) (normal.z * radius))
                            .setColor(rising ? 150 : 200, rising ? 235 : 215, 255, alpha);
                }
            }
        }
    }

    static void emitHit(VertexConsumer vertices, Matrix4f matrix, Vec3 normal, double age, Vec3 look, boolean firstPerson) {
        if (age < 0 || age >= 1 || normal.lengthSqr() < 0.01) return;
        normal = normal.normalize();
        Vec3 side = normal.cross(Math.abs(normal.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 up = normal.cross(side);
        for (int ring = 0; ring < 2; ring++) {
            double wave = Math.max(0, age - ring * 0.12);
            double spread = 0.04 + wave * 0.56;
            for (int segment = 0; segment < 48; segment++) for (int lane = 0; lane < 3; lane++) {
                for (int corner = 0; corner < 4; corner++) {
                    double angle = (segment + (corner == 1 || corner == 2 ? 1 : 0)) * Math.PI / 24;
                    double edge = EDGES[lane + (corner >= 2 ? 1 : 0)];
                    double phi = spread + edge * (0.018 + wave * 0.025);
                    Vec3 n = normal.scale(Math.cos(phi)).add(side.scale(Math.sin(phi) * Math.cos(angle)))
                            .add(up.scale(Math.sin(phi) * Math.sin(angle)));
                    double radius = 3.3 - 0.18 * Math.pow(1 - age, 3) * Math.exp(-phi * phi * 8);
                    double visibility = firstPerson ? 1 - 0.88 * smooth(0.55, 0.92, n.dot(look)) : 1;
                    int alpha = (int)(210 * Math.pow(1 - age, 2) * visibility * (Math.abs(edge) > 0.9 ? 0 : 1));
                    vertices.addVertex(matrix, (float)(n.x * radius), (float)(n.y * radius), (float)(n.z * radius)).setColor(185, 236, 255, alpha);
                }
            }
        }
    }

    private static double smooth(double start, double end, double value) {
        double t = Math.clamp((value - start) / (end - start), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
