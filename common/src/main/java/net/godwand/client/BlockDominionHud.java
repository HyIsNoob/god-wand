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
 * Minimalist arcane HUD for Block Dominion (respects GodWandConfig.CLIENT.skillHud):
 * - Frameless feathered backdrop (no heavy industrial rectangle box!)
 * - Sculpted pixel-diamond rune emblem tinted by active Material Resonance
 * - Clean ivory block counter, delicate 2px arcane hairline gauge, subtle flight speed hairline,
 *   and tiny diamond pips when Monolith Cages (up to 3) are active.
 * - Thin clockwise crosshair charge arc while charging Myriad Volley / Orbital Barrage.
 */
public final class BlockDominionHud {
    private BlockDominionHud() {}

    public static void render(GuiGraphics g, BlockDominionRules.Mode mode, BlockDominionBrain.FormationKind formation,
                              int count, int capacity, boolean flying, boolean glideMode, boolean boosting,
                              boolean stairAscending, boolean volleyCharging, float volleyProgress,
                              BlockDominionPalette palette, double speedBlocksPerTick) {
        render(g, mode, formation, count, capacity, flying, glideMode, boosting,
                stairAscending, volleyCharging, volleyProgress, palette, speedBlocksPerTick, 0, 0);
    }

    public static void render(GuiGraphics g, BlockDominionRules.Mode mode, BlockDominionBrain.FormationKind formation,
                              int count, int capacity, boolean flying, boolean glideMode, boolean boosting,
                              boolean stairAscending, boolean volleyCharging, float volleyProgress,
                              BlockDominionPalette palette, double speedBlocksPerTick, int meteorTier) {
        render(g, mode, formation, count, capacity, flying, glideMode, boosting,
                stairAscending, volleyCharging, volleyProgress, palette, speedBlocksPerTick, meteorTier, 0);
    }

