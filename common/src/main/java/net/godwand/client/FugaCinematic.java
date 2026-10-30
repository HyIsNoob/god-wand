package net.godwand.client;

import net.godwand.config.GodWandConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Frame-local camera ownership; never changes the player's facing or perspective preference. */
public final class FugaCinematic {
    private static long cast = Long.MIN_VALUE;
    private static boolean skipped;
    private static double renderedFov = 70;
    private FugaCinematic() {}

    private static boolean present(Minecraft client, FugaRenderer.Frame frame) {
        return frame != null && client.player != null && client.level != null
                && client.player.isAlive() && !client.player.isSpectator()
                && (frame.phase() == 0 || frame.phase() == 1 || frame.phase() == 2 && frame.age() < 160);
    }
    public static boolean openingFaceVisible(FugaRenderer.Frame frame) {return frame!=null&&frame.phase()==0&&frame.age()<36;}
    public static boolean showCaster(float partial) {return openingFaceVisible(FugaRenderer.localFrame(partial));}
    public static boolean active() {
        var client = Minecraft.getInstance();
        var frame = FugaRenderer.localFrame(0);
        return present(client, frame) && client.screen == null && client.isWindowActive()
                && (frame.phase() == 0 || GodWandConfig.CLIENT.fugaCutscene && !skipped);
    }
    public static boolean movementLocked() {
        var client = Minecraft.getInstance();
        var frame = FugaRenderer.localFrame(0);
        return present(client, frame) && (frame.phase() == 0 || GodWandConfig.CLIENT.fugaCutscene && !skipped);
    }
    public static void tick(Minecraft client) {
        var frame = FugaRenderer.localFrame(0);
        if (!present(client, frame)) { reset(); return; }
        if (cast != frame.startedAt()) { cast = frame.startedAt(); skipped = false; }
        // Opening a menu only suspends shots; it must not mark the next cast as skipped.
        if (client.screen != null || !client.isWindowActive()) return;
        long window = client.getWindow().getWindow();
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F5) == GLFW.GLFW_PRESS) skip();
    }
    public static void skip() { skipped = true; }
    public static void reset() { cast = Long.MIN_VALUE; skipped = false; renderedFov = 70; }
    public static AtomicCinematic.Pose camera(float delta, Vec3 vanilla, float yaw, float pitch) {
        if (!active()) return null;
        var client = Minecraft.getInstance();
        var frame = FugaRenderer.localFrame(delta);
        var shot = cameraFor(frame, vanilla, yaw, pitch, client.player.getPosition(delta),
                client.player.getViewYRot(delta), client.player.getViewXRot(delta),
                GodWandConfig.CLIENT.fugaCutscene && !skipped);
        // Never move a detached shot into an unloaded chunk. Keep the shoulder/vanilla fallback local.
        if (shot != null && !client.level.getChunkSource().hasChunk(Mth.floor(shot.position().x) >> 4, Mth.floor(shot.position().z) >> 4))
            return frame.phase() == 0 ? shoulder(client.player.getPosition(delta), client.player.getViewYRot(delta), client.player.getViewXRot(delta)) : null;
        if (shot != null && frame.phase() == 0 && (!GodWandConfig.CLIENT.fugaCutscene || skipped || frame.age() >= 120)) {
            Vec3 eye = client.player.getEyePosition(delta);
            var hit = client.level.clip(new net.minecraft.world.level.ClipContext(eye, shot.position(),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, client.player));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                Vec3 safe = hit.getLocation().add(eye.subtract(hit.getLocation()).normalize().scale(.15));
                shot = look(safe, eye.add(client.player.getViewVector(delta).scale(80)));
            }
        }
        return shot;
    }

    public static AtomicCinematic.Pose cameraFor(FugaRenderer.Frame frame, Vec3 vanilla, float vanillaYaw,
            float vanillaPitch, Vec3 player, float yaw, float pitch, boolean director) {
        if (frame == null || frame.phase() > 2) return null;
        double age = Math.max(0, frame.age());
        Vec3 forward = Vec3.directionFromRotation(0, yaw), right = forward.cross(new Vec3(0, 1, 0));
        var aim = shoulder(player, yaw, pitch);
        if (frame.phase() == 0) {
            if (!director || age >= 120) return aim;
            Vec3 focus = player.add(0, 1.52, 0);
            Vec3 face = player.add(forward.scale(.8 + age * .003)).add(right.scale(.12)).add(0, 1.62, 0);
            Vec3 hands = player.add(forward.scale(1.3)).add(right.scale(-.35)).add(0, 1.2, 0);
            Vec3 body = player.add(forward.scale(2.9)).add(right.scale(1.1)).add(0, 1.55, 0);
            Vec3 position;
            if (age < 28) position = face;
            else if (age < 56) { position = face.lerp(hands, smooth(age, 28, 56)); focus = focus.lerp(player.add(forward.scale(.5)).add(0, 1.15, 0), smooth(age, 28, 56)); }
            else { position = hands.lerp(body, smooth(age, 56, 100)); focus = player.add(forward.scale(.5)).add(0, 1.2, 0); }
            var intro = look(position, focus);
            return blend(intro, aim, smooth(age, 100, 120));
        }
        if (!director) return null;
        if (frame.phase() == 1) {
            // A point-blank arrow has no useful chase distance; begin the upward reveal locally.
            boolean shortFlight = frame.origin().distanceTo(frame.target()) < 12;
            Vec3 arrow = frame.arrowPosition();
            Vec3 direction = frame.target().subtract(frame.origin()).normalize();
            Vec3 position = shortFlight ? player.add(0, 2.6 + age * .15, 0).subtract(forward.scale(3))
                    : arrow.subtract(direction.scale(6)).add(right.scale(2)).add(0, 2 + age * .12, 0);
            return blend(aim, look(position, arrow.add(direction.scale(4))), smooth(age, 0, 5));
        }
        if (age >= 160) return null;
        Vec3 center = frame.center();
        double angle = age * .009;
        Vec3 overhead = new Vec3(Math.sin(angle) * 30, 62 - age * .22, Math.cos(angle) * 30);
        Vec3 underneath = new Vec3(Math.sin(angle) * 29, 4 + age * .08, Math.cos(angle) * 29);
        Vec3 far = new Vec3(Math.sin(angle) * 150, 125 + age * .1, Math.cos(angle) * 150);
        Vec3 crater = new Vec3(Math.sin(angle) * 22, 12 + Math.sin(age * .04) * 2, Math.cos(angle) * 22);
        Vec3 offset = age < 20 ? overhead.lerp(underneath, smooth(age, 15, 20))
                : age < 40 ? underneath.lerp(far, smooth(age, 34, 40))
                : age < 110 ? far.lerp(crater, smooth(age, 100, 110)) : crater;
        double sphereY = 12 + 38 * (1 - smooth(age, 0, 40));
        double focusY = age < 40 ? sphereY : (12 + 153 * smooth(age, 40, 58)) * (1 - smooth(age, 90, 110));
        var impact = look(center.add(offset), center.add(0, focusY, 0));
        return blend(impact, new AtomicCinematic.Pose(vanilla, vanillaYaw, vanillaPitch), smooth(age, 140, 160));
    }
    private static AtomicCinematic.Pose shoulder(Vec3 player, float yaw, float pitch) {
        Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
        Vec3 right = Vec3.directionFromRotation(0, yaw).cross(new Vec3(0, 1, 0));
        Vec3 eye = player.add(0, 1.62, 0);
        return look(eye.subtract(forward.scale(3.2)).add(right.scale(.65)).add(0, .25, 0), eye.add(forward.scale(80)));
    }
    private static AtomicCinematic.Pose look(Vec3 position, Vec3 target) {
        Vec3 d = target.subtract(position);
        return new AtomicCinematic.Pose(position, (float)Math.toDegrees(Math.atan2(-d.x, d.z)),
                (float)-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
    }
    private static AtomicCinematic.Pose blend(AtomicCinematic.Pose a, AtomicCinematic.Pose b, float t) {
        return new AtomicCinematic.Pose(a.position().lerp(b.position(), t), Mth.rotLerp(t, a.yaw(), b.yaw()), Mth.lerp(t, a.pitch(), b.pitch()));
    }
    static float smooth(double age, double start, double end) {
        float t = (float)Math.max(0, Math.min(1, (age - start) / (end - start)));
        return t * t * (3 - 2 * t);
    }
    public static float environmentGain() {
        var frame = FugaRenderer.localFrame(0);
        return active() && GodWandConfig.CLIENT.fugaCutscene && !skipped && frame.phase() == 0 && frame.age() < 36 ? .015f + .985f * smooth(frame.age(), 26, 36) : 1;
    }
    public static double fov(double vanilla, float delta) {
        var frame = FugaRenderer.localFrame(delta);
        if (!active() || !GodWandConfig.CLIENT.cameraFov || !GodWandConfig.CLIENT.fugaCutscene || skipped) return vanilla;
        if (frame.phase() == 0) return Mth.lerp(smooth(frame.age(), 90, 120), 46, vanilla);
        if (frame.phase() == 2) {
            double wide = 68 + 37 * smooth(frame.age(), 34, 46) * (1 - smooth(frame.age(), 100, 110));
            return Mth.lerp(smooth(frame.age(), 140, 160), wide, vanilla);
        }
        return vanilla;
    }
    public static float shake(float delta) {
        var frame = FugaRenderer.localFrame(delta);
        if (frame == null) return 0;
        double age = frame.age();
        float strength = frame.phase() == 1 ? (1 - smooth(age, 0, 8)) * .8f
                : frame.phase() == 2 ? (1 - smooth(age, 0, 9)) * .7f + smooth(age, 36, 40) * (1 - smooth(age, 42, 60)) * 1.8f
                + smooth(age, 40, 48) * (1 - smooth(age, 112, 140)) * .25f : 0;
        return strength * GodWandConfig.CLIENT.cameraShake;
    }
    /** Called only for the world FOV; hand rendering uses a separate lens. */
    public static void recordFov(double fov) {
        if (Double.isFinite(fov) && fov > 1 && fov < 179) renderedFov = fov;
    }
    /** Perspective projection in GUI pixels. Targets behind or outside the camera are omitted. */
    static Vec3 project(Vec3 target, Vec3 camera, float yaw, float pitch, double fov, int width, int height) {
        if (width <= 0 || height <= 0 || !Double.isFinite(fov) || fov <= 1 || fov >= 179) return null;
        Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
        Vec3 right = Vec3.directionFromRotation(0, yaw).cross(new Vec3(0, 1, 0));
        Vec3 up = right.cross(forward), relative = target.subtract(camera);
        double depth = relative.dot(forward);
        if (depth <= .01) return null;
        double scale = height / (2 * Math.tan(Math.toRadians(fov) * .5));
        double x = width * .5 + relative.dot(right) * scale / depth;
        double y = height * .5 - relative.dot(up) * scale / depth;
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 7 || x > width - 7 || y < 7 || y > height - 7) return null;
        return new Vec3(x, y, depth);
    }
    private static Vec3 aimTarget(Minecraft client, float partial) {
        Vec3 eye = client.player.getEyePosition(partial);
        Vec3 end = eye.add(client.player.getViewVector(partial).scale(160));
        var block = client.level.clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, client.player));
        Vec3 target = block.getLocation();
        double nearest = eye.distanceToSqr(target);
        var bounds = new net.minecraft.world.phys.AABB(eye, target).inflate(1);
        for (var entity : client.level.getEntities(client.player, bounds, entity -> entity.isPickable() && !entity.isSpectator())) {
            var box = entity.getBoundingBox().inflate(entity.getPickRadius());
            if (box.contains(eye)) continue;
            var hit = box.clip(eye, target);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < nearest) {
                target = hit.get(); nearest = eye.distanceToSqr(target);
            }
        }
        return target;
    }
    private static void reticle(GuiGraphics graphics, Minecraft client, float partial) {
        var camera = client.gameRenderer.getMainCamera();
        Vec3 point = project(aimTarget(client, partial), camera.getPosition(), camera.getYRot(), camera.getXRot(),
                renderedFov, graphics.guiWidth(), graphics.guiHeight());
        if (point == null) return;
        int x = (int)Math.round(point.x), y = (int)Math.round(point.y);
        graphics.fill(x - 7, y - 2, x - 3, y + 3, 0xC0200A00);
        graphics.fill(x + 3, y - 2, x + 7, y + 3, 0xC0200A00);
        graphics.fill(x - 2, y - 7, x + 3, y - 3, 0xC0200A00);
        graphics.fill(x - 2, y + 3, x + 3, y + 7, 0xC0200A00);
        graphics.fill(x - 6, y, x - 3, y + 1, 0xFFFF9A32);
        graphics.fill(x + 3, y, x + 6, y + 1, 0xFFFF9A32);
        graphics.fill(x, y - 6, x + 1, y - 3, 0xFFFF9A32);
        graphics.fill(x, y + 3, x + 1, y + 6, 0xFFFF9A32);
        graphics.fill(x - 1, y - 1, x + 2, y + 2, 0xFFFFF2D8);
    }
    public static float overlayFlash(int phase, double age) {
        return phase == 1 ? (1 - smooth(age, 0, 4)) * .55f : phase == 2 ? flash(age) * .92f : 0;
    }
    public static float flash(double impactAge) {
        return smooth(impactAge, 38, 40) * (1 - smooth(impactAge, 41, 46));
    }
    public static void render(GuiGraphics graphics, DeltaTracker timer) {
        float partial = timer.getGameTimeDeltaPartialTick(false);
        var frame = FugaRenderer.localFrame(partial);
        var client = Minecraft.getInstance();
        if (!present(client, frame) || client.screen != null || !client.isWindowActive()) return;
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        double age = frame.age();
        boolean directing = GodWandConfig.CLIENT.fugaCutscene && !skipped;
        if (frame.phase() == 0 && (age >= 120 || !directing) && GodWandConfig.CLIENT.targetMarker) reticle(graphics, client, partial);
        float envelope = !directing ? 0 : frame.phase() == 0 ? 1 - smooth(age, 100, 120)
                : frame.phase() == 2 ? 1 - smooth(age, 140, 160) : 1;
        int bars = Math.round(height * .09f * envelope);
        if (bars > 0) { graphics.fill(0, 0, width, bars, 0xFF080301); graphics.fill(0, height - bars, width, height, 0xFF080301); }
        if (directing && frame.phase() == 0 && age < 30) {
            int alpha = Math.round(255 * smooth(age, 2, 8) * (1 - smooth(age, 23, 30)));
            if (alpha > 3) {
                graphics.pose().pushPose();
                float scale = Math.min(6, width / 85f);
                graphics.pose().translate(width * .5f, height * .68f, 0);
                graphics.pose().scale(scale, scale, 1);
                graphics.drawString(Minecraft.getInstance().font, "F U G A", -Minecraft.getInstance().font.width("F U G A") / 2, 0, (alpha << 24) | 0xFFF0DC, false);
                graphics.pose().popPose();
            }
        }
        if (GodWandConfig.CLIENT.impactFrames) {
            int alpha = Math.round(255 * overlayFlash(frame.phase(), age));
            if (alpha > 0) graphics.fill(0, 0, width, height, (alpha << 24) | 0xFFF4D9);
        }
    }
}
