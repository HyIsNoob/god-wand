package net.godwand.skill;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.spatial.DisplacedBlockRecord;
import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.block.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpatialPathWalker {
    private static final java.util.Map<UUID,Integer> LAST_SHIELD_SOUND = new ConcurrentHashMap<>();
    private static final Set<UUID> ACTIVE_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final java.util.Map<UUID, Double> LAST_GROUND_Y = new ConcurrentHashMap<>();
    private static final java.util.Map<UUID, Vec3d> LAST_PLAYER_POS = new ConcurrentHashMap<>();
    private static final java.util.Map<UUID, Double> CURRENT_FORWARD_LEAD = new ConcurrentHashMap<>();
    private static final java.util.Map<UUID, Double> CURRENT_PLAYER_SPEED = new ConcurrentHashMap<>();

    public static boolean isActive(PlayerEntity player) {
        return ACTIVE_PLAYERS.contains(player.getUuid());
    }

    public static void toggle(ServerPlayerEntity player) {
        if (player.getItemCooldownManager().isCoolingDown(net.godwand.GodWandMod.GOD_WAND_ITEM)) return;
        player.getItemCooldownManager().set(net.godwand.GodWandMod.GOD_WAND_ITEM, net.godwand.config.GodWandConfig.cooldownTicks(20));
        ServerWorld world = player.getServerWorld();
        if (ACTIVE_PLAYERS.contains(player.getUuid())) {
            ACTIVE_PLAYERS.remove(player.getUuid());
            SpatialBridge.disable(player.getUuid());
            LAST_GROUND_Y.remove(player.getUuid());
            LAST_PLAYER_POS.remove(player.getUuid());
            CURRENT_FORWARD_LEAD.remove(player.getUuid());
            CURRENT_PLAYER_SPEED.remove(player.getUuid());
            LAST_SHIELD_SOUND.remove(player.getUuid());
            player.sendMessage(Text.literal("Path disabled."), true);

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 0.45f, 0.9f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.55f, 1.2f);

            // Push nearby entities away
            for (net.minecraft.entity.Entity e : world.getOtherEntities(player, player.getBoundingBox().expand(5.0))) {
                Vec3d diff = e.getPos().subtract(player.getPos()).normalize().multiply(0.95).add(0, 0.28, 0);
                e.addVelocity(diff.x, diff.y, diff.z);
                e.velocityModified = true;
            }

            // Immediately begin 3-Phase Magnetic Snap Reassembly for all blocks in player's aura
            SpatialDisplacementManager.collapsePlayerAura(player.getUuid());
            SpatialDisplacementManager.collapseNearbyDisplacements(world.getRegistryKey(), player.getBlockPos(), 14.0);

            // Trigger Tactical Collapse Post-FX
            ImpactFxPayload colPayload = new ImpactFxPayload(
                    player.getX(), player.getEyeY(), player.getZ(),
                    ImpactFxPayload.TYPE_COLLAPSE, 8, 1.0f
            );
            for (ServerPlayerEntity p : PlayerLookup.around(world, player.getPos(), 48.0)) {
                ServerPlayNetworking.send(p, colPayload);
            }
        } else {
            ACTIVE_PLAYERS.add(player.getUuid());
            repelMobs(player);
            deflectProjectiles(player);
            player.sendMessage(Text.literal("Path enabled."), true);

            // Audio kích hoạt uy lực
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.65f, 1.25f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.35f, 1.2f);

            // Rung chấn nhẹ tức thời (Subtle Camera Pop Shake)
            CinematicFxPayload actShake = new CinematicFxPayload(
                    CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                    player.getX(), player.getY(), player.getZ(),
                    0.32f, 8
            );
            ServerPlayNetworking.send(player, actShake);

            // Sóng năng lượng dưới chân bung tỏa ra từ người chơi (Concentric Ground Shockwave)

        }
        syncAura(player);
    }

    public static void deactivate(PlayerEntity player) {
        SpatialBridge.disable(player.getUuid());
        ACTIVE_PLAYERS.remove(player.getUuid());
        LAST_GROUND_Y.remove(player.getUuid());
        LAST_PLAYER_POS.remove(player.getUuid());
        CURRENT_FORWARD_LEAD.remove(player.getUuid());
        CURRENT_PLAYER_SPEED.remove(player.getUuid());
        LAST_SHIELD_SOUND.remove(player.getUuid());
        SpatialDisplacementManager.collapsePlayerAura(player.getUuid());
        if (player instanceof ServerPlayerEntity serverPlayer) syncAura(serverPlayer);
    }

    private static void syncAura(ServerPlayerEntity player) {
        var payload = new net.godwand.network.SpatialAuraPayload(player.getId(), isActive(player));
        for (ServerPlayerEntity viewer : PlayerLookup.around(player.getServerWorld(), player.getPos(), 96)) {
            ServerPlayNetworking.send(viewer, payload);
        }
    }

    static boolean shouldPlayShieldSound(UUID player, int tick) {
        Integer previous = LAST_SHIELD_SOUND.get(player);
        if (previous != null && tick >= previous && tick - previous < 6) return false;
        LAST_SHIELD_SOUND.put(player,tick);
        return true;
    }

    private static void sendAuraHit(ServerPlayerEntity player, Vec3d normal) {
        var payload = new net.godwand.network.SpatialAuraPayload(player.getId(), true, normal);
        for (var viewer : PlayerLookup.around(player.getServerWorld(), player.getPos(), 96)) {
            ServerPlayNetworking.send(viewer, payload);
        }
    }

    private static void deflectProjectiles(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d center = player.getPos().add(0, 1.5, 0);
        // shortcut: scans 16 blocks for incoming projectiles; use a global projectile hook for modded speeds above 16 blocks/tick.
        for (var arrow : world.getEntitiesByClass(net.minecraft.entity.projectile.ProjectileEntity.class,
                player.getBoundingBox().expand(16), e -> e.getOwner() != player && e.getVelocity().lengthSquared() > 0.0001)) {
            Vec3d velocity = arrow.getVelocity();
            Vec3d hit = SpatialMath.auraArrowIntersection(arrow.getPos(), arrow.getPos().add(velocity), center);
            if (hit == null) {
                Vec3d offset = arrow.getPos().subtract(center);
                if (offset.lengthSquared() < 25 && velocity.dotProduct(offset) < 0) {
                    arrow.setVelocity(velocity.multiply(0.85));
                    arrow.velocityModified = true;
                }
                continue;
            }
            var obstruction = world.raycast(new net.minecraft.world.RaycastContext(arrow.getPos(), hit,
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE, arrow));
            if (obstruction.getType() != net.minecraft.util.hit.HitResult.Type.MISS) continue;
            Vec3d normal = hit.subtract(center).normalize();
            if (normal.lengthSquared() < 0.01) normal = velocity.normalize().negate();
            arrow.setPosition(hit.add(normal.multiply(0.05)));
            arrow.setVelocity(SpatialMath.deflectAuraArrow(velocity, normal));
            if (arrow instanceof net.minecraft.entity.projectile.ExplosiveProjectileEntity explosive) {
                explosive.accelerationPower *= 0.35;
            }
            arrow.velocityModified = true;
            sendAuraHit(player, normal);
            if (shouldPlayShieldSound(player.getUuid(),player.age)) world.playSound(null, hit.x, hit.y, hit.z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.35f, 1.6f);
        }
    }

    private static void repelMobs(ServerPlayerEntity player) {
        Vec3d center = player.getPos().add(0, 1.5, 0);
        for (var mob : player.getServerWorld().getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class,
                player.getBoundingBox().expand(5), e -> e.isAlive() && !e.isSpectator() && !e.isConnectedThroughVehicle(player))) {
            Vec3d offset = mob.getBoundingBox().getCenter().subtract(center);
            double distance = offset.length();
            double reach = 3.3 + mob.getWidth() * 0.5;
            if (distance > reach + 0.8) continue;
            Vec3d normal;
            if (distance < 0.05) {
                double angle = (mob.getUuid().getLeastSignificantBits() & 1023) * Math.PI * 2 / 1024;
                normal = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
            } else {
                normal = offset.normalize();
                if (mob.isOnGround()) {
                    normal = new Vec3d(normal.x, Math.max(0, normal.y), normal.z).normalize();
                    if (normal.lengthSquared() < 0.01) normal = new Vec3d(1, 0, 0);
                }
            }
            mob.setVelocity(SpatialMath.auraMobVelocity(mob.getVelocity(), normal, distance - mob.getWidth() * 0.5));
            mob.velocityModified = true;
            if (distance <= reach && (player.age + mob.getId()) % 12 == 0) sendAuraHit(player, normal);
        }
    }

    public static double updatePlayerForwardLead(ServerPlayerEntity player) {
        Vec3d currentPos = player.getPos();
        Vec3d lastPos = LAST_PLAYER_POS.getOrDefault(player.getUuid(), currentPos);
        LAST_PLAYER_POS.put(player.getUuid(), currentPos);

        Vec3d deltaHoriz = new Vec3d(currentPos.x - lastPos.x, 0, currentPos.z - lastPos.z);
        double speed = deltaHoriz.length();
        CURRENT_PLAYER_SPEED.put(player.getUuid(), speed);
        // Nếu tốc độ dịch chuyển hợp lệ (< 4.0 blocks/tick để loại trừ teleport)
        double boost = (speed > 0.02 && speed < 4.0) ? Math.min(3.2, speed * 7.0) : 0.0;
        if (player.isSprinting() && boost < 1.2) {
            boost = 1.2;
        }
        double lead = 1.0 + boost;
        CURRENT_FORWARD_LEAD.put(player.getUuid(), lead);
        return lead;
    }

    public static double getPlayerSpeed(ServerPlayerEntity player) {
        return CURRENT_PLAYER_SPEED.getOrDefault(player.getUuid(), 0.0);
    }

    public static double getPlayerForwardLead(ServerPlayerEntity player) {
        return CURRENT_FORWARD_LEAD.getOrDefault(player.getUuid(), 1.0);
    }

    public static void tickPlayer(ServerPlayerEntity player) {
        if (!isActive(player)) return;

        ServerWorld world = player.getServerWorld();
        SpatialBridge.tickPlayer(player);
        if (player.age % 20 == 0) syncAura(player);
        if (player.age % 30 == 0) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.40f, 1.2f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_RESPAWN_ANCHOR_AMBIENT, SoundCategory.PLAYERS, 0.30f, 1.25f);
        }
        deflectProjectiles(player);
        repelMobs(player);
        Vec3d playerPos = player.getPos();
        Vec3d chestPos = playerPos.add(0, 1.1, 0);
        Vec3d lookVec = player.getRotationVec(1.0f);
        Vec3d lookDirHoriz = new Vec3d(lookVec.x, 0, lookVec.z);
        if (lookDirHoriz.lengthSquared() < 0.001) {
            lookDirHoriz = new Vec3d(1, 0, 0);
        } else {
            lookDirHoriz = lookDirHoriz.normalize();
        }

        boolean allowDown = player.getAbilities().flying && player.getPitch() > 45.0f;

        if (player.isOnGround()) {
            LAST_GROUND_Y.put(player.getUuid(), player.getY());
        }
        double baseGroundY = player.isOnGround() ? player.getY() : LAST_GROUND_Y.getOrDefault(player.getUuid(), player.getY());
        int floorY = (int) Math.floor(baseGroundY);

        double forwardLead = updatePlayerForwardLead(player);
        double auraRadius = 2.1;
        double maxRange = 3.3 + forwardLead + 0.8;

        int minX = MathHelper.floor(playerPos.x - maxRange);
        int maxX = MathHelper.floor(playerPos.x + maxRange);
        int minY = allowDown ? MathHelper.floor(playerPos.y - 2.2) : floorY;
        int maxY = MathHelper.floor(playerPos.y + 4.8);
        int minZ = MathHelper.floor(playerPos.z - maxRange);
        int maxZ = MathHelper.floor(playerPos.z + maxRange);

        // Falling entities have not become world blocks yet, so the block scan cannot see them.
        for (FallingBlockEntity falling : world.getEntitiesByClass(FallingBlockEntity.class,
                player.getBoundingBox().expand(maxRange + 1), e -> e.isAlive())) {
            if (falling.getY() < baseGroundY || SpatialMath.distanceToPathAura(
                    falling.getPos(), chestPos, lookDirHoriz, forwardLead) > 3.9) continue;
            falling.setVelocity(SpatialMath.pathLooseBlockVelocity(falling.getPos(), chestPos, lookDirHoriz));
            falling.velocityModified = true;
        }

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutablePos.set(x, y, z);

                    // Preserve actual support surfaces, not the obstacle row at foot height.
                    if (!SpatialMath.canOpenPathBlock(y, baseGroundY, allowDown)) continue;
                    BlockState state = world.getBlockState(mutablePos);
                    if (!allowDown) {
                        var shape = state.getCollisionShape(world, mutablePos, net.minecraft.block.ShapeContext.of(player));
                        if (!shape.isEmpty() && y + shape.getMax(net.minecraft.util.math.Direction.Axis.Y) <= baseGroundY + 0.125) continue;
                        boolean supportsFeet = false;
                        for (var box : shape.getBoundingBoxes()) {
                            if (SpatialMath.supportsPathFeet(box.offset(mutablePos), player.getBoundingBox())) {
                                supportsFeet = true;
                                break;
                            }
                        }
                        if (supportsFeet) continue;
                    }

                    Vec3d blockCenter = Vec3d.ofCenter(mutablePos);

                    // Quét 2 tầng (Tầng trong <= 2.1m vòm cao, Tầng ngoài <= 3.3m sóng địa chấn)
                    double spineDist = SpatialMath.distanceToPathAura(blockCenter, chestPos, lookDirHoriz, forwardLead);
                    if (spineDist > 3.3) continue;

                    // Distance Hysteresis: keep open and reset restore tick if already displaced
                    if (SpatialDisplacementManager.isPositionDisplaced(world.getRegistryKey(), mutablePos)) {
                        SpatialDisplacementManager.refreshDisplacement(world.getRegistryKey(), mutablePos, 100);
                        // An incoming block must not overwrite the original block saved for restoration.
                        if (!state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity()
                                && state.getHardness(world, mutablePos) >= 0) {
                            FallingBlockEntity loose = FallingBlockEntity.spawnFromBlock(world, mutablePos.toImmutable(), state);
                            loose.setVelocity(SpatialMath.pathLooseBlockVelocity(blockCenter, chestPos, lookDirHoriz));
                            loose.velocityModified = true;
                        }
                        continue;
                    }

                    if (!SpatialDisplacementManager.canDisplace(world, mutablePos, state)) continue;

                    // Compute dynamic aura offset from SpatialMath
                    Vec3d auraDisp = SpatialMath.computeDynamicAuraOffset(mutablePos, playerPos, lookDirHoriz, auraRadius, 0);
                    Vector3f targetDisp = new Vector3f((float) auraDisp.x, (float) auraDisp.y, (float) auraDisp.z);

                    Quaternionf rotation = SpatialMath.computeArchwayRotation(mutablePos, playerPos, lookDirHoriz, 0);

                    // Thu nhỏ nhẹ kích thước mỗi block (0.82f) để tạo khe hở ma pháp giữa các viên gạch thay vì dính bệt vào nhau!
                    float blockScale = 0.78f + (float) (SpatialMath.hash01(mutablePos) * 0.14);
                    Vector3f scale = new Vector3f(blockScale, blockScale, blockScale);

                    // Xen kẽ màu viền Cyan (0x00F5FF) và Amethyst Purple (0xA855F7)
                    int glowColor = ((x + y + z) & 1) == 0 ? 0x00F5FF : 0xA855F7;

                    SpatialDisplacementManager.displaceBlock(
                            world,
                            mutablePos.toImmutable(),
                            targetDisp,
                            rotation,
                            scale,
                            100,
                            glowColor,
                            true,
                            DisplacedBlockRecord.DisplacementMode.PATH_WALKER,
                            player.getUuid(),
                            null,
                            0.0
                    );

                }
            }
        }

    }
}
