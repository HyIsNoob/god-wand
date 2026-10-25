package net.godwand.fabric.platform;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.network.GodWandPacket;
import net.godwand.platform.INetworkHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class FabricNetworkHelper implements INetworkHelper {
    @Override
    public void sendToPlayer(ServerPlayer player, GodWandPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(buf);
        ServerPlayNetworking.send(player, packet.getId(), buf);
    }

    @Override
    public void sendToNearby(ServerLevel level, Vec3 pos, double radius, GodWandPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(buf);
        for (ServerPlayer player : PlayerLookup.around(level, pos, radius)) {
            ServerPlayNetworking.send(player, packet.getId(), buf);
        }
    }

    @Override
    public void sendToServer(GodWandPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(buf);
        ClientPlayNetworking.send(packet.getId(), buf);
    }
}
