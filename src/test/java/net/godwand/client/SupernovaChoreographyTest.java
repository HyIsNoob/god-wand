package net.godwand.client;

import com.google.gson.Gson;
import net.godwand.skill.SupernovaSequence;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SupernovaChoreographyTest {
    @Test
    void chargeMarkerConnectsTheLockedGroundToTheFutureCoreWithProminentBeacon() {
        for (float charge : new float[]{0, .5f, 1}) {
            Mesh mesh = new Mesh();
            SupernovaAccretionRenderer.emitChargeMarker(mesh, new Matrix4f(), charge);
            assertFalse(mesh.vertices.isEmpty());
            assertEquals(-20, mesh.vertices.stream().mapToDouble(v -> v[1]).min().orElseThrow());
            assertTrue(mesh.vertices.stream().anyMatch(v -> v[1] > 0 && v[6] > 0));
            for (float[] v : mesh.vertices) {
                // Assert slender vertical beacon with no expansive ground rings
                assertTrue(Math.abs(v[0]) <= 0.8 && Math.abs(v[2]) <= 0.8);
                assertTrue(v[6] >= 0 && v[6] <= 255);
            }
        }
    }
    @Test
    void skyOpensDuringChargeThenSurgesAndReturnsWithOneInwardWave() {
        float previous = 0;
        for (int tick = 0; tick <= 140; tick++) {
            float scale = SupernovaSequence.chargeRiftScale(tick);
            assertTrue(scale >= previous && scale <= 1);
            previous = scale;
        }
        assertEquals(0, SupernovaSequence.chargeRiftScale(0));
        assertEquals(1, SupernovaSequence.chargeRiftScale(140));
        assertEquals(1, SupernovaSequence.skyScale(0));
        assertEquals(1, SupernovaSequence.skyScale(SupernovaSequence.SKY_RELEASE));
        assertEquals(1.65f, SupernovaSequence.skyScale(SupernovaSequence.SKY_RELEASE + 20), .00001f);
        assertEquals(net.godwand.skill.SupernovaManager.DETONATION_TICK, SupernovaSequence.SKY_RELEASE);
        assertEquals(0, SupernovaSequence.activationPulse(0));
        assertTrue(SupernovaSequence.activationPulse(3) > .99);
        assertEquals(0, SupernovaSequence.activationPulse(22));
        assertEquals(0, SupernovaSequence.skyPulseStrength(100));
        assertEquals(SupernovaSequence.SKY_RELEASE + 94, SupernovaSequence.SKY_CLOSE_START);
        assertEquals(SupernovaSequence.SKY_RELEASE + 186, SupernovaSequence.SKY_CLOSE_END);
        assertEquals(0, SupernovaSequence.skyCollapse(SupernovaSequence.SKY_RELEASE + 90));
        previous = SupernovaSequence.skyScale(SupernovaSequence.SKY_CLOSE_START);
        float previousRadius = SupernovaSequence.skyPulseRadius(SupernovaSequence.SKY_CLOSE_START);
        for (int tick = SupernovaSequence.SKY_CLOSE_START; tick <= SupernovaSequence.SKY_CLOSE_END; tick++) {
            assertTrue(SupernovaSequence.skyScale(tick) <= previous);
            assertTrue(SupernovaSequence.skyPulseRadius(tick) <= previousRadius);
            previous = SupernovaSequence.skyScale(tick);
            previousRadius = SupernovaSequence.skyPulseRadius(tick);
        }
        assertEquals(0, SupernovaSequence.skyScale(SupernovaSequence.SKY_CLOSE_END));
        assertEquals(0, SupernovaSequence.skyPulseStrength(SupernovaSequence.SKY_CLOSE_END));
    }

    @Test
    void detonationSwallowsTheSkyThenRestoresDaylightOverTheLongerReturn() {
        assertEquals(0, SupernovaSequence.skyBlackout(0));
        assertEquals(0, SupernovaSequence.skyBlackout(SupernovaSequence.SKY_RELEASE));
        assertEquals(1, SupernovaSequence.skyBlackout(SupernovaSequence.SKY_RELEASE + 20));
        assertEquals(1, SupernovaSequence.skyBlackout(SupernovaSequence.SKY_CLOSE_START));
        float previous = 1;
        for (int tick = SupernovaSequence.SKY_CLOSE_START; tick <= SupernovaSequence.SKY_CLOSE_END; tick++) {
            float darkness = SupernovaSequence.skyBlackout(tick);
            assertTrue(darkness >= 0 && darkness <= previous);
            previous = darkness;
        }
        assertEquals(0, previous);
        assertEquals(92, SupernovaSequence.SKY_CLOSE_END - SupernovaSequence.SKY_CLOSE_START);
    }

    @Test
    void swallowingIsStaggeredAndFinishesJustBeforeTheCoreCutsOut() {
        assertEquals(0,SupernovaSequence.swallow(250,0));
        assertTrue(SupernovaSequence.swallow(270,0) > SupernovaSequence.swallow(270,1));
        assertTrue(SupernovaSequence.swallow(318,1) < 0.99);
        assertEquals(1,SupernovaSequence.swallow(SupernovaSequence.SWALLOW_END,1));
        assertTrue(SupernovaSequence.SWALLOW_END < SupernovaSequence.CUT_START);
        assertTrue(SupernovaSequence.CUT_END < SupernovaSequence.FLARE_START);
        assertTrue(SupernovaSequence.FLARE_START < SupernovaSequence.IMPACT);
        float previous=0;
        for (int tick=0;tick<=324;tick++) {
            float pressure=SupernovaSequence.pressure(tick);
            assertTrue(pressure >= previous);
            previous=pressure;
        }
        assertTrue(SupernovaSequence.environmentGain(300) > 0.5);
        assertTrue(SupernovaSequence.environmentGain(324) < 0.05);
        assertEquals(0,SupernovaSoundscape.volume(300,0));
        assertTrue(SupernovaSoundscape.volume(-1,0.7f) > 0);
        assertEquals(0,SupernovaSoundscape.volume(324,0));
        assertEquals(0,SupernovaSoundscape.volume(-1,0));
        assertTrue(SupernovaSequence.cameraFov(180)>1);
        assertTrue(SupernovaSequence.cameraFov(324)<0.9);
        assertTrue(SupernovaSequence.cameraFov(250)>SupernovaSequence.cameraFov(300));
    }

    @Test
    void actualCinematicMeshesStayFiniteAndVanishBeforeTheFlare() throws Exception {
        for (int tick:new int[]{10,80,180,250,290,318,324,328,332}) {
            Mesh mesh=new Mesh();
            SupernovaAccretionRenderer.emit(mesh,new Matrix4f(),tick);
            if(tick<328 || (tick>=330 && tick<=340)) assertFalse(mesh.vertices.isEmpty());
            else assertTrue(mesh.vertices.isEmpty());
            assertEquals(0,mesh.vertices.size()%4);
            assertTrue(mesh.vertices.size()<=10000);
            for(float[] v:mesh.vertices) {
                for(float value:v) assertTrue(Float.isFinite(value));
                assertTrue(Math.abs(v[0])<65 && Math.abs(v[1])<65 && Math.abs(v[2])<65);
                assertTrue(v[6]>=0 && v[6]<=255);
                if (v[6] > 0) {
                    assertTrue(v[3] >= v[4] + 30 && v[5] >= v[4] + 30,
                            "Black-hole ribbons and infall sparks must share the violet-magenta palette");
                }
            }
            save("nova-buildup-"+tick,mesh);
        }
        assertNotEquals(SupernovaAccretionRenderer.streamPoint(200,1,0.4),SupernovaAccretionRenderer.streamPoint(201,1,0.4));
    }

    @Test
    void idleAuraHasReadablePresenceAgainstDaylightAtEveryPhase() {
        double minimum=Double.MAX_VALUE;
        for(int tick=0;tick<400;tick+=10) {
            Mesh mesh=new Mesh();
            SpatialAuraRenderer.emitField(mesh,new Matrix4f(),new Vec3d(0,0,-1),tick,false);
            double coverage=mesh.vertices.stream().mapToDouble(v->v[6]).sum();
            minimum=Math.min(minimum,coverage);
        }
        assertTrue(minimum>60000,"Keep a visible field throughout the cycle, not only at ribbon peaks");
    }
    private static void save(String name,Mesh mesh)throws Exception {
        Path p=Path.of("build/previews/"+name+"-mesh.json");
        Files.createDirectories(p.getParent());Files.writeString(p,new Gson().toJson(mesh.vertices));
    }
    private static class Mesh implements VertexConsumer {
        final List<float[]> vertices=new ArrayList<>();float[] current;
        public VertexConsumer vertex(float x,float y,float z){current=new float[]{x,y,z,0,0,0,0};vertices.add(current);return this;}
        public VertexConsumer color(int r,int g,int b,int a){current[3]=r;current[4]=g;current[5]=b;current[6]=a;return this;}
        public VertexConsumer texture(float u,float v){return this;}
        public VertexConsumer overlay(int u,int v){return this;}
        public VertexConsumer light(int u,int v){return this;}
        public VertexConsumer normal(float x,float y,float z){return this;}
    }
}
