package net.godwand.client;

import net.godwand.skill.BlueRedRules;
import net.godwand.skill.BlueRedRules.BlueState;
import net.minecraft.world.phys.Vec3;

/** Pure timing and shape of the Blue/Red visuals. */
final class BlueRedVisual {
    private BlueRedVisual() {}

    /** Blue is big from the start; the ready form changes how it looks, never how big it is. Only a slow breath. */
    static double blueRadius(BlueState state, double age) {
        return BlueRedRules.BLUE_RADIUS;
    }

    /** White flash when Blue turns ready: full at once, gone after three ticks. */
    static double flash(double ticksSinceReady) {
        return ticksSinceReady < 0 || ticksSinceReady >= 3 ? 0 : 1 - ticksSinceReady / 3;
    }

    /** Two quick beats then a rest, like a heart (one cycle per second). */
    static double heartbeat(double age) {
        double t = (age % 20) / 20;
        return Math.max(Math.exp(-Math.pow((t - .05) / .04, 2)), .7 * Math.exp(-Math.pow((t - .2) / .04, 2)));
    }

    static Vec3 interp(Vec3 prev, Vec3 next, double partial) {
        double k = Math.max(0, Math.min(1, partial));
        return prev.add(next.subtract(prev).scale(k));
    }

    /** Where Red's head is drawn: straight flight at its fixed speed, never past its range or the point the server ended it. */
    static Vec3 redHead(Vec3 origin, Vec3 dir, double age, double range, Vec3 end) {
        if (end != null) return end;
        return origin.add(dir.scale(Math.min(range, age * BlueRedRules.RED_SPEED)));
    }

    /** Red's charging orb snaps into being in a few ticks and then holds its size (owner, 2026-11-16: no scaling animation). */
    static double redChargeSize(double age, double chargeTicks) {
        double k = Math.max(0, Math.min(1, age / 6));
        return .12 + .3 * k * k * (3 - 2 * k);
    }
}
