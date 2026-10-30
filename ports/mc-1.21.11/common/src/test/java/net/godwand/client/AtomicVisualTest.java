package net.godwand.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.godwand.skill.AtomicSequence;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AtomicVisualTest {
    @Test void blastColumnReachesTheCraterFloorWithoutMovingItsTop() {
        Mesh mesh=new Mesh();
        AtomicRenderer.emit(mesh,new Matrix4f(),500,new net.minecraft.world.phys.Vec3(0,2,160));
        var core=mesh.vertices.stream().filter(p -> p[3]==245&&p[4]==223&&p[5]==255).toList();
        assertFalse(core.isEmpty());
        assertEquals(-30,core.stream().mapToDouble(p -> p[1]).min().orElseThrow(),.001);
        assertEquals(1024,core.stream().mapToDouble(p -> p[1]).max().orElseThrow(),.001);
        assertTrue(AtomicSequence.columnOccludes(new net.minecraft.world.phys.Vec3(0,-20,160),new net.minecraft.world.phys.Vec3(0,-20,-160),500));
        assertFalse(AtomicSequence.columnOccludes(new net.minecraft.world.phys.Vec3(0,-31,160),new net.minecraft.world.phys.Vec3(0,-31,-160),500));
    }
    @Test void meshesStayBoundedAndGiveWayToTheAftermath() throws Exception {
        for (int age : new int[]{0, 36, 72, 114, 150, 174, 199, 209, 216, 240, 296, 344, 370, 385, 396, 398, 400, 402, 404, 406, 408, 410, 412, 413, 414, 416, 434, 474, 534, 594, 674, 754, 834}) {
            Mesh mesh = new Mesh();
            AtomicRenderer.emit(mesh, new Matrix4f(), age,new net.minecraft.world.phys.Vec3(0,2,160));
            if (age == 0 || age >= 734 || age>=AtomicSequence.WAIT_TICK&&age<AtomicSequence.DETONATION) assertTrue(mesh.vertices.isEmpty());
            else assertFalse(mesh.vertices.isEmpty(), "Missing Atomic phase at " + age);
            assertEquals(0, mesh.vertices.size() % 4);
            assertTrue(mesh.vertices.size() <= 60000, "Unbounded mesh at " + age+": "+mesh.vertices.size());
            for (float[] vertex : mesh.vertices) {
                for (float n : vertex) assertTrue(Float.isFinite(n));
                assertTrue(Math.abs(vertex[0]) <= 330 && Math.abs(vertex[2]) <= 330);
                assertTrue(vertex[1] >= -31 && vertex[1] <= 1100);
                assertTrue(vertex[6] >= 0 && vertex[6] <= 255);
            }
            Path path = Path.of("build/previews/atomic-" + age + "-mesh.json");
            Files.createDirectories(path.getParent());
            Files.writeString(path, new Gson().toJson(mesh.vertices));
        }
    }

    @Test void thinContinuousAuraPersistsThroughTheBlastAndFadesDuringContraction() throws Exception {
        var method=AtomicRenderer.class.getDeclaredMethod("pressureAura",VertexConsumer.class,Matrix4f.class,double.class,float.class,net.minecraft.world.phys.Vec3.class);
        method.setAccessible(true);
        var eye=new net.minecraft.world.phys.Vec3(0,55,160);
        Mesh early=new Mesh(),late=new Mesh();
        method.invoke(null,early,new Matrix4f(),418d,1f,eye);
        method.invoke(null,late,new Matrix4f(),430d,1f,eye);
        assertFalse(early.vertices.isEmpty()); assertFalse(late.vertices.isEmpty());
        double near=early.vertices.stream().mapToDouble(p -> Math.hypot(p[0],p[2])).max().orElseThrow();
        double far=late.vertices.stream().mapToDouble(p -> Math.hypot(p[0],p[2])).max().orElseThrow();
        assertTrue(far>near+40,"The gust front must cross the scene quickly");
        Mesh next=new Mesh(),outward=new Mesh();
        method.invoke(null,next,new Matrix4f(),419d,1f,eye);
        method.invoke(null,outward,new Matrix4f(),444d,1f,eye);
        double earlyY=early.vertices.subList(0,4).stream().mapToDouble(p -> p[1]).average().orElseThrow();
        double nextY=next.vertices.subList(0,4).stream().mapToDouble(p -> p[1]).average().orElseThrow();
        assertEquals(earlyY,nextY,.001,"The same gust must keep its height instead of climbing");
        assertTrue(outward.vertices.stream().mapToDouble(p -> Math.hypot(p[0],p[2])).max().orElseThrow()>200,
                "The pressure must continue beyond the wide camera instead of stopping near the column");
        for(int age=414;age<=734;age++) {
            Mesh mesh=new Mesh(); method.invoke(null,mesh,new Matrix4f(),(double)age,1f,eye);
            assertTrue(mesh.vertices.size()<=7000,"Pressure aura must remain bounded");
            for(float[] p:mesh.vertices) {
                assertTrue(Math.hypot(p[0],p[2])<330);
                assertTrue(p[1]>=0&&p[1]<85);
            }
        }
        for(int age:new int[]{450,534,574,614,674,720}) {
            Mesh mesh=new Mesh(); method.invoke(null,mesh,new Matrix4f(),(double)age,1f,eye);
            assertFalse(mesh.vertices.isEmpty(),"Aura vanished before the column finished shrinking at "+age);
        }
        Mesh puffs=new Mesh(); method.invoke(null,puffs,new Matrix4f(),480d,1f,eye);
        for(int i=0;i<puffs.vertices.size();i+=4) {
            var a=puffs.vertices.get(i); var b=puffs.vertices.get(i+1); var c=puffs.vertices.get(i+2);
            double width=Math.sqrt(Math.pow(a[0]-b[0],2)+Math.pow(a[1]-b[1],2)+Math.pow(a[2]-b[2],2));
            double height=Math.sqrt(Math.pow(c[0]-b[0],2)+Math.pow(c[1]-b[1],2)+Math.pow(c[2]-b[2],2));
            assertTrue(width>5&&height>5,"Smoke puffs need volume rather than thin bright threads");
            assertTrue(Math.max(width,height)/Math.min(width,height)<1.5,"No stretched streamer quads");
        }
        Set<Integer> filled=new HashSet<>();
        for(int i=0;i<puffs.vertices.size();i+=8) {
            double x=0,z=0;
            for(int j=0;j<4;j++){x+=puffs.vertices.get(i+j)[0]/4;z+=puffs.vertices.get(i+j)[2]/4;}
            filled.add((int)((Math.hypot(x,z)-AtomicSequence.beamRadius(480))/18));
        }
        assertTrue(filled.size()>=10,"Continuous flow must occupy the intervening radii, not isolated wave shells");
        assertTrue(puffs.vertices.stream().allMatch(p -> p[6]<=22),"Aura must remain a thin flow instead of dense smoke");
        Mesh full=new Mesh(),fading=new Mesh();
        method.invoke(null,full,new Matrix4f(),570d,1f,eye);
        method.invoke(null,fading,new Matrix4f(),714d,1f,eye);
        assertTrue(full.vertices.stream().mapToDouble(p -> p[6]).max().orElseThrow()
                >fading.vertices.stream().mapToDouble(p -> p[6]).max().orElseThrow()*4,
                "Aura must fade with contraction instead of stopping abruptly");
        for(int age:new int[]{400,414,734,800}) {
            Mesh mesh=new Mesh(); method.invoke(null,mesh,new Matrix4f(),(double)age,1f,eye);
            assertTrue(mesh.vertices.isEmpty(),"No gusts before detonation or after the column closes");
        }
    }

    @Test void theGroundTracesHaveACompleteSourceRingAtTheCaster() {
        Mesh mesh=new Mesh(); AtomicRenderer.emit(mesh,new Matrix4f(),45,new net.minecraft.world.phys.Vec3(0,2,160));
        Set<Integer> angles=new HashSet<>();
        for(float[] p:mesh.vertices) {
            double r=Math.hypot(p[0],p[2]);
            if(r>1.8&&r<2.3&&p[1]>.04&&p[1]<.12)
                angles.add((int)Math.floor((Math.atan2(p[2],p[0])+Math.PI)*96/(2*Math.PI)));
        }
        assertTrue(angles.size()>80,"Ground filaments have no complete source ring");
    }

    @Test void sourceSigilGrowsOutwardFromTheCasterBeforeFeedingTheTraces() {
        Mesh early=new Mesh(),late=new Mesh();
        var eye=new net.minecraft.world.phys.Vec3(0,2,160);
        AtomicRenderer.emit(early,new Matrix4f(),18,eye); AtomicRenderer.emit(late,new Matrix4f(),45,eye);
        double first=early.vertices.stream().filter(p -> Math.abs(p[1]-.065)<1e-6&&p[6]>0)
                .mapToDouble(p -> Math.hypot(p[0],p[2])).max().orElseThrow();
        double full=late.vertices.stream().filter(p -> Math.abs(p[1]-.065)<1e-6&&p[6]>0)
                .mapToDouble(p -> Math.hypot(p[0],p[2])).max().orElseThrow();
        assertTrue(first<1.2&&full>2.3,"The source sigil must grow radially instead of appearing at full size");
    }

    @Test void energyMotesHaveVisibleVioletGlowThroughoutTheTerritory() {
        Mesh mesh=new Mesh(); AtomicRenderer.emit(mesh,new Matrix4f(),240,new net.minecraft.world.phys.Vec3(0,2,160));
        long violet=mesh.vertices.stream().filter(p -> p[1]>.5&&p[1]<44&&p[3]==159&&p[4]==50&&p[5]==255&&p[6]>5).count();
        assertTrue(violet>300,"The rising energy must include broad violet glow, not just thin white sparks");
    }

    @Test void theTerritoryHasMoreSmallRoundMotesBeforeTheRelease() {
        Mesh mesh=new Mesh(); AtomicRenderer.emit(mesh,new Matrix4f(),320,new net.minecraft.world.phys.Vec3(0,2,160));
        long roundGlow=mesh.vertices.stream().filter(p -> p[1]>.5&&p[1]<44&&p[3]==159&&p[4]==50&&p[5]==255&&p[6]>5).count();
        assertTrue(roundGlow>5000,"The territory must visibly contain more random energy motes");
    }

    @Test void circularPowerBandWrapsAllEightTowerFacesAtTheSameHeight() {
        Mesh mesh=new Mesh(); AtomicRenderer.emit(mesh,new Matrix4f(),199,new net.minecraft.world.phys.Vec3(0,2,160));
        Set<Integer> towers=new HashSet<>();
        long band=mesh.vertices.stream().filter(p -> Math.hypot(p[0],p[2])>28&&Math.abs(p[1]-27)<.5).peek(p ->
                towers.add(Math.floorMod((int)Math.round(Math.atan2(p[2],p[0])*4/Math.PI),8))).count();
        assertTrue(band>300,"A rising circumferential band must hug the tower faces, rather than separate traveling packets");
        assertEquals(8,towers.size());
    }

    @Test void fortressHasExternalWatchtowersAndEverythingCompressesIntoTheCaster() {
        var eye=new net.minecraft.world.phys.Vec3(0,2,160);
        Mesh fortress=new Mesh(); AtomicRenderer.emit(fortress,new Matrix4f(),216,eye);
        assertTrue(fortress.vertices.stream().filter(p -> Math.hypot(p[0],p[2])>28&&p[1]>15).count()>400,"Missing outer watchtowers");
        Mesh last=new Mesh(); AtomicRenderer.emit(last,new Matrix4f(),AtomicSequence.WAIT_TICK-.1,eye);
        assertFalse(last.vertices.isEmpty());
        assertTrue(last.vertices.stream().allMatch(p -> Math.sqrt(p[0]*p[0]+p[1]*p[1]+p[2]*p[2])<1.2),"Some charge effects stopped collapsing before the blast");
    }

    @Test void compressedGroundCircuitsStayAtTheFeetInsteadOfRisingToTheWaist() {
        for(int age:new int[]{398,402,404,405}) {
            Mesh mesh=new Mesh(); AtomicRenderer.emit(mesh,new Matrix4f(),age,new net.minecraft.world.phys.Vec3(0,2,160));
            double scale=1-AtomicSequence.compression(age);
            long source=mesh.vertices.stream().filter(p -> Math.hypot(p[0],p[2])>1.8*scale
                    &&Math.hypot(p[0],p[2])<2.3*scale&&p[1]>=0&&p[1]<.12*scale).count();
            assertTrue(source>100,"The source ring floated above the feet at "+age);
        }
    }

    @Test void overheadRoofHasBrightVioletCoresBeforeTheCameraCuts() {
        Mesh mesh=new Mesh(); AtomicRenderer.emit(mesh,new Matrix4f(),170,new net.minecraft.world.phys.Vec3(0,80,0));
        long roof=mesh.vertices.stream().filter(p -> Math.abs(p[1]-46)<.2&&Math.hypot(p[0],p[2])<25
                &&p[6]>140&&p[5]>p[3]*1.25).count();
        assertTrue(roof>60,"Roof circuits are too thin/dim to read from the overhead shot");
        Mesh start=new Mesh(); AtomicRenderer.emit(start,new Matrix4f(),45,new net.minecraft.world.phys.Vec3(0,2,160));
        assertTrue(start.vertices.stream().filter(p -> p[1]>.04&&p[1]<.12&&p[6]>40)
                .noneMatch(p -> p[3]==231&&p[4]==136||p[3]==236&&p[4]==133),"Early source circuits use pink cores instead of the same violet palette");
    }

    @Test void theHeadHaloIsOneHorizontalRingRatherThanSeveralTiltedOrStackedRings() {
        Mesh mesh=new Mesh(); AtomicRenderer.emit(mesh,new Matrix4f(),350,new net.minecraft.world.phys.Vec3(0,2,160));
        Set<Integer> heights=new HashSet<>();
        for(int i=0;i<mesh.vertices.size();i+=4) {
            var a=mesh.vertices.get(i);
            double r=Math.hypot(a[0],a[2]);
            if(r<1.2||r>3||a[1]<2.8||a[1]>3.6)continue;
            boolean flat=true;
            for(int j=1;j<4;j++)flat&=Math.abs(mesh.vertices.get(i+j)[1]-a[1])<1e-6;
            if(flat)heights.add(Math.round(a[1]*1000));
        }
        assertEquals(Set.of(3200),heights,"The overhead halo must occupy one horizontal plane");
    }

    @Test void fortressPanelsCarrySparseCircuitTextureWithoutIncreasingTheMeshBudget() {
        int lit=0;
        for(int y=0;y<128;y++)for(int x=0;x<64;x++) {
            int alpha=AtomicRenderer.circuitAlpha(x,y);
            assertTrue(alpha>=0&&alpha<=255);
            if(x<3||x>60||y<2||y>125)assertEquals(0,alpha,"Atlas gutters must remain transparent");
            if(alpha>30)lit++;
        }
        assertTrue(lit>500&&lit<3000,"Circuit panels must contain fine traces with transparent space between them");
        Mesh mesh=new Mesh(); AtomicRenderer.emit(mesh,new Matrix4f(),216,new net.minecraft.world.phys.Vec3(0,2,160));
        long panelUvs=mesh.uvs.stream().filter(uv -> uv[0]>.5&&uv[0]<.75).count();
        assertEquals(48*4,panelUvs,"Both tower faces and curtain walls must use the circuit material");
    }

    @Test void energySpriteHasCircularTransparentCornersAndASoftEdge() {
        assertTrue(AtomicRenderer.energyAlpha(15,15)>245);
        assertEquals(0,AtomicRenderer.energyAlpha(0,0));
        assertEquals(0,AtomicRenderer.energyAlpha(31,31));
        assertTrue(AtomicRenderer.energyAlpha(1,15)>0&&AtomicRenderer.energyAlpha(1,15)<40);
        assertEquals(AtomicRenderer.energyAlpha(1,15),AtomicRenderer.energyAlpha(15,1));
    }

    @Test void wandUsesTheWholeAtomicChargeAndGetsItsOwnVioletCore() {
        assertEquals(.5f, net.godwand.client.render.GodWandItemRenderer.chargeRatio(5, 190));
        assertEquals(1, net.godwand.client.render.GodWandItemRenderer.chargeRatio(5, 380));
        int color = net.godwand.client.render.GodWandItemRenderer.coreColor(5, 1);
        assertTrue((color & 255) > ((color >> 8) & 255) + 80);
    }

    @Test void raisedStaffRemainsVerticalThroughTheRealHandTransform() {
        for (int age : new int[]{0,80,160,240,314}) {
            float pitch=AtomicSequence.armPitch(age);
            var pose=new com.mojang.blaze3d.vertex.PoseStack();
            pose.scale(-1,-1,1);
            pose.mulPose(com.mojang.math.Axis.XP.rotation(pitch));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(20));
            net.godwand.client.render.GodWandItemRenderer.applyAtomicHoldTransform(pose,pitch,1);
            var shaft=pose.last().pose().transformDirection(new org.joml.Vector3f(0,1,0));
            assertEquals(0,shaft.x,1e-5);
            assertEquals(1,shaft.y,1e-5);
            assertEquals(0,shaft.z,1e-5);
        }
    }

    @Test void swordTipTrailIsBoundedExpiresAndDoesNotBridgeTeleports() throws Exception {
        List<AtomicRenderer.TipSample> trail=new ArrayList<>();
        for(int i=0;i<1000;i++)AtomicRenderer.sampleSwordTip(trail,new net.minecraft.world.phys.Vec3(i*.001,2,0),i*.01);
        assertTrue(trail.size()<=81);
        AtomicRenderer.sampleSwordTip(trail,new net.minecraft.world.phys.Vec3(20,2,0),11);
        assertEquals(1,trail.size());
        AtomicRenderer.sampleSwordTip(trail,new net.minecraft.world.phys.Vec3(20,2,0),28);
        assertEquals(1,trail.size());
        var method=AtomicRenderer.class.getDeclaredMethod("swordTrail",VertexConsumer.class,Matrix4f.class,List.class,net.minecraft.world.phys.Vec3.class,double.class);
        method.setAccessible(true);
        Mesh faded=new Mesh();
        method.invoke(null,faded,new Matrix4f(),trail,net.minecraft.world.phys.Vec3.ZERO,45d);
        assertTrue(faded.vertices.isEmpty()); assertTrue(trail.isEmpty());
    }

    @Test void convergenceDisconnectsWhenTheSwordStartsSweepingSideways() throws Exception {
        var method=AtomicRenderer.class.getDeclaredMethod("bladeConvergence",VertexConsumer.class,Matrix4f.class,double.class,
                net.minecraft.world.phys.Vec3.class,net.minecraft.world.phys.Vec3.class,net.minecraft.world.phys.Vec3.class);
        method.setAccessible(true);
        Mesh raised=new Mesh(),sweeping=new Mesh();
        var base=new net.minecraft.world.phys.Vec3(0,1.5,1);
        var tip=new net.minecraft.world.phys.Vec3(0,3.1,1);
        var eye=new net.minecraft.world.phys.Vec3(0,2,6);
        method.invoke(null,raised,new Matrix4f(),350d,base,tip,eye);
        method.invoke(null,sweeping,new Matrix4f(),388d,base,tip,eye);
        assertFalse(raised.vertices.isEmpty());assertTrue(sweeping.vertices.isEmpty());
    }

    static class Mesh implements VertexConsumer {
        public VertexConsumer setColor(int c) { return setColor((c>>>16)&255,(c>>>8)&255,c&255,c>>>24); }
        public VertexConsumer setUv3(float u,float v) { return this; }
        public VertexConsumer setLineWidth(float width) { return this; }
        final List<float[]> vertices = new ArrayList<>(), uvs=new ArrayList<>(); float[] current;
        public VertexConsumer addVertex(float x,float y,float z) { current=new float[]{x,y,z,0,0,0,0}; vertices.add(current); return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) { current[3]=r; current[4]=g; current[5]=b; current[6]=a; return this; }
        public VertexConsumer setUv(float u,float v) { uvs.add(new float[]{u,v}); return this; }
        public VertexConsumer setUv1(int u,int v) { return this; }
        public VertexConsumer setUv2(int u,int v) { return this; }
        public VertexConsumer setNormal(float x,float y,float z) { return this; }
    }
}
