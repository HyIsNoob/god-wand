package net.godwand.client;

import net.godwand.client.GateDraw.Ctx;
import net.godwand.client.GateFx.Pose;
import net.godwand.skill.SilentGateSequence;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static net.godwand.client.GateDraw.clamp01;
import static net.godwand.client.GateDraw.smooth;

/**
 * The divine gate, sculpted from quads and shaded by normal (no light engine): two spinning-top columns, a bevelled frame with a crown,
 * two scroll hooks, two door leaves with raised borders and engraved panels, and a spike hanging underneath with two small scrolls.
 * White porcelain, cool shadow, darker towards the foot and underneath; edges catch a thin additive rim light and the engraved lines
 * (atlas strips) light up as glowing veins. The body is alpha blended; only rims, veins and light are additive.
 */
final class GateModel {
    private GateModel() {}
    static final int PORCELAIN = 0xEEF2F8, SHADOW = 0x6A7892, ENGRAVE = 0x44526C, RIM = 0xA8DFFF, VEIN = 0x7FE8FF, WHITE = 0xFFFFFF;
    private static final double BODY_ALPHA = .96, TAU = Math.PI * 2;
    /** Column axis from the centre line: outer edge GATE_WIDTH/2 minus the 4.5 reach of the capital. */
    static final double COLUMN_X = SilentGateSequence.GATE_WIDTH / 2.0 - 4.5, JAMB_X = SilentGateSequence.GATE_INNER_WIDTH / 2.0 - 1.2;
    static final double OPENING_Y0 = 15, OPENING_Y1 = OPENING_Y0 + SilentGateSequence.GATE_INNER_HEIGHT, TOP_Y = SilentGateSequence.GATE_HEIGHT;
    /** Radius profile of a column, tip first. Bands (engraved inlay) sit on rows 19-23, 30-32.5 and 40-44. */
    private static final double[] COL_Y = {0, 1.2, 3, 5, 7.5, 9.5, 11.5, 13, 14, 14.4, 15.4, 15.8, 17, 18.4, 19, 23, 23.6, 26, 29.4, 30, 32.5, 33.1, 36, 39.4, 40, 44, 44.6, 50, 56, 57, 58.5, 59.5, 60.5, 62.5, 64, 65, 66.5, 68.5, 71, 74},
            COL_R = {0, .5, 1.5, 2.9, 4, 4.2, 3.6, 2.8, 2.6, 3.2, 3.2, 2.6, 2.5, 2.5, 3.3, 3.3, 2.5, 2.4, 2.4, 3.1, 3.1, 2.4, 2.4, 2.4, 3.1, 3.1, 2.4, 2.4, 2.4, 2.9, 3.1, 2.7, 3.6, 4, 3.4, 2.4, 1.2, .8, .45, 0};
    private static final Band[] COL_BANDS = {new Band(19, 23, GateAtlas.FILIGREE), new Band(30, 32.5, GateAtlas.RUNES), new Band(40, 44, GateAtlas.FILIGREE)};
    private static final double[] SPIKE_Y = {-10, -4, 0, 3, 6, 7, 8.5, 10, 11, 12}, SPIKE_R = {0, .35, .55, .8, 1.0, 1.45, 1.2, 1.6, 2.1, .9};
    private static final double[] SPIRE_Y = {68.8, 69.8, 71.3, 72.7, 74}, SPIRE_R = {1.7, 1.3, .85, .4, 0};

    private record Band(double y0, double y1, GateAtlas.Region region) {}
    /** Rigid piece frame: local (x, y, z) to world. */
    private record Basis(Vec3 o, Vec3 x, Vec3 y, Vec3 z, double s) {
        Vec3 p(double a, double b, double c) { return o.add(x.scale(a * s)).add(y.scale(b * s)).add(z.scale(c * s)); }
    }
    private record Piece(Vec3 at, Runnable run) {}

    /** Glow of the veins: dark while falling, a blue flare at the shockwave, then a steady beat; every cast activation flares them once more. */
    static double vein(double age, double commandAge, double endK) {
        double v = 0;
        if (age >= 206) {
            double t = age - 206;
            v = 1.2 * Math.exp(-t / 10) + smooth(t / 20) * (.72 + .28 * Math.sin(t * TAU / 20));
        }
        if (commandAge >= 0) v += .9 * Math.exp(-commandAge / 10);
        return Math.min(1.4, v) * (1 - smooth(endK / .5));
    }

