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
 * Client-side input & synchronized local movement prediction for Block Dominion (mode 9):
 * - Shift+LMB cycles mode; LMB/hold lifts blocks; LMB on open air triggers Earthen Surge
 * - Double-tap Space toggles Block Wings flight; Shift+Space while flying switches Hover <-> Elytra Glide mode
 * - Synchronized local prediction for Hover, Elytra Glide + Rocket Boost, and Ascending/Descending Block Stairs.
 */
public final class BlockDominionInput {
    private static boolean suppressHoldUntilRelease;
    private static int holdCooldown;

    private static boolean prevJumpDown;
    private static int jumpTapWindow;
    private static int flightPacketCooldown;
    private static int stairPacketCooldown;
    private static int lastSentFwd, lastSentStrafe, lastSentVert;
    private static boolean lastSentBoost;
    private static boolean lastSentAscend;
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
        if (BlockDominionRenderer.isLocalManualActive()
                && (BlockDominionRenderer.localReserveCount() >= BlockDominionRules.CAGE_MIN_BLOCKS
                || BlockDominionRenderer.hasLocalActiveCage())) {
            net.minecraft.world.entity.LivingEntity target = findCrosshairLivingTarget(client, 20.0);
            if (target != null) {
                suppressHoldUntilRelease = true;
                Services.NETWORK.sendToServer(BlockDominionInputPayload.cageEntity(target.getId()));
                return;
            }
        }
        HitResult blockCheck = client.hitResult;
        if (!(blockCheck instanceof BlockHitResult bhr0 && bhr0.getType() == HitResult.Type.BLOCK)) {
            blockCheck = client.player.pick(7.0, 1.0f, false);
        }
        if (blockCheck instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            holdCooldown = BlockDominionRules.HOLD_PICKUP_INTERVAL;
            Services.NETWORK.sendToServer(new BlockDominionInputPayload(
                    BlockDominionInputPayload.ACTION_PICKUP, bhr.getBlockPos()));
        } else if (BlockDominionRenderer.isLocalManualActive()) {
            suppressHoldUntilRelease = true;
            Services.NETWORK.sendToServer(new BlockDominionInputPayload(
                    BlockDominionInputPayload.ACTION_EARTHEN_SURGE, BlockPos.ZERO));
        }
    }

    /** Finds a hostile/living target under the player's crosshair up to {@code range} blocks for Monolith Cage. */
    private static net.minecraft.world.entity.LivingEntity findCrosshairLivingTarget(Minecraft client, double range) {
        if (client.hitResult instanceof net.minecraft.world.phys.EntityHitResult ehr
                && ehr.getEntity() instanceof net.minecraft.world.entity.LivingEntity l && l.isAlive()) {
            return l;
        }
        if (client.level == null || client.player == null) return null;
        if (client.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) return null;
        Vec3 eye = client.player.getEyePosition(1.0f);
        Vec3 look = client.player.getViewVector(1.0f).normalize();
        Vec3 end = eye.add(look.scale(range));
        var box = client.player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.2);
        net.minecraft.world.entity.LivingEntity best = null;
        double bestDistSq = range * range;
        for (var entity : client.level.getEntities(client.player, box,
                e -> e instanceof net.minecraft.world.entity.LivingEntity l && l.isAlive() && !l.isSpectator())) {
            var hit = entity.getBoundingBox().inflate(0.65).clip(eye, end);
            if (hit.isPresent()) {
                double d2 = eye.distanceToSqr(hit.get());
                if (d2 < bestDistSq) {
                    bestDistSq = d2;
                    best = (net.minecraft.world.entity.LivingEntity) entity;
                }
            }
        }
        return best;
    }

    /** Per-client-tick handler for continuous pickup, double-tap Space wing toggle, Hover/Glide flight & Air-Step stairs. */
    public static void tick(Minecraft client) {
        if (client == null || client.player == null || client.getConnection() == null) {
            reset();
            return;
        }
        LocalPlayer player = client.player;
        boolean inGui = client.screen != null;

        tickMobility(client, player, inGui);

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
        boolean shiftDown = !inGui && client.options.keyShift.isDown();
        boolean freshJump = jumpDown && !prevJumpDown;
        prevJumpDown = jumpDown;
        if (jumpTapWindow > 0) jumpTapWindow--;

        boolean localManual = BlockDominionRenderer.isLocalManualActive();
        int localBlocks = BlockDominionRenderer.localReserveCount();
        boolean localFlying = BlockDominionRenderer.isLocalWingFlying();
        boolean localGlide = BlockDominionRenderer.isLocalGlideMode();
        boolean localStairs = BlockDominionRenderer.isLocalAirStairs();

        // While flying, Shift + Space cleanly toggles between Hover Mode and Elytra Glide Mode!
        if (localFlying && freshJump && shiftDown) {
            jumpTapWindow = 0;
            Services.NETWORK.sendToServer(new BlockDominionInputPayload(
                    BlockDominionInputPayload.ACTION_TOGGLE_FLIGHT_STYLE, BlockPos.ZERO));
            return;
        }

        // Double-tap Space toggles Block Wings flight ON/OFF
        if (freshJump && !shiftDown && localManual && (localFlying || (localBlocks >= BlockDominionRules.MIN_FLIGHT_BLOCKS && !player.onGround()))) {
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
            boolean chargingOrbital = BlockDominionRenderer.isLocalVolleyCharging()
                    || (!inGui && shiftDown && client.options.keyUse.isDown() && eligible(player));
            int vert = inGui ? 0 : (jumpDown ? 1 : 0) - (shiftDown && !chargingOrbital ? 1 : 0);
            boolean sprintDown = !inGui && (client.options.keySprint.isDown() || player.isSprinting());
            boolean boost = !chargingOrbital && (localGlide
                    ? (sprintDown || jumpDown)
                    : (sprintDown && (fwd != 0 || strafe != 0 || vert != 0)));

            if (chargingOrbital) {
                // Hold regal midair hover while charging Shift+RMB Orbital Meteor Barrage!
                localFlightVel = BlockDominionRules.stepFlightVelocity(
                        localFlightVel, player.getViewVector(1.0f), 0, 0, Math.max(0, vert), false, localBlocks);
            } else if (localGlide) {
                localFlightVel = BlockDominionRules.stepGlideVelocity(
                        localFlightVel, player.getViewVector(1.0f), strafe, boost, localBlocks);
            } else {
                localFlightVel = BlockDominionRules.stepFlightVelocity(
                        localFlightVel, player.getViewVector(1.0f), fwd, strafe, vert, boost, localBlocks);
            }
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
    }

    public static void reset() {
        suppressHoldUntilRelease = false;
        holdCooldown = 0;
        prevJumpDown = false;
        jumpTapWindow = 0;
        flightPacketCooldown = 0;
        stairPacketCooldown = 0;
        lastSentFwd = lastSentStrafe = lastSentVert = 0;
        lastSentBoost = false;
        lastSentAscend = false;
        localFlightVel = Vec3.ZERO;
        wasLocalFlying = false;
    }
}
