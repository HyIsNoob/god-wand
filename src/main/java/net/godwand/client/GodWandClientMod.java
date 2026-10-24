package net.godwand.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.godwand.client.render.GodWandItemRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.godwand.GodWandMod;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.ImpactFxPayload;

public class GodWandClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        net.godwand.config.GodWandConfig.loadClient(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    public net.minecraft.util.Identifier getFabricId() { return net.minecraft.util.Identifier.of("godwand", "post_effects"); }
                    public void reload(net.minecraft.resource.ResourceManager manager) { GodWandPostFxManager.reloadShaders(); }
                });
        SkillWheelScreen.register();
        BuiltinItemRendererRegistry.INSTANCE.register(GodWandMod.GOD_WAND_ITEM, new GodWandItemRenderer());
        EntityRendererRegistry.register(GodWandMod.ARCANE_ORB_ENTITY_TYPE, ArcaneOrbEntityRenderer::new);
        EntityRendererRegistry.register(GodWandMod.STONE_CANNON_ENTITY_TYPE, StoneCannonEntityRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(net.godwand.network.DrillWindPayload.ID, (payload, context) ->
                context.client().execute(() -> DrillWindRenderer.receive(payload)));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> DrillWindRenderer.reset());
        WorldRenderEvents.AFTER_TRANSLUCENT.register(DrillWindRenderer::render);

        ClientPlayNetworking.registerGlobalReceiver(net.godwand.network.SpatialAuraPayload.ID, (payload, context) ->
                context.client().execute(() -> SpatialAuraRenderer.receive(payload)));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(SpatialAuraRenderer::render);
        ClientPlayNetworking.registerGlobalReceiver(net.godwand.network.CannonPressurePayload.ID, (payload, context) ->
                context.client().execute(() -> { CannonPressureRenderer.receive(payload); ImpactDebrisRenderer.receive(payload); }));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(CannonPressureRenderer::render);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ImpactDebrisRenderer::render);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(SupernovaAccretionRenderer::render);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CannonPressureRenderer.reset());
        ClientPlayNetworking.registerGlobalReceiver(net.godwand.network.SingularityThreadsPayload.ID, (payload, context) ->
                context.client().execute(() -> SingularityThreadRenderer.receive(payload)));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(SingularityThreadRenderer::render);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SingularityThreadRenderer.reset());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SpatialAuraRenderer.reset());

        // Register S2C Impact FX receiver
        ClientPlayNetworking.registerGlobalReceiver(ImpactFxPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.type() == ImpactFxPayload.TYPE_BLACK_HOLE)
                    SupernovaSoundscape.start(new net.minecraft.util.math.Vec3d(payload.x(),payload.y(),payload.z()));
                if (payload.type() == ImpactFxPayload.TYPE_GRAVITY_CORE)
                    EarthDrillSoundscape.start(new net.minecraft.util.math.Vec3d(payload.x(), payload.y(), payload.z()));
                SupernovaAccretionRenderer.receive(payload);
                ImpactDebrisRenderer.receive(payload);
                GodWandPostFxManager.triggerImpact(
                        payload.x(), payload.y(), payload.z(),
                        payload.type(), payload.durationTicks(), payload.intensity()
                );
            });
        });

        // Register S2C Cinematic FX receiver (Trauma Shake, Dynamic FOV Zoom, Vacuum Silence)
        ClientPlayNetworking.registerGlobalReceiver(CinematicFxPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                GodWandCinematicEngine.onCinematicEvent(payload);
            });
        });

        // Draw impact frames on the HUD, independently of shaderpack world composition.
        HudRenderCallback.EVENT.register(CustomImpactFrameOverlay::render);

        // Suppress vanilla block target outline while aiming or holding God Wand in Supernova mode
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, hitResult) -> {
            var client = MinecraftClient.getInstance();
            if (client.player != null) {
                var main = client.player.getMainHandStack();
                var off = client.player.getOffHandStack();
                var stack = main.getItem() instanceof net.godwand.item.GodWandItem ? main : (off.getItem() instanceof net.godwand.item.GodWandItem ? off : null);
                if (stack != null && net.godwand.item.GodWandItem.getSkillMode(stack) == net.godwand.item.GodWandItem.MODE_SUPERNOVA) {
                    return false;
                }
            }
            return true;
        });

        // Register 3D Charging Drill & Aerodynamic Wind Ribbon Vortex
        WorldRenderEvents.AFTER_TRANSLUCENT.register(StoneCannonChargeRenderer::render);

        // Capture matrices here; compose after WorldRenderer returns so Iris has finished its final pass.
        WorldRenderEvents.LAST.register(GodWandPostFxManager::onWorldRender);

        // Register Client Tick for Shader & Cinematic Animation Progress
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SupernovaAccretionRenderer.tickCharge(client);
            StoneCannonWindSound.update(client);
            EarthDrillSoundscape.tick(client);
            GodWandItemRenderer.tick(client);
            GodWandPostFxManager.onClientTick();
            GodWandCinematicEngine.onClientTick();
            CustomImpactFrameOverlay.onClientTick();
        });

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            StoneCannonWindSound.reset();
            EarthDrillSoundscape.reset();
            SupernovaAccretionRenderer.reset();
            ImpactDebrisRenderer.reset();
            GodWandPostFxManager.reset();
            GodWandCinematicEngine.reset();
            CustomImpactFrameOverlay.reset();
        });

        // Cleanup on Client Stopping
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
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
