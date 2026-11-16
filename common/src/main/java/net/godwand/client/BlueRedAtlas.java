package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;

/**
 * The Blue/Red/Purple VFX atlas, authored by tools/blue-red-atlas.py: 1024 square like GateAtlas (so GateAtlas.Region and GateDraw's solid texel at
 * (504, 504) work unchanged), white shapes tinted per vertex.
 */
final class BlueRedAtlas {
    private BlueRedAtlas() {}
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("godwand", "textures/vfx/blue_red.png");
    static final GateAtlas.Region GLOW = new GateAtlas.Region(0, 0, 128, 128), STAR = new GateAtlas.Region(128, 0, 128, 128),
            BUBBLE = new GateAtlas.Region(256, 0, 64, 64), LIGHT = new GateAtlas.Region(320, 0, 64, 64), SHOCK = new GateAtlas.Region(384, 0, 128, 128),
            /** Turbulent plasma, two periods side by side: U 0..0.5 is one full turn. */
            SURFACE = new GateAtlas.Region(0, 512, 512, 256), RAYS = new GateAtlas.Region(512, 768, 256, 256), SOLID = GateAtlas.SOLID;
    /** Flame tongues: base at V 1, tip at V 0. */
    static final GateAtlas.Region[] FLAME = region(4, 512, 0, 128, 256, 128, 0);
    static final GateAtlas.Region[] SMOKE = region(4, 0, 256, 248, 248, 256, 0);
    /** Brush strokes along U: thin tail at U 0, full head at U 1. */
    static final GateAtlas.Region[] INK = region(4, 512, 512, 512, 64, 0, 64);
    static final GateAtlas.Region[] SLASH = region(2, 0, 768, 512, 64, 0, 64);

    private static GateAtlas.Region[] region(int n, int x, int y, int w, int h, int dx, int dy) {
        GateAtlas.Region[] out = new GateAtlas.Region[n];
        for (int i = 0; i < n; i++) out[i] = new GateAtlas.Region(x + i * dx, y + i * dy, w, h);
        return out;
    }

    static void bind() { RenderSystem.setShaderTexture(0, TEXTURE); }
}
