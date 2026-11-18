package net.godwand.client;

import net.godwand.network.BlueRedPayload;
import net.godwand.skill.BlueRedRules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Purple's cutscene camera, for its two players only (Blue owner and Red shooter); everyone else just sees the blast. */
public final class PurpleCinematic {
    private static Vec3 center, eye;
    private static double age = -1;

    private PurpleCinematic() {}

    public static void receive(BlueRedPayload p) {
        var player = Minecraft.getInstance().player;
        if (player != null) start(p.ownerId(), p.otherId(), player.getId(), new Vec3(p.x(), p.y(), p.z()), player.getEyePosition());
    }

    static void start(int ownerId, int shooterId, int localId, Vec3 at, Vec3 localEye) {
        if (localId != ownerId && localId != shooterId) return;
        center = at; eye = localEye; age = 0;
    }

    static void tick() {
        if (age < 0) return;
        if (++age >= BlueRedRules.PURPLE_CINEMATIC) reset();
    }

    public static void tick(Minecraft client) { if (!client.isPaused()) tick(); }

    public static boolean active() { return age >= 0 && age < BlueRedRules.PURPLE_CINEMATIC; }
    public static boolean movementLocked() { return active(); }
    /** The two orbs meet against black (owner, 2026-11-16) before the cut back to the world. */
    public static boolean inVoid() { return active() && age >= BlueRedRules.PURPLE_SWIRL_END && age < BlueRedRules.PURPLE_VOID_END; }
    /** Fully black: the world Purple is not drawn yet (it shows under the black once that starts thinning). */
    public static boolean blackout() { return active() && age >= BlueRedRules.PURPLE_SWIRL_END + 12 && age < BlueRedRules.PURPLE_MERGE; }
    static double age() { return age; }
    public static void reset() { age = -1; center = null; eye = null; }

    public static AtomicCinematic.Pose camera(float delta, Vec3 vanillaPosition, float vanillaYaw, float vanillaPitch) {
        if (!active()) return null;
        AtomicCinematic.Pose p = pose(center, eye, age + delta);
        Vec3 orbs = center.add(0, BlueRedRules.purpleRise(age + delta), 0);
        return aim(unblocked(orbs, p.position()), orbs);
    }

    /** Owner's direction (2026-11-16): the player's own view for the blink, a cut to a close shot of the orbs swirling in the world, held still behind
     *  the 2D void, a cut back to a fast move round Purple as it swells, thrown far back by the blast. */
    static AtomicCinematic.Pose pose(Vec3 center, Vec3 eye, double age) {
        // The camera follows the orbs as they soar to the burst point (center is the burst point, up in the sky).
        center = center.add(0, BlueRedRules.purpleRise(age), 0);
        Vec3 away = new Vec3(eye.x - center.x, 0, eye.z - center.z);
        Vec3 side = away.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : away.normalize();
        int blink = BlueRedRules.PURPLE_BLINK, swirl = BlueRedRules.PURPLE_SWIRL_END, merge = BlueRedRules.PURPLE_MERGE, det = BlueRedRules.PURPLE_DETONATE;
        if (age < blink) return aim(eye, center);
        if (age < merge) {
            // Owner, 2026-11-18: a slow cinematic arc, never turning with the orbs. It starts close and a little below, eases back as they speed
            // up, and lands square-on to the face-off line (Red on the left, Blue on the right, ready for the 2D act), then creeps in for the hold.
            int face = BlueRedRules.PURPLE_FACE;
            double a0 = Math.atan2(turn(side, .5).z, turn(side, .5).x);
            // up x axis: from there +axis (Red) is on screen-left, as in the 2D act.
            Vec3 axis = BlueRedDraw.faceAxis(), end = new Vec3(axis.z, 0, -axis.x);
            double a1 = Math.atan2(end.z, end.x), da = Math.IEEEremainder(a1 - a0, Math.PI * 2);
            double k = GateDraw.smooth((age - blink) / (double) (face - blink)), hold = GateDraw.smooth((age - face) / (double) (swirl - face));
            double a = a0 + da * k, dist = 7 + 1.5 * GateDraw.smooth((age - BlueRedRules.PURPLE_PLAY) / 30.0)
                    - 3.2 * GateDraw.smooth((age - BlueRedRules.PURPLE_LIFT_END) / (double) (swirl + 8 - BlueRedRules.PURPLE_LIFT_END));
            // Rising from a little below to a little above, so the level face-off orbit is seen as a ring, not edge-on.
            return aim(center.add(new Vec3(Math.cos(a), 0, Math.sin(a)).scale(dist)).add(0, -.8 + 3.3 * k, 0), center);
        }
        // Racing round it, low and close, then thrown far back and up by the blast.
        double run = age - merge, thrown = GateDraw.smooth((age - det) / 18);
        double ang = 1.2 + run * .055 - Math.max(0, age - det) * .045;
        double d = 22 - 4 * GateDraw.smooth(run / (det - merge)) + thrown * 30 + Math.max(0, age - det - 18) * .1;
        // Kept above the ground the sphere sits on (it is centred about eye height): never under the grass.
        double h = 3 + 2.5 * Math.sin(run * .05) + thrown * 14;
        return aim(center.add(turn(side, ang).scale(d)).add(0, h, 0), center);
    }

    /** Never inside a wall: if blocks stand between the sphere and the camera, the camera stops just in front of them. */
    private static Vec3 unblocked(Vec3 target, Vec3 cam) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return cam;
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(target, cam, net.minecraft.world.level.ClipContext.Block.VISUAL,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return cam;
        Vec3 back = cam.subtract(target);
        return hit.getLocation().subtract(back.normalize().scale(.6));
    }

    private static Vec3 turn(Vec3 v, double a) {
        return new Vec3(v.x * Math.cos(a) - v.z * Math.sin(a), 0, v.x * Math.sin(a) + v.z * Math.cos(a));
    }

    private static AtomicCinematic.Pose aim(Vec3 cam, Vec3 target) {
        Vec3 look = target.subtract(cam);
        float yaw = (float) Math.toDegrees(Math.atan2(look.z, look.x)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        return new AtomicCinematic.Pose(cam, yaw, pitch);
    }
}
