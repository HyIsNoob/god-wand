package net.godwand.skill;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.spatial.DisplacedBlockRecord;
import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
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
    private static final Set<UUID> ACTIVE_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final java.util.Map<UUID, Double> LAST_GROUND_Y = new ConcurrentHashMap<>();
    private static final java.util.Map<UUID, Vec3d> LAST_PLAYER_POS = new ConcurrentHashMap<>();
    private static final java.util.Map<UUID, Double> CURRENT_FORWARD_LEAD = new ConcurrentHashMap<>();
    private static final java.util.Map<UUID, Double> CURRENT_PLAYER_SPEED = new ConcurrentHashMap<>();

    public static boolean isActive(PlayerEntity player) {
        return ACTIVE_PLAYERS.contains(player.getUuid());
    }

    public static void toggle(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        if (ACTIVE_PLAYERS.contains(player.getUuid())) {
            ACTIVE_PLAYERS.remove(player.getUuid());
            LAST_GROUND_Y.remove(player.getUuid());
            LAST_PLAYER_POS.remove(player.getUuid());
            CURRENT_FORWARD_LEAD.remove(player.getUuid());
            CURRENT_PLAYER_SPEED.remove(player.getUuid());
            player.sendMessage(Text.literal("§c§l[✦ SPATIAL PATH: COLLAPSED ✦]"), true);

            // Explosive Deactivation Impact
            world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getEyeY(), player.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
            world.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 40, 1.2, 0.8, 1.2, 0.25);
            world.spawnParticles(new DustParticleEffect(new Vector3f(0.25f, 0.88f, 1.0f), 1.2f),
                    player.getX(), player.getY() + 1.0, player.getZ(), 30, 1.5, 0.8, 1.5, 0.2);

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 2.5f, 0.75f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 2.0f, 1.5f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 2.0f, 1.2f);

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
            player.sendMessage(Text.literal("§d§l[✦ SPATIAL PATH: §aACTIVE §d§l✦]"), true);

            // Audio kích hoạt uy lực
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 2.2f, 1.35f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.PLAYERS, 2.0f, 1.4f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 2.5f, 1.2f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.2f, 1.8f);

            // Rung chấn nhẹ tức thời (Subtle Camera Pop Shake)
            CinematicFxPayload actShake = new CinematicFxPayload(
                    CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                    player.getX(), player.getY(), player.getZ(),
                    0.32f, 8
            );
            ServerPlayNetworking.send(player, actShake);

            // Sóng năng lượng dưới chân bung tỏa ra từ người chơi (Concentric Ground Shockwave)
            double feetY = player.getY() + 0.08;
            world.spawnParticles(ParticleTypes.FLASH, player.getX(), feetY + 0.1, player.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
            world.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX(), feetY + 0.15, player.getZ(), 1, 0, 0, 0, 0);

            for (int step = 1; step <= 3; step++) {
                double r = step * 1.25;
                int points = 16 * step;
                for (int i = 0; i < points; i++) {
                    double ang = (Math.PI * 2.0 * i) / points;
                    double px = player.getX() + Math.cos(ang) * r;
                    double pz = player.getZ() + Math.sin(ang) * r;
                    world.spawnParticles(new DustParticleEffect(new Vector3f(0.0f, 0.95f, 1.0f), 1.2f),
                            px, feetY, pz, 1, Math.cos(ang) * 0.16, 0.02, Math.sin(ang) * 0.16, 0.08);
                    if (i % 2 == 0) {
                        world.spawnParticles(new DustParticleEffect(new Vector3f(0.70f, 0.25f, 1.0f), 1.2f),
                                px, feetY, pz, 1, Math.cos(ang) * 0.14, 0.03, Math.sin(ang) * 0.14, 0.06);
                        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, px, feetY + 0.05, pz, 1, Math.cos(ang) * 0.08, 0.02, Math.sin(ang) * 0.08, 0.04);
                    }
                }
            }

            world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.8, player.getZ(), 25, 0.6, 0.6, 0.6, 0.15);
        }
    }

    public static void deactivate(PlayerEntity player) {
        ACTIVE_PLAYERS.remove(player.getUuid());
        LAST_GROUND_Y.remove(player.getUuid());
        LAST_PLAYER_POS.remove(player.getUuid());
        CURRENT_FORWARD_LEAD.remove(player.getUuid());
        CURRENT_PLAYER_SPEED.remove(player.getUuid());
        SpatialDisplacementManager.collapsePlayerAura(player.getUuid());
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
        int maxY = MathHelper.floor(playerPos.y + 2.8);
        int minZ = MathHelper.floor(playerPos.z - maxRange);
        int maxZ = MathHelper.floor(playerPos.z + maxRange);

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutablePos.set(x, y, z);

                    // Absolute Ground Safety Rule
                    if (!allowDown && y < floorY) continue;

                    // Footing Protection
                    if (!allowDown && Math.abs(x + 0.5 - playerPos.x) <= 0.85 && Math.abs(z + 0.5 - playerPos.z) <= 0.85 && y <= floorY) {
                        continue;
                    }

                    Vec3d blockCenter = Vec3d.ofCenter(mutablePos);

                    // Quét 2 tầng (Tầng trong <= 2.1m vòm cao, Tầng ngoài <= 3.3m sóng địa chấn)
                    double spineDist = SpatialMath.distanceToAuraSpine(blockCenter, chestPos, lookDirHoriz, forwardLead);
                    if (spineDist > 3.3) continue;

                    // Distance Hysteresis: keep open and reset restore tick if already displaced
                    if (SpatialDisplacementManager.isPositionDisplaced(world.getRegistryKey(), mutablePos)) {
                        SpatialDisplacementManager.refreshDisplacement(world.getRegistryKey(), mutablePos, 100);
                        continue;
                    }

                    BlockState state = world.getBlockState(mutablePos);
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

                    // Ambient shimmer particle
                    if (((x ^ z) & 7) == 0) {
                        world.spawnParticles(ParticleTypes.END_ROD, blockCenter.x, blockCenter.y, blockCenter.z, 1, 0.1, 0.1, 0.1, 0.01);
                    }
                }
            }
        }

        // Vòng ma pháp & sóng năng lượng huyền ảo trong bán kính ảnh hưởng mỗi 2 ticks
        if (player.age % 2 == 0) {
            double baseFeetY = floorY + 0.05;

            // 1. Vòng tròn ma pháp chân người chơi (Vòng trong R=1.3m xoay thuận, Vòng ngoài R=2.5m xoay ngược)
            double t = player.age * 0.18;
            for (int i = 0; i < 6; i++) {
                double aIn = t + (i * Math.PI / 3.0);
                double rxIn = Math.cos(aIn) * 1.35;
                double rzIn = Math.sin(aIn) * 1.35;
                world.spawnParticles(new DustParticleEffect(new Vector3f(0.0f, 0.95f, 1.0f), 0.9f),
                        playerPos.x + rxIn, baseFeetY, playerPos.z + rzIn, 1, 0, 0.01, 0, 0);

                double aOut = -t * 0.85 + (i * Math.PI / 3.0);
                double rxOut = Math.cos(aOut) * 2.5;
                double rzOut = Math.sin(aOut) * 2.5;
                world.spawnParticles(new DustParticleEffect(new Vector3f(0.72f, 0.28f, 1.0f), 0.95f),
                        playerPos.x + rxOut, baseFeetY, playerPos.z + rzOut, 1, 0, 0.015, 0, 0);
            }

            // 2. Ký tự ma pháp cổ ngữ (Enchant & End Rod) bay lơ lửng trong bán kính ảnh hưởng
            world.spawnParticles(ParticleTypes.ENCHANT,
                    playerPos.x + (Math.random() - 0.5) * 3.5, baseFeetY + 0.15, playerPos.z + (Math.random() - 0.5) * 3.5,
                    4, 0.1, 0.2, 0.1, 0.08);

            // 3. Tia dẫn hướng năng lượng gợn sóng về phía trước trên mặt đất
            for (double d = 1.6; d <= forwardLead + 2.4; d += 1.4) {
                double fx = playerPos.x + lookDirHoriz.x * d;
                double fz = playerPos.z + lookDirHoriz.z * d;
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, fx, baseFeetY + 0.04, fz, 1, 0, 0.01, 0, 0.02);
            }
        }
    }
}
