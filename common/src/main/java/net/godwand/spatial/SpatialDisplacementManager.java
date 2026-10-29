package net.godwand.spatial;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.mojang.math.Transformation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

public class SpatialDisplacementManager {
    private static final Map<DimensionPos, DisplacedBlockRecord> ACTIVE_RECORDS = new ConcurrentHashMap<>();
    private static final Map<DimensionPos, DisplacedBlockRecord> BRIDGE_DESTINATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockDisplay> ACTIVE_ENTITIES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_PATH_OPEN_SOUND = new HashMap<>();
    private static final Map<UUID, Long> LAST_PATH_RETURN_SOUND = new HashMap<>();

    static boolean shouldPlayPathSound(UUID owner, long tick, boolean returning) {
        if (owner == null) return false;
        var times = returning ? LAST_PATH_RETURN_SOUND : LAST_PATH_OPEN_SOUND;
        Long previous = times.get(owner);
        if (previous != null && tick >= previous && tick - previous < (returning ? 8 : 6)) return false;
        times.put(owner, tick);
        return true;
    }

    public static boolean isPositionDisplaced(ResourceKey<Level> worldKey, BlockPos pos) {
        return ACTIVE_RECORDS.containsKey(new DimensionPos(worldKey, pos));
    }

    public static boolean isPositionDisplaced(BlockPos pos) {
        return isPositionDisplaced(Level.OVERWORLD, pos);
    }

    public static boolean canDisplace(ServerLevel world, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        BlockPos immutablePos = pos.immutable();
        if (state.getDestroySpeed(world, immutablePos) < 0) return false;
        if (state.is(Blocks.BEDROCK) || state.is(Blocks.END_PORTAL) || state.is(Blocks.BARRIER)) return false;
        if (!BRIDGE_DESTINATIONS.isEmpty() && BRIDGE_DESTINATIONS.containsKey(new DimensionPos(world.dimension(), immutablePos))) return false;
        return ACTIVE_RECORDS.isEmpty() || !isPositionDisplaced(world.dimension(), immutablePos);
    }

    public static void clearRuntimeState() {
        ACTIVE_RECORDS.clear(); ACTIVE_ENTITIES.clear(); BRIDGE_DESTINATIONS.clear();
        LAST_PATH_OPEN_SOUND.clear(); LAST_PATH_RETURN_SOUND.clear();
    }

    public static void recoverBridge(DisplacedBlockRecord record) {
        ACTIVE_RECORDS.put(new DimensionPos(record.worldKey, record.pos), record);
        if (record.bridgeDestination != null) BRIDGE_DESTINATIONS.put(new DimensionPos(record.worldKey, record.bridgeDestination), record);
    }

    public static void moveBridge(DisplacedBlockRecord record, BlockPos target) {
        if (record.bridgeDestination != null) BRIDGE_DESTINATIONS.remove(new DimensionPos(record.worldKey, record.bridgeDestination));
        record.bridgeDestination = target == null ? null : target.immutable();
        if (target != null) BRIDGE_DESTINATIONS.put(new DimensionPos(record.worldKey, target), record);
    }

    public static int getActiveRecordCount() {
        return ACTIVE_RECORDS.size();
    }

    public static int getActiveEntityCount() {
        return ACTIVE_ENTITIES.size();
    }

    public static DisplacedBlockRecord getActiveRecord(ResourceKey<Level> worldKey, BlockPos pos) {
        return ACTIVE_RECORDS.get(new DimensionPos(worldKey, pos));
    }

    public static BlockDisplay getActiveEntity(UUID uuid) {
        return uuid != null ? ACTIVE_ENTITIES.get(uuid) : null;
    }

    public static void trackEntity(UUID uuid, BlockDisplay entity) {
        if (uuid != null && entity != null) {
            ACTIVE_ENTITIES.put(uuid, entity);
        }
    }

    public static void untrackEntity(UUID uuid) {
        if (uuid != null) {
            ACTIVE_ENTITIES.remove(uuid);
        }
    }

    public static DisplacedBlockRecord removeActiveRecord(ResourceKey<Level> worldKey, BlockPos pos) {
        DisplacedBlockRecord rec = ACTIVE_RECORDS.remove(new DimensionPos(worldKey, pos));
        if (rec != null && rec.bridgeDestination != null) BRIDGE_DESTINATIONS.remove(new DimensionPos(rec.worldKey, rec.bridgeDestination));
        if (rec != null && rec.displayEntityUuid != null) {
            BlockDisplay ent = ACTIVE_ENTITIES.remove(rec.displayEntityUuid);
            if (ent != null && ent.isAlive()) {
                ent.discard();
            }
        }
        return rec;
    }

