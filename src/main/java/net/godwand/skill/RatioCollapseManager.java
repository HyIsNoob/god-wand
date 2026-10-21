package net.godwand.skill;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.network.ImpactFxPayload;
import net.godwand.spatial.DimensionPos;
import net.godwand.spatial.DisplacedBlockRecord;
import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RatioCollapseManager {
    public static class RatioCollapseTask {
        public final ServerWorld world;
        public final ServerPlayerEntity caster;
        public final BlockPos center;
        public final double radius;
        public int ageTicks = 0;
        public final int maxLifetimeTicks = 65;
        public float swirlAngle = 0.0f;
        public boolean slammed = false;

        public final List<BlockPos> displacedPositions = new ArrayList<>();
        public final Map<BlockPos, Float> originalRadius = new HashMap<>();
        public final Map<BlockPos, Float> originalAngle = new HashMap<>();
        public final Map<BlockPos, Float> apexHeight = new HashMap<>();
        public final Map<BlockPos, Float> rotationSpeed = new HashMap<>();
        public final Map<BlockPos, Quaternionf> baseRotation = new HashMap<>();
        public final Map<BlockPos, Vector3f> blockScale = new HashMap<>();

        public RatioCollapseTask(ServerWorld world, ServerPlayerEntity caster, BlockPos center, double radius) {
            this.world = world;
            this.caster = caster;
            this.center = center.toImmutable();
            this.radius = radius;
        }
    }

    private static final Queue<RatioCollapseTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();

    public static void cast(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        // Raycast forward from eye position to find ground target
        HitResult hit = player.raycast(28.0, 0.0f, false);
        BlockPos targetPos;
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bHit) {
            targetPos = bHit.getBlockPos();
        } else {
            Vec3d targetVec = player.getEyePos().add(player.getRotationVec(1.0f).multiply(16.0));
            targetPos = BlockPos.ofFloored(targetVec);
            // Snap down to ground
            while (targetPos.getY() > world.getBottomY() && world.getBlockState(targetPos).isAir()) {
                targetPos = targetPos.down();
            }
        }

        launch(world, player, targetPos, 6.5);
    }

    public static void launch(ServerWorld world, ServerPlayerEntity player, BlockPos center, double radius) {
        RatioCollapseTask task = new RatioCollapseTask(world, player, center, radius);

        // Sonic Fracture Activation Audio
        world.playSound(null, center, SoundEvents.BLOCK_HEAVY_CORE_BREAK, SoundCategory.BLOCKS, 3.0f, 0.75f);
        world.playSound(null, center, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.BLOCKS, 2.5f, 1.35f);
        world.playSound(null, center, SoundEvents.ITEM_MACE_SMASH_GROUND, SoundCategory.BLOCKS, 3.0f, 0.8f);

        // Ground shockwave rupture VFX
        world.spawnParticles(ParticleTypes.SONIC_BOOM, center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.FLASH, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.0);
        world.spawnParticles(new DustParticleEffect(new Vector3f(0.15f, 0.90f, 1.0f), 1.5f),
                center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 30, 1.2, 0.2, 1.2, 0.25);

        // Kinetic uprooting: select surface blocks in a bowl within radius
        int intR = (int) Math.ceil(radius);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int displacedCount = 0;
        int maxBlocks = 42;

        for (int x = -intR; x <= intR && displacedCount < maxBlocks; x++) {
            for (int z = -intR; z <= intR && displacedCount < maxBlocks; z++) {
                double dSq = x * x + z * z;
                if (dSq > radius * radius) continue;

                // Find top solid ground block at (center.x + x, center.z + z)
                int startY = center.getY() + 2;
                mutable.set(center.getX() + x, startY, center.getZ() + z);
                while (mutable.getY() > center.getY() - 4 && world.getBlockState(mutable).isAir()) {
                    mutable.move(0, -1, 0);
                }

                BlockState state = world.getBlockState(mutable);
                if (!SpatialDisplacementManager.canDisplace(world, mutable, state)) continue;

                BlockPos targetPos = mutable.toImmutable();
                double dist = Math.sqrt(dSq);
                float angle = (float) Math.atan2(z, x);

                // Calculate vertical lift apex (6.5 to 9.5 blocks above ground)
                float hash = (float) SpatialMath.hashCoordinateToPhase(targetPos.asLong());
                float apexY = 6.5f + 2.5f * (float) Math.abs(Math.sin(hash));

                // Initial lift displacement vector with physical inertia (accelerates upwards)
                Vector3f initialLift = new Vector3f(
                        (float) (-x * 0.15),
                        apexY,
                        (float) (-z * 0.15)
                );

                // Random 3D angular tumble
                Quaternionf rot = new Quaternionf()
                        .rotateX((float) Math.toRadians(25.0 * Math.sin(hash * 2.1)))
                        .rotateY((float) Math.toRadians(45.0 * Math.cos(hash * 1.7)))
                        .rotateZ((float) Math.toRadians(30.0 * Math.sin(hash * 3.3)));

                float scaleVal = 1.05f + 0.2f * (float) Math.abs(Math.cos(hash));
                Vector3f scale = new Vector3f(scaleVal, scaleVal, scaleVal);

                // Displace block with 75 ticks lifetime
                SpatialDisplacementManager.displaceBlock(world, targetPos, initialLift, rot, scale, 75, 0x00F0FF, true);

                task.displacedPositions.add(targetPos);
                task.originalRadius.put(targetPos, (float) dist);
                task.originalAngle.put(targetPos, angle);
                task.apexHeight.put(targetPos, apexY);
                task.rotationSpeed.put(targetPos, 0.8f + 0.6f * (float) Math.abs(Math.sin(hash * 1.5)));
                task.baseRotation.put(targetPos, rot);
                task.blockScale.put(targetPos, scale);

                displacedCount++;
            }
        }

        // Anti-Gravity Vortex: Launch nearby entities into mid-air
        Box vortexBox = new Box(center).expand(radius * 1.25);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, vortexBox, ent -> ent != player)) {
            e.addVelocity(0, 0.48, 0);
            e.velocityModified = true;
            e.damage(world.getDamageSources().magic(), 4.0f);
        }

        ACTIVE_TASKS.add(task);
    }

    public static void tick(ServerWorld world) {
        Iterator<RatioCollapseTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            RatioCollapseTask task = it.next();
            if (task.world != world) continue;

            task.ageTicks++;

            // Phase 1 & 2: Levitation & Orbital Swirl into Asteroid Cluster (Ticks 16..45)
            if (task.ageTicks >= 16 && task.ageTicks < 46) {
                task.swirlAngle += 0.16f;

                // Every 6 ticks: update display positions to swirl and pull inward into a dense composite asteroid!
                if ((task.ageTicks - 16) % 6 == 0) {
                    float phaseProgress = Math.min(1.0f, (task.ageTicks - 16) / 26.0f);
                    float inwardFactor = 1.0f - (phaseProgress * 0.45f); // 45% radius contraction

                    for (BlockPos pos : task.displacedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        float origR = task.originalRadius.getOrDefault(pos, 2.0f);
                        float origAngle = task.originalAngle.getOrDefault(pos, 0.0f);
                        float apexY = task.apexHeight.getOrDefault(pos, 7.0f);
                        Quaternionf baseRot = task.baseRotation.getOrDefault(pos, new Quaternionf());
                        Vector3f scale = task.blockScale.getOrDefault(pos, new Vector3f(1.0f, 1.0f, 1.0f));
                        float rotSpeed = task.rotationSpeed.getOrDefault(pos, 1.0f);

                        // Orbital position around center vertical axis
                        float curR = origR * inwardFactor;
                        float curAngle = origAngle + task.swirlAngle;
                        float relX = (float) (Math.cos(curAngle) * curR);
                        float relZ = (float) (Math.sin(curAngle) * curR);
                        float curY = apexY + (float) (Math.sin(curAngle * 2.0 + task.ageTicks * 0.15) * 0.35);

                        // Target vector relative to home block
                        double homeRelX = (task.center.getX() + relX) - pos.getX();
                        double homeRelZ = (task.center.getZ() + relZ) - pos.getZ();
                        double homeRelY = (task.center.getY() + curY) - pos.getY();

                        Vector3f newDisp = new Vector3f((float) homeRelX, (float) homeRelY, (float) homeRelZ);
                        Quaternionf newRot = new Quaternionf(baseRot).rotateY(task.swirlAngle * rotSpeed);

                        Vector3f comp = SpatialMath.compensatePivot(newDisp, newRot);
                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(6);
                        display.setTransformation(new AffineTransformation(comp, newRot, scale, null));
                    }

                    // Sound of grinding, hovering boulders
                    world.playSound(null, task.center.up(7), SoundEvents.BLOCK_BASALT_HIT, SoundCategory.BLOCKS, 1.4f, 1.2f);
                }

                // Vacuum pull & crushing damage on trapped mobs
                if (task.ageTicks % 8 == 0) {
                    Box crushBox = new Box(task.center).expand(task.radius * 1.3);
                    Vec3d vortexCenter = task.center.toCenterPos().add(0, 6.0, 0);

                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, crushBox, ent -> ent != task.caster)) {
                        Vec3d pull = vortexCenter.subtract(e.getPos()).multiply(0.14);
                        e.addVelocity(pull.x, 0.08, pull.z);
                        e.velocityModified = true;
                        e.damage(world.getDamageSources().magic(), 3.5f);
                    }
                }
            }

            // Phase 3: Tension / Calm before Cataclysm (Tick 44)
            if (task.ageTicks == 44) {
                world.playSound(null, task.center.up(7), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 3.0f, 1.7f);
                world.playSound(null, task.center, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 2.5f, 1.8f);
            }

            // Phase 4: THE METEORITE CATACLYSM SLAM (Tick 46)
            if (task.ageTicks == 46) {
                // Plunge all floating displays straight down at terminal velocity (4 ticks)
                for (BlockPos pos : task.displacedPositions) {
                    DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                    if (record == null || record.displayEntityUuid == null) continue;

                    BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                    if (display == null || !display.isAlive()) continue;

                    display.setStartInterpolation(0);
                    display.setInterpolationDuration(4); // 4 ticks: lightning-fast gravitational slam!
                    display.setTransformation(new AffineTransformation(
                            new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), null));
                }
            }

            // Phase 5: GROUND ZERO IMPACT & ANIME IMPACT FRAME (Tick 50)
            if (task.ageTicks == 50 && !task.slammed) {
                task.slammed = true;

                // Massive Cataclysmic Audio Layering
                world.playSound(null, task.center, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 4.5f, 0.55f);
                world.playSound(null, task.center, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.BLOCKS, 3.5f, 0.65f);
                world.playSound(null, task.center, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 3.5f, 0.5f);
                world.playSound(null, task.center, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.BLOCKS, 3.0f, 1.1f);

                // Ground Zero Cataclysm VFX
                world.spawnParticles(ParticleTypes.FLASH,
                        task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.0);
                world.spawnParticles(ParticleTypes.SONIC_BOOM,
                        task.center.getX() + 0.5, task.center.getY() + 0.8, task.center.getZ() + 0.5, 3, 0.1, 0.1, 0.1, 0.0);
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                        task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5, 3, 0.5, 0.5, 0.5, 0.0);

                // 360-Degree Radial Shrapnel & Dust Shockwave
                for (int i = 0; i < 28; i++) {
                    double angle = (i / 28.0) * (2.0 * Math.PI);
                    double r = task.radius * 0.9;
                    double px = task.center.getX() + 0.5 + Math.cos(angle) * r;
                    double pz = task.center.getZ() + 0.5 + Math.sin(angle) * r;
                    world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, task.center.getY() + 0.2, pz,
                            1, Math.cos(angle) * 0.15, 0.05, Math.sin(angle) * 0.15, 0.03);
                    world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                            px, task.center.getY() + 0.2, pz, 4, 0.2, 0.15, 0.2, 0.08);
                }

                // Massive AOE Slam Damage to all caught entities
                Box slamBox = new Box(task.center).expand(task.radius * 1.35);
                for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, slamBox, ent -> ent != task.caster)) {
                    double dist = e.getPos().distanceTo(task.center.toCenterPos());
                    float dmg = (float) Math.max(12.0, (1.0 - (dist / (task.radius * 1.35))) * 32.0 + 8.0);
                    e.damage(world.getDamageSources().fallingBlock(task.caster), dmg);

                    // Violent radial knockback away from ground zero
                    Vec3d diff = e.getPos().subtract(task.center.toCenterPos()).normalize().multiply(1.35).add(0, 0.55, 0);
                    e.addVelocity(diff.x, diff.y, diff.z);
                    e.velocityModified = true;
                }

                // TRIGGER POST-PROCESSING SHADER: Anime Impact Frame + Shockwave Lens Refraction
                ImpactFxPayload slamPayload = new ImpactFxPayload(
                        task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5,
                        ImpactFxPayload.TYPE_DETONATION, 16, 1.4f
                );
                for (ServerPlayerEntity p : PlayerLookup.around(world, task.center.toCenterPos(), 96.0)) {
                    ServerPlayNetworking.send(p, slamPayload);
                }
            }

            // Phase 6: Seamless Two-Pass Restoration (Tick 62)
            if (task.ageTicks >= task.maxLifetimeTicks) {
                List<DisplacedBlockRecord> toRestore = new ArrayList<>();
                for (BlockPos pos : task.displacedPositions) {
                    DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                    if (rec != null) {
                        toRestore.add(rec);
                    }
                }
                if (!toRestore.isEmpty()) {
                    SpatialDisplacementManager.executeTwoPassRestoration(world, toRestore);
                }
                it.remove();
            }
        }
    }

    public static int getActiveTaskCount() {
        return ACTIVE_TASKS.size();
    }

    public static void clearAllTasks() {
        ACTIVE_TASKS.clear();
    }
}
