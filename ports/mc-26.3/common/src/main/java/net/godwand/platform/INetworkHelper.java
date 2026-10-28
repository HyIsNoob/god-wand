package net.godwand.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public interface INetworkHelper {
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    void sendToNearby(ServerLevel level, Vec3 pos, double radius, CustomPacketPayload payload);

    void sendToServer(CustomPacketPayload payload);
}
