package net.godwand.client;

import net.godwand.client.SilentGateRenderer.Frame;
import net.godwand.client.SilentGateRenderer.Shot;
import net.godwand.config.GodWandConfig;
import net.godwand.network.SilentGateCancelPayload;
import net.godwand.platform.Services;
import net.godwand.skill.SilentGateSequence;
import net.godwand.skill.SilentGateSequence.Phase;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.ToDoubleBiFunction;

import static net.godwand.client.FugaCinematic.blend;
import static net.godwand.client.FugaCinematic.look;
import static net.godwand.client.FugaCinematic.shoulder;
import static net.godwand.client.FugaCinematic.smooth;

/** Directed camera, input locks, ESC/F5 handling and the eyelid/hint overlay of Silent Gate. */
public final class SilentGateCinematic {
    /** No ground clamp (pure tests / unknown terrain). */
    private static final ToDoubleBiFunction<Double, Double> NO_GROUND = (x, z) -> Double.NEGATIVE_INFINITY;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double S = SilentGateSequence.SCALE;
    private static final int WAIT = Phase.WAIT.ordinal();
    private static long cast = Long.MIN_VALUE, cancelSent = Long.MIN_VALUE;
    private static boolean skipped;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private SilentGateCinematic() {}

    private static boolean present(Minecraft client, Frame frame) {
        boolean valid = frame != null && client.player != null && client.level != null
                && client.player.isAlive() && !client.player.isSpectator();
        // Render hooks can see a new cast before the next client tick clears the previous skip.
        if (valid && (cast != frame.startedAt() || world != client.level)) { cast = frame.startedAt(); world = client.level; skipped = false; }
        return valid;
    }
    private static boolean directing() { return GodWandConfig.CLIENT.gateCutscene && !skipped; }
    private static boolean menuOpen(Minecraft client) { return client.screen != null || !client.isWindowActive(); }

    /** BUILD and WAIT always own the view; after the command only the directed cutscene does. */
    public static boolean active() {
        var client = Minecraft.getInstance();
        var frame = SilentGateRenderer.localFrame(0);
        return present(client, frame) && !menuOpen(client) && (frame.phase() <= WAIT || directing());
    }
    public static boolean movementLocked() {
        var frame = SilentGateRenderer.localFrame(0);
        return present(Minecraft.getInstance(), frame) && (frame.phase() <= WAIT || directing());
    }
    public static boolean rotationLocked() { return movementLocked() && directing(); }

