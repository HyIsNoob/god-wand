package net.godwand.neoforge.platform;

import net.godwand.platform.INetworkHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class NeoForgeNetworkHelper implements INetworkHelper {
    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToNearby(ServerLevel level, Vec3 pos, double radius, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, radius, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload);
    }
}
