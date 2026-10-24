package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.godwand.item.GodWandItem;
import net.godwand.network.ImpactFxPayload;
import net.godwand.skill.SupernovaSequence;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class SupernovaAccretionRenderer {
    private static ClientWorld world;
    private static Vec3d center = Vec3d.ZERO;
    private static long start;
    private static boolean active;
    private static ClientWorld chargeWorld;
    private static Vec3d chargeCenter = Vec3d.ZERO;
    private static float chargeTicks, previousChargeTicks;
    private static boolean charging;
    public record SkyFrame(Vec3d center, float scale, float collapse, float pulseRadius, float pulseStrength, float time) {}
    private static final float[] EDGES = {-1, -0.22f, 0.22f, 1};

    public static void reset() { active = false; world = null; chargeWorld = null; chargeTicks = previousChargeTicks = 0; charging = false; }
    public static void receive(ImpactFxPayload payload) {
        if (payload.type() == ImpactFxPayload.TYPE_SUPERNOVA_IMPACT || payload.type() == ImpactFxPayload.TYPE_SUPERNOVA) return;
        if (payload.type() != ImpactFxPayload.TYPE_BLACK_HOLE) return;
        reset();
        world = MinecraftClient.getInstance().world;
        if (world == null) return;
        center = new Vec3d(payload.x(), payload.y(), payload.z());
        start = world.getTime();
        active = true;
        var player = MinecraftClient.getInstance().player;
        if (player != null) GodWandCinematicEngine.addTrauma(.22f * GodWandCinematicEngine.distanceFactor(
                net.godwand.network.CinematicFxPayload.EVENT_SUPERNOVA_COMPRESS, player.getPos().distanceTo(center)), 6);
    }
    public static void tickCharge(MinecraftClient client) {
        if (client.world != chargeWorld) { chargeTicks = previousChargeTicks = 0; chargeWorld = client.world; charging = false; }
        previousChargeTicks = chargeTicks;
        if (client.player != null && client.world != null && client.player.isUsingItem()) {
            var stack = client.player.getActiveItem();
            if (stack.getItem() instanceof GodWandItem && GodWandItem.getSkillMode(stack) == GodWandItem.MODE_SUPERNOVA) {
                if (!charging || client.player.getItemUseTime() < chargeTicks) {
                    var target = net.godwand.skill.SupernovaManager.findTarget(client.player);
                    if (target == null) {
                        chargeTicks = Math.max(0, chargeTicks - 8);
                        return;
                    }
                    chargeCenter = target.toCenterPos().add(0, 20, 0);
                    charging = true;
                }
                chargeTicks = Math.clamp(client.player.getItemUseTime(), 0, SupernovaSequence.CHARGE_TICKS);
                return;
            }
        }
        charging = false;
        chargeTicks = Math.max(0, chargeTicks - 8);
    }
    public static SkyFrame skyFrame(float delta) {
        double age = skyAge(delta);
        if (age >= 0 && (SupernovaSequence.skyScale(age) > .001 || SupernovaSequence.skyPulseStrength(age) > .001))
            return new SkyFrame(center, SupernovaSequence.skyScale(age), SupernovaSequence.skyCollapse(age),
                    SupernovaSequence.skyPulseRadius(age), SupernovaSequence.skyPulseStrength(age), (float)age + SupernovaSequence.CHARGE_TICKS);
        float ticks = previousChargeTicks + (chargeTicks - previousChargeTicks) * delta;
        return ticks > 0 ? new SkyFrame(chargeCenter, SupernovaSequence.chargeRiftScale(ticks), 0, 0, 0, ticks) : null;
    }
    public static double age(float delta) {
        double age = skyAge(delta);
        return age < net.godwand.skill.SupernovaManager.DETONATION_TICK ? age : -1;
    }
    public static double skyAge(float delta) {
        var current = MinecraftClient.getInstance();
        return active && current != null && world == current.world && world.getTime() - start < net.godwand.skill.SupernovaManager.MAX_LIFETIME_TICKS
                ? world.getTime() - start + delta : -1;
    }
    public static Vec3d center() { return center; }

    public static void render(WorldRenderContext context) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        float delta = context.tickCounter().getTickDelta(false);
        double age = age(delta);
        boolean marker = age < 0 && charging && chargeTicks > 0 && net.godwand.config.GodWandConfig.CLIENT.targetMarker;
        boolean preImpactWave = age >= 330 && age <= 342;
        if (!marker && !preImpactWave && (age < 0 || SupernovaSequence.ribbonVisibility(age) <= 0)) return;
        Vec3d origin = marker ? chargeCenter : center;
        var matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        matrices.push();
        matrices.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false); RenderSystem.disableCull(); RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        try {
            var vertices = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            if (marker) emitChargeMarker(vertices, matrices.peek().getPositionMatrix(), chargeTicks / SupernovaSequence.CHARGE_TICKS);
            else emit(vertices, matrices.peek().getPositionMatrix(), age);
            var built = vertices.endNullable();
            if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        } finally {
            matrices.pop(); RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
        }
    }

    static void emitChargeMarker(VertexConsumer v, Matrix4f m, float charge) {
        float pulse = 0.82f + 0.18f * (float) Math.sin((System.currentTimeMillis() % 10000) * 0.014);
        float bottomY = -20.0f;
        float topY = 120.0f;

        // CỘT SÁNG TRỤ THIÊN MẢNH MAI (Slender Vertical Celestial Pillar - rõ ràng, định vị chuẩn xác, không che khuất tầm nhìn)
        for (int i = 0; i < 4; i++) {
            double angle = i * (Math.PI / 4.0);
            Vec3d side = new Vec3d(Math.cos(angle), 0, Math.sin(angle));

            // Layer 0: Lõi chùm sáng sắc nét (Sharp Slender Core Beam)
            double coreW = 0.12 + charge * 0.08;
            Vec3d coreSide = side.multiply(coreW);
            int coreAlpha = (int) (220 * pulse * (0.6f + charge * 0.4f));
            vertex(v, m, coreSide.negate().add(0, bottomY, 0), 255, 235, 250, coreAlpha);
            vertex(v, m, coreSide.add(0, bottomY, 0), 255, 235, 250, coreAlpha);
            vertex(v, m, coreSide.add(0, topY, 0), 255, 180, 240, (int) (coreAlpha * 0.4f));
            vertex(v, m, coreSide.negate().add(0, topY, 0), 255, 180, 240, (int) (coreAlpha * 0.4f));

            // Layer 1: Hào quang tím nhạt tinh tế (Subtle Radiant Halo)
            double haloW = 0.38 + charge * 0.22;
            Vec3d haloSide = side.multiply(haloW);
            int haloAlpha = (int) (80 * pulse * (0.5f + charge * 0.5f));
            vertex(v, m, haloSide.negate().add(0, bottomY, 0), 210, 50, 200, haloAlpha);
            vertex(v, m, haloSide.add(0, bottomY, 0), 210, 50, 200, haloAlpha);
            vertex(v, m, haloSide.add(0, topY, 0), 160, 20, 180, 0);
            vertex(v, m, haloSide.negate().add(0, topY, 0), 160, 20, 180, 0);
        }
    }

    private static double streamPhase(double age, int strand) {
        double travel = age * (0.006 + SupernovaSequence.pressure(age) * 0.009) + strand * 0.618034;
        return travel - Math.floor(travel);
    }

    public static Vec3d streamPoint(double age, int strand, double tail) {
        double pressure = SupernovaSequence.pressure(age);
        double collapse = SupernovaSequence.swallow(age, 0.5);
        double travel = streamPhase(age, strand) - tail * 0.30;
        travel = Math.max(0, travel);
        double core = SupernovaSequence.coreRadius(age);
        double radius = core * 0.65 + Math.pow(1 - travel, 1.8) * (15 + 30 * pressure) * (1 - collapse * 0.80);
        double angle = strand * 2.399963 + travel * (3.2 + pressure * 4.5) + age * 0.012;
        double elevation = Math.sin(strand * 1.7 + travel * 2.5) * 0.42;
        return new Vec3d(Math.cos(angle) * radius, Math.sin(elevation) * radius, Math.sin(angle) * radius);
    }

    static void emit(VertexConsumer v, Matrix4f m, double age) {
        if (age >= 330 && age <= 342) {
            emitPreImpactShockwave(v, m, age);
        }
        double visible = SupernovaSequence.ribbonVisibility(age);
        if (visible <= 0) return;
        double pressure = SupernovaSequence.pressure(age);
        for (int strand = 0; strand < 12; strand++) for (int segment = 0; segment < 48; segment++) {
            for (int lane = 0; lane < 3; lane++) for (int corner = 0; corner < 4; corner++) {
                double t = (segment + (corner == 1 || corner == 2 ? 1 : 0)) / 48.0;
                double edge = EDGES[lane + (corner >= 2 ? 1 : 0)];
                Vec3d point = streamPoint(age, strand, t);
                Vec3d width = new Vec3d(Math.sin(strand * 2.4), 0.8, Math.cos(strand * 2.4)).normalize();
                double feather = Math.abs(edge) > 0.9 ? 0 : 1;
                double taper = Math.pow(Math.sin(t * Math.PI), 0.8);
                double cycle = streamPhase(age,strand);
                double lifetime = SupernovaSequence.phase(cycle,0,0.12) * (1 - SupernovaSequence.phase(cycle,0.86,1));
                point = point.add(width.multiply(edge * (0.40 + pressure * 0.80) * taper));
                vertex(v,m,point,lane == 1 ? 255 : 220, lane == 1 ? 170 : 55, lane == 1 ? 225 : 200,
                        (int)(visible * feather * taper * lifetime * (125 + pressure * 85)));
            }
        }
        double collapse = SupernovaSequence.swallow(age, 0);
        for (int orbit = 0; orbit < 3; orbit++) {
            double radius = SupernovaSequence.coreRadius(age) * (1.45 + (0.65 + orbit * 0.48) * (1 - collapse));
            arc(v,m,radius,age * (0.015 + pressure * 0.045) + orbit * 2.4,0.25 + orbit * 0.42,0,
                    visible * 0.85,0.28 + pressure * 0.25,235,65,205);
        }
        emitInfallSparks(v, m, age, visible, pressure);
    }

    private static void emitInfallSparks(VertexConsumer v, Matrix4f m, double age, double visible, double pressure) {
        for (int spark = 0; spark < 24; spark++) {
            double cycle = streamPhase(age, spark + 20);
            double life = SupernovaSequence.phase(cycle, 0, .12) * (1 - SupernovaSequence.phase(cycle, .86, 1));
            for (int segment = 0; segment < 5; segment++) {
                double t = segment / 45.0;
                Vec3d a = streamPoint(age, spark + 20, t), b = streamPoint(age, spark + 20, t + 1 / 45.0);
                Vec3d width = b.subtract(a).crossProduct(new Vec3d(.3, 1, .2)).normalize()
                        .multiply((.05 + pressure * .08) * (1 - segment / 5.0));
                int alpha = (int)(230 * visible * life * (1 - segment / 5.0));
                vertex(v,m,a.subtract(width),255,190,230,alpha);
                vertex(v,m,b.subtract(width),235,65,205,alpha);
                vertex(v,m,b.add(width),235,65,205,alpha);
                vertex(v,m,a.add(width),255,190,230,alpha);
            }
        }
        double core = SupernovaSequence.coreRadius(age);
        for (int strand = 0; strand < 4; strand++) for (int segment = 0; segment < 6; segment++) {
            double wave = .5 + .5 * Math.sin(age * .07 + strand * 1.8);
            double a = strand * 1.6 + age * .012 + segment * .12;
            double b = a + .12;
            double radius = core * (1.7 + strand * .15);
            double bend = Math.sin(segment * 2.3 + strand) * core * .035;
            Vec3d p = new Vec3d(Math.cos(a) * radius, Math.sin(a) * radius * .8 + bend, Math.sin(a) * radius * .65);
            Vec3d q = new Vec3d(Math.cos(b) * radius, Math.sin(b) * radius * .8 - bend, Math.sin(b) * radius * .65);
            Vec3d width = new Vec3d(.025 + pressure * .025, .025, 0);
            int alpha = (int)(180 * visible * wave * Math.sin((segment + .5) / 6 * Math.PI));
            vertex(v,m,p.subtract(width),255,145,230,alpha);
            vertex(v,m,q.subtract(width),255,145,230,alpha);
            vertex(v,m,q.add(width),255,145,230,alpha);
            vertex(v,m,p.add(width),255,145,230,alpha);
        }
    }

    private static void arc(VertexConsumer v, Matrix4f m, double radius, double rotation, double tilt, double y,
                            double strength, double width, int red, int green, int blue) {
        for (int segment = 0; segment < 64; segment++) for (int lane = 0; lane < 3; lane++) for (int corner = 0; corner < 4; corner++) {
            double t = (segment + (corner == 1 || corner == 2 ? 1 : 0)) / 64.0;
            double edge = EDGES[lane + (corner >= 2 ? 1 : 0)];
            double angle = rotation + t * 2.4;
            double r = radius + edge * width;
            Vec3d point = new Vec3d(Math.cos(angle)*r,Math.sin(angle)*r*Math.sin(tilt)+y,Math.sin(angle)*r*Math.cos(tilt));
            vertex(v,m,point,red,green,blue,(int)(220 * strength * Math.sin(t*Math.PI) * (Math.abs(edge)>0.9?0:1)));
        }
    }
    private static void vertex(VertexConsumer v, Matrix4f m, Vec3d point, int r, int g, int b, int alpha) {
        v.vertex(m,(float)point.x,(float)point.y,(float)point.z).color(r,g,b,Math.clamp(alpha,0,255));
    }

    private static void emitPreImpactShockwave(VertexConsumer v, Matrix4f m, double age) {
        double waveT = Math.clamp((age - 330.0) / 10.0, 0.0, 1.0);
        float alpha = (float) Math.sin(waveT * Math.PI);
        if (alpha <= 0.001f) return;

        double radius = 1.5 + Math.pow(waveT, 0.55) * 32.0;
        double width = 0.8 + waveT * 2.5;

        // 1. Sóng xung kích xích đạo (Equatorial Shockwave Ring)
        emitShockwaveRing(v, m, radius, width, 0.0, 0.0, alpha);
        // 2. Sóng xung kích nghiêng nhẹ (Secondary Tilted Ring)
        emitShockwaveRing(v, m, radius * 0.92, width * 0.85, 0.28, waveT * 0.5, alpha * 0.75f);
    }

    private static void emitShockwaveRing(VertexConsumer v, Matrix4f m, double radius, double width, double tilt, double rotation, float alpha) {
        int segments = 64;
        for (int seg = 0; seg < segments; seg++) {
            double a1 = rotation + (seg * 2.0 * Math.PI) / segments;
            double a2 = rotation + ((seg + 1) * 2.0 * Math.PI) / segments;

            double rIn = Math.max(0.1, radius - width);
            double rMid = radius;
            double rOut = radius + width;

            Vec3d p1In = new Vec3d(Math.cos(a1) * rIn, Math.sin(a1) * rIn * Math.sin(tilt), Math.sin(a1) * rIn * Math.cos(tilt));
            Vec3d p2In = new Vec3d(Math.cos(a2) * rIn, Math.sin(a2) * rIn * Math.sin(tilt), Math.sin(a2) * rIn * Math.cos(tilt));
            Vec3d p1Mid = new Vec3d(Math.cos(a1) * rMid, Math.sin(a1) * rMid * Math.sin(tilt), Math.sin(a1) * rMid * Math.cos(tilt));
            Vec3d p2Mid = new Vec3d(Math.cos(a2) * rMid, Math.sin(a2) * rMid * Math.sin(tilt), Math.sin(a2) * rMid * Math.cos(tilt));
            Vec3d p1Out = new Vec3d(Math.cos(a1) * rOut, Math.sin(a1) * rOut * Math.sin(tilt), Math.sin(a1) * rOut * Math.cos(tilt));
            Vec3d p2Out = new Vec3d(Math.cos(a2) * rOut, Math.sin(a2) * rOut * Math.sin(tilt), Math.sin(a2) * rOut * Math.cos(tilt));

            int aIn = (int) (60 * alpha);
            int aMid = (int) (235 * alpha);
            int aOut = 0;

            // Layer 1: In -> Mid (gradient from soft purple to luminous pink core)
            vertex(v, m, p1In, 210, 45, 230, aIn);
            vertex(v, m, p2In, 210, 45, 230, aIn);
            vertex(v, m, p2Mid, 255, 140, 250, aMid);
            vertex(v, m, p1Mid, 255, 140, 250, aMid);

            // Layer 2: Mid -> Out (gradient from luminous pink core to transparent magenta edge)
            vertex(v, m, p1Mid, 255, 140, 250, aMid);
            vertex(v, m, p2Mid, 255, 140, 250, aMid);
            vertex(v, m, p2Out, 220, 30, 210, aOut);
            vertex(v, m, p1Out, 220, 30, 210, aOut);
        }
    }

}
