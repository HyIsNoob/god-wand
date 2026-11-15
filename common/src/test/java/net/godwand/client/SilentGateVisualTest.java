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
    /** 160 circles, beams firing: no layer (body, glow, shade: each is drawn with its own budget) uses more than 13500 of its 20000 quads, so mana and a full
     *  barrage of beams (drawn last) always fit. */
    @Test void fullSceneLeavesRoomInTheQuadBudget() {
        int peak = 0;
        var shots = new ArrayList<SilentGateRenderer.Shot>();
        for (int i = 0; i < 24; i++) shots.add(new SilentGateRenderer.Shot(i, new Vec3(i, 70, 40), 100 + 320));
        for (int age = 0; age <= 700; age += 5) {
            double command = age < 480 ? -1 : Math.min(age - 480, 372);
            for (int layer = 0; layer <= 2; layer++) {
                for (Vec3 eye : new Vec3[]{Vec3.ZERO, new Vec3(0, 130, 48)}) {
                    int quads = SilentGateRenderer.emitScene(new Mesh(), new Matrix4f(), frame(age, command, 160), shots, eye, layer);
                    peak = Math.max(peak, quads);
                    assertTrue(quads <= 14500, "age " + age + " layer " + layer + " eye " + eye + ": " + quads);
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
    /** No circle moves when it fires: the circles are drawn before the beams, so the scene without shots is an exact prefix of the scene with them. */
    @Test void circlesNeverMoveWhenTheyFire() {
        int n = 220;
        for (double command : new double[]{20, 60, 100, 150}) {
            var f = new SilentGateRenderer.Frame(1, 100, 2, 480 + command, command, ANCHOR, 0f, 7L, n, 200, -1);
            var shots = new ArrayList<SilentGateRenderer.Shot>();
            for (int i = 0; i < n; i += 9) {
                Vec3 home = SilentGateSequence.skyCircle(ANCHOR, 0f, 7L, i, n);
                for (int back = 0; back < 20; back += 4) shots.add(new SilentGateRenderer.Shot(i, new Vec3(home.x + 90 - i, ANCHOR.y, home.z - 60 + i), 100 + 480 + (long) command - back, i % 18 == 0));
            }
            Mesh still = new Mesh(), firing = new Mesh();
            SilentGateRenderer.emitScene(still, new Matrix4f(), f, List.of(), Vec3.ZERO, 0);
            SilentGateRenderer.emitScene(firing, new Matrix4f(), f, shots, Vec3.ZERO, 0);
            assertTrue(firing.vertices.size() > still.vertices.size(), "beams are drawn");
            for (int k = 0; k < still.vertices.size(); k++) assertArrayEquals(still.vertices.get(k), firing.vertices.get(k), 0f, "vertex " + k + " at command " + command);
        }
        // The kill beam fires HEAVY_CHARGE ticks after it is announced; an ordinary one within STAGGER_TICKS, each at its own moment (a running fire, not volleys).
        assertEquals(-SilentGateSequence.HEAVY_CHARGE, SilentGateRenderer.beamLife(new SilentGateRenderer.Shot(3, Vec3.ZERO, 700, true), 700), 0);
        java.util.Set<Long> moments = new java.util.HashSet<>();
        for (int circle = 0; circle < 12; circle++) {
            double life = SilentGateRenderer.beamLife(new SilentGateRenderer.Shot(circle, Vec3.ZERO, 700, false), 700);
            assertTrue(life <= 0 && life > -SilentGateRenderer.STAGGER_TICKS, "held back a little: " + life);
            moments.add(Math.round(life * 20));
        }
        assertTrue(moments.size() >= 6, "the beams of one tick leave at different moments: " + moments);
    }
    @Test void twoHundredFiftySixCirclesStayUnderTheBudgetEvenFromInsideTheField() {
        int peak = 0;
        Vec3 lane = ANCHOR.add(0, 5, 75);
        for (int age = 285; age <= 480; age += 5)
            for (Vec3 eye : new Vec3[]{Vec3.ZERO, lane, SilentGateSequence.doorCenter(ANCHOR, 0f), new Vec3(0, 160, -120)}) {
                for (int layer = 0; layer <= 2; layer++) {
                    int quads = SilentGateRenderer.emitScene(new Mesh(), new Matrix4f(), frame(age, -1, 256), List.of(), eye, layer);
                    peak = Math.max(peak, quads);
                    assertTrue(quads <= 14500, "age " + age + " layer " + layer + " eye " + eye + ": " + quads);
                }
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
            assertTrue(c.quads <= 5200, "eye " + eye + " open " + open + ": " + c.quads);
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
        assertTrue(maxX >= halfW - 3.6 && maxX <= halfW + 10 * k && minX <= -halfW + 3.6 && minX >= -halfW - 10 * k, "outer columns and the folded wings span " + minX + ".." + maxX);
        assertTrue(maxY >= (SilentGateSequence.GATE_HEIGHT + 6) * k && maxY <= (SilentGateSequence.GATE_HEIGHT + 11) * k, "finial top " + maxY);
        assertTrue(minY <= 0 && minY >= -11 * k, "column tips and spike " + minY);
        assertTrue(SilentGateSequence.GATE_INNER_WIDTH == 22 && SilentGateSequence.GATE_INNER_HEIGHT == 44, "door opening 22 x 44");
        // The finials stay under the great circle (altitude 100 above the anchor) when the gate is at rest.
        assertTrue(SilentGateSequence.GATE_BASE + maxY < SilentGateSequence.circleAltitude(0), "finials stay under the great circle");
        assertEquals(4, SilentGateSequence.SCALE);
    }
    @Test void doorLeavesSwingOutTowardsTheCasterWithGateOpen() {
        Vec3 eye = new Vec3(0, 120, -240);
        double[] centre = new double[2]; double[] depth = new double[2];
        for (int k = 0; k < 2; k++) {
            Mesh mesh = new Mesh();
            GateModel.draw(ctx(mesh, eye, 0), POSE, 250, k, 0);
            centre[k] = mesh.vertices.stream().filter(p -> p[1] + eye.y > 68 && p[1] + eye.y < 224 && Math.abs(p[0]) < 12).count();
            depth[k] = mesh.vertices.stream().filter(p -> p[1] + eye.y > 68 && p[1] + eye.y < 224 && Math.abs(p[0]) < 45).mapToDouble(p -> -(p[2] + eye.z)).max().orElse(0);
        }
        assertTrue(centre[0] > 20, "closed leaves meet in the middle: " + centre[0]);
        assertEquals(0, centre[1], "open leaves have left the middle");
        assertTrue(depth[1] > 24 && depth[0] < 12, "open leaves swing out towards the caster (-z): " + depth[1] + " vs " + depth[0]);
        assertEquals(SilentGateSequence.gateOpen(360) < SilentGateSequence.gateOpen(400), true);
    }
    @Test void slitShockwaveAndVeinsAppearAtTheirTicks() {
        Vec3 eye = new Vec3(0, 120, -270);
        int[] slit = new int[2], wave = new int[3];
        for (int k = 0; k < 2; k++) { GateDraw.Ctx c = ctx(new Mesh(), eye, 1); GateFx.slit(c, POSE.at(0, 80, 0), POSE.r(), POSE.d(), k, 100); slit[k] = c.quads; }
        assertEquals(0, slit[0]);
        assertTrue(slit[1] > 30, "slit " + slit[1]);
        double[] ages = {312, 330, 360};
        for (int k = 0; k < 3; k++) { GateDraw.Ctx c = ctx(new Mesh(), eye, 1); GateFx.shockwave(c, POSE, ages[k], 318, 120, 34, 1); wave[k] = c.quads; }
        assertEquals(0, wave[0]); assertTrue(wave[1] > 40, "shockwave " + wave[1]); assertEquals(0, wave[2]);
        assertEquals(0, GateModel.vein(SilentGateSequence.SHOCK - 2, -1, 0), "veins are dark while falling");
        assertTrue(GateModel.vein(SilentGateSequence.SHOCK + 1, -1, 0) > 1, "flare at the shockwave");
        assertTrue(GateModel.vein(400, -1, 0) > .4, "steady glow while waiting");
        assertTrue(GateModel.vein(400, 2, 0) > GateModel.vein(400, -1, 0), "activation flares them");
        // In the scene: the gate is absent before the slit, present after, and the slit itself is drawn from 140.
        Mesh before = new Mesh(), after = new Mesh();
        int a = SilentGateRenderer.emitScene(before, new Matrix4f(), frame(150, -1, 0), List.of(), Vec3.ZERO, 0);
        int b = SilentGateRenderer.emitScene(after, new Matrix4f(), frame(218, -1, 0), List.of(), Vec3.ZERO, 0);
        assertTrue(b - a > 500, "gate body appears: " + a + " -> " + b);
        int g1 = SilentGateRenderer.emitScene(new Mesh(), new Matrix4f(), frame(160, -1, 0), List.of(), Vec3.ZERO, 1);
        int g0 = SilentGateRenderer.emitScene(new Mesh(), new Matrix4f(), frame(135, -1, 0), List.of(), Vec3.ZERO, 1);
        assertTrue(g1 - g0 > 30, "slit glow at 160: " + g0 + " -> " + g1);
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
        assertEquals(0, emit(early, frame(1, -1, 48), List.of()));
        Mesh mesh = new Mesh();
        emit(mesh, frame(175, -1, 48), List.of());
        double target = SilentGateSequence.circleAltitude(ANCHOR.y);
        assertTrue(mesh.vertices.stream().anyMatch(p -> Math.abs(p[1] - (ANCHOR.y + target)) <= 3));
    }
    @Test void groundRingDiscsStandUprightBehindTheCaster() {
        Mesh mesh = new Mesh();
        // Body layer only: the glow layer adds a halo, a pillar of light and drifting motes round the caster.
        SilentGateRenderer.emitScene(mesh, new Matrix4f(), frame(520, -1, 0), List.of(), Vec3.ZERO, 0);
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (float[] p : mesh.vertices) if (p[1] > ANCHOR.y - 1 && p[1] < ANCHOR.y + 8 && Math.hypot(p[0] - ANCHOR.x, p[2] - ANCHOR.z) < 12) { minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]); }
        // Gold disc (r 2.4) centred 3 above the anchor; its spinning square seal tile reaches a little past the circle.
        assertTrue(minY >= ANCHOR.y + 3 - 3.4 && minY <= ANCHOR.y + 3 - 2.0, "min " + minY);
        assertTrue(maxY <= ANCHOR.y + 3 + 3.4 && maxY >= ANCHOR.y + 3 + 2.0, "max " + maxY);
    }
    @Test void beamsLastEightTicksAndTheEndingFadesToNothing() {
        Vec3 home3 = SilentGateSequence.skyCircle(ANCHOR, 0f, 7L, 3, 48);
        var shot = new SilentGateRenderer.Shot(3, new Vec3(home3.x + 40, ANCHOR.y, home3.z), 100 + 320);
        int a = emit(new Mesh(), frame(326, 26, 48), List.of(shot)), b = emit(new Mesh(), frame(326, 26, 48), List.of());
        assertTrue(a - b > 20, "ribbon, muzzle and impact: " + (a - b));
        assertEquals(emit(new Mesh(), frame(319, 19, 48), List.of()), emit(new Mesh(), frame(319, 19, 48), List.of(shot)), "no beam before it is fired");
        // After 16 ticks the beam is gone, soon after its sparks: only the smoke of its impact hangs there (three puffs, dark and lit), until that has thinned out too.
        assertTrue(emit(new Mesh(), frame(329, 29, 48), List.of(shot)) - emit(new Mesh(), frame(329, 29, 48), List.of()) > 6, "sparks still fly at 9 ticks");
        // (and the ground it struck still lit and scorched: see struckGroundGlowsThenKeepsAScorchForAWhile)
        GateDraw.Ctx ground = ctx(new Mesh(), Vec3.ZERO, -1);
        GateBeams.pool(ground, shot.hit(), SilentGateRenderer.beamLife(shot, 440) - GateBeams.FALL_TICKS, GateMinor.JADE, 0, 1);
        assertEquals(6 + ground.quads, emit(new Mesh(), frame(340, 40, 48), List.of(shot)) - emit(new Mesh(), frame(340, 40, 48), List.of()), "beam and sparks gone, smoke left");
        assertEquals(emit(new Mesh(), frame(368, 68, 48), List.of()), emit(new Mesh(), frame(368, 68, 48), List.of(shot)), "smoke gone after " + GateBeams.SMOKE_TICKS);
        // The kill beam: its circle gathers light for HEAVY_CHARGE ticks (something is drawn), then the beam.
        var heavy = new SilentGateRenderer.Shot(3, new Vec3(home3.x + 40, ANCHOR.y, home3.z), 100 + 320, true);
        assertTrue(emit(new Mesh(), frame(328, 28, 48), List.of(heavy)) > emit(new Mesh(), frame(328, 28, 48), List.of()), "charging");
        assertTrue(emit(new Mesh(), frame(338, 38, 48), List.of(heavy)) - emit(new Mesh(), frame(338, 38, 48), List.of()) > 20, "the kill beam");
        Mesh half = new Mesh(), start = new Mesh(), end = new Mesh();
        double over = SilentGateSequence.ACTIVATE_TICKS + 200;
        emit(start, frame(480 + over, over, 48), List.of());
        emit(half, frame(480 + over + SilentGateSequence.ENDING_TICKS - 10, over + SilentGateSequence.ENDING_TICKS - 10, 48), List.of());
        assertEquals(0, emit(end, frame(480 + over + SilentGateSequence.ENDING_TICKS, over + SilentGateSequence.ENDING_TICKS, 48), List.of()));
        assertTrue(half.vertices.stream().mapToDouble(p -> p[3]).max().orElseThrow()
                < start.vertices.stream().mapToDouble(p -> p[3]).max().orElseThrow());
    }

    // ---- small circles, the great circle ---------------------------------------------------------------------------------
    private static int skyQuads(int i, Vec3 at, Vec3 gate, Vec3 eye, double age, double e, int layer) {
        GateDraw.Ctx c = ctx(new Mesh(), eye, layer);
        GateMinor.sky(c, at, i, age, gate, -1, e);
        return c.quads;
    }

    @Test void smallCirclesAreWrittenNotScaledAndOnlyBetween420And470() {
        int n = 160, drawn = 0;
        Vec3 gate = SilentGateSequence.gateCenter(ANCHOR, 0f), eye = new Vec3(0, ANCHOR.y + 3, -60);
        for (int i = 0; i < n; i++) {
            Vec3 at = SilentGateSequence.skyCircle(ANCHOR, 0f, 7L, i, n);
            double born = SilentGateSequence.circleBorn(i);
            for (double age : new double[]{0, 100, 300, 419.9}) assertEquals(0, skyQuads(i, at, gate, eye, age, -1, -1), "circle " + i + " is not drawn at " + age);
            assertEquals(0, GateMinor.formed(i, born, -1), 0);
            assertEquals(1, GateMinor.formed(i, 470, -1), 0, "complete by 470, no change after it");
            for (double age = 471; age < 700; age += 29) assertEquals(1, GateMinor.formed(i, age, -1), 0);
            // First a seed of light falls (glow only), then the rim is drawn before anything else, and the tiers drop out last.
            assertTrue(skyQuads(i, at, gate, eye, born - 5, -1, 1) > 0 && skyQuads(i, at, gate, eye, born - 5, -1, 0) == 0, "the seed falls before the circle is written");
            int early = skyQuads(i, at, gate, eye, born + 3, -1, 0), full = skyQuads(i, at, gate, eye, 471, -1, 0);
            if (full > 0) { drawn++; assertTrue(early > 0 && early < full, "circle " + i + " is written piece by piece: " + early + " of " + full); }
        }
        assertTrue(drawn > n * .55, "circles are drawn once written: " + drawn + "/" + n);
        // The stages: rim first, the seal inside it, then the tiers drop one after the other; every stage is complete at 1.
        assertTrue(GateMinor.rim(.25) > .4 && GateMinor.seal(.25) == 0 && GateMinor.drop(.25, 1) == 0);
        assertTrue(GateMinor.rim(.5) == 1 && GateMinor.drop(.5, 1) > 0 && GateMinor.drop(.5, 2) == 0);
        assertTrue(GateMinor.drop(.9, 1) == 1 && GateMinor.drop(.9, 2) < 1);
        assertEquals(1, GateMinor.rim(1) * GateMinor.seal(1) * GateMinor.drop(1, 1) * GateMinor.drop(1, 2), 0);
        // The size is the same from the first stroke: a half-written rim already has the full radius.
        Vec3 at = SilentGateSequence.skyCircle(ANCHOR, 0f, 7L, 5, n);
        assertEquals(reach(5, at, gate, eye, 700, -1), reach(5, at, gate, eye, SilentGateSequence.circleBorn(5) + 6, -1), 2, "written at full size (the seal's square tile reaches a little past the rim)");
        // Ground discs (gold/green behind the caster) are made after the "shh" (from 132), long before the canopy.
        for (int i = 0; i < SilentGateSequence.GROUND_RING; i++) {
            GateDraw.Ctx before = ctx(new Mesh(), eye, -1), after = ctx(new Mesh(), eye, -1);
            GateMinor.ground(before, SilentGateSequence.groundCircle(ANCHOR, 0f, i), ANCHOR, i, 125, SilentGateSequence.groundCircleGold(i), gate, -1, -1);
            GateMinor.ground(after, SilentGateSequence.groundCircle(ANCHOR, 0f, i), ANCHOR, i, 470, SilentGateSequence.groundCircleGold(i), gate, -1, -1);
            assertEquals(0, before.quads); assertTrue(after.quads > 0);
        }
    }

    @Test void struckGroundGlowsThenKeepsAScorchForAWhile() {
        Vec3 hit = ANCHOR.add(20, 0, 40), eye = ANCHOR.add(0, 30, 0);
        GateDraw.Ctx before = ctx(new Mesh(), eye, 1), lit = ctx(new Mesh(), eye, 1), late = ctx(new Mesh(), eye, 1), gone = ctx(new Mesh(), eye, 1), scorch = ctx(new Mesh(), eye, 2);
        GateBeams.pool(before, hit, -1, GateMinor.JADE, 7, 1);
        GateBeams.pool(lit, hit, 3, GateMinor.JADE, 7, 1);
        GateBeams.pool(late, hit, 30, GateMinor.JADE, 7, 1);
        GateBeams.pool(gone, hit, GateBeams.POOL_TICKS, GateMinor.JADE, 7, 1);
        GateBeams.pool(scorch, hit, 10, GateMinor.JADE, 7, 1);
        assertEquals(0, before.quads); assertEquals(0, gone.quads);
        assertTrue(lit.quads >= 3, "pool, white heart and ember: " + lit.quads);
        assertTrue(late.quads > 0 && late.quads < lit.quads, "the ember outlives the pool: " + late.quads);
        assertEquals(1, scorch.quads, "the burnt mark, in the shade layer");
    }

    @Test void endingUnwritesEachCircleWhereItStands() {
        int n = 160;
        Vec3 gate = SilentGateSequence.gateCenter(ANCHOR, 0f), eye = new Vec3(0, ANCHOR.y + 3, -60);
        double firstStart = 1e9, lastEnd = 0;
        for (int i = 0; i < n; i++) {
            assertEquals(1, GateMinor.formed(i, 700, 0), 0); assertEquals(1, GateMinor.formed(i, 700, 24.9), 0);
            assertEquals(0, GateMinor.formed(i, 700, SilentGateSequence.END_RETRACT_TO), 0, "every circle is gone by the end of the retract");
            double start = GateMinor.unwriteStart(i);
            firstStart = Math.min(firstStart, start); lastEnd = Math.max(lastEnd, start + GateMinor.FORM);
            assertTrue(start >= SilentGateSequence.END_RETRACT_FROM && start + GateMinor.FORM <= SilentGateSequence.END_RETRACT_TO, "circle " + i + " is unwritten inside the retract: " + start);
            // The reverse of the writing: the same stages run backwards.
            assertEquals(.3, GateMinor.formed(i, 700, start + GateMinor.FORM * .7), 1e-9);
        }
        assertTrue(lastEnd - firstStart > 40, "staggered over most of the window: " + firstStart + ".." + lastEnd);
        // Half unwritten, a circle is still exactly where it was: nothing of it lies outside its own disc or travels anywhere.
        int seen = 0;
        for (int i = 0; i < n; i++) {
            Vec3 at = SilentGateSequence.skyCircle(ANCHOR, 0f, 7L, i, n);
            double e = GateMinor.unwriteStart(i) + GateMinor.FORM * .8;
            int full = skyQuads(i, at, gate, eye, 700, 0, 0);
            if (full == 0) continue; // hidden: it overlaps the gate from here
            seen++;
            assertTrue(skyQuads(i, at, gate, eye, 700, e, 0) < full, "circle " + i + " partly unwritten");
            assertTrue(reach(i, at, gate, eye, 700, e) <= reach(i, at, gate, eye, 700, 0) + 1e-6, "inside its own disc");
        }
        assertTrue(seen > n / 2, "most circles are in view: " + seen);
    }
    /** Farthest horizontal distance of the body geometry of circle {@code i} from its centre {@code at}. */
    private double reach(int i, Vec3 at, Vec3 gate, Vec3 eye, double age, double e) {
        Mesh mesh = new Mesh();
        GateMinor.sky(ctx(mesh, eye, 0), at, i, age, gate, -1, e);
        double r = 0;
        for (float[] p : mesh.vertices) {
            r = Math.max(r, Math.hypot(p[0] + eye.x - at.x, p[2] + eye.z - at.z));
            assertTrue(p[1] + eye.y <= at.y + .01 && p[1] + eye.y >= at.y - GateMinor.NOZZLE - 1, "between the cap and the nozzle");
        }
        return r;
    }

    @Test void farGeometryIsPulledInsideTheFarPlaneAlongItsSightLine() {
        double far = 512;
        for (Vec3 q : new Vec3[]{new Vec3(10, 3, 5), new Vec3(300, 100, 100), new Vec3(344, 487, 30), new Vec3(-600, 480, 500), new Vec3(0, 5000, 0)}) {
            Vec3 p = GateDraw.pull(q, far);
            assertTrue(p.length() < far, "inside the far plane: " + p.length());
            assertEquals(0, p.normalize().distanceTo(q.normalize()), 1e-9, "same line of sight");
            if (q.length() <= .7 * far) assertEquals(q, p, "near geometry is untouched");
            assertEquals(q, GateDraw.pull(q, Double.MAX_VALUE), "no far plane, no pull");
        }
        // Depth order is kept: of two points on one sight line the farther stays farther.
        Vec3 dir = new Vec3(.3, .8, .5).normalize();
        double prev = 0;
        for (double d = 100; d < 3000; d += 50) { double len = GateDraw.pull(dir.scale(d), far).length(); assertTrue(len > prev); prev = len; }
    }

    @Test void theCastersOwnCircleIsWrittenAsTheWandSettlesAndTheKillBeamChargesInward() {
        Vec3 eye = ANCHOR.add(3, 4, 6);
        GateDraw.Ctx before = ctx(new Mesh(), eye, -1), part = ctx(new Mesh(), eye, 0), full = ctx(new Mesh(), eye, 0), gone = ctx(new Mesh(), eye, -1);
        GateMinor.aura(before, ANCHOR, 29, -1); GateMinor.aura(part, ANCHOR, 40, -1); GateMinor.aura(full, ANCHOR, 200, -1); GateMinor.aura(gone, ANCHOR, 900, 140);
        assertEquals(0, before.quads); assertEquals(0, gone.quads);
        assertTrue(part.quads > 0 && part.quads < full.quads, "written piece by piece: " + part.quads + " of " + full.quads);
        GateDraw.Ctx none = ctx(new Mesh(), eye, 1), charge = ctx(new Mesh(), eye, 1);
        GateBeams.charge(none, ANCHOR.add(0, 100, 0), 0); GateBeams.charge(charge, ANCHOR.add(0, 100, 0), .6);
        assertEquals(0, none.quads); assertTrue(charge.quads > 10);
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
        for (int i = 0; i < tiers; i++) for (double age = 70; age < 480 + 372; age += 3)
            assertEquals(Math.toRadians(GateCircles.speed(i)) * 3, GateCircles.angle(i, age + 3) - GateCircles.angle(i, age), 1e-12, "tier " + i + " keeps turning at " + age);
        // Formation: inner tiers first, all in place by 142; nothing before 70. A ring is not blown up from the centre: it arrives near its own size.
        for (int i = 0; i < tiers; i++) assertEquals(0, GateCircles.scale(i, 69.9, -1), 0);
        assertEquals(0, GateCircles.scale(4, 85, -1), 0, "the outer tier has not started");
        assertTrue(GateCircles.scale(1, 85, -1) > .85, "a ring appears at its own size, not as a dot");
        for (double age = 70; age < 142; age += 4) for (int i = 1; i < tiers; i++) assertTrue(GateCircles.form(i - 1, age) >= GateCircles.form(i, age), "inside out at " + age);
        for (int i = 0; i < tiers; i++) assertEquals(1, GateCircles.scale(i, 142, -1), 1e-12);
        // Each ring arrives as a steeply tilted hoop and settles level, coming to rest without a jolt; while it settles it turns the way it will go on turning,
        // only faster: the turning never stops and never reverses.
        for (int i = 1; i < tiers; i++) {
            assertTrue(Math.abs(GateCircles.tilt(i, new int[]{76, 84, 94, 104}[i - 1] + 2)) > .5, "tilted when it appears: tier " + i);
            assertEquals(0, GateCircles.tilt(i, 142), 0);
            assertTrue(Math.abs(GateCircles.tilt(i, 141) - GateCircles.tilt(i, 142)) < .01, "comes to rest softly");
        }
        for (int i = 0; i < tiers; i++) for (double age = 70; age < 160; age += 1) {
            double turn = GateCircles.angle(i, age + 1) + GateCircles.spinUp(i, age + 1) - GateCircles.angle(i, age) - GateCircles.spinUp(i, age);
            assertTrue(turn * GateCircles.speed(i) > 0 && Math.abs(turn) >= Math.abs(Math.toRadians(GateCircles.speed(i))) - 1e-12, "tier " + i + " turns one way, never slower than its steady turn, at " + age);
        }
        // Each tier is written while it settles: points of light draw it round, done in the first half; nothing is half-written afterwards.
        for (int i = 1; i < tiers; i++) assertEquals(1, GateCircles.written(i, 142), 1e-12);
        assertTrue(GateCircles.written(4, 115) > 0 && GateCircles.written(4, 115) < 1 && GateCircles.written(4, 104) == 0);
        // And the caster's own circle the same way: one direction from the first frame, the speed only ever falling toward its slow turn.
        double last = Double.MAX_VALUE;
        for (double age = 30; age < 200; age += 1) {
            double turn = GateMinor.auraSpin(age + 1) - GateMinor.auraSpin(age);
            assertTrue(turn >= .01 - 1e-12 && turn <= last + 1e-12, "the caster's circle at " + age);
            last = turn;
        }
        assertTrue(GateMinor.auraSpin(31) - GateMinor.auraSpin(30) > .1, "it starts fast");
        assertTrue(SilentGateSoundscape.near(0) == 1 && SilentGateSoundscape.near(120) < .3 && SilentGateSoundscape.near(500) == 0, "strikes fade with distance");
        assertTrue(SilentGateSoundscape.voiced(.5, 7) && !SilentGateSoundscape.voiced(.1, 7) && SilentGateSoundscape.voiced(.1, 8), "far strikes are thinned out");
        Mesh half = new Mesh(), whole = new Mesh();
        GateCircles.great(ctx(half, new Vec3(0, 130, -120), 0), ANCHOR, SilentGateSequence.gateCenter(ANCHOR, 0f), 116, -1);
        GateCircles.great(ctx(whole, new Vec3(0, 130, -120), 0), ANCHOR, SilentGateSequence.gateCenter(ANCHOR, 0f), 300, -1);
        assertTrue(half.vertices.size() < whole.vertices.size(), "the outer tier is only partly drawn at 116");
        // The thread of light that lights the circle leaves the caster before the seal appears (glow only) and is gone soon after.
        GateDraw.Ctx thread = ctx(new Mesh(), new Vec3(0, 130, -120), 1), none = ctx(new Mesh(), new Vec3(0, 130, -120), 1);
        GateCircles.great(thread, ANCHOR, SilentGateSequence.gateCenter(ANCHOR, 0f), 66, -1);
        GateCircles.great(none, ANCHOR, SilentGateSequence.gateCenter(ANCHOR, 0f), 60, -1);
        assertTrue(thread.quads > 0 && none.quads == 0);
        assertEquals(1, GateCircles.scale(4, 480, 100), 1e-12, "untouched until the ending dissolves it");
        // The ending: tiers shrink to the centre, outer first, all gone by its end.
        assertTrue(GateCircles.scale(4, 560, 242) < GateCircles.scale(0, 560, 242));
        for (int i = 0; i < tiers; i++) assertEquals(0, GateCircles.scale(i, 700, SilentGateSequence.ENDING_TICKS), 0);
        // Geometry: the mesh really turns from one tick to the next (also while it shrinks) and shrinks to the centre.
        Vec3 gate = SilentGateSequence.gateCenter(ANCHOR, 0f);
        for (double[] ae : new double[][]{{300, -1}, {600, 240}, {640, 260}}) {
            Mesh a = new Mesh(), b = new Mesh();
            GateCircles.great(ctx(a, new Vec3(0, 130, -120), 0), ANCHOR, gate, ae[0], ae[1]);
            GateCircles.great(ctx(b, new Vec3(0, 130, -120), 0), ANCHOR, gate, ae[0] + 1, ae[1] < 0 ? -1 : ae[1] + 1);
            assertEquals(a.vertices.size(), b.vertices.size());
            boolean moved = false;
            for (int k = 0; k < a.vertices.size() && !moved; k++) moved = Math.abs(a.vertices.get(k)[0] - b.vertices.get(k)[0]) > 1e-4 || Math.abs(a.vertices.get(k)[2] - b.vertices.get(k)[2]) > 1e-4;
            assertTrue(moved, "turning at " + ae[0] + "/" + ae[1]);
        }
        double rStart = maxRadius(480, 220, gate), rMid = maxRadius(560, 245, gate), rEnd = maxRadius(600, 270, gate);
        assertTrue(rStart > 150 && rMid < rStart * .9 && rEnd < rMid && rEnd < 120, "dissolves toward the centre: " + rStart + " " + rMid + " " + rEnd);
    }
    private double maxRadius(double age, double e, Vec3 gate) {
        Mesh m = new Mesh(); Vec3 eye = new Vec3(0, 130, -120);
        GateCircles.great(ctx(m, eye, 0), ANCHOR, gate, age, e);
        double r = 0;
        for (float[] p : m.vertices) r = Math.max(r, Math.hypot(p[0] + eye.x - gate.x, p[2] + eye.z - gate.z));
        return r;
    }

    @Test
    void doorPanelOrnamentIsSymmetricLineArt() {
        int on = 0;
        for (int y = 0; y < GateAtlas.PANEL_H; y++) for (int x = 0; x < GateAtlas.PANEL_W; x++) {
            assertEquals(GateAtlas.panel(x, y), GateAtlas.panel(GateAtlas.PANEL_W - 1 - x, y), "mirrored at " + x + "," + y);
            if (GateAtlas.panel(x, y)) on++;
        }
        double cover = on / (double) (GateAtlas.PANEL_W * GateAtlas.PANEL_H);
        assertTrue(cover > .15 && cover < .4, "line art, not a slab: " + cover);
        assertTrue(GateAtlas.PIX_PANEL.x() + GateAtlas.PIX_PANEL.w() <= GateAtlas.SIZE && GateAtlas.PIX_PANEL.x() >= GateAtlas.PIX_STONE.x() + GateAtlas.PIX_STONE.w());
    }

    @Test
    void nightIsOnlyShownAndTheTrueTimeComesBack() {
        assertEquals(12000, Math.abs(GateNight.toMidnight(6000)), "noon is half a day from midnight");
        assertEquals(3000, GateNight.toMidnight(15000), "dusk runs forward");
        assertEquals(-5000, GateNight.toMidnight(23000), "late night runs back");
        assertEquals(6000, GateNight.shown(6000, 0));
        assertEquals(18000, GateNight.shown(6000, 1));
        assertEquals(Math.floorMod(GateNight.MIDNIGHT, GateNight.DAY), Math.floorMod(GateNight.shown(1000, 1), GateNight.DAY));
        assertTrue(GateNight.shown(1000, 1) >= 0);
        // The client ticks our written value on by one (or not, with the daylight cycle off): the truth follows; a server packet replaces it.
        assertEquals(6001, GateNight.follow(6000, 18000, 18001));
        assertEquals(6000, GateNight.follow(6000, 18000, 18000));
        assertEquals(7250, GateNight.follow(6000, 18000, 7250));
    }

    @Test
    void wingsFoldOpenPastTheMarkAndFoldAgain() {
        assertEquals(0, GateModel.spread(SilentGateSequence.SHOCK, -1), 1e-9);
        double peak = 0;
        for (double t = SilentGateSequence.SHOCK; t < SilentGateSequence.SHOCK + 22; t += .5) peak = Math.max(peak, GateModel.spread(t, -1));
        assertTrue(peak > 1.02 && peak < 1.2, "overshoot " + peak);
        assertEquals(1, GateModel.spread(SilentGateSequence.SHOCK + 22, -1), 1e-9);
        assertEquals(0, GateModel.spread(900, SilentGateSequence.END_CLOSE + 30), 1e-9, "folded for the climb back");
        // Open wings reach well past the columns, folded ones stay by them.
        Vec3 eye = POSE.at(0, 36, -200);
        double[] reach = new double[2];
        for (int k = 0; k < 2; k++) {
            Mesh mesh = new Mesh();
            GateModel.draw(ctx(mesh, eye, 0), POSE, 400, 0, 0, k);
            for (float[] p : mesh.vertices) reach[k] = Math.max(reach[k], Math.abs(new Vec3(p[0] + eye.x, p[1] + eye.y, p[2] + eye.z).subtract(POSE.foot()).dot(POSE.r())) / POSE.s());
        }
        assertTrue(reach[0] < 30 && reach[1] > 44 && reach[1] < 62, reach[0] + " folded, " + reach[1] + " open");
    }
}