    public static List<DisplacedBlockRecord> getActiveRecordsForWorld(ResourceKey<Level> worldKey) {
        List<DisplacedBlockRecord> list = new ArrayList<>();
        for (DisplacedBlockRecord r : ACTIVE_RECORDS.values()) {
            if (r.worldKey.equals(worldKey) && !r.permanentDestruction) {
                list.add(r);
            }
        }
        return list;
    }

    public static void refreshDisplacement(ResourceKey<Level> worldKey, BlockPos pos, int lifetime) {
        DisplacedBlockRecord record = ACTIVE_RECORDS.get(new DimensionPos(worldKey, pos));
        if (record != null) {
            record.interruptRestoration();
            record.maxLifetimeTicks = lifetime;
        }
    }

    public static BlockPos getMatchingDoubleBlockHalf(ServerLevel world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            if (state.hasProperty(DoorBlock.HALF)) {
                DoubleBlockHalf half = state.getValue(DoorBlock.HALF);
                BlockPos target = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
                BlockState targetState = world.getBlockState(target);
                if (targetState.is(state.getBlock()) && targetState.hasProperty(DoorBlock.HALF) && targetState.getValue(DoorBlock.HALF) != half) {
                    return target;
                }
            }
        } else if (state.getBlock() instanceof DoublePlantBlock) {
            if (state.hasProperty(DoublePlantBlock.HALF)) {
                DoubleBlockHalf half = state.getValue(DoublePlantBlock.HALF);
                BlockPos target = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
                BlockState targetState = world.getBlockState(target);
                if (targetState.is(state.getBlock()) && targetState.hasProperty(DoublePlantBlock.HALF) && targetState.getValue(DoublePlantBlock.HALF) != half) {
                    return target;
                }
            }
        } else if (state.getBlock() instanceof BedBlock) {
            if (state.hasProperty(BedBlock.PART) && state.hasProperty(BedBlock.FACING)) {
                BedPart part = state.getValue(BedBlock.PART);
                Direction facing = state.getValue(BedBlock.FACING);
                BlockPos target = part == BedPart.FOOT ? pos.relative(facing) : pos.relative(facing.getOpposite());
                BlockState targetState = world.getBlockState(target);
                if (targetState.is(state.getBlock()) && targetState.hasProperty(BedBlock.PART) && targetState.getValue(BedBlock.PART) != part) {
                    return target;
                }
            }
        }
        return null;
    }

    public static void displaceBlock(ServerLevel world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, int lifetime, int glowColor) {
        displaceBlock(world, pos, targetDisplacement, rotation, new Vector3f(1.0f, 1.0f, 1.0f), lifetime, glowColor, true);
    }

    public static void displaceBlock(ServerLevel world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, int lifetime, int glowColor, boolean spawnDisplay) {
        displaceBlock(world, pos, targetDisplacement, rotation, new Vector3f(1.0f, 1.0f, 1.0f), lifetime, glowColor, spawnDisplay);
    }

    public static void displaceBlock(ServerLevel world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, Vector3f scale, int lifetime, int glowColor, boolean spawnDisplay) {
        displaceBlock(world, pos, targetDisplacement, rotation, scale, lifetime, glowColor, spawnDisplay,
                DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE, null, null, 0.0);
    }

    public static void displaceBlock(ServerLevel world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, Vector3f scale, int lifetime, int glowColor,
                                      boolean spawnDisplay, DisplacedBlockRecord.DisplacementMode mode,
                                      UUID boundPlayerUuid, Vec3 blastCenter, double blastRadius) {
        displaceBlockInternal(world, pos, targetDisplacement, rotation, scale, lifetime, glowColor,
                spawnDisplay, mode, boundPlayerUuid, blastCenter, blastRadius, true, false);
    }

    public static void displaceBlock(ServerLevel world, BlockPos pos, Vector3f targetDisplacement,
            Quaternionf rotation, Vector3f scale, int lifetime, int glowColor, boolean spawnDisplay,
            DisplacedBlockRecord.DisplacementMode mode, UUID boundPlayerUuid, Vec3 blastCenter, double blastRadius,
            boolean permanent) {
        displaceBlockInternal(world, pos, targetDisplacement, rotation, scale, lifetime, glowColor,
                spawnDisplay, mode, boundPlayerUuid, blastCenter, blastRadius, true, permanent);
    }

    public static void restorePositions(ServerLevel world, java.util.Collection<BlockPos> positions) {
        Set<DisplacedBlockRecord> records=new LinkedHashSet<>();
        for(BlockPos pos:positions) {
            var record=getActiveRecord(world.dimension(),pos);
            if(record==null||record.permanentDestruction)continue;
            records.add(record);
            BlockState state=record.originalState;
            BlockPos other=null;
            if(state.getBlock() instanceof DoorBlock||state.getBlock() instanceof DoublePlantBlock)
                other=state.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER?pos.below():pos.above();
            else if(state.getBlock() instanceof BedBlock)
                other=pos.relative(state.getValue(BedBlock.PART)==BedPart.FOOT?state.getValue(BedBlock.FACING):state.getValue(BedBlock.FACING).getOpposite());
            var pair=other==null?null:getActiveRecord(world.dimension(),other);
            if(pair!=null&&!pair.permanentDestruction&&pair.originalState.is(state.getBlock())
                    &&Objects.equals(pair.boundPlayerUuid,record.boundPlayerUuid))records.add(pair);
        }
        if(!records.isEmpty())executeTwoPassRestoration(world,new ArrayList<>(records));
    }

    public static void displaceBlockPermanently(ServerLevel world, BlockPos pos, Vector3f targetDisplacement,
            Quaternionf rotation, Vector3f scale, int lifetime, int glowColor, boolean spawnDisplay,
            DisplacedBlockRecord.DisplacementMode mode, UUID boundPlayerUuid, Vec3 blastCenter, double blastRadius) {
        displaceBlockInternal(world, pos, targetDisplacement, rotation, scale, lifetime, glowColor,
                spawnDisplay, mode, boundPlayerUuid, blastCenter, blastRadius, true, true);
    }

    private static void displaceBlockInternal(ServerLevel world, BlockPos pos, Vector3f targetDisplacement,
                                              Quaternionf rotation, Vector3f scale, int lifetime, int glowColor,
                                              boolean spawnDisplay, DisplacedBlockRecord.DisplacementMode mode,
                                              UUID boundPlayerUuid, Vec3 blastCenter, double blastRadius, boolean checkDoubleBlock, boolean permanent) {
        BlockPos immutablePos = pos.immutable();
        BlockState state = world.getBlockState(immutablePos);
        if (!canDisplace(world, immutablePos, state)) return;

        BlockPos otherHalf = checkDoubleBlock ? getMatchingDoubleBlockHalf(world, immutablePos, state) : null;

        // Skip display spawn for pure water or lava
        if (state.is(Blocks.WATER) || state.is(Blocks.LAVA)) {
            spawnDisplay = false;
        }

        // Detach before replacement: SKIP_DROPS does not suppress container callbacks.
        BlockEntity be = world.getBlockEntity(immutablePos);
        CompoundTag nbt = null;
        if (be != null) {
            nbt = be.saveWithFullMetadata(world.registryAccess());
            world.removeBlockEntity(immutablePos);
        }

        boolean isFull = state.isCollisionShapeFullBlock(world, immutablePos);
        DisplacedBlockRecord record = new DisplacedBlockRecord(
                immutablePos, world.dimension(), state, nbt, targetDisplacement, rotation, scale,
                lifetime, isFull, mode, boundPlayerUuid);
        record.permanentDestruction = permanent;
        record.blastCenter = blastCenter;
        record.blastRadius = blastRadius;

        // Replace with AIR using atomic notification flags
        if (!world.setBlock(immutablePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS)) {
            if (be != null) {
                be.clearRemoved();
                world.setBlockEntity(be);
            }
            return;
        }
        if (be != null) {
            Clearable.tryClear(be);
            for (ServerPlayer player : world.players()) {
                if (!player.containerMenu.stillValid(player)) player.closeContainer();
            }
        }
        ACTIVE_RECORDS.put(new DimensionPos(world.dimension(), immutablePos), record);
        DisplacementPersistentState.getServerState(world).setDirty();

        // Spawn at origin before applying the displacement animation.
        if (spawnDisplay) {
            BlockDisplay display = new BlockDisplay(EntityType.BLOCK_DISPLAY, world);
            display.addTag("godwand_displaced");
            display.setPos(immutablePos.getX(), immutablePos.getY(), immutablePos.getZ());
            display.setBlockState(state);

            // Triệt tiêu hoàn toàn glowing và không ép brightness override để giữ ánh sáng tự nhiên
            display.setGlowingTag(false);
            display.setGlowColorOverride(-1);

            display.setTransformationInterpolationDelay(0);
            display.setTransformationInterpolationDuration(0);
            display.setTransformation(Transformation.identity());

            world.addFreshEntity(display);
            record.displayEntityUuid = display.getUUID();
            ACTIVE_ENTITIES.put(display.getUUID(), display);

            if (mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER
                    && shouldPlayPathSound(boundPlayerUuid, world.getGameTime(), false)) {
                // Đã bỏ tiếng đập gõ block, nhường cho ambience vô hạn trong SpatialPathWalker
            }

            // Subtle break crumbs
            if (mode != DisplacedBlockRecord.DisplacementMode.PATH_WALKER && (immutablePos.asLong() & 3) == 0) {
                world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        immutablePos.getX() + 0.5, immutablePos.getY() + 0.5, immutablePos.getZ() + 0.5,
                        3, 0.15, 0.15, 0.15, 0.08);
                if (!permanent && (immutablePos.asLong() & 31) == 0) world.playSound(null, immutablePos, SoundEvents.BASALT_BREAK, SoundSource.BLOCKS, 0.6f, 0.85f + 0.25f * (float)Math.random());
            }
        }

        // Displace matching half of double block simultaneously
        if (otherHalf != null && canDisplace(world, otherHalf, world.getBlockState(otherHalf))) {
            displaceBlockInternal(world, otherHalf, targetDisplacement, rotation, scale, lifetime,
                    glowColor, spawnDisplay, mode, boundPlayerUuid, blastCenter, blastRadius, false, permanent);
        }
    }

    public static void tick(ServerLevel world) {
        List<DisplacedBlockRecord> toRemove = new ArrayList<>();

        for (DisplacedBlockRecord record : ACTIVE_RECORDS.values()) {
            if (!record.worldKey.equals(world.dimension())) {
                continue;
            }

            if (record.bridgeDestination != null) continue;
            record.ageTicks++;
            BlockDisplay display = record.displayEntityUuid != null ? ACTIVE_ENTITIES.get(record.displayEntityUuid) : null;

            // ==========================================
            // 1. PHA THU HỒI 3 BƯỚC (3-PHASE MAGNETIC SNAP REASSEMBLY)
            // ==========================================
            if (record.permanentDestruction && record.ageTicks >= record.maxLifetimeTicks + 20) {
                removeActiveRecord(record.worldKey, record.pos);
                continue;
            }

            if (record.isRestoring()) {
                // Nếu là PATH_WALKER mà người chơi bước lại vào vùng <= 4.2m trong lúc đang đóng -> Hất ngược ra vòm Aura ngay lập tức!
                if (record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER ||
                    record.mode == DisplacedBlockRecord.DisplacementMode.HYSTERESIS_AURA) {
                    Vec3 blockCenter = Vec3.atCenterOf(record.pos);
                    for (ServerPlayer p : world.players()) {
                        if (net.godwand.skill.SpatialPathWalker.isActive(p)) {
                            Vec3 lv = p.getViewVector(1.0f);
                            Vec3 lh = new Vec3(lv.x, 0, lv.z);
                            lh = lh.lengthSqr() < 0.001 ? new Vec3(1, 0, 0) : lh.normalize();
                            double lead = net.godwand.skill.SpatialPathWalker.getPlayerForwardLead(p);
                            double holdingLead = Math.max(lead, 5.5);
                            double spineDist = SpatialMath.distanceToPathAura(blockCenter, p.position().add(0, 1.1, 0), lh, holdingLead);
                            if (spineDist <= 4.2) {
                                record.interruptRestoration();
                                if (display != null && display.isAlive()) {
                                    Vec3 rebound = SpatialMath.computeDynamicAuraOffset(record.pos, p.position(), lh, 2.1, record.ageTicks);
                                    record.currentOffset.set((float) rebound.x, (float) rebound.y, (float) rebound.z);
                                    Quaternionf reboundRot = SpatialMath.computeArchwayRotation(record.pos, p.position(), lh, record.ageTicks);
                                    record.currentRotation.set(reboundRot);
                                    display.setTransformationInterpolationDelay(0);
                                    display.setTransformationInterpolationDuration(5);
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
                            Vec3 spiralPreSnap = SpatialMath.computeSpiralReturnOffset(record.getCurrentOffset(), record.pos);
                            record.currentOffset.set((float) spiralPreSnap.x, (float) spiralPreSnap.y, (float) spiralPreSnap.z);
                            Quaternionf nearZeroRot = new Quaternionf().slerp(record.currentRotation, 0.18f);
                            record.currentRotation.set(nearZeroRot);

                            display.setTransformationInterpolationDelay(0);
                            display.setTransformationInterpolationDuration(9);
                            display.setTransformation(SpatialMath.createPivotTransform(spiralPreSnap, nearZeroRot, 0.96f));
                        }
                        // Pha 2 (restoreTick == 10): Khóa khớp từ tính (Magnetic Snap) về đúng (0,0,0) trong 4 ticks
                        else if (record.restoreTick == 10) {
                            record.currentOffset.set(0, 0, 0);
                            display.setTransformationInterpolationDelay(0);
                            display.setTransformationInterpolationDuration(4);
                            display.setTransformation(Transformation.identity());
                            if (record.mode != DisplacedBlockRecord.DisplacementMode.PATH_WALKER && (record.pos.asLong() & 31) == 0) {
                                world.playSound(null, record.pos, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 0.25f, 1.35f);
                            }
                        }
                    }

                    // Pha 3 - Pass 1 (restoreTick == 14): Khôi phục khối đặc đúng 1:1
                    if (record.restoreTick == 14 && record.originalState.isCollisionShapeFullBlock(world, record.pos)) {
                        if (restoreRecordDirectly(world, record, display)) toRemove.add(record);
                    }
                    // Pha 3 - Pass 2 (restoreTick >= 15): Khôi phục khối đính kèm (Đuốc, Đèn lồng, Hoa...)
                    else if (record.restoreTick >= 15) {
                        if (restoreRecordDirectly(world, record, display)) toRemove.add(record);
                    }

                    record.restoreTick++;
                    continue;
                }
            }

            // ==========================================
            // 1.5. CÁCH LY HOÀN TOÀN CUSTOM_SCRIPTED (Chiêu 2 / Chiêu 3 / Chiêu 4)
            // ==========================================
            if (record.mode == DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED) {
                if (record.ageTicks >= record.maxLifetimeTicks + 20) {
                    record.beginRestoration();
                }
                continue;
            }

            // ==========================================
            // 2. VÒNG ĐỜI SPAWN 2-TICK & BẢO VỆ MỞ ĐƯỜNG ĐẦU TIÊN
            // ==========================================
            if (record.spawnTickDelay > 0) {
                record.spawnTickDelay--;
                if (record.spawnTickDelay == 0 && display != null && display.isAlive()) {
                    display.setTransformationInterpolationDelay(0);
                    int openDuration = 8;
                    if (record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER) {
                        double playerSpeed = 0.0;
                        double distToPlayer = 3.0;
                        if (record.boundPlayerUuid != null) {
                            ServerPlayer p = world.getServer().getPlayerList().getPlayer(record.boundPlayerUuid);
                            if (p != null) {
                                playerSpeed = net.godwand.skill.SpatialPathWalker.getPlayerSpeed(p);
                                distToPlayer = Vec3.atCenterOf(record.pos).distanceTo(p.position());
                            }
                        }
                        if (playerSpeed >= 0.15) {
                            // Chạy nhanh / Sprint / Bay: mở cực nhanh (5 ticks)
                            openDuration = 5;
                        } else {
                            // Đi bộ chậm hoặc đứng yên ("farming aura"):
                            if (distToPlayer <= 2.0) {
                                openDuration = 5;
                            } else {
                                // Khối ở xa phía trước (2m - 4m): từ từ, chậm rãi và uy quyền (6 - 9 ticks)
                                float tDist = (float) Mth.clamp((distToPlayer - 2.0) / 2.0, 0.0, 1.0);
                                openDuration = (int) Mth.lerp(tDist, 6.0f, 9.0f);
                            }
                        }
                    }
                    record.openDurationTicks = openDuration;
                    display.setTransformationInterpolationDuration(openDuration);
                    display.setTransformation(SpatialMath.createPivotTransform(
                            record.getCurrentOffset(), record.currentRotation, record.targetScale.x));

                    record.animationStage = 1;
                    record.transformed = true;
                }
                continue;
            }

            // Keep the opening target responsive while the player moves.
            int minHoldTicks = record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER ? 3 : 9;
            if (record.ageTicks < minHoldTicks) {
                continue;
            }

            // ==========================================
            // 3. CHẾ ĐỘ CHIÊU 1: LIVING AURA + SPRING LERP + INTERACTIVE EVASION
            // ==========================================
            if (record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER ||
                record.mode == DisplacedBlockRecord.DisplacementMode.HYSTERESIS_AURA) {

                Vec3 blockCenter = Vec3.atCenterOf(record.pos);
                ServerPlayer activeOwner = null;
                double minSpineDist = Double.MAX_VALUE;

                for (ServerPlayer p : world.players()) {
                    if (net.godwand.skill.SpatialPathWalker.isActive(p)) {
                        Vec3 lv = p.getViewVector(1.0f);
                        Vec3 lh = new Vec3(lv.x, 0, lv.z);
                        lh = lh.lengthSqr() < 0.001 ? new Vec3(1, 0, 0) : lh.normalize();
                        double lead = net.godwand.skill.SpatialPathWalker.getPlayerForwardLead(p);
                        double holdingLead = Math.max(lead, 5.5);
                        double d = SpatialMath.distanceToPathAura(blockCenter, p.position().add(0, 1.1, 0), lh, holdingLead);
                        if (d < minSpineDist) {
                            minSpineDist = d;
                            activeOwner = p;
                        }
                    }
                }

                // Bán kính quét quanh trục là 3.3m, ngưỡng giữ mở quanh trục là 4.2m với holdingLead 5.5m -> Giữ khối phía trước luôn mở lơ lửng khi đứng yên
                if (activeOwner != null && minSpineDist <= 4.2) {
                    record.resetRestoreTick();

                    if (display != null && display.isAlive() && ((record.ageTicks + Math.abs(record.pos.hashCode())) % 3 == 0)) {
                        Vec3 lookVec = activeOwner.getViewVector(1.0f);
                        Vec3 lookHoriz = new Vec3(lookVec.x, 0, lookVec.z);
                        lookHoriz = lookHoriz.lengthSqr() < 0.001 ? new Vec3(1, 0, 0) : lookHoriz.normalize();

                        // Tính vị trí đích trên vòm Aura
                        Vec3 desiredAuraOffset = SpatialMath.computeDynamicAuraOffset(
                                record.pos, activeOwner.position(), lookHoriz, 2.1, record.ageTicks);

                        // Kiểm tra né tương tác dựa trên VỊ TRÍ THỰC TẾ ĐANG BAY của mảnh vỡ
                        Vec3 actualDebrisWorldPos = blockCenter.add(record.getCurrentOffset());
                        Player closestToDebris = world.getNearestPlayer(actualDebrisWorldPos.x, actualDebrisWorldPos.y, actualDebrisWorldPos.z, 3.8, false);
                        if (closestToDebris != null) {
                            Vec3 evasion = SpatialMath.computeInteractiveEvasion(
                                    actualDebrisWorldPos, closestToDebris.position().add(0, 1.1, 0), 3.4);
                            if (evasion.lengthSqr() > 0.001) {
                                desiredAuraOffset = desiredAuraOffset.add(evasion);
                                record.hoverYaw += 0.38f;
                            }
                        }

                        // Quán tính lò xo (Spring Damping Lerp 0.42) giúp mảnh vỡ lướt mượt không bị giật khi xoay chuột nhanh
                        Vec3 smoothedOffset = record.getCurrentOffset().lerp(desiredAuraOffset, 0.42);
                        record.currentOffset.set((float) smoothedOffset.x, (float) smoothedOffset.y, (float) smoothedOffset.z);

                        record.hoverYaw += 0.09f;
                        Quaternionf rot = SpatialMath.computeArchwayRotation(record.pos, activeOwner.position(), lookHoriz, record.ageTicks);
                        record.currentRotation.set(rot);

                        display.setTransformationInterpolationDelay(0);
                        display.setTransformationInterpolationDuration(3);
                        display.setTransformation(SpatialMath.createPivotTransform(smoothedOffset, rot, record.targetScale.x));

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
                    Vec3 blockCenter = Vec3.atCenterOf(record.pos);
                    Vec3 vortexOffset = SpatialMath.computeCanopyVortexOffset(record.pos, record.blastCenter, record.blastRadius, record.ageTicks);

                    // Nếu người chơi bay/đi lại gần mảnh thiên thạch của Chiêu 2, mảnh thiên thạch cũng tự né người chơi!
                    Vec3 actualDebrisWorldPos = blockCenter.add(record.getCurrentOffset());
                    Player closestPlayer = world.getNearestPlayer(actualDebrisWorldPos.x, actualDebrisWorldPos.y, actualDebrisWorldPos.z, 3.8, false);
                    if (closestPlayer != null) {
                        Vec3 evasion = SpatialMath.computeInteractiveEvasion(
                                actualDebrisWorldPos, closestPlayer.position().add(0, 1.1, 0), 3.4);
                        if (evasion.lengthSqr() > 0.001) {
                            vortexOffset = vortexOffset.add(evasion);
                            record.hoverYaw += 0.35f;
                        }
                    }

                    Vec3 smoothedVortex = record.getCurrentOffset().lerp(vortexOffset, 0.45);
                    record.currentOffset.set((float) smoothedVortex.x, (float) smoothedVortex.y, (float) smoothedVortex.z);
                    record.hoverYaw += 0.07f;
                    Quaternionf rot = new Quaternionf(record.targetRotation).rotateY(record.hoverYaw);
                    record.currentRotation.set(rot);

                    display.setTransformationInterpolationDelay(0);
                    display.setTransformationInterpolationDuration(3);
                    display.setTransformation(SpatialMath.createPivotTransform(smoothedVortex, rot, record.targetScale.x));
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

                    display.setTransformationInterpolationDelay(0);
                    display.setTransformationInterpolationDuration(6);
                    display.setTransformation(SpatialMath.createPivotTransform(hoverDisp, hoverRot, record.targetScale.x));
                }
            }
        }

        for (DisplacedBlockRecord r : toRemove) {
            ACTIVE_RECORDS.remove(new DimensionPos(r.worldKey, r.pos));
            DisplacementPersistentState.getServerState(world).setDirty();
        }
    }

    private static boolean restoreRecordDirectly(ServerLevel world, DisplacedBlockRecord r, BlockDisplay display) {
        // Keep the original journal until a newly placed block/container has been moved out of the way.
        BlockState current=world.getBlockState(r.pos);
        if(!r.permanentDestruction&&!current.isAir()&&(!current.equals(r.originalState)||r.blockEntityNbt!=null))return false;
        if (r.bridgeDestination != null) {
            // Keep the journal until both ends can be reconciled without loading chunks.
            if (!world.getChunkSource().hasChunk(r.bridgeDestination.getX() >> 4, r.bridgeDestination.getZ() >> 4)
                    || !world.getChunkSource().hasChunk(r.pos.getX() >> 4, r.pos.getZ() >> 4)) return false;
            if (!world.getBlockState(r.bridgeDestination).equals(r.originalState)) {
                removeActiveRecord(r.worldKey, r.pos);
                return true;
            }
            if (!world.removeBlock(r.bridgeDestination, false)) return false;
            BRIDGE_DESTINATIONS.remove(new DimensionPos(r.worldKey, r.bridgeDestination));
            r.bridgeDestination = null;
        }
        if (r.permanentDestruction) {
            removeActiveRecord(r.worldKey, r.pos);
            return true;
        }
        // Exact 1:1 original BlockState and NBT restoration
        if (!world.setBlock(r.pos, r.originalState, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS)
                && !world.getBlockState(r.pos).equals(r.originalState)) return false;
        if (r.blockEntityNbt != null) {
            BlockEntity be = world.getBlockEntity(r.pos);
            if (be != null) {
                be.loadWithComponents(r.blockEntityNbt, world.registryAccess());
                be.setChanged();
            }
        }
        BlockDisplay toDiscard = display;
        if (toDiscard == null && r.displayEntityUuid != null) {
            toDiscard = ACTIVE_ENTITIES.remove(r.displayEntityUuid);
        }
        if (toDiscard != null) {
            ACTIVE_ENTITIES.remove(r.displayEntityUuid);
            toDiscard.discard();
        }
        // Crisp stone impact particle and sound
        if (r.mode != DisplacedBlockRecord.DisplacementMode.PATH_WALKER) world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, r.originalState),
                r.pos.getX() + 0.5, r.pos.getY() + 0.5, r.pos.getZ() + 0.5, 4, 0.15, 0.15, 0.15, 0.05);
        if (r.mode != DisplacedBlockRecord.DisplacementMode.PATH_WALKER && (r.pos.asLong() & 31) == 0) {
            world.playSound(null, r.pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.3f, 1.0f);
        } else if (r.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER) {
            shouldPlayPathSound(r.boundPlayerUuid, world.getGameTime(), true);
        }
        return true;
    }

    public static void collapsePlayerAura(UUID playerUuid) {
        for (DisplacedBlockRecord record : ACTIVE_RECORDS.values()) {
            if (record.boundPlayerUuid != null && record.boundPlayerUuid.equals(playerUuid)) {
                record.beginRestoration();
            }
        }
    }

    public static void restoreAllForPlayer(UUID playerUuid, ServerLevel preferredLevel) {
        if (playerUuid == null) return;
        List<DisplacedBlockRecord> toRestore = new ArrayList<>();
        for (DisplacedBlockRecord r : ACTIVE_RECORDS.values()) {
            if (playerUuid.equals(r.boundPlayerUuid)) {
                if (r.permanentDestruction) {
                    removeActiveRecord(r.worldKey, r.pos);
                } else {
                    toRestore.add(r);
                }
            }
        }
        if (toRestore.isEmpty()) return;

        Map<ResourceKey<Level>, List<DisplacedBlockRecord>> byWorld = new HashMap<>();
        for (DisplacedBlockRecord r : toRestore) {
            byWorld.computeIfAbsent(r.worldKey, k -> new ArrayList<>()).add(r);
        }

        var server = preferredLevel != null ? preferredLevel.getServer() : null;
        for (var entry : byWorld.entrySet()) {
            ServerLevel targetLevel = preferredLevel;
            if (server != null && (targetLevel == null || !targetLevel.dimension().equals(entry.getKey()))) {
                targetLevel = server.getLevel(entry.getKey());
            }
            if (targetLevel != null) {
                executeTwoPassRestoration(targetLevel, entry.getValue());
            }
        }
    }

    public static void emergencyRestoreAll(ServerLevel world) {
        List<DisplacedBlockRecord> matching = new ArrayList<>();
        for (DisplacedBlockRecord r : ACTIVE_RECORDS.values()) {
            if (r.worldKey.equals(world.dimension())) {
                if (r.permanentDestruction) {
                    ACTIVE_RECORDS.remove(new DimensionPos(r.worldKey, r.pos));
                    if (r.displayEntityUuid != null) {
                        BlockDisplay ent = ACTIVE_ENTITIES.remove(r.displayEntityUuid);
                        if (ent != null && ent.isAlive()) {
                            ent.discard();
                        }
                    }
                } else {
                    matching.add(r);
                }
            }
        }
        executeTwoPassRestoration(world, matching);
    }

    public static void executeTwoPassRestoration(ServerLevel world, List<DisplacedBlockRecord> records) {
        // Pass 1: Solid base blocks
        for (DisplacedBlockRecord r : records) {
            if (r.isFullCube) {
                BlockDisplay d = r.displayEntityUuid != null ? ACTIVE_ENTITIES.get(r.displayEntityUuid) : null;
                if (restoreRecordDirectly(world, r, d)) ACTIVE_RECORDS.remove(new DimensionPos(r.worldKey, r.pos));
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
            BlockDisplay d = r.displayEntityUuid != null ? ACTIVE_ENTITIES.get(r.displayEntityUuid) : null;
            if (restoreRecordDirectly(world, r, d)) ACTIVE_RECORDS.remove(new DimensionPos(r.worldKey, r.pos));
        }
        DisplacementPersistentState.getServerState(world).setDirty();
    }

    public static void collapseNearbyDisplacements(ResourceKey<Level> worldKey, BlockPos center, double radius) {
        double rSq = radius * radius;
        for (DisplacedBlockRecord record : ACTIVE_RECORDS.values()) {
            if (record.worldKey.equals(worldKey)) {
                double dSq = record.pos.distSqr(center);
                if (dSq <= rSq) {
                    record.beginRestoration();
                }
            }
        }
    }
}
