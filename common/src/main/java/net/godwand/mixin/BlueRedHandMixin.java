package net.godwand.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blue/Red in first person (owner, 2026-11-20): the wand is put away and the bare hand shows, moving with the cast. */
@Mixin(ItemInHandRenderer.class)
public abstract class BlueRedHandMixin {
    @Shadow protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffer, int light, float equipped, float swing, HumanoidArm side);

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void godwand$bareHand(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing, ItemStack stack,
                                  float equipped, PoseStack pose, MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (hand != InteractionHand.MAIN_HAND || player.isInvisible() || !net.godwand.client.BlueRedHand.holds(player)) return;
        pose.pushPose();
        net.godwand.client.BlueRedHand.firstPerson(pose, player, partial);
        renderPlayerArm(pose, buffer, light, equipped, swing, player.getMainArm());
        // The arm first (so the fire sits on it), then the cursed energy burning on it.
        if (buffer instanceof MultiBufferSource.BufferSource batch) batch.endBatch();
        net.godwand.client.BlueRedHand.curse(pose, player, partial);
        pose.popPose();
        ci.cancel();
    }
}
