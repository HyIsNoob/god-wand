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

    @Test void shotTableCoversTheBuildWithoutGaps() {
        var cuts = SilentGateCinematic.CUTS;
        assertEquals(10, cuts.size());
        assertEquals(0, cuts.get(0).t0());
        for (int i = 1; i < cuts.size(); i++) assertEquals(cuts.get(i - 1).t1(), cuts.get(i).t0(), cuts.get(i).name());
        assertEquals(SilentGateSequence.BUILD_TICKS, cuts.get(cuts.size() - 1).t1());
        assertArrayEquals(new int[]{0, 40, 80, 120, 170, 210, 250, 290, 380, 430},
                cuts.stream().mapToInt(SilentGateCinematic.Cut::t0).toArray(), "S1..S10 start ticks");
        for (var c : cuts) assertTrue(c.t1() - c.t0() >= (c.name().equals("S9") ? 50 : 40), c.name() + " is at least 2 s (S9 2.5 s)");
        assertEquals(SilentGateCinematic.Curve.STATIC, cut("S6").curve());
        assertEquals(SilentGateCinematic.Curve.EASE_IN, cut("S9").curve());
        assertEquals(SilentGateCinematic.Curve.EASE_OUT_LONG, cut("S10").curve());
        // Events finish before the shot that shows them is over, so each result can be read (holds).
        assertTrue(SilentGateSequence.DESCENT_END < cut("S6").t1() && SilentGateSequence.SHOCK < cut("S7").t1() - 10);
        assertTrue(cut("S4").t1() - 168 >= 2, "great circle is complete before the cut");
    }

    @Test void endingAndBarrageTablesHaveReadableShots() {
        assertEquals(4, SilentGateCinematic.ENDING.size());
        assertEquals(0, SilentGateCinematic.ENDING.get(0).t0());
        assertEquals(SilentGateSequence.ENDING_TICKS, SilentGateCinematic.ENDING.get(3).t1());
        for (int i = 1; i < 4; i++) assertEquals(SilentGateCinematic.ENDING.get(i - 1).t1(), SilentGateCinematic.ENDING.get(i).t0());
        assertEquals(5, SilentGateCinematic.BARRAGE.size());
        for (var c : SilentGateCinematic.BARRAGE) assertTrue(c.t1() - c.t0() >= 30, c.name() + " lasts at least 1.5 s");
        assertEquals(212, SilentGateCinematic.BARRAGE.get(4).t1());
    }

    @Test void cameraFollowsTheShotList() {
        assertTrue(build(0, 10).position().distanceTo(EYE) <= .6, "S1 first person");
        var up = build(0, 150);
        assertTrue(up.position().y < PLAYER.y + 3 && up.position().distanceTo(new Vec3(PLAYER.x, up.position().y, PLAYER.z)) < 15, "S4 low, near the caster");
        assertTrue(up.pitch() < -50, "S4 looking up at the great circle: " + up.pitch());
        var face = build(0, 119.9);
        assertEquals(1.1, Math.hypot(face.position().x - PLAYER.x, face.position().z - PLAYER.z), .3, "S3 ends close on the face");
        // S6: the camera does not move at all while the gate falls past it.
        assertEquals(0, pos(210).distanceTo(pos(249.9)), 1e-9);
        assertEquals(FOOT.y + 25, build(0, 210).position().y, 1e-6, "S6 camera sits beside the spike's path");
        assertTrue(build(0, 220).pitch() < -25, "S6 looks up at the descending gate: " + build(0, 220).pitch());
        // S7: extreme wide.
        var wide = build(0, 270);
        assertTrue(wide.position().distanceTo(FOOT.add(0, 96, 0)) > 240, "S7 distance " + wide.position().distanceTo(FOOT.add(0, 96, 0)));
        assertTrue(wide.position().distanceTo(PLAYER) > 100 && wide.position().distanceTo(PLAYER) < 160, "caster stays in the frame");
        // S8: 30..50 blocks from the door, turning about 46 degrees and ending dead frontal.
        var door0 = build(0, 290.01);
        assertTrue(door0.position().distanceTo(DOOR) > 30 && door0.position().distanceTo(DOOR) < 55, "S8 close to the door: " + door0.position().distanceTo(DOOR));
        var door1 = build(0, 379.9);
        assertEquals(0, door1.yaw(), .5, "ends frontal and symmetric");
        assertEquals(45, Math.abs(door0.yaw() - door1.yaw()), 3, "S8 turn");
        // S9 leaves the doorway; S10 is far behind the caster.
        assertTrue(pos(380).distanceTo(DOOR) < 6, "S9 starts in the doorway: " + pos(380).distanceTo(DOOR));
        assertTrue(pos(429.9).distanceTo(SilentGateSequence.flight(PLAYER, 0f, 1)) < 1.5);
        assertTrue(PLAYER.z - pos(479.9).z > 100 && pos(479.9).y > FOOT.y + 40, "S10 is high and far behind: " + pos(479.9));
        // WAIT: medium, in front of the caster looking back at them, breathing very slowly.
        var held = build(1, 480);
        assertTrue(held.position().z - PLAYER.z > 4 && held.position().distanceTo(PLAYER) < 9, "medium frame in front of the caster: " + held.position());
        for (double age : new double[]{480, 520, 800})
            assertTrue(build(1, age).position().distanceTo(held.position()) <= .6, "WAIT holds the frame at " + age);
    }

    @Test void speedsAreContinuousInsideEveryShot() {
        for (var c : SilentGateCinematic.CUTS) {
            // The spring's first ticks and the door-stop jolt that rings into S9 are deliberate impacts.
            double from = c.t0() + 1.5, to = c.t1() - 1.5;
            if (c.name().equals("S7")) from += 4;
            if (c.name().equals("S9")) from += 24;
            double worst = 0;
            for (double t = from; t + 1 <= to; t += 1) worst = Math.max(worst, speed(t + 1).subtract(speed(t)).length());
            assertTrue(worst < 1.0, c.name() + " speed jump " + worst + " blocks/tick");
        }
    }

    @Test void shotsMoveSlowlyUnlessTheyAreTheRush() {
        for (var c : SilentGateCinematic.CUTS) {
            double max = 0, turn = 0;
            for (double t = c.t0() + 1; t < c.t1() - 1; t += 1) {
                max = Math.max(max, speed(t).length());
                turn = Math.max(turn, Math.abs(Mth.wrapDegrees(build(0, t + 1).yaw() - build(0, t).yaw())));
            }
            if (c.name().equals("S9") || c.name().equals("S10")) assertTrue(max > 4, c.name() + " is the rush: " + max);
            else assertTrue(max < 3.5, c.name() + " speed " + max);
            if (c.name().equals("S8") || c.name().equals("S4"))
                assertTrue(turn < 1.2, c.name() + " turns " + turn + " deg/tick");
        }
        // The orbits of S4 (about the caster) and S8 (about the door) turn at no more than 0.6 degrees per tick.
        double s4 = 0, s8 = 0;
        for (double t = 121; t < 164; t++) s4 = Math.max(s4, orbit(pos(t), pos(t + 5), PLAYER) / 5);
        for (double t = 291; t < 374; t++) s8 = Math.max(s8, orbit(pos(t), pos(t + 5), DOOR) / 5);
        assertTrue(s4 <= .6 && s4 > .4, "S4 " + s4);
        assertTrue(s8 <= .6 && s8 > .4, "S8 " + s8);
    }
    private static double orbit(Vec3 a, Vec3 b, Vec3 center) {
        return Math.abs(Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(b.x - center.x, b.z - center.z) - Math.atan2(a.x - center.x, a.z - center.z))));
    }

    @Test void s9HandsItsSpeedToS10() {
        Vec3 end9 = speed(429.5), start10 = speed(430.5);
        assertEquals(end9.length(), start10.length(), end9.length() * .12, "matched speed " + end9.length() + " / " + start10.length());
        assertTrue(end9.dot(start10) > 0, "same direction of travel");
    }

    @Test void curvesAreNotTheDefaultEaseInOut() {
        var out = SilentGateCinematic.Curve.EASE_OUT_LONG;
        assertEquals(0, out.u(0), 1e-9); assertEquals(1, out.u(1), 1e-9);
        assertTrue(out.u(.1) > .25, "starts with momentum");
        assertTrue(out.u(1) - out.u(.9) < .005, "stops very slowly");
        var in = SilentGateCinematic.Curve.EASE_IN;
        assertTrue(in.u(.1) < .02 && in.u(1) - in.u(.9) > .15, "accelerates to the end");
        var spring = SilentGateCinematic.Curve.SPRING;
        double peak = 0; for (double k = 0; k <= 1; k += .01) peak = Math.max(peak, spring.u(k));
        assertTrue(peak > 1.1 && Math.abs(spring.u(1) - 1) < .01, "overshoots and rings out: " + peak);
        assertEquals(0, SilentGateCinematic.Curve.STATIC.u(.7), 0);
    }

    @Test void stuckDoorsTrembleThenJolt() {
        assertEquals(0, SilentGateCinematic.rattle(SilentGateSequence.OPEN_START - 1).length(), 1e-9);
        double stuck = 0, jolt = 0;
        for (double t = 296; t < 312; t += .5) stuck = Math.max(stuck, SilentGateCinematic.rattle(t).length());
        for (double t = SilentGateSequence.OPEN_END + 1; t < SilentGateSequence.OPEN_END + 5; t += .25) jolt = Math.max(jolt, SilentGateCinematic.rattle(t).length());
        assertTrue(stuck > .05 && stuck < .5, "tremble " + stuck);
        assertTrue(jolt > .3, "stop jolt " + jolt);
        assertEquals(0, SilentGateCinematic.rattle(SilentGateSequence.OPEN_END + 25).length(), .01, "calm again");
    }

    @Test void barrageShotListHasFewLongReadableShots() {
        assertArrayEquals(new int[]{48, 84, 134, 170, 212}, SilentGateCinematic.boundaries(200));
        assertArrayEquals(new int[]{12 + 72, 12 + 144, 12 + 244, 12 + 316, 412}, SilentGateCinematic.boundaries(400), "scaled with the barrage length");
        var shots = List.of(new SilentGateRenderer.Shot(3, new Vec3(5, 70, 40), 100));
        // B1 12..48 low wide rain, a slow dolly.
        assertTrue(barrage(30, 200).position().y < PLAYER.y + 6, "low wide shot");
        for (double c = 13; c < 46; c += 3)
            assertTrue(barrage(c, 200).position().distanceTo(barrage(c + 3, 200).position()) < 4, "slow dolly at " + c);
        // B2 48..84 dive from a sky circle: it starts high above the ground and ends near it.
        assertTrue(barrage(49, 200).position().y > PLAYER.y + 40 && barrage(83.9, 200).position().y < PLAYER.y + 12, "dive " + barrage(49, 200).position() + " -> " + barrage(83.9, 200).position());
        // B3 84..134 kill close-up, 5.5 blocks from the target, below its eye, looking up, never jumping.
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
        // B4 134..170 ground tracking: low, moving steadily.
        for (double c = 135; c < 168; c += 3) {
            assertTrue(barrage(c, 200).position().y < PLAYER.y + 8, "low at " + c);
            double v = barrage(c, 200).position().distanceTo(barrage(c + 3, 200).position()) / 3;
            assertTrue(v > 1.5 && v < 3.5, "steady speed " + v);
        }
        // B5 170..212 high wide sinking slowly.
        var high = barrage(171, 200);
        assertEquals(PLAYER.y + 140, high.position().y, 2);
        assertTrue(barrage(211, 200).position().y < high.position().y - 35);
        assertTrue(high.pitch() > 5 && high.pitch() < 20, "slightly down, horizon in the upper third: " + high.pitch());
        double toCaster = Math.toDegrees(Math.atan2(high.position().y - PLAYER.y, high.position().distanceTo(new Vec3(PLAYER.x, high.position().y, PLAYER.z))));
        assertTrue(toCaster < high.pitch() + 35, "the caster is inside the frame (" + toCaster + " below the horizon)");
        // The ending (barrage + 160 ticks): doors medium, wide, low looking up at the climbing gate, wide.
        int endB = SilentGateCinematic.boundaries(200)[4];
        Vec3 door = SilentGateSequence.doorCenter(PLAYER, 0f);
        assertEquals(66, barrage(endB + 1, 200).position().distanceTo(door.add(0, -34, 0).add(new Vec3(0, 0, 0))), 8, "E1 medium on the doors");
        assertTrue(PLAYER.z - barrage(endB + 60, 200).position().z > 90, "E2 wide, behind the caster");
        assertTrue(barrage(endB + 110, 200).position().y < PLAYER.y + 10 && barrage(endB + 110, 200).pitch() < -40, "E3 low, looking steeply up: " + barrage(endB + 110, 200).pitch());
        assertTrue(barrage(endB + 150, 200).pitch() < -5, "E4 looks up at the great circle");
        for (double e = 1; e < 158; e += 2) {
            var p = barrage(endB + e, 200); var q = barrage(endB + e + 2, 200);
            if (Math.abs(e - 40) > 3 && Math.abs(e - 80) > 3 && Math.abs(e - 125) > 3) assertTrue(p.position().distanceTo(q.position()) < 8, "no jump inside an ending shot at " + e);
        }
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
        int s60 = SilentGateCinematic.boundaries(200)[1], s125 = SilentGateCinematic.boundaries(200)[2];
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