    public static void render(GuiGraphics g, BlockDominionRules.Mode mode, BlockDominionBrain.FormationKind formation,
                              int count, int capacity, boolean flying, boolean glideMode, boolean boosting,
                              boolean stairAscending, boolean volleyCharging, float volleyProgress,
                              BlockDominionPalette palette, double speedBlocksPerTick, int meteorTier, int activeCages) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || !GodWandConfig.CLIENT.skillHud
                || mc.options.hideGui || mc.screen != null) {
            return;
        }
        if (AtomicCinematic.active() || FugaCinematic.active()
                || SilentGateCinematic.active() || PurpleCinematic.active()) {
            return;
        }
        if (mode != BlockDominionRules.Mode.MANUAL) return;

        int accent = 0xFF000000 | palette.hexAccent;
        int accentDim = 0x55000000 | palette.hexAccent;
        int hot = 0xFF000000 | ((palette.hotR << 16) | (palette.hotG << 8) | palette.hotB);
        if (formation == BlockDominionBrain.FormationKind.METEOR_DIVE && meteorTier >= 1) {
            int flameHex = meteorTier >= 3 ? 0xFF451A : (meteorTier == 2 ? 0xFF8224 : 0xFFAE36);
            accent = 0xFF000000 | flameHex;
            accentDim = 0x66000000 | flameHex;
            hot = meteorTier >= 3 ? 0xFFFFF4BC : 0xFFFFD074;
        }

        // 1. Delicate clockwise crosshair charge ring while charging Myriad Volley / Orbital Barrage
        if (volleyCharging) {
            int cx = g.guiWidth() / 2;
            int cy = g.guiHeight() / 2;
            float p = Mth.clamp(volleyProgress, 0f, 1f);
            int ringCol = p >= 0.98f ? hot : accent;
            drawRing(g, cx, cy, 11, 48, p, ringCol, accentDim);
            if (p >= 0.98f) {
                drawRing(g, cx, cy, 14, 56, 1.0f, 0x88000000 | palette.hexAccent, 0);
            }
        }

        // Only surface a refined Title-Case state whisper for meaningful modes (no noisy ALL-CAPS idle/railgun labels!)
        String stateLabel = null;
        if (formation == BlockDominionBrain.FormationKind.METEOR_DIVE) {
            stateLabel = meteorTier >= 3 ? "Cataclysm" : (meteorTier == 2 ? "Meteor II" : "Meteor I");
        } else if (formation == BlockDominionBrain.FormationKind.ORBITAL_METEOR) {
            stateLabel = "Orbital";
        } else if (formation == BlockDominionBrain.FormationKind.VOLLEY_FAN) {
            stateLabel = "Volley";
        } else if (formation == BlockDominionBrain.FormationKind.SOVEREIGN_PERCH) {
            stateLabel = "Perch";
        } else if (boosting) {
            stateLabel = glideMode ? "Jet" : "Mach";
        } else if (flying) {
            stateLabel = glideMode ? "Glide" : "Hover";
        } else if (formation.isShield()) {
            stateLabel = "Aegis";
        } else if (formation == BlockDominionBrain.FormationKind.RAIN_UMBRELLA) {
            stateLabel = "Canopy";
        }

        Font font = mc.font;
        String countText = count + " / " + capacity;
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int labelW = stateLabel != null ? font.width(stateLabel) + 8 : 0;
        int barW = Math.max(72, Math.max(font.width(countText) + 14 + labelW, 84));
        int x = Math.min(sw - barW - 10, sw / 2 + 100);
        int baseY = sh - (flying ? 26 : 21);

        // Soft frameless feathered dark gradient behind text & hairline (zero hard industrial rectangle!)
        g.fillGradient(x - 6, baseY - 4, x + barW + 6, baseY + (flying ? 21 : 16), 0x00090910, 0x62090910);

        // Sculpted 5x5 pixel-diamond arcane gem emblem on the left
        drawDiamond(g, x + 2, baseY + 3, accent, hot);

        // Clean warm-ivory block count with crisp shadow
        g.drawString(font, countText, x + 10, baseY, 0xFFF5F1E6, true);

        // Subtle state whisper on the right (only when flying / shielding / volley / meteor)
        if (stateLabel != null) {
            int stW = font.width(stateLabel);
            int stColor = (boosting || formation.isVolley() || formation.isMeteorDive()) ? hot : accent;
            g.drawString(font, stateLabel, x + barW - stW, baseY, stColor, true);
        }

        // Delicate 2px arcane filigree reserve gauge with subtle 1px end-cap pins
        int barY = baseY + 11;
        g.fill(x, barY, x + barW, barY + 2, 0x4814141D);
        g.fill(x - 1, barY - 1, x, barY + 3, accentDim);
        g.fill(x + barW, barY - 1, x + barW + 1, barY + 3, accentDim);
        float fill = capacity > 0 ? Mth.clamp(count / (float) capacity, 0f, 1f) : 0f;
        int fillW = Math.round(barW * fill);
        if (fillW > 0) {
            g.fill(x, barY, x + fillW, barY + 2, accent);
            g.fill(x, barY, x + fillW, barY + 1, hot);
            // Bright glowing tip spark at the live fill edge
            g.fill(x + fillW - 1, barY - 1, x + fillW + 1, barY + 3, hot);
        }

        // Sleek 1px speed hairline only while airborne with Block Wings
        if (flying) {
            int spY = barY + 5;
            g.fill(x, spY, x + barW, spY + 1, 0x3A14141D);
            double refMax = glideMode ? BlockDominionRules.GLIDE_MAX_SPEED : BlockDominionRules.FLIGHT_BOOST_SPEED;
            float spFill = Mth.clamp((float) (speedBlocksPerTick / refMax), 0f, 1f);
            int spW = Math.round(barW * spFill);
            if (spW > 0) {
                g.fill(x, spY, x + spW, spY + 1, boosting ? hot : accent);
            }
        }

        // Tiny glowing diamond pips above the hairline ONLY when Monolith Cages are active (1..3)
        if (activeCages > 0) {
            int pipY = baseY - 7;
            for (int c = 0; c < BlockDominionRules.CAGE_MAX_ACTIVE; c++) {
                boolean lit = c < activeCages;
                drawSmallDiamond(g, x + 2 + c * 8, pipY, lit ? hot : 0x44888898);
            }
        }
    }

    /**
     * Simple Minecraft-style controls guide in the bottom-left corner for Block Dominion.
     * Gated independently by {@link GodWandConfig.Client#dominionGuide} (separate from {@code skillHud}).
     */
    public static void renderGuide(GuiGraphics g, boolean active, boolean flying, BlockDominionPalette palette) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || !GodWandConfig.CLIENT.dominionGuide
                || mc.options.hideGui || mc.screen != null) {
            return;
        }
        if (AtomicCinematic.active() || FugaCinematic.active()
                || SilentGateCinematic.active() || PurpleCinematic.active()) {
            return;
        }
        boolean holding = BlockDominionInput.eligible(mc.player);
        if (!holding && !active) return;

        String[][] rows = flying
                ? new String[][]{
                        {"Shift + Space", "Hover / Glide"},
                        {"Ctrl / Space", "Boost"},
                        {"Look Down", "Meteor Dive"},
                        {"Shift + RMB", "Orbital Volley"},
                        {"2x Space", "Land"}
                }
                : new String[][]{
                        {"Shift + LMB", active ? "Off" : "On"},
                        {"LMB Block", "Lift"},
                        {"LMB Air", "Surge"},
                        {"LMB Mob", "Cage (8)"},
                        {"RMB / Hold", "Fire / Volley"},
                        {"2x Space", "Wings (6+)"}
                };

        Font font = mc.font;
        int maxKeyW = 0;
        int maxDescW = 0;
        for (String[] row : rows) {
            maxKeyW = Math.max(maxKeyW, font.width(row[0]));
            maxDescW = Math.max(maxDescW, font.width(row[1]));
        }
        int padX = 5;
        int padY = 2;
        int colGap = 10;
        int rowH = 8;
        int boxW = maxKeyW + colGap + maxDescW + padX * 2;
        int boxH = padY * 2 + rows.length * rowH + 1;

        float scale = 0.66f;
        int margin = 3;
        float screenX = margin;
        float screenY = g.guiHeight() - margin - boxH * scale;

        g.pose().pushPose();
        g.pose().translate(screenX, screenY, 0.0f);
        g.pose().scale(scale, scale, 1.0f);

        // Plain vanilla Minecraft translucent dark backdrop (like F3 / chat — zero neon borders or glowing accents!)
        g.fill(0, 0, boxW, boxH, 0x66000000);

        int y = padY;
        for (String[] row : rows) {
            g.drawString(font, row[0], padX, y, 0xFFFFFFFF, true);
            g.drawString(font, row[1], padX + maxKeyW + colGap, y, 0xFFB8B8B8, true);
            y += rowH;
        }

        g.pose().popPose();
    }

    private static void drawDiamond(GuiGraphics g, int cx, int cy, int rim, int core) {
        g.fill(cx, cy - 2, cx + 1, cy + 3, rim);
        g.fill(cx - 2, cy, cx + 3, cy + 1, rim);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, rim);
        g.fill(cx, cy, cx + 1, cy + 1, core);
    }

    private static void drawSmallDiamond(GuiGraphics g, int cx, int cy, int col) {
        g.fill(cx, cy - 1, cx + 1, cy + 2, col);
        g.fill(cx - 1, cy, cx + 2, cy + 1, col);
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
