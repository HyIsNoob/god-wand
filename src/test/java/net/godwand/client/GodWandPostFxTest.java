package net.godwand.client;

import net.godwand.network.ImpactFxPayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GodWandPostFxTest {

    @Test
    public void testImpactFxPayloadTypes() {
        assertEquals(0, ImpactFxPayload.TYPE_DETONATION);
        assertEquals(1, ImpactFxPayload.TYPE_IMPLOSION);
        assertEquals(2, ImpactFxPayload.TYPE_COLLAPSE);
        assertEquals(3, ImpactFxPayload.TYPE_SUPERNOVA);
        assertEquals(4, ImpactFxPayload.TYPE_BLACK_HOLE);

        ImpactFxPayload payload = new ImpactFxPayload(10.0, 64.0, -20.0, ImpactFxPayload.TYPE_BLACK_HOLE, 186, 1.0f);
        assertEquals(10.0, payload.x());
        assertEquals(64.0, payload.y());
        assertEquals(-20.0, payload.z());
        assertEquals(ImpactFxPayload.TYPE_BLACK_HOLE, payload.type());
        assertEquals(186, payload.durationTicks());
        assertEquals(1.0f, payload.intensity());
    }

    @Test
    public void testPostFxLifecycleAndTicking() {
        GodWandPostFxManager.reset();
        GodWandPostFxManager.triggerImpact(0, 0, 0, ImpactFxPayload.TYPE_BLACK_HOLE, 10, 1.0f);

        for (int i = 0; i < 15; i++) {
            GodWandPostFxManager.onClientTick();
        }

        GodWandPostFxManager.reset();
    }
}
