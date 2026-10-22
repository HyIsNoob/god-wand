package net.godwand.skill;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.entity.StoneCannonProjectileEntity;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.spatial.DisplacedBlockRecord;
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
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
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

    /**
     * Vận chiêu Stone Cannon (Mũi khoan đá hình thành trước ngực + Gió cuộn xoáy khí động học)
     */
    public static void tickCharge(ServerPlayerEntity player, int chargeTicks, int level) {
        ServerWorld sw = player.getServerWorld();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d focusPos = player.getEyePos().add(look.multiply(2.0)).add(0, -0.22, 0);

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
        Vec3d up = Math.abs(look.y) < 0.99 ? new Vec3d(0, 1, 0) : new Vec3d(0, 0, 1);
        Vec3d tangent = look.crossProduct(up).normalize();
        Vec3d binormal = look.crossProduct(tangent).normalize();

        // 3. Vòng quay ma sát cao độ tại lõi mũi khoan
        int corePoints = 8;
        double coreR = 0.28 + level * 0.10;
        double coreSpin = chargeTicks * (0.45 + level * 0.35);
        for (int p = 0; p < corePoints; p++) {
            double ang = coreSpin + (p * 2.0 * Math.PI / corePoints);
            Vec3d cp = focusPos.add(tangent.multiply(coreR * Math.cos(ang)))
                    .add(binormal.multiply(coreR * Math.sin(ang)));
            sw.spawnParticles(new DustParticleEffect(new Vector3f(0.85f, 0.78f, 0.58f), 1.25f),
                    cp.x, cp.y, cp.z, 1, 0, 0, 0, 0);
        }

        if (level >= 3 && chargeTicks % 2 == 0) {
            sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, focusPos.x, focusPos.y, focusPos.z,
                    3, 0.15, 0.15, 0.15, 0.05);
        }

        // 4. Âm thanh thăng cấp tầng lực
        if (chargeTicks == 25 || chargeTicks == 50 || chargeTicks == 80) {
            float pitch = (level == 4) ? 2.0f : (level == 3) ? 1.5f : 1.1f;
            sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 2.0f, pitch);
            sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 2.0f, pitch);
            sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, 2.0f, pitch);
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
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 3.2f, 1.6f);
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 3.0f, 0.8f);
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 2.5f, 1.4f);

        player.sendMessage(Text.literal("§6§l✦ Stone Cannon Fired! [Level " + level + "] ✦"), true);
    }

    /**
     * Va chạm mục tiêu: Nổ động năng khủng khiếp, đào hố chấn động và phục hồi địa hình
     */
    public static void onProjectileImpact(StoneCannonProjectileEntity projectile, HitResult hit) {
        int level = projectile.getChargeLevel();
        Vec3d hitPos = hit.getPos();
        ServerWorld sw = (ServerWorld) projectile.getWorld();

        // 1. Sát thương xuyên thấu trực tiếp vào mục tiêu bị đâm trúng
        if (hit instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity victim) {
            float directDmg = calculateDirectDamage(level);
            victim.damage(sw.getDamageSources().fallingBlock(projectile.getOwner()), directDmg);
        }

        // 2. Sát thương chấn động diện rộng (AOE Blast & Shockwave)
        double blastRadius = calculateCraterRadius(level) * 1.25;
        Box aoeBox = new Box(hitPos.x - blastRadius, hitPos.y - blastRadius, hitPos.z - blastRadius,
                hitPos.x + blastRadius, hitPos.y + blastRadius, hitPos.z + blastRadius);

        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, aoeBox, ent -> ent != projectile.getOwner() && ent.isAlive())) {
            double dist = e.getPos().distanceTo(hitPos);
            if (dist <= blastRadius) {
                float splashDmg = (float) Math.max(30.0, (1.0 - (dist / blastRadius)) * (calculateDirectDamage(level) * 0.75) + 30.0);
                e.damage(sw.getDamageSources().fallingBlock(projectile.getOwner()), splashDmg);
                Vec3d blastDir = e.getPos().subtract(hitPos);
                Vec3d push = (blastDir.length() > 0.01) ? blastDir.normalize().multiply(1.8).add(0, 0.65, 0) : new Vec3d(0, 1.2, 0);
                e.setVelocity(push);
                e.velocityModified = true;
            }
        }

        // 3. Kích phá tường / mặt đất (Spatial Crater Excavation with Clean Auto-Restoration)
        double craterR = calculateCraterRadius(level);
        BlockPos centerPos = BlockPos.ofFloored(hitPos);
        int intR = (int) Math.ceil(craterR);
        double rSq = craterR * craterR;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int displacedCount = 0;

        for (int x = -intR; x <= intR && displacedCount < 120; x++) {
            for (int y = -intR; y <= intR && displacedCount < 120; y++) {
                for (int z = -intR; z <= intR && displacedCount < 120; z++) {
                    double dSq = x * x + y * y + z * z;
                    if (dSq > rSq) continue;

                    mutable.set(centerPos.getX() + x, centerPos.getY() + y, centerPos.getZ() + z);
                    BlockState state = sw.getBlockState(mutable);
                    if (!SpatialDisplacementManager.canDisplace(sw, mutable, state)) continue;

                    Vec3d radDir = mutable.toCenterPos().subtract(hitPos);
                    double rDist = radDir.length();
                    Vec3d outwardDisp = (rDist > 0.1)
                            ? radDir.normalize().multiply(2.2 + sw.random.nextDouble() * 2.8).add(0, 1.2 + sw.random.nextDouble() * 1.5, 0)
                            : new Vec3d(0, 2.5, 0);

                    Quaternionf rot = new Quaternionf()
                            .rotateY((float) (sw.random.nextDouble() * Math.PI * 2.0))
                            .rotateX((float) (sw.random.nextDouble() * Math.PI));

                    SpatialDisplacementManager.displaceBlock(
                            sw,
                            mutable,
                            new Vector3f((float) outwardDisp.x, (float) outwardDisp.y, (float) outwardDisp.z),
                            rot,
                            new Vector3f(1.0f, 1.0f, 1.0f),
                            160,
                            0,
                            displacedCount < 60,
                            DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
                            null,
                            hitPos,
                            craterR
                    );
                    displacedCount++;
                }
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
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 4.0f, 0.6f);
        sw.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 3.5f, 1.2f);
        sw.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 3.5f, 0.8f);
        sw.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.BLOCK_HEAVY_CORE_PLACE, SoundCategory.BLOCKS, 3.5f, 0.9f);
        sw.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 3.0f, 1.3f);
    }
}