    /** Draws one gate whose foot (lowest column tip) is {@code pose.foot()}; {@code open} 0..1 swings the leaves, {@code vein} 0..1.4 lights the inlay. */
    static void draw(Ctx c, Pose pose, double age, double open, double vein) {
        Pen g = new Pen(c, pose, vein);
        double angle = open * 1.45;
        Vec3 mid = pose.at(0, 36, 0);
        double dist = c.eye.distanceTo(mid);
        g.sides = dist < 90 * pose.s() ? 12 : dist < 170 * pose.s() ? 10 : 8;
        List<Piece> pieces = new ArrayList<>();
        pieces.add(new Piece(pose.at(-COLUMN_X, 36, 0), () -> g.column(-COLUMN_X)));
        pieces.add(new Piece(pose.at(COLUMN_X, 36, 0), () -> g.column(COLUMN_X)));
        pieces.add(new Piece(pose.at(0, 13.5, 0), g::sill));
        pieces.add(new Piece(pose.at(0, 37, 0), g::jambs));
        pieces.add(new Piece(pose.at(0, 66, 0), g::crown));
        pieces.add(new Piece(pose.at(0, 3, 0), g::spike));
        pieces.add(new Piece(pose.at(-7, 68, 0), () -> g.bigHook(-1)));
        pieces.add(new Piece(pose.at(7, 68, 0), () -> g.bigHook(1)));
        for (int side = -1; side <= 1; side += 2) {
            int s = side;
            pieces.add(new Piece(pose.at(s * JAMB_X / 2, 37, 0), () -> g.leaf(s, angle)));
        }
        // Far to near, so what is behind is blended first.
        pieces.sort(Comparator.comparingDouble((Piece p) -> p.at().distanceToSqr(c.eye)).reversed());
        for (Piece p : pieces) p.run().run();
        GateFx.light(c, pose, open, angle, age);
    }

    private static final class Pen {
        final Ctx c; final Pose pose; final double vein; final Vec3 light; int sides = 12;
        Pen(Ctx c, Pose pose, double vein) {
            this.c = c; this.pose = pose; this.vein = vein;
            // Light from the great circle above and a little in front of the gate.
            light = pose.r().scale(.25).add(pose.u().scale(.8)).add(pose.d().scale(-.45)).normalize();
        }

        // ---- shading: Minecraft-style stepped faces --------------------------------------------------------------------
        /** Flat face colour: top 100%, north/south 80%, east/west 60%, bottom 50% (in the gate's own axes), three bands darker towards the foot. */
        int step(Vec3 n, double groove, double y) {
            double ny = n.dot(pose.u()), nx = Math.abs(n.dot(pose.r())), nz = Math.abs(n.dot(pose.d()));
            double f = ny > .5 ? 1 : ny < -.5 ? .5 : nz >= nx ? .8 : .6;
            double h = (y - pose.foot().y) / (46 * pose.s());
            double t = f * (h < .33 ? .82 : h < .66 ? .92 : 1);
            if (groove > .5) t *= .82;
            return mix(SHADOW, PORCELAIN, clamp01((t - .4) / .6));
        }
        static int mix(int a, int b, double t) {
            int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t),
                    bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
            return r << 16 | g << 8 | bl;
        }

