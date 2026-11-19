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
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
        Vec3 prev, pos; double at, gap = 2, readyAt = -1, born, goneAt = -1; BlueState state = BlueState.CHARGING;
        /** The dust it has shed (world-space). */
        final List<BlueRedDraw.Puff> puffs = new ArrayList<>();
        /** The owner's own Blue, predicted on the client each frame (so it follows the mouse smoothly). */
        Vec3 own, ownVel = Vec3.ZERO; double ownAt = -1;
        /** Where it has been (oldest first) and when, for the smears it leaves behind. */
        final java.util.ArrayDeque<Vec3> trail = new java.util.ArrayDeque<>(); final java.util.ArrayDeque<Double> trailAt = new java.util.ArrayDeque<>();
    }
    record Burst(Vec3 pos, double at, boolean blue, Vec3 dir) {
        Burst(Vec3 pos, double at, boolean blue) { this(pos, at, blue, Vec3.ZERO); }
    }
    record Purple(Vec3 pos, double at) {}
    static final class Flight { final int owner; final Vec3 origin, dir; final double at; double power = 1; Vec3 end; double endAt = -1;
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
                if (v == null || v.goneAt >= 0) { v = new BlueView(); v.prev = at; v.born = clock; BLUES.put(p.ownerId(), v); sound(at, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.6f); }
                else v.prev = drawn(v, 1);
                v.trail.addLast(v.pos == null ? at : v.pos); v.trailAt.addLast(clock);
                while (v.trail.size() > 16) { v.trail.removeFirst(); v.trailAt.removeFirst(); }
                // The server sends every tick while Blue moves and every other tick when it is still: blend over the real gap, adding no lag.
                v.gap = Math.max(1, Math.min(2, clock - v.at)); v.pos = at; v.at = clock;
                BlueState state = BlueState.values()[p.state()];
                if (state != BlueState.CHARGING && v.readyAt < 0) {
                    v.readyAt = clock;
                    sound(at, SoundEvents.BEACON_POWER_SELECT, 2f, .7f);
                    sound(at, SoundEvents.WARDEN_SONIC_BOOM, 1f, 1.8f);
                    // Real particles thrown out with the wave, and a pressure front hugging the ground (owner, round 44).
                    for (int i = 0; i < 90; i++) {
                        Vec3 d = randomDir();
                        spray(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SOUL_FIRE_FLAME, at.add(d.scale(.8)), d.scale(.55 + .6 * RANDOM.nextDouble()));
                    }
                    ImpactDebrisRenderer.groundWave(groundUnder(at), 22, 0x3A8CFF);
                }
                v.state = state;
            }
            case BlueRedPayload.BLUE_GONE -> {
                // It goes by being sucked into itself (owner, 2026-11-19), so the view lingers a moment.
                BlueView v = BLUES.get(p.ownerId());
                if (v != null && v.goneAt < 0) {
                    v.goneAt = clock;
                    Vec3 c = v.own != null ? v.own : v.pos;
                    sound(c, SoundEvents.BEACON_DEACTIVATE, 1.4f, 1.5f);
                    for (int i = 0; i < 40; i++) { Vec3 d = randomDir(); spray(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SOUL_FIRE_FLAME, c.add(d.scale(4)), d.scale(-.28)); }
                }
            }
            case BlueRedPayload.BLUE_SHATTER -> { BURSTS.add(new Burst(at, clock, true)); sound(at, SoundEvents.GLASS_BREAK, 2f, .6f); }
            case BlueRedPayload.RED_CHARGE -> { CHARGES.put(p.ownerId(), clock); sound(at, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1f, 1.4f); }
            case BlueRedPayload.RED_CANCEL -> CHARGES.remove(p.ownerId());
            case BlueRedPayload.RED_FIRE -> {
                CHARGES.remove(p.ownerId());
                Flight fl = new Flight(p.ownerId(), at, new Vec3(p.dx(), p.dy(), p.dz()), clock);
                fl.power = p.state() > 0 ? p.state() / 100.0 : 1;
                FLIGHTS.add(fl);
                // The shooter feels the force leave: a short, hard kick and a brief widening of the view.
                var me = Minecraft.getInstance().player;
                if (me != null && me.getId() == p.ownerId()) {
                    // A hard kick on the shot (owner, 2026-11-19: it barely showed), harder the more it was charged.
                    double power = p.state() > 0 ? p.state() / 100.0 : 1;
                    GodWandCinematicEngine.addTrauma((float) (.55 + .35 * power), 14); GodWandCinematicEngine.setFovPunch((float) (1.06 + .06 * power), 3);
                    // Black and red impact frames on the shot (owner, 2026-11-19).
                    if (GodWandConfig.CLIENT.impactFrames) { IMPACTS.clear(); IMPACTS.add(new Burst(at, clock, false, new Vec3(p.dx(), p.dy(), p.dz()))); }
                }
                sound(at, SoundEvents.WARDEN_SONIC_BOOM, 1.5f, 1.5f);
            }
            case BlueRedPayload.RED_END -> {
                endFlight(p.ownerId(), at);
                BURSTS.add(new Burst(at, clock, false, new Vec3(p.dx(), p.dy(), p.dz())));
                impactFx(at, 30);
                impactNear(at);
                var me = Minecraft.getInstance().player;
                if (me != null) GodWandCinematicEngine.addTrauma((float) Math.max(0, .45 * (1 - me.position().distanceTo(at) / 40)), 10);
                sound(at, SoundEvents.GENERIC_EXPLODE.value(), 3f, .8f);
            }
            case BlueRedPayload.RED_HIT -> {
                BURSTS.add(new Burst(at, clock, false, new Vec3(p.dx(), p.dy(), p.dz())));
                impactFx(at, 18);
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
        // Blue's ready wave shakes the camera as its front sweeps past it (owner, 2026-11-19).
        var view = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (view != null) for (BlueView v : BLUES.values()) {
            if (v.readyAt < 0 || v.pos == null) continue;
            double d = v.pos.distanceTo(view.getPosition()), t = clock - v.readyAt;
            if (d > BlueRedDraw.readyWave(t - 1) && d <= BlueRedDraw.readyWave(t) || t == 0 && d <= BlueRedDraw.readyWave(0))
                GodWandCinematicEngine.addTrauma((float) Math.max(.15, .45 * (1 - d / 40)), 10);
        }
        for (Purple p : PURPLES) particles(p, clock - p.at());
        // A light tremble while you hold Red or Blue (owner, 2026-11-19), a little stronger as Red fills.
        var self = Minecraft.getInstance().player;
        if (self != null) {
            Double redStart = CHARGES.get(self.getId());
            if (redStart != null) GodWandCinematicEngine.holdTrauma((float) (.14 + .12 * Math.min(1, (clock - redStart) / GodWandConfig.SERVER.redChargeTicks)));
            BlueView mine = BLUES.get(self.getId());
            if (mine != null && mine.goneAt < 0 && mine.state != BlueState.ANCHORED) GodWandCinematicEngine.holdTrauma(.12f);
        }
        var lvl = Minecraft.getInstance().level;
        if (lvl != null) for (var charge : CHARGES.entrySet()) {
            var owner = lvl.getEntity(charge.getKey());
            if (owner != null && clock - charge.getValue() == GodWandConfig.SERVER.redChargeTicks) {
                // Full: a pulse, a sound, sparks (owner, 2026-11-19).
                Vec3 at = owner.getEyePosition().add(owner.getViewVector(1).scale(1.9));
                sound(at, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 1.2f, 1.6f);
            }
        }
        // Blue's dust: shed every tick off its skin; it stays where it was shed.
        BLUES.values().removeIf(v -> v.goneAt >= 0 && clock - v.goneAt > BlueRedDraw.VANISH_TICKS + 2);
        for (BlueView v : BLUES.values()) {
            v.puffs.removeIf(q -> clock - q.born() > BlueRedDraw.PUFF_LIFE);
            if (v.goneAt >= 0 || v.pos == null) continue;
            Vec3 c = v.own != null ? v.own : v.pos;
            double r = BlueRedVisual.blueRadius(v.state, clock - v.born);
            for (int i = 0; i < (v.state == BlueState.CHARGING ? 4 : 6); i++) {
                Vec3 d = randomDir();
                v.puffs.add(new BlueRedDraw.Puff(c.add(d.scale(r * .75)), d.scale(.03 + .03 * RANDOM.nextDouble()), clock, RANDOM.nextInt(100000)));
            }
        }
        for (Purple p : PURPLES) {
            double age = clock - p.at();
            if (age == BlueRedRules.PURPLE_DETONATE) detonated(p.pos());
            var me = Minecraft.getInstance().player;
            if (me != null && me.position().distanceTo(p.pos()) < 60) {
                // The contact hits (owner, 2026-11-18), and an orb sweeping past the camera shakes it a little.
                if (age == BlueRedRules.PURPLE_BLINK) {
                    GodWandCinematicEngine.addTrauma(.45f, 10);
                    // A pressure front hugging the ground, like Fuga's (owner, 2026-11-18).
                    ImpactDebrisRenderer.groundWave(groundUnder(p.pos()), 26, 0xF4F0FF);
                }
                if (age == BlueRedRules.PURPLE_DETONATE) { ImpactDebrisRenderer.groundWave(groundUnder(p.pos()), 96, 0xB050FF); GodWandCinematicEngine.addTrauma(.9f, 40); }
                // An aftershock a second later: a second, wider violet front over the land and a heavy jolt.
                if (age == BlueRedRules.PURPLE_DETONATE + 22) {
                    ImpactDebrisRenderer.groundWave(groundUnder(p.pos()), 130, 0x8A30E0); GodWandCinematicEngine.addTrauma(.45f, 20);
                    for (int i = 0; i < 120; i++) { Vec3 d = randomDir(); spray(i % 2 == 0 ? ParticleTypes.DRAGON_BREATH : ParticleTypes.REVERSE_PORTAL, p.pos().add(d.scale(20)), d.scale(1.5 + RANDOM.nextDouble())); }
                }
                // A low rumble while it holds its breath (owner, round 42).
                if (age >= BlueRedRules.PURPLE_DETONATE - 18 && age < BlueRedRules.PURPLE_DETONATE) GodWandCinematicEngine.addTrauma(.06f, 4);
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

    private static final Random RANDOM = new Random();
    /** Red's hit as real effects (owner, 2026-11-19: not only drawn sprites): a white pressure front over the ground, sparks, embers and smoke. */
    private static void impactFx(Vec3 at, int n) {
        Vec3 g = groundUnder(at);
        if (at.y - g.y < 6) ImpactDebrisRenderer.groundWave(g, 8 + n * .3, 0xFF2A3A);
        for (int i = 0; i < n; i++) {
            Vec3 d = randomDir();
            spray(ParticleTypes.SMOKE, at.add(d.scale(.4)), d.scale(.25 + .35 * RANDOM.nextDouble()));
        }
        spray(ParticleTypes.EXPLOSION, at, Vec3.ZERO);
    }
    private static Vec3 randomDir() { return new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize(); }
    private static void spray(ParticleOptions type, Vec3 p, Vec3 v) {
        var level = Minecraft.getInstance().level;
        if (level != null) level.addAlwaysVisibleParticle(type, true, p.x, p.y, p.z, v.x, v.y, v.z);
    }

    /** Real particles round Purple (owner, round 44: not only drawn sprites): the charged ground fizzing and the chunks floating off it before
     *  the burst, its skin shedding violet motes, the burst throwing violet out every way, then violet drifting over the ground behind the
     *  shock front. Also gathers the floating chunks for the lightning drawn between them. */
    private static void particles(Purple p, double age) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        int merge = BlueRedRules.PURPLE_MERGE, det = BlueRedRules.PURPLE_DETONATE;
        Vec3 sky = p.pos(), ground = groundUnder(sky);
        double s = BlueRedDraw.purpleSize(age);
        BlueRedDraw.CHARGED.clear();
        if (age > merge && age < det) {
            for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class, new net.minecraft.world.phys.AABB(ground, ground).inflate(34, 14, 34))) {
                Vec3 at = e.position();
                BlueRedDraw.CHARGED.add(at.add(.5, .5 + BlueRedRules.liftRise(e.tickCount, BlueRedRules.liftHash(at.x, at.y, at.z)), .5));
            }
            for (int i = 0; i < 6; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2, r = 5 + 25 * Math.sqrt(RANDOM.nextDouble());
                double x = ground.x + Math.cos(a) * r, z = ground.z + Math.sin(a) * r;
                double y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
                spray(ParticleTypes.REVERSE_PORTAL, new Vec3(x, y + .2, z), new Vec3(0, .15 + .25 * RANDOM.nextDouble(), 0));
            }
            for (int i = 0; i < 5; i++) { Vec3 d = randomDir(); spray(ParticleTypes.WITCH, sky.add(d.scale(s * 1.02)), d.scale(.08)); }
            for (int i = 0; i < 3 && !BlueRedDraw.CHARGED.isEmpty(); i++)
                spray(ParticleTypes.ELECTRIC_SPARK, BlueRedDraw.CHARGED.get(RANDOM.nextInt(BlueRedDraw.CHARGED.size())), randomDir().scale(.25));
            // Held breath: violet drawn into it from all round.
            if (age > det - 18) for (int i = 0; i < 14; i++) { Vec3 d = randomDir(); spray(ParticleTypes.PORTAL, sky, d.scale(s * 1.6)); }
        }
        if (age == det) for (int i = 0; i < 160; i++) {
            Vec3 d = randomDir();
            spray(i % 4 == 0 ? ParticleTypes.ELECTRIC_SPARK : i % 4 == 1 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.DRAGON_BREATH, sky.add(d.scale(s)), d.scale(1.2 + 1.6 * RANDOM.nextDouble()));
        }
        double t = age - det;
        if (t > 0 && t < 90) {
            double front = 200 * (1 - Math.pow(1 - Math.min(1, t / 60), 2)), fade = 1 - t / 90;
            for (int i = 0; i < 16 * fade; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2, r = front * (.6 + .4 * RANDOM.nextDouble());
                double x = ground.x + Math.cos(a) * r, z = ground.z + Math.sin(a) * r;
                double y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
                spray(i % 2 == 0 ? ParticleTypes.DRAGON_BREATH : ParticleTypes.REVERSE_PORTAL, new Vec3(x, y + .5, z), new Vec3(Math.cos(a) * .25, .12, Math.sin(a) * .25));
            }
        }
    }

    /** The latest Purple after it bursts, for I am Atomic's world pass (owner, round 42): {ground x, y, z, age}, or null. */
    public static double[] purpleWorld(float partial) {
        if (PURPLES.isEmpty()) return null;
        Purple p = PURPLES.get(PURPLES.size() - 1);
        double age = clock + partial - p.at();
        if (age < BlueRedRules.PURPLE_DETONATE) return null;
        Vec3 g = groundUnder(p.pos());
        return new double[]{g.x, g.y, g.z, age};
    }

    private static Vec3 groundUnder(Vec3 at) {
        var lvl = Minecraft.getInstance().level;
        return lvl == null ? at : new Vec3(at.x, lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(at.x), (int) Math.floor(at.z)), at.z);
    }

    private static Vec3 drawn(BlueView v, double partial) {
        if (v.own != null) return v.own;
        return BlueRedVisual.interp(v.prev, v.pos, (clock + partial - v.at) / v.gap);
    }

    /** The owner's own held Blue, run on the client every frame with the server's spring (BlueRedRules.follow) towards where the player is
     *  looking right now, so it tracks the mouse smoothly instead of stepping with the server's packets; it is drawn back towards the server's
     *  position only if the two drift apart. Everyone else's Blue, and an anchored one, use the server's positions. */
    private static void predict(BlueView v, int ownerId, float partial) {
        var me = Minecraft.getInstance().player;
        if (me == null || me.getId() != ownerId || v.state == BlueState.ANCHORED || v.goneAt >= 0 || v.pos == null) { v.own = null; return; }
        double now = clock + partial;
        Vec3 server = BlueRedVisual.interp(v.prev, v.pos, (now - v.at) / v.gap);
        if (v.own == null) { v.own = server; v.ownVel = Vec3.ZERO; v.ownAt = now; return; }
        double dt = Math.min(2, now - v.ownAt);
        if (dt <= 0) return;
        v.ownAt = now;
        Vec3 target = me.getEyePosition(partial).add(me.getViewVector(partial).scale(BlueRedRules.BLUE_DISTANCE));
        for (double t = 0; t < dt; t += .25) {
            double h = Math.min(.25, dt - t);
            v.ownVel = v.ownVel.scale(Math.pow(BlueRedRules.BLUE_DAMP, h)).add(target.subtract(v.own).scale(BlueRedRules.BLUE_PULL * h));
            v.own = v.own.add(v.ownVel.scale(h));
        }
        if (v.own.distanceTo(server) > 2.5) v.own = v.own.add(server.subtract(v.own).scale(.2));
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
                // Owner, 2026-11-19: the bending cut parts of Purple away; it is left out.
                warp = 0;
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
            if (clip.w > .001f) { sx = clip.x / clip.w * .5f + .5f; sy = clip.y / clip.w * .5f + .5f; } else { burst = 0; warp = 0; }
            // Inside or right at the sphere the lens has no rim to bend round: fade it out instead of tearing the frame.
            if (!PURPLES.isEmpty()) {
                double dist = light.distanceTo(camera.getPosition()), size = BlueRedDraw.purpleSize(now - PURPLES.get(PURPLES.size() - 1).at());
                warp *= smooth((dist - size * 1.2) / (size * .8));
            }
        }
        if (burst == 0 && light != null && sx == .5f && sy == .5f) warp = 0;
        // Black-and-white ink frames (the gate filter) on the contact and the blast: two frames, lit-on-black then inverted.
        float ink = 0;
        if (!PURPLES.isEmpty() && GodWandConfig.CLIENT.impactFrames) {
            int age = (int) Math.floor(now - PURPLES.get(PURPLES.size() - 1).at());
            if (age == BlueRedRules.PURPLE_BLINK) ink = 1; else if (age == BlueRedRules.PURPLE_BLINK + 1) ink = 2;
            // The 2D spheres' rims touch: ink frames, and Purple is born (owner, 2026-11-19).
            // Violet ink, as on the blast: the cut to Purple.
            if (age == BlueRedDraw.voidTouch()) ink = 3; else if (age == BlueRedDraw.voidTouch() + 1) ink = 4;
            // The blast: heavy violet ink (owner, 2026-11-19), four frames flipping.
            int t = age - BlueRedRules.PURPLE_DETONATE;
            if (t >= 0 && t < 4) ink = t % 2 == 0 ? 3 : 4;
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
                for (Burst b : IMPACTS) BlueRedDraw.impact(c, b.pos(), (int) (clock - b.at()), eye, lookVec, b.blue(), b.dir().lengthSqr() > 0);
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
        for (var e : BLUES.entrySet())
        {
            BlueView b = e.getValue();
            if (layer == 0) predict(b, e.getKey(), partial);
            BlueRedDraw.blue(c, drawn(b, partial), b.state != BlueState.CHARGING, now - b.born, b.readyAt < 0 ? -1 : now - b.readyAt, b.puffs, now,
                    b.goneAt < 0 ? -1 : now - b.goneAt);
            if (b.readyAt >= 0 && now - b.readyAt < 16) BlueRedDraw.readyWave(c, drawn(b, partial), now - b.readyAt);
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
            double age = now - charge.getValue(), full = GodWandConfig.SERVER.redChargeTicks;
            // The shooter's world darkens round the gathering Red so it stands out (owner, 2026-11-19); drawn before it, under it.
            var me = Minecraft.getInstance().player;
            if (me != null && me.getId() == charge.getKey()) {
                // The frame goes dark red round it (owner's refs): darkened under it, a red wash over it.
                double g = smooth(age / 15);
                if (layer == 0) sprite(c, eye.add(look.scale(.5)), 4, .38 * g, 0x1A0003, BlueRedAtlas.SOLID);
                else sprite(c, eye.add(look.scale(.5)), 4, (.06 + .05 * Math.min(1, age / full)) * g, 0xFF1A2A, BlueRedAtlas.SOLID);
            }
            // Its pressure is thrown back behind it and a little up and out to the side, never into the lens (owner, 2026-11-19).
            // Front to back (owner, 2026-11-19): straight back along the view, fanning out evenly all round, not swept across the screen.
            Vec3 back = view.scale(-1);
            BlueRedDraw.redCharge(c, at, BlueRedVisual.redChargeSize(Math.min(age, full), (int) full), age, Math.min(1, age / full), age >= full, back);
            BlueRedDraw.darkSwirl(c, owner.getPosition(partial).add(0, 1.1, 0), age, Math.min(1, age / full), view);
        }
        for (Flight f : FLIGHTS) {
            double age = now - f.at, after = f.endAt < 0 ? 0 : now - f.endAt;
            Vec3 head = BlueRedVisual.redHead(f.origin, f.dir, age, Math.min(160, GodWandConfig.SERVER.redRange), f.end);
            BlueRedDraw.redFlight(c, f.origin, f.dir, head, age, 1 - clamp01(after / 10), f.owner * 31L + (long) f.at, f.end == null, f.power);
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
