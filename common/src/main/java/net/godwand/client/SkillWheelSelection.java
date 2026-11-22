package net.godwand.client;

import net.godwand.item.GodWandItem;

final class SkillWheelSelection {
    static int modeAt(double x, double y) {
        if (x * x + y * y < 48 * 48) return -1;
        double angle = Math.atan2(y, x) + Math.PI / 2;
        return Math.floorMod((int) Math.floor(angle / (Math.PI * 2 / GodWandItem.TOTAL_MODES) + 0.5), GodWandItem.TOTAL_MODES);
    }

    /** Moves {@code steps} enabled skills forward (negative: backward), skipping bits set in {@code disabledMask}. */
    static int cycle(int current, int steps, int disabledMask) {
        int n = GodWandItem.TOTAL_MODES, mode = current, dir = Integer.signum(steps);
        for (int i = 0; i < Math.abs(steps); i++) {
            int next = mode;
            do next = Math.floorMod(next + dir, n); while ((disabledMask >> next & 1) != 0 && next != mode);
            if ((disabledMask >> next & 1) != 0) return current; // every other skill is disabled
            mode = next;
        }
        return mode;
    }
}
