package net.godwand.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.godwand.GodWandMod;
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
import net.godwand.client.render.GodWandItemRenderer;
import net.godwand.config.GodWandConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;

public class GodWandForgeClientMod {
    public static void init(IEventBus modBus) {
        GodWandConfig.loadClient(FMLPaths.CONFIGDIR.get());
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
                net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> new net.godwand.client.gui.GodWandConfigScreen(parent)
                )
        );
        modBus.register(ModBusEvents.class);
        MinecraftForge.EVENT_BUS.register(GameBusEvents.class);
    }

    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(SkillWheelScreen.OPEN);
            event.register(SkillWheelScreen.OPEN_CONFIG);
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            GodWandMod.ARCANE_ORB_ENTITY_TYPE = net.godwand.forge.GodWandForgeMod.ARCANE_ORB.get();
            GodWandMod.STONE_CANNON_ENTITY_TYPE = net.godwand.forge.GodWandForgeMod.STONE_CANNON.get();
            event.registerEntityRenderer(net.godwand.forge.GodWandForgeMod.ARCANE_ORB.get(), ArcaneOrbEntityRenderer::new);
            event.registerEntityRenderer(net.godwand.forge.GodWandForgeMod.STONE_CANNON.get(), StoneCannonEntityRenderer::new);
        }

        @SubscribeEvent
        public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> { GodWandPostFxManager.reloadShaders(); net.godwand.client.AtomicPostFx.reload(); });
        }

        @SubscribeEvent
        public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("atomic_cinematic", (gui, graphics, partialTick, width, height) -> {net.godwand.client.AtomicCinematic.render(graphics, partialTick);net.godwand.client.SkillCooldownClient.render(graphics);});
            event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), "impact_frame",
                    (gui, guiGraphics, partialTick, screenWidth, screenHeight) ->
                            CustomImpactFrameOverlay.render(guiGraphics, partialTick));
        }
    }

    public static class GameBusEvents {
        @SubscribeEvent
        public static void onGuiOverlay(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) {
            if(net.godwand.client.AtomicCinematic.active() && event.getOverlay().id().getNamespace().equals("minecraft"))event.setCanceled(true);
        }

        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                PoseStack matrices = event.getPoseStack();
                Camera camera = event.getCamera();
                float tickDelta = event.getPartialTick();
                net.godwand.client.AtomicRenderer.render(matrices, camera, tickDelta);
                net.godwand.client.AtomicPostFx.capture(matrices.last().pose(), event.getProjectionMatrix(), tickDelta);
                CannonPressureRenderer.render(matrices, camera, tickDelta);
                DrillWindRenderer.render(matrices, camera, tickDelta);
                ImpactDebrisRenderer.render(matrices, camera, tickDelta);
                SingularityThreadRenderer.render(matrices, camera, tickDelta);
                SpatialAuraRenderer.render(matrices, camera, tickDelta);
                StoneCannonChargeRenderer.render(matrices, camera, tickDelta);
                ArcaneOrbChargeRenderer.render(matrices, camera, tickDelta);
                SupernovaAccretionRenderer.render(matrices, camera, tickDelta);
                GodWandPostFxManager.onWorldRender(matrices.last().pose(), event.getProjectionMatrix(), tickDelta);
            } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
                GodWandPostFxManager.renderAfterWorld();
                net.godwand.client.AtomicPostFx.renderAfterWorld();
            }
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft client = Minecraft.getInstance();
            net.godwand.client.AtomicRenderer.tick(client);
            net.godwand.client.SkillCooldownClient.tick(client);
            SkillWheelScreen.handleKeyInput(client);
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
            net.godwand.client.AtomicRenderer.reset();
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
