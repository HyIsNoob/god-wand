package net.godwand.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.godwand.skill.SilentGateSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SilentGateVisualTest {
    private static final Vec3 ANCHOR = new Vec3(0, 70, 0);

    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    static class Mesh implements VertexConsumer {
        final List<float[]> vertices = new ArrayList<>(); float[] current;
        public VertexConsumer addVertex(float x, float y, float z) { current = new float[]{x, y, z, 0}; vertices.add(current); return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { current[3] = a; return this; }
        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
    private static SilentGateRenderer.Frame frame(double age, double commandAge, int circles) {
        return new SilentGateRenderer.Frame(1, 100, 2, age, commandAge, ANCHOR, 0f, 7L, circles, 200, -1);
    }
    private static int emit(Mesh mesh, SilentGateRenderer.Frame f, List<SilentGateRenderer.Shot> shots) {
        return SilentGateRenderer.emitScene(mesh, new Matrix4f(), f, shots, Vec3.ZERO);
    }

    @Test void quadBudgetHoldsAtEveryAgeWith160Circles() {
        for (int age = 0; age <= 480 + 300 + 387; age += 5) {
            double command = age < 480 ? -1 : Math.min(age - 480, 372);
            Mesh mesh = new Mesh();
            int quads = emit(mesh, frame(age, command, 160), List.of());
            assertTrue(quads <= SilentGateRenderer.QUAD_BUDGET, "age " + age + ": " + quads);
            assertEquals(quads * 4, mesh.vertices.size());
        }
    }
    @Test void fullSceneStaysUnderTwelveThousandQuads() {
        int peak = 0;
        var shots = new ArrayList<SilentGateRenderer.Shot>();
        for (int i = 0; i < 24; i++) shots.add(new SilentGateRenderer.Shot(i, new Vec3(i, 70, 40), 100 + 320));
        for (int age = 0; age <= 700; age += 5) {
            double command = age < 480 ? -1 : Math.min(age - 480, 372);
            for (int layer = -1; layer <= 1; layer++) {
                for (Vec3 eye : new Vec3[]{Vec3.ZERO, new Vec3(0, 130, 48)}) {
                    int quads = SilentGateRenderer.emitScene(new Mesh(), new Matrix4f(), frame(age, command, 160), shots, eye, layer);
                    peak = Math.max(peak, quads);
                    assertTrue(quads <= 12000, "age " + age + " layer " + layer + " eye " + eye + ": " + quads);
                }
            }
        }
        assertTrue(peak > 1000, "scene should really be drawn, peak " + peak);
    }
    @Test void circlesInFrontOfTheGateFadeSoItsSilhouetteStaysClean() {
        Vec3 gate = new Vec3(0, 135, 144), eye = new Vec3(0, 3, 0);
        assertEquals(0, GateMinor.gateClear(eye, new Vec3(0, 150, 80), gate, 12), 1e-9, "on the gate's silhouette");
        assertEquals(1, GateMinor.gateClear(eye, new Vec3(150, 150, 80), gate, 12), 1e-9, "well to the side");
        assertEquals(1, GateMinor.gateClear(eye, new Vec3(0, 150, 300), gate, 12), 1e-9, "behind the gate");
        double prev = 0;
        for (double x = 0; x <= 120; x += 6) { double k = GateMinor.gateClear(eye, new Vec3(x, 150, 80), gate, 12); assertTrue(k >= prev - 1e-12); prev = k; }
    }
    @Test void circlesStayExactlyWhereTheyBloomThroughWaitActivationAndBarrage() {
        int n = 220, total = SilentGateSequence.skyTotal(n);
        for (double age : new double[]{440, 480, 520, 700}) for (double command : new double[]{-1, 0, 5, 11, 12})
            for (int i = 0; i < total; i += 7) {
                var f = new SilentGateRenderer.Frame(1, 100, command < 0 ? 1 : 2, age, command, ANCHOR, 0f, 7L, n, 200, -1);
                assertEquals(SilentGateSequence.skyCircle(ANCHOR, 0f, 7L, i, n), SilentGateRenderer.circlePosition(ANCHOR, f, i, n, null, 100 + age), "circle " + i + " at age " + age + " command " + command);
            }
        // With a shot a circle only slides a few blocks sideways (never more than 20) and comes back.
        var f = new SilentGateRenderer.Frame(1, 100, 2, 600, 100, ANCHOR, 0f, 7L, n, 200, -1);
        for (int i = 0; i < n; i += 5) {
            Vec3 home = SilentGateSequence.skyCircle(ANCHOR, 0f, 7L, i, n);
            for (boolean heavy : new boolean[]{false, true}) for (double dx : new double[]{-60, -9, 0, 14, 90}) {
                var shot = new SilentGateRenderer.Shot(i, new Vec3(home.x + dx, ANCHOR.y, home.z + dx / 2), 700, heavy);
                for (double now = 700; now < 700 + 45; now += 1) {
                    Vec3 at = SilentGateRenderer.circlePosition(ANCHOR, f, i, n, shot, now);
                    assertTrue(at.distanceTo(home) <= 20.0001, "slides " + at.distanceTo(home));
                    assertEquals(home.y, at.y, 0);
                }
                assertEquals(home, SilentGateRenderer.circlePosition(ANCHOR, f, i, n, shot, 700 + 40), "back home");
            }
        }
    }
    @Test void twoHundredFiftySixCirclesStayUnderTheBudgetEvenFromInsideTheField() {
        int peak = 0;
        Vec3 lane = SilentGateSequence.flight(ANCHOR, 0f, .5);
        for (int age = 285; age <= 480; age += 5)
            for (Vec3 eye : new Vec3[]{Vec3.ZERO, lane, SilentGateSequence.doorCenter(ANCHOR, 0f), new Vec3(0, 160, -120)}) {
                int quads = SilentGateRenderer.emitScene(new Mesh(), new Matrix4f(), frame(age, -1, 256), List.of(), eye, -1);
                peak = Math.max(peak, quads);
                assertTrue(quads <= 14000, "age " + age + " eye " + eye + ": " + quads);
            }
        assertTrue(peak > 3000, "hundreds of circles are really drawn: " + peak);
    }
    private static GateDraw.Ctx ctx(Mesh mesh, Vec3 eye, int layer) {
        GateDraw.Ctx c = new GateDraw.Ctx(); c.v = mesh; c.m = new Matrix4f(); c.eye = eye; c.layer = layer; return c;
    }
    private static final GateFx.Pose POSE = GateFx.Pose.of(new Vec3(0, 0, 0), 0f);

    @Test void gateStaysUnderFourThousandQuadsFromEveryDistance() {
        int peak = 0;
        Vec3[] eyes = {new Vec3(0, 108, -270), new Vec3(0, 90, -36), new Vec3(-42, -9, -18), new Vec3(0, 108, -1200), new Vec3(90, 240, 60), new Vec3(0, 108, 9)};
        for (Vec3 eye : eyes) for (double open : new double[]{0, .5, 1}) {
            GateDraw.Ctx c = ctx(new Mesh(), eye, -1);
            GateModel.draw(c, POSE, 250, open, 1.2);
            peak = Math.max(peak, c.quads);
            assertTrue(c.quads <= 4000, "eye " + eye + " open " + open + ": " + c.quads);
        }
        assertTrue(peak > 700, "the gate should really be sculpted, peak " + peak);
    }
    @Test void gateScalesWithTheSequenceConstants() {
        Mesh mesh = new Mesh();
        Vec3 eye = new Vec3(0, 120, -270);
        GateModel.draw(ctx(mesh, eye, 0), POSE, 250, 0, 0);
        double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
        for (float[] p : mesh.vertices) { minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]); minY = Math.min(minY, p[1] + eye.y); maxY = Math.max(maxY, p[1] + eye.y); }
        double halfW = SilentGateSequence.GATE_WIDTH / 2.0 * SilentGateSequence.SCALE, k = SilentGateSequence.SCALE;
        assertTrue(maxX >= halfW - 3.6 && maxX <= halfW + 1.5 && minX <= -halfW + 3.6 && minX >= -halfW - 1.5, "outer columns span " + minX + ".." + maxX);
        assertTrue(maxY >= (SilentGateSequence.GATE_HEIGHT + 6) * k && maxY <= (SilentGateSequence.GATE_HEIGHT + 11) * k, "finial top " + maxY);
        assertTrue(minY <= 0 && minY >= -11 * k, "column tips and spike " + minY);
        assertTrue(SilentGateSequence.GATE_INNER_WIDTH == 22 && SilentGateSequence.GATE_INNER_HEIGHT == 44, "door opening 22 x 44");
        // The finials stay under the great circle (altitude 100 above the anchor) when the gate is at rest.
        assertTrue(SilentGateSequence.gateBase(0) + maxY < SilentGateSequence.circleAltitude(0), "finials stay under the great circle");
        assertEquals(3, SilentGateSequence.SCALE);
    }
    @Test void doorLeavesSwingOutTowardsTheCasterWithGateOpen() {
        Vec3 eye = new Vec3(0, 120, -240);
        double[] centre = new double[2]; double[] depth = new double[2];
        for (int k = 0; k < 2; k++) {
            Mesh mesh = new Mesh();
            GateModel.draw(ctx(mesh, eye, 0), POSE, 250, k, 0);
            centre[k] = mesh.vertices.stream().filter(p -> p[1] + eye.y > 51 && p[1] + eye.y < 168 && Math.abs(p[0]) < 9).count();
            depth[k] = mesh.vertices.stream().filter(p -> p[1] + eye.y > 51 && p[1] + eye.y < 168 && Math.abs(p[0]) < 34).mapToDouble(p -> -(p[2] + eye.z)).max().orElse(0);
        }
        assertTrue(centre[0] > 20, "closed leaves meet in the middle: " + centre[0]);
        assertEquals(0, centre[1], "open leaves have left the middle");
        assertTrue(depth[1] > 18 && depth[0] < 9, "open leaves swing out towards the caster (-z): " + depth[1] + " vs " + depth[0]);
        assertEquals(SilentGateSequence.gateOpen(360) < SilentGateSequence.gateOpen(400), true);
    }
    @Test void slitShockwaveAndVeinsAppearAtTheirTicks() {
        Vec3 eye = new Vec3(0, 120, -270);
        int[] slit = new int[2], wave = new int[3];
        for (int k = 0; k < 2; k++) { GateDraw.Ctx c = ctx(new Mesh(), eye, 1); GateFx.slit(c, POSE, k, 84); slit[k] = c.quads; }
        assertEquals(0, slit[0]);
        assertTrue(slit[1] > 30, "slit " + slit[1]);
        double[] ages = {312, 330, 360};
        for (int k = 0; k < 3; k++) { GateDraw.Ctx c = ctx(new Mesh(), eye, 1); GateFx.shockwave(c, POSE, ages[k], 318, 120, 34, 1); wave[k] = c.quads; }
        assertEquals(0, wave[0]); assertTrue(wave[1] > 40, "shockwave " + wave[1]); assertEquals(0, wave[2]);
        assertEquals(0, GateModel.vein(230, -1, 0), "veins are dark while falling");
        assertTrue(GateModel.vein(319, -1, 0) > 1, "flare at the shockwave");
        assertTrue(GateModel.vein(400, -1, 0) > .4, "steady glow while waiting");
        assertTrue(GateModel.vein(400, 2, 0) > GateModel.vein(400, -1, 0), "activation flares them");
        // In the scene: the gate is absent before the slit, present after, and the slit itself is drawn from 140.
        Mesh before = new Mesh(), after = new Mesh();
        int a = SilentGateRenderer.emitScene(before, new Matrix4f(), frame(255, -1, 0), List.of(), Vec3.ZERO, 0);
        int b = SilentGateRenderer.emitScene(after, new Matrix4f(), frame(300, -1, 0), List.of(), Vec3.ZERO, 0);
        assertTrue(b - a > 500, "gate body appears: " + a + " -> " + b);
        int g1 = SilentGateRenderer.emitScene(new Mesh(), new Matrix4f(), frame(250, -1, 0), List.of(), Vec3.ZERO, 1);
        int g0 = SilentGateRenderer.emitScene(new Mesh(), new Matrix4f(), frame(225, -1, 0), List.of(), Vec3.ZERO, 1);
        assertTrue(g1 - g0 > 30, "slit glow at 250: " + g0 + " -> " + g1);
    }

    @Test void circleGeometryFadesOutWithinTwelveBlocksOfTheCamera() {
        Vec3 disc = SilentGateSequence.groundCircle(ANCHOR, 0f, 0);
        Mesh near = new Mesh(), far = new Mesh();
        SilentGateRenderer.emitScene(near, new Matrix4f(), frame(520, -1, 0), List.of(), disc);
        SilentGateRenderer.emitScene(far, new Matrix4f(), frame(520, -1, 0), List.of(), Vec3.ZERO);
        // Vertices are camera relative: the camera sits on the first gold disc, so everything within 2.5 blocks of it must be invisible.
        double nearAlpha = near.vertices.stream().filter(p -> Math.hypot(Math.hypot(p[0], p[1]), p[2]) < 2.5).mapToDouble(p -> p[3]).max().orElse(-1);
        double farAlpha = far.vertices.stream().filter(p -> p[1] > ANCHOR.y - 1 && p[1] < ANCHOR.y + 8).mapToDouble(p -> p[3]).max().orElseThrow();
        assertEquals(0, nearAlpha, "disc vanishes around the camera");
        assertTrue(farAlpha > 40, "same disc is visible from afar: " + farAlpha);
    }
    @Test void greatCircleAppearsAtItsAltitudeAndNothingBeforeIt() {
        Mesh early = new Mesh();
        assertEquals(0, emit(early, frame(100, -1, 48), List.of()));
        Mesh mesh = new Mesh();
        emit(mesh, frame(175, -1, 48), List.of());
        double target = SilentGateSequence.circleAltitude(ANCHOR.y);
        assertTrue(mesh.vertices.stream().anyMatch(p -> Math.abs(p[1] - (ANCHOR.y + target)) <= 3));
    }
    @Test void groundRingDiscsStandUprightBehindTheCaster() {
        Mesh mesh = new Mesh();
        emit(mesh, frame(520, -1, 0), List.of());
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (float[] p : mesh.vertices) if (p[1] > ANCHOR.y - 1 && p[1] < ANCHOR.y + 8 && Math.hypot(p[0] - ANCHOR.x, p[2] - ANCHOR.z) < 12) { minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]); }
        // Gold disc (r 2.4) centred 3 above the anchor; its spinning square seal tile reaches a little past the circle.
        assertTrue(minY >= ANCHOR.y + 3 - 3.4 && minY <= ANCHOR.y + 3 - 2.0, "min " + minY);
        assertTrue(maxY <= ANCHOR.y + 3 + 3.4 && maxY >= ANCHOR.y + 3 + 2.0, "max " + maxY);
    }
    @Test void beamsLastEightTicksAndTheEndingFadesToNothing() {
        Vec3 home3 = SilentGateSequence.skyCircle(ANCHOR, 0f, 7L, 3, 48);
        var shot = new SilentGateRenderer.Shot(3, new Vec3(home3.x + 12, ANCHOR.y, home3.z), 100 + 320);
        Mesh with = new Mesh(), without = new Mesh();
        int g = SilentGateSequence.glideTicks(SilentGateSequence.skyCircle(ANCHOR, 0f, 7L, 3, 48), shot.hit());
        assertTrue(g > 0, "the circle first glides over its target");
        int a = emit(with, frame(322 + g, 22 + g, 48), List.of(shot)), b = emit(without, frame(322 + g, 22 + g, 48), List.of());
        assertTrue(a - b > 20, "ribbon, muzzle and impact: " + (a - b));
        assertEquals(0, emit(new Mesh(), frame(322 + g - 1, 22 + g - 1, 48), List.of(shot)) - emit(new Mesh(), frame(322 + g - 1, 22 + g - 1, 48), List.of()) - (emit(new Mesh(), frame(322 + g - 1, 22 + g - 1, 48), List.of(shot)) - emit(new Mesh(), frame(322 + g - 1, 22 + g - 1, 48), List.of())), "no beam before the circle arrives");
        assertEquals(emit(new Mesh(), frame(329 + g, 29 + g, 48), List.of()), emit(new Mesh(), frame(329 + g, 29 + g, 48), List.of(shot)), "beam gone after 8 ticks");
        Mesh half = new Mesh(), start = new Mesh(), end = new Mesh();
        double over = SilentGateSequence.ACTIVATE_TICKS + 200;
        emit(start, frame(480 + over, over, 48), List.of());
        emit(half, frame(480 + over + 150, over + 150, 48), List.of());
        assertEquals(0, emit(end, frame(480 + over + 160, over + 160, 48), List.of()));
        assertTrue(half.vertices.stream().mapToDouble(p -> p[3]).max().orElseThrow()
                < start.vertices.stream().mapToDouble(p -> p[3]).max().orElseThrow());
    }

    // ---- script v3: small circles, the great circle ---------------------------------------------------------------------
    @Test void smallCirclesBloomOnlyBetween420And470() {
        int n = 160, drawn = 0, total = SilentGateSequence.skyTotal(n);
        Vec3 gate = SilentGateSequence.gateCenter(ANCHOR, 0f), eye = new Vec3(0, 130, -120);
        for (int i = 0; i < total; i++) {
            double born = SilentGateSequence.circleBorn(i, n, 7L);
            assertEquals(0, GateMinor.bloom((419.9 - born) / GateMinor.BLOOM_TICKS), 0, "nothing before 420");
            assertEquals(1, GateMinor.bloom((470 - born) / GateMinor.BLOOM_TICKS), 1e-12, "fully grown by 470, no growth after it");
            Vec3 at = SilentGateSequence.skyCircle(ANCHOR, 0f, 7L, i, n);
            for (double age : new double[]{0, 100, 300, 419.9}) {
                GateDraw.Ctx c = ctx(new Mesh(), eye, -1);
                GateMinor.sky(c, at, i, age, born, gate, -1, -1);
                assertEquals(0, c.quads, "circle " + i + " is not drawn at " + age);
            }
            GateDraw.Ctx c = ctx(new Mesh(), eye, -1);
            GateMinor.sky(c, at, i, 471, born, gate, -1, -1);
            if (c.quads > 0) drawn++;
            // After 470 the circle's size is constant: no further bloom in the wait or the barrage.
            for (double age = 471; age < 700; age += 29) assertEquals(1, GateMinor.bloom((age - born) / GateMinor.BLOOM_TICKS), 0);
        }
        assertTrue(drawn > total * .55, "circles are drawn once grown: " + drawn + "/" + total);
        // Ground discs (gold/green behind the caster) are also done by 470.
        for (int i = 0; i < SilentGateSequence.GROUND_RING; i++) {
            GateDraw.Ctx before = ctx(new Mesh(), eye, -1), after = ctx(new Mesh(), eye, -1);
            GateMinor.ground(before, SilentGateSequence.groundCircle(ANCHOR, 0f, i), ANCHOR, i, 419, SilentGateSequence.groundCircleGold(i), gate, -1, -1);
            GateMinor.ground(after, SilentGateSequence.groundCircle(ANCHOR, 0f, i), ANCHOR, i, 470, SilentGateSequence.groundCircleGold(i), gate, -1, -1);
            assertEquals(0, before.quads); assertTrue(after.quads > 0);
        }
    }

    @Test void endingShrinksEachCircleInPlaceAndReversesTheBloom() {
        int n = 160, total = SilentGateSequence.skyTotal(n);
        Vec3 gate = SilentGateSequence.gateCenter(ANCHOR, 0f), eye = new Vec3(0, 130, -120);
        double firstStart = 1e9, lastEnd = 0;
        for (int i = 0; i < total; i++) {
            assertEquals(1, GateMinor.shrink(i, 0), 0); assertEquals(1, GateMinor.shrink(i, 24.9), 0);
            assertEquals(0, GateMinor.shrink(i, 90), 0, "every circle is gone by 90");
            double start = 0; while (GateMinor.shrink(i, start + .01) == 1 && start < 100) start += .01;
            firstStart = Math.min(firstStart, start); lastEnd = Math.max(lastEnd, start + GateMinor.SHRINK_TICKS);
            assertTrue(start >= 24.9 && start + GateMinor.SHRINK_TICKS <= 90.1, "circle " + i + " shrinks inside 25..90: " + start);
            // The reverse of the bloom: the same curve run backwards.
            assertEquals(GateMinor.bloom(.3), GateMinor.shrink(i, start + GateMinor.SHRINK_TICKS * .7), 1e-2);
        }
        assertTrue(lastEnd - firstStart > 40, "staggered over most of the window: " + firstStart + ".." + lastEnd);
        // Position: a circle drawn mid-shrink is centred exactly where it was drawn at full size, only its extent shrinks.
        for (int i : new int[]{5, 40, 77, 120}) {
            Vec3 at = SilentGateSequence.skyCircle(ANCHOR, 0f, 7L, i, n);
            double born = SilentGateSequence.circleBorn(i, n, 7L), e = 25;
            while (GateMinor.shrink(i, e) > .8 && e < 90) e += .25;
            double[] full = extent(i, at, gate, eye, born, 0), part = extent(i, at, gate, eye, born, e);
            assertEquals(full[0], part[0], 1e-3, "x centre unchanged, circle " + i); assertEquals(full[1], part[1], 1e-3, "z centre unchanged");
            assertTrue(part[2] < full[2] * .95, "smaller while shrinking: " + part[2] + " vs " + full[2]);
        }
    }
    private double[] extent(int i, Vec3 at, Vec3 gate, Vec3 eye, double born, double e) {
        Mesh mesh = new Mesh();
        GateMinor.sky(ctx(mesh, eye, 0), at, i, 700, born, gate, -1, e);
        double minX = 1e9, maxX = -1e9, minZ = 1e9, maxZ = -1e9;
        for (float[] p : mesh.vertices) { minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]); minZ = Math.min(minZ, p[2]); maxZ = Math.max(maxZ, p[2]); }
        return new double[]{(minX + maxX) / 2, (minZ + maxZ) / 2, Math.max(maxX - minX, maxZ - minZ)};
    }

    @Test void greatCircleTiersGrowFromTheCentreAndNeverStandStill() {
        int tiers = GateCircles.TIER_COUNT;
        assertEquals(5, tiers);
        // Distinct signed speeds, neighbours turning against each other.
        for (int i = 0; i < tiers; i++) {
            assertTrue(Math.abs(GateCircles.speed(i)) > .1);
            for (int j = i + 1; j < tiers; j++) assertNotEquals(Math.abs(GateCircles.speed(i)), Math.abs(GateCircles.speed(j)), 1e-9, "tiers " + i + " and " + j + " turn at different speeds");
            if (i > 0) assertTrue(GateCircles.speed(i) * GateCircles.speed(i - 1) < 0, "neighbours turn against each other");
        }
        // Rotating at every age from the first tick of the circle until the last tick of the ending, whatever its size.
        for (int i = 0; i < tiers; i++) for (double age = 160; age < 480 + 372; age += 3)
            assertEquals(Math.toRadians(GateCircles.speed(i)) * 3, GateCircles.angle(i, age + 3) - GateCircles.angle(i, age), 1e-12, "tier " + i + " keeps turning at " + age);
        // Growth: inner tiers first, from the centre, all grown by 232; nothing before 160.
        for (int i = 0; i < tiers; i++) assertEquals(0, GateCircles.scale(i, 159.9, -1), 0);
        assertEquals(0, GateCircles.scale(4, 175, -1), 0, "the outer tier has not started");
        assertTrue(GateCircles.scale(0, 175, -1) > GateCircles.scale(1, 175, -1) && GateCircles.scale(1, 175, -1) > 0);
        for (double age = 160; age < 232; age += 4) for (int i = 1; i < tiers; i++) assertTrue(GateCircles.scale(i - 1, age, -1) >= GateCircles.scale(i, age, -1) - 1e-12, "inside out at " + age);
        for (int i = 0; i < tiers; i++) assertEquals(1, GateCircles.scale(i, 232, -1), 1e-12);
        assertEquals(1, GateCircles.scale(4, 480, 100), 1e-12, "untouched until the ending dissolves it");
        // The ending: tiers shrink to the centre, outer first, all gone by 160.
        assertTrue(GateCircles.scale(4, 560, 120) < GateCircles.scale(0, 560, 120));
        for (int i = 0; i < tiers; i++) assertEquals(0, GateCircles.scale(i, 700, 160), 0);
        // Geometry: the mesh really turns from one tick to the next (also while it shrinks) and shrinks to the centre.
        Vec3 gate = SilentGateSequence.gateCenter(ANCHOR, 0f);
        for (double[] ae : new double[][]{{300, -1}, {600, 120}, {640, 140}}) {
            Mesh a = new Mesh(), b = new Mesh();
            GateCircles.great(ctx(a, new Vec3(0, 130, -120), 0), ANCHOR, gate, ae[0], ae[1]);
            GateCircles.great(ctx(b, new Vec3(0, 130, -120), 0), ANCHOR, gate, ae[0] + 1, ae[1] < 0 ? -1 : ae[1] + 1);
            assertEquals(a.vertices.size(), b.vertices.size());
            boolean moved = false;
            for (int k = 0; k < a.vertices.size() && !moved; k++) moved = Math.abs(a.vertices.get(k)[0] - b.vertices.get(k)[0]) > 1e-4 || Math.abs(a.vertices.get(k)[2] - b.vertices.get(k)[2]) > 1e-4;
            assertTrue(moved, "turning at " + ae[0] + "/" + ae[1]);
        }
        double rStart = maxRadius(480, 100, gate), rMid = maxRadius(560, 125, gate), rEnd = maxRadius(600, 150, gate);
        assertTrue(rStart > 150 && rMid < rStart * .9 && rEnd < rMid && rEnd < 120, "dissolves toward the centre: " + rStart + " " + rMid + " " + rEnd);
    }
    private double maxRadius(double age, double e, Vec3 gate) {
        Mesh m = new Mesh(); Vec3 eye = new Vec3(0, 130, -120);
        GateCircles.great(ctx(m, eye, 0), ANCHOR, gate, age, e);
        double r = 0;
        for (float[] p : m.vertices) r = Math.max(r, Math.hypot(p[0] + eye.x - gate.x, p[2] + eye.z - gate.z));
        return r;
    }
}
