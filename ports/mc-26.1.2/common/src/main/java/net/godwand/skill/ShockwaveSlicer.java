package net.godwand.skill;

import net.godwand.platform.Services;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.network.SingularityThreadsPayload;
import net.godwand.spatial.DisplacedBlockRecord;
import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ShockwaveSlicer {

    public static class SingularityTask {
        public final ServerLevel world;
        public final BlockPos center;
        public final double maxRadius;
        public final int chargeLevel;
        public final Vec3 orbCenter;
        public final ServerPlayer caster;

        public final List<BlockPos> displayedPositions = new ArrayList<>();
        public final Map<BlockPos, BlockDisplay> beams = new HashMap<>();
        public final List<BlockPos> interiorPositions = new ArrayList<>();
        public final Map<BlockPos, Vec3> fullExpansionMap = new HashMap<>();
        public final Map<BlockPos, Quaternionf> baseRotationMap = new HashMap<>();
        public final Map<BlockPos, Float> blockScaleMap = new HashMap<>();

        // Lưu lại trạng thái thực tế của từng block trước khi thu về để không bị giật
        public final Map<BlockPos, Vec3> lastOffsetMap = new HashMap<>();
        public final Map<BlockPos, Quaternionf> lastRotationMap = new HashMap<>();

        public int ageTicks = 0;
        public final int maxLifetimeTicks = 132;
        public boolean snappedBack = false;
        public boolean restored = false;
        public boolean earlySnapped = false;
        public int earlySnapTick = -1;

        public SingularityTask(ServerLevel world, BlockPos center, double maxRadius, Vec3 orbCenter) {
            this(world, center, maxRadius, orbCenter, null);
        }

        public SingularityTask(ServerLevel world, BlockPos center, double maxRadius, Vec3 orbCenter, ServerPlayer caster) {
            this.world = world;
            this.center = center.immutable();
            this.maxRadius = maxRadius;
            this.chargeLevel = maxRadius<=5?1:maxRadius<=10?2:3;
            this.orbCenter = orbCenter;
            this.caster = caster;
        }
    }

    private static final Queue<SingularityTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();

    private static boolean isExposedSurfaceBlock(ServerLevel world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockState neighbor = world.getBlockState(pos.relative(dir));
            if (neighbor.isAir() || !neighbor.isSolidRender()) {
                return true;
            }
        }
        return false;
    }

    public static void queueCalamityDetonation(ServerLevel world, BlockPos center, double maxRadius) {
        queueCalamityDetonation(world, center, maxRadius, null);
    }

    public static void queueCalamityDetonation(ServerLevel world, BlockPos center, double maxRadius, ServerPlayer caster) {
        Vec3 orbCenter = Vec3.atCenterOf(center).add(0, 3.8 + maxRadius * 0.35, 0);
        SingularityTask task = new SingularityTask(world, center, maxRadius, orbCenter, caster);

        int intR = (int) Math.ceil(maxRadius);
        double maxRadiusSq = maxRadius * maxRadius;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        List<BlockPos> surfaceCandidates = new ArrayList<>();
        List<BlockPos> deepCandidates = new ArrayList<>();

        // 1. Quét và phân loại: Ưu tiên 100% các block bề mặt (Surface Shell) để lúc ghép lại khớp kín miệng hố 1:1
        for (int x = -intR; x <= intR; x++) {
            for (int y = -intR; y <= intR; y++) {
                int targetY = center.getY() + y;
                if (targetY < world.getMinY() || targetY >= (world.getMaxY() + 1)) continue;
                for (int z = -intR; z <= intR; z++) {
                    double dSq = x * x + y * y + z * z;
                    if (dSq > maxRadiusSq) continue;

                    mutable.set(center.getX() + x, targetY, center.getZ() + z);
                    BlockState state = world.getBlockState(mutable);
                    if (!SpatialDisplacementManager.canDisplace(world, mutable, state)) continue;

                    BlockPos imm = mutable.immutable();
                    if (isExposedSurfaceBlock(world, imm)) {
                        surfaceCandidates.add(imm);
                    } else {
                        deepCandidates.add(imm);
                    }
                }
            }
        }

        Random rng = new Random(center.asLong());
        Collections.shuffle(surfaceCandidates, rng);
        Collections.shuffle(deepCandidates, rng);

        List<BlockPos> candidatePositions = new ArrayList<>(surfaceCandidates.size() + deepCandidates.size());
        candidatePositions.addAll(surfaceCandidates);
        candidatePositions.addAll(deepCandidates);

        int maxDisplays = Math.min(600, candidatePositions.size());

        for (int i = 0; i < candidatePositions.size(); i++) {
            BlockPos pos = candidatePositions.get(i);
            boolean isDisplay = (i < maxDisplays);

            if (isDisplay) {
                float hashVal = (float) SpatialMath.hash01(pos);
                float hashVal2 = (float) SpatialMath.hash01(pos.above());

                // QUỸ ĐẠO XUYÊN TÂM ĐỒNG BỘ VỊ TRÍ GỐC (TRUE RADIAL + DOME ELEVATION):
                // Giữ nguyên góc phương vị ngang (azimuth) từ tâm hố tới đúng vị trí block đó,
                // giúp block ở phía nào bay ra đúng phía đó và khi thu về khớp 1:1 không bị bay chéo hỗn độn!
                double dx = (pos.getX() + 0.5) - (center.getX() + 0.5);
                double dz = (pos.getZ() + 0.5) - (center.getZ() + 0.5);
                double baseAzimuth = Math.atan2(dz, dx) + (hashVal - 0.5) * 0.22;

                // Trải đều góc nâng phi từ [-pi/3, +pi/2.05] để có đầy đủ block bao phủ cả TRÊN ĐỈNH, NGANG và DƯỚI quả cầu
                double phi = (hashVal2 * 1.45 - 0.55) * (Math.PI * 0.5);
                double expandRadius = maxRadius * (1.15 + hashVal * 0.95);

                Vec3 sphereDir = new Vec3(
                        Math.cos(phi) * Math.cos(baseAzimuth),
                        Math.sin(phi),
                        Math.cos(phi) * Math.sin(baseAzimuth)
                ).normalize();

                Vec3 targetWorldPos = orbCenter.add(sphereDir.scale(expandRadius));
                double minHoverY = center.getY() + 1.0 + hashVal2 * 2.5;
                if (targetWorldPos.y < minHoverY) {
                    targetWorldPos = new Vec3(targetWorldPos.x, minHoverY, targetWorldPos.z);
                }

                Vec3 fullExpansionVec = targetWorldPos.subtract(Vec3.atCenterOf(pos));

                float scale = 0.92f + hashVal * 0.48f;
                Quaternionf rot = new Quaternionf()
                        .rotateY((float) (hashVal * Math.PI * 2.0))
                        .rotateX((float) Math.toRadians(25.0 + hashVal * 35.0));

                SpatialDisplacementManager.displaceBlock(
                        world,
                        pos,
                        new Vector3f(0, 0, 0),
                        rot,
                        new Vector3f(scale, scale, scale),
                        140,
                        0,
                        true,
                        DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
                        null,
                        Vec3.atCenterOf(center),
                        maxRadius
                );

                task.displayedPositions.add(pos);
                task.fullExpansionMap.put(pos, fullExpansionVec);
                task.baseRotationMap.put(pos, rot);
                task.blockScaleMap.put(pos, scale);
                task.lastOffsetMap.put(pos, Vec3.ZERO);
                task.lastRotationMap.put(pos, rot);
            } else {
                SpatialDisplacementManager.displaceBlock(
                        world,
                        pos,
                        new Vector3f(0, 0, 0),
                        new Quaternionf(),
                        new Vector3f(1.0f, 1.0f, 1.0f),
                        140,
                        0,
                        false,
                        DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
                        null,
                        Vec3.atCenterOf(center),
                        maxRadius
                );
                task.interiorPositions.add(pos);
            }
        }

        // Audio & Visuals nổ khai cuộc
        world.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), orbCenter.x, orbCenter.y, orbCenter.z, 4, 0.2, 0.2, 0.2, 0.0);
        world.sendParticles(ParticleTypes.SONIC_BOOM, orbCenter.x, orbCenter.y, orbCenter.z, 2, 0.1, 0.1, 0.1, 0.0);
        world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 3, 1.0, 1.0, 1.0, 0.0);
        world.sendParticles(new DustParticleOptions(net.godwand.GodWandMod.colorFromVector3f(new Vector3f(0.0f, 0.95f, 1.0f)), 1.8f),
                orbCenter.x, orbCenter.y, orbCenter.z, 60, 1.5, 1.5, 1.5, 0.45);

        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3.5f, 0.6f);
        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0f, 0.8f);
        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 2.8f, 0.7f);

        AABB blastBox = new AABB(center).inflate(maxRadius * 1.25);
        for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, blastBox, ent -> true)) {
            Vec3 delta = e.position().subtract(Vec3.atCenterOf(center));
            double dist = delta.length();
            if (dist < maxRadius * 1.25 && dist > 0.1) {
                double force = (1.0 - (dist / (maxRadius * 1.25))) * 2.2;
                Vec3 push = delta.normalize().scale(force).add(0, 0.55, 0);
                e.push(push.x, push.y, push.z);
                e.hurtMarked = true;
            }
        }

        ImpactFxPayload detPayload = new ImpactFxPayload(
                task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                ImpactFxPayload.TYPE_DETONATION, 132, (float) Math.max(1.0, maxRadius / 10.0)
        );
        CinematicFxPayload detCinematic = new CinematicFxPayload(
                CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                0.65f, 16
        );
        Services.NETWORK.sendToNearby(world, task.orbCenter, 96.0, detPayload);
        Services.NETWORK.sendToNearby(world, task.orbCenter, 96.0, detCinematic);

        ACTIVE_TASKS.add(task);

        if (caster != null) {
            caster.sendOverlayMessage(Component.literal("§dDeployed. Click to collapse."));
        }
    }

    /**
     * Vẽ 3 Vòng Quỹ Đạo Particle (3-Ring Astrolabe Gimbal) bay vòng vòng cực lực quanh Quả Cầu Năng Lượng
     */
    private static void spawnThreeOrbitalParticleRings(ServerLevel world, Vec3 center, int ageTicks, double radius) {
        double baseSpeed = ageTicks * 0.28;
        int pointsPerRing = 6;

        for (int ring = 0; ring < 3; ring++) {
            double rotX = (ring == 0) ? Math.toRadians(38.0) : (ring == 1) ? Math.toRadians(-42.0) : Math.toRadians(15.0);
            double rotZ = (ring == 0) ? Math.toRadians(22.0) : (ring == 1) ? Math.toRadians(35.0) : Math.toRadians(-55.0);
            double dir = (ring == 1) ? -1.35 : (ring == 2) ? 1.15 : 1.0;

            Vector3f ringColor = switch (ring) {
                case 0 -> new Vector3f(0.0f, 0.96f, 1.0f);   // Điện quang Cyan
                case 1 -> new Vector3f(0.90f, 0.98f, 1.0f);  // Trắng sáng lõi
                default -> new Vector3f(0.68f, 0.22f, 1.0f); // Tím Arcane
            };

            for (int i = 0; i < pointsPerRing; i++) {
                double theta = baseSpeed * dir + (i * 2.0 * Math.PI / pointsPerRing);
                double lx = Math.cos(theta) * radius;
                double lz = Math.sin(theta) * radius;
                double ly = 0.0;

                // Xoay nghiêng 3D theo trục Z và X
                double x1 = lx * Math.cos(rotZ) - ly * Math.sin(rotZ);
                double y1 = lx * Math.sin(rotZ) + ly * Math.cos(rotZ);
                double z1 = lz;

                double fx = x1;
                double fy = y1 * Math.cos(rotX) - z1 * Math.sin(rotX);
                double fz = y1 * Math.sin(rotX) + z1 * Math.cos(rotX);

                world.sendParticles(new DustParticleOptions(net.godwand.GodWandMod.colorFromVector3f(ringColor), 1.35f),
                        center.x + fx, center.y + fy, center.z + fz,
                        1, 0.02, 0.02, 0.02, 0.0);

                if (i % 2 == 0) {
                    world.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            center.x + fx, center.y + fy, center.z + fz,
                            1, 0.03, 0.03, 0.03, 0.02);
                }
            }
        }
    }

    public static boolean hasActiveTask(ServerPlayer player) {
        for (SingularityTask task : ACTIVE_TASKS) {
            if (!task.restored && !task.earlySnapped) {
                if (task.caster == null || task.caster == player || task.caster.getUUID().equals(player.getUUID())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean triggerEarlySnap(ServerPlayer player) {
        if (player.getCooldowns().isOnCooldown(net.godwand.GodWandMod.GOD_WAND_ITEM.getDefaultInstance())) return false;
        for (SingularityTask task : ACTIVE_TASKS) {
            if (!task.restored && !task.earlySnapped) {
                if (task.caster == null || task.caster == player || task.caster.getUUID().equals(player.getUUID())) {
                    snapTask(task);
                    return true;
                }
            }
        }
        return false;
    }

    private static void snapTask(SingularityTask task) {
        task.earlySnapped = true;
        task.earlySnapTick = task.ageTicks;
        task.snappedBack = true;
        sendThreadFx(task, 4);

        ServerLevel world = task.world;

        // 1. Audio feedback uy lực
        world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 0.4f, 1.8f);
        world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 0.45f, 1.6f);
        world.playSound(null, task.center, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 0.65f, 0.8f);
        world.playSound(null, task.center, SoundEvents.HEAVY_CORE_PLACE, SoundSource.BLOCKS, 0.45f, 0.7f);

        // 2. Sát thương nghiền nát (45 - 125) & Lực hút trọng trường kéo tụt mục tiêu xuống đáy hố
        AABB snapBox = new AABB(task.center).inflate(task.maxRadius * 1.15);
        for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, snapBox, ent -> ent != task.caster && ent.isAlive())) {
            Vec3 diff = Vec3.atCenterOf(task.center).subtract(e.position());
            double dist = diff.length();
            if (dist <= task.maxRadius * 1.15) {
                float dmg = (float) Math.max(45.0, (1.0 - (dist / (task.maxRadius * 1.15))) * 80.0 + 45.0) * (float) net.godwand.config.GodWandConfig.skillDamageMultiplier(1,task.chargeLevel);
                e.hurtServer(world, world.damageSources().fallingBlock(task.caster), (e instanceof net.minecraft.world.entity.Mob ? 2.0f : 1.0f) * (dmg));

                Vec3 pull = (dist > 0.01) ? diff.normalize().scale(1.9).add(0, -0.9, 0) : new Vec3(0, -1.0, 0);
                e.setDeltaMovement(pull);
                e.hurtMarked = true;
            }
        }

        // 3. Interpolate siêu tốc (4 ticks) kéo toàn bộ khối hiển thị về ô gốc (0,0,0)
        for (BlockPos pos : task.displayedPositions) {
            DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
            if (record == null || record.displayEntityUuid == null) continue;

            BlockDisplay display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
            if (display == null || !display.isAlive()) continue;

            display.setTransformationInterpolationDelay(0);
            display.setTransformationInterpolationDuration(4);
            updateBeam(task, pos, Vec3.ZERO, 4);
            display.setTransformation(SpatialMath.createPivotTransform(Vec3.ZERO, new Quaternionf(), 1.0f));
        }

        // 4. Gửi hiệu ứng rung chấn Trauma Shake & FOV Punch
        ImpactFxPayload snapFx = new ImpactFxPayload(task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                ImpactFxPayload.TYPE_REASSEMBLY, 10, 1.45f);
        CinematicFxPayload snapShake = new CinematicFxPayload(
                CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                0.95f, 14
        );
        CinematicFxPayload snapFov = new CinematicFxPayload(
                CinematicFxPayload.EVENT_FOV_PUNCH,
                task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                0.85f, 6
        );
        Services.NETWORK.sendToNearby(world, task.orbCenter, 96.0, snapFx);
        Services.NETWORK.sendToNearby(world, task.orbCenter, 96.0, snapShake);
        Services.NETWORK.sendToNearby(world, task.orbCenter, 96.0, snapFov);
    }

    public static void tick(ServerLevel world) {
        Iterator<SingularityTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            SingularityTask task = it.next();
            if (task.world != world) continue;

            task.ageTicks++;

            // Xử lý Early Snap: Thu toàn bộ block về thần tốc trong 4 ticks, gây sát thương nghiền nát và chôn vùi (Entombment)
            if (task.earlySnapped) {
                int ticksSinceSnap = task.ageTicks - task.earlySnapTick;
                if (ticksSinceSnap < 4) animateReturn(task, ticksSinceSnap / 3f);
                if (ticksSinceSnap < 4) {
                    world.sendParticles(ParticleTypes.REVERSE_PORTAL, task.center.getX() + 0.5, task.center.getY() + 1.0, task.center.getZ() + 0.5, 16, 1.5, 1.0, 1.5, 0.2);
                    world.sendParticles(new DustParticleOptions(net.godwand.GodWandMod.colorFromVector3f(new Vector3f(0.0f, 0.95f, 1.0f)), 1.5f), task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 12, 0.8, 0.8, 0.8, 0.3);
                } else if (!task.restored) {
                    executeRestoration(task);
                    it.remove();
                }
                continue;
            }

            // Refresh references for clients entering the area after the cast.
            if (task.ageTicks <= 105 && task.ageTicks % 20 == 0) sendThreadFx(task, 0);
            if (task.ageTicks <= 112) {
                double ringR = (task.ageTicks <= 105) ? 1.55 : Math.max(0.2, 1.55 * (1.0 - (task.ageTicks - 105) / 7.0));
                spawnThreeOrbitalParticleRings(world, task.orbCenter, task.ageTicks, ringR);
            }

            // =========================================================================
            // PHA 1: NỨT VỞ & BUNG NĂNG LƯỢNG (Ticks 1..22) - CUBIC EASE-OUT
            // =========================================================================
            if (task.ageTicks <= 22) {
                if (task.ageTicks % 2 == 0) {
                    float t = task.ageTicks / 22.0f;
                    double p = 1.0 - Math.pow(1.0 - t, 3.0);
                    updateBlockPositions(task, p, 2);

                    // Sóng năng lượng bung tỏa kiểu Orbital Railgun phóng ra ngoài
                    double outProg = task.ageTicks / 22.0;
                    double shockOutR = 0.8 + Math.pow(outProg, 0.65) * (task.maxRadius * 1.35);
                    int outPoints = 36;
                    for (int pt = 0; pt < outPoints; pt++) {
                        double ang = (pt / (double) outPoints) * Math.PI * 2.0;
                        double px = task.orbCenter.x + Math.cos(ang) * shockOutR;
                        double pz = task.orbCenter.z + Math.sin(ang) * shockOutR;
                        double py = task.orbCenter.y;
                        double vx = Math.cos(ang) * 0.45;
                        double vz = Math.sin(ang) * 0.45;
                        world.sendParticles(new DustParticleOptions(net.godwand.GodWandMod.colorFromVector3f(new Vector3f(0.08f, 0.92f, 1.0f)), 1.6f),
                                px, py, pz, 1, vx, 0.05, vz, 0.15);
                        if (pt % 4 == 0) {
                            world.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 1, vx, 0.1, vz, 0.05);
                        }
                    }
                }
            }

            // =========================================================================
            // PHA 2: LÀM CHẬM THỜI GIAN CỰC HẠN / BULLET-TIME DRIFT (Ticks 23..105)
            // Không bao giờ bị giật ở Tick 23 nhờ góc xoay nối tiếp liên tục từ 0.0!
            // =========================================================================
            else if (task.ageTicks <= 105) {
                // The final expansion transform is held unchanged until recall.
            }

            // =========================================================================
            // PHA 3: THU HỒI KHỚP 1:1 THEO LỚP TỪ DƯỚI LÊN (Ticks 106..132)
            // =========================================================================
            else {
                // Ticks 106..130: Lực từ trường không gian co kéo căng như dây cung (Magnetic Tension / Pre-Snap Recall)
                // Tuyệt đối không dùng sóng shockwave co rút gây rối mắt!
                if (task.ageTicks >= 106 && task.ageTicks <= 130) {
                    if (task.ageTicks % 4 == 0) {
                        for (int k = 0; k < 6; k++) {
                            if (!task.displayedPositions.isEmpty()) {
                                BlockPos rPos = task.displayedPositions.get(world.getRandom().nextInt(task.displayedPositions.size()));
                                Vec3 curOff = task.lastOffsetMap.getOrDefault(rPos, Vec3.ZERO);
                                Vec3 bPos = Vec3.atCenterOf(rPos).add(curOff);
                                world.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                        bPos.x, bPos.y, bPos.z, 2, 0.05, 0.05, 0.05, 0.04);
                                world.sendParticles(new DustParticleOptions(net.godwand.GodWandMod.colorFromVector3f(new Vector3f(0.0f, 0.96f, 1.0f)), 1.2f),
                                        bPos.x, bPos.y, bPos.z, 1, 0, 0.02, 0, 0.01);
                            }
                        }
                    }
                }

                // Ticks 106..111: Nhích nhẹ lấy đà (+4%) từ ĐÚNG vị trí & góc xoay hiện tại (không reset góc xoay gây giật!)
                if (task.ageTicks == 106) {
                    world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.55f, 1.5f);

                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;
                        BlockDisplay display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        Vec3 curOff = task.lastOffsetMap.getOrDefault(pos, task.fullExpansionMap.getOrDefault(pos, Vec3.ZERO));
                        Quaternionf curRot = task.lastRotationMap.getOrDefault(pos, task.baseRotationMap.getOrDefault(pos, new Quaternionf()));
                        float scale = task.blockScaleMap.getOrDefault(pos, 1.0f);

                        display.setTransformationInterpolationDelay(0);
                        display.setTransformationInterpolationDuration(5);
                        updateBeam(task, pos, curOff.scale(1.04), 5);
                        task.lastOffsetMap.put(pos, curOff.scale(1.04));
                        display.setTransformation(SpatialMath.createPivotTransform(curOff.scale(1.04), curRot, scale));
                    }
                }

                // Ticks 112..130: Vacuum Snap-Back — Thu toàn bộ block về đúng ô gốc (0,0,0), góc xoay (0,0,0) và trả scale về đúng 1.0f!
                if (task.ageTicks == 112 && !task.snappedBack) {
                    task.snappedBack = true;
                    sendThreadFx(task, 18);

                    world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 0.9f, 1.6f);
                    world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.45f, 1.8f);

                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplay display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        // TRẢ SCALE VỀ ĐÚNG 1.0f ĐỂ KHỚP KHÍT TỪNG Ô GẠCH TRƯỚC KHI ĐẶT BLOCK THẬT!
                        display.setTransformationInterpolationDelay(0);
                        display.setTransformationInterpolationDuration(18);
                        updateBeam(task, pos, Vec3.ZERO, 18);
                        display.setTransformation(SpatialMath.createPivotTransform(Vec3.ZERO, new Quaternionf(), 1.0f));
                    }
                }

                // Tick 131: Dọn dẹp thực thể và khôi phục địa hình 1:1 ngay khi kết thúc nội suy 18 ticks (112 + 18 = 130)
                if (task.ageTicks >= 112 && task.ageTicks <= 130) animateReturn(task, (task.ageTicks - 112) / 16f);
                if (task.ageTicks >= 131 && !task.restored) {
                    executeRestoration(task);
                    it.remove();
                }
            }
        }
    }

    public static float returnProgress(float progress) {
        float t = Mth.clamp(progress, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static void animateReturn(SingularityTask task, float progress) {
        float t = returnProgress(progress);
        for (BlockPos pos : task.displayedPositions) {
            var record = SpatialDisplacementManager.getActiveRecord(task.world.dimension(), pos);
            if (record == null || record.displayEntityUuid == null) continue;
            var display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
            if (display == null || !display.isAlive()) continue;
            Vec3 offset = task.lastOffsetMap.getOrDefault(pos, Vec3.ZERO).scale(1 - t);
            Quaternionf rotation = new Quaternionf(task.lastRotationMap.getOrDefault(pos, new Quaternionf())).slerp(new Quaternionf(), t);
            float scale = Mth.lerp(t, task.blockScaleMap.getOrDefault(pos, 1f), 1f);
            display.setTransformationInterpolationDelay(0);
            display.setTransformationInterpolationDuration(1);
            display.setTransformation(SpatialMath.createPivotTransform(offset, rotation, scale));
            updateBeam(task, pos, offset, 1);
        }
    }

    private static void executeRestoration(SingularityTask task) {
        clearBeams(task);
        task.restored = true;
        ServerLevel world = task.world;

        List<DisplacedBlockRecord> toRestore = new ArrayList<>();
        for (BlockPos pos : task.displayedPositions) {
            DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
            if (rec != null) toRestore.add(rec);
        }
        for (BlockPos pos : task.interiorPositions) {
            DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
            if (rec != null) toRestore.add(rec);
        }

        if (!toRestore.isEmpty()) {
            SpatialDisplacementManager.executeTwoPassRestoration(world, toRestore);
        }

        // Sát thương chôn vùi & ngạt thở (Entombment & Suffocation)
        AABB entombBox = new AABB(task.center).inflate(task.maxRadius * 1.05);
        for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, entombBox, ent -> ent != task.caster && ent.isAlive())) {
            BlockPos feetPos = BlockPos.containing(e.position());
            BlockPos eyePos = BlockPos.containing(e.getEyePosition());
            BlockState feetState = world.getBlockState(feetPos);
            BlockState eyeState = world.getBlockState(eyePos);
            boolean inSolid = feetState.isSolidRender() || eyeState.isSolidRender();
            if (inSolid) {
                e.hurtServer(world, task.caster != null ? world.damageSources().fallingBlock(task.caster) : world.damageSources().inWall(), (e instanceof net.minecraft.world.entity.Mob ? 2.0f : 1.0f) * (35.0f) * net.godwand.config.GodWandConfig.skillDamageMultiplier(1,task.chargeLevel));
                BlockState blockToBreak = feetState.isSolidRender() ? feetState : eyeState;
                world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockToBreak),
                        e.getX(), e.getY() + 0.5, e.getZ(), 24, 0.3, 0.5, 0.3, 0.15);
            }
        }

        if (task.caster != null) {
            task.caster.sendOverlayMessage(Component.literal("§dCollapsed!"));
        }

        // Hiệu ứng chớp sáng Manga & rung chấn cực mạnh khi địa hình khóa sập 1:1 liền mạch
        ImpactFxPayload snapFx = new ImpactFxPayload(task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                ImpactFxPayload.TYPE_REASSEMBLY, 10, 1.35f);
        CinematicFxPayload snapShake = new CinematicFxPayload(
                CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                0.82f, 12
        );
        CinematicFxPayload snapFov = new CinematicFxPayload(
                CinematicFxPayload.EVENT_FOV_PUNCH,
                task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                0.90f, 6
        );
        Services.NETWORK.sendToNearby(world, task.orbCenter, 96.0, snapFx);
        Services.NETWORK.sendToNearby(world, task.orbCenter, 96.0, snapShake);
        Services.NETWORK.sendToNearby(world, task.orbCenter, 96.0, snapFov);

        // Chớp sáng Flash & Mạng lưới hàn gắn địa chấn mặt đất (Seam Fusion Sparks)
        world.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 4, 0.4, 0.4, 0.4, 0.0);
        world.sendParticles(ParticleTypes.SONIC_BOOM, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 1, 0, 0, 0, 0);

        for (int s = 0; s < 54; s++) {
            double ang = (s / 54.0) * Math.PI * 2.0;
            double dist = task.maxRadius * (0.35 + world.getRandom().nextDouble() * 0.75);
            double px = task.center.getX() + 0.5 + Math.cos(ang) * dist;
            double pz = task.center.getZ() + 0.5 + Math.sin(ang) * dist;
            world.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), px, task.center.getY() + 1.05, pz, 1, 0, 0, 0, 0);
            world.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, task.center.getY() + 1.1, pz, 2, 0.1, 0.1, 0.1, 0.05);
            world.sendParticles(new DustParticleOptions(net.godwand.GodWandMod.colorFromVector3f(new Vector3f(0.0f, 0.95f, 1.0f)), 1.5f),
                    px, task.center.getY() + 1.1, pz, 1, 0, 0.05, 0, 0.02);
        }

        // Dàn âm thanh cơ học khóa sập cực kỳ uy lực
        world.playSound(null, task.center, SoundEvents.HEAVY_CORE_PLACE, SoundSource.BLOCKS, 3.5f, 1.05f);
        world.playSound(null, task.center, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 3.0f, 1.4f);
        world.playSound(null, task.center, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 3.2f, 1.7f);
        world.playSound(null, task.center, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.BLOCKS, 3.0f, 1.35f);
        world.playSound(null, task.center, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 2.8f, 1.8f);
    }

    private static void updateBlockPositions(SingularityTask task, double progress, int duration) {
        int previousBeamCount = task.beams.size();

        for (BlockPos pos : task.displayedPositions) {
            DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(task.world.dimension(), pos);
            if (record == null || record.displayEntityUuid == null) continue;

            BlockDisplay display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
            if (display == null || !display.isAlive()) continue;

            Vec3 fullExp = task.fullExpansionMap.getOrDefault(pos, Vec3.ZERO);
            Quaternionf rot = task.baseRotationMap.getOrDefault(pos, new Quaternionf());
            float scale = task.blockScaleMap.getOrDefault(pos, 1.0f);

            Vec3 curOffset = fullExp.scale(progress);
            Quaternionf currentRot = rot;

            int index = task.displayedPositions.indexOf(pos);
            int beamStride = Math.max(1, (task.displayedPositions.size() + 143) / 144);
            if (index % beamStride == 0) {
                BlockDisplay beam = task.beams.get(pos);
                if (beam == null) {
                    beam = new BlockDisplay(net.minecraft.world.entity.EntityType.BLOCK_DISPLAY, task.world);
                    beam.addTag("godwand_displaced");
                    beam.setPos(task.orbCenter);
                    beam.setBlockState(net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK.defaultBlockState());
                    beam.setBrightnessOverride(new net.minecraft.util.Brightness(15, 15));
                    beam.setGlowingTag(false);
                    beam.setGlowColorOverride(-1);
                    task.world.addFreshEntity(beam);
                    task.beams.put(pos, beam);
                }
                updateBeam(task, pos, curOffset, duration);
            }

            task.lastOffsetMap.put(pos, curOffset);
            task.lastRotationMap.put(pos, currentRot);

            display.setTransformationInterpolationDelay(0);
            display.setTransformationInterpolationDuration(duration);
            display.setTransformation(SpatialMath.createPivotTransform(curOffset, currentRot, scale));
        }
        if (task.beams.size() != previousBeamCount) sendThreadFx(task, 0);
    }

    private static void sendThreadFx(SingularityTask task, int recallTicks) {
        if (task.beams.isEmpty()) return;
        var ids = task.beams.values().stream().filter(BlockDisplay::isAlive).map(BlockDisplay::getId).toList();
        var payload = new net.godwand.network.SingularityThreadsPayload(ids, recallTicks);
        Services.NETWORK.sendToNearby(task.world, task.orbCenter, 96.0, payload);
    }

    public static int getActiveTaskCount() {
        return ACTIVE_TASKS.size();
    }

    private static void updateBeam(SingularityTask task, BlockPos pos, Vec3 offset, int duration) {
        BlockDisplay beam = task.beams.get(pos);
        if (beam == null) return;
        beam.setTransformationInterpolationDelay(0);
        beam.setTransformationInterpolationDuration(duration);
        beam.setTransformation(SpatialMath.createThinBeamTransform(task.orbCenter, Vec3.atCenterOf(pos).add(offset), 0.012f));
    }

    private static void clearBeams(SingularityTask task) {
        task.beams.values().forEach(BlockDisplay::discard);
        task.beams.clear();
    }

    public static void clearAllTasks() {
        ACTIVE_TASKS.forEach(ShockwaveSlicer::clearBeams);
        ACTIVE_TASKS.clear();
    }
}



