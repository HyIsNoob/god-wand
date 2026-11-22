package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.godwand.network.BlockDominionStatePayload;
import net.godwand.skill.BlockDominionRules;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
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

import java.util.*;

/**
 * Client-side formation animator and block renderer for Block Dominion (mode 9).
 * Renders real BlockStates with per-frame spring interpolation, stowed shoulder cluster,
 * smooth unfold, volley fan, parry bursts and Auto-mode gravity field pebbles.
 */
public final class BlockDominionRenderer {
    private static final class VisualBlock {
        BlockState state;
        Vec3 pos;
        Vec3 prevPos;
        float scale;
        float prevScale;
        float spinPhase;

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

    private static final class ClientFormation {
        BlockDominionRules.Mode mode = BlockDominionRules.Mode.OFF;
        boolean stowed;
        boolean volleyCharging;
        boolean hiddenInCutscene;
        int volleyTicks;
        long unfoldStartTick = -1000;
        long lastUpdateTick;
        final List<VisualBlock> blocks = new ArrayList<>();
    }

    private static final Map<Integer, ClientFormation> FORMATIONS = new HashMap<>();
    private static final List<ParryBurst> PARRIES = new ArrayList<>();
    private static ClientLevel world;

    private BlockDominionRenderer() {}

    public static void reset() {
        FORMATIONS.clear();
        PARRIES.clear();
        world = null;
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
            return;
        }

        ClientFormation f = FORMATIONS.computeIfAbsent(payload.playerId(), k -> new ClientFormation());
        boolean wasStowed = f.stowed;
        f.mode = mode;
        f.stowed = (payload.flags() & BlockDominionRules.FLAG_STOWED) != 0;
        f.volleyCharging = (payload.flags() & BlockDominionRules.FLAG_VOLLEY_CHARGING) != 0;
        f.hiddenInCutscene = (payload.flags() & BlockDominionRules.FLAG_HIDDEN) != 0;
        f.volleyTicks = payload.volleyTicks();
        f.lastUpdateTick = now;

        if (payload.eventKind() == BlockDominionRules.EVENT_UNFOLD || (wasStowed && !f.stowed)) {
            f.unfoldStartTick = now;
        }

        Entity owner = world.getEntity(payload.playerId());
        Vec3 defaultAnchor = owner != null ? owner.position().add(0, 1.25, 0) : new Vec3(payload.eventX(), payload.eventY(), payload.eventZ());
        int[] ids = payload.blockStateIds();
        int oldSize = f.blocks.size();

        // Sync visual block list to exact server reserve, preserving existing spring positions smoothly
        while (f.blocks.size() > ids.length) {
            f.blocks.remove(f.blocks.size() - 1);
        }
        for (int i = 0; i < f.blocks.size(); i++) {
            f.blocks.get(i).state = decodeState(ids[i]);
        }
        for (int i = f.blocks.size(); i < ids.length; i++) {
            BlockState st = decodeState(ids[i]);
            Vec3 spawnPos = (payload.eventKind() == BlockDominionRules.EVENT_PICKUP && i >= oldSize)
                    ? new Vec3(payload.eventX(), payload.eventY(), payload.eventZ())
                    : defaultAnchor;
            float startScale = payload.eventKind() == BlockDominionRules.EVENT_PICKUP ? 0.95f : 0.25f;
            f.blocks.add(new VisualBlock(st, spawnPos, startScale, (i * 1.37f) % 6.283f));
        }

        if (payload.eventKind() == BlockDominionRules.EVENT_PARRY) {
            if (PARRIES.size() >= 16) PARRIES.remove(0);
            PARRIES.add(new ParryBurst(payload.playerId(),
                    new Vec3(payload.eventX(), payload.eventY(), payload.eventZ()),
                    decodeState(payload.eventBlockId()), now));
        }
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

        FORMATIONS.entrySet().removeIf(entry -> now - entry.getValue().lastUpdateTick > 100);
        PARRIES.removeIf(p -> now - p.startTick > 12);

