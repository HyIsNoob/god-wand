package net.godwand.client;

import net.godwand.client.GateDraw.Ctx;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GateManaTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    private static SilentGateRenderer.Frame frame(double age, double cmd) {
        return new SilentGateRenderer.Frame(1, 0, cmd < 0 ? 0 : 2, age, cmd, new Vec3(0, 70, 0), 0, 0, 0, 200, -1);
    }
    private static GateMana.Sim run(double from, double to) {
        var s = new GateMana.Sim(0);
        for (double a = from; a <= to; a++) GateMana.step(s, frame(a, -1), new Vec3(0, 70, 0), 0);
        return s;
    }

    @Test void pointsNeverEnterTheBodyCylinderDuringTheSpin() {
        Vec3 feet = new Vec3(0, 70, 0);
        var s = run(0, 90);
        for (int k = 0; k < GateMana.STRANDS; k++) for (int i = 1; i < GateMana.POINTS; i++) {
            Vec3 p = s.cur[k * GateMana.POINTS + i];
            double dy = p.y - feet.y;
            if (dy >= 0 && dy <= GateMana.CYL_H) assertTrue(Math.hypot(p.x, p.z) >= GateMana.CYL_R - 1e-6, "strand " + k + " point " + i + " " + p);
        }
    }

    @Test void simulationStaysFiniteAndRootsFollowTheWand() {
        var s = run(0, 320);
        var wand = GatePose.wand(320, -1, 212, new Vec3(0, 70, 0), 0);
        for (Vec3 p : s.cur) assertTrue(Double.isFinite(p.x + p.y + p.z) && p.distanceTo(wand.pos()) < 12);
        assertTrue(s.cur[0].distanceTo(wand.tip()) < 1e-6);
    }

    @Test void ribbonsStreamBackBehindTheCaster() {
        var s = run(0, 60);
        double behind = 0;
        for (int k = 0; k < GateMana.STRANDS; k++) {
            Vec3 root = s.cur[k * GateMana.POINTS], tail = s.cur[k * GateMana.POINTS + GateMana.POINTS - 1];
            behind += root.z - tail.z; // the caster faces +z at yaw 0, so behind is -z
            assertTrue(tail.distanceTo(root) > 1.5, "long ribbon " + k);
        }
        assertTrue(behind / GateMana.STRANDS > 1.8, "tails trail behind on average: " + behind / GateMana.STRANDS);
    }

    @Test void quadBudgetAndLayers() {
        var s = run(0, 60);
        var f = frame(60, -1);
        int worst = 0;
        for (int layer = -1; layer <= 1; layer++) {
            var mesh = new SilentGateVisualTest.Mesh();
            Ctx c = new Ctx(); c.v = mesh; c.m = new Matrix4f(); c.eye = new Vec3(3, 71.5, 4); c.layer = layer;
            int n = GateMana.emit(c, s, f);
            assertEquals(n * 4, mesh.vertices.size());
            assertTrue(n <= GateMana.QUAD_BUDGET, "layer " + layer + " quads " + n);
            worst = Math.max(worst, n);
            if (layer == 0) assertEquals(GateMana.STRANDS * (GateMana.POINTS - 1) * 2, n);
        }
        assertTrue(worst > 300);
    }

    @Test void colourRunsBlueJadeWhite() {
        assertEquals(0x7FE8FF, GateMana.color(0));
        assertEquals(0x5CFFC8, GateMana.color(.6));
        assertEquals(0xFFFFFF, GateMana.color(1));
        assertTrue(GateMana.alpha(0, 1) > GateMana.alpha(.5, 1) && GateMana.alpha(1, 1) < 1e-9);
    }

    @Test void silkAndStarTilesAreNeitherEmptyNorSolid() {
        byte[] px = GateAtlas.alpha();
        for (var r : new GateAtlas.Region[]{GateAtlas.SILK, GateAtlas.STAR}) {
            long sum = 0;
            for (int y = r.y(); y < r.y() + r.h(); y++) for (int x = r.x(); x < r.x() + r.w(); x++) sum += px[y * GateAtlas.SIZE + x] & 255;
            double cov = sum / 255.0 / (r.w() * r.h());
            assertTrue(cov > .008 && cov < .6, r + " " + cov);
        }
    }

    @Test void eyelidsOpenCloseAndReopen() {
        for (double t = 0; t <= 15; t += 1) assertEquals(0, GateEyelids.openness(t), 1e-9, "black until 15 at " + t);
        double prev = 0;
        for (double t = 15; t <= 45; t += .5) { double v = GateEyelids.openness(t); assertTrue(v >= prev - 1e-12, "opens smoothly 15..45 at " + t); prev = v; }
        assertEquals(1, GateEyelids.openness(45), 1e-9);
        for (double t = 45; t <= 55; t += .5) { double v = GateEyelids.openness(t); assertTrue(v <= prev + 1e-12, "closes smoothly 45..55 at " + t); prev = v; }
        assertEquals(0, GateEyelids.openness(55), 1e-9, "shut at 55");
        assertEquals(.5, GateEyelids.openness(50), .01, "half shut in the middle of the blink");
        for (double t = 55; t <= 62; t += .25) { double v = GateEyelids.openness(t); assertTrue(v >= prev - 1e-12, "opens again 55..62 at " + t); prev = v; }
        assertEquals(1, GateEyelids.openness(62), 1e-9);
        assertEquals(1, GateEyelids.openness(200), 1e-9);
        assertEquals(0, GateEyelids.vignette(62), 1e-9);
        assertTrue(GateEyelids.half(.5, 0, 100) > GateEyelids.half(.5, 1, 100), "almond: widest in the middle");
    }
}
