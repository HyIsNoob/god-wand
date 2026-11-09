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
            return buildShot(frame, frame.age(), ground);
        }
        double c = frame.commandAge();
        int end = SilentGateSequence.endTick(frame.barrageTicks());
        if (c >= end + SilentGateSequence.RETURN_TICKS) return null;
        if (c < 0) return buildShot(frame, frame.age(), ground);
        var shot = barrageShot(frame, Math.min(c, end), shots, ground, focus);
        // Slide from the last framing into the player's own eyes, then the camera is released.
        return c <= end ? shot : blend(shot, new AtomicCinematic.Pose(player.add(0, 1.62, 0), yaw, pitch), smooth(c, end, end + SilentGateSequence.RETURN_TICKS));
    }

    // ---- shot tables ---------------------------------------------------------------------------------------------
    /** How a sequence moves: KEYS is one continuous Hermite move through keyframes (velocity continuous, "rest" keys stop dead; the easing is in the keys);
     *  the others are single curves of progress u (0..1) over time k (0..1). No default symmetric ease-in-out anywhere. */
    enum Curve {
        /** One continuous camera move through keyframes. */
        KEYS,
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
                case KEYS, STATIC -> 0;
                case LINEAR -> k;
                case EASE_OUT_LONG -> 1 - (1 - k) * (1 - k) * (1 - k);
                case EASE_IN -> k * k;
                case SPRING -> 1 - Math.exp(-5 * k) * Math.cos(2 * Math.PI * 1.25 * k);
            };
        }
    }
    /** Cast geometry shared by every shot: caster, forward/right, the landed gate, the live ground, the barrage's recent hits and tracked target. */
    record Rig(Vec3 a, Vec3 f, Vec3 r, float yaw, long seed, int sky, ToDoubleBiFunction<Double, Double> ground, List<Shot> shots, Vec3 focus) {
        Rig(Frame fr, ToDoubleBiFunction<Double, Double> ground, List<Shot> shots, Vec3 focus) {
            this(fr.anchor(), Vec3.directionFromRotation(0, fr.yaw()), Vec3.directionFromRotation(0, fr.yaw()).cross(UP), fr.yaw(), fr.seed(), fr.skyCircles(), ground, shots, focus);
        }
        Vec3 foot() { return SilentGateSequence.gateFoot(a, yaw, 0); }
        Vec3 slit() { return SilentGateSequence.gateFoot(a, yaw, 1); }
        Vec3 door() { return SilentGateSequence.doorCenter(a, yaw); }
        /** Point at {@code fwd} blocks forward, {@code right} to the right and {@code up} above the caster's feet. */
        Vec3 rel(double fwd, double right, double up) { return a.add(f.scale(fwd)).add(r.scale(right)).add(0, up, 0); }
        /** {@code p} lifted to at least {@code clearance} above the live surface under it. */
        Vec3 above(Vec3 p, double clearance) {
            double floor = ground.applyAsDouble(p.x, p.z) + clearance;
            return p.y < floor ? new Vec3(p.x, floor, p.z) : p;
        }
    }
    /** Pose at progress {@code u}; {@code t} is the clock of the table (cast age for the build, authored barrage ticks, ticks after the barrage for the ending). */
    @FunctionalInterface interface Path { AtomicCinematic.Pose at(Rig g, double u, double t); }
    /** One continuous sequence (no cut inside): ticks [t0, t1), a curve and a path. */
    record Cut(String name, int t0, int t1, Curve curve, Path path) {
        AtomicCinematic.Pose at(Rig g, double t) { return path.at(g, curve.u((t - t0) / (t1 - t0)), t); }
    }

    // -- keyframes: position and gaze target at a tick; velocity at a key is the central difference of its neighbours (continuous), zero at a rest key
    record Key(double t, Vec3 pos, Vec3 look, boolean rest) {}
    private static Key key(double t, Vec3 pos, Vec3 look) { return new Key(t, pos, look, false); }
    private static Key rest(double t, Vec3 pos, Vec3 look) { return new Key(t, pos, look, true); }
    private static Vec3 vel(List<Key> k, int i, boolean target) {
        if (i <= 0 || i >= k.size() - 1 || k.get(i).rest()) return Vec3.ZERO;
        Vec3 a = target ? k.get(i - 1).look() : k.get(i - 1).pos(), b = target ? k.get(i + 1).look() : k.get(i + 1).pos();
        return b.subtract(a).scale(1.0 / (k.get(i + 1).t() - k.get(i - 1).t()));
    }
    private static Vec3 herm(Vec3 p0, Vec3 p1, Vec3 m0, Vec3 m1, double h, double s) {
        double s2 = s * s, s3 = s2 * s;
        return p0.scale(2 * s3 - 3 * s2 + 1).add(m0.scale(h * (s3 - 2 * s2 + s))).add(p1.scale(-2 * s3 + 3 * s2)).add(m1.scale(h * (s3 - s2)));
    }
    private static int segment(List<Key> k, double t) { int i = 0; while (i < k.size() - 2 && t >= k.get(i + 1).t()) i++; return i; }
    static Vec3 splinePos(List<Key> k, double t) {
        int i = segment(k, t); double h = k.get(i + 1).t() - k.get(i).t();
        return herm(k.get(i).pos(), k.get(i + 1).pos(), vel(k, i, false), vel(k, i + 1, false), h, Mth.clamp((t - k.get(i).t()) / h, 0, 1));
    }
    static Vec3 splineLook(List<Key> k, double t) {
        int i = segment(k, t); double h = k.get(i + 1).t() - k.get(i).t();
        return herm(k.get(i).look(), k.get(i + 1).look(), vel(k, i, true), vel(k, i + 1, true), h, Mth.clamp((t - k.get(i).t()) / h, 0, 1));
    }
    private static AtomicCinematic.Pose spline(List<Key> k, double t) { return look(splinePos(k, t), splineLook(k, t)); }

    /** Camera tremble from the stuck doors, and the jolt when they meet the stops. */
    static Vec3 rattle(double t) {
        double shake = SilentGateSequence.gateShake(t), d = t - SilentGateSequence.OPEN_END;
        double jolt = d >= 0 ? .8 * Math.exp(-d / 4) * Math.sin(1.4 * d) : 0;
        return new Vec3(shake * .35 * Math.sin(t * 1.7 + 1), shake * .5 * Math.sin(t * 2.3) + jolt, shake * .25 * Math.sin(t * 1.3 + 2));
    }
    /** The gate's touchdown at 248 shakes the camera once. */
    static double landing(double t) {
        double d = t - SilentGateSequence.DESCENT_END;
        return d >= 0 ? .6 * Math.exp(-d / 5) * Math.sin(1.2 * d) : 0;
    }
    private static double alt(Rig g) { return SilentGateSequence.circleAltitude(g.a().y); }
    private static AtomicCinematic.Pose lift(Rig g, AtomicCinematic.Pose p, double clearance) {
        Vec3 q = g.above(p.position(), clearance);
        return q == p.position() ? p : new AtomicCinematic.Pose(q, p.yaw(), p.pitch());
    }

    private static List<Key> faceKeys(Rig g) {
        return List.of(
            // first person, wand floating: barely moving; then out round the shoulder to a front three-quarter view (the wand spins), drifting sideways; then a push in on the face that stops dead
            rest(0, g.rel(.1, 0, 1.45), g.rel(8, 0, 1.35)),
            key(20, g.rel(.9, 1.7, 1.5), g.rel(3, 0, 1.4)),
            key(40, g.rel(3, 2.8, .9), g.rel(-.2, 0, 1.35)),
            key(80, g.rel(3, .5, 1.1), g.rel(-.2, 0, 1.35)),
            rest(120, g.rel(1.1, .35, 1.6), g.rel(0, 0, 1.62)));
    }
    private static Vec3 orbit(Rig g, double th) { return g.a().add(g.f().scale(-Math.cos(th) * 14)).add(g.r().scale(Math.sin(th) * 14)).add(0, 1.8, 0); }
    private static List<Key> skyKeys(Rig g) {
        Vec3 up = g.rel(100, 0, alt(g) * .7), glow = g.foot().add(0, 96, 0), wideAim = g.foot().add(0, 60, 0);
        Vec3 near = g.foot().add(g.f().scale(-55)).add(g.r().scale(9)).add(0, 25, 0), wide = g.rel(-50, 30, 4);
        return List.of(
            // under the great circle (turning slowly about the caster while it is written 120..168), then a slow dolly toward the gate (the slit opens, the gate comes out)
            rest(120, orbit(g, -.25), up), key(145, orbit(g, 0), up), key(170, orbit(g, .25), up),
            key(210, g.rel(62, 6, 16), g.slit().add(0, 40, 0)),
            // arriving at rest beside the spike's path as the gate lands (248), a beat of stillness, then a pull-back to the extreme wide that starts from rest
            rest(246, near, glow), rest(250, near, glow),
            key(266, near.lerp(wide, .45), glow.lerp(wideAim, .45)),
            rest(290, wide, wideAim));
    }
    private static List<Key> endKeys(Rig g) {
        Vec3 door = g.door();
        return List.of(
            // medium on the doors from below as they close, drifting back and sideways while the circles retract, down low as the gate climbs, then still while the great circle fades
            rest(0, door.add(g.f().scale(-66)).add(g.r().scale(14)).add(0, -34, 0), door),
            key(40, door.add(g.f().scale(-100)).add(g.r().scale(10)).add(0, -75, 0), door),
            key(80, g.rel(40, 30, 35), door),
            key(125, g.rel(70, 30, 4), door),
            rest(160, g.rel(40, 28, 8), door));
    }
    private static List<Key> riseKeys(Rig g) {
        return List.of(
            // from the waiting frame, round the caster to behind and low, then forward and up under the formation, always looking up at the sky
            rest(0, g.rel(6.2, 3.2, 1.1), g.rel(-4, 0, 9)),
            key(30, g.rel(-6, 10, 2.5), g.rel(110, 0, 120)),
            key(56, g.rel(20, 8, 20), g.rel(110, 0, 150)),
            key(84, g.rel(55, 6, 65), g.rel(120, 0, 170)));
    }

    /** The four sequences of the build (BUILD_TICKS = 480). Between them are the only three hard cuts (see the notes): 120 face to sky, 290 wide to door, 380 door to overview. */
    static final List<Cut> CUTS = List.of(
        new Cut("SEQ1 face", 0, 120, Curve.KEYS, (g, u, t) -> spline(faceKeys(g), t)),
        new Cut("SEQ2 sky-gate", 120, 290, Curve.KEYS, (g, u, t) -> {
            var p = spline(skyKeys(g), t);
            return new AtomicCinematic.Pose(p.position().add(0, landing(t), 0), p.yaw(), p.pitch());
        }),
        // The doors push open (stuck 292, parting 316, heavy stop 392): a slow, steady turn round them (0.5 deg/tick) ending dead frontal; tremble on top.
        new Cut("SEQ3 door", 290, 380, Curve.LINEAR, (g, u, t) -> {
            double th = -.8 + .8 * u;
            Vec3 door = g.door();
            return look(door.add(g.f().scale(-Math.cos(th) * 46)).add(g.r().scale(Math.sin(th) * 46)).add(0, -10, 0).add(rattle(t)), door.add(0, -4, 0));
        }),
        // Overview from the ground under the gate looking up and back over the spread (circles bloom 384..440), then ONE low flight toward the caster, looking up at the
        // circles that bloom overhead, and a long ease-out onto the waiting frame (the lane circles hang 35..75 above the camera: seen from below, never edge-on).
        new Cut("SEQ4 bloom", 380, 480, Curve.KEYS, (g, u, t) -> {
            double w = SilentGateSequence.flightU(t);
            Vec3 p = g.above(SilentGateSequence.flight(g.a(), g.yaw(), w), 1).add(rattle(t));
            Vec3 v0 = g.f().scale(-60).add(0, 55, 0), v1 = g.f().scale(-10.2).add(g.r().scale(-3.2)).add(0, 7.9, 0);
            return look(p, p.add(v0.lerp(v1, smooth(w, 0, 1))));
        }));

    /** Barrage sequences in authored ticks after the command (a 200 tick barrage starts at 12; scaled with the barrage length): rise, kill with pull-away, low look-up. */
    static final List<Cut> BARRAGE = List.of(
        new Cut("BAR1 rise", 0, 84, Curve.KEYS, (g, u, t) -> lift(g, spline(riseKeys(g), t), 2)),
        new Cut("BAR2 kill", 84, 170, Curve.KEYS, (g, u, t) -> killPull(g, t)),
        // Low behind the caster, rising slowly, looking up at the sky circles and the beams criss-crossing.
        new Cut("BAR3 low", 170, 212, Curve.LINEAR, (g, u, t) -> lift(g, look(g.rel(-70 + 10 * u, 25, 6 + 22 * u), g.rel(110, 0, 120)), 2)));

    /** The ending (ticks after the barrage): one continuous move. */
    static final List<Cut> ENDING = List.of(
        new Cut("END", 0, 160, Curve.KEYS, (g, u, e) -> {
            List<Key> k = endKeys(g);
            Vec3 gate = SilentGateSequence.gateFoot(g.a(), g.yaw(), SilentGateSequence.gateRise(e)).add(0, 100, 0), circle = g.rel(144, 0, alt(g) * .9);
            return lift(g, look(splinePos(k, e), gate.lerp(circle, smooth(e, 125, 160))), 2);
        }));

    /** The waiting frame: medium, in front of the caster pointing at the sky, the circle behind them, breathing very slowly. */
    private static AtomicCinematic.Pose waitShot(Rig g, double t) {
        double w = smooth(t, 0, 20);
        Vec3 breath = g.r().scale(Math.sin(t * .045) * .15 * w).add(0, Math.sin(t * .07) * .2 * w, 0);
        return look(g.rel(6.2, 3.2, 1.1).add(breath), g.rel(-4, 0, 9));
    }

    static AtomicCinematic.Pose buildShot(Frame frame, double age) { return buildShot(frame, age, NO_GROUND); }
    static AtomicCinematic.Pose buildShot(Frame frame, double age, ToDoubleBiFunction<Double, Double> ground) {
        Rig g = new Rig(frame, ground, List.of(), null);
        if (age >= SilentGateSequence.BUILD_TICKS) return waitShot(g, age - SilentGateSequence.BUILD_TICKS);
        for (Cut c : CUTS) if (age < c.t1()) return c.at(g, Math.max(age, c.t0()));
        return waitShot(g, 0);
    }

    /** Boundaries in the barrage (authored for 200 ticks, scaled): the kill starts (also when the target is locked), the pull-away starts, the cut to the low shot, the barrage ends. */
    static int[] boundaries(int barrageTicks) {
        return new int[]{SilentGateSequence.shotTick(84, barrageTicks), SilentGateSequence.shotTick(134, barrageTicks),
                SilentGateSequence.shotTick(170, barrageTicks), SilentGateSequence.ACTIVATE_TICKS + barrageTicks};
    }

    private static AtomicCinematic.Pose barrageShot(Frame frame, double c, List<Shot> shots, ToDoubleBiFunction<Double, Double> ground, Vec3 focus) {
        int[] b = boundaries(frame.barrageTicks());
        Rig g = new Rig(frame, ground, shots, focus);
        if (c >= b[3]) {
            double e = c - b[3];
            var cut = ENDING.get(0);
            return cut.at(g, Math.min(e, cut.t1()));
        }
        // Authored clock: the first 12 ticks (the command) run as they are, the rest is stretched with the barrage.
        double tb = c < SilentGateSequence.ACTIVATE_TICKS ? c : SilentGateSequence.ACTIVATE_TICKS + (c - SilentGateSequence.ACTIVATE_TICKS) * 200.0 / frame.barrageTicks();
        for (Cut cut : BARRAGE) if (tb < cut.t1()) return cut.at(g, tb);
        return BARRAGE.get(BARRAGE.size() - 1).at(g, BARRAGE.get(BARRAGE.size() - 1).t1());
    }

    /** Kill close-up: {@value #KILL_DISTANCE} blocks from the target on the caster's side, just above its feet, looking up past it so the beams fall into the frame;
     *  a steady 0.4 deg/tick orbit, and from authored tick 134 one continuous pull-away (radius and height grow from zero speed) that keeps the target in frame. */
    static final double KILL_DISTANCE = 5.5, KILL_CAM_HEIGHT = .6, KILL_LOOK_HEIGHT = 2.6;
    private static AtomicCinematic.Pose killPull(Rig g, double t) {
        Vec3 at = g.focus() != null ? g.focus() : densest(g.shots(), g.a().add(g.f().scale(40)));
        Vec3 toward = new Vec3(g.a().x - at.x, 0, g.a().z - at.z);
        toward = toward.lengthSqr() < 1 ? g.f().scale(-1) : toward.normalize();
        double q = Mth.clamp((t - 134) / 36, 0, 1);
        q *= q;
        Vec3 offset = toward.yRot((float) Math.toRadians(-10 + .4 * (t - 84))).scale(KILL_DISTANCE + 25 * q);
        Vec3 cam = new Vec3(at.x + offset.x, at.y + KILL_CAM_HEIGHT + 9 * q, at.z + offset.z);
        // Stay above the live surface: the hit's crater may already be dug out under the camera.
        double floor = g.ground().applyAsDouble(cam.x, cam.z) + .5;
        return look(cam.y < floor ? new Vec3(cam.x, floor, cam.z) : cam, at.add(0, KILL_LOOK_HEIGHT + 4 * q, 0));
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
        int lockAt = boundaries(frame.barrageTicks())[0];
        var anchor = frame.anchor();
        List<Candidate> list = new java.util.ArrayList<>();
        for (var e : client.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(anchor, anchor).inflate(160), x -> x != client.player && x.isAlive()))
            list.add(new Candidate(e.getId(), e.getMaxHealth(), e.position()));
        Vec3 at = LOCK.update(frame.startedAt(), frame.commandAge(), lockAt, list, anchor, frame.featured());
        if (hitCast != frame.startedAt()) { hitCast = frame.startedAt(); hitAt = -1; }
        if (hitAt < 0 && at != null) for (Shot s : shots) if (s.heavy() && s.hit().distanceToSqr(at) < 36) { hitAt = s.tick() + SilentGateSequence.HEAVY_GLIDE; break; }
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
