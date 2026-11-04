package net.godwand.qa.mixin;
import net.godwand.client.*;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FugaRenderer.class)
public abstract class FugaRigNoVfxMixin {
 @Inject(method="render",at=@At("HEAD"),cancellable=true,remap=false)
 private static void qa$noVfx(PoseStack pose,Camera camera,float partial,CallbackInfo ci){var f=FugaRenderer.localFrame(partial);if(f==null)return;
 if(net.godwand.qa.FugaRigProbe.POSE_ONLY || net.godwand.qa.FugaRigProbe.scenario!=2 && net.godwand.qa.FugaRigProbe.scenario!=3) {
  // QA hides flame meshes but retains the real cinematic backdrop.
  com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
  com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
  com.mojang.blaze3d.systems.RenderSystem.disableCull();com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
  var buffer=com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
  FugaRenderer.emitBackdrop(buffer,pose.last().pose(),f,camera.getPosition(),1-FugaCinematic.environmentGain());
  var mesh=buffer.build();if(mesh!=null)com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
  com.mojang.blaze3d.systems.RenderSystem.depthMask(true);com.mojang.blaze3d.systems.RenderSystem.enableCull();com.mojang.blaze3d.systems.RenderSystem.disableBlend();ci.cancel();
 }}
}
