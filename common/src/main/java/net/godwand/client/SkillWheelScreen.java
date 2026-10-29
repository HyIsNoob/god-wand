package net.godwand.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.godwand.platform.Services;
import net.godwand.item.GodWandItem;
import net.godwand.network.SelectSkillPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.lwjgl.glfw.GLFW;

public final class SkillWheelScreen extends Screen {
    public static final KeyMapping OPEN = new KeyMapping("key.godwand.skill_wheel",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.godwand");
    public static final KeyMapping OPEN_CONFIG = new KeyMapping("key.godwand.config",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.godwand");
    private static final double SECTOR = Math.PI * 2 / GodWandItem.TOTAL_MODES;
    private final InteractionHand hand;
    private final int slot;
    private final ItemStack wand;
    private int hovered = -1;

    private SkillWheelScreen(InteractionHand hand, int slot, ItemStack wand) {
        super(Component.translatable("key.godwand.skill_wheel"));
        this.hand = hand;
        this.slot = slot;
        this.wand = wand;
    }

    public static void handleKeyInput(Minecraft client) {
        while (OPEN_CONFIG.consumeClick()) {
            if (client.player != null && !client.player.isUsingItem() && !AtomicCinematic.movementLocked()
                    && client.screen == null) {
                client.setScreen(new net.godwand.client.gui.GodWandConfigScreen(client.screen));
            }
        }
        while (OPEN.consumeClick()) {
            if (client.screen != null || client.player == null || client.level == null
                    || !client.isWindowActive() || !client.player.isAlive()
                    || client.player.isSpectator() || client.player.isUsingItem()) continue;
            InteractionHand hand = client.player.getMainHandItem().getItem() instanceof GodWandItem
                    ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = client.player.getItemInHand(hand);
            if (stack.getItem() instanceof GodWandItem) {
                client.setScreen(new SkillWheelScreen(hand,
                        hand == InteractionHand.MAIN_HAND ? client.player.getInventory().selected : 40, stack));
            }
        }
    }

    private float wheelScale() {
        return Math.min(1f, Math.min(width, height) / 290f);
    }

    private boolean canSelect() {
        return minecraft != null && minecraft.level != null && minecraft.player != null
                && minecraft.player.isAlive() && !minecraft.player.isSpectator() && !minecraft.player.isUsingItem()
                && minecraft.player.getItemInHand(hand) == wand
                && (hand == InteractionHand.OFF_HAND || minecraft.player.getInventory().selected == slot);
    }

    private void updateSelection() {
        double x = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
        double y = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
        hovered = SkillWheelSelection.modeAt((x - width / 2.0) / wheelScale(),
                (y - height / 2.0) / wheelScale());
    }

    private void confirm() {
        if (canSelect() && minecraft.isWindowActive()) {
            updateSelection();
            if (hovered >= 0 && hovered != GodWandItem.getSkillMode(wand)) {
                Services.NETWORK.sendToServer(new SelectSkillPayload(hovered, slot));
            }
        }
        onClose();
    }

    @Override
    public void tick() {
        if (!canSelect() || !minecraft.isWindowActive()) {
            onClose();
            return;
        }
        // Opening a Screen clears KeyBinding state; poll the physical key while it is open.
        InputConstants.Key key = InputConstants.getKey(OPEN.saveString());
        boolean held = key.getType() == InputConstants.Type.MOUSE
                ? GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), key.getValue()) == GLFW.GLFW_PRESS
                : key.getType() == InputConstants.Type.SCANCODE
                || InputConstants.isKeyDown(minecraft.getWindow().getWindow(), key.getValue());
        if (!held) confirm();
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (OPEN.matches(keyCode, scanCode)) {
            confirm();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (OPEN.matchesMouse(button)) {
            confirm();
            return true;
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!canSelect()) return;
        hovered = SkillWheelSelection.modeAt((mouseX - width / 2.0) / wheelScale(),
                (mouseY - height / 2.0) / wheelScale());
        int current = Math.max(0, Math.min(GodWandItem.TOTAL_MODES - 1, GodWandItem.getSkillMode(wand)));
        int shown = hovered >= 0 ? hovered : current;
        float cooldown = minecraft.player.getCooldowns().getCooldownPercent(wand.getItem(), delta);
        context.fill(0, 0, width, height, 0x38100D17);
        context.pose().pushPose();
        context.pose().translate(width / 2f, height / 2f, 0);
        context.pose().scale(wheelScale(), wheelScale(), 1);
        context.flush();
        drawWheel(context.pose().last().pose(), current, hovered, cooldown);
        Component name = Component.translatable("skill.godwand." + shown);
        float labelScale = Math.min(1f, 68f / Math.max(1, font.width(name)));
        context.pose().pushPose();
        context.pose().scale(labelScale, labelScale, 1);
        context.drawCenteredString(font, name, 0, -4, skillColor(shown));
        context.pose().popPose();
        context.drawCenteredString(font, Component.translatable("gui.godwand.release", OPEN.getTranslatedKeyMessage()), 0, 127, 0xFFE0D5C8);
        context.pose().popPose();
    }

    static void drawWheel(Matrix4f matrix, int current, int hovered, float cooldown) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder mesh = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        emitWheel(mesh, matrix, current, hovered, cooldown);
        BufferUploader.drawWithShader(mesh.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    static void emitWheel(VertexConsumer mesh, Matrix4f matrix, int current, int hovered, float cooldown) {
        int shown = hovered >= 0 ? hovered : current;
        arc(mesh, matrix, 0, 0, 0, 38, 0, Math.PI * 2, 0xDF141219);
        arc(mesh, matrix, 0, 0, 38, 38.6, 0, Math.PI * 2, 0x886F626F);
        for (int mode = 0; mode < GodWandItem.TOTAL_MODES; mode++) {
            double angle = -Math.PI / 2 + mode * SECTOR;
            float x = (float)Math.cos(angle) * 82, y = (float)Math.sin(angle) * 82;
            boolean over = mode == hovered, selected = mode == current;
            int ink = skillColor(mode), rim = tint(ink, over ? 255 : selected ? 220 : 135);
            // Broken outer arcs keep the six seals separate instead of covering the world.
            arc(mesh, matrix, 0, 0, 113.5, 114, angle - .29, angle + .29, tint(ink, 80));
            seal(mesh, matrix, mode, x, y, over, rim);
            icon(mesh, matrix, mode, x, y, over ? 0xFFF8F1E4 : tint(ink, 255));
            if (selected) {
                float xx = x * .57f, yy = y * .57f;
                arc(mesh, matrix, xx, yy, 0, 1.8, 0, Math.PI * 2, tint(ink, 255));
                if (cooldown > 0)
                    arc(mesh, matrix, x, y, 31, 32, -Math.PI / 2,
                            -Math.PI / 2 + Math.PI * 2 * cooldown, 0xFFF4E7CF);
            }
        }
        arc(mesh, matrix, 0, 0, 40, 40.8, .20, Math.PI - .20, tint(skillColor(shown), 200));
        arc(mesh, matrix, 0, 0, 40, 40.8, Math.PI + .20, Math.PI * 2 - .20, tint(skillColor(shown), 200));
    }

    static int skillColor(int mode) {
        return switch (mode) {
            case 0 -> 0xFF7AD9CF;
            case 1 -> 0xFFAA92EB;
            case 2 -> 0xFFDCAD77;
            case 3 -> 0xFFFFBB61;
            case 4 -> 0xFF90BEDA;
            default -> 0xFFE392EE;
        };
    }

    private static int tint(int color, int alpha) { return (color & 0xFFFFFF) | (alpha << 24); }

    private static void seal(VertexConsumer mesh, Matrix4f matrix, int mode, float x, float y, boolean over, int rim) {
        arc(mesh, matrix, x, y, 0, 29, 0, Math.PI * 2, over ? 0xF02D2536 : 0xE51A171F);
        arc(mesh, matrix, x, y, 28.4, 29.2, 0, Math.PI * 2, rim);
        arc(mesh, matrix, x, y, 23, 23.5, 0, Math.PI * 2, tint(rim, 90));
        arc(mesh, matrix, x, y, 18.6, 19, 0, Math.PI * 2, tint(rim, 65));
        // Fixed flecks imitate worn ink; no random allocations or changing noise each frame.
        for (int i = 0; i < 48; i++) {
            double angle = i * 2.399963 + mode;
            double radius = 5 + Math.sqrt((i * 17 % 47) / 47.0) * 22;
            float xx = x + (float)(Math.cos(angle) * radius), yy = y + (float)(Math.sin(angle) * radius);
            line(mesh, matrix, xx, yy, xx + .3f, yy + .15f, tint(rim, 22 + i % 4 * 7));
        }
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6 + mode * .11;
            float dx = (float)Math.cos(a), dy = (float)Math.sin(a);
            float xx = x + dx * 26, yy = y + dy * 26;
            line(mesh, matrix, xx - dx, yy - dy, xx + dx, yy + dy, rim);
            if (i % 3 != 0)
                line(mesh, matrix, xx, yy, xx - dy * 1.2f + dx, yy + dx * 1.2f + dy, tint(rim, 130));
            if (i % 3 == 0) {
                arc(mesh, matrix, x, y, 30, 30.5, a - .08, a + .08, rim);
                float tx = x + dx * 20, ty = y + dy * 20;
                line(mesh, matrix, tx, ty, tx - dx * 2 + dy, ty - dy * 2 - dx, tint(rim, 100));
                line(mesh, matrix, tx, ty, tx - dx * 2 - dy, ty - dy * 2 + dx, tint(rim, 100));
            }
        }
    }

    private static void icon(VertexConsumer mesh, Matrix4f matrix, int mode, float x, float y, int color) {
        switch (mode) {
            case 0 -> {
                arc(mesh, matrix, x, y, 12, 13, -2.8, -.34, color);
                arc(mesh, matrix, x, y, 12, 13, .34, 2.8, color);
                line(mesh, matrix, x - 5, y + 10, x - 5, y - 7, color);
                line(mesh, matrix, x - 5, y - 7, x, y - 12, color);
                line(mesh, matrix, x, y - 12, x + 5, y - 7, color);
                line(mesh, matrix, x + 5, y - 7, x + 5, y + 10, color);
                line(mesh, matrix, x - 7, y + 10, x + 7, y + 10, color);
                line(mesh, matrix, x - 1, y - 5, x + 1, y + 5, tint(color, 120));
            }
            case 1 -> {
                arc(mesh, matrix, x, y, 0, 3.8, 0, Math.PI * 2, color);
                arc(mesh, matrix, x, y, 6, 7, -.8, 3.5, color);
                arc(mesh, matrix, x, y, 10, 11, 1.1, 5.5, color);
                arc(mesh, matrix, x, y, 14, 14.5, 3, 6.9, tint(color, 160));
                line(mesh, matrix, x - 16, y + 6, x - 7, y + 2, color);
                line(mesh, matrix, x + 7, y - 2, x + 16, y - 6, color);
            }
            case 2 -> {
                line(mesh, matrix, x, y - 14, x - 10, y - 2, color);
                line(mesh, matrix, x - 10, y - 2, x, y + 15, color);
                line(mesh, matrix, x, y + 15, x + 10, y - 2, color);
                line(mesh, matrix, x + 10, y - 2, x, y - 14, color);
                line(mesh, matrix, x, y - 14, x + 3, y - 2, tint(color, 160));
                line(mesh, matrix, x + 3, y - 2, x, y + 15, tint(color, 160));
                line(mesh, matrix, x - 13, y + 3, x + 12, y - 5, color);
                line(mesh, matrix, x - 9, y + 9, x + 9, y + 3, color);
            }
            case 3 -> {
                arc(mesh, matrix, x, y, 0, 3, 0, Math.PI * 2, color);
                arc(mesh, matrix, x, y, 6, 6.8, 0, Math.PI * 2, color);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4, b = a + Math.PI / 4;
                    float xx = x + (float)Math.cos(a) * (i % 2 == 0 ? 16 : 12);
                    float yy = y + (float)Math.sin(a) * (i % 2 == 0 ? 16 : 12);
                    line(mesh, matrix, x + (float)Math.cos(a - Math.PI / 4) * 8, y + (float)Math.sin(a - Math.PI / 4) * 8, xx, yy, color);
                    line(mesh, matrix, xx, yy, x + (float)Math.cos(b) * 8, y + (float)Math.sin(b) * 8, color);
                }
            }
            case 4 -> {
                arc(mesh, matrix, x + 5, y + 3, 6, 7, 0, Math.PI * 2, color);
                arc(mesh, matrix, x + 5, y + 3, 0, 2, 0, Math.PI * 2, color);
                line(mesh, matrix, x - 13, y - 12, x + 9, y - 3, color);
                line(mesh, matrix, x - 13, y - 12, x - 1, y + 7, color);
                line(mesh, matrix, x - 17, y - 5, x - 9, y + 6, tint(color, 170));
                line(mesh, matrix, x - 7, y - 15, x + 7, y - 9, tint(color, 170));
            }
            case 5 -> {
                line(mesh, matrix, x, y - 16, x - 2, y + 3, color);
                line(mesh, matrix, x, y - 16, x + 2, y + 3, color);
                line(mesh, matrix, x, y + 3, x, y + 13, color);
                line(mesh, matrix, x - 7, y + 1, x, y + 4, color);
                line(mesh, matrix, x, y + 4, x + 7, y + 1, color);
                arc(mesh, matrix, x, y + 14, 0, 1.5, 0, Math.PI * 2, color);
                arc(mesh, matrix, x, y - 3, 12, 12.5, -.9, .9, tint(color, 160));
                arc(mesh, matrix, x, y - 3, 12, 12.5, Math.PI - .9, Math.PI + .9, tint(color, 160));
            }
        }
    }

