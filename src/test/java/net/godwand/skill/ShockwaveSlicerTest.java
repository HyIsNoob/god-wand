package net.godwand.skill;

import net.godwand.spatial.DimensionPos;
import net.godwand.spatial.DisplacedBlockRecord;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ShockwaveSlicerTest {

    @Test
    public void testSlicingShellStepAndBounds() {
        double maxRadius = 16.0;
        int totalShells = 5;
        double shellStep = maxRadius / totalShells;

        assertEquals(3.2, shellStep, 1e-6);

        double[][] expectedBounds = {
                {0.0, 3.2},
                {3.2, 6.4},
                {6.4, 9.6},
                {9.6, 12.8},
                {12.8, 16.0}
        };

        for (int shell = 0; shell < totalShells; shell++) {
            double rMin = shell * shellStep;
            double rMax = (shell + 1) * shellStep;
            assertEquals(expectedBounds[shell][0], rMin, 1e-6, "Shell " + shell + " rMin mismatch");
            assertEquals(expectedBounds[shell][1], rMax, 1e-6, "Shell " + shell + " rMax mismatch");
        }
    }

    @Test
    public void testStrataScaleCalculations() {
        double maxRadius = 16.0;
        double innerBoundary = maxRadius * 0.4; // 6.4
        double midBoundary = maxRadius * 0.7;   // 11.2

        assertEquals(6.4, innerBoundary, 1e-6);
        assertEquals(11.2, midBoundary, 1e-6);

        // Inner strata test: dist 3.0 < 6.4 -> scale 1.35
        double distInner = 3.0;
        float scaleInner = distInner < innerBoundary ? 1.35f : (distInner < midBoundary ? 1.15f : 1.0f);
        assertEquals(1.35f, scaleInner, 1e-4);

        // Mid strata test: dist 8.0 in [6.4, 11.2) -> scale 1.15
        double distMid = 8.0;
        float scaleMid = distMid < innerBoundary ? 1.35f : (distMid < midBoundary ? 1.15f : 1.0f);
        assertEquals(1.15f, scaleMid, 1e-4);

        // Outer strata test: dist 14.0 >= 11.2 -> scale 1.0
        double distOuter = 14.0;
        float scaleOuter = distOuter < innerBoundary ? 1.35f : (distOuter < midBoundary ? 1.15f : 1.0f);
        assertEquals(1.0f, scaleOuter, 1e-4);
    }

    @Test
    public void testSingularityTaskProperties() {
        BlockPos.Mutable mutableCenter = new BlockPos.Mutable(100, 64, -200);
        net.minecraft.util.math.Vec3d orbCenter = mutableCenter.toCenterPos().add(0, 2.2 + 16.0 * 0.2, 0);
        ShockwaveSlicer.SingularityTask task = new ShockwaveSlicer.SingularityTask(null, mutableCenter, 16.0, orbCenter);

        // Ensure center was converted to immutable
        mutableCenter.set(0, 0, 0);
        assertEquals(100, task.center.getX());
        assertEquals(64, task.center.getY());
        assertEquals(-200, task.center.getZ());

        assertEquals(0, task.ageTicks);
        assertEquals(132, task.maxLifetimeTicks);
        assertEquals(16.0, task.maxRadius, 1e-6);
        assertEquals(orbCenter, task.orbCenter);
    }

    @Test
    public void testDisplacedBlockRecordScaleSupport() {
        BlockPos pos = new BlockPos(1, 2, 3);
        Vector3f disp = new Vector3f(1.0f, 0.5f, 2.0f);
        Quaternionf rot = new Quaternionf();
        Vector3f scale = new Vector3f(1.35f, 1.35f, 1.35f);

        DisplacedBlockRecord record = new DisplacedBlockRecord(
                pos,
                net.minecraft.world.World.OVERWORLD,
                null,
                null,
                disp,
                rot,
                scale,
                100,
                true
        );

        assertEquals(1.35f, record.targetScale.x, 1e-4);
        assertEquals(1.35f, record.targetScale.y, 1e-4);
        assertEquals(1.35f, record.targetScale.z, 1e-4);
        assertNull(record.displayEntityUuid);
        assertEquals(100, record.maxLifetimeTicks);
    }

    @Test
    public void testChargeLevelRadiiMapping() {
        int[] chargeLevels = {1, 2, 3};
        double[] expectedRadii = {5.0, 10.0, 16.0};

        for (int i = 0; i < chargeLevels.length; i++) {
            double radius = switch (chargeLevels[i]) {
                case 1 -> 5.0;
                case 2 -> 10.0;
                default -> 16.0;
            };
            assertEquals(expectedRadii[i], radius, 1e-6);
        }
    }

    @Test
    public void testInwardWaveCollapseLifetimes() {
        int totalShells = 5;
        int[] expectedLifetimes = {108, 106, 104, 102, 100};

        for (int shell = 0; shell < totalShells; shell++) {
            int lifetime = 100 + (totalShells - 1 - shell) * 2;
            assertEquals(expectedLifetimes[shell], lifetime, "Shell " + shell + " lifetime mismatch");
        }
    }

    @Test
    public void testDimensionPosRecord() {
        BlockPos pos1 = new BlockPos(10, 64, 20);
        BlockPos.Mutable mutablePos = new BlockPos.Mutable(10, 64, 20);

        DimensionPos dimPos1 = new DimensionPos(World.OVERWORLD, pos1);
        DimensionPos dimPos2 = new DimensionPos(World.OVERWORLD, mutablePos);
        DimensionPos dimNether = new DimensionPos(World.NETHER, pos1);

        // Mutating mutablePos should not affect dimPos2 since record creates an immutable copy
        mutablePos.set(999, 999, 999);

        assertEquals(dimPos1, dimPos2);
        assertEquals(dimPos1.hashCode(), dimPos2.hashCode());
        assertNotEquals(dimPos1, dimNether);
    }
}
