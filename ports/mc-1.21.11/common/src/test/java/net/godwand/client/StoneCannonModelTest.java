package net.godwand.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoneCannonModelTest {
    @Test
    void windStartsFastAndSharesTheDrillsRotationAndHelixDirection() {
        float initialSpeed = StoneCannonModel.chargeWindRotation(21) - StoneCannonModel.chargeWindRotation(20);
        assertTrue(initialSpeed > 0.8f && initialSpeed < 0.9f);
        for (int tick = 20; tick <= 100; tick++) {
            assertTrue(StoneCannonModel.chargeWindRotation(tick + 1) > StoneCannonModel.chargeWindRotation(tick));
            assertTrue(StoneCannonModel.chargeDrillRotation(tick + 1) > StoneCannonModel.chargeDrillRotation(tick));
        }
        var nose = StoneCannonModel.chargeWindPoint(0, 0, 1, 1);
        var behindNose = StoneCannonModel.chargeWindPoint(0.01f, 0, 1, 1);
        assertTrue(behindNose.y < nose.y, "Helix trails backwards along the stone rifling");
    }

    @Test
    void windWaitsOneSecondThenFadesInAndAcceleratesContinuously() {
        assertEquals(0, StoneCannonModel.chargeWindVisibility(0));
        assertEquals(0, StoneCannonModel.chargeWindVisibility(20));
        assertEquals(0.5f, StoneCannonModel.chargeWindVisibility(32), 0.001f);
        assertEquals(1, StoneCannonModel.chargeWindVisibility(44));
        float previousSpeed = 0;
        for (int tick = 20; tick < 120; tick++) {
            float speed = StoneCannonModel.chargeWindRotation(tick + 1) - StoneCannonModel.chargeWindRotation(tick);
            assertTrue(speed + 0.0001f >= previousSpeed);
            previousSpeed = speed;
        }
    }

    @Test
    void chargedWindReachesBehindPlayerAndTightensAsPressureBuilds() {
        for (float charge : new float[]{0, 0.5f, 1}) {
            float scale = 0.22f + charge * 0.4f;
            var tip = StoneCannonModel.chargeWindPoint(0, 0, scale, charge);
            var tail = StoneCannonModel.chargeWindPoint(1, 0, scale, charge);
            assertEquals(2.45f * scale, tip.z, 0.001);
            assertTrue(tail.z + 1.85f < -2, "Wind should reach at least two blocks behind the player");
        }
        var loose = StoneCannonModel.chargeWindPoint(0.5f, 0, 0.22f, 0);
        var tight = StoneCannonModel.chargeWindPoint(0.5f, 0, 0.62f, 1);
        assertTrue(Math.hypot(tight.x, tight.y) < Math.hypot(loose.x, loose.y));
    }

    @Test
    void impactPressureOpensAwayFromCollisionAndStaysShort() {
        for (int level = 1; level <= 4; level++) {
            var point = CannonPressureRenderer.conePoint(1, 0, 1, level);
            assertTrue(point.z < 0 && point.z > -3.1);
            assertTrue(point.x > 1 && point.x < 2.5);
            var beginning = CannonPressureRenderer.conePoint(1, 0, 0.15f, level);
            assertTrue(beginning.x < point.x);
        }
    }
    @Test
    void chargeWindRunsFromNoseBehindTheStoneAndChangesWithPhase() {
        var nose = StoneCannonModel.chargeWindPoint(0, 0, 1, 0);
        var tail = StoneCannonModel.chargeWindPoint(1, 0, 1, 0);
        assertEquals(2.45f, nose.z, 0.001f);
        assertTrue(tail.z < -0.75f);
        assertNotEquals(StoneCannonModel.chargeWindPoint(0.5f, 0, 1, 0), StoneCannonModel.chargeWindPoint(0.5f, 1, 1, 0));
    }
    @Test
    void stoneHasClosedPointAndNarrowsAlongItsWholeLength() {
        for (int side = 0; side < 24; side++) {
            float angle = side * (float) Math.PI / 12;
            var tip = StoneCannonModel.surface(1, angle);
            assertEquals(0, Math.hypot(tip.x, tip.y), 0.00001);
            assertEquals(2.45, tip.z, 0.00001);
            for (int ring = 0; ring < 16; ring++) {
                var point = StoneCannonModel.surface(ring / 16f, angle);
                assertTrue(Float.isFinite(point.x) && Float.isFinite(point.y));
                assertTrue(Math.hypot(point.x, point.y) <= 0.4651);
                assertTrue(point.z < tip.z);
            }
        }
    }
}
