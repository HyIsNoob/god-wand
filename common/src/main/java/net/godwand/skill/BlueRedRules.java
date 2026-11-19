package net.godwand.skill;

import net.minecraft.world.phys.Vec3;

/** Pure Blue/Red/Purple rules (mode 8), kept free of world state so they can be tested on their own. */
public final class BlueRedRules {
    public enum BlueState { CHARGING, READY, ANCHORED }
    public enum RedMeetsBlue { PURPLE, SHATTER }

    /** Blue sits this far along the caster's look; its sphere and damaging core radii. */
    public static final double BLUE_DISTANCE = 8.0, BLUE_RADIUS = 2.0, CORE_RADIUS = 3.0;
    /** Core damage lands every CORE_PERIOD ticks. */
    public static final int CORE_PERIOD = 5;
    /** Red flies RED_SPEED blocks per tick and hurts anything within RED_HIT_RADIUS of its path. */
    public static final double RED_SPEED = 3.0, RED_HIT_RADIUS = 2.0;
    /** Blue's inertia (owner, 2026-11-18): how hard it is pulled towards the aim point each tick, and how much of its speed it keeps. */
    public static final double BLUE_PULL = .22, BLUE_DAMP = .62;

    /** One tick of a held Blue chasing the aim point like a heavy weight on a spring: it lags behind turns, carries its momentum a little past
     *  the point and settles in about half a second. Returns {position, velocity}. */
    public static net.minecraft.world.phys.Vec3[] follow(net.minecraft.world.phys.Vec3 pos, net.minecraft.world.phys.Vec3 vel, net.minecraft.world.phys.Vec3 target) {
        var v = vel.scale(BLUE_DAMP).add(target.subtract(pos).scale(BLUE_PULL));
        return new net.minecraft.world.phys.Vec3[]{pos.add(v), v};
    }
    /** Purple timeline in ticks (owner's direction, 2026-11-16): a blink, the orbs swirl in the world, a quick fade to the 2D void where they are pushed
     *  together and Purple swallows them (the void thins out from MERGE to VOID_END), the camera races round Purple, it detonates, the cutscene
     *  ends, the crater stops growing. */
    public static final int PURPLE_BLINK = 4, PURPLE_SWIRL_END = 116, PURPLE_MERGE = 194, PURPLE_VOID_END = 202,
            PURPLE_DETONATE = 272, PURPLE_CINEMATIC = 384, PURPLE_TERRAIN_END = 404;
    /** The convergence in the world (owner, 2026-11-18), about 5 seconds: contact until CONTACT; an orbit until PLAY; the build-up until LIFT_END, the
     *  spin climbing hard while the pair lifts; braking and parting to opposite sides until FACE; the face-off held until SWIRL_END, when the 2D
     *  act fades in. */
    public static final int PURPLE_CONTACT = 12, PURPLE_PLAY = 40, PURPLE_LIFT_END = 90, PURPLE_FACE = 112;
    /** Purple's lightning strikes on the ground (owner, 2026-11-18): bolts hammer the ground round it from MERGE on, each blowing out a pit. */
    public static final int PURPLE_STRIKES = 22;
    public static int strikeTick(int k) { return PURPLE_MERGE + 6 + k * 4; }
    /** Where strike k lands, as {dx, dz} from the centre: all round it, 7 to 30 blocks out (deterministic, same on server and client). */
    public static double[] strikeOffset(int k) {
        long z = (k + 1) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L; z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL; z ^= z >>> 31;
        double a = (z & 0xFFFF) / 65536.0 * Math.PI * 2 + k * 2.4, d = 7 + 23 * (((z >>> 16) & 0xFFFF) / 65536.0);
        return new double[]{Math.cos(a) * d, Math.sin(a) * d};
    }
    /** Purple bursts this far above the Blue that made it (owner, 2026-11-16: "nổ trên trời"): the orbs soar up while they swirl. */
    public static final double PURPLE_LIFT = 16;

    /** Offset of the orbs below the burst point at this age: -PURPLE_LIFT at the Blue at contact, rising slowly all through the swirl to 0 at
     *  the face-off (owner, 2026-11-18). */
    public static double purpleRise(double age) {
        // Slow (owner, round 37): the rise lasts the whole swirl and the coast into the fade, so it never outpaces the spin.
        double k = Math.max(0, Math.min(1, (age - PURPLE_CONTACT) / (double) (PURPLE_SWIRL_END + 12 - PURPLE_CONTACT)));
        return -PURPLE_LIFT * (1 - k * k * (3 - 2 * k));
    }

    private BlueRedRules() {}

    /** Letting go of left click: an unready Blue vanishes (null), a ready one stays where it is. */
    public static BlueState onRelease(BlueState state) {
        return state == BlueState.CHARGING ? null : BlueState.ANCHORED;
    }

    /** A ready Blue still held at the charge limit anchors instead of vanishing. */
    public static BlueState atChargeLimit(BlueState state, int age, int chargeTicks) {
        return state == BlueState.READY && age >= chargeTicks ? BlueState.ANCHORED : state;
    }

    public static boolean expired(BlueState state, int age, int anchoredAge, int chargeTicks, int anchorTicks) {
        return switch (state) {
            case CHARGING -> age >= chargeTicks;
            case READY -> false;
            case ANCHORED -> anchoredAge >= anchorTicks;
        };
    }

    /** Health a hit really took, never more than the target had: overkill does not count towards the ready form. */
    public static float credit(float healthBefore, float healthAfter) {
        return Math.max(0, Math.min(healthBefore, healthBefore - healthAfter));
    }

    /** First point where segment a→b enters the sphere, as a fraction of the segment, or -1 when it misses. */
    public static double segmentSphere(Vec3 a, Vec3 b, Vec3 center, double radius) {
        Vec3 d = b.subtract(a), f = a.subtract(center);
        double c = f.lengthSqr() - radius * radius;
        if (c <= 0) return 0;
        double qa = d.lengthSqr(), qb = 2 * f.dot(d);
        if (qa == 0) return -1;
        double disc = qb * qb - 4 * qa * c;
        if (disc < 0) return -1;
        double t = (-qb - Math.sqrt(disc)) / (2 * qa);
        return t >= 0 && t <= 1 ? t : -1;
    }

    public static Vec3 redAt(Vec3 origin, Vec3 dir, double speed, double age) {
        return origin.add(dir.scale(speed * age));
    }

    public static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        double len = d.lengthSqr();
        double t = len == 0 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(d) / len));
        return p.distanceTo(a.add(d.scale(t)));
    }

    public static RedMeetsBlue meet(BlueState state) {
        return state == BlueState.CHARGING ? RedMeetsBlue.SHATTER : RedMeetsBlue.PURPLE;
    }
}
