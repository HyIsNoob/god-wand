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
            if (mesh.vertices.get(i)[3] != 54) continue;
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
        var blade = new Mesh();
        var renderBlade = GodWandModel.class.getDeclaredMethod("renderCrescent", PoseStack.class, VertexConsumer.class, int.class, int.class);
        renderBlade.setAccessible(true);
        renderBlade.invoke(null, new PoseStack(), blade, 0, 0);
        double headDepth = blade.vertices.stream().mapToDouble(v -> v[2]).max().orElseThrow()
                - blade.vertices.stream().mapToDouble(v -> v[2]).min().orElseThrow();
        assertTrue(headDepth > .14 && headDepth < .22, "The crescent needs real depth without becoming a slab");
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
                "The luminous sphere must have an opaque body, not rely on a translucent shader pass");
        assertFalse(buffers.get(0).vertices.isEmpty());
        assertFalse(buffers.get(1).vertices.isEmpty());
        assertTrue(buffers.get(0).vertices.stream().anyMatch(v -> v[3] == 184), "Gold trim must reach the solid buffer");
    }

    @Test
    void idleCoreIsSphericalAndItsSatelliteKeepsOrbiting() {
        var root = GodWandModel.getTexturedModelData().bakeRoot();
        var model = new GodWandModel(root);
        model.setupProceduralAnimations(0, 0, 0);
        var solid = new Mesh();
        var energy = new Mesh();
        model.renderWand(new PoseStack(), () -> solid, () -> energy, 0, 0, 0xFF00FF00);
        var sphere = solid.vertices.stream().filter(v -> v[4] > v[3] && v[4] > v[5]).toList();
        assertTrue(sphere.size() > 200);
        for (var v : sphere) {
            double x = v[0] + .55 / 16, y = v[1] - 21.2 / 16, z = v[2];
            assertEquals(1.9 / 16, Math.sqrt(x*x + y*y + z*z), .00001,
                    "The idle energy body must be spherical, not a cut crystal");
        }
        assertFalse(energy.vertices.isEmpty(), "Idle glow and the satellite must stay visible");
        float before = root.getChild("orbit").getChild("satellite").yRot;
        model.setupProceduralAnimations(30, 0, 0);
        assertNotEquals(before, root.getChild("orbit").getChild("satellite").yRot);
    }

    @Test
    void crescentHasNoOpenSeamsOrInternalSegmentCaps() throws Exception {
        var render = GodWandModel.class.getDeclaredMethod("renderCrescent", PoseStack.class, VertexConsumer.class, int.class, int.class);
        render.setAccessible(true);
        var mesh = new Mesh();
        render.invoke(null, new PoseStack(), mesh, 0, 0);
        assertFalse(mesh.vertices.isEmpty());
        var edges = new java.util.HashMap<String, Integer>();
        for (int i = 0; i < mesh.vertices.size(); i += 4) for (int j = 0; j < 4; j++) {
            float[] a = mesh.vertices.get(i+j), b = mesh.vertices.get(i+(j+1)%4);
            String first = a[0]+","+a[1]+","+a[2], second = b[0]+","+b[1]+","+b[2];
            if (first.equals(second)) continue;
            String key = first.compareTo(second) < 0 ? first+"/"+second : second+"/"+first;
            edges.merge(key, 1, Integer::sum);
        }
        for (int count : edges.values()) assertEquals(2, count,
                "Every blade edge must belong to exactly two faces; no cracks or overlapping segment caps");
    }

    @Test
    void crescentActuallySamplesTheObsidianAndGoldTextureTiles() throws Exception {
        var mesh = new Mesh();
        var render = GodWandModel.class.getDeclaredMethod("renderCrescent", PoseStack.class, VertexConsumer.class, int.class, int.class);
        render.setAccessible(true);
        render.invoke(null, new PoseStack(), mesh, 0, 0);
        double minU = 1, maxU = 0, minV = 1, maxV = 0;
        for (var v : mesh.vertices) {
            boolean gold = v[3] == 184;
            assertTrue(v[7] >= (gold ? .5f : 0) && v[7] < (gold ? 1 : .5f));
            assertTrue(v[8] >= 0 && v[8] < .5f);
            double materialU = v[7] - (gold ? .5 : 0);
            minU = Math.min(minU, materialU); maxU = Math.max(maxU, materialU);
            minV = Math.min(minV, v[8]); maxV = Math.max(maxV, v[8]);
        }
        assertTrue(maxU-minU > .1 && maxV-minV > .1,
                "Flat sampling at one texel cannot show material grain");
    }

    @Test
    void neckTopIsBuriedInsideTheCrescentFromEverySide() throws Exception {
        var mesh = new Mesh();
        var blade = GodWandModel.class.getDeclaredMethod("renderCrescent", PoseStack.class, VertexConsumer.class, int.class, int.class);
        blade.setAccessible(true);
        blade.invoke(null, new PoseStack(), mesh, 0, 0);
        var neck = GodWandModel.class.getDeclaredMethod("neckPoint", int.class, int.class);
        neck.setAccessible(true);
        for (int side = 0; side < 16; side++) {
            var point = ((org.joml.Vector3f)neck.invoke(null, 8, side)).div(16);
            assertTrue(insideBlade(mesh.vertices, point), "Neck top protrudes outside the blade at side " + side);
        }
    }

    @Test
    void idleSatelliteHasVisibleVolumeAndATiltedEnergyRing() throws Exception {
        var model = new GodWandModel(GodWandModel.getTexturedModelData().bakeRoot());
        model.setupProceduralAnimations(0, 0, 0);
        var energy = new Mesh();
        model.renderWand(new PoseStack(), Mesh::new, () -> energy, 0, 0, 0xFF00FF00);
        var satellite = energy.vertices.stream().filter(v -> v[6] == 255).toList();
        assertFalse(satellite.isEmpty());
        double width = satellite.stream().mapToDouble(v -> v[0]).max().orElseThrow()
                - satellite.stream().mapToDouble(v -> v[0]).min().orElseThrow();
        assertTrue(width >= .55 / 16, "The satellite should read as a small orb, not a single pixel");
        assertTrue(energy.vertices.stream().anyMatch(v -> v[6] > 60 && v[6] < 220), "The orbit ring must be visible at idle");
        var ringMesh = new Mesh();
        var ring = GodWandModel.class.getDeclaredMethod("renderOrbitRing", PoseStack.class, VertexConsumer.class, int.class, int.class);
        ring.setAccessible(true);
        ring.invoke(null, new PoseStack(), ringMesh, 0, 0xFFFFFFFF);
        assertFalse(ringMesh.vertices.isEmpty());
        for (int i = 0; i < ringMesh.vertices.size(); i += 4) {
            var a = new org.joml.Vector3f(ringMesh.vertices.get(i));
            var b = new org.joml.Vector3f(ringMesh.vertices.get(i+1));
            var c = new org.joml.Vector3f(ringMesh.vertices.get(i+2));
            var d = new org.joml.Vector3f(ringMesh.vertices.get(i+3));
            var center = new org.joml.Vector3f(a).add(b).add(c).add(d).mul(.25f);
            var tubeCenter = new org.joml.Vector3f(center.x, 0, center.z).normalize().mul(2.75f/16);
            var outward = new org.joml.Vector3f(center).sub(tubeCenter);
            var normal = new org.joml.Vector3f(b).sub(a).cross(new org.joml.Vector3f(c).sub(a));
            assertTrue(normal.dot(outward) > 0, "Orbit ring faces must point outward for shader culling");
        }
    }

    private static boolean insideBlade(List<float[]> vertices, org.joml.Vector3f origin) {
        var direction = new org.joml.Vector3f(.173f, .291f, 1).normalize();
        int hits = 0;
        for (int i = 0; i < vertices.size(); i += 4) for (int[] triangle : new int[][]{{0,1,2},{0,2,3}}) {
            var a = new org.joml.Vector3f(vertices.get(i+triangle[0]));
            var edge1 = new org.joml.Vector3f(vertices.get(i+triangle[1])).sub(a);
            var edge2 = new org.joml.Vector3f(vertices.get(i+triangle[2])).sub(a);
            var cross = new org.joml.Vector3f(direction).cross(edge2);
            float determinant = edge1.dot(cross);
            if (Math.abs(determinant) < 1e-8f) continue;
            var offset = new org.joml.Vector3f(origin).sub(a);
            float u = offset.dot(cross) / determinant;
            if (u < 0 || u > 1) continue;
            var q = new org.joml.Vector3f(offset).cross(edge1);
            float v = direction.dot(q) / determinant;
            if (v < 0 || u+v > 1) continue;
            if (edge2.dot(q) / determinant > 1e-6f) hits++;
        }
        return hits % 2 == 1;
    }

    @Test
    void chargedAndCancelledStaffNeverCrossesTheCameraOrCoversTheCrosshair() throws Exception {
        var display = GodWandItemRenderer.class.getDeclaredMethod("applyDisplayTransform", PoseStack.class, net.minecraft.world.item.ItemDisplayContext.class);
        display.setAccessible(true);
        var use = assertDoesNotThrow(() -> GodWandItemRenderer.class.getDeclaredMethod("applyUseTransform", PoseStack.class, float.class, float.class));
        use.setAccessible(true);
        for (int hand : new int[]{-1, 1}) for (float[] state : new float[][]{{0,0},{.5f,0},{1,0},{0,GodWandItemRenderer.releaseRecoil(2)}}) {
            var pose = new PoseStack();
            pose.translate(hand*.56f, -.52f, -.72f);
            display.invoke(new GodWandItemRenderer(), pose, hand > 0 ? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
            use.invoke(null, pose, state[0], state[1]);
            var model = new GodWandModel(GodWandModel.getTexturedModelData().bakeRoot());
            model.setupProceduralAnimations(20, state[0], 3);
            var mesh = new Mesh();
            model.renderWand(pose, () -> mesh, () -> mesh, 0, 0, 0xFF00FF00);
            for (float[] v : mesh.vertices) {
                assertTrue(v[2] < -.08f, "Charging or release must not pull geometry through the camera");
                for (int fov : new int[]{50,70,90}) {
                    double x = v[0]/(-v[2]*Math.tan(Math.toRadians(fov)/2)*16/9);
                    assertTrue(hand*x > .12, "The central aiming area must stay clear while charging or cancelling");
                }
            }
        }
    }

    private static class Mesh implements VertexConsumer {
        final List<float[]> vertices = new ArrayList<>();
        float[] current;
        Runnable validate = () -> {};
        public VertexConsumer addVertex(float x, float y, float z) {
            validate.run();
            assertTrue(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z));
            current = new float[]{x, y, z, 0, 0, 0, 255, 0, 0};
            vertices.add(current);
            return this;
        }
        public VertexConsumer setColor(int r, int g, int b, int a) {
            current[3] = r; current[4] = g; current[5] = b; current[6] = a;
            return this;
        }
        public VertexConsumer setUv(float u, float v) { current[7] = u; current[8] = v; return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
