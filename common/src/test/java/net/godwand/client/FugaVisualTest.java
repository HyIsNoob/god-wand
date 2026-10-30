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
    private static double impactTick(double visual) { return visual<40?visual*2:80+(visual-40)/.8; }
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    private static FugaRenderer.Frame frame(int phase,double age) {
        return new FugaRenderer.Frame(1,100,phase,phase==2?impactTick(age):phase==0?age*5/3:age,Vec3.ZERO,new Vec3(0,0,160));
    }
    @Test void allPhasesHaveFiniteBoundedVolumesAndFinishCleanly() {
        for(int phase=0;phase<5;phase++) for(double age:new double[]{0,1,32,60,119,120,159,160,600}) {
            Mesh mesh=new Mesh(); FugaRenderer.emit(mesh,new Matrix4f(),frame(phase,age),Vec3.ZERO);
            if(phase>=3 || phase==2&&age>=160) assertTrue(mesh.vertices.isEmpty());
            else assertFalse(mesh.vertices.isEmpty(),"Missing phase "+phase+" at "+age);
            assertEquals(0,mesh.vertices.size()%4);
            assertTrue(mesh.vertices.size()<40000,"Unbounded mesh: "+mesh.vertices.size());
            for(float[] p:mesh.vertices) {
                for(float n:p)assertTrue(Float.isFinite(n));
                assertTrue(Math.abs(p[0])<130&&Math.abs(p[2])<180);
                assertTrue(p[1]>=-384&&p[1]<=384);
                assertTrue(p[6]>=0&&p[6]<=255);
            }
        }
        Mesh invalid=new Mesh(); FugaRenderer.emit(invalid,new Matrix4f(),frame(1,Double.NaN),Vec3.ZERO);
        assertTrue(invalid.vertices.isEmpty());
    }
    @Test void arrowIsStraightClampedAndLeavesALongAfterimage() {
        assertEquals(new Vec3(0,0,80),frame(1,20).arrowPosition());
        assertEquals(new Vec3(0,0,160),frame(1,100).arrowPosition());
        Mesh mesh=new Mesh(); FugaRenderer.emit(mesh,new Matrix4f(),frame(1,20),Vec3.ZERO);
        assertTrue(mesh.vertices.stream().mapToDouble(p->p[2]).min().orElseThrow()<18);
        assertEquals(80,mesh.vertices.stream().filter(p->p[3]==255&&p[4]==241&&p[5]==186)
                .mapToDouble(p->p[2]).max().orElseThrow(),.001);
        assertTrue(mesh.vertices.stream().anyMatch(p->Math.abs(p[0])>.5),"Arrowhead needs a broad silhouette");
    }
    @Test void bowAndArrowKeepTheirSizeDuringHoldAndRotateWithAim() {
        Mesh ready=new Mesh(),held=new Mesh(),side=new Mesh();
        FugaRenderer.emit(ready,new Matrix4f(),frame(0,120),Vec3.ZERO);
        FugaRenderer.emit(held,new Matrix4f(),frame(0,600),Vec3.ZERO);
        FugaRenderer.emit(side,new Matrix4f(),new FugaRenderer.Frame(1,100,0,120,Vec3.ZERO,new Vec3(160,0,0)),Vec3.ZERO);
        assertEquals(ready.vertices.size(),held.vertices.size());
        assertTrue(ready.vertices.stream().mapToDouble(p->p[2]).max().orElseThrow()<1.5);
        assertTrue(side.vertices.stream().mapToDouble(p->p[0]).max().orElseThrow()>.8);
        assertTrue(held.vertices.stream().anyMatch(p->p[3]==255&&p[4]==241&&p[5]==186));
    }
    @Test void sphereDescendsBeforeTheColumnReaches384AndContractsAfter120() {
        Mesh high=new Mesh(),low=new Mesh(),column=new Mesh(),contract=new Mesh();
        FugaRenderer.emit(high,new Matrix4f(),frame(2,1),Vec3.ZERO);
        FugaRenderer.emit(low,new Matrix4f(),frame(2,32),Vec3.ZERO);
        assertTrue(high.vertices.stream().mapToDouble(p->p[1]).max().orElseThrow()
                >low.vertices.stream().mapToDouble(p->p[1]).max().orElseThrow()+10);
        assertTrue(high.vertices.stream().anyMatch(p->p[5]>p[3]),"Sphere needs its limited violet/blue edge");
        FugaRenderer.emit(column,new Matrix4f(),frame(2,90),Vec3.ZERO);
        FugaRenderer.emit(contract,new Matrix4f(),frame(2,155),Vec3.ZERO);
        assertEquals(384,column.vertices.stream().mapToDouble(p->p[1]).max().orElseThrow(),.001);
        double full=column.vertices.stream().filter(p->p[1]>300).mapToDouble(p->Math.hypot(p[0],p[2])).max().orElseThrow();
        double small=contract.vertices.stream().filter(p->p[1]>300).mapToDouble(p->Math.hypot(p[0],p[2])).max().orElseThrow();
        assertTrue(full>small*8);
    }
    @Test void beamMatchesDamageRadiusAndReachesWorldFloorWithoutMovingTopOrSurfaceAura() {
        var origin=new Vec3(5,70,9); var eye=new Vec3(1,4,2);
        var frame=new FugaRenderer.Frame(1,100,2,impactTick(90),origin,origin);
        Mesh mesh=new Mesh(); FugaRenderer.emit(mesh,new Matrix4f(),frame,eye,-134);
        var shell=mesh.vertices.stream().filter(p->p[3]==244&&p[4]==43&&p[5]==6).toList();
        assertFalse(shell.isEmpty());
        assertEquals(-68,shell.stream().mapToDouble(p->p[1]).min().orElseThrow(),.001,"World min -64 minus eye Y4");
        assertEquals(450,shell.stream().mapToDouble(p->p[1]).max().orElseThrow(),.001,"Anchor70 plus384 minus eye4");
        double maxRadius=shell.stream().mapToDouble(p->Math.hypot(p[0]-4,p[2]-7)).max().orElseThrow();
        assertTrue(maxRadius>47&&maxRadius<=48.0001,"Turbulent shell must fill the server radius without exceeding48: "+maxRadius);
        Mesh shallow=new Mesh(); FugaRenderer.emit(shallow,new Matrix4f(),frame,eye,-20);
        var aura=mesh.vertices.stream().filter(p->p[3]==255&&p[4]==98&&p[5]==21).toList();
        var shallowAura=shallow.vertices.stream().filter(p->p[3]==255&&p[4]==98&&p[5]==21).toList();
        assertEquals(aura.size(),shallowAura.size());
        for(int i=0;i<aura.size();i++)assertArrayEquals(aura.get(i),shallowAura.get(i));
    }
    @Test void auraContinuouslyOccupiesInterveningRadiiInsteadOfOnlyWaveShells() {
        for(int age=40;age<160;age++) {
            Mesh mesh=new Mesh(); FugaRenderer.aura(mesh,new Matrix4f(),Vec3.ZERO,age,1);
            Set<Integer> radii=new HashSet<>();
            for(float[] p:mesh.vertices)radii.add((int)Math.hypot(p[0],p[2])/7);
            assertTrue(radii.size()>=12,"Aura gaps at "+age);
            assertEquals(6912,mesh.vertices.size());
        }
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
    @Test void blackIntroBackdropStaysBehindSubjectAndEndsAt36() {
        Mesh mesh=new Mesh(); FugaRenderer.emitBackdrop(mesh,new Matrix4f(),frame(0,12),Vec3.ZERO,1);
        assertEquals(4,mesh.vertices.size());
        for(float[] p:mesh.vertices) {
            assertEquals(-.4,p[2],.0001,"Backdrop must stay opposite the horizontal aim");
            assertEquals(0,p[3]+p[4]+p[5]); assertEquals(255,p[6]);
        }
        assertTrue(mesh.vertices.stream().mapToDouble(p->p[0]).max().orElseThrow()>20);
        Mesh faded=new Mesh(); FugaRenderer.emitBackdrop(faded,new Matrix4f(),frame(0,31),Vec3.ZERO,.5);
        assertEquals(127,faded.vertices.get(0)[6]);
        for(int phase:new int[]{0,1,2}) {
            Mesh ended=new Mesh(); FugaRenderer.emitBackdrop(ended,new Matrix4f(),frame(phase,36),Vec3.ZERO,1);
            assertTrue(ended.vertices.isEmpty());
        }
        Mesh disabled=new Mesh(); FugaRenderer.emitBackdrop(disabled,new Matrix4f(),frame(0,12),Vec3.ZERO,0);
        assertTrue(disabled.vertices.isEmpty());
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
}
