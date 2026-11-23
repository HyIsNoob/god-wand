package net.godwand.skill;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlockDominionPaletteTest {

    @Test
    void classifiesExoticAndDefaultBlocksAccurately() {
        assertEquals(BlockDominionPalette.AMETHYST_ROYAL, BlockDominionPalette.classifyBlockPath("amethyst_block"));
        assertEquals(BlockDominionPalette.AMETHYST_ROYAL, BlockDominionPalette.classifyBlockPath("crying_obsidian"));
        assertEquals(BlockDominionPalette.INFERNO_OBSIDIAN, BlockDominionPalette.classifyBlockPath("obsidian"));
        assertEquals(BlockDominionPalette.INFERNO_OBSIDIAN, BlockDominionPalette.classifyBlockPath("magma_block"));
        assertEquals(BlockDominionPalette.FROST_PRISMARINE, BlockDominionPalette.classifyBlockPath("packed_ice"));
        assertEquals(BlockDominionPalette.FROST_PRISMARINE, BlockDominionPalette.classifyBlockPath("diamond_block"));
        assertEquals(BlockDominionPalette.EMERALD_VERDANT, BlockDominionPalette.classifyBlockPath("emerald_block"));
        assertEquals(BlockDominionPalette.SOVEREIGN_GOLD, BlockDominionPalette.classifyBlockPath("gold_block"));
        assertEquals(BlockDominionPalette.SOVEREIGN_GOLD, BlockDominionPalette.classifyBlockPath("gilded_blackstone"));
        assertEquals(BlockDominionPalette.SOVEREIGN_GOLD, BlockDominionPalette.classifyBlockPath("deepslate_bricks"));
    }

    @Test
    void resolvesMaterialResonanceWhenExoticShareReachesThreshold() {
        assertEquals(BlockDominionPalette.SOVEREIGN_GOLD, BlockDominionPalette.resolvePaths(List.of()));
        assertEquals(BlockDominionPalette.SOVEREIGN_GOLD, BlockDominionPalette.resolvePaths(
                List.of("stone", "stone", "stone", "stone", "stone", "stone", "amethyst_block")));

        assertEquals(BlockDominionPalette.AMETHYST_ROYAL, BlockDominionPalette.resolvePaths(
                List.of("stone", "stone", "amethyst_block", "amethyst_block", "purpur_block")));
        assertEquals(BlockDominionPalette.INFERNO_OBSIDIAN, BlockDominionPalette.resolvePaths(
                List.of("deepslate", "obsidian", "magma_block", "netherrack", "obsidian")));
        assertEquals(BlockDominionPalette.FROST_PRISMARINE, BlockDominionPalette.resolvePaths(
                List.of("stone", "packed_ice", "blue_ice", "prismarine")));
    }
}
