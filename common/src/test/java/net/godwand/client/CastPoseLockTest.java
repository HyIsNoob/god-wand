package net.godwand.client;

import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CastPoseLockTest {
    @Test void anotherModsAnimationIsUndoneOnceOnly() {
        var arm = new ModelPart(List.of(), Map.of()); var leg = new ModelPart(List.of(), Map.of());
        arm.xRot = -2.9f; arm.x = -5;
        CastPoseLock.capture(arm);
        arm.xRot = .3f; arm.x = 0; leg.xRot = .5f; // an animation mod moves the arm (and the leg, which the cast never posed)
        assertTrue(CastPoseLock.restore(arm, leg));
        assertEquals(-2.9f, arm.xRot, 1e-6); assertEquals(-5, arm.x, 1e-6);
        assertEquals(.5f, leg.xRot, 1e-6, "a part the cast did not pose is left to the other mod");
        arm.xRot = .3f;
        assertFalse(CastPoseLock.restore(arm), "only the frame it was captured in");
        assertEquals(.3f, arm.xRot, 1e-6);
    }
}
