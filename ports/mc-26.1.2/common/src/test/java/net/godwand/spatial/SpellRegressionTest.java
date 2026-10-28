package net.godwand.spatial;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpellRegressionTest {
    @Test
    void heldOuterBlocksNeverReturnToTheirOriginalPosition() {
        for (int age = 0; age < 80; age++) {
            Vec3 offset = SpatialMath.computeDynamicAuraOffset(
                    new BlockPos(4, 65, 0), new Vec3(0.5, 64, 0.5), new Vec3(0, 0, 1), 2.1, age);
            assertTrue(offset.y >= 0.2, "Held block must remain visibly lifted");
        }
    }

    @Test
    void permanentlyDestroyedBlocksCannotBeginRestoration() {
        var record = new DisplacedBlockRecord(BlockPos.ZERO, Level.OVERWORLD, null, null,
                new Vector3f(), new Quaternionf(), 100, true);
        record.permanentDestruction = true;
        record.beginRestoration();
        assertFalse(record.isRestoring());
    }

    @Test
    void thinBeamConnectsBothCentersInEveryDirection() {
        Vec3 start = new Vec3(12, 70, -9);
        for (Vec3 delta : new Vec3[]{new Vec3(0, 8, 0), new Vec3(0, 0, -9), new Vec3(-5, 3, 7)}) {
            var matrix = SpatialMath.createThinBeamTransform(start, start.add(delta), 0.012f).getMatrix();
            Vector3f near = matrix.transformPosition(new Vector3f(0.5f, 0.5f, 0));
            Vector3f far = matrix.transformPosition(new Vector3f(0.5f, 0.5f, 1));
            assertEquals(0, near.length(), 1.0e-5);
            assertEquals(delta.x, far.x, 1.0e-5);
            assertEquals(delta.y, far.y, 1.0e-5);
            assertEquals(delta.z, far.z, 1.0e-5);
        }
    }
}
