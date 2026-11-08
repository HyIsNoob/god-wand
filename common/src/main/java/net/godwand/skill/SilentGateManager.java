package net.godwand.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

public final class SilentGateManager {
    private SilentGateManager() {}

    public static boolean click(ServerPlayer player, InteractionHand hand) { return false; }
    public static void cancelFromClient(ServerPlayer player, long startedAt) {}
}
