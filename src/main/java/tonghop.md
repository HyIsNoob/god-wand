# File: GodWandMod.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\GodWandMod.java
```java
package net.godwand;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.godwand.entity.ArcaneOrbEntity;
import net.godwand.item.GodWandItem;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.skill.RatioCollapseManager;
import net.godwand.skill.ShockwaveSlicer;
import net.godwand.skill.SpatialPathWalker;
import net.godwand.skill.SupernovaManager;
import net.godwand.spatial.DisplacementPersistentState;
import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GodWandMod implements ModInitializer {
    public static final String MOD_ID = "godwand";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static EntityType<ArcaneOrbEntity> ARCANE_ORB_ENTITY_TYPE;
    public static Item GOD_WAND_ITEM;

    @Override
    public void onInitialize() {
        ARCANE_ORB_ENTITY_TYPE = Registry.register(
                Registries.ENTITY_TYPE,
                Identifier.of(MOD_ID, "arcane_orb"),
                EntityType.Builder.<ArcaneOrbEntity>create(ArcaneOrbEntity::new, SpawnGroup.MISC)
                        .dimensions(0.5f, 0.5f)
                        .build()
        );

        GOD_WAND_ITEM = Registry.register(
                Registries.ITEM,
                Identifier.of(MOD_ID, "god_wand"),
                new GodWandItem(new Item.Settings())
        );

        PayloadTypeRegistry.playS2C().register(ImpactFxPayload.ID, ImpactFxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CinematicFxPayload.ID, CinematicFxPayload.CODEC);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(GOD_WAND_ITEM));

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                SpatialPathWalker.tickPlayer(player);
            }
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            ShockwaveSlicer.tick(world);
            RatioCollapseManager.tick(world);
            SupernovaManager.tick(world);
            SpatialDisplacementManager.tick(world);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SpatialPathWalker.deactivate(handler.player);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ShockwaveSlicer.clearAllTasks();
            RatioCollapseManager.clearAllTasks();
            SupernovaManager.clearAllTasks();
            for (ServerWorld world : server.getWorlds()) {
                SpatialDisplacementManager.emergencyRestoreAll(world);
            }
        });

        ServerWorldEvents.LOAD.register((server, world) -> {
            // Restore any crash-orphaned displaced blocks and cleanup orphaned display entities
            DisplacementPersistentState state = DisplacementPersistentState.getServerState(world);
            state.restoreAndCleanup(world);
        });

        LOGGER.info("God Wand Mod initialized.");
    }
}

```

# File: ArcaneOrbEntityRenderer.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\client\ArcaneOrbEntityRenderer.java
```java
package net.godwand.client;

import net.godwand.entity.ArcaneOrbEntity;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public class ArcaneOrbEntityRenderer extends EntityRenderer<ArcaneOrbEntity> {
    private final BlockRenderManager blockRenderManager;

    public ArcaneOrbEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
    }

    @Override
    public Identifier getTexture(ArcaneOrbEntity entity) {
        return Identifier.ofVanilla("textures/atlas/blocks.png");
    }

    @Override
    public void render(ArcaneOrbEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float time = entity.age + tickDelta;
        float baseScale = 0.45f + entity.getChargeLevel() * 0.18f;

        // 1. LÃµi nÄƒng lÆ°á»£ng xoay tá»‘c Ä‘á»™ cao
        matrices.push();
        matrices.translate(0.0f, 0.25f, 0.0f);
        matrices.scale(baseScale, baseScale, baseScale);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 18.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(time * 14.0f));
        matrices.translate(-0.5f, -0.5f, -0.5f);
        blockRenderManager.renderBlockAsEntity(
                Blocks.SEA_LANTERN.getDefaultState(),
                matrices, vertexConsumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV
        );
        matrices.pop();

        // 2. Lá»›p vá» ma phÃ¡p xoay ngÆ°á»£c chiá»u
        matrices.push();
        matrices.translate(0.0f, 0.25f, 0.0f);
        float outerScale = baseScale * 1.45f;
        matrices.scale(outerScale, outerScale, outerScale);
        matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(time * 12.0f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(time * 16.0f));
        matrices.translate(-0.5f, -0.5f, -0.5f);
        blockRenderManager.renderBlockAsEntity(
                Blocks.CYAN_STAINED_GLASS.getDefaultState(),
                matrices, vertexConsumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV
        );
        matrices.pop();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}

```

# File: GodWandCinematicEngine.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\client\GodWandCinematicEngine.java
```java
package net.godwand.client;

import net.godwand.network.CinematicFxPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Bá»™ Ä‘iá»u khiá»ƒn Camera Äiá»‡n áº¢nh & Ã‚m Thanh phÃ­a Client:
 * - Trauma Screen Shake (MÃ´ hÃ¬nh TraumaÂ² káº¿t há»£p hÃ m sÃ³ng Ä‘a táº§n sá»‘)
 * - Dynamic FOV Zoom (Ná»™i suy co/bung gÃ³c nhÃ¬n theo tá»«ng pha ká»¹ nÄƒng)
 * - Vacuum Silence (Táº¯t toÃ n bá»™ Ã¢m thanh mÃ´i trÆ°á»ng táº¡o khoáº£ng láº·ng trÆ°á»›c vá»¥ ná»•)
 */
public class GodWandCinematicEngine {

    // ==========================================
    // 1. TRAUMA SCREEN SHAKE
    // ==========================================
    private static float trauma = 0.0f;
    private static float traumaDecay = 0.045f;
    private static float shakeTime = 0.0f;

    // ==========================================
    // 2. DYNAMIC FOV ZOOM
    // ==========================================
    private static float targetFovMultiplier = 1.0f;
    private static float currentFovMultiplier = 1.0f;
    private static float prevFovMultiplier = 1.0f;
    private static int fovHoldTicks = 0;
    private static float fovRecoverySpeed = 0.08f;

    // ==========================================
    // 3. VACUUM SILENCE (AUDIO DUCKING)
    // ==========================================
    private static int vacuumSilenceTicks = 0;

    /**
     * KÃ­ch hoáº¡t sá»± kiá»‡n Ä‘iá»‡n áº£nh tá»« gÃ³i máº¡ng CinematicFxPayload
     */
    public static void onCinematicEvent(CinematicFxPayload payload) {
        float distFactor = 1.0f;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            // TÃ­nh toÃ¡n suy giáº£m cháº¥n Ä‘á»™ng theo khoáº£ng cÃ¡ch 3D tá»« tÃ¢m ná»• tá»›i ngÆ°á»i chÆ¡i
            double distSq = client.player.squaredDistanceTo(payload.x(), payload.y(), payload.z());
            if (payload.x() != 0 || payload.y() != 0 || payload.z() != 0) {
                double dist = Math.sqrt(distSq);
                double maxDist = 64.0;
                if (dist > maxDist) {
                    distFactor = 0.0f;
                } else if (dist > 8.0) {
                    distFactor = (float) (1.0 - ((dist - 8.0) / (maxDist - 8.0)));
                }
            }
        }

        switch (payload.eventType()) {
            case CinematicFxPayload.EVENT_TRAUMA_SHAKE -> {
                addTrauma(payload.intensity() * distFactor, payload.durationTicks());
            }
            case CinematicFxPayload.EVENT_FOV_PUNCH -> {
                setFovPunch(payload.intensity(), payload.durationTicks());
            }
            case CinematicFxPayload.EVENT_VACUUM_SILENCE -> {
                // Khoáº£ng láº·ng chÃ¢n khÃ´ng kÃ­ch hoáº¡t náº¿u ngÆ°á»i chÆ¡i á»Ÿ trong bÃ¡n kÃ­nh áº£nh hÆ°á»Ÿng
                if (distFactor > 0.1f) {
                    vacuumSilenceTicks = Math.max(vacuumSilenceTicks, payload.durationTicks());
                }
            }
            case CinematicFxPayload.EVENT_METEOR_FOCUS -> {
                // ChiÃªu 3: Co FOV láº¡i Ä‘á»ƒ táº¡o cáº£m giÃ¡c táº­p trung nÄƒng lÆ°á»£ng cá»±c háº¡n
                if (distFactor > 0.2f) {
                    setFovFocus(payload.intensity(), payload.durationTicks(), 0.12f);
                }
            }
            case CinematicFxPayload.EVENT_METEOR_SLAM -> {
                // ChiÃªu 3: Bung cá»±c Ä‘áº¡i FOV + rung cháº¥n dá»¯ dá»™i khi thiÃªn tháº¡ch cáº¯m Ä‘áº¥t
                addTrauma(payload.intensity() * distFactor, payload.durationTicks());
                setFovPunch(1.0f + 0.25f * distFactor, 14);
            }
            case CinematicFxPayload.EVENT_SUPERNOVA_COMPRESS -> {
                // ChiÃªu 4: Sá»¥p Ä‘á»• háº¥p dáº«n - Rung nhá»‹p tim dá»“n dáº­p
                addTrauma(0.45f * distFactor, payload.durationTicks());
                setFovFocus(0.92f, payload.durationTicks(), 0.06f);
            }
            case CinematicFxPayload.EVENT_SUPERNOVA_BURST -> {
                // ChiÃªu 4: Äáº¡i ná»• siÃªu tÃ¢n tinh - Rung cháº¥n cá»±c Ä‘áº¡i + bung FOV toÃ n cáº£nh
                addTrauma(payload.intensity() * distFactor, payload.durationTicks());
                setFovPunch(1.0f + 0.32f * distFactor, 24);
            }
        }
    }

    public static void setFovFocus(float targetMultiplier, int holdTicks, float recoverySpeed) {
        targetFovMultiplier = targetMultiplier;
        fovHoldTicks = holdTicks;
        fovRecoverySpeed = recoverySpeed;
    }

    public static void addTrauma(float amount, int durationTicks) {
        trauma = MathHelper.clamp(trauma + amount, 0.0f, 1.0f);
        if (durationTicks > 0) {
            traumaDecay = Math.max(0.015f, 1.0f / (float) durationTicks);
        }
    }

    public static void setFovPunch(float multiplier, int holdTicks) {
        targetFovMultiplier = multiplier;
        currentFovMultiplier = multiplier;
        fovHoldTicks = holdTicks;
        fovRecoverySpeed = 0.065f;
    }

    public static void setVacuumSilence(int ticks) {
        vacuumSilenceTicks = Math.max(0, ticks);
    }

    /**
     * Cáº­p nháº­t logic má»—i Client Tick
     */
    public static void onClientTick() {
        // 1. Cáº­p nháº­t Trauma suy giáº£m dáº§n
        if (trauma > 0.0f) {
            trauma = Math.max(0.0f, trauma - traumaDecay);
            shakeTime += 0.85f;
        }

        // 2. Cáº­p nháº­t Dynamic FOV
        prevFovMultiplier = currentFovMultiplier;
        if (fovHoldTicks > 0) {
            fovHoldTicks--;
            // Ná»™i suy mÆ°á»£t tá»« FOV hiá»‡n táº¡i vá» targetFovMultiplier trong lÃºc nÃ©n lá»±c
            currentFovMultiplier = MathHelper.lerp(0.25f, currentFovMultiplier, targetFovMultiplier);
        } else {
            // Há»“i phá»¥c dáº§n vá» tráº¡ng thÃ¡i bÃ¬nh thÆ°á»ng (1.0f)
            currentFovMultiplier = MathHelper.lerp(fovRecoverySpeed, currentFovMultiplier, 1.0f);
            if (Math.abs(currentFovMultiplier - 1.0f) < 0.002f) {
                currentFovMultiplier = 1.0f;
                targetFovMultiplier = 1.0f;
            }
        }

        // 3. Cáº­p nháº­t Vacuum Silence
        if (vacuumSilenceTicks > 0) {
            vacuumSilenceTicks--;
        }
    }

    /**
     * Ãp dá»¥ng Trauma Screen Shake vÃ o ma tráº­n Camera (Gá»i tá»« GameRendererMixin.tiltViewWhenHurt)
     */
    public static void applyScreenShake(MatrixStack matrices, float tickDelta) {
        if (trauma <= 0.001f) return;

        // MÃ´ hÃ¬nh phi tuyáº¿n tÃ­nh TraumaÂ² giÃºp rung cháº¥n máº¡nh máº½, gÃ£y gá»n á»Ÿ má»©c cao
        float intensity = trauma * trauma;
        float t = shakeTime + tickDelta * 0.85f;

        // SÃ³ng Ä‘iá»u hÃ²a Ä‘a táº§n sá»‘ táº¡o cáº£m giÃ¡c rung láº¯c báº¥t quy táº¯c tá»± nhiÃªn
        float pitch = (float) (Math.sin(t * 1.6) * Math.cos(t * 0.7) * 4.2) * intensity;
        float yaw   = (float) (Math.cos(t * 1.3) * Math.sin(t * 0.9) * 3.8) * intensity;
        float roll  = (float) (Math.sin(t * 2.1) * 2.8) * intensity;

        float transX = (float) (Math.sin(t * 1.8) * 0.065) * intensity;
        float transY = (float) (Math.cos(t * 2.4) * 0.085) * intensity;
        float transZ = (float) (Math.sin(t * 1.1) * 0.045) * intensity;

        matrices.translate(transX, transY, transZ);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
    }

    /**
     * Láº¥y há»‡ sá»‘ co/bung FOV ná»™i suy mÆ°á»£t mÃ  (Gá»i tá»« GameRendererMixin.getFov)
     */
    public static double getFovMultiplier(float tickDelta) {
        float interpolated = MathHelper.lerp(tickDelta, prevFovMultiplier, currentFovMultiplier);
        return (double) interpolated;
    }

    /**
     * Kiá»ƒm tra tráº¡ng thÃ¡i cÃ¢m láº·ng chÃ¢n khÃ´ng (Gá»i tá»« SoundEngineMixin.getVolume)
     */
    public static boolean isVacuumSilenceActive() {
        return vacuumSilenceTicks > 0;
    }

    public static void reset() {
        trauma = 0.0f;
        currentFovMultiplier = 1.0f;
        prevFovMultiplier = 1.0f;
        targetFovMultiplier = 1.0f;
        fovHoldTicks = 0;
        vacuumSilenceTicks = 0;
    }
}

```

# File: GodWandClientMod.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\client\GodWandClientMod.java
```java
package net.godwand.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.godwand.GodWandMod;
import net.godwand.network.CinematicFxPayload;
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

        // Register S2C Cinematic FX receiver (Trauma Shake, Dynamic FOV Zoom, Vacuum Silence)
        ClientPlayNetworking.registerGlobalReceiver(CinematicFxPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                GodWandCinematicEngine.onCinematicEvent(payload);
            });
        });

        // Register Post-Processing Shader Render Hook (renders right after translucent pass)
        WorldRenderEvents.AFTER_TRANSLUCENT.register(GodWandPostFxManager::onWorldRender);

        // Register Client Tick for Shader & Cinematic Animation Progress
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            GodWandPostFxManager.onClientTick();
            GodWandCinematicEngine.onClientTick();
        });

        // Cleanup on Client Stopping
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            GodWandPostFxManager.reset();
            GodWandCinematicEngine.reset();
        });
    }
}


```

# File: GodWandPostFxManager.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\client\GodWandPostFxManager.java
```java
package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.godwand.GodWandMod;
import net.godwand.mixin.PostEffectProcessorAccessor;
import net.godwand.network.ImpactFxPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.JsonEffectShaderProgram;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class GodWandPostFxManager {
    private static PostEffectProcessor postProcessor;
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    private static boolean active = false;
    private static int currentTick = 0;
    private static int maxTicks = 0;
    private static int fxType = 0;
    private static float baseIntensity = 1.0f;
    private static Vec3d blastCenter = Vec3d.ZERO;

    public static void triggerImpact(double x, double y, double z, int type, int durationTicks, float intensity) {
        blastCenter = new Vec3d(x, y, z);
        fxType = type;
        maxTicks = Math.max(1, durationTicks);
        currentTick = maxTicks;
        baseIntensity = Math.max(0.1f, intensity);
        active = true;
    }

    public static void onClientTick() {
        if (active) {
            currentTick--;
            if (currentTick <= 0) {
                active = false;
            }
        }
    }

    public static void reset() {
        if (postProcessor != null) {
            postProcessor.close();
            postProcessor = null;
        }
        active = false;
        lastWidth = -1;
        lastHeight = -1;
    }

    public static void onWorldRender(WorldRenderContext context) {
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getFramebuffer() == null) return;

        int fbWidth = client.getWindow().getFramebufferWidth();
        int fbHeight = client.getWindow().getFramebufferHeight();
        if (fbWidth <= 0 || fbHeight <= 0) return;

        if (postProcessor == null) {
            try {
                postProcessor = new PostEffectProcessor(
                        client.getTextureManager(),
                        client.getResourceManager(),
                        client.getFramebuffer(),
                        Identifier.ofVanilla("shaders/post/godwand_impact.json")
                );
                postProcessor.setupDimensions(fbWidth, fbHeight);
                lastWidth = fbWidth;
                lastHeight = fbHeight;
            } catch (Exception e) {
                GodWandMod.LOGGER.error("Failed to initialize God Wand PostEffectProcessor", e);
                active = false;
                return;
            }
        }

        if (fbWidth != lastWidth || fbHeight != lastHeight) {
            postProcessor.setupDimensions(fbWidth, fbHeight);
            lastWidth = fbWidth;
            lastHeight = fbHeight;
        }

        float tickDelta = context.tickCounter().getTickDelta(false);
        float progress = MathHelper.clamp(1.0f - ((float) currentTick - tickDelta) / (float) maxTicks, 0.0f, 1.0f);

        float impactIntensity = baseIntensity * (1.0f - progress * 0.75f);
        float impactFrame = 0.0f;
        float impactMode = 0.0f;
        float shockwaveRadius = 0.0f;
        float distortionPower = 0.0f;
        float chromaticAmount = 0.0f;
        float zoomBlur = 0.0f;
        float retinaBurn = 0.0f;
        float coreRadius = 0.0f;
        float lensingPower = 0.0f;
        float gTime = (float) ((System.currentTimeMillis() % 10000000L) / 1000.0);

        int tickIdx = Math.max(0, maxTicks - currentTick);

        switch (fxType) {
            case ImpactFxPayload.TYPE_DETONATION -> {
                // Calamity Detonation:
                // Ticks 0..4: 36 Anime Impact Frames cycle: Six-Eyes Cyan -> Crimson Invert -> Ink-Wash -> Prism Line-Art
                if (tickIdx < 5) {
                    impactFrame = 1.0f - ((float) tickIdx / 5.0f);
                    int[] detModes = {3, 2, 0, 33, 3};
                    impactMode = detModes[tickIdx];
                }

                // Ticks 0..15: SÃ³ng xung kÃ­ch bÃ¹ng ná»•, quang sai vÃ  lÃ³a vÃµng máº¡c
                if (tickIdx < 16) {
                    float shockProg = (float) tickIdx / 16.0f;
                    shockwaveRadius = (float) Math.pow(shockProg, 0.65) * 1.35f;
                    float distCurve = (float) Math.sin(shockProg * Math.PI);
                    distortionPower = 0.048f * baseIntensity * distCurve;
                    chromaticAmount = 0.024f * baseIntensity * (1.0f - shockProg);
                    zoomBlur = 0.065f * baseIntensity * (1.0f - shockProg);
                    retinaBurn = Math.max(0.0f, 0.65f * baseIntensity * (1.0f - shockProg * 1.4f));
                }

                // 3D Singularity Gravitational Lensing & LÃµi nÄƒng lÆ°á»£ng duy trÃ¬:
                // Nhá» gá»n hÆ¡n (base 0.72m thay vÃ¬ 1.6m), dao Ä‘á»™ng to/nhá» chá»›p chá»›p kiá»ƒu tá»¥ nÄƒng lÆ°á»£ng sá»‘ng Ä‘á»™ng
                float pulse = (float) (Math.sin(gTime * 18.0) * 0.16 + Math.sin(gTime * 34.0) * 0.08);
                float activeCoreR = Math.max(0.35f, 0.72f + pulse);
                float activeLensing = 1.35f + pulse * 1.4f;

                if (progress < 0.94f) {
                    coreRadius = activeCoreR;
                    lensingPower = activeLensing;
                } else {
                    // Ticks 106..112: HÃºt ngÆ°á»£c chÃ¢n khÃ´ng sá»¥p Ä‘á»• lÃµi
                    float snapProg = (progress - 0.94f) / 0.06f;
                    coreRadius = MathHelper.lerp(snapProg, activeCoreR, 0.0f);
                    lensingPower = MathHelper.lerp(snapProg, activeLensing, 0.0f);
                    zoomBlur = -0.05f * snapProg;
                }
            }
            case ImpactFxPayload.TYPE_IMPLOSION -> {
                // Epicenter Calamity Implosion:
                // Phase 1 (progress 0..0.3): Inward suction (negative distortion power pulls pixels towards center)
                if (progress < 0.30f) {
                    float suckCurve = progress / 0.30f;
                    distortionPower = -0.042f * baseIntensity * suckCurve;
                    shockwaveRadius = 0.7f * (1.0f - suckCurve);
                    zoomBlur = -0.052f * baseIntensity * suckCurve; // HÃºt hÆ°á»›ng tÃ¢m
                    impactMode = 4.0f; // Shadow Blue
                    coreRadius = suckCurve * 1.5f;
                    lensingPower = 1.6f * suckCurve;
                } else {
                    // Phase 2 (progress 0.30..0.55): Slam Impact Frame flash
                    float slamProg = (progress - 0.30f) / 0.70f;
                    if (slamProg < 0.30f) {
                        impactFrame = 1.0f - (slamProg / 0.30f);
                        impactMode = (tickIdx % 2 == 0) ? 3.0f : 2.0f;
                    }
                    shockwaveRadius = slamProg * 1.45f;
                    distortionPower = 0.052f * baseIntensity * (1.0f - slamProg);
                    zoomBlur = 0.048f * baseIntensity * (1.0f - slamProg);
                    retinaBurn = Math.max(0.0f, 0.45f * baseIntensity * (1.0f - slamProg * 1.5f));
                    coreRadius = Math.max(0.0f, (1.0f - slamProg) * 1.5f);
                    lensingPower = Math.max(0.0f, 2.2f * (1.0f - slamProg));
                }
                chromaticAmount = 0.030f * baseIntensity * (1.0f - progress);
            }
            case ImpactFxPayload.TYPE_COLLAPSE -> {
                // Spatial Path Collapse: Snappy, sharp tactical burst
                if (progress < 0.35f) {
                    impactFrame = 0.85f * (1.0f - (progress / 0.35f));
                    impactMode = (tickIdx % 2 == 0) ? 3.0f : 0.0f;
                }
                shockwaveRadius = progress * 1.1f;
                distortionPower = 0.022f * baseIntensity * (1.0f - progress);
                chromaticAmount = 0.012f * baseIntensity * (1.0f - progress);
                zoomBlur = 0.0f;
                coreRadius = 0.0f; // Tuyá»‡t Ä‘á»‘i khÃ´ng váº½ há»‘ Ä‘en
                lensingPower = 0.0f;
            }
            case ImpactFxPayload.TYPE_SEISMIC_SLAM -> {
                // ChiÃªu 3: Ground Seismic Slam (ThiÃªn tháº¡ch giÃ¡ng sáº­p)
                // 2-3 frame Anime Impact Frame sáº¯c láº¡nh tráº¯ng Ä‘en
                if (tickIdx < 4) {
                    impactFrame = 1.0f - ((float) tickIdx / 4.0f);
                    int[] slamModes = {0, 1, 33, 0};
                    impactMode = slamModes[tickIdx];
                }
                // SÃ³ng xung kÃ­ch máº·t Ä‘áº¥t lan rá»™ng nhanh
                shockwaveRadius = (float) Math.pow(progress, 0.45) * 1.6f;
                distortionPower = 0.065f * baseIntensity * (1.0f - progress);
                chromaticAmount = 0.025f * baseIntensity * (1.0f - progress);
                zoomBlur = 0.045f * baseIntensity * (1.0f - progress);
                retinaBurn = Math.max(0.0f, 0.45f * baseIntensity * (1.0f - progress * 1.5f));
                coreRadius = 0.0f; // Tuyá»‡t Ä‘á»‘i khÃ´ng cÃ³ há»‘ Ä‘en
                lensingPower = 0.0f;
            }
            case ImpactFxPayload.TYPE_SUPERNOVA -> {
                // Cataclysmic Cosmic Supernova (Burst & Lingering Mushroom Aftermath - 140 ticks / 7.0s duration):
                // Ticks 0..11 (progress < 0.08f): Chuá»—i 36 Anime Impact Frame liÃªn tá»¥c
                if (progress < 0.08f) {
                    impactFrame = 1.0f - (progress / 0.08f);
                    int[] novaModes = {35, 34, 33, 2, 3, 0, 1, 33};
                    impactMode = novaModes[Math.min(tickIdx, novaModes.length - 1)];
                } else {
                    impactFrame = 0.0f;
                }

                // Relativistic shockwave expands across the entire sky
                shockwaveRadius = (float) Math.pow(progress, 0.42) * 2.8f;
                float distCurve = (float) Math.sin(progress * Math.PI);
                distortionPower = 0.085f * baseIntensity * distCurve * (1.0f - progress * 0.7f);
                chromaticAmount = 0.055f * baseIntensity * (1.0f - progress);

                // Zoom Blur & Retina Burn: cá»±c Ä‘áº¡i lÃºc ná»• rá»“i tan dáº§n mÆ°á»£t mÃ 
                zoomBlur = 0.12f * baseIntensity * (float) Math.sin(progress * Math.PI) * (1.0f - progress * 0.5f);
                retinaBurn = Math.max(0.0f, 1.0f * baseIntensity * (1.0f - progress * 2.0f));

                // Quáº£ cáº§u lá»­a & Ä‘Ã¡m mÃ¢y náº¥m vÅ© trá»¥:
                // Ticks 0..35 (progress 0..0.25): Bung ná»Ÿ cá»±c nhanh tá»« 5.2m lÃªn 38.0m
                // Ticks 36..140 (progress 0.25..1.0): Duy trÃ¬ kÃ­ch thÆ°á»›c khá»•ng lá»“ 38m trong khi chuyá»ƒn thÃ nh khÃ³i bá»¥i & tan dáº§n
                float expandT = Math.min(1.0f, progress / 0.25f);
                coreRadius = MathHelper.lerp((float) Math.sin(expandT * Math.PI * 0.5), 5.2f, 38.0f);
                lensingPower = Math.max(0.0f, (1.0f - progress) * 2.5f);

                // AFTERMATH: KhÃ´ng biáº¿n máº¥t Ä‘á»™t ngá»™t! CÆ°á»ng Ä‘á»™ má» dáº§n tá»« tá»« trong 140 ticks (7 giÃ¢y)
                impactIntensity = baseIntensity * (1.0f - (float) Math.pow(progress, 2.2));
            }
            case ImpactFxPayload.TYPE_BLACK_HOLE -> {
                impactIntensity = 1.0f;
                // Ticks 0..340 (progress 0.0 -> 0.944): Há»‘ Äen to dáº§n tá»« 1 cháº¥m (0.15f) lÃªn cá»±c Ä‘áº¡i (5.2f)
                // ÄÆ N ÄIá»†U LIÃŠN Tá»¤C - TUYá»†T Äá»I KHÃ”NG CO RÃšT HOáº¶C DAO Äá»˜NG!
                if (progress < 0.944f) {
                    float grow = progress / 0.944f;
                    // ÄÆ°á»ng cong ná»Ÿ mÆ°á»£t: lÃºc Ä‘áº§u nhá» ná»Ÿ cháº­m, cÃ ng vá» sau cÃ ng ná»Ÿ to uy nghi
                    coreRadius = MathHelper.lerp((float) Math.pow(grow, 0.92), 0.15f, 5.2f);
                    lensingPower = MathHelper.lerp(grow, 0.4f, 3.2f);
                    shockwaveRadius = 0.0f;
                    distortionPower = 0.0f;
                    zoomBlur = -0.015f * grow;
                }
                // Ticks 341..360 (progress 0.944 -> 1.0): ChÃ¢n khÃ´ng im láº·ng (Vacuum Silence) & TÃ­ch tá»¥ nÄƒng lÆ°á»£ng cá»±c háº¡n trÆ°á»›c ná»•
                else {
                    coreRadius = 5.2f + 0.05f * (float) Math.sin(gTime * 40.0);
                    lensingPower = 5.5f;
                    zoomBlur = -0.075f;
                    distortionPower = -0.05f;
                }
            }
        }

        // Project 3D explosion coordinates to 2D Screen UV
        float centerX = 0.5f;
        float centerY = 0.5f;
        if (blastCenter.lengthSquared() > 0.001) {
            Camera camera = client.gameRenderer.getCamera();
            Vec3d camPos = camera.getPos();
            Vec3d rel = blastCenter.subtract(camPos);

            Quaternionf camRot = camera.getRotation();
            Vector3f local = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z);
            new Quaternionf(camRot).conjugate().transform(local);

            // In camera coordinates: -Z is forward
            if (local.z < -0.1f) {
                float fov = (float) Math.toRadians(client.options.getFov().getValue());
                float tanFov = (float) Math.tan(fov / 2.0);
                float aspect = (float) fbWidth / (float) fbHeight;
                float projX = 0.5f + (local.x / (-local.z * tanFov * aspect)) * 0.5f;
                float projY = 0.5f - (local.y / (-local.z * tanFov)) * 0.5f;
                centerX = MathHelper.clamp(projX, -2.0f, 3.0f);
                centerY = MathHelper.clamp(projY, -2.0f, 3.0f);
            } else {
                // Khi vá»¥ ná»• á»Ÿ sau lÆ°ng ngÆ°á»i chÆ¡i: trÆ°á»£t tÃ¢m ra xa khá»i mÃ n hÃ¬nh thay vÃ¬ nháº£y vá» giá»¯a tÃ¢m ngáº¯m
                float signX = (local.x >= 0.0f) ? 1.0f : -1.0f;
                float signY = (local.y >= 0.0f) ? -1.0f : 1.0f;
                centerX = 0.5f + signX * 5.0f;
                centerY = 0.5f + signY * 5.0f;
            }
        }

        float aspect = (float) fbWidth / (float) fbHeight;

        Camera camera = client.gameRenderer.getCamera();
        Vec3d camPos = (camera != null) ? camera.getPos() : Vec3d.ZERO;
        Matrix4f invProj = new Matrix4f(context.projectionMatrix()).invert();
        Matrix4f invView = new Matrix4f(context.positionMatrix()).invert();

        for (PostEffectPass pass : ((PostEffectProcessorAccessor) postProcessor).getPasses()) {
            JsonEffectShaderProgram prog = pass.getProgram();
            prog.getUniformByNameOrDummy("InvProjMat").set(invProj);
            prog.getUniformByNameOrDummy("InvViewMat").set(invView);
            prog.getUniformByNameOrDummy("CameraPos").set((float) camPos.x, (float) camPos.y, (float) camPos.z);
            prog.getUniformByNameOrDummy("BlastCenter").set((float) blastCenter.x, (float) blastCenter.y, (float) blastCenter.z);
            prog.getUniformByNameOrDummy("CoreRadius").set(coreRadius);
            prog.getUniformByNameOrDummy("LensingPower").set(lensingPower);
            prog.getUniformByNameOrDummy("FxType").set((float) fxType);
        }

        postProcessor.setUniforms("ImpactIntensity", impactIntensity);
        postProcessor.setUniforms("ImpactFrame", impactFrame);
        postProcessor.setUniforms("ImpactMode", impactMode);
        postProcessor.setUniforms("ShockwaveRadius", shockwaveRadius);
        postProcessor.setUniforms("DistortionPower", distortionPower);
        postProcessor.setUniforms("ChromaticAmount", chromaticAmount);
        postProcessor.setUniforms("ZoomBlur", zoomBlur);
        postProcessor.setUniforms("RetinaBurn", retinaBurn);
        postProcessor.setUniforms("CenterX", centerX);
        postProcessor.setUniforms("CenterY", centerY);
        postProcessor.setUniforms("Aspect", aspect);
        postProcessor.setUniforms("GTime", gTime);
        postProcessor.setUniforms("CoreRadius", coreRadius);
        postProcessor.setUniforms("LensingPower", lensingPower);
        postProcessor.setUniforms("FxType", (float) fxType);

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        postProcessor.render(tickDelta);
    }
}

```

