package net.godwand.spatial;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpatialAuraTest {
    @Test
    void looseBlocksLeaveBothSidesOfThePathInsteadOfLandingInItsCenter() {
        for (Vec3 forward : new Vec3[]{new Vec3(0, 0, 1), new Vec3(1, 0, 0), new Vec3(1, 0, 1).normalize()}) {
            Vec3 side = new Vec3(-forward.z, 0, forward.x);
            for (int sign : new int[]{-1, 1}) {
                Vec3 position = side.scale(sign * .1);
                Vec3 velocity = SpatialMath.pathLooseBlockVelocity(position, Vec3.ZERO, forward);
                assertTrue(velocity.dot(side) * sign > .6);
                assertEquals(0, velocity.dot(forward), .00001);
                assertTrue(velocity.y > 0 && velocity.y < .3);
                assertTrue(position.add(velocity.scale(7)).horizontalDistance() > 3.9);
            }
        }
    }

    @Test
    void supportDetectionKeepsSlabsButDoesNotKeepTheWallAhead() {
        var feet = new net.minecraft.world.phys.AABB(0.2, 64.5, 0.2, 0.8, 66.3, 0.8);
        assertTrue(SpatialMath.supportsPathFeet(new net.minecraft.world.phys.AABB(0, 64, 0, 1, 64.5, 1), feet));
        assertFalse(SpatialMath.supportsPathFeet(new net.minecraft.world.phys.AABB(0, 64, 1, 1, 65, 2), feet));
        assertFalse(SpatialMath.supportsPathFeet(new net.minecraft.world.phys.AABB(0, 64.5, 0, 1, 65, 1), feet));
    }
    @Test
    void lowFloorSurvivesSmallHeightChangesWithoutKeepingFullObstacles() {
        for (double height : new double[]{0.125, 0.5, 0.875, 0.9375, 1}) {
            var player = new net.minecraft.world.phys.AABB(.2, 64 + height, .2, .8, 66 + height, .8);
            assertTrue(SpatialMath.supportsPathFeet(new net.minecraft.world.phys.AABB(0, 64, 0, 1, 64 + height, 1), player));
        }
        var pathFeet = new net.minecraft.world.phys.AABB(.2, 64.9375, .2, .8, 66.7375, .8);
        assertTrue(SpatialMath.supportsPathFeet(new net.minecraft.world.phys.AABB(0, 64, 0, 1, 65, 1), pathFeet));
        var soulFeet = pathFeet.move(0, -.0625, 0);
        assertTrue(SpatialMath.supportsPathFeet(new net.minecraft.world.phys.AABB(0, 64, 0, 1, 65, 1), soulFeet));
        assertFalse(SpatialMath.supportsPathFeet(new net.minecraft.world.phys.AABB(0, 65, 0, 1, 66, 1), pathFeet));
        assertFalse(SpatialMath.supportsPathFeet(new net.minecraft.world.phys.AABB(0, 64, 1, 1, 65, 2), pathFeet));
    }

    @Test
    void pathOpensObstaclesAtFootHeightButKeepsActualFloor() {
        assertTrue(SpatialMath.canOpenPathBlock(64, 64, false));
        assertTrue(SpatialMath.canOpenPathBlock(64, 64.5, false));
        assertFalse(SpatialMath.canOpenPathBlock(64, 64.875, false));
        assertFalse(SpatialMath.canOpenPathBlock(64, 64.9375, false));
        assertFalse(SpatialMath.canOpenPathBlock(63, 64, false));
        assertTrue(SpatialMath.canOpenPathBlock(65, 64, false));
        assertTrue(SpatialMath.canOpenPathBlock(63, 64, true));
    }
    @Test
    void mobFieldCancelsInwardMovementAndPushesWithoutLaunching() {
        Vec3 normal = new Vec3(1, 0, 0);
        Vec3 response = SpatialMath.auraMobVelocity(new Vec3(-0.4, 0, 0.2), normal, 1.5);
        assertTrue(response.x > 0);
        assertTrue(Math.abs(response.y) <= 0.15);
        assertTrue(response.length() <= 0.7);
        assertEquals(0.2, response.z, 0.00001);
        Vec3 repeated = SpatialMath.auraMobVelocity(response, normal, 1.5);
        assertEquals(response.x, repeated.x, 0.00001, "The field must not accelerate mobs every tick");
        Vec3 leaving = SpatialMath.auraMobVelocity(new Vec3(0.4, 0, 0), normal, 3.8);
        assertTrue(leaving.x >= 0.4);
    }
    @Test
    void fastIncomingArrowHitsShellButOutgoingAndMissingArrowsDoNot() {
        Vec3 hit = SpatialMath.auraArrowIntersection(new Vec3(-10, 0, 0), new Vec3(10, 0, 0), Vec3.ZERO);
        assertNotNull(hit);
        assertEquals(-3.3, hit.x, 0.00001);
        assertNull(SpatialMath.auraArrowIntersection(new Vec3(2, 0, 0), new Vec3(5, 0, 0), Vec3.ZERO));
        assertNull(SpatialMath.auraArrowIntersection(new Vec3(-10, 5, 0), new Vec3(10, 5, 0), Vec3.ZERO));
        Vec3 velocity = new Vec3(6, 0, 0);
        Vec3 result = SpatialMath.deflectAuraArrow(velocity, new Vec3(-1, 0, 0));
        assertTrue(result.x < 0);
        assertTrue(Math.abs(result.z) > 0);
        assertEquals(velocity.length() * 0.55, result.length(), 0.00001);
        assertTrue(SpatialMath.deflectAuraArrow(new Vec3(0, -3, 0), new Vec3(0, 1, 0)).y > 0);
    }

    @Test
    void upperRangeAndForwardVisibilityMatchTheDome() {
        assertEquals(3.3, SpatialMath.distanceToPathAura(new Vec3(0, 4.8, 0), new Vec3(0, 1.1, 0), new Vec3(0, 0, 1), 1), 0.00001);
        assertTrue(SpatialMath.auraVisibility(1) < 0.1);
        assertEquals(1, SpatialMath.auraVisibility(-1));
        assertTrue(SpatialMath.auraVisibility(0.6) > SpatialMath.auraVisibility(0.9));
    }
}
