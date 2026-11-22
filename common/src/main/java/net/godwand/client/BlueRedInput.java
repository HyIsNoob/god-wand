package net.godwand.client;

import net.godwand.item.GodWandItem;
import net.godwand.network.BlueInputPayload;
import net.godwand.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Turns holding left click with the wand on Blue/Red into start/stop messages for the server. */
public final class BlueRedInput {
    private static boolean held;
    /** Left-click hold as seen by Minecraft.continueAttack; also true for controller attack buttons. */
    public static boolean attackHeld;
    private BlueRedInput() {}

    /** Main hand holds the wand set to Blue/Red; the attack mixin swallows left click only then. */
    public static boolean eligible(Player player) {
        return player != null && !player.isSpectator() && player.getMainHandItem().getItem() instanceof GodWandItem
                && GodWandItem.getSkillMode(player.getMainHandItem()) == GodWandItem.MODE_BLUE_RED;
    }

    /** TRUE when holding starts, FALSE when it stops, null when nothing changed. */
    public static Boolean edge(boolean wasHeld, boolean attackDown, boolean eligible, boolean screenOpen, boolean focused) {
        boolean now = attackDown && eligible && !screenOpen && focused;
        return now == wasHeld ? null : now;
    }

    public static void tick(Minecraft client) {
        if (client.player == null || client.getConnection() == null) { held = false; return; }
        Boolean change = edge(held, client.options.keyAttack.isDown() || attackHeld, eligible(client.player), client.screen != null, client.isWindowActive());
        if (change == null) return;
        held = change;
        Services.NETWORK.sendToServer(new BlueInputPayload(held));
    }

    public static void reset() { held = false; attackHeld = false; }
}
