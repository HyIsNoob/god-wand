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
import net.minecraft.client.renderer.GameRenderer;
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
 * Milestone 2 Block Brain formation renderer, "farm aura" procedural VFX and CC0 soundscape driver.
 * Renders real BlockStates with nearest-slot matching, smart shield/dome/fan orientation,
 * pickup ground-crack + kinetic tether, sovereign halo rings, hex energy shields, parry bursts
 * with returning dust motes, and Auto-mode levitating gravity field.
 */
public final class BlockDominionRenderer {
    private static final class VisualBlock {
        BlockState state;
        Vec3 pos;
        Vec3 prevPos;
        float scale;
        float prevScale;
        float spinPhase;
        // Pickup arc animation (smooth ground shudder -> parabolic lift)
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

        WaveBurst(int playerId, Vec3 axis, double maxRadius, int duration, long startTick) {
            this.playerId = playerId;
            this.axis = axis.lengthSqr() > 1e-4 ? axis.normalize() : new Vec3(0, 1, 0);
            this.maxRadius = maxRadius;
            this.duration = duration;
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
        int volleyTicks;
        long unfoldStartTick = -1000;
        long shieldStartTick = -1000;
        long lastUpdateTick;
        final List<VisualBlock> blocks = new ArrayList<>();
    }

    private static final Map<Integer, ClientFormation> FORMATIONS = new HashMap<>();
    private static final List<ParryBurst> PARRIES = new ArrayList<>();
    private static final List<WaveBurst> WAVES = new ArrayList<>();
    private static ClientLevel world;
    private static int localParryFlashTicks;

    private BlockDominionRenderer() {}

    public static void reset() {
        FORMATIONS.clear();
        PARRIES.clear();
        WAVES.clear();
        localParryFlashTicks = 0;
        world = null;
        BlockDominionSounds.reset();
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
        f.volleyTicks = payload.volleyTicks();
        f.formation = BlockDominionBrain.FormationKind.byOrdinal(payload.formationKind());
        f.threatPrimary = new Vec3(payload.threat1X(), payload.threat1Y(), payload.threat1Z());
        f.threatSecondary = new Vec3(payload.threat2X(), payload.threat2Y(), payload.threat2Z());
        f.lastUpdateTick = now;

        Entity owner = world.getEntity(payload.playerId());
        Vec3 ownerPos = owner != null ? owner.position().add(0, 1.25, 0) : new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
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

        // Nearest-slot matching when formation transitions so blocks glide cleanly without tangling
        if (prevFormation != f.formation && f.blocks.size() > 1 && owner != null) {
            reassignNearestSlots(f, owner, now);
        }

        // Trigger VFX & CC0 SFX for discrete state transitions
        if (payload.eventKind() == BlockDominionRules.EVENT_PICKUP) {
            Vec3 origin = new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
            ImpactDebrisRenderer.groundWave(origin.add(0, -0.4, 0), 1.9, 0xEBC64E);
            BlockDominionSounds.play(origin, BlockDominionSounds.PICKUP, 0.55f, 0.92f + Math.min(ids.length, 32) * 0.012f);
        } else if (payload.eventKind() == BlockDominionRules.EVENT_UNFOLD || (wasStowed && !f.stowed)) {
            f.unfoldStartTick = now;
            if (WAVES.size() >= 12) WAVES.remove(0);
            WAVES.add(new WaveBurst(payload.playerId(), new Vec3(0, 1, 0), 3.1, 12, now));
            BlockDominionSounds.play(ownerPos, BlockDominionSounds.UNFOLD, 0.7f, 1.05f);
        } else if (payload.eventKind() == BlockDominionRules.EVENT_VOLLEY) {
            BlockDominionSounds.stopVolleyCharge(payload.playerId());
            BlockDominionSounds.play(ownerPos, BlockDominionSounds.VOLLEY_RELEASE, 1.05f, 1.0f);
            Vec3 aim = owner != null ? owner.getViewVector(1.0f) : new Vec3(0, 0, 1);
            if (WAVES.size() >= 12) WAVES.remove(0);
            WAVES.add(new WaveBurst(payload.playerId(), aim, 4.6, 14, now));
        } else if (payload.eventKind() == BlockDominionRules.EVENT_PARRY) {
            Vec3 threatDir = new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
            if (PARRIES.size() >= 16) PARRIES.remove(0);
            PARRIES.add(new ParryBurst(payload.playerId(), threatDir, decodeState(payload.eventBlockId()), now));
            BlockDominionSounds.play(ownerPos, BlockDominionSounds.PARRY, 0.95f, 1.02f);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getId() == payload.playerId()) {
                localParryFlashTicks = 4;
            }
        }

