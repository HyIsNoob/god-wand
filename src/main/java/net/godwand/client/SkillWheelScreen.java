package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.godwand.item.GodWandItem;
import net.godwand.network.SelectSkillPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public final class SkillWheelScreen extends Screen {
    private static final KeyBinding OPEN = new KeyBinding("key.godwand.skill_wheel",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.godwand");
    private static final int[] COLORS = {0x80E5FF, 0xC493FF, 0xFFB66E, 0xFF768F, 0xFFE4A3};
    private static final double SECTOR = Math.PI * 2 / GodWandItem.TOTAL_MODES;
    private final Hand hand;
    private final int slot;
    private final ItemStack wand;
    private int hovered = -1;

    private SkillWheelScreen(Hand hand, int slot, ItemStack wand) {
        super(Text.translatable("key.godwand.skill_wheel"));
        this.hand = hand;
        this.slot = slot;
        this.wand = wand;
    }

    public static void register() {
        KeyBindingHelper.registerKeyBinding(OPEN);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN.wasPressed()) {
                if (client.currentScreen != null || client.player == null || client.world == null
                        || !client.isWindowFocused() || !client.player.isAlive()
                        || client.player.isSpectator() || client.player.isUsingItem()
                        || !ClientPlayNetworking.canSend(SelectSkillPayload.ID)) continue;
                Hand hand = client.player.getMainHandStack().getItem() instanceof GodWandItem
                        ? Hand.MAIN_HAND : Hand.OFF_HAND;
                ItemStack stack = client.player.getStackInHand(hand);
                if (stack.getItem() instanceof GodWandItem) {
                    client.setScreen(new SkillWheelScreen(hand,
                            hand == Hand.MAIN_HAND ? client.player.getInventory().selectedSlot : 40, stack));
                }
            }
        });
    }

    private float wheelScale() {
        return Math.min(1f, Math.min(width, height) / 290f);
    }

    private boolean canSelect() {
        return client != null && client.world != null && client.player != null
                && client.player.isAlive() && !client.player.isSpectator() && !client.player.isUsingItem()
                && client.player.getStackInHand(hand) == wand
                && (hand == Hand.OFF_HAND || client.player.getInventory().selectedSlot == slot);
    }

    private void updateSelection() {
        double x = client.mouse.getX() * width / client.getWindow().getWidth();
        double y = client.mouse.getY() * height / client.getWindow().getHeight();
        hovered = SkillWheelSelection.modeAt((x - width / 2.0) / wheelScale(),
                (y - height / 2.0) / wheelScale());
    }

    private void confirm() {
        if (canSelect() && client.isWindowFocused() && ClientPlayNetworking.canSend(SelectSkillPayload.ID)) {
            updateSelection();
            if (hovered >= 0 && hovered != GodWandItem.getSkillMode(wand)) {
                ClientPlayNetworking.send(new SelectSkillPayload(hovered, slot));
            }
        }
        close();
    }

    @Override
    public void tick() {
        if (!canSelect() || !client.isWindowFocused()) {
            close();
            return;
        }
        // Opening a Screen clears KeyBinding state; poll the physical key while it is open.
        InputUtil.Key key = InputUtil.fromTranslationKey(OPEN.getBoundKeyTranslationKey());
        boolean held = key.getCategory() == InputUtil.Type.MOUSE
                ? GLFW.glfwGetMouseButton(client.getWindow().getHandle(), key.getCode()) == GLFW.GLFW_PRESS
                : key.getCategory() == InputUtil.Type.SCANCODE
                || InputUtil.isKeyPressed(client.getWindow().getHandle(), key.getCode());
        if (!held) confirm();
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (OPEN.matchesKey(keyCode, scanCode)) {
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
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!canSelect()) return;
        hovered = SkillWheelSelection.modeAt((mouseX - width / 2.0) / wheelScale(),
                (mouseY - height / 2.0) / wheelScale());
        int current = Math.max(0, Math.min(GodWandItem.TOTAL_MODES - 1, GodWandItem.getSkillMode(wand)));
        int shown = hovered >= 0 ? hovered : current;
        float cooldown = client.player.getItemCooldownManager().getCooldownProgress(wand.getItem(), delta);
        context.fill(0, 0, width, height, 0x24040912);
        context.getMatrices().push();
        context.getMatrices().translate(width / 2f, height / 2f, 0);
        context.getMatrices().scale(wheelScale(), wheelScale(), 1);
        context.draw();
        drawWheel(context.getMatrices().peek().getPositionMatrix(), current, hovered, cooldown);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("skill.godwand." + shown),
                0, -5, 0xFF000000 | COLORS[shown]);
        context.getMatrices().push();
        context.getMatrices().scale(0.75f, 0.75f, 1);
        context.drawCenteredTextWithShadow(textRenderer,
                hovered < 0 ? Text.translatable("gui.godwand.keep")
                        : Text.translatable("gui.godwand.release", OPEN.getBoundKeyLocalizedText()),
                0, 16, 0xFFBEC8D9);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.godwand.cancel"),
                0, 170, 0xFF9AA8BD);
        context.getMatrices().pop();
        context.getMatrices().pop();
    }

    static void drawWheel(Matrix4f matrix, int current, int hovered, float cooldown) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder mesh = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
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
        BufferRenderer.drawWithGlobalProgram(mesh.end());
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
        mesh.vertex(matrix, x1 - nx, y1 - ny, 0).color(color);
        mesh.vertex(matrix, x1 + nx, y1 + ny, 0).color(color);
        mesh.vertex(matrix, x2 + nx, y2 + ny, 0).color(color);
        mesh.vertex(matrix, x2 - nx, y2 - ny, 0).color(color);
    }

    private static void arc(BufferBuilder mesh, Matrix4f matrix, float x, float y,
                            double inner, double outer, double start, double end, int color) {
        int steps = Math.max(2, (int) Math.ceil((end - start) * 24));
        for (int i = 0; i < steps; i++) {
            double a = start + (end - start) * i / steps;
            double b = start + (end - start) * (i + 1) / steps;
            mesh.vertex(matrix, x + (float) (Math.cos(a) * inner), y + (float) (Math.sin(a) * inner), 0).color(color);
            mesh.vertex(matrix, x + (float) (Math.cos(b) * inner), y + (float) (Math.sin(b) * inner), 0).color(color);
            mesh.vertex(matrix, x + (float) (Math.cos(b) * outer), y + (float) (Math.sin(b) * outer), 0).color(color);
            mesh.vertex(matrix, x + (float) (Math.cos(a) * outer), y + (float) (Math.sin(a) * outer), 0).color(color);
        }
    }
}
