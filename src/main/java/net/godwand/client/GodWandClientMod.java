package net.godwand.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.godwand.GodWandMod;
import net.godwand.network.ImpactFxPayload;

public class GodWandClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(GodWandMod.ARCANE_ORB_ENTITY_TYPE, ArcaneOrbEntityRenderer::new);

        // Register S2C Impact FX receiver
        ClientPlayNetworking.registerGlobalReceiver(ImpactFxPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                GodWandPostFxManager.triggerImpact(
                        payload.x(), payload.y(), payload.z(),
                        payload.type(), payload.durationTicks(), payload.intensity()
                );
            });
        });

        // Register Post-Processing Shader Render Hook (renders right after translucent pass)
        WorldRenderEvents.AFTER_TRANSLUCENT.register(GodWandPostFxManager::onWorldRender);

        // Register Client Tick for Shader Animation Progress
        ClientTickEvents.END_CLIENT_TICK.register(client -> GodWandPostFxManager.onClientTick());

        // Cleanup on Client Stopping
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> GodWandPostFxManager.reset());
    }
}

