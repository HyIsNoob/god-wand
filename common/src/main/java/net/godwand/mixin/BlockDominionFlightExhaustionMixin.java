package net.godwand.mixin;

import net.godwand.skill.BlockDominionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents high-speed sprint/jump/water hunger exhaustion when skimming close to the ground
 * or water while flying on Block Dominion wings (Boost uses the Sprint key).
 */
@Mixin(Player.class)
public abstract class BlockDominionFlightExhaustionMixin {
    @Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
    private void godwand$blockDominionNoFlightExhaustion(float exhaustion, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && BlockDominionManager.isWingFlying(player)) {
            ci.cancel();
        }
    }
}