    public static void tick(Minecraft client) {
        var frame = SilentGateRenderer.localFrame(0);
        if (!present(client, frame)) { reset(); return; }
        // ESC arrives through the keyboard mixin (cancel in WAIT); F5 only drops the director.
        if (!menuOpen(client) && GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_F5) == GLFW.GLFW_PRESS) skip();
    }
    public static void skip() { skipped = true; }
    public static void reset() { cast = Long.MIN_VALUE; cancelSent = Long.MIN_VALUE; world = null; skipped = false; hitCast = Long.MIN_VALUE; hitAt = -1; }

    /** ESC while the gate owns the screen: cancels the spell in WAIT, skips the cutscene otherwise. True when the key was used. */
    public static boolean escape() {
        if (!active()) return false;
        var frame = SilentGateRenderer.localFrame(0);
        if (frame.phase() != WAIT) { skip(); return true; }
        if (cancelSent != frame.startedAt()) {
            cancelSent = frame.startedAt();
            Services.NETWORK.sendToServer(new SilentGateCancelPayload(frame.startedAt()));
        }
        return true;
    }

    public static AtomicCinematic.Pose camera(float delta, Vec3 vanilla, float vanillaYaw, float vanillaPitch) {
        if (!active()) return null;
        var client = Minecraft.getInstance();
        var frame = SilentGateRenderer.localFrame(delta);
        var player = client.player;
        Vec3 feet = player.getPosition(delta);
        float yaw = player.getViewYRot(delta), pitch = player.getViewXRot(delta);
        var level = client.level;
        var shots = SilentGateRenderer.shots(frame.casterId());
        Vec3 focus = directing() && frame.commandAge() >= 0 ? focus(client, frame, shots) : null;
        var shot = cameraFor(frame, feet, yaw, pitch, directing(), shots,
                (x, z) -> level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)), focus);
        if (shot == null) return null;
        // Never move a detached shot into an unloaded chunk.
        if (!FugaCinematic.hasChunk(client, shot.position()))
            return frame.phase() <= WAIT ? shoulder(feet, yaw, pitch) : null;
        Vec3 eye = player.getEyePosition(delta);
        if (shot.position().distanceToSqr(eye) < 1) return shot;
        Vec3 safe = FugaCinematic.clipTowards(client, eye, shot.position());
        return safe == null ? shot : new AtomicCinematic.Pose(safe, shot.yaw(), shot.pitch());
    }

    public static AtomicCinematic.Pose cameraFor(Frame frame, Vec3 player, float yaw, boolean director) {
        return cameraFor(frame, player, yaw, 0, director, List.of());
    }

    /** Pure shot list. {@code yaw}/{@code pitch} are the player's own view; {@code shots} are recent hits for the close-ups. */
    public static AtomicCinematic.Pose cameraFor(Frame frame, Vec3 player, float yaw, float pitch, boolean director, List<Shot> shots) {
        return cameraFor(frame, player, yaw, pitch, director, shots, NO_GROUND);
    }

    /** As above; {@code ground} gives the live surface height at (x, z) so close-ups stay out of fresh craters. */
    public static AtomicCinematic.Pose cameraFor(Frame frame, Vec3 player, float yaw, float pitch, boolean director, List<Shot> shots,
            ToDoubleBiFunction<Double, Double> ground) {
        return cameraFor(frame, player, yaw, pitch, director, shots, ground, null);
    }

    /** As above; {@code focus} is the tracked kill target (null: the densest impact area is framed instead). */
    public static AtomicCinematic.Pose cameraFor(Frame frame, Vec3 player, float yaw, float pitch, boolean director, List<Shot> shots,
            ToDoubleBiFunction<Double, Double> ground, Vec3 focus) {
        if (frame == null) return null;
        if (!director) return frame.phase() <= WAIT ? shoulder(player, yaw, pitch) : null;
        if (frame.phase() <= WAIT) {
            return buildShot(frame, frame.age());
        }
        double c = frame.commandAge();
        int end = SilentGateSequence.endTick(frame.barrageTicks());
        if (c >= end + SilentGateSequence.RETURN_TICKS) return null;
        if (c < 0) return buildShot(frame, frame.age());
        var shot = barrageShot(frame, Math.min(c, end), shots, ground, focus);
        // Slide from the last framing into the player's own eyes, then the camera is released.
        return c <= end ? shot : blend(shot, new AtomicCinematic.Pose(player.add(0, 1.62, 0), yaw, pitch), smooth(c, end, end + SilentGateSequence.RETURN_TICKS));
    }

    // ---- BUILD shot table ----------------------------------------------------------------------------------------
    /** How a shot's progress u (0..1) follows its time k (0..1): one curve per shot, never a default symmetric ease-in-out. */
    enum Curve {
        /** Camera does not move; the subject does. */
        STATIC, LINEAR,
        /** Starts with momentum and coasts to a very slow stop (speed 3x the mean at the start). */
        EASE_OUT_LONG,
        /** Accelerates continuously (speed 2x the mean at the end). */
        EASE_IN,
        /** Quick recoil with an overshoot that rings out (damped spring). */
        SPRING;
        double u(double k) {
            k = Mth.clamp(k, 0, 1);
            return switch (this) {
                case STATIC -> 0;
                case LINEAR -> k;
                case EASE_OUT_LONG -> 1 - (1 - k) * (1 - k) * (1 - k);
                case EASE_IN -> k * k;
                case SPRING -> 1 - Math.exp(-5 * k) * Math.cos(2 * Math.PI * 1.25 * k);
            };
        }
    }
    /** Cast geometry shared by every shot: caster, forward/right, the landed gate foot, the door centre. */
    record Rig(Vec3 a, Vec3 f, Vec3 r, float yaw) {
        Rig(Vec3 anchor, float yaw) { this(anchor, Vec3.directionFromRotation(0, yaw), Vec3.directionFromRotation(0, yaw).cross(UP), yaw); }
        Vec3 foot() { return SilentGateSequence.gateFoot(a, yaw, 0); }
        Vec3 slit() { return SilentGateSequence.gateFoot(a, yaw, 1); }
        Vec3 door() { return SilentGateSequence.doorCenter(a, yaw); }
        /** Point at {@code fwd} blocks forward, {@code right} to the right and {@code up} above the caster's feet. */
        Vec3 rel(double fwd, double right, double up) { return a.add(f.scale(fwd)).add(r.scale(right)).add(0, up, 0); }
    }
    /** Pose at progress {@code u}; {@code t} is the cast age for effects that run on the clock (shake, look-up). */
    @FunctionalInterface interface Path { AtomicCinematic.Pose at(Rig g, double u, double t); }
    /** One shot: ticks [t0, t1) from click 1, a curve and a path. */
    record Cut(String name, int t0, int t1, Curve curve, Path path) {
        AtomicCinematic.Pose at(Rig g, double age) { return path.at(g, curve.u((age - t0) / (t1 - t0)), age); }
    }
    private static Path line(java.util.function.Function<Rig, Vec3> p0, java.util.function.Function<Rig, Vec3> p1,
            java.util.function.Function<Rig, Vec3> l0, java.util.function.Function<Rig, Vec3> l1) {
        return (g, u, t) -> look(p0.apply(g).lerp(p1.apply(g), u), l0.apply(g).lerp(l1.apply(g), u));
    }
    /** Camera tremble from the stuck doors, and the jolt when they meet the stops at tick 285. */
    static Vec3 rattle(double t) {
        double shake = SilentGateSequence.gateShake(t), d = t - SilentGateSequence.OPEN_END;
        double jolt = d >= 0 ? .8 * Math.exp(-d / 4) * Math.sin(1.4 * d) : 0;
        return new Vec3(shake * .35 * Math.sin(t * 1.7 + 1), shake * .5 * Math.sin(t * 2.3) + jolt, shake * .25 * Math.sin(t * 1.3 + 2));
    }
    /** The ten shots of the build (plan 2026-11-09-silent-gate-cinematography.md), authored for BUILD_TICKS = 360. */
    static final List<Cut> CUTS = List.of(
        // S1 eyes open, wand floating, mana drifting past: first person, barely moving.
        new Cut("S1", 0, 20, Curve.LINEAR, line(g -> g.rel(.1, 0, 1.45), g -> g.rel(.25, 0, 1.45), g -> g.rel(8, 0, 1.35), g -> g.rel(8, 0, 1.35))),
        // S2 the wand spins round the hand: a steady sideways drift in front of the caster.
        new Cut("S2", 20, 45, Curve.LINEAR, line(g -> g.rel(3, 2.8, .9), g -> g.rel(3, .5, 1.1), g -> g.rel(-.2, 0, 1.35), g -> g.rel(-.2, 0, 1.35))),
        // S3 "shh": push in on the face, a long ease-out tail.
        new Cut("S3", 45, 80, Curve.EASE_OUT_LONG, line(g -> g.rel(2.4, .9, 1.45), g -> g.rel(1.1, .35, 1.6), g -> g.rel(0, 0, 1.62), g -> g.rel(0, 0, 1.62))),
        // S4 the great circle writes itself: low, looking up, turning steadily (0.57 deg/tick) about the vertical axis.
        new Cut("S4", 80, 130, Curve.LINEAR, (g, u, t) -> {
            double th = -.25 + .5 * u, alt = SilentGateSequence.circleAltitude(g.a().y);
            Vec3 pos = g.a().add(g.f().scale(-Math.cos(th) * 14)).add(g.r().scale(Math.sin(th) * 14)).add(0, 1.8, 0);
            return look(pos, g.rel(100, 0, alt * .7));
        }),
        // S5 the slit opens: close to its edge, drifting up along it.
        new Cut("S5", 130, 150, Curve.LINEAR, line(g -> g.slit().add(g.f().scale(-22)).add(g.r().scale(4)).add(0, 10, 0), g -> g.slit().add(g.f().scale(-22)).add(g.r().scale(4)).add(0, 70, 0),
                g -> g.slit().add(0, 16, 0), g -> g.slit().add(0, 76, 0))),
        // S6 the gate comes out and falls: the camera is still beside the path of the spike and the columns, looking up, and eases its gaze up at the end.
        new Cut("S6", 150, 190, Curve.STATIC, (g, u, t) -> look(g.foot().add(g.f().scale(-40)).add(g.r().scale(9)).add(0, 25, 0),
                g.foot().add(0, 60 + 36 * smooth(t, 175, 190), 0))),
        // S7 the stop, extreme wide: the caster is a speck; the camera recoils on the touchdown (spring), the shockwave runs through it.
        new Cut("S7", 190, 215, Curve.SPRING, line(g -> g.rel(-115, 30, 4), g -> g.rel(-119, 30, 1), g -> g.foot().add(0, 85, 0), g -> g.foot().add(0, 85, 0))),
        // S8 the doors push open: a slow, steady turn round them (0.6 deg/tick), ending dead frontal; stuck tremble and the closing jolt ride on top.
        new Cut("S8", 215, 290, Curve.LINEAR, (g, u, t) -> {
            double th = -.8 + .8 * u;
            Vec3 door = g.door(), rock = rattle(t);
            Vec3 pos = door.add(g.f().scale(-Math.cos(th) * 46)).add(g.r().scale(Math.sin(th) * 46)).add(0, -10, 0).add(rock);
            return look(pos, door.add(0, -4, 0));
        }),
        // S9 the doors' gap to the sky: the camera darts out of the doorway along the lane the small circles bloom along, accelerating to the cut.
        new Cut("S9", 290, 330, Curve.EASE_IN, (g, u, t) -> look(SilentGateSequence.flight(g.a(), g.yaw(), u),
                SilentGateSequence.flight(g.a(), g.yaw(), Math.min(1.08, u + .08)).add(0, 3, 0))),
        // S10 the scale: the same motion keeps going (matched speed) and dies away very slowly; extreme wide, high, gate, caster and circles.
        new Cut("S10", 330, 360, Curve.EASE_OUT_LONG, (g, u, t) -> {
            Vec3 p0 = SilentGateSequence.flight(g.a(), g.yaw(), 1), p1 = p0.add(g.f().scale(-90)).add(0, 6, 0);
            return look(p0.lerp(p1, u), g.rel(110, 0, 85));
        }));

    /** The waiting frame: medium, in front of the caster pointing at the sky, the circle behind them, breathing very slowly. */
    private static AtomicCinematic.Pose waitShot(Rig g, double t) {
        double w = smooth(t, 0, 20);
        Vec3 breath = g.r().scale(Math.sin(t * .045) * .15 * w).add(0, Math.sin(t * .07) * .2 * w, 0);
        return look(g.rel(6.2, 3.2, 1.1).add(breath), g.rel(0, 0, 2.9));
    }

    static AtomicCinematic.Pose buildShot(Frame frame, double age) {
        Rig g = new Rig(frame.anchor(), frame.yaw());
        if (age >= SilentGateSequence.BUILD_TICKS) return waitShot(g, age - SilentGateSequence.BUILD_TICKS);
        for (Cut c : CUTS) if (age < c.t1()) return c.at(g, Math.max(age, c.t0()));
        return waitShot(g, 0);
    }

    /** Shot boundaries inside the barrage (authored for 200 ticks, scaled like every barrage shot): wave, rain, kill, scale, wide finish. */
    static int[] boundaries(int barrageTicks) {
        return new int[]{SilentGateSequence.shotTick(12, barrageTicks), SilentGateSequence.shotTick(60, barrageTicks), SilentGateSequence.shotTick(125, barrageTicks),
                SilentGateSequence.shotTick(165, barrageTicks), SilentGateSequence.endTick(barrageTicks)};
    }

    private static AtomicCinematic.Pose barrageShot(Frame frame, double c, List<Shot> shots, ToDoubleBiFunction<Double, Double> ground, Vec3 focus) {
        int[] b = boundaries(frame.barrageTicks());
        int s12 = b[0], s60 = b[1], s125 = b[2], s165 = b[3], end = b[4];
        Vec3 a = frame.anchor(), f = Vec3.directionFromRotation(0, frame.yaw()), r = f.cross(UP);
        if (c < s12) return buildShot(frame, frame.age());
        if (c < s60) {
            // Low wide rain under the formation, a slow dolly forward.
            double t = smooth(c, s12, s60);
            return look(a.add(f.scale(-6 + 40 * t)).add(r.scale(10 - 8 * t)).add(0, 2.5, 0), a.add(f.scale(110 + 40 * t)).add(0, 120, 0));
        }
        if (c < s125) return killShot(a, f, shots, focus, smooth(c, s60, s125), ground);
        if (c < s165) {
            // High oblique view from behind the caster, sinking slowly: caster and arc small at the bottom, the crater field and beams
            // across the middle, the gate and its circle above the horizon.
            double t = smooth(c, s125, s165);
            return look(a.add(f.scale(-125 + 10 * t)).add(r.scale(25)).add(0, 140 - 40 * t, 0), a.add(f.scale(110)).add(0, 70, 0));
        }
        double t = smooth(c, s165, end);
        return look(a.add(f.scale(-14 - 6 * t)).add(r.scale(3)).add(0, 3.2 + 2 * t, 0), a.add(f.scale(100)).add(0, 130, 0));
    }

    /** Kill close-up: {@value #KILL_DISTANCE} blocks from the target on the caster's side, just above its feet, looking up past it so the beams fall into the frame; 20 degree orbit. */
    static final double KILL_DISTANCE = 5.5, KILL_CAM_HEIGHT = .6, KILL_LOOK_HEIGHT = 2.6;
    private static AtomicCinematic.Pose killShot(Vec3 caster, Vec3 forward, List<Shot> shots, Vec3 focus, double t, ToDoubleBiFunction<Double, Double> ground) {
        Vec3 at = focus != null ? focus : densest(shots, caster.add(forward.scale(40)));
        Vec3 toward = new Vec3(caster.x - at.x, 0, caster.z - at.z);
        toward = toward.lengthSqr() < 1 ? forward.scale(-1) : toward.normalize();
        Vec3 offset = toward.yRot((float) Math.toRadians(-10 + 20 * t)).scale(KILL_DISTANCE);
        Vec3 cam = new Vec3(at.x + offset.x, at.y + KILL_CAM_HEIGHT, at.z + offset.z);
        // Stay above the live surface: the hit's crater may already be dug out under the camera.
        double floor = ground.applyAsDouble(cam.x, cam.z) + .5;
        return look(cam.y < floor ? new Vec3(cam.x, floor, cam.z) : cam, at.add(0, KILL_LOOK_HEIGHT, 0));
    }

    /** The impact with the most other impacts within 12 blocks (the newest on ties); {@code none} without any. */
    static Vec3 densest(List<Shot> shots, Vec3 none) {
        Vec3 best = none; int bestCount = -1;
        for (Shot s : shots) {
            int n = 0;
            for (Shot o : shots) if (o.hit().distanceToSqr(s.hit()) < 144) n++;
            if (n >= bestCount) { bestCount = n; best = s.hit(); }
        }
        return best;
    }

    // ---- kill target lock and hit-stop ----------------------------------------------------------------------------
    /** A living entity the client can see. */
    record Candidate(int id, double maxHealth, Vec3 position) {}

    /** Picks the server's featured target (else the highest max-health candidate) once, when the kill shot starts, and then follows that entity (its last position once it is gone). */
    static final class KillLock {
        private long cast = Long.MIN_VALUE; private boolean locked; private int id; private Vec3 last;
        int id() { return id; }
        Vec3 update(long castId, double commandAge, int lockAt, List<Candidate> candidates, Vec3 caster) { return update(castId, commandAge, lockAt, candidates, caster, -1); }
        Vec3 update(long castId, double commandAge, int lockAt, List<Candidate> candidates, Vec3 caster, int featured) {
            if (cast != castId) { cast = castId; locked = false; last = null; }
            if (!locked && commandAge >= lockAt) {
                Candidate best = null;
                for (Candidate k : candidates) if (best == null || k.maxHealth() > best.maxHealth()
                        || k.maxHealth() == best.maxHealth() && k.position().distanceToSqr(caster) < best.position().distanceToSqr(caster)) best = k;
                for (Candidate k : candidates) if (k.id() == featured) best = k; // the server's featured target wins when it is still around
                if (best != null) { id = best.id(); locked = true; }
            }
            if (locked) for (Candidate k : candidates) if (k.id() == id) last = k.position();
            return locked ? last : null;
        }
    }
    private static final KillLock LOCK = new KillLock();
    private static long hitCast = Long.MIN_VALUE; private static double hitAt = -1;
    static final double HIT_STOP_TICKS = 3, HIT_STOP_SPEED = .15;

    private static Vec3 focus(Minecraft client, Frame frame, List<Shot> shots) {
        int lockAt = boundaries(frame.barrageTicks())[1];
        var anchor = frame.anchor();
        List<Candidate> list = new java.util.ArrayList<>();
        for (var e : client.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(anchor, anchor).inflate(160), x -> x != client.player && x.isAlive()))
            list.add(new Candidate(e.getId(), e.getMaxHealth(), e.position()));
        Vec3 at = LOCK.update(frame.startedAt(), frame.commandAge(), lockAt, list, anchor, frame.featured());
        if (hitCast != frame.startedAt()) { hitCast = frame.startedAt(); hitAt = -1; }
        if (hitAt < 0 && at != null) for (Shot s : shots) if (s.hit().distanceToSqr(at) < 36) { hitAt = s.tick(); break; }
        return at;
    }

    /** Render clock with a ~3 tick hit-stop: it crawls at 15% of real speed for 3 ticks from the hit, then stays that much behind. */
    static double warp(long castStartedAt, double now) { return castStartedAt == hitCast ? warp(now, hitAt) : now; }
    static double warp(double now, double hit) {
        if (hit < 0 || now <= hit) return now;
        double d = now - hit;
        return d < HIT_STOP_TICKS ? hit + d * HIT_STOP_SPEED : now - HIT_STOP_TICKS * (1 - HIT_STOP_SPEED);
    }

    /** Eyelids ({@link GateEyelids}) over the first 30 ticks and the command hint in WAIT. */
    public static void render(GuiGraphics graphics, DeltaTracker timer) {
        float partial = timer.getGameTimeDeltaPartialTick(false);
        var client = Minecraft.getInstance();
        var frame = SilentGateRenderer.localFrame(partial);
        if (menuOpen(client) || !present(client, frame)) return;
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        if (directing() && frame.phase() == 0 && frame.age() < GateEyelids.VIGNETTE_TICKS)
            GateEyelids.render(graphics, frame.age(), width, height, client.getWindow().getGuiScale());
        if (frame.phase() == WAIT) {
            String hint = Component.translatable("message.godwand.gate_command").getString();
            graphics.drawString(client.font, hint, (width - client.font.width(hint)) / 2, height - 62, 0xFFFFFFFF, true);
        }
    }
}
