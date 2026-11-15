package net.godwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.godwand.client.model.SilentWitchModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SilentWitchModelTest {

    @Test
    void silentWitchStaffRendersSolidAndEmissiveWithoutBufferInvalidation() {
        var root = SilentWitchModel.getTexturedModelData().bakeRoot();
        var model = new SilentWitchModel(root);

        for (float charge : new float[]{0.0f, 0.5f, 1.0f}) {
            model.setupProceduralAnimations(42.0f, charge, 7);
            assertEquals(24.15f, root.getChild("mana_core").y, 0.01f);
            assertNotEquals(0.0f, root.getChild("mana_core").yRot);
            assertNotEquals(0.0f, root.getChild("left_pendant").zRot);
            assertNotEquals(0.0f, root.getChild("right_pendant").zRot);
        }

        var solid = new Mesh();
        var emissive = new Mesh();
        Mesh[] active = {null};
        solid.validate = () -> assertSame(solid, active[0], "Solid buffer invalidated by another render layer");
        emissive.validate = () -> assertSame(emissive, active[0], "Emissive buffer invalidated by another render layer");

        model.setupProceduralAnimations(20.0f, 0.5f, 7);
        model.renderWand(new PoseStack(),
                () -> { active[0] = solid; return solid; },
                () -> { active[0] = emissive; return emissive; },
                0x00F000F0, 0, 0xFFAC66F5);

        assertFalse(solid.vertices.isEmpty(), "Solid geometry must not be empty");
        assertFalse(emissive.vertices.isEmpty(), "Emissive geometry must not be empty");
        assertEquals(0, solid.vertices.size() % 4, "All faces must be quads");
        assertEquals(0, emissive.vertices.size() % 4, "All emissive faces must be quads");

        // Verify each quad stays within a single material tile quadrant
        for (var mesh : List.of(solid, emissive)) {
            for (int i = 0; i < mesh.vertices.size(); i += 4) {
                var first = mesh.vertices.get(i);
                for (int j = 0; j < 4; j++) {
                    var vertex = mesh.vertices.get(i + j);
                    assertEquals((int) (first[7] * 2), (int) (vertex[7] * 2), "Each face must stay inside one material column");
                    assertEquals((int) (first[8] * 2), (int) (vertex[8] * 2), "Each face must stay inside one material row");
                }
            }
        }

        // Verify holding origin (Y = 0) lies inside the grip section
        var grip = solid.vertices.stream().filter(v -> v[3] == 0xC8 && v[4] == 0x9B && v[5] == 0x3C).toList();
        assertFalse(grip.isEmpty(), "Grip section must exist");
        assertTrue(grip.stream().anyMatch(v -> v[1] < 0), "Grip must extend below Y = 0");
        assertTrue(grip.stream().anyMatch(v -> v[1] > 0), "Grip must extend above Y = 0");

        // Verify total height matches full-length mage staff proportions
        double maxY = solid.vertices.stream().mapToDouble(v -> v[1]).max().orElseThrow();
        double minY = solid.vertices.stream().mapToDouble(v -> v[1]).min().orElseThrow();
        double height = maxY - minY;
        assertTrue(height * 0.44 >= 1.9 && height * 0.44 <= 2.65,
                "Third-person staff height must match full-length staff proportions: " + (height * 0.44));
    }

    private static class Mesh implements VertexConsumer {
        final List<float[]> vertices = new ArrayList<>();
        float[] current;
        Runnable validate = () -> {};

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            validate.run();
            assertTrue(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z));
            current = new float[]{x, y, z, 0, 0, 0, 255, 0, 0};
            vertices.add(current);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            current[3] = r; current[4] = g; current[5] = b; current[6] = a;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            current[7] = u; current[8] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) { return this; }

        @Override
        public VertexConsumer setUv2(int u, int v) { return this; }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            assertTrue(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z));
            return this;
        }
    }
}
