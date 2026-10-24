package net.godwand.skill;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.godwand.spatial.*;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class SpatialBridgeTest {
    @Test
    void bridgeJournalRoundTripsNegativeCoordinatesAndOwner() {
        var source = new DisplacedBlockRecord(new BlockPos(-30, 70, 40), World.OVERWORLD, null, null,
                new Vector3f(), new Quaternionf(), 100, true);
        source.bridgeDestination = new BlockPos(-700, 100, -400);
        source.boundPlayerUuid = java.util.UUID.randomUUID();
        var nbt = new net.minecraft.nbt.NbtCompound();
        source.writeBridgeData(nbt);
        var recovered = new DisplacedBlockRecord(source.pos, World.OVERWORLD, null, null,
                new Vector3f(), new Quaternionf(), 100, true);
        recovered.readBridgeData(nbt);
        assertEquals(source.bridgeDestination, recovered.bridgeDestination);
        assertEquals(source.boundPlayerUuid, recovered.boundPlayerUuid);
    }
    @Test
    void bridgeHasThreeLanesAndNoDiagonalHoles() {
        for (Vec3d direction : new Vec3d[]{new Vec3d(1,0,0), new Vec3d(0,0,-1), new Vec3d(1,0,1)}) {
            var plan = SpatialBridge.plan(new Vec3d(10.5, 65, -20.5), direction, 64);
            assertEquals(12, plan.size());
            assertEquals(12, new HashSet<>(plan).size());
            assertTrue(plan.stream().allMatch(p -> p.getY() == 64));
            for (int i = 1; i < 4; i++) assertEquals(1, plan.get(i * 3).getManhattanDistance(plan.get((i - 1) * 3)));
        }
    }

    @Test
    void jumpOnlyRaisesBridgeAfterPlayerCanClearTheStep() {
        assertEquals(64, SpatialBridge.raisedLevel(64, 65.5));
        assertEquals(65, SpatialBridge.raisedLevel(64, 66.0));
        assertEquals(65, SpatialBridge.raisedLevel(65, 66.25));
    }

    @Test
    void borrowedBlockKeepsItsSourceAndDestinationSeparate() {
        var record = new DisplacedBlockRecord(new BlockPos(1, 64, 1), World.OVERWORLD, null, null,
                new Vector3f(), new Quaternionf(), 100, true);
        SpatialDisplacementManager.recoverBridge(record);
        SpatialDisplacementManager.moveBridge(record, new BlockPos(5, 64, 1));
        assertEquals(new BlockPos(1, 64, 1), record.pos);
        assertEquals(new BlockPos(5, 64, 1), record.bridgeDestination);
        SpatialDisplacementManager.moveBridge(record, new BlockPos(6, 65, 1));
        assertEquals(65, record.bridgeDestination.getY());
        SpatialDisplacementManager.moveBridge(record, null);
        assertNull(record.bridgeDestination);
        SpatialDisplacementManager.removeActiveRecord(World.OVERWORLD, record.pos);
    }

    @Test
    void pathWalkerRejectsNullPlayer() {
        assertFalse(SpatialPathWalker.hasGodWand(null));
    }

    @Test
    void restoreAllForPlayerHandlesPlayerRecordsSafely() {
        SpatialDisplacementManager.clearRuntimeState();
        java.util.UUID playerUuid = java.util.UUID.randomUUID();
        var record1 = new DisplacedBlockRecord(new BlockPos(10, 64, 10), World.OVERWORLD, null, null,
                new Vector3f(), new Quaternionf(), 100, true);
        record1.boundPlayerUuid = playerUuid;
        SpatialDisplacementManager.recoverBridge(record1);
        SpatialDisplacementManager.moveBridge(record1, new BlockPos(10, 64, 15));

        assertEquals(1, SpatialDisplacementManager.getActiveRecordCount());
        SpatialDisplacementManager.restoreAllForPlayer(playerUuid, null);
        SpatialDisplacementManager.clearRuntimeState();
        assertEquals(0, SpatialDisplacementManager.getActiveRecordCount());
    }
}
