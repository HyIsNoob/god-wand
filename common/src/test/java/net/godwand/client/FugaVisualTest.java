package net.godwand.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.godwand.network.FugaFxPayload;
import net.godwand.item.GodWandItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FugaVisualTest {
    private static double impactTick(double visual) { return visual<40?visual*2:visual<60?80+(visual-40)/.8:105+(visual-60)/.5; }
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    private static FugaRenderer.Frame frame(int phase,double age) {
        return new FugaRenderer.Frame(1,100,phase,phase==2?impactTick(age):phase==0?age*5/3:age,Vec3.ZERO,new Vec3(0,0,160));
    }
    @Test void allPhasesHaveFiniteBoundedVolumesAndFinishCleanly() {
        for(int phase=0;phase<5;phase++) for(double age:new double[]{0,1,32,60,119,120,159,160,600}) {
            Mesh mesh=new Mesh(); FugaRenderer.emit(mesh,new Matrix4f(),frame(phase,age),Vec3.ZERO);
            if(phase>=3 || phase==2&&age>=160 || phase==0&&age<15) assertTrue(mesh.vertices.isEmpty());
            else assertFalse(mesh.vertices.isEmpty(),"Missing phase "+phase+" at "+age);
            assertEquals(0,mesh.vertices.size()%4);
            assertTrue(mesh.vertices.size()<48000,"Unbounded mesh: "+mesh.vertices.size());
            for(float[] p:mesh.vertices) {
                for(float n:p)assertTrue(Float.isFinite(n));
                assertTrue(Math.abs(p[0])<520&&Math.abs(p[2])<520,"shock front stays bounded");
                assertTrue(p[1]>=-384&&p[1]<=384);
                assertTrue(p[6]>=0&&p[6]<=255);
            }
        }
        Mesh invalid=new Mesh(); FugaRenderer.emit(invalid,new Matrix4f(),frame(1,Double.NaN),Vec3.ZERO);
        assertTrue(invalid.vertices.isEmpty());
    }
    @Test void arrowIsStraightClampedAndLeavesALongAfterimage() {
        assertEquals(net.godwand.skill.FugaSequence.flightDistance(20),frame(1,20).arrowPosition().z,1e-9);
        assertEquals(new Vec3(0,0,160),frame(1,100).arrowPosition());
        Mesh mesh=new Mesh(); FugaRenderer.emit(mesh,new Matrix4f(),frame(1,20),Vec3.ZERO);
        assertTrue(mesh.vertices.stream().mapToDouble(p->p[2]).min().orElseThrow()<18);
        assertEquals(net.godwand.skill.FugaSequence.flightDistance(20),mesh.vertices.stream().mapToDouble(p->p[2]).max().orElseThrow(),.001,"the broadhead tip leads");
        assertTrue(mesh.vertices.stream().anyMatch(p->Math.abs(p[0])>.5),"Arrowhead needs a broad silhouette");
    }
    @Test void bowAndArrowKeepTheirSizeDuringHoldAndRotateWithAim() {
        Mesh ready=new Mesh(),held=new Mesh(),side=new Mesh();
        FugaRenderer.emit(ready,new Matrix4f(),frame(0,120),Vec3.ZERO);
        FugaRenderer.emit(held,new Matrix4f(),frame(0,600),Vec3.ZERO);
        FugaRenderer.emit(side,new Matrix4f(),new FugaRenderer.Frame(1,100,0,200,Vec3.ZERO,new Vec3(160,0,0)),Vec3.ZERO);
        assertEquals(ready.vertices.size(),held.vertices.size());
        assertTrue(ready.vertices.stream().filter(p->p[1]>.2).mapToDouble(p->p[2]).max().orElseThrow()<8,"held shot, its vortex and flares stay around the archer");
        assertEquals(ready.vertices.stream().mapToDouble(p->p[2]).max().orElseThrow(),
                side.vertices.stream().mapToDouble(p->p[0]).max().orElseThrow(),.05,"charge geometry rotates with aim");
        assertTrue(held.vertices.stream().anyMatch(p->p[3]==255&&p[4]==248&&p[5]==230),"white-hot flame core");
    }
    @Test void sphereDescendsBeforeTheColumnReaches384AndContractsAfter120() {
        Mesh high=new Mesh(),low=new Mesh(),column=new Mesh(),contract=new Mesh();
        FugaRenderer.emit(high,new Matrix4f(),frame(2,1),Vec3.ZERO);
        FugaRenderer.emit(low,new Matrix4f(),frame(2,32),Vec3.ZERO);
        double highTop=high.vertices.stream().mapToDouble(p->p[1]).max().orElseThrow(),lowTop=low.vertices.stream().mapToDouble(p->p[1]).max().orElseThrow();
        // The energy mass falls from 10 blocks up and slows until its underside meets the ground.
        double gapStart=FugaRenderer.orbCenter(6)-FugaRenderer.orbRadius(6)*FugaRenderer.orbScale(6),gapEnd=FugaRenderer.orbCenter(36)-FugaRenderer.orbRadius(36)*FugaRenderer.orbScale(36);
        assertTrue(gapStart>6&&gapEnd<1.5,"visible fall that slows to contact "+gapStart+" -> "+gapEnd+" (tops "+highTop+", "+lowTop+")");
        assertTrue(high.vertices.stream().anyMatch(p->p[5]>p[3]),"Sphere needs its limited violet/blue edge");
        FugaRenderer.emit(column,new Matrix4f(),frame(2,90),Vec3.ZERO);
        FugaRenderer.emit(contract,new Matrix4f(),frame(2,155),Vec3.ZERO);
        assertEquals(384,column.vertices.stream().mapToDouble(p->p[1]).max().orElseThrow(),.001);
        // Retracting pulls the column down and in rather than leaving a thin beam.
        double full=column.vertices.stream().mapToDouble(p->p[1]).max().orElseThrow();
        double small=contract.vertices.stream().mapToDouble(p->p[1]).max().orElseThrow();
        assertTrue(full>small*2.5,"contract "+full+" -> "+small);
    }
    @Test void columnReachesWorldFloorAndSkyWithAFlaredBaseAndDamageSizedBody() {
        var origin=new Vec3(5,70,9); var eye=new Vec3(1,4,2);
        var frame=new FugaRenderer.Frame(1,100,2,impactTick(90),origin,origin);
        Mesh mesh=new Mesh(); FugaRenderer.emit(mesh,new Matrix4f(),frame,eye,-134);
        assertEquals(-68,mesh.vertices.stream().mapToDouble(p->p[1]).min().orElseThrow(),.001,"World min -64 minus eye Y4");
        assertEquals(450,mesh.vertices.stream().mapToDouble(p->p[1]).max().orElseThrow(),.5,"Anchor70 plus384 minus eye4");
        var body=mesh.vertices.stream().filter(p->p[1]>166&&p[1]<366).mapToDouble(p->Math.hypot(p[0]-4,p[2]-7)).max().orElseThrow();
        var base=mesh.vertices.stream().filter(p->p[1]>66&&p[1]<72).mapToDouble(p->Math.hypot(p[0]-4,p[2]-7)).max().orElseThrow();
        assertTrue(body>net.godwand.skill.FugaSequence.RADIUS*.5&&body<net.godwand.skill.FugaSequence.RADIUS*1.6,"column body "+body);
        assertTrue(base>body,"flared base");
        Mesh shallow=new Mesh(); FugaRenderer.emit(shallow,new Matrix4f(),frame,eye,-20);
        assertEquals(450,shallow.vertices.stream().mapToDouble(p->p[1]).max().orElseThrow(),.5);
    }
    @Test void columnIsNotARotationallyUniformCylinder() {
        Mesh mesh=new Mesh(); FugaRenderer.emit(mesh,new Matrix4f(),frame(2,90),Vec3.ZERO);
        // Fire is gathered into twisting streams, so some angular sectors at a given height are much denser.
        double[] sectors=new double[16];
        for(float[] p:mesh.vertices)if(p[1]>140&&p[1]<160) sectors[(int)((Math.atan2(p[2],p[0])+Math.PI)/(2*Math.PI)*16)%16]++;
        double lo=Arrays.stream(sectors).min().orElseThrow(),hi=Arrays.stream(sectors).max().orElseThrow();
        assertTrue(hi>lo*1.5,"clumped streams "+lo+".."+hi);
    }
    @Test void detonationAddsRaysDebrisAndGroundFireAroundTheBase() {
        Mesh mesh=new Mesh(); FugaRenderer.emit(mesh,new Matrix4f(),frame(2,46),Vec3.ZERO);
        assertTrue(mesh.vertices.stream().anyMatch(p->p[1]<6&&Math.hypot(p[0],p[2])>40),"ground fire ring");
        assertTrue(mesh.vertices.stream().anyMatch(p->p[3]==42&&p[4]==33&&p[5]==28),"dark debris");
        assertTrue(mesh.vertices.stream().anyMatch(p->p[3]==176&&p[4]==112&&p[5]==255),"violet energy rays");
    }
    private static FugaFxPayload event(int caster,long token,int phase) {
        return new FugaFxPayload(caster,token,0,0,0,0,0,160,phase,token+(phase==0?0:200));
    }
    @Test void staleEventsCannotReviveCancelledCastsAndWorldChangesClearAllViews() {
        Minecraft client=mock(Minecraft.class); client.level=mock(ClientLevel.class); client.player=mock(LocalPlayer.class);
        when(client.level.getGameTime()).thenReturn(1000L);
        when(client.player.getId()).thenReturn(20);
        ItemStack wand=mock(ItemStack.class);
        when(wand.getItem()).thenReturn(mock(GodWandItem.class));
        when(client.player.isAlive()).thenReturn(true); when(client.player.isUsingItem()).thenReturn(true);
        when(client.player.getUseItem()).thenReturn(wand);
        when(client.player.getPosition(anyFloat())).thenReturn(Vec3.ZERO);
        when(client.player.getViewVector(anyFloat())).thenReturn(new Vec3(0,0,1));
        when(client.level.getEntity(20)).thenReturn(client.player);
        try(var minecraft=mockStatic(Minecraft.class);var mode=mockStatic(GodWandItem.class)) {
            mode.when(() -> GodWandItem.getSkillMode(wand)).thenReturn(GodWandItem.MODE_FUGA);
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            FugaRenderer.reset();
            for(int caster=0;caster<20;caster++) FugaRenderer.receive(event(caster,100,0));
            assertNull(FugaRenderer.frameFor(0,0)); assertNotNull(FugaRenderer.frameFor(19,0));
            FugaRenderer.receive(event(20,100,1));
            assertEquals(1,FugaRenderer.localFrame(0).phase());
            FugaRenderer.receive(event(20,100,0)); assertEquals(1,FugaRenderer.localFrame(0).phase());
            FugaRenderer.receive(event(20,100,3)); FugaRenderer.receive(event(20,100,1));
            assertNull(FugaRenderer.localFrame(0));
            FugaRenderer.receive(event(20,200,0)); assertNotNull(FugaRenderer.localFrame(0));
            assertTrue(FugaRenderer.localFrame(0).age()>120,"Hold age must not clamp at ready");
            when(client.level.getGameTime()).thenReturn(20000L); FugaRenderer.tick(client);
            assertNotNull(FugaRenderer.localFrame(0),"Held aim has no arbitrary client timeout");
            client.level=mock(ClientLevel.class); FugaRenderer.tick(client);
            assertNull(FugaRenderer.localFrame(0)); assertNull(FugaRenderer.frameFor(19,0));
        } finally { FugaRenderer.reset(); }
    }
    @Test void impactStampedAheadSurvivesAClientClockThatTrailsTheServer() {
        Minecraft client=mock(Minecraft.class); client.level=mock(ClientLevel.class); client.player=mock(LocalPlayer.class);
        when(client.player.getId()).thenReturn(20);
        when(client.player.isAlive()).thenReturn(true);
        when(client.player.getPosition(anyFloat())).thenReturn(Vec3.ZERO);
        when(client.player.getViewVector(anyFloat())).thenReturn(new Vec3(0,0,1));
        when(client.level.getEntity(20)).thenReturn(client.player);
        try(var minecraft=mockStatic(Minecraft.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            FugaRenderer.reset();
            // Server time 300 stamps the impact 314; the client's clock trails it by 40 ticks (latency + server catch-up).
            when(client.level.getGameTime()).thenReturn(260L);
            FugaRenderer.receive(new FugaFxPayload(20,100,0,0,0,0,0,160,FugaFxPayload.IMPACT,314));
            FugaRenderer.tick(client);
            assertNotNull(FugaRenderer.localFrame(0),"A delayed clock must not drop the impact");
            when(client.level.getGameTime()).thenReturn(-40L);
            FugaRenderer.tick(client);
            assertNull(FugaRenderer.localFrame(0),"A frame stamped far in the future is still discarded");
        } finally { FugaRenderer.reset(); }
    }
    @Test void missingOrReleasedCastersExpireAfterReceiptGraceButValidHoldHasNoTimeout() {
        Minecraft client=mock(Minecraft.class); client.level=mock(ClientLevel.class); client.player=mock(LocalPlayer.class);
        when(client.player.getId()).thenReturn(20);
        when(client.level.getGameTime()).thenReturn(1000L);
        ItemStack wand=mock(ItemStack.class);
        when(wand.getItem()).thenReturn(mock(GodWandItem.class));
        when(client.player.isAlive()).thenReturn(true); when(client.player.isUsingItem()).thenReturn(true);
        when(client.player.getUseItem()).thenReturn(wand);
        when(client.player.getPosition(anyFloat())).thenReturn(Vec3.ZERO);
        when(client.player.getViewVector(anyFloat())).thenReturn(new Vec3(0,0,1));
        try(var minecraft=mockStatic(Minecraft.class);var mode=mockStatic(GodWandItem.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            mode.when(() -> GodWandItem.getSkillMode(wand)).thenReturn(GodWandItem.MODE_FUGA);
            FugaRenderer.reset(); FugaRenderer.receive(event(20,100,0));
            when(client.level.getGameTime()).thenReturn(1019L); FugaRenderer.tick(client);
            assertNotNull(FugaRenderer.localFrame(0),"Allow entity/item synchronization after receipt, even for an old cast");
            when(client.level.getGameTime()).thenReturn(1021L); FugaRenderer.tick(client);
            assertNull(FugaRenderer.localFrame(0),"An unloaded caster cannot leave a permanent charge");
            when(client.level.getEntity(20)).thenReturn(client.player);
            FugaRenderer.receive(event(20,200,0));
            when(client.level.getGameTime()).thenReturn(50000L); FugaRenderer.tick(client);
            assertNotNull(FugaRenderer.localFrame(0),"Valid hold is indefinite");
            when(client.player.isUsingItem()).thenReturn(false); FugaRenderer.tick(client);
            assertNull(FugaRenderer.localFrame(0),"A missed CANCEL must recover from the synced item-use state");
            when(client.player.isUsingItem()).thenReturn(true); FugaRenderer.receive(event(20,300,0));
            when(client.level.getGameTime()).thenReturn(50021L);
            mode.when(() -> GodWandItem.getSkillMode(wand)).thenReturn(GodWandItem.MODE_ATOMIC);
            FugaRenderer.tick(client); assertNull(FugaRenderer.localFrame(0),"A different spell is not a held Fuga");
        } finally { FugaRenderer.reset(); }
    }
    @Test void blackBackdropCoversTheRitualAndEndsBeforeShoulderAim() {
        Mesh mesh=new Mesh(); FugaRenderer.emitBackdrop(mesh,new Matrix4f(),frame(0,12),Vec3.ZERO,1);
        assertEquals(8,mesh.vertices.size(),"black wall and black floor only");
        for(float[] p:mesh.vertices) { assertEquals(0,p[3]+p[4]+p[5]); assertEquals(255,p[6]); }
        for(float[] p:mesh.vertices.subList(0,4)) assertEquals(-.4,p[2],.0001,"Opening wall sits just behind the subject, opposite the aim");
        Mesh silence=new Mesh(); FugaRenderer.emitBackdrop(silence,new Matrix4f(),new FugaRenderer.Frame(1,100,0,110,Vec3.ZERO,new Vec3(0,0,160)),Vec3.ZERO,1);
        for(float[] p:silence.vertices.subList(0,4)) assertEquals(-3.4,p[2],.0001,"After the clap the wall moves behind the whole vortex");
        assertTrue(mesh.vertices.stream().mapToDouble(p->p[0]).max().orElseThrow()>20);
        Mesh faded=new Mesh(); FugaRenderer.emitBackdrop(faded,new Matrix4f(),frame(0,31),Vec3.ZERO,.5);
        assertEquals(127,faded.vertices.get(0)[6]);
        for(int phase:new int[]{0,1,2}) {
            Mesh ended=new Mesh(); FugaRenderer.emitBackdrop(ended,new Matrix4f(),new FugaRenderer.Frame(1,100,phase,176,Vec3.ZERO,new Vec3(0,0,160)),Vec3.ZERO,1);
            assertTrue(ended.vertices.isEmpty());
        }
        Mesh disabled=new Mesh(); FugaRenderer.emitBackdrop(disabled,new Matrix4f(),frame(0,12),Vec3.ZERO,0);
        assertTrue(disabled.vertices.isEmpty());
    }
    @Test void ritualFireHasNestedHotCoreAndVolumeAcrossBothTransverseAxes() {
        Mesh mesh=new Mesh();
        FugaRenderer.emit(mesh,new Matrix4f(),new FugaRenderer.Frame(1,100,0,100,Vec3.ZERO,new Vec3(0,0,160)),Vec3.ZERO);
        assertFalse(mesh.vertices.isEmpty());
        var core=mesh.vertices.stream().filter(p->p[3]==255&&p[4]==248&&p[5]==230).toList();
        var shell=mesh.vertices.stream().filter(p->p[3]==184&&p[4]==26&&p[5]==3).toList();
        assertFalse(core.isEmpty());assertFalse(shell.isEmpty());
        for(int axis:new int[]{1,2}) {
            final int a=axis;
            assertTrue(shell.stream().mapToDouble(p->p[a]).max().orElseThrow()-shell.stream().mapToDouble(p->p[a]).min().orElseThrow()>.07,"fire is not a flat plane");
        }
        assertTrue(mesh.vertices.size()<32000,"bounded ritual mesh "+mesh.vertices.size());
    }
    @Test void heldArrowStaysBetweenHandsAndHasNoLongBackwardBeam() {
        var saved=FugaAimRig.availableFor;FugaAimRig.availableFor=id->true;
        try {
            var f=new FugaRenderer.Frame(1,100,0,240,Vec3.ZERO,new Vec3(0,0,160));
            Mesh mesh=new Mesh();FugaRenderer.emit(mesh,new Matrix4f(),f,Vec3.ZERO);
            assertTrue(mesh.vertices.stream().filter(v->v[1]>.2).mapToDouble(v->v[2]).min().orElseThrow()>-7,"trail drags far behind, but stays bounded");
            assertTrue(mesh.vertices.stream().filter(v->v[1]>.2).mapToDouble(v->v[2]).max().orElseThrow()<8);
            var rear=FugaRenderer.chargeHand(f,Vec3.ZERO,true);var front=FugaRenderer.chargeHand(f,Vec3.ZERO,false);
            assertTrue(front.z-rear.z>.6);assertTrue(Math.abs(front.x-rear.x)<.15);
            assertFalse(mesh.vertices.isEmpty());
        } finally {FugaAimRig.availableFor=saved;}
    }
    static class Mesh implements VertexConsumer {
        final List<float[]> vertices=new ArrayList<>(); float[] current;
        public VertexConsumer addVertex(float x,float y,float z) { current=new float[]{x,y,z,0,0,0,0};vertices.add(current);return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) {current[3]=r;current[4]=g;current[5]=b;current[6]=a;return this;}
        public VertexConsumer setUv(float u,float v){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    @Test void pulledFlameCracklesInSingleTickFlashesDenserBeforeTheClap() {
        int early=0,late=0;
        for(int tick=0;tick<220;tick++) {
            double c=FugaRenderer.crackle(tick+.5);
            assertTrue(c>=0&&c<=1);
            if(tick<40||tick>=FugaPose.CLAP)assertEquals(0,c,"only while the palms pull the ball");
            else if(c>0){if(tick<FugaPose.CLAP-22)early++;else late++;}
        }
        assertEquals(1,FugaRenderer.crackle(FugaPose.CLAP-1.5),"flash right before the clap");
        assertTrue(early>0,"occasional flashes while pulling");
        assertTrue(late/22.0>early/Math.max(1.0,FugaPose.CLAP-62),"denser as the clap nears");
    }
    @Test void hitStopFreezesTheArrowAtTheDistanceItFlew() {
        for(double d:new double[]{.5,12,80,160}) assertEquals(d,net.godwand.skill.FugaSequence.flightDistance(FugaRenderer.flightTime(d)),1e-6);
    }
}
