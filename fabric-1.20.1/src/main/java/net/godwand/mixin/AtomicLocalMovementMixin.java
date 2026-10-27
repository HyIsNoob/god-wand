package net.godwand.mixin;

import net.godwand.client.AtomicCinematic;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class AtomicLocalMovementMixin {
    @Inject(method="move",at=@At("HEAD"),cancellable=true)
    private void godwand$atomicStill(MoverType type,Vec3 movement,CallbackInfo ci) {
        if((Object)this instanceof LocalPlayer player && AtomicCinematic.movementLocked()) {
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance=0;
            ci.cancel();
        }
    }
}
