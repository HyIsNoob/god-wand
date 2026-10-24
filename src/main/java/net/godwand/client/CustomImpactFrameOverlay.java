package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.godwand.network.ImpactFxPayload;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** Four impact drawings, followed by a short light fade into the expanding explosion. */
public class CustomImpactFrameOverlay {
    public static final Identifier[] FRAMES = {
            Identifier.of("godwand", "textures/vfx/impact_frame_0.png"),
            Identifier.of("godwand", "textures/vfx/impact_frame_1.png"),
            Identifier.of("godwand", "textures/vfx/impact_frame_2.png"),
            Identifier.of("godwand", "textures/vfx/impact_frame_3.png")
    };

    public static final int IMPACT_FRAME_TICKS = ImpactFxPayload.SUPERNOVA_IMPACT_TICKS;
    public static final int FADEOUT_TICKS = 3;     // Smooth whiteout fadeout
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

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!active || !net.godwand.config.GodWandConfig.CLIENT.impactFrames) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        // Hold each drawing for two ticks before the explosion begins.
        if (currentTick < IMPACT_FRAME_TICKS) {
            int frameIndex = Math.min(FRAMES.length - 1, (currentTick * FRAMES.length) / IMPACT_FRAME_TICKS);
            Identifier texture = FRAMES[frameIndex];

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            context.drawTexture(texture, 0, 0, 0.0f, 0.0f, screenWidth, screenHeight, screenWidth, screenHeight);
        }

        if (currentTick >= IMPACT_FRAME_TICKS) {
            float fadeProgress = (float) (currentTick - IMPACT_FRAME_TICKS) / (float) FADEOUT_TICKS;
            fadeProgress = MathHelper.clamp(fadeProgress, 0.0f, 1.0f);

            // Đường cong mờ dần mượt mà (smoothstep / ease-out)
            float whiteAlpha = 1.0f - fadeProgress;
            whiteAlpha = 0.28f * whiteAlpha * whiteAlpha; // Êm ái ở phần đuôi

            if (whiteAlpha > 0.002f) {
                int alphaByte = MathHelper.clamp((int) (whiteAlpha * 255.0f), 0, 255);
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