# File: ArcaneOrbEntity.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\entity\ArcaneOrbEntity.java
```java
package net.godwand.entity;

import net.godwand.GodWandMod;
import net.godwand.skill.ShockwaveSlicer;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

public class ArcaneOrbEntity extends ProjectileEntity {
    private int chargeLevel = 1;

    public ArcaneOrbEntity(EntityType<? extends ProjectileEntity> entityType, World world) {
        super(entityType, world);
        this.noClip = false;
    }

    public ArcaneOrbEntity(World world, LivingEntity owner, int chargeLevel) {
        super(GodWandMod.ARCANE_ORB_ENTITY_TYPE, world);
        this.setOwner(owner);
        this.setChargeLevel(chargeLevel);
        this.noClip = false;
    }

    public void setChargeLevel(int level) {
        this.chargeLevel = level;
    }

    public int getChargeLevel() {
        return this.chargeLevel;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("ChargeLevel", this.chargeLevel);
        nbt.putInt("Age", this.age);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.chargeLevel = nbt.getInt("ChargeLevel");
        this.age = nbt.getInt("Age");
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;

        Vec3d velocity = this.getVelocity();

        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit.getType() != HitResult.Type.MISS) {
            onCollision(hit);
            return;
        }

        this.setPosition(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);

        if (this.getWorld().isClient()) {
            this.getWorld().addParticle(new DustParticleEffect(new Vector3f(0.0f, 0.94f, 1.0f), 1.3f),
                    this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            this.getWorld().addParticle(ParticleTypes.REVERSE_PORTAL,
                    this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
        } else {
            ServerWorld sw = (ServerWorld) this.getWorld();
            // Cyan & Amethyst dual particles
            sw.spawnParticles(new DustParticleEffect(new Vector3f(0.0f, 0.94f, 1.0f), 1.2f),
                    this.getX(), this.getY(), this.getZ(), 4, 0.1, 0.1, 0.1, 0.0);
            sw.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                    this.getX(), this.getY(), this.getZ(), 3, 0.1, 0.1, 0.1, 0.02);

            if (this.age > 200) {
                this.discard();
            }
        }
    }

    @Override
    protected boolean canHit(net.minecraft.entity.Entity entity) {
        if (entity == this.getOwner() && this.age < 5) {
            return false;
        }
        return super.canHit(entity);
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!this.getWorld().isClient()) {
            double radius = switch (this.chargeLevel) {
                case 1 -> 5.0;
                case 2 -> 10.0;
                default -> 16.0; // Calamity Level 3
            };
            BlockPos center = hitResult instanceof BlockHitResult bhr ? bhr.getBlockPos() : this.getBlockPos();
            ShockwaveSlicer.queueCalamityDetonation((ServerWorld) this.getWorld(), center, radius);
            this.discard();
        }
    }
}

```

# File: GodWandItem.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\item\GodWandItem.java
```java
package net.godwand.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.entity.ArcaneOrbEntity;
import net.godwand.network.CinematicFxPayload;
import net.godwand.skill.RatioCollapseManager;
import net.godwand.skill.SpatialPathWalker;
import net.godwand.skill.SupernovaManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;

public class GodWandItem extends Item {
    public static final int MODE_SPATIAL_PATH = 0;
    public static final int MODE_ARCANE_ORB = 1;
    public static final int MODE_RATIO_COLLAPSE = 2;
    public static final int MODE_SUPERNOVA = 3;
    public static final int TOTAL_MODES = 4;

    public GodWandItem(Settings settings) {
        super(settings.maxCount(1));
    }

    public static int getSkillMode(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null && customData.contains("GodWandMode")) {
            return customData.copyNbt().getInt("GodWandMode");
        }
        return MODE_SPATIAL_PATH;
    }

    public static void setSkillMode(ItemStack stack, int mode) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putInt("GodWandMode", mode));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        int currentMode = getSkillMode(stack);

        // SHIFT + RIGHT CLICK: Cycle Skill Mode
        if (user.isSneaking()) {
            int nextMode = (currentMode + 1) % TOTAL_MODES;
            setSkillMode(stack, nextMode);

            if (!world.isClient()) {
                switch (nextMode) {
                    case MODE_SPATIAL_PATH -> {
                        user.sendMessage(Text.literal("Â§bÂ§l[âœ¦ CHIÃŠU 1/3: Lá»I ÄI KHÃ”NG GIAN (SPATIAL PATH) âœ¦]"), true);
                        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 2.0f, 1.3f);
                    }
                    case MODE_ARCANE_ORB -> {
                        user.sendMessage(Text.literal("Â§dÂ§l[âœ¦ CHIÃŠU 2/3: Cáº¦U MA PHÃP Há»¦Y DIá»†T (SINGULARITY ORB) âœ¦]"), true);
                        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 2.0f, 1.4f);
                    }
                    case MODE_RATIO_COLLAPSE -> {
                        user.sendMessage(Text.literal("Â§6Â§l[âœ¦ CHIÃŠU 3/4: Sá»¤P Äá»” THIÃŠN THáº CH (RATIO COLLAPSE) âœ¦]"), true);
                        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                                SoundEvents.BLOCK_HEAVY_CORE_PLACE, SoundCategory.PLAYERS, 2.5f, 0.9f);
                    }
                    case MODE_SUPERNOVA -> {
                        user.sendMessage(Text.literal("Â§cÂ§l[âœ¦ CHIÃŠU 4/4: SIÃŠU TÃ‚N TINH (SUPERNOVA & CHRONO REVERSAL) âœ¦]"), true);
                        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 2.5f, 0.6f);
                        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 2.5f, 0.5f);
                    }
                }
            }
            return TypedActionResult.success(stack);
        }

        // NORMAL RIGHT CLICK: Execute Current Selected Skill
        switch (currentMode) {
            case MODE_SPATIAL_PATH -> {
                if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
                    SpatialPathWalker.toggle(serverPlayer);
                }
                return TypedActionResult.success(stack);
            }
            case MODE_ARCANE_ORB, MODE_RATIO_COLLAPSE, MODE_SUPERNOVA -> {
                user.setCurrentHand(hand);
                return TypedActionResult.consume(stack);
            }
            default -> {
                return TypedActionResult.pass(stack);
            }
        }
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        int mode = getSkillMode(stack);
        if (mode == MODE_ARCANE_ORB || mode == MODE_SUPERNOVA || mode == MODE_RATIO_COLLAPSE) {
            return 72000;
        }
        return 0;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        int mode = getSkillMode(stack);
        if (mode == MODE_ARCANE_ORB || mode == MODE_RATIO_COLLAPSE) {
            return UseAction.BOW;
        } else if (mode == MODE_SUPERNOVA) {
            return UseAction.SPEAR;
        }
        return UseAction.NONE;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        int currentMode = getSkillMode(stack);

        // Váº¬N CHIÃŠU 3: Sá»¤P Äá»” THIÃŠN THáº CH (RATIO COLLAPSE)
        if (currentMode == MODE_RATIO_COLLAPSE) {
            int chargeTicks = this.getMaxUseTime(stack, user) - remainingUseTicks;
            if (chargeTicks < 0) return;

            int level = (chargeTicks >= 50) ? 3 : (chargeTicks >= 24) ? 2 : 1;
            float progress = Math.min(1.0f, chargeTicks / 50.0f);

            if (!world.isClient() && user instanceof ServerPlayerEntity player) {
                ServerWorld sw = (ServerWorld) world;

                // Bá»¥i Ä‘áº¥t Ä‘Ã¡ ná»©t gÃ£y vÃ  khÃ³i Ä‘á»‹a cháº¥n dÆ°á»›i chÃ¢n
                if (chargeTicks % 2 == 0) {
                    sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            player.getX() + (Math.random() - 0.5) * 1.8, player.getY() + 0.1, player.getZ() + (Math.random() - 0.5) * 1.8,
                            1, 0, 0.06 + progress * 0.10, 0, 0.01);
                }

                // Äiá»ƒm chá»‰ thá»‹ má»¥c tiÃªu rÆ¡i thiÃªn tháº¡ch
                double reach = (level == 3) ? 65.0 : (level == 2) ? 45.0 : 28.0;
                HitResult hit = player.raycast(reach, 0.0f, false);
                Vec3d targetPos = (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bHit)
                        ? bHit.getBlockPos().toCenterPos()
                        : player.getEyePos().add(player.getRotationVec(1.0f).multiply(reach * 0.7));

                if (chargeTicks % 3 == 0) {
                    sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                            targetPos.x, targetPos.y + 0.5, targetPos.z, 2, 0.4, 0.2, 0.4, 0.05);
                }

                // Ã‚m thanh nÃ©n trá»ng lá»±c tÄƒng dáº§n
                if (chargeTicks % 10 == 0) {
                    float pitch = 0.7f + progress * 0.8f;
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1.6f, pitch);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_HEAVY_CORE_PLACE, SoundCategory.PLAYERS, 1.4f, pitch);
                }

                int percent = (int) (progress * 100);
                String lvlStr = (level == 3) ? "Â§cÂ§l[Cáº¤P 3: Äáº I THIÃŠN THáº CH]" : (level == 2) ? "Â§6Â§l[Cáº¤P 2: NÃ‰N TRá»ŒNG Lá»°C]" : "Â§eÂ§l[Cáº¤P 1: CÄ‚N Báº¢N]";
                player.sendMessage(Text.literal("Â§6Â§l[âœ¦ Váº¬N KHÃ THIÃŠN THáº CH: " + lvlStr + " (" + percent + "%) âœ¦]"), true);
            }
            return;
        }

        if (currentMode != MODE_SUPERNOVA) return;

        int chargeTicks = this.getMaxUseTime(stack, user) - remainingUseTicks;
        if (chargeTicks < 0) return;

        // Váº­n chiÃªu trong 140 ticks (7 giÃ¢y):
        double progress = Math.min(1.0, (double) chargeTicks / 140.0);

        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            ServerWorld sw = (ServerWorld) world;

            // 1. Hiá»‡u á»©ng dÆ°á»›i chÃ¢n ngÆ°á»i chÆ¡i: VÃ²ng ma tráº­n háº¡t xoay trÃ²n tÄƒng tá»‘c
            double spinSpeed = 0.2 + progress * 0.8;
            double angle = (chargeTicks * spinSpeed) % (Math.PI * 2.0);
            double ringRadius = 1.6 + (1.0 - progress) * 0.8;
            double px = player.getX() + Math.cos(angle) * ringRadius;
            double pz = player.getZ() + Math.sin(angle) * ringRadius;
            sw.spawnParticles(ParticleTypes.PORTAL, px, player.getY() + 0.15, pz, 2, 0.05, 0.1, 0.05, 0.02);
            sw.spawnParticles(ParticleTypes.ENCHANT, px, player.getY() + 0.25, pz, 1, 0, 0.05, 0, 0.05);

            // Bá»¥i Ä‘áº¥t Ä‘Ã¡ nhá» lÆ¡ lá»­ng bá»‘c lÃªn
            if (chargeTicks % 2 == 0) {
                sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        player.getX() + (Math.random() - 0.5) * 2.0, player.getY() + 0.1, player.getZ() + (Math.random() - 0.5) * 2.0,
                        1, 0, 0.08 + progress * 0.12, 0, 0.01);
            }

            // 2. Camera rung tÄƒng dáº§n (Trauma 0.05 -> 0.35) & FOV thu háº¹p (1.0 -> 0.85)
            float trauma = (float) (0.05 + progress * 0.30);
            CinematicFxPayload shake = new CinematicFxPayload(
                    CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                    player.getX(), player.getY(), player.getZ(),
                    trauma, 4
            );
            ServerPlayNetworking.send(player, shake);

            if (chargeTicks % 12 == 0) {
                float fovTarget = (float) (1.0 - progress * 0.15);
                CinematicFxPayload focus = new CinematicFxPayload(
                        CinematicFxPayload.EVENT_METEOR_FOCUS,
                        player.getX(), player.getY(), player.getZ(),
                        fovTarget, 16
                );
                ServerPlayNetworking.send(player, focus);
            }

            // 3. Ã‚m thanh nhá»‹p tim Warden & náº¡p nÄƒng lÆ°á»£ng tÄƒng dáº§n cao Ä‘á»™ (pitch 0.5 -> 1.8)
            if (chargeTicks % 16 == 0) {
                float pitch = (float) (0.5f + progress * 1.3f);
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 2.5f, pitch);
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1.8f, pitch);
            }

            // 4. Hiá»‡u á»©ng táº¡i Ä‘iá»ƒm ngáº¯m (Aim Target): Tia chá»‰ thá»‹ khÃ´ng gian & máº§m há»‘ Ä‘en hÃºt bá»¥i
            HitResult hit = player.raycast(45.0, 0.0f, false);
            Vec3d targetPos;
            if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bHit) {
                targetPos = bHit.getBlockPos().toCenterPos();
            } else {
                targetPos = player.getEyePos().add(player.getRotationVec(1.0f).multiply(30.0));
            }
            Vec3d seedPos = targetPos.add(0, 18.0, 0);

            // Tia chá»‰ thá»‹ khÃ´ng gian
            sw.spawnParticles(ParticleTypes.END_ROD, seedPos.x, seedPos.y, seedPos.z, 2, 0.1, 0.1, 0.1, 0.02);
            sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, seedPos.x, seedPos.y, seedPos.z, 4, 0.5 + progress * 1.2, 0.5 + progress * 1.2, 0.5 + progress * 1.2, 0.08);

            // Action bar thÃ´ng bÃ¡o tiáº¿n Ä‘á»™ váº­n chiÃªu
            int percent = (int) (progress * 100);
            player.sendMessage(Text.literal("Â§cÂ§l[âœ¦ Váº¬N KHÃ SIÃŠU TÃ‚N TINH: " + percent + "% âœ¦]"), true);

            // KHI Váº¬N Äá»¦ 140 TICKS (7 GIÃ‚Y) -> PHÃT Äá»˜NG SIÃŠU TÃ‚N TINH Cáº¤P CALAMITY!
            if (chargeTicks >= 140) {
                SupernovaManager.cast(player);
                player.getItemCooldownManager().set(this, 520); // Cooldown 26s bao trá»n toÃ n bá»™ Ä‘áº¡i chiÃªu
                player.clearActiveItem();
                player.sendMessage(Text.literal("Â§4Â§l[â˜  Äáº I CHIÃŠU SIÃŠU TÃ‚N TINH KHá»žI Äá»˜NG! â˜ ]"), true);
            }
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (getSkillMode(stack) == MODE_SUPERNOVA) {
            int chargeTicks = this.getMaxUseTime(stack, user) - remainingUseTicks;
            if (chargeTicks < 140) {
                // Tháº£ tay trÆ°á»›c 7 giÃ¢y -> Há»¦Y CHIÃŠU!
                if (!world.isClient() && user instanceof PlayerEntity player) {
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 2.0f, 0.8f);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 2.0f, 1.2f);
                    player.sendMessage(Text.literal("Â§7Â§o[ÄÃ£ há»§y váº­n chiÃªu SiÃªu TÃ¢n Tinh]"), true);
                }
            }
            return;
        }

        if (getSkillMode(stack) == MODE_RATIO_COLLAPSE) {
            if (!world.isClient() && user instanceof ServerPlayerEntity player) {
                int chargeDuration = this.getMaxUseTime(stack, user) - remainingUseTicks;
                int level = 1;
                if (chargeDuration >= 50) {
                    level = 3;
                } else if (chargeDuration >= 24) {
                    level = 2;
                }

                RatioCollapseManager.cast(player, level);
                player.getItemCooldownManager().set(this, 35);
            }
            return;
        }

        if (getSkillMode(stack) != MODE_ARCANE_ORB) return;

        if (!world.isClient() && user instanceof PlayerEntity player) {
            int chargeDuration = this.getMaxUseTime(stack, user) - remainingUseTicks;
            int level = 1;
            if (chargeDuration >= 50) { // 2.5s+ -> Calamity Level 3
                level = 3;
            } else if (chargeDuration >= 24) { // 1.2s -> Level 2
                level = 2;
            }

            ArcaneOrbEntity orb = new ArcaneOrbEntity(net.godwand.GodWandMod.ARCANE_ORB_ENTITY_TYPE, world);
            orb.setOwner(player);
            orb.setChargeLevel(level);
            orb.setPosition(player.getEyePos().x, player.getEyePos().y - 0.1, player.getEyePos().z);

            Vec3d look = player.getRotationVec(1.0f);
            orb.setVelocity(look.x * 2.3, look.y * 2.3, look.z * 2.3);
            world.spawnEntity(orb);

            // Powerful Arcane Recoil
            double recoilForce = switch (level) {
                case 1 -> 0.45;
                case 2 -> 0.85;
                default -> 1.35;
            };
            player.addVelocity(-look.x * recoilForce, -look.y * recoilForce * 0.4 + 0.18, -look.z * recoilForce);
            player.velocityModified = true;

            // Muzzle Blast VFX
            ServerWorld sw = (ServerWorld) world;
            sw.spawnParticles(ParticleTypes.FLASH, player.getX() + look.x, player.getEyeY() + look.y, player.getZ() + look.z, 2, 0.1, 0.1, 0.1, 0.0);
            sw.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX() + look.x * 1.5, player.getEyeY() + look.y * 1.5, player.getZ() + look.z * 1.5, 1, 0, 0, 0, 0);
            sw.spawnParticles(new DustParticleEffect(new Vector3f(0.0f, 0.95f, 1.0f), 1.5f),
                    player.getX() + look.x, player.getEyeY() + look.y, player.getZ() + look.z, 20, 0.3, 0.3, 0.3, 0.2);

            // Muzzle Audio Layering
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.PLAYERS, 2.0f, 1.3f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 2.0f, 1.5f);
            if (level == 3) {
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 2.5f, 1.0f);
            }
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        int mode = getSkillMode(stack);
        tooltip.add(Text.literal("Â§7[Shift + Chuá»™t Pháº£i]: Â§eÄá»•i chiÃªu thá»©c"));
        switch (mode) {
            case MODE_SPATIAL_PATH -> tooltip.add(Text.literal("Â§aÄang chá»n: Â§b[1] Lá»‘i Äi KhÃ´ng Gian Â§7(Báº­t/Táº¯t ráº½ khá»‘i)"));
            case MODE_ARCANE_ORB -> tooltip.add(Text.literal("Â§aÄang chá»n: Â§d[2] Cáº§u Ma PhÃ¡p Há»§y Diá»‡t Â§7(Giá»¯ báº¯n)"));
            case MODE_RATIO_COLLAPSE -> tooltip.add(Text.literal("Â§aÄang chá»n: Â§6[3] Sá»¥p Äá»• ThiÃªn Tháº¡ch Â§7(Bá»©t phÃ¡ & GiÃ¡ng sáº­p)"));
            case MODE_SUPERNOVA -> tooltip.add(Text.literal("Â§aÄang chá»n: Â§c[4] SiÃªu TÃ¢n Tinh & Nghá»‹ch Äáº£o Thá»i Gian Â§7(Lá»— Ä‘en & Ná»• bÃ¹ng)"));
        }
        tooltip.add(Text.literal("Â§8--------------------------------"));
        tooltip.add(Text.translatable("item.godwand.god_wand.desc"));
        super.appendTooltip(stack, context, tooltip, type);
    }
}

```

