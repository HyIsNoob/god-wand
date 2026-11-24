package net.godwand.client;

import net.godwand.config.GodWandConfig;
import net.godwand.skill.BlockDominionBrain;
import net.godwand.skill.BlockDominionPalette;
import net.godwand.skill.BlockDominionRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Sleek minimal HUD for Block Dominion (respects GodWandConfig.CLIENT.skillHud):
 * - Compact resonant-accent badge to the right of the hotbar showing active mode
 *   (MANUAL / AUTO / WINGS / MACH / STAIRS / RAILGUN), block reserve bar (e.g. 48/48),
 *   and live flight speed bar while airborne with Block Wings.
 * - Thin clockwise crosshair charge arc (matching BlueRedHud) while charging Myriad Volley
 *   or Aerial Orbital Meteor Barrage.
 */
public final class BlockDominionHud {
    private BlockDominionHud() {}

    public static void render(GuiGraphics g, BlockDominionRules.Mode mode, BlockDominionBrain.FormationKind formation,
                              int count, int capacity, boolean flying, boolean glideMode, boolean boosting,
                              boolean stairAscending, boolean volleyCharging, float volleyProgress,
                              BlockDominionPalette palette, double speedBlocksPerTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || !GodWandConfig.CLIENT.skillHud
                || mc.options.hideGui || mc.screen != null) {
            return;
        }
        if (AtomicCinematic.active() || FugaCinematic.active()
                || SilentGateCinematic.active() || PurpleCinematic.active()) {
            return;
        }
        if (mode == BlockDominionRules.Mode.OFF) return;

        int accent = 0xFF000000 | palette.hexAccent;
        int accentSoft = 0x55000000 | palette.hexAccent;
        int hot = 0xFF000000 | ((palette.hotR << 16) | (palette.hotG << 8) | palette.hotB);

        // 1. Crosshair clockwise charge ring while charging Volley / Orbital Meteor Barrage
        if (volleyCharging) {
            int cx = g.guiWidth() / 2;
            int cy = g.guiHeight() / 2;
            float p = Mth.clamp(volleyProgress, 0f, 1f);
            int ringCol = p >= 0.98f ? hot : accent;
            drawRing(g, cx, cy, 11, 48, p, ringCol, accentSoft);
            if (p >= 0.98f) {
                drawRing(g, cx, cy, 14, 56, 1.0f, 0x90000000 | palette.hexAccent, 0);
            }
        }

        // 2. Compact status badge tucked cleanly beside the right edge of the hotbar (never collides with chat/hearts)
        Font font = mc.font;
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int panelW = 86;
        int panelH = flying ? 32 : 24;
        int x = Math.min(sw - panelW - 6, sw / 2 + 98);
        int y = sh - panelH - 4;

        // Translucent dark obsidian card + resonant left accent stripe & 1px frame
        g.fill(x, y, x + panelW, y + panelH, 0xA8101016);
        g.fill(x, y, x + 2, y + panelH, accent);
        g.fill(x, y, x + panelW, y + 1, accentSoft);
        g.fill(x, y + panelH - 1, x + panelW, y + panelH, accentSoft);
        g.fill(x + panelW - 1, y, x + panelW, y + panelH, accentSoft);

        String tag;
        if (mode == BlockDominionRules.Mode.AUTO) {
            tag = "AUTO";
        } else if (formation == BlockDominionBrain.FormationKind.METEOR_DIVE
                || formation == BlockDominionBrain.FormationKind.ORBITAL_METEOR) {
            tag = "METEOR";
        } else if (formation == BlockDominionBrain.FormationKind.VOLLEY_FAN) {
            tag = "VOLLEY";
        } else if (formation == BlockDominionBrain.FormationKind.SOVEREIGN_PERCH) {
            tag = "PERCH";
        } else if (boosting) {
            tag = glideMode ? "JET" : "MACH";
        } else if (flying) {
            tag = glideMode ? "GLIDE" : "HOVER";
        } else if (formation == BlockDominionBrain.FormationKind.LIQUID_STRIDE) {
            tag = "STRIDE";
        } else if (formation == BlockDominionBrain.FormationKind.AIR_STAIRS) {
            tag = stairAscending ? "STAIR ▲" : "STAIR ▼";
        } else if (formation == BlockDominionBrain.FormationKind.RAIN_UMBRELLA) {
            tag = "CANOPY";
        } else if (formation == BlockDominionBrain.FormationKind.RAILGUN_RING) {
            tag = "RAILGUN";
        } else if (formation.isShield()) {
            tag = "SHIELD";
        } else {
            tag = "DOMINION";
        }

        int tagColor = (boosting || formation.isVolley() || formation.isMeteorDive()) ? hot : accent;
        g.drawString(font, tag, x + 6, y + 4, tagColor, false);

        if (mode == BlockDominionRules.Mode.MANUAL) {
            String num = count + "/" + capacity;
            int numW = font.width(num);
            g.drawString(font, num, x + panelW - numW - 4, y + 4, 0xFFEFECE6, false);

            // Reserve fill bar
            int barX = x + 6;
            int barY = y + 15;
            int barW = panelW - 10;
            g.fill(barX, barY, barX + barW, barY + 4, 0x661C1C24);
            float fill = capacity > 0 ? Mth.clamp(count / (float) capacity, 0f, 1f) : 0f;
            int fillW = Math.round(barW * fill);
            if (fillW > 0) {
                g.fill(barX, barY, barX + fillW, barY + 4, accent);
                g.fill(barX, barY, barX + fillW, barY + 1, hot);
            }

            // Live speed / Mach bar while Wing Flight is active (normalized to full-48 peak speed so 24 vs 48 depth is visible!)
            if (flying) {
                int spY = y + 23;
                g.fill(barX, spY, barX + barW, spY + 3, 0x661C1C24);
                double refMax = glideMode ? BlockDominionRules.GLIDE_MAX_SPEED : BlockDominionRules.FLIGHT_BOOST_SPEED;
                float spFill = Mth.clamp((float) (speedBlocksPerTick / refMax), 0f, 1f);
                int spW = Math.round(barW * spFill);
                if (spW > 0) {
                    g.fill(barX, spY, barX + spW, spY + 3, boosting ? hot : accent);
                }
            }
        } else {
            g.drawString(font, "FIELD", x + panelW - font.width("FIELD") - 4, y + 4, 0xFFDAD4C4, false);
            int barX = x + 6;
            int barY = y + 15;
            int barW = panelW - 10;
            g.fill(barX, barY, barX + barW, barY + 4, accentSoft);
        }
    }

    private static void drawRing(GuiGraphics g, int cx, int cy, int radius, int steps, float fill, int onColor, int offColor) {
        for (int i = 0; i < steps; i++) {
            float frac = i / (float) steps;
            double ang = frac * Math.PI * 2.0;
            int px = cx + (int) Math.round(Math.sin(ang) * radius);
            int py = cy - (int) Math.round(Math.cos(ang) * radius);
            int col = frac < fill ? onColor : offColor;
            if (col != 0) g.fill(px, py, px + 1, py + 1, col);
        }
    }
}
