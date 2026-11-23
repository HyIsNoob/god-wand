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
import net.minecraft.sounds.SoundEvents;
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
        long stairAscendUntil = -1000;
        int flightForward;
        int flightStrafe;
        int flightVertical;
        boolean flightBoostInput;
        long lastFlightPacketTick = -1000;
        long lastStepEventTick = -1000;
        long lastPickupTick = -1000;
        long lastSurgeTick = -1000;
        long safeFallUntil = -1;
        Vec3 flightVel = Vec3.ZERO;
        final ArrayDeque<QueuedShot> volleyQueue = new ArrayDeque<>();
        RecallSnapshot recall;
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

    private static void stopWingFlightInternal(ServerPlayer player, PlayerState st, boolean broadcast) {
        if (!st.wingFlying) return;
        st.wingFlying = false;
        st.wingBoosting = false;
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
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.0f, 0.65f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.85f, 0.85f);
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
        st.airStairsActive = false;
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
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.15f);
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.DEEPSLATE_PLACE, SoundSource.PLAYERS, 0.9f, 0.9f);
                if (recalled > 0) {
                    player.displayClientMessage(Component.translatable("message.godwand.dominion_manual_recall", recalled), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.godwand.dominion_manual"), true);
                }
                GodWandAdvancements.award(player, "use_9");
            }
            case AUTO -> {
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.3f);
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.55f, 1.35f);
                player.displayClientMessage(Component.translatable("message.godwand.dominion_auto"), true);
                GodWandAdvancements.award(player, "use_9");
            }
            case OFF -> {
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.65f, 1.0f);
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
        world.playSound(null, cx, cy, cz, SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 0.7f, 0.85f + st.reserve.size() * 0.012f);
        world.playSound(null, cx, cy, cz, SoundEvents.BASALT_PLACE, SoundSource.PLAYERS, 0.65f, 1.25f);
        world.playSound(null, cx, cy, cz, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.22f, 1.1f + st.reserve.size() * 0.015f);

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
            ServerLevel world = player.serverLevel();
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.7f, 0.8f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.DEEPSLATE_PLACE, SoundSource.PLAYERS, 0.85f, 0.75f);
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
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75f, 1.25f);
            } else if (usedTicks == BlockDominionRules.VOLLEY_CHARGE_TICKS) {
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.9f, 1.45f);
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.45f, 1.55f);
            }
            if (usedTicks % 6 == 0 && usedTicks <= BlockDominionRules.VOLLEY_CHARGE_TICKS) {
                float pitch = 0.75f + Math.min(usedTicks, BlockDominionRules.VOLLEY_CHARGE_TICKS) * 0.03f;
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.35f, pitch);
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
            ServerLevel world = player.serverLevel();
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.DEEPSLATE_PLACE, SoundSource.PLAYERS, 0.5f, 0.7f);
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
        ServerLevel world = player.serverLevel();
        Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                CinematicFxPayload.EVENT_FOV_PUNCH, player.getX(), player.getY(), player.getZ(), aerial ? 1.16f : 1.12f, 12));
        Services.NETWORK.sendToPlayer(player, new CinematicFxPayload(
                CinematicFxPayload.EVENT_TRAUMA_SHAKE, player.getX(), player.getY(), player.getZ(), aerial ? 0.65f : 0.55f, 16));
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.05f, aerial ? 1.05f : 1.25f);
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

        ServerLevel world = player.serverLevel();
        float pitch = 0.9f + Math.min(burstIndex, 12) * 0.055f;
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BASALT_BREAK, SoundSource.PLAYERS, 0.85f, pitch);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.DEEPSLATE_PLACE, SoundSource.PLAYERS, 0.65f, pitch * 1.15f);
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
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BASALT_BREAK, SoundSource.PLAYERS, 0.9f, 1.15f);
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

        // Discard direct incoming hostile projectile so it doesn't stick/re-hit
        if (source.getDirectEntity() instanceof Projectile incoming && !(incoming instanceof DominionBlockProjectileEntity)) {
            incoming.discard();
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

        // Parry VFX & audio (§1.4 #3)
        Vec3 parryPoint = playerCenter.add(threatDir.scale(1.35));
        world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, parriedState),
                parryPoint.x, parryPoint.y, parryPoint.z, 18, 0.3, 0.3, 0.3, 0.18);
        world.sendParticles(ParticleTypes.CRIT, parryPoint.x, parryPoint.y, parryPoint.z, 8, 0.2, 0.2, 0.2, 0.12);
        world.playSound(null, parryPoint.x, parryPoint.y, parryPoint.z,
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.55f, 1.45f);
        world.playSound(null, parryPoint.x, parryPoint.y, parryPoint.z,
                SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 0.95f, 0.95f);
        world.playSound(null, parryPoint.x, parryPoint.y, parryPoint.z,
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.85f, 1.15f);
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

        // Reused pressure wave & debris VFX + crisp impact sounds
        Services.NETWORK.sendToNearby(world, hitPos, SYNC_RADIUS,
                new CannonPressurePayload(hitPos, dir, projectile.isVolley() ? 2 : 1, surfaceNormal));
        world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                hitPos.x, hitPos.y, hitPos.z, 22, 0.45, 0.45, 0.45, 0.22);
        world.sendParticles(ParticleTypes.EXPLOSION, hitPos.x, hitPos.y, hitPos.z, 1, 0.1, 0.1, 0.1, 0.0);
        world.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.0f, 0.72f);
        world.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.BASALT_BREAK, SoundSource.PLAYERS, 0.85f, 0.65f);
        world.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                IMPACT_SFX, SoundSource.PLAYERS, projectile.isVolley() ? 1.15f : 0.85f,
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
                        SoundEvents.BASALT_BREAK, SoundSource.PLAYERS, 0.7f, 1.05f + (float) (Math.random() * 0.35));
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
                        SoundEvents.DEEPSLATE_PLACE, SoundSource.PLAYERS, 0.45f, 1.35f);
            }
        } else if (unfolded && !st.reserve.isEmpty()) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.DEEPSLATE_PLACE, SoundSource.PLAYERS, 0.65f, 0.95f);
        }

        // Wing Flight & Air-Step Stairs mobility controllers
        int mobilityEvent = BlockDominionRules.EVENT_SYNC;
        double mobEx = player.getX(), mobEy = player.getY() + 1.2, mobEz = player.getZ();
        boolean mobilityChanged = false;

        if (st.wingFlying) {
            boolean landingFromFlight = player.onGround() && !st.volleyCharging
                    && ((st.wingGlideMode && st.flightVel.y <= 0.08) || (st.flightVertical < 0 && st.flightVel.y <= 0.02));
            if (st.mode != BlockDominionRules.Mode.MANUAL || st.hiddenInCutscene
                    || st.reserve.size() < BlockDominionRules.MIN_FLIGHT_BLOCKS
                    || landingFromFlight) {
                boolean heroLand = landingFromFlight
                        && (st.flightVel.lengthSqr() > 0.62 * 0.62 || st.flightVel.y < -0.18);
                Vec3 landVel = st.flightVel;
                stopWingFlightInternal(player, st, false);
                if (heroLand) {
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
                if (st.volleyCharging) {
                    // Lock into regal midair hover while charging Aerial Orbital Meteor Barrage
                    st.flightVel = BlockDominionRules.stepFlightVelocity(
                            st.flightVel, player.getViewVector(1.0f), 0, 0, Math.max(0, st.flightVertical), false);
                    st.wingBoosting = false;
                } else if (st.wingGlideMode) {
                    boolean glideBoost = st.flightBoostInput || st.flightVertical > 0;
                    st.flightVel = BlockDominionRules.stepGlideVelocity(
                            st.flightVel, player.getViewVector(1.0f), st.flightStrafe, glideBoost);
                    st.wingBoosting = glideBoost && st.flightVel.lengthSqr() > 0.65 * 0.65;
                } else {
                    st.flightVel = BlockDominionRules.stepFlightVelocity(
                            st.flightVel, player.getViewVector(1.0f),
                            st.flightForward, st.flightStrafe, st.flightVertical, st.flightBoostInput);
                    st.wingBoosting = st.flightBoostInput && st.flightVel.lengthSqr() > 0.55 * 0.55;
                }
                player.setDeltaMovement(st.flightVel);
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
        }

        // Air-Step Block Stairs (both slow regal descending stairs AND ascending sky stairs!)
        boolean ascendIntent = st.stairAscending && now <= st.stairAscendUntil;
        boolean canStair = st.mode == BlockDominionRules.Mode.MANUAL && !st.stowed && !st.wingFlying
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

        // Prevent dedicated Survival servers (allow-flight=false) from kicking players using Block Wings or Air Stairs!
        if ((st.wingFlying || st.airStairsActive || st.safeFallUntil >= now) && player.connection != null) {
            ((net.godwand.mixin.ServerGamePacketListenerAccessor) player.connection).godwand$setAboveGroundTickCount(0);
        }

        // Milestone 2+3+4 Block Brain: scan threats every 2 ticks and update defensive/mobility/offensive/QoL formation
        boolean brainChanged = false;
        if (st.mode == BlockDominionRules.Mode.MANUAL && !st.hiddenInCutscene
                && ((now + player.getId()) % 2 == 0 || stateChanged || mobilityChanged)) {
            List<BlockDominionBrain.ThreatSample> threats = st.reserve.isEmpty() || st.volleyCharging
                    ? List.of() : scanThreats(world, player);
            BlockDominionBrain.BrainPlan plan = BlockDominionBrain.plan(
                    threats, st.reserve.size(), st.stowed, st.volleyCharging,
                    st.wingFlying, st.wingBoosting, st.airStairsActive, st.rapidFiring, st.rainingOverhead);
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
            sync(player, st, evt, mobEx, mobEy, mobEz, 0);
            return;
        }

        if (now - st.lastHeartbeatTick >= 40) {
            sync(player, st, BlockDominionRules.EVENT_SYNC, player.getX(), player.getY() + 1.2, player.getZ(), 0);
        }
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
        st.airStairsActive = false;
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
                SoundEvents.DEEPSLATE_FALL, SoundSource.PLAYERS, 0.85f, 0.85f);

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
                | (st.stairAscending ? BlockDominionRules.FLAG_STAIR_ASCEND : 0);
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