# File: FlowableFluidMixin.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\mixin\FlowableFluidMixin.java
```java
package net.godwand.mixin;

import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowableFluid.class)
public abstract class FlowableFluidMixin {
    @Inject(method = "canFlow", at = @At("HEAD"), cancellable = true)
    private void godwand$preventFlowIntoDisplacedArea(
            BlockView world, BlockPos fluidPos, BlockState fluidBlockState,
            Direction flowDirection, BlockPos targetPos, BlockState targetBlockState,
            FluidState targetFluidState, Fluid fluid,
            CallbackInfoReturnable<Boolean> cir) {
        if (world instanceof ServerWorld serverWorld) {
            if (SpatialDisplacementManager.isPositionDisplaced(serverWorld.getRegistryKey(), targetPos)) {
                cir.setReturnValue(false);
            }
        }
    }
}

```

# File: GameRendererMixin.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\mixin\GameRendererMixin.java
```java
package net.godwand.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.godwand.client.GodWandCinematicEngine;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    /**
     * BÆ¡m Trauma Screen Shake trá»±c tiáº¿p vÃ o ma tráº­n gÃ³c nhÃ¬n Camera
     */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
    private void godwand$applyScreenShake(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        GodWandCinematicEngine.applyScreenShake(matrices, tickDelta);
    }

    /**
     * BÆ¡m Dynamic FOV Zoom (Co khi nÃ©n lá»±c, bung khi ná»•)
     */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void godwand$applyDynamicFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        double current = cir.getReturnValue();
        double multiplier = GodWandCinematicEngine.getFovMultiplier(tickDelta);
        if (Math.abs(multiplier - 1.0) > 0.0001) {
            cir.setReturnValue(current * multiplier);
        }
    }
}

```

# File: PostEffectProcessorAccessor.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\mixin\PostEffectProcessorAccessor.java
```java
package net.godwand.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(PostEffectProcessor.class)
public interface PostEffectProcessorAccessor {
    @Accessor("passes")
    List<PostEffectPass> getPasses();
}

```

# File: SoundSystemMixin.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\mixin\SoundSystemMixin.java
```java
package net.godwand.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.godwand.client.GodWandCinematicEngine;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {

    /**
     * Vacuum Silence (Khoáº£ng láº·ng chÃ¢n khÃ´ng):
     * Ã‰p Ã¢m lÆ°á»£ng má»i Ã¢m thanh mÃ´i trÆ°á»ng/mob vá» 0 ngay trÆ°á»›c khoáº£nh kháº¯c Ä‘áº¡i ná»•.
     * Ngoáº¡i trá»« PLAYERS (tiáº¿ng ngÆ°á»i chÆ¡i/vÅ© khÃ­) vÃ  MASTER.
     */
    @Inject(method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F", at = @At("HEAD"), cancellable = true)
    private void godwand$applyVacuumSilence(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (GodWandCinematicEngine.isVacuumSilenceActive()) {
            SoundCategory category = sound.getCategory();
            if (category != SoundCategory.PLAYERS && category != SoundCategory.MASTER) {
                cir.setReturnValue(0.0f);
            }
        }
    }
}

```

# File: CinematicFxPayload.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\network\CinematicFxPayload.java
```java
package net.godwand.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Payload Ä‘á»“ng bá»™ cÃ¡c sá»± kiá»‡n Ä‘iá»‡n áº£nh tá»« Server xuá»‘ng Client:
 * - Trauma Screen Shake
 * - Dynamic FOV Zoom (Co/Bung)
 * - Vacuum Silence (Táº¯t Ã¢m thanh mÃ´i trÆ°á»ng trÆ°á»›c vá»¥ ná»•)
 */
public record CinematicFxPayload(
        int eventType,
        double x,
        double y,
        double z,
        float intensity,
        int durationTicks
) implements CustomPayload {

    public static final CustomPayload.Id<CinematicFxPayload> ID = new CustomPayload.Id<>(Identifier.of("godwand", "cinematic_fx"));

    // CÃ¡c sá»± kiá»‡n Ä‘iá»‡n áº£nh Ä‘á»“ng bá»™ vá»›i ká»¹ nÄƒng
    public static final int EVENT_TRAUMA_SHAKE        = 0; // Rung cháº¥n thÃ´ng thÆ°á»ng
    public static final int EVENT_FOV_PUNCH           = 1; // Giáº­t máº¡nh FOV
    public static final int EVENT_VACUUM_SILENCE      = 2; // Khoáº£ng láº·ng chÃ¢n khÃ´ng (táº¯t tiáº¿ng mob/mÃ´i trÆ°á»ng)
    public static final int EVENT_METEOR_FOCUS        = 3; // NÃ©n lá»±c thiÃªn tháº¡ch ChiÃªu 3 (FOV 1.0 -> 0.88)
    public static final int EVENT_METEOR_SLAM         = 4; // ThiÃªn tháº¡ch cáº¯m Ä‘áº¥t ChiÃªu 3 (Bung FOV 1.25 + Trauma 0.85)
    public static final int EVENT_SUPERNOVA_COMPRESS  = 5; // Sá»¥p Ä‘á»• háº¥p dáº«n ChiÃªu 4 (Rung nhá»‹p tim + FOV dao Ä‘á»™ng)
    public static final int EVENT_SUPERNOVA_BURST     = 6; // Äáº¡i ná»• SiÃªu TÃ¢n Tinh ChiÃªu 4 (Max Trauma 1.0 + Bung FOV 1.32)

    public static final PacketCodec<RegistryByteBuf, CinematicFxPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, CinematicFxPayload::eventType,
            PacketCodecs.DOUBLE, CinematicFxPayload::x,
            PacketCodecs.DOUBLE, CinematicFxPayload::y,
            PacketCodecs.DOUBLE, CinematicFxPayload::z,
            PacketCodecs.FLOAT, CinematicFxPayload::intensity,
            PacketCodecs.INTEGER, CinematicFxPayload::durationTicks,
            CinematicFxPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

```

# File: ImpactFxPayload.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\network\ImpactFxPayload.java
```java
package net.godwand.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ImpactFxPayload(double x, double y, double z, int type, int durationTicks, float intensity) implements CustomPayload {
    public static final CustomPayload.Id<ImpactFxPayload> ID = new CustomPayload.Id<>(Identifier.of("godwand", "impact_fx"));

    public static final int TYPE_DETONATION = 0;
    public static final int TYPE_IMPLOSION = 1;
    public static final int TYPE_COLLAPSE = 2;
    public static final int TYPE_SUPERNOVA = 3;
    public static final int TYPE_BLACK_HOLE = 4;
    public static final int TYPE_SEISMIC_SLAM = 5;

    public static final PacketCodec<RegistryByteBuf, ImpactFxPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.DOUBLE, ImpactFxPayload::x,
            PacketCodecs.DOUBLE, ImpactFxPayload::y,
            PacketCodecs.DOUBLE, ImpactFxPayload::z,
            PacketCodecs.INTEGER, ImpactFxPayload::type,
            PacketCodecs.INTEGER, ImpactFxPayload::durationTicks,
            PacketCodecs.FLOAT, ImpactFxPayload::intensity,
            ImpactFxPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

```

# File: RatioCollapseManager.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\skill\RatioCollapseManager.java
```java
package net.godwand.skill;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.spatial.DisplacedBlockRecord;
import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RatioCollapseManager {

    public static class RatioCollapseTask {
        public final ServerWorld world;
        public final ServerPlayerEntity caster;
        public final BlockPos center;
        public final double radius;
        public final double boulderRadius;
        public final int maxBlocks;
        public final int craterRadius;
        public final int craterDepth;
        public final float damageMult;
        public int ageTicks = 0;
        public final int maxLifetimeTicks = 95;
        public boolean slammed = false;
        public boolean restored = false;

        public final List<BlockPos> displacedPositions = new ArrayList<>();
        public final Map<BlockPos, Float> apexHeight = new HashMap<>();
        public final Map<BlockPos, Vec3d> phase1Displacement = new HashMap<>();
        public final Map<BlockPos, Vec3d> clusterOffset = new HashMap<>();
        public final Map<BlockPos, Quaternionf> baseRotation = new HashMap<>();

        public RatioCollapseTask(ServerWorld world, ServerPlayerEntity caster, BlockPos center, double radius,
                                 double boulderRadius, int maxBlocks, int craterRadius, int craterDepth, float damageMult) {
            this.world = world;
            this.caster = caster;
            this.center = center.toImmutable();
            this.radius = radius;
            this.boulderRadius = boulderRadius;
            this.maxBlocks = maxBlocks;
            this.craterRadius = craterRadius;
            this.craterDepth = craterDepth;
            this.damageMult = damageMult;
        }
    }

    private static final Queue<RatioCollapseTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();

    public static void cast(ServerPlayerEntity player) {
        cast(player, 1);
    }

    public static void cast(ServerPlayerEntity player, int chargeLevel) {
        ServerWorld world = player.getServerWorld();

        double reach = switch (chargeLevel) {
            case 2 -> 45.0;
            case 3 -> 65.0;
            default -> 28.0;
        };

        HitResult hit = player.raycast(reach, 0.0f, false);
        BlockPos targetPos;
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bHit) {
            targetPos = bHit.getBlockPos();
        } else {
            Vec3d targetVec = player.getEyePos().add(player.getRotationVec(1.0f).multiply(reach * 0.7));
            targetPos = BlockPos.ofFloored(targetVec);
            while (targetPos.getY() > world.getBottomY() && world.getBlockState(targetPos).isAir()) {
                targetPos = targetPos.down();
            }
        }

        double radius = switch (chargeLevel) {
            case 2 -> 13.5;
            case 3 -> 19.0;
            default -> 8.5;
        };

        double boulderRadius = switch (chargeLevel) {
            case 2 -> 3.8;
            case 3 -> 5.5;
            default -> 2.4;
        };

        int maxBlocks = switch (chargeLevel) {
            case 2 -> 180;
            case 3 -> 270;
            default -> 110;
        };

        int craterRadius = switch (chargeLevel) {
            case 2 -> 14;
            case 3 -> 21;
            default -> 9;
        };

        int craterDepth = switch (chargeLevel) {
            case 2 -> 7;
            case 3 -> 11;
            default -> 4;
        };

        float damageMult = switch (chargeLevel) {
            case 2 -> 1.6f;
            case 3 -> 2.5f;
            default -> 1.0f;
        };

        launch(world, player, targetPos, radius, boulderRadius, maxBlocks, craterRadius, craterDepth, damageMult);
    }

    public static void launch(ServerWorld world, ServerPlayerEntity player, BlockPos center, double radius) {
        launch(world, player, center, radius, 2.4, 110, 9, 4, 1.0f);
    }

    public static void launch(ServerWorld world, ServerPlayerEntity player, BlockPos center,
                              double radius, double boulderRadius, int maxBlocks,
                              int craterRadius, int craterDepth, float damageMult) {
        RatioCollapseTask task = new RatioCollapseTask(world, player, center, radius, boulderRadius, maxBlocks, craterRadius, craterDepth, damageMult);

        // Ã‚m thanh ná»©t gÃ£y Ä‘á»‹a cháº¥n ban Ä‘áº§u
        world.playSound(null, center, SoundEvents.BLOCK_HEAVY_CORE_BREAK, SoundCategory.BLOCKS, 3.5f, 0.75f);
        world.playSound(null, center, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.BLOCKS, 2.5f, 1.25f);
        world.playSound(null, center, SoundEvents.ITEM_MACE_SMASH_GROUND, SoundCategory.BLOCKS, 3.0f, 0.8f);

        world.spawnParticles(ParticleTypes.SONIC_BOOM, center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.FLASH, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.0);
        world.spawnParticles(new DustParticleEffect(new Vector3f(0.15f, 0.90f, 1.0f), 1.5f),
                center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 40, 1.5, 0.3, 1.5, 0.25);

        // BÃ³c tá»‘i Ä‘a task.maxBlocks khá»‘i Ä‘áº¥t Ä‘Ã¡ trong bÃ¡n kÃ­nh task.radius
        int intR = (int) Math.ceil(task.radius);
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        List<BlockPos> validPositions = new ArrayList<>();
        for (int x = -intR; x <= intR; x++) {
            for (int z = -intR; z <= intR; z++) {
                double dSq = x * x + z * z;
                if (dSq > task.radius * task.radius) continue;

                int startY = center.getY() + 3;
                mutable.set(center.getX() + x, startY, center.getZ() + z);
                while (mutable.getY() > center.getY() - 6 && world.getBlockState(mutable).isAir()) {
                    mutable.move(0, -1, 0);
                }

                BlockState state = world.getBlockState(mutable);
                if (!SpatialDisplacementManager.canDisplace(world, mutable, state)) continue;
                validPositions.add(mutable.toImmutable());
            }
        }

        // Chá»n tá»‘i Ä‘a task.maxBlocks khá»‘i
        Collections.shuffle(validPositions, new Random(center.asLong()));
        int count = Math.min(task.maxBlocks, validPositions.size());

        for (int i = 0; i < count; i++) {
            BlockPos pos = validPositions.get(i);
            float hash = (float) SpatialMath.hashCoordinateToPhase(pos.asLong());
            float apexY = 5.5f + (float) (task.boulderRadius * 0.8) + 3.0f * (float) Math.abs(Math.sin(hash));

            // GÃ³c xoay ngáº«u nhiÃªn
            Quaternionf rot = new Quaternionf()
                    .rotateX((float) Math.toRadians(25.0 * Math.sin(hash * 2.1)))
                    .rotateY((float) Math.toRadians(45.0 * Math.cos(hash * 1.7)))
                    .rotateZ((float) Math.toRadians(30.0 * Math.sin(hash * 3.3)));

            // BÃ³c tÃ¡ch á»Ÿ cháº¿ Ä‘á»™ CUSTOM_SCRIPTED vá»›i lifetime 105
            SpatialDisplacementManager.displaceBlock(
                    world,
                    pos,
                    new Vector3f(0, 0, 0),
                    rot,
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    105,
                    0x00F0FF,
                    true,
                    DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
                    player.getUuid(),
                    null,
                    0.0
            );

            DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
            if (rec != null) {
                rec.permanentDestruction = true;
            }

            task.displacedPositions.add(pos);
            task.apexHeight.put(pos, apexY);
            task.baseRotation.put(pos, rot);

            // Vector Ä‘á»‰nh nÃ¢ng á»Ÿ Pha 1
            double dx = pos.getX() - center.getX();
            double dz = pos.getZ() - center.getZ();
            Vec3d p1Disp = new Vec3d(-dx * 0.15, apexY, -dz * 0.15);
            task.phase1Displacement.put(pos, p1Disp);

            // Tá»a Ä‘á»™ Fibonacci Sphere trong táº£ng thiÃªn tháº¡ch bÃ¡n kÃ­nh task.boulderRadius
            Vec3d clusterOff = computeBoulderClusterOffset(i, count, task.boulderRadius);
            task.clusterOffset.put(pos, clusterOff);
        }

        // Äáº©y quÃ¡i váº­t xung quanh lÃªn trá»i
        Box vortexBox = new Box(center).expand(task.radius * 1.25);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, vortexBox, ent -> ent != player)) {
            e.addVelocity(0, 0.48, 0);
            e.velocityModified = true;
            e.damage(world.getDamageSources().magic(), 4.0f * task.damageMult);
        }

        ACTIVE_TASKS.add(task);
    }

    public static Vec3d computeBoulderClusterOffset(int index, int total, double radius) {
        if (total <= 1) return Vec3d.ZERO;
        double phi = Math.acos(1.0 - 2.0 * (index + 0.5) / total);
        double theta = Math.PI * (1.0 + Math.sqrt(5.0)) * index;
        double r = radius * (0.65 + 0.35 * Math.sqrt((index + 0.5) / (double) total));
        return new Vec3d(
                r * Math.sin(phi) * Math.cos(theta),
                r * Math.cos(phi),
                r * Math.sin(phi) * Math.sin(theta)
        );
    }

    public static void tick(ServerWorld world) {
        Iterator<RatioCollapseTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            RatioCollapseTask task = it.next();
            if (task.world != world) continue;

            task.ageTicks++;
            Vec3d meteorCore = task.center.toCenterPos().add(0, 11.0 + task.boulderRadius * 1.35, 0);

            // =========================================================================
            // PHA 1: Cáº Y TUNG Máº¶T Äáº¤T CÃ“ Sá»¨C Náº¶NG (Ticks 1..28) - Ná»˜I SUY SINUSOIDAL MÆ¯á»¢T MÃ€
            // =========================================================================
            if (task.ageTicks <= 28) {
                if (task.ageTicks % 2 == 0) {
                    float t = MathHelper.clamp(task.ageTicks / 28.0f, 0.0f, 1.0f);
                    float u = 0.5f - 0.5f * (float) Math.cos(t * Math.PI); // Cosine ease-in-out S-curve Ãªm Ã¡i
                    for (BlockPos pos : task.displacedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        Vec3d p1Disp = task.phase1Displacement.getOrDefault(pos, Vec3d.ZERO).multiply(u);
                        Quaternionf rot = task.baseRotation.getOrDefault(pos, new Quaternionf());

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(2);
                        display.setTransformation(SpatialMath.createPivotTransform(p1Disp, rot, 1.0f));
                    }

                    if (task.ageTicks % 6 == 0) {
                        world.playSound(null, task.center.up(4), SoundEvents.BLOCK_BASALT_BREAK, SoundCategory.BLOCKS, 1.8f, 0.7f);
                    }
                }
            }

            // =========================================================================
            // PHA 2: Há»¢P NHáº¤T THÃ€NH 1 Táº¢NG THIÃŠN THáº CH KHá»”NG Lá»’ (Ticks 29..68)
            // =========================================================================
            else if (task.ageTicks <= 68) {
                // Ticks 29..58 (30 ticks): Ã‰p cháº·t cÃ¡c khá»‘i vÃ o khá»‘i cáº§u meteorCore vá»›i ná»™i suy mÆ°á»£t mÃ 
                if (task.ageTicks <= 58) {
                    float cp = (task.ageTicks - 28) / 30.0f;
                    float smoothCp = 0.5f - 0.5f * (float) Math.cos(cp * Math.PI);
                    float scaleVal = 1.0f + 0.28f * smoothCp; // PhÃ³ng to 1.0x -> 1.28x Ä‘á»ƒ cÃ¡c khá»‘i Ä‘an khÃ­t vÃ o nhau

                    if ((task.ageTicks - 28) % 2 == 0) {
                        for (BlockPos pos : task.displacedPositions) {
                            DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                            if (record == null || record.displayEntityUuid == null) continue;

                            BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                            if (display == null || !display.isAlive()) continue;

                            Vec3d p1Disp = task.phase1Displacement.getOrDefault(pos, Vec3d.ZERO);
                            Vec3d clusterOff = task.clusterOffset.getOrDefault(pos, Vec3d.ZERO);
                            Vec3d finalClusterWorldPos = meteorCore.add(clusterOff);
                            Vec3d finalDisp = finalClusterWorldPos.subtract(Vec3d.ofCenter(pos));

                            Vec3d curDisp = p1Disp.lerp(finalDisp, smoothCp);
                            Quaternionf rot = task.baseRotation.getOrDefault(pos, new Quaternionf());

                            display.setStartInterpolation(0);
                            display.setInterpolationDuration(2);
                            display.setTransformation(SpatialMath.createPivotTransform(curDisp, rot, scaleVal));
                        }
                    }

                    // HÃºt cÃ¡c thá»±c thá»ƒ xung quanh lÃªn báº§u trá»i
                    if (task.ageTicks % 6 == 0) {
                        Box suctionBox = new Box(task.center).expand(task.radius * 1.2);
                        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, suctionBox, ent -> ent != task.caster)) {
                            Vec3d pull = meteorCore.subtract(e.getPos()).normalize().multiply(0.22);
                            e.addVelocity(pull.x, pull.y, pull.z);
                            e.velocityModified = true;
                        }
                    }
                }
                // Ticks 59..68 (10 ticks nÃ©n lá»±c): Táº£ng thiÃªn tháº¡ch khá»•ng lá»“ rung láº¯c dá»¯ dá»™i táº¡i chá»—
                else {
                    for (BlockPos pos : task.displacedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        Vec3d clusterOff = task.clusterOffset.getOrDefault(pos, Vec3d.ZERO);
                        double jitterX = (Math.random() - 0.5) * 0.28;
                        double jitterY = (Math.random() - 0.5) * 0.28;
                        double jitterZ = (Math.random() - 0.5) * 0.28;

                        Vec3d vibDisp = meteorCore.add(clusterOff).add(jitterX, jitterY, jitterZ).subtract(Vec3d.ofCenter(pos));
                        Quaternionf rot = task.baseRotation.getOrDefault(pos, new Quaternionf());

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(2);
                        display.setTransformation(SpatialMath.createPivotTransform(vibDisp, rot, 1.28f));
                    }

                    // Tia sÃ©t vÃ  cháº¥n Ä‘á»™ng Ã¢m thanh
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                            meteorCore.x, meteorCore.y, meteorCore.z, 8, 1.8, 1.8, 1.8, 0.2);
                    world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.15f, 0.15f), 1.6f),
                            meteorCore.x, meteorCore.y, meteorCore.z, 6, 1.5, 1.5, 1.5, 0.1);

                    if (task.ageTicks == 59) {
                        CinematicFxPayload focusPayload = new CinematicFxPayload(
                                CinematicFxPayload.EVENT_METEOR_FOCUS,
                                meteorCore.x, meteorCore.y, meteorCore.z,
                                0.88f, 10
                        );
                        for (ServerPlayerEntity p : PlayerLookup.around(world, meteorCore, 96.0)) {
                            ServerPlayNetworking.send(p, focusPayload);
                        }
                    }

                    if (task.ageTicks == 60) {
                        world.playSound(null, meteorCore.x, meteorCore.y, meteorCore.z,
                                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.BLOCKS, 3.5f, 0.65f);
                    }
                    if (task.ageTicks == 65) {
                        world.playSound(null, meteorCore.x, meteorCore.y, meteorCore.z,
                                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 3.5f, 1.8f);
                    }
                }
            }

            // =========================================================================
            // PHA 3: GIÃNG Sáº¬P THIÃŠN THáº CH (Ticks 69..75)
            // =========================================================================
            else if (task.ageTicks <= 75) {
                // Táº¡i Tick 69: Cáº¢ KHá»I THIÃŠN THáº CH KHá»”NG Lá»’ Cáº®M THáº²NG XUá»NG Äáº¤T NHÆ¯ 1 KHá»I DUY NHáº¤T (6 ticks)
                if (task.ageTicks == 69) {
                    Vec3d groundCenterTarget = task.center.toCenterPos().add(0, 1.2, 0);

                    for (BlockPos pos : task.displacedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        Vec3d clusterOff = task.clusterOffset.getOrDefault(pos, Vec3d.ZERO);
                        Vec3d impactWorldPos = groundCenterTarget.add(clusterOff);
                        Vec3d slamDisp = impactWorldPos.subtract(Vec3d.ofCenter(pos));
                        Quaternionf rot = task.baseRotation.getOrDefault(pos, new Quaternionf());

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(6); // RÆ¡i vá»›i tá»‘c Ä‘á»™ tÃªn lá»­a trong 6 ticks
                        display.setTransformation(SpatialMath.createPivotTransform(slamDisp, rot, 1.28f));
                    }

                    world.playSound(null, task.center, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.BLOCKS, 3.0f, 0.5f);
                }

                // Táº¡i Tick 75: Vá»¥ va cháº¡m máº·t Ä‘áº¥t (Ground Zero Impact)
                if (task.ageTicks == 75 && !task.slammed) {
                    task.slammed = true;

                    // Ã‚m thanh va Ä‘áº­p táº§ng lá»›p
                    world.playSound(null, task.center, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 4.5f, 0.55f);
                    world.playSound(null, task.center, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.BLOCKS, 4.0f, 0.65f);
                    world.playSound(null, task.center, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 3.5f, 0.5f);
                    world.playSound(null, task.center, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.BLOCKS, 3.5f, 1.1f);

                    // SÃ³ng xung kÃ­ch VFX
                    world.spawnParticles(ParticleTypes.FLASH,
                            task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.0);
                    world.spawnParticles(ParticleTypes.SONIC_BOOM,
                            task.center.getX() + 0.5, task.center.getY() + 0.8, task.center.getZ() + 0.5, 3, 0.1, 0.1, 0.1, 0.0);
                    world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                            task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5, 4, 0.8, 0.8, 0.8, 0.0);

                    for (int i = 0; i < 32; i++) {
                        double angle = (i / 32.0) * (2.0 * Math.PI);
                        double r = task.radius * 0.95;
                        double px = task.center.getX() + 0.5 + Math.cos(angle) * r;
                        double pz = task.center.getZ() + 0.5 + Math.sin(angle) * r;
                        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, task.center.getY() + 0.2, pz,
                                1, Math.cos(angle) * 0.18, 0.06, Math.sin(angle) * 0.18, 0.03);
                        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                                px, task.center.getY() + 0.2, pz, 5, 0.2, 0.2, 0.2, 0.1);
                    }

                    // SÃ¡t thÆ°Æ¡ng va Ä‘áº­p vÃ  Ä‘áº©y vÄƒng
                    Box slamBox = new Box(task.center).expand(task.radius * 1.4);
                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, slamBox, ent -> ent != task.caster)) {
                        double dist = e.getPos().distanceTo(task.center.toCenterPos());
                        float dmg = (float) Math.max(16.0, ((1.0 - (dist / (task.radius * 1.4))) * 42.0 + 10.0) * task.damageMult);
                        e.damage(world.getDamageSources().fallingBlock(task.caster), dmg);

                        Vec3d diff = e.getPos().subtract(task.center.toCenterPos()).normalize().multiply(1.6 * task.damageMult).add(0, 0.65, 0);
                        e.addVelocity(diff.x, diff.y, diff.z);
                        e.velocityModified = true;
                    }

                    // Shader Anime Impact Frame & Ground Seismic Shockwave (Ground Zero)
                    ImpactFxPayload slamPayload = new ImpactFxPayload(
                            task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5,
                            ImpactFxPayload.TYPE_SEISMIC_SLAM, 20, 2.0f * task.damageMult
                    );
                    CinematicFxPayload slamCinematic = new CinematicFxPayload(
                            CinematicFxPayload.EVENT_METEOR_SLAM,
                            task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5,
                            Math.min(1.0f, 0.95f * task.damageMult), 24
                    );
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.center.toCenterPos(), 96.0)) {
                        ServerPlayNetworking.send(p, slamPayload);
                        ServerPlayNetworking.send(p, slamCinematic);
                    }

                    // Táº O Há» VA CHáº M THIÃŠN THáº CH THá»°C Sá»° - KHÃ”NG HOÃ€N TRáº¢
                    int craterR = task.craterRadius;
                    int maxD = task.craterDepth;
                    BlockPos.Mutable cMut = new BlockPos.Mutable();
                    Random rng = new Random(task.center.asLong());

                    for (int cx = -craterR; cx <= craterR; cx++) {
                        for (int cz = -craterR; cz <= craterR; cz++) {
                            double distSq = cx * cx + cz * cz;
                            if (distSq > craterR * craterR) continue;

                            double dist2D = Math.sqrt(distSq);
                            int depth = (int) Math.round((1.0 - (dist2D / craterR)) * maxD);

                            for (int cy = -depth; cy <= 1; cy++) {
                                cMut.set(task.center.getX() + cx, task.center.getY() + cy, task.center.getZ() + cz);
                                if (cMut.getY() < world.getBottomY() || cMut.getY() >= world.getTopY()) continue;

                                BlockState bState = world.getBlockState(cMut);
                                if (bState.isAir() || bState.getBlock() == Blocks.BEDROCK) continue;

                                if (cy < -depth + 1) {
                                    BlockState floorState = (rng.nextFloat() < 0.40f) ? Blocks.MAGMA_BLOCK.getDefaultState() :
                                            (rng.nextFloat() < 0.70f) ? Blocks.COBBLESTONE.getDefaultState() :
                                                    Blocks.CRACKED_STONE_BRICKS.getDefaultState();
                                    world.setBlockState(cMut, floorState, Block.NOTIFY_ALL);
                                } else {
                                    world.setBlockState(cMut, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                                }
                            }
                        }
                    }

                    // Khá»‘i cá»§a táº£ng thiÃªn tháº¡ch vÄƒng tung tÃ³e ra xung quanh rÃ¬a há»‘ thÃ nh FallingBlockEntity
                    int flingCount = 0;
                    int maxFling = Math.min(65, task.maxBlocks / 3);
                    for (BlockPos pos : task.displacedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), pos);
                        if (record != null && flingCount < maxFling && rng.nextFloat() < 0.55f) {
                            flingCount++;
                            double fAngle = rng.nextDouble() * Math.PI * 2.0;
                            double fSpeed = 0.45 + rng.nextDouble() * 0.75;
                            double spawnX = task.center.getX() + 0.5 + Math.cos(fAngle) * 3.5;
                            double spawnY = task.center.getY() + 2.0;
                            double spawnZ = task.center.getZ() + 0.5 + Math.sin(fAngle) * 3.5;

                            FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world,
                                     BlockPos.ofFloored(spawnX, spawnY, spawnZ),
                                     record.originalState);
                            falling.setVelocity(Math.cos(fAngle) * fSpeed, 0.45 + rng.nextDouble() * 0.45, Math.sin(fAngle) * fSpeed);
                            falling.velocityModified = true;
                        }
                    }
                }
            }

            // =========================================================================
            // PHA 4: HIá»†N TRÆ¯á»œNG HOANG TÃ€N & Káº¾T THÃšC (Ticks 76..85)
            // =========================================================================
            else {
                // Káº¿t thÃºc task táº¡i Tick 78 sau khi khÃ³i bá»¥i Ä‘Ã£ tan bá»›t
                if (task.ageTicks >= 78) {
                    it.remove();
                }
            }
        }
    }

    public static int getActiveTaskCount() {
        return ACTIVE_TASKS.size();
    }

    public static void clearAllTasks() {
        ACTIVE_TASKS.clear();
    }
}

```

