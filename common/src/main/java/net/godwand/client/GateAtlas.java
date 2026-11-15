package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Random;

/**
 * Procedural 512x512 line-art atlas for the Silent Gate circles (no PNG shipped), the same recipe as Fuga's flame texture:
 * white RGB, alpha carries the drawing, tinted with vertex colour. Strips repeat along U, so every geometry quad
 * samples a tile-local phase 0..1 of its region (inset half a texel) and never relies on hardware wrap.
 */
final class GateAtlas {
    record Region(int x, int y, int w, int h) {
        float u(double phase) { return (float) ((x + .5 + phase * (w - 1)) / SIZE); }
        float v(double t) { return (float) ((y + .5 + t * (h - 1)) / SIZE); }
    }
    static final int SIZE = 1024;
    static final Region SWIRL = new Region(0, 0, 512, 64), RUNES = new Region(0, 64, 512, 64), DASH = new Region(0, 128, 512, 64),
            RIM = new Region(0, 192, 512, 64), SEAL = new Region(0, 256, 256, 256), FILIGREE = new Region(256, 256, 256, 64),
            GLOW = new Region(256, 320, 64, 64), CLOUD = new Region(384, 320, 128, 128), FIBRE = new Region(256, 448, 256, 56), SOLID = new Region(504, 504, 8, 8),
            /** Mana silk (U along the ribbon, V across it) and the four-point glint. */
            SILK = new Region(0, 512, 256, 64), STAR = new Region(0, 576, 64, 64),
            /** A feather (quill at V 0, tip at V 1): what the gate's wings shed. */
            FEATHER = new Region(64, 576, 64, 64),
            /** Billowing smoke with fine detail (for large puffs), a long wisp of it (U along the wisp), and the rays of a shaft of light (U across the shaft, V along it). */
            SMOKE = new Region(0, 640, 256, 256), WISP = new Region(256, 640, 256, 128), RAYS = new Region(256, 768, 128, 256);
    /** Pixel-art tiles of the gate: 16x16 texels drawn 8 atlas pixels each and repeated 3x3, so any quad up to 32 texels wide samples one run
     *  without wrapping and the (bilinear) filter only softens a texel edge by an eighth of a texel. */
    static final int TEXEL = 8, TILE = 16, REPEAT = 3;
    static final Region PIX_STONE = new Region(512, 0, TILE * TEXEL * REPEAT, TILE * TEXEL * REPEAT),
            PIX_VEIN = new Region(512, TILE * TEXEL * REPEAT, TILE * TEXEL * REPEAT, TILE * TEXEL * REPEAT);
    /** Atlas coordinate of a texel offset (0..{@code TILE * REPEAT}) inside a pixel region. */
    static float pixU(Region r, double texel) { return (float) ((r.x + texel * TEXEL) / SIZE); }
    static float pixV(Region r, double texel) { return (float) ((r.y + texel * TEXEL) / SIZE); }
    /** Brightness of the stone tile at texel (x, y): grey ashlar block with a dark mortar edge on the right and bottom, a bright chisel line on the left and top. */
    static double stone(int x, int y) {
        x %= TILE; y %= TILE;
        if (x == TILE - 1 || y == TILE - 1) return .66;
        if (x == 0 || y == 0) return 1;
        double n = lattice(x, y, 5);
        return n < .12 ? .8 : n < .4 ? .88 : n < .75 ? .94 : 1;
    }
    /** Vein tile: a one texel line through the middle with alternating teeth. */
    static boolean vein(int x, int y) {
        x %= TILE; y %= TILE;
        return y == 7 || (x % 4 == 1 && (y == 5 || y == 6)) || (x % 4 == 3 && (y == 8 || y == 9));
    }
    /** The ornament of a door panel, one texel to a block like the stone ({@value #PANEL_TEXEL} atlas pixels each), row 0 at the top. */
    static final int PANEL_W = 28, PANEL_H = 76, PANEL_TEXEL = 4;
    static final Region PIX_PANEL = new Region(896, 0, PANEL_W * PANEL_TEXEL, PANEL_H * PANEL_TEXEL);
    private static boolean[] panelArt;
    /** True where the door panel ornament has a line at texel (x, y), y up from the panel's foot: a double arched frame, inside it a tree whose boughs curl
     *  like fern heads, roots at its foot, a rayed sun in the arch (reference 5: the leaves of the gate are filigree, not bare slabs). Mirrored about the middle. */
    static boolean panel(int x, int y) {
        if (x < 0 || y < 0 || x >= PANEL_W || y >= PANEL_H) return false;
        if (panelArt == null) {
            boolean[] g = new boolean[PANEL_W * PANEL_H];
            double mid = (PANEL_W - 1) / 2.0;
            int spring = 61;
            for (int inset = 0; inset <= 2; inset += 2) {
                for (int yy = inset; yy <= spring; yy++) g[yy * PANEL_W + inset] = true;
                for (int xx = inset; xx <= mid; xx++) g[inset * PANEL_W + xx] = true;
                for (int i = 0; i <= 90; i++) { double a = Math.PI / 2 * i / 90; mark(g, mid - (mid - inset) * Math.cos(a), spring + (mid - inset) * Math.sin(a)); }
            }
            for (int yy = 5; yy <= 53; yy++) g[yy * PANEL_W + 13] = true;
            int[][] roots = {{12, 4}, {11, 4}, {10, 3}, {9, 3}};
            for (int[] r : roots) g[r[1] * PANEL_W + r[0]] = true;
            double[][] boughs = {{12, 4}, {23, 4}, {34, 3.5}, {44, 3}};
            for (double[] b : boughs) for (int i = 0; i <= 70; i++) {
                double t = i / 70.0, a = TAU * 1.25 * t, rho = b[1] * (1 - .78 * t);
                mark(g, 12.5 - b[1] + rho * Math.cos(a), b[0] + rho * Math.sin(a));
            }
            for (int i = 0; i <= 40; i++) { double a = Math.PI * i / 40 + Math.PI / 2; mark(g, mid + 4 * Math.cos(a), 58 + 4 * Math.sin(a)); }
            for (int k = 0; k < 8; k++) for (double r = 5.6; r <= 7.6; r += .5) mark(g, mid + r * Math.cos(k * TAU / 8), 58 + r * Math.sin(k * TAU / 8));
            g[58 * PANEL_W + 13] = g[57 * PANEL_W + 13] = true;
            for (int yy = 0; yy < PANEL_H; yy++) for (int xx = 0; xx < PANEL_W / 2; xx++) g[yy * PANEL_W + PANEL_W - 1 - xx] = g[yy * PANEL_W + xx];
            panelArt = g;
        }
        return panelArt[y * PANEL_W + x];
    }
    private static void mark(boolean[] g, double x, double y) {
        int xi = (int) Math.round(x), yi = (int) Math.round(y);
        if (xi >= 0 && yi >= 0 && xi < PANEL_W && yi < PANEL_H) g[yi * PANEL_W + xi] = true;
    }
    static final Region[] STRIPS = {SWIRL, RUNES, DASH, RIM, FILIGREE};
    static final float SOLID_U = SOLID.u(.5), SOLID_V = SOLID.v(.5);
    private static final double TAU = Math.PI * 2;

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("godwand", "silent_gate_runes");
    private static boolean ready;
    private static byte[] cache;

