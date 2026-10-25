package net.godwand.client;

import com.google.gson.Gson;
import net.godwand.client.model.GodWandModel;
import net.godwand.client.render.GodWandItemRenderer;
import net.godwand.network.CannonPressurePayload;
import net.godwand.network.SpatialAuraPayload;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import io.netty.buffer.Unpooled;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ImpactVisualTest {
    @Test
    void novaPressureTravelsAndUnknownTerrainDoesNotEmitFloatingLight() {
        var burst = new ImpactDebrisRenderer.Burst(Vec3.ZERO, new Vec3(0, 1, 0), 122, 2, 0);
        Mesh early = new Mesh(), later = new Mesh();
        ImpactDebrisRenderer.emit(early, new Matrix4f(), burst, 12);
        ImpactDebrisRenderer.emit(later, new Matrix4f(), burst, 24);
        var a = early.vertices.getFirst();
        var b = later.vertices.getFirst();
        assertTrue(Math.hypot(b[0], b[2]) > Math.hypot(a[0], a[2]) + 20);
        assertTrue(later.vertices.stream().anyMatch(v -> v[3] == 255 && v[4] >= 238 && v[6] > 100));
        for (double[] row : burst.surface) Arrays.fill(row, Double.NaN);
        Mesh unknown = new Mesh();
        ImpactDebrisRenderer.emit(unknown, new Matrix4f(), burst, 24);
        assertTrue(unknown.vertices.stream().allMatch(v -> v[6] == 0));
    }

    @Test
    void contactRippleFollowsTheShellAndDisappearsWithoutBlockingTheView() throws Exception {
        Mesh outside = new Mesh(), inside = new Mesh(), expired = new Mesh();
        Vec3 normal = new Vec3(0, 0, -1);
        SpatialAuraRenderer.emitHit(outside, new Matrix4f(), normal, 0.3, normal, false);
        SpatialAuraRenderer.emitHit(inside, new Matrix4f(), normal, 0.3, normal, true);
        SpatialAuraRenderer.emitHit(expired, new Matrix4f(), normal, 1, normal, false);
        assertFalse(outside.vertices.isEmpty());
        assertTrue(expired.vertices.isEmpty());
        assertTrue(inside.vertices.stream().mapToDouble(v -> v[6]).sum() < outside.vertices.stream().mapToDouble(v -> v[6]).sum() * 0.25);
        for (float[] v : outside.vertices) {
            double radius = Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);
            assertTrue(radius >= 3.1 && radius <= 3.31);
        }
        save("aura-contact", outside);
    }

    @Test
    void orientedMeshIsBoundedFiniteAndExpiresForFloorWallAndNova() throws Exception {
        for (int kind = 0; kind < 3; kind++) {
            var burst = new ImpactDebrisRenderer.Burst(Vec3.ZERO, kind == 0 ? new Vec3(0,0,1) : new Vec3(0,1,0), kind == 2 ? 122 : 12, kind, 0);
            for (double age : new double[]{0, 4, 12, 24, 40, 75, 100, 140}) {
                Mesh mesh = new Mesh();
                ImpactDebrisRenderer.emit(mesh, new Matrix4f(), burst, age);
                assertEquals(0, mesh.vertices.size() % 4);
                assertTrue(mesh.vertices.size() <= (kind == 2 ? 11500 : 4500));
                for (float[] v : mesh.vertices) {
                    for (float value : v) assertTrue(Float.isFinite(value));
                    assertTrue(v[6] >= 0 && v[6] <= 255);
                }
                if (age >= (kind == 2 ? 140 : 75)) assertTrue(mesh.vertices.isEmpty());
                if (kind == 2 && age == 100) assertFalse(mesh.vertices.isEmpty(), "Embers outlive the pressure front");
                save("impact-" + kind + "-" + (int)age, mesh);
            }
        }
        assertEquals(0, ImpactDebrisRenderer.waveRadius(122, 5, true));
        assertEquals(122, ImpactDebrisRenderer.waveRadius(122, 48, true));
        Vec3 wallShot = ImpactDebrisRenderer.fragmentOffset(new Vec3(0,0,1),new Vec3(1,0,0),6,1);
        assertTrue(wallShot.z > 0 && wallShot.x > 0 && wallShot.y < 0);
    }

    @Test
    void chargePartsResetWhenSwitchingSkillsAndRecoilSettles() {
        var root = GodWandModel.getTexturedModelData().bakeRoot();
        var model = new GodWandModel(root);
        model.setupProceduralAnimations(0, 1, 3);
        assertTrue(root.getChild("singularity_core").xScale < 0.7);
        assertEquals(21.2f, root.getChild("singularity_core").y, .001);
        model.setupProceduralAnimations(0, 0, 0);
        assertEquals(1, root.getChild("singularity_core").xScale);
        assertEquals(21.2f, root.getChild("singularity_core").y, .001);
        assertEquals(0, GodWandItemRenderer.releaseRecoil(0));
        assertTrue(GodWandItemRenderer.releaseRecoil(2) > 0.5);
        assertEquals(0, GodWandItemRenderer.releaseRecoil(12));
        assertEquals(0xFF160D26, GodWandItemRenderer.coreColor(3,1));
        assertNotEquals(GodWandItemRenderer.coreColor(1,1),GodWandItemRenderer.coreColor(2,1));
    }

    @Test
    void chargedWandGeometryRendersAllSkillsWithoutLosingItsBody() throws Exception {
        var model = new GodWandModel(GodWandModel.getTexturedModelData().bakeRoot());
        for (int skill = 0; skill < 5; skill++) for (float charge : new float[]{0.5f, 1}) {
            model.setupProceduralAnimations(60, charge, skill);
            Mesh mesh = new Mesh();
            model.renderWand(new com.mojang.blaze3d.vertex.PoseStack(), () -> mesh, () -> mesh, 0, 0,
                    GodWandItemRenderer.coreColor(skill,charge));
            assertFalse(mesh.vertices.isEmpty());
            for (float[] v : mesh.vertices) for (float value : v) assertTrue(Float.isFinite(value));
            save("wand-charge-"+skill+"-"+(int)(charge*100),mesh);
        }
    }

    @Test
    void contactAndSurfaceNormalsSurviveNetworkRoundTrip() {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        try {
            var aura = new SpatialAuraPayload(17,true,new Vec3(0,0,-1));
            SpatialAuraPayload.CODEC.encode(buf,aura);
            assertEquals(aura,SpatialAuraPayload.CODEC.decode(buf));
            var cannon = new CannonPressurePayload(new Vec3(2,70,3),new Vec3(1,0,0),4,new Vec3(0,1,0));
            CannonPressurePayload.CODEC.encode(buf,cannon);
            assertEquals(cannon,CannonPressurePayload.CODEC.decode(buf));
            assertEquals(0,buf.readableBytes());
        } finally { buf.release(); }
    }

    private static void save(String name, Mesh mesh) throws Exception {
        Path file=Path.of("build/previews/"+name+"-mesh.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file,new Gson().toJson(mesh.vertices));
    }
    private static class Mesh implements VertexConsumer {
        final List<float[]> vertices=new ArrayList<>();
        float[] current;
        public VertexConsumer addVertex(float x,float y,float z) { current=new float[]{x,y,z,0,0,0,0};vertices.add(current);return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) { current[3]=r;current[4]=g;current[5]=b;current[6]=a;return this; }
        public VertexConsumer setUv(float u,float v) { return this; }
        public VertexConsumer setUv1(int u,int v) { return this; }
        public VertexConsumer setUv2(int u,int v) { return this; }
        public VertexConsumer setNormal(float x,float y,float z) { return this; }
    }
}
