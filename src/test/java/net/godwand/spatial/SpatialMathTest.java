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
}
