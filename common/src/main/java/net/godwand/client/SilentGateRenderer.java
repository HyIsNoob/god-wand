package net.godwand.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.godwand.network.SilentGatePayload;
import net.godwand.skill.SilentGateSequence;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client state of every visible Silent Gate cast. Geometry is drawn on top of {@link #localFrame} / {@link #shots}. */
public final class SilentGateRenderer {
    /** {@code age}: ticks since the first click; {@code commandAge}: ticks since the second click (-1 before it). */
    public record Frame(int casterId, long startedAt, int phase, double age, double commandAge,
            Vec3 anchor, float yaw, long seed, int skyCircles, int barrageTicks) {}
    public record Shot(int circle, Vec3 hit, long tick) {}

    static final int SHOT_MEMORY_TICKS = 10;
    private static final class Cast {
        long startedAt, phaseAt, commandAt = -1;
        int phase, skyCircles, barrageTicks;
        Vec3 anchor;
        float yaw;
        long seed;
        final ArrayDeque<Shot> shots = new ArrayDeque<>();
    }
    private static final Map<Integer, Cast> CASTS = new LinkedHashMap<>();
    // Newest cast and the cast last cancelled per caster, so late or reordered packets cannot resurrect anything.
    private static final Map<Integer, Long> LATEST = new LinkedHashMap<>(), CLOSED = new LinkedHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private SilentGateRenderer() {}

    public static void receive(SilentGatePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != world) reset();
        world = client.level;
        if (world != null) accept(payload, world.getGameTime());
    }

    static void accept(SilentGatePayload p, long now) {
        long latest = LATEST.getOrDefault(p.casterId(), Long.MIN_VALUE);
        if (p.startedAt() < latest || p.startedAt() == CLOSED.getOrDefault(p.casterId(), Long.MIN_VALUE)) return;
        LATEST.put(p.casterId(), p.startedAt());
        if (p.phase() == SilentGatePayload.CANCEL) {
            CLOSED.put(p.casterId(), p.startedAt());
            CASTS.remove(p.casterId());
            return;
        }
        Cast cast = CASTS.get(p.casterId());
        if (cast == null || cast.startedAt != p.startedAt()) {
            CASTS.put(p.casterId(), cast = new Cast());
            cast.startedAt = p.startedAt();
            cast.phase = -1;
        }
        if (p.phase() > cast.phase) { cast.phase = p.phase(); cast.phaseAt = p.phaseAt(); }
        if (p.phase() == SilentGateSequence.Phase.BARRAGE.ordinal() && cast.commandAt < 0) cast.commandAt = p.phaseAt();
        cast.anchor = new Vec3(p.x(), p.y(), p.z());
        cast.yaw = p.yaw(); cast.seed = p.seed(); cast.skyCircles = p.skyCircles(); cast.barrageTicks = p.barrageTicks();
        for (int i = 0; i < p.shots().length; i++)
            cast.shots.add(new Shot(p.shots()[i], new Vec3(p.hits()[3 * i], p.hits()[3 * i + 1], p.hits()[3 * i + 2]), now));
        trim(cast, now);
        if (CASTS.size() > 16) CASTS.remove(CASTS.keySet().iterator().next());
    }

    private static void trim(Cast cast, long now) { cast.shots.removeIf(s -> now - s.tick() > SHOT_MEMORY_TICKS); }

    /** Past this the camera is released and the cast is forgotten. */
    private static boolean over(Cast cast, double now) {
        int release = SilentGateSequence.endTick(cast.barrageTicks) + SilentGateSequence.RETURN_TICKS;
        if (cast.commandAt >= 0) return now - cast.commandAt >= release;
        return cast.phase == SilentGateSequence.Phase.END.ordinal()
                && now - cast.phaseAt >= SilentGateSequence.ENDING_TICKS + SilentGateSequence.RETURN_TICKS;
    }

    static Frame frameAt(int casterId, double now) {
        Cast c = CASTS.get(casterId);
        if (c == null || over(c, now)) return null;
        return new Frame(casterId, c.startedAt, c.phase, now - c.startedAt, c.commandAt < 0 ? -1 : now - c.commandAt,
                c.anchor, c.yaw, c.seed, c.skyCircles, c.barrageTicks);
    }
    public static Frame frame(int casterId, float partial) {
        return world == null ? null : frameAt(casterId, world.getGameTime() + partial);
    }
    public static Frame localFrame(float partial) {
        var player = Minecraft.getInstance().player;
        return player == null ? null : frame(player.getId(), partial);
    }
    /** Shots of the last {@value #SHOT_MEMORY_TICKS} ticks, oldest first. */
    public static List<Shot> shots(int casterId) {
        Cast c = CASTS.get(casterId);
        return c == null ? List.of() : List.copyOf(c.shots);
    }

    /** Every visible cast, so other players' gates are drawn for this viewer too. */
    public static List<Frame> frames(float partial) {
        if (world == null) return List.of();
        List<Frame> out = new ArrayList<>();
        for (int id : CASTS.keySet()) { Frame f = frame(id, partial); if (f != null) out.add(f); }
        return out;
    }

    // ---- placeholder geometry (milestone 1): flat rings, boxes, discs and straight beams ----
    static final int QUAD_BUDGET = 20000;
    static final int WHITE_BLUE = 0xCFE6FF, JADE = 0x2FD6A0, GOLD = 0xFFC83A;
    static final double BEAM_TICKS = 6, GATE_DROP = 60;
    private static final int GREAT_SEGMENTS = 48;
    private static final double[] GREAT_RADII = {8, 16, 28, 40, 56}, GREAT_LIFT = {-2, 1, 0, 2, -1};
    /** Per-pass opacity gain: the additive pass only adds a soft glow over the body. */
    private static double gain = 1;

    public static void render(PoseStack pose, Camera camera, float partial) {
        List<Frame> frames = frames(partial);
        if (frames.isEmpty()) return;
        setup();
        try {
            for (int pass = 0; pass < 2; pass++) {
                if (pass == 1) additiveBlend(); else RenderSystem.defaultBlendFunc();
                gain = pass == 0 ? 1 : .35;
                for (Frame f : frames) {
                    BufferBuilder buffer = begin();
                    emitScene(buffer, pose.last().pose(), f, shots(f.casterId()), camera.getPosition());
                    draw(buffer);
                }
            }
        } finally { gain = 1; teardown(); }
    }
    private record Deferred(Matrix4f pose, Matrix4f modelView, Matrix4f projection, Camera camera, float partial) {}
    private static Deferred deferred;
    /** Same rule as Fuga: with a shaderpack the level pass repaints immediate geometry, so draw after it in flushDeferred. */
    public static boolean deferForShaderPack(PoseStack pose, Camera camera, float partial) {
        if (!FugaRenderer.shaderPackDefers()) return false;
        deferred = new Deferred(new Matrix4f(pose.last().pose()), new Matrix4f(RenderSystem.getModelViewMatrix()),
                new Matrix4f(RenderSystem.getProjectionMatrix()), camera, partial);
        return true;
    }
    public static void flushDeferred() {
        var d = deferred; deferred = null;
        if (d == null) return;
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        var stack = RenderSystem.getModelViewStack();
        stack.pushMatrix(); stack.set(d.modelView()); RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(d.projection(), com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        var pose = new PoseStack(); pose.last().pose().set(d.pose());
        try { render(pose, d.camera(), d.partial()); }
        finally { RenderSystem.restoreProjectionMatrix(); stack.popMatrix(); RenderSystem.applyModelViewMatrix(); }
    }
    private static void setup() {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        FugaRenderer.bindFlameTexture(); RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }
    private static void additiveBlend() { RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE); }
    private static void teardown() {
        RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
    }
    private static BufferBuilder begin() { return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR); }
    private static void draw(BufferBuilder buffer) {
        var mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private static final class Ctx {
        VertexConsumer v; Matrix4f m; Vec3 eye; double fade; int quads;
    }
    private static double clamp01(double x) { return Math.max(0, Math.min(1, x)); }
    private static double smooth(double x) { x = clamp01(x); return x * x * (3 - 2 * x); }

    /** Pure geometry for one cast; returns the quads emitted (never more than {@link #QUAD_BUDGET}). */
    static int emitScene(VertexConsumer v, Matrix4f m, Frame f, List<Shot> shots, Vec3 eye) {
        if (!Double.isFinite(f.age()) || f.age() < 0 || f.anchor() == null || !Double.isFinite(f.anchor().x + f.anchor().y + f.anchor().z)
                || !Double.isFinite(eye.x + eye.y + eye.z)) return 0;
        Ctx c = new Ctx(); c.v = v; c.m = m; c.eye = eye; c.fade = 1;
        int endStart = SilentGateSequence.ACTIVATE_TICKS + f.barrageTicks();
        if (f.commandAge() >= endStart) c.fade = clamp01(1 - (f.commandAge() - endStart) / SilentGateSequence.ENDING_TICKS);
        if (c.fade <= 0) return 0;
        double age = f.age();
        Vec3 anchor = f.anchor();
        Vec3 gate = SilentGateSequence.gateCenter(anchor, f.yaw());
        // Great circle: five flat rings at slightly different heights, swept into view over 80..140.
        double reveal = smooth((age - SilentGateSequence.BEAT_CIRCLE) / (SilentGateSequence.BEAT_GATE - SilentGateSequence.BEAT_CIRCLE));
        for (int i = 0; i < GREAT_RADII.length; i++) {
            Vec3 center = new Vec3(gate.x, anchor.y + SilentGateSequence.CIRCLE_ALTITUDE + GREAT_LIFT[i], gate.z);
            double step = Math.PI * 2 / GREAT_SEGMENTS, spin = (i % 2 == 0 ? 1 : -1) * age * .004;
            for (int s = 0; s < GREAT_SEGMENTS && s * step < reveal * Math.PI * 2; s++)
                ring(c, center, GREAT_RADII[i] - .75, GREAT_RADII[i] + .75, spin + s * step, spin + (s + 1) * step, WHITE_BLUE, .8);
        }
        int sky = Math.max(0, Math.min(f.skyCircles(), 96));
        for (int i = 0; i < sky; i++)
            disc(c, SilentGateSequence.skyCircle(anchor, f.yaw(), f.seed(), i, f.skyCircles()), 3,
                    WHITE_BLUE, smooth((age - 140 - 60.0 * i / sky) / 20));
        for (int i = 0; i < SilentGateSequence.GROUND_RING; i++)
            disc(c, SilentGateSequence.groundCircle(anchor, f.yaw(), i), 1.2,
                    SilentGateSequence.groundCircleGold(i) ? GOLD : JADE, smooth((age - 200 - 4.0 * i) / 20));
        gate(c, gate, f.yaw(), age);
        // Beams from circle to hit point, alive for BEAM_TICKS.
        double now = f.startedAt() + age;
        for (Shot s : shots) {
            double life = now - s.tick();
            if (life < 0 || life >= BEAM_TICKS || s.circle() < 0) continue;
            Vec3 from = s.circle() < f.skyCircles()
                    ? SilentGateSequence.skyCircle(anchor, f.yaw(), f.seed(), s.circle(), f.skyCircles())
                    : SilentGateSequence.groundCircle(anchor, f.yaw(), s.circle() - f.skyCircles());
            beam(c, from, s.hit(), 1 - life / BEAM_TICKS);
        }
        return c.quads;
    }

    /** Three stacked flat discs, narrowing upwards. */
    private static void disc(Ctx c, Vec3 at, double radius, int color, double alpha) {
        if (alpha <= 0) return;
        for (int tier = 0; tier < 3; tier++)
            for (int s = 0; s < 8; s++)
                ring(c, at.add(0, tier * .4, 0), 0, radius * (1 - tier * .3), s * Math.PI / 4, (s + 1) * Math.PI / 4, color, alpha * (tier == 0 ? .6 : .85));
    }
    /** One flat annulus sector between two angles. */
    private static void ring(Ctx c, Vec3 center, double r0, double r1, double a0, double a1, int color, double alpha) {
        quad(c, center.add(Math.cos(a0) * r0, 0, Math.sin(a0) * r0), center.add(Math.cos(a1) * r0, 0, Math.sin(a1) * r0),
                center.add(Math.cos(a1) * r1, 0, Math.sin(a1) * r1), center.add(Math.cos(a0) * r1, 0, Math.sin(a0) * r1), color, alpha);
    }
    private static void gate(Ctx c, Vec3 gate, float yaw, double age) {
        double alpha = smooth((age - 120) / 20);
        if (alpha <= 0) return;
        double rad = Math.toRadians(yaw);
        Vec3 d = new Vec3(-Math.sin(rad), 0, Math.cos(rad)), r = new Vec3(Math.cos(rad), 0, Math.sin(rad)), up = new Vec3(0, 1, 0);
        Vec3 foot = gate.add(0, GATE_DROP * SilentGateSequence.gateDescent(age), 0);
        double h = SilentGateSequence.GATE_HEIGHT, w = SilentGateSequence.GATE_WIDTH / 2.0;
        for (int side = -1; side <= 1; side += 2)
            box(c, foot.add(r.scale(side * w)).add(up.scale(h / 2)), r.scale(1.5), up.scale(h / 2), d.scale(1.5), WHITE_BLUE, alpha);
        box(c, foot.add(up.scale(h - 1.5)), r.scale(w), up.scale(1.5), d.scale(1.5), WHITE_BLUE, alpha);
        box(c, foot.add(up.scale(.75)), r.scale(w), up.scale(.75), d.scale(1.5), WHITE_BLUE, alpha);
        double open = SilentGateSequence.gateOpen(age) * Math.PI / 2;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 hinge = foot.add(r.scale(side * (w - 1.5))).add(up.scale(1.5));
            Vec3 leaf = r.scale(-side * Math.cos(open)).add(d.scale(Math.sin(open))).scale(w - 1.5), rise = up.scale(h - 3);
            quad(c, hinge, hinge.add(leaf), hinge.add(leaf).add(rise), hinge.add(rise), JADE, alpha * .55);
        }
    }
    private static void box(Ctx c, Vec3 mid, Vec3 x, Vec3 y, Vec3 z, int color, double alpha) {
        Vec3[][] axes = {{x, y, z}, {y, z, x}, {z, x, y}};
        for (Vec3[] a : axes) for (int sign = -1; sign <= 1; sign += 2) {
            Vec3 face = mid.add(a[2].scale(sign));
            quad(c, face.subtract(a[0]).subtract(a[1]), face.add(a[0]).subtract(a[1]), face.add(a[0]).add(a[1]), face.subtract(a[0]).add(a[1]), color, alpha);
        }
    }
    /** Two crossed quads from the circle to the hit. */
    private static void beam(Ctx c, Vec3 from, Vec3 to, double alpha) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1e-6) return;
        dir = dir.normalize();
        Vec3 s = dir.cross(Math.abs(dir.y) > .98 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize().scale(.4), t = dir.cross(s).normalize().scale(.4);
        quad(c, from.subtract(s), from.add(s), to.add(s), to.subtract(s), WHITE_BLUE, alpha);
        quad(c, from.subtract(t), from.add(t), to.add(t), to.subtract(t), WHITE_BLUE, alpha);
    }
    private static void quad(Ctx c, Vec3 a, Vec3 b, Vec3 d, Vec3 e, int color, double alpha) {
        if (c.quads >= QUAD_BUDGET) return;
        double k = alpha * c.fade * gain;
        vertex(c, a, color, k); vertex(c, b, color, k); vertex(c, d, color, k); vertex(c, e, color, k);
        c.quads++;
    }
    private static void vertex(Ctx c, Vec3 p, int color, double alpha) {
        Vec3 q = p.subtract(c.eye);
        int opacity = (int) (clamp01(alpha) * 255);
        c.v.addVertex(c.m, (float) q.x, (float) q.y, (float) q.z).setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, opacity)
                .setUv(FugaRenderer.SOLID_U, FugaRenderer.SOLID_V);
    }

    public static void tick(Minecraft client) {
        if (client.level != world || client.player == null) { reset(); return; }
        long now = world.getGameTime();
        CASTS.entrySet().removeIf(e -> {
            boolean gone = over(e.getValue(), now)
                    || world.getEntity(e.getKey()) instanceof net.minecraft.world.entity.LivingEntity living && !living.isAlive();
            if (gone) CLOSED.put(e.getKey(), e.getValue().startedAt);
            return gone;
        });
        CASTS.values().forEach(c -> trim(c, now));
    }
    public static void reset() { CASTS.clear(); LATEST.clear(); CLOSED.clear(); world = null; }
}
