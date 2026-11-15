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
        // The heavy beats shake the view once each (the engine's trauma), and the director picks the lens of every shot.
        double age = frame.age(), hit = sinceHit(frame);
        if (beatCast != frame.startedAt()) {
            beatCast = frame.startedAt(); lastAge = age; lastHit = hit; lastCmd = frame.commandAge(); sneakHeld = true;
            // The first click is felt.
            if (age < 6) GodWandCinematicEngine.addTrauma(.35f, 10);
        }
        // The view shakes with every heavy thing: a rumble while the gate falls, its stop, the wings and their wave reaching the caster, the stuck doors and their
        // stop, the command, a low rumble under the whole barrage, the kill.
        double cmd = frame.commandAge();
        // The view shudders in heavy beats while the gate comes down; the gate itself stays steady (owner, 2026-11-15: "camera shake, not gate shake").
        if (age >= SilentGateSequence.DESCENT_START + 4 && age < SilentGateSequence.DESCENT_END && (int) age % 2 == 0)
            GodWandCinematicEngine.addTrauma((float) (.07 + .24 * SilentGateSequence.descentStrain(age)), 6);
        if (lastAge < SilentGateSequence.DESCENT_END && age >= SilentGateSequence.DESCENT_END) GodWandCinematicEngine.addTrauma(.4f, 18);
        if (lastAge < SilentGateSequence.SHOCK && age >= SilentGateSequence.SHOCK) GodWandCinematicEngine.addTrauma(.6f, 18);
        if (lastAge < SilentGateSequence.SHOCK + 18 && age >= SilentGateSequence.SHOCK + 18) GodWandCinematicEngine.addTrauma(.2f, 12);
        if (lastAge < SilentGateSequence.CIRCLE_START - 9 && age >= SilentGateSequence.CIRCLE_START - 9) GodWandCinematicEngine.addTrauma(.22f, 10);
        if (cmd < 0 && age >= SilentGateSequence.OPEN_START && age < SilentGateSequence.STUCK_END && (int) age % 4 == 0) GodWandCinematicEngine.addTrauma(.07f, 5);
        if (lastAge < SilentGateSequence.OPEN_END && age >= SilentGateSequence.OPEN_END) GodWandCinematicEngine.addTrauma(.38f, 16);
        if (lastCmd < 0 && cmd >= 0) { GodWandCinematicEngine.addTrauma(.6f, 14); firstBeam = false; }
        // The first column of light to come down is felt.
        if (!firstBeam && cmd >= 0 && !SilentGateRenderer.shots(frame.casterId()).isEmpty()) { firstBeam = true; GodWandCinematicEngine.addTrauma(.5f, 14); }
        // Every beam that lands is felt, the nearer the lens the harder (owner, 2026-11-14: "no weight").
        if (cmd >= 0) {
            Vec3 lens = client.gameRenderer.getMainCamera().getPosition();
            double now = frame.startedAt() + age, sum = 0;
            for (Shot s : SilentGateRenderer.shots(frame.casterId())) {
                double life = SilentGateRenderer.beamLife(s, now) - GateBeams.FALL_TICKS;
                if (life < 0 || life >= 1) continue;
                double near = Mth.clamp(1 - (s.hit().distanceTo(lens) - 10) / 150, 0, 1);
                sum += (s.heavy() ? .5 : .16) * near * near;
            }
            if (sum > .01) GodWandCinematicEngine.addTrauma((float) Math.min(.45, sum), 6);
        }
        if (lastHit < 0 && hit >= 0) GodWandCinematicEngine.addTrauma(.7f, 16);
        lastAge = age; lastHit = hit; lastCmd = cmd;
        if (directing() && !menuOpen(client)) GodWandCinematicEngine.setFovPunch((float) lens(age, frame.commandAge(), frame.barrageTicks()), 2);
        // A held use button is one click: the key is let go for the game (a new press still counts), so holding it neither replays the arm swing every 4 ticks
        // nor gives the command the instant the wait begins.
        client.options.keyUse.setDown(false);
        // Sneak stops the spell while it is being built or waits; once it fires it cannot be called back. A fresh press only: whoever was sneaking when they
        // cast must let go first.
        boolean sneak = !menuOpen(client) && client.options.keyShift.isDown();
        if (sneak && !sneakHeld && age > 8 && frame.phase() <= WAIT && cancelSent != frame.startedAt()) {
            cancelSent = frame.startedAt();
            Services.NETWORK.sendToServer(new SilentGateCancelPayload(frame.startedAt()));
        }
        sneakHeld = sneak;
        // ESC arrives through the keyboard mixin (cancel in WAIT); F5 only drops the director.
        if (!menuOpen(client) && GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_F5) == GLFW.GLFW_PRESS) skip();
    }
    private static long beatCast = Long.MIN_VALUE; private static double lastAge, lastHit, lastCmd; private static boolean firstBeam, sneakHeld;

    /** Ticks since the kill beam landed on the render clock of {@code f} (it crawls through the hit-stop); negative before it. */
    static double sinceHit(Frame f) { return f.startedAt() == hitCast && hitAt >= 0 ? f.startedAt() + f.age() - hitAt : -1; }

    /** Impact frames, the anime kind (owner, 2026-11-14): {ink 0/1/2, white flash 0..1}. Ink 1 is the view in two tones, white on black; ink 2 the same turned
     *  over, black on white; one after the other, then a small flash that dies away. The first click, the touchdown, the command and the kill beam are inked;
     *  the doors' stop only flashes. */
    static double[] flash(double age, double commandAge, double sinceHit) {
        double ink = 0, white = 0, d = age - 3;
        if (d >= 0 && d < 2) ink = d < 1 ? 1 : 2; else if (d >= 2) white = .22 * Math.exp(-(d - 2) / 2.5);
        d = age - SilentGateSequence.DESCENT_END;
        if (d >= 0 && d < 2.5) ink = d < 1.25 ? 1 : 2; else if (d >= 2.5) white = Math.max(white, .3 * Math.exp(-(d - 2.5) / 2.5));
        d = age - SilentGateSequence.OPEN_END;
        if (d >= 0) white = Math.max(white, .22 * Math.exp(-d / 4));
        if (commandAge >= 0 && commandAge < 2) ink = commandAge < 1 ? 1 : 2; else if (commandAge >= 2) white = Math.max(white, .22 * Math.exp(-(commandAge - 2) / 2.5));
        // The hit-stop holds the render clock at 15% speed for 3 ticks, so .3 on this clock is two real ticks.
        if (sinceHit >= 0 && sinceHit < .3) ink = sinceHit < .15 ? 1 : 2; else if (sinceHit >= .3) white = Math.max(white, .36 * Math.exp(-(sinceHit - .3) / 2.5));
        return new double[]{ink, white < .01 ? 0 : white};
    }

    /** Field-of-view multiplier of the shot at this moment: long lens on the caster's close-ups, wide on the gate, and a slow zoom inside the held wides. */
    static double lens(double age, double commandAge, int barrageTicks) {
        if (commandAge >= 0) {
            int[] b = boundaries(barrageTicks);
            if (commandAge >= b[3]) { double e = commandAge - b[3]; return e < SilentGateSequence.END_CLOSE ? .4 : e < SilentGateSequence.END_RISE_FROM ? 1.08 : .55; }
            double tb = commandAge < SilentGateSequence.ACTIVATE_TICKS ? commandAge : SilentGateSequence.ACTIVATE_TICKS + (commandAge - SilentGateSequence.ACTIVATE_TICKS) * 200.0 / barrageTicks;
            // The command close; down on the field through a longer lens (it keeps the picture inside the loaded land); wide from the caster's feet up into the
            // rain; then the track over the ground and the far round.
            // Each shot holds one lens from its first frame to its last: the camera moves, the lens never zooms.
            // The shot up from the caster's feet on a normal lens: a wide one stretched it (owner, 2026-11-15).
            return tb < 14 ? .9 * (1 - .1 * Math.exp(-tb / 2.5)) : tb < 60 ? 1.05 : tb < B4_FROM ? 1 : tb < B4_TO ? .95 : 1.02;
        }
        // The first click kicks the lens once.
        if (age < 70) return .9 * (1 + .1 * smooth(age, 1, 3) * Math.exp(-Math.max(0, age - 3) / 3.5));
        if (age < 142) return 1;
        // The gate comes down on a long lens from far off (the flattened perspective is what makes it enormous); the view opens slowly as the wings spread.
        // Close on the gate while it sinks; when the wings burst open the view is thrown wide, fast at first and still opening when the shot ends: it never
        // arrives and stands (owner, 2026-11-14: "a little slower, and no dead moment").
        if (age < 258) return .38 + .57 * thrown(age);
        if (age < 290) return .82;
        if (age < 340) return .82 + .13 * Curve.FLOW.u((age - 290) / 50);
        // One shot for the doors: round the gate, a gentle push in as they give.
        if (age < ORBIT_TO) return .8 - .04 * (age - ORBIT_FROM) / 80.0 - .1 * smooth(age, 396, 418);
        // The canopy, wide, opening slowly while the circles bloom.
        if (age < CANOPY_TO) return 1.04 + .1 * (age - ORBIT_TO) / (CANOPY_TO - ORBIT_TO);
        return .95;
    }

    /** Shot changes (owner, 2026-11-15: fades at every cut grated): where the next setup is close by, the camera does not cut but glides there over the first
     *  ticks of the new shot ({@link #glideTicks}), easing out of the last shot's end and into the new move (smootherstep); a setup far away is a plain hard cut. */
    static final double GLIDE_REACH = 30;
    /** The farther the glide, the longer it takes: never a whip. */
    static double glideTicks(double distance) { return Mth.clamp(14 + 3 * distance, 20, 45); }
    /** The pose of {@code table} at {@code t} (its own clock), with the glides between neighbouring setups; past the table, its last shot runs on. */
    private static AtomicCinematic.Pose play(List<Cut> table, Rig g, double t) { return play(table, g, t, true); }
    /** {@code glide} false: every shot change is a cut (the barrage: owner, 2026-11-15, the command cuts to the shot from the caster). */
    private static AtomicCinematic.Pose play(List<Cut> table, Rig g, double t, boolean glide) {
        int i = 0;
        while (i < table.size() - 1 && t >= table.get(i).t1()) i++;
        Cut c = table.get(i);
        var p = c.at(g, Math.max(t, c.t0()));
        if (!glide || i == 0 || t - c.t0() >= glideTicks(GLIDE_REACH)) return p;
        Cut prev = table.get(i - 1);
        var from = prev.at(g, prev.t1());
        double d = from.position().distanceTo(c.at(g, c.t0()).position());
        if (d > GLIDE_REACH) return p;
        return blend(from, p, (float) Curve.GLIDE.u((t - c.t0()) / glideTicks(d)));
    }

    /** 0..1: how far the view has been thrown back by the wings, from the shockwave to the end of the gate's shot (258): most of it in the first half second, the rest a long coast. */
    static double thrown(double age) { double k = 1 - Mth.clamp((age - SilentGateSequence.SHOCK) / (258.0 - SilentGateSequence.SHOCK), 0, 1); return 1 - k * k * k; }

    public static void skip() { skipped = true; }
    public static void reset() { cast = Long.MIN_VALUE; cancelSent = Long.MIN_VALUE; world = null; skipped = false; hitCast = Long.MIN_VALUE; hitAt = -1; beatCast = Long.MIN_VALUE; }

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
        // Every frame, before the sky is drawn: a time packet may have put the day back since the last tick.
        GateNight.frame(delta);
        if (!active()) return null;
        var client = Minecraft.getInstance();
        var frame = SilentGateRenderer.localFrame(delta);
        // The cast can end between the tick and this frame's partial tick (it crashed here: NullPointerException, 2026-11-10 and -11).
        if (frame == null) return null;
        var player = client.player;
        Vec3 feet = player.getPosition(delta);
        float yaw = player.getViewYRot(delta), pitch = player.getViewXRot(delta);
        var level = client.level;
        var shots = SilentGateRenderer.shots(frame.casterId());
        Vec3 focus = directing() && frame.commandAge() >= 0 ? focus(client, frame, shots) : null;
        var shot = cameraFor(frame, feet, yaw, pitch, directing(), shots,
                (x, z) -> level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)), focus);
        // The camera keeps its path through blocks (owner, 2026-11-14) and past the loaded chunks: the far shots (the orbit reaches 184 blocks out) fell back to
        // the shoulder at a render distance of 8 and showed as a stray shot before the bloom (owner, 2026-11-15). Gate, sky and circles need no chunks.
        return shot;
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
     *  the others are single curves of progress u (0..1) over time k (0..1). */
    enum Curve {
        /** One continuous camera move through keyframes. */
        KEYS,
        /** Camera does not move; the subject does. */
        STATIC, LINEAR,
        /** A move that starts and ends inside its shot: from rest to rest, with no jerk at either end (smootherstep). */
        GLIDE,
        /** A move that is already under way when the shot opens and still going when it is cut: it swells in the middle but never rests (speed .55 of the mean at
         *  both ends, 1.2 at the middle). The camera of this spell is always on its way somewhere (owner, 2026-11-14). */
        FLOW,
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
                case GLIDE -> k * k * k * (k * (6 * k - 15) + 10);
                case FLOW -> .55 * k + .45 * k * k * (3 - 2 * k);
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
        /** Height of the field the beams fall on: the median of the ground at points across it, never above the caster. A caster on a tree or a tower stands
         *  over it (owner, 2026-11-15); without ground (unloaded, tests) it is the caster's own height. */
        double fieldY() {
            double[] ys = new double[15]; int n = 0;
            for (int i = 0; i < 5; i++) for (int j = -1; j <= 1; j++) {
                Vec3 p = rel(40 + 30 * i, 50 * j, 0);
                double y = ground.applyAsDouble(p.x, p.z);
                if (Double.isFinite(y) && y > a.y - 160) ys[n++] = y;
            }
            if (n < 4) return a.y;
            java.util.Arrays.sort(ys, 0, n);
            return Math.min(a.y, ys[n / 2]);
        }
        /** As {@link #rel} but {@code up} above the field rather than the caster's feet. */
        Vec3 onField(double fwd, double right, double up) { Vec3 p = rel(fwd, right, 0); return new Vec3(p.x, fieldY() + up, p.z); }
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
    /** {@code p} turned by a shake given in degrees for a normal lens: on the long lens of the shot at {@code t} it is scaled down, so it stays about 5% of the frame. */
    private static AtomicCinematic.Pose turn(AtomicCinematic.Pose p, double[] shake, double t) {
        double k = Math.min(1, 1.6 * lens(t, -1, 200));
        return new AtomicCinematic.Pose(p.position(), (float) (p.yaw() + shake[0] * k), (float) (p.pitch() + shake[1] * k));
    }

    // One axis for the whole cast: the camera stays on the caster's right of the caster-to-gate line and never crosses it. The gate is shot from far off on long
    // lenses (the flattened perspective is what makes it enormous), the sky on wide ones; the only reverse angles are the caster's own beats (face, waiting frame, command).

    /** Blocks from the caster to the gate. */
    static final double GATE_AHEAD = SilentGateSequence.GATE_AHEAD;

    /** Script v6 (owner, 2026-11-12): few cuts, and a camera that is always slowly on its way somewhere. The opening is close before the caster's face while the
     *  finger comes to the lips, the wand afloat between; a slow drift in. */
    private static AtomicCinematic.Pose faceShot(Rig g, double u) {
        // Square on to the face and closer (owner, 2026-11-14): between the face and the wand afloat before it.
        return look(g.rel(1.22 - .14 * u, 0, 1.66), g.rel(0, 0, 1.62));
    }
    /** Then it draws back from the face, quickly at first and coasting to a crawl, into the wide before the caster: there the wand is spun and the discs are made behind. */
    private static AtomicCinematic.Pose drawBack(Rig g, double u) {
        // Still square on, and not far: the caster and the discs behind them fill the frame.
        return look(g.rel(1.08 + 2.7 * u, 0, 1.66 + .12 * u), g.rel(0, 0, 1.62 - .12 * u));
    }
    /** Script v7, the first shot: beside the caster and a little before them, low: the raised hand, the wand afloat before it, and the wind of mana streaming
     *  back past them across the frame; the camera drifts back with the stream. */
    private static AtomicCinematic.Pose raiseShot(Rig g, double u) {
        return look(g.rel(1.5 - 1.3 * u, 3.5 - .4 * u, 1.35 + .15 * u), g.rel(.3 - .5 * u, 0, 1.45));
    }
    /** The gate comes out of the sky and down, in one shot from the ground far behind the caster, looking up (owner, 2026-11-12: not from above): the camera holds
     *  the crack until the gate comes through it, tilts down with the gate, and rides in slowly all the while; when the wings open it looks a little higher. */
    private static AtomicCinematic.Pose comingDown(Rig g, double u, double t) {
        double crack = SilentGateSequence.CIRCLE_ALTITUDE - SilentGateSequence.GATE_BASE - 30;
        double centre = SilentGateSequence.gateFoot(g.a(), g.yaw(), SilentGateSequence.gateDescent(t)).y - g.foot().y + 150, soft = 25;
        // The lower of the two, without a kink where they cross.
        double aim = -soft * Math.log(Math.exp(-crack / soft) + Math.exp(-centre / soft));
        return look(g.rel(-88 + 30 * u, 16, 3), g.foot().add(0, aim + 30 * thrown(t), 0));
    }
    /** The orbit (owner, 2026-11-11, reference 2): once the gate hangs still and before its doors move, the camera is in the air under it, to the caster's right,
     *  and goes slowly round it from 16 to 44 degrees off the caster's line, looking up at the leaves. */
    static final int ORBIT_FROM = 340, ORBIT_TO = 420;
    /** The canopy (owner, 2026-11-15: the most epic shot, keep it and make it long): from the ground at the caster's shoulder, looking up at the gate's face while
     *  the small circles bloom across the sky (the timing as first cut: a longer one felt off). */
    static final int CANOPY_TO = 468;
    private static AtomicCinematic.Pose orbitShot(Rig g, double u) {
        double a = Math.toRadians(14 + 32 * u), r = 170;
        return look(g.rel(GATE_AHEAD - r * Math.cos(a), r * Math.sin(a), 130), g.door().add(0, -18, 0));
    }
    /** Shot 7: over the caster's shoulder from the ground, one glide of a tilt (28 to 52 degrees) up into the sky where the great circle writes itself tier by tier. */
    private static AtomicCinematic.Pose circleShot(Rig g, double u) {
        return look(g.rel(-7 + 2.5 * u, 3, 1.2 + 1.5 * u), g.rel(93, -2, 1.2 + 100 * Math.tan(Math.toRadians(28 + 24 * u))));
    }
    /** The sky circle of shot 14: one on the caster's right, 25..95 blocks off (so the camera under it stays in loaded chunks), not in the top tiers, whose writing
     *  starts nearest to tick 449: its seed is falling as the shot opens and it locks just before the shot ends. -1 without any. */
    static int formingCircle(Rig g) {
        int best = -1; double bestD = Double.MAX_VALUE;
        for (int pass = 0; pass < 2 && best < 0; pass++) for (int i = 0; i < Math.min(g.sky(), SilentGateSequence.MAX_CIRCLES); i++) {
            Vec3 p = SilentGateSequence.skyCircle(g.a(), g.yaw(), g.seed(), i, g.sky()).subtract(g.a());
            double h = Math.hypot(p.x, p.z), d = Math.abs(SilentGateSequence.circleBorn(i) - 449);
            // Second pass: any circle at all, when none is so well placed.
            if (pass == 0 && (p.dot(g.r()) < 10 || h < 25 || h > 95 || SilentGateSequence.tier(i) > 2)) continue;
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }
    /** The far round: on a circle of 125 blocks about the caster, behind them and to their right, 62 up and rising, slowly from 28 to 54 degrees off the line. */
    /** One against an army (owner, 2026-11-15): far off and low over the land, slowly going round the struck field, the circles filling the upper frame and
     *  the field and its dying below. 150 blocks from the field's centre, 35 over it. */
    private static AtomicCinematic.Pose panorama(Rig g, double u) {
        Vec3 c = g.onField(FIELD_CENTRE, 0, 0);
        double a = Math.toRadians(150 - 30 * u), r = 150;
        Vec3 p = g.rel(FIELD_CENTRE + r * Math.cos(a), r * Math.sin(a), 0);
        return look(new Vec3(p.x, c.y + 35, p.z), c.add(0, 75, 0));
    }
    /** Where the barrage falls thickest: the field before the caster, {@value #FIELD_CENTRE} blocks out. */
    static final double FIELD_CENTRE = 60;
    /** B3 (owner, 2026-11-15): just under the lowest tier of circles, far out, going round the field and looking down on it, where the beams strike. */
    private static AtomicCinematic.Pose fieldOrbit(Rig g, double u) {
        Vec3 c = g.onField(FIELD_CENTRE, 0, 0);
        double a = Math.toRadians(155 + 35 * u), r = 115;
        Vec3 p = g.rel(FIELD_CENTRE + r * Math.cos(a), r * Math.sin(a), 0);
        return look(new Vec3(p.x, g.a().y + SilentGateSequence.TIER_HEIGHTS[0] - 13, p.z), c.add(0, 4, 0));
    }
    /** Shot 14: from the ground 30 blocks off under one circle, looking up its axis on a long lens while it is written: the seed falls in, two points of light draw the
     *  rim, the seal turns into place and the tiers drop toward the lens. */
    private static AtomicCinematic.Pose formingShot(Rig g) {
        int i = formingCircle(g);
        Vec3 c = i >= 0 ? SilentGateSequence.skyCircle(g.a(), g.yaw(), g.seed(), i, g.sky()) : g.rel(30, 70, SilentGateSequence.TIER_HEIGHTS[0]);
        Vec3 toCaster = new Vec3(g.a().x - c.x, 0, g.a().z - c.z);
        toCaster = toCaster.lengthSqr() < 1 ? g.f().scale(-1) : toCaster.normalize();
        return look(g.above(new Vec3(c.x, g.a().y + 2, c.z).add(toCaster.scale(30)), 2), c.add(0, -6, 0));
    }

    /** The fifteen shots of the build (BUILD_TICKS = 480): hard cuts, each shot still, a slow drift, or one glide from rest to rest. */
    static final List<Cut> CUTS = List.of(
            new Cut("S01 the hand is raised", 0, 70, Curve.FLOW, (g, u, t) -> raiseShot(g, u)),
            new Cut("S02 circle writes", 70, 142, Curve.FLOW, (g, u, t) -> circleShot(g, u)),
            new Cut("S03 the gate comes down", 142, 258, Curve.LINEAR, (g, u, t) -> turn(comingDown(g, u, t), landing(t), t)),
            new Cut("S04 face", 258, 290, Curve.FLOW, (g, u, t) -> faceShot(g, u)),
            new Cut("S05 draws back", 290, ORBIT_FROM, Curve.FLOW, (g, u, t) -> drawBack(g, u)),
            // One shot for the doors (owner, 2026-11-14): the camera goes slowly round the gate while they tremble, give and flood; it shakes with them.
            new Cut("S06 round the gate as it opens", ORBIT_FROM, ORBIT_TO, Curve.LINEAR, (g, u, t) -> turn(orbitShot(g, u), rattle(t), t)),
            new Cut("S13 canopy writes", ORBIT_TO, CANOPY_TO, Curve.LINEAR, (g, u, t) -> look(g.rel(-2.5 - 2 * u, 1.6 + .5 * u, 1.4 + .8 * u), g.rel(GATE_AHEAD, 0, 169))),
            new Cut("S15 waiting frame", CANOPY_TO, SilentGateSequence.BUILD_TICKS, Curve.STATIC, (g, u, t) -> waitShot(g, t - CANOPY_TO)));

    /** Barrage shots in authored ticks after the command (a 200 tick barrage starts at 12; scaled with the barrage length). The owner's four, each one slow steady
     *  move with no cut inside it, in the owner's order (2026-11-15, second pass): up from the caster, down from the gate, a track over the ground (where the kill
     *  beam's impact frame falls), a slow round from far off. */
    /** The shot over the ground, kept short around the kill (owner, 2026-11-15: one and a half seconds at 360 ticks of barrage, about one before the kill
     *  beam lands and half a second after, then 0.4 s more after the death); the time went to the shots on either side. */
    static final int B4_FROM = 125, B4_TO = 146;
    static final List<Cut> BARRAGE = List.of(
            new Cut("B1 command", 0, 14, Curve.LINEAR, (g, u, t) -> look(g.rel(3.4 - .3 * u, .5, 1.25), g.rel(0, 0, 1.55))),
            // The camera keeps its path through blocks here too (owner, 2026-11-15: lifted over the ground it jumped about): no shot is pushed by the terrain.
            // From the caster: low at their shoulder, looking up past them into the circles that fire overhead, rising a little toward them.
            // Owner, 2026-11-15: by the caster's feet, close to the ground, looking up on a slightly wide lens: the caster whole, the ground and the command's wave
            // at the foot of the frame, the rain above.
            new Cut("B2 from the caster, up", 14, 60, Curve.LINEAR, (g, u, t) -> look(g.rel(-3 + .3 * u, 1.1 - .1 * u, .12), g.rel(16, -.5, 6 + 2 * u))),
            new Cut("B3 round the field, down", 60, B4_FROM, Curve.LINEAR, (g, u, t) -> fieldOrbit(g, u)),
            // Over the ground: a low track across the field before the caster, the strikes going by on either hand, the gate far ahead; the kill beam lands here.
            new Cut("B4 over the ground", B4_FROM, B4_TO, Curve.LINEAR, (g, u, t) -> g.focus() != null ? killTrack(g, u)
                    : look(g.onField(30 + 4 * u, -14 + 28 * u, 4.5), g.onField(78, -2 + 28 * u, 10))),
            // The whole of it to end on: far behind the caster and high, going slowly round; no zoom, only the move.
            new Cut("B5 the far round", B4_TO, 212, Curve.LINEAR, (g, u, t) -> panorama(g, u)));

    /** The ending (ticks after the barrage): the three setups of the build, in the order the build used them, each drifting slowly: none stands still. */
    static final List<Cut> ENDING = List.of(
            new Cut("E1 doors close", 0, SilentGateSequence.END_CLOSE, Curve.LINEAR, (g, u, e) -> look(g.rel(40 + 3 * u, 88 - 5 * u, 3), g.door().add(0, -10, 0))),
            new Cut("E2 circles unwrite", SilentGateSequence.END_CLOSE, SilentGateSequence.END_RISE_FROM, Curve.LINEAR, (g, u, e) -> look(g.rel(-2.5 - 1.5 * u, 1.6 + .4 * u, 1.7 + .5 * u), g.rel(GATE_AHEAD, 0, 169))),
            // The far camera follows the gate up into the crack and on to the great circle, drawing back as it goes.
            new Cut("E3 gate returns", SilentGateSequence.END_RISE_FROM, SilentGateSequence.ENDING_TICKS, Curve.LINEAR, (g, u, e) -> look(g.rel(-70 - 18 * u, 16, 3), g.foot().add(0, Math.min(SilentGateSequence.CIRCLE_ALTITUDE - SilentGateSequence.GATE_BASE,
                    88 + SilentGateSequence.gateRise(e) * SilentGateSequence.GATE_DROP * SilentGateSequence.SCALE), 0))));

    /** The waiting frame: in front of the caster pointing at the sky, low and almost square on, so the half ring of discs behind them stands symmetric (reference 10).
     *  It sways from its first frame on (no ramp from a standstill) for as long as the wait lasts. */
    private static AtomicCinematic.Pose waitShot(Rig g, double t) {
        Vec3 breath = g.r().scale(Math.sin(t * .045) * .3).add(0, Math.sin(t * .07 + 1) * .22, 0).add(g.f().scale(Math.sin(t * .031) * .35));
        return look(g.rel(5.2, .5, .75).add(breath), g.rel(-2, 0, 4.2));
    }

    static AtomicCinematic.Pose buildShot(Frame frame, double age) { return buildShot(frame, age, NO_GROUND); }
    static AtomicCinematic.Pose buildShot(Frame frame, double age, ToDoubleBiFunction<Double, Double> ground) {
        Rig g = new Rig(frame, ground, List.of(), null);
        return play(CUTS, g, age);
    }

    /** Boundaries in the barrage (authored for 200 ticks, scaled): the kill shot starts (the target is locked), the shot from above starts, the low shot starts, the barrage ends. */
    static int[] boundaries(int barrageTicks) {
        return new int[]{SilentGateSequence.shotTick(46, barrageTicks), SilentGateSequence.shotTick(125, barrageTicks),
                SilentGateSequence.shotTick(162, barrageTicks), SilentGateSequence.ACTIVATE_TICKS + barrageTicks};
    }

    private static AtomicCinematic.Pose barrageShot(Frame frame, double c, List<Shot> shots, ToDoubleBiFunction<Double, Double> ground, Vec3 focus) {
        int[] b = boundaries(frame.barrageTicks());
        Rig g = new Rig(frame, ground, shots, focus);
        if (c >= b[3]) {
            double e = Math.min(c - b[3], SilentGateSequence.ENDING_TICKS);
            return play(ENDING, g, e);
        }
        // Authored clock: the first 12 ticks (the command) run as they are, the rest is stretched with the barrage.
        double tb = c < SilentGateSequence.ACTIVATE_TICKS ? c : SilentGateSequence.ACTIVATE_TICKS + (c - SilentGateSequence.ACTIVATE_TICKS) * 200.0 / frame.barrageTicks();
        var pose = play(BARRAGE, g, tb, false);
        int i = 0;
        while (i < BARRAGE.size() - 1 && tb >= BARRAGE.get(i).t1()) i++;
        double lift = shotLift(BARRAGE.get(i), g);
        return lift > 0 ? new AtomicCinematic.Pose(pose.position().add(0, lift, 0), pose.yaw(), pose.pitch()) : pose;
    }

    /** Real land (owner, 2026-11-15: the shots were made on a flat world): how far a whole shot is raised so that along its path the camera is never
     *  inside the ground and, where it looks out level, no hill in front hides the view. One height for the whole shot, so the camera never jumps inside
     *  it (being pushed about by the terrain was what the owner disliked before). At most 60 blocks. */
    static double shotLift(Cut c, Rig g) {
        double lift = 0;
        for (int k = 0; k <= 10; k++) {
            var p = c.at(g, c.t0() + (c.t1() - c.t0()) * k / 10.0);
            Vec3 o = p.position();
            lift = Math.max(lift, g.ground().applyAsDouble(o.x, o.z) + 1 - o.y);
            if (Math.abs(p.pitch()) < 15) {
                Vec3 f = Vec3.directionFromRotation(p.pitch(), p.yaw());
                for (int j = 1; j <= 8; j++) { Vec3 q = o.add(f.scale(5 * j)); lift = Math.max(lift, g.ground().applyAsDouble(q.x, q.z) + 1 - q.y); }
            }
        }
        return Math.min(lift, 60);
    }

    /** B4 with a kill target (owner, 2026-11-15: the track went past and the mob's death was never seen): the same low sideways track, but
     *  {@value #KILL_DISTANCE} blocks off the target on the caster's side and always looking at it, so it is square in frame when the kill beam lands. */
    static final double KILL_DISTANCE = 16, KILL_CAM_HEIGHT = 3.5, KILL_LOOK_HEIGHT = 3;
    private static AtomicCinematic.Pose killTrack(Rig g, double u) {
        Vec3 at = g.focus();
        Vec3 toward = new Vec3(g.a().x - at.x, 0, g.a().z - at.z);
        toward = toward.lengthSqr() < 1 ? g.f().scale(-1) : toward.normalize();
        Vec3 side = toward.cross(UP);
        // A slow drift (owner, 2026-11-15): four blocks across the shot.
        Vec3 cam = at.add(toward.scale(KILL_DISTANCE)).add(side.scale(-2 + 4 * u)), aim = at.add(0, KILL_LOOK_HEIGHT, 0);
        // Above the live surface (the strikes dig craters under the track) and high enough that no hill or tree stands between the lens and the target
        // (owner, 2026-11-15: in hilly land the kill was hidden).
        double y = Math.max(at.y + KILL_CAM_HEIGHT, g.ground().applyAsDouble(cam.x, cam.z) + 1.5);
        for (int k = 1; k <= 32; k++) {
            double s = k / 32.0;
            double ground = g.ground().applyAsDouble(aim.x + (cam.x - aim.x) * s, aim.z + (cam.z - aim.z) * s) + 1;
            // As if the rise began a sample nearer the target: the slope between two samples is steeper than at either.
            if (Double.isFinite(ground)) y = Math.max(y, aim.y + (ground - aim.y) / (s - .5 / 32));
        }
        return look(new Vec3(cam.x, Math.min(y, aim.y + 40), cam.z), aim);
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
                // The server's featured target is the one the kill beam strikes: never stand in another for it (owner, 2026-11-15: the camera framed the
                // wrong mob, or none); until the client sees it, nothing is locked.
                if (featured >= 0) { best = null; for (Candidate k : candidates) if (k.id() == featured) best = k; }
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
        // No featured target: no kill beam, nothing to follow.
        Vec3 at = frame.featured() < 0 ? null : LOCK.update(frame.startedAt(), frame.commandAge(), lockAt, list, anchor, frame.featured());
        if (hitCast != frame.startedAt()) { hitCast = frame.startedAt(); hitAt = -1; }
        Shot heavy = null;
        for (Shot s : shots) if (s.heavy()) { heavy = s; break; }
        // Out of the client's tracking range the target is not seen: frame where the kill beam lands instead.
        if (at == null && heavy != null) at = heavy.hit();
        if (hitAt < 0 && heavy != null && at != null && heavy.hit().distanceToSqr(at) < 36) hitAt = heavy.tick() + SilentGateSequence.HEAVY_CHARGE;
        return at;
    }

    /** Render clock with a ~3 tick hit-stop: it crawls at 15% of real speed for 3 ticks from the hit, then stays that much behind. */
    static double warp(long castStartedAt, double now) { return castStartedAt == hitCast ? warp(now, hitAt) : now; }
    static double warp(double now, double hit) {
        if (hit < 0 || now <= hit) return now;
        double d = now - hit;
        return d < HIT_STOP_TICKS ? hit + d * HIT_STOP_SPEED : now - HIT_STOP_TICKS * (1 - HIT_STOP_SPEED);
    }

    /** The scope bars: the share of the frame's height each bar covers (they are drawn in the world pass, so light can fly over them). Shut at the first frame,
     *  they part like a curtain and close again as the camera returns to the player; in between they change only at a cut, never inside a shot (owner,
     *  2026-11-15: they kept pulling in and out). 2.2:1 as a rule; more open for the doors and the canopy and for the shot up from the caster's feet. */
    static double bar(double age, double commandAge, double sinceHit, int barrageTicks, double frameAspect) {
        double aspect = 2.2, k = 1;
        if (commandAge < 0) {
            if (age >= ORBIT_FROM && age < CANOPY_TO) aspect = 1.95;
        } else {
            double tb = commandAge < SilentGateSequence.ACTIVATE_TICKS ? commandAge : SilentGateSequence.ACTIVATE_TICKS + (commandAge - SilentGateSequence.ACTIVATE_TICKS) * 200.0 / barrageTicks;
            if (tb >= 14 && tb < 60) aspect = 1.9;
            int end = SilentGateSequence.endTick(barrageTicks);
            if (commandAge > end - 10) k = 1 - smooth(commandAge, end - 10, end + SilentGateSequence.RETURN_TICKS);
        }
        double open = smooth(age, 1, 24), scope = Math.max(0, (1 - frameAspect / aspect) / 2);
        return k * (scope + (1 - open) * (.5 - scope));
    }
    /** The bar of the local cast right now, 0 when it is not being directed. */
    static double bar(Frame frame, double frameAspect) {
        return directing() && frame != null ? bar(frame.age(), frame.commandAge(), sinceHit(frame), frame.barrageTicks(), frameAspect) : 0;
    }

    /** The command hint in WAIT. */
    public static void render(GuiGraphics graphics, DeltaTracker timer) {
        float partial = timer.getGameTimeDeltaPartialTick(false);
        var client = Minecraft.getInstance();
        var frame = SilentGateRenderer.localFrame(partial);
        if (menuOpen(client) || !present(client, frame)) return;
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        if (frame.phase() == WAIT) {
            String hint = Component.translatable("message.godwand.gate_command").getString();
            graphics.drawString(client.font, hint, (width - client.font.width(hint)) / 2, height - 62, 0xFFFFFFFF, true);
        }
    }
}
