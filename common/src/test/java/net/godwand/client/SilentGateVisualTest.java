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
    @Test void greatCircleAppearsAtItsAltitudeAndNothingBeforeIt() {
        Mesh early = new Mesh();
        assertEquals(0, emit(early, frame(50, -1, 48), List.of()));
        Mesh mesh = new Mesh();
        emit(mesh, frame(100, -1, 48), List.of());
        double target = SilentGateSequence.CIRCLE_ALTITUDE;
        assertTrue(mesh.vertices.stream().anyMatch(p -> Math.abs(p[1] - (ANCHOR.y + target)) <= 3));
    }
    @Test void groundRingDiscsStandUprightAtHeadHeight() {
        Mesh mesh = new Mesh();
        emit(mesh, frame(300, -1, 0), List.of());
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (float[] p : mesh.vertices) if (p[1] < ANCHOR.y + 10) { minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]); }
        // Gold disc (r 2.4) centred 2.5 above ground: spans about 0.1..4.9 above the anchor.
        assertEquals(ANCHOR.y + 2.5 - 2.4, minY, .01);
        assertEquals(ANCHOR.y + 2.5 + 2.4, maxY, .01);
    }
    @Test void beamsLastSixTicksAndTheEndingFadesToNothing() {
        var shot = new SilentGateRenderer.Shot(3, new Vec3(5, 70, 40), 100 + 320);
        Mesh with = new Mesh(), without = new Mesh();
        int a = emit(with, frame(320, 20, 48), List.of(shot)), b = emit(without, frame(320, 20, 48), List.of());
        assertEquals(2, a - b, "two crossed quads");
        assertEquals(emit(new Mesh(), frame(327, 27, 48), List.of()), emit(new Mesh(), frame(327, 27, 48), List.of(shot)), "beam gone after 6 ticks");
        Mesh half = new Mesh(), start = new Mesh(), end = new Mesh();
        double over = SilentGateSequence.ACTIVATE_TICKS + 200;
        emit(start, frame(300 + over, over, 48), List.of());
        emit(half, frame(300 + over + 30, over + 30, 48), List.of());
        assertEquals(0, emit(end, frame(300 + over + 60, over + 60, 48), List.of()));
        assertTrue(half.vertices.stream().mapToDouble(p -> p[3]).max().orElseThrow()
                < start.vertices.stream().mapToDouble(p -> p[3]).max().orElseThrow());
    }
}
