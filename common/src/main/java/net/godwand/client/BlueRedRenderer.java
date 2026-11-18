package net.godwand.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.godwand.config.GodWandConfig;
import net.godwand.network.BlueRedPayload;
import net.godwand.skill.BlueRedRules;
import net.godwand.skill.BlueRedRules.BlueState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.*;

import static net.godwand.client.GateDraw.*;

/** Draws Blue, Red and their bursts for every nearby player, from BlueRedPayload events. */
public final class BlueRedRenderer {
    static final class BlueView {
        Vec3 prev, pos; double at, gap = 2, readyAt = -1, born; BlueState state = BlueState.CHARGING;
        /** Where it has been (oldest first) and when, for the smears it leaves behind. */
        final java.util.ArrayDeque<Vec3> trail = new java.util.ArrayDeque<>(); final java.util.ArrayDeque<Double> trailAt = new java.util.ArrayDeque<>();
    }
    record Burst(Vec3 pos, double at, boolean blue, Vec3 dir) {
        Burst(Vec3 pos, double at, boolean blue) { this(pos, at, blue, Vec3.ZERO); }
    }
    record Purple(Vec3 pos, double at) {}
    static final class Flight { final int owner; final Vec3 origin, dir; final double at; Vec3 end; double endAt = -1;
        Flight(int owner, Vec3 origin, Vec3 dir, double at) { this.owner = owner; this.origin = origin; this.dir = dir; this.at = at; } }

    private static final Map<Integer, BlueView> BLUES = new HashMap<>();
    private static final Map<Integer, Double> CHARGES = new HashMap<>();
    private static final List<Flight> FLIGHTS = new ArrayList<>();
    /** Whether a Red is in flight (used by the QA probe to time its side camera). */
    static boolean anyFlight() { return !FLIGHTS.isEmpty(); }
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final List<Purple> PURPLES = new ArrayList<>();
    private static final List<Burst> IMPACTS = new ArrayList<>();
    private static double clock;

    private BlueRedRenderer() {}

    public static void receive(BlueRedPayload p) {
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        switch (p.kind()) {
            case BlueRedPayload.BLUE -> {
                BlueView v = BLUES.get(p.ownerId());
                if (v == null) { v = new BlueView(); v.prev = at; v.born = clock; BLUES.put(p.ownerId(), v); sound(at, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.6f); }
                else v.prev = drawn(v, 1);
                v.trail.addLast(v.pos == null ? at : v.pos); v.trailAt.addLast(clock);
                while (v.trail.size() > 16) { v.trail.removeFirst(); v.trailAt.removeFirst(); }
                // The server sends every tick while Blue moves and every other tick when it is still: blend over the real gap, adding no lag.
                v.gap = Math.max(1, Math.min(2, clock - v.at)); v.pos = at; v.at = clock;
                BlueState state = BlueState.values()[p.state()];
                if (state != BlueState.CHARGING && v.readyAt < 0) {
                    v.readyAt = clock;
                    sound(at, SoundEvents.BEACON_POWER_SELECT, 2f, .7f);
                    var player = Minecraft.getInstance().player;
                    if (player != null && player.position().distanceTo(at) < 32) GodWandCinematicEngine.addTrauma(.18f, 6);
                }
                v.state = state;
            }
            case BlueRedPayload.BLUE_GONE -> BLUES.remove(p.ownerId());
            case BlueRedPayload.BLUE_SHATTER -> { BURSTS.add(new Burst(at, clock, true)); sound(at, SoundEvents.GLASS_BREAK, 2f, .6f); }
            case BlueRedPayload.RED_CHARGE -> { CHARGES.put(p.ownerId(), clock); sound(at, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1f, 1.4f); }
            case BlueRedPayload.RED_CANCEL -> CHARGES.remove(p.ownerId());
            case BlueRedPayload.RED_FIRE -> {
                CHARGES.remove(p.ownerId());
                FLIGHTS.add(new Flight(p.ownerId(), at, new Vec3(p.dx(), p.dy(), p.dz()), clock));
                // The shooter feels the force leave: a short, hard kick and a brief widening of the view.
                var me = Minecraft.getInstance().player;
                if (me != null && me.getId() == p.ownerId()) { GodWandCinematicEngine.addTrauma(.35f, 8); GodWandCinematicEngine.setFovPunch(1.08f, 2); }
                sound(at, SoundEvents.WARDEN_SONIC_BOOM, 1.5f, 1.5f);
            }
            case BlueRedPayload.RED_END -> {
                endFlight(p.ownerId(), at);
                BURSTS.add(new Burst(at, clock, false, new Vec3(p.dx(), p.dy(), p.dz())));
                impactNear(at);
                var me = Minecraft.getInstance().player;
                if (me != null) GodWandCinematicEngine.addTrauma((float) Math.max(0, .45 * (1 - me.position().distanceTo(at) / 40)), 10);
                sound(at, SoundEvents.GENERIC_EXPLODE.value(), 3f, .8f);
            }
            case BlueRedPayload.RED_HIT -> {
                BURSTS.add(new Burst(at, clock, false, new Vec3(p.dx(), p.dy(), p.dz())));
                impactNear(at);
                var me = Minecraft.getInstance().player;
                if (me != null) GodWandCinematicEngine.addTrauma((float) Math.max(0, .35 * (1 - me.position().distanceTo(at) / 40)), 8);
                sound(at, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 1.1f);
            }
            case BlueRedPayload.PURPLE -> {
                endFlight(p.otherId(), at); BLUES.remove(p.ownerId());
                PURPLES.add(new Purple(at, clock));
                PurpleCinematic.receive(p);
                sound(at, SoundEvents.BEACON_ACTIVATE, 3f, .5f);
            }
            default -> {}
        }
    }

