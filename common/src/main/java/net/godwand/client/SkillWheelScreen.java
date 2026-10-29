package net.godwand.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.godwand.platform.Services;
import net.godwand.item.GodWandItem;
import net.godwand.network.SelectSkillPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class SkillWheelScreen extends Screen {
    public static final KeyMapping OPEN = new KeyMapping("key.godwand.skill_wheel",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.godwand");
    public static final KeyMapping OPEN_CONFIG = new KeyMapping("key.godwand.config",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.godwand");
    private static final net.minecraft.resources.ResourceLocation ART = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("godwand", "textures/gui/skill_wheel.png");
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
        drawWheel(context, current, hovered, cooldown);
        context.drawCenteredString(font, Component.translatable("skill.godwand." + current), 0, -4, skillColor(current));
        if (hovered >= 0 && hovered != current)
            context.drawCenteredString(font, Component.translatable("skill.godwand." + shown), 0, -124, skillColor(shown));
        context.drawCenteredString(font, Component.translatable("gui.godwand.release", OPEN.getTranslatedKeyMessage()), 0, 127, 0xFFE0D5C8);
        context.pose().popPose();
    }

    static void drawWheel(GuiGraphics context, int current, int hovered, float cooldown) {
        for (int mode = 0; mode < GodWandItem.TOTAL_MODES; mode++) {
            double angle = -Math.PI / 2 + mode * SECTOR;
            int x = (int)Math.round(Math.cos(angle) * 82) - 20;
            int y = (int)Math.round(Math.sin(angle) * 82) - 20;
            int row = mode == hovered ? 2 : mode == current ? 1 : 0;
            context.blit(ART, x, y, mode * 40f, row * 40f, 40, 40, 240, 120);
            if (mode == current) {
                context.fill(x + 14, y + 43, x + 26, y + 45, 0xFFE9BF70);
                if (cooldown > 0) {
                    context.fill(x + 6, y + 32, x + 34, y + 34, 0xFF17131C);
                    int remaining = (int)Math.ceil(28 * Math.max(0, Math.min(1, cooldown)));
                    context.fill(x + 6, y + 32, x + 6 + remaining, y + 34, 0xFFE9BF70);
                }
            }
        }
    }

    private static int skillColor(int mode) {
        return switch (mode) {
            case 0 -> 0xFF8BD8FF;
            case 1 -> 0xFF4898FF;
            case 2 -> 0xFFFF6436;
            case 3 -> 0xFFF18BAB;
            case 4 -> 0xFFB08759;
            default -> 0xFFC17AF2;
        };
    }
}