# File: ShockwaveSlicer.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\skill\ShockwaveSlicer.java
```java
package net.godwand.skill;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.spatial.DisplacedBlockRecord;
import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ShockwaveSlicer {

    public static class SingularityTask {
        public final ServerWorld world;
        public final BlockPos center;
        public final double maxRadius;
        public final Vec3d orbCenter;

        public final List<BlockPos> displayedPositions = new ArrayList<>();
        public final List<BlockPos> interiorPositions = new ArrayList<>();
        public final Map<BlockPos, Vec3d> fullExpansionMap = new HashMap<>();
        public final Map<BlockPos, Quaternionf> baseRotationMap = new HashMap<>();
        public final Map<BlockPos, Float> blockScaleMap = new HashMap<>();

        public int ageTicks = 0;
        public final int maxLifetimeTicks = 130;
        public boolean snappedBack = false;
        public boolean restored = false;

        public SingularityTask(ServerWorld world, BlockPos center, double maxRadius, Vec3d orbCenter) {
            this.world = world;
            this.center = center.toImmutable();
            this.maxRadius = maxRadius;
            this.orbCenter = orbCenter;
        }
    }

    private static final Queue<SingularityTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();

    public static void queueCalamityDetonation(ServerWorld world, BlockPos center, double maxRadius) {
        Vec3d orbCenter = center.toCenterPos().add(0, 3.8 + maxRadius * 0.35, 0);
        SingularityTask task = new SingularityTask(world, center, maxRadius, orbCenter);

        // 1. Quáº£ cáº§u nÄƒng lÆ°á»£ng trung tÃ¢m Ä‘Æ°á»£c káº¿t xuáº¥t trá»±c tiáº¿p báº±ng 3D Raymarched Shader (TYPE_DETONATION)
        // 2. BÃ³c tÃ¡ch cÃ¡c khá»‘i Ä‘áº¥t Ä‘Ã¡ trong hÃ¬nh cáº§u maxRadius
        int intR = (int) Math.ceil(maxRadius);
        double maxRadiusSq = maxRadius * maxRadius;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        List<BlockPos> candidatePositions = new ArrayList<>();
        for (int x = -intR; x <= intR; x++) {
            for (int y = -intR; y <= intR; y++) {
                int targetY = center.getY() + y;
                if (targetY < world.getBottomY() || targetY >= world.getTopY()) continue;
                for (int z = -intR; z <= intR; z++) {
                    double dSq = x * x + y * y + z * z;
                    if (dSq > maxRadiusSq) continue;

                    mutable.set(center.getX() + x, targetY, center.getZ() + z);
                    BlockState state = world.getBlockState(mutable);
                    if (!SpatialDisplacementManager.canDisplace(world, mutable, state)) continue;

                    candidatePositions.add(mutable.toImmutable());
                }
            }
        }

        // Trá»™n ngáº«u nhiÃªn trÆ°á»›c khi chia táº§ng bÃ¡n kÃ­nh Ä‘á»ƒ 500 khá»‘i phÃ¢n bá»• Ä‘á»u 360 Ä‘á»™ quanh tÃ¢m
        Collections.shuffle(candidatePositions, new Random(center.asLong()));
        candidatePositions.sort((a, b) -> {
            int bandA = (int) (Math.sqrt(a.getSquaredDistance(task.center)) / 2.0);
            int bandB = (int) (Math.sqrt(b.getSquaredDistance(task.center)) / 2.0);
            return Integer.compare(bandB, bandA);
        });

        int maxDisplays = Math.min(500, candidatePositions.size());

        for (int i = 0; i < candidatePositions.size(); i++) {
            BlockPos pos = candidatePositions.get(i);
            boolean isDisplay = (i < maxDisplays);

            if (isDisplay) {
                // PhÃ¢n bá»• Cáº§u Ngáº«u NhiÃªn 3D (Spherical Fibonacci + Chaos Noise) quanh orbCenter
                // GÃ³c nÃ¢ng phi tráº£i Ä‘á»u [-pi/2, +pi/2] Ä‘á»ƒ 50% khá»‘i báº¯n vá»t lÃªn TRÃŠN quáº£ cáº§u (cao hÆ¡n 3m - 14m)
                double goldenRatio = (1.0 + Math.sqrt(5.0)) / 2.0;
                double phi = Math.asin(1.0 - 2.0 * (i + 0.5) / maxDisplays);
                double theta = 2.0 * Math.PI * i / goldenRatio;
                double chaosJitter = SpatialMath.hash01(pos) * 0.35 - 0.175;
                phi = MathHelper.clamp(phi + chaosJitter, -Math.PI * 0.48, Math.PI * 0.48);

                double expandRadius = maxRadius * (1.35 + SpatialMath.hash01(pos) * 1.0);
                Vec3d sphereDir = new Vec3d(
                        Math.cos(phi) * Math.cos(theta),
                        Math.sin(phi),
                        Math.cos(phi) * Math.sin(theta)
                ).normalize();

                Vec3d targetWorldPos = orbCenter.add(sphereDir.multiply(expandRadius));
                // Äáº£m báº£o cÃ¡c máº£nh vá»¡ ná»­a dÆ°á»›i cáº§u khÃ´ng bá»‹ chÃ´n sÃ¢u dÆ°á»›i lÃ²ng Ä‘áº¥t mÃ  lÆ¡ lá»­ng quanh miá»‡ng há»‘
                double minHoverY = center.getY() + 1.2 + SpatialMath.hash01(pos.up()) * 3.0;
                if (targetWorldPos.y < minHoverY) {
                    targetWorldPos = new Vec3d(
                            targetWorldPos.x,
                            minHoverY + (minHoverY - targetWorldPos.y) * 0.25,
                            targetWorldPos.z
                    );
                }
                Vec3d fullExpansionVec = targetWorldPos.subtract(Vec3d.ofCenter(pos));

                float hashVal = (float) SpatialMath.hash01(pos);
                // KÃ­ch thÆ°á»›c Block to rÃµ háº§m há»‘: 0.90x Ä‘áº¿n 1.45x
                float scale = 0.90f + hashVal * 0.55f;
                Quaternionf rot = new Quaternionf()
                        .rotateY((float) (hashVal * Math.PI * 2.0))
                        .rotateX((float) Math.toRadians(25.0 + hashVal * 30.0));

                int glowColor = (hashVal > 0.5f) ? 0x00F0FF : 0xA855F7;

                // BÃ³c khá»‘i á»Ÿ cháº¿ Ä‘á»™ CUSTOM_SCRIPTED Ä‘á»ƒ script kiá»ƒm soÃ¡t 100%
                SpatialDisplacementManager.displaceBlock(
                        world,
                        pos,
                        new Vector3f(0, 0, 0),
                        rot,
                        new Vector3f(scale, scale, scale),
                        135,
                        glowColor,
                        true,
                        DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
                        null,
                        center.toCenterPos(),
                        maxRadius
                );

                task.displayedPositions.add(pos);
                task.fullExpansionMap.put(pos, fullExpansionVec);
                task.baseRotationMap.put(pos, rot);
                task.blockScaleMap.put(pos, scale);
            } else {
                // CÃ¡c khá»‘i bÃªn trong lÃ²ng há»‘ chá»‰ bÃ³c sang AIR khÃ´ng táº¡o display
                SpatialDisplacementManager.displaceBlock(
                        world,
                        pos,
                        new Vector3f(0, 0, 0),
                        new Quaternionf(),
                        new Vector3f(1.0f, 1.0f, 1.0f),
                        135,
                        0,
                        false,
                        DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
                        null,
                        center.toCenterPos(),
                        maxRadius
                );
                task.interiorPositions.add(pos);
            }
        }

        // 3. Audio & Visuals ná»• khai cuá»™c
        world.spawnParticles(ParticleTypes.FLASH, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.0);
        world.spawnParticles(ParticleTypes.SONIC_BOOM, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.0);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 3, 1.0, 1.0, 1.0, 0.0);
        world.spawnParticles(new DustParticleEffect(new Vector3f(0.0f, 0.95f, 1.0f), 1.8f),
                center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 60, 1.5, 1.5, 1.5, 0.45);

        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 3.5f, 0.6f);
        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 3.0f, 0.8f);
        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.PLAYERS, 2.8f, 0.7f);

        // Äáº©y quÃ¡i váº­t xung quanh
        Box blastBox = new Box(center).expand(maxRadius * 1.25);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, blastBox, ent -> true)) {
            Vec3d delta = e.getPos().subtract(center.toCenterPos());
            double dist = delta.length();
            if (dist < maxRadius * 1.25 && dist > 0.1) {
                double force = (1.0 - (dist / (maxRadius * 1.25))) * 2.2;
                Vec3d push = delta.normalize().multiply(force).add(0, 0.55, 0);
                e.addVelocity(push.x, push.y, push.z);
                e.velocityModified = true;
            }
        }

        ImpactFxPayload detPayload = new ImpactFxPayload(
                task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                ImpactFxPayload.TYPE_DETONATION, 112, (float) Math.max(1.0, maxRadius / 10.0)
        );
        CinematicFxPayload detCinematic = new CinematicFxPayload(
                CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                0.65f, 16
        );
        for (ServerPlayerEntity p : PlayerLookup.around(world, task.orbCenter, 96.0)) {
            ServerPlayNetworking.send(p, detPayload);
            ServerPlayNetworking.send(p, detCinematic);
        }

        ACTIVE_TASKS.add(task);
    }

    public static void tick(ServerWorld world) {
        Iterator<SingularityTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            SingularityTask task = it.next();
            if (task.world != world) continue;

            task.ageTicks++;

            // =========================================================================
            // PHA 1: Ná»¨T Vá»  & BUNG NÄ‚NG LÆ¯á»¢NG (Ticks 1..22) - MÆ¯á»¢T MÃ€ Vá»šI CUBIC EASE-OUT
            // =========================================================================
            if (task.ageTicks <= 22) {
                if (task.ageTicks % 2 == 0) {
                    float t = task.ageTicks / 22.0f;
                    double p = 1.0 - Math.pow(1.0 - t, 3.0);
                    updateBlockPositions(task, p, 2, false);
                }
            }

            // =========================================================================
            // PHA 2: LÃ€M CHáº¬M THá»œI GIAN Cá»°C Háº N / BULLET-TIME DRIFT & TETHER (Ticks 23..105)
            // =========================================================================
            else if (task.ageTicks <= 105) {
                // TrÃ´i dáº¡t vÃ  xoay há»—n loáº¡n (Chaotic Tumble) trong khÃ´ng gian 3D
                if ((task.ageTicks - 22) % 4 == 0) {
                    double p = 1.0 + 0.08 * ((task.ageTicks - 22) / 83.0);
                    updateBlockPositions(task, p, 4, true);
                }

                // DÃ¢y nÄƒng lÆ°á»£ng Ä‘iá»‡n siÃªu má»ng ná»‘i tá»« tÃ¢m orbCenter tá»›i cÃ¡c khá»‘i lÆ¡ lá»­ng xung quanh
                int tetherBatch = Math.min(14, task.displayedPositions.size());
                for (int k = 0; k < tetherBatch; k++) {
                    int idx = (task.ageTicks * 5 + k * 23) % task.displayedPositions.size();
                    BlockPos pPos = task.displayedPositions.get(idx);
                    DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pPos);
                    if (rec != null) {
                        Vec3d blockWorld = Vec3d.ofCenter(pPos).add(rec.getCurrentOffset());
                        Vec3d ray = blockWorld.subtract(task.orbCenter);
                        double rayLen = ray.length();
                        if (rayLen > 0.5) {
                            int steps = Math.min(5, (int) (rayLen / 3.0) + 2);
                            for (int s = 1; s < steps; s++) {
                                double frac = (double) s / steps;
                                Vec3d pt = task.orbCenter.add(ray.multiply(frac));
                                if ((k + s) % 2 == 0) {
                                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0.01);
                                } else {
                                    world.spawnParticles(new DustParticleEffect(new Vector3f(0.15f, 0.92f, 1.0f), 0.5f),
                                            pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0.0);
                                }
                            }
                        }
                    }
                }

                // Hiá»‡u á»©ng Ã¡nh sÃ¡ng ngÆ°ng Ä‘á»ng thá»i gian quanh quáº£ cáº§u
                if (task.ageTicks % 6 == 0) {
                    world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                            task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 6, 0.4, 0.4, 0.4, 0.05);
                    world.spawnParticles(ParticleTypes.END_ROD,
                            task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 2, task.maxRadius * 0.4, 1.2, task.maxRadius * 0.4, 0.01);
                }
            }

            // =========================================================================
            // PHA 3: CO RÃšT & THU Há»’I NGÆ¯á»¢C (Ticks 106..132) - Ná»˜I SUY MÆ¯á»¢T MÃ€
            // =========================================================================
            else {
                // Ticks 106..111: Anticipation (KÃ©o dáº¡t thÃªm +6% láº¥y Ä‘Ã )
                if (task.ageTicks == 106) {
                    world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 3.0f, 1.5f);
                    updateBlockPositions(task, 1.10, 5, false);
                }

                // Ticks 112..130: Vacuum Snap-Back (Quáº£ cáº§u co nhá», cÃ¡c khá»‘i lÆ°á»›t mÆ°á»£t vá» 0,0,0 trong 18 ticks)
                if (task.ageTicks == 112 && !task.snappedBack) {
                    task.snappedBack = true;

                    world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.BLOCKS, 3.5f, 1.6f);
                    world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 3.0f, 1.8f);

                    // LÆ°á»›t mÆ°á»£t toÃ n bá»™ khá»‘i Ä‘Ã¡ vá» vá»‹ trÃ­ ban Ä‘áº§u trong 18 ticks
                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        float scale = task.blockScaleMap.getOrDefault(pos, 1.0f);
                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(18);
                        display.setTransformation(SpatialMath.createPivotTransform(Vec3d.ZERO, new Quaternionf(), scale));
                    }
                }

                // Tick 132: Dá»n dáº¹p thá»±c thá»ƒ vÃ  khÃ´i phá»¥c Ä‘á»‹a hÃ¬nh 1:1
                if (task.ageTicks >= 132 && !task.restored) {
                    task.restored = true;

                    // KhÃ´i phá»¥c 2-pass toÃ n bá»™ khá»‘i khá»›p kÃ­n miá»‡ng há»‘
                    List<DisplacedBlockRecord> toRestore = new ArrayList<>();
                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (rec != null) toRestore.add(rec);
                    }
                    for (BlockPos pos : task.interiorPositions) {
                        DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (rec != null) toRestore.add(rec);
                    }

                    if (!toRestore.isEmpty()) {
                        SpatialDisplacementManager.executeTwoPassRestoration(world, toRestore);
                    }

                    world.playSound(null, task.center, SoundEvents.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.BLOCKS, 2.5f, 1.2f);
                    it.remove();
                }
            }
        }
    }

    private static void updateBlockPositions(SingularityTask task, double progress, int duration, boolean chaoticTumble) {
        for (BlockPos pos : task.displayedPositions) {
            DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(task.world.getRegistryKey(), pos);
            if (record == null || record.displayEntityUuid == null) continue;

            BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
            if (display == null || !display.isAlive()) continue;

            Vec3d fullExp = task.fullExpansionMap.getOrDefault(pos, Vec3d.ZERO);
            Quaternionf rot = task.baseRotationMap.getOrDefault(pos, new Quaternionf());
            float scale = task.blockScaleMap.getOrDefault(pos, 1.0f);

            Vec3d curOffset;
            Quaternionf currentRot;

            if (chaoticTumble) {
                // Chuyá»ƒn Ä‘á»™ng xoay há»—n loáº¡n (Chaotic Tumble) quanh trá»¥c ngáº«u nhiÃªn riÃªng
                float tumbleAngle = (float) (task.ageTicks * 0.05 + SpatialMath.hash01(pos) * 6.28);
                Vector3f tumbleAxis = new Vector3f(
                        (float) (SpatialMath.hash01(pos.north()) - 0.5),
                        (float) (SpatialMath.hash01(pos.up()) - 0.5),
                        (float) (SpatialMath.hash01(pos.east()) - 0.5)
                ).normalize();
                currentRot = new Quaternionf(rot).rotateAxis(tumbleAngle, tumbleAxis.x, tumbleAxis.y, tumbleAxis.z);

                // Dao Ä‘á»™ng nháº¹ báº¥t quy táº¯c
                double bob = Math.sin(task.ageTicks * 0.12 + SpatialMath.hash01(pos) * 6.28) * 0.28;
                curOffset = fullExp.multiply(progress).add(0, bob, 0);
            } else {
                curOffset = fullExp.multiply(progress);
                currentRot = rot;
            }

            display.setStartInterpolation(0);
            display.setInterpolationDuration(duration);
            display.setTransformation(SpatialMath.createPivotTransform(curOffset, currentRot, scale));
        }
    }

    public static int getActiveTaskCount() {
        return ACTIVE_TASKS.size();
    }

    public static void clearAllTasks() {
        ACTIVE_TASKS.clear();
    }
}

```

