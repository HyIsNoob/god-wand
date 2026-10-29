package net.godwand.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArcaneOrbChargeTest {
    @Test void glowFadesSmoothlyFromBrightCoreToTransparentEdge() {
        assertEquals(1, ArcaneOrbModel.chargeGlowAlpha(0));
        float previous = 1;
        for (int i = 1; i <= 100; i++) {
            float alpha = ArcaneOrbModel.chargeGlowAlpha(i / 100f);
            assertTrue(alpha >= 0 && alpha <= previous);
            previous = alpha;
        }
        assertEquals(0, previous);
        assertEquals(0, ArcaneOrbModel.chargeGlowAlpha(2));
    }
}
