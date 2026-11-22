package net.godwand.client;

import net.godwand.item.GodWandItem;
import net.godwand.network.BlockDominionInputPayload;
import net.godwand.platform.Services;
import net.godwand.skill.BlockDominionRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side input & local movement prediction for Block Dominion (mode 9):
 * - Shift+LMB cycles mode; LMB/hold lifts blocks
 * - Double-tap Space toggles Block Wings 3D free flight (works in Survival & Creative)
 * - Synchronized local velocity prediction for silky Wing Flight (accelerate / boost / hover)
 *   and Air-Step Block Stairs ("stepping down creates stairs").
 */
public final class BlockDominionInput {
    private static boolean suppressHoldUntilRelease;
    private static int holdCooldown;

    // Double-tap Space detector for Block Wings toggle
    private static boolean prevJumpDown;
    private static int jumpTapWindow;
    private static int flightPacketCooldown;
    private static int lastSentFwd, lastSentStrafe, lastSentVert;
    private static boolean lastSentBoost;
    private static Vec3 localFlightVel = Vec3.ZERO;
    private static boolean wasLocalFlying;

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
                    BlockDominionInputPayload.ACTION_CYCLE_MODE, BlockPos.ZERO));
            return;
        }
        suppressHoldUntilRelease = false;
        if (client.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            holdCooldown = BlockDominionRules.HOLD_PICKUP_INTERVAL;
            Services.NETWORK.sendToServer(new BlockDominionInputPayload(
                    BlockDominionInputPayload.ACTION_PICKUP, bhr.getBlockPos()));
        }
    }

    /** Per-client-tick handler for continuous pickup, double-tap Space wing toggle, and local flight/stairs prediction. */
    public static void tick(Minecraft client) {
        if (client == null || client.player == null || client.getConnection() == null) {
            reset();
            return;
        }
        LocalPlayer player = client.player;
        boolean inGui = client.screen != null || !client.isWindowActive();

        // 1. Block Wings double-tap Space toggle & local 3D flight / air-step prediction
        tickMobility(client, player, inGui);

        // 2. Continuous LMB hold pickup
        boolean attackDown = client.options.keyAttack.isDown() || BlueRedInput.attackHeld;
        if (!attackDown || inGui || !eligible(player)) {
            suppressHoldUntilRelease = false;
            holdCooldown = 0;
            return;
        }
        if (suppressHoldUntilRelease || player.isShiftKeyDown()) return;
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

    private static void tickMobility(Minecraft client, LocalPlayer player, boolean inGui) {
        boolean jumpDown = !inGui && client.options.keyJump.isDown();
        boolean freshJump = jumpDown && !prevJumpDown;
        prevJumpDown = jumpDown;
        if (jumpTapWindow > 0) jumpTapWindow--;

        boolean localManual = BlockDominionRenderer.isLocalManualActive();
        int localBlocks = BlockDominionRenderer.localReserveCount();
        boolean localFlying = BlockDominionRenderer.isLocalWingFlying();
        boolean localStairs = BlockDominionRenderer.isLocalAirStairs();

        // Detect double-tap Space to toggle Block Wings flight
        if (freshJump && localManual && (localFlying || (localBlocks >= BlockDominionRules.MIN_FLIGHT_BLOCKS && !player.onGround()))) {
            if (jumpTapWindow > 0) {
                jumpTapWindow = 0;
                Services.NETWORK.sendToServer(new BlockDominionInputPayload(
                        BlockDominionInputPayload.ACTION_TOGGLE_FLIGHT, BlockPos.ZERO));
                if (!localFlying && player.getAbilities().flying) {
                    player.getAbilities().flying = false;
                }
            } else {
                jumpTapWindow = 8;
            }
        }

        if (localFlying) {
            if (!wasLocalFlying) {
                localFlightVel = player.getDeltaMovement();
                wasLocalFlying = true;
            }
            if (player.getAbilities().flying) {
                player.getAbilities().flying = false;
            }
            int fwd = inGui ? 0 : (client.options.keyUp.isDown() ? 1 : 0) - (client.options.keyDown.isDown() ? 1 : 0);
            int strafe = inGui ? 0 : (client.options.keyRight.isDown() ? 1 : 0) - (client.options.keyLeft.isDown() ? 1 : 0);
            int vert = inGui ? 0 : (client.options.keyJump.isDown() ? 1 : 0) - (client.options.keyShift.isDown() ? 1 : 0);
            boolean boost = !inGui && (client.options.keySprint.isDown() || player.isSprinting())
                    && (fwd != 0 || strafe != 0 || vert != 0);

            localFlightVel = BlockDominionRules.stepFlightVelocity(
                    localFlightVel, player.getViewVector(1.0f), fwd, strafe, vert, boost);
            player.setNoGravity(true);
            player.fallDistance = 0.0f;
            player.setDeltaMovement(localFlightVel);

            boolean changed = fwd != lastSentFwd || strafe != lastSentStrafe || vert != lastSentVert || boost != lastSentBoost;
            if (changed || flightPacketCooldown <= 0) {
                Services.NETWORK.sendToServer(BlockDominionInputPayload.flightControl(fwd, strafe, vert, boost));
                lastSentFwd = fwd;
                lastSentStrafe = strafe;
                lastSentVert = vert;
                lastSentBoost = boost;
                flightPacketCooldown = 2;
            } else {
                flightPacketCooldown--;
            }
            return;
        }

        if (wasLocalFlying) {
            wasLocalFlying = false;
            localFlightVel = Vec3.ZERO;
            player.setNoGravity(false);
        }

        // Local prediction for Air-Step Block Stairs so walking off ledges catches smoothly on the very first tick
        if (localStairs && !player.onGround() && !player.isShiftKeyDown() && !player.getAbilities().flying) {
            Vec3 cur = player.getDeltaMovement();
            double horiz = Math.sqrt(cur.x * cur.x + cur.z * cur.z);
            Vec3 next = BlockDominionRules.stepAirStairsVelocity(cur, horiz, freshJump);
            player.fallDistance = 0.0f;
            player.setDeltaMovement(next);
        }
    }

    public static void reset() {
        suppressHoldUntilRelease = false;
        holdCooldown = 0;
        prevJumpDown = false;
        jumpTapWindow = 0;
        flightPacketCooldown = 0;
        lastSentFwd = lastSentStrafe = lastSentVert = 0;
        lastSentBoost = false;
        localFlightVel = Vec3.ZERO;
        wasLocalFlying = false;
    }
}
