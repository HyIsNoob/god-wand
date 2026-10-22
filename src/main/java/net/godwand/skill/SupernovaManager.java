package net.godwand.skill;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.spatial.DisplacedBlockRecord;
import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
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
        public final BlockPos groundCenter;
        public final Vec3d corePos;
        public final double maxRadius = 16.0;

        public int ageTicks = 0;
        public final int maxLifetimeTicks = 260;
        public float swirlAngle = 0.0f;
        public boolean exploded = false;
        public boolean rewindTriggered = false;
        public boolean restored = false;

        public final List<BlockPos> displayedPositions = new ArrayList<>();
        public final List<BlockPos> interiorPositions = new ArrayList<>();
        public final Map<BlockPos, Vec3d> stage1UpliftMap = new HashMap<>();
        public final Map<BlockPos, Quaternionf> baseRotationMap = new HashMap<>();
        public final Map<BlockPos, Float> blockScaleMap = new HashMap<>();

        public SupernovaTask(ServerWorld world, ServerPlayerEntity caster, BlockPos groundCenter, Vec3d corePos) {
            this.world = world;
            this.caster = caster;
            this.groundCenter = groundCenter.toImmutable();
            this.corePos = corePos;
        }
    }

    private static final Queue<SupernovaTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();

    public static void cast(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        HitResult hit = player.raycast(36.0, 0.0f, false);
        BlockPos groundCenter;
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bHit) {
            groundCenter = bHit.getBlockPos();
        } else {
            Vec3d forward = player.getEyePos().add(player.getRotationVec(1.0f).multiply(20.0));
            groundCenter = BlockPos.ofFloored(forward);
            while (groundCenter.getY() > world.getBottomY() && world.getBlockState(groundCenter).isAir()) {
                groundCenter = groundCenter.down();
            }
        }

        Vec3d corePos = groundCenter.toCenterPos().add(0, 9.5, 0);
        SupernovaTask task = new SupernovaTask(world, player, groundCenter, corePos);

        // 1 & 2. 3D Raymarched Black Hole & Relativistic Pulsar Jets Shader
        // Kích hoạt Shader Hố Đen 3D & Khúc xạ ánh sáng hấp dẫn (Einstein Ring + Accretion Disk + Pulsar Jets)
        ImpactFxPayload blackHolePayload = new ImpactFxPayload(
                corePos.x, corePos.y, corePos.z,
                ImpactFxPayload.TYPE_BLACK_HOLE, 186, 1.0f
        );
        for (ServerPlayerEntity p : PlayerLookup.around(world, corePos, 128.0)) {
            ServerPlayNetworking.send(p, blackHolePayload);
        }

        // 3. Quét hình cầu bán kính R = 16.0 quanh groundCenter, lấy tối đa 400 khối
        int intR = (int) Math.ceil(task.maxRadius);
        double maxRadiusSq = task.maxRadius * task.maxRadius;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        List<BlockPos> candidatePositions = new ArrayList<>();
        for (int x = -intR; x <= intR; x++) {
            for (int y = -intR; y <= intR; y++) {
                int targetY = groundCenter.getY() + y;
                if (targetY < world.getBottomY() || targetY >= world.getTopY()) continue;
                for (int z = -intR; z <= intR; z++) {
                    double dSq = x * x + y * y + z * z;
                    if (dSq > maxRadiusSq) continue;

                    mutable.set(groundCenter.getX() + x, targetY, groundCenter.getZ() + z);
                    BlockState state = world.getBlockState(mutable);
                    if (!SpatialDisplacementManager.canDisplace(world, mutable, state)) continue;
                    candidatePositions.add(mutable.toImmutable());
                }
            }
        }

        // Trộn ngẫu nhiên trước khi chia tầng bán kính 2.5m để 400 khối phân bổ đều 360 độ
        Collections.shuffle(candidatePositions, new Random(groundCenter.asLong()));
        candidatePositions.sort((a, b) -> {
            int bandA = (int) (Math.sqrt(a.getSquaredDistance(task.groundCenter)) / 2.5);
            int bandB = (int) (Math.sqrt(b.getSquaredDistance(task.groundCenter)) / 2.5);
            return Integer.compare(bandB, bandA);
        });

        int maxDisplays = Math.min(400, candidatePositions.size());
        for (int i = 0; i < candidatePositions.size(); i++) {
            BlockPos pos = candidatePositions.get(i);
            boolean isDisplay = (i < maxDisplays);

            if (isDisplay) {
                float hash = (float) SpatialMath.hash01(pos);
                float liftY = 0.4f + hash * 2.4f; // Nâng chậm 0.4m -> 2.8m ở Giai đoạn 1
                Vec3d stage1Lift = new Vec3d((hash - 0.5) * 0.4, liftY, (SpatialMath.hash01(pos.up()) - 0.5) * 0.4);

                Quaternionf rot = new Quaternionf()
                        .rotateY((float) (hash * Math.PI * 2.0))
                        .rotateX((float) Math.toRadians(20.0 + hash * 25.0));
                float scale = 0.65f + hash * 0.42f;

                SpatialDisplacementManager.displaceBlock(
                        world,
                        pos,
                        new Vector3f(0, 0, 0),
                        rot,
                        new Vector3f(scale, scale, scale),
                        265,
                        0x00F5FF,
                        true,
                        DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
                        player.getUuid(),
                        null,
                        0.0
                );

                task.displayedPositions.add(pos);
                task.stage1UpliftMap.put(pos, stage1Lift);
                task.baseRotationMap.put(pos, rot);
                task.blockScaleMap.put(pos, scale);
            } else {
                SpatialDisplacementManager.displaceBlock(
                        world,
                        pos,
                        new Vector3f(0, 0, 0),
                        new Quaternionf(),
                        new Vector3f(1.0f, 1.0f, 1.0f),
                        265,
                        0,
                        false,
                        DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE,
                        null,
                        null,
                        0.0
                );
                task.interiorPositions.add(pos);
            }
        }

        // Âm thanh khởi sinh hố đen vũ trụ
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 3.5f, 0.5f);
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 3.0f, 0.7f);
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 3.0f, 0.6f);

        world.spawnParticles(ParticleTypes.SONIC_BOOM, corePos.x, corePos.y, corePos.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.FLASH, corePos.x, corePos.y, corePos.z, 2, 0.1, 0.1, 0.1, 0.0);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, corePos.x, corePos.y, corePos.z, 50, 1.5, 1.5, 1.5, 0.35);

        ACTIVE_TASKS.add(task);
    }

    public static void tick(ServerWorld world) {
        Iterator<SupernovaTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            SupernovaTask task = it.next();
            if (task.world != world) continue;

            task.ageTicks++;

            // =========================================================================
            // GIAI ĐOẠN 1: RUNG CHẤN & TÁCH KHỐI TỪ TỪ (Ticks 1..50 / 0s - 2.5s)
            // =========================================================================
            if (task.ageTicks <= 50) {
                if (task.ageTicks % 2 == 0) {
                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        double distRatio = Math.min(1.0, Math.sqrt(pos.getSquaredDistance(task.groundCenter)) / task.maxRadius);
                        double localProgress = Math.max(0.0, Math.min(1.0, (task.ageTicks / 50.0 - distRatio * 0.45) / 0.55));
                        float u = (float) (localProgress * localProgress);

                        Vec3d lift = task.stage1UpliftMap.getOrDefault(pos, Vec3d.ZERO).multiply(u);
                        Quaternionf rot = task.baseRotationMap.getOrDefault(pos, new Quaternionf());
                        float scale = task.blockScaleMap.getOrDefault(pos, 1.0f);

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(2);
                        display.setTransformation(SpatialMath.createPivotTransform(lift, rot, scale));
                    }
                }

                if (task.ageTicks % 15 == 0) {
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 2.5f, 0.7f + (task.ageTicks / 100.0f));
                }
            }

            // =========================================================================
            // GIAI ĐOẠN 2: ĐI VÀO 3 VÀNH ĐAI & TĂNG TỐC CỰC HẠN (Ticks 51..150 / 2.5s - 7.5s)
            // =========================================================================
            else if (task.ageTicks <= 150) {
                float accelProgress = (task.ageTicks - 50) / 100.0f; // 0.0 -> 1.0
                float currentAngularSpeed = 0.008f + (accelProgress * accelProgress * 0.24f); // Tăng tốc cấp số nhân
                task.swirlAngle += currentAngularSpeed;

                float lerpProgress = Math.min(1.0f, (task.ageTicks - 50) / 35.0f); // Ticks 51..85 nhập mượt vào quỹ đạo
                float beamThick = 0.35f + (accelProgress * 0.90f); // Phình to lên 1.25m!

                if ((task.ageTicks - 50) % 2 == 0) {
                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        Vec3d ringOffset = SpatialMath.computeCanopyVortexOffset(pos, task.corePos, task.maxRadius, task.swirlAngle, 1.0);
                        Vec3d finalOffset;
                        if (lerpProgress < 1.0f) {
                            Vec3d stage1Lift = task.stage1UpliftMap.getOrDefault(pos, Vec3d.ZERO);
                            finalOffset = stage1Lift.lerp(ringOffset, lerpProgress);
                        } else {
                            finalOffset = ringOffset;
                        }

                        Quaternionf rot = new Quaternionf(task.baseRotationMap.getOrDefault(pos, new Quaternionf()))
                                .rotateY(task.swirlAngle * 0.8f);
                        float scale = task.blockScaleMap.getOrDefault(pos, 1.0f);

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(2);
                        display.setTransformation(SpatialMath.createPivotTransform(finalOffset, rot, scale));
                    }
                }

                // Hút các thực thể xung quanh vào đĩa bồi tụ
                if (task.ageTicks % 6 == 0) {
                    Box suctionBox = new Box(task.groundCenter).expand(task.maxRadius * 1.35);
                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, suctionBox, ent -> ent != task.caster)) {
                        Vec3d pull = task.corePos.subtract(e.getPos()).normalize().multiply(0.24);
                        e.addVelocity(pull.x, pull.y, pull.z);
                        e.velocityModified = true;
                    }
                }
            }

            // =========================================================================
            // GIAI ĐOẠN 3: SỤP ĐỔ HẤP DẪN VÀO TÂM LỖ ĐEN (Ticks 151..185 / 7.5s - 9.25s)
            // =========================================================================
            else if (task.ageTicks <= 185) {
                if (task.ageTicks == 151) {
                    CinematicFxPayload compressCinematic = new CinematicFxPayload(
                            CinematicFxPayload.EVENT_SUPERNOVA_COMPRESS,
                            task.corePos.x, task.corePos.y, task.corePos.z,
                            0.65f, 35
                    );
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 128.0)) {
                        ServerPlayNetworking.send(p, compressCinematic);
                    }
                }

                if (task.ageTicks == 176) {
                    CinematicFxPayload silencePayload = new CinematicFxPayload(
                            CinematicFxPayload.EVENT_VACUUM_SILENCE,
                            task.corePos.x, task.corePos.y, task.corePos.z,
                            1.0f, 10
                    );
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 128.0)) {
                        ServerPlayNetworking.send(p, silencePayload);
                    }
                }

                float shrinkProgress = (task.ageTicks - 150) / 35.0f;
                float radiusScale = 1.0f - (shrinkProgress * 0.88f); // Bán kính co rút mạnh từ 1.0 về 0.12!
                task.swirlAngle += 0.25f;

                if ((task.ageTicks - 150) % 2 == 0) {
                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        Vec3d ringOffset = SpatialMath.computeCanopyVortexOffset(pos, task.corePos, task.maxRadius, task.swirlAngle, radiusScale);
                        Quaternionf rot = new Quaternionf(task.baseRotationMap.getOrDefault(pos, new Quaternionf()))
                                .rotateY(task.swirlAngle);
                        float scale = task.blockScaleMap.getOrDefault(pos, 1.0f);

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(2);
                        display.setTransformation(SpatialMath.createPivotTransform(ringOffset, rot, scale));
                    }
                }

                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        task.corePos.x, task.corePos.y, task.corePos.z, 14, 1.2, 1.2, 1.2, 0.3);

                // Im lặng chân không tuyệt đối trong 10 ticks trước đại nổ (Ticks 174..185)
                if (task.ageTicks < 174 && task.ageTicks % 8 == 0) {
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 3.5f, 1.8f);
                }
            }

            // =========================================================================
            // GIAI ĐOẠN 4: ĐẠI VỤ NỔ SIÊU TÂN TINH & NGHỊCH ĐẢO THỜI GIAN (Ticks 186..260)
            // =========================================================================
            else {
                // Tại Tick 186: ĐẠI VỤ NỔ SIÊU TÂN TINH
                if (task.ageTicks == 186 && !task.exploded) {
                    task.exploded = true;

                    // Kích hoạt Shader Siêu Tân Tinh & Đại Rung Chấn Điện Ảnh
                    ImpactFxPayload novaPayload = new ImpactFxPayload(
                            task.corePos.x, task.corePos.y, task.corePos.z,
                            ImpactFxPayload.TYPE_SUPERNOVA, 28, 2.2f
                    );
                    CinematicFxPayload burstCinematic = new CinematicFxPayload(
                            CinematicFxPayload.EVENT_SUPERNOVA_BURST,
                            task.corePos.x, task.corePos.y, task.corePos.z,
                            1.0f, 35
                    );
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 128.0)) {
                        ServerPlayNetworking.send(p, novaPayload);
                        ServerPlayNetworking.send(p, burstCinematic);
                    }

                    // Âm thanh tận thế
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

                    world.spawnParticles(ParticleTypes.FLASH, task.corePos.x, task.corePos.y, task.corePos.z, 10, 0.8, 0.8, 0.8, 0.0);
                    world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, task.corePos.x, task.corePos.y, task.corePos.z, 6, 1.5, 1.5, 1.5, 0.0);
                    world.spawnParticles(ParticleTypes.SONIC_BOOM, task.corePos.x, task.corePos.y, task.corePos.z, 5, 0.8, 0.8, 0.8, 0.0);
                    world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.85f, 0.25f), 2.2f),
                            task.corePos.x, task.corePos.y, task.corePos.z, 120, 4.0, 4.0, 4.0, 0.6);

                    // Phóng 400 khối nổ tung ra bán kính 42m trong không gian 3D
                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        Vec3d blockWorldCenter = Vec3d.ofCenter(pos);
                        Vec3d diff = blockWorldCenter.subtract(task.corePos);
                        if (diff.lengthSquared() < 0.01) diff = new Vec3d(0, 1, 0);
                        Vec3d blastDir = diff.normalize();

                        double blastDist = 28.0 + SpatialMath.hash01(pos) * 16.0; // 28m -> 44m
                        Vec3d blastWorldPos = task.corePos.add(blastDir.multiply(blastDist));
                        Vec3d blastDisp = blastWorldPos.subtract(blockWorldCenter);

                        Quaternionf tumble = new Quaternionf().rotateAxis(
                                (float) Math.toRadians(75), (float) blastDir.z, (float) blastDir.x, (float) blastDir.y);

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(10); // Phóng bung ra trong 10 ticks
                        display.setTransformation(SpatialMath.createPivotTransform(blastDisp, tumble, 1.25f));
                    }

                    // Sát thương khủng khiếp và lực đẩy
                    Box blastBox = new Box(task.groundCenter).expand(32.0);
                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, blastBox, ent -> ent != task.caster)) {
                        double dist = e.getPos().distanceTo(task.corePos);
                        float dmg = (float) Math.max(25.0, (1.0 - (dist / 32.0)) * 75.0 + 15.0);
                        e.damage(world.getDamageSources().explosion(null, task.caster), dmg);

                        Vec3d push = e.getPos().subtract(task.corePos).normalize().multiply(2.5).add(0, 0.95, 0);
                        e.addVelocity(push.x, push.y, push.z);
                        e.velocityModified = true;
                    }
                }

                // Ticks 196..220: Mảnh vỡ trôi lơ lửng trong không gian hậu vụ nổ
                if (task.ageTicks >= 196 && task.ageTicks < 221) {
                    if (task.ageTicks % 6 == 0) {
                        world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 2.5f, 1.2f);
                        world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                                SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 2.0f, 0.9f);
                    }
                }

                // Tại Tick 221: TUA NGƯỢC THỜI GIAN (Chrono Reversal)
                if (task.ageTicks >= 221 && !task.rewindTriggered) {
                    task.rewindTriggered = true;

                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 4.0f, 1.35f);
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 3.5f, 1.6f);

                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(22); // Bay lùi mượt mà về lại 0,0,0 trong 22 ticks
                        display.setTransformation(SpatialMath.createPivotTransform(Vec3d.ZERO, new Quaternionf(), 1.0f));
                    }
                }

                // Tại Tick 246+: Khôi phục địa hình hoàn chỉnh
                if (task.ageTicks >= 246 && !task.restored) {
                    task.restored = true;

                    List<DisplacedBlockRecord> toRestore = new ArrayList<>();
                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (rec != null) toRestore.add(rec);
                    }
                    for (BlockPos pos : task.interiorPositions) {
                        DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (rec != null) toRestore.add(rec);
                    }

                    if (!toRestore.isEmpty()) {
                        SpatialDisplacementManager.executeTwoPassRestoration(world, toRestore);
                    }

                    world.playSound(null, task.groundCenter, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 3.0f, 1.8f);
                    world.playSound(null, task.groundCenter, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 3.0f, 1.2f);
                    it.remove();
                }
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
