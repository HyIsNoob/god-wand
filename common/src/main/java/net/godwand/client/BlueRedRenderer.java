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
    private static final int BLUE_GLOW = 0x2E6BFF, BLUE_MID = 0x5AA0FF, BLUE_WHITE = 0xE8F6FF, BLUE_RIM = 0xBFE6FF, BLUE_DARK = 0x040A1C;
    private static final int RED = 0xFF2A14, RED_HOT = 0xFFB070, RED_DARK = 0x050102;

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
                sound(at, SoundEvents.WARDEN_SONIC_BOOM, 1.5f, 1.5f);
            }
            case BlueRedPayload.RED_END -> {
                endFlight(p.ownerId(), at);
                BURSTS.add(new Burst(at, clock, false));
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
        FLIGHTS.removeIf(f -> f.endAt >= 0 ? clock - f.endAt > 10 : clock - f.at > 200);
        CHARGES.values().removeIf(at -> clock - at > 400);
        for (Purple p : PURPLES) if (clock - p.at() == BlueRedRules.PURPLE_DETONATE) detonated(p.pos());
        PURPLES.removeIf(p -> clock - p.at() > BlueRedRules.PURPLE_CINEMATIC + 10);
    }

    public static void reset() { BLUES.clear(); CHARGES.clear(); FLIGHTS.clear(); BURSTS.clear(); PURPLES.clear(); clock = 0; }

    private static void detonated(Vec3 at) {
        sound(at, SoundEvents.GENERIC_EXPLODE.value(), 6f, .5f);
        sound(at, SoundEvents.WARDEN_SONIC_BOOM, 6f, .6f);
        var player = Minecraft.getInstance().player;
        if (player != null) GodWandCinematicEngine.addTrauma((float) Math.max(.15, 1 - player.position().distanceTo(at) / 120), 24);
    }

    private static Vec3 drawn(BlueView v, double partial) {
        return BlueRedVisual.interp(v.prev, v.pos, (clock + partial - v.at) / 2);
    }

    // ---- drawing ----

    public static void render(PoseStack pose, Camera camera, float partial) {
        if (BLUES.isEmpty() && CHARGES.isEmpty() && FLIGHTS.isEmpty() && BURSTS.isEmpty() && PURPLES.isEmpty()) return;
        RenderSystem.enableBlend(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        GateAtlas.bind(); RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        try {
            for (int layer : new int[]{0, 1}) {
                if (layer == 0) RenderSystem.defaultBlendFunc();
                else RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                emit(buffer, pose.last().pose(), camera.getPosition(), layer, partial);
                var mesh = buffer.build();
                if (mesh != null) BufferUploader.drawWithShader(mesh);
            }
        } finally {
            RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
        }
    }

    static int emit(VertexConsumer v, Matrix4f m, Vec3 eye, int layer, float partial) {
        Ctx c = new Ctx(); c.v = v; c.m = m; c.eye = eye; c.layer = layer; c.nearMin = .05; c.nearMax = .4;
        double now = clock + partial;
        for (BlueView b : BLUES.values()) blue(c, drawn(b, partial), b, now);
        var level = Minecraft.getInstance().level;
        for (var charge : CHARGES.entrySet()) {
            var owner = level == null ? null : level.getEntity(charge.getKey());
            if (owner == null) continue;
            Vec3 at = owner.getEyePosition(partial).add(owner.getViewVector(partial).scale(1.5));
            redCharge(c, at, now - charge.getValue());
        }
        for (Flight f : FLIGHTS) redFlight(c, f, now);
        for (Burst b : BURSTS) burst(c, b, now - b.at());
        for (Purple p : PURPLES) purple(c, p.pos(), now - p.at());
        return c.quads;
    }

    /** Blue: a big glowing sphere with debris spiralling in; ready, its core goes dark, its rim white, rings spin round it and it beats. */
    private static void blue(Ctx c, Vec3 at, BlueView b, double now) {
        double age = now - b.born, r = BlueRedVisual.blueRadius(b.state, age), appear = smooth(age / 6);
        boolean ready = b.state != BlueState.CHARGING;
        double beat = ready ? BlueRedVisual.heartbeat(age) : 0, flash = BlueRedVisual.flash(b.readyAt < 0 ? -1 : now - b.readyAt);
        if (c.body() && ready) {
            for (int i = 0; i < 3; i++) sprite(c, at, r * (.95 - i * .12), .9 * appear, BLUE_DARK);
        }
        if (!c.glow()) return;
        halo(c, at, r * 2.6, (.28 + .12 * beat) * appear, BLUE_GLOW);
        if (ready) {
            halo(c, at, r * 1.35, (.75 + .25 * beat) * appear, BLUE_RIM);
            ring(c, at, r * 1.02, .22, BLUE_WHITE, .85 * appear);
            for (int k = 0; k < 3; k++) {
                double spin = age * (.06 + .02 * k) + k * 2.1;
                Vec3 n = new Vec3(Math.cos(spin), .6 * Math.sin(spin * .7 + k), Math.sin(spin)).normalize();
                orbit(c, at, n, r * (1.35 + .12 * k), .07, BLUE_RIM, .8 * appear);
            }
            lightning(c, at, r, age);
        } else {
            halo(c, at, r * 1.6, .7 * appear, BLUE_MID);
            halo(c, at, r * .8, .9 * appear, BLUE_WHITE);
            ring(c, at, r * 1.45, .35, BLUE_MID, .25 * appear);
        }
        motes(c, at, r, age, ready ? BLUE_RIM : BLUE_MID, 46);
        if (flash > 0) { halo(c, at, r * 6, flash, 0xFFFFFF); halo(c, at, r * 3, flash, 0xFFFFFF); }
    }

    /** Red charging: a small black core with a red rim and sparks sucked in, swelling as the charge fills. */
    private static void redCharge(Ctx c, Vec3 at, double age) {
        double size = BlueRedVisual.redChargeSize(age, GodWandConfig.SERVER.redChargeTicks);
        if (c.body()) { sprite(c, at, size * .7, .95, RED_DARK); sprite(c, at, size * .6, .95, RED_DARK); }
        if (!c.glow()) return;
        halo(c, at, size * 2.2, .9, RED);
        halo(c, at, size * 4.5, .35, RED);
        ring(c, at, size * .8, size * .25, RED_HOT, .9);
        motes(c, at, 1.4, age * 2.2, RED_HOT, 28);
    }

    /** Red in flight: a black-cored red head, a long hot trail and sparks along it. */
    private static void redFlight(Ctx c, Flight f, double now) {
        double age = now - f.at, after = f.endAt < 0 ? 0 : now - f.endAt;
        Vec3 head = BlueRedVisual.redHead(f.origin, f.dir, age, Math.min(160, GodWandConfig.SERVER.redRange), f.end);
        double fade = 1 - clamp01(after / 10);
        if (fade <= 0) return;
        double travelled = head.distanceTo(f.origin);
        Vec3 tail = head.subtract(f.dir.scale(Math.min(travelled, 18)));
        if (c.body() && f.end == null) sprite(c, head, .45, .95, RED_DARK);
        if (!c.glow()) return;
        shaft(c, tail, head, .04, .55, RED, .15 * fade, .95 * fade, age);
        shaft(c, head.subtract(f.dir.scale(Math.min(travelled, 7))), head, .02, .22, RED_HOT, .2 * fade, fade, age * 1.7);
        if (f.end == null) { halo(c, head, 1.6, .9, RED); ring(c, head, .5, .14, RED_HOT, .9); }
        Random rand = new Random(f.owner * 31L + (long) f.at);
        for (int i = 0; i < 24; i++) {
            double t = rand.nextDouble(), drift = (age - t * 6) * .08;
            Vec3 p = tail.add(head.subtract(tail).scale(t)).add(rand.nextGaussian() * drift, rand.nextGaussian() * drift, rand.nextGaussian() * drift);
            sprite(c, p, .06 + .05 * rand.nextDouble(), .9 * fade * (.3 + .7 * t), i % 3 == 0 ? RED_HOT : RED, GateAtlas.SOLID);
        }
    }

    private static void burst(Ctx c, Burst b, double age) {
        if (!c.glow()) return;
        double k = clamp01(age / (b.blue() ? 10 : 14)), fade = 1 - k;
        int color = b.blue() ? BLUE_RIM : RED;
        halo(c, b.pos(), 2 + age * (b.blue() ? .7 : .9), fade, color);
        halo(c, b.pos(), 1 + age * .3, fade, 0xFFFFFF);
        ring(c, b.pos(), 1 + age * (b.blue() ? 1.1 : 1.4), .4, color, .9 * fade);
    }

    /** Placeholder Purple (mechanics round): blue and red spiral together, a violet sphere swells with arcs, then detonates. */
    private static void purple(Ctx c, Vec3 at, double age) {
        int violet = 0x9B30FF, pale = 0xE6C8FF;
        double merge = clamp01(age / BlueRedRules.PURPLE_MERGE), blast = clamp01((age - BlueRedRules.PURPLE_DETONATE) / 16);
        double fade = 1 - clamp01((age - BlueRedRules.PURPLE_DETONATE - 16) / (BlueRedRules.PURPLE_CINEMATIC - BlueRedRules.PURPLE_DETONATE - 16));
        if (fade <= 0) return;
        if (age < BlueRedRules.PURPLE_MERGE) {
            double a = age * .6, d = 2.5 * (1 - merge);
            Vec3 off = new Vec3(Math.cos(a) * d, 0, Math.sin(a) * d);
            if (c.glow()) { halo(c, at.add(off), 2.5, .9, BLUE_MID); halo(c, at.subtract(off), 1.5, .9, RED); }
            return;
        }
        double size = age < BlueRedRules.PURPLE_DETONATE ? 1.5 + 4.5 * smooth((age - BlueRedRules.PURPLE_MERGE) / 60.0) : 6 + 20 * blast;
        if (c.body() && age < BlueRedRules.PURPLE_DETONATE) sprite(c, at, size * .7, .85, 0x12021E);
        if (!c.glow()) return;
        halo(c, at, size * 2.4, .55 * fade, violet);
        halo(c, at, size * 1.2, .9 * fade, pale);
        ring(c, at, size * 1.05, .4 + size * .05, pale, .8 * fade);
        if (age < BlueRedRules.PURPLE_DETONATE + 8) {
            Random rand = new Random((long) Math.floor(age / 2) * 104729L);
            for (int bolt = 0; bolt < 7; bolt++) {
                Vec3 dir = new Vec3(rand.nextGaussian(), rand.nextGaussian(), rand.nextGaussian()).normalize();
                Vec3 p = at.add(dir.scale(size * .9));
                for (int s = 0; s < 5; s++) {
                    Vec3 q = p.add(dir.scale(size * .35)).add(rand.nextGaussian() * .6, rand.nextGaussian() * .6, rand.nextGaussian() * .6);
                    shaft(c, p, q, .12, .08, pale, .95, .7, age);
                    p = q;
                }
            }
        }
        if (blast > 0) {
            halo(c, at, 8 + 60 * blast, (1 - blast) * 1.2, 0xFFFFFF);
            ring(c, at, 4 + 30 * blast, 2.5, violet, (1 - blast));
        }
    }

    /** Camera-facing ring: soft inner and outer edge. */
    private static void ring(Ctx c, Vec3 at, double radius, double width, int color, double alpha) {
        Vec3 look = at.subtract(c.eye);
        if (look.lengthSqr() < 1e-6) return;
        Vec3 u = look.cross(new Vec3(0, 1, 0));
        u = u.lengthSqr() < 1e-8 ? new Vec3(1, 0, 0) : u.normalize();
        Vec3 w = u.cross(look).normalize();
        band(c, at, u, w, radius, width, color, alpha);
    }

    /** Ring in the plane normal to n (the gyroscope rings of the ready Blue). */
    private static void orbit(Ctx c, Vec3 at, Vec3 n, double radius, double width, int color, double alpha) {
        Vec3 u = n.cross(new Vec3(0, 1, 0));
        u = u.lengthSqr() < 1e-8 ? new Vec3(1, 0, 0) : u.normalize();
        band(c, at, u, n.cross(u).normalize(), radius, width, color, alpha);
    }

    private static void band(Ctx c, Vec3 at, Vec3 u, Vec3 w, double radius, double width, int color, double alpha) {
        int n = 40;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            Vec3 d0 = u.scale(Math.cos(a0)).add(w.scale(Math.sin(a0))), d1 = u.scale(Math.cos(a1)).add(w.scale(Math.sin(a1)));
            Vec3 i0 = at.add(d0.scale(radius - width)), i1 = at.add(d1.scale(radius - width));
            Vec3 m0 = at.add(d0.scale(radius)), m1 = at.add(d1.scale(radius));
            Vec3 o0 = at.add(d0.scale(radius + width)), o1 = at.add(d1.scale(radius + width));
            gradient(c, i0, i1, m1, m0, color, 0, 0, alpha, alpha);
            gradient(c, m0, m1, o1, o0, color, alpha, alpha, 0, 0);
        }
    }

    /** Pixel motes spiralling into the core from up to radius * 2.2 (blocks and sparks being swallowed). */
    private static void motes(Ctx c, Vec3 at, double radius, double age, int color, int count) {
        for (int i = 0; i < count; i++) {
            double phase = (i * .618034 + age * (.018 + .006 * (i % 4))) % 1;
            double r = radius * (2.2 - 1.9 * phase), a = i * 2.39996 + phase * 7;
            Vec3 n = new Vec3(Math.sin(i * 1.3), Math.cos(i * 2.1), Math.sin(i * .7 + 1)).normalize();
            Vec3 u = n.cross(new Vec3(0, 1, 0));
            u = u.lengthSqr() < 1e-8 ? new Vec3(1, 0, 0) : u.normalize();
            Vec3 w = n.cross(u);
            Vec3 p = at.add(u.scale(Math.cos(a) * r)).add(w.scale(Math.sin(a) * r));
            sprite(c, p, .05 + .06 * (1 - phase), .35 + .6 * phase, i % 5 == 0 ? 0xFFFFFF : color, GateAtlas.SOLID, a);
        }
    }

    /** Small blue arcs crackling off the ready Blue, redrawn every three ticks. */
    private static void lightning(Ctx c, Vec3 at, double radius, double age) {
        Random rand = new Random((long) Math.floor(age / 3) * 7919L);
        for (int bolt = 0; bolt < 4; bolt++) {
            Vec3 dir = new Vec3(rand.nextGaussian(), rand.nextGaussian(), rand.nextGaussian()).normalize();
            Vec3 p = at.add(dir.scale(radius * .95));
            double reach = radius * (.5 + .6 * rand.nextDouble());
            for (int s = 0; s < 4; s++) {
                Vec3 q = p.add(dir.scale(reach / 4)).add(rand.nextGaussian() * .25, rand.nextGaussian() * .25, rand.nextGaussian() * .25);
                shaft(c, p, q, .06, .04, BLUE_RIM, .9, .6, age);
                p = q;
            }
        }
    }
}
