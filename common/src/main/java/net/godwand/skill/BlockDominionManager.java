package net.godwand.skill;

import net.godwand.config.GodWandConfig;
import net.godwand.entity.DominionBlockProjectileEntity;
import net.godwand.item.GodWandItem;
import net.godwand.network.BlockDominionInputPayload;
import net.godwand.network.BlockDominionStatePayload;
import net.godwand.network.CannonPressurePayload;
import net.godwand.network.CinematicFxPayload;
import net.godwand.platform.Services;
import net.godwand.spatial.DisplacementPersistentState;
import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Server authority for Block Dominion (mode 9). */
public final class BlockDominionManager {
    private static final double PICKUP_REACH = 7.5;
    private static final double SYNC_RADIUS = 96.0;
    private static final SoundEvent IMPACT_SFX = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath("godwand", "dominion_impact"));
    private static final SoundEvent FIRE_SFX = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath("godwand", "dominion_fire"));
    private static final SoundEvent PICKUP_SFX = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath("godwand", "dominion_pickup"));
    private static final SoundEvent UNFOLD_SFX = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath("godwand", "dominion_unfold"));
    private static final SoundEvent CAGE_FORGE_SFX = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath("godwand", "dominion_cage_forge"));
    private static final SoundEvent METEOR_IGNITE_SFX = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath("godwand", "dominion_meteor_ignite"));

    private record DroppedBlock(BlockPos pos, BlockState state) {}
    private record RecallSnapshot(ResourceKey<Level> dimension, long deactivatedAtTick, boolean died, List<DroppedBlock> blocks) {}
    private record QueuedShot(BlockState state, double sideOffset, double upOffset, double fwdOffset, int delayTicks) {}

    private static final class PlayerState {
        BlockDominionRules.Mode mode = BlockDominionRules.Mode.OFF;
        final List<BlockState> reserve = new ArrayList<>();
        boolean stowed;
        boolean hiddenInCutscene;
        boolean volleyCharging;
        int volleyChargeTicks;
        boolean volleyAerial;
        boolean rapidFiring;
        int rapidShotsFired;
        int nextRapidFireTick;
        long postParryInvulnUntil = -1;
        long lastHeartbeatTick = -1;
        int volleySilenceLeft;
        BlockDominionBrain.FormationKind formation = BlockDominionBrain.FormationKind.IDLE;
        Vec3 threatPrimary = new Vec3(0, 0, 1);
        Vec3 threatSecondary = Vec3.ZERO;
        boolean wingFlying;
        boolean wingBoosting;
        boolean wingGlideMode;
        boolean airStairsActive;
        boolean stairAscending;
        boolean rainingOverhead;
        boolean liquidStrideActive;
        boolean sovereignPerchActive;
        boolean meteorDiveActive;
        int hoverIdleTicks;
        int meteorDiveTicks;
        int meteorDiveTier;
        double meteorDiveStartY = Double.NaN;
        long stairAscendUntil = -1000;
        int flightForward;
        int flightStrafe;
        int flightVertical;
        boolean flightBoostInput;
        long lastFlightPacketTick = -1000;
        long lastStepEventTick = -1000;
        long lastPickupTick = -1000;
        long lastSurgeTick = -1000;
        long lastCageTick = -1000;
        long safeFallUntil = -1;
        Vec3 flightVel = Vec3.ZERO;
        final List<ActiveCage> activeCages = new ArrayList<>();
        final ArrayDeque<QueuedShot> volleyQueue = new ArrayDeque<>();
        RecallSnapshot recall;
    }

    private static final class ActiveCage {
        final int targetId;
        final List<BlockState> blocks;
        final Vec3 anchor;
        final long startTick;
        long endTick;
        final ResourceKey<Level> dimension;
        boolean impaled;
        long impaleTick = -1;
        boolean victimKilled;

        ActiveCage(int targetId, List<BlockState> blocks, Vec3 anchor, long startTick, long endTick, ResourceKey<Level> dimension) {
            this.targetId = targetId;
            this.blocks = blocks;
            this.anchor = anchor;
            this.startTick = startTick;
            this.endTick = endTick;
            this.dimension = dimension;
        }
    }

    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();
    private static final ThreadLocal<UUID> SELF_DAMAGE_CASTER = new ThreadLocal<>();

    private BlockDominionManager() {}

    private static PlayerState stateOf(UUID id) {
        return STATES.computeIfAbsent(id, k -> new PlayerState());
    }

    public static BlockDominionRules.Mode getMode(Player player) {
        if (player == null) return BlockDominionRules.Mode.OFF;
        PlayerState state = STATES.get(player.getUUID());
        return state == null ? BlockDominionRules.Mode.OFF : state.mode;
    }

    public static boolean isActive(Player player) {
        return getMode(player) != BlockDominionRules.Mode.OFF;
    }

    public static int reserveCount(Player player) {
        if (player == null) return 0;
        PlayerState state = STATES.get(player.getUUID());
        return state == null ? 0 : state.reserve.size();
    }

    public static boolean isWingFlying(Player player) {
        if (player == null) return false;
        PlayerState state = STATES.get(player.getUUID());
        return state != null && state.wingFlying;
    }

    public static boolean isMeteorDiving(Player player) {
        if (player == null) return false;
        PlayerState state = STATES.get(player.getUUID());
        return state != null && state.wingFlying && state.meteorDiveActive;
    }

    public static int meteorDiveTier(Player player) {
        if (player == null) return 0;
        PlayerState state = STATES.get(player.getUUID());
        return state != null && state.wingFlying ? state.meteorDiveTier : 0;
    }

    public static boolean isAirStairsActive(Player player) {
        if (player == null) return false;
        PlayerState state = STATES.get(player.getUUID());
        return state != null && state.airStairsActive;
    }

    public static boolean isGlideMode(Player player) {
        if (player == null) return false;
        PlayerState state = STATES.get(player.getUUID());
        return state != null && state.wingGlideMode;
    }

    public static boolean isUmbrellaActive(Player player) {
        if (player == null) return false;
        PlayerState state = STATES.get(player.getUUID());
        return state != null && state.formation == BlockDominionBrain.FormationKind.RAIN_UMBRELLA;
    }

    public static boolean isLiquidStrideActive(Player player) {
        if (player == null) return false;
        PlayerState state = STATES.get(player.getUUID());
        return state != null && state.liquidStrideActive;
    }

    public static boolean isSovereignPerchActive(Player player) {
        if (player == null) return false;
        PlayerState state = STATES.get(player.getUUID());
        return state != null && state.formation == BlockDominionBrain.FormationKind.SOVEREIGN_PERCH;
    }

    public static boolean hasActiveCage(Player player) {
        if (player == null) return false;
        PlayerState state = STATES.get(player.getUUID());
        return state != null && !state.activeCages.isEmpty();
    }

    public static int activeCageCount(Player player) {
        if (player == null) return 0;
        PlayerState state = STATES.get(player.getUUID());
        return state == null ? 0 : state.activeCages.size();
    }

    /** Switches Block Wings between Hover (free 3D levitation) and Glide (Elytra aerodynamic gliding + rocket boost). */
    public static void setFlightStyle(ServerPlayer player, boolean glide) {
        if (player == null) return;
        PlayerState st = stateOf(player.getUUID());
        st.wingGlideMode = glide;
        if (st.wingFlying && glide && st.flightVel.lengthSqr() < 0.25) {
            st.flightVel = player.getViewVector(1.0f).normalize().scale(0.65);
            player.setDeltaMovement(st.flightVel);
            player.hurtMarked = true;
        }
        player.displayClientMessage(Component.translatable(
                glide ? "message.godwand.dominion_wings_glide" : "message.godwand.dominion_wings_hover"), true);
        if (st.mode != BlockDominionRules.Mode.OFF) {
            sync(player, st, BlockDominionRules.EVENT_SYNC, player.getX(), player.getY() + 1.25, player.getZ(), 0);
        }
    }

    public static void toggleFlightStyle(ServerPlayer player) {
        if (player == null) return;
        setFlightStyle(player, !isGlideMode(player));
    }

    /** Programmatically engages or disengages Block Wings free flight (also used by double-tap Space and QA). */
    public static boolean setWingFlight(ServerPlayer player, boolean enable) {
        if (player == null) return false;
        PlayerState st = stateOf(player.getUUID());
        if (enable) {
            if (st.mode != BlockDominionRules.Mode.MANUAL
                    || st.reserve.size() < BlockDominionRules.MIN_FLIGHT_BLOCKS
                    || st.hiddenInCutscene || !player.isAlive() || player.isSpectator()) {
                return false;
            }
            if (st.wingFlying) return true;
            st.wingFlying = true;
            st.wingBoosting = false;
            st.airStairsActive = false;
            Vec3 cur = player.getDeltaMovement();
            st.flightVel = new Vec3(cur.x, Math.max(0.32, cur.y + 0.22), cur.z);
            if (player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            player.setNoGravity(true);
            player.fallDistance = 0.0f;
            player.setDeltaMovement(st.flightVel);
            player.hurtMarked = true;
            Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                    CinematicFxPayload.EVENT_FOV_PUNCH, player.getX(), player.getY(), player.getZ(), 1.06f, 6));
            player.displayClientMessage(Component.translatable("message.godwand.dominion_wings_on"), true);
            st.formation = BlockDominionBrain.FormationKind.WINGS_HOVER;
            sync(player, st, BlockDominionRules.EVENT_FLIGHT_START, player.getX(), player.getY() + 1.25, player.getZ(), 0);
            return true;
        } else {
            if (!st.wingFlying) return false;
            stopWingFlightInternal(player, st, true);
            return true;
        }
    }

    /** Updates desired 3D wing-flight control axes (used by client input packets and automated QA). */
    public static void setFlightControls(ServerPlayer player, int forward, int strafe, int vertical, boolean boost, boolean fromClientPacket) {
        if (player == null) return;
        PlayerState st = STATES.get(player.getUUID());
        if (st == null || !st.wingFlying) return;
        st.flightForward = Mth.clamp(forward, -1, 1);
        st.flightStrafe = Mth.clamp(strafe, -1, 1);
        st.flightVertical = Mth.clamp(vertical, -1, 1);
        st.flightBoostInput = boost;
        if (fromClientPacket) {
            st.lastFlightPacketTick = player.serverLevel().getGameTime();
        }
    }

    /**
     * Multi-Tier Meteor Strike Dive Impact (Tier I -> Tier II Inferno Comet -> Tier III CATACLYSM):
     * Slams down like an extinction asteroid with zero fall damage, unleashing tier-scaled seismic AoE devastation,
     * fiery radial launch, and (when terrain damage is enabled) a scorched, smoldering volcanic impact crater!
     */
    private static void detonateMeteorStrike(ServerLevel world, ServerPlayer player, PlayerState st,
                                             Vec3 landVel, int tier, float charge) {
        int t = Mth.clamp(tier, 1, 3);
        player.setDeltaMovement(landVel.x * 0.12, 0.0, landVel.z * 0.12);
        player.fallDistance = 0.0f;
        player.hurtMarked = true;

        Vec3 impactPos = player.position();
        // Consume orbiting blocks scaled by Meteor Tier (Tier I ~25%, Tier II ~55%, Tier III CATACLYSM = 100% ALL blocks!)
        int reserveBefore = st.reserve.size();
        int blockCost = BlockDominionRules.meteorBlockCost(t, reserveBefore);
        BlockState primaryMat = st.reserve.isEmpty() ? Blocks.DEEPSLATE.defaultBlockState() : st.reserve.get(st.reserve.size() - 1);
        for (int c = 0; c < blockCost && !st.reserve.isEmpty(); c++) {
            st.reserve.remove(st.reserve.size() - 1);
        }

        double blockFrac = Mth.clamp(reserveBefore / (double) BlockDominionRules.DEFAULT_CAPACITY, 0.25, 1.15);
        double radius = switch (t) {
            case 3 -> Mth.lerp(charge, 16.5, BlockDominionRules.METEOR_STRIKE_MAX_RADIUS);
            case 2 -> Mth.lerp(charge, 11.2, 13.8);
            default -> Mth.lerp(charge, BlockDominionRules.METEOR_STRIKE_MIN_RADIUS, 8.2);
        };
        var cfg = GodWandConfig.SERVER;
        float rawDmg = switch (t) {
            case 3 -> (float) (Mth.lerp(charge, 95.0f, 138.0f) * (0.75 + 0.25 * blockFrac));
            case 2 -> (float) (Mth.lerp(charge, 42.0f, 58.0f) * (0.80 + 0.20 * blockFrac));
            default -> (float) (Mth.lerp(charge, 18.0f, 26.0f) * (0.85 + 0.15 * blockFrac));
        };
        float baseDmg = rawDmg * cfg.damageMultiplier;
        int igniteSecs = t == 3 ? 6 : (t == 2 ? 4 : 3);

        UUID previousSelf = SELF_DAMAGE_CASTER.get();
        SELF_DAMAGE_CASTER.set(player.getUUID());
        int hitCount = 0;
        try {
            AABB aoe = new AABB(impactPos, impactPos).inflate(radius, 4.5 + t * 1.5, radius);
            SpellDamage.hurtExtras(world, aoe, world.damageSources().fallingBlock(player),
                    e -> {
                        double d = e.position().distanceTo(impactPos);
                        return d <= radius ? 2.0 * Math.max(baseDmg * 0.35, (1.0 - d / radius) * baseDmg) : 0.0;
                    });
            for (LivingEntity entity : world.getEntitiesOfClass(LivingEntity.class, aoe,
                    e -> e.isAlive() && !SpellDamage.isCaster(e, player))) {
                double dist = entity.position().distanceTo(impactPos);
                if (dist <= radius && SpellDamage.affects(entity, player)) {
                    double falloff = Math.max(t == 3 ? 0.45 : 0.35, 1.0 - dist / radius);
                    float dmg = (float) (baseDmg * falloff) * (entity instanceof Mob ? 1.5f : 1.0f);
                    SpellDamage.hurt(entity, world.damageSources().fallingBlock(player), dmg);
                    entity.igniteForSeconds(igniteSecs);
                    Vec3 out = entity.position().subtract(impactPos);
                    Vec3 horiz = new Vec3(out.x, 0, out.z);
                    Vec3 dir = horiz.lengthSqr() > 1e-4 ? horiz.normalize() : new Vec3(1, 0, 0);
                    double push = (1.05 + t * 0.55) * falloff;
                    entity.setDeltaMovement(entity.getDeltaMovement().add(dir.scale(push)).add(0, 0.55 + t * 0.28, 0));
                    entity.hurtMarked = true;
                    hitCount++;
                }
            }
        } finally {
            if (previousSelf == null) SELF_DAMAGE_CASTER.remove();
            else SELF_DAMAGE_CASTER.set(previousSelf);
        }

        // Tier-scaled volcanic crater (Tier I shallow 3m -> Tier II volcanic 5.4m -> Tier III CATACLYSM 9.0m burning bowl!)
        if (cfg.terrain(cfg.blockDominionTerrainDamage)) {
            double craterR = t == 3 ? (8.4 + charge * 1.0) : (t == 2 ? (4.8 + charge * 0.8) : 2.9);
            carveVolcanicCrater(world, impactPos, craterR, t, player);
        }

        BlockState groundState = world.getBlockState(player.blockPosition().below());
        if (groundState.isAir()) {
            groundState = primaryMat;
        }
        // Clean radial debris & fiery ember ring (zero boxy multi-square vanilla explosion clouds!)
        world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundState),
                impactPos.x, impactPos.y + 0.3, impactPos.z, 40 * t, 1.4 * t, 0.45 * t, 1.4 * t, 0.36 + t * 0.08);
        world.sendParticles(ParticleTypes.FLAME, impactPos.x, impactPos.y + 0.3, impactPos.z,
                34 * t, 1.3 * t, 0.4 * t, 1.3 * t, 0.22 + t * 0.08);
        world.sendParticles(ParticleTypes.LAVA, impactPos.x, impactPos.y + 0.4, impactPos.z,
                16 * t, 1.2 * t, 0.35 * t, 1.2 * t, 0.12);
        world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, impactPos.x, impactPos.y + 0.4, impactPos.z,
                18 * t, 1.4 * t, 0.45 * t, 1.4 * t, 0.08 + t * 0.03);

        Services.NETWORK.sendToNearby(world, impactPos, SYNC_RADIUS,
                new CannonPressurePayload(impactPos.add(0, 0.2, 0), new Vec3(0, -1, 0), t >= 2 ? 3 : 2, new Vec3(0, 1, 0)));
        Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                CinematicFxPayload.EVENT_TRAUMA_SHAKE, impactPos.x, impactPos.y, impactPos.z,
                t == 3 ? 0.58f : (t == 2 ? 0.44f : 0.32f), t == 3 ? 18 : (t == 2 ? 14 : 10)));
        Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                CinematicFxPayload.EVENT_FOV_PUNCH, impactPos.x, impactPos.y, impactPos.z,
                t == 3 ? 1.12f : (t == 2 ? 1.08f : 1.05f), t == 3 ? 10 : 8));

        if (hitCount > 0) {
            player.displayClientMessage(Component.translatable(
                    t == 3 ? "message.godwand.dominion_meteor_cataclysm" : "message.godwand.dominion_meteor_strike",
                    hitCount), true);
        }
    }

    /**
     * Carves a clean, perfectly circular hemispherical volcanic asteroid crater (no blocky jagged corners!),
     * scorching exposed crater floor blocks into magma/basalt and scattering embers/fire on Tier II & Tier III!
     */
    private static void carveVolcanicCrater(ServerLevel world, Vec3 center, double radius, int tier, Entity owner) {
        int r = Mth.ceil(radius);
        int maxDepth = Mth.ceil(radius * 0.68);
        BlockPos origin = BlockPos.containing(center.x, center.y - 0.35, center.z);
        boolean removedDisplacement = false;
        java.util.Random rng = new java.util.Random(world.getGameTime() * 31L + origin.asLong());

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double hDist = Math.sqrt(dx * dx + dz * dz);
                if (hDist > radius + 0.8) continue;
                for (int dy = 2; dy >= -maxDepth; dy--) {
                    // Clean true ellipsoid bowl (strictly circular in XZ, smoothly rounded bowl bottom!)
                    double ny = dy < 0 ? (dy / 0.68) : (dy * 0.90);
                    double dist = Math.sqrt(dx * dx + ny * ny + dz * dz);
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!world.isInWorldBounds(pos) || !world.hasChunkAt(pos)) continue;
                    BlockState st = world.getBlockState(pos);
                    if (st.isAir() || st.getDestroySpeed(world, pos) < 0 || world.getBlockEntity(pos) != null) continue;
                    if (dist <= radius) {
                        if (SpatialDisplacementManager.getActiveRecord(world.dimension(), pos) != null) {
                            SpatialDisplacementManager.removeActiveRecord(world.dimension(), pos);
                            removedDisplacement = true;
                        }
                        world.destroyBlock(pos, false, owner, 0);
                    } else if (tier >= 2 && dist <= radius + 1.15 && dy <= 0 && st.getFluidState().isEmpty()) {
                        // Scorch the circular crater bowl crust into glowing Magma / Basalt on Tier II & Tier III!
                        if (SpatialDisplacementManager.getActiveRecord(world.dimension(), pos) == null) {
                            double roll = rng.nextDouble();
                            if (roll < (tier == 3 ? 0.26 : 0.15)) {
                                world.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                            } else if (roll < (tier == 3 ? 0.46 : 0.28)) {
                                world.setBlock(pos, Blocks.BASALT.defaultBlockState(), Block.UPDATE_CLIENTS);
                            }
                        }
                    }
                }
                // Scatter burning fire pockets strictly inside the circular crater rim on Tier II & Tier III!
                if (tier >= 2 && hDist <= radius - 0.5 && rng.nextDouble() < (tier == 3 ? 0.22 : 0.11)) {
                    for (int dy = 2; dy >= -maxDepth - 1; dy--) {
                        BlockPos firePos = origin.offset(dx, dy, dz);
                        BlockPos below = firePos.below();
                        if (!world.isInWorldBounds(firePos) || !world.isInWorldBounds(below) || !world.hasChunkAt(firePos)) continue;
                        if (world.getBlockState(firePos).isAir() && world.getBlockState(below).isSolidRender(world, below)) {
                            world.setBlock(firePos, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
                            break;
                        }
                    }
                }
            }
        }
        if (removedDisplacement) DisplacementPersistentState.getServerState(world).setDirty();
    }

    private static void stopWingFlightInternal(ServerPlayer player, PlayerState st, boolean broadcast) {
        if (!st.wingFlying) return;
        st.wingFlying = false;
        st.wingBoosting = false;
        st.meteorDiveActive = false;
        st.meteorDiveTicks = 0;
        st.meteorDiveTier = 0;
        st.meteorDiveStartY = Double.NaN;
        st.flightForward = 0;
        st.flightStrafe = 0;
        st.flightVertical = 0;
        st.flightBoostInput = false;
        st.flightVel = Vec3.ZERO;
        player.setNoGravity(false);
        player.fallDistance = 0.0f;
        if (broadcast) {
            st.formation = st.stowed ? BlockDominionBrain.FormationKind.STOWED : BlockDominionBrain.FormationKind.IDLE;
            sync(player, st, BlockDominionRules.EVENT_FLIGHT_END, player.getX(), player.getY() + 1.25, player.getZ(), 0);
        }
    }

    public static boolean isSelfDamage(ServerPlayer player) {
        UUID caster = SELF_DAMAGE_CASTER.get();
        return caster != null && player != null && caster.equals(player.getUUID());
    }

    public static boolean holdsBlockDominion(Player player) {
        if (player == null) return false;
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof GodWandItem && GodWandItem.getSkillMode(main) == GodWandItem.MODE_BLOCK_DOMINION) {
            return true;
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof GodWandItem && GodWandItem.getSkillMode(off) == GodWandItem.MODE_BLOCK_DOMINION;
    }

    private static boolean inCutscene(ServerPlayer player) {
        return AtomicManager.invulnerable(player)
                || FugaManager.invulnerable(player)
                || SilentGateManager.invulnerable(player)
                || BlueRedManager.invulnerable(player);
    }

    /** Handles client input packets (Shift+LMB cycle mode, LMB pickup, double-tap Space wing flight, 3D flight controls). */
    public static void handleInput(ServerPlayer player, BlockDominionInputPayload payload) {
        if (player == null || !player.isAlive() || player.isSpectator()) return;
        if (GodWandConfig.SERVER.skillDisabled(GodWandItem.MODE_BLOCK_DOMINION)) return;
        ServerLevel world = player.serverLevel();
        if (!GodWandConfig.SERVER.isDimensionAllowed(world.dimension().location().toString())) {
            player.displayClientMessage(Component.translatable("message.godwand.dimension_blocked"), true);
            return;
        }
        if (payload.action() == BlockDominionInputPayload.ACTION_TOGGLE_FLIGHT) {
            PlayerState st = STATES.get(player.getUUID());
            if (st != null && st.wingFlying) {
                setWingFlight(player, false);
            } else if (holdsBlockDominion(player) || (st != null && st.mode == BlockDominionRules.Mode.MANUAL)) {
                if (!setWingFlight(player, true) && st != null && st.mode == BlockDominionRules.Mode.MANUAL
                        && st.reserve.size() < BlockDominionRules.MIN_FLIGHT_BLOCKS) {
                    player.displayClientMessage(Component.translatable("message.godwand.dominion_wings_need",
                            BlockDominionRules.MIN_FLIGHT_BLOCKS), true);
                }
            }
            return;
        }
        if (payload.action() == BlockDominionInputPayload.ACTION_FLIGHT_CONTROL) {
            setFlightControls(player, payload.flightForward(), payload.flightStrafe(),
                    payload.flightVertical(), payload.flightBoost(), true);
            return;
        }
        if (payload.action() == BlockDominionInputPayload.ACTION_TOGGLE_FLIGHT_STYLE) {
            toggleFlightStyle(player);
            return;
        }
        if (payload.action() == BlockDominionInputPayload.ACTION_STAIR_INTENT) {
            PlayerState st = STATES.get(player.getUUID());
            if (st != null && st.mode == BlockDominionRules.Mode.MANUAL && !st.wingFlying) {
                st.stairAscending = payload.stairAscending();
                if (payload.stairAscending()) {
                    st.stairAscendUntil = world.getGameTime() + 6;
                }
            }
            return;
        }
        if (!holdsBlockDominion(player)) return;
        if (payload.action() == BlockDominionInputPayload.ACTION_CYCLE_MODE) {
            cycleMode(player);
        } else if (payload.action() == BlockDominionInputPayload.ACTION_PICKUP) {
            tryPickUpBlock(player, payload.targetPos());
        } else if (payload.action() == BlockDominionInputPayload.ACTION_EARTHEN_SURGE) {
            triggerEarthenSurge(player);
        } else if (payload.action() == BlockDominionInputPayload.ACTION_CAGE_ENTITY) {
            triggerMonolithCage(player, payload.cageEntityId());
        }
    }

    /**
     * Monolith Cage ("Lồng Đá Phong Ấn + Mũi Giáo Xuyên Tâm"):
     * - Permanently consumes 8 orbiting blocks per cast; up to 3 active cages simultaneously!
     * - Phase 1: Slams 8 reserve blocks into an architectural hexagonal stone prison lifting the target midair.
     * - Phase 2: Forges 4 converging sculpted stone lances aimed straight through the victim's core.
     * - Phase 3: Impales straight through the victim (auto at tick 38, or IMMEDIATELY on demand if Left-Clicked again!).
     * - If the victim is slain by the impale, crossed stone lances remain skewered into the ground at the death site!
     */
    public static boolean triggerMonolithCage(ServerPlayer player, int targetEntityId) {
        if (player == null || !player.isAlive() || player.isSpectator()) return false;
        PlayerState st = STATES.get(player.getUUID());
        if (st == null || st.mode != BlockDominionRules.Mode.MANUAL || st.stowed || st.hiddenInCutscene) return false;
        ServerLevel world = player.serverLevel();
        long now = world.getGameTime();

        // Re-clicking an already-caged target triggers immediate manual Stone Lance Impale execution!
        for (ActiveCage existingCage : st.activeCages) {
            if (existingCage.targetId == targetEntityId) {
                if (!existingCage.impaled && now - existingCage.startTick >= 8
                        && world.dimension().equals(existingCage.dimension)) {
                    Entity existing = world.getEntity(targetEntityId);
                    if (existing instanceof LivingEntity cagedTarget && cagedTarget.isAlive()) {
                        executeCageImpale(world, player, st, existingCage, cagedTarget, now);
                        return true;
                    }
                }
                return false;
            }
        }

        if (st.activeCages.size() >= BlockDominionRules.CAGE_MAX_ACTIVE) {
            player.displayClientMessage(Component.translatable("message.godwand.dominion_cage_limit",
                    st.activeCages.size(), BlockDominionRules.CAGE_MAX_ACTIVE), true);
            return false;
        }
        if (now - st.lastCageTick < BlockDominionRules.CAGE_COOLDOWN_TICKS) return false;
        int cost = BlockDominionRules.CAGE_BLOCK_COST;
        if (st.reserve.size() < cost) {
            player.displayClientMessage(Component.translatable("message.godwand.dominion_cage_need", cost), true);
            return false;
        }
        Entity raw = world.getEntity(targetEntityId);
        if (!(raw instanceof LivingEntity target) || !target.isAlive()
                || SpellDamage.isCaster(target, player) || !SpellDamage.affects(target, player)) {
            return false;
        }
        if (target.distanceToSqr(player) > BlockDominionRules.CAGE_RANGE * BlockDominionRules.CAGE_RANGE) {
            return false;
        }

        List<BlockState> cageBlocks = new ArrayList<>(cost);
        for (int i = 0; i < cost; i++) {
            cageBlocks.add(st.reserve.remove(st.reserve.size() - 1));
        }
        Vec3 anchor = target.position().add(0, 1.45, 0);
        st.activeCages.add(new ActiveCage(target.getId(), cageBlocks, anchor,
                now, now + BlockDominionRules.CAGE_DURATION_TICKS, world.dimension()));
        st.lastCageTick = now;

        target.setNoGravity(true);
        target.setDeltaMovement(0, 0.30, 0);
        target.hurtMarked = true;

        world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, cageBlocks.get(0)),
                anchor.x, anchor.y, anchor.z, 22, 0.65, 0.85, 0.65, 0.14);
        player.displayClientMessage(Component.translatable("message.godwand.dominion_cage",
                cost, st.activeCages.size(), BlockDominionRules.CAGE_MAX_ACTIVE), true);
        sync(player, st, BlockDominionRules.EVENT_CAGE_START, anchor.x, anchor.y, anchor.z, target.getId());
        return true;
    }

    /** Executes Phase 3 of Monolith Cage: drives 4 sculpted stone lances violently through the sealed target. */
    private static void executeCageImpale(ServerLevel world, ServerPlayer player, PlayerState st,
                                          ActiveCage cage, LivingEntity target, long now) {
        if (cage == null || cage.impaled) return;
        cage.impaled = true;
        cage.impaleTick = now;

        Vec3 hitPos = target.position().add(0, target.getBbHeight() * 0.52, 0);
        var cfg = GodWandConfig.SERVER;
        float dmg = cfg.blockDominionDamage * 2.35f * cfg.damageMultiplier * (target instanceof Mob ? 1.5f : 1.0f);

        UUID previousSelf = SELF_DAMAGE_CASTER.get();
        SELF_DAMAGE_CASTER.set(player.getUUID());
        try {
            if (SpellDamage.affects(target, player)) {
                SpellDamage.hurt(target, world.damageSources().fallingBlock(player), dmg);
            }
        } finally {
            if (previousSelf == null) SELF_DAMAGE_CASTER.remove();
            else SELF_DAMAGE_CASTER.set(previousSelf);
        }

        boolean killed = !target.isAlive() || target.isDeadOrDying();
        cage.victimKilled = killed;
        if (killed) {
            // Let the slain target collapse onto the crossed impaling lances while the monument lingers on clients!
            target.setNoGravity(false);
            target.setDeltaMovement(0, -0.35, 0);
            target.hurtMarked = true;
            cage.endTick = Math.min(cage.endTick, now + 12);
        } else {
            target.setDeltaMovement(Vec3.ZERO);
            target.hurtMarked = true;
            cage.endTick = Math.min(cage.endTick, now + 22);
        }

        BlockState spearMat = cage.blocks.isEmpty() ? Blocks.DEEPSLATE.defaultBlockState() : cage.blocks.get(0);
        world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, spearMat),
                hitPos.x, hitPos.y, hitPos.z, 34, 0.75, 0.75, 0.75, 0.28);
        world.sendParticles(ParticleTypes.CRIT, hitPos.x, hitPos.y, hitPos.z, 24, 0.55, 0.55, 0.55, 0.25);
        world.sendParticles(ParticleTypes.SWEEP_ATTACK, hitPos.x, hitPos.y, hitPos.z, 3, 0.45, 0.45, 0.45, 0.0);

        Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                CinematicFxPayload.EVENT_TRAUMA_SHAKE, hitPos.x, hitPos.y, hitPos.z, 0.30f, 9));
        player.displayClientMessage(Component.translatable("message.godwand.dominion_cage_impale"), true);

        int packed = (target.getId() << 1) | (killed ? 1 : 0);
        sync(player, st, BlockDominionRules.EVENT_CAGE_IMPALE, hitPos.x, hitPos.y, hitPos.z, packed);
    }

    /** Releases a single Monolith Cage (the 8 consumed blocks are spent and NOT refunded!). */
    private static void releaseCage(ServerPlayer player, PlayerState st, ActiveCage cage, boolean broadcast) {
        if (cage == null) return;
        st.activeCages.remove(cage);
        ServerLevel world = player.serverLevel();
        if (world.dimension().equals(cage.dimension)) {
            Entity raw = world.getEntity(cage.targetId);
            if (raw instanceof LivingEntity target) {
                target.setNoGravity(false);
                target.fallDistance = 0.0f;
            }
        }
        if (broadcast) {
            sync(player, st, BlockDominionRules.EVENT_CAGE_END, cage.anchor.x, cage.anchor.y, cage.anchor.z, cage.targetId);
        }
    }

    private static void releaseAllCages(ServerPlayer player, PlayerState st, boolean broadcast) {
        if (st.activeCages.isEmpty()) return;
        for (ActiveCage cage : new ArrayList<>(st.activeCages)) {
            releaseCage(player, st, cage, broadcast);
        }
    }

    /**
     * Earthen Surge ("Hút Lốc Địa Hình"): rips up to 24 surface blocks in an 8-block radius spiral
     * around the player straight into the orbiting armada in one seismic tectonic pulse!
     */
    public static int triggerEarthenSurge(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return 0;
        PlayerState st = stateOf(player.getUUID());
        if (st.mode == BlockDominionRules.Mode.OFF) {
            player.displayClientMessage(Component.translatable("message.godwand.dominion_hint_off"), true);
            return 0;
        }
        if (st.mode != BlockDominionRules.Mode.MANUAL || st.volleyCharging || !st.volleyQueue.isEmpty()) {
            return 0;
        }
        ServerLevel world = player.serverLevel();
        long now = world.getGameTime();
        if (now - st.lastSurgeTick < BlockDominionRules.SURGE_COOLDOWN_TICKS) {
            return 0;
        }
        int capacity = BlockDominionRules.clampCapacity(GodWandConfig.SERVER.blockDominionCapacity);
        int room = capacity - st.reserve.size();
        if (room <= 0) {
            player.displayClientMessage(Component.translatable("message.godwand.dominion_full", st.reserve.size(), capacity), true);
            return 0;
        }

        int wanted = Math.min(room, BlockDominionRules.SURGE_MAX_BLOCKS);
        BlockPos feet = player.blockPosition();
        BlockPos under1 = feet.below();
        BlockPos under2 = under1.below();
        int r = BlockDominionRules.SURGE_RADIUS;
        List<BlockPos> candidates = new ArrayList<>();
        int minY = Math.max(world.getMinBuildHeight(), feet.getY() - 5);
        int maxY = Math.min(world.getMaxBuildHeight() - 1, feet.getY() + 3);

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int h2 = dx * dx + dz * dz;
                if (h2 < 4 || h2 > r * r) continue; // keep a 2-block safe footing island right under the player
                int bx = feet.getX() + dx;
                int bz = feet.getZ() + dz;
                if (!world.getChunkSource().hasChunk(bx >> 4, bz >> 4)) continue;
                for (int y = maxY; y >= minY; y--) {
                    BlockPos pos = new BlockPos(bx, y, bz);
                    if (pos.equals(under1) || pos.equals(under2)) continue;
                    if (!canPickUpWorldBlock(world, pos)) continue;
                    if (world.getBlockState(pos.above()).isAir()) {
                        candidates.add(pos);
                        break;
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.godwand.dominion_no_nearby"), true);
            return 0;
        }

        // Sort outward in a dramatic spiral wave (near -> far)
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(feet)));
        int lifted = 0;
        BlockState lastState = Blocks.STONE.defaultBlockState();
        for (BlockPos pos : candidates) {
            if (lifted >= wanted) break;
            BlockState bs = world.getBlockState(pos);
            if (world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS)) {
                st.reserve.add(bs);
                lastState = bs;
                lifted++;
                if (lifted % 2 == 0) {
                    world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, bs),
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.25, 0.25, 0.25, 0.14);
                }
            }
        }
        if (lifted == 0) return 0;

        st.lastSurgeTick = now;
        GodWandAdvancements.award(player, "use_9");
        Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                CinematicFxPayload.EVENT_TRAUMA_SHAKE, player.getX(), player.getY(), player.getZ(), 0.36f, 10));
        Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                CinematicFxPayload.EVENT_FOV_PUNCH, player.getX(), player.getY(), player.getZ(), 1.07f, 8));
        player.displayClientMessage(Component.translatable("message.godwand.dominion_surge", lifted, st.reserve.size(), capacity), true);
        sync(player, st, BlockDominionRules.EVENT_SURGE, player.getX(), player.getY(), player.getZ(), Block.getId(lastState));
        return lifted;
    }

    /** Shift + Left Click: cycles OFF -> MANUAL -> AUTO -> OFF. */
    public static void cycleMode(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        PlayerState st = stateOf(player.getUUID());
        BlockDominionRules.Mode previous = st.mode;
        BlockDominionRules.Mode next = previous.next();

        cancelVolleyInternal(player, st, false);
        stopWingFlightInternal(player, st, false);
        releaseAllCages(player, st, true);
        st.airStairsActive = false;
        st.liquidStrideActive = false;
        st.sovereignPerchActive = false;
        st.hoverIdleTicks = 0;
        st.rapidFiring = false;
        st.volleyQueue.clear();

        if (previous == BlockDominionRules.Mode.MANUAL && next != BlockDominionRules.Mode.MANUAL) {
            dropReserveSpiral(player, st, false);
        }

        st.mode = next;
        st.stowed = !holdsBlockDominion(player);
        st.hiddenInCutscene = inCutscene(player);

        switch (next) {
            case MANUAL -> {
                int recalled = tryRecallBlocks(player, st);
                if (recalled > 0) {
                    player.displayClientMessage(Component.translatable("message.godwand.dominion_manual_recall", recalled), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.godwand.dominion_manual"), true);
                }
                GodWandAdvancements.award(player, "use_9");
            }
            case AUTO -> {
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        UNFOLD_SFX, SoundSource.PLAYERS, 0.80f, 1.24f);
                player.displayClientMessage(Component.translatable("message.godwand.dominion_auto"), true);
                GodWandAdvancements.award(player, "use_9");
            }
            case OFF -> {
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        UNFOLD_SFX, SoundSource.PLAYERS, 0.65f, 0.68f);
                player.displayClientMessage(Component.translatable("message.godwand.dominion_off"), true);
            }
        }
        sync(player, st, next == BlockDominionRules.Mode.MANUAL ? BlockDominionRules.EVENT_UNFOLD : BlockDominionRules.EVENT_SYNC,
                player.getX(), player.getY() + 1.2, player.getZ(), 0);
    }

    private static boolean canPickUpWorldBlock(ServerLevel world, BlockPos pos) {
        if (!world.isInWorldBounds(pos) || !world.hasChunkAt(pos)) return false;
        BlockState state = world.getBlockState(pos);
        boolean isAir = state.isAir();
        boolean isFluid = !state.getFluidState().isEmpty();
        boolean hasBe = state.hasBlockEntity();
        float speed = state.getDestroySpeed(world, pos);
        boolean spatial = SpatialDisplacementManager.isPositionDisplaced(world.dimension(), pos);
        boolean protectedBlock = state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER)
                || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.NETHER_PORTAL);
        return BlockDominionRules.canPickUp(isAir, isFluid, hasBe, speed, spatial, protectedBlock);
    }

    /** Left click (or hold) on a block in MANUAL mode lifts it into the orbiting reserve. */
    public static boolean tryPickUpBlock(ServerPlayer player, BlockPos pos) {
        PlayerState st = stateOf(player.getUUID());
        if (st.mode == BlockDominionRules.Mode.OFF) {
            player.displayClientMessage(Component.translatable("message.godwand.dominion_hint_off"), true);
            return false;
        }
        if (st.mode != BlockDominionRules.Mode.MANUAL) {
            return false;
        }
        if (st.volleyCharging || !st.volleyQueue.isEmpty()) return false;

        int capacity = BlockDominionRules.clampCapacity(GodWandConfig.SERVER.blockDominionCapacity);
        if (st.reserve.size() >= capacity) {
            player.displayClientMessage(Component.translatable("message.godwand.dominion_full", st.reserve.size(), capacity), true);
            return false;
        }

        ServerLevel world = player.serverLevel();
        if (pos == null || player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > PICKUP_REACH * PICKUP_REACH) {
            return false;
        }
        if (!canPickUpWorldBlock(world, pos)) return false;

        BlockState state = world.getBlockState(pos);
        if (!world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS)) {
            return false;
        }
        st.reserve.add(state);
        long now = world.getGameTime();
        boolean continuousHold = (now - st.lastPickupTick) <= 6;
        st.lastPickupTick = now;

        // Holding left-click rips a cluster of up to 2 extra adjacent blocks so players summon a massive swarm rapidly
        if (continuousHold && st.reserve.size() < capacity) {
            int extra = 0;
            BlockPos[] neighbors = {
                    pos.north(), pos.south(), pos.east(), pos.west(),
                    pos.above(), pos.north().east(), pos.south().west(), pos.below()
            };
            for (BlockPos nb : neighbors) {
                if (extra >= 2 || st.reserve.size() >= capacity) break;
                if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(nb)) > (PICKUP_REACH + 1.0) * (PICKUP_REACH + 1.0)) continue;
                if (!canPickUpWorldBlock(world, nb)) continue;
                BlockState ns = world.getBlockState(nb);
                if (world.setBlock(nb, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS)) {
                    st.reserve.add(ns);
                    extra++;
                    world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ns),
                            nb.getX() + 0.5, nb.getY() + 0.5, nb.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.1);
                }
            }
        }
        GodWandAdvancements.award(player, "use_9");

        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), cx, cy, cz, 12, 0.25, 0.25, 0.25, 0.12);

        player.displayClientMessage(Component.translatable("message.godwand.dominion_count", st.reserve.size(), capacity), true);
        sync(player, st, BlockDominionRules.EVENT_PICKUP, cx, cy, cz, Block.getId(state));
        return true;
    }

    /** Right-click handler from GodWandItem.use. Returns true if the wand should enter continuous use (rapid fire or volley). */
    public static boolean beginRightClick(ServerPlayer player) {
        PlayerState st = stateOf(player.getUUID());
        if (st.mode == BlockDominionRules.Mode.OFF) {
            player.displayClientMessage(Component.translatable("message.godwand.dominion_hint_off"), true);
            return false;
        }
        if (st.mode == BlockDominionRules.Mode.AUTO) {
            // Auto mode: tap right-click grabs nearest terrain block and fires 1 shot (no rapid fire, no volley)
            fireAutoSingle(player, st);
            return false;
        }
        // MANUAL mode
        if (st.reserve.isEmpty()) {
            // Instant Earthen Surge when right-clicking with an empty reserve!
            triggerEarthenSurge(player);
            return false;
        }
        if (player.isShiftKeyDown()) {
            if (!SkillCooldowns.ready(player, GodWandItem.MODE_BLOCK_DOMINION)) return false;
            st.volleyCharging = true;
            st.volleyChargeTicks = 0;
            st.volleyAerial = st.wingFlying;
            st.rapidFiring = false;
            sync(player, st, BlockDominionRules.EVENT_SYNC, player.getX(), player.getY() + 1.2, player.getZ(), 0);
            return true;
        }
        // Normal right-click: fire 1 block immediately, and enter rapid-fire hold (raises Floating Railgun Ring!)
        st.volleyCharging = false;
        st.rapidFiring = true;
        st.rapidShotsFired = 0;
        fireManualShot(player, st, 0);
        st.rapidShotsFired = 1;
        st.nextRapidFireTick = BlockDominionRules.rapidFireInterval(1);
        return !st.reserve.isEmpty();
    }

    /** Called every use tick while right-click is held on Block Dominion. */
    public static void useTick(ServerPlayer player, int usedTicks) {
        PlayerState st = STATES.get(player.getUUID());
        if (st == null || st.mode != BlockDominionRules.Mode.MANUAL) {
            player.stopUsingItem();
            return;
        }
        if (st.volleyCharging) {
            if (st.reserve.isEmpty()) {
                cancelVolleyInternal(player, st, true);
                player.stopUsingItem();
                return;
            }
            st.volleyChargeTicks = usedTicks;
            ServerLevel world = player.serverLevel();
            if (usedTicks == BlockDominionRules.VOLLEY_MIN_CHARGE_TICKS) {
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        PICKUP_SFX, SoundSource.PLAYERS, 0.55f, 1.22f);
            } else if (usedTicks == BlockDominionRules.VOLLEY_CHARGE_TICKS) {
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        PICKUP_SFX, SoundSource.PLAYERS, 0.75f, 1.48f);
            }
            if (usedTicks % 6 == 0 && usedTicks <= BlockDominionRules.VOLLEY_CHARGE_TICKS) {
                Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                        CinematicFxPayload.EVENT_TRAUMA_SHAKE, player.getX(), player.getY(), player.getZ(),
                        0.08f + Math.min(1f, usedTicks / 20f) * 0.14f, 6));
            }
            int bars = Math.min(4, 1 + (usedTicks * 3) / BlockDominionRules.VOLLEY_CHARGE_TICKS);
            player.displayClientMessage(Component.literal("§eMyriad Volley: " + GodWandItem.chargeMeter(bars, 4)), true);
            if (usedTicks % 2 == 0) {
                sync(player, st, BlockDominionRules.EVENT_SYNC, player.getX(), player.getY() + 1.2, player.getZ(), 0);
            }
            return;
        }
        if (st.rapidFiring) {
            if (st.reserve.isEmpty()) {
                st.rapidFiring = false;
                player.stopUsingItem();
                return;
            }
            if (usedTicks >= st.nextRapidFireTick) {
                fireManualShot(player, st, st.rapidShotsFired);
                st.rapidShotsFired++;
                st.nextRapidFireTick = usedTicks + BlockDominionRules.rapidFireInterval(st.rapidShotsFired);
            }
        }
    }

    /** Called when right-click is released on Block Dominion. */
    public static void releaseRightClick(ServerPlayer player) {
        PlayerState st = STATES.get(player.getUUID());
        if (st == null) return;
        boolean wasRapid = st.rapidFiring;
        st.rapidFiring = false;
        if (!st.volleyCharging) {
            if (wasRapid) {
                sync(player, st, BlockDominionRules.EVENT_SYNC, player.getX(), player.getY() + 1.2, player.getZ(), 0);
            }
            return;
        }

        int charged = st.volleyChargeTicks;
        boolean aerial = st.volleyAerial || st.wingFlying;
        st.volleyCharging = false;
        st.volleyChargeTicks = 0;
        st.volleyAerial = false;

        if (charged < BlockDominionRules.VOLLEY_MIN_CHARGE_TICKS || st.reserve.isEmpty()) {
            sync(player, st, BlockDominionRules.EVENT_SYNC, player.getX(), player.getY() + 1.2, player.getZ(), 0);
            return;
        }

        // Launch Myriad Volley (ground fan) or Aerial Orbital Meteor Barrage (when soaring on Block Wings!)
        int count = st.reserve.size();
        int[] order = BlockDominionRules.volleyReleaseOrder(count);
        List<BlockState> snapshot = new ArrayList<>(st.reserve);
        st.reserve.clear();
        st.volleyQueue.clear();
        st.volleySilenceLeft = BlockDominionRules.VOLLEY_SILENCE_TICKS;

        if (aerial) {
            for (int i = 0; i < order.length; i++) {
                int slot = order[i];
                int ring = slot % 3;
                int ringIdx = slot / 3;
                int ringCount = Math.max(1, (count + 2 - ring) / 3);
                double ang = (Math.PI * 2.0 * ringIdx / ringCount);
                double r = 2.15 + ring * 1.45;
                double sideOff = Math.cos(ang) * r;
                double fwdOff = Math.sin(ang) * r * 0.72 + 0.55;
                double upOff = 2.6 + ring * 0.85;
                st.volleyQueue.add(new QueuedShot(snapshot.get(slot), sideOff, upOff, fwdOff, i / 2));
            }
            // Safe landing cushion for 4 seconds after expending Block Wings into an Orbital Meteor Barrage
            st.safeFallUntil = player.serverLevel().getGameTime() + 80;
        } else {
            int rows = count > 16 ? 2 : 1;
            for (int i = 0; i < order.length; i++) {
                int slot = order[i];
                int row = rows == 2 ? (slot % 2) : 0;
                int col = rows == 2 ? (slot / 2) : slot;
                int colCount = rows == 2 ? (row == 0 ? (count + 1) / 2 : count / 2) : count;
                double u = colCount <= 1 ? 0.0 : (col / (double) (colCount - 1)) * 2.0 - 1.0;
                double theta = u * (Math.PI * 0.39);
                double r = row == 0 ? 2.6 : 3.4;
                st.volleyQueue.add(new QueuedShot(snapshot.get(slot), Math.sin(theta) * r, Math.cos(theta) * r * 0.7 + 0.35, 0.8, i / 2));
            }
        }

        SkillCooldowns.start(player, GodWandItem.MODE_BLOCK_DOMINION,
                GodWandConfig.skillCooldownTicks(GodWandItem.MODE_BLOCK_DOMINION, 1));
        Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                CinematicFxPayload.EVENT_FOV_PUNCH, player.getX(), player.getY(), player.getZ(), aerial ? 1.16f : 1.12f, 12));
        Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                CinematicFxPayload.EVENT_TRAUMA_SHAKE, player.getX(), player.getY(), player.getZ(), aerial ? 0.65f : 0.55f, 16));
        player.displayClientMessage(Component.translatable(
                aerial ? "message.godwand.dominion_orbital" : "message.godwand.dominion_volley", count), true);
        sync(player, st, aerial ? BlockDominionRules.EVENT_ORBITAL_BARRAGE : BlockDominionRules.EVENT_VOLLEY,
                player.getX(), player.getY() + 1.2, player.getZ(), 0);
    }

    public static void cancelCharge(ServerPlayer player) {
        PlayerState st = STATES.get(player.getUUID());
        if (st == null) return;
        st.rapidFiring = false;
        cancelVolleyInternal(player, st, true);
    }

    private static void cancelVolleyInternal(ServerPlayer player, PlayerState st, boolean broadcast) {
        if (!st.volleyCharging) return;
        st.volleyCharging = false;
        st.volleyChargeTicks = 0;
        if (broadcast) {
            sync(player, st, BlockDominionRules.EVENT_SYNC, player.getX(), player.getY() + 1.2, player.getZ(), 0);
        }
    }

    private static void fireManualShot(ServerPlayer player, PlayerState st, int burstIndex) {
        if (st.reserve.isEmpty()) return;
        BlockState block = st.reserve.remove(st.reserve.size() - 1);
        Vec3 look = player.getViewVector(1.0f).normalize();
        Vec3 spawnPos = player.getEyePosition().add(look.scale(1.45));
        launchProjectile(player, block, spawnPos, look, false);

        Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                CinematicFxPayload.EVENT_TRAUMA_SHAKE, player.getX(), player.getY(), player.getZ(), 0.12f, 4));
        sync(player, st, BlockDominionRules.EVENT_FIRE, spawnPos.x, spawnPos.y, spawnPos.z, Block.getId(block));
    }

    private static void fireAutoSingle(ServerPlayer player, PlayerState st) {
        ServerLevel world = player.serverLevel();
        BlockPos found = findNearestAutoBlock(world, player, BlockDominionRules.AUTO_SCAN_RADIUS);
        if (found == null) {
            player.displayClientMessage(Component.translatable("message.godwand.dominion_no_nearby"), true);
            return;
        }
        BlockState block = world.getBlockState(found);
        if (!world.setBlock(found, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS)) {
            return;
        }
        world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block),
                found.getX() + 0.5, found.getY() + 0.5, found.getZ() + 0.5, 10, 0.2, 0.2, 0.2, 0.1);
        Vec3 look = player.getViewVector(1.0f).normalize();
        Vec3 spawnPos = player.getEyePosition().add(look.scale(1.45));
        launchProjectile(player, block, spawnPos, look, false);
        sync(player, st, BlockDominionRules.EVENT_FIRE, found.getX() + 0.5, found.getY() + 0.5, found.getZ() + 0.5, Block.getId(block));
    }

    private static void launchProjectile(ServerPlayer player, BlockState state, Vec3 origin, Vec3 direction, boolean volley) {
        double speed = GodWandConfig.SERVER.blockDominionProjectileSpeed;
        DominionBlockProjectileEntity proj = new DominionBlockProjectileEntity(player.level(), player, state, volley);
        proj.setPos(origin.x, origin.y, origin.z);
        proj.setDeltaMovement(direction.normalize().scale(speed));
        player.level().addFreshEntity(proj);
    }

    /** Damage defense called from LivingEntityMixin on LivingEntity.hurt(DamageSource, float). */
    public static boolean tryAbsorbDamage(ServerPlayer player, DamageSource source, float amount) {
        if (player == null || amount <= 0 || !player.isAlive()) return false;

        boolean bypasses = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        boolean alreadyInvuln = player.isCreative() || player.isSpectator() || player.getAbilities().invulnerable
                || inCutscene(player);
        boolean ownDamage = isSelfDamage(player)
                || (source.getDirectEntity() instanceof DominionBlockProjectileEntity proj && SpellDamage.isCaster(proj.getOwner(), player));

        PlayerState st = STATES.get(player.getUUID());
        BlockDominionRules.Mode mode = st == null ? BlockDominionRules.Mode.OFF : st.mode;
        ServerLevel world = player.serverLevel();
        long now = world.getGameTime();
        boolean inPostParry = st != null && st.postParryInvulnUntil >= now;

        BlockPos autoPos = null;
        if (mode == BlockDominionRules.Mode.AUTO && !bypasses && !alreadyInvuln && !ownDamage && !inPostParry) {
            autoPos = findNearestAutoBlock(world, player, BlockDominionRules.AUTO_SCAN_RADIUS);
        }

        BlockDominionRules.AbsorbResult decision = BlockDominionRules.decideAbsorb(
                mode,
                st == null ? 0 : st.reserve.size(),
                autoPos != null,
                bypasses,
                alreadyInvuln,
                ownDamage,
                inPostParry
        );

        if (decision == BlockDominionRules.AbsorbResult.NONE) return false;
        if (decision == BlockDominionRules.AbsorbResult.FREE_INVULN) {
            mitigateStatusEffects(player, source);
            return true;
        }

        BlockState parriedState;
        if (decision == BlockDominionRules.AbsorbResult.CONSUME_RESERVE) {
            parriedState = st.reserve.remove(st.reserve.size() - 1);
        } else {
            // CONSUME_AUTO_BLOCK
            parriedState = world.getBlockState(autoPos);
            if (!world.setBlock(autoPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS)) {
                return false;
            }
            world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, parriedState),
                    autoPos.getX() + 0.5, autoPos.getY() + 0.5, autoPos.getZ() + 0.5, 10, 0.25, 0.25, 0.25, 0.1);
        }

        st.postParryInvulnUntil = now + BlockDominionRules.PARRY_INVULN_TICKS;
        mitigateStatusEffects(player, source);

        // Determine threat direction for shield flash & auto-counter
        Vec3 playerCenter = player.position().add(0, 1.15, 0);
        Vec3 threatDir = player.getViewVector(1.0f).normalize();
        if (source.getSourcePosition() != null) {
            Vec3 delta = source.getSourcePosition().subtract(playerCenter);
            if (delta.lengthSqr() > 1e-4) threatDir = delta.normalize();
        } else if (source.getEntity() != null) {
            Vec3 delta = source.getEntity().position().add(0, source.getEntity().getBbHeight() * 0.5, 0).subtract(playerCenter);
            if (delta.lengthSqr() > 1e-4) threatDir = delta.normalize();
        }

        // Kinetic Deflect: reflect direct incoming hostile projectile straight back at shooter!
        if (source.getDirectEntity() instanceof Projectile incoming && !(incoming instanceof DominionBlockProjectileEntity)) {
            deflectProjectile(world, player, incoming);
        }

        // Auto mode: counter-launch the parried block straight back at the attacker!
        if (decision == BlockDominionRules.AbsorbResult.CONSUME_AUTO_BLOCK
                && source.getEntity() instanceof LivingEntity attacker
                && !SpellDamage.isCaster(attacker, player)
                && SpellDamage.affects(attacker, player)
                && attacker.isAlive()) {
            Vec3 target = attacker.position().add(0, attacker.getBbHeight() * 0.5, 0);
            Vec3 spawn = playerCenter.add(threatDir.scale(1.5));
            Vec3 dir = target.subtract(spawn);
            if (dir.lengthSqr() > 1e-4) {
                launchProjectile(player, parriedState, spawn, dir.normalize(), false);
            }
        }

        // Parry VFX & audio (client plays custom CC0 dominion_parry on EVENT_PARRY)
        Vec3 parryPoint = playerCenter.add(threatDir.scale(1.35));
        world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, parriedState),
                parryPoint.x, parryPoint.y, parryPoint.z, 18, 0.3, 0.3, 0.3, 0.18);
        world.sendParticles(ParticleTypes.CRIT, parryPoint.x, parryPoint.y, parryPoint.z, 8, 0.2, 0.2, 0.2, 0.12);
        Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                CinematicFxPayload.EVENT_TRAUMA_SHAKE, player.getX(), player.getY(), player.getZ(), 0.28f, 6));
        sync(player, st, BlockDominionRules.EVENT_PARRY, threatDir.x, threatDir.y, threatDir.z, Block.getId(parriedState));
        return true;
    }

    private static void mitigateStatusEffects(ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE)) player.clearFire();
        if (source.is(DamageTypeTags.IS_FALL)) player.fallDistance = 0.0f;
        if (source.is(DamageTypeTags.IS_DROWNING)) player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 40));
    }

    /** Impact handler for DominionBlockProjectileEntity. */
    public static void onProjectileImpact(DominionBlockProjectileEntity projectile, HitResult hit) {
        if (!(projectile.level() instanceof ServerLevel world)) return;
        Vec3 hitPos = hit.getLocation();
        Vec3 dir = projectile.getDeltaMovement().lengthSqr() > 1e-6 ? projectile.getDeltaMovement().normalize() : new Vec3(0, -1, 0);
        Vec3 surfaceNormal = hit instanceof BlockHitResult bhr
                ? Vec3.atLowerCornerOf(bhr.getDirection().getNormal())
                : dir.reverse();
        Entity owner = projectile.getOwner();
        BlockState state = projectile.getBlockState();
        var cfg = GodWandConfig.SERVER;

        float baseDamage = cfg.blockDominionDamage * cfg.damageMultiplier;
        double blastRadius = cfg.blockDominionBlastRadius;
        double craterRadius = cfg.blockDominionCraterRadius;

        UUID previousSelf = SELF_DAMAGE_CASTER.get();
        if (owner != null) SELF_DAMAGE_CASTER.set(owner.getUUID());
        try {
            if (hit instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity victim
                    && !SpellDamage.isCaster(victim, owner) && projectile.markHit(victim)) {
                float dmg = baseDamage * (victim instanceof Mob ? 2.0f : 1.0f);
                SpellDamage.hurt(victim, world.damageSources().fallingBlock(owner), dmg);
            }

            AABB aoe = new AABB(hitPos, hitPos).inflate(blastRadius);
            SpellDamage.hurtExtras(world, aoe, world.damageSources().fallingBlock(owner),
                    e -> e.position().distanceTo(hitPos) <= blastRadius
                            ? 2.0 * Math.max(baseDamage * 0.35, (1.0 - e.position().distanceTo(hitPos) / blastRadius) * baseDamage)
                            : 0.0);

            for (LivingEntity entity : world.getEntitiesOfClass(LivingEntity.class, aoe,
                    e -> e.isAlive() && !SpellDamage.isCaster(e, owner))) {
                double dist = entity.position().distanceTo(hitPos);
                if (dist <= blastRadius && SpellDamage.affects(entity, owner) && projectile.markHit(entity)) {
                    double falloff = Math.max(0.35, 1.0 - dist / blastRadius);
                    float splashDmg = (float) (baseDamage * falloff) * (entity instanceof Mob ? 2.0f : 1.0f);
                    SpellDamage.hurt(entity, world.damageSources().fallingBlock(owner), splashDmg);
                    Vec3 out = entity.position().subtract(hitPos);
                    Vec3 push = out.lengthSqr() > 1e-4 ? out.normalize().scale(0.95).add(0, 0.35, 0) : new Vec3(0, 0.5, 0);
                    entity.setDeltaMovement(entity.getDeltaMovement().add(push));
                    entity.hurtMarked = true;
                }
            }
        } finally {
            if (previousSelf == null) SELF_DAMAGE_CASTER.remove();
            else SELF_DAMAGE_CASTER.set(previousSelf);
        }

        // Small terrain crater (gated by server config & protected-block rules)
        if (craterRadius > 0.1 && cfg.terrain(cfg.blockDominionTerrainDamage)) {
            carveSmallCrater(world, hitPos, craterRadius, owner);
        }

        // Reused pressure wave & debris VFX + crisp CC0 impact audio
        Services.NETWORK.sendToNearby(world, hitPos, SYNC_RADIUS,
                new CannonPressurePayload(hitPos, dir, projectile.isVolley() ? 2 : 1, surfaceNormal));
        world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                hitPos.x, hitPos.y, hitPos.z, 22, 0.45, 0.45, 0.45, 0.22);
        world.sendParticles(ParticleTypes.EXPLOSION, hitPos.x, hitPos.y, hitPos.z, 1, 0.1, 0.1, 0.1, 0.0);
        world.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                IMPACT_SFX, SoundSource.PLAYERS, projectile.isVolley() ? 1.15f : 0.92f,
                0.92f + (float) (Math.random() * 0.16));
    }

    private static void carveSmallCrater(ServerLevel world, Vec3 center, double radius, Entity owner) {
        int r = Mth.ceil(radius);
        double r2 = radius * radius;
        BlockPos origin = BlockPos.containing(center);
        boolean removedDisplacement = false;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!world.isInWorldBounds(pos) || !world.hasChunkAt(pos)) continue;
                    BlockState st = world.getBlockState(pos);
                    if (st.isAir() || st.getDestroySpeed(world, pos) < 0) continue;
                    if (SpatialDisplacementManager.getActiveRecord(world.dimension(), pos) != null) {
                        SpatialDisplacementManager.removeActiveRecord(world.dimension(), pos);
                        removedDisplacement = true;
                    }
                    world.destroyBlock(pos, false, owner, 0);
                }
            }
        }
        if (removedDisplacement) DisplacementPersistentState.getServerState(world).setDirty();
    }

    /** Per-player server tick: handles stow/unfold transitions, cutscene hiding, volley release queue and heartbeat sync. */
    public static void tickPlayer(ServerPlayer player) {
        PlayerState st = STATES.get(player.getUUID());
        if (st == null) return;
        ServerLevel world = player.serverLevel();
        long now = world.getGameTime();

        // Expire old recall snapshots quietly
        if (st.recall != null && !BlockDominionRules.canRecall(now, st.recall.deactivatedAtTick(), st.recall.died())) {
            st.recall = null;
        }

        if (st.mode == BlockDominionRules.Mode.OFF && st.volleyQueue.isEmpty()) {
            if (st.recall == null) STATES.remove(player.getUUID());
            return;
        }

        // Validate player still has wand & allowed dimension & skill enabled
        if (!player.isAlive() || player.isSpectator()
                || GodWandConfig.SERVER.skillDisabled(GodWandItem.MODE_BLOCK_DOMINION)
                || !GodWandConfig.SERVER.isDimensionAllowed(world.dimension().location().toString())
                || !SpatialPathWalker.hasGodWand(player)) {
            deactivate(player, !player.isAlive());
            return;
        }

        // Process staggered center-out volley release queue
        if (!st.volleyQueue.isEmpty()) {
            if (st.volleySilenceLeft > 0) {
                st.volleySilenceLeft--;
            } else {
                Vec3 aimDir = player.getViewVector(1.0f).normalize();
                HitResult targetHit = player.pick(64.0, 0.0f, false);
                Vec3 convergeTarget = targetHit.getType() == HitResult.Type.MISS
                        ? player.getEyePosition().add(aimDir.scale(48.0))
                        : targetHit.getLocation();
                Vec3 side = aimDir.cross(Math.abs(aimDir.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
                Vec3 camUp = side.cross(aimDir).normalize();
                Vec3 chest = player.position().add(0, 1.25, 0);

                // Launch up to 3 shots per tick for a sweeping meteor-storm feel
                for (int launched = 0; launched < 3 && !st.volleyQueue.isEmpty(); launched++) {
                    QueuedShot shot = st.volleyQueue.poll();
                    Vec3 origin = chest.add(side.scale(shot.sideOffset())).add(camUp.scale(shot.upOffset())).add(aimDir.scale(shot.fwdOffset()));
                    Vec3 dir = convergeTarget.subtract(origin);
                    if (dir.lengthSqr() < 1e-4) dir = aimDir;
                    launchProjectile(player, shot.state(), origin, dir.normalize(), true);
                }
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        FIRE_SFX, SoundSource.PLAYERS, 0.58f, 1.05f + (float) (Math.random() * 0.30));
            }
        }

        boolean newStowed = !holdsBlockDominion(player);
        boolean newHidden = inCutscene(player);
        boolean stateChanged = newStowed != st.stowed || newHidden != st.hiddenInCutscene;
        boolean unfolded = st.stowed && !newStowed;
        boolean stowedNow = !st.stowed && newStowed;
        st.stowed = newStowed;
        st.hiddenInCutscene = newHidden;

        if (stowedNow) {
            cancelVolleyInternal(player, st, false);
            st.rapidFiring = false;
            if (!st.reserve.isEmpty()) {
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        UNFOLD_SFX, SoundSource.PLAYERS, 0.42f, 1.32f);
            }
        }

        // Wing Flight & Air-Step Stairs mobility controllers
        int mobilityEvent = BlockDominionRules.EVENT_SYNC;
        int mobEventBlockId = 0;
        double mobEx = player.getX(), mobEy = player.getY() + 1.2, mobEz = player.getZ();
        boolean mobilityChanged = false;

        if (st.wingFlying) {
            boolean grounded = player.onGround() || (st.meteorDiveActive && player.verticalCollisionBelow);
            boolean landingFromFlight = grounded && !st.volleyCharging
                    && (st.meteorDiveActive || (st.wingGlideMode && st.flightVel.y <= 0.08)
                    || (st.flightVertical < 0 && st.flightVel.y <= 0.02));
            if (st.mode != BlockDominionRules.Mode.MANUAL || st.hiddenInCutscene
                    || st.reserve.size() < BlockDominionRules.MIN_FLIGHT_BLOCKS
                    || landingFromFlight) {
                // Only trigger Meteor Strike if Tier I+ actually ignited during a deliberate steep plunge!
                boolean meteorImpact = landingFromFlight && st.meteorDiveActive && st.meteorDiveTier >= 1;
                boolean heroLand = landingFromFlight && !meteorImpact
                        && (st.flightVel.lengthSqr() > 0.62 * 0.62 || st.flightVel.y < -0.18);
                Vec3 landVel = st.flightVel;
                double dropBlocks = Double.isNaN(st.meteorDiveStartY) ? 0.0 : Math.max(0.0, st.meteorDiveStartY - player.getY());
                int impactTier = Math.max(1, st.meteorDiveTier);
                float impactCharge = BlockDominionRules.meteorDiveCharge(st.meteorDiveTicks, dropBlocks, st.reserve.size());
                stopWingFlightInternal(player, st, false);
                if (meteorImpact) {
                    // Multi-tier Meteor Strike detonation: blazing volcanic crater + apocalyptic AoE + zero fall damage!
                    st.safeFallUntil = now + 20;
                    detonateMeteorStrike(world, player, st, landVel, impactTier, impactCharge);
                    mobilityEvent = BlockDominionRules.EVENT_METEOR_STRIKE;
                    mobEventBlockId = impactTier;
                    mobEy = player.getY() + 0.15;
                } else if (heroLand) {
                    // Superhero touchdown: damp horizontal slide, zero fall damage, shockwave + trauma!
                    player.setDeltaMovement(landVel.x * 0.25, 0.0, landVel.z * 0.25);
                    player.hurtMarked = true;
                    Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                            CinematicFxPayload.EVENT_TRAUMA_SHAKE, player.getX(), player.getY(), player.getZ(), 0.24f, 8));
                    mobilityEvent = BlockDominionRules.EVENT_HERO_LAND;
                    mobEy = player.getY() + 0.1;
                } else {
                    mobilityEvent = BlockDominionRules.EVENT_FLIGHT_END;
                }
                mobilityChanged = true;
            } else {
                if (player.getAbilities().flying) {
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
                player.setNoGravity(true);
                player.fallDistance = 0.0f;
                boolean wasBoosting = st.wingBoosting;
                int prevMeteorTier = st.meteorDiveTier;
                int blockCount = st.reserve.size();
                if (st.volleyCharging) {
                    // Lock into regal midair hover while charging Aerial Orbital Meteor Barrage
                    st.flightVel = BlockDominionRules.stepFlightVelocity(
                            st.flightVel, player.getViewVector(1.0f), 0, 0, Math.max(0, st.flightVertical), false, blockCount);
                    st.wingBoosting = false;
                } else if (st.wingGlideMode) {
                    boolean glideBoost = st.flightBoostInput || st.flightVertical > 0;
                    st.flightVel = BlockDominionRules.stepGlideVelocity(
                            st.flightVel, player.getViewVector(1.0f), st.flightStrafe, glideBoost, blockCount);
                    st.wingBoosting = glideBoost && st.flightVel.lengthSqr() > 0.65 * 0.65;
                } else {
                    st.flightVel = BlockDominionRules.stepFlightVelocity(
                            st.flightVel, player.getViewVector(1.0f),
                            st.flightForward, st.flightStrafe, st.flightVertical, st.flightBoostInput, blockCount);
                    st.wingBoosting = st.flightBoostInput && st.flightVel.lengthSqr() > 0.55 * 0.55;
                }
                player.setDeltaMovement(st.flightVel);

                // Multi-Tier Meteor Dive Escalation (Tier I -> Tier II Inferno Comet -> Tier III CATACLYSM!)
                boolean divingNow = !st.volleyCharging
                        && BlockDominionRules.isSteepMeteorDive(player.getViewVector(1.0f), st.flightVel, st.wingGlideMode);
                if (divingNow) {
                    if (Double.isNaN(st.meteorDiveStartY) || player.getY() > st.meteorDiveStartY) {
                        st.meteorDiveStartY = player.getY();
                    }
                    st.meteorDiveTicks = Math.min(120, st.meteorDiveTicks + 1);
                } else {
                    st.meteorDiveTicks = Math.max(0, st.meteorDiveTicks - 2);
                    if (st.meteorDiveTicks == 0) st.meteorDiveStartY = Double.NaN;
                }
                double dropBlocks = Double.isNaN(st.meteorDiveStartY) ? 0.0 : Math.max(0.0, st.meteorDiveStartY - player.getY());
                int nextTier = BlockDominionRules.meteorDiveTier(st.meteorDiveTicks, dropBlocks, blockCount);
                st.meteorDiveTier = nextTier;
                st.meteorDiveActive = nextTier >= 1;
                if (nextTier != prevMeteorTier) {
                    mobilityChanged = true;
                    if (nextTier == 1 && prevMeteorTier == 0) {
                        Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                                CinematicFxPayload.EVENT_FOV_PUNCH, player.getX(), player.getY(), player.getZ(), 1.04f, 6));
                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                METEOR_IGNITE_SFX, SoundSource.PLAYERS, 0.72f, 1.18f);
                    } else if (nextTier == 2 && prevMeteorTier < 2) {
                        player.displayClientMessage(Component.translatable("message.godwand.dominion_meteor_tier2"), true);
                        Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                                CinematicFxPayload.EVENT_FOV_PUNCH, player.getX(), player.getY(), player.getZ(), 1.05f, 7));
                        Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                                CinematicFxPayload.EVENT_TRAUMA_SHAKE, player.getX(), player.getY(), player.getZ(), 0.14f, 5));
                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                METEOR_IGNITE_SFX, SoundSource.PLAYERS, 0.95f, 0.96f);
                    } else if (nextTier == 3 && prevMeteorTier < 3) {
                        player.displayClientMessage(Component.translatable("message.godwand.dominion_meteor_tier3"), true);
                        Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                                CinematicFxPayload.EVENT_FOV_PUNCH, player.getX(), player.getY(), player.getZ(), 1.06f, 8));
                        Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                                CinematicFxPayload.EVENT_TRAUMA_SHAKE, player.getX(), player.getY(), player.getZ(), 0.18f, 6));
                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                METEOR_IGNITE_SFX, SoundSource.PLAYERS, 1.25f, 0.76f);
                    }
                }
                // Keep camera rock-steady during plunge (zero jerky periodic camera shake, zero lens-blocking sprites!)
                if (nextTier >= 2 && (now % 3 == 0)) {
                    world.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() - 0.65, player.getZ(),
                            nextTier == 3 ? 3 : 2, 0.45, 0.15, 0.45, 0.03);
                }

                // Sovereign Perch: calm idle Hover for ~1.7s -> materialize floating stone pedestal & fold guardian wings!
                boolean idleHover = !st.wingGlideMode && !st.volleyCharging && !st.wingBoosting && !st.meteorDiveActive
                        && st.flightForward == 0 && st.flightStrafe == 0 && st.flightVertical == 0
                        && st.flightVel.lengthSqr() < 0.015;
                if (idleHover) {
                    st.hoverIdleTicks = Math.min(200, st.hoverIdleTicks + 1);
                } else {
                    st.hoverIdleTicks = 0;
                }
                boolean perchNow = st.hoverIdleTicks >= BlockDominionRules.PERCH_IDLE_TICKS;
                if (perchNow != st.sovereignPerchActive) {
                    st.sovereignPerchActive = perchNow;
                    mobilityChanged = true;
                }
                // Only force-push velocity to client when not driven by client's own synchronized prediction
                if (now - st.lastFlightPacketTick > 4) {
                    player.hurtMarked = true;
                }
                if (st.wingBoosting && !wasBoosting) {
                    mobilityChanged = true;
                    mobilityEvent = BlockDominionRules.EVENT_FLIGHT_BOOST;
                    Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                            CinematicFxPayload.EVENT_FOV_PUNCH, player.getX(), player.getY(), player.getZ(), 1.13f, 10));
                    Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                            CinematicFxPayload.EVENT_TRAUMA_SHAKE, player.getX(), player.getY(), player.getZ(), 0.16f, 6));
                } else if (!st.wingBoosting && wasBoosting) {
                    mobilityChanged = true;
                }
            }
        } else {
            st.hoverIdleTicks = 0;
            st.meteorDiveTicks = 0;
            st.meteorDiveStartY = Double.NaN;
            if (st.sovereignPerchActive || st.meteorDiveActive || st.meteorDiveTier != 0) {
                st.sovereignPerchActive = false;
                st.meteorDiveActive = false;
                st.meteorDiveTier = 0;
                mobilityChanged = true;
            }
        }

        // Tick up to 3 simultaneous active Monolith Cages: Phase 1 seal -> Phase 2 spear forging -> Phase 3 impale execution!
        if (!st.activeCages.isEmpty()) {
            for (ActiveCage cage : new ArrayList<>(st.activeCages)) {
                Entity raw = world.dimension().equals(cage.dimension) ? world.getEntity(cage.targetId) : null;
                if (!(raw instanceof LivingEntity target) || now >= cage.endTick
                        || (!target.isAlive() && !cage.impaled)) {
                    releaseCage(player, st, cage, true);
                    mobilityChanged = true;
                } else {
                    long cageAge = now - cage.startTick;
                    if (!cage.impaled && cageAge == BlockDominionRules.CAGE_SPEAR_FORM_TICK) {
                        world.playSound(null, cage.anchor.x, cage.anchor.y, cage.anchor.z,
                                CAGE_FORGE_SFX, SoundSource.PLAYERS, 0.92f, 1.02f);
                    }
                    if (!cage.impaled && cageAge >= BlockDominionRules.CAGE_IMPALE_TICK) {
                        executeCageImpale(world, player, st, cage, target, now);
                    } else if (target.isAlive()) {
                        Vec3 pull = cage.anchor.subtract(target.position()).scale(0.32);
                        target.setNoGravity(true);
                        target.setDeltaMovement(pull);
                        target.fallDistance = 0.0f;
                        target.hurtMarked = true;
                    }
                }
            }
        }

        // QoL Liquid Stride: automatically lay flat stone stepping slabs over water & lava surfaces!
        double liquidSurface = findLiquidStrideSurface(world, player);
        boolean wantLiquidStride = st.mode == BlockDominionRules.Mode.MANUAL && !st.stowed && !st.wingFlying
                && !st.volleyCharging && !st.hiddenInCutscene
                && st.reserve.size() >= BlockDominionRules.MIN_STAIR_BLOCKS
                && !player.isShiftKeyDown() && !player.isUnderWater()
                && !player.isPassenger() && !player.getAbilities().flying
                && !Double.isNaN(liquidSurface);
        if (wantLiquidStride != st.liquidStrideActive) {
            st.liquidStrideActive = wantLiquidStride;
            mobilityChanged = true;
            if (wantLiquidStride) {
                mobilityEvent = BlockDominionRules.EVENT_AIR_STEP;
                mobEy = liquidSurface;
                st.lastStepEventTick = now;
            }
        }
        if (st.liquidStrideActive) {
            player.fallDistance = 0.0f;
            if (player.isOnFire() || !world.getFluidState(player.blockPosition().below()).isEmpty()) {
                player.clearFire();
            }
            Vec3 cur = player.getDeltaMovement();
            Vec3 nextVel = BlockDominionRules.stepLiquidStrideVelocity(cur, player.getY(), liquidSurface, false);
            if (cur.y < nextVel.y - 0.02 || player.getY() < liquidSurface - 0.05) {
                player.setDeltaMovement(nextVel);
            }
            double horiz = Math.sqrt(cur.x * cur.x + cur.z * cur.z);
            if (horiz > 0.03 && now - st.lastStepEventTick >= 6) {
                st.lastStepEventTick = now;
                mobilityChanged = true;
                mobilityEvent = BlockDominionRules.EVENT_AIR_STEP;
                mobEy = liquidSurface;
            }
        }

        // Air-Step Block Stairs (both slow regal descending stairs AND ascending sky stairs!)
        boolean ascendIntent = st.stairAscending && now <= st.stairAscendUntil;
        boolean canStair = st.mode == BlockDominionRules.Mode.MANUAL && !st.stowed && !st.wingFlying
                && !st.liquidStrideActive
                && !st.volleyCharging && !st.hiddenInCutscene
                && st.reserve.size() >= BlockDominionRules.MIN_STAIR_BLOCKS
                && !player.isShiftKeyDown()
                && !player.isInWater() && !player.isInLava() && !player.onClimbable()
                && !player.isPassenger() && !player.getAbilities().flying;
        boolean wantStair = canStair && (
                ascendIntent
                || (!player.onGround() && (st.airStairsActive || (player.getDeltaMovement().y < 0.04 && hasDropBelow(world, player))))
        );
        if (wantStair != st.airStairsActive) {
            st.airStairsActive = wantStair;
            if (!wantStair) st.stairAscending = false;
            mobilityChanged = true;
            if (wantStair) {
                mobilityEvent = BlockDominionRules.EVENT_AIR_STEP;
                mobEy = player.getY() - 0.15;
                st.lastStepEventTick = now;
            }
        }
        if (st.airStairsActive) {
            player.fallDistance = 0.0f;
            Vec3 cur = player.getDeltaMovement();
            double horiz = Math.sqrt(cur.x * cur.x + cur.z * cur.z);
            Vec3 nextVel = BlockDominionRules.stepAirStairsVelocity(cur, horiz, ascendIntent, ascendIntent);
            if (ascendIntent || cur.y < nextVel.y - 0.03) {
                player.setDeltaMovement(nextVel);
            }
            if ((horiz > 0.025 || ascendIntent) && now - st.lastStepEventTick >= 6) {
                st.lastStepEventTick = now;
                mobilityChanged = true;
                mobilityEvent = BlockDominionRules.EVENT_AIR_STEP;
                mobEy = player.getY() - 0.15;
            }
        }

        // QoL Rain Umbrella check every 4 ticks (shelters player when standing under open rain/snow!)
        if ((now + player.getId()) % 4 == 0) {
            BlockPos headAbove = player.blockPosition().above(2);
            boolean rainNow = world.isRaining() && world.canSeeSky(headAbove);
            if (rainNow != st.rainingOverhead) {
                st.rainingOverhead = rainNow;
                mobilityChanged = true;
            }
        }

        if (st.safeFallUntil >= now) {
            player.fallDistance = 0.0f;
        }

        // Prevent dedicated Survival servers (allow-flight=false) from kicking players using Block Wings, Stairs or Liquid Stride!
        if ((st.wingFlying || st.airStairsActive || st.liquidStrideActive || st.safeFallUntil >= now) && player.connection != null) {
            ((net.godwand.mixin.ServerGamePacketListenerAccessor) player.connection).godwand$setAboveGroundTickCount(0);
        }

        // Kinetic Deflect: when a shield formation is raised, proactively catch & reflect incoming hostile projectiles!
        if (st.mode == BlockDominionRules.Mode.MANUAL && st.formation.isShield()
                && !st.hiddenInCutscene && !st.reserve.isEmpty()) {
            if (tryProactiveKineticDeflect(world, player, st, now)) {
                return;
            }
        }

        // Milestone 2+3+4+5 Block Brain: scan threats every 2 ticks and update defensive/mobility/offensive/QoL formation
        boolean brainChanged = false;
        if (st.mode == BlockDominionRules.Mode.MANUAL && !st.hiddenInCutscene
                && ((now + player.getId()) % 2 == 0 || stateChanged || mobilityChanged)) {
            List<BlockDominionBrain.ThreatSample> threats = st.reserve.isEmpty() || st.volleyCharging
                    ? List.of() : scanThreats(world, player);
            BlockDominionBrain.BrainPlan plan = BlockDominionBrain.plan(
                    threats, st.reserve.size(), st.stowed, st.volleyCharging,
                    st.wingFlying, st.wingBoosting, st.airStairsActive, st.rapidFiring, st.rainingOverhead,
                    st.liquidStrideActive, st.sovereignPerchActive, st.meteorDiveActive);
            if (plan.kind() != st.formation
                    || (plan.kind().isShield() && (BlockDominionBrain.directionChangedSignificantly(plan.primaryDir(), st.threatPrimary)
                    || BlockDominionBrain.directionChangedSignificantly(plan.secondaryDir(), st.threatSecondary)))) {
                st.formation = plan.kind();
                st.threatPrimary = plan.primaryDir();
                st.threatSecondary = plan.secondaryDir();
                brainChanged = true;
            }
        } else if (st.mode != BlockDominionRules.Mode.MANUAL) {
            st.formation = BlockDominionBrain.FormationKind.IDLE;
        }

        if (stateChanged || brainChanged || mobilityChanged) {
            int evt = stowedNow ? BlockDominionRules.EVENT_STOW
                    : unfolded ? BlockDominionRules.EVENT_UNFOLD
                    : mobilityEvent != BlockDominionRules.EVENT_SYNC ? mobilityEvent
                    : BlockDominionRules.EVENT_SYNC;
            sync(player, st, evt, mobEx, mobEy, mobEz, mobEventBlockId);
            return;
        }

        if (now - st.lastHeartbeatTick >= 40) {
            sync(player, st, BlockDominionRules.EVENT_SYNC, player.getX(), player.getY() + 1.2, player.getZ(), 0);
        }
    }

    /**
     * Checks if the player is walking directly atop or skimming a water or lava surface (within 0.65 blocks).
     * Returns the target standing Y atop the fluid surface, or {@code Double.NaN} if not over liquid.
     */
    private static double findLiquidStrideSurface(ServerLevel world, ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        BlockPos[] probe = {feet, feet.below()};
        for (BlockPos p : probe) {
            if (!world.hasChunkAt(p) || !world.isInWorldBounds(p)) continue;
            if (!world.getFluidState(p).isEmpty() && world.getFluidState(p.above()).isEmpty()) {
                double surface = p.getY() + 0.96;
                if (player.getY() <= surface + 0.55 && player.getY() >= p.getY() - 0.15) {
                    return surface;
                }
            }
        }
        return Double.NaN;
    }

    /**
     * Kinetic Deflect: proactively catches incoming hostile projectiles hitting the raised Block Shield
     * and fires them straight back toward their shooter!
     */
    private static boolean tryProactiveKineticDeflect(ServerLevel world, ServerPlayer player, PlayerState st, long now) {
        Vec3 center = player.position().add(0, 1.15, 0);
        AABB box = player.getBoundingBox().inflate(2.75);
        for (Entity entity : world.getEntities(player, box, Entity::isAlive)) {
            if (!(entity instanceof Projectile proj)) continue;
            if (proj instanceof DominionBlockProjectileEntity) continue;
            if (SpellDamage.isCaster(proj.getOwner(), player)) continue;
            Vec3 vel = proj.getDeltaMovement();
            if (vel.lengthSqr() < 0.04) continue;
            Vec3 toPlayer = center.subtract(proj.position());
            if (toPlayer.dot(vel) <= 0.0) continue; // Moving away already!

            boolean free = st.postParryInvulnUntil >= now;
            BlockState parriedState = free
                    ? st.reserve.get(st.reserve.size() - 1)
                    : st.reserve.remove(st.reserve.size() - 1);
            st.postParryInvulnUntil = now + BlockDominionRules.PARRY_INVULN_TICKS;

            Vec3 threatDir = proj.position().subtract(center);
            threatDir = threatDir.lengthSqr() > 1e-4 ? threatDir.normalize() : player.getViewVector(1.0f);
            deflectProjectile(world, player, proj);

            Vec3 parryPoint = center.add(threatDir.scale(1.35));
            world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, parriedState),
                    parryPoint.x, parryPoint.y, parryPoint.z, 14, 0.25, 0.25, 0.25, 0.16);
            world.sendParticles(ParticleTypes.CRIT, parryPoint.x, parryPoint.y, parryPoint.z, 8, 0.2, 0.2, 0.2, 0.14);
            Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                    CinematicFxPayload.EVENT_TRAUMA_SHAKE, player.getX(), player.getY(), player.getZ(), 0.20f, 5));
            sync(player, st, BlockDominionRules.EVENT_PARRY, threatDir.x, threatDir.y, threatDir.z, Block.getId(parriedState));
            return true;
        }
        return false;
    }

    private static void deflectProjectile(ServerLevel world, ServerPlayer defender, Projectile proj) {
        Entity origOwner = proj.getOwner();
        Vec3 shooterPos = (origOwner instanceof LivingEntity l && l.isAlive() && !SpellDamage.isCaster(l, defender))
                ? l.position().add(0, l.getBbHeight() * 0.65, 0) : null;
        Vec3 deflected = BlockDominionRules.deflectVelocity(proj.getDeltaMovement(), proj.position(), shooterPos, 1.85);
        proj.setOwner(defender);
        proj.setDeltaMovement(deflected);
        proj.hasImpulse = true;
        proj.hurtMarked = true;
    }

    /** Scans within 16 blocks for incoming projectiles, imminent explosions and hostile mobs targeting the player. */
    private static List<BlockDominionBrain.ThreatSample> scanThreats(ServerLevel world, ServerPlayer player) {
        List<BlockDominionBrain.ThreatSample> threats = new ArrayList<>();
        Vec3 center = player.position().add(0, 1.15, 0);
        AABB box = player.getBoundingBox().inflate(BlockDominionBrain.THREAT_SCAN_RADIUS);

        for (Entity entity : world.getEntities(player, box, Entity::isAlive)) {
            if (entity.isSpectator()) continue;
            if (entity instanceof Projectile proj) {
                if (proj instanceof DominionBlockProjectileEntity && SpellDamage.isCaster(proj.getOwner(), player)) continue;
                if (SpellDamage.isCaster(proj.getOwner(), player)) continue;
                if (BlockDominionBrain.isIncomingProjectile(center, proj.position(), proj.getDeltaMovement())) {
                    Vec3 dir = proj.position().subtract(center);
                    double dist = dir.length();
                    if (dist > 0.05) {
                        threats.add(new BlockDominionBrain.ThreatSample(dir, 14.0 + (16.0 - dist), false));
                    }
                }
            } else if (entity instanceof PrimedTnt tnt) {
                Vec3 dir = tnt.position().add(0, 0.5, 0).subtract(center);
                double dist = dir.length();
                if (dist <= 9.0 && dist > 0.05) {
                    threats.add(new BlockDominionBrain.ThreatSample(dir, 22.0 + (9.0 - dist), true));
                }
            } else if (entity instanceof Creeper creeper) {
                Vec3 dir = creeper.position().add(0, 0.9, 0).subtract(center);
                double dist = dir.length();
                boolean fusing = creeper.isIgnited() || creeper.getSwellDir() > 0;
                if ((fusing && dist <= 7.0) || (creeper.getTarget() == player && dist <= 5.5)) {
                    if (dist > 0.05) {
                        threats.add(new BlockDominionBrain.ThreatSample(dir, fusing ? 24.0 : 9.0, fusing));
                    }
                }
            } else if (entity instanceof Mob mob && mob.getTarget() == player) {
                Vec3 dir = mob.position().add(0, mob.getBbHeight() * 0.5, 0).subtract(center);
                double dist = dir.length();
                boolean ranged = mob instanceof RangedAttackMob;
                double maxRange = ranged ? 15.0 : 6.5;
                if (dist <= maxRange && dist > 0.05) {
                    threats.add(new BlockDominionBrain.ThreatSample(dir, (ranged ? 5.0 : 8.5) + (maxRange - dist) * 0.4, false));
                }
            }
        }
        return threats;
    }

    /** True when there is at least ~1.2 blocks of open air directly beneath the player's feet (stepping off a ledge). */
    private static boolean hasDropBelow(ServerLevel world, ServerPlayer player) {
        BlockPos feet = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
        BlockPos below1 = feet.below();
        if (!world.isInWorldBounds(below1) || !world.hasChunkAt(below1)) return false;
        return world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
                && world.getBlockState(below1).getCollisionShape(world, below1).isEmpty();
    }

    /** Deactivates Block Dominion, dropping all reserve blocks in a spiral around the player. */
    public static void deactivate(ServerPlayer player, boolean died) {
        if (player == null) return;
        PlayerState st = STATES.get(player.getUUID());
        if (st == null) return;
        cancelVolleyInternal(player, st, false);
        stopWingFlightInternal(player, st, false);
        releaseAllCages(player, st, true);
        st.airStairsActive = false;
        st.liquidStrideActive = false;
        st.sovereignPerchActive = false;
        st.hoverIdleTicks = 0;
        st.rapidFiring = false;
        st.volleyQueue.clear();
        if (!st.reserve.isEmpty()) {
            dropReserveSpiral(player, st, died);
        }
        st.mode = BlockDominionRules.Mode.OFF;
        st.stowed = false;
        sync(player, st, BlockDominionRules.EVENT_SYNC, player.getX(), player.getY(), player.getZ(), 0);
        if (died || st.recall == null) {
            STATES.remove(player.getUUID());
        }
    }

    public static void onLeave(ServerPlayer player, boolean died) {
        deactivate(player, died);
        if (died) STATES.remove(player.getUUID());
    }

    public static void clearAll() {
        STATES.clear();
        SELF_DAMAGE_CASTER.remove();
    }

    private static void dropReserveSpiral(ServerPlayer player, PlayerState st, boolean died) {
        ServerLevel world = player.serverLevel();
        int count = st.reserve.size();
        if (count == 0) return;

        int[][] offsets = BlockDominionRules.spiralDropOffsets(count);
        BlockPos origin = player.blockPosition();
        List<DroppedBlock> dropped = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            BlockState blockState = st.reserve.get(i);
            int tx = origin.getX() + offsets[i][0];
            int tz = origin.getZ() + offsets[i][1];
            if (!world.getChunkSource().hasChunk(tx >> 4, tz >> 4)) {
                continue; // Unloaded chunk: do not force-load (§1.2)
            }
            BlockPos landingPos = findDropLandingPos(world, tx, tz, origin.getY());
            if (landingPos == null) continue;
            BlockState below = world.getBlockState(landingPos.below());
            BlockState atLanding = world.getBlockState(landingPos);

            // If column is water/lava, spawn falling block with dropItem=true so it drops cleanly instead of replacing liquid
            boolean liquidColumn = !below.getFluidState().isEmpty() || !atLanding.getFluidState().isEmpty();
            int dropHeight = 1 + (i % 3);
            int spawnY = landingPos.getY();
            for (int h = 1; h <= dropHeight; h++) {
                BlockPos candidate = landingPos.above(h);
                if (world.isInWorldBounds(candidate) && world.getBlockState(candidate).isAir()) {
                    spawnY = candidate.getY();
                } else {
                    break;
                }
            }
            BlockPos spawnPos = new BlockPos(tx, spawnY, tz);
            if (!world.isInWorldBounds(spawnPos) || !world.getBlockState(spawnPos).isAir()) {
                if (!liquidColumn && world.isInWorldBounds(landingPos) && world.getBlockState(landingPos).isAir()) {
                    world.setBlock(landingPos, blockState, Block.UPDATE_ALL);
                    dropped.add(new DroppedBlock(landingPos, blockState));
                }
                continue;
            }
            FallingBlockEntity falling = FallingBlockEntity.fall(world, spawnPos, blockState);
            falling.dropItem = liquidColumn;
            if (!liquidColumn) {
                dropped.add(new DroppedBlock(landingPos, blockState));
            }
        }
        st.reserve.clear();
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                UNFOLD_SFX, SoundSource.PLAYERS, 0.72f, 0.78f);

        if (!died && !dropped.isEmpty()) {
            st.recall = new RecallSnapshot(world.dimension(), world.getGameTime(), false, dropped);
        } else {
            st.recall = null;
        }
    }

    /** Finds a safe landing BlockPos near refY (works both outdoors and inside caves/buildings without forcing chunks). */
    private static BlockPos findDropLandingPos(ServerLevel world, int x, int z, int refY) {
        int minY = Math.max(world.getMinBuildHeight() + 1, refY - 16);
        int maxY = Math.min(world.getMaxBuildHeight() - 2, refY + 6);
        int startY = Mth.clamp(refY + 2, minY, maxY);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, startY, z);
        if (!world.getBlockState(cursor).isAir()) {
            boolean foundAir = false;
            for (int y = startY + 1; y <= maxY; y++) {
                cursor.set(x, y, z);
                if (world.getBlockState(cursor).isAir()) {
                    startY = y;
                    foundAir = true;
                    break;
                }
            }
            if (!foundAir) return null;
        }
        for (int y = startY; y >= minY; y--) {
            cursor.set(x, y - 1, z);
            BlockState below = world.getBlockState(cursor);
            if (!below.isAir()) {
                return new BlockPos(x, y, z);
            }
        }
        return new BlockPos(x, minY, z);
    }

    private static int tryRecallBlocks(ServerPlayer player, PlayerState st) {
        RecallSnapshot snap = st.recall;
        st.recall = null;
        if (snap == null) return 0;
        ServerLevel world = player.serverLevel();
        if (!snap.dimension().equals(world.dimension())) return 0;
        if (!BlockDominionRules.canRecall(world.getGameTime(), snap.deactivatedAtTick(), snap.died())) return 0;

        int capacity = BlockDominionRules.clampCapacity(GodWandConfig.SERVER.blockDominionCapacity);
        int recalled = 0;
        for (DroppedBlock entry : snap.blocks()) {
            if (st.reserve.size() >= capacity) break;
            BlockPos pos = entry.pos();
            if (!world.hasChunkAt(pos) || !world.isInWorldBounds(pos)) continue;
            BlockState current = world.getBlockState(pos);
            if (current.equals(entry.state()) && canPickUpWorldBlock(world, pos)) {
                if (world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS)) {
                    st.reserve.add(current);
                    recalled++;
                    world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, current),
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.08);
                }
            }
        }
        return recalled;
    }

    /** Finds the nearest eligible solid block within {@code radius} blocks for Auto mode, avoiding the block right under feet. */
    private static BlockPos findNearestAutoBlock(ServerLevel world, ServerPlayer player, int radius) {
        BlockPos feet = player.blockPosition();
        BlockPos underFeet = feet.below();
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        int minY = Math.max(world.getMinBuildHeight(), feet.getY() - 3);
        int maxY = Math.min(world.getMaxBuildHeight() - 1, feet.getY() + 4);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int horizSq = dx * dx + dz * dz;
                if (horizSq > radius * radius) continue;
                int x = feet.getX() + dx;
                int z = feet.getZ() + dz;
                if (!world.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
                for (int y = maxY; y >= minY; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (pos.equals(underFeet) || pos.equals(underFeet.below())) continue;
                    double d2 = pos.distSqr(feet);
                    if (d2 >= bestDistSq) continue;
                    if (!canPickUpWorldBlock(world, pos)) continue;
                    // Prefer exposed blocks (air above or beside) so Auto pulls visible surface stones!
                    BlockState above = world.getBlockState(pos.above());
                    if (!above.isAir() && d2 > 4.0) continue;
                    best = pos;
                    bestDistSq = d2;
                }
            }
        }
        return best;
    }

    private static void sync(ServerPlayer player, PlayerState st, int eventKind, double ex, double ey, double ez, int eventBlockId) {
        ServerLevel world = player.serverLevel();
        st.lastHeartbeatTick = world.getGameTime();
        int flags = (st.stowed ? BlockDominionRules.FLAG_STOWED : 0)
                | (st.volleyCharging ? BlockDominionRules.FLAG_VOLLEY_CHARGING : 0)
                | (st.hiddenInCutscene ? BlockDominionRules.FLAG_HIDDEN : 0)
                | (st.wingFlying ? BlockDominionRules.FLAG_FLYING : 0)
                | (st.wingBoosting ? BlockDominionRules.FLAG_BOOSTING : 0)
                | (st.airStairsActive ? BlockDominionRules.FLAG_AIR_STAIRS : 0)
                | (st.rapidFiring ? BlockDominionRules.FLAG_RAPID_FIRING : 0)
                | (st.rainingOverhead ? BlockDominionRules.FLAG_UMBRELLA : 0)
                | (st.wingGlideMode ? BlockDominionRules.FLAG_GLIDE_MODE : 0)
                | (st.stairAscending ? BlockDominionRules.FLAG_STAIR_ASCEND : 0)
                | (st.liquidStrideActive ? BlockDominionRules.FLAG_LIQUID_STRIDE : 0)
                | (st.sovereignPerchActive ? BlockDominionRules.FLAG_PERCH : 0)
                | (st.meteorDiveActive ? BlockDominionRules.FLAG_METEOR_DIVE : 0)
                | (st.meteorDiveTier >= 2 ? BlockDominionRules.FLAG_METEOR_TIER2 : 0)
                | (st.meteorDiveTier >= 3 ? BlockDominionRules.FLAG_METEOR_TIER3 : 0);
        int[] ids = new int[st.reserve.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = Block.getId(st.reserve.get(i));
        BlockDominionBrain.FormationKind form = st.volleyCharging && !st.stowed
                ? (st.wingFlying ? BlockDominionBrain.FormationKind.ORBITAL_METEOR : BlockDominionBrain.FormationKind.VOLLEY_FAN)
                : (st.mode == BlockDominionRules.Mode.MANUAL ? st.formation : BlockDominionBrain.FormationKind.IDLE);
        BlockDominionStatePayload payload = new BlockDominionStatePayload(
                player.getId(), st.mode.ordinal(), flags, st.volleyChargeTicks,
                form.ordinal(),
                (float) st.threatPrimary.x, (float) st.threatPrimary.y, (float) st.threatPrimary.z,
                (float) st.threatSecondary.x, (float) st.threatSecondary.y, (float) st.threatSecondary.z,
                ids, eventKind, ex, ey, ez, eventBlockId);
        Services.NETWORK.sendToNearby(world, player.position(), SYNC_RADIUS, payload);
    }
}
