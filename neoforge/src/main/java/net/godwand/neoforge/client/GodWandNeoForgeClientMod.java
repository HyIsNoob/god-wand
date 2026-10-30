package net.godwand.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.godwand.GodWandMod;
import net.godwand.client.ArcaneOrbChargeRenderer;
import net.godwand.client.ArcaneOrbEntityRenderer;
import net.godwand.client.CannonPressureRenderer;
import net.godwand.client.CustomImpactFrameOverlay;
import net.godwand.client.AtomicRenderer;
import net.godwand.client.AtomicCinematic;
import net.godwand.client.AtomicPostFx;
import net.godwand.network.AtomicFxPayload;
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
import net.godwand.neoforge.GodWandNeoForgeMod;
import net.godwand.network.CannonPressurePayload;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.DrillWindPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.network.SingularityThreadsPayload;
import net.godwand.network.SpatialAuraPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class GodWandNeoForgeClientMod {

    static {
        GodWandConfig.loadClient(FMLPaths.CONFIGDIR.get());
        net.neoforged.fml.ModLoadingContext.get().registerExtensionPoint(
                net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                () -> (container, parent) -> new net.godwand.client.gui.GodWandConfigScreen(parent)
        );
    }

    public static void registerClientPayloads(PayloadRegistrar registrar) {
        registrar.playToClient(net.godwand.network.SkillCooldownPayload.ID,net.godwand.network.SkillCooldownPayload.CODEC,
                (payload,context)->context.enqueueWork(()->net.godwand.client.SkillCooldownClient.receive(payload)));
        registrar.playToClient(AtomicFxPayload.ID, AtomicFxPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> AtomicRenderer.receive(payload)));
        registrar.playToClient(
                DrillWindPayload.ID,
                DrillWindPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> DrillWindRenderer.receive(payload))
        );
        registrar.playToClient(
                SpatialAuraPayload.ID,
                SpatialAuraPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> SpatialAuraRenderer.receive(payload))
        );
        registrar.playToClient(
                CannonPressurePayload.ID,
                CannonPressurePayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    CannonPressureRenderer.receive(payload);
                    ImpactDebrisRenderer.receive(payload);
                })
        );
        registrar.playToClient(
                SingularityThreadsPayload.ID,
                SingularityThreadsPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> SingularityThreadRenderer.receive(payload))
        );
        registrar.playToClient(
                ImpactFxPayload.ID,
                ImpactFxPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
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
                })
        );
        registrar.playToClient(
                CinematicFxPayload.ID,
                CinematicFxPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> GodWandCinematicEngine.onCinematicEvent(payload))
        );
    }

    @EventBusSubscriber(modid = GodWandMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(SkillWheelScreen.OPEN);
            event.register(SkillWheelScreen.OPEN_CONFIG);
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(GodWandMod.ARCANE_ORB_ENTITY_TYPE, ArcaneOrbEntityRenderer::new);
            event.registerEntityRenderer(GodWandMod.STONE_CANNON_ENTITY_TYPE, StoneCannonEntityRenderer::new);
        }

        @SubscribeEvent
        public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
                GodWandPostFxManager.reloadShaders(); AtomicPostFx.reload();
            });
        }

        @SubscribeEvent
        public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
            event.registerItem(new IClientItemExtensions() {
                private BlockEntityWithoutLevelRenderer customRenderer;

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (customRenderer == null) {
                        customRenderer = new BlockEntityWithoutLevelRenderer(
                                Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                                Minecraft.getInstance().getEntityModels()) {
                            private final GodWandItemRenderer itemRenderer = new GodWandItemRenderer();

                            @Override
                            public void renderByItem(ItemStack stack, ItemDisplayContext mode, PoseStack matrices,
                                                    MultiBufferSource vertexConsumers, int light, int overlay) {
                                itemRenderer.render(stack, mode, matrices, vertexConsumers, light, overlay);
                            }
                        };
                    }
                    return customRenderer;
                }
            }, GodWandNeoForgeMod.GOD_WAND.get());
        }
    }

    @EventBusSubscriber(modid = GodWandMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static class GameBusEvents {
        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                PoseStack matrices = event.getPoseStack();
                Camera camera = event.getCamera();
                float tickDelta = event.getPartialTick().getGameTimeDeltaPartialTick(false);
                CannonPressureRenderer.render(matrices, camera, tickDelta);
                DrillWindRenderer.render(matrices, camera, tickDelta);
                ImpactDebrisRenderer.render(matrices, camera, tickDelta);
                SingularityThreadRenderer.render(matrices, camera, tickDelta);
                SpatialAuraRenderer.render(matrices, camera, tickDelta);
                StoneCannonChargeRenderer.render(matrices, camera, tickDelta);
                ArcaneOrbChargeRenderer.render(matrices, camera, tickDelta);
                SupernovaAccretionRenderer.render(matrices, camera, tickDelta);
                AtomicRenderer.render(matrices, camera, tickDelta);
            } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
                float tickDelta = event.getPartialTick().getGameTimeDeltaPartialTick(false);
                GodWandPostFxManager.onWorldRender(event.getModelViewMatrix(), event.getProjectionMatrix(), tickDelta);
                AtomicPostFx.capture(event.getModelViewMatrix(), event.getProjectionMatrix(), tickDelta);
            }
        }

        @SubscribeEvent
        public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
            if (VanillaGuiLayers.CAMERA_OVERLAYS.equals(event.getName())) {
                CustomImpactFrameOverlay.render(event.getGuiGraphics(), event.getPartialTick());
                AtomicCinematic.render(event.getGuiGraphics(), event.getPartialTick());
                net.godwand.client.SkillCooldownClient.render(event.getGuiGraphics());
            }
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
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
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
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
        }
    }
}
