package net.godwand.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.godwand.skill.SilentGateSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SilentGateVisualTest {
    private static final Vec3 ANCHOR = new Vec3(0, 70, 0);

    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    static class Mesh implements VertexConsumer {
        final List<float[]> vertices = new ArrayList<>(); float[] current;
        public VertexConsumer addVertex(float x, float y, float z) { current = new float[]{x, y, z, 0}; vertices.add(current); return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { current[3] = a; return this; }
        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
    private static SilentGateRenderer.Frame frame(double age, double commandAge, int circles) {
        return new SilentGateRenderer.Frame(1, 100, 2, age, commandAge, ANCHOR, 0f, 7L, circles, 200, -1);
    }
    private static int emit(Mesh mesh, SilentGateRenderer.Frame f, List<SilentGateRenderer.Shot> shots) {
        return SilentGateRenderer.emitScene(mesh, new Matrix4f(), f, shots, Vec3.ZERO);
    }

    @Test void quadBudgetHoldsAtEveryAgeWith160Circles() {
        for (int age = 0; age <= 480 + 300 + 387; age += 5) {
            double command = age < 480 ? -1 : Math.min(age - 480, 372);
            Mesh mesh = new Mesh();
            int quads = emit(mesh, frame(age, command, 160), List.of());
            assertTrue(quads <= SilentGateRenderer.QUAD_BUDGET, "age " + age + ": " + quads);
            assertEquals(quads * 4, mesh.vertices.size());
        }
    }
    @Test void fullSceneStaysUnderTwelveThousandQuads() {
        int peak = 0;
        var shots = new ArrayList<SilentGateRenderer.Shot>();
        for (int i = 0; i < 24; i++) shots.add(new SilentGateRenderer.Shot(i, new Vec3(i, 70, 40), 100 + 320));
        for (int age = 0; age <= 700; age += 5) {
            double command = age < 480 ? -1 : Math.min(age - 480, 372);
            for (int layer = -1; layer <= 1; layer++) {
                for (Vec3 eye : new Vec3[]{Vec3.ZERO, new Vec3(0, 130, 48)}) {
                    int quads = SilentGateRenderer.emitScene(new Mesh(), new Matrix4f(), frame(age, command, 160), shots, eye, layer);
                    peak = Math.max(peak, quads);
                    assertTrue(quads <= 12000, "age " + age + " layer " + layer + " eye " + eye + ": " + quads);
                }
            }
        }
        assertTrue(peak > 1000, "scene should really be drawn, peak " + peak);
    }
    @Test void twoHundredFiftySixCirclesStayUnderTheBudgetEvenFromInsideTheField() {
        int peak = 0;
        Vec3 lane = SilentGateSequence.flight(ANCHOR, 0f, .5);
        for (int age = 285; age <= 480; age += 5)
            for (Vec3 eye : new Vec3[]{Vec3.ZERO, lane, SilentGateSequence.doorCenter(ANCHOR, 0f), new Vec3(0, 160, -120)}) {
                int quads = SilentGateRenderer.emitScene(new Mesh(), new Matrix4f(), frame(age, -1, 256), List.of(), eye, -1);
                peak = Math.max(peak, quads);
                assertTrue(quads <= 14000, "age " + age + " eye " + eye + ": " + quads);
            }
        assertTrue(peak > 3000, "hundreds of circles are really drawn: " + peak);
    }
    private static GateDraw.Ctx ctx(Mesh mesh, Vec3 eye, int layer) {
        GateDraw.Ctx c = new GateDraw.Ctx(); c.v = mesh; c.m = new Matrix4f(); c.eye = eye; c.layer = layer; return c;
    }
    private static final GateFx.Pose POSE = GateFx.Pose.of(new Vec3(0, 0, 0), 0f);

    @Test void gateStaysUnderFourThousandQuadsFromEveryDistance() {
        int peak = 0;
        Vec3[] eyes = {new Vec3(0, 108, -270), new Vec3(0, 90, -36), new Vec3(-42, -9, -18), new Vec3(0, 108, -1200), new Vec3(90, 240, 60), new Vec3(0, 108, 9)};
        for (Vec3 eye : eyes) for (double open : new double[]{0, .5, 1}) {
            GateDraw.Ctx c = ctx(new Mesh(), eye, -1);
            GateModel.draw(c, POSE, 250, open, 1.2);
            peak = Math.max(peak, c.quads);
            assertTrue(c.quads <= 4000, "eye " + eye + " open " + open + ": " + c.quads);
        }
        assertTrue(peak > 700, "the gate should really be sculpted, peak " + peak);
    }
    @Test void gateScalesWithTheSequenceConstants() {
        Mesh mesh = new Mesh();
        Vec3 eye = new Vec3(0, 120, -270);
        GateModel.draw(ctx(mesh, eye, 0), POSE, 250, 0, 0);
        double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
        for (float[] p : mesh.vertices) { minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]); minY = Math.min(minY, p[1] + eye.y); maxY = Math.max(maxY, p[1] + eye.y); }
        double halfW = SilentGateSequence.GATE_WIDTH / 2.0 * SilentGateSequence.SCALE, k = SilentGateSequence.SCALE;
        assertTrue(maxX >= halfW - 3.6 && maxX <= halfW + 1.5 && minX <= -halfW + 3.6 && minX >= -halfW - 1.5, "outer columns span " + minX + ".." + maxX);
        assertTrue(maxY >= (SilentGateSequence.GATE_HEIGHT + 6) * k && maxY <= (SilentGateSequence.GATE_HEIGHT + 11) * k, "finial top " + maxY);
        assertTrue(minY <= 0 && minY >= -11 * k, "column tips and spike " + minY);
        assertTrue(SilentGateSequence.GATE_INNER_WIDTH == 22 && SilentGateSequence.GATE_INNER_HEIGHT == 44, "door opening 22 x 44");
        // The finials stay under the great circle (altitude 100 above the anchor) when the gate is at rest.
        assertTrue(SilentGateSequence.gateBase(0) + maxY < SilentGateSequence.circleAltitude(0), "finials stay under the great circle");
        assertEquals(3, SilentGateSequence.SCALE);
    }
    @Test void doorLeavesSwingOutTowardsTheCasterWithGateOpen() {
        Vec3 eye = new Vec3(0, 120, -240);
        double[] centre = new double[2]; double[] depth = new double[2];
        for (int k = 0; k < 2; k++) {
            Mesh mesh = new Mesh();
            GateModel.draw(ctx(mesh, eye, 0), POSE, 250, k, 0);
            centre[k] = mesh.vertices.stream().filter(p -> p[1] + eye.y > 51 && p[1] + eye.y < 168 && Math.abs(p[0]) < 9).count();
            depth[k] = mesh.vertices.stream().filter(p -> p[1] + eye.y > 51 && p[1] + eye.y < 168 && Math.abs(p[0]) < 34).mapToDouble(p -> -(p[2] + eye.z)).max().orElse(0);
        }
        assertTrue(centre[0] > 20, "closed leaves meet in the middle: " + centre[0]);
        assertEquals(0, centre[1], "open leaves have left the middle");
        assertTrue(depth[1] > 18 && depth[0] < 9, "open leaves swing out towards the caster (-z): " + depth[1] + " vs " + depth[0]);
        assertEquals(SilentGateSequence.gateOpen(300) < SilentGateSequence.gateOpen(340), true);
    }
    @Test void slitShockwaveAndVeinsAppearAtTheirTicks() {
        Vec3 eye = new Vec3(0, 120, -270);
        int[] slit = new int[2], wave = new int[3];
        for (int k = 0; k < 2; k++) { GateDraw.Ctx c = ctx(new Mesh(), eye, 1); GateFx.slit(c, POSE, k, 84); slit[k] = c.quads; }
        assertEquals(0, slit[0]);
        assertTrue(slit[1] > 30, "slit " + slit[1]);
        double[] ages = {270, 280, 310};
        for (int k = 0; k < 3; k++) { GateDraw.Ctx c = ctx(new Mesh(), eye, 1); GateFx.shockwave(c, POSE, ages[k], 274, 120, 34, 1); wave[k] = c.quads; }
        assertEquals(0, wave[0]); assertTrue(wave[1] > 40, "shockwave " + wave[1]); assertEquals(0, wave[2]);
        assertEquals(0, GateModel.vein(230, -1, 0), "veins are dark while falling");
        assertTrue(GateModel.vein(275, -1, 0) > 1, "flare at the shockwave");
        assertTrue(GateModel.vein(400, -1, 0) > .4, "steady glow while waiting");
        assertTrue(GateModel.vein(400, 2, 0) > GateModel.vein(400, -1, 0), "activation flares them");
        // In the scene: the gate is absent before the slit, present after, and the slit itself is drawn from 140.
        Mesh before = new Mesh(), after = new Mesh();
        int a = SilentGateRenderer.emitScene(before, new Matrix4f(), frame(209, -1, 0), List.of(), Vec3.ZERO, 0);
        int b = SilentGateRenderer.emitScene(after, new Matrix4f(), frame(260, -1, 0), List.of(), Vec3.ZERO, 0);
        assertTrue(b - a > 500, "gate body appears: " + a + " -> " + b);
        int g1 = SilentGateRenderer.emitScene(new Mesh(), new Matrix4f(), frame(190, -1, 0), List.of(), Vec3.ZERO, 1);
        int g0 = SilentGateRenderer.emitScene(new Mesh(), new Matrix4f(), frame(160, -1, 0), List.of(), Vec3.ZERO, 1);
        assertTrue(g1 - g0 > 30, "slit glow at 190: " + g0 + " -> " + g1);
    }

    @Test void circleGeometryFadesOutWithinTwelveBlocksOfTheCamera() {
        Vec3 disc = SilentGateSequence.groundCircle(ANCHOR, 0f, 0);
        Mesh near = new Mesh(), far = new Mesh();
        SilentGateRenderer.emitScene(near, new Matrix4f(), frame(520, -1, 0), List.of(), disc);
        SilentGateRenderer.emitScene(far, new Matrix4f(), frame(520, -1, 0), List.of(), Vec3.ZERO);
        // Vertices are camera relative: the camera sits on the first gold disc, so everything within 2.5 blocks of it must be invisible.
        double nearAlpha = near.vertices.stream().filter(p -> Math.hypot(Math.hypot(p[0], p[1]), p[2]) < 2.5).mapToDouble(p -> p[3]).max().orElse(-1);
        double farAlpha = far.vertices.stream().filter(p -> p[1] > ANCHOR.y - 1 && p[1] < ANCHOR.y + 8).mapToDouble(p -> p[3]).max().orElseThrow();
        assertEquals(0, nearAlpha, "disc vanishes around the camera");
        assertTrue(farAlpha > 40, "same disc is visible from afar: " + farAlpha);
    }
    @Test void greatCircleAppearsAtItsAltitudeAndNothingBeforeIt() {
        Mesh early = new Mesh();
        assertEquals(0, emit(early, frame(100, -1, 48), List.of()));
        Mesh mesh = new Mesh();
        emit(mesh, frame(175, -1, 48), List.of());
        double target = SilentGateSequence.circleAltitude(ANCHOR.y);
        assertTrue(mesh.vertices.stream().anyMatch(p -> Math.abs(p[1] - (ANCHOR.y + target)) <= 3));
    }
    @Test void groundRingDiscsStandUprightBehindTheCaster() {
        Mesh mesh = new Mesh();
        emit(mesh, frame(520, -1, 0), List.of());
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (float[] p : mesh.vertices) if (p[1] > ANCHOR.y - 1 && p[1] < ANCHOR.y + 8 && Math.hypot(p[0] - ANCHOR.x, p[2] - ANCHOR.z) < 12) { minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]); }
        // Gold disc (r 2.4) centred 3 above the anchor; its spinning square seal tile reaches a little past the circle.
        assertTrue(minY >= ANCHOR.y + 3 - 3.4 && minY <= ANCHOR.y + 3 - 2.0, "min " + minY);
        assertTrue(maxY <= ANCHOR.y + 3 + 3.4 && maxY >= ANCHOR.y + 3 + 2.0, "max " + maxY);
    }
    @Test void beamsLastEightTicksAndTheEndingFadesToNothing() {
        var shot = new SilentGateRenderer.Shot(3, new Vec3(5, 70, 40), 100 + 320);
        Mesh with = new Mesh(), without = new Mesh();
        int a = emit(with, frame(322, 22, 48), List.of(shot)), b = emit(without, frame(322, 22, 48), List.of());
        assertTrue(a - b > 20, "ribbon, muzzle and impact: " + (a - b));
        assertEquals(emit(new Mesh(), frame(329, 29, 48), List.of()), emit(new Mesh(), frame(329, 29, 48), List.of(shot)), "beam gone after 8 ticks");
        Mesh half = new Mesh(), start = new Mesh(), end = new Mesh();
        double over = SilentGateSequence.ACTIVATE_TICKS + 200;
        emit(start, frame(480 + over, over, 48), List.of());
        emit(half, frame(480 + over + 150, over + 150, 48), List.of());
        assertEquals(0, emit(end, frame(480 + over + 160, over + 160, 48), List.of()));
        assertTrue(half.vertices.stream().mapToDouble(p -> p[3]).max().orElseThrow()
                < start.vertices.stream().mapToDouble(p -> p[3]).max().orElseThrow());
    }
}
