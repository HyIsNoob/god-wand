package net.godwand.client;

import net.godwand.skill.BlueRedRules;
import net.godwand.skill.BlueRedRules.BlueState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlueRedVisualTest {
    @Test void blueDoesNotGrowWhenReady() {
        double maxCharging = 0, maxReady = 0;
        for (int age = 0; age < 400; age++) {
            maxCharging = Math.max(maxCharging, BlueRedVisual.blueRadius(BlueState.CHARGING, age));
            maxReady = Math.max(maxReady, BlueRedVisual.blueRadius(BlueState.READY, age));
            assertEquals(BlueRedRules.BLUE_RADIUS, BlueRedVisual.blueRadius(BlueState.ANCHORED, age), BlueRedRules.BLUE_RADIUS * .06);
        }
        assertEquals(maxCharging, maxReady, BlueRedRules.BLUE_RADIUS * .03, "same size from the start");
    }

    @Test void flashLastsThreeTicks() {
        assertEquals(1, BlueRedVisual.flash(0), 1e-9);
        assertTrue(BlueRedVisual.flash(1.5) > 0 && BlueRedVisual.flash(1.5) < 1);
        assertEquals(0, BlueRedVisual.flash(3), 1e-9);
        assertEquals(0, BlueRedVisual.flash(-1), 1e-9);
    }

    @Test void interpolatesBetweenUpdates() {
        assertEquals(new Vec3(1, 2, 3), BlueRedVisual.interp(new Vec3(0, 0, 0), new Vec3(2, 4, 6), .5));
        assertEquals(new Vec3(2, 4, 6), BlueRedVisual.interp(new Vec3(0, 0, 0), new Vec3(2, 4, 6), 1.7), "never overshoots");
    }

    @Test void redHeadStopsWhereRedEnded() {
        Vec3 o = Vec3.ZERO, d = new Vec3(0, 0, 1);
        assertEquals(new Vec3(0, 0, 30), BlueRedVisual.redHead(o, d, 10, 100, null));
        assertEquals(new Vec3(0, 0, 100), BlueRedVisual.redHead(o, d, 50, 100, null), "clamped to range");
        assertEquals(new Vec3(0, 0, 12), BlueRedVisual.redHead(o, d, 10, 100, new Vec3(0, 0, 12)), "server end wins");
    }

    @Test void redChargeGrowsWithTheCharge() {
        assertTrue(BlueRedVisual.redChargeSize(0, 50) < BlueRedVisual.redChargeSize(25, 50));
        assertEquals(BlueRedVisual.redChargeSize(50, 50), BlueRedVisual.redChargeSize(90, 50), 1e-9);
    }
}
