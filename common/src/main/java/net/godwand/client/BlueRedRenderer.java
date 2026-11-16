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
    static final class BlueView { Vec3 prev, pos; double at, readyAt = -1, born; BlueState state = BlueState.CHARGING; }
    record Burst(Vec3 pos, double at, boolean blue) {}
    record Purple(Vec3 pos, double at) {}
    static final class Flight { final int owner; final Vec3 origin, dir; final double at; Vec3 end; double endAt = -1;
        Flight(int owner, Vec3 origin, Vec3 dir, double at) { this.owner = owner; this.origin = origin; this.dir = dir; this.at = at; } }

    private static final Map<Integer, BlueView> BLUES = new HashMap<>();
    private static final Map<Integer, Double> CHARGES = new HashMap<>();
    private static final List<Flight> FLIGHTS = new ArrayList<>();
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
                v.pos = at; v.at = clock;
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
                impactNear(at);
                sound(at, SoundEvents.WARDEN_SONIC_BOOM, 1.5f, 1.5f);
            }
            case BlueRedPayload.RED_END -> {
                endFlight(p.ownerId(), at);
                BURSTS.add(new Burst(at, clock, false));
                impactNear(at);
                sound(at, SoundEvents.GENERIC_EXPLODE.value(), 3f, .8f);
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
        for (Purple p : PURPLES) if (clock - p.at() == BlueRedRules.PURPLE_DETONATE) detonated(p.pos());
        PURPLES.removeIf(p -> clock - p.at() > BlueRedRules.PURPLE_CINEMATIC + 10);
    }

    public static void reset() { BLUES.clear(); CHARGES.clear(); FLIGHTS.clear(); BURSTS.clear(); PURPLES.clear(); IMPACTS.clear(); clock = 0; }

    private static void detonated(Vec3 at) {
        sound(at, SoundEvents.GENERIC_EXPLODE.value(), 6f, .5f);
        sound(at, SoundEvents.WARDEN_SONIC_BOOM, 6f, .6f);
        var player = Minecraft.getInstance().player;
        if (player != null && player.position().distanceTo(at) < 120) {
            GodWandCinematicEngine.addTrauma((float) Math.max(.15, 1 - player.position().distanceTo(at) / 120), 24);
            GodWandCinematicEngine.setFovPunch(1.2f, 5);
        }
    }

    private static Vec3 drawn(BlueView v, double partial) {
        return BlueRedVisual.interp(v.prev, v.pos, (clock + partial - v.at) / 2);
    }

    // ---- drawing ----

    private record Overlay(Matrix4f pose, Camera camera, float partial) {}
    private static Overlay overlay;

    public static void render(PoseStack pose, Camera camera, float partial) {
        overlay = new Overlay(new Matrix4f(pose.last().pose()), camera, partial);
        if (BLUES.isEmpty() && CHARGES.isEmpty() && FLIGHTS.isEmpty() && BURSTS.isEmpty() && PURPLES.isEmpty() && IMPACTS.isEmpty()) return;
        RenderSystem.enableBlend(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        GateAtlas.bind(); RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
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
        GateAtlas.bind(); RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
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
                for (Burst b : IMPACTS) BlueRedDraw.impact(c, b.pos(), (int) (clock - b.at()), eye, lookVec);
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
            BlueRedDraw.blue(c, drawn(b, partial), b.state != BlueState.CHARGING, now - b.born, b.readyAt < 0 ? -1 : now - b.readyAt);
        var level = Minecraft.getInstance().level;
        for (var charge : CHARGES.entrySet()) {
            var owner = level == null ? null : level.getEntity(charge.getKey());
            if (owner == null) continue;
            // Held low and to the right, where the raised hand will be, so it frames the view instead of blocking it.
            Vec3 view = owner.getViewVector(partial), right = view.cross(new Vec3(0, 1, 0));
            right = right.lengthSqr() < 1e-6 ? Vec3.ZERO : right.normalize();
            Vec3 at = owner.getEyePosition(partial).add(view.scale(1.9)).add(right.scale(.45)).add(0, -.3, 0);
            double age = now - charge.getValue();
            BlueRedDraw.redOrb(c, at, BlueRedVisual.redChargeSize(age, GodWandConfig.SERVER.redChargeTicks), age);
        }
        for (Flight f : FLIGHTS) {
            double age = now - f.at, after = f.endAt < 0 ? 0 : now - f.endAt;
            Vec3 head = BlueRedVisual.redHead(f.origin, f.dir, age, Math.min(160, GodWandConfig.SERVER.redRange), f.end);
            BlueRedDraw.redFlight(c, f.origin, f.dir, head, age, 1 - clamp01(after / 10), f.owner * 31L + (long) f.at, f.end == null);
        }
        for (Burst b : BURSTS) BlueRedDraw.burst(c, b.pos(), now - b.at(), b.blue());
        if (!PurpleCinematic.inVoid()) for (Purple p : PURPLES) BlueRedDraw.purple(c, p.pos(), now - p.at(), eye, look);
        return c.quads;
    }
}
