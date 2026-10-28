package net.godwand.client;

import net.godwand.client.compat.GodWandGeometry;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.godwand.platform.Services;
import net.godwand.item.GodWandItem;
import net.godwand.network.SelectSkillPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;


public final class SkillWheelScreen extends Screen {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("godwand", "controls"));
    public static final KeyMapping OPEN = new KeyMapping("key.godwand.skill_wheel",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY);
    public static final KeyMapping OPEN_CONFIG = new KeyMapping("key.godwand.config",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY);
    private static final int[] COLORS = {0x80E5FF, 0xC493FF, 0xFFB66E, 0xFF768F, 0xFFE4A3, 0xD779FF};
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
                    && client.gui.screen() == null) {
                client.setScreenAndShow(new net.godwand.client.gui.GodWandConfigScreen(client.gui.screen()));
            }
        }
        while (OPEN.consumeClick()) {
            if (client.gui.screen() != null || client.player == null || client.level == null
                    || !client.isWindowActive() || !client.player.isAlive()
                    || client.player.isSpectator() || client.player.isUsingItem()) continue;
            InteractionHand hand = client.player.getMainHandItem().getItem() instanceof GodWandItem
                    ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = client.player.getItemInHand(hand);
            if (stack.getItem() instanceof GodWandItem) {
                client.setScreenAndShow(new SkillWheelScreen(hand,
                        hand == InteractionHand.MAIN_HAND ? client.player.getInventory().getSelectedSlot() : 40, stack));
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
                && (hand == InteractionHand.OFF_HAND || minecraft.player.getInventory().getSelectedSlot() == slot);
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
        boolean held = key.getType() == InputConstants.Type.MOUSE || InputConstants.isKeyDown(key.getValue());
        if (!held) confirm();
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        if (OPEN.matches(event)) {
            confirm();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (OPEN.matchesMouse(event)) {
            confirm();
            return true;
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) { }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!canSelect()) return;
        hovered = SkillWheelSelection.modeAt((mouseX - width / 2.0) / wheelScale(),
                (mouseY - height / 2.0) / wheelScale());
        int current = Math.max(0, Math.min(GodWandItem.TOTAL_MODES - 1, GodWandItem.getSkillMode(wand)));
        int shown = hovered >= 0 ? hovered : current;
        float cooldown = minecraft.player.getCooldowns().getCooldownPercent(wand, delta);
        context.fill(0, 0, width, height, 0x24040912);
        context.pose().pushMatrix();
        context.pose().translate(width / 2f, height / 2f);
        context.pose().scale(wheelScale(), wheelScale());
        context.guiRenderState.addGuiElement(new WheelState(new org.joml.Matrix3x2f(context.pose()), current, hovered, cooldown,
                new net.minecraft.client.gui.navigation.ScreenRectangle(-117,-117,234,234).transformMaxBounds(context.pose())));
        context.centeredText(font, Component.translatable("skill.godwand." + shown),
                0, -4, 0xFF000000 | COLORS[shown]);
        context.pose().popMatrix();
    }

    static void drawWheel(com.mojang.blaze3d.vertex.VertexConsumer mesh, Matrix4f matrix, int current, int hovered, float cooldown) {
        arc(mesh, matrix, 0, 0, 0, 47, 0, Math.PI * 2, 0xD00A0F1D);
        arc(mesh, matrix, 0, 0, 46, 47.5, 0, Math.PI * 2, 0x604A628A);
        if (hovered >= 0) {
            double hAngle = -Math.PI / 2 + hovered * SECTOR;
            arc(mesh, matrix, 0, 0, 43.5, 47.5, hAngle - 0.22, hAngle + 0.22, 0xFF000000 | COLORS[hovered]);
            arc(mesh, matrix, 0, 0, 39, 43.5, hAngle - 0.12, hAngle + 0.12, 0x88000000 | COLORS[hovered]);
        }
        for (int mode = 0; mode < GodWandItem.TOTAL_MODES; mode++) {
            double angle = -Math.PI / 2 + mode * SECTOR;
            double start = angle - SECTOR / 2 + 0.03;
            double end = angle + SECTOR / 2 - 0.03;
            int tint = COLORS[mode];
            boolean over = mode == hovered;
            boolean isCurrent = mode == current;
            arc(mesh, matrix, 0, 0, 52, 111, start, end, over ? 0xD81E2B42 : 0x900B1220);
            if (over) {
                arc(mesh, matrix, 0, 0, 52, 111, start, end, 0x22000000 | tint);
                arc(mesh, matrix, 0, 0, 109, 113, start, end, 0xFF000000 | tint);
                arc(mesh, matrix, 0, 0, 52, 54.5, start, end, 0xCC000000 | tint);
            } else {
                arc(mesh, matrix, 0, 0, 110, 111, start, end, (isCurrent ? 0x80000000 : 0x38000000) | tint);
            }
            if (isCurrent) {
                arc(mesh, matrix, 0, 0, 52, 55, start + 0.04, end - 0.04, (over ? 0xFF000000 : 0xBB000000) | tint);
            }
            if (mode == current && cooldown > 0) {
                arc(mesh, matrix, 0, 0, 114, 116.5, start, start + (end - start) * cooldown, 0xD9FFFFFF);
            }
            int iconColor = over ? (0xFF000000 | tint) : (isCurrent ? (0xDD000000 | tint) : (0x77000000 | tint));
            icon(mesh, matrix, mode, (float) Math.cos(angle) * 82, (float) Math.sin(angle) * 82, iconColor);
        }
    }

    private record WheelState(org.joml.Matrix3x2f pose, int current, int hovered, float cooldown,
            net.minecraft.client.gui.navigation.ScreenRectangle bounds)
            implements net.minecraft.client.renderer.state.gui.GuiElementRenderState {
        public com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline() { return net.minecraft.client.renderer.RenderPipelines.GUI; }
        public net.minecraft.client.gui.render.TextureSetup textureSetup() { return net.minecraft.client.gui.render.TextureSetup.noTexture(); }
        public net.minecraft.client.gui.navigation.ScreenRectangle scissorArea() { return null; }
        public void buildVertices(com.mojang.blaze3d.vertex.VertexConsumer vertices) {
            Matrix4f matrix = new Matrix4f().m00(pose.m00()).m01(pose.m01()).m10(pose.m10()).m11(pose.m11()).m30(pose.m20()).m31(pose.m21());
            drawWheel(vertices, matrix, current, hovered, cooldown);
        }
    }

    private static void icon(com.mojang.blaze3d.vertex.VertexConsumer mesh, Matrix4f matrix, int mode, float x, float y, int color) {
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
            case 5 -> {
                arc(mesh, matrix, x, y, 0, 3, 0, Math.PI * 2, color);
                for (int i = 0; i < 3; i++) {
                    double angle = i * Math.PI * 2 / 3;
                    for (int j = 0; j < 36; j++) {
                        double a = j * Math.PI / 18, b = (j + 1) * Math.PI / 18;
                        float ax = (float) (Math.cos(a) * 15), ay = (float) (Math.sin(a) * 5);
                        float bx = (float) (Math.cos(b) * 15), by = (float) (Math.sin(b) * 5);
                        line(mesh, matrix, x + ax * (float)Math.cos(angle) - ay * (float)Math.sin(angle), y + ax * (float)Math.sin(angle) + ay * (float)Math.cos(angle),
                                x + bx * (float)Math.cos(angle) - by * (float)Math.sin(angle), y + bx * (float)Math.sin(angle) + by * (float)Math.cos(angle), color);
                    }
                }
            }
        }
    }

    private static void line(com.mojang.blaze3d.vertex.VertexConsumer mesh, Matrix4f matrix, float x1, float y1, float x2, float y2, int color) {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        float nx = -(y2 - y1) * 0.7f / length, ny = (x2 - x1) * 0.7f / length;
        mesh.addVertex(matrix, x1 - nx, y1 - ny, 0).setColor(color);
        mesh.addVertex(matrix, x1 + nx, y1 + ny, 0).setColor(color);
        mesh.addVertex(matrix, x2 + nx, y2 + ny, 0).setColor(color);
        mesh.addVertex(matrix, x2 - nx, y2 - ny, 0).setColor(color);
    }

    private static void arc(com.mojang.blaze3d.vertex.VertexConsumer mesh, Matrix4f matrix, float x, float y,
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
