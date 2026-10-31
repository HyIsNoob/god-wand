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
        assertTrue(height * .44 >= 1.75 && height * .44 <= 2.65,
                "Third-person staff must be full-length without the needle-thin proportions");
        var blade = new Mesh();
        var renderBlade = GodWandModel.class.getDeclaredMethod("renderCrescent", PoseStack.class, VertexConsumer.class, int.class, int.class);
        renderBlade.setAccessible(true);
        renderBlade.invoke(null, new PoseStack(), blade, 0, 0);
        double headDepth = blade.vertices.stream().mapToDouble(v -> v[2]).max().orElseThrow()
                - blade.vertices.stream().mapToDouble(v -> v[2]).min().orElseThrow();
        assertTrue(headDepth >= .10 && headDepth < .25, "The crescent needs real depth without becoming a slab");
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
                "The luminous body must have an opaque body, not rely on a translucent shader pass");
        assertFalse(buffers.get(0).vertices.isEmpty());
        assertFalse(buffers.get(1).vertices.isEmpty());
        assertTrue(buffers.get(0).vertices.stream().anyMatch(v -> v[3] == 184), "Gold trim must reach the solid buffer");
    }

    @Test
    void idleCoreIsFacetedGemstoneAndBezelPrecesses() {
        var root = GodWandModel.getTexturedModelData().bakeRoot();
        var model = new GodWandModel(root);
        model.setupProceduralAnimations(0, 0, 0);
        var solid = new Mesh();
        var energy = new Mesh();
        model.renderWand(new PoseStack(), () -> solid, () -> energy, 0, 0, 0xFF00FF00);
        var gem = solid.vertices.stream().filter(v -> v[4] > v[3] && v[4] > v[5]).toList();
        assertTrue(gem.size() >= 48, "The faceted gemstone must have defined facet vertices");
        assertFalse(energy.vertices.isEmpty(), "Idle glow and floating particles must stay visible");
        float before = root.getChild("orbit").getChild("satellite").yRot;
        model.setupProceduralAnimations(30, 0, 0);
        assertNotEquals(before, root.getChild("orbit").getChild("satellite").yRot);
    }

    @Test
    void crescentActuallySamplesTheObsidianAndGoldTextureTiles() throws Exception {
        var mesh = new Mesh();
        var render = GodWandModel.class.getDeclaredMethod("renderCrescent", PoseStack.class, VertexConsumer.class, int.class, int.class);
        render.setAccessible(true);
        render.invoke(null, new PoseStack(), mesh, 0, 0);
        double minU = 1, maxU = 0, minV = 1, maxV = 0;
        for (var v : mesh.vertices) {
            assertTrue(v[7] >= 0.0f && v[7] <= 0.5f, "Obsidian segment faces must sample the obsidian slate tile");
            assertTrue(v[8] >= 0.0f && v[8] <= 0.5f);
            minU = Math.min(minU, v[7]); maxU = Math.max(maxU, v[7]);
            minV = Math.min(minV, v[8]); maxV = Math.max(maxV, v[8]);
        }
        assertTrue(maxU - minU > 0.1 && maxV - minV > 0.1,
                "Sampling across the tile must show material grain");
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
                    assertTrue(hand*x > .06, "The central aiming area must stay clear while charging or cancelling");
                }
            }
        }
    }

    @Test
    void swordGripStaysCenteredAndOnlyAlignsToTheForearmDuringAtomicSpread() {
        for(int hand:new int[]{-1,1}) for(float pitch:new float[]{-2.2f,-1.15f,-.12f}) {
            PoseStack arm=new PoseStack();
            arm.translate(hand*.3125f,.125f,0);
            arm.mulPose(com.mojang.math.Axis.ZP.rotation(hand*1.15f));
            arm.mulPose(com.mojang.math.Axis.YP.rotation(hand*.3f));
            arm.mulPose(com.mojang.math.Axis.XP.rotation(pitch));
            var expectedCenter=arm.last().pose().transformPosition(new org.joml.Vector3f(0,.625f,0));
            var expectedAxis=arm.last().pose().transformDirection(new org.joml.Vector3f(0,1,0)).normalize();
            arm.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));
            arm.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
            arm.translate(hand/16f,.125f,-.625f);
            GodWandItemRenderer.applySwordDisplayTransform(arm,hand>0
                    ?net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                    :net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
            var idle=arm.last().pose().transformDirection(new org.joml.Vector3f(0,1,0)).normalize();
            assertTrue(Math.abs(idle.dot(expectedAxis))<.5,"Ordinary held sword must not hang down along the forearm");
            GodWandItemRenderer.applyAtomicSwordHoldTransform(arm,402);
            var center=arm.last().pose().transformPosition(new org.joml.Vector3f());
            var axis=arm.last().pose().transformDirection(new org.joml.Vector3f(0,1,0)).normalize();
            assertEquals(0,center.distance(expectedCenter),.00001,"Sword grip must remain inside the palm");
            assertEquals(0,axis.distance(expectedAxis),.00001,"Sword must extend along the forearm");
        }
    }

    @Test
    void raisedSwordIsVerticalInFrontOfTheCasterAndItsRootOverlapsTheGuard() throws Exception {
        PoseStack pose=new PoseStack();
        pose.scale(-1,-1,1);
        pose.mulPose(com.mojang.math.Axis.YP.rotation(-.64f));
        pose.mulPose(com.mojang.math.Axis.XP.rotation(net.godwand.skill.AtomicSequence.armPitch(360)));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
        GodWandItemRenderer.applySwordDisplayTransform(pose,net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
        GodWandItemRenderer.applyAtomicSwordHoldTransform(pose,360);
        var direction=pose.last().pose().transformDirection(new org.joml.Vector3f(0,1,0)).normalize();
        assertEquals(0,direction.x,.00001); assertEquals(1,direction.y,.00001); assertEquals(0,direction.z,.00001);
        var method=net.godwand.client.model.ShadowBladeModel.class.getDeclaredMethod("createTsubaQuads");method.setAccessible(true);
        var faces=(org.joml.Vector3f[][])method.invoke(null);
        assertTrue(java.util.Arrays.stream(faces).flatMap(java.util.Arrays::stream)
                .anyMatch(v -> v.y< -2.4f/16&&Math.abs(v.z)<1.4f/16),"Guard socket must overlap the blade root");
    }

    @Test
    void referenceSwordHasDiamondGuardTipsAndAThreeProngedPommel() throws Exception {
        for(String method:new String[]{"createTsubaQuads","createPommelQuads"}) {
            var source=net.godwand.client.model.ShadowBladeModel.class.getDeclaredMethod(method);source.setAccessible(true);
            var quads=(org.joml.Vector3f[][])source.invoke(null);
            var vertices=java.util.Arrays.stream(quads).flatMap(java.util.Arrays::stream).toList();
            assertFalse(vertices.isEmpty());
            if(method.equals("createTsubaQuads")) {
                assertTrue(vertices.stream().anyMatch(v -> v.z>7.9f/16));
                assertTrue(vertices.stream().anyMatch(v -> v.z< -7.9f/16));
            } else {
                assertEquals(-21f/16,vertices.stream().mapToDouble(v -> v.y).min().orElseThrow(),.00001);
                assertTrue(vertices.stream().anyMatch(v -> v.z>3f/16));
                assertTrue(vertices.stream().anyMatch(v -> v.z< -3f/16));
            }
        }
    }

    @Test void referenceSwordHasSmoothMaterialsVisibleBevelsAndOutwardFacingGuardCaps() throws Exception {
        for(String name:new String[]{"createTsubaQuads","createPommelQuads"}) {
            var method=net.godwand.client.model.ShadowBladeModel.class.getDeclaredMethod(name);method.setAccessible(true);
            var quads=(org.joml.Vector3f[][])method.invoke(null);
            for(var q:quads) {
                if(Math.abs(q[0].x)<.001||java.util.Arrays.stream(q).anyMatch(v -> Math.abs(v.x-q[0].x)>.001))continue;
                var normal=new org.joml.Vector3f(q[1]).sub(q[0]).cross(new org.joml.Vector3f(q[2]).sub(q[0]));
                if(normal.lengthSquared()>.00000001)assertTrue(normal.x*q[0].x>0,"Mirrored fittings must have outward-facing normals");
            }
        }
        var sword=new net.godwand.client.model.ShadowBladeModel(net.godwand.client.model.ShadowBladeModel.getTexturedModelData().bakeRoot());
        Mesh mesh=new Mesh();sword.renderBlade(new PoseStack(),() -> mesh,() -> mesh,0,0,-1);
        assertTrue(mesh.vertices.stream().anyMatch(v -> v[1]>1&&v[3]>60&&v[5]>60),"Blade bevels need readable graphite highlights");
        assertTrue(mesh.vertices.stream().allMatch(v -> v[7]>=.8&&v[7]<=.841&&v[8]>=.8&&v[8]<=.841),"Use the smooth satin patch rather than the engraved staff tile");
    }

    @Test
    void swordHasTwoSymmetricEdgesAndAFlatFaceWithoutACenterStrip() throws Exception {
        var method=net.godwand.client.model.ShadowBladeModel.class.getDeclaredMethod("createBladeQuads");
        method.setAccessible(true);
        var quads=(org.joml.Vector3f[][])method.invoke(null);
        var base=java.util.Arrays.stream(quads).flatMap(java.util.Arrays::stream)
                .filter(v -> Math.abs(v.y+2.4f/16)<.001).toList();
        assertFalse(base.isEmpty());
        assertEquals(47f/16,java.util.Arrays.stream(quads).flatMap(java.util.Arrays::stream).mapToDouble(v -> v.y).max().orElseThrow(),.001);
        assertEquals(1.615f/16,base.stream().mapToDouble(v -> Math.abs(v.z)).max().orElseThrow(),.001);
        for(var vertex:base) {
            assertTrue(base.stream().anyMatch(v -> Math.abs(v.z+vertex.z)<.001&&Math.abs(v.x-vertex.x)<.001),
                    "The two cutting edges must mirror each other");
            if(Math.abs(vertex.z)>1.60f/16) assertEquals(0,vertex.x,.001);
        }
        assertTrue(base.stream().filter(v -> v.x>.01).allMatch(v -> Math.abs(v.x-.26f/16)<.001),
                "Broad face must be flat without a raised central ridge");
    }

    @Test
    void aquaReacquiresBuffersAfterSwitchingBetweenSolidAndEmissiveLayers() {
        var model = new net.godwand.client.model.AquaHeartiaModel(net.godwand.client.model.AquaHeartiaModel.getTexturedModelData().bakeRoot());
        var solid = new Mesh(); var emissive = new Mesh();
        Mesh[] active = {null};
        solid.validate = () -> assertSame(solid, active[0], "Solid buffer invalidated by another render layer");
        emissive.validate = () -> assertSame(emissive, active[0], "Emissive buffer invalidated by another render layer");
        model.renderWand(new PoseStack(), () -> { active[0]=solid; return solid; },
                () -> { active[0]=emissive; return emissive; }, 0, 0, -1);
        assertFalse(solid.vertices.isEmpty()); assertFalse(emissive.vertices.isEmpty());
    }

    @Test
    void alternateModelsUseSingleMaterialTilesAndSwordGripContainsTheHoldingOrigin() throws Exception {
        var aqua = new net.godwand.client.model.AquaHeartiaModel(net.godwand.client.model.AquaHeartiaModel.getTexturedModelData().bakeRoot());
        var sword = new net.godwand.client.model.ShadowBladeModel(net.godwand.client.model.ShadowBladeModel.getTexturedModelData().bakeRoot());
        var aquaMesh = new Mesh(); var swordMesh = new Mesh();
        aqua.setupProceduralAnimations(0, 0, 2);
        aqua.renderWand(new PoseStack(), () -> aquaMesh, () -> aquaMesh, 0, 0, -1);
        sword.renderBlade(new PoseStack(), () -> swordMesh, () -> swordMesh, 0, 0, -1);
        for (var mesh : List.of(aquaMesh, swordMesh)) {
            assertFalse(mesh.vertices.isEmpty());
            for (int i = 0; i < mesh.vertices.size(); i += 4) {
                var first = mesh.vertices.get(i);
                for (int j = 0; j < 4; j++) {
                    var vertex = mesh.vertices.get(i+j);
                    assertEquals((int)(first[7]*2), (int)(vertex[7]*2), "Each face must stay inside one material column");
                    assertEquals((int)(first[8]*2), (int)(vertex[8]*2), "Each face must stay inside one material row");
                }
            }
        }
        var grip = swordMesh.vertices.stream().filter(v -> v[3] == 23 && v[4] == 23).toList();
        assertTrue(grip.stream().anyMatch(v -> v[1] < 0));
        assertTrue(grip.stream().anyMatch(v -> v[1] > 0));
        assertTrue(aquaMesh.vertices.stream().mapToDouble(v -> v[1]).max().orElseThrow() > 2.2,
                "The enlarged staff head must extend above the original small hood");
        var body = swordMesh.vertices.stream().filter(v -> v[3] == 41 && v[4] == 42).toList();
        double tipY = body.stream().mapToDouble(v -> v[1]).max().orElseThrow();
        assertEquals(3.4375, tipY, .001);
        assertTrue(body.stream().filter(v -> Math.abs(v[1]-tipY)<.001).allMatch(v -> Math.abs(v[0])+Math.abs(v[2])<.001),
                "All blade bevels must meet at the same tip");
        Path previews = Path.of("build/previews"); Files.createDirectories(previews);
        Files.writeString(previews.resolve("aqua-heartia-mesh.json"), new Gson().toJson(aquaMesh.vertices));
        Files.writeString(previews.resolve("shadow-blade-mesh.json"), new Gson().toJson(swordMesh.vertices));
    }

    @Test void atomicChargesOnlyTheBladeAndReleasesTheEmissiveBufferAfterSolidGeometry() {
        var sword=new net.godwand.client.model.ShadowBladeModel(net.godwand.client.model.ShadowBladeModel.getTexturedModelData().bakeRoot());
        Mesh solid=new Mesh(),glow=new Mesh();Mesh[] active={null};
        solid.validate=() -> assertSame(solid,active[0]);glow.validate=() -> assertSame(glow,active[0]);
        sword.setupProceduralAnimations(350,1,5);
        sword.renderBlade(new PoseStack(),() -> {active[0]=solid;return solid;},() -> {active[0]=glow;return glow;},0,0,-1);
        assertFalse(glow.vertices.isEmpty());
        assertTrue(glow.vertices.stream().allMatch(v -> v[1]>=.35f-.001&&v[5]>v[3]));
        assertTrue(solid.vertices.stream().anyMatch(v -> v[3]==23&&v[4]==23&&v[5]==27));
        Mesh idleGlow=new Mesh();
        sword.setupProceduralAnimations(0,0,5);
        sword.renderBlade(new PoseStack(),Mesh::new,() -> idleGlow,0,0,-1);
        assertTrue(idleGlow.vertices.isEmpty());
    }

    private static class Mesh implements VertexConsumer {
        public VertexConsumer setColor(int c) { return setColor((c>>>16)&255,(c>>>8)&255,c&255,c>>>24); }
        public VertexConsumer setUv3(float u,float v) { return this; }
        public VertexConsumer setLineWidth(float width) { return this; }
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
