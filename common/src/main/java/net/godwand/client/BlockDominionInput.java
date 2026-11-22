package net.godwand.client;

import net.godwand.item.GodWandItem;
import net.godwand.network.BlockDominionInputPayload;
import net.godwand.platform.Services;
import net.godwand.skill.BlockDominionRules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Client-side left-click handler for Block Dominion (mode 9): Shift+LMB cycles mode, LMB/hold lifts blocks. */
public final class BlockDominionInput {
    private static boolean suppressHoldUntilRelease;
    private static int holdCooldown;

    private BlockDominionInput() {}

    /** True when main hand holds God Wand on Block Dominion; suppresses block breaking in both Survival and Creative. */
    public static boolean eligible(Player player) {
        return player != null && !player.isSpectator()
                && player.getMainHandItem().getItem() instanceof GodWandItem
                && GodWandItem.getSkillMode(player.getMainHandItem()) == GodWandItem.MODE_BLOCK_DOMINION;
    }

    /** Called from Minecraft.startAttack when eligible; always consumes left-click so blocks are never broken. */
    public static void onStartAttack(Minecraft client) {
        if (client == null || client.player == null || client.getConnection() == null) return;
        if (client.player.isShiftKeyDown()) {
            suppressHoldUntilRelease = true;
            holdCooldown = BlockDominionRules.HOLD_PICKUP_INTERVAL;
            Services.NETWORK.sendToServer(new BlockDominionInputPayload(
                    BlockDominionInputPayload.ACTION_CYCLE_MODE, net.minecraft.core.BlockPos.ZERO));
            return;
        }
        suppressHoldUntilRelease = false;
        if (client.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            holdCooldown = BlockDominionRules.HOLD_PICKUP_INTERVAL;
            Services.NETWORK.sendToServer(new BlockDominionInputPayload(
                    BlockDominionInputPayload.ACTION_PICKUP, bhr.getBlockPos()));
        }
    }

    /** Continuous pickup while left-click is held on blocks (every 4 ticks). */
    public static void tick(Minecraft client) {
        if (client == null || client.player == null || client.getConnection() == null) {
            reset();
            return;
        }
        boolean attackDown = client.options.keyAttack.isDown() || BlueRedInput.attackHeld;
        if (!attackDown || client.screen != null || !client.isWindowActive() || !eligible(client.player)) {
            suppressHoldUntilRelease = false;
            holdCooldown = 0;
            return;
        }
        if (suppressHoldUntilRelease || client.player.isShiftKeyDown()) return;
        if (holdCooldown > 0) {
            holdCooldown--;
            return;
        }
        if (client.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            holdCooldown = BlockDominionRules.HOLD_PICKUP_INTERVAL;
            Services.NETWORK.sendToServer(new BlockDominionInputPayload(
                    BlockDominionInputPayload.ACTION_PICKUP, bhr.getBlockPos()));
        }
    }

    public static void reset() {
        suppressHoldUntilRelease = false;
        holdCooldown = 0;
    }
}
