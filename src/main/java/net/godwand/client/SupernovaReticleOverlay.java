package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.godwand.item.GodWandItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;

/**
 * Sci-Fi Anime Aiming Reticle & Lock-On Ground Zero Cursor for Cosmic Supernova (Chiêu 4).
 * Displays a high-tech crosshair bracket, real-time distance readout (up to 180m),
 * and target lock indicator when aiming at terrain.
 */
public class SupernovaReticleOverlay {

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) return;

        // Chỉ hiển thị khi đang cầm God Wand ở chế độ Cosmic Supernova
        ItemStack mainStack = client.player.getMainHandStack();
        ItemStack offStack = client.player.getOffHandStack();
        ItemStack activeStack = null;

        if (mainStack.getItem() instanceof GodWandItem && GodWandItem.getSkillMode(mainStack) == GodWandItem.MODE_SUPERNOVA) {
            activeStack = mainStack;
        } else if (offStack.getItem() instanceof GodWandItem && GodWandItem.getSkillMode(offStack) == GodWandItem.MODE_SUPERNOVA) {
            activeStack = offStack;
        }

        if (activeStack == null) return;

        // Bỏ qua nếu đang mở GUI khác (như inventory, chat, menu)
        if (client.currentScreen != null) return;

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;

        float tickDelta = tickCounter.getTickDelta(true);

        // Raycast tầm xa 180m để xác định điểm rơi siêu tân tinh
        HitResult hit = client.player.raycast(180.0, tickDelta, false);
        double dist = hit.getPos().distanceTo(client.player.getCameraPosVec(tickDelta));
        boolean locked = (hit.getType() == HitResult.Type.BLOCK);
        boolean isCharging = client.player.isUsingItem() && client.player.getActiveItem() == activeStack;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Màu sắc: Khóa mục tiêu (Đỏ rực neon 0xFFFF3344), Tầm xa (Cyan neon 0xFF55DDFF)
        int primaryColor = locked ? 0xFFFF3344 : 0xFF55DDFF;
        int dimColor = locked ? 0x88FF3344 : 0x8855DDFF;

        int bracketSize = isCharging ? 14 : 18;
        int armLength = 5;

        // 1. Vẽ 4 góc khung ngắm Lock-On Brackets xung quanh tâm màn hình
        // Top-Left
        context.fill(cx - bracketSize, cy - bracketSize, cx - bracketSize + armLength, cy - bracketSize + 1, primaryColor);
        context.fill(cx - bracketSize, cy - bracketSize, cx - bracketSize + 1, cy - bracketSize + armLength, primaryColor);

        // Top-Right
        context.fill(cx + bracketSize - armLength, cy - bracketSize, cx + bracketSize, cy - bracketSize + 1, primaryColor);
        context.fill(cx + bracketSize - 1, cy - bracketSize, cx + bracketSize, cy - bracketSize + armLength, primaryColor);

        // Bottom-Left
        context.fill(cx - bracketSize, cy + bracketSize, cx - bracketSize + armLength, cy + bracketSize + 1, primaryColor);
        context.fill(cx - bracketSize, cy + bracketSize - armLength + 1, cx - bracketSize + 1, cy + bracketSize + 1, primaryColor);

        // Bottom-Right
        context.fill(cx + bracketSize - armLength, cy + bracketSize, cx + bracketSize, cy + bracketSize + 1, primaryColor);
        context.fill(cx + bracketSize - 1, cy + bracketSize - armLength + 1, cx + bracketSize, cy + bracketSize + 1, primaryColor);

        // Chấm tâm nhỏ
        context.fill(cx - 1, cy - 1, cx + 1, cy + 1, locked ? 0xFFFF1122 : 0xFFFFFFFF);

        // 2. Thông số cự ly Real-time & Trạng thái Lock
        TextRenderer textRenderer = client.textRenderer;
        String statusText;
        if (locked) {
            statusText = String.format("§c§l[LOCK: %.0fm]", dist);
        } else {
            statusText = "§b[TARGET: >180m]";
        }
        int textWidth = textRenderer.getWidth(statusText);
        context.drawText(textRenderer, Text.literal(statusText), cx - textWidth / 2, cy + bracketSize + 5, 0xFFFFFFFF, true);

        // 3. Nếu đang gồng chiêu (Charging): Hiển thị thanh đo năng lượng tích tụ
        if (isCharging) {
            int useTicks = client.player.getItemUseTime();
            float progress = Math.min(1.0f, (float) useTicks / 140.0f);

            int barWidth = 60;
            int barHeight = 3;
            int bx = cx - barWidth / 2;
            int by = cy + bracketSize + 16;

            // Khung nền
            context.fill(bx - 1, by - 1, bx + barWidth + 1, by + barHeight + 1, 0x99000000);
            // Vạch nạp
            int fillW = (int) (barWidth * progress);
            int barColor = progress >= 1.0f ? 0xFFFF2222 : 0xFFFF8800;
            context.fill(bx, by, bx + fillW, by + barHeight, barColor);

            String chargePercent = String.format("§e%d%%", (int) (progress * 100));
            int pctWidth = textRenderer.getWidth(chargePercent);
            context.drawText(textRenderer, Text.literal(chargePercent), cx - pctWidth / 2, by + 5, 0xFFFFFFFF, true);
        }

        RenderSystem.disableBlend();
    }
}
