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
        net.minecraft.core.BlockPos.MutableBlockPos mutablePos = new net.minecraft.core.BlockPos.MutableBlockPos(10, 20, 30);
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> worldKey = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "the_nether")
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
        net.minecraft.core.BlockPos.MutableBlockPos mutablePos = new net.minecraft.core.BlockPos.MutableBlockPos(5, 5, 5);
        assertFalse(SpatialDisplacementManager.isPositionDisplaced(mutablePos));
    }

    @Test
    public void testDisplacedBlockRecordHysteresisAndRestoration() {
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(1, 2, 3);
        DisplacedBlockRecord record = new DisplacedBlockRecord(
                pos,
                net.minecraft.world.level.Level.OVERWORLD,
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
        net.minecraft.world.phys.Vec3 offset = new net.minecraft.world.phys.Vec3(1.0, 2.0, 3.0);
        Quaternionf rot = new Quaternionf();
        com.mojang.math.Transformation transform = SpatialMath.createPivotTransform(offset, rot, 1.0f);
        assertNotNull(transform);
        assertEquals(1.0f, transform.translation().x(), 1e-4);
        assertEquals(2.0f, transform.translation().y(), 1e-4);
        assertEquals(3.0f, transform.translation().z(), 1e-4);
    }

    @Test
    public void testComputeInteractiveEvasion() {
        net.minecraft.world.phys.Vec3 debris = new net.minecraft.world.phys.Vec3(10.0, 65.0, 10.0);
        net.minecraft.world.phys.Vec3 playerChest = new net.minecraft.world.phys.Vec3(9.0, 65.0, 10.0); // 1.0m away (< 2.8m)
        net.minecraft.world.phys.Vec3 evasion = SpatialMath.computeInteractiveEvasion(debris, playerChest, 2.8);
        assertTrue(evasion.lengthSqr() > 0.0);
        assertTrue(evasion.x > 0.0); // Pushed away from player (x increases)

        // Far away (> 2.8m)
        net.minecraft.world.phys.Vec3 farPlayer = new net.minecraft.world.phys.Vec3(5.0, 65.0, 10.0);
        net.minecraft.world.phys.Vec3 noEvasion = SpatialMath.computeInteractiveEvasion(debris, farPlayer, 2.8);
        assertEquals(net.minecraft.world.phys.Vec3.ZERO, noEvasion);
    }

    @Test
    public void testComputeCanopyVortexOffsetLifting() {
        net.minecraft.core.BlockPos craterBottom = new net.minecraft.core.BlockPos(0, 50, 0);
        net.minecraft.world.phys.Vec3 blastCenter = new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0);
        net.minecraft.world.phys.Vec3 offset = SpatialMath.computeCanopyVortexOffset(craterBottom, blastCenter, 10.0, 0);

        // Crater bottom is at Y=50, blastCenter Y=64 (depth = 14)
        // Offset Y must lift it up towards core level
        assertTrue(offset.y >= 14.0 - 5.0, "Debris at crater bottom must be lifted towards core level");
    }

    @Test
    public void testDistanceToAuraSpine() {
        net.minecraft.world.phys.Vec3 chest = new net.minecraft.world.phys.Vec3(0.0, 65.0, 0.0);
        net.minecraft.world.phys.Vec3 lookDir = new net.minecraft.world.phys.Vec3(1.0, 0.0, 0.0);
        double forwardLead = 4.0;

        // Block on the spine midway
        net.minecraft.world.phys.Vec3 onSpine = new net.minecraft.world.phys.Vec3(2.0, 65.0, 0.0);
        assertEquals(0.0, SpatialMath.distanceToAuraSpine(onSpine, chest, lookDir, forwardLead), 1e-4);

        // Block perpendicular to the spine
        net.minecraft.world.phys.Vec3 perp = new net.minecraft.world.phys.Vec3(2.0, 65.0, 1.5);
        assertEquals(1.5, SpatialMath.distanceToAuraSpine(perp, chest, lookDir, forwardLead), 1e-4);

        // Block ahead beyond forward lead (clamped at x=4.0)
        net.minecraft.world.phys.Vec3 beyond = new net.minecraft.world.phys.Vec3(6.0, 65.0, 0.0);
        assertEquals(2.0, SpatialMath.distanceToAuraSpine(beyond, chest, lookDir, forwardLead), 1e-4);

        // Block behind chest (clamped at x=0.0)
        net.minecraft.world.phys.Vec3 behind = new net.minecraft.world.phys.Vec3(-1.5, 65.0, 0.0);
        assertEquals(1.5, SpatialMath.distanceToAuraSpine(behind, chest, lookDir, forwardLead), 1e-4);
    }

    @Test
    public void testCreateThinBeamTransform() {
        net.minecraft.world.phys.Vec3 start = new net.minecraft.world.phys.Vec3(0.0, 0.0, 0.0);
        net.minecraft.world.phys.Vec3 end = new net.minecraft.world.phys.Vec3(0.0, 0.0, 10.0);
        float thickness = 0.04f;

        com.mojang.math.Transformation transform = SpatialMath.createThinBeamTransform(start, end, thickness);
        assertNotNull(transform);
        assertEquals(thickness, transform.scale().x(), 1e-4);
        assertEquals(thickness, transform.scale().y(), 1e-4);
        assertEquals(10.0f, transform.scale().z(), 1e-4);
    }
}
