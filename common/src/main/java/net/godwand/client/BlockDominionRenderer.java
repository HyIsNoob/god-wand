package net.godwand.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
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
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * Milestone 3A Block Dominion renderer:
 * - Second-order damped harmonic spring-damper block physics
 * - Articulated Block Wings (hover flap + swept Mach boost chevron) & Air-Step Block Stairs
 * - Custom GLSL procedural shader (godwand_dominion) for rune halos, Voronoi hex shields,
 *   flowing plasma ribbons, Mach shockwaves, custom 3D GPU particles, and anime impact frames
 * - CC0 adaptive soundscape (orbit, auto, shield lock, flight wind loop, wing flap, boost, air step).
 */
public final class BlockDominionRenderer {
    private static final int WING_TRAIL_LEN = 12;

    private static final class VisualBlock {
        BlockState state;
        Vec3 pos;
        Vec3 prevPos;
        Vec3 vel = Vec3.ZERO;
        float scale;
        float prevScale;
        float spinPhase;
        Vec3 pickupOrigin;
        long pickupStartTick = -1000;

        VisualBlock(BlockState state, Vec3 startPos, float startScale, float spinPhase) {
            this.state = state;
            this.pos = startPos;
            this.prevPos = startPos;
            this.scale = startScale;
            this.prevScale = startScale;
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
        final int effectId;

        WaveBurst(int playerId, Vec3 axis, double maxRadius, int duration, long startTick, int effectId) {
            this.playerId = playerId;
            this.axis = axis.lengthSqr() > 1e-4 ? axis.normalize() : new Vec3(0, 1, 0);
            this.maxRadius = maxRadius;
            this.duration = duration;
            this.startTick = startTick;
            this.effectId = effectId;
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

    // Stylized shader impact frame state (local player)
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
            float startScale = isPickup ? 0.96f : 0.25f;
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

        // Discrete event VFX, custom particles, anime impact frames & CC0 SFX
        switch (payload.eventKind()) {
            case BlockDominionRules.EVENT_PICKUP -> {
                Vec3 origin = new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
                ImpactDebrisRenderer.groundWave(origin.add(0, -0.4, 0), 1.9, 0xEBC64E);
                DominionParticles.spawnRingBurst(origin, new Vec3(0, 1, 0), 0.55, 10, 0.14, 255, 218, 108, DominionParticles.STYLE_SHARD);
                BlockDominionSounds.play(origin, BlockDominionSounds.PICKUP, 0.55f, 0.92f + Math.min(ids.length, 32) * 0.012f);
            }
            case BlockDominionRules.EVENT_UNFOLD -> {
                f.unfoldStartTick = now;
                addWave(payload.playerId(), new Vec3(0, 1, 0), 3.3, 12, now, DominionShader.FX_RUNE_RING);
                DominionParticles.spawnRingBurst(ownerPos, new Vec3(0, 1, 0), 1.4, 16, 0.18, 255, 224, 122, DominionParticles.STYLE_STAR);
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.UNFOLD, 0.7f, 1.05f);
            }
            case BlockDominionRules.EVENT_VOLLEY -> {
                BlockDominionSounds.stopVolleyCharge(payload.playerId());
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.VOLLEY_RELEASE, 1.05f, 1.0f);
                Vec3 aim = owner != null ? owner.getViewVector(1.0f) : new Vec3(0, 0, 1);
                addWave(payload.playerId(), aim, 5.2, 14, now, DominionShader.FX_MACH_SHOCK);
                DominionParticles.spawnRingBurst(ownerPos.add(aim.scale(1.5)), aim, 1.8, 28, 0.32, 255, 232, 138, DominionParticles.STYLE_STAR);
                if (isLocal) {
                    triggerImpactFrame(4, 1.0f, (int) (now & 63));
                }
            }
            case BlockDominionRules.EVENT_PARRY -> {
                Vec3 threatDir = new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
                if (PARRIES.size() >= 16) PARRIES.remove(0);
                PARRIES.add(new ParryBurst(payload.playerId(), threatDir, decodeState(payload.eventBlockId()), now));
                Vec3 impactPt = ownerPos.add(threatDir.normalize().scale(1.35));
                DominionParticles.spawnRingBurst(impactPt, threatDir, 0.85, 22, 0.28, 255, 234, 145, DominionParticles.STYLE_SHARD);
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.PARRY, 0.95f, 1.02f);
                if (isLocal) {
                    triggerImpactFrame(3, 0.88f, (int) ((now + 17) & 63));
                }
            }
            case BlockDominionRules.EVENT_FLIGHT_START -> {
                f.leftWingTrail.clear();
                f.rightWingTrail.clear();
                f.lastFlapSoundTick = now;
                addWave(payload.playerId(), new Vec3(0, 1, 0), 3.8, 12, now, DominionShader.FX_MACH_SHOCK);
                DominionParticles.spawnRingBurst(ownerPos, new Vec3(0, 1, 0), 1.6, 22, 0.24, 255, 226, 124, DominionParticles.STYLE_STAR);
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.WING_FLAP, 0.85f, 0.96f);
            }
            case BlockDominionRules.EVENT_FLIGHT_END -> {
                f.leftWingTrail.clear();
                f.rightWingTrail.clear();
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.UNFOLD, 0.45f, 0.82f);
            }
            case BlockDominionRules.EVENT_FLIGHT_BOOST -> {
                Vec3 aim = owner != null ? owner.getViewVector(1.0f) : new Vec3(0, 0, 1);
                addWave(payload.playerId(), aim, 4.4, 10, now, DominionShader.FX_MACH_SHOCK);
                DominionParticles.spawnRingBurst(ownerPos, aim, 1.5, 20, 0.30, 255, 240, 175, DominionParticles.STYLE_STAR);
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.FLIGHT_BOOST, 0.92f, 1.04f);
                if (isLocal) {
                    triggerImpactFrame(2, 0.55f, (int) ((now + 31) & 63));
                }
            }
            case BlockDominionRules.EVENT_AIR_STEP -> {
                Vec3 stepPos = new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
                if (STEP_FLASHES.size() >= 18) STEP_FLASHES.remove(0);
                STEP_FLASHES.add(new StepFlash(stepPos, now));
                DominionParticles.spawnRingBurst(stepPos, new Vec3(0, 1, 0), 0.72, 12, 0.13, 255, 224, 126, DominionParticles.STYLE_SHARD);
                BlockDominionSounds.play(stepPos, BlockDominionSounds.STEP, 0.52f, 0.95f + (float) ((now % 5) * 0.04));
            }
            default -> {
                if (wasStowed && !f.stowed) {
                    f.unfoldStartTick = now;
                    addWave(payload.playerId(), new Vec3(0, 1, 0), 3.1, 12, now, DominionShader.FX_RUNE_RING);
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

    private static void addWave(int playerId, Vec3 axis, double maxRadius, int duration, long now, int effectId) {
        if (WAVES.size() >= 14) WAVES.remove(0);
        WAVES.add(new WaveBurst(playerId, axis, maxRadius, duration, now, effectId));
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
                        double base = 0.14 + Math.min(1.0, cf.blocks.size() / 32.0) * 0.28;
                        return cf.stowed ? base * 0.35 : (cf.formation.isShield() ? base * 1.25 : base);
                    },
                    () -> {
                        ClientFormation cf = FORMATIONS.get(pid);
                        if (cf == null) return 1.0;
                        double countFrac = Math.min(1.0, cf.blocks.size() / 32.0);
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

                Vec3 offset = BlockDominionBrain.slotOffset(
                        f.formation, i, count, now, look, f.threatPrimary, f.threatSecondary, unfoldProgress, volleyProgress);
                Vec3 target = chest.add(offset);
                float targetScale = targetBlockScale(f, i, count, unfoldProgress, volleyProgress);

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
                        // Carry player's frame-to-frame motion into the spring so high-speed flight/boost stays
                        // cleanly locked to the player (never lagging into the 3rd-person camera) while keeping
                        // silky spring inertia on turns and formation swaps!
                        double carry = f.formation.isMobility() ? 0.92 : (f.formation.isShield() ? 0.85 : 0.65);
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
                            stiffness = f.formation == BlockDominionBrain.FormationKind.WINGS_BOOST ? 0.64 : 0.50;
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
                vb.scale = Mth.lerp(0.36f, vb.scale, targetScale);
                vb.spinPhase += f.stowed ? 0.08f : (f.volleyCharging ? 0.14f : 0.045f);
            }

            // Wingtip trail recording, rhythmic wing-flap SFX, and continuous custom GPU particle emission
            if (!f.hiddenInCutscene && f.mode == BlockDominionRules.Mode.MANUAL && !f.blocks.isEmpty()) {
                if (f.formation.isWings()) {
                    boolean boosted = f.formation == BlockDominionBrain.FormationKind.WINGS_BOOST;
                    Vec3 lTip = chest.add(BlockDominionBrain.wingTipOffset(true, now, look, boosted));
                    Vec3 rTip = chest.add(BlockDominionBrain.wingTipOffset(false, now, look, boosted));
                    pushTrail(f.leftWingTrail, lTip);
                    pushTrail(f.rightWingTrail, rTip);

                    // Emit shimmering golden wingtip sparks & Mach wind streaks
                    int emitCount = boosted ? 3 : 1;
                    Vec3 backVel = owner.getDeltaMovement().scale(-0.35);
                    for (int k = 0; k < emitCount; k++) {
                        DominionParticles.spawn(lTip, backVel.add((Math.random() - 0.5) * 0.06, (Math.random() - 0.5) * 0.06, (Math.random() - 0.5) * 0.06),
                                boosted ? 0.22f : 0.15f, boosted ? 12 : 16, 0.90, 0.0,
                                255, boosted ? 242 : 220, boosted ? 175 : 112,
                                boosted ? DominionParticles.STYLE_STAR : DominionParticles.STYLE_EMBER);
                        DominionParticles.spawn(rTip, backVel.add((Math.random() - 0.5) * 0.06, (Math.random() - 0.5) * 0.06, (Math.random() - 0.5) * 0.06),
                                boosted ? 0.22f : 0.15f, boosted ? 12 : 16, 0.90, 0.0,
                                255, boosted ? 242 : 220, boosted ? 175 : 112,
                                boosted ? DominionParticles.STYLE_STAR : DominionParticles.STYLE_EMBER);
                    }
                    // Subtle rhythmic wingbeat whoosh during calm hovering/cruising
                    if (!boosted && now - f.lastFlapSoundTick >= 29) {
                        f.lastFlapSoundTick = now;
                        BlockDominionSounds.play(chest, BlockDominionSounds.WING_FLAP, 0.32f, 0.88f + (float) (Math.random() * 0.12));
                    }
                } else {
                    f.leftWingTrail.clear();
                    f.rightWingTrail.clear();
                    // Ambient sovereign golden embers drifting off orbiting blocks / stairs
                    if (!f.stowed && (now % 2 == 0)) {
                        VisualBlock sample = f.blocks.get((int) (now % count));
                        DominionParticles.spawnAmbient(sample.pos, 0.28, 0.035, 255, 218, 106,
                                (now % 4 == 0) ? DominionParticles.STYLE_STAR : DominionParticles.STYLE_EMBER);
                    }
                    if (f.formation == BlockDominionBrain.FormationKind.AIR_STAIRS && (now % 2 == 0)) {
                        DominionParticles.spawnAmbient(owner.position().add(0, -0.15, 0), 0.75, 0.025, 255, 232, 138, DominionParticles.STYLE_SHARD);
                    }
                }
            } else if (!f.hiddenInCutscene && f.mode == BlockDominionRules.Mode.AUTO && (now % 2 == 0)) {
                double ang = (now * 0.45) % (Math.PI * 2.0);
                double rad = 1.8 + (now % 7) * 1.1;
                Vec3 p = owner.position().add(Math.cos(ang) * rad, 0.25, Math.sin(ang) * rad);
                DominionParticles.spawnAmbient(p, 0.45, 0.055, 255, 224, 118, DominionParticles.STYLE_EMBER);
            }
        }
    }

    private static void pushTrail(ArrayDeque<Vec3> trail, Vec3 pt) {
        if (trail.size() >= WING_TRAIL_LEN) trail.removeFirst();
        trail.addLast(pt);
    }

    private static float targetBlockScale(ClientFormation f, int index, int count, float unfoldProgress, float volleyProgress) {
        if (f.formation.isShield()) return 0.72f;
        if (f.formation == BlockDominionBrain.FormationKind.AIR_STAIRS) {
            return index < 6 ? 0.62f : 0.44f;
        }
        if (f.formation.isWings()) {
            int spine = count >= 10 ? 2 : 0;
            if (index < spine) return 0.52f;
            int wingBlock = index - spine;
            int perWing = Math.max(1, (count - spine + 1) / 2);
            int primary = Math.max(3, (int) Math.ceil(perWing * 0.62));
            boolean isPrimary = (wingBlock / 2) < primary;
            return isPrimary ? 0.56f : 0.44f;
        }
        return BlockDominionRules.blockScale(f.stowed, unfoldProgress, f.volleyCharging, volleyProgress);
    }

    /** Stylized anime impact frame overlay rendered through custom GLSL shader (respects impactFrames config). */
    public static void renderHud(GuiGraphics graphics) {
        if (impactFrameTicks <= 0 || !GodWandConfig.CLIENT.impactFrames) return;
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        float frac = Mth.clamp(impactFrameTicks / (float) Math.max(1, impactFrameMax), 0f, 1f);
        float alpha = Mth.clamp(frac * impactFrameIntensity, 0f, 1f);
        int alphaByte = Mth.clamp((int) (alpha * 225f), 0, 255);
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
            buf.addVertex(mat, 0, h, 0).setUv(u0, v1).setColor(255, 222, 118, alphaByte);
            buf.addVertex(mat, w, h, 0).setUv(u1, v1).setColor(255, 222, 118, alphaByte);
            buf.addVertex(mat, w, 0, 0).setUv(u1, v0).setColor(255, 222, 118, alphaByte);
            buf.addVertex(mat, 0, 0, 0).setUv(u0, v0).setColor(255, 222, 118, alphaByte);
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

        // 1. Render solid orbiting / shield / dome / fan / wings / stair blocks & Auto levitating stones
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
                    float scale = Mth.lerp(tickDelta, vb.prevScale, vb.scale);
                    if (firstPerson && renderWorld.distanceToSqr(camPos) < 0.72 * 0.72) continue;

                    BlockPos lightPos = BlockPos.containing(renderWorld);
                    if (world.getBlockState(lightPos).isSolidRender(world, lightPos)) lightPos = owner.blockPosition().above();
                    int light = LevelRenderer.getLightColor(world, lightPos);

                    matrices.pushPose();
                    matrices.translate(renderWorld.x - camPos.x, renderWorld.y - camPos.y, renderWorld.z - camPos.z);

                    applySmartOrientation(matrices, f, i, count, renderWorld, chest, look, time, vb.spinPhase + tickDelta * 0.045f);
                    applyFormationScale(matrices, f, i, scale);
                    matrices.translate(-0.5, -0.5, -0.5);
                    blockRenderer.renderSingleBlock(vb.state, matrices, bufferSource, light, OverlayTexture.NO_OVERLAY);
                    matrices.popPose();
                }
            } else if (f.mode == BlockDominionRules.Mode.AUTO) {
                renderAutoGravityPebbles(matrices, bufferSource, blockRenderer, owner, camPos, time, tickDelta, firstPerson);
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
            BlockPos lightPos = BlockPos.containing(pos);
            if (world.getBlockState(lightPos).isSolidRender(world, lightPos)) lightPos = owner.blockPosition().above();
            int light = LevelRenderer.getLightColor(world, lightPos);
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

        // 3. Custom GLSL procedural VFX pass (rune halos, hex shields, ribbons, Mach cones, shockwaves, custom 3D GPU particles)
        renderCustomShaderVfx(matrices, camera, time, tickDelta, client, localCutscene);
    }

    private static void applyFormationScale(PoseStack matrices, ClientFormation f, int index, float scale) {
        if (f.formation == BlockDominionBrain.FormationKind.AIR_STAIRS && index < 6) {
            // Sculpted broad flat stepping stones under feet
            matrices.scale(scale * 1.18f, scale * 0.48f, scale * 1.18f);
        } else if (f.formation.isWings()) {
            // Sleek elongated stone-feather pinion proportions
            boolean boosted = f.formation == BlockDominionBrain.FormationKind.WINGS_BOOST;
            matrices.scale(scale * (boosted ? 1.25f : 1.12f), scale * 0.56f, scale * (boosted ? 1.40f : 0.95f));
        } else {
            matrices.scale(scale, scale, scale);
        }
    }

    /** Aligns block rotation to active formation: flat stair treads, angled wing feathers, shield walls, aimed volley, tumbling orbit. */
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
            float yaw = (float) (Mth.atan2(-look.x, look.z) * (180.0 / Math.PI));
            matrices.mulPose(Axis.YP.rotationDegrees(-yaw));
            int spine = count >= 10 ? 2 : 0;
            if (index >= spine) {
                int wingBlock = index - spine;
                float side = (wingBlock & 1) == 0 ? -1f : 1f;
                int perWing = Math.max(1, (count - spine + 1) / 2);
                float u = Math.min(1f, (wingBlock / 2) / (float) Math.max(1, perWing - 1));
                if (f.formation == BlockDominionBrain.FormationKind.WINGS_BOOST) {
                    matrices.mulPose(Axis.YP.rotationDegrees(side * (-36f - u * 18f)));
                    matrices.mulPose(Axis.ZP.rotationDegrees(side * (22f + u * 14f)));
                    matrices.mulPose(Axis.XP.rotationDegrees(-18f));
                } else {
                    float flapAngle = (float) Math.sin(time * 0.22 - u * 1.25) * 18f;
                    matrices.mulPose(Axis.YP.rotationDegrees(side * (-14f - u * 12f)));
                    matrices.mulPose(Axis.ZP.rotationDegrees(side * (20f + flapAngle)));
                }
            }
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

    private static void renderAutoGravityPebbles(PoseStack matrices, MultiBufferSource.BufferSource bufferSource,
                                                 BlockRenderDispatcher blockRenderer, Entity owner,
                                                 Vec3 camPos, double time, float tickDelta, boolean firstPerson) {
        Vec3 base = owner.getPosition(tickDelta);
        int pebbleCount = 18;
        for (int i = 0; i < pebbleCount; i++) {
            double angle = (Math.PI * 2.0 * i / pebbleCount) + time * 0.016 * ((i & 1) == 0 ? 1 : -1);
            double dist = 2.4 + (i % 5) * 1.65;
            double wx = base.x + Math.cos(angle) * dist;
            double wz = base.z + Math.sin(angle) * dist;
            int bx = Mth.floor(wx), bz = Mth.floor(wz);
            if (!world.getChunkSource().hasChunk(bx >> 4, bz >> 4)) continue;
            int groundY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            if (Math.abs(groundY - base.y) > 6) continue;
            BlockPos samplePos = new BlockPos(bx, groundY - 1, bz);
            BlockState ground = world.getBlockState(samplePos);
            if (ground.isAir() || !ground.getFluidState().isEmpty()) ground = Blocks.STONE.defaultBlockState();
            double hoverY = groundY + 0.48 + Math.sin(time * 0.12 + i * 1.7) * 0.18;
            Vec3 p = new Vec3(wx, hoverY, wz);
            if (firstPerson && p.distanceToSqr(camPos) < 1.2 * 1.2) continue;

            float scale = 0.23f + (i % 3) * 0.065f;
            int light = LevelRenderer.getLightColor(world, BlockPos.containing(p));
            matrices.pushPose();
            matrices.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
            matrices.mulPose(Axis.YP.rotationDegrees((float) (time * 3.8 + i * 37)));
            matrices.mulPose(Axis.XP.rotationDegrees((float) (time * 2.4 + i * 23)));
            matrices.scale(scale, scale, scale);
            matrices.translate(-0.5, -0.5, -0.5);
            blockRenderer.renderSingleBlock(ground, matrices, bufferSource, light, OverlayTexture.NO_OVERLAY);
            matrices.popPose();
        }
    }

    private static void renderCustomShaderVfx(PoseStack matrices, Camera camera, double time, float tickDelta,
                                              Minecraft client, boolean localCutscene) {
        Vec3 camPos = camera.getPosition();
        float seconds = (float) (time / 20.0);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
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
                    Vec3 feet = owner.getPosition(tickDelta).add(0, 0.12, 0).subtract(camPos);
                    DominionShader.emitDisc(buffer, mat, feet, new Vec3(0, 1, 0),
                            BlockDominionRules.AUTO_SCAN_RADIUS + 0.6, DominionShader.FX_RUNE_RING, 1, 245, 204, 86, 145);
                    DominionShader.emitDisc(buffer, mat, feet.add(0, 0.04, 0), new Vec3(0, 1, 0),
                            4.2, DominionShader.FX_RUNE_RING, 7, 255, 232, 138, 125);
                    double pulseR = 1.6 + ((time * 0.24) % (BlockDominionRules.AUTO_SCAN_RADIUS - 1.6));
                    int pulseA = (int) (170 * (1.0 - pulseR / BlockDominionRules.AUTO_SCAN_RADIUS));
                    DominionShader.emitDisc(buffer, mat, feet.add(0, 0.08, 0), new Vec3(0, 1, 0),
                            pulseR, DominionShader.FX_MACH_SHOCK, 11, 255, 238, 165, pulseA);
                    for (int s = 0; s < 8; s++) {
                        double ang = s * (Math.PI / 4.0) + time * 0.025;
                        double r = 3.2 + (s % 3) * 2.1;
                        double h = 1.35 + 0.55 * Math.sin(time * 0.14 + s);
                        Vec3 base = feet.add(Math.cos(ang) * r, 0.0, Math.sin(ang) * r);
                        DominionShader.emitBeam(buffer, mat, base, base.add(0, h, 0), 0.09, s + 20, 255, 224, 128, 110);
                    }
                    emitted = true;
                } else if (f.mode == BlockDominionRules.Mode.MANUAL && !f.blocks.isEmpty()) {
                    int count = f.blocks.size();

                    // Flowing golden plasma pickup tethers
                    for (int i = 0; i < count; i++) {
                        VisualBlock vb = f.blocks.get(i);
                        if (vb.pickupOrigin != null) {
                            double age = time - vb.pickupStartTick;
                            if (age >= 0 && age < 10) {
                                int a = (int) (220 * (1.0 - age / 10.0));
                                Vec3 from = vb.pickupOrigin.subtract(camPos);
                                Vec3 to = vb.prevPos.lerp(vb.pos, tickDelta).subtract(camPos);
                                DominionShader.emitBeam(buffer, mat, from, to, 0.11, i + 3, 255, 224, 118, a);
                                emitted = true;
                            }
                        }
                    }

                    if (f.formation == BlockDominionBrain.FormationKind.IDLE) {
                        int alpha = firstPerson ? 52 : 96;
                        if (count <= 8) {
                            DominionShader.emitDisc(buffer, mat, chest.add(forward.scale(-0.35)), new Vec3(0, 1, 0),
                                    2.4, DominionShader.FX_RUNE_RING, 2, 240, 200, 82, alpha);
                        } else if (count <= 20) {
                            Vec3 n1 = new Vec3(0, Math.cos(0.44), 0).add(forward.scale(Math.sin(0.44))).normalize();
                            Vec3 n2 = new Vec3(0, Math.cos(0.44), 0).add(forward.scale(-Math.sin(0.44))).normalize();
                            DominionShader.emitDisc(buffer, mat, chest.add(forward.scale(-0.35)).add(0, 0.15, 0),
                                    n1, 2.9, DominionShader.FX_RUNE_RING, 3, 240, 200, 82, alpha);
                            DominionShader.emitDisc(buffer, mat, chest.add(forward.scale(-0.35)).add(0, 0.15, 0),
                                    n2, 2.3, DominionShader.FX_RUNE_RING, 5, 255, 234, 152, alpha);
                        } else {
                            Vec3 haloCenter = chest.add(forward.scale(-1.55)).add(0, 0.55, 0);
                            DominionShader.emitDisc(buffer, mat, haloCenter, forward,
                                    3.15, DominionShader.FX_RUNE_RING, 4, 245, 206, 86, alpha + 22);
                            DominionShader.emitDisc(buffer, mat, haloCenter.add(forward.scale(0.18)), forward,
                                    2.30, DominionShader.FX_RUNE_RING, 9, 255, 236, 158, alpha + 10);
                            DominionShader.emitDisc(buffer, mat, chest.add(forward.scale(-0.2)).add(0, -0.15, 0),
                                    new Vec3(0, 1, 0), 2.25, DominionShader.FX_RUNE_RING, 12, 240, 200, 82, alpha);
                        }
                        emitted = true;
                    } else if (f.formation == BlockDominionBrain.FormationKind.SHIELD_SINGLE) {
                        double r = Math.min(2.45, 1.15 + count * 0.07);
                        Vec3 n = f.threatPrimary.lengthSqr() > 1e-4 ? f.threatPrimary.normalize() : forward;
                        DominionShader.emitDisc(buffer, mat, chest.add(n.scale(1.55)), n, r,
                                DominionShader.FX_HEX_SHIELD, 14, 255, 214, 96, firstPerson ? 105 : 195);
                        emitted = true;
                    } else if (f.formation == BlockDominionBrain.FormationKind.SHIELD_MULTI) {
                        double r = Math.min(2.05, 1.05 + count * 0.05);
                        Vec3 n1 = f.threatPrimary.lengthSqr() > 1e-4 ? f.threatPrimary.normalize() : forward;
                        Vec3 n2 = f.threatSecondary.lengthSqr() > 1e-4 ? f.threatSecondary.normalize() : forward.scale(-1);
                        int a = firstPerson ? 95 : 185;
                        DominionShader.emitDisc(buffer, mat, chest.add(n1.scale(1.55)), n1, r,
                                DominionShader.FX_HEX_SHIELD, 15, 255, 214, 96, a);
                        DominionShader.emitDisc(buffer, mat, chest.add(n2.scale(1.55)), n2, r,
                                DominionShader.FX_HEX_SHIELD, 16, 255, 224, 118, a);
                        emitted = true;
                    } else if (f.formation == BlockDominionBrain.FormationKind.SHIELD_DOME) {
                        int domeA = firstPerson ? 85 : 165;
                        Vec3 c = chest.add(0, 0.15, 0);
                        DominionShader.emitDisc(buffer, mat, c, new Vec3(0, 1, 0), 2.65, DominionShader.FX_HEX_SHIELD, 18, 255, 218, 102, domeA);
                        DominionShader.emitDisc(buffer, mat, c, new Vec3(Math.cos(time * 0.04), 0.28, Math.sin(time * 0.04)),
                                2.65, DominionShader.FX_HEX_SHIELD, 19, 255, 234, 148, domeA);
                        DominionShader.emitDisc(buffer, mat, c, new Vec3(-Math.sin(time * 0.04), 0.28, Math.cos(time * 0.04)),
                                2.65, DominionShader.FX_HEX_SHIELD, 20, 240, 202, 84, domeA);
                        emitted = true;
                    } else if (f.formation == BlockDominionBrain.FormationKind.VOLLEY_FAN) {
                        float vp = Mth.clamp(f.volleyTicks / (float) BlockDominionRules.VOLLEY_CHARGE_TICKS, 0f, 1f);
                        Vec3 focal = chest.add(look.normalize().scale(2.15));
                        DominionShader.emitDisc(buffer, mat, focal, look, 0.95 + (1.0 - vp) * 1.65,
                                DominionShader.FX_RUNE_RING, 21, 255, 226, 118, (int) (195 * vp + 45));
                        int step = Math.max(1, count / 10);
                        for (int i = 0; i < count; i += step) {
                            Vec3 bp = f.blocks.get(i).prevPos.lerp(f.blocks.get(i).pos, tickDelta).subtract(camPos);
                            DominionShader.emitBeam(buffer, mat, bp, focal, 0.065 + vp * 0.045, i + 40, 255, 234, 145, (int) (165 * vp + 25));
                        }
                        emitted = true;
                    } else if (f.formation == BlockDominionBrain.FormationKind.AIR_STAIRS) {
                        // Glowing arcane rune step pads underneath the floating stone staircase treads
                        int stairSlots = Math.min(4, count);
                        for (int s = 0; s < stairSlots; s++) {
                            Vec3 bp = f.blocks.get(s).prevPos.lerp(f.blocks.get(s).pos, tickDelta).subtract(camPos).add(0, -0.18, 0);
                            DominionShader.emitDisc(buffer, mat, bp, new Vec3(0, 1, 0), 0.78 - s * 0.06,
                                    DominionShader.FX_RUNE_RING, 30 + s, 255, 224, 118, 175 - s * 22);
                        }
                        emitted = true;
                    } else if (f.formation.isWings()) {
                        boolean boosted = f.formation == BlockDominionBrain.FormationKind.WINGS_BOOST;
                        // Sovereign wing-core sigil behind shoulders
                        Vec3 spinePos = chest.add(forward.scale(-0.62)).add(0, 0.14, 0);
                        DominionShader.emitDisc(buffer, mat, spinePos, forward, boosted ? 1.25 : 1.18,
                                DominionShader.FX_RUNE_RING, 36, 245, boosted ? 228 : 210, boosted ? 142 : 96, firstPerson ? 55 : 125);

                        // Flowing wingtip plasma slipstream ribbons
                        emitWingTrail(buffer, mat, f.leftWingTrail, camPos, boosted ? 0.36 : 0.18, 41, boosted);
                        emitWingTrail(buffer, mat, f.rightWingTrail, camPos, boosted ? 0.36 : 0.18, 42, boosted);

                        // Crisp supersonic Mach-cone shockwave rings framing the swept wings when boosting!
                        if (boosted) {
                            Vec3 vel = owner.getDeltaMovement();
                            Vec3 axis = vel.lengthSqr() > 0.04 ? vel.normalize() : look.normalize();
                            double pulse = (time * 0.20) % 1.0;
                            DominionShader.emitDisc(buffer, mat, chest.add(axis.scale(0.95 - pulse * 1.4)), axis,
                                    1.25 + pulse * 1.55, DominionShader.FX_MACH_SHOCK, 45, 255, 224, 124, (int) (145 * (1.0 - pulse)));
                            DominionShader.emitDisc(buffer, mat, chest.add(axis.scale(0.25)), axis,
                                    2.05, DominionShader.FX_MACH_SHOCK, 46, 245, 206, 86, firstPerson ? 65 : 115);
                        }
                        emitted = true;
                    }
                }
            }

            // Air-step transient floor pulse rings
            for (StepFlash sf : STEP_FLASHES) {
                double age = time - sf.startTick;
                if (age < 0 || age >= 16) continue;
                double t = age / 16.0;
                int a = (int) (210 * (1.0 - t) * (1.0 - t));
                DominionShader.emitDisc(buffer, mat, sf.pos.subtract(camPos), new Vec3(0, 1, 0),
                        0.55 + t * 1.35, DominionShader.FX_RUNE_RING, 50, 255, 232, 138, a);
                emitted = true;
            }

            // Parry double shockwave + hex shield flash + curving plasma motes
            for (ParryBurst burst : PARRIES) {
                Entity owner = world.getEntity(burst.playerId);
                if (owner == null || owner.distanceToSqr(camPos) > 96.0 * 96.0) continue;
                double age = time - burst.startTick;
                if (age < 0 || age >= 14) continue;
                double t = age / 14.0;
                Vec3 chest = owner.getPosition(tickDelta).add(0, 1.15, 0).subtract(camPos);
                Vec3 impact = chest.add(burst.direction.scale(1.32));
                int a1 = (int) (240 * (1.0 - t) * (1.0 - t));
                DominionShader.emitDisc(buffer, mat, impact, burst.direction, 0.85 + t * 2.15,
                        DominionShader.FX_HEX_SHIELD, 55, 255, 224, 118, a1);
                DominionShader.emitDisc(buffer, mat, impact.add(burst.direction.scale(0.08)), burst.direction,
                        0.55 + t * 2.65, DominionShader.FX_MACH_SHOCK, 56, 255, 246, 200, a1);
                Vec3 side = burst.direction.cross(Math.abs(burst.direction.y) < 0.92 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
                Vec3 up = burst.direction.cross(side).normalize();
                for (int m = 0; m < 6; m++) {
                    double ang = m * (Math.PI / 3.0) + t * 1.4;
                    Vec3 flare = impact.add(side.scale(Math.cos(ang) * (0.95 * (1.0 - t))))
                            .add(up.scale(Math.sin(ang) * (0.95 * (1.0 - t))));
                    Vec3 head = flare.lerp(chest, t * t);
                    Vec3 tail = flare.lerp(chest, Math.max(0.0, t * t - 0.30));
                    DominionShader.emitBeam(buffer, mat, tail, head, 0.06, 60 + m, 255, 232, 142, (int) (215 * (1.0 - t)));
                }
                emitted = true;
            }

            // Unfold, Takeoff, Boost & Volley-release shockwaves
            for (WaveBurst w : WAVES) {
                Entity owner = world.getEntity(w.playerId);
                if (owner == null || owner.distanceToSqr(camPos) > 96.0 * 96.0) continue;
                double age = time - w.startTick;
                if (age < 0 || age >= w.duration) continue;
                double t = age / w.duration;
                double ease = 1.0 - (1.0 - t) * (1.0 - t);
                Vec3 origin = owner.getPosition(tickDelta).add(0, 1.25, 0).subtract(camPos);
                int a = (int) (225 * (1.0 - t) * (1.0 - t));
                DominionShader.emitDisc(buffer, mat, origin, w.axis, 0.55 + ease * w.maxRadius,
                        w.effectId, 70, 255, 228, 128, a);
                emitted = true;
            }

            // Custom 3D GPU Particles (star sparks, embers, crystal shards) batched into the same shader draw!
            if (DominionParticles.emitAll(buffer, mat, camera, tickDelta)) {
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

    private static void emitWingTrail(com.mojang.blaze3d.vertex.VertexConsumer v, Matrix4f m,
                                      ArrayDeque<Vec3> trail, Vec3 camPos, double width, int seed, boolean boosted) {
        if (trail.size() < 2) return;
        Vec3[] pts = new Vec3[trail.size()];
        int i = 0;
        for (Vec3 p : trail) pts[i++] = p.subtract(camPos);
        DominionShader.emitTrail(v, m, pts, width, seed,
                255, boosted ? 242 : 216, boosted ? 176 : 104, boosted ? 225 : 150);
    }
}
