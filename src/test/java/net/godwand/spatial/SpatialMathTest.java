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

    @Test
    public void testDisplacedBlockRecordHysteresisAndRestoration() {
        net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(1, 2, 3);
        DisplacedBlockRecord record = new DisplacedBlockRecord(
                pos,
                net.minecraft.world.World.OVERWORLD,
                null,
                null,
                new Vector3f(0, 2, 0),
                new Quaternionf(),
                new Vector3f(1, 1, 1),
                100,
                true,
                DisplacedBlockRecord.DisplacementMode.HYSTERESIS_AURA,
                java.util.UUID.randomUUID()
        );

        assertEquals(DisplacedBlockRecord.DisplacementMode.HYSTERESIS_AURA, record.mode);
        assertNotNull(record.boundPlayerUuid);
        assertFalse(record.isRestoring());
        assertEquals(0, record.restoreTick);

        record.beginRestoration();
        assertTrue(record.isRestoring());
        assertEquals(1, record.restoreTick);
        assertEquals(3, record.animationStage);

        record.interruptRestoration();
        assertFalse(record.isRestoring());
        assertEquals(0, record.restoreTick);
        assertEquals(2, record.animationStage);
    }

    @Test
    public void testCreatePivotTransform() {
        net.minecraft.util.math.Vec3d offset = new net.minecraft.util.math.Vec3d(1.0, 2.0, 3.0);
        Quaternionf rot = new Quaternionf();
        net.minecraft.util.math.AffineTransformation transform = SpatialMath.createPivotTransform(offset, rot, 1.0f);
        assertNotNull(transform);
        assertEquals(1.0f, transform.getTranslation().x, 1e-4);
        assertEquals(2.0f, transform.getTranslation().y, 1e-4);
        assertEquals(3.0f, transform.getTranslation().z, 1e-4);
    }

    @Test
    public void testComputeInteractiveEvasion() {
        net.minecraft.util.math.Vec3d debris = new net.minecraft.util.math.Vec3d(10.0, 65.0, 10.0);
        net.minecraft.util.math.Vec3d playerChest = new net.minecraft.util.math.Vec3d(9.0, 65.0, 10.0); // 1.0m away (< 2.8m)
        net.minecraft.util.math.Vec3d evasion = SpatialMath.computeInteractiveEvasion(debris, playerChest, 2.8);
        assertTrue(evasion.lengthSquared() > 0.0);
        assertTrue(evasion.x > 0.0); // Pushed away from player (x increases)

        // Far away (> 2.8m)
        net.minecraft.util.math.Vec3d farPlayer = new net.minecraft.util.math.Vec3d(5.0, 65.0, 10.0);
        net.minecraft.util.math.Vec3d noEvasion = SpatialMath.computeInteractiveEvasion(debris, farPlayer, 2.8);
        assertEquals(net.minecraft.util.math.Vec3d.ZERO, noEvasion);
    }

    @Test
    public void testComputeCanopyVortexOffsetLifting() {
        net.minecraft.util.math.BlockPos craterBottom = new net.minecraft.util.math.BlockPos(0, 50, 0);
        net.minecraft.util.math.Vec3d blastCenter = new net.minecraft.util.math.Vec3d(0.0, 64.0, 0.0);
        net.minecraft.util.math.Vec3d offset = SpatialMath.computeCanopyVortexOffset(craterBottom, blastCenter, 10.0, 0);

        // Crater bottom is at Y=50, blastCenter Y=64 (depth = 14)
        // Offset Y must lift it up above crater center (> 14m)
        assertTrue(offset.y >= 14.0 + 3.8, "Debris at crater bottom must be lifted above crater rim");
    }

    @Test
    public void testDistanceToAuraSpine() {
        net.minecraft.util.math.Vec3d chest = new net.minecraft.util.math.Vec3d(0.0, 65.0, 0.0);
        net.minecraft.util.math.Vec3d lookDir = new net.minecraft.util.math.Vec3d(1.0, 0.0, 0.0);
        double forwardLead = 4.0;

        // Block on the spine midway
        net.minecraft.util.math.Vec3d onSpine = new net.minecraft.util.math.Vec3d(2.0, 65.0, 0.0);
        assertEquals(0.0, SpatialMath.distanceToAuraSpine(onSpine, chest, lookDir, forwardLead), 1e-4);

        // Block perpendicular to the spine
        net.minecraft.util.math.Vec3d perp = new net.minecraft.util.math.Vec3d(2.0, 65.0, 1.5);
        assertEquals(1.5, SpatialMath.distanceToAuraSpine(perp, chest, lookDir, forwardLead), 1e-4);

        // Block ahead beyond forward lead (clamped at x=4.0)
        net.minecraft.util.math.Vec3d beyond = new net.minecraft.util.math.Vec3d(6.0, 65.0, 0.0);
        assertEquals(2.0, SpatialMath.distanceToAuraSpine(beyond, chest, lookDir, forwardLead), 1e-4);

        // Block behind chest (clamped at x=0.0)
        net.minecraft.util.math.Vec3d behind = new net.minecraft.util.math.Vec3d(-1.5, 65.0, 0.0);
        assertEquals(1.5, SpatialMath.distanceToAuraSpine(behind, chest, lookDir, forwardLead), 1e-4);
    }
}
