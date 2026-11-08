package net.godwand.client;

import net.godwand.network.SilentGatePayload;
import net.godwand.skill.SilentGateSequence;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SilentGateCinematicTest {
    private static final Vec3 PLAYER = new Vec3(4, 70, 8);
    private static final Vec3 EYE = PLAYER.add(0, 1.62, 0);

    private static SilentGateRenderer.Frame frame(int phase, double age, double commandAge, int barrage) {
        return new SilentGateRenderer.Frame(1, 100, phase, age, commandAge, PLAYER, 0f, 7L, 48, barrage, -1);
    }
    private static AtomicCinematic.Pose build(int phase, double age) {
        return SilentGateCinematic.cameraFor(frame(phase, age, -1, 200), PLAYER, 0f, true);
    }
    private static AtomicCinematic.Pose barrage(double commandAge, int barrage) {
        return SilentGateCinematic.cameraFor(frame(2, 300 + commandAge, commandAge, barrage), PLAYER, 0f, true);
    }
    private static SilentGatePayload payload(long startedAt, int phase, int[] shots, double[] hits) {
        return new SilentGatePayload(1, startedAt, startedAt, phase, 4, 70, 8, 0f, 7L, 48, 200, shots, hits);
    }

    @BeforeEach void clean() { SilentGateRenderer.reset(); }

    @Test void cameraFollowsTheShotList() {
        assertTrue(build(0, 10).position().distanceTo(EYE) <= .6, "first person at the start");
        var up = build(0, 100);
        assertTrue(up.position().y < PLAYER.y + SilentGateSequence.circleAltitude(PLAYER.y), "under the great circle");
        assertTrue(up.pitch() < -40, "looking up at it");
        Vec3 gate = SilentGateSequence.gateCenter(PLAYER, 0f);
        assertTrue(build(0, 170).position().y < gate.y - SilentGateSequence.GATE_HEIGHT / 2.0, "below the gate's foot");
        var closing = build(0, 299);
        assertTrue(closing.position().y >= PLAYER.y + 8, "high");
        assertTrue(PLAYER.z - closing.position().z >= 12, "behind the caster (forward is +z at yaw 0)");
        var held = build(0, 300);
        for (double age : new double[]{300, 340, 400, 700})
            assertTrue(build(1, age).position().distanceTo(held.position()) <= .5, "WAIT holds the frame at " + age);
    }

    @Test void gateShotsLookUpAtTheColumnsAndPullBackWide() {
        Vec3 foot = SilentGateSequence.gateFoot(PLAYER, 0f, 0);
        var under = build(0, 205);
        assertTrue(under.position().y < foot.y - 5, "below the gate's foot: " + under.position().y);
        assertTrue(under.pitch() < -30, "looking up at the columns: " + under.pitch());
        assertTrue(under.position().distanceTo(foot) < 100, "close to the foot: " + under.position().distanceTo(foot));
        var wide = build(0, 249);
        assertTrue(wide.position().distanceTo(foot.add(0, 108, 0)) > 150, "wide enough to hold the whole gate: " + wide.position().distanceTo(foot.add(0, 108, 0)));
        assertTrue(wide.position().distanceTo(under.position()) > 100);
        assertTrue(wide.position().distanceTo(PLAYER) < 150, "the caster stays in the wide frame");
        var fall = build(0, 150);
        assertTrue(fall.pitch() < -50, "watching it come down from underneath: " + fall.pitch());
    }

    @Test void barrageShotListHasFewLongReadableShots() {
        assertArrayEquals(new int[]{12, 60, 125, 165, 272}, SilentGateCinematic.boundaries(200));
        assertArrayEquals(new int[]{12, 108, 238, 318, 472}, SilentGateCinematic.boundaries(400), "scaled with the barrage length");
        var shots = List.of(new SilentGateRenderer.Shot(3, new Vec3(5, 70, 40), 100));
        // 12..60: low wide rain, no cut inside the shot.
        assertTrue(barrage(30, 200).position().y < PLAYER.y + 6, "low wide shot");
        for (double c = 13; c < 56; c += 3)
            assertTrue(barrage(c, 200).position().distanceTo(barrage(c + 3, 200).position()) < 4, "slow dolly at " + c);
        // 60..125: kill close-up, 5.5 blocks from the target, below its eye, looking up, never jumping.
        Vec3 target = new Vec3(6, 70, 50);
        for (double c = 61; c < 122; c += 2) {
            var pose = shotAt(c, shots, target);
            assertEquals(5.5, Math.hypot(pose.position().x - target.x, pose.position().z - target.z), .01, "5.5 blocks at " + c);
            assertEquals(target.y + .6, pose.position().y, 1e-9, "just above its feet, under its 1.6 eye at " + c);
            assertTrue(pose.pitch() < -15 && pose.pitch() > -30, "looking a bit up so the beams fall into the frame: " + pose.pitch());
            var next = shotAt(c + 2, shots, target);
            assertTrue(pose.position().distanceTo(next.position()) < 1.2, "slow orbit at " + c);
        }
        double a0 = Math.atan2(shotAt(60.5, shots, target).position().x - target.x, shotAt(60.5, shots, target).position().z - target.z);
        double a1 = Math.atan2(shotAt(124.5, shots, target).position().x - target.x, shotAt(124.5, shots, target).position().z - target.z);
        assertEquals(20, Math.abs(Math.IEEEremainder(Math.toDegrees(a1 - a0), 360)), 1.5, "orbit sweep");
        // 125..165: high scale shot sinking slowly.
        var high = barrage(126, 200);
        assertEquals(PLAYER.y + 140, high.position().y, 1);
        assertTrue(barrage(164, 200).position().y < high.position().y - 35);
        assertTrue(high.pitch() > 5 && high.pitch() < 20, "slightly down, horizon in the upper third: " + high.pitch());
        double toCaster = Math.toDegrees(Math.atan2(high.position().y - PLAYER.y, high.position().distanceTo(new Vec3(PLAYER.x, high.position().y, PLAYER.z))));
        assertTrue(toCaster < high.pitch() + 35, "the caster is inside the frame (" + toCaster + " below the horizon)");
        // 165..: behind the caster, then the return.
        assertTrue(PLAYER.z - barrage(200, 200).position().z >= 14, "behind the caster");
        assertNotNull(barrage(272 + 15 - .01, 200));
        assertNull(barrage(272 + 15, 200));
        int end = SilentGateSequence.endTick(400);
        assertNotNull(barrage(end + 15 - .01, 400));
        assertNull(barrage(end + 15, 400));
        assertNotEquals(barrage(60, 200).position(), barrage(60, 400).position());
    }
    private static AtomicCinematic.Pose shotAt(double c, List<SilentGateRenderer.Shot> shots, Vec3 focus) {
        return SilentGateCinematic.cameraFor(frame(2, 300 + c, c, 200), PLAYER, 0f, 0f, true, shots, (x, z) -> Double.NEGATIVE_INFINITY, focus);
    }

    @Test void killShotKeepsTheSameTargetForItsWholeWindow() {
        var lock = new SilentGateCinematic.KillLock();
        var zombie = new SilentGateCinematic.Candidate(1, 20, new Vec3(0, 70, 30));
        var warden = new SilentGateCinematic.Candidate(2, 500, new Vec3(10, 70, 40));
        assertNull(lock.update(100, 59, 60, List.of(zombie, warden), PLAYER), "nothing is chosen before the kill shot");
        assertEquals(warden.position(), lock.update(100, 60, 60, List.of(zombie, warden), PLAYER), "highest max health wins");
        // A stronger mob walks in, and the target keeps moving: still the same entity, followed by id.
        var moved = new SilentGateCinematic.Candidate(2, 500, new Vec3(12, 70, 41));
        var giant = new SilentGateCinematic.Candidate(3, 9999, new Vec3(1, 70, 31));
        for (double c = 61; c <= 125; c += 4) {
            assertEquals(moved.position(), lock.update(100, c, 60, List.of(zombie, giant, moved), PLAYER));
            assertEquals(2, lock.id());
        }
        // It dies: the camera holds on the last position.
        assertEquals(moved.position(), lock.update(100, 100, 60, List.of(zombie), PLAYER));
        // No target at all: nothing locked, the densest impacts are framed.
        var empty = new SilentGateCinematic.KillLock();
        assertNull(empty.update(200, 70, 60, List.of(), PLAYER));
        var impacts = List.of(new SilentGateRenderer.Shot(1, new Vec3(0, 70, 90), 1), new SilentGateRenderer.Shot(2, new Vec3(3, 70, 91), 2),
                new SilentGateRenderer.Shot(3, new Vec3(-4, 70, 88), 3), new SilentGateRenderer.Shot(4, new Vec3(80, 70, 10), 4));
        assertTrue(SilentGateCinematic.densest(impacts, Vec3.ZERO).z > 85, "cluster beats the stray hit");
    }

    @Test void killLockPrefersTheServersFeaturedTargetAndFallsBackWhenItIsGone() {
        var zombie = new SilentGateCinematic.Candidate(1, 20, new Vec3(0, 70, 30));
        var warden = new SilentGateCinematic.Candidate(2, 500, new Vec3(10, 70, 40));
        var ravager = new SilentGateCinematic.Candidate(3, 100, new Vec3(-5, 70, 50));
        var lock = new SilentGateCinematic.KillLock();
        assertNull(lock.update(100, 59, 60, List.of(zombie, warden, ravager), PLAYER, 3), "not before the kill shot");
        assertEquals(ravager.position(), lock.update(100, 60, 60, List.of(zombie, warden, ravager), PLAYER, 3), "featured beats higher max health");
        assertEquals(3, lock.id());
        var gone = new SilentGateCinematic.KillLock();
        assertEquals(warden.position(), gone.update(100, 60, 60, List.of(zombie, warden), PLAYER, 3), "vanished featured: highest max health");
        assertEquals(warden.position(), new SilentGateCinematic.KillLock().update(100, 60, 60, List.of(zombie, warden), PLAYER, -1));
    }

    @Test void hitStopSlowsTheRenderClockForAboutThreeTicks() {
        assertEquals(50, SilentGateCinematic.warp(50.0, 100), 1e-9);
        assertEquals(100, SilentGateCinematic.warp(100.0, 100), 1e-9);
        assertEquals(100.15 * 1 + 0.15 * 0, SilentGateCinematic.warp(101.0, 100), .16);
        double after = SilentGateCinematic.warp(110.0, 100);
        assertEquals(110 - 3 * .85, after, 1e-9);
        assertEquals(after + 1, SilentGateCinematic.warp(111.0, 100), 1e-9, "full speed again after the stop");
        assertEquals(77, SilentGateCinematic.warp(77.0, -1), 1e-9, "no hit: no stop");
    }

    @Test void closeUpsStayAboveTheLiveGround() {
        var shots = List.of(new SilentGateRenderer.Shot(3, new Vec3(5, 70, 40), 100));
        int s60 = SilentGateCinematic.boundaries(200)[1], s125 = SilentGateCinematic.boundaries(200)[2];
        for (double c = s60 + 1; c < s125; c += 5) {
            var f = frame(2, 300 + c, c, 200);
            // A crater: the surface under the camera is 20 blocks above the planned shot.
            var pose = SilentGateCinematic.cameraFor(f, PLAYER, 0f, 0f, true, shots, (x, z) -> PLAYER.y + 20);
            assertTrue(pose.position().y >= PLAYER.y + 20.5 - 1e-6, "above ground at " + c);
        }
    }

    @Test void returnSlidesIntoTheFirstPersonView() {
        var end = SilentGateSequence.endTick(200);
        var last = SilentGateCinematic.cameraFor(frame(3, 0, end + 14.99, 200), PLAYER, 20f, 10f, true, List.of());
        assertTrue(last.position().distanceTo(EYE) < .05);
        assertEquals(20, last.yaw(), .2);
        assertEquals(10, last.pitch(), .2);
    }

    @Test void skipReturnsToShoulder() {
        var shoulder = FugaCinematic.shoulder(PLAYER, 0f, 0f);
        for (int phase = 0; phase <= 3; phase++)
            for (double age : new double[]{0, 10, 100, 250, 400}) {
                var pose = SilentGateCinematic.cameraFor(frame(phase, age, phase >= 2 ? age : -1, 200), PLAYER, 0f, false);
                assertTrue(pose == null || pose.equals(shoulder), "phase " + phase + " age " + age);
            }
    }

    @Test void staleAndOutOfOrderPacketsAreIgnored() {
        SilentGateRenderer.accept(payload(100, SilentGatePayload.CANCEL, new int[0], new double[0]), 100);
        assertNull(SilentGateRenderer.frameAt(1, 100));
        SilentGateRenderer.accept(payload(100, 0, new int[0], new double[0]), 100);
        assertNull(SilentGateRenderer.frameAt(1, 100), "a cancelled cast stays cancelled");
        SilentGateRenderer.accept(payload(200, 0, new int[0], new double[0]), 200);
        SilentGateRenderer.accept(payload(150, 0, new int[0], new double[0]), 210);
        assertEquals(200, SilentGateRenderer.frameAt(1, 210).startedAt());
        SilentGateRenderer.accept(payload(150, SilentGatePayload.CANCEL, new int[0], new double[0]), 211);
        assertNotNull(SilentGateRenderer.frameAt(1, 211), "an older cancel does nothing");
        SilentGateRenderer.accept(payload(200, SilentGatePayload.CANCEL, new int[0], new double[0]), 212);
        assertNull(SilentGateRenderer.frameAt(1, 212));
    }

    @Test void framesTrackCommandAgeShotsAndRelease() {
        SilentGateRenderer.accept(payload(100, 0, new int[0], new double[0]), 100);
        SilentGateRenderer.accept(payload(100, 1, new int[0], new double[0]), 400);
        assertEquals(-1, SilentGateRenderer.frameAt(1, 450).commandAge());
        var command = new SilentGatePayload(1, 100, 500, 2, 4, 70, 8, 0f, 7L, 48, 200, new int[]{3}, new double[]{1, 2, 3});
        SilentGateRenderer.accept(command, 500);
        var frame = SilentGateRenderer.frameAt(1, 530);
        assertEquals(430, frame.age(), 1e-9);
        assertEquals(30, frame.commandAge(), 1e-9);
        assertEquals(new Vec3(1, 2, 3), SilentGateRenderer.shots(1).get(0).hit());
        SilentGateRenderer.accept(new SilentGatePayload(1, 100, 500, 2, 4, 70, 8, 0f, 7L, 48, 200, new int[]{4}, new double[]{4, 5, 6}), 505);
        assertEquals(30, SilentGateRenderer.frameAt(1, 530).commandAge(), 1e-9, "later batches do not move the command time");
        assertEquals(2, SilentGateRenderer.shots(1).size());
        assertNotNull(SilentGateRenderer.frameAt(1, 500 + SilentGateSequence.endTick(200) + 14));
        assertNull(SilentGateRenderer.frameAt(1, 500 + SilentGateSequence.endTick(200) + 15));
    }

    @Test void castNeverCommandedExpiresClientSide() {
        SilentGateRenderer.accept(payload(100, 1, new int[0], new double[0]), 400);
        long limit = 100 + SilentGateSequence.BUILD_TICKS + 1200 + 20;
        assertNotNull(SilentGateRenderer.frameAt(1, limit));
        assertNull(SilentGateRenderer.frameAt(1, limit + 1));
    }
}
