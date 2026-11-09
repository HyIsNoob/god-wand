package net.godwand.client;

import net.godwand.network.SilentGatePayload;
import net.godwand.skill.SilentGateSequence;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
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
        return SilentGateCinematic.cameraFor(frame(2, 480 + commandAge, commandAge, barrage), PLAYER, 0f, true);
    }
    private static SilentGatePayload payload(long startedAt, int phase, int[] shots, double[] hits) {
        return new SilentGatePayload(1, startedAt, startedAt, phase, 4, 70, 8, 0f, 7L, 48, 200, shots, hits);
    }

    @BeforeEach void clean() { SilentGateRenderer.reset(); }

    private static final Vec3 DOOR = SilentGateSequence.doorCenter(PLAYER, 0f);
    private static final Vec3 FOOT = SilentGateSequence.gateFoot(PLAYER, 0f, 0);

    private static Vec3 pos(double age) { return build(age < SilentGateSequence.BUILD_TICKS ? 0 : 1, age).position(); }
    private static Vec3 speed(double age) { return pos(age + .5).subtract(pos(age - .5)); }
    private static SilentGateCinematic.Cut cut(String name) { return SilentGateCinematic.CUTS.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow(); }

    @Test void sequenceTablesHaveFewHardCuts() {
        var cuts = SilentGateCinematic.CUTS;
        assertEquals(4, cuts.size(), "four continuous sequences, three cuts in the build");
        assertEquals(0, cuts.get(0).t0());
        for (int i = 1; i < cuts.size(); i++) assertEquals(cuts.get(i - 1).t1(), cuts.get(i).t0(), cuts.get(i).name());
        assertEquals(SilentGateSequence.BUILD_TICKS, cuts.get(cuts.size() - 1).t1());
        assertArrayEquals(new int[]{0, 120, 290, 380}, cuts.stream().mapToInt(SilentGateCinematic.Cut::t0).toArray());
        for (var c : cuts) assertTrue(c.t1() - c.t0() >= 90, c.name() + " is a long continuous move");
        assertEquals(3, SilentGateCinematic.BARRAGE.size(), "three sequences, two cuts in the barrage");
        assertEquals(0, SilentGateCinematic.BARRAGE.get(0).t0());
        for (int i = 1; i < 3; i++) assertEquals(SilentGateCinematic.BARRAGE.get(i - 1).t1(), SilentGateCinematic.BARRAGE.get(i).t0());
        assertEquals(212, SilentGateCinematic.BARRAGE.get(2).t1());
        assertEquals(1, SilentGateCinematic.ENDING.size(), "the ending is one continuous move");
        assertEquals(SilentGateSequence.ENDING_TICKS, SilentGateCinematic.ENDING.get(0).t1());
        // The gate events are inside long sequences: landing, shockwave, doors and circle bloom all happen inside a move, not at a cut.
        assertTrue(SilentGateSequence.DESCENT_START > 120 && SilentGateSequence.SHOCK < 290 && SilentGateSequence.OPEN_END > 290);
    }

    @Test void cameraFollowsTheSequences() {
        assertTrue(build(0, 1).position().distanceTo(EYE) <= .6, "starts first person");
        var face = build(0, 119.9);
        assertEquals(1.15, Math.hypot(face.position().x - PLAYER.x, face.position().z - PLAYER.z), .3, "SEQ1 ends close on the face");
        var up = build(0, 130);
        assertTrue(up.position().y < PLAYER.y + 3 && up.position().distanceTo(new Vec3(PLAYER.x, up.position().y, PLAYER.z)) < 15, "SEQ2 starts low, near the caster");
        assertTrue(up.pitch() < -50, "looking up at the great circle: " + up.pitch());
        // It arrives at rest beside the spike's path and holds there through the touchdown.
        assertEquals(0, pos(246).distanceTo(pos(247.9)), 1e-6);
        assertEquals(FOOT.y + 25, pos(246).y, 1e-6, "beside the spike's path");
        assertTrue(build(0, 230).pitch() < -25, "looking up at the falling gate: " + build(0, 230).pitch());
        var wide = build(0, 289.9);
        assertTrue(wide.position().distanceTo(FOOT.add(0, 96, 0)) > 180, "extreme wide at the end of SEQ2: " + wide.position().distanceTo(FOOT.add(0, 96, 0)));
        assertTrue(PLAYER.z - wide.position().z > 40, "behind the caster");
        // SEQ3: 30..55 blocks from the door, turning about 46 degrees, ending dead frontal.
        var door0 = build(0, 290.01);
        assertTrue(door0.position().distanceTo(DOOR) > 30 && door0.position().distanceTo(DOOR) < 55, "door close: " + door0.position().distanceTo(DOOR));
        var door1 = build(0, 379.9);
        assertEquals(0, door1.yaw(), .5, "ends frontal and symmetric");
        assertEquals(45, Math.abs(door0.yaw() - door1.yaw()), 3, "SEQ3 turn");
        // SEQ4: overview from the ground under the gate, one low flight toward the caster, onto the waiting frame.
        assertEquals(0, pos(380).distanceTo(SilentGateSequence.flight(PLAYER, 0f, 0)), 1);
        assertEquals(0, pos(479.99).distanceTo(build(1, 480).position()), .1, "lands on the waiting frame without a cut");
        for (double t = 381; t < 479; t += 3) {
            var p = build(0, t);
            assertTrue(p.position().y < PLAYER.y + 12, "stays low at " + t + ": " + p.position().y);
            if (t < 470) assertTrue(p.pitch() < -25, "looks up at the circles, never level with them, at " + t + ": " + p.pitch());
        }
        double dz = 0; for (double t = 381; t < 479; t++) { assertTrue(pos(t + 1).z <= pos(t).z + .15, "one direction"); dz += pos(t).z - pos(t + 1).z; }
        assertTrue(dz > 120, "travels about 134 blocks: " + dz);
        var held = build(1, 480);
        assertTrue(held.position().z - PLAYER.z > 4 && held.position().distanceTo(PLAYER) < 9, "medium frame in front of the caster: " + held.position());
        for (double age : new double[]{480, 520, 800})
            assertTrue(build(1, age).position().distanceTo(held.position()) <= .6, "WAIT holds the frame at " + age);
    }

    @Test void speedsAreContinuousInsideEverySequence() {
        for (var c : SilentGateCinematic.CUTS) {
            double worst = 0;
            for (double t = c.t0() + 1.5; t + 1 <= c.t1() - 1.5; t += 1) {
                // The touchdown shake (248..262) and the door-stop jolt (392..404) are deliberate impacts.
                if (t > 247 && t < 262 || t > 391 && t < 404) continue;
                worst = Math.max(worst, speed(t + 1).subtract(speed(t)).length());
            }
            assertTrue(worst < 1.0, c.name() + " speed jump " + worst + " blocks/tick");
        }
    }

    @Test void sequencesMoveSlowly() {
        for (var c : SilentGateCinematic.CUTS) {
            double max = 0, turn = 0;
            for (double t = c.t0() + 1; t < c.t1() - 1; t += 1) {
                if (t > 247 && t < 262) continue;
                max = Math.max(max, speed(t).length());
                turn = Math.max(turn, Math.abs(Mth.wrapDegrees(build(0, t + 1).yaw() - build(0, t).yaw())));
            }
            assertTrue(max < (c.name().startsWith("SEQ2") ? 7 : 4), c.name() + " speed " + max + " blocks/tick");
            if (c.name().startsWith("SEQ3")) assertTrue(turn < 1.2, c.name() + " turns " + turn + " deg/tick");
        }
        double s3 = 0;
        for (double t = 291; t < 374; t++) s3 = Math.max(s3, orbit(pos(t), pos(t + 5), DOOR) / 5);
        assertTrue(s3 <= .6 && s3 > .4, "door orbit " + s3 + " deg/tick");
    }
    private static double orbit(Vec3 a, Vec3 b, Vec3 center) {
        return Math.abs(Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(b.x - center.x, b.z - center.z) - Math.atan2(a.x - center.x, a.z - center.z))));
    }

    @Test void curvesAreNotTheDefaultEaseInOut() {
        var out = SilentGateCinematic.Curve.EASE_OUT_LONG;
        assertEquals(0, out.u(0), 1e-9); assertEquals(1, out.u(1), 1e-9);
        assertTrue(out.u(.1) > .25, "starts with momentum");
        assertTrue(out.u(1) - out.u(.9) < .005, "stops very slowly");
        var in = SilentGateCinematic.Curve.EASE_IN;
        assertTrue(in.u(.1) < .02 && in.u(1) - in.u(.9) > .15, "accelerates to the end");
        // The bloom flight: rest, quick gain, a long tail to rest (asymmetric).
        assertEquals(0, SilentGateSequence.flightU(SilentGateSequence.FLIGHT_START), 1e-12);
        assertEquals(1, SilentGateSequence.flightU(SilentGateSequence.FLIGHT_END), 1e-12);
        assertTrue(SilentGateSequence.flightU(430) > .4 && SilentGateSequence.flightU(470) > .97, "front loaded");
        assertEquals(1.2, SilentGateSequence.flightTime(SilentGateSequence.flightU(441.2)) - 440, .05, "inverse");
        // Hermite key moves: velocity continuous through a key, and a rest key stops dead.
        var keys = List.of(new SilentGateCinematic.Key(0, new Vec3(0, 0, 0), Vec3.ZERO, true), new SilentGateCinematic.Key(10, new Vec3(10, 0, 0), Vec3.ZERO, false),
                new SilentGateCinematic.Key(20, new Vec3(20, 0, 0), Vec3.ZERO, true));
        assertEquals(10, SilentGateCinematic.splinePos(keys, 10).x, 1e-9);
        double vBefore = SilentGateCinematic.splinePos(keys, 9.99).x - SilentGateCinematic.splinePos(keys, 9.98).x, vAfter = SilentGateCinematic.splinePos(keys, 10.02).x - SilentGateCinematic.splinePos(keys, 10.01).x;
        assertEquals(vBefore, vAfter, 1e-3);
        assertEquals(0, SilentGateCinematic.splinePos(keys, 20).x - SilentGateCinematic.splinePos(keys, 19.99).x, 5e-4);
    }

    @Test void stuckDoorsTrembleThenJolt() {
        assertEquals(0, SilentGateCinematic.rattle(SilentGateSequence.OPEN_START - 1).length(), 1e-9);
        double stuck = 0, jolt = 0;
        for (double t = 296; t < 312; t += .5) stuck = Math.max(stuck, SilentGateCinematic.rattle(t).length());
        for (double t = SilentGateSequence.OPEN_END + 1; t < SilentGateSequence.OPEN_END + 5; t += .25) jolt = Math.max(jolt, SilentGateCinematic.rattle(t).length());
        assertTrue(stuck > .05 && stuck < .5, "tremble " + stuck);
        assertTrue(jolt > .3, "stop jolt " + jolt);
        assertEquals(0, SilentGateCinematic.rattle(SilentGateSequence.OPEN_END + 25).length(), .01, "calm again");
        assertEquals(0, SilentGateCinematic.landing(247), 0);
        assertTrue(Math.abs(SilentGateCinematic.landing(250)) > .1, "the touchdown shakes the camera");
    }

    @Test void barrageSequencesLookUpAtCirclesAndFlowWithoutCuts() {
        assertArrayEquals(new int[]{84, 134, 170, 212}, SilentGateCinematic.boundaries(200));
        assertArrayEquals(new int[]{12 + 144, 12 + 244, 12 + 316, 412}, SilentGateCinematic.boundaries(400), "scaled with the barrage length");
        var shots = List.of(new SilentGateRenderer.Shot(3, new Vec3(5, 70, 40), 100));
        // BAR1 starts exactly on the waiting frame (no cut) and moves slowly, always looking up at the sky.
        assertEquals(0, barrage(0, 200).position().distanceTo(build(1, 480).position()), 1e-6);
        for (double c = 1; c < 80; c += 3) {
            var p = barrage(c, 200);
            assertTrue(p.position().distanceTo(barrage(c + 3, 200).position()) < 12, "no jump at " + c);
            if (c > 25) assertTrue(p.pitch() < -10, "looks up at " + c + ": " + p.pitch());
        }
        assertTrue(barrage(83.9, 200).position().y > PLAYER.y + 40, "has risen into the formation");
        // BAR2 84..134 kill close-up, 5.5 blocks from the target, below its eye, looking up, never jumping; then one pull-away.
        Vec3 target = new Vec3(6, 70, 50);
        for (double c = 85; c < 132; c += 2) {
            var pose = shotAt(c, shots, target);
            assertEquals(5.5, Math.hypot(pose.position().x - target.x, pose.position().z - target.z), .01, "5.5 blocks at " + c);
            assertEquals(target.y + .6, pose.position().y, 1e-9, "just above its feet, under its 1.6 eye at " + c);
            assertTrue(pose.pitch() < -15 && pose.pitch() > -30, "looking a bit up so the beams fall into the frame: " + pose.pitch());
            assertTrue(pose.position().distanceTo(shotAt(c + 2, shots, target).position()) < 1.2, "slow orbit at " + c);
        }
        double a0 = Math.atan2(shotAt(84.5, shots, target).position().x - target.x, shotAt(84.5, shots, target).position().z - target.z);
        double a1 = Math.atan2(shotAt(133.5, shots, target).position().x - target.x, shotAt(133.5, shots, target).position().z - target.z);
        assertEquals(20, Math.abs(Math.IEEEremainder(Math.toDegrees(a1 - a0), 360)), 1.5, "orbit sweep");
        Vec3 v1 = shotAt(133, shots, target).position().subtract(shotAt(132, shots, target).position()), v2 = shotAt(136, shots, target).position().subtract(shotAt(135, shots, target).position());
        assertEquals(0, v1.subtract(v2).length(), .3, "the pull-away takes over the orbit without a jump");
        var far = shotAt(169.9, shots, target);
        assertTrue(Math.hypot(far.position().x - target.x, far.position().z - target.z) > 25 && far.position().y > target.y + 8, "pulled away and up");
        // BAR3 170..212 low behind the caster, rising slowly, looking up at the sky circles and beams.
        for (double c = 171; c < 211; c += 4) {
            assertTrue(barrage(c, 200).position().y < PLAYER.y + 32, "low at " + c);
            assertTrue(barrage(c, 200).pitch() < -20, "looks up at " + c + ": " + barrage(c, 200).pitch());
        }
        // The ending is one move: medium on the doors from below, back, low and looking steeply up as the gate climbs, then still.
        int endB = SilentGateCinematic.boundaries(200)[3];
        Vec3 door = SilentGateSequence.doorCenter(PLAYER, 0f);
        assertEquals(door.add(0, -34, 0).distanceTo(barrage(endB + .01, 200).position()), 66, 14, "medium on the doors");
        assertTrue(barrage(endB + 118, 200).position().y < PLAYER.y + 12 && barrage(endB + 118, 200).pitch() < -40, "low, looking steeply up: " + barrage(endB + 118, 200).pitch());
        assertTrue(barrage(endB + 159, 200).pitch() < -5, "looks up at the great circle");
        double worst = 0;
        for (double e = 1; e < 158; e++) worst = Math.max(worst, barrage(endB + e + 1, 200).position().subtract(barrage(endB + e, 200).position()).subtract(
                barrage(endB + e, 200).position().subtract(barrage(endB + e - 1, 200).position())).length());
        assertTrue(worst < .6, "the ending's speed never jumps: " + worst);
        int end = SilentGateSequence.endTick(200);
        assertEquals(372, end);
        assertNotNull(barrage(end + 15 - .01, 200));
        assertNull(barrage(end + 15, 200));
        int end4 = SilentGateSequence.endTick(400);
        assertNotNull(barrage(end4 + 15 - .01, 400));
        assertNull(barrage(end4 + 15, 400));
        assertNotEquals(barrage(60, 200).position(), barrage(60, 400).position());
    }
    private static AtomicCinematic.Pose shotAt(double c, List<SilentGateRenderer.Shot> shots, Vec3 focus) {
        return SilentGateCinematic.cameraFor(frame(2, 480 + c, c, 200), PLAYER, 0f, 0f, true, shots, (x, z) -> Double.NEGATIVE_INFINITY, focus);
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
        int s60 = SilentGateCinematic.boundaries(200)[0], s125 = SilentGateCinematic.boundaries(200)[1];
        for (double c = s60 + 1; c < s125; c += 5) {
            var f = frame(2, 480 + c, c, 200);
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
