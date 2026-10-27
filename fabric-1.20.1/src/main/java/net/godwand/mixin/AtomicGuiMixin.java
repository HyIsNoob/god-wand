package net.godwand.mixin;
import net.godwand.client.AtomicCinematic;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Gui.class)
public abstract class AtomicGuiMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void godwand$atomicHud(GuiGraphics graphics,float delta,CallbackInfo ci) {
        if(AtomicCinematic.active()) { AtomicCinematic.render(graphics,delta); ci.cancel(); }
    }
}