    /** Red's black-and-red impact frames, for anyone close enough to feel it. */
    private static void impactNear(Vec3 at) {
        var player = Minecraft.getInstance().player;
        if (GodWandConfig.CLIENT.impactFrames && player != null && player.position().distanceTo(at) < 48) { IMPACTS.clear(); IMPACTS.add(new Burst(at, clock, false)); }
    }

    private static void endFlight(int owner, Vec3 at) {
        for (Flight f : FLIGHTS) if (f.owner == owner && f.end == null) { f.end = at; f.endAt = clock; return; }
    }

    private static void sound(Vec3 at, SoundEvent sound, float volume, float pitch) {
        var level = Minecraft.getInstance().level;
        if (level != null) level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    public static void tick(Minecraft client) {
        if (client.level == null) { reset(); return; }
        if (client.isPaused()) return;
        clock++;
        BURSTS.removeIf(b -> clock - b.at() > 20);
        IMPACTS.removeIf(b -> clock - b.at() > 6);
        FLIGHTS.removeIf(f -> f.endAt >= 0 ? clock - f.endAt > 10 : clock - f.at > 200);
        CHARGES.values().removeIf(at -> clock - at > 400);
        for (Purple p : PURPLES) {
            double age = clock - p.at();
            if (age == BlueRedRules.PURPLE_DETONATE) detonated(p.pos());
            var me = Minecraft.getInstance().player;
            if (me != null && me.position().distanceTo(p.pos()) < 60) {
                // The contact hits (owner, 2026-11-18), and an orb sweeping past the camera shakes it a little.
                if (age == BlueRedRules.PURPLE_BLINK) {
                    GodWandCinematicEngine.addTrauma(.45f, 10);
                    // A pressure front hugging the ground, like Fuga's (owner, 2026-11-18).
                    ImpactDebrisRenderer.groundWave(groundUnder(p.pos()), 18);
                }
                if (age == BlueRedRules.PURPLE_DETONATE) ImpactDebrisRenderer.groundWave(groundUnder(p.pos()), 46);
                if (age > BlueRedRules.PURPLE_CONTACT && age < BlueRedRules.PURPLE_SWIRL_END) {
                    Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(), centre = p.pos().add(0, BlueRedRules.purpleRise(age), 0);
                    for (Vec3 o : BlueRedDraw.convergeOffsets(age)) {
                        double d = centre.add(o).distanceTo(cam);
                        if (d < 5) GodWandCinematicEngine.addTrauma((float) (.015 * (5 - d) / 5), 4);
                    }
                }
            }
            // The 2D act shakes harder as the spheres close in, and the touch hits with an impact frame (owner, 2026-11-18).
            if (PurpleCinematic.active() && PurpleCinematic.inVoid()) {
                double close = BlueRedDraw.voidClose(age);
                if (close > .6) GodWandCinematicEngine.addTrauma((float) (.05 * (close - .6) / .4), 6);
                if (age == BlueRedDraw.voidTouch() && GodWandConfig.CLIENT.impactFrames) { IMPACTS.clear(); IMPACTS.add(new Burst(p.pos(), clock, true)); GodWandCinematicEngine.addTrauma(.5f, 10); }
            }
        }
        PURPLES.removeIf(p -> clock - p.at() > BlueRedRules.PURPLE_CINEMATIC + 10);
    }

    public static void reset() { BLUES.clear(); CHARGES.clear(); FLIGHTS.clear(); BURSTS.clear(); PURPLES.clear(); IMPACTS.clear(); clock = 0; }

    private static void detonated(Vec3 at) {
        var me = Minecraft.getInstance().player;
        if (GodWandConfig.CLIENT.impactFrames && me != null && me.position().distanceTo(at) < 120) { IMPACTS.clear(); IMPACTS.add(new Burst(at, clock, true)); }
        sound(at, SoundEvents.GENERIC_EXPLODE.value(), 6f, .5f);
        sound(at, SoundEvents.WARDEN_SONIC_BOOM, 6f, .6f);
        var player = Minecraft.getInstance().player;
        if (player != null && player.position().distanceTo(at) < 120) {
            GodWandCinematicEngine.addTrauma((float) Math.max(.15, 1 - player.position().distanceTo(at) / 120), 24);
            GodWandCinematicEngine.setFovPunch(1.2f, 5);
        }
    }

    private static Vec3 groundUnder(Vec3 at) {
        var lvl = Minecraft.getInstance().level;
        return lvl == null ? at : new Vec3(at.x, lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(at.x), (int) Math.floor(at.z)), at.z);
    }

