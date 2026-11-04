package net.godwand.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FugaCinematicTest {
    private static double impactTick(double visual) { return visual<40?visual*2:visual<60?80+(visual-40)/.8:105+(visual-60)/.5; }
    private static final Vec3 PLAYER = new Vec3(4, 70, 8);
    private static FugaRenderer.Frame frame(int phase, double age) {
        return new FugaRenderer.Frame(1, 100, phase, phase==2?impactTick(age):phase==0?age*5/3:age, PLAYER, PLAYER.add(0, 0, 100));
    }
    private static AtomicCinematic.Pose camera(int phase, double age, float yaw, float pitch, boolean director) {
        return FugaCinematic.cameraFor(frame(phase, age), PLAYER, 20, 10, PLAYER, yaw, pitch, director);
    }
    @Test void chargeReachesFreeMouseShoulderWithoutSnapping() {
        var ready = camera(0, 120, 0, 0, true);
        assertEquals(ready, camera(0, 500, 0, 0, true));
        assertEquals(ready, camera(0, 5, 0, 0, false));
        assertTrue(ready.position().distanceTo(camera(0, 119.99, 0, 0, true).position()) < .001);
        assertTrue(ready.position().distanceTo(camera(0, 120, 90, 0, true).position()) > 3);
        assertTrue(Math.abs(camera(0, 120, 0, 40, true).pitch() - ready.pitch()) > 30);
    }
    @Test void directorDisabledReleasesCameraAfterLaunchAndEndAlwaysRestores() {
        assertNull(camera(1, 2, 0, 0, false));
        assertNull(camera(2, 30, 0, 0, false));
        assertNull(camera(2, 160, 0, 0, true));
        assertNull(camera(3, 0, 0, 0, true));
        // The closing pull-back morphs into the player's own view by the end.
        var returning = camera(2, 159.99, 0, 0, true);
        assertTrue(returning.position().distanceTo(PLAYER) < .001);
        assertEquals(20, returning.yaw(), .001);
        assertEquals(10, returning.pitch(), .001);
    }
    @Test void impactShotTransitionsStayContinuousAndTravelDuringColumn() {
        for (double boundary : new double[]{110, 140}) {
            var a = camera(2, boundary - .001, 0, 0, true);
            var b = camera(2, boundary + .001, 0, 0, true);
            assertTrue(a.position().distanceTo(b.position()) < .02, "camera cut at " + boundary);
            assertEquals(0, net.minecraft.util.Mth.wrapDegrees(a.yaw() - b.yaw()), .03, "yaw cut at " + boundary);
            assertEquals(a.pitch(), b.pitch(), .03, "pitch cut at " + boundary);
        }
        assertTrue(camera(2, 60, 0, 0, true).position().distanceTo(camera(2, 90, 0, 0, true).position()) > 5);
        assertEquals(0, FugaCinematic.flash(20));
        assertEquals(1, FugaCinematic.flash(41));
        assertEquals(0, FugaCinematic.flash(50));
    }
    @Test void letterboxBreathesWithTheBeats() {
        assertTrue(FugaCinematic.letterbox(0,FugaPose.CLAP+.5)>FugaCinematic.letterbox(0,60)+.04,"the clap punches the bars wide");
        assertEquals(0,FugaCinematic.letterbox(0,220),1e-4,"no bars while the player aims");
        assertTrue(FugaCinematic.letterbox(2,40.5)>FugaCinematic.letterbox(2,30)+.05,"the detonation punches them wider");
        assertTrue(FugaCinematic.letterbox(2,30)>FugaCinematic.letterbox(2,5),"they tighten as the mass nears the ground");
        assertEquals(0,FugaCinematic.letterbox(2,160),1e-3,"gone by the end");
    }
    @Test void meteorShotsStayOutsideTheSphereAndColumnAndFrameThemFromAfar() {
        assertTrue(camera(2,10,0,0,true).position().subtract(PLAYER).horizontalDistance()<60,"close enough to feel the sphere's size");
        for(double age=0;age<40;age+=.5) {
            var sphere=PLAYER.add(0,FugaRenderer.orbCenter(age),0);
            assertTrue(camera(2,age,0,0,true).position().distanceTo(sphere)>FugaRenderer.orbRadius(age)*FugaRenderer.orbScale(age)*1.15,"inside sphere at "+age);
        }
        for(double age=46;age<120;age+=.5)
            assertTrue(camera(2,age,0,0,true).position().subtract(PLAYER).horizontalDistance()>net.godwand.skill.FugaSequence.RADIUS*1.4,"inside column at "+age);
    }
    @Test void bowArmsTrackAimAndFadeAfterImpact() {
        var ready = FugaPose.arms(0, 200, 0, 0);
        var aim = FugaPose.arms(0, 200, .5f, .4f);
        assertEquals(.5f, aim.leftPitch() - ready.leftPitch(), .0001f);
        assertEquals(.4f, aim.rightYaw() - ready.rightYaw(), .0001f);
        assertEquals(-.5f,ready.rightYaw(),.0001);
        assertTrue(ready.leftYaw() > 0);
        assertEquals(0, FugaPose.arms(2, 14, 0, 0).weight());
        assertTrue(FugaPose.arms(1, 8, 0, 0).rightYaw() < ready.rightYaw());
    }
    @Test void reticleUsesRenderedCameraLensAndRejectsBehindCamera() {
        Vec3 camera = new Vec3(10, 80, 20);
        var center = FugaCinematic.project(camera.add(0, 0, 100), camera, 0, 0, 90, 800, 600);
        assertNotNull(center);
        assertEquals(400, center.x, .001);
        assertEquals(300, center.y, .001);
        var offset = FugaCinematic.project(camera.add(-10, 10, 100), camera, 0, 0, 90, 800, 600);
        assertEquals(430, offset.x, .001);
        assertEquals(270, offset.y, .001);
        var zoom = FugaCinematic.project(camera.add(-10, 10, 100), camera, 0, 0, 45, 800, 600);
        assertTrue(zoom.x > offset.x);
        assertNull(FugaCinematic.project(camera.add(0, 0, -10), camera, 0, 0, 90, 800, 600));
        assertNull(FugaCinematic.project(camera.add(100, 0, 1), camera, 0, 0, 90, 800, 600));
        var rotated = FugaCinematic.project(camera.add(Vec3.directionFromRotation(35, 80).scale(100)), camera, 80, 35, 90, 800, 600);
        assertNotNull(rotated);
        assertEquals(400, rotated.x, .001);
        assertEquals(300, rotated.y, .001);
    }
    @Test void releaseAndImpactFlashesAreBriefAndNeverOpaque() {
        assertEquals(0, FugaCinematic.overlayFlash(1, 0), "the release impact frame owns the first ticks");
        assertTrue(FugaCinematic.overlayFlash(1, 4) > .25);
        assertEquals(0, FugaCinematic.overlayFlash(1, 7));
        assertEquals(0, FugaCinematic.overlayFlash(2, 0));
        assertTrue(FugaCinematic.overlayFlash(2, 41) > .9);
        assertEquals(0, FugaCinematic.overlayFlash(2, 46));
        for (int phase = 0; phase <= 2; phase++) for (int age = 0; age < 180; age++)
            assertTrue(FugaCinematic.overlayFlash(phase, age) < 1);
    }

 @org.junit.jupiter.api.Test void faceIsVisibleAtTheOpeningButHiddenInsideTheReturningCamera() {
  assertTrue(FugaCinematic.openingFaceVisible(new FugaRenderer.Frame(1,1,0,1,Vec3.ZERO,new Vec3(0,0,160))));
  assertFalse(FugaCinematic.openingFaceVisible(new FugaRenderer.Frame(1,1,2,159,Vec3.ZERO,new Vec3(0,0,160))));
 }
}
