package net.godwand.skill;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.network.ImpactFxPayload;
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

public class SupernovaManager {
    public static class SupernovaTask {
        public final ServerWorld world;
        public final ServerPlayerEntity caster;
        public final BlockPos center;
        public final Vec3d corePos;
        public int ageTicks = 0;
        public final int maxLifetimeTicks = 220;
        public float swirlAngle = 0.0f;
        public boolean exploded = false;
        public boolean rewindTriggered = false;

        public final List<BlockPos> displacedPositions = new ArrayList<>();
        public final Map<BlockPos, Float> originalRadius = new HashMap<>();
        public final Map<BlockPos, Float> originalAngle = new HashMap<>();
        public final Map<BlockPos, Float> apexY = new HashMap<>();
        public final Map<BlockPos, Quaternionf> baseRotation = new HashMap<>();
        public final Map<BlockPos, Vector3f> blockScale = new HashMap<>();

        public SupernovaTask(ServerWorld world, ServerPlayerEntity caster, BlockPos center, Vec3d corePos) {
            this.world = world;
            this.caster = caster;
            this.center = center.toImmutable();
            this.corePos = corePos;
        }
    }

    private static final Queue<SupernovaTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();

    public static void cast(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        // Target: In front of the player elevated in the sky
        HitResult hit = player.raycast(32.0, 0.0f, false);
        BlockPos groundCenter;
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bHit) {
            groundCenter = bHit.getBlockPos();
        } else {
            Vec3d forward = player.getEyePos().add(player.getRotationVec(1.0f).multiply(16.0));
            groundCenter = BlockPos.ofFloored(forward);
            while (groundCenter.getY() > world.getBottomY() && world.getBlockState(groundCenter).isAir()) {
                groundCenter = groundCenter.down();
            }
        }

        // Singularity core floating 8 blocks above ground center
        Vec3d corePos = new Vec3d(groundCenter.getX() + 0.5, groundCenter.getY() + 7.5, groundCenter.getZ() + 0.5);

        SupernovaTask task = new SupernovaTask(world, player, groundCenter, corePos);

