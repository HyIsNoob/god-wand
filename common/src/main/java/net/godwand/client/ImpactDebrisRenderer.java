package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.godwand.network.CannonPressurePayload;
import net.godwand.network.ImpactFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;

/** Bounded client meshes: surface fractures, billowing pressure fronts and ballistic stone facets. */
public final class ImpactDebrisRenderer {
    private static final int SEGMENTS = 64, RINGS = 12;
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static ClientLevel world;

    static final class Burst {
        final Vec3 center, normal, side, up;
        final double radius;
        final int kind;
        final long tick;
        final double[][] surface = new double[RINGS + 1][SEGMENTS];
        boolean sounded;
        Burst(Vec3 center, Vec3 normal, double radius, int kind, long tick) {
            this.center = center; this.normal = normal.normalize(); this.radius = radius; this.kind = kind;
            side = this.normal.cross(Math.abs(normal.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
            up = this.normal.cross(side);
            this.tick = tick;
        }
        void sampleSurface() {
            for (int r = 0; r <= RINGS; r++) for (int a = 0; a < SEGMENTS; a++) {
                Vec3 point = point(radius * r / RINGS, a * Math.PI * 2 / SEGMENTS, 0);
                var block = BlockPos.containing(point);
                double height = Double.NaN;
                if (world.getChunkSource().hasChunk(block.getX() >> 4, block.getZ() >> 4)) {
                    if (kind == 2) {
                        height = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, block.getX(), block.getZ()) - center.y;
                    } else {
                        var hit = world.clip(new ClipContext(point.add(this.normal.scale(3)), point.subtract(this.normal.scale(5)),
                                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
                        if (hit.getType() != HitResult.Type.MISS) height = hit.getLocation().subtract(point).dot(this.normal);
                    }
                }
                surface[r][a] = height;
            }
        }
        Vec3 point(double r, double angle, double height) {
            return center.add(side.scale(Math.cos(angle) * r)).add(up.scale(Math.sin(angle) * r)).add(normal.scale(height));
        }
        double height(double r, double angle) {
            double radial = Math.clamp(r / radius * RINGS, 0, RINGS);
            int low = (int)radial, high = Math.min(RINGS, low + 1);
            double angular = angle / (Math.PI * 2) * SEGMENTS;
            int a = Math.floorMod((int)Math.floor(angular), SEGMENTS), next = (a + 1) % SEGMENTS;
            double t = angular - Math.floor(angular);
            double near = surface[low][a] * (1 - t) + surface[low][next] * t;
            double far = surface[high][a] * (1 - t) + surface[high][next] * t;
            return near * (1 - (radial - low)) + far * (radial - low);
        }
    }

    public static void reset() { BURSTS.clear(); world = null; }
    private static void add(Vec3 center, Vec3 normal, double radius, int kind) {
        var current = Minecraft.getInstance().level;
        if (current != world) { reset(); world = current; }
        if (world == null || normal.lengthSqr() < 0.01) return;
        if (BURSTS.size() >= 8) BURSTS.removeFirst();
        var burst = new Burst(center, normal, radius, kind, world.getGameTime());
        burst.sampleSurface();
        BURSTS.add(burst);
    }
    public static void receive(CannonPressurePayload payload) {
        add(payload.center(), payload.surfaceNormal(), 3 + payload.level(), 0);
    }
    public static void receive(ImpactFxPayload payload) {
        Vec3 center = new Vec3(payload.x(), payload.y(), payload.z());
        if (payload.fxType() == ImpactFxPayload.TYPE_SUPERNOVA) add(center, new Vec3(0, 1, 0), 122, 2);
        // Cannon also sends a short slam for camera/post FX; its oriented event already supplies the mesh.
        if (payload.fxType() == ImpactFxPayload.TYPE_SEISMIC_SLAM && payload.durationTicks() >= 22)
            add(center, new Vec3(0, 1, 0), Math.clamp(10 + payload.intensity() * 3, 12, 26), 1);
    }

    public static double waveRadius(double radius, double age, boolean nova) {
        double progress = Math.clamp((age - (nova ? 6 : 0)) / (nova ? 42 : 18), 0, 1);
        return radius * Math.sqrt(progress);
    }
    public static Vec3 fragmentOffset(Vec3 normal, Vec3 outward, double age, double strength) {
        return outward.scale(age * strength * 0.18).add(normal.scale(age * strength * 0.24)).add(0, -0.025 * age * age, 0);
    }

    public static void render(PoseStack matrices, Camera cameraObj, float tickDelta) {
        ClientLevel current = Minecraft.getInstance().level;
        if (current != world) { reset(); world = current; }
        if (world == null) return;
        double now = world.getGameTime() + tickDelta;
        BURSTS.removeIf(b -> now - b.tick >= (b.kind == 2 ? 140 : 44));
        if (BURSTS.isEmpty()) return;
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false); RenderSystem.disableCull(); RenderSystem.setShader(GameRenderer::getPositionColorShader);
        matrices.pushPose();
        Vec3 camera = cameraObj.getPosition();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        try {
            var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Matrix4f matrix = matrices.last().pose();
            for (Burst b : BURSTS) {
                double age = now - b.tick;
                emit(buffer, matrix, b, age);
                if (b.kind == 2 && age >= 6 && !b.sounded && waveRadius(b.radius, age, true) >= Math.hypot(camera.x - b.center.x, camera.z - b.center.z)) {
                    b.sounded = true;
                    if (Math.abs(camera.y - (b.center.y + b.height(Math.hypot(camera.x - b.center.x, camera.z - b.center.z), Math.atan2(camera.subtract(b.center).dot(b.up), camera.subtract(b.center).dot(b.side))))) < 24) {
                        GodWandCinematicEngine.addTrauma(0.22f, 8);
                    }
                }
            }
            var built = buffer.build();
            if (built != null) BufferUploader.drawWithShader(built);
        } finally {
            matrices.popPose(); RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
        }
    }

    static void emit(VertexConsumer v, Matrix4f m, Burst b, double age) {
        boolean nova = b.kind == 2;
        double front = waveRadius(b.radius, age, nova);
        double fade = Math.clamp(1 - (age - (nova ? 32 : 12)) / (nova ? 30 : 20), 0, 1);
        if (front > 0.1 && fade > 0) {
            for (int layer = 0; layer < 2; layer++) for (int a = 0; a < SEGMENTS * 2; a++) {
                for (int row = 0; row < 3; row++) for (int corner = 0; corner < 4; corner++) {
                    double angle = (a + (corner == 1 || corner == 2 ? 1 : 0)) * Math.PI / SEGMENTS;
                    double t = (row + (corner >= 2 ? 1 : 0)) / 3.0;
                    double lobe = 0.7 + 0.2 * Math.sin(angle * 11 + age * 0.1) + 0.1 * Math.cos(angle * 19 - age * 0.13);
                    double r = Math.max(0.1, front - layer * (nova ? 4.8 : 0.55) - Math.sin(t * Math.PI) * (nova ? 4.0 : 0.8));
                    double ground = b.height(r, angle);
                    if (!Double.isFinite(ground)) ground = 0;
                    double height = ground + 0.1 + t * lobe * (nova ? 11.0 : 3.2) * fade;
                    double alpha = Math.sin(t * Math.PI) * fade * (layer == 0 ? 0.48 : 0.22);
                    if (!Double.isFinite(b.height(r, angle))) alpha = 0;
                    int shade = (int)(18 * Math.cos(angle - 0.8) * Math.sin(t * Math.PI));
                    vertex(v, m, b.point(r, angle, height), 160 + row * 20 + shade, 133 + row * 18 + shade, 113 + row * 17 + shade, (int)(alpha * 255));
                }
            }
        }
        if (nova) emitNovaPressure(v, m, b, age);
        if (!nova && age < 18) {
            for (int branch = 0; branch < 10; branch++) for (int step = 0; step < 5; step++) {
                double angle = branch * Math.PI * 0.2 + Math.sin(step * 2.7 + branch) * 0.075;
                double r = b.radius * (0.35 + step * 0.12);
                double next = r + b.radius * 0.13;
                double h = b.height(r, angle), h2 = b.height(next, angle + 0.025);
                if (!Double.isFinite(h) || !Double.isFinite(h2)) continue;
                Vec3 a = b.point(r, angle, h + 0.055), end = b.point(next, angle + 0.025, h2 + 0.055);
                Vec3 width = b.side.scale(-Math.sin(angle) * 0.04).add(b.up.scale(Math.cos(angle) * 0.04));
                quad(v, m, a.subtract(width), end.subtract(width), end.add(width), a.add(width), 255, 160, 75, (int)(200 * (1 - age / 18)));
            }
        }
        int count = nova ? 64 : 24;
        for (int i = 0; i < count; i++) {
            double angle = i * 2.399963;
            double fraction = 0.25 + 0.7 * Math.sqrt((i + 0.5) / count);
            double launch = nova ? 6 + fraction * fraction * 42 : fraction * fraction * 8;
            double t = age - launch;
            if (t < 0 || t > 24) continue;
            double r = b.radius * fraction, h = b.height(r, angle);
            if (!Double.isFinite(h)) continue;
            Vec3 outward = b.side.scale(Math.cos(angle)).add(b.up.scale(Math.sin(angle)));
            Vec3 point = b.point(r, angle, h + 0.1).add(fragmentOffset(b.normal, outward, t, nova ? 1.8 : 1.2));
            double size = (nova ? 0.23 : 0.12) * (0.7 + (i % 5) * 0.2);
            Vec3 x = b.side.scale(Math.cos(t * 0.18) * size).add(b.normal.scale(Math.sin(t * 0.18) * size));
            Vec3 y = b.up.scale(size * 0.7), z = x.cross(y).normalize().scale(size);
            int alpha = (int)(255 * Math.min(1, (24 - t) / 7));
            quad(v,m,point.add(x),point.add(y),point.add(z),point.subtract(y),112,102,92,alpha);
            quad(v,m,point.add(x),point.subtract(z),point.subtract(x),point.add(z),75,70,67,alpha);
            quad(v,m,point.add(y),point.subtract(x),point.subtract(y),point.subtract(z),151,135,111,alpha);
        }
        if (nova) emitNovaEmbers(v, m, b, age);
    }
    private static void emitNovaPressure(VertexConsumer v, Matrix4f m, Burst b, double age) {
        for (int wave = 0; wave < 3; wave++) {
            double t = age - 6 - wave * 5;
            if (t <= 0 || t >= 42) continue;
            double front = waveRadius(b.radius, age - wave * 5, true);
            double fade = Math.min(1, t / 3) * (1 - Math.clamp((t - 26) / 16, 0, 1));
            double width = 1.6 + front * .025;
            for (int segment = 0; segment < 128; segment++) for (int lane = 0; lane < 4; lane++) {
                for (int corner = 0; corner < 4; corner++) {
                    double angle = (segment + (corner == 1 || corner == 2 ? 1 : 0)) * Math.PI / 64;
                    double u = (lane + (corner >= 2 ? 1 : 0)) / 4.0;
                    double radius = front + (u - .5) * width * 2;
                    double ground = b.height(radius, angle);
                    if (!Double.isFinite(ground)) ground = 0;
                    double crest = Math.sin(u * Math.PI);
                    double lift = .22 + crest * (1.2 + wave * .4) * (0.85 + .15 * Math.sin(angle * 9 - t * .13));
                    int alpha = Double.isFinite(b.height(radius, angle)) ? (int)(220 * crest * fade / (1 + wave * .35)) : 0;
                    vertex(v, m, b.point(radius, angle, ground + lift), 255,
                            lane == 1 || lane == 2 ? 238 : 157, lane == 1 || lane == 2 ? 172 : 65, alpha);
                }
            }
        }
        if (age < 6 || age > 62) return;
        for (int branch = 0; branch < 18; branch++) for (int step = 0; step < 8; step++) {
            double r = b.radius * (.77 + step * .029);
            double start = 6 + 42 * Math.pow(r / b.radius, 2);
            double fade = Math.clamp((age - start) / 2, 0, 1) * Math.clamp((start + 16 - age) / 12, 0, 1);
            if (fade <= 0) continue;
            double angle = branch * Math.PI / 9 + Math.sin(branch * 3 + step * 2.4) * .012;
            double next = r + b.radius * .031;
            double h = b.height(r, angle), h2 = b.height(next, angle + .006);
            if (!Double.isFinite(h) || !Double.isFinite(h2)) continue;
            Vec3 a = b.point(r, angle, h + .075), end = b.point(next, angle + .006, h2 + .075);
            Vec3 width = b.side.scale(-Math.sin(angle) * .12).add(b.up.scale(Math.cos(angle) * .12));
            quad(v, m, a.subtract(width), end.subtract(width), end.add(width), a.add(width), 255, 190, 83, (int)(210 * fade));
        }
    }

    private static void emitNovaEmbers(VertexConsumer v, Matrix4f m, Burst b, double age) {
        for (int i = 0; i < 192; i++) {
            double fraction = Math.sqrt((i + .5) / 192), angle = i * 2.399963;
            double t = age - 6 - fraction * fraction * 42;
            if (t < 0 || t > 90) continue;
            double radius = b.radius * fraction, h = b.height(radius, angle);
            if (!Double.isFinite(h)) continue;
            Vec3 point = b.point(radius, angle + t * .0015, h + 8 + (i % 13) * 1.3 + t * .24 - t * t * .004);
            double size = .10 + (i % 5) * .035;
            Vec3 x = b.side.scale(size), y = b.normal.scale(size * (2 + Math.abs(.24 - t * .008) * 4));
            int alpha = (int)(230 * Math.min(1, t / 4) * Math.min(1, (90 - t) / 22));
            quad(v, m, point.subtract(x), point.subtract(y), point.add(x), point.add(y), 255, 202 + i % 36, 112, alpha);
        }
    }

    private static void vertex(VertexConsumer v, Matrix4f m, Vec3 p, int r, int g, int b, int a) {
        v.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setColor(r,g,b,Math.clamp(a,0,255));
    }
    private static void quad(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int r, int g, int blue, int alpha) {
        vertex(v,m,a,r,g,blue,alpha);vertex(v,m,b,r,g,blue,alpha);vertex(v,m,c,r,g,blue,alpha);vertex(v,m,d,r,g,blue,alpha);
    }
}