        // ---- pixel texture mapping: one texel is one block ------------------------------------------------------------
        /** Texel coordinates (u, v per corner) of a face by projecting its blocks on the two gate axes its normal faces least. */
        double[] project(Vec3[] p, Vec3 n) {
            double ny = Math.abs(n.dot(pose.u())), nx = Math.abs(n.dot(pose.r())), nz = Math.abs(n.dot(pose.d()));
            double[] t = new double[8];
            for (int i = 0; i < 4; i++) {
                Vec3 q = p[i].subtract(pose.foot());
                double x = q.dot(pose.r()), y = q.dot(pose.u()), z = q.dot(pose.d());
                if (ny >= nx && ny >= nz) { t[2 * i] = x; t[2 * i + 1] = z; }
                else if (nx > nz) { t[2 * i] = z; t[2 * i + 1] = y; }
                else { t[2 * i] = x; t[2 * i + 1] = y; }
            }
            return t;
        }
        private static double range(double[] t, int off) {
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (int i = 0; i < 4; i++) { lo = Math.min(lo, t[2 * i + off]); hi = Math.max(hi, t[2 * i + off]); }
            return hi - lo;
        }
        /** Atlas coordinates of the texel corners, relative to the tile the face starts in. */
        static float[] uvFor(GateAtlas.Region r, double[] t) {
            double bu = Double.MAX_VALUE, bv = Double.MAX_VALUE;
            for (int i = 0; i < 4; i++) { bu = Math.min(bu, t[2 * i]); bv = Math.min(bv, t[2 * i + 1]); }
            bu = Math.floor(bu / GateAtlas.TILE) * GateAtlas.TILE; bv = Math.floor(bv / GateAtlas.TILE) * GateAtlas.TILE;
            float[] uv = new float[8];
            for (int i = 0; i < 4; i++) { uv[2 * i] = GateAtlas.pixU(r, t[2 * i] - bu); uv[2 * i + 1] = GateAtlas.pixV(r, t[2 * i + 1] - bv); }
            return uv;
        }
        interface Cell { void emit(Vec3[] p, double[] t); }
        /** Hands the quad over whole when it spans at most 28 texels each way, else in a grid of cells that do (positions and texels interpolated). */
        static void cells(Vec3[] p, double[] t, Cell cell) {
            int nu = Math.max(1, (int) Math.ceil(range(t, 0) / 28)), nv = Math.max(1, (int) Math.ceil(range(t, 1) / 28));
            if (nu == 1 && nv == 1) { cell.emit(p, t); return; }
            for (int i = 0; i < nu; i++) for (int j = 0; j < nv; j++) {
                double[] a = {(double) i / nu, (double) (i + 1) / nu, (double) (i + 1) / nu, (double) i / nu}, b = {(double) j / nv, (double) j / nv, (double) (j + 1) / nv, (double) (j + 1) / nv};
                Vec3[] cp = new Vec3[4]; double[] ct = new double[8];
                for (int k = 0; k < 4; k++) {
                    double w0 = (1 - a[k]) * (1 - b[k]), w1 = a[k] * (1 - b[k]), w2 = a[k] * b[k], w3 = (1 - a[k]) * b[k];
                    cp[k] = p[0].scale(w0).add(p[1].scale(w1)).add(p[2].scale(w2)).add(p[3].scale(w3));
                    ct[2 * k] = t[0] * w0 + t[2] * w1 + t[4] * w2 + t[6] * w3; ct[2 * k + 1] = t[1] * w0 + t[3] * w1 + t[5] * w2 + t[7] * w3;
                }
                cell.emit(cp, ct);
            }
        }

        /** Emits one stepped-shade stone quad (body) and its additive highlight; {@code rim} 0 none, 1 silhouette glow, 2 edge light. False when it faces away. */
        boolean face(Vec3[] p, Vec3[] n, double[] groove, int rim) { return face(p, n, groove, rim, null); }

        boolean face(Vec3[] p, Vec3[] n, double[] groove, int rim, double[] texels) {
            Vec3 ctr = p[0].add(p[1]).add(p[2]).add(p[3]).scale(.25), v = c.eye.subtract(ctr);
            double dist = v.length();
            Vec3 nm = n[0].add(n[1]).add(n[2]).add(n[3]);
            if (dist < 1e-6 || nm.dot(v) < -.16 * nm.length() * dist) return false;
            if (c.body()) {
                Vec3 flat = nm.normalize();
                double g = 0;
                if (groove != null) for (double x : groove) g = Math.max(g, x);
                int col = step(flat, g, ctr.y);
                cells(p, texels != null ? texels : project(p, flat), (cp, ct) ->
                        GateDraw.shaded(c, cp, new int[]{col, col, col, col}, new double[]{BODY_ALPHA, BODY_ALPHA, BODY_ALPHA, BODY_ALPHA}, uvFor(GateAtlas.PIX_STONE, ct)));
            }
            if (c.glow() && rim > 0) {
                double[] a = new double[4]; double top = 0;
                int[] col = {RIM, RIM, RIM, RIM};
                for (int i = 0; i < 4; i++) {
                    double lit = clamp01(.4 + .6 * n[i].dot(light));
                    if (rim == 1) {
                        double ndv = Math.max(0, n[i].dot(c.eye.subtract(p[i]).normalize()));
                        double e = smooth((.42 - ndv) / .42); a[i] = .5 * lit * e * e;
                    } else a[i] = .5 * lit * lit;
                    top = Math.max(top, a[i]);
                }
                if (top > .02) GateDraw.shaded(c, p, col, a, null, 0, 1, 0, 1);
            }
            return true;
        }