    private static Vec3 drawn(BlueView v, double partial) {
        return BlueRedVisual.interp(v.prev, v.pos, (clock + partial - v.at) / v.gap);
    }

    // ---- drawing ----

    private record Overlay(Matrix4f pose, Camera camera, float partial) {}
    private static Overlay overlay;

    /** This frame's bloom (strength, shafts, light x, y on screen 0..1, partial), run on the finished frame by flushPostFx; reuses the Silent Gate chain. */
    private static float[] bloom;
    public static boolean bloomPending() { return bloom != null; }
    public static void flushPostFx() {
        float[] b = bloom; bloom = null;
        if (b != null) GatePostFx.process(b[0], b[1], b[2], b[3], b[4], b[7], 0, b[5], b[6], (float) (clock / 20.0));
    }

    private static void planBloom(PoseStack pose, Camera camera, float partial) {
        double now = clock + partial, strength = 0, burst = 0, warp = 0, warpSize = 0;
        Vec3 light = null;
        if (!PURPLES.isEmpty()) {
            Purple p = PURPLES.get(PURPLES.size() - 1);
            double age = now - p.at();
            light = p.pos().add(0, BlueRedRules.purpleRise(age), 0); strength = .65;
            // The world bends round Purple: building as it swells, a hard kick at the burst, dying away.
            if (age >= BlueRedRules.PURPLE_MERGE) {
                double grow = smooth((age - BlueRedRules.PURPLE_MERGE) / (double) (BlueRedRules.PURPLE_DETONATE - BlueRedRules.PURPLE_MERGE));
                double after = age - BlueRedRules.PURPLE_DETONATE;
                warp = after < 0 ? .4 + .4 * grow : .8 * Math.exp(-after / 16);
                // The sphere's radius on screen, in screen heights (about a 70 degree view).
                warpSize = BlueRedDraw.purpleSize(age) / (1.4 * Math.max(4, light.distanceTo(camera.getPosition())));
            }
            burst = .55 * (1 - clamp01((age - BlueRedRules.PURPLE_DETONATE) / 24.0)) * (age >= BlueRedRules.PURPLE_DETONATE ? 1 : 0)
                    + .35 * smooth((age - (BlueRedRules.PURPLE_MERGE - 20)) / 20.0) * (age < BlueRedRules.PURPLE_VOID_END ? 1 : 0);
        } else {
            double best = Double.MAX_VALUE;
            for (BlueView b : BLUES.values()) { double d = b.pos.distanceToSqr(camera.getPosition()); if (d < best) { best = d; light = b.pos; } }
            for (Flight f : FLIGHTS) if (f.end == null) { light = BlueRedVisual.redHead(f.origin, f.dir, now - f.at, 160, null); break; }
            if (light != null || !CHARGES.isEmpty() || !BURSTS.isEmpty()) strength = .45;
        }
        if (strength <= 0) return;
        float sx = .5f, sy = .5f;
        if (light != null) {
            Vec3 src = light.subtract(camera.getPosition());
            var clip = new org.joml.Vector4f((float) src.x, (float) src.y, (float) src.z, 1)
                    .mul(pose.last().pose()).mul(RenderSystem.getModelViewMatrix()).mul(RenderSystem.getProjectionMatrix());
            if (clip.w > .001f) { sx = clip.x / clip.w * .5f + .5f; sy = clip.y / clip.w * .5f + .5f; } else burst = 0;
        }
        if (burst == 0 && light != null && sx == .5f && sy == .5f) warp = 0;
        // Black-and-white ink frames (the gate filter) on the contact and the blast: two frames, lit-on-black then inverted.
        float ink = 0;
        if (!PURPLES.isEmpty() && GodWandConfig.CLIENT.impactFrames) {
            int age = (int) Math.floor(now - PURPLES.get(PURPLES.size() - 1).at());
            for (int hit : new int[]{BlueRedRules.PURPLE_BLINK, BlueRedRules.PURPLE_DETONATE}) if (age == hit) ink = 1; else if (age == hit + 1) ink = 2;
        }
        bloom = new float[]{(float) (GodWandConfig.CLIENT.bloomStrength * strength), (float) burst, sx, sy, partial, (float) warp, (float) Math.min(.9, warpSize), ink};
    }

