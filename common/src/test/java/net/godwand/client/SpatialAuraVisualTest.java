package net.godwand.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SpatialAuraVisualTest {
    @Test
    void ribbonsStayOnTheShellAboveTheFeetAndLeaveTheViewAheadClear() throws Exception {
        for (double time : new double[]{0, 17, 80, 40000}) {
            Mesh mesh = new Mesh();
            SpatialAuraRenderer.emitField(mesh, new Matrix4f(), new Vec3(0, 0, -1), time, true);
            assertTrue(mesh.vertices.stream().filter(v -> v[6] > 1).count() > 100);
            for (float[] v : mesh.vertices) {
                for (float value : v) assertTrue(Float.isFinite(value));
                assertTrue(v[6] >= 0 && v[6] <= 215);
                if (v[6] <= 1) continue;
                double radius = Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
                assertEquals(3.3, radius, 0.04);
                assertTrue(v[1] >= -1.5, "Do not draw ribbons below the player's feet");
                assertTrue(-v[2] / radius < 0.92, "First person must have a clear forward cone");
            }
        }
        Mesh mesh = new Mesh();
        SpatialAuraRenderer.emitField(mesh, new Matrix4f(), new Vec3(0, 0, -1), 80, false);
        Path preview = Path.of("build/previews/spatial-aura-mesh.json");
        Files.createDirectories(preview.getParent());
        Files.writeString(preview, new Gson().toJson(mesh.vertices));
    }

    @Test
    void streamsFadeBeforeWrappingBackToTheBottom() {
        for (double time : new double[]{166, 249, 374}) {
            Mesh a = new Mesh(), b = new Mesh();
            SpatialAuraRenderer.emitField(a, new Matrix4f(), new Vec3(0, 0, -1), time, false);
            SpatialAuraRenderer.emitField(b, new Matrix4f(), new Vec3(0, 0, -1), time + 1, false);
            for (int i = 0; i < a.vertices.size(); i++) {
                float[] before = a.vertices.get(i), after = b.vertices.get(i);
                double jump = Math.sqrt(Math.pow(before[0]-after[0], 2)
                        + Math.pow(before[1]-after[1], 2) + Math.pow(before[2]-after[2], 2));
                if (jump > 1) {
                    assertTrue(before[6] <= 3 && after[6] <= 3, "Do not teleport a visible ribbon back down");
                }
            }
        }
    }

    @Test
    void flowingRibbonsMoveUpwardWithoutBecomingAStaticLattice() {
        Mesh a = new Mesh(), b = new Mesh();
        SpatialAuraRenderer.emitField(a, new Matrix4f(), new Vec3(0, 0, -1), 10, false);
        SpatialAuraRenderer.emitField(b, new Matrix4f(), new Vec3(0, 0, -1), 11, false);
        assertFalse(a.vertices.isEmpty());
        assertEquals(a.vertices.size(), b.vertices.size());
        assertTrue(b.vertices.get(64)[1] > a.vertices.get(64)[1], "Streams should rise with time");
        assertNotEquals(a.vertices.get(64)[0], b.vertices.get(64)[0], "Streams should curve as they rise");
    }

    private static class Mesh implements VertexConsumer {
        final List<float[]> vertices = new ArrayList<>();
        float[] current;
        public VertexConsumer addVertex(float x, float y, float z) {
            current = new float[]{x,y,z,0,0,0,0}; vertices.add(current); return this;
        }
        public VertexConsumer setColor(int r,int g,int b,int a) {
            current[3]=r;current[4]=g;current[5]=b;current[6]=a;return this;
        }
        public VertexConsumer setUv(float u,float v) { return this; }
        public VertexConsumer setUv1(int u,int v) { return this; }
        public VertexConsumer setUv2(int u,int v) { return this; }
        public VertexConsumer setNormal(float x,float y,float z) { return this; }
    }
}
