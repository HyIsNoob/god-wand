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
    public static boolean inVoid() { return active() && age < BlueRedRules.PURPLE_VOID_END; }
    public static void reset() { age = -1; center = null; eye = null; }

    public static AtomicCinematic.Pose camera(float delta, Vec3 vanillaPosition, float vanillaYaw, float vanillaPitch) {
        return active() ? pose(center, eye, age + delta) : null;
    }

    /** From the player's side of the sphere: drifts back as it swells, is thrown back by the blast, then settles. Never further than ~36 blocks to start. */
    static AtomicCinematic.Pose pose(Vec3 center, Vec3 eye, double age) {
        Vec3 away = new Vec3(eye.x - center.x, 0, eye.z - center.z);
        Vec3 side = away.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : away.normalize();
        if (age < BlueRedRules.PURPLE_VOID_END) {
            // In the void: a slow dolly in on the two orbs, side on, slightly above.
            double turn = age * .004, d = 12 - 3.5 * GateDraw.smooth(age / BlueRedRules.PURPLE_VOID_END);
            Vec3 s = new Vec3(side.x * Math.cos(turn) - side.z * Math.sin(turn), 0, side.x * Math.sin(turn) + side.z * Math.cos(turn));
            return aim(center.add(s.scale(d)).add(0, .6, 0), center);
        }
        double turn = age * .006;
        side = new Vec3(side.x * Math.cos(turn) - side.z * Math.sin(turn), 0, side.x * Math.sin(turn) + side.z * Math.cos(turn));
        double start = Math.max(14, Math.min(36, eye.distanceTo(center)));
        double swell = GateDraw.smooth(age / BlueRedRules.PURPLE_DETONATE) * 6;
        double thrown = GateDraw.smooth((age - BlueRedRules.PURPLE_DETONATE) / 16) * 14;
        double drift = Math.max(0, age - BlueRedRules.PURPLE_DETONATE - 16) * .08;
        // Low and looking up while it swells (it reads huge), thrown back and up by the blast to show the dome and the lightning.
        Vec3 cam = center.add(side.scale(start * .8 + swell + thrown + drift)).add(0, -1.5 + age * .03 + thrown * .6, 0);
        return aim(cam, center);
    }

    private static AtomicCinematic.Pose aim(Vec3 cam, Vec3 target) {
        Vec3 look = target.subtract(cam);
        float yaw = (float) Math.toDegrees(Math.atan2(look.z, look.x)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        return new AtomicCinematic.Pose(cam, yaw, pitch);
    }
}
