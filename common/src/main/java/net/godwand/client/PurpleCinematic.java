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
        // Owner, round 39: the camera stays on the player's side; the orbit is turned so Red opens between it and Blue (a little off the line,
        // so Blue still shows behind).
        Vec3 away = new Vec3(localEye.x - at.x, 0, localEye.z - at.z);
        Vec3 cam = turn(away.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : away.normalize(), .5);
        BlueRedDraw.orbitPhase = 0;
        Vec3 r0 = BlueRedDraw.convergeOffsets(BlueRedRules.PURPLE_CONTACT)[1];
        BlueRedDraw.orbitPhase = Math.atan2(cam.z, cam.x) + .35 - Math.atan2(r0.z, r0.x);
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
        // A strike shot frames the strike on the ground, not the sphere.
        if (strikeShot(age + delta) >= 0) return p;
        Vec3 orbs = center.add(0, BlueRedRules.purpleRise(age + delta), 0);
        // The swirl is shot through blocks freely (owner, round 38).
        if (age + delta < BlueRedRules.PURPLE_MERGE) return p;
        // The last second hands the view back to the player, smoothly.
        double back = GateDraw.smooth((age + delta - (BlueRedRules.PURPLE_CINEMATIC - 20)) / 20.0);
        if (back > 0) {
            AtomicCinematic.Pose q = aim(unblocked(orbs, p.position()), orbs);
            float yaw = q.yaw() + (float) (Math.IEEEremainder(vanillaYaw - q.yaw(), 360) * back);
            return new AtomicCinematic.Pose(q.position().add(vanillaPosition.subtract(q.position()).scale(back)), yaw, q.pitch() + (vanillaPitch - q.pitch()) * (float) back);
        }
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
            // Owner, 2026-11-18: from low and close, rising and pulling back; then, while they still spin, it closes in until the orbs are about
            // as big on screen as in the 2D act, square-on (Red left, Blue right), so the cut to 2D lands on the same picture.
            int le = BlueRedRules.PURPLE_LIFT_END;
            double out = GateDraw.smooth((age - blink) / (double) (le - 16 - blink)), in = GateDraw.smooth((age - (le - 16)) / 18.0);
            // Owner, round 39: it never rises faster than the orbs (a touch slower), and it comes in closer while they slow and stop.
            // Starts once the coast is down to its last half-turn, so no orb sweeps across the lens on the way in.
            double stop = GateDraw.smooth((age - (le + 10)) / (double) (swirl + 6 - le - 10));
            // Round 40 (owner): starts far (Red is far out too) and comes in step by step as the two draw together.
            double near = GateDraw.smooth((age - blink) / (double) (le - blink));
            // Round 40b (owner): closer still at the stop, just before the cut to 2D.
            double a = a0 + da * k, dist = 8.5 - 4.1 * near - 2.3 * stop, h = -.7 * out;
            // While they coast, an orb swinging round to the camera's side pushes it back to keep ~2.8 blocks clear (no lens-filling glare);
            // orbs off to the sides need nothing, so the square-on close-up at the stop is untouched.
            double need = 0;
            for (Vec3 o : BlueRedDraw.convergeOffsets(age)) {
                double al = o.x * Math.cos(a) + o.z * Math.sin(a);
                need = Math.max(need, al + 2.8 * GateDraw.smooth(al / 1.2));
            }
            dist = Math.max(dist, need * GateDraw.smooth((age - (le - 4)) / 6.0));
            // Round 40 (owner): it only turns its gaze part-way towards Red (no following it bodily, so no dizziness), easing back to the middle.
            double track = 1 - GateDraw.smooth((age - BlueRedRules.PURPLE_CONTACT) / (double) (le - BlueRedRules.PURPLE_CONTACT));
            Vec3 red = BlueRedDraw.convergeOffsets(age)[1], look = center.add(red.scale(.45 * track));
            Vec3 cam = center.add(new Vec3(Math.cos(a), 0, Math.sin(a)).scale(dist)).add(0, h, 0);
            return aim(cam, look);
        }
        // Quick cuts to the ground strikes (owner, 2026-11-18): for a few ticks round a strike, a low shot next to it looking up the bolt.
        int shot = strikeShot(age);
        if (shot >= 0) {
            double[] o = BlueRedRules.strikeOffset(shot);
            Vec3 g = new Vec3(Math.floor(center.x + o[0]) + .5, center.y - BlueRedRules.PURPLE_LIFT - 1.6, Math.floor(center.z + o[1]) + .5);
            Vec3 outward = new Vec3(o[0], 0, o[1]).normalize();
            double t = age - BlueRedRules.strikeTick(shot);
            // Slow: a gentle drift in and tilt up the bolt.
            return aim(g.add(outward.scale(8 - .08 * t)).add(turn(outward, Math.PI / 2).scale(2.5)).add(0, 1.4, 0), g.add(0, 4 + .1 * t, 0));
        }
        // A far shot first (owner, 2026-11-18): the whole sky and the ground round it, drifting slowly.
        if (age < FAR_END) {
            double f = (age - merge) / (double) (FAR_END - merge);
            // Far enough to see the strikes tearing up the ground round it.
            return aim(center.add(turn(side, .6 + .2 * f).scale(48 - 6 * f)).add(0, 6, 0), center);
        }
        // Bolder (owner, 2026-11-18): a low hero shot looking up at it as it is born, a sweep round it rising to its level, a fast push in
        // just before the burst, then thrown far back and up by the blast.
        // Round 37 (owner): close, slowly pulling out and round it; the blast throws it farther still.
        // Round 45 (owner): one continuous move. It starts low and close by the player, slides off to one side and climbs as Purple grows,
        // creeps in while it holds its breath, then the blast hurls it far out and up, and it keeps drifting out over the violet land.
        double run = age - merge, span = det - merge, thrown = GateDraw.smooth((age - det) / 30.0);
        double climb = GateDraw.smooth(run / span), crush = GateDraw.smooth((age - (det - 18)) / 16.0) * (1 - thrown);
        double after = Math.max(0, age - det - 30);
        double ang = .1 + 1.1 * climb + after * .003;
        double near = Math.max(8, Math.sqrt((eye.x - center.x) * (eye.x - center.x) + (eye.z - center.z) * (eye.z - center.z)));
        // Round 46 (owner): it climbs well above Purple so the charged ground and the chunks lifting off it show below, and the blast only
        // pushes it a short way out (it was thrown too far).
        double d = (near + 6 * climb) * (1 - .2 * crush) + thrown * 35 + after * .12;
        double h = (eye.y - center.y + 2) * (1 - climb) + 16 * climb + thrown * 20 + after * .08;
        return aim(center.add(turn(side, ang).scale(d)).add(0, h, 0), center);
    }

    /** The strikes the camera cuts to, and how long each shot holds round its strike (from 2 ticks before to 8 after). */
    static final int[] STRIKE_SHOTS = {};
    /** The far shot holds from MERGE to here. */
    static final int FAR_END = BlueRedRules.PURPLE_MERGE;

    /** The strike being shown at this age, or -1. */
    static int strikeShot(double age) {
        for (int k : STRIKE_SHOTS) { double t = age - BlueRedRules.strikeTick(k); if (t >= -4 && t < 16) return k; }
        return -1;
    }

    /** Is a cut to or from a strike shot near this age? */
    static boolean nearStrikeCut(double age) {
        for (int k : STRIKE_SHOTS) { double t = age - BlueRedRules.strikeTick(k); if (Math.abs(t + 4) < .6 || Math.abs(t - 16) < .6) return true; }
        return false;
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
