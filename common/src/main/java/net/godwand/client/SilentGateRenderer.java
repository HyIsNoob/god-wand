package net.godwand.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.godwand.client.GateDraw.Ctx;
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

import static net.godwand.client.GateDraw.clamp01;
import static net.godwand.client.GateDraw.smooth;

/** Client state of every visible Silent Gate cast. Geometry is drawn on top of {@link #localFrame} / {@link #shots}. */
public final class SilentGateRenderer {
    /** {@code age}: ticks since the first click; {@code commandAge}: ticks since the second click (-1 before it). */
    public record Frame(int casterId, long startedAt, int phase, double age, double commandAge,
            Vec3 anchor, float yaw, long seed, int skyCircles, int barrageTicks) {}
    public record Shot(int circle, Vec3 hit, long tick) {}

    static final int SHOT_MEMORY_TICKS = 10;
    /** Server config maximum of gateWaitTicks. */
    static final int MAX_WAIT_TICKS = 1200;
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
        // No barrage packet ever came (viewer left the 256-block range): the server's longest wait plus slack.
        if (cast.phase <= SilentGateSequence.Phase.WAIT.ordinal()
                && now - cast.startedAt > SilentGateSequence.BUILD_TICKS + MAX_WAIT_TICKS + 20) return true;
        return cast.phase == SilentGateSequence.Phase.END.ordinal()
                && now - cast.phaseAt >= SilentGateSequence.ENDING_TICKS + SilentGateSequence.RETURN_TICKS;
    }

    static Frame frameAt(int casterId, double now) {
        Cast c = CASTS.get(casterId);
        if (c == null || over(c, now)) return null;
        now = SilentGateCinematic.warp(c.startedAt, now);
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

    // ---- geometry: great circle in GateCircles; sky discs, ground ring, gate and beams are still milestone-1 placeholders ----
    static final int QUAD_BUDGET = 20000;
    static final double BEAM_TICKS = GateBeams.LIFE;
    /** Opacity of the legacy flat geometry in the additive pass: it only adds a soft glow over the body. */
    private static final double GLOW_GAIN = .35;

    public static void render(PoseStack pose, Camera camera, float partial) {
        List<Frame> frames = frames(partial);
        if (frames.isEmpty()) return;
        setup();
        try {
            for (int pass = 0; pass < 2; pass++) {
                if (pass == 1) { depthPrepass(pose, camera, frames); additiveBlend(); } else RenderSystem.defaultBlendFunc();
                for (Frame f : frames) {
                    BufferBuilder buffer = begin();
                    emitScene(buffer, pose.last().pose(), f, shots(f.casterId()), camera.getPosition(), pass);
                    draw(buffer);
                }
            }
        } finally { teardown(); }
    }
    /** The gate body is alpha blended without depth writes; stamp its depth so the additive layer (circle glow, rays) cannot shine through it. */
    private static void depthPrepass(PoseStack pose, Camera camera, List<Frame> frames) {
        RenderSystem.colorMask(false, false, false, false); RenderSystem.depthMask(true);
        try {
            for (Frame f : frames) {
                BufferBuilder buffer = begin();
                emitScene(buffer, pose.last().pose(), f, List.of(), camera.getPosition(), 0, true);
                draw(buffer);
            }
        } finally { RenderSystem.colorMask(true, true, true, true); RenderSystem.depthMask(false); }
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
        GateAtlas.bind(); RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
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

    /** Pure geometry for one cast with both layers (tests). */
    static int emitScene(VertexConsumer v, Matrix4f m, Frame f, List<Shot> shots, Vec3 eye) { return emitScene(v, m, f, shots, eye, -1); }

    /** Geometry of one layer (0 body, 1 additive glow, -1 both); returns the quads emitted (never more than {@link #QUAD_BUDGET}). */
    static int emitScene(VertexConsumer v, Matrix4f m, Frame f, List<Shot> shots, Vec3 eye, int layer) { return emitScene(v, m, f, shots, eye, layer, false); }

    static int emitScene(VertexConsumer v, Matrix4f m, Frame f, List<Shot> shots, Vec3 eye, int layer, boolean solidOnly) {
        if (!Double.isFinite(f.age()) || f.age() < 0 || f.anchor() == null || !Double.isFinite(f.anchor().x + f.anchor().y + f.anchor().z)
                || !Double.isFinite(eye.x + eye.y + eye.z)) return 0;
        Ctx c = new Ctx(); c.v = v; c.m = m; c.eye = eye; c.layer = layer; c.gain = layer == 1 ? GLOW_GAIN : 1; c.solidOnly = solidOnly;
        int endStart = SilentGateSequence.ACTIVATE_TICKS + f.barrageTicks();
        if (f.commandAge() >= endStart) c.fade = clamp01(1 - (f.commandAge() - endStart) / SilentGateSequence.ENDING_TICKS);
        if (c.fade <= 0) return 0;
        double age = f.age();
        Vec3 anchor = f.anchor();
        Vec3 gate = SilentGateSequence.gateCenter(anchor, f.yaw());
        double endK = f.commandAge() >= endStart ? clamp01((f.commandAge() - endStart) / SilentGateSequence.ENDING_TICKS) : 0;
        if (solidOnly) {
            gate(c, anchor, f.yaw(), age, f.commandAge(), endK);
            return c.quads;
        }
        GateSky.dome(c, gate, age);
        GateSky.clouds(c, GateCircles.center(anchor, gate), age, endK);
        GateCircles.great(c, anchor, gate, age, endK);
        int sky = Math.max(0, Math.min(f.skyCircles(), 96));
        double wave = f.commandAge() >= 0 ? f.commandAge() * GateMinor.WAVE_SPEED : -1;
        for (int i = 0; i < sky; i++)
            GateMinor.sky(c, SilentGateSequence.skyCircle(anchor, f.yaw(), f.seed(), i, f.skyCircles()), i, age, 140 + 60.0 * i / sky, gate, wave);
        for (int i = 0; i < SilentGateSequence.GROUND_RING; i++)
            GateMinor.ground(c, SilentGateSequence.groundCircle(anchor, f.yaw(), i), anchor, i, age, SilentGateSequence.groundCircleGold(i), gate, wave);
        GateMinor.wave(c, gate, f.yaw(), wave);
        gate(c, anchor, f.yaw(), age, f.commandAge(), endK);
        // Beams from circle to hit point, alive for BEAM_TICKS.
        double now = f.startedAt() + age;
        for (Shot s : shots) {
            double life = now - s.tick();
            if (life < 0 || life >= BEAM_TICKS || s.circle() < 0) continue;
            Vec3 from = s.circle() < f.skyCircles()
                    ? SilentGateSequence.skyCircle(anchor, f.yaw(), f.seed(), s.circle(), f.skyCircles())
                    : SilentGateSequence.groundCircle(anchor, f.yaw(), s.circle() - f.skyCircles());
            GateBeams.shot(c, from, s.hit(), life, s.circle() < f.skyCircles() ? (s.circle() % 2 == 0 ? GateMinor.JADE : GateCircles.GLOW_BLUE)
                    : SilentGateSequence.groundCircleGold(s.circle() - f.skyCircles()) ? GateMinor.GOLD : GateMinor.GREEN, s.tick() * 31L + s.circle(), now);
        }
        lastQuads = c.quads;
        return c.quads;
    }
    /** Quads of the last emitted cast (dev statistic read by the QA probe). */
    public static volatile int lastQuads;

    /** The gate: slit it climbs out of (140-200), descent wind and dust, the sculpted model, then shockwave, pressure rings and the opening light. */
    private static void gate(Ctx c, Vec3 anchor, float yaw, double age, double commandAge, double endK) {
        double descent = SilentGateSequence.gateDescent(age);
        // Ending: the leaves close first, then the gate climbs back into the slit and fades there.
        double rise = smooth((endK - .2) / .55), fadeOut = clamp01(4 * (1 - endK));
        double appear = smooth((age - 141) / 9);
        double slit = Math.max(smooth((age - 138) / 10) * (1 - smooth((age - 188) / 12)), Math.sin(Math.PI * clamp01((endK - .1) / .8)));
        Vec3 start = SilentGateSequence.gateFoot(anchor, yaw, 1);
        GateFx.Pose slitPose = GateFx.Pose.of(start, yaw);
        if (!c.solidOnly) GateFx.slit(c, slitPose, age < 138 && endK <= 0 ? 0 : slit, SilentGateSequence.GATE_HEIGHT + 20);
        if (appear <= 0 || fadeOut <= 0) return;
        double saved = c.fade;
        c.fade = saved * appear * (endK > 0 ? Math.min(1, fadeOut) : 1);
        GateFx.Pose pose = GateFx.Pose.of(SilentGateSequence.gateFoot(anchor, yaw, descent + rise), yaw);
        double speed = (SilentGateSequence.gateDescent(age - .5) - SilentGateSequence.gateDescent(age + .5)) * SilentGateSequence.GATE_DROP;
        double open = SilentGateSequence.gateOpen(age) * (1 - smooth(endK / .2));
        GateModel.draw(c, pose, age, open, GateModel.vein(age, commandAge, endK));
        if (!c.solidOnly) {
            GateFx.wind(c, pose, age, Math.max(0, speed));
            GateFx.dust(c, pose, age);
            GateFx.shockwave(c, pose, age);
            GateFx.pressure(c, pose, age, endK);
        }
        c.fade = saved;
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
        vanillaClouds(client, CASTS.values().stream().anyMatch(c -> c.anchor != null && c.anchor.distanceToSqr(client.player.position()) < RENDER_RANGE * RENDER_RANGE));
    }

    /** The cast replaces the sky with its own vortex, so vanilla clouds are switched off while one is in range and put back after. */
    private static final double RENDER_RANGE = 600;
    private static net.minecraft.client.CloudStatus savedClouds;
    static void vanillaClouds(Minecraft client, boolean cast) {
        var option = client.options.cloudStatus();
        if (cast && savedClouds == null) { savedClouds = option.get(); option.set(net.minecraft.client.CloudStatus.OFF); }
        else if (!cast && savedClouds != null) { option.set(savedClouds); savedClouds = null; }
    }
    public static void reset() {
        CASTS.clear(); LATEST.clear(); CLOSED.clear(); world = null;
        if (savedClouds != null) vanillaClouds(Minecraft.getInstance(), false);
    }
}
