package net.godwand.skill;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StoneCannonMathTest {

    public static int calculateChargeLevel(int chargeTicks) {
        if (chargeTicks >= 80) return 4;
        if (chargeTicks >= 50) return 3;
        if (chargeTicks >= 25) return 2;
        return 1;
    }

    public static double calculateSpeed(int level) {
        return switch (level) {
            case 1 -> 2.8;
            case 2 -> 3.6;
            case 3 -> 4.5;
            default -> 5.4;
        };
    }

    public static float calculateDirectDamage(int level) {
        return switch (level) {
            case 1 -> 65.0f;
            case 2 -> 120.0f;
            case 3 -> 185.0f;
            default -> 260.0f;
        };
    }

    public static double calculateCraterRadius(int level) {
        return switch (level) {
            case 1 -> 3.0;
            case 2 -> 4.5;
            case 3 -> 6.0;
            default -> 8.0;
        };
    }

    @Test
    public void testChargeLevelThresholds() {
        assertEquals(1, calculateChargeLevel(0));
        assertEquals(1, calculateChargeLevel(24));
        assertEquals(2, calculateChargeLevel(25));
        assertEquals(2, calculateChargeLevel(49));
        assertEquals(3, calculateChargeLevel(50));
        assertEquals(3, calculateChargeLevel(79));
        assertEquals(4, calculateChargeLevel(80));
        assertEquals(4, calculateChargeLevel(140));
    }

    @Test
    public void testSpeedAndDamageScaling() {
        for (int lvl = 1; lvl <= 4; lvl++) {
            double speed = calculateSpeed(lvl);
            float dmg = calculateDirectDamage(lvl);
            double crater = calculateCraterRadius(lvl);

            assertTrue(speed >= 2.8 && speed <= 5.4, "Speed out of range for level " + lvl);
            assertTrue(dmg >= 65.0f && dmg <= 260.0f, "Damage out of range for level " + lvl);
            assertTrue(crater >= 3.0 && crater <= 8.0, "Crater radius out of range for level " + lvl);
        }
    }

    @Test
    public void testHelicalVortexGeometry() {
        Vec3 dir = new Vec3(1.0, 0.0, 0.0).normalize();
        Vec3 up = Math.abs(dir.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
        Vec3 tangent = dir.cross(up).normalize();
        Vec3 binormal = dir.cross(tangent).normalize();

        assertEquals(0.0, dir.dot(tangent), 1e-6, "Tangent must be orthogonal to direction");
        assertEquals(0.0, dir.dot(binormal), 1e-6, "Binormal must be orthogonal to direction");
        assertEquals(0.0, tangent.dot(binormal), 1e-6, "Tangent and binormal must be orthogonal");

        double radius = 1.2;
        double angle = Math.PI / 2.0; // 90 deg -> cos=0, sin=1
        Vec3 point = tangent.scale(radius * Math.cos(angle)).add(binormal.scale(radius * Math.sin(angle)));
        assertEquals(radius, point.length(), 1e-6, "Point radius should match expected radius");
    }
}
