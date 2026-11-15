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
import java.util.HashMap;
import java.util.Map;
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
            Vec3 anchor, float yaw, long seed, int skyCircles, int barrageTicks, int featured) {}
    /** A beam: its circle, where it lands, the tick it was announced (the circle then glides over the target, the beam fires {@code glide} ticks later); heavy is the kill beam. */
    public record Shot(int circle, Vec3 hit, long tick, boolean heavy) {
        public Shot(int circle, Vec3 hit, long tick) { this(circle, hit, tick, false); }
    }

    static final int SHOT_MEMORY_TICKS = 45;
    /** Server config maximum of gateWaitTicks. */
    static final int MAX_WAIT_TICKS = 1200;
    private static final class Cast {
        long startedAt, phaseAt, commandAt = -1;
        int phase, skyCircles, barrageTicks, featured = SilentGatePayload.NO_FEATURED;
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
        if (p.featured() >= 0) cast.featured = p.featured();
        for (int i = 0; i < p.shots().length; i++)
            { int idx = p.shots()[i]; boolean heavy = idx >= SilentGatePayload.HEAVY;
            cast.shots.add(new Shot(heavy ? idx - SilentGatePayload.HEAVY : idx, new Vec3(p.hits()[3 * i], p.hits()[3 * i + 1], p.hits()[3 * i + 2]), now, heavy)); }
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
                c.anchor, c.yaw, c.seed, c.skyCircles, c.barrageTicks, c.featured);
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
        GateDraw.farPlane = Minecraft.getInstance().gameRenderer.getDepthFar();
        planBloom(pose, camera, frames, partial);
        setup();
        try {
            // Body first; then, with the gate's depth stamped, the shade (smoke and storm darken what is behind them) and last the light, which adds over both.
            // The body is drawn over its own stamped depth too: only its nearest faces pass, else back faces painted over front ones made it look hollow.
            for (int layer : new int[]{0, 2, 1}) {
                if (layer == 0) { depthPrepass(pose, camera, frames, true); RenderSystem.defaultBlendFunc(); }
                else if (layer == 2) { depthPrepass(pose, camera, frames, false); RenderSystem.blendFunc(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR); GateAtlas.bindGlow(); GateDraw.premultiplied = true; }
                else additiveBlend();
                for (Frame f : frames) {
                    BufferBuilder buffer = begin();
                    emitScene(buffer, pose.last().pose(), f, shots(f.casterId()), camera.getPosition(), layer);
                    draw(buffer);
                }
            }
            // The floating wand belongs to the picture: drawn before the impact frames and the scope bars, so they cover it (it showed through the bars).
            teardown(); wands(pose, camera, frames); setup();
            flash(pose, camera, partial);
            bars(pose, partial);
            // What flies over the bars: beams, sparks, feathers, the motes and the mana. Drawn after them, it comes out of the frame.
            GateDraw.premultiplied = true; GateAtlas.bindGlow(); additiveBlend(); RenderSystem.enableDepthTest();
            for (Frame f : frames) {
                BufferBuilder buffer = begin();
                emitScene(buffer, pose.last().pose(), f, shots(f.casterId()), camera.getPosition(), 3);
                draw(buffer);
            }
        } finally { GateDraw.premultiplied = false; RenderSystem.enableDepthTest(); teardown(); }
    }
    /** The scope bars of the local cast, as black quads right before the lens: in the world pass, so the pop-out layer can be drawn over them. */
    private static void bars(PoseStack pose, float partial) {
        var window = Minecraft.getInstance().getWindow();
        if (window.getHeight() <= 0) return;
        float bar = (float) SilentGateCinematic.bar(localFrame(partial), (double) window.getWidth() / window.getHeight());
        inkBar = bar;
        if (bar <= 0) return;
        GateDraw.premultiplied = false; GateAtlas.bind(); RenderSystem.disableDepthTest(); RenderSystem.defaultBlendFunc();
        Matrix4f inverse = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(pose.last().pose()).invert();
        BufferBuilder b = begin();
        for (int top = 0; top < 2; top++) {
            float y0 = top == 0 ? -1 : 1 - 2 * bar, y1 = top == 0 ? -1 + 2 * bar : 1;
            float[][] ndc = {{-1, y0}, {1, y0}, {1, y1}, {-1, y1}};
            for (float[] p : ndc) {
                var q = inverse.transformProject(p[0], p[1], 0, new org.joml.Vector3f());
                b.addVertex(pose.last().pose(), q.x, q.y, q.z).setColor(0, 0, 0, 255).setUv(GateAtlas.SOLID_U, GateAtlas.SOLID_V);
            }
        }
        draw(b);
    }
    /** Bloom of this frame, read by {@link #flushDeferred} once the level is drawn: strength, shafts, the light's place on screen (0..1), partial tick. */
    private static float[] bloom;
    /** The light spills from the great circle until the doors open, then from the doorway; it comes up with the circle and goes with the ending. */
    private static void planBloom(PoseStack pose, Camera camera, List<Frame> frames, float partial) {
        Frame f = localFrame(partial);
        if (f == null) f = frames.get(0);
        double age = f.age();
        if (f.anchor() == null || !Double.isFinite(age)) return;
        int endStart = SilentGateSequence.ACTIVATE_TICKS + f.barrageTicks();
        double e = f.commandAge() >= endStart ? f.commandAge() - endStart : -1;
        double on = smooth((age - 20) / 60) * (e < 0 ? 1 : clamp01(1 - smooth((e - (SilentGateSequence.ENDING_TICKS - 25)) / 25)));
        double open = e < 0 ? SilentGateSequence.gateOpen(age) : SilentGateSequence.closingOpen(e);
        Vec3 gate = SilentGateSequence.gateCenter(f.anchor(), f.yaw());
        GateFx.Pose doorway = GateFx.Pose.of(SilentGateSequence.gateFoot(f.anchor(), f.yaw(), SilentGateSequence.gateDescent(age) + (e < 0 ? 0 : SilentGateSequence.gateRise(e))), f.yaw());
        Vec3 door = doorway.at(0, 37, 0);
        Vec3 src = (open > .02 ? door : GateCircles.center(f.anchor(), gate)).subtract(camera.getPosition());
        var clip = new org.joml.Vector4f((float) src.x, (float) src.y, (float) src.z, 1)
                .mul(pose.last().pose()).mul(RenderSystem.getModelViewMatrix()).mul(RenderSystem.getProjectionMatrix());
        boolean seen = clip.w > .001f;
        // The open doorway floods the frame (reference 5): nothing holds the bloom back.
        double volley = f.commandAge() >= 0 ? 1 - .45 * (1 - smooth((f.commandAge() - 30) / 20)) : 1;
        double settle = age > SilentGateSequence.OPEN_END ? .3 + .7 * Math.exp(-(age - SilentGateSequence.OPEN_END) / 30) : 1;
        open *= settle;
        bloom = new float[]{(float) (net.godwand.config.GodWandConfig.CLIENT.bloomStrength * (.95 + .15 * open) * on * volley),
                seen ? (float) (on * (.25 * smooth((age - SilentGateSequence.CIRCLE_FULL) / 40) + .38 * open)) : 0,
                seen ? clip.x / clip.w * .5f + .5f : .5f, seen ? clip.y / clip.w * .5f + .5f : .5f, partial};
    }
    /** Ink stage (0 none, 1 white on black, 2 black on white) and scope bar of this frame, read by {@link #flushDeferred} for the post chain. */
    private static float ink, inkBar;
    /** The local cast's impact frames: the view inked in two tones for two ticks (click, touchdown, command, kill beam) by the post chain, then a small flash of
     *  warm white that dies away (one quad right before the lens). Where the post chain cannot run (post effects off, a shaderpack) the view is inverted instead. */
    private static void flash(PoseStack pose, Camera camera, float partial) {
        Frame f = localFrame(partial);
        ink = 0;
        if (f == null) return;
        double[] fx = SilentGateCinematic.flash(f.age(), f.commandAge(), SilentGateCinematic.sinceHit(f));
        boolean invert = fx[0] > 0 && net.godwand.config.GodWandConfig.CLIENT.impactFrames;
        if (invert && GatePostFx.ready()) { ink = (float) fx[0]; invert = false; }
        if (!invert && fx[1] < .01) return;
        var look = camera.getLookVector(); var up = camera.getUpVector(); var left = camera.getLeftVector();
        GateDraw.premultiplied = false; GateAtlas.bind(); RenderSystem.disableDepthTest();
        for (int layer = invert ? 0 : 1; layer < 2; layer++) {
            if (layer == 1 && fx[1] < .01) break;
            if (layer == 0) RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR); else additiveBlend();
            double w = layer == 0 ? 1 : Math.min(1, fx[1]);
            BufferBuilder b = begin();
            for (int i = 0; i < 4; i++) {
                float x = i == 0 || i == 3 ? -3 : 3, y = i < 2 ? -3 : 3;
                b.addVertex(pose.last().pose(), look.x() * .5f + left.x() * x + up.x() * y, look.y() * .5f + left.y() * x + up.y() * y, look.z() * .5f + left.z() * x + up.z() * y)
                        .setColor((int) (255 * w), (int) (250 * w), (int) (236 * w), 255).setUv(GateAtlas.SOLID_U, GateAtlas.SOLID_V);
            }
            draw(b);
        }
    }
    /** True while any cast is visible: the in-hand wand of Silent Gate wands is hidden then, the floating one is drawn instead. */
    public static boolean holdsWand() { return !CASTS.isEmpty(); }
    private static net.godwand.client.render.GodWandItemRenderer wandRenderer;
    /** The wand floats in the world (it leaves the hand), drawn with the item renderer's own model. */
    private static void wands(PoseStack pose, Camera camera, List<Frame> frames) {
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        for (Frame f : frames) {
            var sim = GateMana.sim(f.casterId());
            if (sim == null || !sim.ready || f.age() < 0) continue;
            double p = sim.partial(f.age());
            var w = GatePose.wand(f, sim.feet(p), sim.yaw(p));
            if (wandRenderer == null) wandRenderer = new net.godwand.client.render.GodWandItemRenderer();
            var pos = w.pos().subtract(camera.getPosition());
            var level = Minecraft.getInstance().level;
            int packed = level == null ? 0xF000F0 : net.minecraft.client.renderer.LevelRenderer.getLightColor(level, net.minecraft.core.BlockPos.containing(w.pos()));
            int light = net.minecraft.client.renderer.LightTexture.pack(Math.max(11, net.minecraft.client.renderer.LightTexture.block(packed)), Math.max(8, net.minecraft.client.renderer.LightTexture.sky(packed)));
            pose.pushPose();
            pose.translate(pos.x, pos.y, pos.z);
            Vec3 y = w.axis(), z = w.face(), x = y.cross(z);
            var m = new org.joml.Matrix3f((float) x.x, (float) x.y, (float) x.z, (float) y.x, (float) y.y, (float) y.z, (float) z.x, (float) z.y, (float) z.z);
            pose.mulPose(new org.joml.Quaternionf().setFromNormalized(m));
            pose.scale((float) GatePose.WAND_SCALE, (float) GatePose.WAND_SCALE, (float) GatePose.WAND_SCALE);
            wandRenderer.renderFloating(pose, buffers, light, (float) f.age());
            pose.popPose();
        }
        buffers.endBatch();
    }
    /** The gate body is alpha blended without depth writes; stamp its depth so the additive layer (circle glow, rays) cannot shine through it. */
    private static void depthPrepass(PoseStack pose, Camera camera, List<Frame> frames, boolean beforeBody) {
        RenderSystem.colorMask(false, false, false, false); RenderSystem.depthMask(true);
        try {
            for (Frame f : frames) {
                BufferBuilder buffer = begin();
                emitScene(buffer, pose.last().pose(), f, List.of(), camera.getPosition(), 0, true, beforeBody);
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
        if (d != null) drawDeferred(d);
        float[] b = bloom; bloom = null;
        if (b != null) GatePostFx.process(b[0], b[1], b[2], b[3], b[4], ink, inkBar);
        else if (CASTS.isEmpty()) GatePostFx.close();
    }
    private static void drawDeferred(Deferred d) {
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

    /** Geometry of one layer (0 body, 1 additive glow, 2 shade, -1 all); returns the quads emitted (never more than {@link #QUAD_BUDGET}). */
    static int emitScene(VertexConsumer v, Matrix4f m, Frame f, List<Shot> shots, Vec3 eye, int layer) { return emitScene(v, m, f, shots, eye, layer, false); }

    static int emitScene(VertexConsumer v, Matrix4f m, Frame f, List<Shot> shots, Vec3 eye, int layer, boolean solidOnly) { return emitScene(v, m, f, shots, eye, layer, solidOnly, false); }
    static int emitScene(VertexConsumer v, Matrix4f m, Frame f, List<Shot> shots, Vec3 eye, int layer, boolean solidOnly, boolean beforeBody) {
        if (!Double.isFinite(f.age()) || f.age() < 0 || f.anchor() == null || !Double.isFinite(f.anchor().x + f.anchor().y + f.anchor().z)
                || !Double.isFinite(eye.x + eye.y + eye.z)) return 0;
        // Layer 3 is the pop-out pass: the light that is drawn after the scope bars. It is left out of the ordinary glow pass (1).
        boolean front = layer == 3;
        Ctx c = new Ctx(); c.v = v; c.m = m; c.eye = eye; c.layer = front ? 1 : layer; c.gain = c.layer == 1 ? GLOW_GAIN : 1; c.solidOnly = solidOnly; c.beforeBody = beforeBody;
        int endStart = SilentGateSequence.ACTIVATE_TICKS + f.barrageTicks();
        // Ending ticks after the barrage (-1 before); everything dims only in the last 25 ticks, the sequence itself is staged by e.
        double e = f.commandAge() >= endStart ? f.commandAge() - endStart : -1;
        if (e >= 0) c.fade = clamp01(1 - smooth((e - (SilentGateSequence.ENDING_TICKS - 25)) / 25));
        if (c.fade <= 0) return 0;
        double age = f.age();
        Vec3 anchor = f.anchor();
        Vec3 gate = SilentGateSequence.gateCenter(anchor, f.yaw());
        double endK = e >= 0 ? clamp01(e / SilentGateSequence.ENDING_TICKS) : 0;
        if (solidOnly) {
            gate(c, anchor, f.yaw(), age, f.commandAge(), e);
            return c.quads;
        }
        int count = Math.max(0, Math.min(f.skyCircles(), SilentGateSequence.MAX_CIRCLES));
        double wave = f.commandAge() >= 0 ? f.commandAge() * GateMinor.WAVE_SPEED : -1;
        double now = f.startedAt() + age;
        if (!front) {
        GateSky.dome(c, gate, age);
        Vec3 sky = GateCircles.center(anchor, gate);
        GateSky.clouds(c, sky, age, endK);
        GateSky.arms(c, sky, age, endK);
        GateSky.lightning(c, sky, age, endK, f.commandAge() >= 0 && e < 0);
        // The aurora comes with the finished circle and brightens when the doors have opened.
        GateSky.aurora(c, sky, age, (f.commandAge() >= 0 && e < 0 ? 1.15 + .3 * Math.sin(age * .22) : 1) * smooth((age - 60) / 80) * (.6 + .4 * smooth((age - SilentGateSequence.OPEN_END) / 30)) * (1 - smooth(endK / .6)));
        GateCircles.great(c, anchor, gate, age, e);
        for (int i = 0; i < count; i++) GateMinor.sky(c, SilentGateSequence.skyCircle(anchor, f.yaw(), f.seed(), i, count), i, age, gate, wave, e);
        for (int i = 0; i < SilentGateSequence.GROUND_RING; i++)
            GateMinor.ground(c, SilentGateSequence.groundCircle(anchor, f.yaw(), i), anchor, i, age, SilentGateSequence.groundCircleGold(i), gate, wave, e);
        GateMinor.aura(c, anchor, age, e);
        GateFx.castBurst(c, anchor, age - 3, 1);
        if (f.commandAge() >= 0) GateFx.castBurst(c, anchor, f.commandAge(), 1.8);
        GateMinor.wave(c, gate, f.yaw(), wave);
        }
        gate(c, anchor, f.yaw(), age, f.commandAge(), e, front);
        // Beams from a circle's nozzle to the hit point. No circle moves: the beam leans as it must. The kill beam's circle gathers light first.
        // Flying over the scope bars is an accent, not a habit: only the first volley after the command and the kill beam do it.
        boolean volley = f.commandAge() >= 0 && f.commandAge() < SilentGateSequence.ACTIVATE_TICKS + 30;
        for (Shot s : shots) {
            if (s.circle() < 0 || s.circle() >= count) continue;
            // The ground's light and scars stay in the picture, under the scope bars, whatever the beam itself does.
            if (!front && s.hit().distanceToSqr(eye) < 260 * 260) GateBeams.pool(c, s.hit(), beamLife(s, now) - GateBeams.FALL_TICKS,
                    s.heavy() ? GateBeams.KILL : s.circle() % 2 == 0 ? GateMinor.JADE : GateCircles.GLOW_BLUE, s.tick() * 31L + s.circle(), s.heavy() ? 2 : 1);
            if ((layer == 1 || front) && (s.heavy() || volley) != front) continue;
            Vec3 from = SilentGateSequence.skyCircle(anchor, f.yaw(), f.seed(), s.circle(), count).add(0, -GateMinor.NOZZLE, 0);
            double life = beamLife(s, now);
            if (s.heavy() && life < 0) GateBeams.charge(c, from, 1 + life / SilentGateSequence.HEAVY_CHARGE);
            int tint = s.heavy() ? GateBeams.KILL : s.circle() % 2 == 0 ? GateMinor.JADE : GateCircles.GLOW_BLUE;
            // Far from the lens the smoke and the sparks of a strike are a pixel or two: they are left out (220 circles firing must fit the quad budget).
            double reach = s.hit().distanceToSqr(eye);
            if (reach < 150 * 150) GateBeams.smoke(c, s.hit(), life - GateBeams.FALL_TICKS, tint, s.tick() * 31L + s.circle(), s.heavy() ? 2 : 1);
            if (reach < 100 * 100) GateBeams.sparks(c, s.hit(), life - GateBeams.FALL_TICKS, tint, s.tick() * 31L + s.circle(), s.heavy() ? 2 : 1);
            if (life < 0 || life >= BEAM_TICKS) continue;
            int stacked = 0;
            for (Shot o : shots) { double ol = beamLife(o, now); if (o.circle() >= 0 && ol >= 0 && ol < BEAM_TICKS && o.hit().distanceToSqr(s.hit()) < 64) stacked++; }
            GateBeams.shot(c, from, s.hit(), life, tint, s.tick() * 31L + s.circle(), now,
                    s.heavy() ? 1 : 1 / Math.max(1, stacked * .6), s.heavy() ? 2 : 1);
        }
        if (!front) {
            GateFx.motes(c, age, smooth((age - SilentGateSequence.CIRCLE_START) / 70) * (1 - smooth(endK / .8)));
            var sim = GateMana.sim(f.casterId());
            if (sim != null) GateMana.emit(c, sim, f);
        }
        if (!front) lastQuads = c.quads;
        return c.quads;
    }
    /** Ticks since the beam of {@code s} fired (the kill beam fires HEAVY_CHARGE ticks after it is announced); negative before. */
    static double beamLife(Shot s, double now) { return now - s.tick() - (s.heavy() ? SilentGateSequence.HEAVY_CHARGE : stagger(s)); }
    /** The server fires in ticks: five or six circles on the same instant, twenty times a second, which reads as volleys. Each beam is held back by its own
     *  fraction of {@value #STAGGER_TICKS} ticks, so they leave one after the other, a running fire (owner, 2026-11-12). Looks only: the hit is the server's. */
    static final double STAGGER_TICKS = 2.4;
    static double stagger(Shot s) { double h = Math.sin(s.circle() * 12.9898 + s.tick() * 78.233) * 43758.5453; return (h - Math.floor(h)) * STAGGER_TICKS; }

    /** Quads of the last emitted cast (dev statistic read by the QA probe). */
    public static volatile int lastQuads;

    /** The gate: slit it climbs out of, descent wind and dust, the sculpted model, shockwave, pressure rings and the opening light; in the ending
     *  (e = ticks after the barrage) the doors close, the gate climbs back into the slit and on into the great circle. */
    private static void gate(Ctx c, Vec3 anchor, float yaw, double age, double commandAge, double e) { gate(c, anchor, yaw, age, commandAge, e, false); }
    /** {@code front}: only what the gate throws over the scope bars (its feathers). */
    private static void gate(Ctx c, Vec3 anchor, float yaw, double age, double commandAge, double e, boolean front) {
        double endK = e < 0 ? 0 : clamp01(e / SilentGateSequence.ENDING_TICKS);
        double descent = SilentGateSequence.gateDescent(age), rise = e < 0 ? 0 : SilentGateSequence.gateRise(e);
        double fadeOut = e < 0 ? 1 : 1 - smooth((rise - 1.15) / .9);
        double appear = smooth((age - SilentGateSequence.DESCENT_START) / 8);
        double slit = e < 0 ? smooth((age - SilentGateSequence.SLIT_START) / 14) * (1 - smooth((age - SilentGateSequence.DESCENT_END + 6) / 14)) : Math.sin(Math.PI * clamp01((e - 70) / 60));
        // The gate comes down out of a crack in the sky: a slit of light in the great circle's plane, across the gate's width. Above that plane the gate is not there yet.
        GateFx.Pose slitPose = GateFx.Pose.of(SilentGateSequence.gateFoot(anchor, yaw, 0), yaw);
        double plane = anchor.y + SilentGateSequence.circleAltitude(anchor.y);
        Vec3 crack = new Vec3(slitPose.foot().x, plane, slitPose.foot().z);
        if (!c.solidOnly && !front) GateFx.slit(c, crack, slitPose.r(), slitPose.d(), slit, SilentGateSequence.GATE_WIDTH * .7 * SilentGateSequence.SCALE);
        if (!c.solidOnly && !front && e < 0) GateFx.strain(c, crack, slitPose.r(), slitPose.d(), age, SilentGateSequence.GATE_WIDTH * .7 * SilentGateSequence.SCALE);
        if (appear <= 0 || fadeOut <= 0) return;
        double saved = c.fade;
        c.fade = saved * appear * fadeOut;
        if (c.beforeBody && c.fade < .95) { c.fade = saved; return; }
        // The stuck doors shudder the whole gate a little.
        double shake = e < 0 ? SilentGateSequence.gateShake(age) : 0;
        GateFx.Pose pose = GateFx.Pose.of(SilentGateSequence.gateFoot(anchor, yaw, descent + rise).add(0, shake * Math.sin(age * 2.3), 0), yaw);
        double speed = (SilentGateSequence.gateDescent(age - .5) - SilentGateSequence.gateDescent(age + .5)) * SilentGateSequence.GATE_DROP;
        double open = e < 0 ? SilentGateSequence.gateOpen(age) : SilentGateSequence.closingOpen(e);
        double spread = GateModel.spread(age, e);
        // The feathers fly over the scope bars only in the burst of the wings; after that they fall inside the picture.
        boolean burst = age >= SilentGateSequence.SHOCK && age < SilentGateSequence.SHOCK + 70;
        if (front) { c.clipY = plane; if (burst) GateFx.feathers(c, pose, age, spread, endK); c.clipY = Double.MAX_VALUE; c.fade = saved; return; }
        double travel = (1 - descent) * SilentGateSequence.GATE_DROP;
        c.clipY = plane;
        GateModel.draw(c, pose, age, open, GateModel.vein(age, commandAge, endK), spread);
        if (!c.solidOnly) {
            GateFx.wind(c, pose, age, Math.max(0, speed), travel);
            GateFx.smoke(c, pose, age, Math.max(0, speed), travel, open, endK);
            if (c.layer != 1 || !burst) GateFx.feathers(c, pose, age, spread, endK);
            c.clipY = Double.MAX_VALUE;
            if (e < 0) GateFx.groundWave(c, new Vec3(pose.foot().x, anchor.y, pose.foot().z), age - SilentGateSequence.SHOCK);
            if (e < 0) {
                GateFx.dust(c, pose, age, SilentGateSequence.DESCENT_END, 1);
                GateFx.shockwave(c, pose, age, SilentGateSequence.SHOCK, 120, 34, 1);
                // The same blow runs through the cloud deck across the whole sky, and again when the command is given.
                GateFx.shockwave(c, pose, age, SilentGateSequence.SHOCK, 190, 80, .75);
                if (commandAge >= 0) {
                    GateFx.shockwave(c, pose, commandAge, 0, 190, 80, .6);
                    // While it rains the sky beats: a ring runs out through the cloud deck every two seconds.
                    int beat = (int) (commandAge / 40) * 40;
                    if (beat > 0) GateFx.shockwave(c, pose, commandAge, beat, 190, 80, .3);
                }
                // The doors meet their stops: dust off the sill and a short ring of light at door height.
                GateFx.dust(c, pose, age, SilentGateSequence.OPEN_END, .55);
                GateFx.shockwave(c, pose, age, SilentGateSequence.OPEN_END, 46, 37, .6);
            } else {
                // The doors shut: the same, smaller, at the moment they close.
                GateFx.dust(c, pose, e, SilentGateSequence.END_CLOSE, .55);
                GateFx.shockwave(c, pose, e, SilentGateSequence.END_CLOSE, 46, 37, .6);
            }
            GateFx.pressure(c, pose, age, endK);
            GateFx.grace(c, pose, anchor, anchor.y + SilentGateSequence.circleAltitude(anchor.y), anchor.y, age,
                    smooth((age - SilentGateSequence.SHOCK) / 40) * (1 - smooth(endK / .5)));
        }
        c.fade = saved;
    }

    public static void tick(Minecraft client) {
        GateAtlas.prepare();
        if (client.level != world || client.player == null) { reset(); return; }
        long now = world.getGameTime();
        CASTS.entrySet().removeIf(e -> {
            boolean gone = over(e.getValue(), now)
                    || world.getEntity(e.getKey()) instanceof net.minecraft.world.entity.LivingEntity living && !living.isAlive();
            if (gone) CLOSED.put(e.getKey(), e.getValue().startedAt);
            return gone;
        });
        CASTS.values().forEach(c -> trim(c, now));
        GateMana.tick(frames(0), world);
        SilentGateSoundscape.tick(client, frames(0));
        // The cast brings its own night (this viewer's sky only; the server's clock is never touched).
        GateNight.tick(world, night(client, 0));
        vanillaClouds(client, CASTS.values().stream().anyMatch(c -> c.anchor != null && c.anchor.distanceToSqr(client.player.position()) < RENDER_RANGE * RENDER_RANGE));
    }

    /** How deep the night is for this viewer at {@code partial}: the deepest of any cast in range. */
    static double night(Minecraft client, float partial) {
        double night = 0;
        if (client.player == null) return 0;
        for (Frame f : frames(partial)) if (f.anchor() != null && f.anchor().distanceToSqr(client.player.position()) < RENDER_RANGE * RENDER_RANGE) night = Math.max(night, GateNight.depth(f));
        return night;
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
        CASTS.clear(); LATEST.clear(); CLOSED.clear(); world = null; GateNight.reset(); SilentGateSoundscape.reset();
        if (savedClouds != null) vanillaClouds(Minecraft.getInstance(), false);
    }
}
