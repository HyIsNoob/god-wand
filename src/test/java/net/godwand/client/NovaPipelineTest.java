package net.godwand.client;

import net.godwand.network.CinematicFxPayload;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NovaPipelineTest {
    @Test
    void configuredBloomResolutionStillCoversOddAndTinyWindows() {
        var client = net.godwand.config.GodWandConfig.CLIENT;
        int original = client.bloomDownsample;
        try {
            for (int divisor : new int[]{2, 4, 8}) {
                client.bloomDownsample = divisor;
                for (int width : new int[]{1, 17, 917, 1920}) {
                    int reduced = GodWandPostFxManager.bloomDimension(width);
                    assertTrue(reduced > 0 && reduced * divisor >= width);
                    assertTrue(reduced * divisor - width < divisor);
                }
            }
        } finally {
            client.bloomDownsample = original;
        }
    }
    @Test
    void reducedTargetsCoverOddAndTinyWindows() {
        assertEquals(1, GodWandPostFxManager.bloomDimension(1));
        assertEquals(1, GodWandPostFxManager.bloomDimension(4));
        assertEquals(2, GodWandPostFxManager.bloomDimension(5));
        assertEquals(480, GodWandPostFxManager.bloomDimension(1919));
        assertEquals(270, GodWandPostFxManager.bloomDimension(1080));
        for (int width = 1; width < 4096; width++) {
            int reduced = GodWandPostFxManager.bloomDimension(width);
            assertTrue(reduced >= 1 && reduced * 4 >= width);
            assertTrue(reduced * 4 - width < 4);
        }
    }

    @Test
    void supernovaImpactReachesLongRangeTargetsAndStillFadesOut() {
        int nova = CinematicFxPayload.EVENT_SUPERNOVA_BURST;
        assertEquals(1, GodWandCinematicEngine.distanceFactor(nova, 64));
        assertTrue(GodWandCinematicEngine.distanceFactor(nova, 180) > 0.3);
        assertEquals(0, GodWandCinematicEngine.distanceFactor(nova, 250));
        assertEquals(0, GodWandCinematicEngine.distanceFactor(nova, 300));
        assertEquals(GodWandCinematicEngine.distanceFactor(nova, 180),
                GodWandCinematicEngine.distanceFactor(CinematicFxPayload.EVENT_SUPERNOVA_COMPRESS, 180));
        assertEquals(0, GodWandCinematicEngine.distanceFactor(CinematicFxPayload.EVENT_METEOR_SLAM, 80));
        assertEquals(1, GodWandCinematicEngine.distanceFactor(CinematicFxPayload.EVENT_TRAUMA_SHAKE, 8));
    }
}