# File: SpatialPathWalker.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\skill\SpatialPathWalker.java
```java
package net.godwand.skill;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.network.ImpactFxPayload;
import net.godwand.spatial.DisplacedBlockRecord;
import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpatialPathWalker {
    private static final Set<UUID> ACTIVE_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final java.util.Map<UUID, Double> LAST_GROUND_Y = new ConcurrentHashMap<>();
    private static final java.util.Map<UUID, Vec3d> LAST_PLAYER_POS = new ConcurrentHashMap<>();
    private static final java.util.Map<UUID, Double> CURRENT_FORWARD_LEAD = new ConcurrentHashMap<>();
    private static final java.util.Map<UUID, Double> CURRENT_PLAYER_SPEED = new ConcurrentHashMap<>();

    public static boolean isActive(PlayerEntity player) {
        return ACTIVE_PLAYERS.contains(player.getUuid());
    }

    public static void toggle(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        if (ACTIVE_PLAYERS.contains(player.getUuid())) {
            ACTIVE_PLAYERS.remove(player.getUuid());
            LAST_GROUND_Y.remove(player.getUuid());
            LAST_PLAYER_POS.remove(player.getUuid());
            CURRENT_FORWARD_LEAD.remove(player.getUuid());
            CURRENT_PLAYER_SPEED.remove(player.getUuid());
            player.sendMessage(Text.literal("Â§cÂ§l[âœ¦ SPATIAL PATH: COLLAPSED âœ¦]"), true);

            // Explosive Deactivation Impact
            world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getEyeY(), player.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
            world.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 40, 1.2, 0.8, 1.2, 0.25);
            world.spawnParticles(new DustParticleEffect(new Vector3f(0.25f, 0.88f, 1.0f), 1.2f),
                    player.getX(), player.getY() + 1.0, player.getZ(), 30, 1.5, 0.8, 1.5, 0.2);

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 2.5f, 0.75f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 2.0f, 1.5f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 2.0f, 1.2f);

            // Push nearby entities away
            for (net.minecraft.entity.Entity e : world.getOtherEntities(player, player.getBoundingBox().expand(5.0))) {
                Vec3d diff = e.getPos().subtract(player.getPos()).normalize().multiply(0.95).add(0, 0.28, 0);
                e.addVelocity(diff.x, diff.y, diff.z);
                e.velocityModified = true;
            }

            // Immediately begin 3-Phase Magnetic Snap Reassembly for all blocks in player's aura
            SpatialDisplacementManager.collapsePlayerAura(player.getUuid());
            SpatialDisplacementManager.collapseNearbyDisplacements(world.getRegistryKey(), player.getBlockPos(), 14.0);

            // Trigger Tactical Collapse Post-FX
            ImpactFxPayload colPayload = new ImpactFxPayload(
                    player.getX(), player.getEyeY(), player.getZ(),
                    ImpactFxPayload.TYPE_COLLAPSE, 8, 1.0f
            );
            for (ServerPlayerEntity p : PlayerLookup.around(world, player.getPos(), 48.0)) {
                ServerPlayNetworking.send(p, colPayload);
            }
        } else {
            ACTIVE_PLAYERS.add(player.getUuid());
            player.sendMessage(Text.literal("Â§dÂ§l[âœ¦ SPATIAL PATH: Â§aACTIVE Â§dÂ§lâœ¦]"), true);

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.8f, 1.4f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 2.5f, 1.1f);
            world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.5, 0.5, 0.5, 0.15);
            world.spawnParticles(new DustParticleEffect(new Vector3f(0.3f, 0.9f, 1.0f), 1.0f),
                    player.getX(), player.getY() + 1.0, player.getZ(), 25, 0.5, 0.8, 0.5, 0.1);
        }
    }

    public static void deactivate(PlayerEntity player) {
        ACTIVE_PLAYERS.remove(player.getUuid());
        LAST_GROUND_Y.remove(player.getUuid());
        LAST_PLAYER_POS.remove(player.getUuid());
        CURRENT_FORWARD_LEAD.remove(player.getUuid());
        CURRENT_PLAYER_SPEED.remove(player.getUuid());
        SpatialDisplacementManager.collapsePlayerAura(player.getUuid());
    }

    public static double updatePlayerForwardLead(ServerPlayerEntity player) {
        Vec3d currentPos = player.getPos();
        Vec3d lastPos = LAST_PLAYER_POS.getOrDefault(player.getUuid(), currentPos);
        LAST_PLAYER_POS.put(player.getUuid(), currentPos);

        Vec3d deltaHoriz = new Vec3d(currentPos.x - lastPos.x, 0, currentPos.z - lastPos.z);
        double speed = deltaHoriz.length();
        CURRENT_PLAYER_SPEED.put(player.getUuid(), speed);
        // Náº¿u tá»‘c Ä‘á»™ dá»‹ch chuyá»ƒn há»£p lá»‡ (< 4.0 blocks/tick Ä‘á»ƒ loáº¡i trá»« teleport)
        double boost = (speed > 0.02 && speed < 4.0) ? Math.min(3.2, speed * 7.0) : 0.0;
        if (player.isSprinting() && boost < 1.2) {
            boost = 1.2;
        }
        double lead = 1.0 + boost;
        CURRENT_FORWARD_LEAD.put(player.getUuid(), lead);
        return lead;
    }

    public static double getPlayerSpeed(ServerPlayerEntity player) {
        return CURRENT_PLAYER_SPEED.getOrDefault(player.getUuid(), 0.0);
    }

    public static double getPlayerForwardLead(ServerPlayerEntity player) {
        return CURRENT_FORWARD_LEAD.getOrDefault(player.getUuid(), 1.0);
    }

    public static void tickPlayer(ServerPlayerEntity player) {
        if (!isActive(player)) return;

        ServerWorld world = player.getServerWorld();
        Vec3d playerPos = player.getPos();
        Vec3d chestPos = playerPos.add(0, 1.1, 0);
        Vec3d lookVec = player.getRotationVec(1.0f);
        Vec3d lookDirHoriz = new Vec3d(lookVec.x, 0, lookVec.z);
        if (lookDirHoriz.lengthSquared() < 0.001) {
            lookDirHoriz = new Vec3d(1, 0, 0);
        } else {
            lookDirHoriz = lookDirHoriz.normalize();
        }

        boolean allowDown = player.getAbilities().flying && player.getPitch() > 45.0f;

        if (player.isOnGround()) {
            LAST_GROUND_Y.put(player.getUuid(), player.getY());
        }
        double baseGroundY = player.isOnGround() ? player.getY() : LAST_GROUND_Y.getOrDefault(player.getUuid(), player.getY());
        int floorY = (int) Math.floor(baseGroundY);

        double forwardLead = updatePlayerForwardLead(player);
        double auraRadius = 2.1;
        double maxRange = 3.3 + forwardLead + 0.8;

        int minX = MathHelper.floor(playerPos.x - maxRange);
        int maxX = MathHelper.floor(playerPos.x + maxRange);
        int minY = allowDown ? MathHelper.floor(playerPos.y - 2.2) : floorY;
        int maxY = MathHelper.floor(playerPos.y + 2.8);
        int minZ = MathHelper.floor(playerPos.z - maxRange);
        int maxZ = MathHelper.floor(playerPos.z + maxRange);

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutablePos.set(x, y, z);

                    // Absolute Ground Safety Rule
                    if (!allowDown && y < floorY) continue;

                    // Footing Protection
                    if (!allowDown && Math.abs(x + 0.5 - playerPos.x) <= 0.85 && Math.abs(z + 0.5 - playerPos.z) <= 0.85 && y <= floorY) {
                        continue;
                    }

                    Vec3d blockCenter = Vec3d.ofCenter(mutablePos);

                    // QuÃ©t 2 táº§ng (Táº§ng trong <= 2.1m vÃ²m cao, Táº§ng ngoÃ i <= 3.3m sÃ³ng Ä‘á»‹a cháº¥n)
                    double spineDist = SpatialMath.distanceToAuraSpine(blockCenter, chestPos, lookDirHoriz, forwardLead);
                    if (spineDist > 3.3) continue;

                    // Distance Hysteresis: keep open and reset restore tick if already displaced
                    if (SpatialDisplacementManager.isPositionDisplaced(world.getRegistryKey(), mutablePos)) {
                        SpatialDisplacementManager.refreshDisplacement(world.getRegistryKey(), mutablePos, 100);
                        continue;
                    }

                    BlockState state = world.getBlockState(mutablePos);
                    if (!SpatialDisplacementManager.canDisplace(world, mutablePos, state)) continue;

                    // Compute dynamic aura offset from SpatialMath
                    Vec3d auraDisp = SpatialMath.computeDynamicAuraOffset(mutablePos, playerPos, lookDirHoriz, auraRadius, 0);
                    Vector3f targetDisp = new Vector3f((float) auraDisp.x, (float) auraDisp.y, (float) auraDisp.z);

                    Quaternionf rotation = SpatialMath.computeArchwayRotation(mutablePos, playerPos, lookDirHoriz, 0);

                    // Thu nhá» nháº¹ kÃ­ch thÆ°á»›c má»—i block (0.82f) Ä‘á»ƒ táº¡o khe há»Ÿ ma phÃ¡p giá»¯a cÃ¡c viÃªn gáº¡ch thay vÃ¬ dÃ­nh bá»‡t vÃ o nhau!
                    float blockScale = 0.78f + (float) (SpatialMath.hash01(mutablePos) * 0.14);
                    Vector3f scale = new Vector3f(blockScale, blockScale, blockScale);

                    // Xen káº½ mÃ u viá»n Cyan (0x00F5FF) vÃ  Amethyst Purple (0xA855F7)
                    int glowColor = ((x + y + z) & 1) == 0 ? 0x00F5FF : 0xA855F7;

                    SpatialDisplacementManager.displaceBlock(
                            world,
                            mutablePos.toImmutable(),
                            targetDisp,
                            rotation,
                            scale,
                            100,
                            glowColor,
                            true,
                            DisplacedBlockRecord.DisplacementMode.PATH_WALKER,
                            player.getUuid(),
                            null,
                            0.0
                    );

                    // Ambient shimmer particle
                    if (((x ^ z) & 7) == 0) {
                        world.spawnParticles(ParticleTypes.END_ROD, blockCenter.x, blockCenter.y, blockCenter.z, 1, 0.1, 0.1, 0.1, 0.01);
                    }
                }
            }
        }

        // VÃ²ng háº¡t ma phÃ¡p xoay quanh chÃ¢n ngÆ°á»i chÆ¡i má»—i 2 ticks
        if (player.age % 2 == 0) {
            double angle = (player.age * 0.25) % (Math.PI * 2.0);
            double rx = Math.cos(angle) * 2.1;
            double rz = Math.sin(angle) * 2.1;
            world.spawnParticles(ParticleTypes.END_ROD, playerPos.x + rx, playerPos.y + 0.2, playerPos.z + rz, 1, 0, 0.05, 0, 0.0);
            world.spawnParticles(ParticleTypes.ENCHANT, playerPos.x - rx, playerPos.y + 0.3, playerPos.z - rz, 2, 0.1, 0.1, 0.1, 0.05);
        }
    }
}

```

