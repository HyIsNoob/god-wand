package net.godwand.client;

import net.godwand.config.GodWandConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Blue/Red around the crosshair (owner, 2026-11-20): Red's charge as a thin ring that fills clockwise and flashes red-white once full; the
 * caster's Blue as a small inner ring, dashed and turning while it charges, solid and bright cyan once it is ready.
 */
public final class BlueRedHud {
    private BlueRedHud() {}

    public static void render(GuiGraphics g, float partial) {
        var mc = Minecraft.getInstance();
        var me = mc.player;
        if (me == null || !net.godwand.config.GodWandConfig.CLIENT.skillHud || mc.options.hideGui || mc.screen != null || PurpleCinematic.active() || !BlueRedHand.holds(me)) return;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        double now = BlueRedRenderer.now(partial);
        double red = BlueRedRenderer.redChargeAge(me.getId(), partial);
        if (red >= 0) {
            double full = Math.max(1, GodWandConfig.SERVER.redChargeTicks), k = Math.min(1, red / full);
            boolean done = k >= 1;
            int lit = done ? (Math.sin(now * .9) > 0 ? 0xFFFFE6E8 : 0xFFFF2A3A) : 0xFFFF3A4A;
            ring(g, cx, cy, 10, 48, k, lit, 0x40FF3A4A, 0);
            if (done) ring(g, cx, cy, 13, 56, 1, 0x80FF2A3A, 0, 0);
        }
        int blue = BlueRedRenderer.blueState(me.getId());
        if (blue >= 0) {
            if (blue == 1) ring(g, cx, cy, 6, 32, 1, 0xFF8EEBFF, 0, 0);
            else ring(g, cx, cy, 6, 32, 1, 0x903A8CFF, 0, now * .08);
        }
    }

    /** A ring of 1-pixel dots, {@code fill} of it (from the top, clockwise) in {@code on}, the rest in {@code off} (0: not drawn). A non-zero
     *  {@code spin} makes it dashed and turning. */
    private static void ring(GuiGraphics g, int cx, int cy, int r, int n, double fill, int on, int off, double spin) {
        for (int i = 0; i < n; i++) {
            if (spin != 0 && (i / 4) % 2 == 1) continue;
            double a = (double) i / n * Math.PI * 2 + spin;
            int x = cx + (int) Math.round(Math.sin(a) * r), y = cy - (int) Math.round(Math.cos(a) * r);
            int color = (double) i / n < fill ? on : off;
            if (color != 0) g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
