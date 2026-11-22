package net.godwand.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.godwand.config.GodWandConfig;
import net.godwand.network.BlockDominionStatePayload;
import net.godwand.skill.BlockDominionBrain;
import net.godwand.skill.BlockDominionRules;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * Milestone 3B Block Dominion renderer — Purple-grade volumetric visual standard:
 * - Two-pass custom GLSL rendering: BODY (alpha-blended smoke, torn ink/gold brush strokes,
 *   dark-core lightning) followed by GLOW (additive liquid plasma ribbons, hot lightning cores,
 *   soft volumetric glows, organic dissolving hex mist, and GPU 3D particles)
 * - Sculpted multi-scale anatomical Block Wings (curved arch + long splayed stone feathers + coverts)
 *   augmented by flowing plasma feather plumes, wingbeat smoke clouds, and arch energy veins
 * - Terrifying massive stone armada with procedural satellite monolith fragments
 * - Second-order damped spring-damper physics with frame-motion carry.
 */
public final class BlockDominionRenderer {
    private static final int WING_TRAIL_LEN = 14;

    private static final class VisualBlock {
        BlockState state;
        Vec3 pos;
        Vec3 prevPos;
        Vec3 vel = Vec3.ZERO;
        Vec3 scale;
        Vec3 prevScale;
        float spinPhase;
        Vec3 pickupOrigin;
        long pickupStartTick = -1000;

        VisualBlock(BlockState state, Vec3 startPos, float startScale, float spinPhase) {
            this.state = state;
            this.pos = startPos;
            this.prevPos = startPos;
            this.scale = new Vec3(startScale, startScale, startScale);
            this.prevScale = this.scale;
            this.spinPhase = spinPhase;
        }
    }

    private static final class ParryBurst {
        final int playerId;
        final Vec3 direction;
        final BlockState state;
        final long startTick;

        ParryBurst(int playerId, Vec3 direction, BlockState state, long startTick) {
            this.playerId = playerId;
            this.direction = direction.lengthSqr() > 1e-4 ? direction.normalize() : new Vec3(0, 0, 1);
            this.state = state;
            this.startTick = startTick;
        }
    }

    private static final class WaveBurst {
        final int playerId;
        final Vec3 axis;
        final double maxRadius;
        final int duration;
        final long startTick;
        final long seed;

        WaveBurst(int playerId, Vec3 axis, double maxRadius, int duration, long startTick) {
            this.playerId = playerId;
            this.axis = axis.lengthSqr() > 1e-4 ? axis.normalize() : new Vec3(0, 1, 0);
            this.maxRadius = maxRadius;
            this.duration = duration;
            this.startTick = startTick;
            this.seed = startTick * 37L + playerId * 13L;
        }
    }

    private static final class StepFlash {
        final Vec3 pos;
        final long startTick;

        StepFlash(Vec3 pos, long startTick) {
            this.pos = pos;
            this.startTick = startTick;
        }
    }

    private static final class ClientFormation {
        BlockDominionRules.Mode mode = BlockDominionRules.Mode.OFF;
        BlockDominionBrain.FormationKind formation = BlockDominionBrain.FormationKind.IDLE;
        Vec3 threatPrimary = new Vec3(0, 0, 1);
        Vec3 threatSecondary = Vec3.ZERO;
        boolean stowed;
        boolean volleyCharging;
        boolean hiddenInCutscene;
        boolean flying;
        boolean boosting;
        boolean airStairs;
        int volleyTicks;
        long unfoldStartTick = -1000;
        long shieldStartTick = -1000;
        long lastFlapSoundTick = -1000;
        long lastUpdateTick;
        Vec3 lastOwnerChest;
        final List<VisualBlock> blocks = new ArrayList<>();
        final ArrayDeque<Vec3> leftWingTrail = new ArrayDeque<>(WING_TRAIL_LEN);
        final ArrayDeque<Vec3> rightWingTrail = new ArrayDeque<>(WING_TRAIL_LEN);
    }

    private static final Map<Integer, ClientFormation> FORMATIONS = new HashMap<>();
    private static final List<ParryBurst> PARRIES = new ArrayList<>();
    private static final List<WaveBurst> WAVES = new ArrayList<>();
    private static final List<StepFlash> STEP_FLASHES = new ArrayList<>();
    private static ClientLevel world;

    private static int impactFrameTicks;
    private static int impactFrameMax = 3;
    private static int impactFrameSeed;
    private static float impactFrameIntensity = 1.0f;

    private BlockDominionRenderer() {}

    public static void reset() {
        FORMATIONS.clear();
        PARRIES.clear();
        WAVES.clear();
        STEP_FLASHES.clear();
        DominionParticles.clear();
        impactFrameTicks = 0;
        world = null;
        BlockDominionSounds.reset();
    }

    public static boolean isLocalManualActive() {
        ClientFormation f = localFormation();
        return f != null && f.mode == BlockDominionRules.Mode.MANUAL && !f.hiddenInCutscene;
    }

    public static int localReserveCount() {
        ClientFormation f = localFormation();
        return f == null ? 0 : f.blocks.size();
    }

    public static boolean isLocalWingFlying() {
        ClientFormation f = localFormation();
        return f != null && f.mode == BlockDominionRules.Mode.MANUAL && f.flying && !f.hiddenInCutscene;
    }

    public static boolean isLocalAirStairs() {
        ClientFormation f = localFormation();
        return f != null && f.mode == BlockDominionRules.Mode.MANUAL && f.airStairs && !f.flying && !f.hiddenInCutscene;
    }

