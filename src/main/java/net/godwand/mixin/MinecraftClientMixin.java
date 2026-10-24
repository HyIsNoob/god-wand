package net.godwand.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    /**
     * Triệt tiêu hoàn toàn viền outline trắng xung quanh các khối BlockDisplayEntity bị nhấc bổng
     */
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void godwand$suppressBlockDisplayOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof DisplayEntity || (entity != null && entity.getCommandTags().contains("godwand_displaced"))) {
            cir.setReturnValue(false);
        }
    }
}
