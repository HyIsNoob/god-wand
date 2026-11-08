package net.godwand.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GateAtlasTest {
    private static int a(byte[] px, int x, int y) { return px[y * GateAtlas.SIZE + x] & 255; }

    private static double coverage(byte[] px, GateAtlas.Region r) {
        long sum = 0;
        for (int y = r.y(); y < r.y() + r.h(); y++) for (int x = r.x(); x < r.x() + r.w(); x++) sum += a(px, x, y);
        return sum / 255.0 / (r.w() * r.h());
    }

    @Test void atlasIs512SquareAndDeterministic() {
        byte[] px = GateAtlas.alpha();
        assertEquals(512 * 512, px.length);
        assertSame(px, GateAtlas.alpha());
    }

    @Test void everyRegionIsNeitherEmptyNorSolid() {
        byte[] px = GateAtlas.alpha();
        for (GateAtlas.Region r : GateAtlas.STRIPS) {
            double c = coverage(px, r);
            assertTrue(c > .06 && c < .5, r + " coverage " + c);
        }
        double seal = coverage(px, GateAtlas.SEAL);
        assertTrue(seal > .06 && seal < .5, "seal coverage " + seal);
        assertEquals(255, a(px, GateAtlas.SOLID.x() + 3, GateAtlas.SOLID.y() + 3));
    }

    @Test void stripsTileSeamlesslyAtTheUEdges() {
        byte[] px = GateAtlas.alpha();
        for (GateAtlas.Region r : GateAtlas.STRIPS) {
            double seam = 0, inner = 0;
            for (int y = r.y(); y < r.y() + r.h(); y++) {
                seam += Math.abs(a(px, r.x(), y) - a(px, r.x() + r.w() - 1, y));
                for (int x = r.x(); x < r.x() + r.w() - 1; x++) inner += Math.abs(a(px, x, y) - a(px, x + 1, y));
            }
            // Wrapping neighbours differ no more than ordinary neighbours do.
            double rows = r.h(), cols = r.w() - 1;
            assertTrue(seam / rows <= 2 * inner / (rows * cols) + 6, r + " seam " + seam / rows + " vs " + inner / (rows * cols));
        }
    }

    @Test void sealIsEightFoldSymmetricAndCornersAreEmpty() {
        byte[] px = GateAtlas.alpha();
        var s = GateAtlas.SEAL;
        assertEquals(0, a(px, s.x() + 1, s.y() + 1));
        double diff = 0, sum = 0; int n = 0;
        for (int y = 20; y < 236; y += 3) for (int x = 20; x < 236; x += 3) {
            // A quarter turn about the centre (127.5,127.5) maps pixel (x,y) to (255-y, x).
            diff += Math.abs(a(px, s.x() + x, s.y() + y) - a(px, s.x() + 255 - y, s.y() + x));
            sum += a(px, s.x() + x, s.y() + y); n++;
        }
        assertTrue(diff / n < .25 * sum / n + 4, "quarter-turn symmetry " + diff / n + " vs mean " + sum / n);
    }

    @Test void dumpForInspection() throws Exception {
        String dir = System.getenv("GATE_ATLAS_DUMP");
        if (dir == null) return;
        byte[] px = GateAtlas.alpha();
        var img = new java.awt.image.BufferedImage(512, 512, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++) { int v = a(px, x, y); img.setRGB(x, y, v << 16 | v << 8 | v); }
        javax.imageio.ImageIO.write(img, "png", new java.io.File(dir, "atlas.png"));
    }
}
