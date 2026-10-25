package net.godwand.skill;

import net.minecraft.util.Mth;

import net.godwand.platform.Services;
import net.godwand.entity.StoneCannonProjectileEntity;
import net.godwand.network.CannonPressurePayload;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.spatial.DisplacedBlockRecord;
import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class StoneCannonManager {

    public static int calculateChargeLevel(int chargeTicks) {
        if (chargeTicks >= 80) return 4;
        if (chargeTicks >= 50) return 3;
        if (chargeTicks >= 25) return 2;
        return 1;
    }

    public static float calculateDirectDamage(int level) {
        return switch (level) {
            case 1 -> 65.0f;
            case 2 -> 120.0f;
            case 3 -> 185.0f;
            default -> 260.0f;
        };
    }

    public static double calculateCraterRadius(int level) {
        return switch (level) {
            case 1 -> 3.0;
            case 2 -> 4.5;
            case 3 -> 6.0;
            default -> 8.0;
        };
    }

    public static double calculateSpeed(int level) {
        return switch (level) {
            case 1 -> 2.8;
            case 2 -> 3.6;
            case 3 -> 4.5;
            default -> 5.4;
        };
    }

    public static double calculateRecoil(int level) {
        return switch (level) {
            case 1 -> 0.40;
            case 2 -> 0.75;
            case 3 -> 1.15;
            default -> 1.65;
        };
    }

    public static boolean insideDrillSweep(Vec3 point, Vec3 start, Vec3 end, double radius, double length) {
        Vec3 travel = end.subtract(start);
        double distance = travel.length();
        if (distance < 1.0e-6) return false;
        Vec3 axis = travel.scale(1.0 / distance);
        Vec3 relative = point.subtract(start);
        double axial = relative.dot(axis);
        if (axial < -length || axial > distance) return false;
        double coneRadius = radius * Math.min(1.0, (distance - axial) / length);
        return relative.lengthSqr() - axial * axial <= coneRadius * coneRadius;
    }

    private static Vec3 excavateImpact(StoneCannonProjectileEntity projectile, Vec3 start, Vec3 end) {
        ServerLevel world = (ServerLevel) projectile.level();
        Vec3 travel = end.subtract(start);
        int steps = Math.max(1, (int) Math.ceil(travel.length() * 4));
        // Stop at protected terrain or unloaded chunks before excavating anything beyond them.
        for (int i = 0; i <= steps; i++) {
            Vec3 sample = start.lerp(end, (double) i / steps);
            BlockPos pos = BlockPos.containing(sample);
            if (!world.hasChunkAt(pos) || !world.isInWorldBounds(pos)
                    || world.getBlockState(pos).getDestroySpeed(world, pos) < 0) {
                end = start.lerp(end, Math.max(0, i - 1) / (double) steps);
                break;
            }
        }
        var barrier = world.clip(new net.minecraft.world.level.ClipContext(start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, projectile) {
            @Override
            public net.minecraft.world.phys.shapes.VoxelShape getBlockShape(BlockState state, net.minecraft.world.level.BlockGetter view, BlockPos pos) {
                return state.getDestroySpeed(view, pos) < 0 ? net.minecraft.world.phys.shapes.Shapes.block()
                        : net.minecraft.world.phys.shapes.Shapes.empty();
            }
        });
        if (barrier.getType() != HitResult.Type.MISS) end = start.lerp(barrier.getLocation(), 0.9999);
        if (start.distanceToSqr(end) < 1.0e-6) return start;
        double radius = 0.65 + projectile.getChargeLevel() * 0.28;
        double length = radius * 3.2;
        Vec3 axis = travel.normalize();
        Vec3 tail = start.subtract(axis.scale(length));
        AABB bounds = new AABB(tail, end).inflate(radius + 0.9);
        int debris = 0;
        boolean removedDisplacement = false;
        for (BlockPos pos : BlockPos.betweenClosed((int) Math.floor(bounds.minX), (int) Math.floor(bounds.minY),
                (int) Math.floor(bounds.minZ), (int) Math.ceil(bounds.maxX), (int) Math.ceil(bounds.maxY),
                (int) Math.ceil(bounds.maxZ))) {
            if (!world.isInWorldBounds(pos) || !world.hasChunkAt(pos)) continue;
            // Half the voxel diagonal includes edge-grazing blocks at the pointed nose.
            if (!insideDrillSweep(pos.getCenter(), start, end.add(axis.scale(0.87)), radius + 0.87, length)) continue;
            BlockState state = world.getBlockState(pos);
            if (state.getDestroySpeed(world, pos) < 0) continue;
            var displaced = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
            if (displaced != null) {
                SpatialDisplacementManager.removeActiveRecord(world.dimension(), pos);
                removedDisplacement = true;
            }
            if (state.isAir()) continue;
            world.destroyBlock(pos, false, projectile.getOwner(), 0);
            if (debris++ < 6) {
                world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.06);
            }
        }
        if (removedDisplacement) net.godwand.spatial.DisplacementPersistentState.getServerState(world).setDirty();
        return end;
    }

    /**
     * Vận chiêu Stone Cannon (Mũi khoan đá hình thành trước ngực + Gió cuộn xoáy khí động học)
     */
    public static float chargeStoneVolume(int ticks) {
        return .28f + .14f * Mth.clamp(ticks / 80f, 0, 1);
    }

    public static void tickCharge(ServerPlayer player, int chargeTicks, int level) {
        ServerLevel sw = player.serverLevel();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 focusPos = player.getEyePosition().add(look.scale(2.0)).add(0, -0.22, 0);

        if (chargeTicks == 0) {
            // Tiếng đá nén và va đập tạo khối cực mạnh, dứt khoát
            sw.playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 1.1f, 0.75f);
            sw.playSound(null, player.blockPosition(), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.0f, 0.65f);
            sw.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.55f, 1.6f);
        }
        // Các lớp đá va đập khớp nối thành hình mũi khoan cực nhanh và lực (chỉ trong 10 ticks đầu)
        if (chargeTicks == 3) {
            sw.playSound(null, player.blockPosition(), SoundEvents.BASALT_PLACE, SoundSource.PLAYERS, 0.95f, 1.25f);
            sw.playSound(null, player.blockPosition(), SoundEvents.DEEPSLATE_PLACE, SoundSource.PLAYERS, 0.9f, 1.15f);
        } else if (chargeTicks == 6) {
            sw.playSound(null, player.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 0.95f, 1.35f);
            sw.playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.45f, 1.7f);
        } else if (chargeTicks == 10) {
            sw.playSound(null, player.blockPosition(), SoundEvents.STONE_STEP, SoundSource.PLAYERS, 1.0f, 1.3f);
            sw.playSound(null, player.blockPosition(), SoundEvents.BASALT_BREAK, SoundSource.PLAYERS, 0.85f, 1.4f);
        }
        if (chargeTicks >= 0 && chargeTicks % 12 == 0) {
            sw.playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS,
                    chargeStoneVolume(chargeTicks), .65f + Math.min(chargeTicks, 80) * .008f);
            if (chargeTicks < 50) sw.playSound(null, player.blockPosition(), SoundEvents.BASALT_HIT,
                    SoundSource.PLAYERS, .28f, .8f + chargeTicks * .006f);
        }

        // 1. Quy tụ đất đá từ mặt đất xung quanh người chơi bay về mũi khoan
        if (chargeTicks % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                double groundR = 3.5 + sw.random.nextDouble() * 5.0;
                double groundAng = sw.random.nextDouble() * Math.PI * 2.0;
                double gx = player.getX() + Math.cos(groundAng) * groundR;
                double gz = player.getZ() + Math.sin(groundAng) * groundR;
                double gy = player.getY() + 0.1;
                BlockPos gPos = BlockPos.containing(gx, gy, gz);
                BlockState gState = sw.getBlockState(gPos.below());
                BlockState blockToSparks = gState.isAir() ? Blocks.STONE.defaultBlockState() : gState;

                Vec3 toFocus = focusPos.subtract(gx, gy, gz).normalize().scale(0.45 + (level * 0.12));
                sw.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockToSparks),
                        gx, gy, gz, 2, toFocus.x, toFocus.y, toFocus.z, 0.15);
            }
        }

        // 2. Quy tụ hạt bụi đá ma sát nén và tia lửa điện cao tần (Hiệu ứng gió xoáy Ribbon được render mượt mà 3D ở Client)
        if (level >= 3 && chargeTicks % 2 == 0) {
            sw.sendParticles(ParticleTypes.ELECTRIC_SPARK, focusPos.x, focusPos.y, focusPos.z,
                    3, 0.15, 0.15, 0.15, 0.05);
        }

        // 4. Âm thanh thăng cấp tầng lực
        if (chargeTicks == 25 || chargeTicks == 50 || chargeTicks == 80) {
            sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.65f, 0.85f + level * 0.15f);
        }

        // 5. Rung chấn Camera (Trauma Shake)
        if (chargeTicks % 4 == 0) {
            float trauma = (float) (0.04 + (level * 0.05));
            CinematicFxPayload shake = new CinematicFxPayload(
                    CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                    player.getX(), player.getY(), player.getZ(),
                    trauma, 4
            );
            Services.NETWORK.sendToPlayer(player, shake);
        }
    }

    /**
     * Bắn mũi khoan Stone Cannon với tốc độ siêu thanh và lực giật mạnh mẽ
     */
    public static void fire(ServerPlayer player, int chargeDuration) {
        int level = calculateChargeLevel(chargeDuration);
        double speed = calculateSpeed(level);
        ServerLevel sw = player.serverLevel();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 spawnPos = player.getEyePosition().add(look.scale(1.5));

        // Khởi tạo projectile
        StoneCannonProjectileEntity cannon = new StoneCannonProjectileEntity(player.level(), player, level);
        cannon.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        cannon.setDeltaMovement(look.scale(speed));
        player.level().addFreshEntity(cannon);

        // Lực giật lùi vật lý lên người chơi (Physical Recoil)
        double recoil = calculateRecoil(level);
        player.push(-look.x * recoil, -look.y * recoil * 0.4 + 0.12, -look.z * recoil);
        player.hurtMarked = true;

        // Vụ nổ nòng súng Sonic Boom & Sweep Flash
        sw.sendParticles(ParticleTypes.SONIC_BOOM, spawnPos.x, spawnPos.y, spawnPos.z, 1, 0, 0, 0, 0);
        sw.sendParticles(ParticleTypes.FLASH, spawnPos.x, spawnPos.y, spawnPos.z, 2, 0.1, 0.1, 0.1, 0);
        sw.sendParticles(ParticleTypes.SWEEP_ATTACK, spawnPos.x, spawnPos.y, spawnPos.z, 3, 0.2, 0.2, 0.2, 0.05);

        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.15f, 1.4f);
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.85f, 0.8f);
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.65f, 1.4f);

        player.displayClientMessage(Component.literal("Fired."), true);
    }

    /**
     * Va chạm mục tiêu: Nổ động năng khủng khiếp, đào hố chấn động và phục hồi địa hình
     */
    public static void onProjectileImpact(StoneCannonProjectileEntity projectile, HitResult hit) {
        int level = projectile.getChargeLevel();
        Vec3 hitPos = hit.getLocation();
        ServerLevel sw = (ServerLevel) projectile.level();
        Vec3 direction = projectile.getDeltaMovement().normalize();
        Vec3 surfaceNormal = hit instanceof net.minecraft.world.phys.BlockHitResult blockHit
                ? Vec3.atLowerCornerOf(blockHit.getDirection().getNormal()) : direction.reverse();
        var pressure = new net.godwand.network.CannonPressurePayload(hitPos, direction, level, surfaceNormal);
        Services.NETWORK.sendToNearby(sw, hitPos, 96.0, pressure);
        excavateImpact(projectile, hitPos, hitPos.add(direction.scale(3.0 + level * 1.5)));
        if (hit instanceof net.minecraft.world.phys.EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity victim
                && victim != projectile.getOwner() && projectile.markHit(victim)) {
            victim.hurt(sw.damageSources().fallingBlock(projectile.getOwner()), calculateDirectDamage(level)
                    * (victim instanceof net.minecraft.world.entity.Mob ? 2.0f : 1.0f));
        }
        double blastRadius = calculateCraterRadius(level) * 1.25;
        AABB aoeBox = new AABB(hitPos.x - blastRadius, hitPos.y - blastRadius, hitPos.z - blastRadius,
                hitPos.x + blastRadius, hitPos.y + blastRadius, hitPos.z + blastRadius);

        for (LivingEntity e : sw.getEntitiesOfClass(LivingEntity.class, aoeBox, ent -> ent != projectile.getOwner() && ent.isAlive())) {
            double dist = e.position().distanceTo(hitPos);
            if (dist <= blastRadius && projectile.markHit(e)) {
                float splashDmg = (float) Math.max(30.0, (1.0 - (dist / blastRadius)) * (calculateDirectDamage(level) * 0.75) + 30.0);
                e.hurt(sw.damageSources().fallingBlock(projectile.getOwner()), (e instanceof net.minecraft.world.entity.Mob ? 2.0f : 1.0f) * (splashDmg));
                Vec3 blastDir = e.position().subtract(hitPos);
                Vec3 push = (blastDir.length() > 0.01) ? blastDir.normalize().scale(1.8).add(0, 0.65, 0) : new Vec3(0, 1.2, 0);
                e.setDeltaMovement(push);
                e.hurtMarked = true;
            }
        }

        // 4. Payloads rung chấn Camera & FOV Punch
        ImpactFxPayload impactPayload = new ImpactFxPayload(
                hitPos.x, hitPos.y, hitPos.z,
                ImpactFxPayload.TYPE_SEISMIC_SLAM, 12, (float) Math.max(1.0, level * 0.8)
        );
        CinematicFxPayload traumaShake = new CinematicFxPayload(
                CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                hitPos.x, hitPos.y, hitPos.z,
                (float) Math.min(1.0f, 0.65f + level * 0.12f), 16
        );
        CinematicFxPayload fovPunch = new CinematicFxPayload(
                CinematicFxPayload.EVENT_FOV_PUNCH,
                hitPos.x, hitPos.y, hitPos.z,
                0.88f, 6
        );
        Services.NETWORK.sendToNearby(sw, hitPos, 96.0, impactPayload);
        Services.NETWORK.sendToNearby(sw, hitPos, 96.0, traumaShake);
        Services.NETWORK.sendToNearby(sw, hitPos, 96.0, fovPunch);

        // 5. Hiệu ứng khói nổ, sonic boom và dàn âm thanh uy lực
        sw.sendParticles(ParticleTypes.EXPLOSION_EMITTER, hitPos.x, hitPos.y, hitPos.z, 4, 1.0, 1.0, 1.0, 0.0);
        sw.sendParticles(ParticleTypes.SONIC_BOOM, hitPos.x, hitPos.y, hitPos.z, 2, 0.2, 0.2, 0.2, 0.0);
        sw.sendParticles(ParticleTypes.SWEEP_ATTACK, hitPos.x, hitPos.y, hitPos.z, 8, 1.2, 0.8, 1.2, 0.1);

        sw.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.4f, 0.6f);
        sw.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7f, 1.2f);
        sw.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.85f, 0.8f);
    }
}
