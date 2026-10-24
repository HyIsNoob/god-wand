package net.godwand.skill;

import net.godwand.GodWandMod;
import net.godwand.platform.Services;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.network.SpatialAuraPayload;
import net.godwand.spatial.DisplacedBlockRecord;
import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpatialPathWalker {
    private static final java.util.Map<UUID,Integer> LAST_SHIELD_SOUND = new ConcurrentHashMap<>();
    private static final Set<UUID> ACTIVE_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final java.util.Map<UUID, Double> LAST_GROUND_Y = new ConcurrentHashMap<>();
    private static final java.util.Map<UUID, Vec3> LAST_PLAYER_POS = new ConcurrentHashMap<>();
    private static final java.util.Map<UUID, Double> CURRENT_FORWARD_LEAD = new ConcurrentHashMap<>();
    private static final java.util.Map<UUID, Double> CURRENT_PLAYER_SPEED = new ConcurrentHashMap<>();

    public static boolean isActive(Player player) {
        return ACTIVE_PLAYERS.contains(player.getUUID());
    }

    public static void toggle(ServerPlayer player) {
        if (player.getCooldowns().isOnCooldown(net.godwand.GodWandMod.GOD_WAND_ITEM)) return;
        player.getCooldowns().addCooldown(net.godwand.GodWandMod.GOD_WAND_ITEM, net.godwand.config.GodWandConfig.cooldownTicks(20));
        ServerLevel world = player.serverLevel();
        if (ACTIVE_PLAYERS.contains(player.getUUID())) {
            ACTIVE_PLAYERS.remove(player.getUUID());
            SpatialBridge.disable(player.getUUID());
            LAST_GROUND_Y.remove(player.getUUID());
            LAST_PLAYER_POS.remove(player.getUUID());
            CURRENT_FORWARD_LEAD.remove(player.getUUID());
            CURRENT_PLAYER_SPEED.remove(player.getUUID());
            LAST_SHIELD_SOUND.remove(player.getUUID());
            player.displayClientMessage(Component.literal("Path disabled."), true);

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.45f, 0.9f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.55f, 1.2f);

            // Push nearby entities away
            for (net.minecraft.world.entity.Entity e : world.getEntities(player, player.getBoundingBox().inflate(5.0))) {
                Vec3 diff = e.position().subtract(player.position()).normalize().scale(0.95).add(0, 0.28, 0);
                e.push(diff.x, diff.y, diff.z);
                e.hurtMarked = true;
            }

            // Immediately begin 3-Phase Magnetic Snap Reassembly for all blocks in player's aura
            SpatialDisplacementManager.collapsePlayerAura(player.getUUID());
            SpatialDisplacementManager.collapseNearbyDisplacements(world.dimension(), player.blockPosition(), 14.0);

            // Trigger Tactical Collapse Post-FX
            ImpactFxPayload colPayload = new ImpactFxPayload(
                    player.getX(), player.getEyeY(), player.getZ(),
                    ImpactFxPayload.TYPE_COLLAPSE, 8, 1.0f
            );
            Services.NETWORK.sendToNearby(world, player.position(), 48.0, colPayload);
        } else {
            ACTIVE_PLAYERS.add(player.getUUID());
            repelMobs(player);
            deflectProjectiles(player);
            player.displayClientMessage(Component.literal("Path enabled."), true);

            // Audio kích hoạt uy lực
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.75f, 1.25f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.55f, 1.4f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.65f, 1.15f);

            // Rung chấn và nhịp giật FOV pop nhẹ nhàng
            CinematicFxPayload actShake = new CinematicFxPayload(
                    CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                    player.getX(), player.getY(), player.getZ(),
                    0.45f, 10
            );
            CinematicFxPayload actFov = new CinematicFxPayload(
                    CinematicFxPayload.EVENT_FOV_PUNCH,
                    player.getX(), player.getY(), player.getZ(),
                    1.08f, 10
            );
            Services.NETWORK.sendToPlayer(player, actShake);
            Services.NETWORK.sendToPlayer(player, actFov);

            // Bụi hạt không gian bung tỏa thành quả cầu quanh người chơi
            world.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.5, 0.7, 0.5, 0.15);
            world.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1.0, player.getZ(), 36, 0.6, 0.8, 0.6, 0.22);

        }
        syncAura(player);
    }

    public static boolean hasGodWand(Player player) {
        if (player == null || GodWandMod.GOD_WAND_ITEM == null) return false;
        for (net.minecraft.world.item.ItemStack stack : player.getInventory().items) {
            if (stack != null && stack.is(GodWandMod.GOD_WAND_ITEM)) return true;
        }
        for (net.minecraft.world.item.ItemStack stack : player.getInventory().offhand) {
            if (stack != null && stack.is(GodWandMod.GOD_WAND_ITEM)) return true;
        }
        return false;
    }

    public static void resetRuntime() {
        ACTIVE_PLAYERS.clear();
        LAST_GROUND_Y.clear();
        LAST_PLAYER_POS.clear();
        CURRENT_FORWARD_LEAD.clear();
        CURRENT_PLAYER_SPEED.clear();
        LAST_SHIELD_SOUND.clear();
    }

    public static void deactivate(Player player) {
        SpatialBridge.disable(player.getUUID());
        ACTIVE_PLAYERS.remove(player.getUUID());
        LAST_GROUND_Y.remove(player.getUUID());
        LAST_PLAYER_POS.remove(player.getUUID());
        CURRENT_FORWARD_LEAD.remove(player.getUUID());
        CURRENT_PLAYER_SPEED.remove(player.getUUID());
        LAST_SHIELD_SOUND.remove(player.getUUID());
        if (player instanceof ServerPlayer serverPlayer) {
            SpatialDisplacementManager.restoreAllForPlayer(player.getUUID(), serverPlayer.serverLevel());
            syncAura(serverPlayer);
        } else {
            SpatialDisplacementManager.restoreAllForPlayer(player.getUUID(), null);
        }
    }

    private static void syncAura(ServerPlayer player) {
        var payload = new net.godwand.network.SpatialAuraPayload(player.getId(), isActive(player));
        Services.NETWORK.sendToNearby(player.serverLevel(), player.position(), 96.0, payload);
    }

    static boolean shouldPlayShieldSound(UUID player, int tick) {
        Integer previous = LAST_SHIELD_SOUND.get(player);
        if (previous != null && tick >= previous && tick - previous < 6) return false;
        LAST_SHIELD_SOUND.put(player,tick);
        return true;
    }

    private static void sendAuraHit(ServerPlayer player, Vec3 normal) {
        var payload = new net.godwand.network.SpatialAuraPayload(player.getId(), true, normal);
        Services.NETWORK.sendToNearby(player.serverLevel(), player.position(), 96.0, payload);
    }

    private static void deflectProjectiles(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        Vec3 center = player.position().add(0, 1.5, 0);
        // shortcut: scans 16 blocks for incoming projectiles; use a global projectile hook for modded speeds above 16 blocks/tick.
        for (var arrow : world.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                player.getBoundingBox().inflate(16), e -> e.getOwner() != player && e.getDeltaMovement().lengthSqr() > 0.0001)) {
            Vec3 velocity = arrow.getDeltaMovement();
            Vec3 hit = SpatialMath.auraArrowIntersection(arrow.position(), arrow.position().add(velocity), center);
            if (hit == null) {
                Vec3 offset = arrow.position().subtract(center);
                if (offset.lengthSqr() < 25 && velocity.dot(offset) < 0) {
                    arrow.setDeltaMovement(velocity.scale(0.85));
                    arrow.hurtMarked = true;
                }
                continue;
            }
            var obstruction = world.clip(new net.minecraft.world.level.ClipContext(arrow.position(), hit,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, arrow));
            if (obstruction.getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
            Vec3 normal = hit.subtract(center).normalize();
            if (normal.lengthSqr() < 0.01) normal = velocity.normalize().reverse();
            arrow.setPos(hit.add(normal.scale(0.05)));
            arrow.setDeltaMovement(SpatialMath.deflectAuraArrow(velocity, normal));
            if (arrow instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile explosive) {
                explosive.accelerationPower *= 0.35;
            }
            arrow.hurtMarked = true;
            sendAuraHit(player, normal);
            if (shouldPlayShieldSound(player.getUUID(),player.tickCount)) world.playSound(null, hit.x, hit.y, hit.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.35f, 1.6f);
        }
    }

    private static void repelMobs(ServerPlayer player) {
        Vec3 center = player.position().add(0, 1.5, 0);
        for (var mob : player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                player.getBoundingBox().inflate(5), e -> e.isAlive() && !e.isSpectator() && !e.isPassengerOfSameVehicle(player))) {
            Vec3 offset = mob.getBoundingBox().getCenter().subtract(center);
            double distance = offset.length();
            double reach = 3.3 + mob.getBbWidth() * 0.5;
            if (distance > reach + 0.8) continue;
            Vec3 normal;
            if (distance < 0.05) {
                double angle = (mob.getUUID().getLeastSignificantBits() & 1023) * Math.PI * 2 / 1024;
                normal = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            } else {
                normal = offset.normalize();
                if (mob.onGround()) {
                    normal = new Vec3(normal.x, Math.max(0, normal.y), normal.z).normalize();
                    if (normal.lengthSqr() < 0.01) normal = new Vec3(1, 0, 0);
                }
            }
            mob.setDeltaMovement(SpatialMath.auraMobVelocity(mob.getDeltaMovement(), normal, distance - mob.getBbWidth() * 0.5));
            mob.hurtMarked = true;
            if (distance <= reach && (player.tickCount + mob.getId()) % 12 == 0) sendAuraHit(player, normal);
        }
    }

    public static double updatePlayerForwardLead(ServerPlayer player) {
        Vec3 currentPos = player.position();
        Vec3 lastPos = LAST_PLAYER_POS.getOrDefault(player.getUUID(), currentPos);
        LAST_PLAYER_POS.put(player.getUUID(), currentPos);

        Vec3 deltaHoriz = new Vec3(currentPos.x - lastPos.x, 0, currentPos.z - lastPos.z);
        double speed = deltaHoriz.length();
        CURRENT_PLAYER_SPEED.put(player.getUUID(), speed);
        // Nếu tốc độ dịch chuyển hợp lệ (< 4.0 blocks/tick để loại trừ teleport)
        double boost = (speed > 0.02 && speed < 4.0) ? Math.min(3.2, speed * 7.0) : 0.0;
        if (player.isSprinting() && boost < 1.2) {
            boost = 1.2;
        }
        double lead = 1.0 + boost;
        CURRENT_FORWARD_LEAD.put(player.getUUID(), lead);
        return lead;
    }

    public static double getPlayerSpeed(ServerPlayer player) {
        return CURRENT_PLAYER_SPEED.getOrDefault(player.getUUID(), 0.0);
    }

    public static double getPlayerForwardLead(ServerPlayer player) {
        return CURRENT_FORWARD_LEAD.getOrDefault(player.getUUID(), 1.0);
    }

    public static void tickPlayer(ServerPlayer player) {
        if (!isActive(player)) return;

        if (!player.isAlive() || player.isSpectator() || !hasGodWand(player)) {
            deactivate(player);
            return;
        }

        ServerLevel world = player.serverLevel();
        SpatialBridge.tickPlayer(player);
        if (player.tickCount % 20 == 0) syncAura(player);
        if (player.tickCount % 30 == 0) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.40f, 1.2f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_AMBIENT, SoundSource.PLAYERS, 0.30f, 1.25f);
        }
        deflectProjectiles(player);
        repelMobs(player);
        Vec3 playerPos = player.position();
        Vec3 chestPos = playerPos.add(0, 1.1, 0);
        Vec3 lookVec = player.getViewVector(1.0f);
        Vec3 lookDirHoriz = new Vec3(lookVec.x, 0, lookVec.z);
        if (lookDirHoriz.lengthSqr() < 0.001) {
            lookDirHoriz = new Vec3(1, 0, 0);
        } else {
            lookDirHoriz = lookDirHoriz.normalize();
        }

        boolean allowDown = player.getAbilities().flying && player.getXRot() > 45.0f;

        if (player.onGround()) {
            LAST_GROUND_Y.put(player.getUUID(), player.getY());
        }
        double baseGroundY = player.onGround() ? player.getY() : LAST_GROUND_Y.getOrDefault(player.getUUID(), player.getY());
        int floorY = (int) Math.floor(baseGroundY);

        double forwardLead = updatePlayerForwardLead(player);
        double auraRadius = 2.1;
        double maxRange = 3.3 + forwardLead + 0.8;

        int minX = Mth.floor(playerPos.x - maxRange);
        int maxX = Mth.floor(playerPos.x + maxRange);
        int minY = allowDown ? Mth.floor(playerPos.y - 2.2) : floorY;
        int maxY = Mth.floor(playerPos.y + 4.8);
        int minZ = Mth.floor(playerPos.z - maxRange);
        int maxZ = Mth.floor(playerPos.z + maxRange);

        // Falling entities have not become world blocks yet, so the block scan cannot see them.
        for (FallingBlockEntity falling : world.getEntitiesOfClass(FallingBlockEntity.class,
                player.getBoundingBox().inflate(maxRange + 1), e -> e.isAlive())) {
            if (falling.getY() < baseGroundY || SpatialMath.distanceToPathAura(
                    falling.position(), chestPos, lookDirHoriz, forwardLead) > 3.9) continue;
            falling.setDeltaMovement(SpatialMath.pathLooseBlockVelocity(falling.position(), chestPos, lookDirHoriz));
            falling.hurtMarked = true;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutablePos.set(x, y, z);

                    // Preserve actual support surfaces, not the obstacle row at foot height.
                    if (!SpatialMath.canOpenPathBlock(y, baseGroundY, allowDown)) continue;
                    BlockState state = world.getBlockState(mutablePos);
                    if (!allowDown) {
                        var shape = state.getCollisionShape(world, mutablePos, net.minecraft.world.phys.shapes.CollisionContext.of(player));
                        if (!shape.isEmpty() && y + shape.max(net.minecraft.core.Direction.Axis.Y) <= baseGroundY + 0.125) continue;
                        boolean supportsFeet = false;
                        for (var box : shape.toAabbs()) {
                            if (SpatialMath.supportsPathFeet(box.move(mutablePos), player.getBoundingBox())) {
                                supportsFeet = true;
                                break;
                            }
                        }
                        if (supportsFeet) continue;
                    }

                    Vec3 blockCenter = Vec3.atCenterOf(mutablePos);

                    // Quét 2 tầng (Tầng trong <= 2.1m vòm cao, Tầng ngoài <= 3.3m sóng địa chấn)
                    double spineDist = SpatialMath.distanceToPathAura(blockCenter, chestPos, lookDirHoriz, forwardLead);
                    if (spineDist > 3.3) continue;

                    // Distance Hysteresis: keep open and reset restore tick if already displaced
                    if (SpatialDisplacementManager.isPositionDisplaced(world.dimension(), mutablePos)) {
                        SpatialDisplacementManager.refreshDisplacement(world.dimension(), mutablePos, 100);
                        // An incoming block must not overwrite the original block saved for restoration.
                        if (!state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity()
                                && state.getDestroySpeed(world, mutablePos) >= 0) {
                            FallingBlockEntity loose = FallingBlockEntity.fall(world, mutablePos.immutable(), state);
                            loose.setDeltaMovement(SpatialMath.pathLooseBlockVelocity(blockCenter, chestPos, lookDirHoriz));
                            loose.hurtMarked = true;
                        }
                        continue;
                    }

                    if (!SpatialDisplacementManager.canDisplace(world, mutablePos, state)) continue;

                    // Compute dynamic aura offset from SpatialMath
                    Vec3 auraDisp = SpatialMath.computeDynamicAuraOffset(mutablePos, playerPos, lookDirHoriz, auraRadius, 0);
                    Vector3f targetDisp = new Vector3f((float) auraDisp.x, (float) auraDisp.y, (float) auraDisp.z);

                    Quaternionf rotation = SpatialMath.computeArchwayRotation(mutablePos, playerPos, lookDirHoriz, 0);

                    // Thu nhỏ nhẹ kích thước mỗi block (0.82f) để tạo khe hở ma pháp giữa các viên gạch thay vì dính bệt vào nhau!
                    float blockScale = 0.78f + (float) (SpatialMath.hash01(mutablePos) * 0.14);
                    Vector3f scale = new Vector3f(blockScale, blockScale, blockScale);

                    // Xen kẽ màu viền Cyan (0x00F5FF) và Amethyst Purple (0xA855F7)
                    int glowColor = ((x + y + z) & 1) == 0 ? 0x00F5FF : 0xA855F7;

                    SpatialDisplacementManager.displaceBlock(
                            world,
                            mutablePos.immutable(),
                            targetDisp,
                            rotation,
                            scale,
                            100,
                            glowColor,
                            true,
                            DisplacedBlockRecord.DisplacementMode.PATH_WALKER,
                            player.getUUID(),
                            null,
                            0.0
                    );

                }
            }
        }

    }
}