# File: SupernovaManager.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\skill\SupernovaManager.java
```java
package net.godwand.skill;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.spatial.DisplacedBlockRecord;
import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SupernovaManager {

    public static final int DETONATION_TICK = 360;
    public static final int MAX_LIFETIME_TICKS = 500;

    public static class SupernovaTask {
        public final ServerWorld world;
        public final ServerPlayerEntity caster;
        public final BlockPos groundCenter;
        public final Vec3d corePos;
        public final double maxRadius = 38.0; // Calamity scale!

        public int ageTicks = 0;
        public float swirlAngle = 0.0f;
        public boolean exploded = false;

        // Progressive Peeling & Accretion Escalation (Ticks 1..280)
        public final List<BlockPos> candidatePositions = new ArrayList<>();
        public final Set<BlockPos> peeledPositions = new HashSet<>();
        public final Map<BlockPos, Integer> peelTickMap = new HashMap<>();

        public final List<BlockPos> displayedPositions = new ArrayList<>();
        public final List<BlockPos> interiorPositions = new ArrayList<>();
        public final Set<BlockPos> swallowedPositions = new HashSet<>();

        public final Map<BlockPos, Vec3d> stage1UpliftMap = new HashMap<>();
        public final Map<BlockPos, Quaternionf> baseRotationMap = new HashMap<>();
        public final Map<BlockPos, Float> blockScaleMap = new HashMap<>();

        // Batched permanent crater carving & aftermath
        public final List<BlockPos> craterCarveBatch = new ArrayList<>();
        public int craterIndex = 0;

        public SupernovaTask(ServerWorld world, ServerPlayerEntity caster, BlockPos groundCenter, Vec3d corePos) {
            this.world = world;
            this.caster = caster;
            this.groundCenter = groundCenter.toImmutable();
            this.corePos = corePos;
        }
    }

    private static final Queue<SupernovaTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();

    public static void cast(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        HitResult hit = player.raycast(45.0, 0.0f, false);
        BlockPos groundCenter;
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bHit) {
            groundCenter = bHit.getBlockPos();
        } else {
            Vec3d forward = player.getEyePos().add(player.getRotationVec(1.0f).multiply(24.0));
            groundCenter = BlockPos.ofFloored(forward);
            while (groundCenter.getY() > world.getBottomY() && world.getBlockState(groundCenter).isAir()) {
                groundCenter = groundCenter.down();
            }
        }

        // Há»‘ Ä‘en lÆ¡ lá»­ng cÃ¡ch máº·t Ä‘áº¥t 20.0m (Quy mÃ´ Calamity)
        Vec3d corePos = groundCenter.toCenterPos().add(0, 20.0, 0);
        SupernovaTask task = new SupernovaTask(world, player, groundCenter, corePos);

        // 1. KÃ­ch hoáº¡t Shader Há»‘ Äen 3D Ä‘á»™c quyá»n cá»§a ChiÃªu 4 (Thá»i lÆ°á»£ng 360 ticks = 18 giÃ¢y tÃ­ch tá»¥)
        // Báº¯t Ä‘áº§u tá»« 1 cháº¥m siÃªu nhá» (0.15m) ná»Ÿ to dáº§n Ä‘Æ¡n Ä‘iá»‡u liÃªn tá»¥c Ä‘áº¿n lÃºc ná»•!
        ImpactFxPayload blackHolePayload = new ImpactFxPayload(
                corePos.x, corePos.y, corePos.z,
                ImpactFxPayload.TYPE_BLACK_HOLE, DETONATION_TICK, 1.0f
        );
        for (ServerPlayerEntity p : PlayerLookup.around(world, corePos, 160.0)) {
            ServerPlayNetworking.send(p, blackHolePayload);
        }

        // 2. Thu tháº­p danh sÃ¡ch khá»‘i á»©ng viÃªn trong bÃ¡n kÃ­nh Calamity R = 38.0m
        // QUY Táº®C Cá»T LÃ•I: KHÃ”NG bÃ³c khá»‘i nÃ o á»Ÿ Tick 0. Khá»‘i sáº½ bÃ³c tá»« tá»« tá»« tÃ¢m lan ra ngoÃ i (tá»« vÃ i khá»‘i -> vÃ i chá»¥c -> vÃ i trÄƒm)
        int intR = (int) Math.ceil(task.maxRadius);
        double maxRadiusSq = task.maxRadius * task.maxRadius;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        List<BlockPos> candidates = new ArrayList<>();
        int groundY = groundCenter.getY();

        for (int x = -intR; x <= intR; x++) {
            for (int z = -intR; z <= intR; z++) {
                double dSq2D = x * x + z * z;
                if (dSq2D > maxRadiusSq) continue;
                double dist2D = Math.sqrt(dSq2D);
                int maxDepth = (int) Math.round((1.0 - (dist2D / task.maxRadius)) * 14.0);
                int minY = Math.max(world.getBottomY(), groundY - maxDepth);
                int maxY = Math.min(world.getTopY() - 1, groundY + 4);

                for (int y = maxY; y >= minY; y--) {
                    mutable.set(groundCenter.getX() + x, y, groundCenter.getZ() + z);
                    BlockState state = world.getBlockState(mutable);
                    if (state.isAir() || state.getBlock() == Blocks.BEDROCK) continue;
                    if (!SpatialDisplacementManager.canDisplace(world, mutable, state)) continue;
                    candidates.add(mutable.toImmutable());
                }
            }
        }

        // Sáº¯p xáº¿p bÃ³c tÃ¡ch: Tá»« TÃ‚M lan dáº§n ra ngoÃ i (khoáº£ng cÃ¡ch 2D tÄƒng dáº§n), lá»›p máº·t trÃªn bÃ³c trÆ°á»›c
        candidates.sort((a, b) -> {
            double distA = a.getSquaredDistance(task.groundCenter.getX(), a.getY(), task.groundCenter.getZ());
            double distB = b.getSquaredDistance(task.groundCenter.getX(), b.getY(), task.groundCenter.getZ());
            int distComp = Double.compare(distA, distB);
            if (distComp != 0) return distComp;
            return Integer.compare(b.getY(), a.getY());
        });

        task.candidatePositions.addAll(candidates);

        // Ã‚m thanh khá»Ÿi sinh Há»‘ Äen Táº­n Tháº¿ (Tráº§m bá»•ng, ngÃ¢n vang)
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 4.0f, 0.45f);
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 3.5f, 0.5f);
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 3.5f, 0.45f);

        world.spawnParticles(ParticleTypes.SONIC_BOOM, corePos.x, corePos.y, corePos.z, 2, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.FLASH, corePos.x, corePos.y, corePos.z, 3, 0.2, 0.2, 0.2, 0.0);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, corePos.x, corePos.y, corePos.z, 80, 1.5, 1.5, 1.5, 0.35);

        ACTIVE_TASKS.add(task);
    }

    public static void tick(ServerWorld world) {
        Iterator<SupernovaTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            SupernovaTask task = it.next();
            if (task.world != world) continue;

            task.ageTicks++;

            // =========================================================================
            // GIAI ÄOáº N 1 & 2: BÃ“C Äáº¤T LÅ¨Y TIáº¾N & 3 VÃ€NH ÄAI Bá»’I Tá»¤ (Ticks 1..280 / 14s)
            // Tá»« 1 vÃ i block -> vÃ i chá»¥c block -> vÃ i trÄƒm block, tá»‘c Ä‘á»™ tÄƒng dáº§n mÆ°á»£t mÃ 
            // =========================================================================
            if (task.ageTicks <= 280) {
                // BÃ¡n kÃ­nh bÃ³c tÃ¡ch má»Ÿ rá»™ng theo Ä‘Æ°á»ng cong lÅ©y thá»«a:
                // Ticks 1..60: BÃ¡n kÃ­nh nhá» (1m - 4m) -> Chá»‰ vÃ i block nÃ¢ng lÃªn nháº¹
                // Ticks 61..180: BÃ¡n kÃ­nh trung (5m - 20m) -> VÃ i chá»¥c block
                // Ticks 181..280: BÃ¡n kÃ­nh Ä‘áº¡i (20m - 38m) -> VÃ i trÄƒm block
                double progress = task.ageTicks / 280.0;
                double currentRadius = task.maxRadius * Math.pow(progress, 1.4);
                double currentRadiusSq = currentRadius * currentRadius;

                int peeledThisTick = 0;
                for (BlockPos pos : task.candidatePositions) {
                    if (task.peeledPositions.contains(pos)) continue;

                    double dx = pos.getX() - task.groundCenter.getX();
                    double dz = pos.getZ() - task.groundCenter.getZ();
                    if (dx * dx + dz * dz <= currentRadiusSq) {
                        task.peeledPositions.add(pos);
                        task.peelTickMap.put(pos, task.ageTicks);
                        peeledThisTick++;

                        // Tá»‘i Ä‘a 450 khá»‘i hiá»ƒn thá»‹ BlockDisplayEntity
                        if (task.displayedPositions.size() < 450) {
                            float hash = (float) SpatialMath.hash01(pos);
                            float liftY = 0.6f + hash * 2.8f;
                            Vec3d stage1Lift = new Vec3d((hash - 0.5) * 0.5, liftY, (SpatialMath.hash01(pos.up()) - 0.5) * 0.5);

                            Quaternionf rot = new Quaternionf()
                                    .rotateY((float) (hash * Math.PI * 2.0))
                                    .rotateX((float) Math.toRadians(20.0 + hash * 25.0));
                            float scale = 0.85f + hash * 0.55f;

                            // KHÃ”NG DÃ™NG setGlowing, chá»‰ dÃ¹ng Brightness(15, 15)
                            SpatialDisplacementManager.displaceBlock(
                                    world,
                                    pos,
                                    new Vector3f(0, 0, 0),
                                    rot,
                                    new Vector3f(scale, scale, scale),
                                    550,
                                    0,
                                    true,
                                    DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
                                    task.caster.getUuid(),
                                    null,
                                    0.0
                            );

                            DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                            if (rec != null) {
                                rec.permanentDestruction = true;
                            }

                            task.displayedPositions.add(pos);
                            task.stage1UpliftMap.put(pos, stage1Lift);
                            task.baseRotationMap.put(pos, rot);
                            task.blockScaleMap.put(pos, scale);

                            if (peeledThisTick <= 6) {
                                world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                        pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                                        1, 0, 0.06, 0, 0.02);
                            }
                        } else {
                            // CÃ¡c khá»‘i lÃ²ng Ä‘áº¥t bÃªn trong: ÄÃ o thÃ nh AIR khÃ´ng tá»‘n Entity
                            SpatialDisplacementManager.displaceBlock(
                                    world,
                                    pos,
                                    new Vector3f(0, 0, 0),
                                    new Quaternionf(),
                                    new Vector3f(1.0f, 1.0f, 1.0f),
                                    550,
                                    0,
                                    false,
                                    DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
                                    null,
                                    null,
                                    0.0
                            );

                            DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                            if (rec != null) {
                                rec.permanentDestruction = true;
                            }

                            task.interiorPositions.add(pos);
                        }
                    }
                }

                // Tá»‘c Ä‘á»™ gÃ³c tÄƒng dáº§n mÆ°á»£t mÃ  theo hÃ m thá»i gian (khÃ´ng giáº­t Ä‘á»™t ngá»™t)
                float overallProgress = task.ageTicks / 340.0f;
                float angularSpeed = 0.004f + (float) Math.pow(overallProgress, 2.0) * 0.075f;
                task.swirlAngle += angularSpeed;

                // Cáº­p nháº­t chuyá»ƒn Ä‘á»™ng ná»™i suy cho toÃ n bá»™ khá»‘i hiá»ƒn thá»‹ má»—i 2 ticks
                if (task.ageTicks % 2 == 0) {
                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        int peelTick = task.peelTickMap.getOrDefault(pos, 0);
                        int ticksSincePeel = task.ageTicks - peelTick;

                        Vec3d stage1Lift = task.stage1UpliftMap.getOrDefault(pos, Vec3d.ZERO);
                        Vec3d ringOffset = SpatialMath.computeCanopyVortexOffset(pos, task.corePos, task.maxRadius, task.swirlAngle, 1.0);

                        Vec3d finalOffset;
                        Quaternionf rot = new Quaternionf(task.baseRotationMap.getOrDefault(pos, new Quaternionf()));

                        if (ticksSincePeel < 35) {
                            // 35 ticks Ä‘áº§u sau khi bÃ³c: Nháº¥c bá»•ng tá»« tá»‘n khá»i máº·t Ä‘áº¥t
                            float liftProgress = ticksSincePeel / 35.0f;
                            float u = liftProgress * liftProgress;
                            finalOffset = stage1Lift.multiply(u);
                        } else {
                            // Sau 35 ticks: HÃ²a mÆ°á»£t vÃ o 3 vÃ nh Ä‘ai bá»“i tá»¥
                            float mergeProgress = Math.min(1.0f, (ticksSincePeel - 35) / 45.0f);
                            finalOffset = stage1Lift.lerp(ringOffset, mergeProgress);
                            rot.rotateY(task.swirlAngle * 0.85f);
                        }

                        float scale = task.blockScaleMap.getOrDefault(pos, 1.0f);
                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(2);
                        display.setTransformation(SpatialMath.createPivotTransform(finalOffset, rot, scale));
                    }
                }

                // Lá»±c hÃºt háº¥p dáº«n kÃ©o thá»±c thá»ƒ xung quanh vÃ o tÃ¢m há»‘ Ä‘en
                if (task.ageTicks % 6 == 0) {
                    Box suctionBox = new Box(task.groundCenter).expand(task.maxRadius * 1.35);
                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, suctionBox, ent -> ent != task.caster)) {
                        Vec3d pull = task.corePos.subtract(e.getPos()).normalize().multiply(0.25 + progress * 0.20);
                        e.addVelocity(pull.x, pull.y, pull.z);
                        e.velocityModified = true;
                    }
                }

                // Ã‚m thanh nhá»‹p Ä‘áº­p & cá»™ng hÆ°á»Ÿng tÄƒng dáº§n cao Ä‘á»™ theo thá»i gian
                if (task.ageTicks % 16 == 0) {
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 3.0f, 0.5f + (float) progress * 1.0f);
                }
                if (task.ageTicks % 40 == 0) {
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 3.0f, 0.6f + (float) progress * 0.8f);
                }
            }

            // =========================================================================
            // GIAI ÄOáº N 3: Sá»¤P Äá»” Háº¤P DáºªN & NUá»T CHá»¬NG SIÃŠU MÆ¯á»¢T (Ticks 281..340 / 3s)
            // Há»‘ Ä‘en giá»¯ nguyÃªn kÃ­ch thÆ°á»›c cá»±c Ä‘áº¡i (KHÃ”NG co rÃºt, KHÃ”NG dá»±t dá»±t)
            // ToÃ n bá»™ khá»‘i Ä‘Ã¡ xoáº¯n á»‘c lao tháº³ng vÃ o chÃ¢n trá»i sá»± kiá»‡n vÃ  thu nhá» dáº§n
            // =========================================================================
            else if (task.ageTicks <= 340) {
                if (task.ageTicks == 285) {
                    CinematicFxPayload compressCinematic = new CinematicFxPayload(
                            CinematicFxPayload.EVENT_SUPERNOVA_COMPRESS,
                            task.corePos.x, task.corePos.y, task.corePos.z,
                            0.75f, 50
                    );
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 160.0)) {
                        ServerPlayNetworking.send(p, compressCinematic);
                    }
                }

                float collapseProgress = (task.ageTicks - 280) / 60.0f; // 0.0 -> 1.0
                double radiusScale = Math.max(0.04, 1.0 - Math.pow(collapseProgress, 1.4) * 0.96);
                task.swirlAngle += 0.12f; // XoÃ¡y cá»±c nhanh

                if ((task.ageTicks - 280) % 2 == 0) {
                    for (BlockPos pos : task.displayedPositions) {
                        if (task.swallowedPositions.contains(pos)) continue;

                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        Vec3d ringOffset = SpatialMath.computeCanopyVortexOffset(pos, task.corePos, task.maxRadius, task.swirlAngle, radiusScale);
                        Vec3d displayWorldPos = Vec3d.ofCenter(pos).add(ringOffset);
                        double distToCore = displayWorldPos.distanceTo(task.corePos);

                        Quaternionf rot = new Quaternionf(task.baseRotationMap.getOrDefault(pos, new Quaternionf()))
                                .rotateY(task.swirlAngle);

                        // Khi cháº¡m vÃ o rÃ¬a ChÃ¢n trá»i sá»± kiá»‡n (< 4.8m): Thu nhá» mÆ°á»£t mÃ  vá» 0 (Spaghettification)
                        if (distToCore < 4.8 || collapseProgress > 0.90f) {
                            task.swallowedPositions.add(pos);
                            display.setStartInterpolation(0);
                            display.setInterpolationDuration(4);
                            display.setTransformation(SpatialMath.createPivotTransform(task.corePos.subtract(Vec3d.ofCenter(pos)), rot, 0.0f));
                        } else {
                            float scale = task.blockScaleMap.getOrDefault(pos, 1.0f) * (float) Math.max(0.12, radiusScale);
                            display.setStartInterpolation(0);
                            display.setInterpolationDuration(2);
                            display.setTransformation(SpatialMath.createPivotTransform(ringOffset, rot, scale));
                        }
                    }
                }

                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        task.corePos.x, task.corePos.y, task.corePos.z, 25, 2.0, 2.0, 2.0, 0.4);

                if (task.ageTicks % 8 == 0) {
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 4.0f, 1.8f);
                }
            }

            // =========================================================================
            // GIAI ÄOáº N 4: KHOáº¢NG Láº¶NG CHÃ‚N KHÃ”NG (VACUUM SILENCE) (Ticks 341..359 / 1s)
            // =========================================================================
            else if (task.ageTicks < DETONATION_TICK) {
                if (task.ageTicks == 341) {
                    CinematicFxPayload silencePayload = new CinematicFxPayload(
                            CinematicFxPayload.EVENT_VACUUM_SILENCE,
                            task.corePos.x, task.corePos.y, task.corePos.z,
                            1.0f, 20
                    );
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 160.0)) {
                        ServerPlayNetworking.send(p, silencePayload);
                    }
                }

                // Tuyá»‡t Ä‘á»‘i im láº·ng, Há»‘ Äen tá»a hÃ o quang chÃ³i lÃ²a trÆ°á»›c khi ná»•
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                        task.corePos.x, task.corePos.y, task.corePos.z, 30, 0.5, 0.5, 0.5, 0.9);
            }

            // =========================================================================
            // GIAI ÄOáº N 5: Äáº I Vá»¤ Ná»” SIÃŠU TÃ‚N TINH Táº I TICK 360
            // =========================================================================
            else if (task.ageTicks == DETONATION_TICK && !task.exploded) {
                task.exploded = true;

                // 1. KÃ­ch hoáº¡t Shader SiÃªu TÃ¢n Tinh (KÃ©o dÃ i 140 ticks / 7 giÃ¢y Aftermath vá»›i Cá»™t Náº¥m & Quáº£ Cáº§u Lá»­a)
                ImpactFxPayload novaPayload = new ImpactFxPayload(
                        task.corePos.x, task.corePos.y, task.corePos.z,
                        ImpactFxPayload.TYPE_SUPERNOVA, 140, 2.8f
                );
                CinematicFxPayload burstCinematic = new CinematicFxPayload(
                        CinematicFxPayload.EVENT_SUPERNOVA_BURST,
                        task.corePos.x, task.corePos.y, task.corePos.z,
                        1.0f, 60
                );
                for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 160.0)) {
                    ServerPlayNetworking.send(p, novaPayload);
                    ServerPlayNetworking.send(p, burstCinematic);
                }

                // 2. Ã‚m thanh táº­n tháº¿ Ä‘a táº§ng
                world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                        SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 6.0f, 0.5f);
                world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                        SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 5.5f, 0.5f);
                world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                        SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 5.0f, 0.75f);
                world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                        SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 5.0f, 0.5f);
                world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                        SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.PLAYERS, 5.0f, 0.5f);

                // 3. VFX vá»¥ ná»• chÃ³i lÃ²a
                world.spawnParticles(ParticleTypes.FLASH, task.corePos.x, task.corePos.y, task.corePos.z, 20, 1.5, 1.5, 1.5, 0.0);
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, task.corePos.x, task.corePos.y, task.corePos.z, 15, 3.0, 3.0, 3.0, 0.0);
                world.spawnParticles(ParticleTypes.SONIC_BOOM, task.corePos.x, task.corePos.y, task.corePos.z, 10, 2.0, 2.0, 2.0, 0.0);
                world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.65f, 0.15f), 3.5f),
                        task.corePos.x, task.corePos.y, task.corePos.z, 200, 8.0, 8.0, 8.0, 0.9);

                // 4. SÃ¡t thÆ°Æ¡ng Calamity há»§y diá»‡t (150 - 300 damage) vÃ  lá»±c háº¥t vÄƒng cá»±c máº¡nh trong bÃ¡n kÃ­nh 60m
                Box blastBox = new Box(task.groundCenter).expand(60.0);
                for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, blastBox, ent -> ent != task.caster)) {
                    double dist = e.getPos().distanceTo(task.corePos);
                    float dmg = (float) Math.max(50.0, (1.0 - (dist / 60.0)) * 250.0 + 50.0);
                    e.damage(world.getDamageSources().explosion(null, task.caster), dmg);

                    Vec3d push = e.getPos().subtract(task.corePos).normalize().multiply(3.8).add(0, 1.5, 0);
                    e.addVelocity(push.x, push.y, push.z);
                    e.velocityModified = true;
                }

                // 5. XÃ³a bá» hoÃ n toÃ n báº£n ghi Ä‘á»ƒ khÃ´ng bao giá» khÃ´i phá»¥c láº¡i máº·t Ä‘áº¥t
                for (BlockPos pos : task.displayedPositions) {
                    SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), pos);
                }
                for (BlockPos pos : task.interiorPositions) {
                    SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), pos);
                }

                // 6. Chuáº©n bá»‹ táº¡o Há»‘ sÃ¢u táº­n tháº¿ (Impact Crater bÃ¡n kÃ­nh 55m, sÃ¢u tá»›i 28m) - Há»‘ vá»±c tháº³m khá»•ng lá»“
                int craterR = 55;
                BlockPos.Mutable cMut = new BlockPos.Mutable();
                int centerGroundY = task.groundCenter.getY();

                for (int cx = -craterR; cx <= craterR; cx++) {
                    for (int cz = -craterR; cz <= craterR; cz++) {
                        double distSq = cx * cx + cz * cz;
                        if (distSq > craterR * craterR) continue;

                        double dist2D = Math.sqrt(distSq);
                        // BiÃªn há»‘ rÄƒng cÆ°a ngáº«u nhiÃªn tá»± nhiÃªn
                        double noise = (SpatialMath.hash01(new BlockPos(cx, 0, cz)) - 0.5) * 6.0;
                        double effectiveR = craterR + noise;
                        if (dist2D > effectiveR) continue;

                        // Äá»™ sÃ¢u cá»±c lá»›n: 28m táº¡i tÃ¢m, rÆ¡i dá»‘c theo hÃ m mÅ© 1.35
                        int depth = (int) Math.round((1.0 - Math.pow(Math.min(1.0, dist2D / effectiveR), 1.35)) * 28.0);

                        for (int cy = -depth; cy <= 4; cy++) {
                            cMut.set(task.groundCenter.getX() + cx, centerGroundY + cy, task.groundCenter.getZ() + cz);
                            if (cMut.getY() < world.getBottomY() || cMut.getY() >= world.getTopY()) continue;
                            task.craterCarveBatch.add(cMut.toImmutable());
                        }
                    }
                }

                // 7. PhÃ³ng 80 khá»‘i Ä‘Ã¡ nÃ³ng cháº£y vÃ  dung nham bay vá»t ra bÃ¡n kÃ­nh 50m - 60m
                Random rng = new Random(task.groundCenter.asLong());
                int fallingBlocksCount = 80;
                BlockState[] moltenStates = new BlockState[] {
                        Blocks.MAGMA_BLOCK.getDefaultState(),
                        Blocks.OBSIDIAN.getDefaultState(),
                        Blocks.CRYING_OBSIDIAN.getDefaultState(),
                        Blocks.BLACKSTONE.getDefaultState(),
                        Blocks.COBBLESTONE.getDefaultState()
                };

                for (int i = 0; i < fallingBlocksCount; i++) {
                    double fAngle = rng.nextDouble() * Math.PI * 2.0;
                    double fSpeed = 1.0 + rng.nextDouble() * 1.6;
                    double spawnX = task.groundCenter.getX() + 0.5 + Math.cos(fAngle) * 4.0;
                    double spawnY = task.groundCenter.getY() + 3.0;
                    double spawnZ = task.groundCenter.getZ() + 0.5 + Math.sin(fAngle) * 4.0;

                    BlockState flingState = moltenStates[rng.nextInt(moltenStates.length)];
                    FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world,
                            BlockPos.ofFloored(spawnX, spawnY, spawnZ),
                            flingState);
                    falling.setVelocity(Math.cos(fAngle) * fSpeed, 0.8 + rng.nextDouble() * 1.0, Math.sin(fAngle) * fSpeed);
                    falling.velocityModified = true;
                }
            }

            // =========================================================================
            // GIAI ÄOáº N 6: AFTERMATH & Cá»˜T Náº¤M KHá»”NG Lá»’ (Ticks 361..500 / 7 GIÃ‚Y SAU Ná»”)
            // =========================================================================
            else if (task.ageTicks > DETONATION_TICK) {
                // 1. ÄÃ o há»‘ táº­n tháº¿ chia theo máº» (Async / Batched Carving trong 20 sub-ticks)
                if (task.craterIndex < task.craterCarveBatch.size()) {
                    int batchSize = (task.craterCarveBatch.size() / 20) + 1;
                    int endIndex = Math.min(task.craterCarveBatch.size(), task.craterIndex + batchSize);
                    Random rng = new Random();

                    for (int i = task.craterIndex; i < endIndex; i++) {
                        BlockPos pos = task.craterCarveBatch.get(i);
                        BlockState currentState = world.getBlockState(pos);
                        if (currentState.getBlock() == Blocks.BEDROCK) continue;

                        double dist2D = Math.sqrt(pos.getSquaredDistance(task.groundCenter.getX(), pos.getY(), task.groundCenter.getZ()));
                        double noise = (SpatialMath.hash01(new BlockPos(pos.getX() - task.groundCenter.getX(), 0, pos.getZ() - task.groundCenter.getZ())) - 0.5) * 6.0;
                        double effectiveR = 55.0 + noise;
                        int depth = (int) Math.round((1.0 - Math.pow(Math.min(1.0, dist2D / effectiveR), 1.35)) * 28.0);
                        int floorY = task.groundCenter.getY() - depth;

                        // 1. Táº¤T Cáº¢ CÃC KHá»I PHÃA TRÃŠN ÄÃY Há» Äá»€U Bá»Š Bá»C HÆ I HOÃ€N TOÃ€N THÃ€NH KHÃ”NG KHÃ (AIR)!
                        if (pos.getY() > floorY) {
                            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
                        }
                        // 2. Táº I ÄÃšNG ÄÃY Há» (pos.getY() == floorY):
                        // CHá»ˆ nung cháº£y náº¿u khá»‘i Ä‘Ã³ Vá»N DÄ¨ ÄANG LÃ€ Äáº¤T ÄÃ Äáº¶C (khÃ´ng tá»± sinh block trÃªn khÃ´ng trung)!
                        else if (pos.getY() == floorY && !currentState.isAir()) {
                            float rVal = rng.nextFloat();
                            BlockState scorchState;
                            if (rVal < 0.20f) {
                                scorchState = Blocks.MAGMA_BLOCK.getDefaultState();
                            } else if (rVal < 0.35f) {
                                scorchState = Blocks.CRYING_OBSIDIAN.getDefaultState();
                            } else if (rVal < 0.55f) {
                                scorchState = Blocks.OBSIDIAN.getDefaultState();
                            } else {
                                scorchState = (pos.getY() < 0) ? Blocks.DEEPSLATE.getDefaultState() : Blocks.BLACKSTONE.getDefaultState();
                            }
                            world.setBlockState(pos, scorchState, Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
                        }
                    }
                    task.craterIndex = endIndex;
                }

                // 2. Vá»¤ Ná»” HÃŒNH Náº¤M 3D (3D MUSHROOM CLOUD PARTICLE SYSTEM):
                // A. ThÃ¢n Náº¥m (Mushroom Stem): Cá»™t khÃ³i & lá»­a bá»‘c tháº³ng cuá»“n cuá»™n tá»« lÃ²ng há»‘ lÃªn cao 50m
                Random rng = new Random();
                for (int k = 0; k < 5; k++) {
                    double stemH = rng.nextDouble() * 48.0;
                    double stemR = 1.0 + (stemH / 48.0) * 3.5;
                    double sAngle = rng.nextDouble() * Math.PI * 2.0;
                    double sx = task.groundCenter.getX() + 0.5 + Math.cos(sAngle) * (rng.nextDouble() * stemR);
                    double sy = task.groundCenter.getY() + stemH;
                    double sz = task.groundCenter.getZ() + 0.5 + Math.sin(sAngle) * (rng.nextDouble() * stemR);

                    if (stemH < 12.0) {
                        world.spawnParticles(ParticleTypes.LAVA, sx, sy, sz, 1, 0, 0.1, 0, 0.02);
                        world.spawnParticles(ParticleTypes.FLAME, sx, sy, sz, 1, 0, 0.15, 0, 0.05);
                    }
                    world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, sx, sy, sz, 1, 0, 0.35, 0, 0.02);
                }

                // B. TÃ¡n Náº¥m (Mushroom Cap): ÄÃ¡m mÃ¢y náº¥m khá»•ng lá»“ bung tá»a hÃ¬nh vÃ nh xuyáº¿n á»Ÿ Ä‘á»™ cao 45m - 55m
                float capProgress = Math.min(1.0f, (task.ageTicks - DETONATION_TICK) / 140.0f);
                double capRadius = 6.0 + capProgress * 24.0; // BÃ¡n kÃ­nh tÃ¡n náº¥m xÃ²e rá»™ng tá»« 6m ra 30m!

                for (int j = 0; j < 8; j++) {
                    double cAngle = rng.nextDouble() * Math.PI * 2.0;
                    double rOffset = (rng.nextDouble() - 0.5) * 5.0;
                    double r = capRadius + rOffset;
                    double cx = task.groundCenter.getX() + 0.5 + Math.cos(cAngle) * r;
                    double cy = task.groundCenter.getY() + 48.0 + (rng.nextDouble() - 0.5) * 6.0;
                    double cz = task.groundCenter.getZ() + 0.5 + Math.sin(cAngle) * r;

                    double outVx = Math.cos(cAngle) * 0.18;
                    double outVz = Math.sin(cAngle) * 0.18;

                    world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy, cz, 1, outVx, 0.04, outVz, 0.02);
                    if (capProgress < 0.45f && rng.nextFloat() < 0.35f) {
                        world.spawnParticles(ParticleTypes.EXPLOSION, cx, cy, cz, 1, outVx, 0.02, outVz, 0.0);
                    }
                }

                // C. Ã‚m thanh sáº¥m rá»n vÃ  tiáº¿ng gáº§m vÅ© trá»¥ vang vá»ng xa dáº§n trong 7 giÃ¢y
                if ((task.ageTicks - DETONATION_TICK) % 25 == 0) {
                    float decayVol = Math.max(0.6f, 3.5f * (1.0f - capProgress));
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, decayVol, 0.5f);
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, decayVol * 0.8f, 0.45f);
                }

                // Káº¿t thÃºc toÃ n bá»™ ká»‹ch báº£n táº¡i Tick 500 (25 giÃ¢y trá»n váº¹n)
                if (task.ageTicks >= MAX_LIFETIME_TICKS) {
                    it.remove();
                }
            }
        }
    }

    public static int getActiveTaskCount() {
        return ACTIVE_TASKS.size();
    }

    public static void clearAllTasks() {
        ACTIVE_TASKS.clear();
    }
}

```

# File: DimensionPos.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\spatial\DimensionPos.java
```java
package net.godwand.spatial;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public record DimensionPos(RegistryKey<World> worldKey, BlockPos pos) {
    public DimensionPos {
        pos = pos.toImmutable();
    }
}

```

# File: DisplacedBlockRecord.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\spatial\DisplacedBlockRecord.java
```java
package net.godwand.spatial;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

public class DisplacedBlockRecord {
    public enum DisplacementMode {
        PATH_WALKER,
        HYSTERESIS_AURA,
        TIMED_LIFECYCLE,
        CANOPY_VORTEX,
        CUSTOM_SCRIPTED
    }

    public final BlockPos pos;
    public final RegistryKey<World> worldKey;
    public final BlockState originalState;
    public final BlockState state;
    public final NbtCompound blockEntityNbt;
    public final NbtCompound nbt;

    public UUID displayEntityUuid;
    public int ageTicks;
    public int maxLifetimeTicks;
    public int spawnTickDelay; // 1 tick handshake delay
    public final Vector3f homePos;
    public final Vector3f baseDisplacement;
    public final Vector3f targetDisplacement;
    public final Quaternionf targetRotation;
    public final Vector3f targetScale;
    public final boolean isFullCube;
    public boolean transformed;
    public int animationStage; // 0 = SPAWN, 1 = BURST, 2 = HOVER/DYNAMIC, 3 = RETURNING
    public float hoverYaw;

    // Refactor Fields:
    public final DisplacementMode mode;
    public UUID boundPlayerUuid;
    public int restoreTick; // 0 = active, 1..15 = restoring (3-Phase Magnetic Snap)
    public Vector3f currentOffset;
    public Quaternionf currentRotation;
    public Vector3f currentScale;
    public Vec3d blastCenter;
    public double blastRadius;
    public int openDurationTicks = 8;
    public boolean permanentDestruction = false;

    public DisplacedBlockRecord(BlockPos pos, RegistryKey<World> worldKey, BlockState state, NbtCompound nbt,
                                Vector3f targetDisplacement, Quaternionf targetRotation, Vector3f targetScale,
                                int maxLifetimeTicks, boolean isFullCube, DisplacementMode mode, UUID boundPlayerUuid) {
        this.pos = pos.toImmutable();
        this.worldKey = worldKey;
        this.originalState = state;
        this.state = state;
        this.blockEntityNbt = nbt;
        this.nbt = nbt;
        this.displayEntityUuid = null;
        this.ageTicks = 0;
        this.maxLifetimeTicks = maxLifetimeTicks;
        this.spawnTickDelay = 1;
        this.homePos = new Vector3f(this.pos.getX(), this.pos.getY(), this.pos.getZ());
        this.baseDisplacement = new Vector3f(targetDisplacement);
        this.targetDisplacement = targetDisplacement;
        this.targetRotation = targetRotation != null ? new Quaternionf(targetRotation) : new Quaternionf();
        this.targetScale = targetScale != null ? new Vector3f(targetScale) : new Vector3f(1.0f, 1.0f, 1.0f);
        this.isFullCube = isFullCube;
        this.transformed = false;
        this.animationStage = 0;
        this.hoverYaw = 0.0f;

        this.mode = mode != null ? mode : DisplacementMode.TIMED_LIFECYCLE;
        this.boundPlayerUuid = boundPlayerUuid;
        this.restoreTick = 0;
        this.currentOffset = new Vector3f(targetDisplacement);
        this.currentRotation = new Quaternionf(this.targetRotation);
        this.currentScale = new Vector3f(this.targetScale);
        this.blastCenter = null;
        this.blastRadius = 0.0;
    }

    public DisplacedBlockRecord(BlockPos pos, RegistryKey<World> worldKey, BlockState state, NbtCompound nbt,
                                Vector3f targetDisplacement, Quaternionf targetRotation, Vector3f targetScale,
                                int maxLifetimeTicks, boolean isFullCube) {
        this(pos, worldKey, state, nbt, targetDisplacement, targetRotation, targetScale, maxLifetimeTicks, isFullCube, DisplacementMode.TIMED_LIFECYCLE, null);
    }

    public DisplacedBlockRecord(BlockPos pos, RegistryKey<World> worldKey, BlockState state, NbtCompound nbt,
                                Vector3f targetDisplacement, Quaternionf targetRotation,
                                int maxLifetimeTicks, boolean isFullCube) {
        this(pos, worldKey, state, nbt, targetDisplacement, targetRotation, new Vector3f(1.0f, 1.0f, 1.0f), maxLifetimeTicks, isFullCube, DisplacementMode.TIMED_LIFECYCLE, null);
    }

    public DisplacedBlockRecord(BlockPos pos, BlockState state, NbtCompound nbt,
                                Vector3f targetDisplacement, Quaternionf targetRotation,
                                int maxLifetimeTicks, boolean isFullCube) {
        this(pos, World.OVERWORLD, state, nbt, targetDisplacement, targetRotation, new Vector3f(1.0f, 1.0f, 1.0f), maxLifetimeTicks, isFullCube, DisplacementMode.TIMED_LIFECYCLE, null);
    }

    public BlockPos pos() {
        return pos;
    }

    public BlockState originalState() {
        return originalState;
    }

    public BlockState state() {
        return state;
    }

    public NbtCompound blockEntityNbt() {
        return blockEntityNbt;
    }

    public NbtCompound nbt() {
        return nbt;
    }

    public Vec3d getCurrentOffset() {
        return new Vec3d(currentOffset.x, currentOffset.y, currentOffset.z);
    }

    public void beginRestoration() {
        if (this.restoreTick == 0) {
            this.restoreTick = 1;
            this.animationStage = 3;
        }
    }

    public void interruptRestoration() {
        if (this.restoreTick > 0) {
            this.restoreTick = 0;
            this.animationStage = 2; // Return to hovering / living aura
        }
    }

    public void resetRestoreTick() {
        this.restoreTick = 0;
    }

    public boolean isRestoring() {
        return this.restoreTick > 0;
    }
}

```

