package net.godwand.client;

import net.godwand.network.SilentGatePayload;
import net.godwand.skill.SilentGateSequence;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToDoubleBiFunction;

import static org.junit.jupiter.api.Assertions.*;

/** Script v5 (docs/superpowers/plans/2026-11-10-silent-gate-script-v4.md, section v5): fourteen build shots, B1..B5 and E1..E3, on one side of the caster-to-gate line. */
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
    /** The shot by its script number (S04 is the first since the waking shots 1..3 were cut). */
    private static SilentGateCinematic.Cut shot(int n) {
        for (var c : SilentGateCinematic.CUTS) if (c.name().startsWith(String.format("S%02d ", n))) return c;
        throw new AssertionError("no shot " + n);
    }
    private static double lens(double t) { return SilentGateCinematic.lens(t, -1, 200); }

    /** True when {@code p} is inside a 16:9 frame of a 70 degree vertical field of view looking from {@code pose}. */
    private static boolean inFrame(AtomicCinematic.Pose pose, Vec3 p) { return inFrame(pose, p, 1); }
    /** As above through a lens: the field of view is {@code lens} times 70 degrees. */
    private static boolean inFrame(AtomicCinematic.Pose pose, Vec3 p, double lens) {
        Vec3 f = Vec3.directionFromRotation(pose.pitch(), pose.yaw()), right = f.cross(new Vec3(0, 1, 0)).normalize(), up = right.cross(f);
        Vec3 d = p.subtract(pose.position());
        double z = d.dot(f), v = Math.tan(Math.toRadians(35 * lens));
        return z > 0 && Math.abs(d.dot(up) / z) <= v && Math.abs(d.dot(right) / z) <= v * 16 / 9;
    }

    @Test void shotTableIsScriptV7() {
        var cuts = SilentGateCinematic.CUTS;
        assertArrayEquals(new int[]{0, 70, 142, 258, 290, 340, 420, 468}, cuts.stream().mapToInt(SilentGateCinematic.Cut::t0).toArray());
        for (int i = 1; i < cuts.size(); i++) assertEquals(cuts.get(i - 1).t1(), cuts.get(i).t0(), cuts.get(i).name());
        assertEquals(480, cuts.get(cuts.size() - 1).t1(), "the waiting frame holds from 468 on into the wait");
        var bar = SilentGateCinematic.BARRAGE;
        assertArrayEquals(new int[]{0, 14, 60, SilentGateCinematic.B4_FROM, SilentGateCinematic.B4_TO}, bar.stream().mapToInt(SilentGateCinematic.Cut::t0).toArray());
        assertEquals(212, bar.get(4).t1(), "the command and four shots for the whole barrage, each a slow move: no cutting about");
        for (int i = 1; i < bar.size(); i++) assertEquals(bar.get(i - 1).t1(), bar.get(i).t0());
        var end = SilentGateCinematic.ENDING;
        assertArrayEquals(new int[]{0, SilentGateSequence.END_CLOSE, SilentGateSequence.END_RISE_FROM}, end.stream().mapToInt(SilentGateCinematic.Cut::t0).toArray());
        assertEquals(SilentGateSequence.ENDING_TICKS, end.get(2).t1());
        // Few cuts: every shot of the build is held for at least a second and a half.
        for (var c : cuts) if (c.t1() <= 468) assertTrue(c.t1() - c.t0() >= 30, c.name() + " is held");
        // The order the owner asked for: the hand is raised; the gate is made and comes down in one shot and has spread its wings before we return to the caster;
        // the "shh", the spin and the discs; only then the doors.
        assertTrue(shot(2).t0() == SilentGateSequence.CIRCLE_START && shot(2).t1() == SilentGateSequence.CIRCLE_FULL && shot(3).t0() == SilentGateSequence.SLIT_START);
        assertTrue(shot(3).t0() <= SilentGateSequence.DESCENT_START && shot(3).t1() >= SilentGateSequence.SHOCK + 22, "crack, descent and wings in one shot");
        assertTrue(SilentGateSequence.OPEN_START >= shot(5).t1(), "the doors wait for the caster");
        assertTrue(SilentGateSequence.OPEN_START >= shot(6).t0() && SilentGateSequence.OPEN_END < shot(6).t1(), "one shot from the stuck doors to the flood");
        assertEquals(SilentGateSequence.BLOOM_START, shot(13).t0());
    }

    @Test void casterShots() {
        Vec3 face = PLAYER.add(0, 1.62, 0);
        // The hand is raised: seen from the caster's right side, close by and low, so the wind of mana streams across the frame.
        for (double t = 0; t < 70; t += 5) {
            var p = build(0, t);
            assertTrue(p.position().x < PLAYER.x - 2.5 && p.position().distanceTo(face) < 6 && p.position().y < PLAYER.y + 2 && inFrame(p, face, lens(t)), "beside the caster at " + t + ": " + p.position());
        }
        // Back on the caster: close before the face while the finger comes to the lips.
        for (double t = 258; t < 290; t += 4) {
            var p = build(0, t);
            assertTrue(p.position().distanceTo(face) < 2.6 && p.position().z > PLAYER.z + 1 && inFrame(p, face, lens(t)), "close before the face at " + t);
        }
        assertEquals(GatePose.LIPS.lp(), GatePose.joints(288, -1, 1000).lp(), .05);
        assertTrue(GatePose.joints(252, -1, 1000).lp() > GatePose.LIPS.lp() + .5, "the finger comes up inside the shot");
        // Then it only ever draws back, most of the way while the wand is spun; the discs behind the caster are made in this shot.
        double prev = 0;
        for (double t = 290; t < 340; t += 1) { double d = build(0, t).position().distanceTo(face); assertTrue(d >= prev - 1e-9, "never closer again at " + t); prev = d; }
        assertTrue(prev > 3 && prev < 4.5, "drawn back, but not far: " + prev);
        // Square on to the caster from the "shh" to the end of the draw-back.
        for (double t = 258; t < 340; t += 4) assertEquals(PLAYER.x, build(0, t).position().x, .02, "square on at " + t);
        assertTrue(GateMinor.DISCS_FROM >= 290 && GateMinor.DISCS_FROM + 2.2 * 7 + 16 <= 340);
        assertTrue(GatePose.spinAngle(GatePose.poseClock(295)) < .2 && GatePose.spinAngle(GatePose.poseClock(331)) > 6, "the wand is spun while the camera draws back");
    }

    /** From the first shot on the camera stays on the caster's right of the caster-to-gate line: the line is never crossed. */
    @Test void everyShotKeepsToOneSideOfTheLine() {
        for (double t = 0; t < 468; t += 4) assertTrue(build(0, t).position().x <= PLAYER.x + 1e-6, "never left of the line at " + t + ": " + build(0, t).position());
        // The great circle: the caster's head in the foreground at first, a tilt up to the circle while the camera creeps forward.
        assertTrue(inFrame(build(0, 82), PLAYER.add(0, 1.7, 0)));
        assertTrue(build(0, 82).pitch() - build(0, 141.99).pitch() > 18, "tilts up some 20 degrees (after the glide in)");
        assertTrue(pos(70).distanceTo(pos(141)) > 2, "and moves");
    }

    @Test void gateShotsFollowTheGate() {
        double k = SilentGateSequence.SCALE;
        // The gate comes down in one shot from the ground far behind the caster, looking up: it holds the crack, then tilts down with the gate, riding in slowly.
        Vec3 crack = new Vec3(FOOT.x, PLAYER.y + ALT, FOOT.z);
        for (double t = 142; t < 258; t += 6) {
            var p = build(0, t);
            assertTrue(p.position().y < PLAYER.y + 10 && p.pitch() < -15 && PLAYER.z - p.position().z > 50, "on the ground behind the caster, looking up at " + t + ": " + p.position() + " " + p.pitch());
            if (t < SilentGateSequence.DESCENT_START) assertTrue(inFrame(p, crack, lens(t)), "the crack is in frame at " + t);
            Vec3 foot = SilentGateSequence.gateFoot(PLAYER, 0f, SilentGateSequence.gateDescent(t));
            if (t >= SilentGateSequence.DESCENT_END - 10) assertTrue(inFrame(p, foot.add(0, 150, 0), lens(t)), "the gate is in frame at " + t);
        }
        assertTrue(build(0, 222).pitch() > build(0, 150).pitch() + 5, "tilts down with the gate");
        assertTrue(lens(200) < .45 && lens(250) > .9, "close on the gate while it sinks, thrown wide when the wings burst open");
        assertTrue(lens(SilentGateSequence.SHOCK + 8) - lens(SilentGateSequence.SHOCK) > .35, "most of it in the first half second");
        assertTrue(lens(257.9) > lens(254) && lens(254) > lens(250), "and still opening when the shot is cut: it never arrives and stands");
        var wide = build(0, 250);
        assertTrue(inFrame(wide, FOOT, lens(250)) && inFrame(wide, FOOT.add(54 * k, 70 * k, 0), lens(250)) && inFrame(wide, FOOT.add(-54 * k, 70 * k, 0), lens(250)), "the winged gate in frame");
        // The doors: one shot, in the air under the gate on the caster's right, going slowly round it from the stuck doors to the flood, the door in frame.
        double first = 0, lastAngle = 0;
        for (double t = 340; t < 420; t += 4) {
            var p = build(0, t);
            assertTrue(p.position().y > PLAYER.y + 100 && p.position().y < FOOT.y && inFrame(p, DOOR, lens(t)), "under the gate at " + t + ": " + p.position());
            double angle = Math.toDegrees(Math.atan2(PLAYER.x - p.position().x, FOOT.z - p.position().z));
            if (t == 340) first = angle; else assertTrue(angle > lastAngle, "going round at " + t);
            lastAngle = angle;
        }
        assertTrue(lastAngle - first > 20 && lastAngle - first < 40, "a slow arc of " + (lastAngle - first) + " degrees");
        assertTrue(lens(418) < lens(390) - .05, "a gentle push in as the doors give");
    }

    @Test void canopyShotHoldsTheCasterAndTheGateForTwoSecondsAndMore() {
        assertTrue(shot(13).t1() - shot(13).t0() >= 48);
        for (double t = 420; t < 468; t += 4) {
            var p = build(0, t);
            assertTrue(inFrame(p, DOOR, lens(t)) && PLAYER.z - p.position().z > 1 && p.position().distanceTo(PLAYER) < 8, "over the shoulder, the gate ahead at " + t);
        }
    }

    @Test void waitingFrameIsCloseOnTheCaster() {
        for (double t = 520; t < 680; t += 10) {
            var p = build(0, t);
            assertTrue(p.position().distanceTo(PLAYER.add(0, 1.5, 0)) < 6.5 && inFrame(p, PLAYER.add(0, 1.5, 0), lens(t)), "close on the caster at " + t + ": " + p.position());
        }
    }

    /** A camera that is always slowly on its way somewhere: no shot of the build stands still, none races. */
    @Test void shotsMoveSlowlyButNeverStandStill() {
        double[] limit = {.1, .15, .4, .1, .15, 1.4, .15};
        for (int n = 0; n < SilentGateCinematic.CUTS.size() - 1; n++) {
            var c = SilentGateCinematic.CUTS.get(n);
            double max = 0;
            // The glide in from a neighbouring setup is checked on its own: smooth, and never a whip.
            double glide = n > 0 && pos(c.t0() - .01).distanceTo(pos(c.t0())) < .05 ? SilentGateCinematic.glideTicks(SilentGateCinematic.GLIDE_REACH) : 0, whip = 0;
            for (double t = c.t0(); t + 1 < c.t1(); t += 1) {
                double v = pos(t + 1).distanceTo(pos(t));
                if (t < c.t0() + glide) whip = Math.max(whip, v); else max = Math.max(max, v);
            }
            assertTrue(max <= limit[n], c.name() + " speed " + max + " blocks/tick");
            assertTrue(whip < .7, c.name() + " glide speed " + whip);
            assertTrue(pos(c.t0()).distanceTo(pos(c.t1() - .01)) > .1, c.name() + " moves");
        }
        assertEquals(480, SilentGateSequence.BUILD_TICKS);
        double[] barrage = {.15, .1, 1.1, 1.8, 1.3};
        for (int n = 0; n < SilentGateCinematic.BARRAGE.size(); n++) {
            var c = SilentGateCinematic.BARRAGE.get(n);
            double max = 0, min = Double.MAX_VALUE;
            double from = n > 0 && barrage(c.t0() - .01, 200).position().distanceTo(barrage(c.t0(), 200).position()) < .05 ? c.t0() + SilentGateCinematic.glideTicks(SilentGateCinematic.GLIDE_REACH) : c.t0();
            for (double t = from; t + 1 < c.t1(); t += 1) {
                double v = barrage(t + 1, 200).position().distanceTo(barrage(t, 200).position());
                max = Math.max(max, v); min = Math.min(min, v);
            }
            assertTrue(max <= barrage[n], c.name() + " speed " + max + " blocks/tick");
            assertTrue(min > .2 * max, c.name() + " never rests: " + min + " .. " + max);
        }
        // Nor does anything in the build, the wait or the ending come to a stand: every shot is still moving in its last tick.
        for (var c : SilentGateCinematic.CUTS) assertTrue(pos(c.t1() - 1).distanceTo(pos(c.t1() - .01)) > 1e-4, c.name() + " is still moving when it is cut");
        assertTrue(pos(600).distanceTo(pos(601)) > 1e-4, "the waiting frame sways");
        for (double e = 1; e < SilentGateSequence.ENDING_TICKS - 1; e += 13)
            assertTrue(barrage(212 + e, 200).position().distanceTo(barrage(213 + e, 200).position()) > 1e-4, "the ending drifts at " + e);
    }

    @Test void impactFramesAndLensFollowTheBeats() {
        assertArrayEquals(new double[]{0, 0}, SilentGateCinematic.flash(SilentGateSequence.DESCENT_END - .1, -1, -1), 0, "nothing before the touchdown");
        assertEquals(1, SilentGateCinematic.flash(3.5, -1, -1)[0], 0, "the first click is an impact frame: white on black");
        assertEquals(2, SilentGateCinematic.flash(4.5, -1, -1)[0], 0, "then black on white");
        assertEquals(1, SilentGateCinematic.flash(700, .5, -1)[0], 0, "and so is the command");
        assertEquals(2, SilentGateCinematic.flash(700, 1.5, -1)[0], 0);
        assertEquals(1, SilentGateCinematic.flash(SilentGateSequence.DESCENT_END + 1, -1, -1)[0], 0, "the touchdown is inked");
        assertEquals(0, SilentGateCinematic.flash(SilentGateSequence.DESCENT_END + 3, -1, -1)[0], 0, "for two and a half ticks only");
        assertTrue(SilentGateCinematic.flash(SilentGateSequence.DESCENT_END + 3, -1, -1)[1] > .2 && SilentGateCinematic.flash(SilentGateSequence.DESCENT_END + 30, -1, -1)[1] == 0, "then a small flash that dies away");
        assertTrue(SilentGateCinematic.flash(412.5, -1, -1)[1] > .15 && SilentGateCinematic.flash(412.5, -1, -1)[0] == 0, "the doors' stop only flashes");
        assertEquals(1, SilentGateCinematic.flash(700, 100, .1)[0], 0, "the kill beam is inked during the hit-stop");
        assertEquals(0, SilentGateCinematic.flash(700, 100, 30)[1], 0);
        for (double t = 0; t < 480; t += 1) {
            double l = SilentGateCinematic.lens(t, -1, 200);
            assertTrue(l >= .2 && l <= 1.15, "lens " + l + " at " + t);
            // Inside a shot the lens only creeps (a slow zoom); it jumps at cuts alone.
            boolean cut = false; for (var c : SilentGateCinematic.CUTS) cut |= c.t0() == t + 1;
            // The one fast zoom: the view is thrown wide as the wings open, and goes on opening to the cut.
            boolean wings = t >= SilentGateSequence.SHOCK - 1 && t < 258;
            // And the kick of the first click.
            if (!cut) assertEquals(l, SilentGateCinematic.lens(t + 1, -1, 200), wings ? .085 : t < 12 ? .06 : .012, "zoom speed at " + t);
        }
        assertTrue(lens(30) <= .91 && lens(160) < .7 && lens(200) < .7 && lens(280) <= .85, "long lenses on the gate (it looks enormous) and on the face");
        assertTrue(lens(100) >= 1 && lens(440) > 1 && lens(360) > .7 && lens(360) < .85, "wide on the sky and on the canopy; the orbit is close, on a wider lens");
        for (double c = 0; c < 372; c += 3) assertTrue(SilentGateCinematic.lens(480 + c, c, 200) >= .35 && SilentGateCinematic.lens(480 + c, c, 200) <= 1.15);
    }

    @Test void curvesAreNotTheDefaultEaseInOut() {
        var out = SilentGateCinematic.Curve.EASE_OUT_LONG;
        assertEquals(0, out.u(0), 1e-9); assertEquals(1, out.u(1), 1e-9);
        assertTrue(out.u(.1) > .25, "starts with momentum");
        assertTrue(out.u(1) - out.u(.9) < .005, "stops very slowly");
        var in = SilentGateCinematic.Curve.EASE_IN;
        assertTrue(in.u(.1) < .02 && in.u(1) - in.u(.9) > .15, "accelerates to the end");
        // The glide: from rest to rest, symmetric, with no jerk at either end.
        var glide = SilentGateCinematic.Curve.GLIDE;
        assertEquals(0, glide.u(0), 0); assertEquals(1, glide.u(1), 1e-12); assertEquals(.5, glide.u(.5), 1e-12);
        assertTrue(glide.u(.02) < 1e-4 && 1 - glide.u(.98) < 1e-4, "at rest at both ends");
        // The flow: under way at both ends, a little faster in the middle, never at rest.
        var flow = SilentGateCinematic.Curve.FLOW;
        assertEquals(0, flow.u(0), 0); assertEquals(1, flow.u(1), 1e-12); assertEquals(.5, flow.u(.5), 1e-12);
        assertTrue(flow.u(.02) > .01 && 1 - flow.u(.98) > .01 && flow.u(.51) - flow.u(.49) < .03, "moving at both ends, no rush between");
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
        for (double t = SilentGateSequence.OPEN_START + 4; t < SilentGateSequence.STUCK_END - 3; t += .5) stuck = Math.max(stuck, Math.abs(SilentGateCinematic.rattle(t)[1]));
        for (double t = SilentGateSequence.OPEN_END + 1; t < SilentGateSequence.OPEN_END + 5; t += .25) jolt = Math.max(jolt, Math.abs(SilentGateCinematic.rattle(t)[1]));
        assertTrue(stuck > .3 && stuck < 2, "tremble " + stuck);
        assertTrue(jolt > 1.2, "stop jolt " + jolt);
        assertEquals(0, SilentGateCinematic.rattle(SilentGateSequence.OPEN_END + 25)[1], .05, "calm again");
        assertEquals(0, SilentGateCinematic.landing(SilentGateSequence.DESCENT_END - 1)[1], 0);
        assertTrue(Math.abs(SilentGateCinematic.landing(SilentGateSequence.DESCENT_END + 2)[1]) > .5, "the touchdown shakes the view");
        assertEquals(0, SilentGateCinematic.landing(SilentGateSequence.DESCENT_END + 30)[1], .05, "a spring that dies away");
    }

    @Test void barrageShotsFollowTheScript() {
        assertArrayEquals(new int[]{46, 125, 162, 212}, SilentGateCinematic.boundaries(200));
        assertArrayEquals(new int[]{12 + 68, 12 + 226, 12 + 300, 412}, SilentGateCinematic.boundaries(400), "scaled with the barrage length");
        // B1: close in front of the caster as the hand comes down.
        for (double c = 0; c < 14; c += 2) {
            var p = barrage(c, 200);
            assertTrue(p.position().distanceTo(PLAYER.add(0, 1.5, 0)) < 4.6 && p.position().z > PLAYER.z + 3, "B1 close, in front, at " + c);
            assertTrue(inFrame(p, PLAYER.add(.37, 1.3, .5)), "the hand is in frame at " + c);
        }
        // B2: from the caster. Low at their shoulder, looking steeply up into the circles that fire overhead: a cut from the command, no move.
        for (double c = 14; c < 60; c += 4) {
            var p = barrage(c, 200);
            assertTrue(p.position().distanceTo(PLAYER) < 7 && p.position().y < PLAYER.y + 1 && p.pitch() < -25, "close behind the caster, low, looking up at " + c + ": " + p.position() + " " + p.pitch());
            assertTrue(inFrame(p, PLAYER.add(0, 1.6, 0), SilentGateCinematic.lens(480 + c, c, 200)), "the caster's head in frame at " + c);
        }
        // B3: from the gate, the other way round: beside the gate's right column, above its doors, looking steeply down the field toward the caster.
        for (double c = 60; c < SilentGateCinematic.B4_FROM; c += 4) {
            var p = barrage(c, 200); double lens = SilentGateCinematic.lens(480 + c, c, 200);
            assertTrue(p.position().distanceTo(DOOR) < 130 && p.position().y > DOOR.y && p.pitch() > 40, "by the gate, looking down at " + c + ": " + p.position() + " " + p.pitch());
            assertTrue(p.position().x < PLAYER.x - 90 && !inFrame(p, DOOR, lens), "well outside the right column, the gate out of the picture at " + c);
            assertTrue(inFrame(p, PLAYER.add(0, 0, 70), lens), "the field in frame at " + c);
        }
        // B4: over the ground. A low track across the field before the caster, from one side of the line to the other, looking on toward the gate's side; the
        // kill beam and its impact frame fall in it.
        for (double c = SilentGateCinematic.B4_FROM; c < SilentGateCinematic.B4_TO; c += 3) {
            var p = barrage(c, 200);
            assertTrue(p.position().y < PLAYER.y + 6 && p.position().z > PLAYER.z + 25 && Math.abs(p.pitch()) < 12, "low over the field at " + c + ": " + p.position() + " " + p.pitch());
        }
        assertTrue(Math.abs(barrage(SilentGateCinematic.B4_TO - .1, 200).position().x - barrage(SilentGateCinematic.B4_FROM, 200).position().x) > 20, "it crosses the field");
        assertTrue((SilentGateCinematic.B4_TO - SilentGateCinematic.B4_FROM) * 1.8 <= 40, "a short shot: about 1.9 s at 360 ticks of barrage (owner, 2026-11-15)");
        int kill = SilentGateSequence.KILL_TICK;
        assertTrue(kill - 10 > SilentGateCinematic.B4_FROM && kill + 5 < SilentGateCinematic.B4_TO, "the kill beam and its squeeze of the bars stay inside the shot over the ground");
        // B5: the whole of it: far behind the caster, high and to the right, going slowly round in an arc, the gate in frame throughout, on one lens.
        double arc0 = 0, arc1 = 0;
        boolean first = true;
        for (double c = SilentGateCinematic.B4_TO; c < 212; c += 4) {
            var p = barrage(c, 200);
            assertTrue(p.position().y > PLAYER.y + 50 && PLAYER.z - p.position().z > 60 && p.position().x < PLAYER.x, "far behind, high, to the right at " + c + ": " + p.position());
            assertTrue(inFrame(p, DOOR, SilentGateCinematic.lens(480 + c, c, 200)), "the gate in frame at " + c);
            assertEquals(SilentGateCinematic.lens(480 + SilentGateCinematic.B4_TO, SilentGateCinematic.B4_TO, 200), SilentGateCinematic.lens(480 + c, c, 200), 0, "no zoom in the far round");
            double angle = Math.toDegrees(Math.atan2(PLAYER.x - p.position().x, PLAYER.z - p.position().z));
            if (first) { arc0 = angle; first = false; }
            arc1 = angle;
        }
        assertTrue(arc1 - arc0 > 15 && arc1 - arc0 < 30, "a slow arc of " + (arc1 - arc0) + " degrees");
        // Shot changes: to a setup close by the camera glides (no jump at the cut, it settles into the new shot), to one far away it cuts hard.
        assertTrue(barrage(13.99, 200).position().distanceTo(barrage(14, 200).position()) > 1, "the command cuts to the shot from the caster");
        assertTrue(barrage(59.99, 200).position().distanceTo(barrage(60, 200).position()) > 100, "the caster to the sky: a hard cut");
        assertEquals(0, pos(69.99).distanceTo(pos(70)), .05, "the raised hand glides into the great circle's shot");
        assertTrue(pos(257.99).distanceTo(pos(258)) > 50, "the gate to the face: a hard cut");
        // The ending reuses the build's setups, each drifting: E1 the doors, E2 the canopy wide, E3 the far camera following the gate up into the great circle.
        int endB = SilentGateCinematic.boundaries(200)[3];
        assertTrue(barrage(endB, 200).position().y < PLAYER.y + 10 && inFrame(barrage(endB, 200), DOOR, SilentGateCinematic.lens(700, endB, 200)), "E1 is the door shot from the ground");
        assertEquals(0, barrage(endB + 90, 200).position().distanceTo(pos(420)), 1.5, "E2 is the canopy wide");
        Vec3 circleCentre = new Vec3(FOOT.x, PLAYER.y + ALT, FOOT.z);
        for (double e = 112; e < 142; e += 6) {
            var p = barrage(endB + e, 200);
            assertEquals(0, p.position().distanceTo(pos(142)), 20, "E3 is the far camera of the gate");
            assertTrue(inFrame(p, SilentGateSequence.gateFoot(PLAYER, 0f, SilentGateSequence.gateRise(e)).add(0, 40 * SilentGateSequence.SCALE, 0), SilentGateCinematic.lens(700, endB + e, 200)), "the climbing gate is in frame at " + e);
        }
        for (double e = 246; e < 290; e += 6) assertTrue(inFrame(barrage(endB + e, 200), circleCentre, SilentGateCinematic.lens(700, endB + e, 200)), "E3 ends on the great circle at " + e);
        int end = SilentGateSequence.endTick(200);
        assertEquals(502, end);
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

    @Test void groundTrackFramesTheKillTarget() {
        Vec3 mob = PLAYER.add(-20, 0, 90);
        for (double c = SilentGateCinematic.B4_FROM; c < SilentGateCinematic.B4_TO; c += 3) {
            var p = shotAt(c, List.of(), mob);
            assertTrue(p.position().distanceTo(mob) < 25 && inFrame(p, mob.add(0, 1, 0), SilentGateCinematic.lens(480 + c, c, 200)), "the target in frame at " + c + ": " + p.position());
        }
        var hit = shotAt(SilentGateSequence.KILL_TICK, List.of(), mob);
        Vec3 f = Vec3.directionFromRotation(hit.pitch(), hit.yaw()), to = mob.add(0, SilentGateCinematic.KILL_LOOK_HEIGHT, 0).subtract(hit.position()).normalize();
        assertTrue(f.dot(to) > .999, "square on the target when the kill beam lands");
    }

    @Test void groundTrackSeesTheTargetOverAHill() {
        Vec3 mob = PLAYER.add(-20, 0, 90), aim = mob.add(0, SilentGateCinematic.KILL_LOOK_HEIGHT, 0);
        // A ridge 12 blocks high between the target and the caster's side.
        ToDoubleBiFunction<Double, Double> ground = (x, z) -> { double d = Math.hypot(x - mob.x, z - mob.z); return d > 5 && d < 10 ? PLAYER.y + 12 : PLAYER.y; };
        for (double c = SilentGateCinematic.B4_FROM; c < SilentGateCinematic.B4_TO; c += 3) {
            var p = SilentGateCinematic.cameraFor(frame(2, 480 + c, c, 200), PLAYER, 0f, 0f, true, List.of(), ground, mob);
            for (double k = .05; k < 1; k += .05) {
                Vec3 q = aim.add(p.position().subtract(aim).scale(k));
                assertTrue(q.y >= ground.applyAsDouble(q.x, q.z) + .9, "the ridge hides the target at " + c + ", " + k);
            }
        }
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

    @Test void barrageCameraPassesThroughTheTerrain() {
        // Owner, 2026-11-15: lifted over the live ground the camera jumped about; it keeps its path through blocks, like every other shot.
        for (double c = 14; c < 212; c += 7) {
            var f = frame(2, 480 + c, c, 200);
            var flat = SilentGateCinematic.cameraFor(f, PLAYER, 0f, 0f, true, List.of(), (x, z) -> Double.NEGATIVE_INFINITY);
            var hills = SilentGateCinematic.cameraFor(f, PLAYER, 0f, 0f, true, List.of(), (x, z) -> PLAYER.y + 20 + 15 * Math.sin(x * .3));
            assertEquals(0, flat.position().distanceTo(hills.position()), 1e-9, "the ground moves no shot at " + c);
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
