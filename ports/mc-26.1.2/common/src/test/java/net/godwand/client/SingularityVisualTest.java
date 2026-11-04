package net.godwand.client;

import io.netty.buffer.Unpooled;
import net.godwand.network.SingularityThreadsPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SingularityVisualTest {
    @Test
    void normalLightTravelsFromBlockToCenterAndRecallEndsAtCenter() {
        for (int seed = 0; seed < 144; seed++) {
            float start = SingularityThreadRenderer.pulseFraction(0, seed, -1);
            float next = SingularityThreadRenderer.pulseFraction(0.25f, seed, -1);
            if (start > 0.01f) assertTrue(next < start);
            float previous = 1;
            for (int frame = 0; frame <= 100; frame++) {
                float fraction = SingularityThreadRenderer.pulseFraction(100, seed, frame / 100f);
                assertTrue(fraction >= 0 && fraction <= previous);
                previous = fraction;
            }
            assertEquals(0, previous);
        }
        assertNotEquals(SingularityThreadRenderer.pulseFraction(0, 1, -1),
                SingularityThreadRenderer.pulseFraction(0, 48, -1));
    }

    @Test
    void threadReferencesRoundTripWithoutEndpointsOrGameplayData() {
        var ids = new ArrayList<Integer>();
        for (int i = 0; i < 144; i++) ids.add(1000 + i);
        var payload = new SingularityThreadsPayload(ids, 18);
        ids.clear();
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SingularityThreadsPayload.CODEC.encode(buf, payload);
            assertEquals(payload, SingularityThreadsPayload.CODEC.decode(buf));
            assertEquals(144, payload.beamIds().size());
        } finally { buf.release(); }
        assertThrows(IllegalArgumentException.class, () -> new SingularityThreadsPayload(java.util.Collections.nCopies(145, 1), 0));
        assertThrows(IllegalArgumentException.class, () -> new SingularityThreadsPayload(List.of(1), -1));
    }

    @Test
    void coreStaysStableThenCollapsesWithoutGrowingAgainOnRestoration() {
        assertEquals(0.68f, GodWandPostFxManager.singularityRadius(0.2f), 0.0001);
        assertEquals(0.68f, GodWandPostFxManager.singularityRadius(0.79f), 0.0001);
        float previous = 0.68f;
        for (int frame = 80; frame <= 100; frame++) {
            float radius = GodWandPostFxManager.singularityRadius(frame / 100f);
            assertTrue(radius <= previous + 0.0001f && radius >= 0);
            previous = radius;
        }
        assertEquals(0, previous, 0.0001f);
        float duringRecall = GodWandPostFxManager.singularityRecallRadius(0.68f, 0.4f);
        assertEquals(duringRecall, GodWandPostFxManager.singularityRecallRadius(duringRecall, 0));
        assertEquals(0, GodWandPostFxManager.singularityRecallRadius(duringRecall, 0.6f));
    }
}