    /** Alpha of the whole atlas, row-major. Pure and deterministic, so tests and the texture upload share it. */
    static byte[] alpha() {
        if (cache != null) return cache;
        Canvas c = new Canvas();
        swirl(c); runes(c); dash(c); rim(c); filigree(c); seal(c); glow(c); cloud(c); silk(c); star(c); smoke(c); wisp(c); rays(c); feather(c);
        for (int y = 0; y < FIBRE.h; y++) for (int x = 0; x < FIBRE.w; x++) c.a[(FIBRE.y + y) * SIZE + FIBRE.x + x] = FugaRenderer.flameAlpha(x, y) / 255f;
        for (int y = 0; y < SOLID.h; y++) for (int x = 0; x < SOLID.w; x++) c.a[(SOLID.y + y) * SIZE + SOLID.x + x] = 1;
        for (int y = 0; y < PIX_STONE.h; y++) for (int x = 0; x < PIX_STONE.w; x++) {
            c.a[(PIX_STONE.y + y) * SIZE + PIX_STONE.x + x] = 1;
            if (vein(x / TEXEL, y / TEXEL)) c.a[(PIX_VEIN.y + y) * SIZE + PIX_VEIN.x + x] = 1;
        }
        for (int y = 0; y < PIX_PANEL.h; y++) for (int x = 0; x < PIX_PANEL.w; x++)
            c.a[(PIX_PANEL.y + y) * SIZE + PIX_PANEL.x + x] = panel(x / PANEL_TEXEL, PANEL_H - 1 - y / PANEL_TEXEL) ? 1 : 0;
        byte[] out = new byte[SIZE * SIZE];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Math.round(Math.min(1, c.a[i]) * 255);
        return cache = out;
    }

