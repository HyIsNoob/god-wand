package net.godwand.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.godwand.GodWandMod;
import net.godwand.client.model.AquaHeartiaModel;
import net.godwand.client.model.GodWandModel;
import net.godwand.client.model.ShadowBladeModel;
import net.godwand.config.GodWandConfig;
import net.godwand.config.WandSkin;
import net.godwand.item.GodWandItem;
import net.minecraft.client.Minecraft;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class GodWandItemRenderer {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(GodWandMod.MOD_ID, "textures/item/eclipse_materials.png");
    private final GodWandModel model;
    private final AquaHeartiaModel aquaHeartiaModel;
    private final ShadowBladeModel shadowBladeModel;
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
            case 0 -> 0xFF2D55;
            case 1 -> 0xBE88FF;
            case 2 -> 0xFF6D31;
            case 3 -> 0x160D26;
            case 4 -> 0xFFF1C0;
            case 5 -> 0xD950FF;
            default -> 0xFF2D55;
        };
        float t = .28f + Math.clamp(charge, 0, 1) * .72f;
        int color = 0xFF000000;
        for (int shift : new int[]{16, 8, 0}) {
            int idle = (0xFF2048 >> shift) & 255;
            color |= Math.round(idle + (((target >> shift) & 255) - idle) * t) << shift;
        }
        return color;
    }

    public GodWandItemRenderer() {
        this.model = new GodWandModel(GodWandModel.getTexturedModelData().bakeRoot());
        this.aquaHeartiaModel = new AquaHeartiaModel(AquaHeartiaModel.getTexturedModelData().bakeRoot());
        this.shadowBladeModel = new ShadowBladeModel(ShadowBladeModel.getTexturedModelData().bakeRoot());
    }

    @FunctionalInterface
    private interface Buffers { com.mojang.blaze3d.vertex.VertexConsumer getBuffer(RenderType type); }

    public void submit(ItemStack stack, ItemDisplayContext mode, PoseStack matrices,
                       net.minecraft.client.renderer.SubmitNodeCollector collector, int light, int overlay) {
        java.util.Map<RenderType, net.godwand.client.compat.ExtractedModelVertices> materials = new java.util.LinkedHashMap<>();
        render(stack,mode,matrices,type -> materials.computeIfAbsent(type, ignored -> new net.godwand.client.compat.ExtractedModelVertices()),light,overlay);
        materials.forEach((type,vertices) -> vertices.submit(collector,type));
    }

    private void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices,
                       Buffers vertexConsumers, int light, int overlay) {
        Minecraft client = Minecraft.getInstance();
        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float time = (client.player != null ? client.player.tickCount : 0) + tickDelta;

        int currentMode = GodWandItem.getSkillMode(stack);
        float chargeRatio = getChargeRatio(client, stack);

        matrices.pushPose();


        // Apply tailored transformation for each view mode
        WandSkin activeSkin = GodWandConfig.CLIENT.getActiveSkin(currentMode);
        boolean swordView=activeSkin==WandSkin.SHADOW_BLADE;
        if(swordView&&(mode==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND||mode==ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                ||mode==ItemDisplayContext.GUI||mode==ItemDisplayContext.FIXED)) applySwordDisplayTransform(matrices,mode);
        else applyDisplayTransform(matrices, mode);
        if(swordView&&currentMode==GodWandItem.MODE_ATOMIC
                &&(mode==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND||mode==ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
            var frame=net.godwand.client.AtomicRenderer.heldFrame(stack,tickDelta);
            if(frame!=null&&frame.age()<802)applyAtomicSwordHoldTransform(matrices,frame.age());
        }


        if (!swordView && currentMode == GodWandItem.MODE_ATOMIC && (mode == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || mode == ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
            var frame = net.godwand.client.AtomicRenderer.heldFrame(stack, tickDelta);
            if (frame != null && frame.age() < 802) {
                float weight=1-net.godwand.skill.AtomicSequence.phase(frame.age(),774,802);
                applyAtomicHoldTransform(matrices,net.godwand.skill.AtomicSequence.armPitch(frame.age()),weight);
                matrices.rotate(Axis.ZP.rotationDegrees(-16*net.godwand.skill.AtomicSequence.armSpread(frame.age())*weight));
            }
        }

        if (mode == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            float recoil = stack == releasedStack && client.level != null
                    ? releaseRecoil(client.level.getGameTime() - releaseTick + tickDelta) * previousCharge : 0;
            applyUseTransform(matrices, chargeRatio, recoil);
        }


        if (activeSkin == WandSkin.AQUA_HEARTIA) {
            this.aquaHeartiaModel.setupProceduralAnimations(time, chargeRatio, currentMode);
            this.aquaHeartiaModel.renderWand(matrices,
                    () -> vertexConsumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(TEXTURE)),
                    () -> vertexConsumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(TEXTURE)),
                    light, overlay, coreColor(currentMode, chargeRatio));
        } else if (activeSkin == WandSkin.SHADOW_BLADE) {
            var swordFrame=currentMode==GodWandItem.MODE_ATOMIC?net.godwand.client.AtomicRenderer.heldFrame(stack,tickDelta):null;
            if(swordFrame!=null&&(mode==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND||mode==ItemDisplayContext.THIRD_PERSON_LEFT_HAND))
                net.godwand.client.AtomicRenderer.captureSwordTip(swordFrame,matrices.last().pose(),tickDelta);
            float bladePower=swordFrame==null?0:net.godwand.skill.AtomicSequence.phase(swordFrame.age(),294,350)
                    *(1-net.godwand.skill.AtomicSequence.phase(swordFrame.age(),734,774));
            this.shadowBladeModel.setupProceduralAnimations(time, bladePower, currentMode);
            this.shadowBladeModel.renderBlade(matrices,
                    () -> vertexConsumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(TEXTURE)),
                    () -> vertexConsumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(TEXTURE)),
                    light, overlay, coreColor(currentMode, chargeRatio));
        } else {
            this.model.setupProceduralAnimations(time, chargeRatio, currentMode);
            this.model.renderWand(matrices,
                    () -> vertexConsumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(TEXTURE)),
                    () -> vertexConsumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(TEXTURE)),
                    light, overlay, coreColor(currentMode, chargeRatio));
        }

        matrices.popPose();
    }

    private static void applyUseTransform(PoseStack matrices, float chargeRatio, float recoil) {
        float draw = chargeRatio * chargeRatio;
        // Keep the long shaft away from the camera, including the first frames after cancellation.
        matrices.translate(0, -draw*.08f + recoil*.08f, draw*.14f + recoil*.24f);
        matrices.rotate(Axis.XP.rotationDegrees(-draw*7 - recoil*16));
    }

    public static void applyAtomicHoldTransform(PoseStack matrices, float armPitch,float weight) {
        // Cancel the arm pitch, vanilla hand rotation and our existing 20-degree display tilt.
        matrices.rotate(Axis.XP.rotationDegrees((70 + (float)Math.toDegrees(armPitch))*weight));
    }

    public static void applySwordDisplayTransform(PoseStack matrices, ItemDisplayContext mode) {
        if(mode==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND||mode==ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            int hand=mode==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND?1:-1;
            // Cancel vanilla's sideways/palm offsets; preserve its ten-pixel distance along the forearm.
            matrices.translate(-hand/16f,-.125f,0);
            matrices.rotate(Axis.XP.rotationDegrees(20));
            matrices.scale(.44f,.44f,.44f);
        } else if(mode==ItemDisplayContext.GUI||mode==ItemDisplayContext.FIXED) {
            matrices.rotate(Axis.ZP.rotationDegrees(-45));
            matrices.rotate(Axis.YP.rotationDegrees(35));
            matrices.scale(.24f,.24f,.24f);
            matrices.translate(0,-1.125f,0);
        }
    }

    public static void applyAtomicSwordHoldTransform(PoseStack matrices,float age) {
        float spread=net.godwand.skill.AtomicSequence.armSpread(age);
        float vertical=net.godwand.skill.AtomicSequence.armRaise(age)
                *(1-net.godwand.skill.AtomicSequence.armWindup(age))*(1-spread);
        float weight=1-net.godwand.skill.AtomicSequence.phase(age,774,802);
        float angle=(70+(float)Math.toDegrees(net.godwand.skill.AtomicSequence.armPitch(age)))*vertical-110*spread;
        matrices.rotate(Axis.XP.rotationDegrees(angle*weight));
    }

    private void applyDisplayTransform(PoseStack matrices, ItemDisplayContext mode) {
        switch (mode) {
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
                int hand = mode == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ? 1 : -1;
                matrices.translate(hand * .020f, -.028f, -.040f);
                matrices.rotate(Axis.XP.rotationDegrees(-35));
                matrices.rotate(Axis.YP.rotationDegrees(hand * 130));
                matrices.rotate(Axis.ZP.rotationDegrees(hand * 12));
                matrices.scale(.46f, .46f, .46f);
            }
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                matrices.rotate(Axis.XP.rotationDegrees(20));
                matrices.scale(.44f, .44f, .44f);
            }
            case GUI -> {
                // Diagonally across hotbar slot, perfectly centered, showing the crescent profile
                matrices.rotate(Axis.ZP.rotationDegrees(-45.0F));
                matrices.rotate(Axis.YP.rotationDegrees(45.0F));
                matrices.scale(0.24F, 0.24F, 0.24F);
                matrices.translate(0.0F, 10F / 16.0F, 0.0F); // Center geometric height in slot
            }
            case FIXED -> {
                // In item frame: tilted 45 degrees, centered
                matrices.rotate(Axis.ZP.rotationDegrees(-45.0F));
                matrices.rotate(Axis.YP.rotationDegrees(45.0F));
                matrices.scale(0.25F, 0.25F, 0.25F);
                matrices.translate(0.0F, 10F / 16.0F, 0.0F);
            }
            case GROUND -> {
                // Lay the long staff along the ground instead of burying its lower half.
                matrices.translate(0.0F, 0.08F, 0.0F);
                matrices.rotate(Axis.XP.rotationDegrees(90));
                matrices.rotate(Axis.ZP.rotationDegrees(-25));
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
            case GodWandItem.MODE_ATOMIC -> net.godwand.skill.AtomicSequence.CHARGE_TICKS;
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
