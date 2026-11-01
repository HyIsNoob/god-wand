package net.godwand.skill;

import net.godwand.spatial.SpatialMath;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecallAnimationTest {
    @Test
    void returningBlockKeepsItsCenterOnThePathWhileUnwindingAndResizing() {
        float previous = 0;
        for (int frame = 0; frame <= 100; frame++) {
            float t = ShockwaveSlicer.returnProgress(frame / 100f);
            assertTrue(t >= previous && t <= 1);
            previous = t;
            Vec3 offset = new Vec3(12, 8, -9).scale(1 - t);
            var rotation = new Quaternionf().rotateXYZ(1.2f, 2.1f, -0.4f).slerp(new Quaternionf(), t);
            var transform = SpatialMath.createPivotTransform(offset, rotation, 0.45f + 0.55f * t);
            var center = transform.getMatrix().transformPosition(new Vector3f(0.5f));
            assertEquals(offset.x + 0.5, center.x, 0.00001);
            assertEquals(offset.y + 0.5, center.y, 0.00001);
            assertEquals(offset.z + 0.5, center.z, 0.00001);
        }
        assertEquals(1, previous);
        assertEquals(0, ShockwaveSlicer.returnProgress(-1));
        assertEquals(1, ShockwaveSlicer.returnProgress(2));
    }
}
