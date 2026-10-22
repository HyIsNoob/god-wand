package net.godwand.client;

import net.minecraft.client.util.math.MatrixStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GodWandCinematicEngineTest {

    @BeforeEach
    public void setup() {
        GodWandCinematicEngine.reset();
    }

    @Test
    public void testTraumaDecayAndShake() {
        // Initially no trauma
        MatrixStack matrices = new MatrixStack();
        GodWandCinematicEngine.applyScreenShake(matrices, 0.0f);
        assertTrue(matrices.isEmpty(), "Matrix stack should not be modified when trauma is 0");

        // Add trauma
        GodWandCinematicEngine.addTrauma(0.8f, 10);
        GodWandCinematicEngine.applyScreenShake(matrices, 0.5f);
        // Matrix should be modified (not empty / has operations)
        assertNotNull(matrices.peek());

        // Tick multiple times, trauma should decay
        for (int i = 0; i < 30; i++) {
            GodWandCinematicEngine.onClientTick();
        }

        MatrixStack cleanMatrices = new MatrixStack();
        GodWandCinematicEngine.applyScreenShake(cleanMatrices, 0.0f);
        assertTrue(cleanMatrices.isEmpty(), "Trauma should fully decay to 0 over time");
    }

    @Test
    public void testDynamicFovZoom() {
        // Default FOV is 1.0
        assertEquals(1.0, GodWandCinematicEngine.getFovMultiplier(0.0f), 0.001);

        // Punch FOV to 1.25
        GodWandCinematicEngine.setFovPunch(1.25f, 5);
        assertEquals(1.25, GodWandCinematicEngine.getFovMultiplier(1.0f), 0.01);

        // After holding and ticking, it should smoothly decay back towards 1.0
        for (int i = 0; i < 70; i++) {
            GodWandCinematicEngine.onClientTick();
        }
        assertEquals(1.0, GodWandCinematicEngine.getFovMultiplier(1.0f), 0.01);
    }

    @Test
    public void testFovFocusContraction() {
        GodWandCinematicEngine.reset();
        assertEquals(1.0, GodWandCinematicEngine.getFovMultiplier(1.0f), 0.001);

        // Simulate EVENT_METEOR_FOCUS payload (target = 0.88f, hold = 16 ticks)
        net.godwand.network.CinematicFxPayload focus = new net.godwand.network.CinematicFxPayload(
                net.godwand.network.CinematicFxPayload.EVENT_METEOR_FOCUS,
                0, 0, 0, 0.88f, 16
        );
        GodWandCinematicEngine.onCinematicEvent(focus);

        // Tick 6 times, FOV should contract below 1.0 towards 0.88
        for (int i = 0; i < 6; i++) {
            GodWandCinematicEngine.onClientTick();
        }
        double fovDuringFocus = GodWandCinematicEngine.getFovMultiplier(1.0f);
        assertTrue(fovDuringFocus < 0.95f, "FOV should contract below 0.95 during focus, was: " + fovDuringFocus);

        // After all hold ticks + recovery, FOV should return to 1.0
        for (int i = 0; i < 50; i++) {
            GodWandCinematicEngine.onClientTick();
        }
        assertEquals(1.0, GodWandCinematicEngine.getFovMultiplier(1.0f), 0.01);
    }

    @Test
    public void testVacuumSilenceTimer() {
        assertFalse(GodWandCinematicEngine.isVacuumSilenceActive());

        GodWandCinematicEngine.setVacuumSilence(5);
        assertTrue(GodWandCinematicEngine.isVacuumSilenceActive());

        for (int i = 0; i < 5; i++) {
            GodWandCinematicEngine.onClientTick();
        }

        assertFalse(GodWandCinematicEngine.isVacuumSilenceActive());
    }
}
