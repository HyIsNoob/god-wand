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
import net.godwand.skill.BlockDominionPalette;
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
import net.minecraft.sounds.SoundEvents;
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
 * Milestone 4 Block Dominion renderer — Purple-grade volumetric visual standard:
 * - Two-pass custom GLSL rendering (BODY alpha-blend + GLOW additive)
 * - Material Resonance: live color adaptation across aura, wing feather plumes, smoke, lightning, impact frame & HUD
 * - Sculpted multi-scale anatomical Block Wings + Low-Altitude Ground Wash
 * - Earthen Surge vortex pickup, Floating Railgun Ring, Aerial Orbital Meteor Barrage, and 130+ stone swarm
 * - Compact resonant BlockDominionHud beside hotbar + crosshair volley arc.
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
        final Quaternionf rot = new Quaternionf();
        final Quaternionf prevRot = new Quaternionf();
        boolean rotInit;
        Vec3 pickupOrigin;
        long pickupStartTick = -1000;
        Vec3 morphOrigin;
        long morphStartTick = -1000;
        int morphDuration = 8;
        int morphDelay = 0;
        double morphArcLift = 0.0;

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
        final int pR, pG, pB;
        final int dR, dG, dB;

        WaveBurst(int playerId, Vec3 axis, double maxRadius, int duration, long startTick, BlockDominionPalette pal) {
            this.playerId = playerId;
            this.axis = axis.lengthSqr() > 1e-4 ? axis.normalize() : new Vec3(0, 1, 0);
            this.maxRadius = maxRadius;
            this.duration = duration;
            this.startTick = startTick;
            this.seed = startTick * 37L + playerId * 13L;
            this.pR = pal.primaryR;
            this.pG = pal.primaryG;
            this.pB = pal.primaryB;
            this.dR = pal.darkR;
            this.dG = pal.darkG;
            this.dB = pal.darkB;
        }
    }

    private static final class StepFlash {
        final Vec3 pos;
        final long startTick;
        final int pR, pG, pB;
        final int dR, dG, dB;

        StepFlash(Vec3 pos, long startTick, BlockDominionPalette pal) {
            this.pos = pos;
            this.startTick = startTick;
            this.pR = pal.primaryR;
            this.pG = pal.primaryG;
            this.pB = pal.primaryB;
            this.dR = pal.darkR;
            this.dG = pal.darkG;
            this.dB = pal.darkB;
        }
    }

    private static final class ClientFormation {
        BlockDominionRules.Mode mode = BlockDominionRules.Mode.OFF;
        BlockDominionBrain.FormationKind formation = BlockDominionBrain.FormationKind.IDLE;
        BlockDominionPalette palette = BlockDominionPalette.SOVEREIGN_GOLD;
        // Smoothly interpolated RGB channels so resonance shifts look fluid and organic
        float pR = 248, pG = 206, pB = 84;
        float hR = 255, hG = 240, hB = 174;
        float dR = 30, dG = 22, dB = 14;
        float mR = 148, mG = 108, mB = 38;

        Vec3 threatPrimary = new Vec3(0, 0, 1);
        Vec3 threatSecondary = Vec3.ZERO;
        boolean stowed;
        boolean volleyCharging;
        boolean hiddenInCutscene;
        boolean flying;
        boolean boosting;
        boolean glideMode;
        boolean airStairs;
        boolean stairAscending;
        boolean umbrella;
        boolean rapidFiring;
        boolean liquidStride;
        boolean sovereignPerch;
        boolean meteorDiving;
        int meteorTier;
        int volleyTicks;
        long unfoldStartTick = -1000;
        long shieldStartTick = -1000;
        long lastFlapSoundTick = -1000;
        long lastUpdateTick;
        Vec3 lastOwnerChest;
        final List<VisualBlock> blocks = new ArrayList<>();
        final ArrayDeque<Vec3> leftWingTrail = new ArrayDeque<>(WING_TRAIL_LEN);
        final ArrayDeque<Vec3> rightWingTrail = new ArrayDeque<>(WING_TRAIL_LEN);
        // Fixed 7-slot ring buffer for world-anchored stepping stones so ONLY the oldest tail block recycles forward
        // while the other 6 steps stay 100% motionless under the player's boots (zero block-shifting jitter!).
        final Vec3[] stepSlots = new Vec3[7];
        int stepCount;
        int stepCursor;
        Vec3 lastStepAnchor;

        int pr() { return Mth.clamp(Math.round(pR), 0, 255); }
        int pg() { return Mth.clamp(Math.round(pG), 0, 255); }
        int pb() { return Mth.clamp(Math.round(pB), 0, 255); }
        int hr() { return Mth.clamp(Math.round(hR), 0, 255); }
        int hg() { return Mth.clamp(Math.round(hG), 0, 255); }
        int hb() { return Mth.clamp(Math.round(hB), 0, 255); }
        int dr() { return Mth.clamp(Math.round(dR), 0, 255); }
        int dg() { return Mth.clamp(Math.round(dG), 0, 255); }
        int db() { return Mth.clamp(Math.round(dB), 0, 255); }
        int mr() { return Mth.clamp(Math.round(mR), 0, 255); }
        int mg() { return Mth.clamp(Math.round(mG), 0, 255); }
        int mb() { return Mth.clamp(Math.round(mB), 0, 255); }

        void stepPalette() {
            float rate = 0.14f;
            pR = Mth.lerp(rate, pR, palette.primaryR);
            pG = Mth.lerp(rate, pG, palette.primaryG);
            pB = Mth.lerp(rate, pB, palette.primaryB);
            hR = Mth.lerp(rate, hR, palette.hotR);
            hG = Mth.lerp(rate, hG, palette.hotG);
            hB = Mth.lerp(rate, hB, palette.hotB);
            dR = Mth.lerp(rate, dR, palette.darkR);
            dG = Mth.lerp(rate, dG, palette.darkG);
            dB = Mth.lerp(rate, dB, palette.darkB);
            mR = Mth.lerp(rate, mR, palette.midR);
            mG = Mth.lerp(rate, mG, palette.midG);
            mB = Mth.lerp(rate, mB, palette.midB);
        }
    }

    private static final class ClientCage {
        final int casterId;
        final int targetId;
        final Vec3 anchor;
        final List<BlockState> blocks;
        final List<Vec3> launchOrigins;
        final long startTick;
        final BlockDominionPalette palette;
        final double yawOffset;
        boolean impaled;
        long impaleTick = -1;
        boolean victimKilled;

        ClientCage(int casterId, int targetId, Vec3 anchor, List<BlockState> blocks,
                   List<Vec3> launchOrigins, long startTick, BlockDominionPalette palette, double yawOffset) {
            this.casterId = casterId;
            this.targetId = targetId;
            this.anchor = anchor;
            this.blocks = blocks;
            this.launchOrigins = launchOrigins;
            this.startTick = startTick;
            this.palette = palette;
            this.yawOffset = yawOffset;
        }
    }

    /** Lingering crossed Stone Lance monument pinned through a slain victim's death spot into the ground (~4.8s). */
    private record ImpaledMonument(Vec3 pinPos, List<BlockState> blocks, Vec3[] spearDirs,
                                   long startTick, BlockDominionPalette palette) {}

    /** Crisp 7-tick Railgun muzzle flash & electromagnetic tracer beam on rapid-fire shot. */
    private record MuzzleTracer(Vec3 origin, Vec3 dir, long startTick, BlockDominionPalette palette) {}

    /** Multi-tier Meteor Strike impact blast dome, rising thermal fire pillar & molten volcanic ground scorch! */
    private record MeteorBlast(Vec3 pos, int tier, long startTick) {}

    private static final Map<Integer, ClientFormation> FORMATIONS = new HashMap<>();
    private static final Map<Long, ClientCage> ACTIVE_CAGES = new HashMap<>();
    private static final List<ImpaledMonument> IMPALED_MONUMENTS = new ArrayList<>();
    private static final List<MuzzleTracer> MUZZLE_TRACERS = new ArrayList<>();
    private static final List<MeteorBlast> METEOR_BLASTS = new ArrayList<>();
    private static final List<ParryBurst> PARRIES = new ArrayList<>();
    private static final List<WaveBurst> WAVES = new ArrayList<>();
    private static final List<StepFlash> STEP_FLASHES = new ArrayList<>();
    private static ClientLevel world;

    private static int impactFrameTicks;
    private static int impactFrameMax = 3;
    private static int impactFrameSeed;
    private static float impactFrameIntensity = 1.0f;
    private static int impactR = 255, impactG = 218, impactB = 108;

    // Cinematic flight camera banking & smooth speed/charge FOV
    private static float camRollDeg, prevCamRollDeg;
    private static float camFovMul = 1.0f, prevCamFovMul = 1.0f;
    private static float lastPlayerYaw = Float.NaN;

    private BlockDominionRenderer() {}

    public static void reset() {
        FORMATIONS.clear();
        ACTIVE_CAGES.clear();
        IMPALED_MONUMENTS.clear();
        MUZZLE_TRACERS.clear();
        METEOR_BLASTS.clear();
        PARRIES.clear();
        WAVES.clear();
        STEP_FLASHES.clear();
        DominionParticles.clear();
        DominionPostFx.reset();
        BlockDominionPose.reset();
        impactFrameTicks = 0;
        camRollDeg = prevCamRollDeg = 0f;
        camFovMul = prevCamFovMul = 1.0f;
        lastPlayerYaw = Float.NaN;
        world = null;
        BlockDominionSounds.reset();
    }

    /** Smooth aerodynamic camera banking roll in degrees during Block Wings flight (read by GameRendererMixin). */
    public static float cameraRollDegrees(float tickDelta) {
        return Mth.lerp(tickDelta, prevCamRollDeg, camRollDeg);
    }

    /** Smooth dynamic FOV multiplier for Block Wings Mach/Jet flight and Volley/Meteor charge. */
    public static double fovMultiplier(float tickDelta) {
        return Mth.lerp(tickDelta, prevCamFovMul, camFovMul);
    }

    public static boolean isLocalManualActive() {
        ClientFormation f = localFormation();
        return f != null && f.mode == BlockDominionRules.Mode.MANUAL && !f.hiddenInCutscene;
    }

    public static int localReserveCount() {
        ClientFormation f = localFormation();
        return f == null ? 0 : f.blocks.size();
    }

    private static long cageKey(int casterId, int targetId) {
        return (((long) casterId) << 32) | (targetId & 0xffffffffL);
    }

    public static boolean hasLocalActiveCage() {
        return localActiveCageCount() > 0;
    }

    public static int localActiveCageCount() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return 0;
        int myId = mc.player.getId();
        int n = 0;
        for (ClientCage c : ACTIVE_CAGES.values()) {
            if (c.casterId == myId) n++;
        }
        return n;
    }

    public static boolean isLocalWingFlying() {
        ClientFormation f = localFormation();
        return f != null && f.mode == BlockDominionRules.Mode.MANUAL && f.flying && !f.hiddenInCutscene;
    }

    public static boolean isLocalAirStairs() {
        ClientFormation f = localFormation();
        return f != null && f.mode == BlockDominionRules.Mode.MANUAL && f.airStairs && !f.flying && !f.hiddenInCutscene;
    }

    public static boolean isLocalGlideMode() {
        ClientFormation f = localFormation();
        return f != null && f.glideMode;
    }

    public static boolean isLocalStowed() {
        ClientFormation f = localFormation();
        return f != null && f.stowed;
    }

    public static boolean isLocalVolleyCharging() {
        ClientFormation f = localFormation();
        return f != null && f.volleyCharging;
    }

    public static BlockDominionPalette localPalette() {
        ClientFormation f = localFormation();
        return f == null ? BlockDominionPalette.SOVEREIGN_GOLD : f.palette;
    }

    public static BlockDominionBrain.FormationKind formationOf(int entityId) {
        ClientFormation f = FORMATIONS.get(entityId);
        return (f == null || f.mode != BlockDominionRules.Mode.MANUAL || f.hiddenInCutscene)
                ? BlockDominionBrain.FormationKind.IDLE : f.formation;
    }

    private static ClientFormation localFormation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return null;
        return FORMATIONS.get(mc.player.getId());
    }

    private static void triggerImpactFrame(int ticks, float intensity, int seed, BlockDominionPalette pal) {
        if (DominionPostFx.ready()) {
            DominionPostFx.triggerImpact(ticks, pal);
            return;
        }
        impactFrameTicks = Math.max(impactFrameTicks, ticks);
        impactFrameMax = Math.max(1, ticks);
        impactFrameIntensity = intensity;
        impactFrameSeed = seed;
        impactR = pal.primaryR;
        impactG = pal.primaryG;
        impactB = pal.primaryB;
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
        f.glideMode = (payload.flags() & BlockDominionRules.FLAG_GLIDE_MODE) != 0;
        f.airStairs = (payload.flags() & BlockDominionRules.FLAG_AIR_STAIRS) != 0;
        f.stairAscending = (payload.flags() & BlockDominionRules.FLAG_STAIR_ASCEND) != 0;
        f.umbrella = (payload.flags() & BlockDominionRules.FLAG_UMBRELLA) != 0;
        f.rapidFiring = (payload.flags() & BlockDominionRules.FLAG_RAPID_FIRING) != 0;
        f.liquidStride = (payload.flags() & BlockDominionRules.FLAG_LIQUID_STRIDE) != 0;
        f.sovereignPerch = (payload.flags() & BlockDominionRules.FLAG_PERCH) != 0;
        f.meteorDiving = (payload.flags() & BlockDominionRules.FLAG_METEOR_DIVE) != 0;
        f.meteorTier = (payload.flags() & BlockDominionRules.FLAG_METEOR_TIER3) != 0 ? 3
                : (payload.flags() & BlockDominionRules.FLAG_METEOR_TIER2) != 0 ? 2
                : (f.meteorDiving ? 1 : 0);
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

        // State-preserving multiset sync: keeps existing VisualBlocks in their EXACT physical slots & textures
        // even after reassignNearestSlots() reorders f.blocks (eliminates block swapping & material flicker!).
        Map<Integer, Integer> targetCounts = new HashMap<>();
        for (int id : ids) targetCounts.merge(id, 1, Integer::sum);

        Map<Integer, Integer> keptCounts = new HashMap<>();
        List<VisualBlock> removedBlocks = new ArrayList<>();
        for (int i = f.blocks.size() - 1; i >= 0; i--) {
            int curId = Block.getId(f.blocks.get(i).state);
            int want = targetCounts.getOrDefault(curId, 0);
            int have = keptCounts.getOrDefault(curId, 0);
            if (have < want) {
                keptCounts.put(curId, have + 1);
            } else {
                removedBlocks.add(f.blocks.remove(i));
            }
        }

        List<BlockState> toAdd = new ArrayList<>();
        Map<Integer, Integer> addedSoFar = new HashMap<>(keptCounts);
        for (int id : ids) {
            int have = addedSoFar.getOrDefault(id, 0);
            int want = targetCounts.getOrDefault(id, 0);
            if (have < want) {
                addedSoFar.put(id, have + 1);
                toAdd.add(decodeState(id));
            }
        }

        boolean isSurgeEvent = payload.eventKind() == BlockDominionRules.EVENT_SURGE;
        int addedCount = Math.max(1, toAdd.size());
        for (int idx = 0; idx < toAdd.size(); idx++) {
            BlockState st = toAdd.get(idx);
            boolean isPickup = (payload.eventKind() == BlockDominionRules.EVENT_PICKUP || isSurgeEvent);
            Vec3 spawnPos;
            if (isSurgeEvent) {
                double ang = (Math.PI * 2.0 * idx / addedCount) + (now * 0.17);
                double rad = 2.6 + (idx % 4) * 1.4;
                spawnPos = new Vec3(payload.eventX() + Math.cos(ang) * rad, payload.eventY() - 0.2, payload.eventZ() + Math.sin(ang) * rad);
            } else {
                spawnPos = isPickup ? new Vec3(payload.eventX(), payload.eventY(), payload.eventZ()) : ownerPos;
            }
            float startScale = isPickup ? 0.94f : 0.25f;
            VisualBlock vb = new VisualBlock(st, spawnPos, startScale, ((f.blocks.size() + idx) * 1.37f) % 6.283f);
            if (isPickup) {
                vb.pickupOrigin = spawnPos;
                vb.pickupStartTick = now;
            }
            f.blocks.add(vb);
        }

        // Update Material Resonance palette dynamically from the live reserve!
        // Keep the previous resonant palette when a Volley/Orbital Barrage empties the reserve so its
        // impact frame and release shockwaves glow in the exact material color of the launched blocks.
        if (!f.blocks.isEmpty()) {
            List<BlockState> states = new ArrayList<>(f.blocks.size());
            for (VisualBlock vb : f.blocks) states.add(vb.state);
            f.palette = BlockDominionPalette.resolveStates(states);
        } else if (payload.eventKind() != BlockDominionRules.EVENT_VOLLEY
                && payload.eventKind() != BlockDominionRules.EVENT_ORBITAL_BARRAGE
                && payload.eventKind() != BlockDominionRules.EVENT_METEOR_STRIKE
                && oldSize == 0) {
            f.palette = BlockDominionPalette.SOVEREIGN_GOLD;
        }
        BlockDominionPalette pal = f.palette;

        if (prevFormation != f.formation && f.blocks.size() > 1 && owner != null) {
            reassignNearestSlots(f, owner, now);
            triggerFormationAssemblyWave(f, prevFormation, now);
        }

        switch (payload.eventKind()) {
            case BlockDominionRules.EVENT_PICKUP -> {
                Vec3 origin = new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
                DominionParticles.spawnRingBurst(origin, new Vec3(0, 1, 0), 0.55, 8, 0.14,
                        pal.primaryR, pal.primaryG, pal.primaryB, DominionParticles.STYLE_SHARD);
                BlockDominionSounds.play(origin, SoundEvents.DEEPSLATE_BREAK, 0.42f, 0.90f + Math.min(ids.length, 48) * 0.008f);
                BlockDominionSounds.play(origin, BlockDominionSounds.PICKUP, 0.50f, 0.94f + Math.min(ids.length, 48) * 0.008f);
            }
            case BlockDominionRules.EVENT_SURGE -> {
                Vec3 base = new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
                addWave(payload.playerId(), new Vec3(0, 1, 0), 7.8, 16, now, pal);
                DominionParticles.spawnRingBurst(base.add(0, 0.35, 0), new Vec3(0, 1, 0), 3.6, 42, 0.30,
                        pal.primaryR, pal.primaryG, pal.primaryB, DominionParticles.STYLE_SHARD);
                for (int s = 0; s < 10; s++) {
                    double a = (Math.PI * 2.0 * s) / 10.0;
                    Vec3 p = base.add(Math.cos(a) * 2.6, 0.25, Math.sin(a) * 2.6);
                    DominionParticles.spawnSmoke(p, new Vec3(Math.cos(a) * 0.08, 0.09, Math.sin(a) * 0.08),
                            0.72f, 18, pal.midR, pal.midG, pal.midB, 110);
                }
                if (isLocal) {
                    DominionPostFx.triggerShockwave(0.88f, 14, pal);
                }
                BlockDominionSounds.play(base, BlockDominionSounds.SURGE, 1.05f, 0.96f);
            }
            case BlockDominionRules.EVENT_FIRE -> {
                Vec3 muzzle = new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
                Vec3 aim = owner != null ? owner.getViewVector(1.0f).normalize() : new Vec3(0, 0, 1);
                if (MUZZLE_TRACERS.size() >= 16) MUZZLE_TRACERS.remove(0);
                MUZZLE_TRACERS.add(new MuzzleTracer(muzzle, aim, now, pal));
                BlockDominionSounds.play(muzzle, BlockDominionSounds.FIRE, 0.84f, 0.94f + (float) ((now % 5) * 0.04));
            }
            case BlockDominionRules.EVENT_UNFOLD -> {
                f.unfoldStartTick = now;
                addWave(payload.playerId(), new Vec3(0, 1, 0), 3.4, 14, now, pal);
                DominionParticles.spawnRingBurst(ownerPos, new Vec3(0, 1, 0), 1.4, 16, 0.18,
                        pal.primaryR, pal.primaryG, pal.primaryB, DominionParticles.STYLE_SHARD);
                if (isLocal) {
                    DominionPostFx.triggerShockwave(0.36f, 9, pal);
                }
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.UNFOLD, 0.7f, 1.05f);
            }
            case BlockDominionRules.EVENT_VOLLEY, BlockDominionRules.EVENT_ORBITAL_BARRAGE -> {
                boolean orbital = payload.eventKind() == BlockDominionRules.EVENT_ORBITAL_BARRAGE;
                BlockDominionSounds.stopVolleyCharge(payload.playerId());
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.VOLLEY_RELEASE, 1.1f, orbital ? 0.88f : 1.0f);
                Vec3 aim = owner != null ? owner.getViewVector(1.0f) : new Vec3(0, 0, 1);
                addWave(payload.playerId(), orbital ? new Vec3(0, 1, 0) : aim, orbital ? 6.4 : 5.2, 14, now, pal);
                DominionParticles.spawnRingBurst(ownerPos.add(orbital ? new Vec3(0, 2.2, 0) : aim.scale(1.5)),
                        orbital ? new Vec3(0, 1, 0) : aim, orbital ? 2.4 : 1.8, 34, 0.34,
                        pal.hotR, pal.hotG, pal.hotB, DominionParticles.STYLE_SHARD);
                if (isLocal) {
                    DominionPostFx.triggerShockwave(orbital ? 1.0f : 0.78f, orbital ? 16 : 12, pal);
                    triggerImpactFrame(orbital ? 4 : 3, 1.0f, (int) (now & 63), pal);
                }
            }
            case BlockDominionRules.EVENT_PARRY -> {
                Vec3 threatDir = new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
                if (PARRIES.size() >= 16) PARRIES.remove(0);
                PARRIES.add(new ParryBurst(payload.playerId(), threatDir, decodeState(payload.eventBlockId()), now));
                Vec3 impactPt = ownerPos.add(threatDir.normalize().scale(1.35));
                DominionParticles.spawnRingBurst(impactPt, threatDir, 0.90, 22, 0.28,
                        pal.hotR, pal.hotG, pal.hotB, DominionParticles.STYLE_SHARD);
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.PARRY, 0.90f, 1.02f);
                BlockDominionSounds.play(ownerPos, SoundEvents.SHIELD_BLOCK, 0.58f, 1.12f);
                // No sakuga impact frame on shields (owner preference: clean defense without screen flash!)
                if (isLocal) {
                    DominionPostFx.triggerShockwave(0.42f, 8, pal);
                }
            }
            case BlockDominionRules.EVENT_HERO_LAND -> {
                f.leftWingTrail.clear();
                f.rightWingTrail.clear();
                Vec3 landPos = new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
                addWave(payload.playerId(), new Vec3(0, 1, 0), 5.6, 14, now, pal);
                DominionParticles.spawnRingBurst(landPos.add(0, 0.15, 0), new Vec3(0, 1, 0), 2.2, 34, 0.30,
                        pal.primaryR, pal.primaryG, pal.primaryB, DominionParticles.STYLE_SHARD);
                if (isLocal) {
                    DominionPostFx.triggerShockwave(0.56f, 11, pal);
                }
                BlockDominionSounds.play(landPos, BlockDominionSounds.WING_FLAP, 0.95f, 0.68f);
                BlockDominionSounds.play(landPos, BlockDominionSounds.IMPACT, 0.72f, 0.85f);
            }
            case BlockDominionRules.EVENT_METEOR_STRIKE -> {
                f.leftWingTrail.clear();
                f.rightWingTrail.clear();
                Vec3 impactPos = new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
                int tier = Mth.clamp(payload.eventBlockId(), 1, 3);
                // Radially kick orbiting stone blocks outward in a dramatic seismic ground-slam burst!
                double kickScale = 0.65 + tier * 0.28;
                for (VisualBlock vb : f.blocks) {
                    Vec3 out = vb.pos.subtract(impactPos);
                    Vec3 horiz = new Vec3(out.x, 0, out.z);
                    Vec3 dir = horiz.lengthSqr() > 1e-4 ? horiz.normalize() : new Vec3(1, 0, 0);
                    vb.vel = dir.scale(kickScale).add(0, 0.24 + tier * 0.14, 0);
                }
                if (METEOR_BLASTS.size() >= 8) METEOR_BLASTS.remove(0);
                METEOR_BLASTS.add(new MeteorBlast(impactPos, tier, now));
                addWave(payload.playerId(), new Vec3(0, 1, 0), 7.5 + tier * 4.2, 18 + tier * 4, now, BlockDominionPalette.INFERNO_OBSIDIAN);
                addWave(payload.playerId(), new Vec3(0, 1, 0), 4.5 + tier * 2.4, 14 + tier * 2, now, pal);
                DominionParticles.spawnRingBurst(impactPos.add(0, 0.25, 0), new Vec3(0, 1, 0),
                        2.8 + tier * 1.6, 36 * tier, 0.38 + tier * 0.12,
                        255, tier == 3 ? 82 : 128, tier == 3 ? 20 : 32, DominionParticles.STYLE_EMBER);
                DominionParticles.spawnRingBurst(impactPos.add(0, 0.20, 0), new Vec3(0, 1, 0),
                        2.2 + tier * 1.1, 24 * tier, 0.34 + tier * 0.08,
                        255, 238, 165, DominionParticles.STYLE_SHARD);
                for (int s = 0; s < 8 * tier; s++) {
                    double ang = (Math.PI * 2.0 * s) / (8.0 * tier);
                    double r = 1.6 + tier * 1.3;
                    DominionParticles.spawnSmoke(
                            impactPos.add(Math.cos(ang) * r, 0.35, Math.sin(ang) * r),
                            new Vec3(Math.cos(ang) * (0.14 + tier * 0.06), 0.12 + tier * 0.06, Math.sin(ang) * (0.14 + tier * 0.06)),
                            0.75f + tier * 0.32f, 22 + tier * 5,
                            255, tier == 3 ? 76 : 118, 24, 145);
                }
                if (isLocal) {
                    DominionPostFx.triggerShockwave(1.0f, 16 + tier * 4, BlockDominionPalette.INFERNO_OBSIDIAN);
                    triggerImpactFrame(2 + tier, 1.0f, (int) (now & 63), BlockDominionPalette.INFERNO_OBSIDIAN);
                    BlockDominionSounds.playLocalFull(BlockDominionSounds.METEOR_CATACLYSM,
                            1.15f + tier * 0.25f, 1.04f - tier * 0.10f);
                    BlockDominionSounds.playLocalFull(BlockDominionSounds.IMPACT,
                            0.95f + tier * 0.20f, 0.72f - tier * 0.05f);
                } else {
                    BlockDominionSounds.play(impactPos, BlockDominionSounds.METEOR_CATACLYSM,
                            1.50f + tier * 0.45f, 1.04f - tier * 0.10f);
                    BlockDominionSounds.play(impactPos, BlockDominionSounds.IMPACT,
                            1.20f + tier * 0.25f, 0.72f - tier * 0.05f);
                }
            }
            case BlockDominionRules.EVENT_CAGE_START -> {
                Vec3 anchor = new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
                int targetId = payload.eventBlockId();
                List<BlockState> cageBlocks = new ArrayList<>(BlockDominionRules.CAGE_BLOCK_COUNT);
                List<Vec3> launchOrigins = new ArrayList<>(BlockDominionRules.CAGE_BLOCK_COUNT);
                for (int i = 0; i < BlockDominionRules.CAGE_BLOCK_COUNT; i++) {
                    if (i < removedBlocks.size()) {
                        VisualBlock src = removedBlocks.get(i);
                        cageBlocks.add(src.state);
                        launchOrigins.add(src.pos);
                    } else {
                        BlockState bs = f.blocks.isEmpty()
                                ? Blocks.DEEPSLATE_BRICKS.defaultBlockState()
                                : f.blocks.get(i % f.blocks.size()).state;
                        cageBlocks.add(bs);
                        double ang = (Math.PI * 2.0 * i) / BlockDominionRules.CAGE_BLOCK_COUNT;
                        launchOrigins.add(ownerPos.add(Math.cos(ang) * 1.8, 0.25, Math.sin(ang) * 1.8));
                    }
                }
                double yawOff = owner != null ? Math.toRadians(owner.getYRot()) : 0.35;
                ACTIVE_CAGES.put(cageKey(payload.playerId(), targetId),
                        new ClientCage(payload.playerId(), targetId, anchor, cageBlocks, launchOrigins, now, pal, yawOff));
                DominionParticles.spawnRingBurst(anchor, new Vec3(0, 1, 0), 1.65, 28, 0.26,
                        pal.primaryR, pal.primaryG, pal.primaryB, DominionParticles.STYLE_SHARD);
                if (isLocal) {
                    DominionPostFx.triggerShockwave(0.42f, 9, pal);
                }
                BlockDominionSounds.play(anchor, BlockDominionSounds.SHIELD_LOCK, 0.98f, 0.88f);
            }
            case BlockDominionRules.EVENT_CAGE_IMPALE -> {
                Vec3 hitPos = new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
                int packed = payload.eventBlockId();
                int targetId = packed >>> 1;
                boolean killed = (packed & 1) != 0;
                ClientCage cage = ACTIVE_CAGES.get(cageKey(payload.playerId(), targetId));
                Vec3[] dirs = BlockDominionBrain.cageSpearDirections(
                        BlockDominionRules.CAGE_SPEAR_COUNT, cage != null ? cage.yawOffset : 0.35);
                if (cage != null) {
                    cage.impaled = true;
                    cage.impaleTick = now;
                    cage.victimKilled = killed;
                }
                for (Vec3 d : dirs) {
                    DominionParticles.spawnRingBurst(hitPos.add(d.scale(1.10)), d, 0.92, 12, 0.36,
                            pal.hotR, pal.hotG, pal.hotB, DominionParticles.STYLE_SHARD);
                    DominionParticles.spawn(hitPos.add(d.scale(0.55)), d.scale(0.42), 0.24f, 14, 0.90, 0.0,
                            pal.primaryR, pal.primaryG, pal.primaryB, DominionParticles.STYLE_EMBER);
                }
                if (killed) {
                    // Plant a dramatic 6-Lance Crossed Monument pinned straight through the fallen corpse into the ground!
                    int gx = Mth.floor(hitPos.x), gz = Mth.floor(hitPos.z);
                    double pinY = hitPos.y - 0.85;
                    if (world != null && world.getChunkSource().hasChunk(gx >> 4, gz >> 4)) {
                        int groundY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
                        if (Math.abs(groundY - hitPos.y) <= 6.0) {
                            pinY = groundY + 0.44;
                        }
                    }
                    List<BlockState> mats = cage != null ? cage.blocks : List.of(Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                    if (IMPALED_MONUMENTS.size() >= 10) IMPALED_MONUMENTS.remove(0);
                    IMPALED_MONUMENTS.add(new ImpaledMonument(new Vec3(hitPos.x, pinY, hitPos.z), mats, dirs, now, pal));
                }
                if (isLocal) {
                    DominionPostFx.triggerShockwave(0.86f, 15, pal);
                    triggerImpactFrame(2, 0.95f, (int) (now & 63), pal);
                    BlockDominionSounds.playLocalFull(BlockDominionSounds.CAGE_IMPALE, 1.15f, 0.94f);
                } else {
                    BlockDominionSounds.play(hitPos, BlockDominionSounds.CAGE_IMPALE, 1.25f, 0.94f);
                }
            }
            case BlockDominionRules.EVENT_CAGE_END -> {
                int targetId = payload.eventBlockId();
                ClientCage old = ACTIVE_CAGES.remove(cageKey(payload.playerId(), targetId));
                if (old != null) {
                    DominionParticles.spawnRingBurst(old.anchor, new Vec3(0, 1, 0), 1.35, 18, 0.20,
                            old.palette.primaryR, old.palette.primaryG, old.palette.primaryB, DominionParticles.STYLE_SHARD);
                }
            }
            case BlockDominionRules.EVENT_FLIGHT_START -> {
                f.leftWingTrail.clear();
                f.rightWingTrail.clear();
                f.lastFlapSoundTick = now;
                addWave(payload.playerId(), new Vec3(0, 1, 0), 3.8, 12, now, pal);
                DominionParticles.spawnRingBurst(ownerPos, new Vec3(0, 1, 0), 1.6, 22, 0.24,
                        pal.primaryR, pal.primaryG, pal.primaryB, DominionParticles.STYLE_SHARD);
                if (isLocal) {
                    DominionPostFx.triggerShockwave(0.42f, 10, pal);
                }
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.WING_FLAP, 0.78f, 0.98f);
                BlockDominionSounds.play(ownerPos, SoundEvents.WIND_CHARGE_BURST.value(), 0.52f, 1.05f);
            }
            case BlockDominionRules.EVENT_FLIGHT_END -> {
                f.leftWingTrail.clear();
                f.rightWingTrail.clear();
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.UNFOLD, 0.45f, 0.82f);
            }
            case BlockDominionRules.EVENT_FLIGHT_BOOST -> {
                Vec3 aim = owner != null ? owner.getViewVector(1.0f) : new Vec3(0, 0, 1);
                addWave(payload.playerId(), aim, 4.2, 12, now, pal);
                DominionParticles.spawnRingBurst(ownerPos, aim, 1.5, 16, 0.30,
                        pal.hotR, pal.hotG, pal.hotB, DominionParticles.STYLE_EMBER);
                BlockDominionSounds.play(ownerPos, BlockDominionSounds.FLIGHT_BOOST, 0.88f, 1.04f);
                if (isLocal) {
                    DominionPostFx.triggerShockwave(0.54f, 10, pal);
                }
            }
            case BlockDominionRules.EVENT_AIR_STEP -> {
                Vec3 stepPos = new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
                if (STEP_FLASHES.size() >= 18) STEP_FLASHES.remove(0);
                STEP_FLASHES.add(new StepFlash(stepPos, now, pal));
                BlockDominionSounds.play(stepPos, SoundEvents.DEEPSLATE_STEP, 0.58f, 0.96f + (float) ((now % 4) * 0.04));
                BlockDominionSounds.play(stepPos, BlockDominionSounds.STEP, 0.34f, 0.98f + (float) ((now % 4) * 0.04));
            }
            default -> {
                if (wasStowed && !f.stowed) {
                    f.unfoldStartTick = now;
                    addWave(payload.playerId(), new Vec3(0, 1, 0), 3.3, 14, now, pal);
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

    private static void addWave(int playerId, Vec3 axis, double maxRadius, int duration, long now, BlockDominionPalette pal) {
        if (WAVES.size() >= 16) WAVES.remove(0);
        WAVES.add(new WaveBurst(playerId, axis, maxRadius, duration, now, pal));
    }

    private static void updateAmbientLoop(int pid, BlockDominionRules.Mode mode, ClientFormation f) {
        // Only play clean vanilla Elytra flight wind while airborne (ZERO constant humming drone while orbiting!)
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
                        return Mth.clamp(spd * 0.44 + (cf.boosting ? 0.20 : 0.0), 0.04, 0.90);
                    },
                    () -> {
                        ClientFormation cf = FORMATIONS.get(pid);
                        if (cf == null) return 1.0;
                        Entity e = world == null ? null : world.getEntity(pid);
                        double spd = e == null ? 0.0 : e.getDeltaMovement().length();
                        return Mth.clamp(0.84 + spd * 0.24 + (cf.boosting ? 0.14 : 0.0), 0.82, 1.38);
                    });
        } else {
            BlockDominionSounds.stopLoop(pid);
        }
    }

    /** Gimbal-lock-free horizontal facing vector from entity yaw (never flips even when looking straight up/down!). */
    private static Vec3 yawForward(Entity owner) {
        float yawRad = (float) Math.toRadians(owner.getYRot());
        return new Vec3(-Mth.sin(yawRad), 0.0, Mth.cos(yawRad));
    }

    private static void reassignNearestSlots(ClientFormation f, Entity owner, long now) {
        int n = f.blocks.size();
        Vec3 chest = owner.position().add(0, 1.25, 0);
        Vec3 look = owner.getViewVector(1.0f);
        Vec3 yawFwd = yawForward(owner);
        float unfold = Mth.clamp((now - f.unfoldStartTick) / (float) BlockDominionRules.UNFOLD_TICKS, 0f, 1f);
        float volley = Mth.clamp(f.volleyTicks / (float) BlockDominionRules.VOLLEY_CHARGE_TICKS, 0f, 1f);

        float wingPitchDeg = f.formation.isWings() ? BlockDominionPose.bodyPitchDegrees(owner.getId(), 1.0f) : 0f;
        float wingRollDeg = f.formation.isWings() ? BlockDominionPose.bodyRollDegrees(owner.getId(), 1.0f) : 0f;
        float wingAeroFrac = f.formation.isWings() ? BlockDominionPose.glideWeight(owner.getId(), 1.0f) : 0f;
        Vec3 wingAnchor = owner.position().add(0, Mth.lerp(wingAeroFrac, 1.25, 0.95), 0);

        List<Vec3> current = new ArrayList<>(n);
        List<Vec3> targets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            current.add(f.blocks.get(i).pos);
            if (f.formation == BlockDominionBrain.FormationKind.METEOR_DIVE) {
                targets.add(wingAnchor.add(BlockDominionBrain.meteorDivePose(
                        i, n, now, yawFwd, wingPitchDeg, wingRollDeg).offset()));
            } else if (f.formation.isWings() && f.formation != BlockDominionBrain.FormationKind.SOVEREIGN_PERCH) {
                targets.add(wingAnchor.add(BlockDominionBrain.wingPose(
                        i, n, now, yawFwd, f.formation == BlockDominionBrain.FormationKind.WINGS_BOOST,
                        wingPitchDeg, wingRollDeg, wingAeroFrac).offset()));
            } else {
                targets.add(chest.add(BlockDominionBrain.slotOffset(
                        f.formation, i, n, now, look, f.threatPrimary, f.threatSecondary, unfold, volley)));
            }
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

    /**
     * Triggers a tactile telekinetic push-out & interlocking wave whenever blocks re-assemble into
     * Wings, Shields, Railgun Ring, Volley Fan, or Stepping Stones.
     */
    private static void triggerFormationAssemblyWave(ClientFormation f, BlockDominionBrain.FormationKind prev, long now) {
        if (prev.isWings() && f.formation.isWings()) return; // Keep Hover <-> Boost <-> Meteor seamless!
        if (prev.isStepping() && f.formation.isStepping()) return;
        int n = f.blocks.size();
        for (int i = 0; i < n; i++) {
            VisualBlock vb = f.blocks.get(i);
            vb.morphOrigin = vb.pos;
            vb.morphStartTick = now;
            if (f.formation.isWings()) {
                vb.morphDelay = Math.min(4, (i * 5) / Math.max(1, n));
                vb.morphDuration = 8;
                vb.morphArcLift = 0.58;
            } else if (f.formation.isVolley()) {
                vb.morphDelay = Math.min(4, (i * 5) / Math.max(1, n));
                vb.morphDuration = 8;
                vb.morphArcLift = 0.72;
            } else if (f.formation == BlockDominionBrain.FormationKind.RAILGUN_RING) {
                vb.morphDelay = i < 5 ? (i / 2) : 2;
                vb.morphDuration = 6;
                vb.morphArcLift = 0.36;
            } else if (f.formation.isShield()) {
                vb.morphDelay = i % 2;
                vb.morphDuration = 5;
                vb.morphArcLift = 0.24;
            } else if (f.formation.isStepping()) {
                vb.morphDelay = i < 7 ? (i / 2) : 1;
                vb.morphDuration = 6;
                vb.morphArcLift = 0.30;
            } else {
                vb.morphDelay = Math.min(3, (i * 4) / Math.max(1, n));
                vb.morphDuration = 7;
                vb.morphArcLift = 0.34;
            }
        }
    }

    /** Samples the exact water/lava top surface beneath the entity so Liquid Stride slabs sit dead-flush under boots. */
    private static double sampleLiquidSurface(Entity owner) {
        if (world == null || owner == null) return Double.NaN;
        BlockPos feet = owner.blockPosition();
        for (BlockPos p : new BlockPos[]{feet, feet.below()}) {
            if (!world.getFluidState(p).isEmpty() && world.getFluidState(p.above()).isEmpty()) {
                double surf = p.getY() + 0.96;
                if (owner.getY() <= surf + 0.65 && owner.getY() >= p.getY() - 0.25) {
                    return surf;
                }
            }
        }
        return Double.NaN;
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
        DominionPostFx.tick();
        DominionParticles.tick();
        updateLocalCinematicCameraAndPostFx(client);

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
        MUZZLE_TRACERS.removeIf(m -> now - m.startTick > 7);
        METEOR_BLASTS.removeIf(b -> now - b.startTick > 38);
        ACTIVE_CAGES.entrySet().removeIf(e -> now - e.getValue().startTick > BlockDominionRules.CAGE_DURATION_TICKS + 6);
        IMPALED_MONUMENTS.removeIf(m -> now - m.startTick > BlockDominionRules.CAGE_MONUMENT_TICKS);
        if (now % 3 == 0 && !IMPALED_MONUMENTS.isEmpty()) {
            for (ImpaledMonument mon : IMPALED_MONUMENTS) {
                DominionParticles.spawnSmoke(
                        mon.pinPos.add((Math.random() - 0.5) * 0.85, 0.25 + Math.random() * 0.4, (Math.random() - 0.5) * 0.85),
                        new Vec3(0, 0.045, 0), 0.52f, 22,
                        mon.palette.darkR, mon.palette.darkG, mon.palette.darkB, 115);
                DominionParticles.spawn(
                        mon.pinPos.add((Math.random() - 0.5) * 0.9, 0.15, (Math.random() - 0.5) * 0.9),
                        new Vec3((Math.random() - 0.5) * 0.03, 0.065, (Math.random() - 0.5) * 0.03),
                        0.16f, 18, 0.92, 0.0,
                        mon.palette.primaryR, mon.palette.primaryG, mon.palette.primaryB, DominionParticles.STYLE_EMBER);
            }
        }

        for (var entry : FORMATIONS.entrySet()) {
            Entity owner = world.getEntity(entry.getKey());
            if (owner == null) continue;
            ClientFormation f = entry.getValue();
            f.stepPalette();
            if (f.volleyCharging) f.volleyTicks++;
            Vec3 chest = owner.position().add(0, 1.25, 0);
            Vec3 ownerDelta = (f.lastOwnerChest != null && f.lastOwnerChest.distanceToSqr(chest) < 25.0 * 25.0)
                    ? chest.subtract(f.lastOwnerChest) : Vec3.ZERO;
            f.lastOwnerChest = chest;
            Vec3 look = owner.getViewVector(1.0f);
            float unfoldProgress = Mth.clamp((now - f.unfoldStartTick) / (float) BlockDominionRules.UNFOLD_TICKS, 0f, 1f);
            float volleyProgress = Mth.clamp(f.volleyTicks / (float) BlockDominionRules.VOLLEY_CHARGE_TICKS, 0f, 1f);

            // Clean architectural stepping stones for Air-Step Stairs AND Liquid Bridge:
            // - Liquid Stride: snaps cleanly to the 1x1 Minecraft block grid (crisp floating walkway over water/lava!)
            // - Air-Stairs: evenly spaced half-slab treads (>= 0.94m apart) with zero awkward overlap or pancake distortion!
            if (f.formation.isStepping()) {
                Vec3 feet = owner.position();
                Vec3 yawFwdStep = yawForward(owner);
                Vec3 moveVec = new Vec3(ownerDelta.x, 0.0, ownerDelta.z);
                if (moveVec.lengthSqr() < 0.0004) {
                    Vec3 dm = owner.getDeltaMovement();
                    moveVec = new Vec3(dm.x, 0.0, dm.z);
                }
                Vec3 moveDir = moveVec.lengthSqr() > 0.0004 ? moveVec.normalize() : yawFwdStep;
                boolean liquid = f.formation == BlockDominionBrain.FormationKind.LIQUID_STRIDE;
                double liquidSurf = liquid ? sampleLiquidSurface(owner) : Double.NaN;
                double soleY = liquid
                        ? ((Double.isNaN(liquidSurf) ? feet.y : Math.min(feet.y, liquidSurf)) - 0.21)
                        : (feet.y - 0.18);
                Vec3 underFoot = liquid
                        ? new Vec3(Mth.floor(feet.x) + 0.5, soleY, Mth.floor(feet.z) + 0.5)
                        : new Vec3(feet.x, soleY, feet.z);
                double nextStepY = liquid ? soleY : (soleY + (f.stairAscending ? 0.34 : -0.28));
                Vec3 aheadPreview;
                if (liquid) {
                    int ax = Mth.floor(feet.x + moveDir.x * 1.0);
                    int az = Mth.floor(feet.z + moveDir.z * 1.0);
                    if (ax == Mth.floor(feet.x) && az == Mth.floor(feet.z)) {
                        ax += Math.abs(moveDir.x) >= Math.abs(moveDir.z) ? (moveDir.x >= 0 ? 1 : -1) : 0;
                        az += Math.abs(moveDir.z) > Math.abs(moveDir.x) ? (moveDir.z >= 0 ? 1 : -1) : 0;
                    }
                    aheadPreview = new Vec3(ax + 0.5, soleY, az + 0.5);
                } else {
                    aheadPreview = new Vec3(feet.x + moveDir.x * 0.96, nextStepY, feet.z + moveDir.z * 0.96);
                }

                f.stepSlots[0] = underFoot;
                f.stepSlots[1] = aheadPreview;
                if (f.stepCount < 2 || f.lastStepAnchor == null) {
                    double backY = liquid ? soleY : (soleY + (f.stairAscending ? -0.34 : 0.28));
                    f.stepSlots[2] = liquid
                            ? new Vec3(Mth.floor(feet.x - moveDir.x * 1.0) + 0.5, soleY, Mth.floor(feet.z - moveDir.z * 1.0) + 0.5)
                            : new Vec3(feet.x - moveDir.x * 0.94, backY, feet.z - moveDir.z * 0.94);
                    f.stepCount = 3;
                    f.stepCursor = 3;
                    f.lastStepAnchor = underFoot;
                } else {
                    double dx = underFoot.x - f.lastStepAnchor.x, dz = underFoot.z - f.lastStepAnchor.z;
                    double horizDist = Math.sqrt(dx * dx + dz * dz);
                    double dy = Math.abs(underFoot.y - f.lastStepAnchor.y);
                    boolean advance = liquid ? (horizDist >= 0.95) : (horizDist >= 0.92 || dy >= 0.34);
                    if (advance) {
                        int slotIdx = f.stepCursor;
                        f.stepSlots[slotIdx] = f.lastStepAnchor;
                        f.stepCursor = 2 + ((slotIdx - 2 + 1) % 5);
                        if (f.stepCount < 7) f.stepCount++;
                        f.lastStepAnchor = underFoot;
                        if (slotIdx < f.blocks.size()) {
                            VisualBlock rec = f.blocks.get(slotIdx);
                            rec.morphOrigin = rec.pos;
                            rec.morphStartTick = now;
                            rec.morphDelay = 0;
                            rec.morphDuration = 4;
                            rec.morphArcLift = 0.16;
                        }
                    }
                }
            } else {
                f.stepCount = 0;
                f.stepCursor = 2;
                f.lastStepAnchor = null;
            }

            // Tick character pose FIRST so wing pitch, bank roll & aerodynamic glide weight stay locked to the body!
            if (owner instanceof net.minecraft.world.entity.LivingEntity living) {
                BlockDominionPose.tickEntity(living, f.flying, f.glideMode, f.boosting, f.volleyCharging,
                        f.meteorDiving || f.formation.isMeteorDive(), now);
            }
            Vec3 yawFwd = yawForward(owner);
            boolean perch = f.formation == BlockDominionBrain.FormationKind.SOVEREIGN_PERCH;
            float wingPitchDeg = perch ? 0f : BlockDominionPose.bodyPitchDegrees(owner.getId(), 1.0f);
            float wingRollDeg = perch ? 0f : BlockDominionPose.bodyRollDegrees(owner.getId(), 1.0f);
            float wingAeroFrac = perch ? 0f : BlockDominionPose.glideWeight(owner.getId(), 1.0f);
            Vec3 wingAnchor = owner.position().add(0, Mth.lerp(wingAeroFrac, 1.25, 0.95), 0);

            int count = f.blocks.size();
            for (int i = 0; i < count; i++) {
                VisualBlock vb = f.blocks.get(i);
                vb.prevPos = vb.pos;
                vb.prevScale = vb.scale;
                vb.prevRot.set(vb.rot);

                Vec3 target;
                Vec3 targetScale;
                boolean isAnchoredStep = f.formation.isStepping() && i < f.stepCount && f.stepSlots[i] != null;
                boolean isLiveFootStep = f.formation.isStepping() && i == 0 && f.stepSlots[0] != null;
                boolean isAheadStep = f.formation.isStepping() && i == 1 && f.stepSlots[1] != null;
                if (isAnchoredStep) {
                    target = f.stepSlots[i];
                    targetScale = f.formation == BlockDominionBrain.FormationKind.LIQUID_STRIDE
                            ? new Vec3(0.96, 0.42, 0.96)
                            : new Vec3(0.90, 0.36, 0.90);
                } else if (perch) {
                    BlockDominionBrain.WingPose wp = BlockDominionBrain.sovereignPerchPose(i, count, now, yawFwd);
                    target = chest.add(wp.offset());
                    targetScale = wp.scale();
                } else if (f.formation.isMeteorDive()) {
                    BlockDominionBrain.WingPose wp = BlockDominionBrain.meteorDivePose(
                            i, count, now, yawFwd, wingPitchDeg, wingRollDeg);
                    target = wingAnchor.add(wp.offset());
                    targetScale = wp.scale();
                } else if (f.formation.isWings()) {
                    BlockDominionBrain.WingPose wp = BlockDominionBrain.wingPose(
                            i, count, now, yawFwd, f.formation == BlockDominionBrain.FormationKind.WINGS_BOOST,
                            wingPitchDeg, wingRollDeg, wingAeroFrac);
                    target = wingAnchor.add(wp.offset());
                    targetScale = wp.scale();
                } else {
                    Vec3 offset = BlockDominionBrain.slotOffset(
                            f.formation, i, count, now, look, f.threatPrimary, f.threatSecondary, unfoldProgress, volleyProgress);
                    target = chest.add(offset);
                    targetScale = targetBlockScaleVec(f, i, unfoldProgress, volleyProgress);
                }

                long pickupAge = now - vb.pickupStartTick;
                long morphAge = now - vb.morphStartTick;
                if (vb.pickupOrigin != null && pickupAge >= 0 && pickupAge < 11) {
                    if (pickupAge < 2) {
                        double j = (pickupAge == 0 ? 0.05 : -0.05);
                        vb.pos = vb.pickupOrigin.add(j, 0.06 * pickupAge, -j);
                        vb.vel = Vec3.ZERO;
                    } else {
                        double t = (pickupAge - 2) / 9.0;
                        double ease = 1.0 - (1.0 - t) * (1.0 - t);
                        Vec3 mid = vb.pickupOrigin.lerp(target, 0.5).add(0, 1.45, 0);
                        Vec3 a = vb.pickupOrigin.lerp(mid, ease);
                        Vec3 b = mid.lerp(target, ease);
                        Vec3 nextPos = a.lerp(b, ease);
                        vb.vel = nextPos.subtract(vb.pos);
                        vb.pos = nextPos;
                    }
                } else if (vb.morphOrigin != null && !isLiveFootStep
                        && morphAge >= 0 && morphAge < vb.morphDelay + vb.morphDuration) {
                    vb.pickupOrigin = null;
                    if (morphAge < vb.morphDelay) {
                        vb.morphOrigin = vb.morphOrigin.add(ownerDelta.scale(0.85));
                        vb.pos = vb.pos.add(ownerDelta.scale(0.85));
                    } else {
                        double u = Mth.clamp((morphAge - vb.morphDelay + 1.0) / Math.max(1.0, vb.morphDuration), 0.0, 1.0);
                        double ease = 1.0 - (1.0 - u) * (1.0 - u) * (1.0 - u);
                        Vec3 startPos = vb.morphOrigin;
                        Vec3 radial = startPos.subtract(chest);
                        Vec3 pushDir = radial.lengthSqr() > 0.04 ? radial.normalize() : new Vec3(0, 1, 0);
                        Vec3 mid = startPos.lerp(target, 0.48).add(pushDir.scale(vb.morphArcLift)).add(0, vb.morphArcLift * 0.28, 0);
                        Vec3 a = startPos.lerp(mid, ease);
                        Vec3 b = mid.lerp(target, ease);
                        Vec3 nextPos = a.lerp(b, ease);
                        vb.vel = nextPos.subtract(vb.pos);
                        vb.pos = nextPos;
                    }
                } else {
                    vb.pickupOrigin = null;
                    vb.morphOrigin = null;
                    if (isLiveFootStep) {
                        vb.vel = target.subtract(vb.pos);
                        vb.pos = target;
                    } else if (vb.pos.distanceToSqr(target) > 48.0 * 48.0) {
                        vb.pos = target;
                        vb.prevPos = target;
                        vb.vel = Vec3.ZERO;
                    } else {
                        // Critically-damped smooth exponential follow (silky glide, ZERO bouncy spring overshoot!)
                        double carry = isAheadStep ? 0.90
                                : isAnchoredStep ? 0.0
                                : f.formation.isWings() ? 0.96
                                : (f.formation.isShield() || f.formation == BlockDominionBrain.FormationKind.RAILGUN_RING ? 0.90 : 0.76);
                        vb.pos = vb.pos.add(ownerDelta.scale(carry));

                        double follow = isAheadStep ? 0.68
                                : isAnchoredStep ? 0.48
                                : (f.formation == BlockDominionBrain.FormationKind.WINGS_BOOST || f.formation.isMeteorDive()) ? 0.50
                                : f.formation.isWings() ? 0.36
                                : f.formation.isShield() ? 0.44
                                : f.formation == BlockDominionBrain.FormationKind.RAILGUN_RING ? 0.46
                                : f.volleyCharging ? 0.30
                                : f.stowed ? 0.34 : 0.24;
                        Vec3 desiredVel = target.subtract(vb.pos).scale(follow);
                        vb.vel = vb.vel.lerp(desiredVel, 0.62);
                        vb.pos = vb.pos.add(vb.vel);
                    }
                }
                vb.scale = vb.scale.lerp(targetScale, 0.26);
                vb.spinPhase += f.stowed ? 0.06f : (f.volleyCharging ? 0.11f : 0.036f);

                // Smooth quaternion slerp for every block so formation changes never snap rotation in 1 frame!
                Quaternionf targetRot = computeTargetOrientation(f, i, count, vb.pos, chest, look, yawFwd, now,
                        vb.spinPhase, wingPitchDeg, wingRollDeg, wingAeroFrac);
                if (!vb.rotInit) {
                    vb.rot.set(targetRot);
                    vb.prevRot.set(targetRot);
                    vb.rotInit = true;
                } else {
                    float rotRate = f.formation.isWings() || f.formation.isShield() ? 0.36f : 0.24f;
                    vb.rot.slerp(targetRot, rotRate);
                }
            }

            emitTickParticles(f, owner, wingAnchor, yawFwd, now, wingPitchDeg, wingRollDeg, wingAeroFrac);
        }
        BlockDominionPose.prune(now);
    }

    /** Drives smooth aerodynamic camera roll (Glide/Hover banking), dynamic speed/charge FOV, and DominionPostFx uniforms. */
    private static void updateLocalCinematicCameraAndPostFx(Minecraft client) {
        prevCamRollDeg = camRollDeg;
        prevCamFovMul = camFovMul;

        ClientFormation local = localFormation();
        var player = client.player;
        if (local == null || player == null || local.mode == BlockDominionRules.Mode.OFF || local.hiddenInCutscene) {
            camRollDeg = Mth.lerp(0.24f, camRollDeg, 0f);
            camFovMul = Mth.lerp(0.22f, camFovMul, 1.0f);
            lastPlayerYaw = player != null ? player.getYRot() : Float.NaN;
            DominionPostFx.setAmbientState(0f, 0f, null);
            return;
        }

        double speed = player.getDeltaMovement().length();
        float targetRoll = 0f;
        float targetFov = 1.0f;
        float targetBlur = 0f;
        // Performance: 0% PostChain overhead when standing/walking normally; only activate for cinematic bursts/flight!
        float targetBloom = 0.0f;

        if (local.flying) {
            float yaw = player.getYRot();
            float yawDelta = Float.isNaN(lastPlayerYaw) ? 0f : Mth.wrapDegrees(yaw - lastPlayerYaw);
            lastPlayerYaw = yaw;
            int strafe = (client.options.keyRight.isDown() ? 1 : 0) - (client.options.keyLeft.isDown() ? 1 : 0);
            float bankGain = local.glideMode ? 1.0f : 0.65f;
            float speedScale = Mth.clamp((float) (speed / 0.85), 0.35f, 1.25f);
            targetRoll = Mth.clamp((strafe * 4.2f + yawDelta * 0.42f) * bankGain * speedScale, -6.5f, 6.5f);

            float speedFrac = Mth.clamp((float) ((speed - 0.65) / 1.55), 0f, 1f);
            boolean meteor = local.meteorDiving || local.formation.isMeteorDive();
            int mTier = meteor ? Math.max(1, local.meteorTier) : 0;
            // Comfortable cinema FOV (never fisheye-stretched!) and refined radial blur that keeps terrain crisp
            targetBlur = meteor ? (0.20f + mTier * 0.10f)
                    : (local.boosting ? Math.max(0.32f, speedFrac * 0.56f) : speedFrac * 0.28f);
            targetFov = 1.0f + speedFrac * (meteor ? (0.055f + mTier * 0.015f) : (local.boosting ? 0.085f : 0.045f));
            targetBloom = meteor ? (0.28f + mTier * 0.10f) : (local.boosting ? 0.30f : (speedFrac > 0.25f ? 0.12f : 0.0f));
        } else {
            lastPlayerYaw = player.getYRot();
        }

        if (local.volleyCharging) {
            float vp = Mth.clamp(local.volleyTicks / (float) BlockDominionRules.VOLLEY_CHARGE_TICKS, 0f, 1f);
            targetFov = 1.0f - vp * 0.045f;
            targetBloom = Math.max(targetBloom, 0.16f + vp * 0.28f);
        } else if (local.formation == BlockDominionBrain.FormationKind.RAILGUN_RING) {
            targetBloom = Math.max(targetBloom, 0.20f);
        }

        camRollDeg = Mth.lerp(0.22f, camRollDeg, targetRoll);
        camFovMul = Mth.lerp(0.18f, camFovMul, targetFov);
        DominionPostFx.setAmbientState(targetBlur, targetBloom, local.palette);
    }

    private static void emitTickParticles(ClientFormation f, Entity owner, Vec3 wingAnchor, Vec3 yawFwd, long now,
                                          float wingPitchDeg, float wingRollDeg, float wingAeroFrac) {
        if (f.hiddenInCutscene) return;
        int count = f.blocks.size();
        int pr = f.pr(), pg = f.pg(), pb = f.pb();
        int hr = f.hr(), hg = f.hg(), hb = f.hb();
        int dr = f.dr(), dg = f.dg(), db = f.db();
        int mr = f.mr(), mg = f.mg(), mb = f.mb();

        if (f.mode == BlockDominionRules.Mode.MANUAL && count > 0) {
            if (f.formation.isWings() && f.formation != BlockDominionBrain.FormationKind.SOVEREIGN_PERCH) {
                boolean meteor = f.formation.isMeteorDive();
                boolean boosted = f.formation == BlockDominionBrain.FormationKind.WINGS_BOOST || meteor;
                if (meteor) {
                    f.leftWingTrail.clear();
                    f.rightWingTrail.clear();
                } else {
                    Vec3 lTip = wingAnchor.add(BlockDominionBrain.wingTipOffset(true, now, yawFwd, boosted, wingPitchDeg, wingRollDeg, wingAeroFrac));
                    Vec3 rTip = wingAnchor.add(BlockDominionBrain.wingTipOffset(false, now, yawFwd, boosted, wingPitchDeg, wingRollDeg, wingAeroFrac));
                    pushTrail(f.leftWingTrail, lTip);
                    pushTrail(f.rightWingTrail, rTip);
                }

                Vec3 wake = owner.getDeltaMovement().scale(-0.32);
                if (!meteor) {
                    if (boosted || now % 2 == 0) {
                        Vec3 lTip = wingAnchor.add(BlockDominionBrain.wingTipOffset(true, now, yawFwd, boosted, wingPitchDeg, wingRollDeg, wingAeroFrac));
                        Vec3 rTip = wingAnchor.add(BlockDominionBrain.wingTipOffset(false, now, yawFwd, boosted, wingPitchDeg, wingRollDeg, wingAeroFrac));
                        DominionParticles.spawn(lTip, wake.add((Math.random() - 0.5) * 0.04, (Math.random() - 0.5) * 0.04, (Math.random() - 0.5) * 0.04),
                                boosted ? 0.16f : 0.11f, boosted ? 10 : 12, 0.88, 0.0,
                                boosted ? hr : pr, boosted ? hg : pg, boosted ? hb : pb, DominionParticles.STYLE_STAR);
                        DominionParticles.spawn(rTip, wake.add((Math.random() - 0.5) * 0.04, (Math.random() - 0.5) * 0.04, (Math.random() - 0.5) * 0.04),
                                boosted ? 0.16f : 0.11f, boosted ? 10 : 12, 0.88, 0.0,
                                boosted ? hr : pr, boosted ? hg : pg, boosted ? hb : pb, DominionParticles.STYLE_STAR);
                    }
                } else {
                    int mTier = Math.max(1, f.meteorTier);
                    int sparkCount = mTier == 3 ? 8 : (mTier == 2 ? 5 : 3);
                    double spread = 0.65 + mTier * 0.22;
                    // Sleek tight fiery ember sparks hugging the diving comet (never flying through the 3rd-person camera!)
                    for (int k = 0; k < sparkCount; k++) {
                        Vec3 sparkPos = wingAnchor.add((Math.random() - 0.5) * spread, (Math.random() - 0.5) * 0.50, (Math.random() - 0.5) * spread);
                        int eg = k < (mTier + 1) ? 236 : (mTier == 3 && k % 2 == 0 ? 78 : 128);
                        int eb = k < (mTier + 1) ? 158 : 22;
                        DominionParticles.spawn(sparkPos, wake.scale(0.38).add((Math.random() - 0.5) * 0.08, 0.04, (Math.random() - 0.5) * 0.08),
                                (0.11f + mTier * 0.025f) + (float) Math.random() * 0.04f, 6 + mTier, 0.84, 0.0,
                                255, eg, eb, DominionParticles.STYLE_EMBER);
                    }
                }

                // Low-Altitude Ground Wash: blowing radial smoke clouds & stone dust off the earth when skimming low!
                double alt = altitudeAboveTerrain(owner);
                if (alt >= 0.2 && alt <= 6.5) {
                    double kWash = (1.0 - alt / 6.5) * (boosted ? 1.4 : 1.0);
                    Vec3 groundPt = new Vec3(owner.getX(), owner.getY() - alt + 0.15, owner.getZ());
                    int puffs = boosted ? 4 : 2;
                    for (int p = 0; p < puffs; p++) {
                        double ang = Math.random() * Math.PI * 2.0;
                        double rOut = 0.8 + Math.random() * 1.6;
                        double spd = (0.14 + Math.random() * 0.18) * kWash;
                        Vec3 dir = new Vec3(Math.cos(ang), 0.0, Math.sin(ang));
                        DominionParticles.spawnSmoke(groundPt.add(dir.scale(rOut)), dir.scale(spd).add(0, 0.04, 0),
                                (float) (0.52 + 0.35 * kWash), 16, (p & 1) == 0 ? mr : dr, (p & 1) == 0 ? mg : dg, (p & 1) == 0 ? mb : db,
                                Mth.clamp((int) (135 * kWash), 40, 170));
                        if (p == 0) {
                            DominionParticles.spawn(groundPt.add(dir.scale(rOut)), dir.scale(spd * 1.4).add(0, 0.08, 0),
                                    0.15f, 12, 0.88, 0.004, pr, pg, pb, DominionParticles.STYLE_SHARD);
                        }
                    }
                }

                if (!boosted && now - f.lastFlapSoundTick >= 29) {
                    f.lastFlapSoundTick = now;
                    BlockDominionSounds.play(wingAnchor, BlockDominionSounds.WING_FLAP, 0.32f, 0.88f + (float) (Math.random() * 0.12));
                }
            } else {
                f.leftWingTrail.clear();
                f.rightWingTrail.clear();
                if (!f.stowed && now % 3 == 0) {
                    VisualBlock sample = f.blocks.get((int) (now % count));
                    DominionParticles.spawnAmbient(sample.pos, 0.24, 0.024, pr, pg, pb, DominionParticles.STYLE_STAR);
                }
                if (f.formation == BlockDominionBrain.FormationKind.RAIN_UMBRELLA) {
                    // Subtle raindrop splash mist rebounding off the overhead stone umbrella canopy!
                    Vec3 roof = owner.position().add((Math.random() - 0.5) * 1.9, 2.95, (Math.random() - 0.5) * 1.9);
                    DominionParticles.spawn(roof, new Vec3((Math.random() - 0.5) * 0.06, 0.05, (Math.random() - 0.5) * 0.06),
                            0.11f, 10, 0.86, 0.008, 185, 224, 255, DominionParticles.STYLE_STAR);
                }
            }
        } else if (f.mode == BlockDominionRules.Mode.AUTO && now % 2 == 0) {
            double ang = (now * 0.52) % (Math.PI * 2.0);
            double rad = 1.6 + (now % 8) * 1.05;
            Vec3 p = owner.position().add(Math.cos(ang) * rad, 0.25, Math.sin(ang) * rad);
            DominionParticles.spawnAmbient(p, 0.35, 0.045, pr, pg, pb, DominionParticles.STYLE_EMBER);
        }
    }

    private static double altitudeAboveTerrain(Entity owner) {
        if (world == null || owner == null) return 99.0;
        int bx = Mth.floor(owner.getX()), bz = Mth.floor(owner.getZ());
        if (!world.getChunkSource().hasChunk(bx >> 4, bz >> 4)) return 99.0;
        int groundY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        return owner.getY() - groundY;
    }

    private static void pushTrail(ArrayDeque<Vec3> trail, Vec3 pt) {
        if (trail.size() >= WING_TRAIL_LEN) trail.removeFirst();
        trail.addLast(pt);
    }

    private static Vec3 targetBlockScaleVec(ClientFormation f, int index, float unfoldProgress, float volleyProgress) {
        if (f.formation.isShield()) return new Vec3(0.74, 0.74, 0.74);
        if (f.formation == BlockDominionBrain.FormationKind.AIR_STAIRS) {
            return index < 7 ? new Vec3(0.86, 0.28, 0.86) : new Vec3(0.46, 0.46, 0.46);
        }
        if (f.formation == BlockDominionBrain.FormationKind.RAIN_UMBRELLA && index < 7) {
            return new Vec3(0.78, 0.22, 0.78);
        }
        if (f.formation == BlockDominionBrain.FormationKind.RAILGUN_RING && index < 5) {
            return new Vec3(0.32, 0.32, 0.78);
        }
        float s = BlockDominionRules.blockScale(f.stowed, unfoldProgress, f.volleyCharging, volleyProgress);
        return new Vec3(s, s, s);
    }

    /** Renders both the resonant BlockDominionHud badge/arc and anime ink-and-resonance Impact Frame overlay. */
    public static void renderHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        ClientFormation local = localFormation();
        if (local != null && mc != null && mc.player != null) {
            int cap = BlockDominionRules.clampCapacity(GodWandConfig.SERVER.blockDominionCapacity);
            float vp = Mth.clamp(local.volleyTicks / (float) BlockDominionRules.VOLLEY_CHARGE_TICKS, 0f, 1f);
            BlockDominionHud.render(graphics, local.mode, local.formation, local.blocks.size(), cap,
                    local.flying, local.glideMode, local.boosting, local.stairAscending,
                    local.volleyCharging, vp, local.palette,
                    mc.player.getDeltaMovement().length(), local.meteorTier, localActiveCageCount());
        }

        if (impactFrameTicks <= 0 || !GodWandConfig.CLIENT.impactFrames || DominionPostFx.ready()) return;
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
            buf.addVertex(mat, 0, h, 0).setUv(u0, v1).setColor(impactR, impactG, impactB, alphaByte);
            buf.addVertex(mat, w, h, 0).setUv(u1, v1).setColor(impactR, impactG, impactB, alphaByte);
            buf.addVertex(mat, w, 0, 0).setUv(u1, v0).setColor(impactR, impactG, impactB, alphaByte);
            buf.addVertex(mat, 0, 0, 0).setUv(u0, v0).setColor(impactR, impactG, impactB, alphaByte);
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
        if (FORMATIONS.isEmpty() && ACTIVE_CAGES.isEmpty() && IMPALED_MONUMENTS.isEmpty()
                && MUZZLE_TRACERS.isEmpty() && METEOR_BLASTS.isEmpty() && PARRIES.isEmpty()
                && WAVES.isEmpty() && STEP_FLASHES.isEmpty()) return;

        Vec3 camPos = camera.getPosition();
        double time = world.getGameTime() + tickDelta;
        boolean localCutscene = AtomicCinematic.active() || FugaCinematic.active()
                || SilentGateCinematic.active() || PurpleCinematic.active();
        BlockRenderDispatcher blockRenderer = client.getBlockRenderer();
        MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        // 1. Render solid reserve blocks + terrifying satellite shattered stone swarm!
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
                Vec3 camLook = new Vec3(camera.getLookVector().x(), camera.getLookVector().y(), camera.getLookVector().z());
                for (int i = 0; i < count; i++) {
                    VisualBlock vb = f.blocks.get(i);
                    Vec3 renderWorld;
                    if (i == 0 && f.formation == BlockDominionBrain.FormationKind.AIR_STAIRS && f.stepSlots[0] != null) {
                        // Frame-exact under-boot lock for Air-Stairs (Liquid Stride stays cleanly grid-snapped!)
                        Vec3 interpFeet = owner.getPosition(tickDelta);
                        renderWorld = new Vec3(interpFeet.x, interpFeet.y - 0.18, interpFeet.z);
                    } else {
                        renderWorld = vb.prevPos.lerp(vb.pos, tickDelta);
                    }
                    Vec3 sc = vb.prevScale.lerp(vb.scale, tickDelta);
                    if (firstPerson && isObstructingFirstPerson(renderWorld, camPos, camLook, f.formation, i)) continue;

                    int light = sampleLight(renderWorld, owner);
                    Quaternionf renderRot = new Quaternionf(vb.prevRot).slerp(vb.rot, tickDelta);
                    matrices.pushPose();
                    matrices.translate(renderWorld.x - camPos.x, renderWorld.y - camPos.y, renderWorld.z - camPos.z);
                    matrices.mulPose(renderRot);
                    matrices.scale((float) sc.x, (float) sc.y, (float) sc.z);
                    matrices.translate(-0.5, -0.5, -0.5);
                    blockRenderer.renderSingleBlock(vb.state, matrices, bufferSource, light, OverlayTexture.NO_OVERLAY);
                    matrices.popPose();
                }

                if (!f.stowed && count > 0) {
                    renderSatelliteStoneSwarm(matrices, bufferSource, blockRenderer, f, owner, camPos, camLook, time, tickDelta, firstPerson);
                }
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

        // 2b. Render active Monolith Cages: Architectural Hexagonal Iron-Maiden Jail + 6 Converging 3-Piece Execution Lances!
        for (ClientCage cage : ACTIVE_CAGES.values()) {
            Entity target = world.getEntity(cage.targetId);
            Vec3 center = target != null && target.isAlive()
                    ? target.getPosition(tickDelta).add(0, target.getBbHeight() * 0.52, 0)
                    : cage.anchor;
            if (center.distanceToSqr(camPos) > 96.0 * 96.0) continue;
            double age = Math.max(0.0, time - cage.startTick);
            int n = Math.max(1, cage.blocks.size());
            int originsN = Math.max(1, cage.launchOrigins.size());
            int light = sampleLight(center, target != null ? target : client.player);

            // Prison shatter burst outward the instant the 6 lances impale through!
            double shatterT = (cage.impaled && cage.impaleTick >= 0)
                    ? Mth.clamp((time - cage.impaleTick) / 10.0, 0.0, 1.0) : 0.0;
            double shatterEase = 1.0 - (1.0 - shatterT) * (1.0 - shatterT);
            float shatterScale = (float) Math.max(0.0, 1.0 - shatterT * 0.85);

            // Slow tightening compression during Phase 1 -> 2 for palpable dread tension!
            double compressFrac = Mth.clamp((age - 8.0) / 28.0, 0.0, 1.0);
            double lockRadius = Mth.lerp(compressFrac, 1.28, 1.12);
            double barSpin = time * (0.024 + compressFrac * 0.018) + cage.yawOffset;

            // 6 carved vertical monolith pillars + interlocking top & bottom horizontal shackle beams!
            if (shatterScale > 0.06f) {
                for (int i = 0; i < 6; i++) {
                    double ang = barSpin + (Math.PI / 3.0) * i;
                    double barAge = Math.max(0.0, age - i * 0.42);
                    double flyT = Mth.clamp(barAge / 6.2, 0.0, 1.0);
                    double flyEase = 1.0 - (1.0 - flyT) * (1.0 - flyT) * (1.0 - flyT);
                    double lockT = Mth.clamp((barAge - 4.8) / 2.6, 0.0, 1.0);
                    double lockEase = 1.0 - (1.0 - lockT) * (1.0 - lockT);
                    // Crisp elastic overshoot bounce right as each monolith slams into lock!
                    double snapBounce = (lockT > 0.0 && lockT < 1.0) ? -Math.sin(lockT * Math.PI) * 0.14 : 0.0;

                    Vec3 radialOut = new Vec3(Math.cos(ang), 0.0, Math.sin(ang));
                    Vec3 finalPos = center.add(radialOut.scale(lockRadius + snapBounce + shatterEase * 2.85))
                            .add(0, shatterEase * (0.65 - (i % 2) * 0.45), 0);
                    Vec3 origin = cage.launchOrigins.get(i % originsN);
                    Vec3 preLockPos = center.add(radialOut.scale(2.55));
                    Vec3 arcMid = origin.lerp(preLockPos, 0.48).add(radialOut.scale(1.35)).add(0, 0.85, 0);
                    Vec3 flightPos = origin.lerp(arcMid, flyEase).lerp(arcMid.lerp(preLockPos, flyEase), flyEase);
                    Vec3 p = flyT < 1.0 ? flightPos : preLockPos.lerp(finalPos, lockEase);

                    float sx = (float) Mth.lerp(flyEase, 0.50, 0.25) * shatterScale;
                    float sy = (float) Mth.lerp(flyEase, 0.50, 2.32) * shatterScale;
                    float sz = (float) Mth.lerp(flyEase, 0.50, 0.23) * shatterScale;
                    float tumble = (float) ((1.0 - flyEase) * 240.0 * ((i & 1) == 0 ? 1.0 : -1.0));
                    float shatterSpin = (float) (shatterEase * 195.0 * ((i & 1) == 0 ? 1.0 : -1.0));

                    matrices.pushPose();
                    matrices.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
                    matrices.mulPose(Axis.YP.rotationDegrees((float) (-Math.toDegrees(ang) + 90.0) + tumble + shatterSpin));
                    matrices.mulPose(Axis.XP.rotationDegrees(tumble * 0.55f + shatterSpin * 0.7f));
                    matrices.scale(sx, sy, sz);
                    matrices.translate(-0.5, -0.5, -0.5);
                    blockRenderer.renderSingleBlock(cage.blocks.get(i % n), matrices, bufferSource, light, OverlayTexture.NO_OVERLAY);
                    matrices.popPose();

                    // Interlocking horizontal shackle collar beams locking top & bottom rims between adjacent pillars!
                    if (lockT > 0.25) {
                        double nextAng = barSpin + (Math.PI / 3.0) * ((i + 1) % 6);
                        double midAng = (ang + nextAng) * 0.5;
                        Vec3 midRad = new Vec3(Math.cos(midAng), 0.0, Math.sin(midAng));
                        double beamR = (lockRadius * 0.88) + shatterEase * 2.4;
                        float beamScale = (float) Mth.clamp((lockT - 0.25) / 0.75, 0.0, 1.0) * shatterScale;
                        for (int rim = 0; rim < 2; rim++) {
                            double rimY = (rim == 0 ? 0.96 : -0.92) + (rim == 0 ? shatterEase * 0.8 : -shatterEase * 0.4);
                            Vec3 bp = center.add(midRad.scale(beamR)).add(0, rimY, 0);
                            matrices.pushPose();
                            matrices.translate(bp.x - camPos.x, bp.y - camPos.y, bp.z - camPos.z);
                            matrices.mulPose(Axis.YP.rotationDegrees((float) (-Math.toDegrees(midAng)) + shatterSpin * 0.6f));
                            matrices.scale(0.18f * beamScale, 0.18f * beamScale, (float) (lockRadius * 1.02) * beamScale);
                            matrices.translate(-0.5, -0.5, -0.5);
                            blockRenderer.renderSingleBlock(cage.blocks.get((i + rim + 1) % n), matrices, bufferSource, light, OverlayTexture.NO_OVERLAY);
                            matrices.popPose();
                        }
                    }
                }

                // Top & bottom hexagonal prison seal plates slamming shut vertically!
                for (int cap = 0; cap < 2; cap++) {
                    double capY = cap == 0 ? 1.18 : -1.08;
                    double capAge = Math.max(0.0, age - (2.4 + cap * 0.8));
                    double flyT = Mth.clamp(capAge / 5.6, 0.0, 1.0);
                    double flyEase = 1.0 - (1.0 - flyT) * (1.0 - flyT) * (1.0 - flyT);
                    double slamT = Mth.clamp((capAge - 4.4) / 2.4, 0.0, 1.0);
                    double slamEase = 1.0 - (1.0 - slamT) * (1.0 - slamT);
                    Vec3 origin = cage.launchOrigins.get((6 + cap) % originsN);
                    Vec3 preSlam = center.add(0, cap == 0 ? 2.75 : -2.45, 0);
                    Vec3 finalCap = center.add(0, capY + (cap == 0 ? shatterEase * 1.8 : -shatterEase * 0.7), 0);
                    Vec3 arcMid = origin.lerp(preSlam, 0.5).add(0, cap == 0 ? 1.35 : -0.65, 0);
                    Vec3 pos = flyT < 1.0
                            ? origin.lerp(arcMid, flyEase).lerp(arcMid.lerp(preSlam, flyEase), flyEase)
                            : preSlam.lerp(finalCap, slamEase);

                    float platW = (float) Mth.lerp(flyEase, 0.50, 1.56) * shatterScale;
                    float platH = (float) Mth.lerp(flyEase, 0.50, 0.17) * shatterScale;
                    for (int k = 0; k < 2; k++) {
                        matrices.pushPose();
                        matrices.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
                        matrices.mulPose(Axis.YP.rotationDegrees((float) (Math.toDegrees(barSpin) * (cap == 0 ? 1 : -1) + k * 45.0 + (1.0 - flyEase) * 180.0)));
                        matrices.scale(platW, platH, platW);
                        matrices.translate(-0.5, -0.5, -0.5);
                        blockRenderer.renderSingleBlock(cage.blocks.get((6 + cap) % n), matrices, bufferSource, light, OverlayTexture.NO_OVERLAY);
                        matrices.popPose();
                    }
                }
            }

            // Phase 2 & 3: 6 Sculpted 3-Piece Stone Lances assembling piece-by-piece -> cock-back tension -> rapid-fire supersonic IMPALE!
            if (age >= BlockDominionRules.CAGE_SPEAR_FORM_TICK || cage.impaled) {
                Vec3[] dirs = BlockDominionBrain.cageSpearDirections(BlockDominionRules.CAGE_SPEAR_COUNT, cage.yawOffset);
                for (int s = 0; s < dirs.length; s++) {
                    double spearAge = Math.max(0.0, age - BlockDominionRules.CAGE_SPEAR_FORM_TICK - s * 0.85);
                    double formFrac = cage.impaled ? 1.0 : Mth.clamp(spearAge / 8.5, 0.0, 1.0);
                    double formEase = 1.0 - (1.0 - formFrac) * (1.0 - formFrac);
                    double thrustDist;
                    if (cage.impaled && cage.impaleTick >= 0) {
                        // Staggered supersonic rapid-fire piercing volley (SHING-SHING-SHING!) driving straight through the core!
                        double impAge = Math.max(0.0, (time - cage.impaleTick) - s * 0.45);
                        double slam = Mth.clamp(impAge / 2.6, 0.0, 1.0);
                        double slamEase = 1.0 - (1.0 - slam) * (1.0 - slam) * (1.0 - slam);
                        thrustDist = Mth.lerp(slamEase, 4.35, -0.42);
                    } else {
                        double chargeFrac = Mth.clamp((age - BlockDominionRules.CAGE_SPEAR_FORM_TICK)
                                / (double) Math.max(1, BlockDominionRules.CAGE_IMPALE_TICK - BlockDominionRules.CAGE_SPEAR_FORM_TICK), 0.0, 1.0);
                        // Unmistakable anime cock-back anticipation pull right before release!
                        double cockBack = chargeFrac > 0.66
                                ? Math.pow((chargeFrac - 0.66) / 0.34, 1.5) * 1.15
                                : 0.0;
                        thrustDist = Mth.lerp(formEase, 4.95, 3.35) + cockBack;
                    }
                    Vec3 dir = dirs[s];
                    double jitter = (!cage.impaled && age > BlockDominionRules.CAGE_SPEAR_FORM_TICK + 9)
                            ? Math.sin(time * 2.8 + s * 1.9) * (age > BlockDominionRules.CAGE_IMPALE_TICK - 7 ? 0.075 : 0.035) : 0.0;
                    Vec3 lanceAnchor = center.subtract(dir.scale(thrustDist + jitter));
                    BlockState tipMat = cage.blocks.get((s * 2) % n);
                    BlockState shaftMat = cage.blocks.get((s * 2 + 1) % n);
                    renderStoneLance(matrices, bufferSource, blockRenderer, lanceAnchor, dir, camPos,
                            (float) Mth.clamp(formEase * 1.12, 0.0, 1.0), (float) formFrac, tipMat, shaftMat, light);
                }
            }
        }

        // 2c. Render Impaled Corpse Monuments: 4 crossed sculpted stone lances skewered into the earth at the slain spot!
        for (ImpaledMonument mon : IMPALED_MONUMENTS) {
            if (mon.pinPos.distanceToSqr(camPos) > 96.0 * 96.0) continue;
            double age = Math.max(0.0, time - mon.startTick);
            if (age < 0 || age > BlockDominionRules.CAGE_MONUMENT_TICKS) continue;
            double fade = age > BlockDominionRules.CAGE_MONUMENT_TICKS - 14
                    ? Mth.clamp((BlockDominionRules.CAGE_MONUMENT_TICKS - age) / 14.0, 0.0, 1.0)
                    : 1.0;
            double settle = Math.min(1.0, age / 4.0);
            double sink = (1.0 - fade) * 0.65;
            int n = Math.max(1, mon.blocks.size());
            int light = sampleLight(mon.pinPos, client.player);
            for (int s = 0; s < mon.spearDirs.length; s++) {
                Vec3 dir = mon.spearDirs[s];
                double shudder = age < 6.0 ? Math.sin(age * 2.8 + s) * (1.0 - age / 6.0) * 0.04 : 0.0;
                Vec3 anchor = mon.pinPos.add(0, -sink, 0).subtract(dir.scale(Mth.lerp(settle, 1.35, -0.32) + shudder));
                BlockState tipMat = mon.blocks.get((s * 2) % n);
                BlockState shaftMat = mon.blocks.get((s * 2 + 1) % n);
                renderStoneLance(matrices, bufferSource, blockRenderer, anchor, dir, camPos,
                        (float) (0.96 * fade), tipMat, shaftMat, light);
            }
        }

        bufferSource.endBatch();

        // 3. Purple-grade TWO-PASS Volumetric GLSL VFX tinted by live Material Resonance!
        renderVolumetricPass(matrices, camera, time, tickDelta, client, localCutscene, true);
        renderVolumetricPass(matrices, camera, time, tickDelta, client, localCutscene, false);
    }

    private static int sampleLight(Vec3 worldPos, Entity owner) {
        BlockPos lightPos = BlockPos.containing(worldPos);
        if (world.getBlockState(lightPos).isSolidRender(world, lightPos)) lightPos = owner.blockPosition().above();
        return LevelRenderer.getLightColor(world, lightPos);
    }

    /** Keeps first-person crosshair & forward sightline completely clear while keeping peripheral blocks & underfoot stairs visible. */
    private static boolean isObstructingFirstPerson(Vec3 pos, Vec3 camPos, Vec3 camLook,
                                                    BlockDominionBrain.FormationKind form, int index) {
        Vec3 delta = pos.subtract(camPos);
        double distSq = delta.lengthSqr();
        if (form.isStepping() && index < 7) {
            return distSq < 1.05 * 1.05;
        }
        if (form == BlockDominionBrain.FormationKind.RAIN_UMBRELLA && index < 7) {
            return distSq < 1.15 * 1.15;
        }
        if (form.isWings()) {
            // Keep first-person wing flight completely clear of wings/feathers intruding into the viewport!
            if (distSq < 3.2 * 3.2) return true;
            if (distSq < 5.8 * 5.8 && delta.normalize().dot(camLook) > 0.22) return true;
            return false;
        }
        if (distSq < 1.75 * 1.75) return true;
        if (distSq < 4.4 * 4.4) {
            double dot = delta.normalize().dot(camLook);
            double cone = form.isShield() ? 0.56 : 0.72;
            if (dot > cone) return true;
        }
        return false;
    }

    private static void renderSatelliteStoneSwarm(PoseStack matrices, MultiBufferSource.BufferSource bufferSource,
                                                  BlockRenderDispatcher blockRenderer, ClientFormation f,
                                                  Entity owner, Vec3 camPos, Vec3 camLook, double time, float tickDelta, boolean firstPerson) {
        // Keep sculpted silhouettes (Wings, Stairs, Bridge, Shields, Railgun, Perch) razor-sharp without noisy crumb clutter!
        if (f.formation != BlockDominionBrain.FormationKind.IDLE) return;
        double distSq = owner.distanceToSqr(camPos);
        if (distSq > 40.0 * 40.0) return;

        int count = f.blocks.size();
        int maxShards = Math.min(10, count / 4);
        if (maxShards <= 0) return;
        int stride = Math.max(1, count / maxShards);
        for (int i = 0; i < count; i += stride) {
            VisualBlock vb = f.blocks.get(i);
            Vec3 host = vb.prevPos.lerp(vb.pos, tickDelta);
            long sd = 3100L + i * 73L;
            double ang = DominionShader.hash(sd) * Math.PI * 2.0 + time * 0.055;
            double rad = 0.50 + 0.40 * DominionShader.hash(sd + 2);
            double vert = Math.sin(ang * 1.4 + sd) * 0.28;
            Vec3 pos = host.add(Math.cos(ang) * rad, vert, Math.sin(ang) * rad);
            if (firstPerson) {
                Vec3 d = pos.subtract(camPos);
                double d2 = d.lengthSqr();
                if (d2 < 2.6 * 2.6 || (d2 < 6.0 * 6.0 && d.normalize().dot(camLook) > 0.52)) continue;
            }
            float s = (float) (0.14 + 0.08 * DominionShader.hash(sd + 3));
            int light = sampleLight(pos, owner);
            matrices.pushPose();
            matrices.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
            matrices.mulPose(Axis.YP.rotationDegrees((float) (time * 3.5 + i * 41)));
            matrices.mulPose(Axis.XP.rotationDegrees((float) (time * 2.4 + i * 27)));
            matrices.scale(s, s, s);
            matrices.translate(-0.5, -0.5, -0.5);
            blockRenderer.renderSingleBlock(vb.state, matrices, bufferSource, light, OverlayTexture.NO_OVERLAY);
            matrices.popPose();
        }
    }

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
            if (ground.isAir() || !ground.getFluidState().isEmpty() || !ground.isCollisionShapeFullBlock(world, samplePos)) {
                ground = Blocks.DEEPSLATE.defaultBlockState();
            }
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

    private static Quaternionf computeTargetOrientation(ClientFormation f, int index, int count,
                                                        Vec3 blockPos, Vec3 chest, Vec3 look, Vec3 yawFwd, double time, float spinPhase,
                                                        float wingPitchDeg, float wingRollDeg, float wingAeroFrac) {
        if (f.formation == BlockDominionBrain.FormationKind.SHIELD_SINGLE) {
            return faceDirection(f.threatPrimary);
        } else if (f.formation == BlockDominionBrain.FormationKind.SHIELD_MULTI) {
            int half = (count + 1) / 2;
            return faceDirection(index < half ? f.threatPrimary : f.threatSecondary);
        } else if (f.formation == BlockDominionBrain.FormationKind.SHIELD_DOME) {
            Vec3 radial = blockPos.subtract(chest);
            return faceDirection(radial.lengthSqr() > 1e-4 ? radial : look);
        } else if (f.formation.isVolley() || (f.formation == BlockDominionBrain.FormationKind.RAILGUN_RING && index < 5)) {
            return faceDirection(look).mul(Axis.ZP.rotationDegrees(spinPhase * 28f));
        } else if (f.formation.isStepping() && index < 7) {
            // World-axis-aligned blocks so Air-Stairs and Liquid Bridge look like genuine crisp Minecraft architecture!
            return new Quaternionf();
        } else if (f.formation == BlockDominionBrain.FormationKind.RAIN_UMBRELLA && index < 7) {
            return Axis.YP.rotationDegrees((float) (time * 1.4 + index * 60f));
        } else if (f.formation == BlockDominionBrain.FormationKind.SOVEREIGN_PERCH) {
            BlockDominionBrain.WingPose wp = BlockDominionBrain.sovereignPerchPose(index, count, time, yawFwd);
            Matrix3f basisMat = new Matrix3f(
                    (float) wp.axisX().x, (float) wp.axisX().y, (float) wp.axisX().z,
                    (float) wp.axisY().x, (float) wp.axisY().y, (float) wp.axisY().z,
                    (float) wp.axisZ().x, (float) wp.axisZ().y, (float) wp.axisZ().z
            );
            return new Quaternionf().setFromNormalized(basisMat);
        } else if (f.formation.isMeteorDive()) {
            BlockDominionBrain.WingPose wp = BlockDominionBrain.meteorDivePose(
                    index, count, time, yawFwd, wingPitchDeg, wingRollDeg);
            Matrix3f basisMat = new Matrix3f(
                    (float) wp.axisX().x, (float) wp.axisX().y, (float) wp.axisX().z,
                    (float) wp.axisY().x, (float) wp.axisY().y, (float) wp.axisY().z,
                    (float) wp.axisZ().x, (float) wp.axisZ().y, (float) wp.axisZ().z
            );
            return new Quaternionf().setFromNormalized(basisMat);
        } else if (f.formation.isWings()) {
            BlockDominionBrain.WingPose wp = BlockDominionBrain.wingPose(
                    index, count, time, yawFwd, f.formation == BlockDominionBrain.FormationKind.WINGS_BOOST,
                    wingPitchDeg, wingRollDeg, wingAeroFrac);
            Matrix3f basisMat = new Matrix3f(
                    (float) wp.axisX().x, (float) wp.axisX().y, (float) wp.axisX().z,
                    (float) wp.axisY().x, (float) wp.axisY().y, (float) wp.axisY().z,
                    (float) wp.axisZ().x, (float) wp.axisZ().y, (float) wp.axisZ().z
            );
            return new Quaternionf().setFromNormalized(basisMat);
        } else {
            float spin = spinPhase * (180f / (float) Math.PI);
            return Axis.YP.rotationDegrees(spin).mul(Axis.XP.rotationDegrees(spin * 0.65f + index * 19f));
        }
    }

    private static Quaternionf faceDirection(Vec3 dir) {
        Vec3 n = dir == null || dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : dir.normalize();
        return new Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f((float) n.x, (float) n.y, (float) n.z));
    }

    /**
     * Renders a sculpted 3-piece 3D Stone Lance (diamond spearhead + carved cross-guard + elongated shaft)
     * with magnetic piece-by-piece interlocking assembly ({@code assembleT = 0..1}).
     */
    private static void renderStoneLance(PoseStack matrices, MultiBufferSource.BufferSource bufferSource,
                                         BlockRenderDispatcher blockRenderer, Vec3 anchor, Vec3 dir, Vec3 camPos,
                                         float scaleMul, BlockState tipMat, BlockState shaftMat, int light) {
        renderStoneLance(matrices, bufferSource, blockRenderer, anchor, dir, camPos, scaleMul, 1.0f, tipMat, shaftMat, light);
    }

    private static void renderStoneLance(PoseStack matrices, MultiBufferSource.BufferSource bufferSource,
                                         BlockRenderDispatcher blockRenderer, Vec3 anchor, Vec3 dir, Vec3 camPos,
                                         float scaleMul, float assembleT, BlockState tipMat, BlockState shaftMat, int light) {
        if (scaleMul <= 0.04f) return;
        float aT = Mth.clamp(assembleT, 0f, 1f);
        float aEase = 1.0f - (1.0f - aT) * (1.0f - aT) * (1.0f - aT);
        float spread = 1.0f - aEase;

        Quaternionf orient = faceDirection(dir);
        matrices.pushPose();
        matrices.translate(anchor.x - camPos.x, anchor.y - camPos.y, anchor.z - camPos.z);
        matrices.mulPose(orient);
        matrices.scale(scaleMul, scaleMul, scaleMul);

        // 1. Elongated razor diamond spearhead blade (+Z) — flies in from upper-front & clicks into collar!
        matrices.pushPose();
        matrices.translate(spread * 0.55, spread * 0.72, 0.82 + spread * 1.15);
        matrices.mulPose(Axis.ZP.rotationDegrees(45f + spread * 135f));
        matrices.scale(0.20f, 0.20f, 1.38f);
        matrices.translate(-0.5, -0.5, -0.5);
        blockRenderer.renderSingleBlock(tipMat, matrices, bufferSource, light, OverlayTexture.NO_OVERLAY);
        matrices.popPose();

        // 2. Heavy carved cross-guard collar — spins inward radially to lock blade & shaft!
        matrices.pushPose();
        matrices.translate(-spread * 0.75, spread * 0.40, 0.06);
        matrices.mulPose(Axis.ZP.rotationDegrees(45f - spread * 210f));
        matrices.scale(0.42f, 0.42f, 0.16f);
        matrices.translate(-0.5, -0.5, -0.5);
        blockRenderer.renderSingleBlock(tipMat, matrices, bufferSource, light, OverlayTexture.NO_OVERLAY);
        matrices.popPose();

        // 3. Long slender stone lance shaft (-Z) — thrusts forward from behind into the cross-guard socket!
        matrices.pushPose();
        matrices.translate(0.0, -spread * 0.45, -1.25 - spread * 1.65);
        matrices.mulPose(Axis.ZP.rotationDegrees(spread * 90f));
        matrices.scale(0.14f, 0.14f, 2.55f);
        matrices.translate(-0.5, -0.5, -0.5);
        blockRenderer.renderSingleBlock(shaftMat, matrices, bufferSource, light, OverlayTexture.NO_OVERLAY);
        matrices.popPose();

        matrices.popPose();
    }

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

                if (f.mode == BlockDominionRules.Mode.MANUAL && !f.blocks.isEmpty()) {
                    int count = f.blocks.size();

                    // Pickup / Earthen Surge crackling lightning tethers from ground up to rising blocks
                    for (int i = 0; i < count; i++) {
                        VisualBlock vb = f.blocks.get(i);
                        if (vb.pickupOrigin != null) {
                            double age = time - vb.pickupStartTick;
                            if (age >= 0 && age < 11) {
                                int a = (int) (215 * (1.0 - age / 11.0));
                                Vec3 from = vb.pickupOrigin.subtract(camPos);
                                Vec3 to = vb.prevPos.lerp(vb.pos, tickDelta).subtract(camPos);
                                if (bodyPass) {
                                    DominionShader.emitLiveBolt(buffer, mat, from, to, 6, 0.22, 0.08, 200L + i, time, f.dr(), f.dg(), f.db(), a);
                                } else {
                                    DominionShader.emitLiveBolt(buffer, mat, from, to, 6, 0.22, 0.045, 200L + i, time, f.pr(), f.pg(), f.pb(), a);
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
                    } else if (f.formation.isVolley()) {
                        emitVolleyVfx(buffer, mat, f, chest, look, camRight, camUp, time, tickDelta, camPos, bodyPass);
                        emitted = true;
                    } else if (f.formation == BlockDominionBrain.FormationKind.RAILGUN_RING) {
                        if (!firstPerson) {
                            emitRailgunVfx(buffer, mat, f, chest, look, camRight, camUp, time, tickDelta, camPos, bodyPass);
                            emitted = true;
                        }
                    } else if (f.formation.isStepping()) {
                        emitAirStairsVfx(buffer, mat, f, camRight, camUp, time, tickDelta, camPos, bodyPass);
                        emitted = true;
                    } else if (f.formation == BlockDominionBrain.FormationKind.RAIN_UMBRELLA) {
                        emitUmbrellaVfx(buffer, mat, f, chest, time, bodyPass);
                        emitted = true;
                    } else if (f.formation.isWings() && f.formation != BlockDominionBrain.FormationKind.SOVEREIGN_PERCH) {
                        emitWingsVfx(buffer, mat, f, owner, chest, look, forward, camRight, camUp, time, tickDelta, camPos, firstPerson, bodyPass);
                        emitted = true;
                    }
                }
            }

            // Parry: true 3D curved Fresnel kinetic shock dome + billowing world-space dust
            for (ParryBurst burst : PARRIES) {
                Entity owner = world.getEntity(burst.playerId);
                if (owner == null || owner.distanceToSqr(camPos) > 96.0 * 96.0) continue;
                double age = time - burst.startTick;
                if (age < 0 || age >= 14) continue;
                double t = age / 14.0;
                ClientFormation pf = FORMATIONS.get(burst.playerId);
                BlockDominionPalette pal = pf != null ? pf.palette : BlockDominionPalette.SOVEREIGN_GOLD;
                Vec3 chest = owner.getPosition(tickDelta).add(0, 1.15, 0).subtract(camPos);
                int a = (int) (205 * (1.0 - t) * (1.0 - t));
                if (bodyPass) {
                    Vec3 impact = chest.add(burst.direction.scale(1.32));
                    for (int s = 0; s < 5; s++) {
                        double ang = s * (Math.PI * 2.0 / 5.0) + t * 1.2;
                        Vec3[] uw = DominionShader.basis(burst.direction);
                        Vec3 sp = impact.add(uw[0].scale(Math.cos(ang) * (0.35 + t * 1.45)))
                                .add(uw[1].scale(Math.sin(ang) * (0.35 + t * 1.45)));
                        DominionShader.emitBillboard(buffer, mat, sp, camRight, camUp, 0.42 + t * 0.52, ang,
                                DominionShader.FX_SMOKE, 50 + s, pal.midR, pal.midG, pal.midB, (int) (a * 0.36));
                    }
                } else {
                    DominionShader.emitFresnelDome(buffer, mat, chest, burst.direction,
                            1.35 + t * 1.15, 0.85, 18, 6, DominionShader.FX_HEX_MIST, 55,
                            pal.hotR, pal.hotG, pal.hotB, a);
                }
                emitted = true;
            }

            // Surge / Takeoff / Volley: true 3D expanding Fresnel air-compression dome (zero flat PNG rings!)
            for (WaveBurst w : WAVES) {
                Entity owner = world.getEntity(w.playerId);
                if (owner == null || owner.distanceToSqr(camPos) > 96.0 * 96.0) continue;
                double age = time - w.startTick;
                if (age < 0 || age >= w.duration) continue;
                double t = age / w.duration;
                double ease = 1.0 - (1.0 - t) * (1.0 - t);
                Vec3 origin = owner.getPosition(tickDelta).add(0, 1.15, 0).subtract(camPos);
                int a = (int) (135 * (1.0 - t) * (1.0 - t));
                if (!bodyPass) {
                    if (Math.abs(w.axis.y) > 0.85) {
                        DominionShader.emitGroundPool(buffer, mat,
                                owner.getPosition(tickDelta).add(0, 0.08, 0).subtract(camPos),
                                1.2 + ease * w.maxRadius * 1.1, (int) (w.seed % 97), w.pR, w.pG, w.pB, (int) (a * 0.85));
                    } else {
                        DominionShader.emitFresnelCone(buffer, mat, origin.add(w.axis.scale(0.8)), w.axis,
                                1.8 + ease * 2.4, 0.75 + ease * w.maxRadius * 0.55, 18, 6,
                                (int) (w.seed % 97), w.pR, w.pG, w.pB, a);
                    }
                    emitted = true;
                }
            }

            // Monolith Cage crisp handcrafted VFX (ZERO plastic hologram dome, ZERO blurry floor blobs, ZERO giant foggy cones!):
            // - Phase 2: Razor-thin dead-straight hairline sightlines locking onto target heart during cock-back anticipation
            // - Phase 3: Crisp kinetic supersonic pierce slash-streaks shooting cleanly along each spear's exit path!
            if (!bodyPass) {
                for (ClientCage cage : ACTIVE_CAGES.values()) {
                    Entity target = world.getEntity(cage.targetId);
                    Vec3 worldCenter = target != null && target.isAlive()
                            ? target.getPosition(tickDelta).add(0, target.getBbHeight() * 0.52, 0)
                            : cage.anchor;
                    Vec3 center = worldCenter.subtract(camPos);
                    if (center.lengthSqr() > 96.0 * 96.0) continue;
                    double age = Math.max(0.0, time - cage.startTick);

                    if (age >= BlockDominionRules.CAGE_SPEAR_FORM_TICK || cage.impaled) {
                        Vec3[] dirs = BlockDominionBrain.cageSpearDirections(BlockDominionRules.CAGE_SPEAR_COUNT, cage.yawOffset);
                        if (cage.impaled && cage.impaleTick >= 0) {
                            double impAge = time - cage.impaleTick;
                            if (impAge >= 0 && impAge < 6) {
                                double it = impAge / 6.0;
                                int ia = (int) (220 * (1.0 - it) * (1.0 - it));
                                for (int s = 0; s < dirs.length; s++) {
                                    Vec3 d = dirs[s];
                                    // Razor-sharp kinetic pierce slash shooting straight through the target!
                                    DominionShader.emitVelocityStreak(buffer, mat,
                                            center.add(d.scale(0.85 + it * 2.2)), d,
                                            1.45 + it * 1.1, 0.048 * (1.0 - it * 0.65),
                                            70 + s, 255, 244, 210, ia);
                                }
                                emitted = true;
                            }
                        } else {
                            double charge = Mth.clamp((age - BlockDominionRules.CAGE_SPEAR_FORM_TICK)
                                    / (double) Math.max(1, BlockDominionRules.CAGE_IMPALE_TICK - BlockDominionRules.CAGE_SPEAR_FORM_TICK), 0.0, 1.0);
                            boolean cocking = charge > 0.66;
                            if (charge > 0.25) {
                                int la = (int) (cocking ? (115 + (charge - 0.66) * 280) : (charge * 70));
                                for (int s = 0; s < dirs.length; s++) {
                                    Vec3 tipPos = center.subtract(dirs[s].scale(cocking ? 3.55 : 2.65));
                                    // Dead-straight razor-thin sightline (zero messy zig-zag lightning!)
                                    DominionShader.emitLiveBolt(buffer, mat, tipPos, center, 2, 0.0,
                                            cocking ? 0.011 : 0.007, 7100L + s * 23L, time,
                                            cocking ? 255 : cage.palette.hotR,
                                            cocking ? 242 : cage.palette.hotG,
                                            cocking ? 205 : cage.palette.hotB, Mth.clamp(la, 0, 210));
                                    if (cocking) {
                                        // Crisp glint star right on the spear tip during cock-back anticipation!
                                        DominionShader.emitBillboard(buffer, mat, tipPos, camRight, camUp,
                                                0.22 + (charge - 0.66) * 0.45, time * 0.15 + s,
                                                DominionShader.FX_SPARK, 90 + s, 255, 246, 215, 195);
                                    }
                                }
                                emitted = true;
                            }
                        }
                    }
                }

                // Sleek forward rail tracer streak (starts cleanly ahead of the camera — zero ugly starburst/cone in 1st person!)
                boolean camFirstPerson = !camera.isDetached();
                for (MuzzleTracer tr : MUZZLE_TRACERS) {
                    double age = time - tr.startTick;
                    if (age < 0 || age >= 5) continue;
                    double t = age / 5.0;
                    Vec3 orig = tr.origin.subtract(camPos);
                    if (orig.lengthSqr() > 96.0 * 96.0) continue;
                    int a = (int) ((camFirstPerson ? 115 : 155) * (1.0 - t) * (1.0 - t));
                    Vec3 start = orig.add(tr.dir.scale(camFirstPerson ? (2.4 + t * 5.0) : (0.6 + t * 3.5)));
                    Vec3 end = start.add(tr.dir.scale(4.5 + t * 5.0));
                    DominionShader.emitLiveBolt(buffer, mat, start, end,
                            3, 0.02, 0.022 * (1.0 - t * 0.65), tr.startTick * 17L, time,
                            tr.palette.hotR, tr.palette.hotG, tr.palette.hotB, a);
                    emitted = true;
                }

                // Multi-Tier Meteor Strike Detonation: Smooth High-Poly Round Spherical Fireball Domes (ZERO boxy/square shapes!)
                for (MeteorBlast blast : METEOR_BLASTS) {
                    double dur = 20.0 + blast.tier * 7.0;
                    double age = time - blast.startTick;
                    if (age < 0 || age >= dur) continue;
                    Vec3 p = blast.pos.subtract(camPos);
                    if (p.lengthSqr() > 140.0 * 140.0) continue;
                    double t = age / dur;
                    double ease = 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t);
                    double fade = (1.0 - t) * (1.0 - t);
                    int tier = blast.tier;
                    double outerR = (tier == 3 ? 17.5 : (tier == 2 ? 12.0 : 7.0)) * (0.22 + 0.78 * ease);

                    // 1. Silky-smooth 36x16 round outer crimson-orange fireball hemisphere
                    DominionShader.emitFresnelDome(buffer, mat, p.add(0, 0.15, 0), new Vec3(0, 1, 0),
                            outerR, Math.PI * 0.54, 36, 16, DominionShader.FX_SHELL, 91,
                            255, tier == 3 ? 64 : 110, tier == 3 ? 12 : 24, (int) (235 * fade));
                    // 2. Smooth circular molten shockwave pool hugging the round crater rim
                    DominionShader.emitGroundPool(buffer, mat, p.add(0, 0.10, 0), outerR * 0.96, 92,
                            255, tier == 3 ? 82 : 118, 22, (int) (210 * (1.0 - t * 0.72)));

                    if (tier >= 2) {
                        // 3. Smooth round mid solar-gold plasma sphere
                        DominionShader.emitFresnelDome(buffer, mat, p.add(0, 0.20, 0), new Vec3(0, 1, 0),
                                outerR * 0.68, Math.PI * 0.55, 32, 14, DominionShader.FX_SHELL, 93,
                                255, 192, 54, (int) (220 * fade));
                    }
                    if (tier == 3) {
                        // 4. Incandescent round white-gold inner core sphere + rising round fireball crown!
                        DominionShader.emitFresnelDome(buffer, mat, p.add(0, 0.25, 0), new Vec3(0, 1, 0),
                                outerR * 0.42, Math.PI * 0.58, 28, 12, DominionShader.FX_SHELL, 95,
                                255, 248, 205, (int) (240 * fade * fade));
                        DominionShader.emitFresnelDome(buffer, mat, p.add(0, outerR * 0.45 * ease, 0), new Vec3(0, 1, 0),
                                outerR * 0.48, Math.PI * 0.95, 32, 14, DominionShader.FX_SHELL, 96,
                                255, 118, 28, (int) (175 * fade));
                    }
                    emitted = true;
                }
            }

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

    private static void emitIdleSwarmVfx(VertexConsumer v, Matrix4f m, ClientFormation f, Vec3 chest, Vec3 forward,
                                         Vec3 camRight, Vec3 camUp, double time, float tickDelta, Vec3 camPos,
                                         boolean firstPerson, boolean bodyPass) {
        // Keep Idle orbit razor-clean: let the real Minecraft blocks shine without hazy smoke clouds or glowing floor blobs!
    }

    /** Floating Stone Railgun Ring VFX (3rd-person only): subtle thin accelerator sparks linking the 5 spinning blocks. */
    private static void emitRailgunVfx(VertexConsumer v, Matrix4f m, ClientFormation f, Vec3 chest, Vec3 look,
                                       Vec3 camRight, Vec3 camUp, double time, float tickDelta, Vec3 camPos, boolean bodyPass) {
        int barrelBlocks = Math.min(5, f.blocks.size());
        if (!bodyPass) {
            for (int i = 0; i < barrelBlocks; i++) {
                Vec3 p1 = f.blocks.get(i).prevPos.lerp(f.blocks.get(i).pos, tickDelta).subtract(camPos);
                Vec3 p2 = f.blocks.get((i + 1) % barrelBlocks).prevPos.lerp(f.blocks.get((i + 1) % barrelBlocks).pos, tickDelta).subtract(camPos);
                DominionShader.emitLiveBolt(v, m, p1, p2, 4, 0.04, 0.016, 4750L + i * 19L, time,
                        f.hr(), f.hg(), f.hb(), 125);
            }
        }
    }

    /**
     * Cinematic 3D Block Wings VFX (ZERO stiff paper-strip feather ribbons, ZERO MS-Paint zig-zag lines!):
     * - True 3D Volumetric Fresnel Aerodynamic Slipstream Cone wrapping the caster during Mach/Jet boost
     * - Sleek gossamer wingtip contrail ribbons + soft world-space vapor shed behind wingtips
     * - World-Space Ground Light Pool dyeing the terrain directly beneath low-flying wings!
     */
    private static void emitWingsVfx(VertexConsumer v, Matrix4f m, ClientFormation f, Entity owner, Vec3 chest,
                                     Vec3 look, Vec3 forward, Vec3 camRight, Vec3 camUp,
                                     double time, float tickDelta, Vec3 camPos, boolean firstPerson, boolean bodyPass) {
        if (bodyPass) return; // Zero murky smoke billboards cluttering the wings!
        boolean meteor = f.formation.isMeteorDive();
        int mTier = meteor ? Math.max(1, f.meteorTier) : 0;
        boolean boosted = f.formation == BlockDominionBrain.FormationKind.WINGS_BOOST || meteor;
        float wingPitchDeg = BlockDominionPose.bodyPitchDegrees(owner.getId(), tickDelta);
        float wingRollDeg = BlockDominionPose.bodyRollDegrees(owner.getId(), tickDelta);
        float wingAeroFrac = BlockDominionPose.glideWeight(owner.getId(), tickDelta);
        Vec3 yawFwd = yawForward(owner);
        Vec3 wingAnchor = chest.add(0, Mth.lerp(wingAeroFrac, 0.0, -0.30), 0);

        // Keep 1st-person sky 100% unobstructed: never draw body-wrapping cones or wingtip smears over the player's eyes!
        if (!firstPerson) {
            if (!meteor) {
                emitWingTrail(v, m, f, f.leftWingTrail, camPos, boosted ? 0.16 : 0.09, 41, boosted, false);
                emitWingTrail(v, m, f, f.rightWingTrail, camPos, boosted ? 0.16 : 0.09, 42, boosted, false);
            }

            if (meteor) {
                Vec3 bodyAxis = BlockDominionBrain.rotateWingVec(new Vec3(0, 1, 0), yawFwd, wingPitchDeg, wingRollDeg);
                Vec3 axis = bodyAxis.lengthSqr() > 1e-4 ? bodyAxis.normalize() : look.normalize();
                if (mTier == 3) {
                    DominionShader.emitFresnelCone(v, m, wingAnchor.add(axis.scale(1.65)), axis,
                            3.4, 2.20, 28, 10, 44, 255, 58, 12, 210);
                    DominionShader.emitFresnelCone(v, m, wingAnchor.add(axis.scale(1.38)), axis,
                            2.7, 1.54, 24, 8, 45, 255, 152, 24, 200);
                    DominionShader.emitFresnelCone(v, m, wingAnchor.add(axis.scale(1.12)), axis,
                            2.0, 0.98, 20, 7, 48, 255, 248, 210, 195);
                } else if (mTier == 2) {
                    DominionShader.emitFresnelCone(v, m, wingAnchor.add(axis.scale(1.45)), axis,
                            2.9, 1.84, 26, 9, 45, 255, 86, 18, 195);
                    DominionShader.emitFresnelCone(v, m, wingAnchor.add(axis.scale(1.18)), axis,
                            2.2, 1.18, 22, 7, 47, 255, 212, 92, 180);
                } else {
                    DominionShader.emitFresnelCone(v, m, wingAnchor.add(axis.scale(1.25)), axis,
                            2.5, 1.46, 24, 8, 45, 255, 118, 26, 175);
                    DominionShader.emitFresnelCone(v, m, wingAnchor.add(axis.scale(1.02)), axis,
                            1.7, 0.90, 20, 6, 48, 255, 238, 168, 160);
                }
                if (mTier >= 2 && f.blocks.size() >= 6) {
                    int arcs = mTier == 3 ? 4 : 2;
                    for (int a = 0; a < arcs; a++) {
                        int i1 = (a * 5 + (int) (time / 3)) % f.blocks.size();
                        int i2 = (i1 + 3) % f.blocks.size();
                        Vec3 p1 = f.blocks.get(i1).prevPos.lerp(f.blocks.get(i1).pos, tickDelta).subtract(camPos);
                        Vec3 p2 = f.blocks.get(i2).prevPos.lerp(f.blocks.get(i2).pos, tickDelta).subtract(camPos);
                        if (p1.distanceToSqr(p2) < 9.0) {
                            DominionShader.emitLiveBolt(v, m, p1, p2, 6, 0.12, mTier == 3 ? 0.028 : 0.020,
                                    8800L + a * 23L, time, 255, mTier == 3 ? 236 : 165, mTier == 3 ? 150 : 48, 175);
                        }
                    }
                }
            } else if (boosted) {
                // Sleek subtle Mach edge in 3rd-person only (never foggy!)
                Vec3 vel = owner.getDeltaMovement();
                Vec3 axis = vel.lengthSqr() > 0.04 ? vel.normalize() : look.normalize();
                DominionShader.emitFresnelCone(v, m, wingAnchor.add(axis.scale(1.25)), axis,
                        2.8, 1.65, 20, 6, 45, f.hr(), f.hg(), f.hb(), 78);
            }
        }

        // Exact raycast-grounded Meteor Target Pool ONLY during active Meteor Dive!
        if (meteor) {
            Vec3 vel = owner.getDeltaMovement();
            Vec3 axis = vel.lengthSqr() > 0.04 ? vel.normalize() : look.normalize();
            double maxRay = 14.0 + mTier * 5.0;
            Vec3 start = owner.getPosition(tickDelta).add(0, 0.8, 0);
            var hit = world.clip(new net.minecraft.world.level.ClipContext(
                    start, start.add(axis.scale(maxRay)),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, owner));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                Vec3 hitPos = hit.getLocation();
                double dist = start.distanceTo(hitPos);
                double kProx = Mth.clamp(1.0 - dist / maxRay, 0.0, 1.0);
                double poolR = 2.2 + mTier * 1.4 + kProx * (2.4 + mTier * 1.6);
                int aWash = Mth.clamp((int) (kProx * (165 + mTier * 20)), 0, 215);
                DominionShader.emitGroundPool(v, m, hitPos.add(0, 0.08, 0).subtract(camPos), poolR, 49,
                        255, mTier == 3 ? 68 : 112, mTier == 3 ? 14 : 26, aWash);
            }
        }
    }

    /** QoL Rain Umbrella Canopy: true 3D curved Fresnel water-repelling dome overhead (zero flat rings!). */
    private static void emitUmbrellaVfx(VertexConsumer v, Matrix4f m, ClientFormation f, Vec3 chest,
                                        double time, boolean bodyPass) {
        if (!bodyPass) {
            // Curved protective 3D spherical cap directly hugging the 7 overhead stone blocks
            DominionShader.emitFresnelDome(v, m, chest.add(0, 0.35, 0), new Vec3(0, 1, 0),
                    1.68, 0.72, 20, 6, DominionShader.FX_HEX_MIST, 33, 175, 224, 255, 115);
        }
    }

    /** Crisp Minecraft-native Air-Step Stairs & Liquid Bridge: zero fake glowing blobs pasted on top of slabs! */
    private static void emitAirStairsVfx(VertexConsumer v, Matrix4f m, ClientFormation f, Vec3 camRight, Vec3 camUp,
                                         double time, float tickDelta, Vec3 camPos, boolean bodyPass) {
        // Let the real grid-aligned stone/block treads read cleanly without blurry spotlight smudges.
    }

    /** True 3D Curved Fresnel Kinetic Shield Domes (zero flat 2D floating discs!). */
    private static void emitShieldVfx(VertexConsumer v, Matrix4f m, ClientFormation f, Vec3 chest, Vec3 forward,
                                      double time, float tickDelta, Vec3 camPos, boolean firstPerson, boolean bodyPass) {
        int count = f.blocks.size();
        if (!bodyPass) {
            if (f.formation == BlockDominionBrain.FormationKind.SHIELD_SINGLE) {
                Vec3 n = f.threatPrimary.lengthSqr() > 1e-4 ? f.threatPrimary.normalize() : forward;
                DominionShader.emitFresnelDome(v, m, chest, n, 1.92, 0.78, 18, 6,
                        DominionShader.FX_HEX_MIST, 14, f.pr(), f.pg(), f.pb(), firstPerson ? 95 : 175);
            } else if (f.formation == BlockDominionBrain.FormationKind.SHIELD_MULTI) {
                Vec3 n1 = f.threatPrimary.lengthSqr() > 1e-4 ? f.threatPrimary.normalize() : forward;
                Vec3 n2 = f.threatSecondary.lengthSqr() > 1e-4 ? f.threatSecondary.normalize() : forward.scale(-1);
                int a = firstPerson ? 85 : 165;
                DominionShader.emitFresnelDome(v, m, chest, n1, 1.88, 0.72, 16, 5, DominionShader.FX_HEX_MIST, 15, f.pr(), f.pg(), f.pb(), a);
                DominionShader.emitFresnelDome(v, m, chest, n2, 1.88, 0.72, 16, 5, DominionShader.FX_HEX_MIST, 16, f.hr(), f.hg(), f.hb(), a);
            } else if (f.formation == BlockDominionBrain.FormationKind.SHIELD_DOME) {
                DominionShader.emitFresnelDome(v, m, chest.add(0, 0.1, 0), new Vec3(0, 1, 0), 2.55, Math.PI * 0.92,
                        22, 10, DominionShader.FX_HEX_MIST, 18, f.pr(), f.pg(), f.pb(), firstPerson ? 85 : 165);
            }

            if (count >= 4) {
                int arcs = Math.min(4, count / 6);
                for (int a = 0; a < arcs; a++) {
                    int i1 = (a * 4 + (int) (time / 5)) % count;
                    int i2 = (i1 + 2) % count;
                    Vec3 p1 = f.blocks.get(i1).prevPos.lerp(f.blocks.get(i1).pos, tickDelta).subtract(camPos);
                    Vec3 p2 = f.blocks.get(i2).prevPos.lerp(f.blocks.get(i2).pos, tickDelta).subtract(camPos);
                    if (p1.distanceToSqr(p2) < 6.5) {
                        DominionShader.emitLiveBolt(v, m, p1, p2, 5, 0.10, 0.020, 2400L + a * 19L, time,
                                f.hr(), f.hg(), f.hb(), firstPerson ? 75 : 135);
                    }
                }
            }
        }
    }

    /** Volley & Aerial Orbital Meteor Charge: crisp sparse convergence glints (zero foggy glowing sphere!). */
    private static void emitVolleyVfx(VertexConsumer v, Matrix4f m, ClientFormation f, Vec3 chest, Vec3 look,
                                      Vec3 camRight, Vec3 camUp, double time, float tickDelta, Vec3 camPos, boolean bodyPass) {
        if (bodyPass) return;
        float vp = Mth.clamp(f.volleyTicks / (float) BlockDominionRules.VOLLEY_CHARGE_TICKS, 0f, 1f);
        boolean orbital = f.formation == BlockDominionBrain.FormationKind.ORBITAL_METEOR;
        Vec3 focal = orbital ? chest.add(0, 3.0, 0) : chest.add(look.normalize().scale(2.15));
        int count = f.blocks.size();
        int step = Math.max(1, count / 5);
        for (int i = 0; i < count; i += step) {
            Vec3 bp = f.blocks.get(i).prevPos.lerp(f.blocks.get(i).pos, tickDelta).subtract(camPos);
            DominionShader.emitLiveBolt(v, m, bp, focal, 4, 0.05, 0.012 + vp * 0.008,
                    2900L + i * 13L, time, f.hr(), f.hg(), f.hb(), (int) (95 * vp + 25));
        }
    }

    /** Auto Mode Gravity Field: restrained expanding ground pulse (zero murky smoke clouds!). */
    private static void emitAutoFieldVfx(VertexConsumer v, Matrix4f m, ClientFormation f, Vec3 feet, Vec3 camRight, Vec3 camUp,
                                         double time, boolean bodyPass) {
        if (bodyPass) return;
        double pulseR = 1.6 + ((time * 0.22) % (BlockDominionRules.AUTO_SCAN_RADIUS - 1.6));
        int pulseA = (int) (72 * (1.0 - pulseR / BlockDominionRules.AUTO_SCAN_RADIUS));
        DominionShader.emitGroundPool(v, m, feet.add(0, 0.03, 0), pulseR, 36, f.pr(), f.pg(), f.pb(), pulseA);
    }

    private static void emitWingTrail(VertexConsumer v, Matrix4f m, ClientFormation f, ArrayDeque<Vec3> trail, Vec3 camPos,
                                      double width, int seed, boolean boosted, boolean meteor) {
        if (trail.size() < 2) return;
        Vec3[] pts = new Vec3[trail.size()];
        int i = 0;
        for (Vec3 p : trail) pts[i++] = p.subtract(camPos);
        // 2x Chaikin corner-cutting subdivision -> silky curved aerodynamic contrail with ZERO polygonal kinks!
        for (int pass = 0; pass < 2 && pts.length >= 3; pass++) {
            Vec3[] next = new Vec3[(pts.length - 1) * 2];
            next[0] = pts[0];
            int w = 1;
            for (int j = 0; j < pts.length - 2; j++) {
                next[w++] = pts[j].lerp(pts[j + 1], 0.75);
                next[w++] = pts[j + 1].lerp(pts[j + 2], 0.25);
            }
            next[w] = pts[pts.length - 1];
            pts = next;
        }
        int r = meteor ? 255 : (boosted ? f.hr() : f.pr());
        int g = meteor ? 128 : (boosted ? f.hg() : f.pg());
        int b = meteor ? 28 : (boosted ? f.hb() : f.pb());
        DominionShader.emitTrail(v, m, pts, width, DominionShader.FX_PLASMA, seed,
                r, g, b, meteor ? 200 : (boosted ? 125 : 75));
    }
}
