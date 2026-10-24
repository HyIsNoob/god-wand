package net.godwand.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StoneCannonManagerTest {

    @Test
    public void testManagerScalingCalculations() {
        assertEquals(1, StoneCannonManager.calculateChargeLevel(10));
        assertEquals(2, StoneCannonManager.calculateChargeLevel(30));
        assertEquals(3, StoneCannonManager.calculateChargeLevel(60));
        assertEquals(4, StoneCannonManager.calculateChargeLevel(100));

        assertEquals(65.0f, StoneCannonManager.calculateDirectDamage(1), 1e-4);
        assertEquals(120.0f, StoneCannonManager.calculateDirectDamage(2), 1e-4);
        assertEquals(185.0f, StoneCannonManager.calculateDirectDamage(3), 1e-4);
        assertEquals(260.0f, StoneCannonManager.calculateDirectDamage(4), 1e-4);

        assertEquals(3.0, StoneCannonManager.calculateCraterRadius(1), 1e-4);
        assertEquals(4.5, StoneCannonManager.calculateCraterRadius(2), 1e-4);
        assertEquals(6.0, StoneCannonManager.calculateCraterRadius(3), 1e-4);
        assertEquals(8.0, StoneCannonManager.calculateCraterRadius(4), 1e-4);

        assertEquals(2.8, StoneCannonManager.calculateSpeed(1), 1e-4);
        assertEquals(3.6, StoneCannonManager.calculateSpeed(2), 1e-4);
        assertEquals(4.5, StoneCannonManager.calculateSpeed(3), 1e-4);
        assertEquals(5.4, StoneCannonManager.calculateSpeed(4), 1e-4);

        assertEquals(0.40, StoneCannonManager.calculateRecoil(1), 1e-4);
        assertEquals(0.75, StoneCannonManager.calculateRecoil(2), 1e-4);
        assertEquals(1.15, StoneCannonManager.calculateRecoil(3), 1e-4);
        assertEquals(1.65, StoneCannonManager.calculateRecoil(4), 1e-4);
    }
}
