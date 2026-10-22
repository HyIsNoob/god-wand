package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * 2D Anime Impact Frame & Smooth Whiteout Aftermath Overlay for Cosmic Supernova (Chiêu 4).
 * - Phase 1: 4 Hand-drawn anime impact frames flash in rapid succession (6 ticks = 0.3s).
 * - Phase 2: Smooth cinematic pure white light fadeout over 16 ticks (0.8s), completely
 *   eliminating harsh strobing/stuttering flashes.
 */
public class CustomImpactFrameOverlay {
    public static final Identifier[] FRAMES = {
            Identifier.of("godwand", "textures/vfx/impact_frame_0.png"),
            Identifier.of("godwand", "textures/vfx/impact_frame_1.png"),
            Identifier.of("godwand", "textures/vfx/impact_frame_2.png"),
            Identifier.of("godwand", "textures/vfx/impact_frame_3.png")
    };

    public static final int IMPACT_FRAME_TICKS = 6;
    public static final int FADEOUT_TICKS = 16;
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
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 1. Giai đoạn 1 (Ticks 0..5, đúng 6 ticks): 4 bức vẽ tay Anime Impact Frame chớp cực nhanh và mạnh mẽ
        if (currentTick < IMPACT_FRAME_TICKS) {
            int frameIndex = Math.min(FRAMES.length - 1, (currentTick * FRAMES.length) / IMPACT_FRAME_TICKS);
            Identifier texture = FRAMES[frameIndex];

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            context.drawTexture(texture, 0, 0, 0.0f, 0.0f, screenWidth, screenHeight, screenWidth, screenHeight);
        }

        // 2. Giai đoạn 2 (Ticks 5..21): Màn hình chuyển thành màu trắng tinh khiết rồi fadeout từ từ êm dịu
        // Không còn tình trạng "flash flash" nhấp nháy giật mắt, tạo cảm giác điện ảnh đỉnh cao
        if (currentTick >= IMPACT_FRAME_TICKS - 1) {
            float fadeProgress = (float) (currentTick - (IMPACT_FRAME_TICKS - 1)) / (float) FADEOUT_TICKS;
            fadeProgress = MathHelper.clamp(fadeProgress, 0.0f, 1.0f);

            // Đường cong mờ dần mượt mà (smoothstep / ease-out)
            float whiteAlpha = 1.0f - fadeProgress;
            whiteAlpha = whiteAlpha * whiteAlpha; // Êm ái ở phần đuôi

            if (whiteAlpha > 0.002f) {
                int alphaByte = MathHelper.clamp((int) (whiteAlpha * 255.0f), 0, 255);
                int whiteColor = (alphaByte << 24) | 0x00FFFFFF;
                context.fill(0, 0, screenWidth, screenHeight, whiteColor);
            }
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }
}
