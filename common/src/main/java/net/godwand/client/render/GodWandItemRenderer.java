package net.godwand.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.godwand.GodWandMod;
import net.godwand.client.model.GodWandModel;
import net.godwand.item.GodWandItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class GodWandItemRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    private final GodWandModel model;
    private static ItemStack chargingStack, releasedStack;
    private static float previousCharge;
    private static long releaseTick = -100;
    private static net.minecraft.client.multiplayer.ClientLevel trackedWorld;

    public static void tick(Minecraft client) {
        if (trackedWorld != client.level || client.player == null) {
            trackedWorld = client.level;
            chargingStack = releasedStack = null;
            previousCharge = 0;
            releaseTick = -100;
        }
        if (client.player == null || client.level == null) return;
        var active = client.player.getUseItem();
        if (client.player.isUsingItem() && active.is(GodWandMod.GOD_WAND_ITEM)) {
            chargingStack = active;
            previousCharge = chargeRatio(GodWandItem.getSkillMode(active), client.player.getTicksUsingItem());
        } else if (chargingStack != null) {
            if (previousCharge > 0.15f) {
                releasedStack = chargingStack;
                releaseTick = client.level.getGameTime();
            }
            chargingStack = null;
        }
    }

    public static float releaseRecoil(float ticks) {
        return ticks < 0 || ticks >= 12 ? 0 : (float)(ticks * Math.exp(-ticks * 0.65) * 1.7);
    }

    public static int coreColor(int mode, float charge) {
        int target = switch (mode) {
            case 0 -> 0xA4EAFF;
            case 1 -> 0xBE88FF;
            case 2 -> 0xFF6D31;
            case 3 -> 0x160D26;
            case 4 -> 0xFFF1C0;
            default -> 0xFFE05A;
        };
        float t = .28f + Math.clamp(charge, 0, 1) * .72f;
        int color = 0xFF000000;
        for (int shift : new int[]{16, 8, 0}) {
            int idle = (0xB85CBA >> shift) & 255;
            color |= Math.round(idle + (((target >> shift) & 255) - idle) * t) << shift;
        }
        return color;
    }

    public GodWandItemRenderer() {
        this.model = new GodWandModel(GodWandModel.getTexturedModelData().bakeRoot());
    }

    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light, int overlay) {
        Minecraft client = Minecraft.getInstance();
        float tickDelta = client.getTimer().getGameTimeDeltaPartialTick(false);
        float time = (client.player != null ? client.player.tickCount : 0) + tickDelta;

        int currentMode = GodWandItem.getSkillMode(stack);
        float chargeRatio = getChargeRatio(client, stack);

        matrices.pushPose();

        // Cancel out vanilla ItemRenderer's translate(-0.5, -0.5, -0.5)
        matrices.translate(0.5F, 0.5F, 0.5F);

        // Apply tailored transformation for each view mode
        applyDisplayTransform(matrices, mode);

        if (mode == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            float recoil = stack == releasedStack && client.level != null
                    ? releaseRecoil(client.level.getGameTime() - releaseTick + tickDelta) * previousCharge : 0;
            float draw = chargeRatio * chargeRatio;
            matrices.translate(0, -draw * 0.08 + recoil * 0.08, draw * 0.14 + recoil * 0.24);
            matrices.mulPose(Axis.XP.rotationDegrees(-draw * 7 - recoil * 16));
        }

        // Animate the floating core without moving the crescent or the grip.
        this.model.setupProceduralAnimations(time, chargeRatio, currentMode);

        // Render solid geometry and emissive core
        this.model.renderWand(matrices,
                () -> vertexConsumers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                () -> vertexConsumers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)),
                light, overlay, coreColor(currentMode, chargeRatio));

        matrices.popPose();
    }

    private void applyDisplayTransform(PoseStack matrices, ItemDisplayContext mode) {
        switch (mode) {
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
                int hand = mode == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ? 1 : -1;
                matrices.translate(hand * .01f, -.025f, -.04f);
                matrices.mulPose(Axis.XP.rotationDegrees(-12));
                matrices.mulPose(Axis.YP.rotationDegrees(hand * 140));
                matrices.mulPose(Axis.ZP.rotationDegrees(hand * 8));
                matrices.scale(.50f, .50f, .50f);
            }
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                matrices.mulPose(Axis.XP.rotationDegrees(20));
                matrices.scale(.44f, .44f, .44f);
            }
            case GUI -> {
                // Diagonally across hotbar slot, perfectly centered, showing the crescent profile
                matrices.mulPose(Axis.ZP.rotationDegrees(-45.0F));
                matrices.mulPose(Axis.YP.rotationDegrees(45.0F));
                matrices.scale(0.24F, 0.24F, 0.24F);
                matrices.translate(0.0F, 10F / 16.0F, 0.0F); // Center geometric height in slot
            }
            case FIXED -> {
                // In item frame: tilted 45 degrees, centered
                matrices.mulPose(Axis.ZP.rotationDegrees(-45.0F));
                matrices.mulPose(Axis.YP.rotationDegrees(45.0F));
                matrices.scale(0.25F, 0.25F, 0.25F);
                matrices.translate(0.0F, 10F / 16.0F, 0.0F);
            }
            case GROUND -> {
                // Lay the long staff along the ground instead of burying its lower half.
                matrices.translate(0.0F, 0.08F, 0.0F);
                matrices.mulPose(Axis.XP.rotationDegrees(90));
                matrices.mulPose(Axis.ZP.rotationDegrees(-25));
                matrices.scale(0.30F, 0.30F, 0.30F);
                matrices.translate(0.0F, 10F / 16.0F, 0.0F);
            }
            default -> {
                matrices.scale(0.50F, 0.50F, 0.50F);
                matrices.translate(0.0F, 10F / 16.0F, 0.0F);
            }
        }
    }

    public static float chargeRatio(int mode, int ticks) {
        int maximum = switch (mode) {
            case GodWandItem.MODE_ARCANE_ORB -> 45;
            case GodWandItem.MODE_RATIO_COLLAPSE -> 68;
            case GodWandItem.MODE_SUPERNOVA -> 140;
            case GodWandItem.MODE_STONE_CANNON -> 80;
            default -> 0;
        };
        return maximum == 0 ? 0 : Math.clamp((float) ticks / maximum, 0, 1);
    }

    private float getChargeRatio(Minecraft client, ItemStack renderedStack) {
        if (client.player != null && client.player.isUsingItem()) {
            ItemStack stack = client.player.getUseItem();
            if (stack == renderedStack && stack.is(GodWandMod.GOD_WAND_ITEM)) {
                int mode = GodWandItem.getSkillMode(stack);
                return chargeRatio(mode, client.player.getTicksUsingItem());
            }
        }
        return 0.0F;
    }
}