        for (var entry : FORMATIONS.entrySet()) {
            Entity owner = world.getEntity(entry.getKey());
            if (owner == null) continue;
            ClientFormation f = entry.getValue();
            if (f.volleyCharging) f.volleyTicks++;
            Vec3 chest = owner.position().add(0, 1.25, 0);
            Vec3 look = owner.getViewVector(1.0f);
            float unfoldProgress = Mth.clamp((now - f.unfoldStartTick) / (float) BlockDominionRules.UNFOLD_TICKS, 0f, 1f);
            float volleyProgress = Mth.clamp(f.volleyTicks / (float) BlockDominionRules.VOLLEY_CHARGE_TICKS, 0f, 1f);
            float targetScale = BlockDominionRules.blockScale(f.stowed, unfoldProgress, f.volleyCharging, volleyProgress);

            int count = f.blocks.size();
            for (int i = 0; i < count; i++) {
                VisualBlock vb = f.blocks.get(i);
                vb.prevPos = vb.pos;
                vb.prevScale = vb.scale;
                Vec3 offset = BlockDominionRules.formationOffset(
                        i, count, now, look, f.stowed, unfoldProgress, f.volleyCharging, volleyProgress);
                Vec3 target = chest.add(offset);
                double lerpRate = f.stowed ? 0.42 : (f.volleyCharging ? 0.36 : 0.30);
                if (vb.pos.distanceToSqr(target) > 64.0 * 64.0) {
                    vb.pos = target;
                    vb.prevPos = target;
                } else {
                    vb.pos = vb.pos.lerp(target, lerpRate);
                }
                vb.scale = Mth.lerp(0.32f, vb.scale, targetScale);
                vb.spinPhase += f.stowed ? 0.08f : (f.volleyCharging ? 0.14f : 0.045f);
            }
        }
    }

