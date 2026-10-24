package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.godwand.network.SpatialAuraPayload;
import net.godwand.spatial.SpatialMath;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import java.util.HashMap;
import java.util.Map;

public final class SpatialAuraRenderer {
    private static final Map<Integer, Long> ACTIVE = new HashMap<>();
    private static final Map<Integer, Long> ACTIVATIONS = new HashMap<>();
    private record Hit(int playerId, Vec3d normal, long tick) {}
    private static final java.util.List<Hit> HITS = new java.util.ArrayList<>();
    private static ClientWorld world;
    private static final float[] EDGES = {-1, -0.28f, 0.28f, 1};

    public static void reset() { ACTIVE.clear(); ACTIVATIONS.clear(); HITS.clear(); world = null; }

    public static void receive(SpatialAuraPayload payload) {
        var current = MinecraftClient.getInstance().world;
        if (current != world) { reset(); world = current; }
        if (current == null) return;
        if (payload.active()) {
            if (!ACTIVE.containsKey(payload.playerId())) {
                ACTIVATIONS.put(payload.playerId(), current.getTime());
            }
            ACTIVE.put(payload.playerId(), current.getTime() + 40);
        } else {
            ACTIVE.remove(payload.playerId());
            ACTIVATIONS.remove(payload.playerId());
            HITS.removeIf(h -> h.playerId == payload.playerId());
        }
        if (payload.active() && payload.hitNormal().lengthSquared() > 0.01) {
            if (HITS.size() >= 32) HITS.removeFirst();
            HITS.add(new Hit(payload.playerId(), payload.hitNormal().normalize(), current.getTime()));
        }
    }

    public static void render(WorldRenderContext context) {
        if (context.world() != world) { reset(); return; }
        ACTIVE.values().removeIf(expiry -> expiry < world.getTime());
        ACTIVATIONS.entrySet().removeIf(e -> world.getTime() - e.getValue() >= 20);
        HITS.removeIf(h -> world.getTime() - h.tick >= 18);
        if (ACTIVE.isEmpty()) return;
        var matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        float delta = context.tickCounter().getTickDelta(false);
        double time = world.getTime() + delta;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        try {
            for (int id : ACTIVE.keySet()) {
                var entity = world.getEntityById(id);
                if (entity == null || entity.squaredDistanceTo(camera) > 96 * 96) continue;
                Vec3d center = entity.getLerpedPos(delta).add(0, 1.5, 0);
                matrices.push();
                try {
                    matrices.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
                    var buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                    boolean firstPerson = !context.camera().isThirdPerson() && context.camera().getFocusedEntity() == entity;
                    emitField(buffer, matrices.peek().getPositionMatrix(), entity.getRotationVec(delta), time, firstPerson);
                    Long actStart = ACTIVATIONS.get(id);
                    if (actStart != null) {
                        double actProgress = (time - actStart) / 18.0;
                        if (actProgress >= 0 && actProgress <= 1.0) {
                            emitActivationShell(buffer, matrices.peek().getPositionMatrix(), entity.getRotationVec(delta), actProgress, time, firstPerson);
                        }
                    }
                    for (Hit hit : HITS) if (hit.playerId == id) {
                        emitHit(buffer, matrices.peek().getPositionMatrix(), hit.normal, (time - hit.tick) / 18,
                                entity.getRotationVec(delta), firstPerson);
                    }
                    BufferRenderer.drawWithGlobalProgram(buffer.end());
                } finally {
                    matrices.pop();
                }
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    static void emitField(VertexConsumer vertices, Matrix4f matrix, Vec3d look, double time, boolean firstPerson) {
        for (int strand = 0; strand < 6; strand++) {
            emitRibbon(vertices, matrix, look, time, firstPerson, strand, true);
        }
        for (int arc = 0; arc < 2; arc++) {
            emitRibbon(vertices, matrix, look, time, firstPerson, arc, false);
        }
    }

    private static void emitRibbon(VertexConsumer vertices, Matrix4f matrix, Vec3d look, double time,
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
                    Vec3d normal = new Vec3d(Math.cos(phi) * Math.cos(theta), Math.sin(phi), Math.cos(phi) * Math.sin(theta));
                    double radius = 3.3 + Math.sin(phi * 5 - theta * 2 + time * 0.025) * 0.018;
                    double front = normal.dotProduct(look);
                    double visibility = firstPerson ? 1 - smooth(0.55, 0.92, front) : 1;
                    double ends = Math.pow(Math.sin(t * Math.PI), 1.3);
                    double heightFade = smooth(-0.46, -0.32, phi) * (1 - smooth(1.12, 1.48, phi));
                    double feather = Math.abs(edge) > 0.9 ? 0 : 1;
                    double flow = 0.7 + 0.3 * Math.sin(t * Math.PI);
                    int alpha = (int) ((rising ? 205 : 165) * visibility * ends * heightFade * feather * flow * lifetimeFade);
                    vertices.vertex(matrix, (float) (normal.x * radius), (float) (normal.y * radius), (float) (normal.z * radius))
                            .color(rising ? 150 : 200, rising ? 235 : 215, 255, alpha);
                }
            }
        }
    }

