package net.godwand.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** The opening eyes of Silent Gate: two curved black lids with soft edges, one light blink, and a vignette that fades by tick 30. */
final class GateEyelids {
    private GateEyelids() {}
    static final double OPEN_TICKS = 20, BLINK_AT = 12, BLINK_LENGTH = 4, BLINK_DEPTH = .30, VIGNETTE_TICKS = 30, FEATHER_PX = 24;

    private static double smoother(double t, double a, double b) { return GatePose.smoother(t, a, b); }

    /** 0 closed .. 1 open: eased opening with a short blink (closes 30% of the way, then reopens) around tick {@value #BLINK_AT}. */
    static double openness(double age) {
        double base = smoother(age, 0, OPEN_TICKS), x = (age - (BLINK_AT - BLINK_LENGTH / 2)) / BLINK_LENGTH;
        double blink = x > 0 && x < 1 ? Math.sin(Math.PI * x) : 0;
        return Mth.clamp(base * (1 - BLINK_DEPTH * blink * blink), 0, 1);
    }
    /** Half height of the eye opening in pixels at horizontal position u (-1..1): widest in the middle, so the lids look like an almond. */
    static double half(double open, double u, int height) { return height * .95 * open * (1 - .45 * u * u); }
    static double vignette(double age) { return 1 - GatePose.sm(age, 0, VIGNETTE_TICKS); }

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