    public static void render(PoseStack matrices, Camera camera, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;
        if (client.level != world) {
            reset();
            world = client.level;
        }
        if (FORMATIONS.isEmpty() && PARRIES.isEmpty()) return;

        Vec3 camPos = camera.getPosition();
        double time = world.getGameTime() + tickDelta;
        boolean localCutscene = AtomicCinematic.active() || FugaCinematic.active()
                || SilentGateCinematic.active() || PurpleCinematic.active();
        BlockRenderDispatcher blockRenderer = client.getBlockRenderer();
        MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        // 1. Render solid orbiting / stowed / volley blocks & Auto-mode levitating terrain pebbles
        for (var entry : FORMATIONS.entrySet()) {
            Entity owner = world.getEntity(entry.getKey());
            if (owner == null || owner.distanceToSqr(camPos) > 96.0 * 96.0) continue;
            ClientFormation f = entry.getValue();
            boolean isLocal = client.player != null && owner.getId() == client.player.getId();
            if (f.hiddenInCutscene || (isLocal && localCutscene)) continue;
            boolean firstPerson = isLocal && !camera.isDetached();

            if (f.mode == BlockDominionRules.Mode.MANUAL) {
                for (int i = 0; i < f.blocks.size(); i++) {
                    VisualBlock vb = f.blocks.get(i);
                    Vec3 renderWorld = vb.prevPos.lerp(vb.pos, tickDelta);
                    float scale = Mth.lerp(tickDelta, vb.prevScale, vb.scale);
                    if (firstPerson && renderWorld.distanceToSqr(camPos) < 0.72 * 0.72) continue;

                    BlockPos lightPos = BlockPos.containing(renderWorld);
                    if (world.getBlockState(lightPos).isSolidRender(world, lightPos)) lightPos = owner.blockPosition().above();
                    int light = LevelRenderer.getLightColor(world, lightPos);
                    matrices.pushPose();
                    matrices.translate(renderWorld.x - camPos.x, renderWorld.y - camPos.y, renderWorld.z - camPos.z);
                    float spin = (vb.spinPhase + tickDelta * 0.045f) * (180f / (float) Math.PI);
                    matrices.mulPose(Axis.YP.rotationDegrees(spin));
                    matrices.mulPose(Axis.XP.rotationDegrees(spin * 0.65f + i * 19f));
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
            float scale = (1.0f - t * t) * 0.92f;
            if (scale <= 0.05f) continue;
            Vec3 chest = owner.getPosition(tickDelta).add(0, 1.15, 0);
            Vec3 pos = chest.add(burst.direction.scale(1.15 + t * 0.45));
            BlockPos lightPos = BlockPos.containing(pos);
            if (world.getBlockState(lightPos).isSolidRender(world, lightPos)) lightPos = owner.blockPosition().above();
            int light = LevelRenderer.getLightColor(world, lightPos);
            matrices.pushPose();
            matrices.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
            matrices.mulPose(Axis.YP.rotationDegrees(age * 28f));
            matrices.mulPose(Axis.ZP.rotationDegrees(age * 21f));
            matrices.scale(scale, scale, scale);
            matrices.translate(-0.5, -0.5, -0.5);
            blockRenderer.renderSingleBlock(burst.state, matrices, bufferSource, light, OverlayTexture.NO_OVERLAY);
            matrices.popPose();
        }

        bufferSource.endBatch();

        // 3. Translucent aura & parry shockwave rings (reuses same lightweight POSITION_COLOR pass as SpatialAuraRenderer)
        renderRings(matrices, camPos, time, tickDelta, client, localCutscene);
    }

    /** Auto mode (§1.4 #5): nearby terrain stones hover half a block off the ground in a ~10-block gravity field. */
    private static void renderAutoGravityPebbles(PoseStack matrices, MultiBufferSource.BufferSource bufferSource,
                                                 BlockRenderDispatcher blockRenderer, Entity owner,
                                                 Vec3 camPos, double time, float tickDelta, boolean firstPerson) {
        Vec3 base = owner.getPosition(tickDelta);
        int pebbleCount = 14;
        for (int i = 0; i < pebbleCount; i++) {
            double angle = (Math.PI * 2.0 * i / pebbleCount) + time * 0.015 * ((i & 1) == 0 ? 1 : -1);
            double dist = 2.6 + (i % 4) * 1.85;
            double wx = base.x + Math.cos(angle) * dist;
            double wz = base.z + Math.sin(angle) * dist;
            int bx = Mth.floor(wx), bz = Mth.floor(wz);
            if (!world.getChunkSource().hasChunk(bx >> 4, bz >> 4)) continue;
            int groundY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            if (Math.abs(groundY - base.y) > 6) continue;
            BlockPos samplePos = new BlockPos(bx, groundY - 1, bz);
            BlockState ground = world.getBlockState(samplePos);
            if (ground.isAir() || !ground.getFluidState().isEmpty()) ground = Blocks.STONE.defaultBlockState();
            double hoverY = groundY + 0.42 + Math.sin(time * 0.11 + i * 1.7) * 0.16;
            Vec3 p = new Vec3(wx, hoverY, wz);
            if (firstPerson && p.distanceToSqr(camPos) < 1.2 * 1.2) continue;

            float scale = 0.24f + (i % 3) * 0.06f;
            int light = LevelRenderer.getLightColor(world, BlockPos.containing(p));
            matrices.pushPose();
            matrices.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
            matrices.mulPose(Axis.YP.rotationDegrees((float) (time * 3.5 + i * 37)));
            matrices.mulPose(Axis.XP.rotationDegrees((float) (time * 2.2 + i * 23)));
            matrices.scale(scale, scale, scale);
            matrices.translate(-0.5, -0.5, -0.5);
            blockRenderer.renderSingleBlock(ground, matrices, bufferSource, light, OverlayTexture.NO_OVERLAY);
            matrices.popPose();
        }
    }

    private static void renderRings(PoseStack matrices, Vec3 camPos, double time, float tickDelta,
                                    Minecraft client, boolean localCutscene) {
        boolean hasAuto = false;
        for (ClientFormation f : FORMATIONS.values()) {
            if (f.mode == BlockDominionRules.Mode.AUTO && !f.hiddenInCutscene) {
                hasAuto = true;
                break;
            }
        }
        if (!hasAuto && PARRIES.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
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
                if (f.mode != BlockDominionRules.Mode.AUTO || f.hiddenInCutscene) continue;
                Entity owner = world.getEntity(entry.getKey());
                if (owner == null || owner.distanceToSqr(camPos) > 96.0 * 96.0) continue;
                if (client.player != null && owner.getId() == client.player.getId() && localCutscene) continue;
                Vec3 center = owner.getPosition(tickDelta).add(0, 0.18, 0).subtract(camPos);
                emitFlatRing(buffer, mat, center, BlockDominionRules.AUTO_SCAN_RADIUS - 0.5, 0.16, 235, 198, 78, 85);
                double pulseR = 2.0 + ((time * 0.18) % (BlockDominionRules.AUTO_SCAN_RADIUS - 2.0));
                int pulseA = (int) (110 * (1.0 - pulseR / BlockDominionRules.AUTO_SCAN_RADIUS));
                emitFlatRing(buffer, mat, center, pulseR, 0.12, 255, 224, 130, pulseA);
                emitted = true;
            }

            for (ParryBurst burst : PARRIES) {
                Entity owner = world.getEntity(burst.playerId);
                if (owner == null || owner.distanceToSqr(camPos) > 96.0 * 96.0) continue;
                double age = time - burst.startTick;
                if (age < 0 || age >= 12) continue;
                double t = age / 12.0;
                Vec3 center = owner.getPosition(tickDelta).add(0, 1.15, 0).add(burst.direction.scale(1.3)).subtract(camPos);
                emitOrientedRing(buffer, mat, center, burst.direction, 0.4 + t * 1.9, 0.14 * (1.0 - t * 0.5),
                        255, 218, 115, (int) (210 * (1.0 - t) * (1.0 - t)));
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

    private static void emitFlatRing(VertexConsumer v, Matrix4f m, Vec3 center, double radius, double halfWidth,
                                     int r, int g, int b, int a) {
        if (a <= 2) return;
        int segs = 48;
        for (int i = 0; i < segs; i++) {
            double a1 = Math.PI * 2.0 * i / segs;
            double a2 = Math.PI * 2.0 * (i + 1) / segs;
            double c1 = Math.cos(a1), s1 = Math.sin(a1), c2 = Math.cos(a2), s2 = Math.sin(a2);
            double rIn = Math.max(0.05, radius - halfWidth), rOut = radius + halfWidth;
            v.addVertex(m, (float) (center.x + c1 * rIn), (float) center.y, (float) (center.z + s1 * rIn)).setColor(r, g, b, a);
            v.addVertex(m, (float) (center.x + c2 * rIn), (float) center.y, (float) (center.z + s2 * rIn)).setColor(r, g, b, a);
            v.addVertex(m, (float) (center.x + c2 * rOut), (float) center.y, (float) (center.z + s2 * rOut)).setColor(r, g, b, 0);
            v.addVertex(m, (float) (center.x + c1 * rOut), (float) center.y, (float) (center.z + s1 * rOut)).setColor(r, g, b, 0);
        }
    }

    private static void emitOrientedRing(VertexConsumer v, Matrix4f m, Vec3 center, Vec3 normal, double radius,
                                         double halfWidth, int r, int g, int b, int a) {
        if (a <= 2) return;
        Vec3 n = normal.normalize();
        Vec3 side = n.cross(Math.abs(n.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 up = n.cross(side).normalize();
        int segs = 32;
        for (int i = 0; i < segs; i++) {
            double a1 = Math.PI * 2.0 * i / segs;
            double a2 = Math.PI * 2.0 * (i + 1) / segs;
            Vec3 d1 = side.scale(Math.cos(a1)).add(up.scale(Math.sin(a1)));
            Vec3 d2 = side.scale(Math.cos(a2)).add(up.scale(Math.sin(a2)));
            double rIn = Math.max(0.05, radius - halfWidth), rOut = radius + halfWidth;
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
}