        // ---- surfaces of revolution ----------------------------------------------------------------------------------
        void column(double cx) { lathe(cx, 0, COL_Y, COL_R, sides, 14, 56, COL_BANDS, 1); bulbRing(cx); }

        /** Glowing ring round the column's bulb and a halo that carries at long range; a faint glow at rest, full with the veins. */
        void bulbRing(double cx) {
            if (!c.glow()) return;
            double lit = .25 + .75 * Math.min(1, vein), y = 9.2;
            int n = 20;
            for (int i = 0; i < n; i++) {
                double a0 = TAU * i / n, a1 = TAU * (i + 1) / n;
                Vec3[] in = {pose.at(cx + 4.1 * Math.cos(a0), y, 4.1 * Math.sin(a0)), pose.at(cx + 4.1 * Math.cos(a1), y, 4.1 * Math.sin(a1))};
                Vec3[] out = {pose.at(cx + 7.5 * Math.cos(a0), y, 7.5 * Math.sin(a0)), pose.at(cx + 7.5 * Math.cos(a1), y, 7.5 * Math.sin(a1))};
                GateDraw.gradient(c, in[0], in[1], out[1], out[0], VEIN, .8 * lit, .8 * lit, 0, 0);
            }
            GateDraw.sprite(c, pose.at(cx, y, 0), 9 * pose.s(), .3 * lit, VEIN);
        }
        void spike() {
            lathe(0, 0, SPIKE_Y, SPIKE_R, Math.min(sides, 8), 1e9, 1e9, new Band[0], 1);
            smallHook(-1); smallHook(1);
        }

        /** Profile snapped to whole blocks with a flat step wherever the radius changes, so the silhouette climbs in stairs. */
        double[][] stairs(double[] ys, double[] rs) {
            double k = pose.s();
            java.util.List<double[]> out = new java.util.ArrayList<>();
            out.add(new double[]{Math.round(ys[0] * k) / k, Math.round(rs[0] * k) / k});
            for (int i = 1; i < ys.length; i++) {
                double y = Math.round(ys[i] * k) / k, r = Math.round(rs[i] * k) / k;
                double[] last = out.get(out.size() - 1);
                if (y == last[0] && r == last[1]) continue;
                if (y != last[0] && r != last[1]) out.add(new double[]{y, last[1]});
                out.add(new double[]{y, r});
            }
            double[][] t = new double[2][out.size()];
            for (int i = 0; i < out.size(); i++) { t[0][i] = out.get(i)[0]; t[1][i] = out.get(i)[1]; }
            return t;
        }

