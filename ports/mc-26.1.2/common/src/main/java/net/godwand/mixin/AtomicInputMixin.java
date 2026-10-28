package net.godwand.mixin;
import net.godwand.client.AtomicCinematic;
import net.minecraft.client.player.*;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=KeyboardInput.class,remap=false)
public abstract class AtomicInputMixin extends ClientInput {
 @Inject(method="tick",at=@At("TAIL"))
 private void godwand$atomicInput(CallbackInfo ci) {
  if(AtomicCinematic.movementLocked()){keyPresses=Input.EMPTY;moveVector=Vec2.ZERO;}
 }
}
