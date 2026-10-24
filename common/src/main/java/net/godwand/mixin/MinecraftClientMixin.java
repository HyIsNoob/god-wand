package net.godwand.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

    /**
     * Triệt tiêu hoàn toàn viền outline trắng xung quanh các khối BlockDisplayEntity bị nhấc bổng
     */
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void godwand$suppressBlockDisplayOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Display || entity.getTags().contains("godwand_displaced")) {
            cir.setReturnValue(false);
        }
    }
}