    private static void line(VertexConsumer mesh, Matrix4f matrix, float x1, float y1, float x2, float y2, int color) {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        float nx = -(y2 - y1) * 0.7f / length, ny = (x2 - x1) * 0.7f / length;
        mesh.addVertex(matrix, x1 - nx, y1 - ny, 0).setColor(color);
        mesh.addVertex(matrix, x1 + nx, y1 + ny, 0).setColor(color);
        mesh.addVertex(matrix, x2 + nx, y2 + ny, 0).setColor(color);
        mesh.addVertex(matrix, x2 - nx, y2 - ny, 0).setColor(color);
    }

    private static void arc(VertexConsumer mesh, Matrix4f matrix, float x, float y,
                            double inner, double outer, double start, double end, int color) {
        int steps = Math.max(2, (int) Math.ceil((end - start) * 16));
        for (int i = 0; i < steps; i++) {
            double a = start + (end - start) * i / steps;
            double b = start + (end - start) * (i + 1) / steps;
            mesh.addVertex(matrix, x + (float) (Math.cos(a) * inner), y + (float) (Math.sin(a) * inner), 0).setColor(color);
            mesh.addVertex(matrix, x + (float) (Math.cos(b) * inner), y + (float) (Math.sin(b) * inner), 0).setColor(color);
            mesh.addVertex(matrix, x + (float) (Math.cos(b) * outer), y + (float) (Math.sin(b) * outer), 0).setColor(color);
            mesh.addVertex(matrix, x + (float) (Math.cos(a) * outer), y + (float) (Math.sin(a) * outer), 0).setColor(color);
        }
    }
}
