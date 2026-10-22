package net.godwand.spatial;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Clearable;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpatialDisplacementManager {
    private static final Map<DimensionPos, DisplacedBlockRecord> ACTIVE_RECORDS = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockDisplayEntity> ACTIVE_ENTITIES = new ConcurrentHashMap<>();

    public static boolean isPositionDisplaced(RegistryKey<World> worldKey, BlockPos pos) {
        return ACTIVE_RECORDS.containsKey(new DimensionPos(worldKey, pos));
    }

    public static boolean isPositionDisplaced(BlockPos pos) {
        return isPositionDisplaced(World.OVERWORLD, pos);
    }

    public static boolean canDisplace(ServerWorld world, BlockPos pos, BlockState state) {
        BlockPos immutablePos = pos.toImmutable();
        if (state.isAir() || state.getHardness(world, immutablePos) < 0) return false;
        if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.END_PORTAL) || state.isOf(Blocks.BARRIER)) return false;
        return !isPositionDisplaced(world.getRegistryKey(), immutablePos);
    }

    public static int getActiveRecordCount() {
        return ACTIVE_RECORDS.size();
    }

    public static int getActiveEntityCount() {
        return ACTIVE_ENTITIES.size();
    }

    public static DisplacedBlockRecord getActiveRecord(RegistryKey<World> worldKey, BlockPos pos) {
        return ACTIVE_RECORDS.get(new DimensionPos(worldKey, pos));
    }

    public static BlockDisplayEntity getActiveEntity(UUID uuid) {
        return uuid != null ? ACTIVE_ENTITIES.get(uuid) : null;
    }

    public static void trackEntity(UUID uuid, BlockDisplayEntity entity) {
        if (uuid != null && entity != null) {
            ACTIVE_ENTITIES.put(uuid, entity);
        }
    }

    public static void untrackEntity(UUID uuid) {
        if (uuid != null) {
            ACTIVE_ENTITIES.remove(uuid);
        }
    }

    public static DisplacedBlockRecord removeActiveRecord(RegistryKey<World> worldKey, BlockPos pos) {
        DisplacedBlockRecord rec = ACTIVE_RECORDS.remove(new DimensionPos(worldKey, pos));
        if (rec != null && rec.displayEntityUuid != null) {
            BlockDisplayEntity ent = ACTIVE_ENTITIES.remove(rec.displayEntityUuid);
            if (ent != null && ent.isAlive()) {
                ent.discard();
            }
        }
        return rec;
    }

    public static List<DisplacedBlockRecord> getActiveRecordsForWorld(RegistryKey<World> worldKey) {
        List<DisplacedBlockRecord> list = new ArrayList<>();
        for (DisplacedBlockRecord r : ACTIVE_RECORDS.values()) {
            if (r.worldKey.equals(worldKey)) {
                list.add(r);
            }
        }
        return list;
    }

    public static void refreshDisplacement(RegistryKey<World> worldKey, BlockPos pos, int lifetime) {
        DisplacedBlockRecord record = ACTIVE_RECORDS.get(new DimensionPos(worldKey, pos));
        if (record != null) {
            record.interruptRestoration();
            record.maxLifetimeTicks = lifetime;
        }
    }

    public static BlockPos getMatchingDoubleBlockHalf(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            if (state.contains(DoorBlock.HALF)) {
                DoubleBlockHalf half = state.get(DoorBlock.HALF);
                BlockPos target = half == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
                BlockState targetState = world.getBlockState(target);
                if (targetState.isOf(state.getBlock()) && targetState.contains(DoorBlock.HALF) && targetState.get(DoorBlock.HALF) != half) {
                    return target;
                }
            }
        } else if (state.getBlock() instanceof TallPlantBlock) {
            if (state.contains(TallPlantBlock.HALF)) {
                DoubleBlockHalf half = state.get(TallPlantBlock.HALF);
                BlockPos target = half == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
                BlockState targetState = world.getBlockState(target);
                if (targetState.isOf(state.getBlock()) && targetState.contains(TallPlantBlock.HALF) && targetState.get(TallPlantBlock.HALF) != half) {
                    return target;
                }
            }
        } else if (state.getBlock() instanceof BedBlock) {
            if (state.contains(BedBlock.PART) && state.contains(BedBlock.FACING)) {
                BedPart part = state.get(BedBlock.PART);
                Direction facing = state.get(BedBlock.FACING);
                BlockPos target = part == BedPart.FOOT ? pos.offset(facing) : pos.offset(facing.getOpposite());
                BlockState targetState = world.getBlockState(target);
                if (targetState.isOf(state.getBlock()) && targetState.contains(BedBlock.PART) && targetState.get(BedBlock.PART) != part) {
                    return target;
                }
            }
        }
        return null;
    }

    public static void displaceBlock(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, int lifetime, int glowColor) {
        displaceBlock(world, pos, targetDisplacement, rotation, new Vector3f(1.0f, 1.0f, 1.0f), lifetime, glowColor, true);
    }

    public static void displaceBlock(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, int lifetime, int glowColor, boolean spawnDisplay) {
        displaceBlock(world, pos, targetDisplacement, rotation, new Vector3f(1.0f, 1.0f, 1.0f), lifetime, glowColor, spawnDisplay);
    }

    public static void displaceBlock(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, Vector3f scale, int lifetime, int glowColor, boolean spawnDisplay) {
        displaceBlock(world, pos, targetDisplacement, rotation, scale, lifetime, glowColor, spawnDisplay,
                DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE, null, null, 0.0);
    }

    public static void displaceBlock(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, Vector3f scale, int lifetime, int glowColor,
                                      boolean spawnDisplay, DisplacedBlockRecord.DisplacementMode mode,
                                      UUID boundPlayerUuid, Vec3d blastCenter, double blastRadius) {
        displaceBlockInternal(world, pos, targetDisplacement, rotation, scale, lifetime, glowColor,
                spawnDisplay, mode, boundPlayerUuid, blastCenter, blastRadius, true);
    }

    private static void displaceBlockInternal(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                              Quaternionf rotation, Vector3f scale, int lifetime, int glowColor,
                                              boolean spawnDisplay, DisplacedBlockRecord.DisplacementMode mode,
                                              UUID boundPlayerUuid, Vec3d blastCenter, double blastRadius, boolean checkDoubleBlock) {
        BlockPos immutablePos = pos.toImmutable();
        BlockState state = world.getBlockState(immutablePos);
        if (!canDisplace(world, immutablePos, state)) return;

        BlockPos otherHalf = checkDoubleBlock ? getMatchingDoubleBlockHalf(world, immutablePos, state) : null;

        // Skip display spawn for pure water or lava
        if (state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA)) {
            spawnDisplay = false;
        }

        // Exact 1:1 state and NBT mapping: read identifying data then clear inventory to prevent duping
        BlockEntity be = world.getBlockEntity(immutablePos);
        NbtCompound nbt = null;
        if (be != null) {
            nbt = be.createNbtWithIdentifyingData(world.getRegistryManager());
            Clearable.clear(be);
        }

        boolean isFull = state.isFullCube(world, immutablePos);
        DisplacedBlockRecord record = new DisplacedBlockRecord(
                immutablePos, world.getRegistryKey(), state, nbt, targetDisplacement, rotation, scale,
                lifetime, isFull, mode, boundPlayerUuid);
        record.blastCenter = blastCenter;
        record.blastRadius = blastRadius;

        // Replace with AIR using atomic notification flags
        world.setBlockState(immutablePos, Blocks.AIR.getDefaultState(), Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);

        // Tick 0 Handshake: spawn at origin with identity matrix, high brightness, and glowing
        if (spawnDisplay) {
            BlockDisplayEntity display = new BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
            display.addCommandTag("godwand_displaced");
            display.setPosition(immutablePos.getX(), immutablePos.getY(), immutablePos.getZ());
            display.setBlockState(state);

            boolean isArcaneHighlight = ((immutablePos.asLong() * 1315423911L) & 15L) == 0L;
            if (isArcaneHighlight && glowColor != 0) {
                display.setGlowing(true);
                display.setGlowColorOverride(glowColor);
                display.setBrightness(new Brightness(15, 15));
            } else {
                display.setGlowing(false);
                display.setBrightness(new Brightness(12, 13));
            }

            display.setStartInterpolation(0);
            display.setInterpolationDuration(0);
            display.setTransformation(AffineTransformation.identity());

            world.spawnEntity(display);
            record.displayEntityUuid = display.getUuid();
            ACTIVE_ENTITIES.put(display.getUuid(), display);

            // Subtle break crumbs
            if ((immutablePos.asLong() & 3) == 0) {
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                        immutablePos.getX() + 0.5, immutablePos.getY() + 0.5, immutablePos.getZ() + 0.5,
                        3, 0.15, 0.15, 0.15, 0.08);
                world.playSound(null, immutablePos, SoundEvents.BLOCK_BASALT_BREAK, SoundCategory.BLOCKS, 0.6f, 0.85f + 0.25f * (float)Math.random());
            }
        }

        ACTIVE_RECORDS.put(new DimensionPos(world.getRegistryKey(), immutablePos), record);
        DisplacementPersistentState.getServerState(world).markDirty();

        // Displace matching half of double block simultaneously
        if (otherHalf != null && canDisplace(world, otherHalf, world.getBlockState(otherHalf))) {
            displaceBlockInternal(world, otherHalf, targetDisplacement, rotation, scale, lifetime,
                    glowColor, spawnDisplay, mode, boundPlayerUuid, blastCenter, blastRadius, false);
        }
    }

    public static void tick(ServerWorld world) {
        List<DisplacedBlockRecord> toRemove = new ArrayList<>();

        for (DisplacedBlockRecord record : ACTIVE_RECORDS.values()) {
            if (!record.worldKey.equals(world.getRegistryKey())) {
                continue;
            }

            record.ageTicks++;
            BlockDisplayEntity display = record.displayEntityUuid != null ? ACTIVE_ENTITIES.get(record.displayEntityUuid) : null;

            // ==========================================
            // 1. PHA THU HỒI 3 BƯỚC (3-PHASE MAGNETIC SNAP REASSEMBLY)
            // ==========================================
            if (record.isRestoring()) {
                // Nếu là PATH_WALKER mà người chơi bước lại vào vùng <= 4.2m trong lúc đang đóng -> Hất ngược ra vòm Aura ngay lập tức!
                if (record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER ||
                    record.mode == DisplacedBlockRecord.DisplacementMode.HYSTERESIS_AURA) {
                    Vec3d blockCenter = Vec3d.ofCenter(record.pos);
                    for (ServerPlayerEntity p : world.getPlayers()) {
                        if (net.godwand.skill.SpatialPathWalker.isActive(p)) {
                            Vec3d lv = p.getRotationVec(1.0f);
                            Vec3d lh = new Vec3d(lv.x, 0, lv.z);
                            lh = lh.lengthSquared() < 0.001 ? new Vec3d(1, 0, 0) : lh.normalize();
                            double lead = net.godwand.skill.SpatialPathWalker.getPlayerForwardLead(p);
                            double spineDist = SpatialMath.distanceToAuraSpine(blockCenter, p.getPos().add(0, 1.1, 0), lh, lead);
                            if (spineDist <= 3.5) {
                                record.interruptRestoration();
                                if (display != null && display.isAlive()) {
                                    Vec3d rebound = SpatialMath.computeDynamicAuraOffset(record.pos, p.getPos(), lh, 2.1, record.ageTicks);
                                    record.currentOffset.set((float) rebound.x, (float) rebound.y, (float) rebound.z);
                                    Quaternionf reboundRot = SpatialMath.computeArchwayRotation(record.pos, p.getPos(), lh, record.ageTicks);
                                    record.currentRotation.set(reboundRot);
                                    display.setStartInterpolation(0);
                                    display.setInterpolationDuration(5);
                                    display.setTransformation(SpatialMath.createPivotTransform(rebound, reboundRot, record.targetScale.x));
                                }
                                break;
                            }
                        }
                    }
                }

                if (record.isRestoring()) {
                    if (display != null && display.isAlive()) {
                        // Pha 1 (restoreTick == 1): Hút xoắn ốc về sát ô gốc (Spiral Pull) trong 9 ticks
                        if (record.restoreTick == 1) {
                            Vec3d spiralPreSnap = SpatialMath.computeSpiralReturnOffset(record.getCurrentOffset(), record.pos);
                            record.currentOffset.set((float) spiralPreSnap.x, (float) spiralPreSnap.y, (float) spiralPreSnap.z);
                            Quaternionf nearZeroRot = new Quaternionf().slerp(record.currentRotation, 0.18f);
                            record.currentRotation.set(nearZeroRot);

                            display.setStartInterpolation(0);
                            display.setInterpolationDuration(9);
                            display.setTransformation(SpatialMath.createPivotTransform(spiralPreSnap, nearZeroRot, 0.96f));
                        }
                        // Pha 2 (restoreTick == 10): Khóa khớp từ tính (Magnetic Snap) về đúng (0,0,0) trong 4 ticks
                        else if (record.restoreTick == 10) {
                            record.currentOffset.set(0, 0, 0);
                            display.setStartInterpolation(0);
                            display.setInterpolationDuration(4);
                            display.setTransformation(AffineTransformation.identity());
                            if ((record.pos.asLong() & 7) == 0) {
                                world.playSound(null, record.pos, SoundEvents.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.BLOCKS, 0.25f, 1.35f);
                            }
                        }
                    }

                    // Pha 3 - Pass 1 (restoreTick == 14): Khôi phục khối đặc đúng 1:1
                    if (record.restoreTick == 14 && record.originalState.isFullCube(world, record.pos)) {
                        restoreRecordDirectly(world, record, display);
                        toRemove.add(record);
                    }
                    // Pha 3 - Pass 2 (restoreTick >= 15): Khôi phục khối đính kèm (Đuốc, Đèn lồng, Hoa...)
                    else if (record.restoreTick >= 15) {
                        restoreRecordDirectly(world, record, display);
                        toRemove.add(record);
                    }

                    record.restoreTick++;
                    continue;
                }
            }

            // ==========================================
            // 2. VÒNG ĐỜI SPAWN 2-TICK & BẢO VỆ MỞ ĐƯỜNG ĐẦU TIÊN
            // ==========================================
            if (record.spawnTickDelay > 0) {
                record.spawnTickDelay--;
                if (record.spawnTickDelay == 0 && display != null && display.isAlive()) {
                    display.setStartInterpolation(0);
                    int openDuration = record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER ? 5 : 8;
                    display.setInterpolationDuration(openDuration); // 5 ticks cho PATH_WALKER, 8 ticks cho các chiêu khác
                    display.setTransformation(SpatialMath.createPivotTransform(
                            record.getCurrentOffset(), record.currentRotation, record.targetScale.x));

                    record.animationStage = 1;
                    record.transformed = true;
                }
                continue;
            }

            // Chờ hết ticks bung mở ban đầu (5 ticks cho PATH_WALKER, 8 ticks cho các chiêu khác) rồi mới chạy cập nhật trường lực động
            int minHoldTicks = record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER ? 6 : 9;
            if (record.ageTicks < minHoldTicks) {
                continue;
            }

            // ==========================================
            // 3. CHẾ ĐỘ CHIÊU 1: LIVING AURA + SPRING LERP + INTERACTIVE EVASION
            // ==========================================
            if (record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER ||
                record.mode == DisplacedBlockRecord.DisplacementMode.HYSTERESIS_AURA) {

                Vec3d blockCenter = Vec3d.ofCenter(record.pos);
                ServerPlayerEntity activeOwner = null;
                double minSpineDist = Double.MAX_VALUE;

                for (ServerPlayerEntity p : world.getPlayers()) {
                    if (net.godwand.skill.SpatialPathWalker.isActive(p)) {
                        Vec3d lv = p.getRotationVec(1.0f);
                        Vec3d lh = new Vec3d(lv.x, 0, lv.z);
                        lh = lh.lengthSquared() < 0.001 ? new Vec3d(1, 0, 0) : lh.normalize();
                        double lead = net.godwand.skill.SpatialPathWalker.getPlayerForwardLead(p);
                        double d = SpatialMath.distanceToAuraSpine(blockCenter, p.getPos().add(0, 1.1, 0), lh, lead);
                        if (d < minSpineDist) {
                            minSpineDist = d;
                            activeOwner = p;
                        }
                    }
                }

                // Bán kính quét quanh trục là 3.3m, ngưỡng giữ mở quanh trục là 3.8m -> Không bao giờ bị đóng sập khi đang chạy nhanh
                if (activeOwner != null && minSpineDist <= 3.8) {
                    record.resetRestoreTick();

                    if (display != null && display.isAlive() && ((record.ageTicks + Math.abs(record.pos.hashCode())) % 3 == 0)) {
                        Vec3d lookVec = activeOwner.getRotationVec(1.0f);
                        Vec3d lookHoriz = new Vec3d(lookVec.x, 0, lookVec.z);
                        lookHoriz = lookHoriz.lengthSquared() < 0.001 ? new Vec3d(1, 0, 0) : lookHoriz.normalize();

                        // Tính vị trí đích trên vòm Aura
                        Vec3d desiredAuraOffset = SpatialMath.computeDynamicAuraOffset(
                                record.pos, activeOwner.getPos(), lookHoriz, 2.1, record.ageTicks);

                        // Kiểm tra né tương tác dựa trên VỊ TRÍ THỰC TẾ ĐANG BAY của mảnh vỡ
                        Vec3d actualDebrisWorldPos = blockCenter.add(record.getCurrentOffset());
                        PlayerEntity closestToDebris = world.getClosestPlayer(actualDebrisWorldPos.x, actualDebrisWorldPos.y, actualDebrisWorldPos.z, 3.8, false);
                        if (closestToDebris != null) {
                            Vec3d evasion = SpatialMath.computeInteractiveEvasion(
                                    actualDebrisWorldPos, closestToDebris.getPos().add(0, 1.1, 0), 3.4);
                            if (evasion.lengthSquared() > 0.001) {
                                desiredAuraOffset = desiredAuraOffset.add(evasion);
                                record.hoverYaw += 0.38f;
                                world.spawnParticles(ParticleTypes.END_ROD, actualDebrisWorldPos.x, actualDebrisWorldPos.y, actualDebrisWorldPos.z, 1, 0.05, 0.05, 0.05, 0.01);
                            }
                        }

                        // Quán tính lò xo (Spring Damping Lerp 0.42) giúp mảnh vỡ lướt mượt không bị giật khi xoay chuột nhanh
                        Vec3d smoothedOffset = record.getCurrentOffset().lerp(desiredAuraOffset, 0.42);
                        record.currentOffset.set((float) smoothedOffset.x, (float) smoothedOffset.y, (float) smoothedOffset.z);

                        record.hoverYaw += 0.09f;
                        Quaternionf rot = SpatialMath.computeArchwayRotation(record.pos, activeOwner.getPos(), lookHoriz, record.ageTicks);
                        record.currentRotation.set(rot);

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(3);
                        display.setTransformation(SpatialMath.createPivotTransform(smoothedOffset, rot, record.targetScale.x));

                        // Dây năng lượng 3D nối từ khối vòm cao xuống mặt đất
                        if (desiredAuraOffset.y > 1.2 && ((record.ageTicks + record.pos.hashCode()) & 7) == 0) {
                            world.spawnParticles(ParticleTypes.END_ROD,
                                    actualDebrisWorldPos.x, actualDebrisWorldPos.y, actualDebrisWorldPos.z,
                                    1, 0.04, -0.4, 0.04, 0.02);
                        }
                    }
                } else {
                    record.beginRestoration();
                }
                continue;
            }

            // ==========================================
            // 4. CHẾ ĐỘ CHIÊU 2: TORNADO FUNNEL VORTEX + INTERACTIVE EVASION
            // ==========================================
            if (record.mode == DisplacedBlockRecord.DisplacementMode.CANOPY_VORTEX ||
               (record.mode == DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE && record.blastCenter != null)) {

                if (record.ageTicks >= record.maxLifetimeTicks) {
                    record.beginRestoration();
                    continue;
                }

                if (display != null && display.isAlive() && (display.getId() + record.ageTicks) % 3 == 0) {
                    Vec3d blockCenter = Vec3d.ofCenter(record.pos);
                    Vec3d vortexOffset = SpatialMath.computeCanopyVortexOffset(record.pos, record.blastCenter, record.blastRadius, record.ageTicks);

                    // Nếu người chơi bay/đi lại gần mảnh thiên thạch của Chiêu 2, mảnh thiên thạch cũng tự né người chơi!
                    Vec3d actualDebrisWorldPos = blockCenter.add(record.getCurrentOffset());
                    PlayerEntity closestPlayer = world.getClosestPlayer(actualDebrisWorldPos.x, actualDebrisWorldPos.y, actualDebrisWorldPos.z, 3.8, false);
                    if (closestPlayer != null) {
                        Vec3d evasion = SpatialMath.computeInteractiveEvasion(
                                actualDebrisWorldPos, closestPlayer.getPos().add(0, 1.1, 0), 3.4);
                        if (evasion.lengthSquared() > 0.001) {
                            vortexOffset = vortexOffset.add(evasion);
                            record.hoverYaw += 0.35f;
                        }
                    }

                    Vec3d smoothedVortex = record.getCurrentOffset().lerp(vortexOffset, 0.45);
                    record.currentOffset.set((float) smoothedVortex.x, (float) smoothedVortex.y, (float) smoothedVortex.z);
                    record.hoverYaw += 0.07f;
                    Quaternionf rot = new Quaternionf(record.targetRotation).rotateY(record.hoverYaw);
                    record.currentRotation.set(rot);

                    display.setStartInterpolation(0);
                    display.setInterpolationDuration(3);
                    display.setTransformation(SpatialMath.createPivotTransform(smoothedVortex, rot, record.targetScale.x));
                }
                continue;
            }

            // ==========================================
            // 4.5. BẢO HIỂM WATCHDOG CHO CUSTOM_SCRIPTED (Chiêu 3 / Chiêu 4)
            // ==========================================
            if (record.mode == DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED) {
                if (record.ageTicks >= record.maxLifetimeTicks + 20) {
                    record.beginRestoration();
                }
                continue;
            }

            // ==========================================
            // 5. DEFAULT TIMED LIFECYCLE (Nội thất ẩn / Khối thường)
            // ==========================================
            if (record.mode == DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE) {
                if (record.ageTicks >= record.maxLifetimeTicks) {
                    record.beginRestoration();
                    continue;
                }

                if (display != null && display.isAlive() && record.ageTicks % 6 == 0) {
                    record.hoverYaw += 0.08f;
                    double hashPhase = SpatialMath.hash01(record.pos) * Math.PI * 2.0;
                    float tremorY = (float) (Math.sin(hashPhase + record.ageTicks / 6.0) * 0.14);

                    Vector3f hoverDisp = new Vector3f(
                            record.baseDisplacement.x,
                            record.baseDisplacement.y + tremorY,
                            record.baseDisplacement.z
                    );
                    record.currentOffset.set(hoverDisp);
                    Quaternionf hoverRot = new Quaternionf(record.targetRotation).rotateY(record.hoverYaw);
                    record.currentRotation.set(hoverRot);

                    display.setStartInterpolation(0);
                    display.setInterpolationDuration(6);
                    display.setTransformation(SpatialMath.createPivotTransform(hoverDisp, hoverRot, record.targetScale.x));
                }
            }
        }

        for (DisplacedBlockRecord r : toRemove) {
            ACTIVE_RECORDS.remove(new DimensionPos(r.worldKey, r.pos));
            DisplacementPersistentState.getServerState(world).markDirty();
        }
    }

    private static void restoreRecordDirectly(ServerWorld world, DisplacedBlockRecord r, BlockDisplayEntity display) {
        // Exact 1:1 original BlockState and NBT restoration
        world.setBlockState(r.pos, r.originalState, Block.FORCE_STATE | Block.NOTIFY_LISTENERS);
        if (r.blockEntityNbt != null) {
            BlockEntity be = world.getBlockEntity(r.pos);
            if (be != null) {
                be.read(r.blockEntityNbt, world.getRegistryManager());
                be.markDirty();
            }
        }
        if (display != null) {
            ACTIVE_ENTITIES.remove(r.displayEntityUuid);
            display.discard();
        }
        // Crisp stone impact particle and sound
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, r.originalState),
                r.pos.getX() + 0.5, r.pos.getY() + 0.5, r.pos.getZ() + 0.5, 4, 0.15, 0.15, 0.15, 0.05);
        if ((r.pos.asLong() & 3) == 0) {
            world.playSound(null, r.pos, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 0.75f, 1.0f);
        }
    }

    public static void collapsePlayerAura(UUID playerUuid) {
        for (DisplacedBlockRecord record : ACTIVE_RECORDS.values()) {
            if (record.boundPlayerUuid != null && record.boundPlayerUuid.equals(playerUuid)) {
                record.beginRestoration();
            }
        }
    }

    public static void emergencyRestoreAll(ServerWorld world) {
        List<DisplacedBlockRecord> matching = new ArrayList<>();
        for (DisplacedBlockRecord r : ACTIVE_RECORDS.values()) {
            if (r.worldKey.equals(world.getRegistryKey())) {
                matching.add(r);
            }
        }
        executeTwoPassRestoration(world, matching);
    }

    public static void executeTwoPassRestoration(ServerWorld world, List<DisplacedBlockRecord> records) {
        // Pass 1: Solid base blocks
        for (DisplacedBlockRecord r : records) {
            if (r.isFullCube) {
                BlockDisplayEntity d = r.displayEntityUuid != null ? ACTIVE_ENTITIES.get(r.displayEntityUuid) : null;
                restoreRecordDirectly(world, r, d);
                ACTIVE_RECORDS.remove(new DimensionPos(r.worldKey, r.pos));
            }
        }
        // Pass 2: Attached & delicate blocks (Torches, Lanterns, Signs, Doors, Plants)
        // Sort by Y ascending so lower parts of double blocks are placed before upper parts
        List<DisplacedBlockRecord> delicate = new ArrayList<>();
        for (DisplacedBlockRecord r : records) {
            if (!r.isFullCube) {
                delicate.add(r);
            }
        }
        delicate.sort(Comparator.comparingInt(r -> r.pos.getY()));
        for (DisplacedBlockRecord r : delicate) {
            BlockDisplayEntity d = r.displayEntityUuid != null ? ACTIVE_ENTITIES.get(r.displayEntityUuid) : null;
            restoreRecordDirectly(world, r, d);
            ACTIVE_RECORDS.remove(new DimensionPos(r.worldKey, r.pos));
        }
        DisplacementPersistentState.getServerState(world).markDirty();
    }

    public static void collapseNearbyDisplacements(RegistryKey<World> worldKey, BlockPos center, double radius) {
        double rSq = radius * radius;
        for (DisplacedBlockRecord record : ACTIVE_RECORDS.values()) {
            if (record.worldKey.equals(worldKey)) {
                double dSq = record.pos.getSquaredDistance(center);
                if (dSq <= rSq) {
                    record.beginRestoration();
                }
            }
        }
    }
}
