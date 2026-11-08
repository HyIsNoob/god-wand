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

import static net.godwand.client.FugaCinematic.blend;
import static net.godwand.client.FugaCinematic.look;
import static net.godwand.client.FugaCinematic.shoulder;
import static net.godwand.client.FugaCinematic.smooth;

/** Directed camera, input locks, ESC/F5 handling and the crude eyelid/hint overlay of Silent Gate. */
public final class SilentGateCinematic {
    private static final Vec3 UP = new Vec3(0, 1, 0);
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
    public static void reset() { cast = Long.MIN_VALUE; cancelSent = Long.MIN_VALUE; world = null; skipped = false; }

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
        var shot = cameraFor(frame, feet, yaw, pitch, directing(), SilentGateRenderer.shots(frame.casterId()));
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
        var shot = barrageShot(frame, Math.min(c, end), shots);
        // Slide from the last framing into the player's own eyes, then the camera is released.
        return c <= end ? shot : blend(shot, new AtomicCinematic.Pose(player.add(0, 1.62, 0), yaw, pitch), smooth(c, end, end + SilentGateSequence.RETURN_TICKS));
    }

    private static AtomicCinematic.Pose buildShot(Frame frame, double age) {
        Vec3 a = frame.anchor(), f = Vec3.directionFromRotation(0, frame.yaw()), r = f.cross(UP);
        Vec3 gate = SilentGateSequence.gateCenter(a, frame.yaw());
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
            return look(position, a.add(0, 35 + 65 * t, 0));
        }
        if (age < SilentGateSequence.BEAT_OPEN) {
            // Low on the ground, watching the gate come down.
            double t = smooth(age, SilentGateSequence.BEAT_GATE, SilentGateSequence.BEAT_OPEN);
            Vec3 position = a.add(f.scale(18 + 8 * t)).add(r.scale(4 - 6 * t)).add(0, 1.5 + .5 * t, 0);
            return look(position, gate.add(0, 22 * Math.max(0, SilentGateSequence.gateDescent(age)), 0));
        }
        if (age < SilentGateSequence.BEAT_ARRAY) {
            double t = smooth(age, SilentGateSequence.BEAT_OPEN, SilentGateSequence.BEAT_ARRAY);
            return look(a.add(f.scale(-12 + 4 * t)).add(r.scale(10 - 6 * t)).add(0, 9 + 3 * t, 0), gate);
        }
        // From the gate's foot, racing back and up to the caster's shoulder: the held frame.
        double s = Mth.clamp((age - SilentGateSequence.BEAT_ARRAY) / 38, 0, 1), e = 1 - (1 - s) * (1 - s) * (1 - s);
        Vec3 start = gate.add(f.scale(-7)).add(0, -14, 0), end = a.add(f.scale(-18)).add(r.scale(2)).add(0, 12, 0);
        return look(start.lerp(end, e), gate.lerp(a.add(f.scale(10)).add(0, 26, 0), e));
    }

    private static AtomicCinematic.Pose barrageShot(Frame frame, double c, List<Shot> shots) {
        int b = frame.barrageTicks();
        int s12 = SilentGateSequence.shotTick(12, b), s50 = SilentGateSequence.shotTick(50, b), s80 = SilentGateSequence.shotTick(80, b),
                s110 = SilentGateSequence.shotTick(110, b), s140 = SilentGateSequence.shotTick(140, b), end = SilentGateSequence.endTick(b);
        Vec3 a = frame.anchor(), f = Vec3.directionFromRotation(0, frame.yaw()), r = f.cross(UP);
        if (c < s12) return buildShot(frame, SilentGateSequence.BUILD_TICKS);
        if (c < s50) {
            double t = smooth(c, s12, s50);
            return look(a.add(f.scale(-11 + 3 * t)).add(r.scale(7 - 4 * t)).add(0, 1.8 + .8 * t, 0), a.add(f.scale(22)).add(0, 26 + 8 * t, 0));
        }
        if (c < s80) return closeUp(a, f, shots.isEmpty() ? a.add(f.scale(7)) : shots.get(shots.size() - 1).hit(), 0, smooth(c, s50, s80));
        if (c < s110) {
            double t = smooth(c, s80, s110);
            // Above the great circle (anchor + 100) so no circle geometry sits around the lens.
            Vec3 center = a.add(f.scale(32));
            return look(center.add(f.scale(-5)).add(0, 125 + 15 * t, 0), center);
        }
        if (c < s140) return closeUp(a, f, shots.isEmpty() ? a.add(r.scale(8)).add(f.scale(10)) : shots.get(0).hit(), 1.9, smooth(c, s110, s140));
        double t = smooth(c, s140, end);
        return look(a.add(f.scale(-9 - 3 * t)).add(r.scale(1.5)).add(0, 3.2 + 1.5 * t, 0), a.add(f.scale(30)).add(0, 35, 0));
    }

    /** Close view of a hit from the caster's side, optionally swung round the target by {@code swing} radians. */
    private static AtomicCinematic.Pose closeUp(Vec3 caster, Vec3 forward, Vec3 hit, double swing, double t) {
        Vec3 toward = new Vec3(caster.x - hit.x, 0, caster.z - hit.z);
        toward = toward.lengthSqr() < 1 ? forward.scale(-1) : toward.normalize();
        Vec3 side = toward.cross(UP);
        Vec3 offset = toward.scale(13 - 4 * t).add(side.scale(3)).add(0, 4.5 - 1.5 * t, 0);
        if (swing != 0) offset = offset.yRot((float)swing);
        return look(hit.add(offset), hit.add(0, 1, 0));
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
