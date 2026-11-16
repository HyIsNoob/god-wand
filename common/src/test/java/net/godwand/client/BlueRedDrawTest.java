package net.godwand.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.godwand.skill.BlueRedRules;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlueRedDrawTest {
    /** Counts nothing, costs nothing: a Mockito mock per vertex made this suite take minutes. */
    static final class Sink implements VertexConsumer {
        public VertexConsumer addVertex(float x, float y, float z) { return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
    static final Vec3 EYE = new Vec3(0, 65, 0), AT = new Vec3(0, 65, 10), LOOK = new Vec3(0, 0, 1);

    static GateDraw.Ctx ctx(int layer) {
        GateDraw.Ctx c = new GateDraw.Ctx();
        c.v = new Sink(); c.m = new Matrix4f(); c.eye = EYE; c.layer = layer; c.nearMin = .05; c.nearMax = .4;
        return c;
    }

    @Test void blueHasDenseBlueFlamesInTheBodyPass() {
        var body = ctx(0);
        BlueRedDraw.blue(body, AT, false, 30, -1);
        assertTrue(body.quads >= 20, "the orb, its flame tongues and smoke, not just glow: " + body.quads);
    }

    @Test void readyBlueLooksClearlyDifferent() {
        var plain = ctx(1); BlueRedDraw.blue(plain, AT, false, 30, -1);
        var ready = ctx(1); BlueRedDraw.blue(ready, AT, true, 30, 20);
        assertTrue(ready.quads > plain.quads + 40, plain.quads + " vs " + ready.quads);
    }

    @Test void redHasBrightAndDarkSwirlStrokes() {
        var body = ctx(0); BlueRedDraw.redOrb(body, AT, .4, 20);
        var glow = ctx(1); BlueRedDraw.redOrb(glow, AT, .4, 20);
        assertTrue(body.quads > 20, "dark strokes and dark orbs: " + body.quads);
        assertTrue(glow.quads > 80, "bright strokes, orbs and swirling particles: " + glow.quads);
    }

    @Test void purpleGrowsAndDetonatesWithinBudget() {
        int before = 0;
        for (double age = 0; age <= BlueRedRules.PURPLE_CINEMATIC + 10; age += 1) {
            var c = ctx(-1);
            BlueRedDraw.purple(c, AT, age, EYE, LOOK);
            assertTrue(c.quads < SilentGateRenderer.QUAD_BUDGET);
            if (age == BlueRedRules.PURPLE_DETONATE - 1) before = c.quads;
            if (age == BlueRedRules.PURPLE_DETONATE + 2) assertTrue(c.quads > before, "the blast adds the radial lightning");
        }
    }

    @Test void voidShowsBothOrbsMergingOverBlack() {
        var body = ctx(0);
        BlueRedDraw.purpleVoid(body, AT, 10, AT.add(0, 0, -10), LOOK);
        assertTrue(body.quads > 30, "backdrop, smoke and the two orbs: " + body.quads);
        var merge = ctx(1);
        BlueRedDraw.purpleVoid(merge, AT, BlueRedRules.PURPLE_VOID_END - 8, AT.add(0, 0, -10), LOOK);
        var early = ctx(1);
        BlueRedDraw.purpleVoid(early, AT, 10, AT.add(0, 0, -10), LOOK);
        assertTrue(merge.quads > early.quads, "lightning and the burst when they meet");
    }

    @Test void voidFadesInAndOutInsteadOfCutting() {
        assertEquals(0, BlueRedDraw.voidAlpha(0), 1e-9);
        assertEquals(1, BlueRedDraw.voidAlpha(30), 1e-9);
        assertTrue(BlueRedDraw.voidAlpha(BlueRedRules.PURPLE_VOID_END + 6) > 0 && BlueRedDraw.voidAlpha(BlueRedRules.PURPLE_VOID_END + 6) < 1);
        assertEquals(0, BlueRedDraw.voidAlpha(BlueRedRules.PURPLE_VOID_END + 12), 1e-9);
    }

    @Test void redImpactFramesAlternateBlackAndRed() {
        assertEquals(BlueRedDraw.INK, BlueRedDraw.impactColor(0));
        assertEquals(BlueRedDraw.RED_BODY, BlueRedDraw.impactColor(1));
        assertEquals(BlueRedDraw.INK, BlueRedDraw.impactColor(2));
        assertTrue(BlueRedDraw.impactAlpha(0) > .8 && BlueRedDraw.impactAlpha(6) == 0);
        var c = ctx(-1);
        BlueRedDraw.impact(c, AT, 0, EYE, LOOK);
        assertTrue(c.quads > 10);
    }

    @Test void flightAndBurstsDraw() {
        var c = ctx(-1);
        BlueRedDraw.redFlight(c, Vec3.ZERO, new Vec3(0, 0, 1), new Vec3(0, 0, 30), 10, 1, 7, true);
        BlueRedDraw.burst(c, AT, 4, true);
        BlueRedDraw.burst(c, AT, 4, false);
        assertTrue(c.quads > 50);
    }
}
