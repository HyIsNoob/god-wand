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
import org.lwjgl.glfw.GLFW;

public final class SkillWheelScreen extends Screen {
    private static final KeyMapping OPEN = new KeyMapping("key.godwand.skill_wheel",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.godwand");
    private static final int[] COLORS = {0x80E5FF, 0xC493FF, 0xFFB66E, 0xFF768F, 0xFFE4A3};
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
        context.fill(0, 0, width, height, 0x24040912);
        context.pose().pushPose();
        context.pose().translate(width / 2f, height / 2f, 0);
        context.pose().scale(wheelScale(), wheelScale(), 1);
        context.flush();
        drawWheel(context.pose().last().pose(), current, hovered, cooldown);
        context.drawCenteredString(font, Component.translatable("skill.godwand." + shown),
                0, -5, 0xFF000000 | COLORS[shown]);
        context.pose().pushPose();
        context.pose().scale(0.75f, 0.75f, 1);
        context.drawCenteredString(font,
                hovered < 0 ? Component.translatable("gui.godwand.keep")
                        : Component.translatable("gui.godwand.release", OPEN.getTranslatedKeyMessage()),
                0, 16, 0xFFBEC8D9);
        context.drawCenteredString(font, Component.translatable("gui.godwand.cancel"),
                0, 170, 0xFF9AA8BD);
        context.pose().popPose();
        context.pose().popPose();
    }

    static void drawWheel(Matrix4f matrix, int current, int hovered, float cooldown) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder mesh = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        arc(mesh, matrix, 0, 0, 0, 47, 0, Math.PI * 2, 0xA609101E);
        arc(mesh, matrix, 0, 0, 46.5, 47.5, 0, Math.PI * 2, 0x70495D7D);
        for (int mode = 0; mode < GodWandItem.TOTAL_MODES; mode++) {
            double angle = -Math.PI / 2 + mode * SECTOR;
            double start = angle - SECTOR / 2 + 0.025;
            double end = angle + SECTOR / 2 - 0.025;
            int tint = COLORS[mode];
            boolean over = mode == hovered;
            arc(mesh, matrix, 0, 0, 53, 111, start, end, (over ? 0xD0202B40 : 0xAC0A111F));
            if (over) arc(mesh, matrix, 0, 0, 108, 117, start, end, 0x18000000 | tint);
            arc(mesh, matrix, 0, 0, 110, over ? 112 : 111, start, end,
                    (over ? 0xFF000000 : 0x60000000) | tint);
            if (mode == current) arc(mesh, matrix, 0, 0, 52, 54, angle - 0.21, angle + 0.21, 0xFF000000 | tint);
            if (mode == current && cooldown > 0) {
                arc(mesh, matrix, 0, 0, 114, 116, start, start + (end - start) * cooldown, 0xD9FFFFFF);
            }
            icon(mesh, matrix, mode, (float) Math.cos(angle) * 82, (float) Math.sin(angle) * 82,
                    (over || mode == current ? 0xFF000000 : 0xBB000000) | tint);
        }
        BufferUploader.drawWithShader(mesh.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    private static void icon(BufferBuilder mesh, Matrix4f matrix, int mode, float x, float y, int color) {
        switch (mode) {
            case 0 -> {
                arc(mesh, matrix, x, y, 12, 13, -2.8, -0.34, color);
                arc(mesh, matrix, x, y, 12, 13, 0.34, 2.8, color);
                line(mesh, matrix, x - 4, y - 9, x - 4, y + 9, color);
                line(mesh, matrix, x + 4, y - 9, x + 4, y + 9, color);
            }
            case 1 -> {
                arc(mesh, matrix, x, y, 0, 4, 0, Math.PI * 2, color);
                arc(mesh, matrix, x, y, 9, 10, 0.5, 5.8, color);
                line(mesh, matrix, x - 15, y + 6, x + 15, y - 6, color);
            }
            case 2 -> {
                for (int i = 0; i < 3; i++) {
                    float yy = y - 10 + i * 7;
                    line(mesh, matrix, x - 10 + i * 2, yy, x, yy + 6, color);
                    line(mesh, matrix, x, yy + 6, x + 10 - i * 2, yy, color);
                }
            }
            case 3 -> {
                arc(mesh, matrix, x, y, 7, 8, 0, Math.PI * 2, color);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    line(mesh, matrix, x + (float) Math.cos(a) * 11, y + (float) Math.sin(a) * 11,
                            x + (float) Math.cos(a) * 16, y + (float) Math.sin(a) * 16, color);
                }
            }
            case 4 -> {
                line(mesh, matrix, x - 5, y - 8, x + 14, y, color);
                line(mesh, matrix, x + 14, y, x - 5, y + 8, color);
                line(mesh, matrix, x - 5, y + 8, x - 1, y, color);
                line(mesh, matrix, x - 1, y, x - 5, y - 8, color);
                line(mesh, matrix, x - 14, y - 4, x - 8, y - 4, color);
                line(mesh, matrix, x - 17, y + 3, x - 8, y + 3, color);
            }
        }
    }

    private static void line(BufferBuilder mesh, Matrix4f matrix, float x1, float y1, float x2, float y2, int color) {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        float nx = -(y2 - y1) * 0.7f / length, ny = (x2 - x1) * 0.7f / length;
        mesh.addVertex(matrix, x1 - nx, y1 - ny, 0).setColor(color);
        mesh.addVertex(matrix, x1 + nx, y1 + ny, 0).setColor(color);
        mesh.addVertex(matrix, x2 + nx, y2 + ny, 0).setColor(color);
        mesh.addVertex(matrix, x2 - nx, y2 - ny, 0).setColor(color);
    }

    private static void arc(BufferBuilder mesh, Matrix4f matrix, float x, float y,
                            double inner, double outer, double start, double end, int color) {
        int steps = Math.max(2, (int) Math.ceil((end - start) * 24));
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
