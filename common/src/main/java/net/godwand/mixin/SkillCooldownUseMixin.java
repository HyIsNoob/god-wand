package net.godwand.mixin;

import net.godwand.client.SkillCooldownClient;
import net.godwand.item.GodWandItem;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class SkillCooldownUseMixin {
    @Inject(method="useItem",at=@At("HEAD"),cancellable=true)
    private void godwand$skillCooldown(Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResult> ci) {
        var stack=player.getItemInHand(hand);
        if(stack.getItem() instanceof GodWandItem && !SkillCooldownClient.canUse(GodWandItem.getSkillMode(stack)))ci.setReturnValue(InteractionResult.FAIL);
    }
}
