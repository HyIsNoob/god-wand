package net.godwand.skill;

import net.godwand.skill.BlueRedRules.BlueState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static net.godwand.skill.BlueRedRules.*;
import static org.junit.jupiter.api.Assertions.*;

class BlueRedRulesTest {
    @Test void releaseUnreadyVanishes() { assertNull(onRelease(BlueState.CHARGING)); }

    @Test void releaseReadyAnchors() {
        assertEquals(BlueState.ANCHORED, onRelease(BlueState.READY));
        assertEquals(BlueState.ANCHORED, onRelease(BlueState.ANCHORED));
    }

    @Test void heldReadyAnchorsAtLimit() {
        assertFalse(expired(BlueState.READY, 160, 0, 160, 300));
        assertEquals(BlueState.ANCHORED, atChargeLimit(BlueState.READY, 160, 160));
        assertEquals(BlueState.READY, atChargeLimit(BlueState.READY, 159, 160));
    }

    @Test void chargingExpiresAt8s() {
        assertTrue(expired(BlueState.CHARGING, 160, 0, 160, 300));
        assertFalse(expired(BlueState.CHARGING, 159, 0, 160, 300));
    }

    @Test void anchoredLasts15s() {
        assertFalse(expired(BlueState.ANCHORED, 999, 299, 160, 300));
        assertTrue(expired(BlueState.ANCHORED, 999, 300, 160, 300));
    }

    @Test void creditCapsAtHealth() {
        assertEquals(4, credit(4, -10));
        assertEquals(0, credit(20, 20));
        assertEquals(0, credit(20, 25));
    }

    @Test void threeZombiesReachThreshold() {
        float total = 0;
        for (int i = 0; i < 3; i++) total += credit(20, 0);
        assertTrue(total >= 50);
        assertTrue(credit(20, 0) * 2 < 50, "two zombies are not enough");
    }

    @Test void fastRedCannotTunnelThroughBlue() {
        double t = segmentSphere(Vec3.ZERO, new Vec3(3, 0, 0), new Vec3(1.5, 0, 0), 2);
        assertTrue(t >= 0 && t <= 1, "a 3-block step through the sphere must hit");
        assertEquals(-1, segmentSphere(Vec3.ZERO, new Vec3(3, 0, 0), new Vec3(1.5, 5, 0), 2));
        assertEquals(0, segmentSphere(new Vec3(1, 0, 0), new Vec3(4, 0, 0), new Vec3(1.5, 0, 0), 2), "starting inside hits at once");
        assertEquals(-1, segmentSphere(Vec3.ZERO, new Vec3(3, 0, 0), new Vec3(10, 0, 0), 2), "sphere beyond the segment end");
        assertEquals(0.25, segmentSphere(Vec3.ZERO, new Vec3(8, 0, 0), new Vec3(4, 0, 0), 2), 1e-9);
    }

    @Test void redMovesStraightAtFixedSpeed() {
        Vec3 p = redAt(new Vec3(1, 2, 3), new Vec3(0, 0, 1), RED_SPEED, 10);
        assertEquals(new Vec3(1, 2, 33), p);
    }

    @Test void distanceToSegmentClampsToEnds() {
        assertEquals(1, distanceToSegment(new Vec3(1, 0, 2), Vec3.ZERO, new Vec3(0, 0, 4)), 1e-9);
        assertEquals(5, distanceToSegment(new Vec3(0, 0, 9), Vec3.ZERO, new Vec3(0, 0, 4)), 1e-9);
        assertEquals(2, distanceToSegment(new Vec3(0, 2, 0), Vec3.ZERO, Vec3.ZERO), 1e-9);
    }

    @Test void purpleRisesDuringTheSwirlThenHolds() {
        assertEquals(-PURPLE_LIFT, purpleRise(0), 1e-9, "starts where the Blue was");
        assertEquals(-PURPLE_LIFT, purpleRise(PURPLE_CONTACT), 1e-9, "they meet where the Blue was, then rise slowly all through the swirl");
        double mid = (PURPLE_CONTACT + PURPLE_SWIRL_END) / 2.0;
        assertTrue(purpleRise(mid) > -PURPLE_LIFT && purpleRise(mid) < 0, "the pair lifts during the lift beat");
        assertEquals(0, purpleRise(PURPLE_SWIRL_END + 12), 1e-9, "up at the burst point once they have come to rest");
        assertEquals(0, purpleRise(PURPLE_DETONATE), 1e-9);
    }

    @Test void meetRules() {
        assertEquals(RedMeetsBlue.SHATTER, meet(BlueState.CHARGING));
        assertEquals(RedMeetsBlue.PURPLE, meet(BlueState.READY));
        assertEquals(RedMeetsBlue.PURPLE, meet(BlueState.ANCHORED));
    }
}