    private static ClientFormation localFormation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return null;
        return FORMATIONS.get(mc.player.getId());
    }

    private static void triggerImpactFrame(int ticks, float intensity, int seed) {
        impactFrameTicks = Math.max(impactFrameTicks, ticks);
        impactFrameMax = Math.max(1, ticks);
        impactFrameIntensity = intensity;
        impactFrameSeed = seed;
    }

    private static BlockState decodeState(int id) {
        if (id <= 0) return Blocks.STONE.defaultBlockState();
        BlockState state = Block.stateById(id);
        return state == null || state.isAir() ? Blocks.STONE.defaultBlockState() : state;
    }

    public static void receive(BlockDominionStatePayload payload) {
        ClientLevel current = Minecraft.getInstance().level;
        if (current != world) {
            reset();
            world = current;
        }
        if (world == null) return;
        long now = world.getGameTime();

        BlockDominionRules.Mode mode = BlockDominionRules.Mode.byOrdinal(payload.mode());
        if (mode == BlockDominionRules.Mode.OFF && payload.blockStateIds().length == 0) {
            FORMATIONS.remove(payload.playerId());
            BlockDominionSounds.stopLoop(payload.playerId());
            BlockDominionSounds.stopVolleyCharge(payload.playerId());
            return;
        }

        ClientFormation f = FORMATIONS.computeIfAbsent(payload.playerId(), k -> new ClientFormation());
        boolean wasStowed = f.stowed;
        boolean wasVolley = f.volleyCharging;
        BlockDominionBrain.FormationKind prevFormation = f.formation;

        f.mode = mode;
        f.stowed = (payload.flags() & BlockDominionRules.FLAG_STOWED) != 0;
        f.volleyCharging = (payload.flags() & BlockDominionRules.FLAG_VOLLEY_CHARGING) != 0;
        f.hiddenInCutscene = (payload.flags() & BlockDominionRules.FLAG_HIDDEN) != 0;
        f.flying = (payload.flags() & BlockDominionRules.FLAG_FLYING) != 0;
        f.boosting = (payload.flags() & BlockDominionRules.FLAG_BOOSTING) != 0;
        f.airStairs = (payload.flags() & BlockDominionRules.FLAG_AIR_STAIRS) != 0;
        f.volleyTicks = payload.volleyTicks();
        f.formation = BlockDominionBrain.FormationKind.byOrdinal(payload.formationKind());
        f.threatPrimary = new Vec3(payload.threat1X(), payload.threat1Y(), payload.threat1Z());
        f.threatSecondary = new Vec3(payload.threat2X(), payload.threat2Y(), payload.threat2Z());
        f.lastUpdateTick = now;

        Entity owner = world.getEntity(payload.playerId());
        Vec3 ownerPos = owner != null ? owner.position().add(0, 1.25, 0)
                : new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
        Minecraft mc = Minecraft.getInstance();
        boolean isLocal = mc.player != null && mc.player.getId() == payload.playerId();
        int[] ids = payload.blockStateIds();
        int oldSize = f.blocks.size();

        while (f.blocks.size() > ids.length) {
            f.blocks.remove(f.blocks.size() - 1);
        }
        for (int i = 0; i < f.blocks.size(); i++) {
            f.blocks.get(i).state = decodeState(ids[i]);
        }
        for (int i = f.blocks.size(); i < ids.length; i++) {
            BlockState st = decodeState(ids[i]);
            boolean isPickup = payload.eventKind() == BlockDominionRules.EVENT_PICKUP && i >= oldSize;
            Vec3 spawnPos = isPickup ? new Vec3(payload.eventX(), payload.eventY(), payload.eventZ()) : ownerPos;
            float startScale = isPickup ? 0.94f : 0.25f;
            VisualBlock vb = new VisualBlock(st, spawnPos, startScale, (i * 1.37f) % 6.283f);
            if (isPickup) {
                vb.pickupOrigin = spawnPos;
                vb.pickupStartTick = now;
            }
            f.blocks.add(vb);
        }

        if (prevFormation != f.formation && f.blocks.size() > 1 && owner != null) {
            reassignNearestSlots(f, owner, now);
        }

        switch (payload.eventKind()) {
            case BlockDominionRules.EVENT_PICKUP -> {
                Vec3 origin = new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
                ImpactDebrisRenderer.groundWave(origin.add(0, -0.4, 0), 2.2, 0xEBC64E);
                DominionParticles.spawnRingBurst(origin, new Vec3(0, 1, 0), 0.65, 14, 0.16, 255, 214, 96, DominionParticles.STYLE_SHARD);
                BlockDominionSounds.play(origin, BlockDominionSounds.PICKUP, 0.55f, 0.92f + Math.min(ids.length, 48) * 0.008f);
            }
            case BlockDominionRules.EVENT_UNFOLD -> {
                f.unfoldStartTick = now;
                addWave(payload.playerId(), new Vec3(0, 1, 0), 3.6, 14, now);
                DominionParticles.spawnRingBurst(ownerPos, new Vec3(0, 1, 0), 1.5, 20, 0.20, 255, 224, 122, DominionParticles.STYLE_STAR);
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.UNFOLD, 0.7f, 1.05f);
            }
            case BlockDominionRules.EVENT_VOLLEY -> {
                BlockDominionSounds.stopVolleyCharge(payload.playerId());
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.VOLLEY_RELEASE, 1.05f, 1.0f);
                Vec3 aim = owner != null ? owner.getViewVector(1.0f) : new Vec3(0, 0, 1);
                addWave(payload.playerId(), aim, 5.6, 16, now);
                DominionParticles.spawnRingBurst(ownerPos.add(aim.scale(1.5)), aim, 2.0, 36, 0.34, 255, 232, 138, DominionParticles.STYLE_STAR);
                if (isLocal) {
                    triggerImpactFrame(4, 1.0f, (int) (now & 63));
                }
            }
            case BlockDominionRules.EVENT_PARRY -> {
                Vec3 threatDir = new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
                if (PARRIES.size() >= 16) PARRIES.remove(0);
                PARRIES.add(new ParryBurst(payload.playerId(), threatDir, decodeState(payload.eventBlockId()), now));
                Vec3 impactPt = ownerPos.add(threatDir.normalize().scale(1.35));
                DominionParticles.spawnRingBurst(impactPt, threatDir, 0.95, 26, 0.30, 255, 234, 145, DominionParticles.STYLE_SHARD);
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.PARRY, 0.95f, 1.02f);
                if (isLocal) {
                    triggerImpactFrame(3, 0.88f, (int) ((now + 17) & 63));
                }
            }
            case BlockDominionRules.EVENT_FLIGHT_START -> {
                f.leftWingTrail.clear();
                f.rightWingTrail.clear();
                f.lastFlapSoundTick = now;
                addWave(payload.playerId(), new Vec3(0, 1, 0), 4.2, 14, now);
                DominionParticles.spawnRingBurst(ownerPos, new Vec3(0, 1, 0), 1.8, 28, 0.25, 255, 224, 118, DominionParticles.STYLE_STAR);
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.WING_FLAP, 0.85f, 0.96f);
            }
            case BlockDominionRules.EVENT_FLIGHT_END -> {
                f.leftWingTrail.clear();
                f.rightWingTrail.clear();
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.UNFOLD, 0.45f, 0.82f);
            }
            case BlockDominionRules.EVENT_FLIGHT_BOOST -> {
                Vec3 aim = owner != null ? owner.getViewVector(1.0f) : new Vec3(0, 0, 1);
                addWave(payload.playerId(), aim, 4.6, 12, now);
                DominionParticles.spawnRingBurst(ownerPos, aim, 1.7, 26, 0.32, 255, 238, 165, DominionParticles.STYLE_STAR);
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.FLIGHT_BOOST, 0.92f, 1.04f);
                if (isLocal) {
                    triggerImpactFrame(2, 0.58f, (int) ((now + 31) & 63));
                }
            }
            case BlockDominionRules.EVENT_AIR_STEP -> {
                Vec3 stepPos = new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
                if (STEP_FLASHES.size() >= 18) STEP_FLASHES.remove(0);
                STEP_FLASHES.add(new StepFlash(stepPos, now));
                DominionParticles.spawnRingBurst(stepPos, new Vec3(0, 1, 0), 0.78, 16, 0.14, 255, 224, 122, DominionParticles.STYLE_SHARD);
                BlockDominionSounds.play(stepPos, BlockDominionSounds.STEP, 0.52f, 0.95f + (float) ((now % 5) * 0.04));
            }
            default -> {
                if (wasStowed && !f.stowed) {
                    f.unfoldStartTick = now;
                    addWave(payload.playerId(), new Vec3(0, 1, 0), 3.3, 14, now);
                    BlockDominionSounds.play(ownerPos, BlockDominionSounds.UNFOLD, 0.7f, 1.05f);
                }
            }
        }

        if (!prevFormation.isShield() && f.formation.isShield() && !f.blocks.isEmpty()) {
            f.shieldStartTick = now;
            BlockDominionSounds.play(ownerPos, BlockDominionSounds.SHIELD_LOCK, 0.72f,
                    f.formation == BlockDominionBrain.FormationKind.SHIELD_DOME ? 0.85f : 1.08f);
        }

        if (!wasVolley && f.volleyCharging) {
            int pid = payload.playerId();
            BlockDominionSounds.startVolleyCharge(pid,
                    () -> {
                        if (world == null) return null;
                        Entity e = world.getEntity(pid);
                        return e == null ? null : e.position().add(0, 1.3, 0);
                    },
                    () -> {
                        ClientFormation cf = FORMATIONS.get(pid);
                        return cf != null && cf.volleyCharging && !cf.hiddenInCutscene;
                    });
        } else if (wasVolley && !f.volleyCharging) {
            BlockDominionSounds.stopVolleyCharge(payload.playerId());
        }

        updateAmbientLoop(payload.playerId(), mode, f);
    }

    private static void addWave(int playerId, Vec3 axis, double maxRadius, int duration, long now) {
        if (WAVES.size() >= 14) WAVES.remove(0);
        WAVES.add(new WaveBurst(playerId, axis, maxRadius, duration, now));
    }

    private static void updateAmbientLoop(int pid, BlockDominionRules.Mode mode, ClientFormation f) {
        if (mode == BlockDominionRules.Mode.MANUAL && f.flying && !f.blocks.isEmpty()) {
            BlockDominionSounds.ensureLoop(pid, BlockDominionSounds.FLIGHT_LOOP,
                    () -> {
                        if (world == null) return null;
                        Entity e = world.getEntity(pid);
                        return e == null ? null : e.position().add(0, 1.25, 0);
                    },
                    () -> {
                        ClientFormation cf = FORMATIONS.get(pid);
                        return cf != null && cf.mode == BlockDominionRules.Mode.MANUAL && cf.flying && !cf.hiddenInCutscene;
                    },
                    () -> {
                        ClientFormation cf = FORMATIONS.get(pid);
                        if (cf == null || !cf.flying || cf.hiddenInCutscene) return 0.0;
                        Entity e = world == null ? null : world.getEntity(pid);
                        double spd = e == null ? 0.0 : e.getDeltaMovement().length();
                        return Mth.clamp(0.16 + spd * 0.34 + (cf.boosting ? 0.22 : 0.0), 0.14, 0.78);
                    },
                    () -> {
                        ClientFormation cf = FORMATIONS.get(pid);
                        if (cf == null) return 1.0;
                        Entity e = world == null ? null : world.getEntity(pid);
                        double spd = e == null ? 0.0 : e.getDeltaMovement().length();
                        return Mth.clamp(0.78 + spd * 0.48 + (cf.boosting ? 0.28 : 0.0), 0.75, 1.85);
                    });
        } else if (mode == BlockDominionRules.Mode.MANUAL && !f.blocks.isEmpty()) {
            BlockDominionSounds.ensureLoop(pid, BlockDominionSounds.ORBIT_LOOP,
                    () -> {
                        if (world == null) return null;
                        Entity e = world.getEntity(pid);
                        return e == null ? null : e.position().add(0, 1.25, 0);
                    },
                    () -> {
                        ClientFormation cf = FORMATIONS.get(pid);
                        return cf != null && cf.mode == BlockDominionRules.Mode.MANUAL && !cf.flying && !cf.blocks.isEmpty() && !cf.hiddenInCutscene;
                    },
                    () -> {
                        ClientFormation cf = FORMATIONS.get(pid);
                        if (cf == null || cf.blocks.isEmpty() || cf.hiddenInCutscene) return 0.0;
                        double base = 0.14 + Math.min(1.0, cf.blocks.size() / 48.0) * 0.30;
                        return cf.stowed ? base * 0.35 : (cf.formation.isShield() ? base * 1.25 : base);
                    },
                    () -> {
                        ClientFormation cf = FORMATIONS.get(pid);
                        if (cf == null) return 1.0;
                        double countFrac = Math.min(1.0, cf.blocks.size() / 48.0);
                        return 0.78 + countFrac * 0.56 + (cf.formation.isShield() ? 0.12 : 0.0);
                    });
        } else if (mode == BlockDominionRules.Mode.AUTO) {
            BlockDominionSounds.ensureLoop(pid, BlockDominionSounds.AUTO_LOOP,
                    () -> {
                        if (world == null) return null;
                        Entity e = world.getEntity(pid);
                        return e == null ? null : e.position().add(0, 1.0, 0);
                    },
                    () -> {
                        ClientFormation cf = FORMATIONS.get(pid);
                        return cf != null && cf.mode == BlockDominionRules.Mode.AUTO && !cf.hiddenInCutscene;
                    },
                    () -> {
                        ClientFormation cf = FORMATIONS.get(pid);
                        return cf != null && !cf.hiddenInCutscene ? 0.28 : 0.0;
                    },
                    () -> 1.0);
        } else {
            BlockDominionSounds.stopLoop(pid);
        }
    }

    private static void reassignNearestSlots(ClientFormation f, Entity owner, long now) {
        int n = f.blocks.size();
        Vec3 chest = owner.position().add(0, 1.25, 0);
        Vec3 look = owner.getViewVector(1.0f);
        float unfold = Mth.clamp((now - f.unfoldStartTick) / (float) BlockDominionRules.UNFOLD_TICKS, 0f, 1f);
        float volley = Mth.clamp(f.volleyTicks / (float) BlockDominionRules.VOLLEY_CHARGE_TICKS, 0f, 1f);

        List<Vec3> current = new ArrayList<>(n);
        List<Vec3> targets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            current.add(f.blocks.get(i).pos);
            targets.add(chest.add(BlockDominionBrain.slotOffset(
                    f.formation, i, n, now, look, f.threatPrimary, f.threatSecondary, unfold, volley)));
        }
        int[] perm = BlockDominionBrain.assignNearestSlots(current, targets);
        List<VisualBlock> reordered = new ArrayList<>(n);
        for (int slot = 0; slot < n; slot++) {
            int src = perm[slot];
            reordered.add(src >= 0 && src < n ? f.blocks.get(src) : f.blocks.get(slot));
        }
        f.blocks.clear();
        f.blocks.addAll(reordered);
    }

    public static void tick(Minecraft client) {
        if (client == null || client.level == null) {
            reset();
            return;
        }
        if (client.level != world) {
            reset();
            world = client.level;
        }
        if (client.isPaused()) return;
        long now = world.getGameTime();
        if (impactFrameTicks > 0) impactFrameTicks--;
        DominionParticles.tick();

        FORMATIONS.entrySet().removeIf(entry -> {
            boolean stale = now - entry.getValue().lastUpdateTick > 100;
            if (stale) {
                BlockDominionSounds.stopLoop(entry.getKey());
                BlockDominionSounds.stopVolleyCharge(entry.getKey());
            }
            return stale;
        });
        PARRIES.removeIf(p -> now - p.startTick > 14);
        WAVES.removeIf(w -> now - w.startTick > w.duration);
        STEP_FLASHES.removeIf(s -> now - s.startTick > 16);

        for (var entry : FORMATIONS.entrySet()) {
            Entity owner = world.getEntity(entry.getKey());
            if (owner == null) continue;
            ClientFormation f = entry.getValue();
            if (f.volleyCharging) f.volleyTicks++;
            Vec3 chest = owner.position().add(0, 1.25, 0);
            Vec3 ownerDelta = (f.lastOwnerChest != null && f.lastOwnerChest.distanceToSqr(chest) < 25.0 * 25.0)
                    ? chest.subtract(f.lastOwnerChest) : Vec3.ZERO;
            f.lastOwnerChest = chest;
            Vec3 look = owner.getViewVector(1.0f);
            float unfoldProgress = Mth.clamp((now - f.unfoldStartTick) / (float) BlockDominionRules.UNFOLD_TICKS, 0f, 1f);
            float volleyProgress = Mth.clamp(f.volleyTicks / (float) BlockDominionRules.VOLLEY_CHARGE_TICKS, 0f, 1f);

            int count = f.blocks.size();
            for (int i = 0; i < count; i++) {
                VisualBlock vb = f.blocks.get(i);
                vb.prevPos = vb.pos;
                vb.prevScale = vb.scale;

                Vec3 offset;
                Vec3 targetScale;
                if (f.formation.isWings()) {
                    BlockDominionBrain.WingPose wp = BlockDominionBrain.wingPose(
                            i, count, now, look, f.formation == BlockDominionBrain.FormationKind.WINGS_BOOST);
                    offset = wp.offset();
                    targetScale = wp.scale();
                } else {
                    offset = BlockDominionBrain.slotOffset(
                            f.formation, i, count, now, look, f.threatPrimary, f.threatSecondary, unfoldProgress, volleyProgress);
                    targetScale = targetBlockScaleVec(f, i, unfoldProgress, volleyProgress);
                }
                Vec3 target = chest.add(offset);

                long pickupAge = now - vb.pickupStartTick;
                if (vb.pickupOrigin != null && pickupAge >= 0 && pickupAge < 10) {
                    if (pickupAge < 2) {
                        double j = (pickupAge == 0 ? 0.05 : -0.05);
                        vb.pos = vb.pickupOrigin.add(j, 0.06 * pickupAge, -j);
                        vb.vel = Vec3.ZERO;
                    } else {
                        double t = (pickupAge - 2) / 8.0;
                        double ease = 1.0 - (1.0 - t) * (1.0 - t);
                        Vec3 mid = vb.pickupOrigin.lerp(target, 0.5).add(0, 1.15, 0);
                        Vec3 a = vb.pickupOrigin.lerp(mid, ease);
                        Vec3 b = mid.lerp(target, ease);
                        Vec3 nextPos = a.lerp(b, ease);
                        vb.vel = nextPos.subtract(vb.pos);
                        vb.pos = nextPos;
                    }
                } else {
                    vb.pickupOrigin = null;
                    if (vb.pos.distanceToSqr(target) > 48.0 * 48.0) {
                        vb.pos = target;
                        vb.prevPos = target;
                        vb.vel = Vec3.ZERO;
                    } else {
                        double carry = f.formation.isMobility() ? 0.94 : (f.formation.isShield() ? 0.85 : 0.65);
                        vb.pos = vb.pos.add(ownerDelta.scale(carry));

                        double stiffness;
                        double damping;
                        if (f.formation == BlockDominionBrain.FormationKind.AIR_STAIRS && i < 6) {
                            stiffness = 0.78;
                            damping = 0.76;
                        } else if (f.formation.isShield()) {
                            stiffness = 0.62;
                            damping = 0.66;
                        } else if (f.formation.isWings()) {
                            stiffness = f.formation == BlockDominionBrain.FormationKind.WINGS_BOOST ? 0.66 : 0.52;
                            damping = 0.72;
                        } else if (f.stowed) {
                            stiffness = 0.48;
                            damping = 0.70;
                        } else if (f.volleyCharging) {
                            stiffness = 0.44;
                            damping = 0.68;
                        } else {
                            stiffness = 0.28;
                            damping = 0.62;
                        }
                        Vec3 force = target.subtract(vb.pos).scale(stiffness);
                        vb.vel = vb.vel.add(force).scale(1.0 - damping);
                        vb.pos = vb.pos.add(vb.vel);
                    }
                }
                vb.scale = vb.scale.lerp(targetScale, 0.34);
                vb.spinPhase += f.stowed ? 0.08f : (f.volleyCharging ? 0.14f : 0.045f);
            }

            // Emit Purple-grade volumetric smoke puffs, wingtip feathers/sparks, and ambient dust
            emitTickParticles(f, owner, chest, look, now);
        }
    }

    private static void emitTickParticles(ClientFormation f, Entity owner, Vec3 chest, Vec3 look, long now) {
        if (f.hiddenInCutscene) return;
        int count = f.blocks.size();
        if (f.mode == BlockDominionRules.Mode.MANUAL && count > 0) {
            if (f.formation.isWings()) {
                boolean boosted = f.formation == BlockDominionBrain.FormationKind.WINGS_BOOST;
                Vec3 lTip = chest.add(BlockDominionBrain.wingTipOffset(true, now, look, boosted));
                Vec3 rTip = chest.add(BlockDominionBrain.wingTipOffset(false, now, look, boosted));
                pushTrail(f.leftWingTrail, lTip);
                pushTrail(f.rightWingTrail, rTip);

                Vec3 wake = owner.getDeltaMovement().scale(-0.32);
                // Billowing obsidian-amber smoke drifting off the wing roots & feathers
                if (now % 2 == 0) {
                    double sampleU = 0.25 + ((now * 0.19) % 0.7);
                    Vec3 lPt = chest.add(BlockDominionBrain.wingFeatherTip(true, sampleU, now, look, boosted));
                    Vec3 rPt = chest.add(BlockDominionBrain.wingFeatherTip(false, sampleU, now, look, boosted));
                    boolean dark = (now % 4 == 0);
                    DominionParticles.spawnSmoke(lPt, wake.add(0, -0.02, 0), boosted ? 0.48f : 0.38f, 16,
                            dark ? 26 : 168, dark ? 20 : 122, dark ? 18 : 42, dark ? 120 : 95);
                    DominionParticles.spawnSmoke(rPt, wake.add(0, -0.02, 0), boosted ? 0.48f : 0.38f, 16,
                            dark ? 26 : 168, dark ? 20 : 122, dark ? 18 : 42, dark ? 120 : 95);
                }
                // Shimmering golden sparks & feather dust streaming off wingtips
                int emitCount = boosted ? 3 : 2;
                for (int k = 0; k < emitCount; k++) {
                    DominionParticles.spawn(lTip, wake.add((Math.random() - 0.5) * 0.06, (Math.random() - 0.5) * 0.06, (Math.random() - 0.5) * 0.06),
                            boosted ? 0.20f : 0.14f, boosted ? 12 : 16, 0.90, 0.0,
                            255, boosted ? 236 : 216, boosted ? 156 : 104,
                            (k == 0) ? DominionParticles.STYLE_STAR : DominionParticles.STYLE_EMBER);
                    DominionParticles.spawn(rTip, wake.add((Math.random() - 0.5) * 0.06, (Math.random() - 0.5) * 0.06, (Math.random() - 0.5) * 0.06),
                            boosted ? 0.20f : 0.14f, boosted ? 12 : 16, 0.90, 0.0,
                            255, boosted ? 236 : 216, boosted ? 156 : 104,
                            (k == 0) ? DominionParticles.STYLE_STAR : DominionParticles.STYLE_EMBER);
                }
                if (!boosted && now - f.lastFlapSoundTick >= 29) {
                    f.lastFlapSoundTick = now;
                    BlockDominionSounds.play(chest, BlockDominionSounds.WING_FLAP, 0.32f, 0.88f + (float) (Math.random() * 0.12));
                }
            } else {
                f.leftWingTrail.clear();
                f.rightWingTrail.clear();
                if (!f.stowed) {
                    // Ambient obsidian-gold dust smoke & embers drifting through the massive stone swarm
                    VisualBlock sample = f.blocks.get((int) (now % count));
                    DominionParticles.spawnAmbient(sample.pos, 0.32, 0.032, 255, 214, 98,
                            (now % 3 == 0) ? DominionParticles.STYLE_STAR : DominionParticles.STYLE_EMBER);
                    if (now % 2 == 0) {
                        boolean dark = (now % 4 == 0);
                        DominionParticles.spawnAmbient(sample.pos, 0.38, 0.02,
                                dark ? 30 : 155, dark ? 24 : 114, dark ? 20 : 38, DominionParticles.STYLE_SMOKE);
                    }
                }
                if (f.formation == BlockDominionBrain.FormationKind.AIR_STAIRS) {
                    Vec3 under = owner.position().add(0, -0.18, 0);
                    DominionParticles.spawnAmbient(under, 0.72, 0.022, 255, 226, 122, DominionParticles.STYLE_SHARD);
                    if (now % 2 == 0) {
                        DominionParticles.spawnSmoke(under.add((Math.random() - 0.5) * 0.8, -0.05, (Math.random() - 0.5) * 0.8),
                                new Vec3(0, 0.015, 0), 0.42f, 16, 152, 118, 46, 105);
                    }
                }
            }
        } else if (f.mode == BlockDominionRules.Mode.AUTO) {
            double ang = (now * 0.52) % (Math.PI * 2.0);
            double rad = 1.6 + (now % 8) * 1.05;
            Vec3 p = owner.position().add(Math.cos(ang) * rad, 0.25, Math.sin(ang) * rad);
            DominionParticles.spawnAmbient(p, 0.45, 0.055, 255, 220, 110, DominionParticles.STYLE_EMBER);
            if (now % 2 == 0) {
                DominionParticles.spawnSmoke(p, new Vec3(0, 0.03, 0), 0.58f, 22, 42, 34, 24, 105);
            }
        }
    }

    private static void pushTrail(ArrayDeque<Vec3> trail, Vec3 pt) {
        if (trail.size() >= WING_TRAIL_LEN) trail.removeFirst();
        trail.addLast(pt);
    }

    private static Vec3 targetBlockScaleVec(ClientFormation f, int index, float unfoldProgress, float volleyProgress) {
        if (f.formation.isShield()) return new Vec3(0.74, 0.74, 0.74);
        if (f.formation == BlockDominionBrain.FormationKind.AIR_STAIRS) {
            return index < 6 ? new Vec3(0.74, 0.30, 0.74) : new Vec3(0.46, 0.46, 0.46);
        }
        float s = BlockDominionRules.blockScale(f.stowed, unfoldProgress, f.volleyCharging, volleyProgress);
        return new Vec3(s, s, s);
    }

    /** Hand-drawn organic anime ink-and-gold impact frame overlay (respects impactFrames config). */
    public static void renderHud(GuiGraphics graphics) {
        if (impactFrameTicks <= 0 || !GodWandConfig.CLIENT.impactFrames) return;
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        float frac = Mth.clamp(impactFrameTicks / (float) Math.max(1, impactFrameMax), 0f, 1f);
        float alpha = Mth.clamp(frac * impactFrameIntensity, 0f, 1f);
        int alphaByte = Mth.clamp((int) (alpha * 228f), 0, 255);
        if (alphaByte <= 3) return;

        float seconds = world != null ? (world.getGameTime() / 20.0f) : 0f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        DominionShader.bind(seconds);
        try {
            Matrix4f mat = graphics.pose().last().pose();
            var buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float u0 = impactFrameSeed + 0.002f, u1 = impactFrameSeed + 0.998f;
            float v0 = (DominionShader.FX_IMPACT_FRAME + 2) + 0.002f, v1 = (DominionShader.FX_IMPACT_FRAME + 2) + 0.998f;
            buf.addVertex(mat, 0, h, 0).setUv(u0, v1).setColor(255, 218, 108, alphaByte);
            buf.addVertex(mat, w, h, 0).setUv(u1, v1).setColor(255, 218, 108, alphaByte);
            buf.addVertex(mat, w, 0, 0).setUv(u1, v0).setColor(255, 218, 108, alphaByte);
            buf.addVertex(mat, 0, 0, 0).setUv(u0, v0).setColor(255, 218, 108, alphaByte);
            var mesh = buf.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    public static void render(PoseStack matrices, Camera camera, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;
        if (client.level != world) {
            reset();
            world = client.level;
        }
        if (FORMATIONS.isEmpty() && PARRIES.isEmpty() && WAVES.isEmpty() && STEP_FLASHES.isEmpty()) return;

        Vec3 camPos = camera.getPosition();
        double time = world.getGameTime() + tickDelta;
        boolean localCutscene = AtomicCinematic.active() || FugaCinematic.active()
                || SilentGateCinematic.active() || PurpleCinematic.active();
        BlockRenderDispatcher blockRenderer = client.getBlockRenderer();
        MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        // 1. Render all solid reserve blocks + terrifying satellite shattered stone swarm!
        for (var entry : FORMATIONS.entrySet()) {
            Entity owner = world.getEntity(entry.getKey());
            if (owner == null || owner.distanceToSqr(camPos) > 96.0 * 96.0) continue;
            ClientFormation f = entry.getValue();
            boolean isLocal = client.player != null && owner.getId() == client.player.getId();
            if (f.hiddenInCutscene || (isLocal && localCutscene)) continue;
            boolean firstPerson = isLocal && !camera.isDetached();
            Vec3 chest = owner.getPosition(tickDelta).add(0, 1.25, 0);
            Vec3 look = owner.getViewVector(tickDelta);

            if (f.mode == BlockDominionRules.Mode.MANUAL) {
                int count = f.blocks.size();
                for (int i = 0; i < count; i++) {
                    VisualBlock vb = f.blocks.get(i);
                    Vec3 renderWorld = vb.prevPos.lerp(vb.pos, tickDelta);
                    Vec3 sc = vb.prevScale.lerp(vb.scale, tickDelta);
                    if (firstPerson && renderWorld.distanceToSqr(camPos) < 0.72 * 0.72) continue;

                    int light = sampleLight(renderWorld, owner);
                    matrices.pushPose();
                    matrices.translate(renderWorld.x - camPos.x, renderWorld.y - camPos.y, renderWorld.z - camPos.z);
                    applySmartOrientation(matrices, f, i, count, renderWorld, chest, look, time, vb.spinPhase + tickDelta * 0.045f);
                    matrices.scale((float) sc.x, (float) sc.y, (float) sc.z);
                    matrices.translate(-0.5, -0.5, -0.5);
                    blockRenderer.renderSingleBlock(vb.state, matrices, bufferSource, light, OverlayTexture.NO_OVERLAY);
                    matrices.popPose();
                }

                // Procedural satellite shattered stone shards swarming around the formation for overwhelming scale!
                if (!f.stowed && count > 0) {
                    renderSatelliteStoneSwarm(matrices, bufferSource, blockRenderer, f, owner, chest, look, camPos, time, tickDelta, firstPerson);
                }
            } else if (f.mode == BlockDominionRules.Mode.AUTO) {
                renderAutoGravitySwarm(matrices, bufferSource, blockRenderer, owner, camPos, time, tickDelta, firstPerson);
            }
        }

        // 2. Render crisp snapping parry blocks
        for (ParryBurst burst : PARRIES) {
            Entity owner = world.getEntity(burst.playerId);
            if (owner == null || owner.distanceToSqr(camPos) > 96.0 * 96.0) continue;
            float age = (float) (time - burst.startTick);
            if (age < 0 || age > 10f) continue;
            float t = age / 10f;
            float scale = (1.0f - t * t) * 0.96f;
            if (scale <= 0.05f) continue;
            Vec3 chest = owner.getPosition(tickDelta).add(0, 1.15, 0);
            Vec3 pos = chest.add(burst.direction.scale(1.22 + t * 0.48));
            int light = sampleLight(pos, owner);
            matrices.pushPose();
            matrices.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
            matrices.mulPose(faceDirection(burst.direction));
            matrices.mulPose(Axis.ZP.rotationDegrees(age * 16f));
            matrices.scale(scale, scale, scale);
            matrices.translate(-0.5, -0.5, -0.5);
            blockRenderer.renderSingleBlock(burst.state, matrices, bufferSource, light, OverlayTexture.NO_OVERLAY);
            matrices.popPose();
        }

        bufferSource.endBatch();

        // 3. Purple-grade TWO-PASS Volumetric GLSL VFX:
        //    Pass 1 (BODY, alpha blend): billowing smoke, torn ink/gold strokes, dark-core lightning, stone shards
        //    Pass 2 (GLOW, additive): liquid plasma currents, wing feather light plumes, hot lightning, soft glows, sparks
        renderVolumetricPass(matrices, camera, time, tickDelta, client, localCutscene, true);
        renderVolumetricPass(matrices, camera, time, tickDelta, client, localCutscene, false);
    }

    private static int sampleLight(Vec3 worldPos, Entity owner) {
        BlockPos lightPos = BlockPos.containing(worldPos);
        if (world.getBlockState(lightPos).isSolidRender(world, lightPos)) lightPos = owner.blockPosition().above();
        return LevelRenderer.getLightColor(world, lightPos);
    }

    /**
     * Renders 2 shattered satellite stone fragments per real block (capped cleanly for framerate),
     * turning the reserve into a menacing, terrifying armada of 100+ swirling stone monoliths & shards!
     */
    private static void renderSatelliteStoneSwarm(PoseStack matrices, MultiBufferSource.BufferSource bufferSource,
                                                  BlockRenderDispatcher blockRenderer, ClientFormation f,
                                                  Entity owner, Vec3 chest, Vec3 look, Vec3 camPos,
                                                  double time, float tickDelta, boolean firstPerson) {
        int count = f.blocks.size();
        int shardsPerBlock = f.formation.isWings() ? 1 : 2;
        int maxShards = f.formation.isWings() ? 36 : 84;
        int emitted = 0;
        for (int i = 0; i < count && emitted < maxShards; i++) {
            VisualBlock vb = f.blocks.get(i);
            Vec3 host = vb.prevPos.lerp(vb.pos, tickDelta);
            for (int k = 0; k < shardsPerBlock && emitted < maxShards; k++) {
                emitted++;
                long sd = 3100L + i * 73L + k * 29L;
                double ang = DominionShader.hash(sd) * Math.PI * 2.0 + time * (0.07 + 0.05 * DominionShader.hash(sd + 1)) * (k == 0 ? 1 : -1);
                double rad = f.formation.isWings()
                        ? (0.35 + 0.45 * DominionShader.hash(sd + 2))
                        : (0.48 + 0.72 * DominionShader.hash(sd + 2));
                double vert = Math.sin(ang * 1.4 + sd) * (f.formation == BlockDominionBrain.FormationKind.AIR_STAIRS ? 0.16 : 0.42);
                Vec3 pos = host.add(Math.cos(ang) * rad, vert, Math.sin(ang) * rad);
                if (firstPerson && pos.distanceToSqr(camPos) < 0.85 * 0.85) continue;

                // Irregular shattered monolith slab/splinter proportions
                float sx = (float) (0.14 + 0.18 * DominionShader.hash(sd + 3));
                float sy = (float) (0.12 + 0.24 * DominionShader.hash(sd + 4));
                float sz = (float) (0.13 + 0.16 * DominionShader.hash(sd + 5));
                int light = sampleLight(pos, owner);

                matrices.pushPose();
                matrices.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
                matrices.mulPose(Axis.YP.rotationDegrees((float) (time * 4.5 + i * 41 + k * 120)));
                matrices.mulPose(Axis.XP.rotationDegrees((float) (time * 3.1 + i * 27 + k * 75)));
                matrices.scale(sx, sy, sz);
                matrices.translate(-0.5, -0.5, -0.5);
                blockRenderer.renderSingleBlock(vb.state, matrices, bufferSource, light, OverlayTexture.NO_OVERLAY);
                matrices.popPose();
            }
        }
    }

    /** Auto mode: 42 swirling terrain stones & jagged monolith chunks tearing off the earth across the 10-block field. */
    private static void renderAutoGravitySwarm(PoseStack matrices, MultiBufferSource.BufferSource bufferSource,
                                               BlockRenderDispatcher blockRenderer, Entity owner,
                                               Vec3 camPos, double time, float tickDelta, boolean firstPerson) {
        Vec3 base = owner.getPosition(tickDelta);
        int stoneCount = 42;
        for (int i = 0; i < stoneCount; i++) {
            long sd = 6100L + i * 53L;
            double dirSign = (i & 1) == 0 ? 1.0 : -1.0;
            double angle = (Math.PI * 2.0 * i / stoneCount) + time * (0.015 + 0.012 * DominionShader.hash(sd)) * dirSign;
            double dist = 2.0 + DominionShader.hash(sd + 1) * 7.8;
            double wx = base.x + Math.cos(angle) * dist;
            double wz = base.z + Math.sin(angle) * dist;
            int bx = Mth.floor(wx), bz = Mth.floor(wz);
            if (!world.getChunkSource().hasChunk(bx >> 4, bz >> 4)) continue;
            int groundY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            if (Math.abs(groundY - base.y) > 7) continue;
            BlockPos samplePos = new BlockPos(bx, groundY - 1, bz);
            BlockState ground = world.getBlockState(samplePos);
            if (ground.isAir() || !ground.getFluidState().isEmpty()) ground = Blocks.DEEPSLATE.defaultBlockState();
            double hoverY = groundY + 0.45 + DominionShader.hash(sd + 2) * 1.45 + Math.sin(time * 0.11 + i * 1.3) * 0.24;
            Vec3 p = new Vec3(wx, hoverY, wz);
            if (firstPerson && p.distanceToSqr(camPos) < 1.25 * 1.25) continue;

            float sx = (float) (0.20 + DominionShader.hash(sd + 3) * 0.30);
            float sy = (float) (0.18 + DominionShader.hash(sd + 4) * 0.34);
            float sz = (float) (0.20 + DominionShader.hash(sd + 5) * 0.28);
            int light = sampleLight(p, owner);
            matrices.pushPose();
            matrices.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
            matrices.mulPose(Axis.YP.rotationDegrees((float) (time * 3.8 + i * 37)));
            matrices.mulPose(Axis.XP.rotationDegrees((float) (time * 2.6 + i * 23)));
            matrices.scale(sx, sy, sz);
            matrices.translate(-0.5, -0.5, -0.5);
            blockRenderer.renderSingleBlock(ground, matrices, bufferSource, light, OverlayTexture.NO_OVERLAY);
            matrices.popPose();
        }
    }

    /** Aligns block rotation to active formation, including exact per-feather sculpted angles for Block Wings. */
    private static void applySmartOrientation(PoseStack matrices, ClientFormation f, int index, int count,
                                              Vec3 blockPos, Vec3 chest, Vec3 look, double time, float spinPhase) {
        if (f.formation == BlockDominionBrain.FormationKind.SHIELD_SINGLE) {
            matrices.mulPose(faceDirection(f.threatPrimary));
        } else if (f.formation == BlockDominionBrain.FormationKind.SHIELD_MULTI) {
            int half = (count + 1) / 2;
            matrices.mulPose(faceDirection(index < half ? f.threatPrimary : f.threatSecondary));
        } else if (f.formation == BlockDominionBrain.FormationKind.SHIELD_DOME) {
            Vec3 radial = blockPos.subtract(chest);
            matrices.mulPose(faceDirection(radial.lengthSqr() > 1e-4 ? radial : look));
        } else if (f.formation == BlockDominionBrain.FormationKind.VOLLEY_FAN) {
            matrices.mulPose(faceDirection(look));
            matrices.mulPose(Axis.ZP.rotationDegrees(spinPhase * 24f));
        } else if (f.formation == BlockDominionBrain.FormationKind.AIR_STAIRS && index < 6) {
            float yaw = (float) (Mth.atan2(-look.x, look.z) * (180.0 / Math.PI));
            matrices.mulPose(Axis.YP.rotationDegrees(-yaw));
        } else if (f.formation.isWings()) {
            BlockDominionBrain.WingPose wp = BlockDominionBrain.wingPose(
                    index, count, time, look, f.formation == BlockDominionBrain.FormationKind.WINGS_BOOST);
            Matrix3f basisMat = new Matrix3f(
                    (float) wp.axisX().x, (float) wp.axisX().y, (float) wp.axisX().z,
                    (float) wp.axisY().x, (float) wp.axisY().y, (float) wp.axisY().z,
                    (float) wp.axisZ().x, (float) wp.axisZ().y, (float) wp.axisZ().z
            );
            matrices.mulPose(new Quaternionf().setFromNormalized(basisMat));
        } else {
            float spin = spinPhase * (180f / (float) Math.PI);
            matrices.mulPose(Axis.YP.rotationDegrees(spin));
            matrices.mulPose(Axis.XP.rotationDegrees(spin * 0.65f + index * 19f));
        }
    }

    private static Quaternionf faceDirection(Vec3 dir) {
        Vec3 n = dir == null || dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : dir.normalize();
        return new Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f((float) n.x, (float) n.y, (float) n.z));
    }

    /**
     * Purple-grade two-pass volumetric GLSL pass:
     * {@code bodyPass=true}: alpha-blended smoke clouds, torn dark-ink/gold brush strokes, dark-core lightning.
     * {@code bodyPass=false}: additive liquid plasma currents, wing feather energy plumes, hot lightning, soft glows, sparks.
     */
    private static void renderVolumetricPass(PoseStack matrices, Camera camera, double time, float tickDelta,
                                             Minecraft client, boolean localCutscene, boolean bodyPass) {
        Vec3 camPos = camera.getPosition();
        Vector3f leftV = camera.getLeftVector();
        Vector3f upV = camera.getUpVector();
        Vec3 camRight = new Vec3(-leftV.x, -leftV.y, -leftV.z);
        Vec3 camUp = new Vec3(upV.x, upV.y, upV.z);
        float seconds = (float) (time / 20.0);

        RenderSystem.enableBlend();
        if (bodyPass) {
            RenderSystem.defaultBlendFunc();
        } else {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        DominionShader.bind(seconds);

        try {
            var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            Matrix4f mat = matrices.last().pose();
            boolean emitted = false;

            for (var entry : FORMATIONS.entrySet()) {
                ClientFormation f = entry.getValue();
                Entity owner = world.getEntity(entry.getKey());
                if (owner == null || owner.distanceToSqr(camPos) > 96.0 * 96.0) continue;
                boolean isLocal = client.player != null && owner.getId() == client.player.getId();
                if (f.hiddenInCutscene || (isLocal && localCutscene)) continue;
                boolean firstPerson = isLocal && !camera.isDetached();
                Vec3 chest = owner.getPosition(tickDelta).add(0, 1.25, 0).subtract(camPos);
                Vec3 look = owner.getViewVector(tickDelta);
                Vec3 forward = new Vec3(look.x, 0, look.z);
                forward = forward.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : forward.normalize();

                if (f.mode == BlockDominionRules.Mode.AUTO) {
                    emitAutoFieldVfx(buffer, mat, owner.getPosition(tickDelta).add(0, 0.14, 0).subtract(camPos),
                            camRight, camUp, time, bodyPass);
                    emitted = true;
                } else if (f.mode == BlockDominionRules.Mode.MANUAL && !f.blocks.isEmpty()) {
                    int count = f.blocks.size();

                    // Pickup crackling lightning & plasma tethers from broken earth up into lifted blocks
                    for (int i = 0; i < count; i++) {
                        VisualBlock vb = f.blocks.get(i);
                        if (vb.pickupOrigin != null) {
                            double age = time - vb.pickupStartTick;
                            if (age >= 0 && age < 10) {
                                int a = (int) (215 * (1.0 - age / 10.0));
                                Vec3 from = vb.pickupOrigin.subtract(camPos);
                                Vec3 to = vb.prevPos.lerp(vb.pos, tickDelta).subtract(camPos);
                                if (bodyPass) {
                                    DominionShader.emitLiveBolt(buffer, mat, from, to, 6, 0.22, 0.08, 200L + i, time, 22, 16, 12, a);
                                } else {
                                    DominionShader.emitLiveBolt(buffer, mat, from, to, 6, 0.22, 0.045, 200L + i, time, 255, 224, 118, a);
                                }
                                emitted = true;
                            }
                        }
                    }

                    if (f.formation == BlockDominionBrain.FormationKind.IDLE) {
                        emitIdleSwarmVfx(buffer, mat, f, chest, forward, camRight, camUp, time, tickDelta, camPos, firstPerson, bodyPass);
                        emitted = true;
                    } else if (f.formation.isShield()) {
                        emitShieldVfx(buffer, mat, f, chest, forward, time, tickDelta, camPos, firstPerson, bodyPass);
                        emitted = true;
                    } else if (f.formation == BlockDominionBrain.FormationKind.VOLLEY_FAN) {
                        emitVolleyVfx(buffer, mat, f, chest, look, camRight, camUp, time, tickDelta, camPos, bodyPass);
                        emitted = true;
                    } else if (f.formation == BlockDominionBrain.FormationKind.AIR_STAIRS) {
                        emitAirStairsVfx(buffer, mat, f, camRight, camUp, time, tickDelta, camPos, bodyPass);
                        emitted = true;
                    } else if (f.formation.isWings()) {
                        emitWingsVfx(buffer, mat, f, owner, chest, look, forward, camRight, camUp, time, camPos, firstPerson, bodyPass);
                        emitted = true;
                    }
                }
            }

            // Air-step transient smoky plasma ripples
            for (StepFlash sf : STEP_FLASHES) {
                double age = time - sf.startTick;
                if (age < 0 || age >= 16) continue;
                double t = age / 16.0;
                Vec3 p = sf.pos.subtract(camPos);
                if (bodyPass) {
                    int a = (int) (135 * (1.0 - t) * (1.0 - t));
                    DominionShader.emitOrganicWave(buffer, mat, p, new Vec3(0, 1, 0), 0.55 + t * 1.45, 0.26 * (1.0 - t * 0.5),
                            time, sf.startTick * 17L, DominionShader.FX_BRUSH, 36, 26, 16, a);
                } else {
                    int a = (int) (185 * (1.0 - t) * (1.0 - t));
                    DominionShader.emitOrganicWave(buffer, mat, p, new Vec3(0, 1, 0), 0.50 + t * 1.35, 0.16 * (1.0 - t * 0.4),
                            time, sf.startTick * 17L + 3, DominionShader.FX_PLASMA, 255, 224, 118, a);
                }
                emitted = true;
            }

            // Parry organic smoke + dissolving hex mist + ragged plasma shockwaves + returning energy wisps
            for (ParryBurst burst : PARRIES) {
                Entity owner = world.getEntity(burst.playerId);
                if (owner == null || owner.distanceToSqr(camPos) > 96.0 * 96.0) continue;
                double age = time - burst.startTick;
                if (age < 0 || age >= 14) continue;
                double t = age / 14.0;
                Vec3 chest = owner.getPosition(tickDelta).add(0, 1.15, 0).subtract(camPos);
                Vec3 impact = chest.add(burst.direction.scale(1.32));
                int a = (int) (220 * (1.0 - t) * (1.0 - t));
                if (bodyPass) {
                    DominionShader.emitOrganicWave(buffer, mat, impact, burst.direction, 0.55 + t * 2.35, 0.36 * (1.0 - t * 0.4),
                            time, burst.startTick * 31L, DominionShader.FX_BRUSH, 24, 18, 14, (int) (a * 0.78));
                    for (int s = 0; s < 5; s++) {
                        double ang = s * (Math.PI * 2.0 / 5.0) + t * 1.2;
                        Vec3[] uw = DominionShader.basis(burst.direction);
                        Vec3 sp = impact.add(uw[0].scale(Math.cos(ang) * (0.4 + t * 1.5))).add(uw[1].scale(Math.sin(ang) * (0.4 + t * 1.5)));
                        DominionShader.emitBillboard(buffer, mat, sp, camRight, camUp, 0.45 + t * 0.55, ang,
                                DominionShader.FX_SMOKE, 50 + s, 145, 108, 42, (int) (a * 0.55));
                    }
                } else {
                    DominionShader.emitDisc(buffer, mat, impact, burst.direction, 1.15 + t * 1.95,
                            DominionShader.FX_HEX_MIST, 55, 255, 224, 118, a);
                    DominionShader.emitOrganicWave(buffer, mat, impact.add(burst.direction.scale(0.08)), burst.direction,
                            0.45 + t * 2.55, 0.22 * (1.0 - t * 0.4), time, burst.startTick * 31L + 5,
                            DominionShader.FX_PLASMA, 255, 240, 172, a);
                    DominionShader.emitBillboard(buffer, mat, impact, camRight, camUp, 1.65 * (1.0 - t * 0.4), 0,
                            DominionShader.FX_GLOW, 58, 255, 232, 138, (int) (a * 0.85));
                }
                emitted = true;
            }

            // Unfold, Takeoff, Boost & Volley-release ragged organic pressure waves (Purple/Red style)
            for (WaveBurst w : WAVES) {
                Entity owner = world.getEntity(w.playerId);
                if (owner == null || owner.distanceToSqr(camPos) > 96.0 * 96.0) continue;
                double age = time - w.startTick;
                if (age < 0 || age >= w.duration) continue;
                double t = age / w.duration;
                double ease = 1.0 - (1.0 - t) * (1.0 - t);
                Vec3 origin = owner.getPosition(tickDelta).add(0, 1.25, 0).subtract(camPos);
                int a = (int) (210 * (1.0 - t) * (1.0 - t));
                if (bodyPass) {
                    DominionShader.emitOrganicWave(buffer, mat, origin, w.axis, 0.5 + ease * w.maxRadius,
                            0.42 * (1.0 - t * 0.45), time, w.seed, DominionShader.FX_BRUSH, 26, 20, 14, (int) (a * 0.72));
                } else {
                    DominionShader.emitOrganicWave(buffer, mat, origin, w.axis, 0.45 + ease * w.maxRadius,
                            0.26 * (1.0 - t * 0.45), time, w.seed + 11, DominionShader.FX_PLASMA, 255, 224, 118, a);
                }
                emitted = true;
            }

            // Two-pass Custom 3D GPU Particles (BODY: smoke & stone shards, GLOW: sparks & embers)
            if (DominionParticles.emitPass(buffer, mat, camera, tickDelta, bodyPass)) {
                emitted = true;
            }

            if (emitted) {
                var built = buffer.build();
                if (built != null) BufferUploader.drawWithShader(built);
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    /** Idle armada VFX: churning golden-obsidian smoke wisps + 3D liquid plasma orbital streams + crackling arcs (no flat PNG circles!). */
    private static void emitIdleSwarmVfx(VertexConsumer v, Matrix4f m, ClientFormation f, Vec3 chest, Vec3 forward,
                                         Vec3 camRight, Vec3 camUp, double time, float tickDelta, Vec3 camPos,
                                         boolean firstPerson, boolean bodyPass) {
        int count = f.blocks.size();
        double tierRadius = count <= 8 ? 2.05 : (count <= 20 ? 2.55 : 3.35);
        Vec3 haloCenter = count > 20 ? chest.add(forward.scale(-1.45)).add(0, 0.85, 0) : chest;
        Vec3 haloNormal = count > 20 ? forward : new Vec3(0, 1, 0);

        if (bodyPass) {
            // Billowing dark-amber/obsidian smoke curling through the stone armada
            int puffs = Math.min(12, 4 + count / 5);
            Vec3[] uw = DominionShader.basis(haloNormal);
            for (int i = 0; i < puffs; i++) {
                double ang = (Math.PI * 2.0 * i / puffs) + time * 0.028 * ((i & 1) == 0 ? 1 : -1);
                double rr = tierRadius * (0.72 + 0.30 * DominionShader.vnoise(1100 + i, time * 0.06));
                Vec3 p = haloCenter.add(uw[0].scale(Math.cos(ang) * rr)).add(uw[1].scale(Math.sin(ang) * rr));
                boolean dark = (i % 2 == 0);
                DominionShader.emitBillboard(v, m, p, camRight, camUp, 0.72 + 0.35 * DominionShader.hash(1120 + i),
                        time * 0.03 + i, DominionShader.FX_SMOKE, 10 + i,
                        dark ? 28 : 132, dark ? 22 : 96, dark ? 18 : 34, firstPerson ? 48 : 88);
            }
            // Flowing dark ink-gold torn brush currents sweeping around the armada
            DominionShader.emitOrganicWave(v, m, haloCenter, haloNormal, tierRadius, 0.28, time, 1200L,
                    DominionShader.FX_BRUSH, 32, 22, 14, firstPerson ? 55 : 108);
        } else {
            // Luminous liquid plasma currents weaving smoothly around the armada (Purple plasma style)
            DominionShader.emitOrganicWave(v, m, haloCenter, haloNormal, tierRadius, 0.22, time, 1205L,
                    DominionShader.FX_PLASMA, 245, 204, 82, firstPerson ? 65 : 128);
            if (count > 20) {
                DominionShader.emitOrganicWave(v, m, haloCenter.add(forward.scale(0.25)), haloNormal,
                        tierRadius * 0.72, 0.16, -time * 1.15, 1215L,
                        DominionShader.FX_PLASMA, 255, 232, 142, firstPerson ? 52 : 108);
                // Soft warm atmospheric core behind the armada (never clips into a hard circle)
                DominionShader.emitBillboard(v, m, haloCenter, camRight, camUp, 2.2, 0,
                        DominionShader.FX_GLOW, 12, 240, 198, 78, firstPerson ? 24 : 58);
            }
            // Occasional crackling golden lightning arcs linking nearby orbiting blocks
            int arcs = Math.min(5, count / 7);
            for (int a = 0; a < arcs; a++) {
                int i1 = (a * 5 + (int) (time / 12)) % count;
                int i2 = (i1 + 1) % count;
                Vec3 p1 = f.blocks.get(i1).prevPos.lerp(f.blocks.get(i1).pos, tickDelta).subtract(camPos);
                Vec3 p2 = f.blocks.get(i2).prevPos.lerp(f.blocks.get(i2).pos, tickDelta).subtract(camPos);
                if (p1.distanceToSqr(p2) < 9.0) {
                    DominionShader.emitLiveBolt(v, m, p1, p2, 5, 0.14, 0.032, 1400L + a * 17L, time, 255, 232, 138, firstPerson ? 85 : 145);
                }
            }
        }
    }

    /**
     * Sculpted Block Wings auxiliary volumetric effects:
     * - 12 flowing golden-amber liquid plasma feather plumes streaming off primary wing feathers
     * - Billowing wingbeat smoke clouds around the shoulder spine & inner membrane
     * - Crackling golden-white energy veins coursing along both curved upper wing arches
     * - Twin wingtip plasma slipstream trails + ragged Mach pressure waves during boost!
     */
    private static void emitWingsVfx(VertexConsumer v, Matrix4f m, ClientFormation f, Entity owner, Vec3 chest,
                                     Vec3 look, Vec3 forward, Vec3 camRight, Vec3 camUp,
                                     double time, Vec3 camPos, boolean firstPerson, boolean bodyPass) {
        boolean boosted = f.formation == BlockDominionBrain.FormationKind.WINGS_BOOST;
        Vec3 spineCenter = chest.add(forward.scale(-0.48)).add(0, 0.18, 0);

        if (bodyPass) {
            // Soft volumetric dark-obsidian / warm-amber wingbeat smoke clouds behind shoulders & wing roots
            for (int sideIdx = 0; sideIdx < 2; sideIdx++) {
                boolean left = sideIdx == 0;
                for (int k = 0; k < 4; k++) {
                    double u = 0.18 + k * 0.24;
                    Vec3 pt = chest.add(BlockDominionBrain.wingArchPoint(left, u, time, look, boosted))
                            .add(forward.scale(-0.22));
                    boolean dark = (k % 2 == 0);
                    DominionShader.emitBillboard(v, m, pt, camRight, camUp, boosted ? 0.65 : 0.58,
                            time * 0.04 * (left ? 1 : -1) + k, DominionShader.FX_SMOKE, 30 + sideIdx * 10 + k,
                            dark ? 28 : 142, dark ? 22 : 102, dark ? 18 : 36, firstPerson ? 45 : 95);
                }
                // Dark ink-gold torn brush plumes weaving behind primary feathers to unify the wing silhouette
                for (int p = 0; p < 5; p++) {
                    double u = 0.15 + p * 0.21;
                    emitWingFeatherPlume(v, m, chest, look, forward, left, u, time, boosted, true, firstPerson);
                }
            }
            // Dark sheath lightning along both upper wing arches
            emitWingArchLightning(v, m, chest, look, time, boosted, true, firstPerson);
        } else {
            // Soft warm volumetric halo glow nestled behind the scapula spine (silky, zero hard outline!)
            DominionShader.emitBillboard(v, m, spineCenter, camRight, camUp, boosted ? 1.85 : 1.55, 0,
                    DominionShader.FX_GLOW, 35, 255, boosted ? 236 : 214, boosted ? 156 : 96, firstPerson ? 45 : 115);

            // Luminous flowing liquid-gold plasma feather plumes streaming off BOTH wings (10 feathers total!)
            for (int sideIdx = 0; sideIdx < 2; sideIdx++) {
                boolean left = sideIdx == 0;
                for (int p = 0; p < 5; p++) {
                    double u = 0.15 + p * 0.21;
                    emitWingFeatherPlume(v, m, chest, look, forward, left, u, time, boosted, false, firstPerson);
                }
            }

            // Hot crackling golden-white energy veins running along both curved wing arches!
            emitWingArchLightning(v, m, chest, look, time, boosted, false, firstPerson);

            // Wingtip luminous slipstream trails
            emitWingTrail(v, m, f.leftWingTrail, camPos, boosted ? 0.34 : 0.18, 41, boosted);
            emitWingTrail(v, m, f.rightWingTrail, camPos, boosted ? 0.34 : 0.18, 42, boosted);

            // Supersonic ragged Mach pressure waves during boost (organic torn plasma waves, NEVER hard white rings!)
            if (boosted) {
                Vec3 vel = owner.getDeltaMovement();
                Vec3 axis = vel.lengthSqr() > 0.04 ? vel.normalize() : look.normalize();
                double pulse = (time * 0.18) % 1.0;
                DominionShader.emitOrganicWave(v, m, chest.add(axis.scale(0.85 - pulse * 1.6)), axis,
                        1.15 + pulse * 1.65, 0.26 * (1.0 - pulse * 0.4), time, 4500L,
                        DominionShader.FX_PLASMA, 255, 232, 138, (int) ((firstPerson ? 85 : 155) * (1.0 - pulse)));
                DominionShader.emitOrganicWave(v, m, chest.add(axis.scale(0.15)), axis,
                        1.95, 0.20, -time * 1.3, 4510L,
                        DominionShader.FX_PLASMA, 245, 206, 86, firstPerson ? 55 : 108);
            }
        }
    }

    /** Emits a flowing 3D curved energy/smoke feather plume streaming out from a primary feather tip! */
    private static void emitWingFeatherPlume(VertexConsumer v, Matrix4f m, Vec3 chest, Vec3 look, Vec3 forward,
                                             boolean left, double u, double time, boolean boosted,
                                             boolean bodyPass, boolean firstPerson) {
        double archU = 0.16 + u * 0.84;
        Vec3 root = chest.add(BlockDominionBrain.wingArchPoint(left, archU, time, look, boosted));
        Vec3 tip = chest.add(BlockDominionBrain.wingFeatherTip(left, u, time, look, boosted));
        Vec3 dir = tip.subtract(root);
        double len = dir.length();
        if (len < 0.05) return;
        Vec3 nDir = dir.normalize();

        int segs = 7;
        Vec3[] pts = new Vec3[segs + 1];
        double[] ws = new double[segs + 1];
        double[] als = new double[segs + 1];
        long sd = (left ? 5100L : 5200L) + (long) (u * 100);
        double extraTail = boosted ? 1.15 : 0.72;
        for (int j = 0; j <= segs; j++) {
            double t = j / (double) segs;
            Vec3 flutter = DominionShader.nvec(sd + j, t * 2.5 - time * 0.22).scale((boosted ? 0.16 : 0.11) * t);
            // Stream from feather root -> past feather tip -> drifting softly backward into airflow
            pts[j] = root.add(nDir.scale(len * (0.25 + t * (0.75 + extraTail))))
                    .add(forward.scale(-t * t * (boosted ? 0.95 : 0.35)))
                    .add(flutter);
            double profile = Math.pow(Math.sin(Math.PI * Math.min(1.0, 0.12 + 0.88 * t)), 0.55);
            ws[j] = (bodyPass ? 0.24 : 0.16) * profile * (1.0 - 0.35 * t);
            als[j] = (1.0 - t * 0.75) * profile;
        }
        int alpha = firstPerson ? (bodyPass ? 55 : 75) : (bodyPass ? 115 : 155);
        if (bodyPass) {
            DominionShader.emitCurvedRibbon(v, m, pts, ws, als, DominionShader.FX_BRUSH, (int) (sd % 97), 34, 24, 14, alpha);
        } else {
            DominionShader.emitCurvedRibbon(v, m, pts, ws, als, DominionShader.FX_PLASMA, (int) (sd % 97),
                    255, boosted ? 236 : 216, boosted ? 156 : 102, alpha);
        }
    }

    /** Emits crackling energy veins along both curved leading-edge wing arches. */
    private static void emitWingArchLightning(VertexConsumer v, Matrix4f m, Vec3 chest, Vec3 look,
                                              double time, boolean boosted, boolean bodyPass, boolean firstPerson) {
        for (int s = 0; s < 2; s++) {
            boolean left = s == 0;
            Vec3 p0 = chest.add(BlockDominionBrain.wingArchPoint(left, 0.02, time, look, boosted));
            Vec3 p1 = chest.add(BlockDominionBrain.wingArchPoint(left, 0.48, time, look, boosted));
            Vec3 p2 = chest.add(BlockDominionBrain.wingArchPoint(left, 0.98, time, look, boosted));
            int alpha = firstPerson ? 75 : (boosted ? 185 : 140);
            if (bodyPass) {
                DominionShader.emitLiveBolt(v, m, p0, p1, 5, 0.12, 0.07, 5400L + s * 31L, time, 24, 18, 12, (int) (alpha * 0.75));
                DominionShader.emitLiveBolt(v, m, p1, p2, 5, 0.14, 0.06, 5450L + s * 31L, time, 24, 18, 12, (int) (alpha * 0.75));
            } else {
                DominionShader.emitLiveBolt(v, m, p0, p1, 5, 0.12, 0.036, 5400L + s * 31L, time, 255, 232, 138, alpha);
                DominionShader.emitLiveBolt(v, m, p1, p2, 5, 0.14, 0.030, 5450L + s * 31L, time, 255, 242, 182, alpha);
            }
        }
    }

    /** Organic Air-Step Stairs VFX: warm dust smoke + ragged glowing plasma rings beneath each floating stair tread. */
    private static void emitAirStairsVfx(VertexConsumer v, Matrix4f m, ClientFormation f, Vec3 camRight, Vec3 camUp,
                                         double time, float tickDelta, Vec3 camPos, boolean bodyPass) {
        int stairSlots = Math.min(4, f.blocks.size());
        for (int s = 0; s < stairSlots; s++) {
            Vec3 bp = f.blocks.get(s).prevPos.lerp(f.blocks.get(s).pos, tickDelta).subtract(camPos).add(0, -0.14, 0);
            if (bodyPass) {
                DominionShader.emitBillboard(v, m, bp, camRight, camUp, 0.65 - s * 0.06, time * 0.05 + s,
                        DominionShader.FX_SMOKE, 70 + s, 136, 102, 42, 95 - s * 14);
            } else {
                DominionShader.emitOrganicWave(v, m, bp, new Vec3(0, 1, 0), 0.58 - s * 0.05, 0.12, time,
                        3200L + s * 19L, DominionShader.FX_PLASMA, 255, 224, 118, 155 - s * 22);
                DominionShader.emitBillboard(v, m, bp, camRight, camUp, 0.72 - s * 0.08, 0,
                        DominionShader.FX_GLOW, 75 + s, 255, 226, 122, 90 - s * 15);
            }
        }
    }

    /** Shield & Dome VFX: dissolving organic hex energy mist + crackling defense arcs (no stiff flat discs!). */
    private static void emitShieldVfx(VertexConsumer v, Matrix4f m, ClientFormation f, Vec3 chest, Vec3 forward,
                                      double time, float tickDelta, Vec3 camPos, boolean firstPerson, boolean bodyPass) {
        int count = f.blocks.size();
        if (f.formation == BlockDominionBrain.FormationKind.SHIELD_SINGLE) {
            double r = Math.min(2.55, 1.25 + count * 0.05);
            Vec3 n = f.threatPrimary.lengthSqr() > 1e-4 ? f.threatPrimary.normalize() : forward;
            Vec3 c = chest.add(n.scale(1.55));
            if (bodyPass) {
                DominionShader.emitOrganicWave(v, m, c, n, r * 0.85, 0.28, time, 2100L, DominionShader.FX_BRUSH, 28, 20, 14, firstPerson ? 55 : 105);
            } else {
                DominionShader.emitDisc(v, m, c, n, r, DominionShader.FX_HEX_MIST, 14, 255, 214, 96, firstPerson ? 95 : 185);
            }
        } else if (f.formation == BlockDominionBrain.FormationKind.SHIELD_MULTI) {
            double r = Math.min(2.15, 1.15 + count * 0.04);
            Vec3 n1 = f.threatPrimary.lengthSqr() > 1e-4 ? f.threatPrimary.normalize() : forward;
            Vec3 n2 = f.threatSecondary.lengthSqr() > 1e-4 ? f.threatSecondary.normalize() : forward.scale(-1);
            if (bodyPass) {
                DominionShader.emitOrganicWave(v, m, chest.add(n1.scale(1.55)), n1, r * 0.82, 0.24, time, 2110L, DominionShader.FX_BRUSH, 28, 20, 14, firstPerson ? 50 : 95);
                DominionShader.emitOrganicWave(v, m, chest.add(n2.scale(1.55)), n2, r * 0.82, 0.24, time, 2120L, DominionShader.FX_BRUSH, 28, 20, 14, firstPerson ? 50 : 95);
            } else {
                int a = firstPerson ? 85 : 175;
                DominionShader.emitDisc(v, m, chest.add(n1.scale(1.55)), n1, r, DominionShader.FX_HEX_MIST, 15, 255, 214, 96, a);
                DominionShader.emitDisc(v, m, chest.add(n2.scale(1.55)), n2, r, DominionShader.FX_HEX_MIST, 16, 255, 224, 118, a);
            }
        } else if (f.formation == BlockDominionBrain.FormationKind.SHIELD_DOME) {
            Vec3 c = chest.add(0, 0.15, 0);
            if (bodyPass) {
                DominionShader.emitOrganicWave(v, m, c, new Vec3(0, 1, 0), 2.25, 0.34, time, 2200L, DominionShader.FX_BRUSH, 26, 20, 14, firstPerson ? 55 : 105);
            } else {
                int domeA = firstPerson ? 75 : 155;
                DominionShader.emitDisc(v, m, c, new Vec3(0, 1, 0), 2.75, DominionShader.FX_HEX_MIST, 18, 255, 218, 102, domeA);
                DominionShader.emitDisc(v, m, c, new Vec3(Math.cos(time * 0.04), 0.28, Math.sin(time * 0.04)),
                        2.75, DominionShader.FX_HEX_MIST, 19, 255, 234, 148, domeA);
                DominionShader.emitDisc(v, m, c, new Vec3(-Math.sin(time * 0.04), 0.28, Math.cos(time * 0.04)),
                        2.75, DominionShader.FX_HEX_MIST, 20, 240, 202, 84, domeA);
            }
        }
        // Crackling golden lightning binding adjacent shield blocks
        if (!bodyPass && count >= 4) {
            int arcs = Math.min(6, count / 4);
            for (int a = 0; a < arcs; a++) {
                int i1 = (a * 3 + (int) (time / 4)) % count;
                int i2 = (i1 + 2) % count;
                Vec3 p1 = f.blocks.get(i1).prevPos.lerp(f.blocks.get(i1).pos, tickDelta).subtract(camPos);
                Vec3 p2 = f.blocks.get(i2).prevPos.lerp(f.blocks.get(i2).pos, tickDelta).subtract(camPos);
                if (p1.distanceToSqr(p2) < 6.5) {
                    DominionShader.emitLiveBolt(v, m, p1, p2, 5, 0.12, 0.032, 2400L + a * 19L, time, 255, 234, 145, firstPerson ? 85 : 155);
                }
            }
        }
    }

    /** Myriad Volley charging VFX: dark ink vortex + crackling lightning convergence + blazing soft focal glow. */
    private static void emitVolleyVfx(VertexConsumer v, Matrix4f m, ClientFormation f, Vec3 chest, Vec3 look,
                                      Vec3 camRight, Vec3 camUp, double time, float tickDelta, Vec3 camPos, boolean bodyPass) {
        float vp = Mth.clamp(f.volleyTicks / (float) BlockDominionRules.VOLLEY_CHARGE_TICKS, 0f, 1f);
        Vec3 focal = chest.add(look.normalize().scale(2.15));
        int count = f.blocks.size();
        int step = Math.max(1, count / 12);

        if (bodyPass) {
            DominionShader.emitOrganicWave(v, m, focal, look, 0.75 + (1.0 - vp) * 1.45, 0.28, time * 1.5, 2800L,
                    DominionShader.FX_BRUSH, 24, 16, 12, (int) (145 * vp + 45));
            for (int i = 0; i < count; i += step) {
                Vec3 bp = f.blocks.get(i).prevPos.lerp(f.blocks.get(i).pos, tickDelta).subtract(camPos);
                DominionShader.emitLiveBolt(v, m, bp, focal, 6, 0.16, 0.075, 2900L + i * 13L, time, 22, 16, 12, (int) (125 * vp + 25));
            }
        } else {
            DominionShader.emitBillboard(v, m, focal, camRight, camUp, 1.35 + vp * 0.95, 0,
                    DominionShader.FX_GLOW, 22, 255, 232, 138, (int) (165 * vp + 55));
            DominionShader.emitOrganicWave(v, m, focal, look, 0.65 + (1.0 - vp) * 1.35, 0.18, -time * 1.8, 2810L,
                    DominionShader.FX_PLASMA, 255, 228, 124, (int) (185 * vp + 45));
            for (int i = 0; i < count; i += step) {
                Vec3 bp = f.blocks.get(i).prevPos.lerp(f.blocks.get(i).pos, tickDelta).subtract(camPos);
                DominionShader.emitLiveBolt(v, m, bp, focal, 6, 0.16, 0.038 + vp * 0.02, 2900L + i * 13L, time, 255, 236, 152, (int) (175 * vp + 35));
            }
        }
    }

    /** Auto Mode Gravity Field: rolling obsidian-amber ground smoke + ragged expanding plasma waves + rising tendrils. */
    private static void emitAutoFieldVfx(VertexConsumer v, Matrix4f m, Vec3 feet, Vec3 camRight, Vec3 camUp,
                                         double time, boolean bodyPass) {
        if (bodyPass) {
            DominionShader.emitOrganicWave(v, m, feet, new Vec3(0, 1, 0), BlockDominionRules.AUTO_SCAN_RADIUS - 0.3,
                    0.55, time * 0.6, 3500L, DominionShader.FX_BRUSH, 32, 24, 16, 115);
            for (int i = 0; i < 10; i++) {
                double ang = (Math.PI * 2.0 * i / 10.0) + time * 0.018;
                double rad = 3.2 + (i % 3) * 2.4;
                Vec3 p = feet.add(Math.cos(ang) * rad, 0.35, Math.sin(ang) * rad);
                DominionShader.emitBillboard(v, m, p, camRight, camUp, 1.35, time * 0.02 + i,
                        DominionShader.FX_SMOKE, 80 + i, (i & 1) == 0 ? 32 : 128, (i & 1) == 0 ? 24 : 94, (i & 1) == 0 ? 18 : 34, 85);
            }
        } else {
            DominionShader.emitOrganicWave(v, m, feet, new Vec3(0, 1, 0), BlockDominionRules.AUTO_SCAN_RADIUS - 0.4,
                    0.34, time * 0.75, 3510L, DominionShader.FX_PLASMA, 245, 204, 84, 140);
            double pulseR = 1.6 + ((time * 0.24) % (BlockDominionRules.AUTO_SCAN_RADIUS - 1.6));
            int pulseA = (int) (165 * (1.0 - pulseR / BlockDominionRules.AUTO_SCAN_RADIUS));
            DominionShader.emitOrganicWave(v, m, feet.add(0, 0.06, 0), new Vec3(0, 1, 0), pulseR,
                    0.26, -time * 0.9, 3520L, DominionShader.FX_PLASMA, 255, 232, 142, pulseA);
            for (int s = 0; s < 10; s++) {
                double ang = s * (Math.PI / 5.0) + time * 0.022;
                double r = 2.6 + (s % 4) * 1.9;
                double h = 1.4 + 0.6 * Math.sin(time * 0.14 + s);
                Vec3 base = feet.add(Math.cos(ang) * r, 0.0, Math.sin(ang) * r);
                DominionShader.emitLiveBolt(v, m, base, base.add(0, h, 0), 5, 0.14, 0.045, 3600L + s * 17L, time, 255, 224, 118, 105);
            }
        }
    }

    private static void emitWingTrail(VertexConsumer v, Matrix4f m, ArrayDeque<Vec3> trail, Vec3 camPos,
                                      double width, int seed, boolean boosted) {
        if (trail.size() < 2) return;
        Vec3[] pts = new Vec3[trail.size()];
        int i = 0;
        for (Vec3 p : trail) pts[i++] = p.subtract(camPos);
        DominionShader.emitTrail(v, m, pts, width, DominionShader.FX_PLASMA, seed,
                255, boosted ? 238 : 216, boosted ? 162 : 102, boosted ? 215 : 145);
    }
}
