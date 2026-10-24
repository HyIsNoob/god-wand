package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.godwand.network.ImpactFxPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Four impact drawings, followed by a short light fade into the expanding explosion. */
public class CustomImpactFrameOverlay {
    public static final ResourceLocation[] FRAMES = {
            ResourceLocation.fromNamespaceAndPath("godwand", "textures/vfx/impact_frame_0.png"),
            ResourceLocation.fromNamespaceAndPath("godwand", "textures/vfx/impact_frame_1.png"),
            ResourceLocation.fromNamespaceAndPath("godwand", "textures/vfx/impact_frame_2.png"),
            ResourceLocation.fromNamespaceAndPath("godwand", "textures/vfx/impact_frame_3.png")
    };

    public static final int IMPACT_FRAME_TICKS = ImpactFxPayload.SUPERNOVA_IMPACT_TICKS;
    public static final int FADEOUT_TICKS = 8;     // Smooth whiteout fadeout
    public static final int TOTAL_DURATION = IMPACT_FRAME_TICKS + FADEOUT_TICKS;

    private static int currentTick = 0;
    private static boolean active = false;

    public static void trigger(int durationTicks) {
        currentTick = 0;
        active = true;
    }

    public static void trigger() {
        trigger(IMPACT_FRAME_TICKS);
    }

    public static void reset() {
        active = false;
        currentTick = 0;
    }

    public static void onClientTick() {
        if (active) {
            currentTick++;
            if (currentTick >= TOTAL_DURATION) {
                active = false;
                currentTick = 0;
            }
        }
    }

    public static void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!active || !net.godwand.config.GodWandConfig.CLIENT.impactFrames) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null) return;

        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        // Hold each drawing for two ticks before the explosion begins.
        if (currentTick < IMPACT_FRAME_TICKS) {
            int frameIndex = Math.min(FRAMES.length - 1, (currentTick * FRAMES.length) / IMPACT_FRAME_TICKS);
            ResourceLocation texture = FRAMES[frameIndex];

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            context.blit(texture, 0, 0, 0.0f, 0.0f, screenWidth, screenHeight, screenWidth, screenHeight);
        }

        if (currentTick >= IMPACT_FRAME_TICKS) {
            float fadeProgress = (float) (currentTick - IMPACT_FRAME_TICKS) / (float) FADEOUT_TICKS;
            fadeProgress = Mth.clamp(fadeProgress, 0.0f, 1.0f);

            // Đường cong mờ dần mượt mà (smoothstep / ease-out)
            float whiteAlpha = 1.0f - fadeProgress;
            whiteAlpha = 0.28f * whiteAlpha * whiteAlpha; // Êm ái ở phần đuôi

            if (whiteAlpha > 0.002f) {
                int alphaByte = Mth.clamp((int) (whiteAlpha * 255.0f), 0, 255);
                int whiteColor = (alphaByte << 24) | 0x00FFFFFF;
                context.fill(0, 0, screenWidth, screenHeight, whiteColor);
            }
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
