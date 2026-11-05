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
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static double renderedFov = 70;
    private FugaCinematic() {}

    private static boolean present(Minecraft client, FugaRenderer.Frame frame) {
        boolean valid = frame != null && client.player != null && client.level != null
                && client.player.isAlive() && !client.player.isSpectator()
                && (frame.phase() == 0 || frame.phase() == 1 || frame.phase() == 2 && frame.age() < net.godwand.skill.FugaSequence.END_TICK);
        // Render hooks can see a new cast before the next client tick clears the previous skip.
        if (valid && (cast != frame.startedAt() || world != client.level)) { cast = frame.startedAt(); world = client.level; skipped = false; }
        return valid;
    }
    public static boolean openingFaceVisible(FugaRenderer.Frame frame) {return frame!=null&&frame.phase()==0&&frame.age()<48;}
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
    /** Facing is frozen through the directed build-up; it returns for the hold so the player can aim. */
    public static boolean rotationLocked() {
        var frame = FugaRenderer.localFrame(0);
        return movementLocked() && frame.phase() == 0 && frame.age() < 120 && GodWandConfig.CLIENT.fugaCutscene && !skipped;
    }
    public static void tick(Minecraft client) {
        var frame = FugaRenderer.localFrame(0);
        if (!present(client, frame)) { reset(); return; }
        // Opening a menu only suspends shots; it must not mark the next cast as skipped.
        if (client.screen != null || !client.isWindowActive()) return;
        long window = client.getWindow().getWindow();
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F5) == GLFW.GLFW_PRESS) skip();
    }
    public static void skip() { skipped = true; }
    public static void reset() { cast = Long.MIN_VALUE; world = null; skipped = false; renderedFov = 70; }
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
        if (shot != null && frame.phase()==0 && frame.age()>=24 && frame.age()<144) {
            Vec3 subject=client.player.getPosition(delta).add(0,1.3,0);
            var hit=client.level.clip(new net.minecraft.world.level.ClipContext(subject,shot.position(),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,client.player));
            if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS) {
                Vec3 safe=hit.getLocation().add(subject.subtract(hit.getLocation()).normalize().scale(.15));
                if(safe.distanceTo(subject)<.65) return shoulder(client.player.getPosition(delta),client.player.getViewYRot(delta),client.player.getViewXRot(delta));
                shot=look(safe,subject);
            }
        }
        if (shot != null && frame.phase() == 0 && (!GodWandConfig.CLIENT.fugaCutscene || skipped || frame.age() >= net.godwand.skill.FugaSequence.CHARGE_TICKS)) {
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
        if(frame.phase()==2)age=net.godwand.skill.FugaSequence.visualAge(age);
        if(frame.phase()==0)age=net.godwand.skill.FugaSequence.chargeAge(age);
        Vec3 forward = Vec3.directionFromRotation(0, yaw), right = forward.cross(new Vec3(0, 1, 0));
        var aim = shoulder(player, yaw, pitch);
        if (frame.phase() == 0) {
            if (!director || frame.age() >= net.godwand.skill.FugaSequence.CHARGE_TICKS) return aim;
            return chargeCamera(frame.age(), player, forward, right, pitch, aim,frame.casterId());
        }
        if (!director) return null;
        // The shot is watched from the shoulder; the camera cuts to the sphere once it forms.
        if (frame.phase() == 1) {
            // The shot leaves the shoulder, then the camera swings behind the arrow and chases it as it accelerates.
            Vec3 d = frame.target().subtract(frame.origin());
            d = d.lengthSqr() < 1e-6 ? forward : d.normalize();
            Vec3 side = d.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1e-6 ? right : side.normalize();
            Vec3 arrow = frame.arrowPosition();
            var chase = look(arrow.subtract(d.scale(16)).add(side.scale(3)).add(0, 3.5, 0), arrow.add(d.scale(10)));
            double stop = FugaRenderer.hitStop(frame.casterId(), 0);
            if (stop >= 0) {
                // The arrow hangs in its impact: the camera rises with the beam and tilts up to follow its head.
                double rise = smooth(stop, .08, 1), top = 6 + 110 * smooth(stop, 0, 1);
                Vec3 position = chase.position().add(0, 14 * rise, 0).subtract(d.scale(6 * rise));
                return look(position, arrow.lerp(arrow.add(d.scale(10)), 1 - rise).add(0, top * .8 * rise, 0));
            }
            return blend(aim, chase, smooth(age, 2, 10));
        }
        if (age >= 160) return null;
        Vec3 center = frame.center();
        // Framed from the caster's side of the impact, so the shot reads as "what I just fired".
        Vec3 away = new Vec3(player.x - center.x, 0, player.z - center.z);
        away = away.lengthSqr() < 1 ? forward.scale(-1) : away.normalize();
        Vec3 across = away.cross(new Vec3(0, 1, 0));
        AtomicCinematic.Pose impact;
        if (age < 40) {
            // Just off-centre under the falling mass looking up, as if it is coming down on your face; it only eases back a little
            // and tilts slightly down as the mass reaches the ground.
            // Starts up close to the full-size mass looking up at it, drops fast to the ground and backs off more slowly,
            // always aimed low at the sphere's lower rim.
            Vec3 position = center.add(away.scale(spline(age, IMPACT_KEYS, IMPACT_OUT))).add(across.scale(spline(age, IMPACT_KEYS, IMPACT_SIDE)))
                    .add(0, 2.2 + 14 * (1 - smooth(age, 0, 12)), 0);
            impact = look(position, center.add(0, FugaRenderer.orbGap(age) + 3, 0));
        } else if (age < PILLAR_CUT) {
            // Pillar, shot 1: low on the ground at its foot looking up, backing away without rising.
            double k = smooth(age, 40, PILLAR_CUT);
            Vec3 position = center.add(away.scale(72 + 70 * k)).add(across.scale(6)).add(0, 2.2, 0);
            impact = look(position, center.add(0, 150 - 40 * k, 0));
        } else if (age < END_CUT) {
            // Pillar, shot 2 (hard cut): a far drone shot high above, looking down and circling the column.
            double orbit = 1.6 * smooth(age, PILLAR_CUT, 160);
            Vec3 around = away.scale(Math.cos(.6 + orbit)).add(across.scale(Math.sin(.6 + orbit)));
            impact = look(center.add(around.scale(150)).add(0, 95, 0), center.add(0, 35, 0));
        } else {
            // Closing shot (hard cut): out near the pillar on the caster's side, looking at it, then slowly backing
            // straight into the caster's own eyes (never past them, so their model never shows) and becoming their view.
            Vec3 eye = player.add(0, 1.62, 0), column = center.add(0, 55, 0);
            Vec3 toward = new Vec3(column.x - eye.x, 0, column.z - eye.z);
            double gap = toward.length();
            toward = gap < 1 ? forward : toward.scale(1 / gap);
            Vec3 near = eye.add(toward.scale(Math.min(gap * .55, 70))).add(0, 4, 0);
            // Arrives at the eyes a little early and turns into the player's own facing over a long stretch, then
            // holds their exact view for the last beat, so the hand-back has no hitch.
            Vec3 position = near.lerp(vanilla, smooth(age, END_CUT, 154));
            impact = blend(look(position, column), new AtomicCinematic.Pose(vanilla, vanillaYaw, vanillaPitch), smooth(age, 138, 156));
        }
        return impact;
    }
    /** Visual age of the hard cut back to the caster for the closing pull-back. */
    static final double END_CUT = 120;
    private static AtomicCinematic.Pose blend(AtomicCinematic.Pose a, AtomicCinematic.Pose b, float t) {
        return new AtomicCinematic.Pose(a.position().lerp(b.position(), t), Mth.rotLerp(t, a.yaw(), b.yaw()), Mth.lerp(t, a.pitch(), b.pitch()));
    }
    /** Visual age of the hard cut from the pillar's ground shot to the drone shot. */
    static final double PILLAR_CUT = 90;
    // Energy mass only (visual 0-40): low, right under it looking up, then backing away as it comes down.
    // Later keys only keep the spline's ends smooth.
    private static final double[] IMPACT_KEYS = {0, 30, 37, 41, 52, 100, 122, 140, 160};
    private static final double[] IMPACT_OUT = {20, 40, 46, 48, 48, 48, 48, 48, 48};
    private static final double[] IMPACT_SIDE = {4, 4, 4, 4, 4, 4, 4, 4, 4};
    private static double spline(double age, double[] keys, double[] values) {
        int i = 0, last = keys.length - 1;
        while (i < last - 1 && age > keys[i + 1]) i++;
        double span = keys[i + 1] - keys[i], t = Math.max(0, Math.min(1, (age - keys[i]) / span));
        double m0 = i == 0 ? 0 : (values[i + 1] - values[i - 1]) / (keys[i + 1] - keys[i - 1]);
        double m1 = i + 1 == last ? 0 : (values[i + 2] - values[i]) / (keys[i + 2] - keys[i]);
        return (2*t*t*t - 3*t*t + 1) * values[i] + (t*t*t - 2*t*t + t) * span * m0
                + (-2*t*t*t + 3*t*t) * values[i + 1] + (t*t*t - t*t) * span * m1;
    }
    // Face; then straight on the hands, easing back through the pull; the clap snaps in close; during the black
    // beat it jumps back for the vortex; then the draw and the shoulder.
    private static final double[] CHARGE_KEYS = {0,24,48,90,96,104,128,144,172,200};
    private static final double[] ORBIT = {-.32,0,0,0,0,0,0,.82,2.25,2.94};
    // The FUGA shot opens close on the face and keeps easing back through the title.
    private static final double[] DISTANCE = {.95,1.05,1.1,2.4,1.2,3.6,4.0,4.4,3.9,3.26};
    private static final double[] HEIGHT = {1.74,1.68,1.38,1.5,1.45,1.6,1.8,1.95,2.1,1.87};

    private static AtomicCinematic.Pose chargeCamera(double age, Vec3 player, Vec3 forward, Vec3 right, float pitch, AtomicCinematic.Pose aim,int casterId) {
        Vec3 endpoint = aim.position().subtract(player);
        double angle = sample(age, ORBIT, Math.atan2(endpoint.dot(right), endpoint.dot(forward)));
        double distance = sample(age, DISTANCE, endpoint.horizontalDistance());
        if(age>=24)angle=Math.atan2(endpoint.dot(right),endpoint.dot(forward))*smooth(age,144,200);
        double height=sample(age,HEIGHT,endpoint.y);
        Vec3 position = player.add(forward.scale(Math.cos(angle) * distance))
                .add(right.scale(Math.sin(angle) * distance)).add(0,height,0);
        Vec3 hands=FugaPose.handFocus(age,(float)Math.toRadians(pitch),FugaAimRig.availableFor.test(casterId));
        Vec3 subject=player.add(right.scale(hands.x)).add(forward.scale(hands.z)).add(0,hands.y,0);
        Vec3 focus=age<24 ? player.add(0,1.57,0) : subject;
        focus=focus.lerp(player.add(forward.scale(.45)).add(0,1.42,0),smooth(age,144,164));
        var portrait = look(position,focus);
        float handoff = smooth(age,188,200);
        return new AtomicCinematic.Pose(position,Mth.rotLerp(handoff,portrait.yaw(),aim.yaw()),
                Mth.lerp(handoff,portrait.pitch(),aim.pitch()));
    }
    // Nonuniform Hermite tangents keep camera position/velocity continuous between authored beats.
    private static double sample(double age, double[] values, double endpoint) {
        int i=0, last=CHARGE_KEYS.length-1;
        while(i<last-1 && age>CHARGE_KEYS[i+1])i++;
        double span=CHARGE_KEYS[i+1]-CHARGE_KEYS[i];
        double t=Math.max(0,Math.min(1,(age-CHARGE_KEYS[i])/span));
        double a=values[i],b=i+1==last?endpoint:values[i+1];
        double before=i==0?a:values[i-1],after=i+2>=last?endpoint:values[i+2];
        double m0=i==0?(b-a)/span:(b-before)/(CHARGE_KEYS[i+1]-CHARGE_KEYS[i-1]);
        double m1=i+1==last?0:(after-a)/(CHARGE_KEYS[i+2]-CHARGE_KEYS[i]);
        return (2*t*t*t-3*t*t+1)*a+(t*t*t-2*t*t+t)*span*m0
                +(-2*t*t*t+3*t*t)*b+(t*t*t-t*t)*span*m1;
    }
    private static AtomicCinematic.Pose shoulder(Vec3 player, float yaw, float pitch) {
        Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
        Vec3 right = Vec3.directionFromRotation(0, yaw).cross(new Vec3(0, 1, 0));
        Vec3 eye = player.add(0, 1.62, 0);
        // Behind, above and clearly to the left: the head no longer hides the huge arrow over the bow shoulder.
        return look(eye.subtract(forward.scale(3.4)).add(right.scale(-1.15)).add(0, 1.05, 0), eye.add(forward.scale(80)));
    }
    private static AtomicCinematic.Pose look(Vec3 position, Vec3 target) {
        Vec3 d = target.subtract(position);
        return new AtomicCinematic.Pose(position, (float)Math.toDegrees(Math.atan2(-d.x, d.z)),
                (float)-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
    }
    static float smooth(double age, double start, double end) {
        float t = (float)Math.max(0, Math.min(1, (age - start) / (end - start)));
        return t * t * (3 - 2 * t);
    }
    public static float environmentGain() {
        var frame = FugaRenderer.localFrame(0);
        return active() && GodWandConfig.CLIENT.fugaCutscene && !skipped && frame.phase() == 0 ? .015f + .985f * (1 - darkness(frame.age())) : 1;
    }
    /** 0..1 how far the world (light and sound) is taken away: black from the opening through the pull and the clap;
     *  it lifts after the silence while the vortex keeps burning, so the same fire is seen first on black, then in the world. */
    static float darkness(double age) {
        return 1 - smooth(age, 110, 140);
    }
    /** A short full-black beat right after the clap; the vortex is already burning when it lifts. */
    static float silence(double age) {
        return smooth(age, FugaPose.CLAP + 1, FugaPose.CLAP + 2.5) * (1 - smooth(age, 104, 112));
    }
    public static double fov(double vanilla, float delta) {
        var frame = FugaRenderer.localFrame(delta);
        if (!active() || !GodWandConfig.CLIENT.cameraFov || !GodWandConfig.CLIENT.fugaCutscene || skipped) return vanilla;
        if (frame.phase() == 0) return Mth.lerp(smooth(frame.age(), 140, 200), 46, vanilla);
        if (frame.phase() == 2) {
            double age=net.godwand.skill.FugaSequence.visualAge(frame.age());
            // Wide under the mass; wide to the end: no lens zoom into the pillar.
            return age < 40 ? 80 : Mth.lerp(smooth(age, 138, 156), 105, vanilla);
        }
        return vanilla;
    }
    public static float shake(float delta) {
        var frame = FugaRenderer.localFrame(delta);
        if (frame == null) return 0;
        double age = frame.phase()==2 ? net.godwand.skill.FugaSequence.visualAge(frame.age()) : frame.phase()==0 ? net.godwand.skill.FugaSequence.chargeAge(frame.age()) : frame.age();
        // Charge: a low rumble that tightens as the pressure builds.
        if (frame.phase() == 0) {
            // The clap lands as a hard jolt; the silence after it is still.
            float clap = smooth(frame.age(), FugaPose.CLAP - 1, FugaPose.CLAP) * (1 - smooth(frame.age(), FugaPose.CLAP, FugaPose.CLAP + 5));
            // Still while the ball is pulled: any jitter made the low-res bloom behind it shimmer.
            return (1.4f * clap
                    + .16f * smooth(frame.age(), 120, 150) + .14f * smooth(frame.age(), 144, 190)
                    // Held: a tight, throbbing rumble as the energy is compressed onto the arrow.
                    + .22f * smooth(frame.age(), 186, 200) * (1 + smooth(frame.age(), 190, 300)) * (.7f + .3f * (float)Math.sin(frame.age() * .9))) * GodWandConfig.CLIENT.cameraShake;
        }
        double stop = FugaRenderer.hitStop(frame.casterId(), delta);
        // The landing hits hard, then the ground keeps trembling as the beam climbs.
        if (frame.phase() == 1 && stop >= 0)
            return (2.6f * (1 - smooth(stop, 0, .25)) + .45f) * GodWandConfig.CLIENT.cameraShake;
        float strength = frame.phase() == 1 ? (1 - smooth(age, 0, 8)) * 1.6f
                // The mass arrives with a heavy jolt and a rumble that never quite settles before the descent.
                : frame.phase() == 2 ? (1 - smooth(age, 0, 10)) * 1.5f
                // The closer the mass gets to the ground, the heavier the pressure rumble.
                + (age < 40 ? (.25f + .5f * smooth(age, 8, 37)) * (.8f + .2f * (float)Math.sin(age * .8)) : 0)
                + smooth(age, 39.8, 40.4) * (1 - smooth(age, 42, 66)) * 2.4f
                // Pillar: a light steady rumble with a kick each time a pressure ring tears off (the shader's 3 rings / 10).
                + smooth(age, 40, 48) * (1 - smooth(age, 130, 160)) * (.35f + .9f * ringKick(age)) : 0;
        return strength * GodWandConfig.CLIENT.cameraShake;
    }
    /** 0..1 kick right as one of the pillar's pressure rings is born, decaying until the next. */
    static float ringKick(double age) {
        double best = 0;
        for (int i = 0; i < 3; i++) { double cycle = ((age - 40) / 10 + i / 3.0) % 1; if (cycle < 0) cycle += 1; best = Math.max(best, Math.exp(-cycle * 14)); }
        return (float)best;
    }
    /** Letterbox height as a fraction of the screen, cut like an edit: thin in the quiet beats, punched wide on every
     *  hit (clap, release, detonation), tightening as the mass nears the ground, gone while the player aims.
     *  Charge ages are raw ticks, impact ages visual. */
    static float letterbox(int phase, double age) {
        if (phase == 0) {
            float kick = age >= FugaPose.CLAP ? .06f * (float)Math.exp(-(age - FugaPose.CLAP) / 4) : 0;
            return (.12f - .04f * smooth(age, 20, 40) + .03f * smooth(age, FugaPose.CLAP, FugaPose.CLAP + 4) + kick) * (1 - smooth(age, 165, 195));
        }
        if (phase == 1) return .08f + .05f * (float)Math.exp(-age / 5);
        if (age < 40) return .07f + .05f * smooth(age, 8, 37);
        float kick = .09f * (float)Math.exp(-(age - 40) / 5);
        return (age < PILLAR_CUT ? .1f : age < END_CUT ? .07f : .05f) + kick * (1 - smooth(age, 148, 160)) - .05f * smooth(age, 148, 160);
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
        return phase == 1 ? smooth(age, 3, 3.6) * (1 - smooth(age, 3.6, 7)) * .32f : phase == 2 ? flash(age) * .92f : 0;
    }
    public static float flash(double impactAge) {
        return smooth(impactAge, 40.4, 40.8) * (1 - smooth(impactAge, 41.5, 46));
    }
    /** Vertical gradient in horizontal bands: alpha {@code top} at the top edge to {@code bottom} at {@code reach} of the height. */
    private static void tint(GuiGraphics graphics, int width, int height, int rgb, float top, float bottom, float reach) {
        int bands = 48, end = Math.round(height * reach);
        for (int i = 0; i < bands; i++) {
            int a = Math.round(255 * Mth.clamp(Mth.lerp(i / (bands - 1f), top, bottom), 0, 1));
            if (a > 0) graphics.fill(0, end * i / bands, width, end * (i + 1) / bands, (a << 24) | rgb);
        }
    }
    /** Ember grade while charging and a scorched sky after the blast; drawn under the letterbox and impact frames. */
    private static void grade(GuiGraphics graphics, Minecraft client, FugaRenderer.Frame frame, double age, float partial) {
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        // The ember grade stays off while the background is black, so black reads as black.
        float charge = frame == null ? 0 : frame.phase() == 0 ? smooth(frame.age(), 48, 110) * (.55f + .45f * smooth(frame.age(), 96, 190)) * (1 - darkness(frame.age()))
                : frame.phase() == 1 ? 1 - smooth(age, 10, 40) : 0;
        float scorch = client.player == null ? 0 : FugaRenderer.aftermath(client.player.getEyePosition(partial));
        if (charge > 0) {
            tint(graphics, width, height, 0x5A1404, .5f * charge, 0, .75f);
            graphics.fill(0, 0, width, height, (Math.round(50 * charge) << 24) | 0xFF5A10);
        }
        if (scorch > 0) {
            // Black smoke rolls over an orange sky; the whole world stays warm until it clears.
            tint(graphics, width, height, 0x140806, .4f * scorch, 0, .5f);
            tint(graphics, width, height, 0xC04A10, .35f * scorch, 0, 1);
            graphics.fill(0, 0, width, height, (Math.round(34 * scorch) << 24) | 0xFF6A1C);
        }
    }
    public static void render(GuiGraphics graphics, DeltaTracker timer) {
        float partial = timer.getGameTimeDeltaPartialTick(false);
        var frame = FugaRenderer.localFrame(partial);
        var client = Minecraft.getInstance();
        if (client.screen != null || !client.isWindowActive()) return;
        boolean here = present(client, frame);
        grade(graphics, client, here ? frame : null, !here ? 0 : frame.phase()==2 ? net.godwand.skill.FugaSequence.visualAge(frame.age()) : frame.age(), partial);
        if (!here) return;
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        double age = frame.phase()==2 ? net.godwand.skill.FugaSequence.visualAge(frame.age()) : frame.phase()==0 ? net.godwand.skill.FugaSequence.chargeAge(frame.age()) : frame.age();
        boolean directing = GodWandConfig.CLIENT.fugaCutscene && !skipped;
        if (frame.phase() == 0 && (age >= 120 || !directing) && GodWandConfig.CLIENT.targetMarker) reticle(graphics, client, partial);
        int bars = directing ? Math.round(height * letterbox(frame.phase(), frame.phase() == 0 ? frame.age() : age)) : 0;
        if (bars > 0) { graphics.fill(0, 0, width, bars, 0xFF080301); graphics.fill(0, height - bars, width, height, 0xFF080301); }
        if (directing && frame.phase() == 0) {
            int black = Math.round(255 * silence(frame.age()));
            if (black > 0) graphics.fill(0, 0, width, height, black << 24);
        }
        if (directing && frame.phase() == 0 && frame.age() < 26) {
            int alpha = Math.round(255 * smooth(frame.age(), 2, 8) * (1 - smooth(frame.age(), 18, 26)));
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
