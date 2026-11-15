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
