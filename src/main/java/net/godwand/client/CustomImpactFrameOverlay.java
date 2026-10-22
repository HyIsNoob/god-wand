package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

/**
 * Placeholder system for custom hand-drawn 2D anime impact frames.
 * Displays 4 frames in sequence on screen during the climax of Cosmic Supernova (Chiêu 4).
 * Players can replace impact_frame_0.png .. impact_frame_3.png in assets/godwand/textures/vfx/
 * with their own drawings anytime.
 */
public class CustomImpactFrameOverlay {
    public static final Identifier[] FRAMES = {
            Identifier.of("godwand", "textures/vfx/impact_frame_0.png"),
            Identifier.of("godwand", "textures/vfx/impact_frame_1.png"),
            Identifier.of("godwand", "textures/vfx/impact_frame_2.png"),
            Identifier.of("godwand", "textures/vfx/impact_frame_3.png")
    };

    private static int activeTicksRemaining = 0;
    private static int totalDuration = 0;

    public static void trigger(int durationTicks) {
        activeTicksRemaining = Math.max(activeTicksRemaining, durationTicks);
        totalDuration = activeTicksRemaining;
    }

    public static void reset() {
        activeTicksRemaining = 0;
        totalDuration = 0;
    }

    public static void onClientTick() {
        if (activeTicksRemaining > 0) {
            activeTicksRemaining--;
        }
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (activeTicksRemaining <= 0 || totalDuration <= 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        int elapsed = totalDuration - activeTicksRemaining;
        int frameCount = FRAMES.length;
        int frameIndex = Math.min(frameCount - 1, (elapsed * frameCount) / totalDuration);

        Identifier texture = FRAMES[frameIndex];

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        float alpha = (activeTicksRemaining <= 2) ? (activeTicksRemaining / 2.0f) : 1.0f;
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

        context.drawTexture(texture, 0, 0, 0.0f, 0.0f, screenWidth, screenHeight, screenWidth, screenHeight);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }
}
