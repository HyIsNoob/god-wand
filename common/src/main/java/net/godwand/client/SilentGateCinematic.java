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

/** Directed camera, input locks, ESC/F5 handling and the crude eyelid/hint overlay of Silent Gate. */
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
            var shot = buildShot(frame, Math.min(frame.age(), SilentGateSequence.BUILD_TICKS));
            // The waiting frame breathes: a slow drift, fading in so the hold starts exactly where the build ended.
            double t = frame.age() - SilentGateSequence.BUILD_TICKS;
            if (frame.phase() != WAIT || t <= 0) return shot;
            Vec3 right = Vec3.directionFromRotation(0, frame.yaw()).cross(UP);
            double w = smooth(t, 0, 20);
            return new AtomicCinematic.Pose(shot.position().add(right.scale(Math.sin(t * .045) * .15 * w)).add(0, Math.sin(t * .07) * .2 * w, 0), shot.yaw(), shot.pitch());
        }
        double c = frame.commandAge();
        int end = SilentGateSequence.endTick(frame.barrageTicks());
        if (c >= end + SilentGateSequence.RETURN_TICKS) return null;
        if (c < 0) return buildShot(frame, SilentGateSequence.BUILD_TICKS);
        var shot = barrageShot(frame, Math.min(c, end), shots, ground, focus);
        // Slide from the last framing into the player's own eyes, then the camera is released.
        return c <= end ? shot : blend(shot, new AtomicCinematic.Pose(player.add(0, 1.62, 0), yaw, pitch), smooth(c, end, end + SilentGateSequence.RETURN_TICKS));
    }

    private static AtomicCinematic.Pose buildShot(Frame frame, double age) {
        Vec3 a = frame.anchor(), f = Vec3.directionFromRotation(0, frame.yaw()), r = f.cross(UP);
        if (age < SilentGateSequence.BEAT_RAISE) {
            // First person at chest height, the wand floating ahead.
            double k = smooth(age, 0, 20);
            Vec3 position = a.add(0, 1.45 + .1 * k, 0).add(f.scale(.1));
            return look(position, position.add(f.scale(8)).add(0, -.3 + .6 * k, 0));
        }
        if (age < SilentGateSequence.BEAT_SHUSH) {
            // Cut to a low three-quarter view from the front while the wand is spun.
            double k = smooth(age, 20, 45);
            return look(a.add(f.scale(3.4)).add(r.scale(1.9)).add(0, .9 + .3 * k, 0), a.add(0, 1.4 + .3 * k, 0));
        }
        if (age < SilentGateSequence.BEAT_CIRCLE) {
            // Slow push in on the face.
            double k = smooth(age, 45, 80);
            return look(a.add(f.scale(2.4 - 1.3 * k)).add(r.scale(.9 - .55 * k)).add(0, 1.45 + .15 * k, 0), a.add(0, 1.62, 0));
        }
        if (age < SilentGateSequence.BEAT_GATE) {
            // From the caster looking up, turning slowly under the great circle.
            double t = smooth(age, 80, 140), angle = 1.2 * t;
            Vec3 position = a.add(f.scale(-Math.cos(angle) * 3)).add(r.scale(Math.sin(angle) * 3)).add(0, 1 + .4 * t, 0);
            return look(position, a.add(0, SilentGateSequence.circleAltitude(a.y) * (.35 + .65 * t), 0));
        }
        Vec3 foot = SilentGateSequence.gateFoot(a, frame.yaw(), 0);
        if (age < SilentGateSequence.BEAT_OPEN) {
            // Low on the ground, far enough back to see the whole gate, watching it come down out of the slit.
            double t = smooth(age, SilentGateSequence.BEAT_GATE, SilentGateSequence.BEAT_OPEN);
            Vec3 position = a.add(f.scale(24 * S + 6 * S * t)).add(r.scale(10 * S - 12 * S * t)).add(0, 1.5 + .5 * t, 0);
            Vec3 target = foot.add(r.scale(-2 * S * t)).add(0, S * (SilentGateSequence.GATE_DROP * Math.max(0, SilentGateSequence.gateDescent(age)) + 24 + 8 * t), 0);
            return look(position, target);
        }
        if (age < SilentGateSequence.BEAT_ARRAY) {
            // The gate settles and opens: from right under the left column and the spike, easing out to a wide shot of the whole gate
            // with the caster a speck beneath it (ref image 4).
            double t = smooth(age, SilentGateSequence.BEAT_OPEN, SilentGateSequence.BEAT_ARRAY);
            double close = 1 - smooth(age, SilentGateSequence.BEAT_OPEN + 4, SilentGateSequence.BEAT_OPEN + 28);
            Vec3 wide = a.add(f.scale(-120)).add(r.scale(22)).add(0, 6, 0), tight = foot.add(f.scale(-17 * S)).add(r.scale(-15 * S)).add(0, -9 * S, 0);
            Vec3 position = wide.lerp(tight, close);
            Vec3 target = foot.add(r.scale(-9 * S * close)).add(0, S * (32 + 12 * close), 0);
            return look(position, target);
        }
        // From below the gate's foot, racing back and up to the caster's shoulder: the held frame.
        double s = Mth.clamp((age - SilentGateSequence.BEAT_ARRAY) / 38, 0, 1), e = 1 - (1 - s) * (1 - s) * (1 - s);
        Vec3 start = a.add(f.scale(-120)).add(r.scale(22)).add(0, 6, 0), end = a.add(f.scale(-18)).add(r.scale(2)).add(0, 12, 0);
        return look(start.lerp(end, e), foot.add(0, 36 * S, 0).lerp(a.add(f.scale(40)).add(0, 60, 0), e));
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
        if (c < s12) return buildShot(frame, SilentGateSequence.BUILD_TICKS);
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

    /** Crude eyelids (two black bars that retract over the first 20 ticks) and the command hint in WAIT. */
    public static void render(GuiGraphics graphics, DeltaTracker timer) {
        float partial = timer.getGameTimeDeltaPartialTick(false);
        var client = Minecraft.getInstance();
        var frame = SilentGateRenderer.localFrame(partial);
        if (menuOpen(client) || !present(client, frame)) return;
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        if (directing() && frame.phase() == 0 && frame.age() < SilentGateSequence.BEAT_RAISE) {
            int bar = Math.round(height * .5f * (1 - smooth(frame.age(), 0, SilentGateSequence.BEAT_RAISE)));
            graphics.fill(0, 0, width, bar, 0xFF000000);
            graphics.fill(0, height - bar, width, height, 0xFF000000);
        }
        if (frame.phase() == WAIT) {
            String hint = Component.translatable("message.godwand.gate_command").getString();
            graphics.drawString(client.font, hint, (width - client.font.width(hint)) / 2, height - 62, 0xFFFFFFFF, true);
        }
    }
}
