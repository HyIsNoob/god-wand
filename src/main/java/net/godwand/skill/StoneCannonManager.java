package net.godwand.skill;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.entity.StoneCannonProjectileEntity;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
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

    public static boolean insideDrillSweep(Vec3d point, Vec3d start, Vec3d end, double radius, double length) {
        Vec3d travel = end.subtract(start);
        double distance = travel.length();
        if (distance < 1.0e-6) return false;
        Vec3d axis = travel.multiply(1.0 / distance);
        Vec3d relative = point.subtract(start);
        double axial = relative.dotProduct(axis);
        if (axial < -length || axial > distance) return false;
        double coneRadius = radius * Math.min(1.0, (distance - axial) / length);
        return relative.lengthSquared() - axial * axial <= coneRadius * coneRadius;
    }

    private static Vec3d excavateImpact(StoneCannonProjectileEntity projectile, Vec3d start, Vec3d end) {
        ServerWorld world = (ServerWorld) projectile.getWorld();
        Vec3d travel = end.subtract(start);
        int steps = Math.max(1, (int) Math.ceil(travel.length() * 4));
        // Stop at protected terrain or unloaded chunks before excavating anything beyond them.
        for (int i = 0; i <= steps; i++) {
            Vec3d sample = start.lerp(end, (double) i / steps);
            BlockPos pos = BlockPos.ofFloored(sample);
            if (!world.isChunkLoaded(pos) || !world.isInBuildLimit(pos)
                    || world.getBlockState(pos).getHardness(world, pos) < 0) {
                end = start.lerp(end, Math.max(0, i - 1) / (double) steps);
                break;
            }
        }
        var barrier = world.raycast(new net.minecraft.world.RaycastContext(start, end,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, projectile) {
            @Override
            public net.minecraft.util.shape.VoxelShape getBlockShape(BlockState state, net.minecraft.world.BlockView view, BlockPos pos) {
                return state.getHardness(view, pos) < 0 ? net.minecraft.util.shape.VoxelShapes.fullCube()
                        : net.minecraft.util.shape.VoxelShapes.empty();
            }
        });
        if (barrier.getType() != HitResult.Type.MISS) end = start.lerp(barrier.getPos(), 0.9999);
        if (start.squaredDistanceTo(end) < 1.0e-6) return start;
        double radius = 0.65 + projectile.getChargeLevel() * 0.28;
        double length = radius * 3.2;
        Vec3d axis = travel.normalize();
        Vec3d tail = start.subtract(axis.multiply(length));
        Box bounds = new Box(tail, end).expand(radius + 0.9);
        int debris = 0;
        boolean removedDisplacement = false;
        for (BlockPos pos : BlockPos.iterate((int) Math.floor(bounds.minX), (int) Math.floor(bounds.minY),
                (int) Math.floor(bounds.minZ), (int) Math.ceil(bounds.maxX), (int) Math.ceil(bounds.maxY),
                (int) Math.ceil(bounds.maxZ))) {
            if (!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos)) continue;
            // Half the voxel diagonal includes edge-grazing blocks at the pointed nose.
            if (!insideDrillSweep(pos.toCenterPos(), start, end.add(axis.multiply(0.87)), radius + 0.87, length)) continue;
            BlockState state = world.getBlockState(pos);
            if (state.getHardness(world, pos) < 0) continue;
            var displaced = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
            if (displaced != null) {
                SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), pos);
                removedDisplacement = true;
            }
            if (state.isAir()) continue;
            world.breakBlock(pos, false, projectile.getOwner(), 0);
            if (debris++ < 6) {
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.06);
            }
        }
        if (removedDisplacement) net.godwand.spatial.DisplacementPersistentState.getServerState(world).markDirty();
        return end;
    }

    /**
     * Vận chiêu Stone Cannon (Mũi khoan đá hình thành trước ngực + Gió cuộn xoáy khí động học)
     */
    public static float chargeStoneVolume(int ticks) {
        return .28f + .14f * Math.clamp(ticks / 80f, 0, 1);
    }

    public static void tickCharge(ServerPlayerEntity player, int chargeTicks, int level) {
        ServerWorld sw = player.getServerWorld();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d focusPos = player.getEyePos().add(look.multiply(2.0)).add(0, -0.22, 0);

        if (chargeTicks == 0) {
            // Tiếng đá nén và va đập tạo khối cực mạnh, dứt khoát
            sw.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_HEAVY_CORE_PLACE, SoundCategory.PLAYERS, 1.1f, 0.75f);
            sw.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.PLAYERS, 1.0f, 0.65f);
            sw.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.55f, 1.6f);
        }
        // Các lớp đá va đập khớp nối thành hình mũi khoan cực nhanh và lực (chỉ trong 10 ticks đầu)
        if (chargeTicks == 3) {
            sw.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BASALT_PLACE, SoundCategory.PLAYERS, 0.95f, 1.25f);
            sw.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_DEEPSLATE_PLACE, SoundCategory.PLAYERS, 0.9f, 1.15f);
        } else if (chargeTicks == 6) {
            sw.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_STONE_PLACE, SoundCategory.PLAYERS, 0.95f, 1.35f);
            sw.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.PLAYERS, 0.45f, 1.7f);
        } else if (chargeTicks == 10) {
            sw.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_HEAVY_CORE_STEP, SoundCategory.PLAYERS, 1.0f, 1.3f);
            sw.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BASALT_BREAK, SoundCategory.PLAYERS, 0.85f, 1.4f);
        }
        if (chargeTicks >= 0 && chargeTicks % 12 == 0) {
            sw.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.PLAYERS,
                    chargeStoneVolume(chargeTicks), .65f + Math.min(chargeTicks, 80) * .008f);
            if (chargeTicks < 50) sw.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BASALT_HIT,
                    SoundCategory.PLAYERS, .28f, .8f + chargeTicks * .006f);
        }

        // 1. Quy tụ đất đá từ mặt đất xung quanh người chơi bay về mũi khoan
        if (chargeTicks % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                double groundR = 3.5 + sw.random.nextDouble() * 5.0;
                double groundAng = sw.random.nextDouble() * Math.PI * 2.0;
                double gx = player.getX() + Math.cos(groundAng) * groundR;
                double gz = player.getZ() + Math.sin(groundAng) * groundR;
                double gy = player.getY() + 0.1;
                BlockPos gPos = BlockPos.ofFloored(gx, gy, gz);
                BlockState gState = sw.getBlockState(gPos.down());
                BlockState blockToSparks = gState.isAir() ? Blocks.STONE.getDefaultState() : gState;

                Vec3d toFocus = focusPos.subtract(gx, gy, gz).normalize().multiply(0.45 + (level * 0.12));
                sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, blockToSparks),
                        gx, gy, gz, 2, toFocus.x, toFocus.y, toFocus.z, 0.15);
            }
        }

        // 2. Quy tụ hạt bụi đá ma sát nén và tia lửa điện cao tần (Hiệu ứng gió xoáy Ribbon được render mượt mà 3D ở Client)
        if (level >= 3 && chargeTicks % 2 == 0) {
            sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, focusPos.x, focusPos.y, focusPos.z,
                    3, 0.15, 0.15, 0.15, 0.05);
        }

        // 4. Âm thanh thăng cấp tầng lực
        if (chargeTicks == 25 || chargeTicks == 50 || chargeTicks == 80) {
            sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.65f, 0.85f + level * 0.15f);
        }

        // 5. Rung chấn Camera (Trauma Shake)
        if (chargeTicks % 4 == 0) {
            float trauma = (float) (0.04 + (level * 0.05));
            CinematicFxPayload shake = new CinematicFxPayload(
                    CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                    player.getX(), player.getY(), player.getZ(),
                    trauma, 4
            );
            ServerPlayNetworking.send(player, shake);
        }
    }

    /**
     * Bắn mũi khoan Stone Cannon với tốc độ siêu thanh và lực giật mạnh mẽ
     */
    public static void fire(ServerPlayerEntity player, int chargeDuration) {
        int level = calculateChargeLevel(chargeDuration);
        double speed = calculateSpeed(level);
        ServerWorld sw = player.getServerWorld();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d spawnPos = player.getEyePos().add(look.multiply(1.5));

        // Khởi tạo projectile
        StoneCannonProjectileEntity cannon = new StoneCannonProjectileEntity(player.getWorld(), player, level);
        cannon.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
        cannon.setVelocity(look.multiply(speed));
        player.getWorld().spawnEntity(cannon);

        // Lực giật lùi vật lý lên người chơi (Physical Recoil)
        double recoil = calculateRecoil(level);
        player.addVelocity(-look.x * recoil, -look.y * recoil * 0.4 + 0.12, -look.z * recoil);
        player.velocityModified = true;

        // Vụ nổ nòng súng Sonic Boom & Sweep Flash
        sw.spawnParticles(ParticleTypes.SONIC_BOOM, spawnPos.x, spawnPos.y, spawnPos.z, 1, 0, 0, 0, 0);
        sw.spawnParticles(ParticleTypes.FLASH, spawnPos.x, spawnPos.y, spawnPos.z, 2, 0.1, 0.1, 0.1, 0);
        sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, spawnPos.x, spawnPos.y, spawnPos.z, 3, 0.2, 0.2, 0.2, 0.05);

        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.15f, 1.4f);
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 0.85f, 0.8f);
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.65f, 1.4f);

        player.sendMessage(Text.literal("Fired."), true);
    }

    /**
     * Va chạm mục tiêu: Nổ động năng khủng khiếp, đào hố chấn động và phục hồi địa hình
     */
    public static void onProjectileImpact(StoneCannonProjectileEntity projectile, HitResult hit) {
        int level = projectile.getChargeLevel();
        Vec3d hitPos = hit.getPos();
        ServerWorld sw = (ServerWorld) projectile.getWorld();
        Vec3d direction = projectile.getVelocity().normalize();
        Vec3d surfaceNormal = hit instanceof net.minecraft.util.hit.BlockHitResult blockHit
                ? Vec3d.of(blockHit.getSide().getVector()) : direction.negate();
        var pressure = new net.godwand.network.CannonPressurePayload(hitPos, direction, level, surfaceNormal);
        for (var player : PlayerLookup.around(sw, hitPos, 96)) ServerPlayNetworking.send(player, pressure);
        excavateImpact(projectile, hitPos, hitPos.add(direction.multiply(3.0 + level * 1.5)));
        if (hit instanceof net.minecraft.util.hit.EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity victim
                && victim != projectile.getOwner() && projectile.markHit(victim)) {
            victim.damage(sw.getDamageSources().fallingBlock(projectile.getOwner()), calculateDirectDamage(level)
                    * (victim instanceof net.minecraft.entity.mob.MobEntity ? 2.0f : 1.0f));
        }
        double blastRadius = calculateCraterRadius(level) * 1.25;
        Box aoeBox = new Box(hitPos.x - blastRadius, hitPos.y - blastRadius, hitPos.z - blastRadius,
                hitPos.x + blastRadius, hitPos.y + blastRadius, hitPos.z + blastRadius);

        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, aoeBox, ent -> ent != projectile.getOwner() && ent.isAlive())) {
            double dist = e.getPos().distanceTo(hitPos);
            if (dist <= blastRadius && projectile.markHit(e)) {
                float splashDmg = (float) Math.max(30.0, (1.0 - (dist / blastRadius)) * (calculateDirectDamage(level) * 0.75) + 30.0);
                e.damage(sw.getDamageSources().fallingBlock(projectile.getOwner()), (e instanceof net.minecraft.entity.mob.MobEntity ? 2.0f : 1.0f) * (splashDmg));
                Vec3d blastDir = e.getPos().subtract(hitPos);
                Vec3d push = (blastDir.length() > 0.01) ? blastDir.normalize().multiply(1.8).add(0, 0.65, 0) : new Vec3d(0, 1.2, 0);
                e.setVelocity(push);
                e.velocityModified = true;
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
        for (ServerPlayerEntity p : PlayerLookup.around(sw, hitPos, 96.0)) {
            ServerPlayNetworking.send(p, impactPayload);
            ServerPlayNetworking.send(p, traumaShake);
            ServerPlayNetworking.send(p, fovPunch);
        }

        // 5. Hiệu ứng khói nổ, sonic boom và dàn âm thanh uy lực
        sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, hitPos.x, hitPos.y, hitPos.z, 4, 1.0, 1.0, 1.0, 0.0);
        sw.spawnParticles(ParticleTypes.SONIC_BOOM, hitPos.x, hitPos.y, hitPos.z, 2, 0.2, 0.2, 0.2, 0.0);
        sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, hitPos.x, hitPos.y, hitPos.z, 8, 1.2, 0.8, 1.2, 0.1);

        sw.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.4f, 0.6f);
        sw.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.7f, 1.2f);
        sw.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 0.85f, 0.8f);
    }
}
