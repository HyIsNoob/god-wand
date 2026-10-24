package net.godwand.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    /**
     * Vô hiệu hóa hiệu ứng phát sáng outline cho toàn bộ khối BlockDisplayEntity
     */
    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void godwand$preventDisplacedIsGlowing(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof DisplayEntity || self.getCommandTags().contains("godwand_displaced")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isGlowingLocal", at = @At("HEAD"), cancellable = true)
    private void godwand$preventDisplacedIsGlowingLocal(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof DisplayEntity || self.getCommandTags().contains("godwand_displaced")) {
            cir.setReturnValue(false);
        }
    }
}
