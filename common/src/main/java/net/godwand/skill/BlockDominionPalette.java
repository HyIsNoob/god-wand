package net.godwand.skill;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Material Resonance color system for Block Dominion:
 * dynamically shifts the energy aura, wing feather plasma, lightning veins, smoke clouds,
 * and HUD accent color to match the dominant resonant blocks held in orbit.
 */
public enum BlockDominionPalette {
    SOVEREIGN_GOLD(
            248, 206, 84,
            255, 240, 174,
            30, 22, 14,
            148, 108, 38,
            0xEBC64E
    ),
    AMETHYST_ROYAL(
            202, 108, 255,
            246, 214, 255,
            24, 12, 42,
            128, 56, 186,
            0xCA6CFF
    ),
    INFERNO_OBSIDIAN(
            255, 106, 44,
            255, 224, 144,
            24, 12, 10,
            172, 54, 22,
            0xFF6A2C
    ),
    FROST_PRISMARINE(
            84, 218, 255,
            224, 250, 255,
            12, 30, 44,
            42, 138, 176,
            0x54DAFF
    ),
    EMERALD_VERDANT(
            74, 238, 148,
            214, 255, 228,
            12, 32, 20,
            40, 144, 86,
            0x4AEE94
    );

    public final int primaryR, primaryG, primaryB;
    public final int hotR, hotG, hotB;
    public final int darkR, darkG, darkB;
    public final int midR, midG, midB;
    public final int hexAccent;

    BlockDominionPalette(int primaryR, int primaryG, int primaryB,
                         int hotR, int hotG, int hotB,
                         int darkR, int darkG, int darkB,
                         int midR, int midG, int midB,
                         int hexAccent) {
        this.primaryR = primaryR;
        this.primaryG = primaryG;
        this.primaryB = primaryB;
        this.hotR = hotR;
        this.hotG = hotG;
        this.hotB = hotB;
        this.darkR = darkR;
        this.darkG = darkG;
        this.darkB = darkB;
        this.midR = midR;
        this.midG = midG;
        this.midB = midB;
        this.hexAccent = hexAccent;
    }

    /** Classifies a block registry path (e.g. "amethyst_block", "gilded_blackstone") into a resonance palette. */
    public static BlockDominionPalette classifyBlockPath(String path) {
        if (path == null || path.isEmpty()) return SOVEREIGN_GOLD;
        String p = path.toLowerCase(java.util.Locale.ROOT);
        if (p.contains("amethyst") || p.contains("purpur") || p.contains("crying_obsidian")
                || p.contains("shulker") || p.contains("chorus") || p.contains("magenta") || p.contains("purple")) {
            return AMETHYST_ROYAL;
        }
        if (p.contains("magma") || p.contains("netherrack") || p.contains("nether_brick")
                || p.contains("obsidian") || p.contains("crimson") || p.contains("redstone")
                || p.contains("blaze") || p.contains("basalt") || p.contains("ancient_debris")
                || p.contains("blackstone") && !p.contains("gilded")) {
            return INFERNO_OBSIDIAN;
        }
        if (p.contains("ice") || p.contains("snow") || p.contains("prismarine")
                || p.contains("diamond") || p.contains("lapis") || p.contains("warped")
                || p.contains("sculk") || p.contains("oxidized") || p.contains("cyan") || p.contains("light_blue")) {
            return FROST_PRISMARINE;
        }
        if (p.contains("emerald") || p.contains("moss") || p.contains("slime")
                || p.contains("bamboo") || p.contains("leaves") || p.contains("cactus")
                || p.contains("lime") || p.contains("green_")) {
            return EMERALD_VERDANT;
        }
        return SOVEREIGN_GOLD;
    }

    /**
     * Resolves the active resonance palette from a collection of block registry paths.
     * Exotic palettes activate once they represent at least 30% of the reserve (or >= 4 blocks and plural leader),
     * otherwise the iconic Sovereign Gold palette anchors mixed earth/stone/gold formations.
     */
    public static BlockDominionPalette resolvePaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) return SOVEREIGN_GOLD;
        int[] counts = new int[values().length];
        for (String path : paths) {
            counts[classifyBlockPath(path).ordinal()]++;
        }
        int total = paths.size();
        int bestIdx = SOVEREIGN_GOLD.ordinal();
        int bestCount = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > bestCount) {
                bestCount = counts[i];
                bestIdx = i;
            }
        }
        if (bestIdx != SOVEREIGN_GOLD.ordinal() && bestCount >= Math.max(3, (int) Math.ceil(total * 0.30))) {
            return values()[bestIdx];
        }
        return SOVEREIGN_GOLD;
    }

    /** Resolves resonance directly from live Minecraft BlockStates on client or server. */
    public static BlockDominionPalette resolveStates(List<BlockState> states) {
        if (states == null || states.isEmpty()) return SOVEREIGN_GOLD;
        List<String> paths = new ArrayList<>(states.size());
        for (BlockState state : states) {
            if (state == null) continue;
            paths.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
        }
        return resolvePaths(paths);
    }
}
