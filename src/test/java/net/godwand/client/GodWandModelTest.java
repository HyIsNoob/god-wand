package net.godwand.client;

import com.google.gson.Gson;
import net.godwand.client.model.GodWandModel;
import net.godwand.client.render.GodWandItemRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GodWandModelTest {
    @Test
    void silverTrimDoesNotShareVisiblePlanesWithTheDarkBody() {
        var model = new GodWandModel(GodWandModel.getTexturedModelData().createModel());
        var mesh = new Mesh();
        model.renderWand(new MatrixStack(), () -> mesh, () -> mesh, 0, 0, 0xFFFFE05A);
        for (int i = 0; i < mesh.vertices.size(); i += 4) {
            if (mesh.vertices.get(i)[3] != 40) continue;
            for (int j = 0; j < mesh.vertices.size(); j += 4) {
                if (mesh.vertices.get(j)[3] != 202) continue;
                for (int axis = 0; axis < 3; axis++) {
                    double plane = mesh.vertices.get(i)[axis];
                    boolean coplanar = true;
                    for (int k = 0; k < 4; k++) {
                        coplanar &= Math.abs(mesh.vertices.get(i + k)[axis] - plane) < 0.000001;
                        coplanar &= Math.abs(mesh.vertices.get(j + k)[axis] - plane) < 0.000001;
                    }
                    if (!coplanar) continue;
                    boolean overlaps = true;
                    for (int other = 0; other < 3; other++) {
                        if (other == axis) continue;
                        double aMin = Double.POSITIVE_INFINITY, bMin = aMin;
                        double aMax = Double.NEGATIVE_INFINITY, bMax = aMax;
                        for (int k = 0; k < 4; k++) {
                            aMin = Math.min(aMin, mesh.vertices.get(i + k)[other]);
                            aMax = Math.max(aMax, mesh.vertices.get(i + k)[other]);
                            bMin = Math.min(bMin, mesh.vertices.get(j + k)[other]);
                            bMax = Math.max(bMax, mesh.vertices.get(j + k)[other]);
                        }
                        overlaps &= Math.min(aMax, bMax) - Math.max(aMin, bMin) > 0.00001;
                    }
                    assertFalse(overlaps, "Overlapping dark/silver faces on axis " + axis + " at " + plane);
                }
            }
        }
    }
    @Test
    void chargeAnimationUsesEachSpellsActualFullChargeTime() {
        int[] durations = {0, 45, 68, 140, 80};
        for (int mode = 1; mode < durations.length; mode++) {
            assertEquals(1, GodWandItemRenderer.chargeRatio(mode, durations[mode]));
            assertEquals((durations[mode] / 2) / (float) durations[mode],
                    GodWandItemRenderer.chargeRatio(mode, durations[mode] / 2));
            assertEquals(1, GodWandItemRenderer.chargeRatio(mode, durations[mode] * 2));
            assertEquals(0, GodWandItemRenderer.chargeRatio(mode, -1));
        }
        assertEquals(0, GodWandItemRenderer.chargeRatio(0, 100));
    }

    @Test
    void renderedGeometryIsFiniteAndFloatingTrimStaysAttached() throws Exception {
        var root = GodWandModel.getTexturedModelData().createModel();
        var model = new GodWandModel(root);
        for (float charge : new float[]{0, 0.5f, 1}) {
            model.setupProceduralAnimations(37, charge, 1);
            assertEquals(root.getChild("floating_spire").pivotY, root.getChild("spire_trim").pivotY);
            assertEquals(root.getChild("floating_spire").yaw, root.getChild("spire_trim").yaw);
            assertTrue(root.getChild("singularity_core").pivotY > 16);
            assertTrue(root.getChild("singularity_core").pivotY < 17);
        }
        model.setupProceduralAnimations(0, 0, 0);
        var mesh = new Mesh();
        model.renderWand(new MatrixStack(), () -> mesh, () -> mesh, 0, 0, 0xFFFFE05A);
        assertFalse(mesh.vertices.isEmpty());
        assertEquals(0, mesh.vertices.size() % 4);
        assertEquals(-28 / 16f, mesh.vertices.stream().mapToDouble(v -> v[1]).min().orElseThrow(), 0.001);
        assertEquals(35.5 / 16f, mesh.vertices.stream().mapToDouble(v -> v[1]).max().orElseThrow(), 0.001);
        double halfWidth = mesh.vertices.stream().mapToDouble(v -> Math.max(Math.abs(v[0]), Math.abs(v[2]))).max().orElseThrow();
        assertTrue(halfWidth <= 3.63 / 16, "The head must keep its slimmer silhouette");
        var preview = Path.of("build/previews/god-wand-mesh.json");
        Files.createDirectories(preview.getParent());
        Files.writeString(preview, new Gson().toJson(mesh.vertices));
    }

    @Test
    void firstPersonCoreRemainsInsideBothHandsView() throws Exception {
        var transform = GodWandItemRenderer.class.getDeclaredMethod("applyDisplayTransform", MatrixStack.class,
                net.minecraft.client.render.model.json.ModelTransformationMode.class);
        transform.setAccessible(true);
        for (int sign : new int[]{-1, 1}) {
            var matrices = new MatrixStack();
            matrices.translate(sign * .56f, -.52f, -.72f);
            var mode = sign > 0 ? net.minecraft.client.render.model.json.ModelTransformationMode.FIRST_PERSON_RIGHT_HAND
                    : net.minecraft.client.render.model.json.ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
            transform.invoke(new GodWandItemRenderer(), matrices, mode);
            var model = new GodWandModel(GodWandModel.getTexturedModelData().createModel());
            model.setupProceduralAnimations(0, 0, 0);
            var mesh = new Mesh();
            model.renderWand(matrices, () -> mesh, () -> mesh, 0, 0, 0xFFFFE05A);
            for (float[] v : mesh.vertices) if (v[3] == 255) {
                assertTrue(v[2] < -.05f, "Core must remain in front of the hand camera");
                double tanFov = Math.tan(Math.toRadians(70) / 2);
                assertTrue(Math.abs(v[0] / (-v[2] * tanFov * 16 / 9)) < .9);
                assertTrue(Math.abs(v[1] / (-v[2] * tanFov)) < .9);
            }
        }
    }

    @Test
    void materialSwitchDoesNotWriteIntoAFlushedFirstPersonBuffer() {
        var model = new GodWandModel(GodWandModel.getTexturedModelData().createModel());
        var buffers = new ArrayList<Mesh>();
        int[] active = {0};
        java.util.function.Supplier<VertexConsumer> nextBuffer = () -> {
            int currentLayer = ++active[0];
            var mesh = new Mesh();
            mesh.validate = () -> assertEquals(currentLayer, active[0], "Writing to a flushed material buffer");
            buffers.add(mesh);
            return mesh;
        };
        model.renderWand(new MatrixStack(), nextBuffer, nextBuffer, 0, 0, 0xFFFFE05A);
        assertEquals(2, buffers.size());
        assertTrue(buffers.get(0).vertices.size() > 1500);
        assertEquals(120, buffers.get(1).vertices.size());
        assertTrue(buffers.get(0).vertices.stream().anyMatch(v -> v[3] == 202), "Silver trim must reach the solid buffer");
    }

    private static class Mesh implements VertexConsumer {
        final List<float[]> vertices = new ArrayList<>();
        float[] current;
        Runnable validate = () -> {};
        public VertexConsumer vertex(float x, float y, float z) {
            validate.run();
            assertTrue(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z));
            current = new float[]{x, y, z, 0, 0, 0};
            vertices.add(current);
            return this;
        }
        public VertexConsumer color(int r, int g, int b, int a) {
            current[3] = r; current[4] = g; current[5] = b;
            return this;
        }
        public VertexConsumer texture(float u, float v) { return this; }
        public VertexConsumer overlay(int u, int v) { return this; }
        public VertexConsumer light(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
    }
}
