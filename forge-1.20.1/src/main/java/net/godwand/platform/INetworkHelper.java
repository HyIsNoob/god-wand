package net.godwand.platform;

import net.godwand.network.GodWandPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public interface INetworkHelper {
    void sendToPlayer(ServerPlayer player, GodWandPacket packet);
    void sendToNearby(ServerLevel level, Vec3 pos, double radius, GodWandPacket packet);
    void sendToServer(GodWandPacket packet);
}
