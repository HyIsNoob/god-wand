package net.godwand.forge.platform;

import net.godwand.network.GodWandPacket;
import net.godwand.platform.INetworkHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class ForgeNetworkHelper implements INetworkHelper {
    @Override
    public void sendToPlayer(ServerPlayer player, GodWandPacket packet) {
        GodWandForgeNetwork.sendToPlayer(player, packet);
    }

    @Override
    public void sendToNearby(ServerLevel level, Vec3 pos, double radius, GodWandPacket packet) {
        GodWandForgeNetwork.sendToNearby(level, pos, radius, packet);
    }

    @Override
    public void sendToServer(GodWandPacket packet) {
        GodWandForgeNetwork.sendToServer(packet);
    }
}