# File: DisplacementPersistentState.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\spatial\DisplacementPersistentState.java
```java
package net.godwand.spatial;

import net.godwand.GodWandMod;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class DisplacementPersistentState extends PersistentState {
    public static final String IDENTIFIER = "godwand_displacements";

    public static final Type<DisplacementPersistentState> TYPE = new Type<>(
            DisplacementPersistentState::new,
            DisplacementPersistentState::createFromNbt,
            null
    );

    private final List<DisplacedBlockRecord> unrestoredRecords = new ArrayList<>();
    private RegistryKey<World> worldKey;

    public DisplacementPersistentState() {
        super();
    }

    public void setWorldKey(RegistryKey<World> worldKey) {
        this.worldKey = worldKey;
    }

    public static DisplacementPersistentState createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        DisplacementPersistentState state = new DisplacementPersistentState();
        if (nbt.contains("displaced_records", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("displaced_records", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound recNbt = list.getCompound(i);
                int x = recNbt.getInt("x");
                int y = recNbt.getInt("y");
                int z = recNbt.getInt("z");
                BlockPos pos = new BlockPos(x, y, z);

                BlockState blockState;
                if (recNbt.contains("state", NbtElement.COMPOUND_TYPE)) {
                    blockState = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), recNbt.getCompound("state"));
                } else {
                    continue;
                }

                NbtCompound beNbt = recNbt.contains("blockEntity", NbtElement.COMPOUND_TYPE) ? recNbt.getCompound("blockEntity") : null;
                String worldKeyStr = recNbt.getString("worldKey");
                RegistryKey<World> wKey = worldKeyStr.isEmpty() ? World.OVERWORLD : RegistryKey.of(RegistryKeys.WORLD, Identifier.of(worldKeyStr));
                boolean isFullCube = recNbt.getBoolean("isFullCube");

                DisplacedBlockRecord record = new DisplacedBlockRecord(
                        pos, wKey, blockState, beNbt,
                        new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f),
                        0, isFullCube
                );
                state.unrestoredRecords.add(record);
            }
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();

        RegistryKey<World> targetKey = this.worldKey != null ? this.worldKey : World.OVERWORLD;
        List<DisplacedBlockRecord> active = SpatialDisplacementManager.getActiveRecordsForWorld(targetKey);
        for (DisplacedBlockRecord record : active) {
            NbtCompound recNbt = new NbtCompound();
            recNbt.putInt("x", record.pos.getX());
            recNbt.putInt("y", record.pos.getY());
            recNbt.putInt("z", record.pos.getZ());
            recNbt.put("state", NbtHelper.fromBlockState(record.state));
            if (record.nbt != null) {
                recNbt.put("blockEntity", record.nbt);
            }
            recNbt.putString("worldKey", record.worldKey.getValue().toString());
            recNbt.putBoolean("isFullCube", record.isFullCube);
            list.add(recNbt);
        }

        for (DisplacedBlockRecord record : this.unrestoredRecords) {
            if (this.worldKey == null || record.worldKey.equals(this.worldKey)) {
                NbtCompound recNbt = new NbtCompound();
                recNbt.putInt("x", record.pos.getX());
                recNbt.putInt("y", record.pos.getY());
                recNbt.putInt("z", record.pos.getZ());
                recNbt.put("state", NbtHelper.fromBlockState(record.state));
                if (record.nbt != null) {
                    recNbt.put("blockEntity", record.nbt);
                }
                recNbt.putString("worldKey", record.worldKey.getValue().toString());
                recNbt.putBoolean("isFullCube", record.isFullCube);
                list.add(recNbt);
            }
        }

        nbt.put("displaced_records", list);
        return nbt;
    }

    public static DisplacementPersistentState getServerState(ServerWorld world) {
        DisplacementPersistentState state = world.getPersistentStateManager().getOrCreate(TYPE, IDENTIFIER);
        state.setWorldKey(world.getRegistryKey());
        return state;
    }

    public void restoreAndCleanup(ServerWorld world) {
        if (!unrestoredRecords.isEmpty()) {
            List<DisplacedBlockRecord> toRestore = new ArrayList<>();
            for (DisplacedBlockRecord r : unrestoredRecords) {
                if (r.worldKey.equals(world.getRegistryKey())) {
                    toRestore.add(r);
                }
            }
            if (!toRestore.isEmpty()) {
                SpatialDisplacementManager.executeTwoPassRestoration(world, toRestore);
                unrestoredRecords.removeAll(toRestore);
                markDirty();
                GodWandMod.LOGGER.info("Crash recovery: Restored {} orphaned displaced blocks in {}", toRestore.size(), world.getRegistryKey().getValue());
            }
        }

        List<Entity> toDiscard = new ArrayList<>();
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof BlockDisplayEntity && entity.getCommandTags().contains("godwand_displaced")) {
                toDiscard.add(entity);
            }
        }
        if (!toDiscard.isEmpty()) {
            for (Entity entity : toDiscard) {
                entity.discard();
            }
            GodWandMod.LOGGER.info("Cleaned up {} orphaned godwand_displaced display entities in {}", toDiscard.size(), world.getRegistryKey().getValue());
        }
    }

    public List<DisplacedBlockRecord> getUnrestoredRecords() {
        return unrestoredRecords;
    }
}

```

# File: SpatialDisplacementManager.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\spatial\SpatialDisplacementManager.java
```java
package net.godwand.spatial;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Clearable;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpatialDisplacementManager {
    private static final Map<DimensionPos, DisplacedBlockRecord> ACTIVE_RECORDS = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockDisplayEntity> ACTIVE_ENTITIES = new ConcurrentHashMap<>();

    public static boolean isPositionDisplaced(RegistryKey<World> worldKey, BlockPos pos) {
        return ACTIVE_RECORDS.containsKey(new DimensionPos(worldKey, pos));
    }

    public static boolean isPositionDisplaced(BlockPos pos) {
        return isPositionDisplaced(World.OVERWORLD, pos);
    }

    public static boolean canDisplace(ServerWorld world, BlockPos pos, BlockState state) {
        BlockPos immutablePos = pos.toImmutable();
        if (state.isAir() || state.getHardness(world, immutablePos) < 0) return false;
        if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.END_PORTAL) || state.isOf(Blocks.BARRIER)) return false;
        return !isPositionDisplaced(world.getRegistryKey(), immutablePos);
    }

    public static int getActiveRecordCount() {
        return ACTIVE_RECORDS.size();
    }

    public static int getActiveEntityCount() {
        return ACTIVE_ENTITIES.size();
    }

    public static DisplacedBlockRecord getActiveRecord(RegistryKey<World> worldKey, BlockPos pos) {
        return ACTIVE_RECORDS.get(new DimensionPos(worldKey, pos));
    }

    public static BlockDisplayEntity getActiveEntity(UUID uuid) {
        return uuid != null ? ACTIVE_ENTITIES.get(uuid) : null;
    }

    public static void trackEntity(UUID uuid, BlockDisplayEntity entity) {
        if (uuid != null && entity != null) {
            ACTIVE_ENTITIES.put(uuid, entity);
        }
    }

    public static void untrackEntity(UUID uuid) {
        if (uuid != null) {
            ACTIVE_ENTITIES.remove(uuid);
        }
    }

    public static DisplacedBlockRecord removeActiveRecord(RegistryKey<World> worldKey, BlockPos pos) {
        DisplacedBlockRecord rec = ACTIVE_RECORDS.remove(new DimensionPos(worldKey, pos));
        if (rec != null && rec.displayEntityUuid != null) {
            BlockDisplayEntity ent = ACTIVE_ENTITIES.remove(rec.displayEntityUuid);
            if (ent != null && ent.isAlive()) {
                ent.discard();
            }
        }
        return rec;
    }

    public static List<DisplacedBlockRecord> getActiveRecordsForWorld(RegistryKey<World> worldKey) {
        List<DisplacedBlockRecord> list = new ArrayList<>();
        for (DisplacedBlockRecord r : ACTIVE_RECORDS.values()) {
            if (r.worldKey.equals(worldKey) && !r.permanentDestruction) {
                list.add(r);
            }
        }
        return list;
    }

    public static void refreshDisplacement(RegistryKey<World> worldKey, BlockPos pos, int lifetime) {
        DisplacedBlockRecord record = ACTIVE_RECORDS.get(new DimensionPos(worldKey, pos));
        if (record != null) {
            record.interruptRestoration();
            record.maxLifetimeTicks = lifetime;
        }
    }

    public static BlockPos getMatchingDoubleBlockHalf(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            if (state.contains(DoorBlock.HALF)) {
                DoubleBlockHalf half = state.get(DoorBlock.HALF);
                BlockPos target = half == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
                BlockState targetState = world.getBlockState(target);
                if (targetState.isOf(state.getBlock()) && targetState.contains(DoorBlock.HALF) && targetState.get(DoorBlock.HALF) != half) {
                    return target;
                }
            }
        } else if (state.getBlock() instanceof TallPlantBlock) {
            if (state.contains(TallPlantBlock.HALF)) {
                DoubleBlockHalf half = state.get(TallPlantBlock.HALF);
                BlockPos target = half == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
                BlockState targetState = world.getBlockState(target);
                if (targetState.isOf(state.getBlock()) && targetState.contains(TallPlantBlock.HALF) && targetState.get(TallPlantBlock.HALF) != half) {
                    return target;
                }
            }
        } else if (state.getBlock() instanceof BedBlock) {
            if (state.contains(BedBlock.PART) && state.contains(BedBlock.FACING)) {
                BedPart part = state.get(BedBlock.PART);
                Direction facing = state.get(BedBlock.FACING);
                BlockPos target = part == BedPart.FOOT ? pos.offset(facing) : pos.offset(facing.getOpposite());
                BlockState targetState = world.getBlockState(target);
                if (targetState.isOf(state.getBlock()) && targetState.contains(BedBlock.PART) && targetState.get(BedBlock.PART) != part) {
                    return target;
                }
            }
        }
        return null;
    }

    public static void displaceBlock(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, int lifetime, int glowColor) {
        displaceBlock(world, pos, targetDisplacement, rotation, new Vector3f(1.0f, 1.0f, 1.0f), lifetime, glowColor, true);
    }

    public static void displaceBlock(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, int lifetime, int glowColor, boolean spawnDisplay) {
        displaceBlock(world, pos, targetDisplacement, rotation, new Vector3f(1.0f, 1.0f, 1.0f), lifetime, glowColor, spawnDisplay);
    }

    public static void displaceBlock(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, Vector3f scale, int lifetime, int glowColor, boolean spawnDisplay) {
        displaceBlock(world, pos, targetDisplacement, rotation, scale, lifetime, glowColor, spawnDisplay,
                DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE, null, null, 0.0);
    }

    public static void displaceBlock(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, Vector3f scale, int lifetime, int glowColor,
                                      boolean spawnDisplay, DisplacedBlockRecord.DisplacementMode mode,
                                      UUID boundPlayerUuid, Vec3d blastCenter, double blastRadius) {
        displaceBlockInternal(world, pos, targetDisplacement, rotation, scale, lifetime, glowColor,
                spawnDisplay, mode, boundPlayerUuid, blastCenter, blastRadius, true);
    }

    private static void displaceBlockInternal(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                              Quaternionf rotation, Vector3f scale, int lifetime, int glowColor,
                                              boolean spawnDisplay, DisplacedBlockRecord.DisplacementMode mode,
                                              UUID boundPlayerUuid, Vec3d blastCenter, double blastRadius, boolean checkDoubleBlock) {
        BlockPos immutablePos = pos.toImmutable();
        BlockState state = world.getBlockState(immutablePos);
        if (!canDisplace(world, immutablePos, state)) return;

        BlockPos otherHalf = checkDoubleBlock ? getMatchingDoubleBlockHalf(world, immutablePos, state) : null;

        // Skip display spawn for pure water or lava
        if (state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA)) {
            spawnDisplay = false;
        }

        // Exact 1:1 state and NBT mapping: read identifying data then clear inventory to prevent duping
        BlockEntity be = world.getBlockEntity(immutablePos);
        NbtCompound nbt = null;
        if (be != null) {
            nbt = be.createNbtWithIdentifyingData(world.getRegistryManager());
            Clearable.clear(be);
        }

        boolean isFull = state.isFullCube(world, immutablePos);
        DisplacedBlockRecord record = new DisplacedBlockRecord(
                immutablePos, world.getRegistryKey(), state, nbt, targetDisplacement, rotation, scale,
                lifetime, isFull, mode, boundPlayerUuid);
        record.blastCenter = blastCenter;
        record.blastRadius = blastRadius;

        // Replace with AIR using atomic notification flags
        world.setBlockState(immutablePos, Blocks.AIR.getDefaultState(), Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);

        // Tick 0 Handshake: spawn at origin with identity matrix, high brightness, and glowing
        if (spawnDisplay) {
            BlockDisplayEntity display = new BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
            display.addCommandTag("godwand_displaced");
            display.setPosition(immutablePos.getX(), immutablePos.getY(), immutablePos.getZ());
            display.setBlockState(state);

            // XÃ³a bá» hoÃ n toÃ n setGlowing Ä‘á»ƒ triá»‡t tiÃªu viá»n outline sticker 2D
            display.setGlowing(false);
            display.setBrightness(new Brightness(15, 15));

            display.setStartInterpolation(0);
            display.setInterpolationDuration(0);
            display.setTransformation(AffineTransformation.identity());

            world.spawnEntity(display);
            record.displayEntityUuid = display.getUuid();
            ACTIVE_ENTITIES.put(display.getUuid(), display);

            // Subtle break crumbs
            if ((immutablePos.asLong() & 3) == 0) {
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                        immutablePos.getX() + 0.5, immutablePos.getY() + 0.5, immutablePos.getZ() + 0.5,
                        3, 0.15, 0.15, 0.15, 0.08);
                world.playSound(null, immutablePos, SoundEvents.BLOCK_BASALT_BREAK, SoundCategory.BLOCKS, 0.6f, 0.85f + 0.25f * (float)Math.random());
            }
        }

        ACTIVE_RECORDS.put(new DimensionPos(world.getRegistryKey(), immutablePos), record);
        DisplacementPersistentState.getServerState(world).markDirty();

        // Displace matching half of double block simultaneously
        if (otherHalf != null && canDisplace(world, otherHalf, world.getBlockState(otherHalf))) {
            displaceBlockInternal(world, otherHalf, targetDisplacement, rotation, scale, lifetime,
                    glowColor, spawnDisplay, mode, boundPlayerUuid, blastCenter, blastRadius, false);
        }
    }

    public static void tick(ServerWorld world) {
        List<DisplacedBlockRecord> toRemove = new ArrayList<>();

        for (DisplacedBlockRecord record : ACTIVE_RECORDS.values()) {
            if (!record.worldKey.equals(world.getRegistryKey())) {
                continue;
            }

            record.ageTicks++;
            BlockDisplayEntity display = record.displayEntityUuid != null ? ACTIVE_ENTITIES.get(record.displayEntityUuid) : null;

            // ==========================================
            // 1. PHA THU Há»’I 3 BÆ¯á»šC (3-PHASE MAGNETIC SNAP REASSEMBLY)
            // ==========================================
            if (record.isRestoring()) {
                // Náº¿u lÃ  PATH_WALKER mÃ  ngÆ°á»i chÆ¡i bÆ°á»›c láº¡i vÃ o vÃ¹ng <= 4.2m trong lÃºc Ä‘ang Ä‘Ã³ng -> Háº¥t ngÆ°á»£c ra vÃ²m Aura ngay láº­p tá»©c!
                if (record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER ||
                    record.mode == DisplacedBlockRecord.DisplacementMode.HYSTERESIS_AURA) {
                    Vec3d blockCenter = Vec3d.ofCenter(record.pos);
                    for (ServerPlayerEntity p : world.getPlayers()) {
                        if (net.godwand.skill.SpatialPathWalker.isActive(p)) {
                            Vec3d lv = p.getRotationVec(1.0f);
                            Vec3d lh = new Vec3d(lv.x, 0, lv.z);
                            lh = lh.lengthSquared() < 0.001 ? new Vec3d(1, 0, 0) : lh.normalize();
                            double lead = net.godwand.skill.SpatialPathWalker.getPlayerForwardLead(p);
                            double holdingLead = Math.max(lead, 5.5);
                            double spineDist = SpatialMath.distanceToAuraSpine(blockCenter, p.getPos().add(0, 1.1, 0), lh, holdingLead);
                            if (spineDist <= 4.2) {
                                record.interruptRestoration();
                                if (display != null && display.isAlive()) {
                                    Vec3d rebound = SpatialMath.computeDynamicAuraOffset(record.pos, p.getPos(), lh, 2.1, record.ageTicks);
                                    record.currentOffset.set((float) rebound.x, (float) rebound.y, (float) rebound.z);
                                    Quaternionf reboundRot = SpatialMath.computeArchwayRotation(record.pos, p.getPos(), lh, record.ageTicks);
                                    record.currentRotation.set(reboundRot);
                                    display.setStartInterpolation(0);
                                    display.setInterpolationDuration(5);
                                    display.setTransformation(SpatialMath.createPivotTransform(rebound, reboundRot, record.targetScale.x));
                                }
                                break;
                            }
                        }
                    }
                }

                if (record.isRestoring()) {
                    if (display != null && display.isAlive()) {
                        // Pha 1 (restoreTick == 1): HÃºt xoáº¯n á»‘c vá» sÃ¡t Ã´ gá»‘c (Spiral Pull) trong 9 ticks
                        if (record.restoreTick == 1) {
                            Vec3d spiralPreSnap = SpatialMath.computeSpiralReturnOffset(record.getCurrentOffset(), record.pos);
                            record.currentOffset.set((float) spiralPreSnap.x, (float) spiralPreSnap.y, (float) spiralPreSnap.z);
                            Quaternionf nearZeroRot = new Quaternionf().slerp(record.currentRotation, 0.18f);
                            record.currentRotation.set(nearZeroRot);

                            display.setStartInterpolation(0);
                            display.setInterpolationDuration(9);
                            display.setTransformation(SpatialMath.createPivotTransform(spiralPreSnap, nearZeroRot, 0.96f));
                        }
                        // Pha 2 (restoreTick == 10): KhÃ³a khá»›p tá»« tÃ­nh (Magnetic Snap) vá» Ä‘Ãºng (0,0,0) trong 4 ticks
                        else if (record.restoreTick == 10) {
                            record.currentOffset.set(0, 0, 0);
                            display.setStartInterpolation(0);
                            display.setInterpolationDuration(4);
                            display.setTransformation(AffineTransformation.identity());
                            if ((record.pos.asLong() & 7) == 0) {
                                world.playSound(null, record.pos, SoundEvents.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.BLOCKS, 0.25f, 1.35f);
                            }
                        }
                    }

                    // Pha 3 - Pass 1 (restoreTick == 14): KhÃ´i phá»¥c khá»‘i Ä‘áº·c Ä‘Ãºng 1:1
                    if (record.restoreTick == 14 && record.originalState.isFullCube(world, record.pos)) {
                        restoreRecordDirectly(world, record, display);
                        toRemove.add(record);
                    }
                    // Pha 3 - Pass 2 (restoreTick >= 15): KhÃ´i phá»¥c khá»‘i Ä‘Ã­nh kÃ¨m (Äuá»‘c, ÄÃ¨n lá»“ng, Hoa...)
                    else if (record.restoreTick >= 15) {
                        restoreRecordDirectly(world, record, display);
                        toRemove.add(record);
                    }

                    record.restoreTick++;
                    continue;
                }
            }

            // ==========================================
            // 1.5. CÃCH LY HOÃ€N TOÃ€N CUSTOM_SCRIPTED (ChiÃªu 2 / ChiÃªu 3 / ChiÃªu 4)
            // ==========================================
            if (record.mode == DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED) {
                if (record.ageTicks >= record.maxLifetimeTicks + 20) {
                    record.beginRestoration();
                }
                continue;
            }

            // ==========================================
            // 2. VÃ’NG Äá»œI SPAWN 2-TICK & Báº¢O Vá»† Má»ž ÄÆ¯á»œNG Äáº¦U TIÃŠN
            // ==========================================
            if (record.spawnTickDelay > 0) {
                record.spawnTickDelay--;
                if (record.spawnTickDelay == 0 && display != null && display.isAlive()) {
                    display.setStartInterpolation(0);
                    int openDuration = 8;
                    if (record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER) {
                        double playerSpeed = 0.0;
                        double distToPlayer = 3.0;
                        if (record.boundPlayerUuid != null) {
                            ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(record.boundPlayerUuid);
                            if (p != null) {
                                playerSpeed = net.godwand.skill.SpatialPathWalker.getPlayerSpeed(p);
                                distToPlayer = Vec3d.ofCenter(record.pos).distanceTo(p.getPos());
                            }
                        }
                        if (playerSpeed >= 0.15) {
                            // Cháº¡y nhanh / Sprint / Bay: má»Ÿ cá»±c nhanh (4 - 6 ticks)
                            openDuration = 5;
                        } else {
                            // Äi bá»™ cháº­m hoáº·c Ä‘á»©ng yÃªn ("farming aura"):
                            if (distToPlayer <= 2.0) {
                                openDuration = 10;
                            } else {
                                // Khá»‘i á»Ÿ xa phÃ­a trÆ°á»›c (2m - 4m): tá»« tá»«, cháº­m rÃ£i vÃ  uy quyá»n (18 - 26 ticks)
                                float tDist = (float) MathHelper.clamp((distToPlayer - 2.0) / 2.0, 0.0, 1.0);
                                openDuration = (int) MathHelper.lerp(tDist, 18.0f, 26.0f);
                            }
                        }
                    }
                    record.openDurationTicks = openDuration;
                    display.setInterpolationDuration(openDuration);
                    display.setTransformation(SpatialMath.createPivotTransform(
                            record.getCurrentOffset(), record.currentRotation, record.targetScale.x));

                    record.animationStage = 1;
                    record.transformed = true;
                }
                continue;
            }

            // Chá» háº¿t ticks bung má»Ÿ ban Ä‘áº§u (Ä‘á»™ng theo openDurationTicks + 1 cho PATH_WALKER, 9 ticks cho cÃ¡c chiÃªu khÃ¡c) rá»“i má»›i cháº¡y cáº­p nháº­t trÆ°á»ng lá»±c Ä‘á»™ng
            int minHoldTicks = record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER
                    ? (record.openDurationTicks + 1) : 9;
            if (record.ageTicks < minHoldTicks) {
                continue;
            }

            // ==========================================
            // 3. CHáº¾ Äá»˜ CHIÃŠU 1: LIVING AURA + SPRING LERP + INTERACTIVE EVASION
            // ==========================================
            if (record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER ||
                record.mode == DisplacedBlockRecord.DisplacementMode.HYSTERESIS_AURA) {

                Vec3d blockCenter = Vec3d.ofCenter(record.pos);
                ServerPlayerEntity activeOwner = null;
                double minSpineDist = Double.MAX_VALUE;

                for (ServerPlayerEntity p : world.getPlayers()) {
                    if (net.godwand.skill.SpatialPathWalker.isActive(p)) {
                        Vec3d lv = p.getRotationVec(1.0f);
                        Vec3d lh = new Vec3d(lv.x, 0, lv.z);
                        lh = lh.lengthSquared() < 0.001 ? new Vec3d(1, 0, 0) : lh.normalize();
                        double lead = net.godwand.skill.SpatialPathWalker.getPlayerForwardLead(p);
                        double holdingLead = Math.max(lead, 5.5);
                        double d = SpatialMath.distanceToAuraSpine(blockCenter, p.getPos().add(0, 1.1, 0), lh, holdingLead);
                        if (d < minSpineDist) {
                            minSpineDist = d;
                            activeOwner = p;
                        }
                    }
                }

                // BÃ¡n kÃ­nh quÃ©t quanh trá»¥c lÃ  3.3m, ngÆ°á»¡ng giá»¯ má»Ÿ quanh trá»¥c lÃ  4.2m vá»›i holdingLead 5.5m -> Giá»¯ khá»‘i phÃ­a trÆ°á»›c luÃ´n má»Ÿ lÆ¡ lá»­ng khi Ä‘á»©ng yÃªn
                if (activeOwner != null && minSpineDist <= 4.2) {
                    record.resetRestoreTick();

                    if (display != null && display.isAlive() && ((record.ageTicks + Math.abs(record.pos.hashCode())) % 3 == 0)) {
                        Vec3d lookVec = activeOwner.getRotationVec(1.0f);
                        Vec3d lookHoriz = new Vec3d(lookVec.x, 0, lookVec.z);
                        lookHoriz = lookHoriz.lengthSquared() < 0.001 ? new Vec3d(1, 0, 0) : lookHoriz.normalize();

                        // TÃ­nh vá»‹ trÃ­ Ä‘Ã­ch trÃªn vÃ²m Aura
                        Vec3d desiredAuraOffset = SpatialMath.computeDynamicAuraOffset(
                                record.pos, activeOwner.getPos(), lookHoriz, 2.1, record.ageTicks);

                        // Kiá»ƒm tra nÃ© tÆ°Æ¡ng tÃ¡c dá»±a trÃªn Vá»Š TRÃ THá»°C Táº¾ ÄANG BAY cá»§a máº£nh vá»¡
                        Vec3d actualDebrisWorldPos = blockCenter.add(record.getCurrentOffset());
                        PlayerEntity closestToDebris = world.getClosestPlayer(actualDebrisWorldPos.x, actualDebrisWorldPos.y, actualDebrisWorldPos.z, 3.8, false);
                        if (closestToDebris != null) {
                            Vec3d evasion = SpatialMath.computeInteractiveEvasion(
                                    actualDebrisWorldPos, closestToDebris.getPos().add(0, 1.1, 0), 3.4);
                            if (evasion.lengthSquared() > 0.001) {
                                desiredAuraOffset = desiredAuraOffset.add(evasion);
                                record.hoverYaw += 0.38f;
                                world.spawnParticles(ParticleTypes.END_ROD, actualDebrisWorldPos.x, actualDebrisWorldPos.y, actualDebrisWorldPos.z, 1, 0.05, 0.05, 0.05, 0.01);
                            }
                        }

                        // QuÃ¡n tÃ­nh lÃ² xo (Spring Damping Lerp 0.42) giÃºp máº£nh vá»¡ lÆ°á»›t mÆ°á»£t khÃ´ng bá»‹ giáº­t khi xoay chuá»™t nhanh
                        Vec3d smoothedOffset = record.getCurrentOffset().lerp(desiredAuraOffset, 0.42);
                        record.currentOffset.set((float) smoothedOffset.x, (float) smoothedOffset.y, (float) smoothedOffset.z);

                        record.hoverYaw += 0.09f;
                        Quaternionf rot = SpatialMath.computeArchwayRotation(record.pos, activeOwner.getPos(), lookHoriz, record.ageTicks);
                        record.currentRotation.set(rot);

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(3);
                        display.setTransformation(SpatialMath.createPivotTransform(smoothedOffset, rot, record.targetScale.x));

                        // DÃ¢y nÄƒng lÆ°á»£ng 3D ná»‘i tá»« khá»‘i vÃ²m cao xuá»‘ng máº·t Ä‘áº¥t
                        if (desiredAuraOffset.y > 1.2 && ((record.ageTicks + record.pos.hashCode()) & 7) == 0) {
                            world.spawnParticles(ParticleTypes.END_ROD,
                                    actualDebrisWorldPos.x, actualDebrisWorldPos.y, actualDebrisWorldPos.z,
                                    1, 0.04, -0.4, 0.04, 0.02);
                        }
                    }
                } else {
                    record.beginRestoration();
                }
                continue;
            }

            // ==========================================
            // 4. CHáº¾ Äá»˜ CHIÃŠU 2: TORNADO FUNNEL VORTEX + INTERACTIVE EVASION
            // ==========================================
            if (record.mode == DisplacedBlockRecord.DisplacementMode.CANOPY_VORTEX ||
               (record.mode == DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE && record.blastCenter != null)) {

                if (record.ageTicks >= record.maxLifetimeTicks) {
                    record.beginRestoration();
                    continue;
                }

                if (display != null && display.isAlive() && (display.getId() + record.ageTicks) % 3 == 0) {
                    Vec3d blockCenter = Vec3d.ofCenter(record.pos);
                    Vec3d vortexOffset = SpatialMath.computeCanopyVortexOffset(record.pos, record.blastCenter, record.blastRadius, record.ageTicks);

                    // Náº¿u ngÆ°á»i chÆ¡i bay/Ä‘i láº¡i gáº§n máº£nh thiÃªn tháº¡ch cá»§a ChiÃªu 2, máº£nh thiÃªn tháº¡ch cÅ©ng tá»± nÃ© ngÆ°á»i chÆ¡i!
                    Vec3d actualDebrisWorldPos = blockCenter.add(record.getCurrentOffset());
                    PlayerEntity closestPlayer = world.getClosestPlayer(actualDebrisWorldPos.x, actualDebrisWorldPos.y, actualDebrisWorldPos.z, 3.8, false);
                    if (closestPlayer != null) {
                        Vec3d evasion = SpatialMath.computeInteractiveEvasion(
                                actualDebrisWorldPos, closestPlayer.getPos().add(0, 1.1, 0), 3.4);
                        if (evasion.lengthSquared() > 0.001) {
                            vortexOffset = vortexOffset.add(evasion);
                            record.hoverYaw += 0.35f;
                        }
                    }

                    Vec3d smoothedVortex = record.getCurrentOffset().lerp(vortexOffset, 0.45);
                    record.currentOffset.set((float) smoothedVortex.x, (float) smoothedVortex.y, (float) smoothedVortex.z);
                    record.hoverYaw += 0.07f;
                    Quaternionf rot = new Quaternionf(record.targetRotation).rotateY(record.hoverYaw);
                    record.currentRotation.set(rot);

                    display.setStartInterpolation(0);
                    display.setInterpolationDuration(3);
                    display.setTransformation(SpatialMath.createPivotTransform(smoothedVortex, rot, record.targetScale.x));
                }
                continue;
            }

            // ==========================================
            // 5. DEFAULT TIMED LIFECYCLE (Ná»™i tháº¥t áº©n / Khá»‘i thÆ°á»ng)
            // ==========================================
            if (record.mode == DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE) {
                if (record.ageTicks >= record.maxLifetimeTicks) {
                    record.beginRestoration();
                    continue;
                }

                if (display != null && display.isAlive() && record.ageTicks % 6 == 0) {
                    record.hoverYaw += 0.08f;
                    double hashPhase = SpatialMath.hash01(record.pos) * Math.PI * 2.0;
                    float tremorY = (float) (Math.sin(hashPhase + record.ageTicks / 6.0) * 0.14);

                    Vector3f hoverDisp = new Vector3f(
                            record.baseDisplacement.x,
                            record.baseDisplacement.y + tremorY,
                            record.baseDisplacement.z
                    );
                    record.currentOffset.set(hoverDisp);
                    Quaternionf hoverRot = new Quaternionf(record.targetRotation).rotateY(record.hoverYaw);
                    record.currentRotation.set(hoverRot);

                    display.setStartInterpolation(0);
                    display.setInterpolationDuration(6);
                    display.setTransformation(SpatialMath.createPivotTransform(hoverDisp, hoverRot, record.targetScale.x));
                }
            }
        }

        for (DisplacedBlockRecord r : toRemove) {
            ACTIVE_RECORDS.remove(new DimensionPos(r.worldKey, r.pos));
            DisplacementPersistentState.getServerState(world).markDirty();
        }
    }

    private static void restoreRecordDirectly(ServerWorld world, DisplacedBlockRecord r, BlockDisplayEntity display) {
        // Exact 1:1 original BlockState and NBT restoration
        world.setBlockState(r.pos, r.originalState, Block.FORCE_STATE | Block.NOTIFY_LISTENERS);
        if (r.blockEntityNbt != null) {
            BlockEntity be = world.getBlockEntity(r.pos);
            if (be != null) {
                be.read(r.blockEntityNbt, world.getRegistryManager());
                be.markDirty();
            }
        }
        if (display != null) {
            ACTIVE_ENTITIES.remove(r.displayEntityUuid);
            display.discard();
        }
        // Crisp stone impact particle and sound
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, r.originalState),
                r.pos.getX() + 0.5, r.pos.getY() + 0.5, r.pos.getZ() + 0.5, 4, 0.15, 0.15, 0.15, 0.05);
        if ((r.pos.asLong() & 3) == 0) {
            world.playSound(null, r.pos, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 0.75f, 1.0f);
        }
    }

    public static void collapsePlayerAura(UUID playerUuid) {
        for (DisplacedBlockRecord record : ACTIVE_RECORDS.values()) {
            if (record.boundPlayerUuid != null && record.boundPlayerUuid.equals(playerUuid)) {
                record.beginRestoration();
            }
        }
    }

    public static void emergencyRestoreAll(ServerWorld world) {
        List<DisplacedBlockRecord> matching = new ArrayList<>();
        for (DisplacedBlockRecord r : ACTIVE_RECORDS.values()) {
            if (r.worldKey.equals(world.getRegistryKey())) {
                if (r.permanentDestruction) {
                    if (r.displayEntityUuid != null) {
                        BlockDisplayEntity ent = ACTIVE_ENTITIES.remove(r.displayEntityUuid);
                        if (ent != null && ent.isAlive()) {
                            ent.discard();
                        }
                    }
                } else {
                    matching.add(r);
                }
            }
        }
        executeTwoPassRestoration(world, matching);
    }

    public static void executeTwoPassRestoration(ServerWorld world, List<DisplacedBlockRecord> records) {
        // Pass 1: Solid base blocks
        for (DisplacedBlockRecord r : records) {
            if (r.isFullCube) {
                BlockDisplayEntity d = r.displayEntityUuid != null ? ACTIVE_ENTITIES.get(r.displayEntityUuid) : null;
                restoreRecordDirectly(world, r, d);
                ACTIVE_RECORDS.remove(new DimensionPos(r.worldKey, r.pos));
            }
        }
        // Pass 2: Attached & delicate blocks (Torches, Lanterns, Signs, Doors, Plants)
        // Sort by Y ascending so lower parts of double blocks are placed before upper parts
        List<DisplacedBlockRecord> delicate = new ArrayList<>();
        for (DisplacedBlockRecord r : records) {
            if (!r.isFullCube) {
                delicate.add(r);
            }
        }
        delicate.sort(Comparator.comparingInt(r -> r.pos.getY()));
        for (DisplacedBlockRecord r : delicate) {
            BlockDisplayEntity d = r.displayEntityUuid != null ? ACTIVE_ENTITIES.get(r.displayEntityUuid) : null;
            restoreRecordDirectly(world, r, d);
            ACTIVE_RECORDS.remove(new DimensionPos(r.worldKey, r.pos));
        }
        DisplacementPersistentState.getServerState(world).markDirty();
    }

    public static void collapseNearbyDisplacements(RegistryKey<World> worldKey, BlockPos center, double radius) {
        double rSq = radius * radius;
        for (DisplacedBlockRecord record : ACTIVE_RECORDS.values()) {
            if (record.worldKey.equals(worldKey)) {
                double dSq = record.pos.getSquaredDistance(center);
                if (dSq <= rSq) {
                    record.beginRestoration();
                }
            }
        }
    }
}

```