    static void emitHit(VertexConsumer vertices, Matrix4f matrix, Vec3d normal, double age, Vec3d look, boolean firstPerson) {
        if (age < 0 || age >= 1 || normal.lengthSquared() < 0.01) return;
        normal = normal.normalize();
        Vec3d side = normal.crossProduct(Math.abs(normal.y) < 0.95 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0)).normalize();
        Vec3d up = normal.crossProduct(side);
        for (int ring = 0; ring < 2; ring++) {
            double wave = Math.max(0, age - ring * 0.12);
            double spread = 0.04 + wave * 0.56;
            for (int segment = 0; segment < 48; segment++) for (int lane = 0; lane < 3; lane++) {
                for (int corner = 0; corner < 4; corner++) {
                    double angle = (segment + (corner == 1 || corner == 2 ? 1 : 0)) * Math.PI / 24;
                    double edge = EDGES[lane + (corner >= 2 ? 1 : 0)];
                    double phi = spread + edge * (0.018 + wave * 0.025);
                    Vec3d n = normal.multiply(Math.cos(phi)).add(side.multiply(Math.sin(phi) * Math.cos(angle)))
                            .add(up.multiply(Math.sin(phi) * Math.sin(angle)));
                    double radius = 3.3 - 0.18 * Math.pow(1 - age, 3) * Math.exp(-phi * phi * 8);
                    double visibility = firstPerson ? 1 - 0.88 * smooth(0.55, 0.92, n.dotProduct(look)) : 1;
                    int alpha = (int)(210 * Math.pow(1 - age, 2) * visibility * (Math.abs(edge) > 0.9 ? 0 : 1));
                    vertices.vertex(matrix, (float)(n.x * radius), (float)(n.y * radius), (float)(n.z * radius)).color(185, 236, 255, alpha);
                }
            }
        }
    }

    private static double smooth(double start, double end, double value) {
        double t = Math.clamp((value - start) / (end - start), 0, 1);
        return t * t * (3 - 2 * t);
    }

    static void emitActivationShell(VertexConsumer vertices, Matrix4f matrix, Vec3d look, double progress, double time, boolean firstPerson) {
        if (progress < 0 || progress >= 1.0) return;
        double ease = 1.0 - Math.pow(1.0 - progress, 3.0);
        double radius = 1.1 + 2.3 * ease;
        double alphaFade = progress < 0.15 ? (progress / 0.15) : Math.pow(1.0 - progress, 1.6);
        if (alphaFade <= 0.005) return;

        int latSteps = 12;
        int lonSteps = 24;

        for (int i = 0; i < latSteps; i++) {
            double phi1 = -Math.PI / 2.0 + Math.PI * i / latSteps;
            double phi2 = -Math.PI / 2.0 + Math.PI * (i + 1) / latSteps;
            double cosPhi1 = Math.cos(phi1), sinPhi1 = Math.sin(phi1);
            double cosPhi2 = Math.cos(phi2), sinPhi2 = Math.sin(phi2);

            for (int j = 0; j < lonSteps; j++) {
                double theta1 = 2.0 * Math.PI * j / lonSteps + time * 0.03;
                double theta2 = 2.0 * Math.PI * (j + 1) / lonSteps + time * 0.03;
                double cosTheta1 = Math.cos(theta1), sinTheta1 = Math.sin(theta1);
                double cosTheta2 = Math.cos(theta2), sinTheta2 = Math.sin(theta2);

                Vec3d n00 = new Vec3d(cosPhi1 * cosTheta1, sinPhi1, cosPhi1 * sinTheta1);
                Vec3d n01 = new Vec3d(cosPhi1 * cosTheta2, sinPhi1, cosPhi1 * sinTheta2);
                Vec3d n11 = new Vec3d(cosPhi2 * cosTheta2, sinPhi2, cosPhi2 * sinTheta2);
                Vec3d n10 = new Vec3d(cosPhi2 * cosTheta1, sinPhi2, cosPhi2 * sinTheta1);

                double frontDot = (n00.dotProduct(look) + n11.dotProduct(look)) * 0.5;
                double viewVis = firstPerson ? (1.0 - 0.45 * smooth(0.55, 0.95, frontDot)) : 1.0;
                double shimmer = 0.75 + 0.25 * Math.sin(theta1 * 3.0 + phi1 * 2.0 + time * 0.1);

                int r = (int) (140 + 50 * Math.sin(phi1 * 2.0));
                int g = (int) (225 + 30 * Math.cos(theta1 + time * 0.05));
                int b = 255;
                int a = (int) (140 * alphaFade * viewVis * shimmer);

                if (a > 3) {
                    vertices.vertex(matrix, (float)(n00.x * radius), (float)(n00.y * radius), (float)(n00.z * radius)).color(r, g, b, a);
                    vertices.vertex(matrix, (float)(n01.x * radius), (float)(n01.y * radius), (float)(n01.z * radius)).color(r, g, b, a);
                    vertices.vertex(matrix, (float)(n11.x * radius), (float)(n11.y * radius), (float)(n11.z * radius)).color(r, g, b, a);
                    vertices.vertex(matrix, (float)(n10.x * radius), (float)(n10.y * radius), (float)(n10.z * radius)).color(r, g, b, a);
                }
            }
        }

        emitEquatorRing(vertices, matrix, radius, alphaFade, new Vec3d(0, 1, 0));
        emitEquatorRing(vertices, matrix, radius * 1.015, alphaFade * 0.85, new Vec3d(0.707, 0.707, 0).normalize());
    }

    static void emitEquatorRing(VertexConsumer vertices, Matrix4f matrix, double radius, double alphaFade, Vec3d axis) {
        Vec3d side = axis.crossProduct(Math.abs(axis.y) < 0.95 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0)).normalize();
        Vec3d up = axis.crossProduct(side).normalize();
        int segments = 32;
        double halfWidth = 0.07;
        Vec3d offset = axis.multiply(halfWidth);
        int alpha = (int) (220 * alphaFade);
        if (alpha <= 3) return;

        for (int i = 0; i < segments; i++) {
            double a1 = 2.0 * Math.PI * i / segments;
            double a2 = 2.0 * Math.PI * (i + 1) / segments;

            Vec3d p1 = side.multiply(Math.cos(a1) * radius).add(up.multiply(Math.sin(a1) * radius));
            Vec3d p2 = side.multiply(Math.cos(a2) * radius).add(up.multiply(Math.sin(a2) * radius));

            vertices.vertex(matrix, (float)(p1.x - offset.x), (float)(p1.y - offset.y), (float)(p1.z - offset.z)).color(210, 245, 255, alpha);
            vertices.vertex(matrix, (float)(p2.x - offset.x), (float)(p2.y - offset.y), (float)(p2.z - offset.z)).color(210, 245, 255, alpha);
            vertices.vertex(matrix, (float)(p2.x + offset.x), (float)(p2.y + offset.y), (float)(p2.z + offset.z)).color(210, 245, 255, alpha);
            vertices.vertex(matrix, (float)(p1.x + offset.x), (float)(p1.y + offset.y), (float)(p1.z + offset.z)).color(210, 245, 255, alpha);
        }
    }
}
