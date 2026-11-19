package net.godwand.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.godwand.skill.BlueRedRules;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlueRedDrawTest {
    /** Counts nothing, costs nothing: a Mockito mock per vertex made this suite take minutes. */
    static final class Sink implements VertexConsumer {
        public VertexConsumer addVertex(float x, float y, float z) { pos.add(new float[]{x, y, z}); return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        final java.util.List<float[]> uv = new java.util.ArrayList<>(), pos = new java.util.ArrayList<>();
        public VertexConsumer setUv(float u, float v) { uv.add(new float[]{u, v}); return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
    static final Vec3 EYE = new Vec3(0, 65, 0), AT = new Vec3(0, 65, 10), LOOK = new Vec3(0, 0, 1);

    static GateDraw.Ctx ctx(int layer) {
        GateDraw.Ctx c = new GateDraw.Ctx();
        c.v = new Sink(); c.m = new Matrix4f(); c.eye = EYE; c.layer = layer; c.nearMin = .05; c.nearMax = .4;
        return c;
    }

    /** Shader ORB seeds drawn into c (effect 0): each quad's first vertex, decoded as the shader decodes it. */
    static java.util.List<Integer> orbs(GateDraw.Ctx c) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        var uv = ((Sink) c.v).uv;
        for (int i = 0; i < uv.size(); i += 4) {
            float u = uv.get(i)[0], v = uv.get(i)[1];
            if (v >= 2 && (int) Math.floor(v) - 2 == BlueRedDraw.ORB) out.add((int) Math.floor(u));
        }
        return out;
    }

    @Test void blueIsOneBlueEnergyOrb() {
        var body = ctx(0);
        BlueRedDraw.blue(body, AT, false, 30, -1);
        assertEquals(java.util.List.of(0), orbs(body), "one blue orb, calm");
        var ready = ctx(0);
        BlueRedDraw.blue(ready, AT, true, 30, 20);
        assertEquals(java.util.List.of(4), orbs(ready), "the ready Blue is the charged orb");
    }

    @Test void redIsOneRedOrb() {
        var body = ctx(0);
        BlueRedDraw.redOrb(body, AT, .4, 20);
        assertEquals(java.util.List.of(1), orbs(body));
    }

    @Test void purpleSwellsAsOneStormOrbAndBursts() {
        var swell = ctx(0);
        BlueRedDraw.purple(swell, AT, BlueRedRules.PURPLE_MERGE + 5, EYE, LOOK);
        assertTrue(orbs(swell).size() == 1);
        assertEquals(java.util.List.of(2), orbs(swell));
        // Owner, 2026-11-19: the texture blends into the bursting one over a few ticks instead of switching at once.
        var mid = ctx(0);
        BlueRedDraw.purple(mid, AT, BlueRedRules.PURPLE_DETONATE + 3, EYE, LOOK);
        assertTrue(orbs(mid).contains(2 + 4 * 2) && orbs(mid).contains(2 + 4), "blending: " + orbs(mid));
        var blast = ctx(0);
        BlueRedDraw.purple(blast, AT, BlueRedRules.PURPLE_DETONATE + 8, EYE, LOOK);
        assertEquals(java.util.List.of(2 + 4 * 2), orbs(blast), "the bursting orb");
    }

    @Test void purpleGrowsAndDetonatesWithinBudget() {
        int before = 0;
        for (double age = 0; age <= BlueRedRules.PURPLE_CINEMATIC + 10; age += 1) {
            var c = ctx(-1);
            BlueRedDraw.purple(c, AT, age, EYE, LOOK);
            assertTrue(c.quads < SilentGateRenderer.QUAD_BUDGET);
            if (age == BlueRedRules.PURPLE_DETONATE - 1) before = c.quads;
            if (age == BlueRedRules.PURPLE_DETONATE + 2) assertTrue(c.quads > before, "the blast adds the radial lightning");
        }
    }

    @Test void voidShowsBothOrbsMergingOverBlack() {
        var body = ctx(0);
        BlueRedDraw.purpleVoid(body, AT, BlueRedRules.PURPLE_SWIRL_END + 15, AT.add(0, 0, -10), LOOK);
        assertTrue(body.quads > 20, "backdrop, smoke and the two orbs: " + body.quads);
        var seeds = orbs(body);
        assertTrue(seeds.contains(3) && seeds.contains(3 + 4), "two textured spheres, Red drifting one way and Blue the other: " + seeds);
        var merged = ctx(0);
        // Owner, 2026-11-19: Purple is there, whole, the instant their rims touch (two ticks before MERGE), not grown over them.
        BlueRedDraw.purpleVoid(merged, AT, BlueRedDraw.voidTouch(), AT.add(0, 0, -10), LOOK);
        assertTrue(orbs(merged).contains(2 + 4), "Purple at the touch");
        var before = ctx(0);
        BlueRedDraw.purpleVoid(before, AT, BlueRedDraw.voidTouch() - 1, AT.add(0, 0, -10), LOOK);
        assertFalse(orbs(before).contains(2 + 4), "not before it");
    }

    static float[][] mesh(java.util.function.Consumer<GateDraw.Ctx> draw) {
        var c = ctx(-1);
        draw.accept(c);
        return ((Sink) c.v).pos.toArray(new float[0][]);
    }

    static double maxShift(float[][] a, float[][] b) {
        assertEquals(a.length, b.length, "same geometry from one instant to the next");
        double m = 0;
        for (int i = 0; i < a.length; i++) m = Math.max(m, Math.hypot(Math.hypot(a[i][0] - b[i][0], a[i][1] - b[i][1]), a[i][2] - b[i][2]));
        return m;
    }

    @Test void lightningCrawlsInsteadOfPopping() {
        Vec3 a = AT, b = AT.add(6, 2, 0);
        float[][] now = mesh(c -> BlueRedDraw.liveBolt(c, a, b, 8, .3, .05, BlueRedDraw.ORCHID, 1, 77, 100, .8));
        float[][] next = mesh(c -> BlueRedDraw.liveBolt(c, a, b, 8, .3, .05, BlueRedDraw.ORCHID, 1, 77, 100.05, .8));
        assertTrue(maxShift(now, next) < .1, "a twentieth of a tick moves the bolt only a little: " + maxShift(now, next));
    }

    @Test void sameInstantSameGeometry() {
        for (double age : new double[]{30, BlueRedRules.PURPLE_SWIRL_END + 20, BlueRedRules.PURPLE_MERGE + 20, BlueRedRules.PURPLE_DETONATE + 5}) {
            float[][] a = mesh(c -> { BlueRedDraw.purple(c, AT, age, EYE, LOOK); BlueRedDraw.purpleVoid(c, AT, age, EYE, LOOK); });
            float[][] b = mesh(c -> { BlueRedDraw.purple(c, AT, age, EYE, LOOK); BlueRedDraw.purpleVoid(c, AT, age, EYE, LOOK); });
            assertEquals(0, maxShift(a, b), 1e-9, "age " + age);
        }
        float[][] r1 = mesh(c -> BlueRedDraw.redOrb(c, AT, .5, 33.3)), r2 = mesh(c -> BlueRedDraw.redOrb(c, AT, .5, 33.3));
        assertEquals(0, maxShift(r1, r2), 1e-9);
    }

    @Test void purpleSizeNeverJumps() {
        double prev = BlueRedDraw.purpleSize(BlueRedRules.PURPLE_MERGE);
        assertEquals(6, prev, .01, "picks up from the size the void leaves it at");
        for (double age = BlueRedRules.PURPLE_MERGE + .25; age < BlueRedRules.PURPLE_CINEMATIC; age += .25) {
            double s = BlueRedDraw.purpleSize(age);
            // It only grows, swelling before the burst (owner, 2026-11-19: grow then blow, never shrink).
            assertTrue(s >= prev - 1e-9 && s - prev < 1.6, "age " + age + ": " + prev + " -> " + s);
            prev = s;
        }
    }

    @Test void blueAndRedDrawDifferentShapes() {
        var blue = ctx(-1);
        BlueRedDraw.blue(blue, AT, true, 30, -1);
        var red = ctx(-1);
        BlueRedDraw.redOrb(red, AT, 2, 30);
        java.util.Set<Integer> b = effects(blue), r = effects(red);
        // Both are painted masses now (owner's reset, 2026-11-18); which way Red streams is checked by redSmearsLeanBack.
        // Owner, 2026-11-18: Blue is a sphere wrapped in soft plasma, with no debris streaks (no thin pointed lines).
        // Owner, 2026-11-19: no more bands; Blue is a sphere pouring dust.
        assertTrue(b.contains(BlueRedDraw.ORB) && b.contains(BlueRedDraw.SMOKE) && !b.contains(BlueRedDraw.DEBRIS), "Blue: sphere and dust, no debris " + b);
        // Owner, 2026-11-18: Red is a red plasma mass with torn ink (brush), no flying object-like fragments.
        assertTrue(r.contains(BlueRedDraw.PLASMA) && r.contains(BlueRedDraw.BRUSH) && !r.contains(BlueRedDraw.DEBRIS), "Red: plasma mass, torn ink, no debris " + r);
    }

    /** Centres of the DEBRIS quads drawn into c, relative to the camera. */
    static java.util.List<double[]> debrisCentres(GateDraw.Ctx c) {
        var sink = (Sink) c.v;
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (int i = 0; i + 3 < sink.uv.size(); i += 4) {
            float v = sink.uv.get(i)[1];
            if (v < 2 || (int) Math.floor(v) - 2 != BlueRedDraw.DEBRIS) continue;
            double x = 0, y = 0, z = 0;
            for (int k = 0; k < 4; k++) { x += sink.pos.get(i + k)[0]; y += sink.pos.get(i + k)[1]; z += sink.pos.get(i + k)[2]; }
            out.add(new double[]{x / 4, y / 4, z / 4, Math.floor(sink.uv.get(i)[0])});
        }
        return out;
    }

    /** Fraction of debris pieces (matched by seed) that moved towards (inward) the core between two instants. */
    static double inwardShare(java.util.function.BiConsumer<GateDraw.Ctx, Double> draw, double age) {
        Vec3 core = AT.subtract(EYE);
        int in = 0, n = 0;
        // Pooled over a few instants: Red keeps only a handful of flung fragments.
        for (double t0 = age; t0 < age + 30; t0 += 10) {
            var a = ctx(0); draw.accept(a, t0);
            var b = ctx(0); draw.accept(b, t0 + 1);
            java.util.Map<Double, double[]> before = new java.util.HashMap<>();
            for (double[] p : debrisCentres(a)) before.put(p[3], p);
            for (double[] p : debrisCentres(b)) {
                double[] q = before.get(p[3]);
                if (q == null) continue;
                double d0 = core.distanceTo(new Vec3(q[0], q[1], q[2])), d1 = core.distanceTo(new Vec3(p[0], p[1], p[2]));
                if (Math.abs(d1 - d0) < .05) continue; // the core trembles a little; judge only pieces that really moved
                n++;
                if (d1 < d0) in++;
            }
        }
        assertTrue(n >= 6, "enough moving pieces to judge: " + n);
        return in / (double) n;
    }

    /** Red's energy streams one way (owner, 2026-11-18): its smears lean towards {@code back}, so the mass reads as surging the other way. */
    @Test void redSmearsLeanBack() {
        Vec3 back = new Vec3(1, 0, 0);
        var c = ctx(0);
        BlueRedDraw.redOrb(c, AT, 1.5, 40, back, 1);
        var sink = (Sink) c.v;
        double sum = 0; int n = 0;
        Vec3 core = AT.subtract(EYE);
        for (int i = 0; i < sink.uv.size(); i++) {
            float[] uv = sink.uv.get(i);
            if (uv[1] < 2 || (int) Math.floor(uv[1]) - 2 != BlueRedDraw.PLASMA) continue;
            float[] q = sink.pos.get(i);
            sum += new Vec3(q[0], q[1], q[2]).subtract(core).dot(back); n++;
        }
        assertTrue(n > 0 && sum / n > .2, "smears lean back: " + (n == 0 ? 0 : sum / n));
    }

    /** Owner, 2026-11-18: contact, an orbit that speeds up hard over several turns, braking into a face-off on opposite sides, held. */
    @Test void convergenceHasItsBeats() {
        int b = BlueRedRules.PURPLE_BLINK, ct = BlueRedRules.PURPLE_CONTACT, pl = BlueRedRules.PURPLE_PLAY, le = BlueRedRules.PURPLE_LIFT_END,
                fa = BlueRedRules.PURPLE_FACE, se = BlueRedRules.PURPLE_SWIRL_END;
        assertTrue(BlueRedDraw.separation(ct) > BlueRedDraw.separation(b) + 1, "contact: they knock apart");
        assertTrue(BlueRedDraw.spin(le - 2) > 3 * BlueRedDraw.spin((ct + pl) / 2.0), "the spin climbs hard");
        assertTrue(BlueRedDraw.spin(le - 2) > .6, "fast at the peak: " + BlueRedDraw.spin(le - 2));
        double turns = BlueRedDraw.orbitAngle(fa) / (2 * Math.PI);
        assertTrue(turns > 3, "several turns, not one or two: " + turns);
        assertTrue(BlueRedDraw.spin(fa + 1) < .15 * BlueRedDraw.spin(le), "braked hard, coasting into the face-off");
        assertTrue(BlueRedDraw.spin(fa + 1) > 0, "still turning a little: no sudden stop");
        assertEquals(0, BlueRedDraw.spin(BlueRedDraw.FACE_REST), 1e-9, "at rest once the black has fallen");
        Vec3[] o = BlueRedDraw.convergeOffsets(se - 1);
        assertTrue(o[0].distanceTo(o[1]) > 2.5, "apart, facing each other: " + o[0].distanceTo(o[1]));
        assertTrue(o[1].subtract(o[0]).normalize().dot(BlueRedDraw.faceAxis()) > .95, "on the face-off line, Red on +axis");
        double maxStep = 0;
        for (double age = b; age < se; age += .5) maxStep = Math.max(maxStep, BlueRedDraw.convergeOffsets(age)[1].distanceTo(BlueRedDraw.convergeOffsets(age + .5)[1]));
        assertTrue(maxStep < 3.5, "no teleport, only fast motion: " + maxStep);
    }

    /** Owner's ref 40: while they chase, each orb drags a long ring of its own colour round the orbit, Red's as long as Blue's. */
    @Test void bothOrbsDrawLongRings() {
        for (double age : new double[]{80, 86, 90}) {
            GateDraw.Ctx c = ctx(0);
            BlueRedDraw.purple(c, AT, age, EYE, LOOK);
            var s = (Sink) c.v;
            double[] span = new double[2];
            for (int which = 0; which < 2; which++) {
                double minX = 1e9, maxX = -1e9, minZ = 1e9, maxZ = -1e9;
                for (int i = 0; i < s.uv.size(); i++) {
                    float[] uv = s.uv.get(i);
                    if (uv[1] < 2 || (int) Math.floor(uv[1]) - 2 != BlueRedDraw.PLASMA || (int) Math.floor(uv[0]) != 3550 + which) continue;
                    float[] p = s.pos.get(i);
                    minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]); minZ = Math.min(minZ, p[2]); maxZ = Math.max(maxZ, p[2]);
                }
                span[which] = Math.max(maxX - minX, maxZ - minZ);
            }
            // Well beyond a ribbon's own width: an arc round the wide orbit, not a blob at the head.
            // As wide as the orbit itself (it tightens as the spin climbs).
            assertTrue(span[0] > 2 && span[1] > 2, "age " + age + ": blue ring " + span[0] + ", red ring " + span[1]);
        }
    }

    static java.util.Set<Integer> effects(GateDraw.Ctx c) {
        java.util.Set<Integer> out = new java.util.TreeSet<>();
        for (float[] uv : ((Sink) c.v).uv) if (uv[1] >= 2) out.add((int) Math.floor(uv[1]) - 2);
        return out;
    }

    @Test void voidFadesQuicklyInAndOutInsteadOfCutting() {
        assertEquals(0, BlueRedDraw.voidAlpha(BlueRedRules.PURPLE_SWIRL_END - 1), 1e-9);
        assertEquals(1, BlueRedDraw.voidAlpha(BlueRedRules.PURPLE_SWIRL_END + 14), 1e-9);
        double out = BlueRedDraw.voidAlpha((BlueRedRules.PURPLE_MERGE + BlueRedRules.PURPLE_VOID_END) / 2.0);
        assertTrue(out > 0 && out < 1);
        assertEquals(0, BlueRedDraw.voidAlpha(BlueRedRules.PURPLE_VOID_END), 1e-9);
    }

    @Test void redImpactFramesAlternateBlackAndRed() {
        assertEquals(BlueRedDraw.INK, BlueRedDraw.impactColor(0));
        assertEquals(BlueRedDraw.RED_BODY, BlueRedDraw.impactColor(1));
        assertEquals(BlueRedDraw.INK, BlueRedDraw.impactColor(2));
        assertTrue(BlueRedDraw.impactAlpha(0) > .8 && BlueRedDraw.impactAlpha(6) == 0);
        // Owner, 2026-11-18: Red's frames are a light tint and a flash at the hit for two frames, so the target stays visible; Purple keeps the full frames.
        var red = ctx(-1);
        BlueRedDraw.impact(red, AT, 0, EYE, LOOK);
        assertTrue(red.quads >= 2 && red.quads < 6, "red: " + red.quads);
        var gone = ctx(-1);
        BlueRedDraw.impact(gone, AT, 2, EYE, LOOK);
        assertEquals(0, gone.quads);
        var purple = ctx(-1);
        BlueRedDraw.impact(purple, AT, 0, EYE, LOOK, true);
        assertTrue(purple.quads > 10);
    }

    @Test void flightAndBurstsDraw() {
        var c = ctx(-1);
        BlueRedDraw.redFlight(c, Vec3.ZERO, new Vec3(0, 0, 1), new Vec3(0, 0, 30), 10, 1, 7, true);
        BlueRedDraw.burst(c, AT, 4, true);
        BlueRedDraw.burst(c, AT, 4, false);
        assertTrue(c.quads > 50);
    }
}
