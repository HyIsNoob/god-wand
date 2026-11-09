package net.godwand.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** The eyes of Silent Gate (script v3): black until 15, they open 15..45, close smoothly 45..55 and open again 55..62; the vignette is gone by 62. */
final class GateEyelids {
    private GateEyelids() {}
    static final double OPEN_FROM = 15, OPEN_FULL = 45, CLOSED_AT = 55, REOPENED_AT = 62, VIGNETTE_TICKS = 62, FEATHER_PX = 24;

    private static double smoother(double t, double a, double b) { return GatePose.smoother(t, a, b); }

    /** 0 closed .. 1 open: black until 15, opens 15..45, closes 45..55 (shut at 55), opens again 55..62. */
    static double openness(double age) {
        double v = age < OPEN_FULL ? smoother(age, OPEN_FROM, OPEN_FULL) : age < CLOSED_AT ? 1 - smoother(age, OPEN_FULL, CLOSED_AT) : smoother(age, CLOSED_AT, REOPENED_AT);
        return Mth.clamp(v, 0, 1);
    }
    /** Half height of the eye opening in pixels at horizontal position u (-1..1): widest in the middle, so the lids look like an almond. */
    static double half(double open, double u, int height) { return height * .95 * open * (1 - .45 * u * u); }
    static double vignette(double age) { return 1 - GatePose.sm(age, OPEN_FROM, VIGNETTE_TICKS); }

    static void render(GuiGraphics g, double age, int width, int height, double guiScale) {
        double open = openness(age), vig = vignette(age);
        if (open >= 1 && vig <= 0) return;
        int feather = Math.max(4, (int) Math.round(FEATHER_PX / Math.max(1, guiScale)));
        int mid = height / 2;
        for (int x = 0; x < width; x++) {
            double u = 2 * (x + .5) / width - 1;
            int h = (int) Math.round(half(open, u, height));
            int top = Math.max(0, mid - h), bottom = Math.min(height, mid + h);
            if (top > 0) {
                g.fill(x, 0, x + 1, top, 0xFF000000);
                g.fillGradient(x, top, x + 1, Math.min(mid, top + feather), 0xFF000000, 0);
            }
            if (bottom < height) {
                g.fill(x, bottom, x + 1, height, 0xFF000000);
                g.fillGradient(x, Math.max(mid, bottom - feather), x + 1, bottom, 0, 0xFF000000);
            }
        }
        if (vig > 0) for (int x = 0; x < width; x += 2) {
            double u = 2 * (x + 1.0) / width - 1;
            int side = (int) (vig * .9 * Math.pow(Math.abs(u), 2.5) * 255), vv = (int) (vig * .5 * 255);
            if (side > 3) g.fill(x, 0, x + 2, height, side << 24);
            g.fillGradient(x, 0, x + 2, height / 4, vv << 24, 0);
            g.fillGradient(x, height - height / 4, x + 2, height, 0, vv << 24);
        }
    }
}
