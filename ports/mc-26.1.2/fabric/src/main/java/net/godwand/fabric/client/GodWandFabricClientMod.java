package net.godwand.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.godwand.GodWandMod;
import net.godwand.client.AtomicRenderer;
import net.godwand.client.AtomicCinematic;
import net.godwand.client.AtomicPostFx;
import net.godwand.network.AtomicFxPayload;
import net.godwand.client.ArcaneOrbChargeRenderer;
import net.godwand.client.ArcaneOrbEntityRenderer;
import net.godwand.client.CannonPressureRenderer;
import net.godwand.client.CustomImpactFrameOverlay;
import net.godwand.client.DrillWindRenderer;
import net.godwand.client.EarthDrillSoundscape;
import net.godwand.client.GodWandCinematicEngine;
import net.godwand.client.GodWandPostFxManager;
import net.godwand.client.ImpactDebrisRenderer;
import net.godwand.client.SingularityThreadRenderer;
import net.godwand.client.SkillWheelScreen;
import net.godwand.client.SpatialAuraRenderer;
import net.godwand.client.StoneCannonChargeRenderer;
import net.godwand.client.StoneCannonEntityRenderer;
import net.godwand.client.StoneCannonWindSound;
import net.godwand.client.SupernovaAccretionRenderer;
import net.godwand.client.SupernovaSoundscape;
import net.godwand.client.render.GodWandItemRenderer;
import net.godwand.config.GodWandConfig;
import net.godwand.item.GodWandItem;
import net.godwand.network.CannonPressurePayload;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.DrillWindPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.network.SingularityThreadsPayload;
import net.godwand.network.SpatialAuraPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class GodWandFabricClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GodWandConfig.loadClient(FabricLoader.getInstance().getConfigDir());
        net.godwand.client.compat.GodWandGeometry.initialize();
        net.minecraft.client.renderer.item.ItemModels.ID_MAPPER.put(Identifier.fromNamespaceAndPath("godwand","wand"),net.godwand.client.render.GodWandItemModel.Unbaked.CODEC);

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.fromNamespaceAndPath(GodWandMod.MOD_ID, "post_effects");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager resourceManager) {
                        GodWandPostFxManager.reloadShaders();
                        AtomicPostFx.reload();
                    }
                });

        KeyMappingHelper.registerKeyMapping(SkillWheelScreen.OPEN);
        KeyMappingHelper.registerKeyMapping(SkillWheelScreen.OPEN_CONFIG);
        EntityRendererRegistry.register(GodWandMod.ARCANE_ORB_ENTITY_TYPE, ArcaneOrbEntityRenderer::new);
        EntityRendererRegistry.register(GodWandMod.STONE_CANNON_ENTITY_TYPE, StoneCannonEntityRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(DrillWindPayload.ID, (payload, context) ->
                context.client().execute(() -> DrillWindRenderer.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(net.godwand.network.SkillCooldownPayload.ID,(payload,context) ->
                context.client().execute(() -> net.godwand.client.SkillCooldownClient.receive(payload)));

        ClientPlayNetworking.registerGlobalReceiver(AtomicFxPayload.ID, (payload, context) ->
                context.client().execute(() -> AtomicRenderer.receive(payload)));

        ClientPlayNetworking.registerGlobalReceiver(SpatialAuraPayload.ID, (payload, context) ->
                context.client().execute(() -> SpatialAuraRenderer.receive(payload)));

        ClientPlayNetworking.registerGlobalReceiver(CannonPressurePayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    CannonPressureRenderer.receive(payload);
                    ImpactDebrisRenderer.receive(payload);
                }));

        ClientPlayNetworking.registerGlobalReceiver(SingularityThreadsPayload.ID, (payload, context) ->
                context.client().execute(() -> SingularityThreadRenderer.receive(payload)));

        ClientPlayNetworking.registerGlobalReceiver(ImpactFxPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (payload.fxType() == ImpactFxPayload.TYPE_BLACK_HOLE) {
                        SupernovaSoundscape.start(new Vec3(payload.x(), payload.y(), payload.z()));
                    }
                    if (payload.fxType() == ImpactFxPayload.TYPE_GRAVITY_CORE) {
                        EarthDrillSoundscape.start(new Vec3(payload.x(), payload.y(), payload.z()));
                    }
                    SupernovaAccretionRenderer.receive(payload);
                    ImpactDebrisRenderer.receive(payload);
                    GodWandPostFxManager.triggerImpact(
                            payload.x(), payload.y(), payload.z(),
                            payload.fxType(), payload.durationTicks(), payload.intensity()
                    );
                }));

        ClientPlayNetworking.registerGlobalReceiver(CinematicFxPayload.ID, (payload, context) ->
                context.client().execute(() -> GodWandCinematicEngine.onCinematicEvent(payload)));

        // Uploads and GPU fences require the terrain render pass to be closed.
        LevelRenderEvents.END_MAIN.register(context -> {
            PoseStack matrices = context.poseStack();
            Camera camera = context.gameRenderer().getMainCamera();
            float tickDelta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            CannonPressureRenderer.render(matrices, camera, tickDelta);
            DrillWindRenderer.render(matrices, camera, tickDelta);
            ImpactDebrisRenderer.render(matrices, camera, tickDelta);
            SingularityThreadRenderer.render(matrices, camera, tickDelta);
            SpatialAuraRenderer.render(matrices, camera, tickDelta);
            StoneCannonChargeRenderer.render(matrices, camera, tickDelta);
            ArcaneOrbChargeRenderer.render(matrices, camera, tickDelta);
            SupernovaAccretionRenderer.render(matrices, camera, tickDelta);
            AtomicRenderer.render(matrices, camera, tickDelta);
            net.godwand.client.compat.GodWandGeometry.flush();
        });

        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, hitResult) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                ItemStack main = client.player.getMainHandItem();
                ItemStack off = client.player.getOffhandItem();
                ItemStack stack = main.getItem() instanceof GodWandItem ? main : (off.getItem() instanceof GodWandItem ? off : null);
                if (stack != null && GodWandItem.getSkillMode(stack) == GodWandItem.MODE_SUPERNOVA) {
                    return false;
                }
            }
            return true;
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("godwand","impact"),CustomImpactFrameOverlay::render);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("godwand","atomic"),AtomicCinematic::render);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("godwand","cooldown"),(graphics,timer)->net.godwand.client.SkillCooldownClient.render(graphics));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SkillWheelScreen.handleKeyInput(client);
            AtomicRenderer.tick(client);
            net.godwand.client.SkillCooldownClient.tick(client);
            SupernovaAccretionRenderer.tickCharge(client);
            StoneCannonWindSound.update(client);
            EarthDrillSoundscape.tick(client);
            GodWandItemRenderer.tick(client);
            GodWandPostFxManager.onClientTick();
            GodWandCinematicEngine.onClientTick();
            CustomImpactFrameOverlay.onClientTick();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            net.godwand.client.compat.GodWandGeometry.reset();
            AtomicRenderer.reset();
            DrillWindRenderer.reset();
            CannonPressureRenderer.reset();
            SingularityThreadRenderer.reset();
            SpatialAuraRenderer.reset();
            StoneCannonWindSound.reset();
            EarthDrillSoundscape.reset();
            SupernovaAccretionRenderer.reset();
            ImpactDebrisRenderer.reset();
            GodWandPostFxManager.reset();
            GodWandCinematicEngine.reset();
            CustomImpactFrameOverlay.reset();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            net.godwand.client.compat.GodWandGeometry.reset();
            AtomicRenderer.reset();
            StoneCannonWindSound.reset();
            EarthDrillSoundscape.reset();
            SupernovaAccretionRenderer.reset();
            ImpactDebrisRenderer.reset();
            GodWandPostFxManager.reset();
            GodWandCinematicEngine.reset();
            CustomImpactFrameOverlay.reset();
        });
    }
}
