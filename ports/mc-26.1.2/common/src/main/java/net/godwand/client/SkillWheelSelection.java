package net.godwand.client;

import net.godwand.item.GodWandItem;

final class SkillWheelSelection {
    static int modeAt(double x, double y) {
        if (x * x + y * y < 48 * 48) return -1;
        double angle = Math.atan2(y, x) + Math.PI / 2;
        return Math.floorMod((int) Math.floor(angle / (Math.PI * 2 / GodWandItem.TOTAL_MODES) + 0.5), GodWandItem.TOTAL_MODES);
    }
}