        void lathe(double cx, double cz, double[] ys0, double[] rs0, int sides, double fy0, double fy1, Band[] bands, int rim) {
            double[][] prof = stairs(ys0, rs0);
            double[] ys = prof[0], rs = prof[1];
            int rows = ys.length;
            Vec3[][] P = new Vec3[rows][sides + 1];
            for (int i = 0; i < rows; i++) {
                boolean fluted = fluted(ys[i], fy0, fy1);
                for (int s = 0; s <= sides; s++) {
                    double phi = TAU * (s % sides) / sides, cs = Math.cos(phi), sn = Math.sin(phi);
                    double r = rs[i] * (1 - .085 * (fluted && s % 2 == 0 ? 1 : 0));
                    P[i][s] = pose.at(cx + r * cs, ys[i], cz + r * sn);
                }
            }
            double circumference = TAU * 2.5 * pose.s();
            for (int i = 0; i + 1 < rows; i++) {
                double dy = ys[i + 1] - ys[i], dr = rs[i + 1] - rs[i], len = Math.hypot(dy, dr);
                if (len < 1e-9) continue;
                double nr = dy / len, ny = -dr / len;
                for (int s = 0; s < sides; s++) {
                    double mid = TAU * (s + .5) / sides;
                    Vec3 nn = pose.r().scale(nr * Math.cos(mid)).add(pose.u().scale(ny)).add(pose.d().scale(nr * Math.sin(mid))).normalize();
                    Vec3[] p = {P[i][s], P[i][s + 1], P[i + 1][s + 1], P[i + 1][s]}, n = {nn, nn, nn, nn};
                    double qg = fluted(ys[i], fy0, fy1) && s % 2 == 0 ? 1 : 0;
                    double u0 = circumference * s / sides, u1 = circumference * (s + 1) / sides, v0 = ys[i] * pose.s(), v1 = ys[i + 1] * pose.s();
                    double[] texels = {u0, v0, u1, v0, u1, v1, u0, v1};
                    if (!face(p, n, new double[]{qg, qg, qg, qg}, rim, texels)) continue;
                    for (Band band : bands) {
                        if (ys[i] < band.y0() - .34 || ys[i + 1] > band.y1() + .34) continue;
                        Vec3[] q = new Vec3[4];
                        for (int k = 0; k < 4; k++) q[k] = p[k].add(n[k].scale(.05 * pose.s()));
                        inlay(q, texels);
                    }
                }
            }
        }
        private static boolean fluted(double y, double fy0, double fy1) { return y >= fy0 - .34 && y <= fy1 + .34; }

        /** Engraved line (dark, alpha blended) with the glowing vein on top of it (additive): a one texel pixel line, not a soft stroke. */
        void inlay(Vec3[] q, double[] texels) {
            Vec3 nm = q[1].subtract(q[0]).cross(q[3].subtract(q[0]));
            double[] t = texels != null ? texels : project(q, nm.lengthSqr() < 1e-12 ? pose.d() : nm.normalize());
            cells(q, t, (cp, ct) -> {
                float[] uv = uvFor(GateAtlas.PIX_VEIN, ct);
                if (c.body()) GateDraw.shaded(c, cp, new int[]{ENGRAVE, ENGRAVE, ENGRAVE, ENGRAVE}, new double[]{.78, .78, .78, .78}, uv);
                if (c.glow() && vein > .02) {
                    double a = Math.min(1, vein) * .95;
                    GateDraw.shaded(c, cp, new int[]{VEIN, VEIN, VEIN, VEIN}, new double[]{a, a, a, a}, uv);
                }
            });
        }
        /** Inlay strip on a face of {@code b} at depth {@code z} looking along {@code sign}*b.z, tiled {@code repeats} times across x0..x1. */
        void strip(Basis b, double x0, double x1, double y0, double y1, double z, int sign, GateAtlas.Region region, int repeats) {
            Vec3 normal = b.z().scale(sign), ctr = b.p((x0 + x1) / 2, (y0 + y1) / 2, z);
            if (normal.dot(c.eye.subtract(ctr)) <= 0) return;
            double zz = z + sign * .05;
            for (int k = 0; k < repeats; k++) {
                double xa = x0 + (x1 - x0) * k / repeats, xb = x0 + (x1 - x0) * (k + 1) / repeats;
                inlay(new Vec3[]{b.p(xa, y1, zz), b.p(xb, y1, zz), b.p(xb, y0, zz), b.p(xa, y0, zz)}, null);
            }
        }

