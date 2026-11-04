package net.godwand.client;

import net.godwand.network.ImpactFxPayload;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

public class GodWandPostFxTest {

    @Test
    void blackHoleAuraGrowsThenFadesOutLinearly() {
        float peak = 0;
        float previous = 0;
        for (int tick = 0; tick <= 340; tick++) {
            float progress = tick / 340.0f;
            float radius = GodWandPostFxManager.blackHoleRadius(progress);
            if (tick <= 34) {
                assertTrue(radius >= previous, "Radius should expand initially at progress " + progress);
            } else if (tick > 328) {
                assertTrue(radius <= previous, "Radius should collapse at end, progress " + progress);
            }
            if (radius > peak) peak = radius;
            previous = radius;
        }
        assertTrue(peak > 0.45f, "Black hole aura peak radius should reach threshold");
        assertEquals(0, GodWandPostFxManager.blackHoleRadius(1.0f), 0.001f);
    }

    @Test
    void singularityRadiusIsSmoothAndBounded() {
        for (int frame = 0; frame <= 1000; frame++) {
            float progress = frame / 1000.0f;
            float r = GodWandPostFxManager.singularityRadius(progress);
            assertTrue(r >= 0.0f && r <= 1.0f, "Singularity radius out of bounds at " + progress);
        }
    }

    @Test
    void blackHoleAuraSurvivesUntilDetonationGraceExpires() throws Exception {
        Field active = GodWandPostFxManager.class.getDeclaredField("active");
        active.setAccessible(true);
        GodWandPostFxManager.reset();
        GodWandPostFxManager.triggerImpact(0, 0, 0, ImpactFxPayload.TYPE_BLACK_HOLE, 340, 1);
        for (int tick = 0; tick < 345; tick++) GodWandPostFxManager.onClientTick();
        assertTrue(active.getBoolean(null), "Keep the flare while the impact cue runs before the burst");
        for (int tick = 0; tick < 50; tick++) GodWandPostFxManager.onClientTick();
        assertFalse(active.getBoolean(null), "A missing detonation packet must not leave an infinite flare");
        GodWandPostFxManager.triggerImpact(0, 0, 0, ImpactFxPayload.TYPE_SUPERNOVA, 10, 1);
        for (int tick = 0; tick < 11; tick++) GodWandPostFxManager.onClientTick();
        assertFalse(active.getBoolean(null));
        GodWandPostFxManager.reset();
    }
    @Test
    void inactiveEffectsNeverAccessTheLevelOrItsFramebuffer() {
        GodWandPostFxManager.reset();
        assertDoesNotThrow(() -> GodWandPostFxManager.onWorldRender(null, null, 0.0f));
    }

    @Test
    void supernovaExpandsWithoutPulsingAndTurbulenceStartsAfterBurst() {
        float previous = 0;
        for (int frame = 0; frame <= 1000; frame++) {
            float progress = frame / 1000.0f;
            float radius = GodWandPostFxManager.supernovaRadius(progress);
            assertTrue(radius >= previous);
            assertTrue(radius <= 98.0f);
            previous = radius;
        }
        assertEquals(14.0f, GodWandPostFxManager.supernovaRadius(0), 0.001f);
        assertEquals(98.0f, previous, 0.001f);
        assertEquals(68.0f, GodWandPostFxManager.supernovaRadius(0.10f), 0.001f);
        assertEquals(98.0f, GodWandPostFxManager.supernovaRadius(0.65f), 0.001f);
        assertEquals(0, GodWandPostFxManager.supernovaAftermath(0.2f));
        assertEquals(1, GodWandPostFxManager.supernovaAftermath(0.5f));
    }

    @Test
    void coreDisappearsBeforeFlareAndTheBurstExpandsSmoothly() {
        float held = GodWandPostFxManager.blackHoleRadius(328f / 340);
        assertTrue(held > 4.5f);
        float previous = held;
        for (int tick = 329; tick <= 340; tick++) {
            float radius = GodWandPostFxManager.blackHoleRadius(tick / 340f);
            assertTrue(radius <= previous);
            previous = radius;
        }
        assertEquals(0, GodWandPostFxManager.blackHoleRadius(330f / 340), 0.001f);
        assertTrue(GodWandPostFxManager.supernovaRadius(4f / 240) > 30);
    }

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
        assertEquals(ImpactFxPayload.TYPE_BLACK_HOLE, payload.fxType());
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
