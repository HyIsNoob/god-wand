package net.godwand.client;

import net.godwand.network.SilentGatePayload;
import net.godwand.skill.SilentGateSequence;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Script v3 (docs/superpowers/plans/2026-11-09-silent-gate-script-v3.md): the eighteen build shots, B1..B6 and E1..E4. */
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
    private static final double ALT = SilentGateSequence.circleAltitude(PLAYER.y);

    private static Vec3 pos(double age) { return build(age < SilentGateSequence.BUILD_TICKS ? 0 : 1, age).position(); }
    private static SilentGateCinematic.Cut shot(int n) { return SilentGateCinematic.CUTS.get(n - 1); }
    private static double mid(SilentGateCinematic.Cut c) { return (c.t0() + c.t1()) / 2.0; }

    /** True when {@code p} is inside a 16:9 frame of a 70 degree vertical field of view looking from {@code pose}. */
    private static boolean inFrame(AtomicCinematic.Pose pose, Vec3 p) {
        Vec3 f = Vec3.directionFromRotation(pose.pitch(), pose.yaw()), right = f.cross(new Vec3(0, 1, 0)).normalize(), up = right.cross(f);
        Vec3 d = p.subtract(pose.position());
        double z = d.dot(f), v = Math.tan(Math.toRadians(35));
        return z > 0 && Math.abs(d.dot(up) / z) <= v && Math.abs(d.dot(right) / z) <= v * 16 / 9;
    }

    @Test void shotTableIsScriptV3() {
        var cuts = SilentGateCinematic.CUTS;
        assertEquals(18, cuts.size());
        assertArrayEquals(new int[]{0, 15, 45, 55, 62, 92, 125, 160, 200, 232, 262, 292, 312, 337, 387, 420, 452, 470}, cuts.stream().mapToInt(SilentGateCinematic.Cut::t0).toArray());
        for (int i = 1; i < cuts.size(); i++) assertEquals(cuts.get(i - 1).t1(), cuts.get(i).t0(), cuts.get(i).name());
        assertEquals(480, cuts.get(17).t1(), "shot 18 holds from 470 on into the wait");
        var bar = SilentGateCinematic.BARRAGE;
        assertArrayEquals(new int[]{0, 14, 50, 82, 125, 162}, bar.stream().mapToInt(SilentGateCinematic.Cut::t0).toArray());
        assertEquals(212, bar.get(5).t1());
        for (int i = 1; i < 6; i++) assertEquals(bar.get(i - 1).t1(), bar.get(i).t0());
        var end = SilentGateCinematic.ENDING;
        assertArrayEquals(new int[]{0, 40, 80, 125}, end.stream().mapToInt(SilentGateCinematic.Cut::t0).toArray());
        assertEquals(SilentGateSequence.ENDING_TICKS, end.get(3).t1());
        for (var c : cuts) if (c.t1() <= 470) assertTrue(c.t1() - c.t0() >= 7, c.name());
        for (var c : cuts) if (c.t0() >= 62 && c.t1() <= 470) assertTrue(c.t1() - c.t0() >= 18, c.name() + " lasts as long as the table says");
        // The script's events sit inside their shots.
        assertTrue(shot(8).t0() == SilentGateSequence.CIRCLE_START && shot(9).t1() == SilentGateSequence.CIRCLE_FULL && shot(10).t0() == SilentGateSequence.SLIT_START);
        assertTrue(shot(11).t0() == SilentGateSequence.DESCENT_START && SilentGateSequence.DESCENT_END > shot(11).t1() && SilentGateSequence.DESCENT_END < shot(12).t1());
        assertTrue(SilentGateSequence.SHOCK > shot(13).t0() && SilentGateSequence.SHOCK < shot(13).t1());
        assertTrue(SilentGateSequence.OPEN_START == shot(14).t0() + 3 && SilentGateSequence.STUCK_END > shot(14).t0() && SilentGateSequence.OPEN_END > shot(15).t0() && SilentGateSequence.OPEN_END < shot(15).t1());
        assertEquals(SilentGateSequence.BLOOM_START, shot(16).t0());
    }

    @Test void firstPersonShotsSitAtTheEye() {
        for (int n = 1; n <= 4; n++) for (double t = shot(n).t0(); t < shot(n).t1(); t += 1) {
            var p = build(0, t);
            assertTrue(p.position().distanceTo(EYE) <= .35, "shot " + n + " at the eye, tick " + t + ": " + p.position().distanceTo(EYE));
            assertEquals(0, p.yaw(), 1, "looks where the caster looks");
            assertTrue(Math.abs(p.pitch()) < 5);
        }
        // The wand floats 1.5 blocks before the face at eye height (the same frame is the camera's centre).
        var wand = GatePose.wand(30, -1, 212, PLAYER, 0);
        assertEquals(1.5, wand.pos().z - PLAYER.z, .05);
        assertEquals(EYE.y, wand.pos().y, .25);
    }

    @Test void farShotShowsASmallCasterInTheField() {
        for (double t = 62; t < 92; t += 3) {
            var p = build(0, t);
            double behind = PLAYER.z - p.position().z, dist = p.position().distanceTo(PLAYER.add(0, 1, 0));
            assertTrue(behind > 20 && behind < 30 && dist > 22 && dist < 32, "about 25 blocks behind, off to the side, at " + t + ": " + behind + " / " + dist);
            assertTrue(p.position().y > PLAYER.y + 3 && p.position().y < PLAYER.y + 10, "slightly high");
            assertTrue(inFrame(p, PLAYER.add(0, 1, 0)), "the caster is in the frame");
        }
    }

    @Test void closeShotsStayCloseAndSmallShotsStaySmall() {
        for (double t = 92; t < 125; t += 4) {
            double d = build(0, t).position().distanceTo(PLAYER.add(0, 1.4, 0));
            assertTrue(d > 3.5 && d < 7, "medium three-quarter " + d);
            assertTrue(build(0, t).position().z > PLAYER.z + 2, "in front of the caster");
        }
        for (double t = 125; t < 160; t += 4) assertTrue(build(0, t).position().distanceTo(PLAYER.add(0, 1.62, 0)) < 3.1, "face close-up at " + t);
        assertTrue(build(0, 159.9).position().distanceTo(PLAYER.add(0, 1.62, 0)) < 1.9);
    }

    @Test void greatCircleShotsLookUpFromBelow() {
        for (double t = 160; t < 200; t += 2) {
            var p = build(0, t);
            assertTrue(p.position().y < PLAYER.y + .5 && p.position().distanceTo(new Vec3(PLAYER.x, p.position().y, PLAYER.z)) < 6, "shot 8 at the caster's feet, tick " + t);
            assertTrue(p.pitch() < -45, "looking steeply up: " + p.pitch());
        }
        assertEquals(.25 * 39, Math.abs(build(0, 199).yaw() - build(0, 160).yaw()), .5, "the yaw turns slowly");
        for (double t = 200; t < 232; t += 2) {
            var p = build(0, t);
            double rim = PLAYER.y + ALT + 3.2 * 3;
            assertEquals(20, rim - p.position().y, 1.5, "shot 9: 20 blocks under the outer tier");
            assertTrue(p.pitch() < -30);
            // The camera sits where the outer tier's band ends up (150 blocks from the circle's centre), drifting inward.
            Vec3 centre = SilentGateSequence.gateCenter(PLAYER, 0f);
            double r = Math.hypot(p.position().x - centre.x, p.position().z - centre.z);
            assertTrue(r > 146 && r < 152, "under the band: " + r);
        }
    }

    @Test void gateShotsFrameTheGate() {
        // Shot 10: level with the slit, about 30 blocks from it.
        Vec3 slitMid = SilentGateSequence.gateFoot(PLAYER, 0f, 1).add(0, 126, 0);
        for (double t = 232; t < 262; t += 3) {
            var p = build(0, t);
            assertEquals(30, Math.hypot(p.position().x - slitMid.x, p.position().z - slitMid.z), 1.5, "about 30 blocks from the slit");
            assertTrue(Math.abs(p.position().y - slitMid.y) < 25 && inFrame(p, slitMid));
        }
        // Shot 11: still camera beside the spike's path; the gate passes through the frame.
        assertEquals(0, pos(262).distanceTo(pos(291.9)), 1e-9, "still");
        Vec3 spike = SilentGateSequence.gateFoot(PLAYER, 0f, 1);
        var still = build(0, 270);
        assertTrue(inFrame(still, SilentGateSequence.gateFoot(PLAYER, 0f, SilentGateSequence.gateDescent(270)).add(0, 60, 0)), "the gate comes through the frame");
        assertTrue(Math.abs(still.position().x - spike.x) < 12 && still.position().distanceTo(FOOT.add(0, 20, 0)) < 60, "beside its path");
        // Shot 12: medium wide from the ground, looking up at the whole gate, before and after the touchdown at 300 (the view rings like a spring).
        for (double t : new double[]{294, 296, 308, 311}) {
            var p = build(0, t);
            assertTrue(p.position().y <= PLAYER.y + 4 && p.pitch() < -20, "from the ground looking up at " + t + ": " + p.pitch());
            assertTrue(inFrame(p, DOOR) && inFrame(p, FOOT.add(0, 10, 0)) && inFrame(p, FOOT.add(0, 190, 0)), "the whole gate is in frame at " + t);
        }
        assertEquals(0, pos(292).distanceTo(pos(311.9)), 1e-9);
        double ring = 0; for (double t = 300; t < 312; t += .5) ring = Math.max(ring, Math.abs(build(0, t).pitch() - build(0, 295).pitch()));
        assertTrue(ring > 1, "the touchdown shakes the view: " + ring);
        assertEquals(0, build(0, 299).pitch() - build(0, 295).pitch(), 1e-3, "calm until it lands");
        // Shot 13: extreme wide, 110 behind and 40 up, the caster tiny but in frame, the gate in frame, the shockwave starts at 318.
        for (double t = 312; t < 337; t += 3) {
            var p = build(0, t);
            assertEquals(110, PLAYER.z - p.position().z, 6, "110 blocks behind");
            assertEquals(40, p.position().y - PLAYER.y, 3, "40 blocks up");
            assertTrue(inFrame(p, PLAYER.add(0, 1, 0)) && inFrame(p, PLAYER.add(0, .2, 0)), "the caster is within the frame at " + t);
            assertTrue(inFrame(p, DOOR), "the gate is in frame");
        }
        // Shot 14: 25 blocks from the seam, at its middle, a slow arc; shot 15 close to the light, then backing off.
        for (double t = 337; t < 387; t += 3) {
            var p = build(0, t);
            assertEquals(25, p.position().distanceTo(DOOR.add(0, -2, 0)), .5, "25 blocks from the door's middle");
            assertTrue(inFrame(p, DOOR));
        }
        double arc = Math.abs(Mth.wrapDegrees(build(0, 386.9).yaw() - build(0, 337).yaw()));
        assertEquals(16, arc, 1.5, "a slow arc round the seam");
        double d15a = build(0, 387).position().distanceTo(DOOR), d15b = build(0, 419.9).position().distanceTo(DOOR);
        assertTrue(d15a < 18 && d15b > d15a + 2, "close to the light, backing off when the doors stop: " + d15a + " -> " + d15b);
    }

    @Test void canopyShotIsHighAndLooksDown() {
        for (double t = 420; t < 452; t += 2) {
            var p = build(0, t);
            assertTrue(p.position().y >= PLAYER.y + 200, "shot 16 camera height at least anchor+200: " + (p.position().y - PLAYER.y));
            assertTrue(p.position().y < PLAYER.y + ALT, "under the great circle");
            assertTrue(p.pitch() > 10 && p.pitch() < 45, "looking down: " + p.pitch());
            assertTrue(inFrame(p, DOOR) && inFrame(p, PLAYER.add(0, 110, 100)), "gate and the canopy in frame");
            // Every height tier of the canopy (55..215) is below the camera.
            assertTrue(p.position().y > PLAYER.y + 215);
        }
    }

    @Test void diveFliesLowAndFastUnderTheCanopyThenCutsToTheWaitingFrame() {
        double prev = 0;
        for (double t = 453; t < 470; t += 1) {
            var p = build(0, t);
            assertTrue(p.position().y < PLAYER.y + 8, "low under the canopy at " + t);
            assertTrue(p.pitch() < -30 && p.pitch() > -45, "looking up at the circles overhead and ahead: " + p.pitch());
            double v = pos(t + .5).distanceTo(pos(t - .5));
            assertTrue(v >= prev - 1e-6 && v <= 3.0, "accelerates continuously to at most 3 blocks per tick: " + v);
            prev = v;
        }
        assertEquals(0, pos(452).distanceTo(SilentGateSequence.flight(PLAYER, 0f, 0).add(0, 0, 0)), 1.5);
        assertTrue(prev > 2.5, "reaches nearly full speed: " + prev);
        var held = build(1, 480);
        assertTrue(held.position().z - PLAYER.z > 4 && held.position().distanceTo(PLAYER) < 9, "medium frame in front of the caster: " + held.position());
        assertEquals(0, pos(470).distanceTo(held.position()), .3, "shot 18 starts on the waiting frame");
        assertEquals(0, pos(479.99).distanceTo(held.position()), .3);
        for (double age : new double[]{480, 520, 800}) assertTrue(build(1, age).position().distanceTo(held.position()) <= .6, "WAIT holds the frame at " + age);
    }

    /** Camera speed per shot: at most 0.15 blocks per tick everywhere in the script except the dive (3), turning at most 0.5 degrees per tick (except the shaking shots). */
    @Test void everyShotMovesSlowly() {
        for (int n = 1; n <= 18; n++) {
            var c = shot(n);
            double max = 0, turn = 0;
            for (double t = c.t0(); t + 1 < Math.min(c.t1(), 480); t += 1) {
                max = Math.max(max, pos(t + 1).distanceTo(pos(t)));
                var a = build(0, t); var b = build(0, t + 1);
                turn = Math.max(turn, Math.max(Math.abs(Mth.wrapDegrees(b.yaw() - a.yaw())), Math.abs(b.pitch() - a.pitch())));
            }
            assertTrue(max <= (n == 17 ? 3.0 : .15), "shot " + n + " speed " + max + " blocks/tick");
            if (n != 12 && n != 14 && n != 15 && n != 17) assertTrue(turn <= .5, "shot " + n + " turns " + turn + " deg/tick");
            else assertTrue(turn <= 4, "shot " + n + " shake " + turn + " deg/tick");
        }
        assertEquals(480, SilentGateSequence.BUILD_TICKS);
        String[] names = {"B1", "B2", "B3", "B4", "B5", "B6"};
        for (int n = 0; n < 6; n++) {
            var c = SilentGateCinematic.BARRAGE.get(n);
            double max = 0;
            for (double t = c.t0(); t + 1 < c.t1(); t += 1) max = Math.max(max, barrage(t + 1, 200).position().distanceTo(barrage(t, 200).position()));
            assertTrue(max <= .15, names[n] + " speed " + max + " blocks/tick");
        }
        for (int n = 0; n < 4; n++) {
            var c = SilentGateCinematic.ENDING.get(n);
            double max = 0;
            for (double t = c.t0(); t + 1 < c.t1(); t += 1) max = Math.max(max, barrage(212 + t + 1, 200).position().distanceTo(barrage(212 + t, 200).position()));
            assertTrue(max <= .15, "E" + (n + 1) + " speed " + max + " blocks/tick");
        }
    }

    @Test void curvesAreNotTheDefaultEaseInOut() {
        var out = SilentGateCinematic.Curve.EASE_OUT_LONG;
        assertEquals(0, out.u(0), 1e-9); assertEquals(1, out.u(1), 1e-9);
        assertTrue(out.u(.1) > .25, "starts with momentum");
        assertTrue(out.u(1) - out.u(.9) < .005, "stops very slowly");
        var in = SilentGateCinematic.Curve.EASE_IN;
        assertTrue(in.u(.1) < .02 && in.u(1) - in.u(.9) > .15, "accelerates to the end");
        // The dive: from rest, speed grows linearly (u = k^2).
        assertEquals(0, SilentGateSequence.flightU(SilentGateSequence.FLIGHT_START), 1e-12);
        assertEquals(1, SilentGateSequence.flightU(SilentGateSequence.FLIGHT_END), 1e-12);
        assertEquals(.25, SilentGateSequence.flightU(461), 1e-12);
        assertEquals(461, SilentGateSequence.flightTime(SilentGateSequence.flightU(461)), 1e-9, "inverse");
        // Hermite key moves: velocity continuous through a key, and a rest key stops dead.
        var keys = List.of(new SilentGateCinematic.Key(0, new Vec3(0, 0, 0), Vec3.ZERO, true), new SilentGateCinematic.Key(10, new Vec3(10, 0, 0), Vec3.ZERO, false),
                new SilentGateCinematic.Key(20, new Vec3(20, 0, 0), Vec3.ZERO, true));
        assertEquals(10, SilentGateCinematic.splinePos(keys, 10).x, 1e-9);
        double vBefore = SilentGateCinematic.splinePos(keys, 9.99).x - SilentGateCinematic.splinePos(keys, 9.98).x, vAfter = SilentGateCinematic.splinePos(keys, 10.02).x - SilentGateCinematic.splinePos(keys, 10.01).x;
        assertEquals(vBefore, vAfter, 1e-3);
        assertEquals(0, SilentGateCinematic.splinePos(keys, 20).x - SilentGateCinematic.splinePos(keys, 19.99).x, 5e-4);
    }

    @Test void stuckDoorsTrembleThenJoltAndTheTouchdownRings() {
        assertEquals(0, Math.hypot(SilentGateCinematic.rattle(SilentGateSequence.OPEN_START - 1)[0], SilentGateCinematic.rattle(SilentGateSequence.OPEN_START - 1)[1]), 1e-9);
        double stuck = 0, jolt = 0;
        for (double t = 345; t < 362; t += .5) stuck = Math.max(stuck, Math.abs(SilentGateCinematic.rattle(t)[1]));
        for (double t = SilentGateSequence.OPEN_END + 1; t < SilentGateSequence.OPEN_END + 5; t += .25) jolt = Math.max(jolt, Math.abs(SilentGateCinematic.rattle(t)[1]));
        assertTrue(stuck > .3 && stuck < 2, "tremble " + stuck);
        assertTrue(jolt > 1.2, "stop jolt " + jolt);
        assertEquals(0, SilentGateCinematic.rattle(SilentGateSequence.OPEN_END + 25)[1], .05, "calm again");
        assertEquals(0, SilentGateCinematic.landing(299)[1], 0);
        assertTrue(Math.abs(SilentGateCinematic.landing(302)[1]) > .5, "the touchdown shakes the view");
        assertEquals(0, SilentGateCinematic.landing(330)[1], .05, "a spring that dies away");
    }

    @Test void barrageShotsFollowTheScript() {
        assertArrayEquals(new int[]{82, 125, 162, 212}, SilentGateCinematic.boundaries(200));
        assertArrayEquals(new int[]{12 + 140, 12 + 226, 12 + 300, 412}, SilentGateCinematic.boundaries(400), "scaled with the barrage length");
        // B1: close on the casting arm from its side, 3 blocks away.
        for (double c = 0; c < 14; c += 2) {
            var p = barrage(c, 200);
            assertEquals(3.1, p.position().distanceTo(PLAYER.add(0.25, 1.3, 0)), .5, "B1 close at " + c);
            assertTrue(inFrame(p, PLAYER.add(.37, 1.3, .5)), "the hand is in frame at " + c);
        }
        // B2: wide and low toward the gate, the whole gate in frame.
        for (double c = 14; c < 50; c += 4) {
            var p = barrage(c, 200);
            assertTrue(p.position().y < PLAYER.y + 5 && p.pitch() < -15, "low, looking up toward the gate: " + p.pitch());
            assertTrue(inFrame(p, DOOR) && inFrame(p, FOOT.add(0, 190, 0)), "the gate in frame");
        }
        // B3: 24 blocks under a circle, looking up at its face.
        var rig = new SilentGateCinematic.Rig(frame(2, 500, 20, 200), (x, z) -> Double.NEGATIVE_INFINITY, List.of(), null);
        int circle = SilentGateCinematic.firingCircle(rig);
        assertTrue(circle >= 0 && circle % 4 == 0, "a low-tier circle in front of the caster");
        Vec3 home = SilentGateSequence.skyCircle(PLAYER, 0f, 7L, circle, 48);
        int fires = 0; for (int a = 38; a < 70; a++) if (SilentGateSequence.fires(circle, a, 200, 7L)) fires++;
        assertTrue(fires >= 1, "it fires during B3");
        for (double c = 50; c < 82; c += 4) {
            var p = barrage(c, 200);
            assertEquals(24, home.y - p.position().y, .5);
            assertEquals(11, Math.hypot(p.position().x - home.x, p.position().z - home.z), .5);
            assertTrue(p.pitch() < -55, "looking up at the face: " + p.pitch());
        }
        // B5: high over the crater field, looking steeply down.
        for (double c = 125; c < 162; c += 6) {
            var p = barrage(c, 200);
            assertTrue(p.position().y > PLAYER.y + 200 && p.pitch() > 45, "from high looking down: " + p.pitch());
        }
        // B6: low behind the caster, looking up.
        for (double c = 163; c < 211; c += 6) {
            var p = barrage(c, 200);
            assertTrue(p.position().y < PLAYER.y + 8 && PLAYER.z - p.position().z > 10 && p.pitch() < -20, "low behind, looking up at " + c + ": " + p.pitch());
        }
        // The ending: E1 medium on the closing doors, E2 high and wide, E3 low looking steeply up at the gate flying into the circle, E4 wide on the dissolving circle.
        int endB = SilentGateCinematic.boundaries(200)[3];
        assertEquals(55, barrage(endB + 1, 200).position().distanceTo(DOOR), 8, "E1 medium on the doors");
        assertTrue(barrage(endB + 60, 200).position().y > PLAYER.y + 200 && barrage(endB + 60, 200).pitch() > 15, "E2 high, looking down at the circles shrinking");
        Vec3 circleCentre = SilentGateSequence.gateCenter(PLAYER, 0f).add(0, ALT - 135, 0);
        for (double e = 82; e < 125; e += 6) {
            var p = barrage(endB + e, 200);
            assertTrue(p.position().y < PLAYER.y + 5 && p.pitch() < -35, "E3 low, looking steeply up: " + p.pitch());
            assertTrue(inFrame(p, SilentGateSequence.gateFoot(PLAYER, 0f, SilentGateSequence.gateRise(e)).add(0, 100, 0)), "the climbing gate is in frame at " + e);
        }
        for (double e = 126; e < 160; e += 6) {
            var p = barrage(endB + e, 200);
            assertTrue(p.pitch() < -35 && inFrame(p, circleCentre), "E4 wide on the great circle at " + e);
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

    @Test void killShotOrbitsTheTargetAtFivePointFiveBlocks() {
        var shots = List.of(new SilentGateRenderer.Shot(3, new Vec3(5, 70, 40), 100));
        Vec3 target = new Vec3(6, 70, 50);
        for (double c = 83; c < 124; c += 2) {
            var pose = shotAt(c, shots, target);
            assertEquals(5.5, Math.hypot(pose.position().x - target.x, pose.position().z - target.z), .01, "5.5 blocks at " + c);
            assertEquals(target.y + .6, pose.position().y, 1e-9, "just above its feet, under its 1.6 eye at " + c);
            assertTrue(pose.pitch() < -15 && pose.pitch() > -30, "looking a bit up so the beams fall into the frame: " + pose.pitch());
        }
        double a0 = Math.atan2(shotAt(82.5, shots, target).position().x - target.x, shotAt(82.5, shots, target).position().z - target.z);
        double a1 = Math.atan2(shotAt(124.5, shots, target).position().x - target.x, shotAt(124.5, shots, target).position().z - target.z);
        assertEquals(.4 * 42, Math.abs(Math.IEEEremainder(Math.toDegrees(a1 - a0), 360)), 1.0, "0.4 deg/tick orbit");
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
