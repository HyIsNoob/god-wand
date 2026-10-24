package net.godwand.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.godwand.GodWandMod;
import net.godwand.client.model.GodWandModel;
import net.godwand.item.GodWandItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public class GodWandItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/block/white_concrete.png");
    private final GodWandModel model;
    private static ItemStack chargingStack, releasedStack;
    private static float previousCharge;
    private static long releaseTick = -100;
    private static net.minecraft.client.world.ClientWorld trackedWorld;

    public static void tick(MinecraftClient client) {
        if (trackedWorld != client.world || client.player == null) {
            trackedWorld = client.world;
            chargingStack = releasedStack = null;
            previousCharge = 0;
            releaseTick = -100;
        }
        if (client.player == null || client.world == null) return;
        var active = client.player.getActiveItem();
        if (client.player.isUsingItem() && active.isOf(GodWandMod.GOD_WAND_ITEM)) {
            chargingStack = active;
            previousCharge = chargeRatio(GodWandItem.getSkillMode(active), client.player.getItemUseTime());
        } else if (chargingStack != null) {
            if (previousCharge > 0.15f) {
                releasedStack = chargingStack;
                releaseTick = client.world.getTime();
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
        float t = Math.clamp(charge, 0, 1);
        int color = 0xFF000000;
        for (int shift : new int[]{16, 8, 0}) {
            int idle = (0xFFE05A >> shift) & 255;
            color |= Math.round(idle + (((target >> shift) & 255) - idle) * t) << shift;
        }
        return color;
    }

    public GodWandItemRenderer() {
        this.model = new GodWandModel(GodWandModel.getTexturedModelData().createModel());
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        MinecraftClient client = MinecraftClient.getInstance();
        float tickDelta = client.getRenderTickCounter().getTickDelta(false);
        float time = (client.player != null ? client.player.age : 0) + tickDelta;

        int currentMode = GodWandItem.getSkillMode(stack);
        float chargeRatio = getChargeRatio(client, stack);

        matrices.push();

        // Cancel out vanilla ItemRenderer's translate(-0.5, -0.5, -0.5)
        matrices.translate(0.5F, 0.5F, 0.5F);

        // Apply tailored transformation for each view mode
        applyDisplayTransform(matrices, mode);

        if (mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND || mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND) {
            float recoil = stack == releasedStack && client.world != null
                    ? releaseRecoil(client.world.getTime() - releaseTick + tickDelta) * previousCharge : 0;
            float draw = chargeRatio * chargeRatio;
            matrices.translate(0, -draw * 0.08 + recoil * 0.08, draw * 0.14 + recoil * 0.24);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-draw * 7 - recoil * 16));
        }

        // Update procedural animations (hovering spire, spinning singularity core)
        this.model.setupProceduralAnimations(time, chargeRatio, currentMode);

        // Render solid geometry and emissive core
        this.model.renderWand(matrices,
                () -> vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)),
                () -> vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE)),
                light, overlay, coreColor(currentMode, chargeRatio));

        matrices.pop();
    }

    private void applyDisplayTransform(MatrixStack matrices, ModelTransformationMode mode) {
        switch (mode) {
            case FIRST_PERSON_RIGHT_HAND -> {
                matrices.translate(0.08F, -0.05F, -0.05F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-28.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-25.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(18.0F));
                matrices.scale(0.48F, 0.48F, 0.48F);
                matrices.translate(0.0F, 2.0F / 16.0F, 0.0F); // Grip at shaft
            }
            case FIRST_PERSON_LEFT_HAND -> {
                matrices.translate(-0.08F, -0.05F, -0.05F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-28.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(25.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-18.0F));
                matrices.scale(0.48F, 0.48F, 0.48F);
                matrices.translate(0.0F, 2.0F / 16.0F, 0.0F);
            }
            case THIRD_PERSON_RIGHT_HAND -> {
                matrices.translate(0.0F, -0.08F, 0.05F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(20.0F));
                matrices.scale(0.60F, 0.60F, 0.60F);
                matrices.translate(0.0F, 2.0F / 16.0F, 0.0F);
            }
            case THIRD_PERSON_LEFT_HAND -> {
                matrices.translate(0.0F, -0.08F, 0.05F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(20.0F));
                matrices.scale(0.60F, 0.60F, 0.60F);
                matrices.translate(0.0F, 2.0F / 16.0F, 0.0F);
            }
            case GUI -> {
                // Diagonally across hotbar slot, perfectly centered, showing 3D Gothic relief
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-45.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F));
                matrices.scale(0.27F, 0.27F, 0.27F);
                matrices.translate(0.0F, -3.75F / 16.0F, 0.0F); // Center geometric height in slot
            }
            case FIXED -> {
                // In item frame: tilted 45 degrees, centered
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-45.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F));
                matrices.scale(0.30F, 0.30F, 0.30F);
                matrices.translate(0.0F, -3.75F / 16.0F, 0.0F);
            }
            case GROUND -> {
                // Dropped on ground
                matrices.translate(0.0F, -0.10F, 0.0F);
                matrices.scale(0.45F, 0.45F, 0.45F);
                matrices.translate(0.0F, -3.75F / 16.0F, 0.0F);
            }
            default -> {
                matrices.scale(0.50F, 0.50F, 0.50F);
                matrices.translate(0.0F, -3.75F / 16.0F, 0.0F);
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

    private float getChargeRatio(MinecraftClient client, ItemStack renderedStack) {
        if (client.player != null && client.player.isUsingItem()) {
            ItemStack stack = client.player.getActiveItem();
            if (stack == renderedStack && stack.isOf(GodWandMod.GOD_WAND_ITEM)) {
                int mode = GodWandItem.getSkillMode(stack);
                return chargeRatio(mode, client.player.getItemUseTime());
            }
        }
        return 0.0F;
    }
}
