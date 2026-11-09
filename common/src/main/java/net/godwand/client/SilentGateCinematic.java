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
    record Rig(Vec3 a, Vec3 f, Vec3 r, float yaw, long seed, int sky, int barrageTicks, ToDoubleBiFunction<Double, Double> ground, List<Shot> shots, Vec3 focus) {
        Rig(Frame fr, ToDoubleBiFunction<Double, Double> ground, List<Shot> shots, Vec3 focus) {
            this(fr.anchor(), Vec3.directionFromRotation(0, fr.yaw()), Vec3.directionFromRotation(0, fr.yaw()).cross(UP), fr.yaw(), fr.seed(), fr.skyCircles(), fr.barrageTicks(), ground, shots, focus);
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

    /** Camera angle tremble of the stuck doors and the jolt when they meet the stops, in degrees: {yaw, pitch}. The camera never moves for it, only turns a little. */
    static double[] rattle(double t) {
        double shake = SilentGateSequence.gateShake(t), d = t - SilentGateSequence.OPEN_END;
        double jolt = d >= 0 ? 2.4 * Math.exp(-d / 4) * Math.sin(1.4 * d) : 0;
        return new double[]{shake * 3 * Math.sin(t * 1.7 + 1), shake * 4 * Math.sin(t * 2.3) + jolt};
    }
    /** The gate's touchdown at 300 shakes the view once: a spring that rings out, in degrees {yaw, pitch}. */
    static double[] landing(double t) {
        double d = t - SilentGateSequence.DESCENT_END, k = d >= 0 ? Math.exp(-d / 6) : 0;
        return new double[]{.9 * k * Math.sin(1.1 * d + 1), 2.6 * k * Math.sin(1.3 * d)};
    }
    private static AtomicCinematic.Pose turn(AtomicCinematic.Pose p, double[] shake) {
        return new AtomicCinematic.Pose(p.position(), (float) (p.yaw() + shake[0]), (float) (p.pitch() + shake[1]));
    }
    private static double alt(Rig g) { return SilentGateSequence.circleAltitude(g.a().y); }
    /** Height of the high wide shots above the caster: between the top of the canopy (215) and the great circle. */
    static double high(Rig g) { return Math.min(235, alt(g) - 14); }
    private static AtomicCinematic.Pose lift(Rig g, AtomicCinematic.Pose p, double clearance) {
        Vec3 q = g.above(p.position(), clearance);
        return q == p.position() ? p : new AtomicCinematic.Pose(q, p.yaw(), p.pitch());
    }
    private static AtomicCinematic.Pose aim(Rig g, Vec3 pos, double pitchUp) {
        return new AtomicCinematic.Pose(pos, g.yaw(), (float) -pitchUp);
    }

    /** First person at the eye (shots 1..4): the wand floats before the face, the view only breathes. */
    private static AtomicCinematic.Pose firstPerson(Rig g, double t) {
        return look(g.rel(.2, 0, 1.6 + .01 * Math.sin(t * .07)), g.rel(8, 0, 1.5));
    }
    /** Shot 8: low at the caster's feet, looking up at the great circle forming overhead, the view turning 0.25 deg/tick about the vertical. */
    private static AtomicCinematic.Pose underCircle(Rig g, double t) {
        return new AtomicCinematic.Pose(g.rel(-5, .3, .25), (float) (g.yaw() - .25 * (t - 160)), -50f);
    }
    /** Shot 9: 20 blocks under the plane of the great circle, at the radius where its outer tier ends up (150 blocks from the centre), looking up and in at 38 degrees: the outer tier grows
     *  toward the camera across the whole frame, turning, and comes to rest over it. Drifts toward the centre at 0.08 blocks/tick. */
    private static AtomicCinematic.Pose underRim(Rig g, double t) {
        Vec3 centre = g.rel(144, 0, 0);
        Vec3 pos = new Vec3(centre.x - g.f().x * 150, g.a().y + alt(g) - 10, centre.z - g.f().z * 150).add(g.f().scale(.08 * (t - 200)));
        return aim(g, pos, 38);
    }
    /** Shot 10: level with the light slit, 30 blocks off, a little under the circle's plane; slides up along the slit (0.1 blocks/tick). */
    private static AtomicCinematic.Pose slitShot(Rig g, double u) {
        Vec3 mid = g.slit().add(0, 126, 0);
        Vec3 pos = mid.add(g.f().scale(-30)).add(g.r().scale(-1.5 + 3 * u)).add(0, -20 + 3 * u, 0);
        return look(pos, mid.add(0, 10, 0));
    }
    /** Shot 11: still, 50 blocks in front of the gate beside the path of its spike, looking up as the gate slides down through the frame. */
    private static AtomicCinematic.Pose descentShot(Rig g) {
        Vec3 foot = g.foot();
        return look(foot.add(g.f().scale(-50)).add(g.r().scale(8)).add(0, 20, 0), foot.add(0, 70, 0));
    }
    /** Shot 12: medium wide from the ground, looking up at the whole gate; it lands at 300 and the view rings like a spring. */
    private static AtomicCinematic.Pose landingShot(Rig g, double t) {
        return turn(look(g.rel(-35, 45, 3), g.foot().add(0, 95, 0)), landing(t));
    }
    /** Shot 13: extreme wide, 110 blocks behind the caster and 40 up; the caster is tiny at the bottom of the frame, the gate fills the middle, the shockwave runs overhead. */
    private static AtomicCinematic.Pose shockShot(Rig g) { return aim(g, g.rel(-110, 0, 40), 9); }
    /** Shot 14: 25 blocks from the seam between the leaves at its middle, a 16 degree arc round it over the 50 ticks (0.14 blocks/tick); the stuck leaves tremble the view. */
    private static AtomicCinematic.Pose seamShot(Rig g, double u, double t) {
        double th = Math.toRadians(-8 + 16 * u);
        Vec3 door = g.door();
        return turn(look(door.add(g.f().scale(-Math.cos(th) * 25)).add(g.r().scale(Math.sin(th) * 25)).add(0, -2, 0), door.add(0, -2, 0)), rattle(t));
    }
    /** Shot 15: close on the light between the doors (16 blocks), backing off at 0.1 blocks/tick; the stop at 412 jolts the view. */
    private static AtomicCinematic.Pose lightShot(Rig g, double u, double t) {
        Vec3 door = g.door();
        return turn(look(door.add(g.f().scale(-(16 + 3.4 * u))).add(g.r().scale(2)).add(0, -3, 0), door.add(0, -3, 0)), rattle(t));
    }
    /** Shot 16: high and wide, 135 blocks behind the caster over the canopy that blooms like a field of lamps, looking down 24 degrees across it at the gate (the whole gate in frame); drifts forward at 0.1 blocks/tick. */
    private static AtomicCinematic.Pose canopyShot(Rig g, double u) {
        Vec3 pos = g.rel(-135 + 3 * u, -40, high(g));
        return look(pos, g.rel(160, 0, high(g) - 138));
    }
    /** Shot 17: the dive, low under the canopy toward the caster along {@link SilentGateSequence#flight}, looking up 38 degrees at the circles that bloom ahead and overhead. */
    private static AtomicCinematic.Pose diveShot(Rig g, double t) {
        Vec3 p = g.above(SilentGateSequence.flight(g.a(), g.yaw(), SilentGateSequence.flightU(t)), 1);
        return new AtomicCinematic.Pose(p, g.yaw() + 180, -38f);
    }

    /** Camera of B3 for a circle at {@code c}: 24 blocks under it, 11 off to the side, turning slowly round it (0.4 deg/tick, about 0.08 blocks/tick). */
    private static Vec3 circleCam(Vec3 c, double t) {
        double th = Math.toRadians(.4 * (t - 50) - 30);
        return new Vec3(c.x + Math.sin(th) * 11, c.y - 24, c.z - Math.cos(th) * 11);
    }
    /** The sky circle of the low tier in front of the caster that fires most often during the window of shot B3 and that the camera sees clear of the gate's silhouette
     *  (circles in front of the gate fade out); nearest to 55 ahead on ties; -1 without circles. */
    static int firingCircle(Rig g) {
        int from = SilentGateSequence.shotTick(50, g.barrageTicks()) - SilentGateSequence.ACTIVATE_TICKS, to = SilentGateSequence.shotTick(82, g.barrageTicks()) - SilentGateSequence.ACTIVATE_TICKS;
        int best = -1, bestN = -1; double bestD = Double.MAX_VALUE; boolean bestClear = false;
        Vec3 spot = g.rel(55, 0, 0), gate = SilentGateSequence.gateCenter(g.a(), g.yaw());
        for (int i = 0; i < g.sky(); i += 4) {
            Vec3 p = SilentGateSequence.skyCircle(g.a(), g.yaw(), g.seed(), i, g.sky());
            double ahead = p.subtract(g.a()).dot(g.f()), side = p.subtract(g.a()).dot(g.r());
            if (ahead < 25 || ahead > 100 || Math.abs(side) > 50) continue;
            boolean clear = true;
            for (double t : new double[]{50, 58, 66, 74, 82}) clear &= GateMinor.gateClear(circleCam(p, t), p, gate, 12) > .999;
            int n = 0;
            for (int a = from; a < to; a++) if (SilentGateSequence.fires(i, a, g.barrageTicks(), g.seed())) n++;
            double d = Math.hypot(p.x - spot.x, p.z - spot.z);
            // A circle the gate would hide only wins when no clear one exists.
            if (clear && !bestClear || clear == bestClear && (n > bestN || n == bestN && d < bestD)) { best = i; bestN = n; bestD = d; bestClear = clear; }
        }
        return best;
    }
    /** B3: 24 blocks under a circle that keeps firing, looking up at its face. */
    private static AtomicCinematic.Pose circleShot(Rig g, double t) {
        int i = firingCircle(g);
        Vec3 c = i >= 0 ? SilentGateSequence.skyCircle(g.a(), g.yaw(), g.seed(), i, g.sky()) : g.rel(55, 40, 70);
        return lift(g, look(circleCam(c, t), c.add(0, 2, 0)), 2);
    }

    /** The eighteen shots of the build (BUILD_TICKS = 480), script v3: hard cuts between all of them. Names are the table numbers. */
    static final List<Cut> CUTS = List.of(
            new Cut("S01 black", 0, 15, Curve.STATIC, (g, u, t) -> firstPerson(g, t)),
            new Cut("S02 eyes open", 15, 45, Curve.STATIC, (g, u, t) -> firstPerson(g, t)),
            new Cut("S03 eyes close", 45, 55, Curve.STATIC, (g, u, t) -> firstPerson(g, t)),
            new Cut("S04 eyes reopen", 55, 62, Curve.STATIC, (g, u, t) -> firstPerson(g, t)),
            new Cut("S05 far behind", 62, 92, Curve.LINEAR, (g, u, t) -> look(g.rel(-24, 9 - 2.5 * u, 6.5), g.rel(2, 0, 2.2))),
            new Cut("S06 three-quarter", 92, 125, Curve.LINEAR, (g, u, t) -> look(g.rel(4.6 - .9 * u, 3.4 - .7 * u, 1.9 - .1 * u), g.rel(0, 0, 1.45))),
            new Cut("S07 face", 125, 160, Curve.EASE_OUT_LONG, (g, u, t) -> look(g.rel(2.4 - .95 * u, .7 - .35 * u, 1.75 - .05 * u), g.rel(0, 0, 1.62))),
            new Cut("S08 under the circle", 160, 200, Curve.STATIC, (g, u, t) -> underCircle(g, t)),
            new Cut("S09 under the rim", 200, 232, Curve.STATIC, (g, u, t) -> underRim(g, t)),
            new Cut("S10 slit", 232, 262, Curve.LINEAR, (g, u, t) -> slitShot(g, u)),
            new Cut("S11 gate descends", 262, 292, Curve.STATIC, (g, u, t) -> descentShot(g)),
            new Cut("S12 touchdown", 292, 312, Curve.STATIC, (g, u, t) -> landingShot(g, t)),
            new Cut("S13 extreme wide", 312, 337, Curve.STATIC, (g, u, t) -> shockShot(g)),
            new Cut("S14 seam", 337, 387, Curve.LINEAR, (g, u, t) -> seamShot(g, u, t)),
            new Cut("S15 light", 387, 420, Curve.LINEAR, (g, u, t) -> lightShot(g, u, t)),
            new Cut("S16 canopy", 420, 452, Curve.LINEAR, (g, u, t) -> canopyShot(g, u)),
            new Cut("S17 dive", 452, 470, Curve.EASE_IN, (g, u, t) -> diveShot(g, t)),
            new Cut("S18 waiting frame", 470, 480, Curve.STATIC, (g, u, t) -> waitShot(g, t - 470)));

    /** Barrage shots in authored ticks after the command (a 200 tick barrage starts at 12; scaled with the barrage length), B1..B6. */
    static final List<Cut> BARRAGE = List.of(
            new Cut("B1 command hand", 0, 14, Curve.LINEAR, (g, u, t) -> look(g.rel(.3, 3.2 - .2 * u, 1.55), g.rel(.25, 0, 1.3))),
            new Cut("B2 beam rain", 14, 50, Curve.LINEAR, (g, u, t) -> lift(g, look(g.rel(-62 + 3 * u, 18, 2.5), g.rel(144, 0, 2.5 + 206 * Math.tan(Math.toRadians(28)))), 2)),
            new Cut("B3 firing circle", 50, 82, Curve.LINEAR, (g, u, t) -> circleShot(g, t)),
            new Cut("B4 kill", 82, 125, Curve.LINEAR, (g, u, t) -> killShot(g, t)),
            new Cut("B5 craters from high", 125, 162, Curve.LINEAR, (g, u, t) -> look(g.rel(-30, 0, high(g) - 3 * u), g.rel(105, 0, 0))),
            new Cut("B6 low behind", 162, 212, Curve.LINEAR, (g, u, t) -> lift(g, look(g.rel(-14, 7, 3 + 4 * u), g.rel(80, -5, 60)), 2)));

    /** The ending (ticks after the barrage), E1..E4. */
    static final List<Cut> ENDING = List.of(
            new Cut("E1 doors close", 0, 40, Curve.LINEAR, (g, u, e) -> look(g.door().add(g.f().scale(-55 - 4 * u)).add(g.r().scale(8)).add(0, -2, 0), g.door())),
            new Cut("E2 circles shrink", 40, 80, Curve.LINEAR, (g, u, e) -> look(g.rel(-95, 85, high(g) + 4 * u), g.rel(80, -20, high(g) - 125))),
            new Cut("E3 gate flies in", 80, 125, Curve.LINEAR, (g, u, e) -> lift(g, look(g.rel(30, 90, 3), g.rel(144, 0, alt(g) * .55)), 2)),
            new Cut("E4 circle dissolves", 125, 160, Curve.LINEAR, (g, u, e) -> look(g.rel(-95 + 3 * u, 55, 3), g.rel(144, 0, alt(g)))));

    /** The waiting frame: medium, in front of the caster pointing at the sky, the circle behind them, breathing very slowly. */
    private static AtomicCinematic.Pose waitShot(Rig g, double t) {
        double w = smooth(t, 0, 20);
        Vec3 breath = g.r().scale(Math.sin(t * .045) * .15 * w).add(0, Math.sin(t * .07) * .2 * w, 0);
        return look(g.rel(6.2, 3.2, 1.1).add(breath), g.rel(-3, 0, 4.2));
    }

    static AtomicCinematic.Pose buildShot(Frame frame, double age) { return buildShot(frame, age, NO_GROUND); }
    static AtomicCinematic.Pose buildShot(Frame frame, double age, ToDoubleBiFunction<Double, Double> ground) {
        Rig g = new Rig(frame, ground, List.of(), null);
        if (age >= SilentGateSequence.BUILD_TICKS) return waitShot(g, age - 470);
        for (Cut c : CUTS) if (age < c.t1()) return c.at(g, Math.max(age, c.t0()));
        return waitShot(g, 10);
    }

    /** Boundaries in the barrage (authored for 200 ticks, scaled): the kill shot starts (the target is locked), the high shot starts, the low shot starts, the barrage ends. */
    static int[] boundaries(int barrageTicks) {
        return new int[]{SilentGateSequence.shotTick(82, barrageTicks), SilentGateSequence.shotTick(125, barrageTicks),
                SilentGateSequence.shotTick(162, barrageTicks), SilentGateSequence.ACTIVATE_TICKS + barrageTicks};
    }

    private static AtomicCinematic.Pose barrageShot(Frame frame, double c, List<Shot> shots, ToDoubleBiFunction<Double, Double> ground, Vec3 focus) {
        int[] b = boundaries(frame.barrageTicks());
        Rig g = new Rig(frame, ground, shots, focus);
        if (c >= b[3]) {
            double e = Math.min(c - b[3], SilentGateSequence.ENDING_TICKS);
            for (Cut cut : ENDING) if (e < cut.t1()) return cut.at(g, e);
            return ENDING.get(ENDING.size() - 1).at(g, SilentGateSequence.ENDING_TICKS);
        }
        // Authored clock: the first 12 ticks (the command) run as they are, the rest is stretched with the barrage.
        double tb = c < SilentGateSequence.ACTIVATE_TICKS ? c : SilentGateSequence.ACTIVATE_TICKS + (c - SilentGateSequence.ACTIVATE_TICKS) * 200.0 / frame.barrageTicks();
        for (Cut cut : BARRAGE) if (tb < cut.t1()) return cut.at(g, tb);
        return BARRAGE.get(BARRAGE.size() - 1).at(g, BARRAGE.get(BARRAGE.size() - 1).t1());
    }

    /** Kill close-up (B4): {@value #KILL_DISTANCE} blocks from the target on the caster's side, just above its feet, looking up past it so the beams fall into the frame; a steady 0.4 deg/tick orbit. */
    static final double KILL_DISTANCE = 5.5, KILL_CAM_HEIGHT = .6, KILL_LOOK_HEIGHT = 2.6;
    private static AtomicCinematic.Pose killShot(Rig g, double t) {
        Vec3 at = g.focus() != null ? g.focus() : densest(g.shots(), g.a().add(g.f().scale(40)));
        Vec3 toward = new Vec3(g.a().x - at.x, 0, g.a().z - at.z);
        toward = toward.lengthSqr() < 1 ? g.f().scale(-1) : toward.normalize();
        Vec3 offset = toward.yRot((float) Math.toRadians(-10 + .4 * (t - 82))).scale(KILL_DISTANCE);
        Vec3 cam = new Vec3(at.x + offset.x, at.y + KILL_CAM_HEIGHT, at.z + offset.z);
        // Stay above the live surface: the hit's crater may already be dug out under the camera.
        double floor = g.ground().applyAsDouble(cam.x, cam.z) + .5;
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
