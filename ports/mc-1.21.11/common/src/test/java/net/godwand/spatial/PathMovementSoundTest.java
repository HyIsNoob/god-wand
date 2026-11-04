package net.godwand.spatial;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PathMovementSoundTest {
    @Test
    void openingAndReturningHaveIndependentLimitsPerPlayer() {
        UUID player = UUID.randomUUID();
        assertTrue(SpatialDisplacementManager.shouldPlayPathSound(player, 100, false));
        assertTrue(SpatialDisplacementManager.shouldPlayPathSound(player, 100, true));
        for (int tick = 100; tick < 106; tick++) {
            assertFalse(SpatialDisplacementManager.shouldPlayPathSound(player, tick, false));
            assertFalse(SpatialDisplacementManager.shouldPlayPathSound(player, tick, true));
        }
        assertTrue(SpatialDisplacementManager.shouldPlayPathSound(player, 106, false));
        assertFalse(SpatialDisplacementManager.shouldPlayPathSound(player, 106, true));
        assertTrue(SpatialDisplacementManager.shouldPlayPathSound(player, 108, true));
        assertTrue(SpatialDisplacementManager.shouldPlayPathSound(UUID.randomUUID(), 100, false));
        assertTrue(SpatialDisplacementManager.shouldPlayPathSound(player, 0, false));
        assertFalse(SpatialDisplacementManager.shouldPlayPathSound(null, 100, false));
    }
}
