package net.godwand.client;

import com.google.gson.Gson;
import net.godwand.client.model.GodWandModel;
import net.godwand.client.render.GodWandItemRenderer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GodWandModelTest {
    @Test
    void goldTrimDoesNotShareVisiblePlanesWithTheObsidian() {
        var model = new GodWandModel(GodWandModel.getTexturedModelData().bakeRoot());
        var mesh = new Mesh();
        model.renderWand(new PoseStack(), () -> mesh, () -> mesh, 0, 0, 0xFFFFE05A);
        for (int i = 0; i < mesh.vertices.size(); i += 4) {
            if (mesh.vertices.get(i)[3] != 36) continue;
            for (int j = 0; j < mesh.vertices.size(); j += 4) {
                if (mesh.vertices.get(j)[3] != 184) continue;
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
                    assertFalse(overlaps, "Overlapping obsidian/gold faces on axis " + axis + " at " + plane);
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
    void eclipseKeepsCoreCenteredAndHoldingOriginInsideLeather() throws Exception {
        var root = GodWandModel.getTexturedModelData().bakeRoot();
        var model = new GodWandModel(root);
        for (float charge : new float[]{0, 0.5f, 1}) {
            model.setupProceduralAnimations(37, charge, 1);
            assertEquals(21.2f, root.getChild("singularity_core").y, 0.001);
            assertNotEquals(0, root.getChild("singularity_core").yRot);
            assertEquals(0, root.getChild("obsidian").yRot);
        }
        model.setupProceduralAnimations(0, 0, 0);
        var mesh = new Mesh();
        model.renderWand(new PoseStack(), () -> mesh, () -> mesh, 0, 0, 0xFFFFE05A);
        assertFalse(mesh.vertices.isEmpty());
        assertEquals(0, mesh.vertices.size() % 4);
        double height = mesh.vertices.stream().mapToDouble(v -> v[1]).max().orElseThrow()
                - mesh.vertices.stream().mapToDouble(v -> v[1]).min().orElseThrow();
        assertEquals(1.75, height * .44, .08, "Third-person staff must be full-length without the needle-thin proportions");
        double headDepth = mesh.vertices.stream().filter(v -> v[1] > 1).mapToDouble(v -> v[2]).max().orElseThrow()
                - mesh.vertices.stream().filter(v -> v[1] > 1).mapToDouble(v -> v[2]).min().orElseThrow();
        assertTrue(headDepth < .18, "The crescent must remain thin");
        var grip = mesh.vertices.stream().filter(v -> v[3] == 99).toList();
        assertFalse(grip.isEmpty(), "Red leather must be a real solid model part");
        double gripWidth = grip.stream().mapToDouble(v -> v[0]).max().orElseThrow()
                - grip.stream().mapToDouble(v -> v[0]).min().orElseThrow();
        assertTrue(gripWidth >= .12, "The grip needs enough volume to read as a staff in third person");
        assertTrue(grip.stream().anyMatch(v -> v[1] < 0) && grip.stream().anyMatch(v -> v[1] > 0),
                "The holding origin must lie within the leather grip");
        var preview = Path.of("build/previews/god-wand-mesh.json");
        Files.createDirectories(preview.getParent());
        var visibleMesh = new Mesh();
        model.renderWand(new PoseStack(), () -> visibleMesh, () -> visibleMesh, 0, 0, GodWandItemRenderer.coreColor(0, 0));
        Files.writeString(preview, new Gson().toJson(visibleMesh.vertices));
    }

    @Test
    void firstPersonCoreRemainsInsideBothHandsView() throws Exception {
        var transform = GodWandItemRenderer.class.getDeclaredMethod("applyDisplayTransform", PoseStack.class,
                net.minecraft.world.item.ItemDisplayContext.class);
        transform.setAccessible(true);
        for (int sign : new int[]{-1, 1}) {
            var matrices = new PoseStack();
            matrices.translate(sign * .56f, -.52f, -.72f);
            var mode = sign > 0 ? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                    : net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            transform.invoke(new GodWandItemRenderer(), matrices, mode);
            var gripCenter = matrices.last().pose().transformPosition(new org.joml.Vector3f());
            assertTrue(gripCenter.distance(new org.joml.Vector3f(sign * .56f, -.52f, -.72f)) < .06f,
                    "The hand anchor must stay inside the red leather sleeve");
            var model = new GodWandModel(GodWandModel.getTexturedModelData().bakeRoot());
            model.setupProceduralAnimations(0, 0, 0);
            var mesh = new Mesh();
            model.renderWand(matrices, () -> mesh, () -> mesh, 0, 0, 0xFF00FF00);
            var preview = Path.of("build/previews/eclipse-first-person-" + (sign > 0 ? "right" : "left") + ".json");
            Files.createDirectories(preview.getParent());
            var visibleMesh = new Mesh();
            model.renderWand(matrices, () -> visibleMesh, () -> visibleMesh, 0, 0, GodWandItemRenderer.coreColor(0, 0));
            Files.writeString(preview, new Gson().toJson(visibleMesh.vertices));
            assertTrue(mesh.vertices.stream().anyMatch(v -> v[4] > v[3] && v[4] > v[5]));
            for (float[] v : mesh.vertices) if (v[4] > v[3] && v[4] > v[5]) {
                assertTrue(v[2] < -.05f, "Core must remain in front of the hand camera");
                double tanFov = Math.tan(Math.toRadians(70) / 2);
                assertTrue(Math.abs(v[0] / (-v[2] * tanFov * 16 / 9)) < .9);
                assertTrue(Math.abs(v[1] / (-v[2] * tanFov)) < .9);
            }
        }
    }

    @Test
    void materialSwitchDoesNotWriteIntoAFlushedFirstPersonBuffer() {
        var model = new GodWandModel(GodWandModel.getTexturedModelData().bakeRoot());
        model.setupProceduralAnimations(17, .5f, 1);
        var buffers = new ArrayList<Mesh>();
        int[] active = {0};
        java.util.function.Supplier<VertexConsumer> nextBuffer = () -> {
            int currentLayer = ++active[0];
            var mesh = new Mesh();
            mesh.validate = () -> assertEquals(currentLayer, active[0], "Writing to a flushed material buffer");
            buffers.add(mesh);
            return mesh;
        };
        model.renderWand(new PoseStack(), nextBuffer, nextBuffer, 0, 0, 0xFF00FF00);
        assertEquals(2, buffers.size());
        assertTrue(buffers.get(0).vertices.stream().anyMatch(v -> v[4] > v[3] && v[4] > v[5]),
                "The faceted core must have an opaque body, not rely on a translucent shader pass");
        assertFalse(buffers.get(0).vertices.isEmpty());
        assertFalse(buffers.get(1).vertices.isEmpty());
        assertTrue(buffers.get(0).vertices.stream().anyMatch(v -> v[3] == 184), "Gold trim must reach the solid buffer");
    }

    private static class Mesh implements VertexConsumer {
        final List<float[]> vertices = new ArrayList<>();
        float[] current;
        Runnable validate = () -> {};
        public VertexConsumer addVertex(float x, float y, float z) {
            validate.run();
            assertTrue(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z));
            current = new float[]{x, y, z, 0, 0, 0};
            vertices.add(current);
            return this;
        }
        public VertexConsumer setColor(int r, int g, int b, int a) {
            current[3] = r; current[4] = g; current[5] = b;
            return this;
        }
        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
