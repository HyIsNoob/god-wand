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
    private static final double[] COL_Y = {0, 1.2, 3, 5, 7.5, 9.5, 11.5, 13, 14, 14.4, 15.4, 15.8, 17, 19, 23, 26, 30, 32.5, 36, 40, 44, 50, 56, 57, 58.5, 59.5, 60.5, 62.5, 64, 65, 66.5, 68.5, 71, 74},
            COL_R = {0, .5, 1.5, 2.9, 4, 4.2, 3.6, 2.8, 2.6, 3.2, 3.2, 2.6, 2.5, 2.5, 2.5, 2.45, 2.45, 2.45, 2.4, 2.4, 2.4, 2.4, 2.4, 2.9, 3.1, 2.7, 3.6, 4, 3.4, 2.4, 1.2, .8, .45, 0};
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
        g.sides = dist < 90 * pose.s() ? 24 : dist < 170 * pose.s() ? 16 : 12;
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
        final Ctx c; final Pose pose; final double vein; final Vec3 light; int sides = 24;
        Pen(Ctx c, Pose pose, double vein) {
            this.c = c; this.pose = pose; this.vein = vein;
            // Light from the great circle above and a little in front of the gate.
            light = pose.r().scale(.25).add(pose.u().scale(.8)).add(pose.d().scale(-.45)).normalize();
        }

        // ---- shading ----------------------------------------------------------------------------------------------
        int tone(Vec3 n, Vec3 p, double groove) {
            double t = .5 + .5 * n.dot(light);
            t = t * t * (3 - 2 * t);
            double y = p.y - pose.foot().y;
            t = Math.pow(t, 1.3) * (.5 + .5 * smooth(y / (46 * pose.s())));
            if (n.y < 0) t *= 1 + .3 * n.y;
            Vec3 v = c.eye.subtract(p);
            double len = v.length();
            if (len > 1e-6) {
                Vec3 view = v.scale(1 / len), h = light.add(view).normalize();
                // Cool fill from the circle's glow on whatever faces the camera, and a tight silver highlight.
                t += .16 * Math.max(0, n.dot(view)) + .2 * Math.pow(Math.max(0, n.dot(h)), 28);
            }
            t = clamp01(t - .26 * groove);
            return mix(SHADOW, PORCELAIN, t);
        }
        static int mix(int a, int b, double t) {
            int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t),
                    bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
            return r << 16 | g << 8 | bl;
        }

        /** Emits one shaded quad (body) and its additive highlight; {@code rim} 0 none, 1 silhouette glow, 2 edge light. False when it faces away. */
        boolean face(Vec3[] p, Vec3[] n, double[] groove, int rim) {
            Vec3 ctr = p[0].add(p[1]).add(p[2]).add(p[3]).scale(.25), v = c.eye.subtract(ctr);
            double dist = v.length();
            Vec3 nm = n[0].add(n[1]).add(n[2]).add(n[3]);
            if (dist < 1e-6 || nm.dot(v) < -.16 * nm.length() * dist) return false;
            if (c.body()) {
                int[] col = new int[4];
                for (int i = 0; i < 4; i++) col[i] = tone(n[i], p[i], groove == null ? 0 : groove[i]);
                GateDraw.shaded(c, p, col, new double[]{BODY_ALPHA, BODY_ALPHA, BODY_ALPHA, BODY_ALPHA}, null, 0, 1, 0, 1);
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
        void column(double cx) { lathe(cx, 0, COL_Y, COL_R, sides, 19, 56, COL_BANDS, 1); }
        void spike() {
            lathe(0, 0, SPIKE_Y, SPIKE_R, Math.min(sides, 8), 1e9, 1e9, new Band[0], 1);
            smallHook(-1); smallHook(1);
        }

        void lathe(double cx, double cz, double[] ys, double[] rs, int sides, double fy0, double fy1, Band[] bands, int rim) {
            int rows = ys.length;
            Vec3[][] P = new Vec3[rows][sides + 1], N = new Vec3[rows][sides + 1];
            double[][] G = new double[rows][sides + 1];
            for (int i = 0; i < rows; i++) {
                int a = Math.max(0, i - 1), b = Math.min(rows - 1, i + 1);
                double tr = rs[b] - rs[a], ty = ys[b] - ys[a], len = Math.hypot(tr, ty);
                double nr = len < 1e-9 ? 1 : ty / len, ny = len < 1e-9 ? 0 : -tr / len;
                boolean fluted = ys[i] >= fy0 && ys[i] <= fy1;
                for (int s = 0; s <= sides; s++) {
                    double phi = TAU * (s % sides) / sides, cs = Math.cos(phi), sn = Math.sin(phi);
                    double groove = fluted && s % 2 == 0 ? 1 : 0, r = rs[i] * (1 - .045 * groove);
                    P[i][s] = pose.at(cx + r * cs, ys[i], cz + r * sn);
                    N[i][s] = pose.r().scale(nr * cs).add(pose.u().scale(ny)).add(pose.d().scale(nr * sn)).normalize();
                    G[i][s] = groove;
                }
            }
            for (int i = 0; i + 1 < rows; i++) for (int s = 0; s < sides; s++) {
                Vec3[] p = {P[i][s], P[i][s + 1], P[i + 1][s + 1], P[i + 1][s]}, n = {N[i][s], N[i][s + 1], N[i + 1][s + 1], N[i + 1][s]};
                double[] gr = {G[i][s], G[i][s + 1], G[i + 1][s + 1], G[i + 1][s]};
                if (!face(p, n, gr, rim)) continue;
                for (Band band : bands) {
                    if (ys[i] < band.y0() - 1e-6 || ys[i + 1] > band.y1() + 1e-6) continue;
                    double v0 = (ys[i] - band.y0()) / (band.y1() - band.y0()), v1 = (ys[i + 1] - band.y0()) / (band.y1() - band.y0());
                    double u0 = (double) s / sides, u1 = (double) (s + 1) / sides;
                    Vec3[] q = new Vec3[4];
                    for (int k = 0; k < 4; k++) q[k] = p[k].add(n[k].scale(.05 * pose.s()));
                    inlay(q, band.region(), u0, u1, v0, v1);
                }
            }
        }

        /** Engraved line (dark, alpha blended) with the glowing vein on top of it (additive). */
        void inlay(Vec3[] q, GateAtlas.Region region, double u0, double u1, double v0, double v1) {
            if (c.body()) GateDraw.shaded(c, q, new int[]{ENGRAVE, ENGRAVE, ENGRAVE, ENGRAVE}, new double[]{.78, .78, .78, .78}, region, u0, u1, v0, v1);
            if (c.glow() && vein > .02) {
                double a = Math.min(1, vein) * .95;
                GateDraw.shaded(c, q, new int[]{VEIN, VEIN, VEIN, VEIN}, new double[]{a, a, a, a}, region, u0, u1, v0, v1);
            }
        }
        /** Inlay strip on a face of {@code b} at depth {@code z} looking along {@code sign}*b.z, tiled {@code repeats} times across x0..x1. */
        void strip(Basis b, double x0, double x1, double y0, double y1, double z, int sign, GateAtlas.Region region, int repeats) {
            Vec3 normal = b.z().scale(sign), ctr = b.p((x0 + x1) / 2, (y0 + y1) / 2, z);
            if (normal.dot(c.eye.subtract(ctr)) <= 0) return;
            double zz = z + sign * .05;
            for (int k = 0; k < repeats; k++) {
                double xa = x0 + (x1 - x0) * k / repeats, xb = x0 + (x1 - x0) * (k + 1) / repeats;
                inlay(new Vec3[]{b.p(xa, y1, zz), b.p(xb, y1, zz), b.p(xb, y0, zz), b.p(xa, y0, zz)}, region, 0, 1, 0, 1);
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
            int n = 26, k = 6;
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
                    double th = TAU * (s % k) / k, ox = nx * Math.cos(th), oy = ny * Math.cos(th), oz = Math.sin(th);
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
