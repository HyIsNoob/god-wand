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
        if((Object)this instanceof LocalPlayer player && (AtomicCinematic.movementLocked()||net.godwand.client.FugaCinematic.movementLocked())) {
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance=0;
            ci.cancel();
        }
    }
    // The Fuga build-up is a directed shot: mouse look must not swing the caster or the frame.
    @Inject(method="turn",at=@At("HEAD"),cancellable=true)
    private void godwand$fugaFacing(double yaw,double pitch,CallbackInfo ci) {
        if((Object)this instanceof LocalPlayer && net.godwand.client.FugaCinematic.rotationLocked())ci.cancel();
    }
    // Fuga is fired level: while it is held the caster keeps turning left and right but cannot tilt the view.
    @Inject(method="turn",at=@At("RETURN"))
    private void godwand$fugaLevel(double yaw,double pitch,CallbackInfo ci) {
        if((Object)this instanceof LocalPlayer player && net.godwand.client.FugaCinematic.aimLevel()) { player.setXRot(0); player.xRotO=0; }
    }
}
