package net.godwand.mixin;

import net.godwand.skill.AtomicManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class AtomicMovementMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handleMovePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",shift=At.Shift.AFTER),cancellable=true)
    private void godwand$atomicAnchor(ServerboundMovePlayerPacket packet,CallbackInfo ci) {
        if(AtomicManager.movementLocked(player))ci.cancel();
    }
}