        // Shield lock snap cue when brain shifts from idle/stowed into defensive shield/dome
        if (!prevFormation.isShield() && f.formation.isShield() && !f.blocks.isEmpty()) {
            f.shieldStartTick = now;
            BlockDominionSounds.play(ownerPos, BlockDominionSounds.SHIELD_LOCK, 0.72f,
                    f.formation == BlockDominionBrain.FormationKind.SHIELD_DOME ? 0.85f : 1.08f);
        }

        // Volley charge spinning loop
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

        // Ensure ambient loop matches mode (Orbit hum in Manual, Gravity hum in Auto)
        int pid = payload.playerId();
        if (mode == BlockDominionRules.Mode.MANUAL && !f.blocks.isEmpty()) {
            BlockDominionSounds.ensureLoop(pid, BlockDominionSounds.ORBIT_LOOP,
                    () -> {
                        if (world == null) return null;
                        Entity e = world.getEntity(pid);
                        return e == null ? null : e.position().add(0, 1.25, 0);
                    },
                    () -> {
                        ClientFormation cf = FORMATIONS.get(pid);
                        return cf != null && cf.mode == BlockDominionRules.Mode.MANUAL && !cf.blocks.isEmpty() && !cf.hiddenInCutscene;
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
        if (localParryFlashTicks > 0) localParryFlashTicks--;

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

        for (var entry : FORMATIONS.entrySet()) {
            Entity owner = world.getEntity(entry.getKey());
            if (owner == null) continue;
            ClientFormation f = entry.getValue();
            if (f.volleyCharging) f.volleyTicks++;
            Vec3 chest = owner.position().add(0, 1.25, 0);
            Vec3 look = owner.getViewVector(1.0f);
            float unfoldProgress = Mth.clamp((now - f.unfoldStartTick) / (float) BlockDominionRules.UNFOLD_TICKS, 0f, 1f);
            float volleyProgress = Mth.clamp(f.volleyTicks / (float) BlockDominionRules.VOLLEY_CHARGE_TICKS, 0f, 1f);
            float targetScale = f.formation.isShield()
                    ? 0.72f
                    : BlockDominionRules.blockScale(f.stowed, unfoldProgress, f.volleyCharging, volleyProgress);

            int count = f.blocks.size();
            for (int i = 0; i < count; i++) {
                VisualBlock vb = f.blocks.get(i);
                vb.prevPos = vb.pos;
                vb.prevScale = vb.scale;

                Vec3 offset = BlockDominionBrain.slotOffset(
                        f.formation, i, count, now, look, f.threatPrimary, f.threatSecondary, unfoldProgress, volleyProgress);
                Vec3 target = chest.add(offset);

                long pickupAge = now - vb.pickupStartTick;
                if (vb.pickupOrigin != null && pickupAge >= 0 && pickupAge < 10) {
                    // 2-tick tremor at ground, then smooth upward parabolic Bézier arc into orbit
                    if (pickupAge < 2) {
                        double j = (pickupAge == 0 ? 0.05 : -0.05);
                        vb.pos = vb.pickupOrigin.add(j, 0.06 * pickupAge, -j);
                    } else {
                        double t = (pickupAge - 2) / 8.0;
                        double ease = 1.0 - (1.0 - t) * (1.0 - t);
                        Vec3 mid = vb.pickupOrigin.lerp(target, 0.5).add(0, 1.15, 0);
                        Vec3 a = vb.pickupOrigin.lerp(mid, ease);
                        Vec3 b = mid.lerp(target, ease);
                        vb.pos = a.lerp(b, ease);
                    }
                } else {
                    vb.pickupOrigin = null;
                    double lerpRate = f.formation.isShield() ? 0.48
                            : f.stowed ? 0.42
                            : f.volleyCharging ? 0.36
                            : 0.30;
                    if (vb.pos.distanceToSqr(target) > 64.0 * 64.0) {
                        vb.pos = target;
                        vb.prevPos = target;
                    } else {
                        vb.pos = vb.pos.lerp(target, lerpRate);
                    }
                }
                vb.scale = Mth.lerp(0.34f, vb.scale, targetScale);
                vb.spinPhase += f.stowed ? 0.08f : (f.volleyCharging ? 0.14f : 0.045f);
            }
        }
    }

    /** Crisp 4-tick golden edge parry flash on HUD (respects impactFrames setting, never obscures center view). */
    public static void renderHud(GuiGraphics graphics) {
        if (localParryFlashTicks <= 0 || !GodWandConfig.CLIENT.impactFrames) return;
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        float t = localParryFlashTicks / 4.0f;
        int edgeAlpha = Mth.clamp((int) (t * t * 95), 0, 255);
        int gold = (edgeAlpha << 24) | 0xFFE696;
        int thick = Math.max(8, Math.min(w, h) / 26);
        graphics.fill(0, 0, w, thick, gold);
        graphics.fill(0, h - thick, w, h, gold);
        graphics.fill(0, thick, thick, h - thick, gold);
        graphics.fill(w - thick, thick, w, h - thick, gold);
    }

    public static void render(PoseStack matrices, Camera camera, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;
        if (client.level != world) {
            reset();
            world = client.level;
        }
        if (FORMATIONS.isEmpty() && PARRIES.isEmpty() && WAVES.isEmpty()) return;

        Vec3 camPos = camera.getPosition();
        double time = world.getGameTime() + tickDelta;
        boolean localCutscene = AtomicCinematic.active() || FugaCinematic.active()
                || SilentGateCinematic.active() || PurpleCinematic.active();
        BlockRenderDispatcher blockRenderer = client.getBlockRenderer();
        MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        // 1. Render solid orbiting / shield / dome / fan / stowed blocks & Auto-mode levitating stones
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

                    applySmartOrientation(matrices, f, i, count, renderWorld, chest, look, vb.spinPhase + tickDelta * 0.045f);

                    matrices.scale(scale, scale, scale);
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

        // 3. Additive "Farm Aura" VFX: sovereign halo rings, hex shields, pickup tethers, parry shockwaves, returning motes, Auto gravity field
        renderAuraVfx(matrices, camPos, time, tickDelta, client, localCutscene);
    }

    /** Aligns block rotation to the active formation: flat fortress faces on shields/dome, aimed on volley, tumbling on idle. */
    private static void applySmartOrientation(PoseStack matrices, ClientFormation f, int index, int count,
                                              Vec3 blockPos, Vec3 chest, Vec3 look, float spinPhase) {
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

    /** Auto mode (§1.4 #5): 18 nearby terrain stones levitate ~0.5 blocks above ground in a 10-block gravity field. */
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

    private static void renderAuraVfx(PoseStack matrices, Vec3 camPos, double time, float tickDelta,
                                      Minecraft client, boolean localCutscene) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Matrix4f mat = matrices.last().pose();
            boolean emitted = false;

            for (var entry : FORMATIONS.entrySet()) {
                ClientFormation f = entry.getValue();
                Entity owner = world.getEntity(entry.getKey());
                if (owner == null || owner.distanceToSqr(camPos) > 96.0 * 96.0) continue;
                boolean isLocal = client.player != null && owner.getId() == client.player.getId();
                if (f.hiddenInCutscene || (isLocal && localCutscene)) continue;
                boolean firstPerson = isLocal && !Minecraft.getInstance().gameRenderer.getMainCamera().isDetached();
                Vec3 chest = owner.getPosition(tickDelta).add(0, 1.25, 0).subtract(camPos);
                Vec3 look = owner.getViewVector(tickDelta);

                if (f.mode == BlockDominionRules.Mode.AUTO) {
                    Vec3 feet = owner.getPosition(tickDelta).add(0, 0.16, 0).subtract(camPos);
                    emitOrientedRing(buffer, mat, feet, new Vec3(0, 1, 0), BlockDominionRules.AUTO_SCAN_RADIUS - 0.5, 0.14, 235, 198, 78, 95, 56);
                    emitOrientedRing(buffer, mat, feet.add(0, 0.04, 0), new Vec3(0, 1, 0), 3.4, 0.08, 255, 226, 130, 70, 36);
                    double pulseR = 1.8 + ((time * 0.22) % (BlockDominionRules.AUTO_SCAN_RADIUS - 1.8));
                    int pulseA = (int) (135 * (1.0 - pulseR / BlockDominionRules.AUTO_SCAN_RADIUS));
                    emitOrientedRing(buffer, mat, feet.add(0, 0.08, 0), new Vec3(0, 1, 0), pulseR, 0.14, 255, 236, 158, pulseA, 48);
                    // Subtle upward gravity strands around the field
                    for (int s = 0; s < 8; s++) {
                        double ang = s * (Math.PI / 4.0) + time * 0.02;
                        double r = 3.2 + (s % 3) * 2.1;
                        double h = 1.1 + 0.5 * Math.sin(time * 0.14 + s);
                        Vec3 base = feet.add(Math.cos(ang) * r, 0.0, Math.sin(ang) * r);
                        emitBeam(buffer, mat, base, base.add(0, h, 0), 0.035, 255, 224, 128, 75, 0);
                    }
                    emitted = true;
                } else if (f.mode == BlockDominionRules.Mode.MANUAL && !f.blocks.isEmpty()) {
                    int count = f.blocks.size();

                    // Pickup golden energy tethers from broken ground up to rising blocks
                    for (VisualBlock vb : f.blocks) {
                        if (vb.pickupOrigin != null) {
                            double age = time - vb.pickupStartTick;
                            if (age >= 0 && age < 10) {
                                int a = (int) (185 * (1.0 - age / 10.0));
                                Vec3 from = vb.pickupOrigin.subtract(camPos);
                                Vec3 to = vb.prevPos.lerp(vb.pos, tickDelta).subtract(camPos);
                                emitBeam(buffer, mat, from, to, 0.055, 255, 226, 126, a, a / 2);
                                emitted = true;
                            }
                        }
                    }

                    if (f.formation == BlockDominionBrain.FormationKind.IDLE) {
                        // Sovereign golden halo tracks scaled to reserve tier
                        Vec3 forward = new Vec3(look.x, 0, look.z);
                        forward = forward.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : forward.normalize();
                        int alpha = firstPerson ? 48 : 88;
                        if (count <= 8) {
                            emitOrientedRing(buffer, mat, chest.add(forward.scale(-0.35)), new Vec3(0, 1, 0), 2.05, 0.035, 235, 198, 78, alpha, 44);
                        } else if (count <= 20) {
                            Vec3 right = new Vec3(-forward.z, 0, forward.x);
                            Vec3 n1 = new Vec3(0, Math.cos(0.44), 0).add(forward.scale(Math.sin(0.44))).normalize();
                            Vec3 n2 = new Vec3(0, Math.cos(0.44), 0).add(forward.scale(-Math.sin(0.44))).normalize();
                            emitOrientedRing(buffer, mat, chest.add(forward.scale(-0.35)).add(0, 0.15, 0), n1, 2.45, 0.038, 235, 198, 78, alpha, 48);
                            emitOrientedRing(buffer, mat, chest.add(forward.scale(-0.35)).add(0, 0.15, 0), n2, 1.95, 0.032, 255, 234, 152, alpha, 44);
                            if (right.lengthSqr() > 0) emitted = true;
                        } else {
                            // Tier 3 (21+): full Sovereign Halo rings behind the player's back
                            Vec3 haloCenter = chest.add(forward.scale(-1.55)).add(0, 0.55, 0);
                            emitOrientedRing(buffer, mat, haloCenter, forward, 2.85, 0.055, 235, 198, 78, alpha + 28, 56);
                            emitOrientedRing(buffer, mat, haloCenter.add(forward.scale(0.18)), forward, 2.15, 0.04, 255, 240, 175, alpha + 18, 48);
                            emitOrientedRing(buffer, mat, chest.add(forward.scale(-0.2)).add(0, -0.15, 0), new Vec3(0, 1, 0), 1.95, 0.03, 235, 198, 78, alpha, 40);
                        }
                        emitted = true;
                    } else if (f.formation == BlockDominionBrain.FormationKind.SHIELD_SINGLE) {
                        emitShieldHexBarrier(buffer, mat, chest, f.threatPrimary, Math.min(2.15, 0.85 + count * 0.065), time, firstPerson);
                        emitted = true;
                    } else if (f.formation == BlockDominionBrain.FormationKind.SHIELD_MULTI) {
                        double r = Math.min(1.75, 0.8 + count * 0.045);
                        emitShieldHexBarrier(buffer, mat, chest, f.threatPrimary, r, time, firstPerson);
                        emitShieldHexBarrier(buffer, mat, chest, f.threatSecondary, r, time, firstPerson);
                        emitted = true;
                    } else if (f.formation == BlockDominionBrain.FormationKind.SHIELD_DOME) {
                        int domeA = firstPerson ? 55 : 105;
                        emitOrientedRing(buffer, mat, chest.add(0, 0.15, 0), new Vec3(0, 1, 0), 2.12, 0.06, 255, 218, 102, domeA, 48);
                        emitOrientedRing(buffer, mat, chest.add(0, 0.15, 0), new Vec3(Math.cos(time * 0.04), 0.3, Math.sin(time * 0.04)), 2.12, 0.05, 255, 236, 156, domeA, 48);
                        emitOrientedRing(buffer, mat, chest.add(0, 0.15, 0), new Vec3(-Math.sin(time * 0.04), 0.3, Math.cos(time * 0.04)), 2.12, 0.05, 235, 198, 78, domeA, 48);
                        emitted = true;
                    } else if (f.formation == BlockDominionBrain.FormationKind.VOLLEY_FAN) {
                        float vp = Mth.clamp(f.volleyTicks / (float) BlockDominionRules.VOLLEY_CHARGE_TICKS, 0f, 1f);
                        Vec3 focal = chest.add(look.normalize().scale(2.1));
                        emitOrientedRing(buffer, mat, focal, look, 0.55 + (1.0 - vp) * 1.4, 0.055, 255, 224, 118, (int) (150 * vp + 35), 36);
                        int step = Math.max(1, count / 10);
                        for (int i = 0; i < count; i += step) {
                            Vec3 bp = f.blocks.get(i).prevPos.lerp(f.blocks.get(i).pos, tickDelta).subtract(camPos);
                            emitBeam(buffer, mat, bp, focal, 0.025 + vp * 0.02, 255, 232, 145, (int) (115 * vp), 20);
                        }
                        emitted = true;
                    }
                }
            }

            // Parry double shockwave + hex flash + returning golden motes (§1.4 #3)
            for (ParryBurst burst : PARRIES) {
                Entity owner = world.getEntity(burst.playerId);
                if (owner == null || owner.distanceToSqr(camPos) > 96.0 * 96.0) continue;
                double age = time - burst.startTick;
                if (age < 0 || age >= 14) continue;
                double t = age / 14.0;
                Vec3 chest = owner.getPosition(tickDelta).add(0, 1.15, 0).subtract(camPos);
                Vec3 impact = chest.add(burst.direction.scale(1.32));
                int a1 = (int) (230 * (1.0 - t) * (1.0 - t));
                emitOrientedRing(buffer, mat, impact, burst.direction, 0.35 + t * 2.25, 0.16 * (1.0 - t * 0.4), 255, 226, 128, a1, 6);
                if (age >= 2) {
                    double t2 = (age - 2) / 12.0;
                    int a2 = (int) (180 * (1.0 - t2) * (1.0 - t2));
                    emitOrientedRing(buffer, mat, impact, burst.direction, 0.25 + t2 * 1.75, 0.09, 255, 248, 210, a2, 24);
                }
                // Returning dust/energy streaks curving back toward player
                Vec3 side = burst.direction.cross(Math.abs(burst.direction.y) < 0.92 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
                Vec3 up = burst.direction.cross(side).normalize();
                for (int m = 0; m < 6; m++) {
                    double ang = m * (Math.PI / 3.0) + t * 1.4;
                    Vec3 flare = impact.add(side.scale(Math.cos(ang) * (0.9 * (1.0 - t)))).add(up.scale(Math.sin(ang) * (0.9 * (1.0 - t))));
                    Vec3 head = flare.lerp(chest, t * t);
                    Vec3 tail = flare.lerp(chest, Math.max(0.0, t * t - 0.28));
                    emitBeam(buffer, mat, tail, head, 0.032, 255, 230, 140, (int) (190 * (1.0 - t)), 0);
                }
                emitted = true;
            }

            // Unfold & Volley-release expanding pressure waves
            for (WaveBurst w : WAVES) {
                Entity owner = world.getEntity(w.playerId);
                if (owner == null || owner.distanceToSqr(camPos) > 96.0 * 96.0) continue;
                double age = time - w.startTick;
                if (age < 0 || age >= w.duration) continue;
                double t = age / w.duration;
                double ease = 1.0 - (1.0 - t) * (1.0 - t);
                Vec3 origin = owner.getPosition(tickDelta).add(0, 1.25, 0).subtract(camPos);
                emitOrientedRing(buffer, mat, origin, w.axis, 0.4 + ease * w.maxRadius, 0.18 * (1.0 - t * 0.5),
                        255, 224, 122, (int) (210 * (1.0 - t) * (1.0 - t)), 40);
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

    private static void emitShieldHexBarrier(VertexConsumer v, Matrix4f m, Vec3 chest, Vec3 threatDir,
                                             double radius, double time, boolean firstPerson) {
        if (threatDir == null || threatDir.lengthSqr() < 1e-4) return;
        Vec3 n = threatDir.normalize();
        Vec3 center = chest.add(n.scale(1.55));
        int baseA = firstPerson ? 72 : 138;
        double pulse = 0.85 + 0.15 * Math.sin(time * 0.25);
        emitOrientedRing(v, m, center, n, radius, 0.075, 255, 214, 96, (int) (baseA * pulse), 6);
        emitOrientedRing(v, m, center.add(n.scale(0.06)), n, radius * 0.62, 0.045, 255, 240, 178, (int) (baseA * 0.8 * pulse), 6);
    }

    private static void emitOrientedRing(VertexConsumer v, Matrix4f m, Vec3 center, Vec3 normal, double radius,
                                         double halfWidth, int r, int g, int b, int a, int segs) {
        if (a <= 2) return;
        Vec3 n = normal.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : normal.normalize();
        Vec3 side = n.cross(Math.abs(n.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 up = n.cross(side).normalize();
        double rIn = Math.max(0.04, radius - halfWidth), rOut = radius + halfWidth;
        for (int i = 0; i < segs; i++) {
            double a1 = Math.PI * 2.0 * i / segs;
            double a2 = Math.PI * 2.0 * (i + 1) / segs;
            Vec3 d1 = side.scale(Math.cos(a1)).add(up.scale(Math.sin(a1)));
            Vec3 d2 = side.scale(Math.cos(a2)).add(up.scale(Math.sin(a2)));
            Vec3 p0 = center.add(d1.scale(rIn));
            Vec3 p1 = center.add(d2.scale(rIn));
            Vec3 p2 = center.add(d2.scale(rOut));
            Vec3 p3 = center.add(d1.scale(rOut));
            v.addVertex(m, (float) p0.x, (float) p0.y, (float) p0.z).setColor(r, g, b, a);
            v.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setColor(r, g, b, a);
            v.addVertex(m, (float) p2.x, (float) p2.y, (float) p2.z).setColor(r, g, b, 0);
            v.addVertex(m, (float) p3.x, (float) p3.y, (float) p3.z).setColor(r, g, b, 0);
        }
    }

    private static void emitBeam(VertexConsumer v, Matrix4f m, Vec3 from, Vec3 to, double halfWidth,
                                 int r, int g, int b, int aFrom, int aTo) {
        Vec3 axis = to.subtract(from);
        if (axis.lengthSqr() < 1e-6) return;
        Vec3 dir = axis.normalize();
        Vec3 side1 = dir.cross(Math.abs(dir.y) < 0.92 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize().scale(halfWidth);
        Vec3 side2 = dir.cross(side1).normalize().scale(halfWidth);
        for (Vec3 s : new Vec3[]{side1, side2}) {
            Vec3 p0 = from.subtract(s), p1 = from.add(s), p2 = to.add(s), p3 = to.subtract(s);
            v.addVertex(m, (float) p0.x, (float) p0.y, (float) p0.z).setColor(r, g, b, aFrom);
            v.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setColor(r, g, b, aFrom);
            v.addVertex(m, (float) p2.x, (float) p2.y, (float) p2.z).setColor(r, g, b, aTo);
            v.addVertex(m, (float) p3.x, (float) p3.y, (float) p3.z).setColor(r, g, b, aTo);
        }
    }
}
