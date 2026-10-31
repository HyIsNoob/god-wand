package net.godwand.fabric.platform;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.platform.INetworkHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class FabricNetworkHelper implements INetworkHelper {
    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToNearby(ServerLevel level, Vec3 pos, double radius, CustomPacketPayload payload) {
        for (ServerPlayer player : PlayerLookup.around(level, pos, radius)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
