package net.godwand.client;

import net.godwand.config.GodWandConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FugaPlan1Test {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    private static FugaRenderer.Frame frame(long token,double age) {return new FugaRenderer.Frame(1,token,0,age,Vec3.ZERO,new Vec3(0,0,160));}
    private static AtomicCinematic.Pose shot(double age) {return FugaCinematic.cameraFor(frame(1,age),Vec3.ZERO,0,0,Vec3.ZERO,0,0,true);}

    @Test void chargeCameraHasContinuousPositionAndFacingAcrossEveryBoundary() {
        var previous=shot(0);
        for(double t=.02;t<=200.02;t+=.02) {
            var current=shot(t);
            if(t<24 || t>24.03) assertTrue(previous.position().distanceTo(current.position())<.02,"position at "+t);
            if(t<24 || t>24.03) assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(current.yaw()-previous.yaw()))<.15,"yaw at "+t);
            if(t<24 || t>24.03) assertTrue(Math.abs(current.pitch()-previous.pitch())<.15,"pitch at "+t);
            assertTrue(current.position().horizontalDistance()>=.90,"camera enters player at "+t);
            previous=current;
        }
    }
    @Test void faceHandsJoinPullAndDrawHaveSeparateReadableBeats() {
        var face=FugaPose.arms(0,24,0,0);
        var join=FugaPose.arms(0,24,0,0);
        var ready=FugaPose.arms(0,200,0,0);
        assertEquals(join.rightYaw(),face.rightYaw(),.001,"hands already joined during face shot");
        assertTrue(join.rightYaw()<-.48&&join.leftYaw()>.48,"palms should meet before drawing");
        assertEquals(-.5f,ready.rightYaw(),.001,"native left-bow drawing arm");
        assertEquals(-(float)Math.PI/2,ready.leftPitch(),.03,"front arm points forward");
        for(double t=.02;t<230;t+=.02) {
            var a=FugaPose.arms(0,t-.02,0,0);var b=FugaPose.arms(0,t,0,0);
            assertTrue(Math.abs(a.rightPitch()-b.rightPitch())<.02,"arm discontinuity at "+t);
            assertTrue(Math.abs(a.rightYaw()-b.rightYaw())<.02,"draw discontinuity at "+t);
        }
        for(int t=200;t<12200;t++) {
            var held=FugaPose.arms(0,t,0,0);
            assertTrue(Math.abs(held.leftPitch()-ready.leftPitch())<.025,"hold drift");
            assertEquals(1,held.weight(),.001);
        }
    }
    @Test void castIdentityAndInvalidStatesRestoreCinematicOwnershipImmediately() {
        var client=mock(Minecraft.class);client.player=mock(LocalPlayer.class);client.level=mock(ClientLevel.class);
        when(client.player.isAlive()).thenReturn(true);when(client.isWindowActive()).thenReturn(true);
        var config=GodWandConfig.CLIENT;GodWandConfig.CLIENT=new GodWandConfig.Client();
        try(var minecraft=mockStatic(Minecraft.class);var frames=mockStatic(FugaRenderer.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            frames.when(()->FugaRenderer.localFrame(0)).thenReturn(frame(100,30));
            FugaCinematic.reset();assertTrue(FugaCinematic.active());FugaCinematic.skip();
            var fired=new FugaRenderer.Frame(1,100,1,2,Vec3.ZERO,new Vec3(0,0,160));
            frames.when(()->FugaRenderer.localFrame(0)).thenReturn(fired);assertFalse(FugaCinematic.active());
            frames.when(()->FugaRenderer.localFrame(0)).thenReturn(new FugaRenderer.Frame(1,101,1,0,Vec3.ZERO,new Vec3(0,0,160)));
            assertTrue(FugaCinematic.active(),"next cast must not inherit skip before next tick");
            when(client.player.isSpectator()).thenReturn(true);assertFalse(FugaCinematic.active());assertFalse(FugaCinematic.movementLocked());
            when(client.player.isSpectator()).thenReturn(false);when(client.player.isAlive()).thenReturn(false);assertFalse(FugaCinematic.active());
            when(client.player.isAlive()).thenReturn(true);client.screen=mock(Screen.class);assertFalse(FugaCinematic.active());
            client.screen=null;when(client.isWindowActive()).thenReturn(false);assertFalse(FugaCinematic.active());
            when(client.isWindowActive()).thenReturn(true);assertTrue(FugaCinematic.active());
            FugaCinematic.skip();client.level=mock(ClientLevel.class);assertTrue(FugaCinematic.active(),"world identity must reset skipped casts even with matching timestamps");
            frames.when(()->FugaRenderer.localFrame(0)).thenReturn(null);assertFalse(FugaCinematic.active());assertFalse(FugaCinematic.movementLocked());assertEquals(1,FugaCinematic.environmentGain());
        } finally {FugaCinematic.reset();GodWandConfig.CLIENT=config;}
    }
    @Test void cinematicOffKeepsOnlyRequiredAimAndNeverDarkensOrZoomsTheScene() {
        var client=mock(Minecraft.class);client.player=mock(LocalPlayer.class);client.level=mock(ClientLevel.class);
        when(client.player.isAlive()).thenReturn(true);when(client.isWindowActive()).thenReturn(true);
        var config=GodWandConfig.CLIENT;GodWandConfig.CLIENT=new GodWandConfig.Client();GodWandConfig.CLIENT.fugaCutscene=false;
        try(var minecraft=mockStatic(Minecraft.class);var frames=mockStatic(FugaRenderer.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            frames.when(()->FugaRenderer.localFrame(0)).thenReturn(frame(200,20));
            assertTrue(FugaCinematic.movementLocked());
            assertEquals(1,FugaCinematic.environmentGain());assertEquals(83,FugaCinematic.fov(83,0));
            var shot=FugaCinematic.cameraFor(frame(200,20),Vec3.ZERO,0,0,Vec3.ZERO,30,15,false);
            var held=FugaCinematic.cameraFor(frame(200,200),Vec3.ZERO,0,0,Vec3.ZERO,30,15,true);
            assertEquals(held,shot,"disabled intro uses aim view immediately");
        } finally {FugaCinematic.reset();GodWandConfig.CLIENT=config;}
    }
    @Test void inactiveAndFinishedFugaNeverOverrideOrdinaryOrAtomicModelAngles() {
        var right=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of());
        var left=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of());
        var torso=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of());
        var head=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of());
        right.xRot=.31f;left.yRot=-.42f;torso.yRot=.27f;
        try(var frames=mockStatic(FugaRenderer.class)) {
            assertFalse(FugaPose.apply(1,0,right,left,torso,head));
            frames.when(()->FugaRenderer.frameFor(1,0)).thenReturn(new FugaRenderer.Frame(1,1,2,14,Vec3.ZERO,Vec3.ZERO));
            assertFalse(FugaPose.apply(1,0,right,left,torso,head));
            assertEquals(.31f,right.xRot);assertEquals(-.42f,left.yRot);assertEquals(.27f,torso.yRot);
        }
    }

    @Test void joinedHandsSeparateInParallelBeforeTheArrowDraw() {
        assertEquals(FugaPose.arms(0,0,0,0),FugaPose.arms(0,40,0,0),"ritual is already joined before hand shot");
        var contact=FugaPose.hand(40,0,true).subtract(FugaPose.hand(40,0,false));
        var spread=FugaPose.hand(88,0,true).subtract(FugaPose.hand(88,0,false));
        var clapped=FugaPose.hand(FugaPose.CLAP,0,true).subtract(FugaPose.hand(FugaPose.CLAP,0,false));
        assertTrue(contact.length()<.25,"palms meet at the cut");
        assertTrue(spread.length()>contact.length()+.6,"hands pull ribbon apart");
        assertTrue(FugaPose.arms(0,85,0,0).rightPitch()<FugaPose.arms(0,85,0,0).leftPitch()-.4,"staggered pull: right hand a little above the left");
        assertTrue(clapped.length()<.25,"the palms snap shut on the flame");
        assertTrue(shot(52).position().horizontalDistance()<1.8,"ritual opens on a close hand shot");
        var held=FugaPose.arms(0,138,0,0);var joined=FugaPose.arms(0,110,0,0);
        assertEquals(joined.rightYaw(),held.rightYaw(),.001,"palms stay joined through the silence and the vortex");
    }

    @Test void ritualCameraTracksTheHandSubjectInsteadOfTheWholeBody() {
        for(int age=48;age<=144;age++) {
            var pose=shot(age);var hands=FugaPose.handFocus(age,0);
            var point=new Vec3(-hands.x,hands.y,hands.z);
            var pixel=FugaCinematic.project(point,pose.position(),pose.yaw(),pose.pitch(),46,1280,720);
            assertNotNull(pixel,"hands leave frame at "+age);
            assertEquals(640,pixel.x,2);assertEquals(360,pixel.y,2);
            // Starts close on the hands, then pulls back so the whole body wrapped in the vortex reads; hands stay centred.
            assertTrue(pose.position().distanceTo(point)<(age<70?2.3:5.0),"hands framed too wide at "+age);
        }
    }

    @Test void handsShotPullsBackAndAimUsesNativeBowArmsWithAnArchersTorsoTurn() {
        double previous=shot(48).position().horizontalDistance();
        for(int t=49;t<=90;t++) {
            double radius=shot(t).position().horizontalDistance();assertTrue(radius>=previous-.001,"eases back through the pull at "+t);previous=radius;
        }
        assertTrue(previous>shot(48).position().horizontalDistance()+.45);
        assertTrue(shot((int)FugaPose.CLAP+2).position().horizontalDistance()<previous*.6,"the clap snaps the camera in on the hands");
        var pose=FugaPose.arms(0,200,0,0);
        assertEquals(FugaPose.TWIST,pose.bodyYaw(),.001,"archer's stance turns the torso; arm angles stay vanilla");
        assertEquals(-.5f,pose.rightYaw(),.001);
        assertEquals(-(float)Math.PI/2,pose.leftPitch(),.03);
    }
    @Test void pullingShotIsFrontalInThePlayersOwnFacingForEveryYaw() {
        for(float yaw:new float[]{0,45,90,180,-135})for(int age=24;age<=144;age++) {
            var p=FugaCinematic.cameraFor(frame(1,age),Vec3.ZERO,0,0,Vec3.ZERO,yaw,0,true);
            var forward=Vec3.directionFromRotation(0,yaw);
            assertEquals(0,p.position().dot(forward.cross(new Vec3(0,1,0))),.00001);
            assertTrue(p.position().dot(forward)>1);
        }
    }

}