    public static void render(PoseStack pose, Camera camera, float partial) {
        overlay = new Overlay(new Matrix4f(pose.last().pose()), camera, partial);
        planBloom(pose, camera, partial);
        if (BLUES.isEmpty() && CHARGES.isEmpty() && FLIGHTS.isEmpty() && BURSTS.isEmpty() && PURPLES.isEmpty() && IMPACTS.isEmpty()) return;
        RenderSystem.enableBlend(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        BlueRedAtlas.bind(); BlueRedShader.bind((float) (((clock + partial) / 20.0) % 3600));
        try {
            for (int layer : new int[]{0, 1}) {
                if (layer == 0) RenderSystem.defaultBlendFunc();
                else RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                var look = camera.getLookVector();
                emit(buffer, pose.last().pose(), camera.getPosition(), new Vec3(look.x(), look.y(), look.z()), layer, partial);
                var mesh = buffer.build();
                if (mesh != null) BufferUploader.drawWithShader(mesh);
            }
        } finally {
            RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
        }
    }

    /** Drawn last in the frame (after clouds and weather), depth off, with this frame's matrix: Purple's black void, then Red's impact frames. */
    public static void renderOverlay() {
        Overlay o = overlay; overlay = null;
        if (o == null || IMPACTS.isEmpty() && !(PurpleCinematic.inVoid() && !PURPLES.isEmpty())) return;
        RenderSystem.enableBlend(); RenderSystem.disableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        BlueRedAtlas.bind(); BlueRedShader.bind((float) (((clock + o.partial()) / 20.0) % 3600));
        try {
            var look = o.camera().getLookVector();
            Vec3 lookVec = new Vec3(look.x(), look.y(), look.z()), eye = o.camera().getPosition();
            for (int layer : new int[]{0, 1}) {
                if (layer == 0) RenderSystem.defaultBlendFunc();
                else RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                Ctx c = new Ctx(); c.v = buffer; c.m = o.pose(); c.eye = eye; c.layer = layer; c.nearMin = .05; c.nearMax = .4;
                if (PurpleCinematic.inVoid() && !PURPLES.isEmpty()) {
                    Purple p = PURPLES.get(PURPLES.size() - 1);
                    BlueRedDraw.purpleVoid(c, p.pos(), clock + o.partial() - p.at(), eye, lookVec);
                }
                for (Burst b : IMPACTS) BlueRedDraw.impact(c, b.pos(), (int) (clock - b.at()), eye, lookVec, b.blue());
                var mesh = buffer.build();
                if (mesh != null) BufferUploader.drawWithShader(mesh);
            }
        } finally {
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
        }
    }

    static int emit(VertexConsumer v, Matrix4f m, Vec3 eye, Vec3 look, int layer, float partial) {
        Ctx c = new Ctx(); c.v = v; c.m = m; c.eye = eye; c.layer = layer; c.nearMin = .05; c.nearMax = .4;
        double now = clock + partial;
        for (BlueView b : BLUES.values())
        {
            Vec3[] trail = b.trail.toArray(new Vec3[0]);
            double[] ago = new double[trail.length];
            int i = 0;
            for (double t : b.trailAt) ago[i++] = now - t;
            BlueRedDraw.blue(c, drawn(b, partial), b.state != BlueState.CHARGING, now - b.born, b.readyAt < 0 ? -1 : now - b.readyAt, trail, ago);
        }
        var level = Minecraft.getInstance().level;
        for (var charge : CHARGES.entrySet()) {
            var owner = level == null ? null : level.getEntity(charge.getKey());
            if (owner == null) continue;
            // Held low and to the right, where the raised hand will be, so it frames the view instead of blocking it; its strokes fly up, right
            // and away from the camera, never into it.
            Vec3 view = owner.getViewVector(partial), right = view.cross(new Vec3(0, 1, 0));
            right = right.lengthSqr() < 1e-6 ? Vec3.ZERO : right.normalize();
            Vec3 at = owner.getEyePosition(partial).add(view.scale(1.9)).add(right.scale(.45)).add(0, -.3, 0);
            double age = now - charge.getValue();
            double squeeze = BlueRedVisual.redSqueeze(age, GodWandConfig.SERVER.redChargeTicks);
            BlueRedDraw.redOrb(c, at, BlueRedVisual.redChargeSize(age, GodWandConfig.SERVER.redChargeTicks) * (1 - .4 * squeeze), age, view.scale(.3).add(0, 1, 0).add(right.scale(.8)), .45);
        }
        for (Flight f : FLIGHTS) {
            double age = now - f.at, after = f.endAt < 0 ? 0 : now - f.endAt;
            Vec3 head = BlueRedVisual.redHead(f.origin, f.dir, age, Math.min(160, GodWandConfig.SERVER.redRange), f.end);
            BlueRedDraw.redFlight(c, f.origin, f.dir, head, age, 1 - clamp01(after / 10), f.owner * 31L + (long) f.at, f.end == null);
        }
        for (Burst b : BURSTS) BlueRedDraw.burst(c, b.pos(), now - b.at(), b.blue(), b.dir());
        if (!PurpleCinematic.blackout()) for (Purple p : PURPLES) {
            double age = now - p.at();
            // For the two players the world pair is gone before the void's pair shows (a dip through black), so two pairs never show at once.
            c.fade = PurpleCinematic.active() && age < BlueRedRules.PURPLE_MERGE ? 1 - Math.min(1, BlueRedDraw.voidAlpha(age) / .55) : 1;
            var lvl = Minecraft.getInstance().level;
            BlueRedDraw.groundAt = lvl == null ? null : (x, z) -> lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            BlueRedDraw.groundY = lvl == null ? Double.NaN : lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(p.pos().x), (int) Math.floor(p.pos().z));
            BlueRedDraw.purple(c, p.pos(), age, eye, look);
            BlueRedDraw.groundY = Double.NaN;
            BlueRedDraw.groundAt = null;
            c.fade = 1;
        }
        return c.quads;
    }
}
