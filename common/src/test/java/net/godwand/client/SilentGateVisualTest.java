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
        return new SilentGateRenderer.Frame(1, 100, 2, age, commandAge, ANCHOR, 0f, 7L, circles, 200);
    }
    private static int emit(Mesh mesh, SilentGateRenderer.Frame f, List<SilentGateRenderer.Shot> shots) {
        return SilentGateRenderer.emitScene(mesh, new Matrix4f(), f, shots, Vec3.ZERO);
    }

    @Test void quadBudgetHoldsAtEveryAgeWith96Circles() {
        for (int age = 0; age <= 300 + 300 + 272 + 15; age += 5) {
            double command = age < 300 ? -1 : Math.min(age - 300, 272);
            Mesh mesh = new Mesh();
            int quads = emit(mesh, frame(age, command, 96), List.of());
            assertTrue(quads <= SilentGateRenderer.QUAD_BUDGET, "age " + age + ": " + quads);
            assertEquals(quads * 4, mesh.vertices.size());
        }
    }
    @Test void fullSceneStaysUnderEightThousandQuads() {
        int peak = 0;
        var shots = new ArrayList<SilentGateRenderer.Shot>();
        for (int i = 0; i < 24; i++) shots.add(new SilentGateRenderer.Shot(i, new Vec3(i, 70, 40), 100 + 320));
        for (int age = 0; age <= 700; age += 5) {
            double command = age < 300 ? -1 : Math.min(age - 300, 272);
            for (int layer = -1; layer <= 1; layer++) {
                for (Vec3 eye : new Vec3[]{Vec3.ZERO, new Vec3(0, 130, 48)}) {
                    int quads = SilentGateRenderer.emitScene(new Mesh(), new Matrix4f(), frame(age, command, 96), shots, eye, layer);
                    peak = Math.max(peak, quads);
                    assertTrue(quads <= 8000, "age " + age + " layer " + layer + " eye " + eye + ": " + quads);
                }
            }
        }
        assertTrue(peak > 1000, "scene should really be drawn, peak " + peak);
    }
    @Test void circleGeometryFadesOutWithinTwelveBlocksOfTheCamera() {
        Vec3 disc = SilentGateSequence.groundCircle(ANCHOR, 0f, 0);
        Mesh near = new Mesh(), far = new Mesh();
        SilentGateRenderer.emitScene(near, new Matrix4f(), frame(300, -1, 0), List.of(), disc);
        SilentGateRenderer.emitScene(far, new Matrix4f(), frame(300, -1, 0), List.of(), Vec3.ZERO);
        // Vertices are camera relative: the camera sits on the first gold disc, so everything within 2.5 blocks of it must be invisible.
        double nearAlpha = near.vertices.stream().filter(p -> Math.hypot(Math.hypot(p[0], p[1]), p[2]) < 2.5).mapToDouble(p -> p[3]).max().orElse(-1);
        double farAlpha = far.vertices.stream().filter(p -> p[1] > ANCHOR.y - 1 && p[1] < ANCHOR.y + 8).mapToDouble(p -> p[3]).max().orElseThrow();
        assertEquals(0, nearAlpha, "disc vanishes around the camera");
        assertTrue(farAlpha > 40, "same disc is visible from afar: " + farAlpha);
    }
    @Test void greatCircleAppearsAtItsAltitudeAndNothingBeforeIt() {
        Mesh early = new Mesh();
        assertEquals(0, emit(early, frame(50, -1, 48), List.of()));
        Mesh mesh = new Mesh();
        emit(mesh, frame(100, -1, 48), List.of());
        double target = SilentGateSequence.CIRCLE_ALTITUDE;
        assertTrue(mesh.vertices.stream().anyMatch(p -> Math.abs(p[1] - (ANCHOR.y + target)) <= 3));
    }
    @Test void groundRingDiscsStandUprightBehindTheCaster() {
        Mesh mesh = new Mesh();
        emit(mesh, frame(300, -1, 0), List.of());
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (float[] p : mesh.vertices) if (p[1] > ANCHOR.y - 1 && p[1] < ANCHOR.y + 8) { minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]); }
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
        emit(start, frame(300 + over, over, 48), List.of());
        emit(half, frame(300 + over + 30, over + 30, 48), List.of());
        assertEquals(0, emit(end, frame(300 + over + 60, over + 60, 48), List.of()));
        assertTrue(half.vertices.stream().mapToDouble(p -> p[3]).max().orElseThrow()
                < start.vertices.stream().mapToDouble(p -> p[3]).max().orElseThrow());
    }
}
