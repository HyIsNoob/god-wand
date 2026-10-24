package net.godwand.client;

import net.godwand.network.ImpactFxPayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GodWandPostFxTest {
    @Test
    void terrainStartsOnlyAfterTheDrawingsAndVisibleBurst() {
        assertEquals(CustomImpactFrameOverlay.IMPACT_FRAME_TICKS,
                net.godwand.skill.SupernovaManager.DETONATION_TICK - net.godwand.skill.SupernovaManager.IMPACT_TICK);
        assertTrue(net.godwand.skill.SupernovaManager.TERRAIN_START_TICK > net.godwand.skill.SupernovaManager.DETONATION_TICK);
    }

    @Test
    void impactCuePreservesTheFlareUntilTheSeparateBurst() throws Exception {
        var type = GodWandPostFxManager.class.getDeclaredField("fxType");
        type.setAccessible(true);
        var overlay = CustomImpactFrameOverlay.class.getDeclaredField("active");
        overlay.setAccessible(true);
        GodWandPostFxManager.reset();
        CustomImpactFrameOverlay.reset();
        GodWandPostFxManager.triggerImpact(0, 0, 0, ImpactFxPayload.TYPE_BLACK_HOLE, 340, 1);
        GodWandPostFxManager.triggerImpact(0, 0, 0, ImpactFxPayload.TYPE_SUPERNOVA_IMPACT, ImpactFxPayload.SUPERNOVA_IMPACT_TICKS, 1);
        assertEquals(ImpactFxPayload.TYPE_BLACK_HOLE, type.getInt(null));
        assertTrue(overlay.getBoolean(null));
        for (int tick = 0; tick < CustomImpactFrameOverlay.IMPACT_FRAME_TICKS; tick++) CustomImpactFrameOverlay.onClientTick();
        var overlayTick = CustomImpactFrameOverlay.class.getDeclaredField("currentTick");
        overlayTick.setAccessible(true);
        assertEquals(CustomImpactFrameOverlay.IMPACT_FRAME_TICKS, overlayTick.getInt(null));
        GodWandPostFxManager.triggerImpact(0, 0, 0, ImpactFxPayload.TYPE_SUPERNOVA, 240, 3.5f);
        assertEquals(ImpactFxPayload.TYPE_SUPERNOVA, type.getInt(null));
        assertEquals(CustomImpactFrameOverlay.IMPACT_FRAME_TICKS, overlayTick.getInt(null), "Burst must not restart the impact drawings");
        GodWandPostFxManager.reset();
        CustomImpactFrameOverlay.reset();
    }

    @Test
    void blackHoleCutsOutQuicklyInsteadOfShrinkingForTwelveTicks() {
        assertTrue(GodWandPostFxManager.blackHoleRadius(328f / 340) > 7);
        assertTrue(GodWandPostFxManager.blackHoleRadius(329f / 340) > 3);
        assertEquals(0, GodWandPostFxManager.blackHoleRadius(330f / 340), 0.001f);
    }


    @Test
    void finalBlackHoleFlareWaitsBrieflyForTheBurstPacket() throws Exception {
        var active = GodWandPostFxManager.class.getDeclaredField("active");
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
    void inactiveEffectsNeverAccessTheWorldOrItsFramebuffer() {
        GodWandPostFxManager.reset();
        assertDoesNotThrow(() -> GodWandPostFxManager.onWorldRender(null));
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
