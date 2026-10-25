package net.godwand.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class GodWandFabricClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GodWandConfig.loadClient(FabricLoader.getInstance().getConfigDir());

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return new ResourceLocation(GodWandMod.MOD_ID, "post_effects");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager resourceManager) {
                        GodWandPostFxManager.reloadShaders();
                    }
                });

        KeyBindingHelper.registerKeyBinding(SkillWheelScreen.OPEN);
        GodWandItemRenderer itemRenderer = new GodWandItemRenderer();
        BuiltinItemRendererRegistry.INSTANCE.register(GodWandMod.GOD_WAND_ITEM, itemRenderer::render);
        EntityRendererRegistry.register(GodWandMod.ARCANE_ORB_ENTITY_TYPE, ArcaneOrbEntityRenderer::new);
        EntityRendererRegistry.register(GodWandMod.STONE_CANNON_ENTITY_TYPE, StoneCannonEntityRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(DrillWindPayload.ID, (client, handler, buf, responseSender) -> {
            DrillWindPayload payload = DrillWindPayload.read(buf);
            client.execute(() -> DrillWindRenderer.receive(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(SpatialAuraPayload.ID, (client, handler, buf, responseSender) -> {
            SpatialAuraPayload payload = SpatialAuraPayload.read(buf);
            client.execute(() -> SpatialAuraRenderer.receive(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(CannonPressurePayload.ID, (client, handler, buf, responseSender) -> {
            CannonPressurePayload payload = CannonPressurePayload.read(buf);
            client.execute(() -> {
                CannonPressureRenderer.receive(payload);
                ImpactDebrisRenderer.receive(payload);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SingularityThreadsPayload.ID, (client, handler, buf, responseSender) -> {
            SingularityThreadsPayload payload = SingularityThreadsPayload.read(buf);
            client.execute(() -> SingularityThreadRenderer.receive(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(ImpactFxPayload.ID, (client, handler, buf, responseSender) -> {
            ImpactFxPayload payload = ImpactFxPayload.read(buf);
            client.execute(() -> {
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
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(CinematicFxPayload.ID, (client, handler, buf, responseSender) -> {
            CinematicFxPayload payload = CinematicFxPayload.read(buf);
            client.execute(() -> GodWandCinematicEngine.onCinematicEvent(payload));
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            PoseStack matrices = context.matrixStack();
            Camera camera = context.camera();
            float tickDelta = context.tickDelta();
            CannonPressureRenderer.render(matrices, camera, tickDelta);
            DrillWindRenderer.render(matrices, camera, tickDelta);
            ImpactDebrisRenderer.render(matrices, camera, tickDelta);
            SingularityThreadRenderer.render(matrices, camera, tickDelta);
            SpatialAuraRenderer.render(matrices, camera, tickDelta);
            StoneCannonChargeRenderer.render(matrices, camera, tickDelta);
            ArcaneOrbChargeRenderer.render(matrices, camera, tickDelta);
            SupernovaAccretionRenderer.render(matrices, camera, tickDelta);
        });

        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, hitResult) -> {
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

        WorldRenderEvents.LAST.register(context -> {
            float tickDelta = context.tickDelta();
            GodWandPostFxManager.onWorldRender(context.matrixStack().last().pose(), context.projectionMatrix(), tickDelta);
        });

        HudRenderCallback.EVENT.register(CustomImpactFrameOverlay::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SkillWheelScreen.handleKeyInput(client);
            SupernovaAccretionRenderer.tickCharge(client);
            StoneCannonWindSound.update(client);
            EarthDrillSoundscape.tick(client);
            GodWandItemRenderer.tick(client);
            GodWandPostFxManager.onClientTick();
            GodWandCinematicEngine.onClientTick();
            CustomImpactFrameOverlay.onClientTick();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
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
