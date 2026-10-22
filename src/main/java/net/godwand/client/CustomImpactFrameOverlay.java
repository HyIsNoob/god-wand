package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * 2D Anime Impact Frame & Smooth Whiteout Aftermath Overlay for Cosmic Supernova (Chiêu 4).
 * - Phase 1: 4 Hand-drawn anime impact frames (Snapshot 1..4) held for 3 ticks each (12 ticks = 0.6s total),
 *   exactly matching Chiêu 2's high-impact cinematic pacing so every brushstroke is clearly visible.
 * - Phase 2: Smooth cinematic pure white light fadeout over 20 ticks (1.0s),
 *   starting strictly AFTER the 4 drawings finish, eliminating whiteout masking over artwork.
 */
public class CustomImpactFrameOverlay {
    public static final Identifier[] FRAMES = {
            Identifier.of("godwand", "textures/vfx/impact_frame_0.png"),
            Identifier.of("godwand", "textures/vfx/impact_frame_1.png"),
            Identifier.of("godwand", "textures/vfx/impact_frame_2.png"),
            Identifier.of("godwand", "textures/vfx/impact_frame_3.png")
    };

    public static final int IMPACT_FRAME_TICKS = 12; // 3 ticks per frame = 12 ticks (0.6s total)
    public static final int FADEOUT_TICKS = 20;      // 1.0s smooth whiteout fadeout
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
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        // 1. Giai đoạn 1 (Ticks 0..11, đúng 12 ticks = 0.6s):
        // 4 bức vẽ tay Anime Impact Frame hiển thị sắc nét, mỗi frame giữ 3 ticks trọn vẹn
        if (currentTick < IMPACT_FRAME_TICKS) {
            int frameIndex = Math.min(FRAMES.length - 1, (currentTick * FRAMES.length) / IMPACT_FRAME_TICKS);
            Identifier texture = FRAMES[frameIndex];

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            context.drawTexture(texture, 0, 0, 0.0f, 0.0f, screenWidth, screenHeight, screenWidth, screenHeight);
        }

        // 2. Giai đoạn 2 (Ticks 12..31):
        // Sau khi kết thúc trọn vẹn 4 frame tranh vẽ, màn hình chuyển sang ánh sáng trắng rồi fadeout từ từ êm dịu
        if (currentTick >= IMPACT_FRAME_TICKS) {
            float fadeProgress = (float) (currentTick - IMPACT_FRAME_TICKS) / (float) FADEOUT_TICKS;
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
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
