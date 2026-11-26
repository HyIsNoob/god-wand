package net.godwand.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scene built once and routed by layer gives exactly the quads, in the same order, that one pass per layer gives. */
class SilentGateRecordTest {
    private static final Vec3 ANCHOR = new Vec3(0, 70, 0);

    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @AfterEach void reset() { GateDraw.premultiplied = false; }

    /** Every vertex as x, y, z, r, g, b, a, u, v. */
    static final class Mesh implements VertexConsumer {
        final List<float[]> v = new ArrayList<>(); float[] cur;
        public VertexConsumer addVertex(float x, float y, float z) { cur = new float[]{x, y, z, 0, 0, 0, 0, 0, 0}; v.add(cur); return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { cur[3] = r; cur[4] = g; cur[5] = b; cur[6] = a; return this; }
        public VertexConsumer setUv(float u, float w) { cur[7] = u; cur[8] = w; return this; }
        public VertexConsumer setUv1(int u, int w) { return this; }
        public VertexConsumer setUv2(int u, int w) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }

    @Test void routedSceneMatchesOnePassPerLayer() {
        GateDraw.Record rec = new GateDraw.Record();
        int checked = 0;
        for (int age = 0; age <= 480 + 400 + 300; age += 23) {
            double command = age < 480 ? -1 : age - 480;
            var f = new SilentGateRenderer.Frame(1, 100, command < 0 ? 1 : 2, age, command, ANCHOR, 30f, 7L, 220, 400, -1);
            List<SilentGateRenderer.Shot> shots = new ArrayList<>();
            if (command >= 0) for (int i = 0; i < 160; i++)
                shots.add(new SilentGateRenderer.Shot(i % 220, new Vec3(-60 + (i * 7) % 120, ANCHOR.y, -60 + (i * 13) % 120), 100 + 480 + (long) command - i / 5, i % 53 == 0));
            for (Vec3 eye : new Vec3[]{ANCHOR.add(0, 1.6, 0), ANCHOR.add(10, 25, -45), ANCHOR.add(-30, 60, 40), ANCHOR.add(2, 40, 8)}) {
                SilentGateRenderer.record(rec, f, shots, eye);
                for (int layer = 0; layer < 3; layer++) {
                    GateDraw.premultiplied = layer != 0;
                    Mesh pass = new Mesh();
                    int quads = SilentGateRenderer.emitScene(pass, new Matrix4f(), f, shots, eye, layer);
                    GateDraw.premultiplied = false;
                    Mesh routed = new Mesh();
                    rec.replay(layer, routed, new Matrix4f());
                    assertEquals(pass.v.size(), routed.v.size(), "vertices of layer " + layer + " at age " + age + " from " + eye);
                    assertEquals(quads, rec.quads[layer], "quads of layer " + layer + " at age " + age);
                    for (int i = 0; i < pass.v.size(); i++)
                        for (int k = 0; k < 9; k++)
                            if (pass.v.get(i)[k] != routed.v.get(i)[k])
                                throw new AssertionError("layer " + layer + " age " + age + " vertex " + i + " field " + k + ": " + pass.v.get(i)[k] + " != " + routed.v.get(i)[k]);
                    checked += pass.v.size();
                }
            }
        }
        assertTrue(checked > 100_000, "too little geometry compared: " + checked);
    }
}