# File: SpatialMath.java
**Đường dẫn:** D:\fileluu\Tools\god-wand\src\main\java\net\godwand\spatial\SpatialMath.java
```java
package net.godwand.spatial;

import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SpatialMath {
    private static final Vector3f PIVOT = new Vector3f(0.5f, 0.5f, 0.5f);

    private SpatialMath() {}

    public static AffineTransformation createPivotTransform(Vec3d offset, Quaternionf rotation, float scale) {
        Vector3f center = new Vector3f(0.5f, 0.5f, 0.5f);
        Vector3f scaledRotatedCenter = new Vector3f(center).mul(scale).rotate(rotation);
        Vector3f translation = new Vector3f(
            (float) offset.x + center.x - scaledRotatedCenter.x,
            (float) offset.y + center.y - scaledRotatedCenter.y,
            (float) offset.z + center.z - scaledRotatedCenter.z
        );
        return new AffineTransformation(
            translation,
            rotation,
            new Vector3f(scale, scale, scale),
            null
        );
    }

    public static AffineTransformation createPivotTransform(Vector3f offset, Quaternionf rotation, float scale) {
        return createPivotTransform(new Vec3d(offset.x, offset.y, offset.z), rotation, scale);
    }

    public static Vector3f compensatePivot(Vector3f displacement, Quaternionf rotation) {
        Vector3f rotatedPivot = new Vector3f(PIVOT);
        rotation.transform(rotatedPivot);

        Vector3f result = new Vector3f(displacement);
        result.add(PIVOT);
        result.sub(rotatedPivot);
        return result;
    }

    /**
     * Biáº¿n má»™t BlockDisplayEntity (Ä‘áº·t táº¡i startPos) thÃ nh má»™t Tia NÄƒng LÆ°á»£ng 3D siÃªu má»ng
     * ná»‘i tháº³ng tá»« startPos tá»›i endPos vá»›i Ä‘á»™ dÃ y thickness (máº·c Ä‘á»‹nh 0.035f).
     */
    public static AffineTransformation createThinBeamTransform(Vec3d startPos, Vec3d endPos, float thickness) {
        Vec3d diff = endPos.subtract(startPos);
        float length = (float) diff.length();
        if (length < 0.01f) {
            return new AffineTransformation(new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(0, 0, 0), null);
        }

        Vector3f dir = new Vector3f((float) diff.x / length, (float) diff.y / length, (float) diff.z / length);
        Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), dir);

        // CÄƒn giá»¯a tiáº¿t diá»‡n tia (thickness x thickness) quanh trá»¥c phÃ³ng Z
        Vector3f localOffset = new Vector3f(-thickness * 0.5f, -thickness * 0.5f, 0.0f);
        rot.transform(localOffset);

        return new AffineTransformation(
            localOffset,
            rot,
            new Vector3f(thickness, thickness, length),
            null
        );
    }

    /**
     * CHIÃŠU 1: Cá»”NG VÃ’M 2 Táº¦NG & SÃ“NG Äá»ŠA CHáº¤N (Inner Cathedral Arch + Outer Ground Ripple)
     * Táº§ng trong uá»‘n cao vÃºt qua Ä‘áº§u, táº§ng ngoÃ i nháº¥p nhÃ´ nháº¹ trÃªn máº·t Ä‘áº¥t táº¡o sÃ³ng cháº¥n Ä‘á»™ng.
     */
    public static Vec3d computeDynamicAuraOffset(BlockPos pos, Vec3d playerPos, Vec3d lookDirHoriz, double auraRadius, int ageTicks) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        Vec3d fromPlayer = blockCenter.subtract(playerPos);

        double forwardProj = fromPlayer.x * lookDirHoriz.x + fromPlayer.z * lookDirHoriz.z;
        Vec3d spinePoint = new Vec3d(
            playerPos.x + lookDirHoriz.x * MathHelper.clamp(forwardProj, -1.5, 7.0),
            blockCenter.y,
            playerPos.z + lookDirHoriz.z * MathHelper.clamp(forwardProj, -1.5, 7.0)
        );

        Vec3d radial = blockCenter.subtract(spinePoint);
        double perpDist = Math.sqrt(radial.x * radial.x + radial.z * radial.z);

        Vec3d rightVec = new Vec3d(-lookDirHoriz.z, 0, lookDirHoriz.x);
        double sideSign = (radial.x * rightVec.x + radial.z * rightVec.z) >= 0 ? 1.0 : -1.0;

        Vec3d pushDir = perpDist < 0.15
                ? rightVec.multiply(sideSign)
                : new Vec3d(radial.x / perpDist, 0, radial.z / perpDist);

        double spatialWave = Math.sin(pos.getX() * 0.5 + pos.getZ() * 0.5 - ageTicks * 0.18);

        // Táº§ng ngoÃ i (perpDist > 2.0m): Hiá»‡u á»©ng sÃ³ng cháº¥n Ä‘á»™ng nháº¥p nhÃ´ nháº¹ trÃªn máº·t Ä‘áº¥t
        if (perpDist > 2.0) {
            double rippleFactor = Math.max(0.0, 1.0 - ((perpDist - 2.0) / 1.4));
            double rippleLift = (0.28 + Math.max(0.0, spatialWave) * 0.42) * rippleFactor;
            return new Vec3d(
                pushDir.x * (0.35 * rippleFactor),
                rippleLift,
                pushDir.z * (0.35 * rippleFactor)
            );
        }

        // Táº§ng trong (perpDist <= 2.0m): Uá»‘n vÃ²m cao vÃºt qua Ä‘áº§u kiá»ƒu Doctor Strange
        double targetCorridorRadius = auraRadius + 0.65 + spatialWave * 0.15;
        double pushOutDist = Math.max(1.35, targetCorridorRadius - perpDist);

        double relY = Math.max(0.0, blockCenter.y - playerPos.y);
        double archFactor = Math.max(0.0, 1.0 - (perpDist / 2.1));
        double archLift = 0.85 + (relY * 0.55) + (archFactor * 1.65) + spatialWave * 0.18;

        Vec3d fullArchOffset = new Vec3d(
            pushDir.x * pushOutDist,
            archLift,
            pushDir.z * pushOutDist
        );

        // HIá»†U á»¨NG Há»œ Há»®NG LÆ  Lá»¬NG PHÃA TRÆ¯á»šC (ANTICIPATORY ALOOF HOVER)
        // Khi forwardProj > 1.8m: Khá»‘i á»Ÿ xa phÃ­a trÆ°á»›c chÆ°a má»Ÿ toang thÃ nh vÃ²m mÃ  chá»‰ há» há»¯ng lÆ¡ lá»­ng gáº§n chá»— cÅ©
        if (forwardProj > 1.8) {
            double approach = MathHelper.clamp((4.5 - forwardProj) / (4.5 - 1.8), 0.0, 1.0);
            double t = approach * approach * (3.0 - 2.0 * approach); // Hermite smooth S-curve

            // Tráº¡ng thÃ¡i há» há»¯ng (Aloof Hover): tÃ¡ch nháº¹ 0.22m, bay nÃ¢ng 0.32m, thá»Ÿ nháº¹ theo nhá»‹p
            double aloofPush = 0.20 + 0.10 * Math.abs(spatialWave);
            double aloofLift = 0.32 + 0.12 * Math.sin(pos.getX() * 0.5 + pos.getZ() * 0.5 + ageTicks * 0.14);
            Vec3d aloofOffset = new Vec3d(
                pushDir.x * aloofPush,
                aloofLift,
                pushDir.z * aloofPush
            );

            return aloofOffset.lerp(fullArchOffset, t);
        }

        return fullArchOffset;
    }

    /**
     * TÃ­nh gÃ³c xoay uá»‘n vÃ²m Ä‘á»“ng bá»™ cho ChiÃªu 1 (Táº§ng ngoÃ i nghiÃªng nháº¹ 14 Ä‘á»™, táº§ng trong láº­t 38 -> 72 Ä‘á»™)
     */
    public static Quaternionf computeArchwayRotation(BlockPos pos, Vec3d playerPos, Vec3d lookDirHoriz, int ageTicks) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        Vec3d rel = blockCenter.subtract(playerPos);
        Vec3d rightVec = new Vec3d(-lookDirHoriz.z, 0, lookDirHoriz.x);
        double perpDist = Math.abs(rel.x * rightVec.x + rel.z * rightVec.z);
        float side = (rel.x * rightVec.x + rel.z * rightVec.z) >= 0 ? 1.0f : -1.0f;

        if (perpDist > 2.0) {
            // Táº§ng sÃ³ng Ä‘á»‹a cháº¥n ngoÃ i: nghiÃªng nháº¹ 14 Ä‘á»™
            float rippleAngle = (float) Math.toRadians(14.0 * side);
            return new Quaternionf()
                    .rotateY((float) Math.atan2(lookDirHoriz.x, lookDirHoriz.z))
                    .rotateZ(rippleAngle);
        }

        // Táº§ng vÃ²m chÃ­nh: xoay láº­t máº¡nh 38 -> 72 Ä‘á»™ táº¡o mÃ¡i vÃ²m cÆ¡ khÃ­
        float relY = (float) MathHelper.clamp(blockCenter.y - playerPos.y, 0.0, 3.0);
        float peelAngle = (float) Math.toRadians((38.0 + relY * 14.0) * side);
        float wavePitch = (float) Math.sin(ageTicks * 0.14 + (pos.getX() + pos.getZ()) * 0.4) * 0.16f;

        Quaternionf fullRot = new Quaternionf()
                .rotateY((float) Math.atan2(lookDirHoriz.x, lookDirHoriz.z))
                .rotateZ(peelAngle)
                .rotateX(wavePitch);

        // Náº¿u á»Ÿ phÃ­a trÆ°á»›c xa (> 1.8m): Xoay há» há»¯ng nháº¹ nhÃ ng (12 Ä‘á»™) thay vÃ¬ láº­t máº¡nh
        double forwardProj = rel.x * lookDirHoriz.x + rel.z * lookDirHoriz.z;
        if (forwardProj > 1.8) {
            double approach = MathHelper.clamp((4.5 - forwardProj) / (4.5 - 1.8), 0.0, 1.0);
            float t = (float) (approach * approach * (3.0 - 2.0 * approach));

            float aloofAngle = (float) Math.toRadians(12.0 * side);
            Quaternionf aloofRot = new Quaternionf()
                    .rotateY((float) Math.atan2(lookDirHoriz.x, lookDirHoriz.z))
                    .rotateZ(aloofAngle)
                    .rotateX(wavePitch * 0.4f);

            return new Quaternionf(aloofRot).slerp(fullRot, t);
        }

        return fullRot;
    }

    public static Vec3d computeAuraDisplacement(BlockPos pos, Vec3d playerPos, Vec3d lookDirHoriz, double auraRadius) {
        return computeDynamicAuraOffset(pos, playerPos, lookDirHoriz, auraRadius, 0);
    }

    public static double distanceToAuraSpine(Vec3d blockCenter, Vec3d chestPos, Vec3d lookDirHoriz, double forwardLead) {
        Vec3d rel = blockCenter.subtract(chestPos);
        double proj = rel.x * lookDirHoriz.x + rel.z * lookDirHoriz.z;
        double clampedProj = MathHelper.clamp(proj, 0.0, forwardLead);
        Vec3d closestPoint = new Vec3d(
            chestPos.x + lookDirHoriz.x * clampedProj,
            chestPos.y,
            chestPos.z + lookDirHoriz.z * clampedProj
        );
        return blockCenter.distanceTo(closestPoint);
    }

    public static Vec3d computeInteractiveEvasion(Vec3d actualDebrisWorldPos, Vec3d playerChestPos, double evasionRadius) {
        Vec3d diff = actualDebrisWorldPos.subtract(playerChestPos);
        double dist = diff.length();
        if (dist >= evasionRadius || dist < 0.001) {
            return Vec3d.ZERO;
        }
        double factor = (evasionRadius - dist) / evasionRadius;
        double strength = factor * factor * 2.8;
        Vec3d dir = diff.normalize();
        return new Vec3d(
            dir.x * strength,
            Math.max(0.35, dir.y * strength + 0.45 * factor),
            dir.z * strength
        );
    }

    /**
     * CHIÃŠU 2: 3 VÃ€NH ÄAI THIÃŠN THáº CH NGHIÃŠNG CHÃ‰O (3-Ring Tilted Gimbal Astrolabe)
     * 3 vÃ nh Ä‘ai nghiÃªng trÃªn cáº£ 2 trá»¥c X vÃ  Z táº¡o thÃ nh khá»‘i cáº§u Armillary Sphere 3D,
     * giá»¯ khoáº£ng cÃ¡ch thÆ°a thoÃ¡ng giá»¯a cÃ¡c viÃªn Ä‘Ã¡ vÃ  Ä‘á»ƒ lá»™ tÃ¢m vá»¥ ná»•.
     */
    public static Vec3d computeCanopyVortexOffset(BlockPos pos, Vec3d blastCenter, double blastRadius, int ageTicks) {
        double dx = pos.getX() + 0.5 - blastCenter.x;
        double dz = pos.getZ() + 0.5 - blastCenter.z;
        double hash = hash01(pos);

        int ringIndex = (int) (Math.abs(pos.asLong()) % 3);
        double baseAngle = Math.atan2(dz, dx) + hash * 0.5;
        double angularSpeed = (ringIndex == 1 ? -0.045 : 0.040) * (0.85 + hash * 0.35);
        double theta = baseAngle + ageTicks * angularSpeed;

        return computeCanopyVortexInternal(pos, blastCenter, blastRadius, ringIndex, theta, hash, 1.0);
    }

    public static Vec3d computeCanopyVortexOffset(BlockPos pos, Vec3d blastCenter, double blastRadius, float customAngle, double radiusScale) {
        double dx = pos.getX() + 0.5 - blastCenter.x;
        double dz = pos.getZ() + 0.5 - blastCenter.z;
        double hash = hash01(pos);

        int ringIndex = (int) (Math.abs(pos.asLong()) % 3);
        double baseAngle = Math.atan2(dz, dx) + hash * 0.5;
        double dir = (ringIndex == 1 ? -1.0 : 1.0);
        double theta = baseAngle + customAngle * dir * (0.85 + hash * 0.35);

        return computeCanopyVortexInternal(pos, blastCenter, blastRadius, ringIndex, theta, hash, radiusScale);
    }

    private static Vec3d computeCanopyVortexInternal(BlockPos pos, Vec3d blastCenter, double blastRadius,
                                                    int ringIndex, double theta, double hash, double radiusScale) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        double baseR = switch (ringIndex) {
            case 0 -> blastRadius * (0.85 + hash * 0.40);
            case 1 -> blastRadius * (0.68 + hash * 0.32);
            default -> blastRadius * (1.02 + hash * 0.42);
        };
        double ringRadius = baseR * radiusScale;

        double lx = Math.cos(theta) * ringRadius;
        double lz = Math.sin(theta) * ringRadius;
        double ly = ((hash - 0.5) * 1.8 + Math.sin(theta * 2.0) * 0.35) * radiusScale;

        double rotX = 0.0;
        double rotZ = 0.0;
        if (ringIndex == 0) {
            rotZ = Math.toRadians(28.0);
        } else if (ringIndex == 1) {
            rotX = Math.toRadians(28.0);
        } else {
            rotX = Math.toRadians(-22.0);
            rotZ = Math.toRadians(-22.0);
        }

        // Xoay quanh trá»¥c Z
        double x1 = lx * Math.cos(rotZ) - ly * Math.sin(rotZ);
        double y1 = lx * Math.sin(rotZ) + ly * Math.cos(rotZ);
        double z1 = lz;

        // Xoay quanh trá»¥c X
        double finalX = x1;
        double finalY = y1 * Math.cos(rotX) - z1 * Math.sin(rotX);
        double finalZ = y1 * Math.sin(rotX) + z1 * Math.cos(rotX);

        // TÃ¢m cá»§a 3 vÃ nh Ä‘ai há»™i tá»¥ chÃ­nh xÃ¡c táº¡i blastCenter (corePos)
        double targetWorldX = blastCenter.x + finalX;
        double targetWorldY = Math.max(blastCenter.y - blastRadius * 0.45, blastCenter.y + finalY);
        double targetWorldZ = blastCenter.z + finalZ;

        return new Vec3d(
            targetWorldX - blockCenter.x,
            targetWorldY - blockCenter.y,
            targetWorldZ - blockCenter.z
        );
    }

    public static Vec3d computeSpiralReturnOffset(Vec3d currentOffset, BlockPos pos) {
        double angle = 0.55 * (((pos.asLong() & 1) == 0) ? 1.0 : -1.0);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double rx = (currentOffset.x * cos - currentOffset.z * sin) * 0.22;
        double rz = (currentOffset.x * sin + currentOffset.z * cos) * 0.22;
        double ry = currentOffset.y * 0.18 + 0.25;
        return new Vec3d(rx, ry, rz);
    }

    public static double hash01(BlockPos pos) {
        long h = pos.asLong() * 0x9E3779B97F4A7C15L;
        h ^= (h >>> 30);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27);
        return ((h & 0xFFFFFFL) / (double) 0x1000000L);
    }

    public static double hashCoordinateToPhase(long posAsLong) {
        long h = posAsLong * 0x5deece66dL + 0xbL;
        return ((h >> 16) & 0xFFFF) / 65535.0 * 2.0 * Math.PI;
    }
}

```

