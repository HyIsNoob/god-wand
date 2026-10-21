package net.godwand.spatial;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SpatialMathTest {
    @Test
    public void testPivotCompensationIdentity() {
        Vector3f disp = new Vector3f(1.0f, 2.0f, 3.0f);
        Quaternionf identity = new Quaternionf(); // no rotation
        Vector3f compensated = SpatialMath.compensatePivot(disp, identity);
        
        assertEquals(1.0f, compensated.x, 1e-4);
        assertEquals(2.0f, compensated.y, 1e-4);
        assertEquals(3.0f, compensated.z, 1e-4);
    }

    @Test
    public void testPivotCompensationWithRotation() {
        Vector3f disp = new Vector3f(0.0f, 0.0f, 0.0f);
        Quaternionf rot90Y = new Quaternionf().rotateY((float) Math.toRadians(90));
        Vector3f compensated = SpatialMath.compensatePivot(disp, rot90Y);
        
        // Pivot C = (0.5, 0.5, 0.5). Rotated C: (0.5, 0.5, -0.5).
        // C - rot(C) = (0, 0, 1.0).
        assertEquals(0.0f, compensated.x, 1e-4);
        assertEquals(0.0f, compensated.y, 1e-4);
        assertEquals(1.0f, compensated.z, 1e-4);
    }

    @Test
    public void testHashCoordinateToPhase() {
        double phase1 = SpatialMath.hashCoordinateToPhase(123456789L);
        double phase2 = SpatialMath.hashCoordinateToPhase(123456789L);
        double phase3 = SpatialMath.hashCoordinateToPhase(987654321L);

        assertEquals(phase1, phase2, 1e-6);
        assertTrue(phase1 >= 0.0 && phase1 < 2.0 * Math.PI);
        assertTrue(phase3 >= 0.0 && phase3 < 2.0 * Math.PI);
        assertNotEquals(phase1, phase3);
    }

    @Test
    public void testDisplacedBlockRecordImmutablePosAndWorldKey() {
        net.minecraft.util.math.BlockPos.Mutable mutablePos = new net.minecraft.util.math.BlockPos.Mutable(10, 20, 30);
        net.minecraft.registry.RegistryKey<net.minecraft.world.World> worldKey = net.minecraft.registry.RegistryKey.of(
                net.minecraft.registry.RegistryKeys.WORLD,
                net.minecraft.util.Identifier.of("minecraft", "the_nether")
        );
        DisplacedBlockRecord record = new DisplacedBlockRecord(
                mutablePos,
                worldKey,
                null,
                null,
                new Vector3f(0, 1, 0),
                new Quaternionf(),
                100,
                true
        );

        // Mutate mutablePos
        mutablePos.set(99, 99, 99);

        // Record pos should remain unchanged at (10, 20, 30)
        assertEquals(10, record.pos.getX());
        assertEquals(20, record.pos.getY());
        assertEquals(30, record.pos.getZ());
        assertEquals(worldKey, record.worldKey);
    }

    @Test
    public void testSpatialDisplacementManagerMutableBlockPosQuery() {
        net.minecraft.util.math.BlockPos.Mutable mutablePos = new net.minecraft.util.math.BlockPos.Mutable(5, 5, 5);
        assertFalse(SpatialDisplacementManager.isPositionDisplaced(mutablePos));
    }
}