        // ---- bevelled boxes ---------------------------------------------------------------------------------------
        void box(Basis b, double x0, double x1, double y0, double y1, double z0, double z1, double bev) {
            double[] lo = {x0, y0, z0}, hi = {x1, y1, z1};
            Vec3[] axis = {b.x(), b.y(), b.z()};
            for (int a = 0; a < 3; a++) for (int s = -1; s <= 1; s += 2) {
                int i = (a + 1) % 3, j = (a + 2) % 3;
                double ext = s > 0 ? hi[a] : lo[a];
                Vec3 n = axis[a].scale(s);
                Vec3[] p = {pt(b, a, ext, i, lo[i] + bev, j, lo[j] + bev), pt(b, a, ext, i, hi[i] - bev, j, lo[j] + bev),
                        pt(b, a, ext, i, hi[i] - bev, j, hi[j] - bev), pt(b, a, ext, i, lo[i] + bev, j, hi[j] - bev)};
                face(p, new Vec3[]{n, n, n, n}, null, 0);
            }
            for (int k = 0; k < 3; k++) {
                int i = (k + 1) % 3, j = (k + 2) % 3;
                for (int si = -1; si <= 1; si += 2) for (int sj = -1; sj <= 1; sj += 2) {
                    double ei = si > 0 ? hi[i] : lo[i], ej = sj > 0 ? hi[j] : lo[j];
                    Vec3[] p = {pt(b, k, lo[k] + bev, i, ei, j, ej - sj * bev), pt(b, k, hi[k] - bev, i, ei, j, ej - sj * bev),
                            pt(b, k, hi[k] - bev, i, ei - si * bev, j, ej), pt(b, k, lo[k] + bev, i, ei - si * bev, j, ej)};
                    Vec3 n = axis[i].scale(si).add(axis[j].scale(sj)).normalize();
                    face(p, new Vec3[]{n, n, n, n}, null, 2);
                }
            }
            for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) for (int sz = -1; sz <= 1; sz += 2) {
                double ex = sx > 0 ? hi[0] : lo[0], ey = sy > 0 ? hi[1] : lo[1], ez = sz > 0 ? hi[2] : lo[2];
                Vec3 ca = b.p(ex, ey - sy * bev, ez - sz * bev), cb = b.p(ex - sx * bev, ey, ez - sz * bev), cc = b.p(ex - sx * bev, ey - sy * bev, ez);
                Vec3 n = axis[0].scale(sx).add(axis[1].scale(sy)).add(axis[2].scale(sz)).normalize();
                face(new Vec3[]{ca, cb, cc, cc}, new Vec3[]{n, n, n, n}, null, 2);
            }
        }
        private static Vec3 pt(Basis b, int a, double va, int i, double vi, int j, double vj) {
            double[] q = new double[3]; q[a] = va; q[i] = vi; q[j] = vj; return b.p(q[0], q[1], q[2]);
        }

        // ---- frame ------------------------------------------------------------------------------------------------
        Basis main() { return new Basis(pose.foot(), pose.r(), pose.u(), pose.d(), pose.s()); }

        void sill() {
            Basis b = main();
            box(b, -12.5, 12.5, 12, OPENING_Y0, -1.8, 1.8, .5);
            for (int s = -1; s <= 1; s += 2) strip(b, -10.5, 10.5, 12.6, 14.4, s * 1.8, s, GateAtlas.FILIGREE, 3);
        }
        void jambs() {
            Basis b = main();
            for (int s = -1; s <= 1; s += 2)
                box(b, s > 0 ? JAMB_X : -JAMB_X - 1.9, s > 0 ? JAMB_X + 1.9 : -JAMB_X, OPENING_Y0, OPENING_Y1, -1.3, 1.3, .25);
        }
        void crown() {
            Basis b = main();
            box(b, -12.5, 12.5, OPENING_Y1, TOP_Y, -1.6, 1.6, .5);
            for (int s = -1; s <= 1; s += 2) strip(b, -10.5, 10.5, OPENING_Y1 + 1.2, OPENING_Y1 + 3.8, s * 1.6, s, GateAtlas.FILIGREE, 2);
            box(b, -8, 8, TOP_Y, TOP_Y + 2.6, -1.5, 1.5, .5);
            for (int s = -1; s <= 1; s += 2) strip(b, -6.5, 6.5, TOP_Y + .6, TOP_Y + 2, s * 1.5, s, GateAtlas.DASH, 1);
            box(b, -4.4, 4.4, TOP_Y + 2.6, TOP_Y + 4.8, -1.2, 1.2, .4);
            lathe(0, 0, SPIRE_Y, SPIRE_R, Math.min(sides, 12), 1e9, 1e9, new Band[0], 1);
        }

        // ---- scroll hooks (tubes swept along a spiral in the gate plane) -----------------------------------------------
        void bigHook(int side) { hook(side * 7.8, 69.2, side * 3.4, 65.8, side > 0 ? 1 : -1, 1.1, .95, .26, 0); }
        void smallHook(int side) { hook(side * 3.5, 5.2, side * .9, 8.6, side > 0 ? -1 : 1, 1.25, .52, .17, 0); }

        /** Tube of radius r0 tapering to r1 along a spiral around (cx, cy) that starts at (px, py) and winds {@code turns} turns. */
        void hook(double cx, double cy, double px, double py, int dir, double turns, double r0, double r1, double z0) {
            double a0 = Math.atan2(py - cy, px - cx), start = Math.hypot(px - cx, py - cy);
            int n = 14, k = 4;
            Vec3[][] P = new Vec3[n + 1][k + 1], N = new Vec3[n + 1][k + 1];
            double[] xs = new double[n + 1], ys = new double[n + 1];
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n, rho = start * (1 - .8 * t * t * (1.25 - .25 * t)), a = a0 + dir * turns * TAU * t;
                xs[i] = cx + rho * Math.cos(a); ys[i] = cy + rho * Math.sin(a);
            }
            for (int i = 0; i <= n; i++) {
                int lo = Math.max(0, i - 1), hi = Math.min(n, i + 1);
                double tx = xs[hi] - xs[lo], ty = ys[hi] - ys[lo], len = Math.hypot(tx, ty);
                double nx = -ty / len, ny = tx / len, rad = r0 + (r1 - r0) * (double) i / n;
                for (int s = 0; s <= k; s++) {
                    double th = TAU * ((s % k) + .5) / k, ox = nx * Math.cos(th), oy = ny * Math.cos(th), oz = Math.sin(th);
                    P[i][s] = pose.at(xs[i] + rad * ox, ys[i] + rad * oy, z0 + rad * oz);
                    N[i][s] = pose.r().scale(ox).add(pose.u().scale(oy)).add(pose.d().scale(oz)).normalize();
                }
            }
            for (int i = 0; i < n; i++) for (int s = 0; s < k; s++)
                face(new Vec3[]{P[i][s], P[i][s + 1], P[i + 1][s + 1], P[i + 1][s]}, new Vec3[]{N[i][s], N[i][s + 1], N[i + 1][s + 1], N[i + 1][s]}, null, 1);
        }

        // ---- doors ------------------------------------------------------------------------------------------------
        /** One leaf hinged on a jamb; {@code angle} swings it out towards the caster. */
        void leaf(int side, double angle) {
            double width = JAMB_X - .05, height = OPENING_Y1 - OPENING_Y0 - .4;
            // The leaves swing out towards the caster (-d), hinged on the jambs.
            Vec3 toward = pose.r().scale(-side * Math.cos(angle)).add(pose.d().scale(-Math.sin(angle)));
            Vec3 normal = pose.d().scale(-Math.cos(angle)).add(pose.r().scale(side * Math.sin(angle)));
            Basis b = new Basis(pose.at(side * JAMB_X, OPENING_Y0 + .2, 0), toward, pose.u(), normal, pose.s());
            box(b, 0, width, 0, height, -.55, .55, .2);
            double rail = height * .48;
            box(b, 0, .9, 0, height, -.95, .95, .25);
            box(b, width - .9, width, 0, height, -.95, .95, .25);
            box(b, .9, width - .9, 0, 1.6, -.9, .9, .25);
            box(b, .9, width - .9, height - 1.6, height, -.9, .9, .25);
            box(b, .9, width - .9, rail, rail + 1.2, -.9, .9, .25);
            for (int s = -1; s <= 1; s += 2) {
                double z = s * .55, up = rail + 1.2;
                strip(b, 1.2, width - 1.2, height - 4.2, height - 2.2, z, s, GateAtlas.FILIGREE, 1);
                double seal = width - 2.4, y0 = up + (height - 1.6 - up) / 2 - seal / 2 - .4;
                strip(b, 1.2, 1.2 + seal, y0, y0 + seal, z, s, GateAtlas.SEAL, 1);
                strip(b, 1.2, width - 1.2, up + 1.2, up + 3.2, z, s, GateAtlas.RUNES, 1);
                strip(b, 1.2, width - 1.2, rail - 3.2, rail - 1.2, z, s, GateAtlas.FILIGREE, 1);
                strip(b, 1.2, width - 1.2, 2.6, 4.6, z, s, GateAtlas.RUNES, 1);
            }
        }
    }
}
