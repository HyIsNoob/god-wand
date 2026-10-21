package net.godwand.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.godwand.GodWandMod;

public class GodWandClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(GodWandMod.ARCANE_ORB_ENTITY_TYPE, ArcaneOrbEntityRenderer::new);
    }
}
