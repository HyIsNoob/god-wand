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
    public static boolean inVoid() { return active() && age < BlueRedRules.PURPLE_VOID_END + 12; }
    /** Fully black: the world Purple is not drawn yet (it shows under the black once that starts thinning). */
    public static boolean blackout() { return active() && age < BlueRedRules.PURPLE_VOID_END; }
    public static void reset() { age = -1; center = null; eye = null; }

    public static AtomicCinematic.Pose camera(float delta, Vec3 vanillaPosition, float vanillaYaw, float vanillaPitch) {
        return active() ? pose(center, eye, age + delta) : null;
    }

    /** One unbroken camera move (owner, 2026-11-16: no hard cuts): out of the player's own eyes, round to the side of the two orbs and slowly in on them
     *  while the world goes black, then back and down as Purple swells (low, looking up), thrown back and up by the blast, settling. */
    static AtomicCinematic.Pose pose(Vec3 center, Vec3 eye, double age) {
        Vec3 away = new Vec3(eye.x - center.x, 0, eye.z - center.z);
        Vec3 side = away.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : away.normalize();
        double turn = age * .004;
        side = new Vec3(side.x * Math.cos(turn) - side.z * Math.sin(turn), 0, side.x * Math.sin(turn) + side.z * Math.cos(turn));
        int v = BlueRedRules.PURPLE_VOID_END, det = BlueRedRules.PURPLE_DETONATE;
        double d = age < v ? 10 - 1.5 * GateDraw.smooth(age / v) : 8.5 + 21.5 * GateDraw.smooth((age - v) / (det - v));
        double thrown = GateDraw.smooth((age - det) / 16);
        d += thrown * 14 + Math.max(0, age - det - 16) * .08;
        double h = (age < v ? .6 : .6 - 1.6 * GateDraw.smooth((age - v) / 30) + Math.max(0, age - v - 30) * .03) + thrown * 8;
        Vec3 cam = center.add(side.scale(d)).add(0, h, 0);
        if (age < 16) {
            Vec3 from = eye.distanceTo(center) > 40 ? center.add(eye.subtract(center).normalize().scale(40)) : eye;
            cam = from.add(cam.subtract(from).scale(GateDraw.smooth(age / 16)));
        }
        return aim(cam, center);
    }

    private static AtomicCinematic.Pose aim(Vec3 cam, Vec3 target) {
        Vec3 look = target.subtract(cam);
        float yaw = (float) Math.toDegrees(Math.atan2(look.z, look.x)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        return new AtomicCinematic.Pose(cam, yaw, pitch);
    }
}