        // Initiation: Cosmic event horizon birth audio
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 3.5f, 0.5f);
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 3.0f, 0.7f);
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 3.0f, 0.6f);

        // Initial Black Hole Event Horizon VFX
        world.spawnParticles(ParticleTypes.SONIC_BOOM, corePos.x, corePos.y, corePos.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.FLASH, corePos.x, corePos.y, corePos.z, 2, 0.1, 0.1, 0.1, 0.0);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, corePos.x, corePos.y, corePos.z, 40, 1.2, 1.2, 1.2, 0.35);

        ACTIVE_TASKS.add(task);
    }

    public static void tick(ServerWorld world) {
        Iterator<SupernovaTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            SupernovaTask task = it.next();
            if (task.world != world) continue;

            task.ageTicks++;

            // =========================================================================
            // PHASE 1: GRAVITATIONAL WELL & ACCRETION DISK UPROOT (Ticks 0..100 / 0s-5s)
            // =========================================================================
            if (task.ageTicks < 100) {
                // Pulsing Black Hole Core in mid-air
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                        task.corePos.x, task.corePos.y, task.corePos.z, 8, 0.4, 0.4, 0.4, 0.2);
                world.spawnParticles(new DustParticleEffect(new Vector3f(0.05f, 0.95f, 1.0f), 1.2f),
                        task.corePos.x, task.corePos.y, task.corePos.z, 4, 0.3, 0.3, 0.3, 0.05);

                if (task.ageTicks % 15 == 0) {
                    float pitch = 0.6f + (task.ageTicks / 100.0f) * 0.9f;
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 2.5f, pitch);
                }

                // Gravitational Vortex Suction: Sucks nearby entities into the event horizon
                Box suctionBox = new Box(task.center).expand(20.0);
                for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, suctionBox, ent -> ent != task.caster)) {
                    Vec3d pull = task.corePos.subtract(e.getPos()).normalize().multiply(0.16).add(0, 0.04, 0);
                    e.addVelocity(pull.x, pull.y, pull.z);
                    e.velocityModified = true;
                    if (task.ageTicks % 12 == 0) {
                        e.damage(world.getDamageSources().magic(), 2.5f);
                    }
                }

                // Wave 1, 2, 3 Uproot: Progressively rip ground blocks into the orbiting accretion disk
                if (task.ageTicks == 10 || task.ageTicks == 35 || task.ageTicks == 60) {
                    int waveIndex = task.ageTicks == 10 ? 1 : (task.ageTicks == 35 ? 2 : 3);
                    double rMin = (waveIndex - 1) * 3.8;
                    double rMax = waveIndex * 3.8;

                    int intR = (int) Math.ceil(rMax);
                    BlockPos.Mutable mutable = new BlockPos.Mutable();
                    int count = 0;

                    for (int x = -intR; x <= intR && count < 22; x++) {
                        for (int z = -intR; z <= intR && count < 22; z++) {
                            double dSq = x * x + z * z;
                            if (dSq < rMin * rMin || dSq > rMax * rMax) continue;

                            int startY = task.center.getY() + 3;
                            mutable.set(task.center.getX() + x, startY, task.center.getZ() + z);
                            while (mutable.getY() > task.center.getY() - 4 && world.getBlockState(mutable).isAir()) {
                                mutable.move(0, -1, 0);
                            }

                            BlockState state = world.getBlockState(mutable);
                            if (!SpatialDisplacementManager.canDisplace(world, mutable, state)) continue;

                            BlockPos pos = mutable.toImmutable();
                            double dist = Math.sqrt(dSq);
                            float angle = (float) Math.atan2(z, x);
                            float hash = (float) SpatialMath.hashCoordinateToPhase(pos.asLong());

                            float floatY = (float) (task.corePos.y - pos.getY() + Math.sin(hash) * 1.5);
                            Vector3f initialLift = new Vector3f((float) (-x * 0.2), floatY, (float) (-z * 0.2));

                            Quaternionf rot = new Quaternionf()
                                    .rotateY((float) Math.toRadians(40.0 * Math.cos(hash)))
                                    .rotateX((float) Math.toRadians(25.0 * Math.sin(hash)));

                            Vector3f scale = new Vector3f(1.1f, 1.1f, 1.1f);

                            SpatialDisplacementManager.displaceBlock(world, pos, initialLift, rot, scale, 220, 0x00F5FF, true);

                            task.displacedPositions.add(pos);
                            task.originalRadius.put(pos, (float) dist);
                            task.originalAngle.put(pos, angle);
                            task.apexY.put(pos, floatY);
                            task.baseRotation.put(pos, rot);
                            task.blockScale.put(pos, scale);
                            count++;
                        }
                    }

                    world.playSound(null, task.center, SoundEvents.BLOCK_BASALT_BREAK, SoundCategory.BLOCKS, 2.0f, 0.8f);
                }

                // Accretion Disk Horizontal Swirling
                if (task.ageTicks >= 20 && task.ageTicks % 6 == 0) {
                    task.swirlAngle += 0.22f;
                    updateAccretionOrbit(task, 1.0f);
                }
            }

            // =========================================================================
            // PHASE 2: SUPERDENSE COMPRESSION (Ticks 100..140 / 5s - 7s)
            // =========================================================================
            else if (task.ageTicks >= 100 && task.ageTicks < 140) {
                task.swirlAngle += 0.35f;

                // Accretion disk contracts inwards towards core center
                float compressFactor = 1.0f - ((task.ageTicks - 100) / 40.0f) * 0.65f;
                if ((task.ageTicks - 100) % 4 == 0) {
                    updateAccretionOrbit(task, compressFactor);
                }

                // Core flashes violently
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        task.corePos.x, task.corePos.y, task.corePos.z, 12, 0.8, 0.8, 0.8, 0.2);
                world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.9f, 0.3f), 1.6f),
                        task.corePos.x, task.corePos.y, task.corePos.z, 6, 0.5, 0.5, 0.5, 0.1);

                if (task.ageTicks % 8 == 0) {
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 3.0f, 1.8f);
                }
            }

            // =========================================================================
            // PHASE 3: EVENT HORIZON SILENCE (Ticks 141..144)
            // =========================================================================
            else if (task.ageTicks >= 141 && task.ageTicks < 145) {
                // The dramatic pause before the supernova
                if (task.ageTicks == 142) {
                    world.spawnParticles(ParticleTypes.FLASH, task.corePos.x, task.corePos.y, task.corePos.z, 2, 0.1, 0.1, 0.1, 0.0);
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 3.0f, 2.0f);
                }
            }

            // =========================================================================
            // PHASE 4: THE SUPERNOVA DETONATION & CATACLYSM (Tick 145 / 7.25s)
            // =========================================================================
            else if (task.ageTicks == 145 && !task.exploded) {
                task.exploded = true;

                // TRIGGER SHADER: Anime Impact Frame Inversion + Mega Relativistic Shockwave
                ImpactFxPayload novaPayload = new ImpactFxPayload(
                        task.corePos.x, task.corePos.y, task.corePos.z,
                        ImpactFxPayload.TYPE_SUPERNOVA, 24, 2.0f
                );
                for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 128.0)) {
                    ServerPlayNetworking.send(p, novaPayload);
                }

                // Apocalyptic Sound Layering
                world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                        SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 5.0f, 0.5f);
                world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                        SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 5.0f, 0.6f);
                world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                        SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 4.0f, 0.8f);
                world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                        SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.PLAYERS, 4.0f, 0.5f);
                world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                        SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 4.0f, 0.55f);

                // Cataclysmic VFX: Flash, Sonic Booms, Solar Corona
                world.spawnParticles(ParticleTypes.FLASH, task.corePos.x, task.corePos.y, task.corePos.z, 8, 0.5, 0.5, 0.5, 0.0);
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, task.corePos.x, task.corePos.y, task.corePos.z, 5, 1.2, 1.2, 1.2, 0.0);
                world.spawnParticles(ParticleTypes.SONIC_BOOM, task.corePos.x, task.corePos.y, task.corePos.z, 4, 0.5, 0.5, 0.5, 0.0);
                world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.85f, 0.25f), 2.0f),
                        task.corePos.x, task.corePos.y, task.corePos.z, 100, 3.5, 3.5, 3.5, 0.6);

                // Blast blocks outward violently in 3D (Stellar Ejecta)
                for (BlockPos pos : task.displacedPositions) {
                    DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                    if (record == null || record.displayEntityUuid == null) continue;

                    BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                    if (display == null || !display.isAlive()) continue;

                    double dx = (pos.getX() + 0.5) - task.corePos.x;
                    double dy = (pos.getY() + 0.5) - task.corePos.y;
                    double dz = (pos.getZ() + 0.5) - task.corePos.z;
                    double d = Math.max(0.5, Math.sqrt(dx * dx + dy * dy + dz * dz));

                    // Outward burst displacement
                    Vector3f blastDisp = new Vector3f(
                            (float) (dx / d * 18.0),
                            (float) (dy / d * 12.0 + 4.0),
                            (float) (dz / d * 18.0)
                    );
                    Quaternionf tumble = new Quaternionf().rotateAxis((float) Math.toRadians(75), (float) dz, (float) dx, (float) dy);
                    Vector3f comp = SpatialMath.compensatePivot(blastDisp, tumble);

                    display.setStartInterpolation(0);
                    display.setInterpolationDuration(8); // 8 ticks explosive outward surge!
                    display.setTransformation(new AffineTransformation(comp, tumble, new Vector3f(1.2f, 1.2f, 1.2f), null));
                }

                // Massive Cataclysmic Blast Damage
                Box blastBox = new Box(task.center).expand(26.0);
                for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, blastBox, ent -> ent != task.caster)) {
                    double dist = e.getPos().distanceTo(task.corePos);
                    float dmg = (float) Math.max(20.0, (1.0 - (dist / 26.0)) * 65.0 + 15.0);
                    e.damage(world.getDamageSources().explosion(null, task.caster), dmg);

                    Vec3d push = e.getPos().subtract(task.corePos).normalize().multiply(2.2).add(0, 0.85, 0);
                    e.addVelocity(push.x, push.y, push.z);
                    e.velocityModified = true;
                }
            }

            // =========================================================================
            // PHASE 5: TIME FREEZE AT THE EVENT HORIZON (Ticks 155..175 / 7.75s - 8.75s)
            // =========================================================================
            else if (task.ageTicks >= 155 && task.ageTicks < 175) {
                // Floating in suspended animation (Zero-G shimmer)
                if (task.ageTicks % 6 == 0) {
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 2.5f, 1.2f);
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 2.0f, 0.9f);
                }
            }

            // =========================================================================
            // PHASE 6: CHRONO REVERSAL (Quay Ngược Thời Gian) (Ticks 176..215 / 8.75s-10.75s)
            // =========================================================================
            else if (task.ageTicks >= 176 && !task.rewindTriggered) {
                task.rewindTriggered = true;

                // Chrono-Reversal Audio & Reverse Portal Streams
                world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                        SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 4.0f, 1.35f);
                world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                        SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 3.0f, 1.6f);

                // All flying debris smoothly decelerates and reverses trajectory back into original sockets!
                for (BlockPos pos : task.displacedPositions) {
                    DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                    if (record == null || record.displayEntityUuid == null) continue;

                    BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                    if (display == null || !display.isAlive()) continue;

                    display.setStartInterpolation(0);
                    display.setInterpolationDuration(16); // 16 ticks (800ms) smooth reverse flight back to earth!
                    display.setTransformation(new AffineTransformation(
                            new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), null));
                }
            }

            // =========================================================================
            // PHASE 7: SEAMLESS RESTORATION & SEALING (Tick 218+)
            // =========================================================================
            if (task.ageTicks >= 218) {
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

                // Final Re-solidification Chime
                world.playSound(null, task.center, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 2.5f, 1.8f);
                world.playSound(null, task.center, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 2.5f, 1.2f);
                it.remove();
            }
        }
    }

    private static void updateAccretionOrbit(SupernovaTask task, float radiusFactor) {
        for (BlockPos pos : task.displacedPositions) {
            DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(task.world.getRegistryKey(), pos);
            if (record == null || record.displayEntityUuid == null) continue;

            BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
            if (display == null || !display.isAlive()) continue;

            float origR = task.originalRadius.getOrDefault(pos, 4.0f);
            float origAngle = task.originalAngle.getOrDefault(pos, 0.0f);
            float apex = task.apexY.getOrDefault(pos, 7.0f);
            Quaternionf baseRot = task.baseRotation.getOrDefault(pos, new Quaternionf());
            Vector3f scale = task.blockScale.getOrDefault(pos, new Vector3f(1.0f, 1.0f, 1.0f));

            float curR = origR * radiusFactor;
            float curAngle = origAngle + task.swirlAngle;

            double targetWorldX = task.corePos.x + Math.cos(curAngle) * curR;
            double targetWorldZ = task.corePos.z + Math.sin(curAngle) * curR;
            double targetWorldY = task.corePos.y + Math.sin(curAngle * 2.0) * 0.8;

            Vector3f newDisp = new Vector3f(
                    (float) (targetWorldX - pos.getX()),
                    (float) (targetWorldY - pos.getY()),
                    (float) (targetWorldZ - pos.getZ())
            );

            Quaternionf newRot = new Quaternionf(baseRot).rotateY(task.swirlAngle);
            Vector3f comp = SpatialMath.compensatePivot(newDisp, newRot);

            display.setStartInterpolation(0);
            display.setInterpolationDuration(6);
            display.setTransformation(new AffineTransformation(comp, newRot, scale, null));
        }
    }

    public static int getActiveTaskCount() {
        return ACTIVE_TASKS.size();
    }

    public static void clearAllTasks() {
        ACTIVE_TASKS.clear();
    }
}