    private static byte[] lumCache;
    /** Grey level of every atlas pixel: white everywhere except the pixel-art stone tile, which carries its own tones. */
    static byte[] lum() {
        if (lumCache != null) return lumCache;
        byte[] out = new byte[SIZE * SIZE];
        java.util.Arrays.fill(out, (byte) 255);
        for (int y = 0; y < PIX_STONE.h; y++) for (int x = 0; x < PIX_STONE.w; x++)
            out[(PIX_STONE.y + y) * SIZE + PIX_STONE.x + x] = (byte) Math.round(255 * stone(x / TEXEL, y / TEXEL));
        return lumCache = out;
    }

    static void bind() { upload(); RenderSystem.setShaderTexture(0, TEXTURE); }
    /** The same atlas for the additive pass: opaque, grey = luminance x alpha (see {@link GateDraw#premultiplied}). */
    static void bindGlow() { upload(); RenderSystem.setShaderTexture(0, GLOW_TEXTURE); }
    private static final ResourceLocation GLOW_TEXTURE = ResourceLocation.fromNamespaceAndPath("godwand", "silent_gate_runes_glow");
    /** Building the atlas takes most of a second; done on the render thread at the first cast it froze the game (owner, 2026-11-15). It is started on a
     *  background thread as soon as a world is joined, and the first bind only uploads what is already there. */
    private static java.util.concurrent.CompletableFuture<byte[][]> pending;
    static void prepare() { if (pending == null) pending = java.util.concurrent.CompletableFuture.supplyAsync(() -> new byte[][]{alpha(), lum()}); }
    private static void upload() {
        if (ready) return;
        prepare();
        byte[][] bytes = pending.join();
        byte[] a = bytes[0], l = bytes[1];
        for (int glow = 0; glow < 2; glow++) {
            var pixels = new com.mojang.blaze3d.platform.NativeImage(SIZE, SIZE, false);
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                int al = a[y * SIZE + x] & 255, g = l[y * SIZE + x] & 255;
                if (glow == 1) { g = g * al / 255; al = 255; }
                pixels.setPixelRGBA(x, y, al << 24 | g << 16 | g << 8 | g);
            }
            var texture = new net.minecraft.client.renderer.texture.DynamicTexture(pixels);
            texture.setFilter(true, false);
            Minecraft.getInstance().getTextureManager().register(glow == 1 ? GLOW_TEXTURE : TEXTURE, texture);
        }
        ready = true;
    }

    // ---- strips -----------------------------------------------------------------------------------------------

    /** Frieze of big scrolls: pairs of mirrored volutes hang from a top rail, each as tall as the strip, strokes tapering into curled tips. */
    private static void swirl(Canvas c) {
        c.clip(SWIRL, true);
        double pitch = 512 / 6.0;
        for (int k = 0; k < 6; k++) {
            double px = pitch * k + pitch / 2;
            spiral(c, px - 21, 34, 19, 25, -Math.PI / 2, 1, 1.45, 6.4, 2.4);
            spiral(c, px + 21, 34, 19, 25, -Math.PI / 2, -1, 1.45, 6.4, 2.4);
            c.dot(px - 21, 34, 2.6); c.dot(px + 21, 34, 2.6);
            // Small counter-curl tucked under the arch between the pair.
            spiral(c, px, 47, 5, 6, Math.PI / 2, 1, 1.3, 3.4, 1.8);
        }
        c.line(0, 9, 512, 9, 4.2); c.line(0, 60, 512, 60, 1.6);
    }

    /** Abstract glyph row: 16 cells of 32 px, three to five strokes each from a seeded set of lines, arcs, dots and rings. */
    private static void runes(Canvas c) {
        c.clip(RUNES, true);
        int y0 = RUNES.y;
        for (int g = 0; g < 16; g++) {
            Random r = new Random(g * 7919L + 13);
            double cx = g * 32 + 16, cy = y0 + 32;
            int strokes = 3 + r.nextInt(3);
            for (int s = 0; s < strokes; s++) {
                double ox = (r.nextDouble() - .5) * 12, top = cy - 14 - r.nextInt(4), bot = cy + 14 + r.nextInt(4);
                switch (r.nextInt(6)) {
                    case 0 -> c.line(cx + ox, top, cx + ox, bot, 3.2);
                    case 1 -> c.line(cx - 9, cy + (r.nextDouble() - .5) * 20, cx + 9, cy + (r.nextDouble() - .5) * 20, 3);
                    case 2 -> c.line(cx - 8, top, cx + 8 * (r.nextBoolean() ? 1 : -1), bot, 3);
                    case 3 -> c.arc(cx + ox, cy + (r.nextDouble() - .5) * 14, 5 + r.nextInt(5), r.nextInt(4) * Math.PI / 2, r.nextInt(4) * Math.PI / 2 + Math.PI, 3);
                    case 4 -> c.dot(cx + ox * 1.3, cy + (r.nextBoolean() ? -16 : 16), 2.6);
                    default -> c.arc(cx + ox * .5, cy + (r.nextDouble() - .5) * 18, 3.5, 0, TAU, 2.8);
                }
            }
            c.line(cx - 12, y0 + 10, cx + 12, y0 + 10, 1.6); // glyph cell cap
        }
        c.line(0, y0 + 6, 512, y0 + 6, 2); c.line(0, y0 + 57, 512, y0 + 57, 2);
        for (int g = 0; g < 16; g++) c.line(g * 32, y0 + 6, g * 32, y0 + 12, 2);
    }

    /** Two broken lines with dots and small diamonds between them. */
    private static void dash(Canvas c) {
        c.clip(DASH, true);
        int y0 = DASH.y;
        for (int i = 0; i < 32; i++) {
            c.line(i * 16 + 1, y0 + 20, i * 16 + 11, y0 + 20, 4);
            c.line(i * 16 + 9, y0 + 44, i * 16 + 19, y0 + 44, 4);
            if (i % 2 == 0) c.dot(i * 16 + 8, y0 + 32, 2.4);
        }
        for (int i = 0; i < 4; i++) {
            double x = i * 128 + 64, y = y0 + 32;
            c.poly(new double[]{x - 8, y, x, y - 8, x + 8, y, x, y + 8, x - 8, y}, 2.4);
        }
    }

    /** Continuous soft rim: one bold core with two fine twins. */
    private static void rim(Canvas c) {
        c.clip(RIM, true);
        int y0 = RIM.y;
        c.line(0, y0 + 32, 512, y0 + 32, 5);
        c.line(0, y0 + 20, 512, y0 + 20, 2); c.line(0, y0 + 44, 512, y0 + 44, 2);
        for (int i = 0; i < 64; i++) { c.line(i * 8 + 4, y0 + 10, i * 8 + 4, y0 + 14, 1.8); c.line(i * 8 + 4, y0 + 50, i * 8 + 4, y0 + 54, 1.8); }
    }

    /** Gold filigree: two vine waves in antiphase, curls on their outer swings, dots where they cross. Period 128 inside a 256 tile. */
    private static void filigree(Canvas c) {
        c.clip(FILIGREE, true);
        int x0 = FILIGREE.x, y0 = FILIGREE.y;
        for (int k = 0; k < 2; k++) {
            double xs = x0 + 128 * k;
            polyline(c, x -> y0 + 32 + 11 * Math.sin(TAU * (x - x0) / 128), xs, xs + 128, 2.8);
            polyline(c, x -> y0 + 32 - 11 * Math.sin(TAU * (x - x0) / 128), xs, xs + 128, 2.8);
            curl(c, xs + 32, y0 + 43 + 8, 8, -Math.PI / 2, 1, 1.9, 2.6);
            curl(c, xs + 96, y0 + 43 + 8 - 0, 8, -Math.PI / 2, -1, 1.9, 2.6);
            curl(c, xs + 32, y0 + 21 - 8, 8, Math.PI / 2, -1, 1.9, 2.6);
            curl(c, xs + 96, y0 + 21 - 8, 8, Math.PI / 2, 1, 1.9, 2.6);
            c.dot(xs, y0 + 32, 2.6); c.dot(xs + 64, y0 + 32, 2.6);
        }
        c.line(x0, y0 + 4, x0 + 256, y0 + 4, 1.6); c.line(x0, y0 + 59, x0 + 256, y0 + 59, 1.6);
    }

    /** 8-fold seal: nested rims, eight curling arms, kite marks and dots, a star and a centre point. */
    private static void seal(Canvas c) {
        c.clip(SEAL, false);
        double cx = SEAL.x + 128, cy = SEAL.y + 128;
        c.arc(cx, cy, 122, 0, TAU, 3.2); c.arc(cx, cy, 115, 0, TAU, 1.6);
        c.arc(cx, cy, 98, 0, TAU, 2.4); c.arc(cx, cy, 92, 0, TAU, 1.4);
        c.arc(cx, cy, 40, 0, TAU, 2.4); c.arc(cx, cy, 34, 0, TAU, 1.4); c.arc(cx, cy, 24, 0, TAU, 2);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4, ca = Math.cos(a), sa = Math.sin(a), mid = a + Math.PI / 8;
            // Curling arm: a spiral rolled on a 66-radius centre, tangent to the ring at r 52.
            curl(c, cx + 66 * ca, cy + 66 * sa, 15, a + Math.PI, 1, 2.0, 3);
            c.line(cx + 40 * ca, cy + 40 * sa, cx + 51 * ca, cy + 51 * sa, 2.4);
            // Kite between the arms, and a dot ring.
            double kx = cx + 106 * Math.cos(mid), ky = cy + 106 * Math.sin(mid);
            c.poly(new double[]{kx + 7 * Math.cos(mid), ky + 7 * Math.sin(mid), kx - 4 * Math.sin(mid), ky + 4 * Math.cos(mid),
                    kx - 7 * Math.cos(mid), ky - 7 * Math.sin(mid), kx + 4 * Math.sin(mid), ky - 4 * Math.cos(mid),
                    kx + 7 * Math.cos(mid), ky + 7 * Math.sin(mid)}, 2.2);
            c.dot(cx + 119 * Math.cos(a), cy + 119 * Math.sin(a), 2.2);
            c.dot(cx + 95 * Math.cos(mid), cy + 95 * Math.sin(mid), 2);
            double[] star = {cx + 20 * ca, cy + 20 * sa, cx + 20 * Math.cos(a + 3 * Math.PI / 4), cy + 20 * Math.sin(a + 3 * Math.PI / 4)};
            c.line(star[0], star[1], star[2], star[3], 1.8);
            c.line(cx + 6 * ca, cy + 6 * sa, cx + 20 * ca, cy + 20 * sa, 1.8);
        }
        c.dot(cx, cy, 4.2); c.arc(cx, cy, 9, 0, TAU, 1.8);
    }

    private static void glow(Canvas c) {
        for (int y = 0; y < GLOW.h; y++) for (int x = 0; x < GLOW.w; x++) {
            double d = Math.hypot(x + .5 - GLOW.w / 2.0, y + .5 - GLOW.h / 2.0) / (GLOW.w / 2.0 - 1);
            double f = Math.max(0, 1 - d);
            c.a[(GLOW.y + y) * SIZE + GLOW.x + x] = (float) (f * f * f);
        }
    }


    /** Silk: soft edges across, long creases and three bright warped fibres along. */
    private static void silk(Canvas c) {
        for (int y = 0; y < SILK.h; y++) for (int x = 0; x < SILK.w; x++) {
            double u = (x + .5) / SILK.w, v = (y + .5) / SILK.h, m = Math.abs(2 * v - 1);
            double edge = Math.pow(Math.max(0, 1 - m), 1.6);
            double crease = .5 + .5 * Math.sin(TAU * (2 * u + .18 * Math.sin(TAU * u)) + v * 7 + 1.3 * Math.sin(v * 11));
            double fibres = 0;
            for (int k = 0; k < 3; k++) {
                double centre = .28 + .22 * k + .05 * Math.sin(TAU * (1.5 * u + k * .31)), d = (v - centre) / .028;
                fibres = Math.max(fibres, Math.exp(-d * d));
            }
            c.a[(SILK.y + y) * SIZE + SILK.x + x] = (float) Math.min(1, edge * (.55 + .22 * crease + .28 * fibres));
        }
    }
    /** Four-point sparkle with a small round core. */
    private static void star(Canvas c) {
        for (int y = 0; y < STAR.h; y++) for (int x = 0; x < STAR.w; x++) {
            double dx = Math.abs((x + .5) / STAR.w * 2 - 1), dy = Math.abs((y + .5) / STAR.h * 2 - 1);
            double arms = Math.max(0, 1 - Math.sqrt(dx) - Math.sqrt(dy)), core = Math.max(0, 1 - Math.hypot(dx, dy) * 2.6);
            c.a[(STAR.y + y) * SIZE + STAR.x + x] = (float) Math.min(1, arms * arms * arms * 2.4 + core * core * core);
        }
    }

    // ---- stroke helpers -----------------------------------------------------------------------------------------

    private interface Curve { double at(double x); }
    private static void polyline(Canvas c, Curve f, double xa, double xb, double w) {
        double[] pts = new double[2 * (int) Math.ceil((xb - xa) / 2 + 1)];
        for (int i = 0; i < pts.length / 2; i++) { double x = Math.min(xb, xa + 2 * i); pts[2 * i] = x; pts[2 * i + 1] = f.at(x); }
        c.poly(pts, w);
    }
    /** Elliptical spiral whose stroke tapers from w0 to w1; starts on the outer ellipse at angle {@code a0}. */
    private static void spiral(Canvas c, double cx, double cy, double rx, double ry, double a0, int dir, double turns, double w0, double w1) {
        int n = (int) (turns * 64);
        double[] pts = new double[2 * (n + 1)];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n, k = 1 - .84 * t * t * (1.25 - .25 * t), a = a0 + dir * turns * TAU * t;
            pts[2 * i] = cx + rx * k * Math.cos(a); pts[2 * i + 1] = cy + ry * k * Math.sin(a);
        }
        c.poly(pts, w0, w1);
    }
    /** Round puff: three octaves of value noise carve a soft, uneven billow whose alpha always falls smoothly to zero well inside the tile. */
    private static void cloud(Canvas c) {
        for (int y = 0; y < CLOUD.h; y++) for (int x = 0; x < CLOUD.w; x++) {
            double dx = (x + .5 - 64) / 62, dy = (y + .5 - 64) / 62, d = Math.hypot(dx, dy);
            double n = .45 * vnoise(x / 22.0, y / 22.0, 1) + .25 * vnoise(x / 11.0, y / 11.0, 2) + .15 * vnoise(x / 5.5, y / 5.5, 3) + .1 * vnoise(x / 2.7, y / 2.7, 4) + .05 * vnoise(x / 1.4, y / 1.4, 5);
            double f = clamp01Soft((1 - d) * 1.5 + (n - .5) * .9);
            c.a[(CLOUD.y + y) * SIZE + CLOUD.x + x] = (float) (f * f * (3 - 2 * f) * .95);
        }
    }
    private static double clamp01Soft(double v) { return Math.max(0, Math.min(1, v)); }
    /** Five octaves of value noise, 0..1. */
    private static double fbm(double x, double y, int salt) {
        double sum = 0, amp = .5, norm = 0;
        for (int o = 0; o < 5; o++) { sum += amp * vnoise(x, y, salt + o); norm += amp; x *= 2.03; y *= 2.03; amp *= .5; }
        return sum / norm;
    }
    private static double step01(double x) { x = clamp01Soft(x); return x * x * (3 - 2 * x); }
    /** Smoke: a mass of rounded billows (ridged noise, domain-warped so it curls) whose density thins out through an eroded, uneven edge and is zero at the tile's border. */
    private static void smoke(Canvas c) {
        for (int y = 0; y < SMOKE.h; y++) for (int x = 0; x < SMOKE.w; x++) {
            double u = (x + .5) / SMOKE.w * 2 - 1, v = (y + .5) / SMOKE.h * 2 - 1;
            double wx = x / 46.0 + 1.7 * (fbm(x / 60.0, y / 60.0, 41) - .5), wy = y / 46.0 + 1.7 * (fbm(x / 60.0 + 9, y / 60.0 + 4, 47) - .5);
            double billow = 1 - Math.abs(2 * fbm(wx, wy, 53) - 1), detail = fbm(x / 13.0, y / 13.0, 61);
            double edge = Math.hypot(u, v) + .55 * (fbm(x / 34.0, y / 34.0, 67) - .5);
            double mass = step01((.92 - edge) / .6);
            c.a[(SMOKE.y + y) * SIZE + SMOKE.x + x] = (float) (mass * clamp01Soft(.25 + .85 * billow * billow + .35 * (detail - .5)));
        }
    }
    /** A feather: a pointed vane with slanting barbs either side of a bright shaft. */
    private static void feather(Canvas c) {
        for (int y = 0; y < FEATHER.h; y++) for (int x = 0; x < FEATHER.w; x++) {
            double u = (x + .5) / FEATHER.w * 2 - 1, v = (y + .5) / FEATHER.h;
            double half = .44 * Math.pow(Math.sin(Math.PI * Math.pow(Math.min(1, v * 1.04), .75)), .7) * (1 - .3 * v);
            double vane = step01((half - Math.abs(u)) / .14) * (.5 + .4 * (.5 + .5 * Math.sin((v * 30 - Math.abs(u) * 16) * 1.6)));
            double shaft = Math.exp(-u * u / .004) * step01((v - .02) / .08) * step01((.99 - v) / .08);
            c.a[(FEATHER.y + y) * SIZE + FEATHER.x + x] = (float) clamp01Soft(vane + .85 * shaft);
        }
    }
    /** A wisp: smoke drawn out along U, thick in the middle of the strip and torn at its edges; both ends fade to nothing. */
    private static void wisp(Canvas c) {
        for (int y = 0; y < WISP.h; y++) for (int x = 0; x < WISP.w; x++) {
            double u = (x + .5) / WISP.w, v = (y + .5) / WISP.h * 2 - 1;
            double centre = .45 * (fbm(x / 70.0, 3, 71) - .5), strand = fbm(x / 90.0, y / 9.0, 73), fine = fbm(x / 20.0, y / 5.0, 79);
            double across = step01((.8 - Math.abs(v - centre) - .5 * (strand - .5)) / .55);
            c.a[(WISP.y + y) * SIZE + WISP.x + x] = (float) (across * Math.pow(Math.sin(Math.PI * u), .7) * clamp01Soft(.3 + .9 * strand * (.6 + .8 * fine)));
        }
    }
    /** Rays: fine bright lines running along V at uneven spacing and strength, each fading in and out along its length, under a soft envelope across U. */
    private static void rays(Canvas c) {
        for (int y = 0; y < RAYS.h; y++) for (int x = 0; x < RAYS.w; x++) {
            double u = (x + .5) / RAYS.w, v = (y + .5) / RAYS.h;
            double lines = 0;
            for (int k = 0; k < 9; k++) {
                double centre = .08 + .84 * lattice(k, 3, 83) + .02 * Math.sin(v * 5 + k), width = .012 + .03 * lattice(k, 5, 83), d = (u - centre) / width;
                lines = Math.max(lines, Math.exp(-d * d) * (.35 + .65 * vnoise(v * 3.5 + k * 7.1, k, 89)));
            }
            double haze = .5 * fbm(x / 9.0, y / 70.0, 97);
            c.a[(RAYS.y + y) * SIZE + RAYS.x + x] = (float) (Math.pow(Math.sin(Math.PI * u), 1.3) * clamp01Soft(.12 + haze * .5 + .8 * lines));
        }
    }
    private static double lattice(int x, int y, int salt) {
        double h = Math.sin(x * 127.1 + y * 311.7 + salt * 74.7) * 43758.5453;
        return h - Math.floor(h);
    }
    private static double vnoise(double x, double y, int salt) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        double a = lattice(ix, iy, salt), b = lattice(ix + 1, iy, salt), d = lattice(ix, iy + 1, salt), e = lattice(ix + 1, iy + 1, salt);
        return a + (b - a) * fx + (d - a) * fy + (a - b - d + e) * fx * fy;
    }
    /** Spiral that starts on the circle of radius {@code r} around (cx,cy) at angle {@code a0} and winds inward {@code turns} turns. */
    private static void curl(Canvas c, double cx, double cy, double r, double a0, int dir, double turns, double w) {
        int n = (int) (turns * 48);
        double[] pts = new double[2 * (n + 1)];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n, rr = r * (1 - .82 * t * t * (1.2 - .2 * t)), a = a0 + dir * turns * TAU * t;
            pts[2 * i] = cx + rr * Math.cos(a); pts[2 * i + 1] = cy + rr * Math.sin(a);
        }
        c.poly(pts, w);
    }

    /** Float coverage canvas: every stroke is an anti-aliased distance field with a faint soft shoulder, composited by max. */
    private static final class Canvas {
        final float[] a = new float[SIZE * SIZE];
        private int cx0, cy0, cx1, cy1; private boolean wrap; private int period;
        void clip(Region r, boolean wrapX) { cx0 = r.x; cy0 = r.y + 1; cx1 = r.x + r.w; cy1 = r.y + r.h - 1; wrap = wrapX; period = r.w; }
        void line(double x0, double y0, double x1, double y1, double w) { poly(new double[]{x0, y0, x1, y1}, w); }
        void dot(double x, double y, double r) { poly(new double[]{x, y, x + .01, y}, r * 2); }
        void arc(double cx, double cy, double r, double a0, double a1, double w) {
            int n = Math.max(8, (int) (Math.abs(a1 - a0) * r / 1.5));
            double[] p = new double[2 * (n + 1)];
            for (int i = 0; i <= n; i++) { double t = a0 + (a1 - a0) * i / n; p[2 * i] = cx + r * Math.cos(t); p[2 * i + 1] = cy + r * Math.sin(t); }
            poly(p, w);
        }
        void poly(double[] p, double w) { poly(p, w, w); }
        void poly(double[] p, double w, double wEnd) {
            for (int off = wrap ? -1 : 0; off <= (wrap ? 1 : 0); off++) {
                double dx = off * period, reach = Math.max(w, wEnd) / 2 + 4;
                double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
                for (int i = 0; i < p.length; i += 2) {
                    minX = Math.min(minX, p[i] + dx); maxX = Math.max(maxX, p[i] + dx);
                    minY = Math.min(minY, p[i + 1]); maxY = Math.max(maxY, p[i + 1]);
                }
                int xa = Math.max(cx0, (int) Math.floor(minX - reach - 4)), xb = Math.min(cx1 - 1, (int) Math.ceil(maxX + reach + 4));
                int ya = Math.max(cy0, (int) Math.floor(minY - reach - 4)), yb = Math.min(cy1 - 1, (int) Math.ceil(maxY + reach + 4));
                if (xa > xb || ya > yb) continue;
                for (int y = ya; y <= yb; y++) for (int x = xa; x <= xb; x++) {
                    double d = Double.MAX_VALUE; // signed distance to the stroke edge
                    int segs = p.length / 2 - 1;
                    for (int i = 0; i + 3 < p.length; i += 2) if (Math.min(p[i], p[i + 2]) + dx - reach <= x + .5 && Math.max(p[i], p[i + 2]) + dx + reach >= x + .5
                            && Math.min(p[i + 1], p[i + 3]) - reach <= y + .5 && Math.max(p[i + 1], p[i + 3]) + reach >= y + .5) {
                        double wi = segs <= 1 ? w : w + (wEnd - w) * (i / 2) / (segs - 1);
                        d = Math.min(d, distance(x + .5, y + .5, p[i] + dx, p[i + 1], p[i + 2] + dx, p[i + 3]) - wi / 2);
                    }
                    double core = Math.max(0, Math.min(1, .6 - d)), out = Math.max(0, d);
                    float v = (float) Math.max(core, .3 * Math.exp(-out * out / 4.5));
                    if (v > a[y * SIZE + x]) a[y * SIZE + x] = v;
                }
            }
        }
        private static double distance(double px, double py, double ax, double ay, double bx, double by) {
            double vx = bx - ax, vy = by - ay, len = vx * vx + vy * vy;
            double t = len < 1e-9 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (py - ay) * vy) / len));
            return Math.hypot(px - (ax + t * vx), py - (ay + t * vy));
        }
    }
}
