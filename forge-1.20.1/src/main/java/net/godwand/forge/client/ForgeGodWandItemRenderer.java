package net.godwand.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.godwand.client.render.GodWandItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class ForgeGodWandItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static ForgeGodWandItemRenderer instance;
    private final GodWandItemRenderer delegate;

    public ForgeGodWandItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.delegate = new GodWandItemRenderer();
    }

    public static ForgeGodWandItemRenderer get() {
        if (instance == null) {
            instance = new ForgeGodWandItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        delegate.render(stack, displayContext, poseStack, buffer, packedLight, packedOverlay);
    }
}
