package net.godwand.neoforge;

import net.godwand.GodWandMod;
import net.godwand.config.GodWandConfig;
import net.godwand.entity.ArcaneOrbEntity;
import net.godwand.entity.StoneCannonProjectileEntity;
import net.godwand.item.GodWandItem;
import net.godwand.neoforge.client.GodWandNeoForgeClientMod;
import net.godwand.network.CannonPressurePayload;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.AtomicFxPayload;
import net.godwand.skill.AtomicManager;
import net.godwand.network.DrillWindPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.network.SelectSkillPayload;
import net.godwand.network.SingularityThreadsPayload;
import net.godwand.network.SpatialAuraPayload;
import net.godwand.skill.RatioCollapseManager;
import net.godwand.skill.ShockwaveSlicer;
import net.godwand.skill.SpatialBridge;
import net.godwand.skill.SpatialPathWalker;
import net.godwand.skill.SupernovaManager;
import net.godwand.spatial.DisplacementPersistentState;
import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(GodWandMod.MOD_ID)
public class GodWandNeoForgeMod {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GodWandMod.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, GodWandMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GodWandMod.MOD_ID);

    public static final DeferredItem<GodWandItem> GOD_WAND = ITEMS.register("god_wand", () -> {
        GodWandItem item = new GodWandItem(new Item.Properties());
        GodWandMod.GOD_WAND_ITEM = item;
        return item;
    });

    public static final DeferredHolder<EntityType<?>, EntityType<ArcaneOrbEntity>> ARCANE_ORB = ENTITY_TYPES.register("arcane_orb", () -> {
        EntityType<ArcaneOrbEntity> type = EntityType.Builder.<ArcaneOrbEntity>of(ArcaneOrbEntity::new, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                .build("arcane_orb");
        GodWandMod.ARCANE_ORB_ENTITY_TYPE = type;
        return type;
    });

    public static final DeferredHolder<EntityType<?>, EntityType<StoneCannonProjectileEntity>> STONE_CANNON = ENTITY_TYPES.register("stone_cannon", () -> {
        EntityType<StoneCannonProjectileEntity> type = EntityType.Builder.<StoneCannonProjectileEntity>of(StoneCannonProjectileEntity::new, MobCategory.MISC)
                .sized(0.6f, 0.6f)
                .build("stone_cannon");
        GodWandMod.STONE_CANNON_ENTITY_TYPE = type;
        return type;
    });

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GODWAND_TAB = CREATIVE_MODE_TABS.register("godwand_tab", () -> {
        CreativeModeTab tab = CreativeModeTab.builder()
                .icon(() -> new ItemStack(GOD_WAND.get()))
                .title(Component.translatable("itemGroup.godwand.tab"))
                .displayItems((parameters, output) -> output.accept(GOD_WAND.get()))
                .build();
        GodWandMod.GODWAND_ITEM_GROUP = tab;
        return tab;
    });

    public GodWandNeoForgeMod(IEventBus modBus) {
        GodWandConfig.loadServer(FMLPaths.CONFIGDIR.get());

        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        CREATIVE_MODE_TABS.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::buildCreativeModeTabContents);
        modBus.addListener(this::registerPayloadHandlers);

        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(this::onLeftClickEmpty);
        NeoForge.EVENT_BUS.addListener(this::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(this::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(this::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onLevelLoad);

        GodWandMod.LOGGER.info("God Wand NeoForge initialized.");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            GodWandMod.GOD_WAND_ITEM = GOD_WAND.get();
            GodWandMod.ARCANE_ORB_ENTITY_TYPE = ARCANE_ORB.get();
            GodWandMod.STONE_CANNON_ENTITY_TYPE = STONE_CANNON.get();
            GodWandMod.GODWAND_ITEM_GROUP = GODWAND_TAB.get();
        });
    }

    private void buildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(GOD_WAND.get());
        }
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(GodWandMod.MOD_ID).versioned("1.3.0");

        registrar.playToServer(
                SelectSkillPayload.ID,
                SelectSkillPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        if (!player.isAlive() || player.isSpectator() || player.isUsingItem()
                                || !payload.matchesHeldSlot(player.getInventory().selected)) return;
                        InteractionHand hand = payload.slot() == 40 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                        if (!player.getItemInHand(hand).is(GodWandMod.GOD_WAND_ITEM)) return;
                        if (GodWandItem.getSkillMode(player.getItemInHand(hand)) == payload.mode()) return;
                        GodWandItem.selectSkill(player, hand, payload.mode());
                    }
                })
        );

        if (FMLLoader.getDist().isClient()) {
            GodWandNeoForgeClientMod.registerClientPayloads(registrar);
        } else {
            registrar.playToClient(DrillWindPayload.ID, DrillWindPayload.CODEC, (payload, context) -> {});
            registrar.playToClient(SpatialAuraPayload.ID, SpatialAuraPayload.CODEC, (payload, context) -> {});
            registrar.playToClient(CannonPressurePayload.ID, CannonPressurePayload.CODEC, (payload, context) -> {});
            registrar.playToClient(SingularityThreadsPayload.ID, SingularityThreadsPayload.CODEC, (payload, context) -> {});
            registrar.playToClient(ImpactFxPayload.ID, ImpactFxPayload.CODEC, (payload, context) -> {});
            registrar.playToClient(CinematicFxPayload.ID, CinematicFxPayload.CODEC, (payload, context) -> {});
            registrar.playToClient(AtomicFxPayload.ID, AtomicFxPayload.CODEC, (payload, context) -> {});
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SpatialPathWalker.tickPlayer(player);
        }
        for (ServerLevel level : server.getAllLevels()) {
            ShockwaveSlicer.tick(level);
            RatioCollapseManager.tick(level);
            SupernovaManager.tick(level);
            AtomicManager.tick(level);
            SpatialBridge.tickWorld(level);
            SpatialDisplacementManager.tick(level);
        }
    }

    private void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer serverPlayer) {
            ItemStack stack = serverPlayer.getItemInHand(event.getHand());
            if (stack.is(GodWandMod.GOD_WAND_ITEM) && GodWandItem.getSkillMode(stack) == GodWandItem.MODE_ARCANE_ORB) {
                if (ShockwaveSlicer.hasActiveTask(serverPlayer)) {
                    boolean snapped = ShockwaveSlicer.triggerEarlySnap(serverPlayer);
                    if (snapped) {
                        serverPlayer.displayClientMessage(Component.literal("§dCollapsed!"), true);
                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    private void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ItemStack stack = serverPlayer.getItemInHand(event.getHand());
            if (stack.is(GodWandMod.GOD_WAND_ITEM) && GodWandItem.getSkillMode(stack) == GodWandItem.MODE_ARCANE_ORB) {
                if (ShockwaveSlicer.hasActiveTask(serverPlayer)) {
                    boolean snapped = ShockwaveSlicer.triggerEarlySnap(serverPlayer);
                    if (snapped) {
                        serverPlayer.displayClientMessage(Component.literal("§dCollapsed!"), true);
                    }
                }
            }
        }
    }

    private void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ItemStack stack = serverPlayer.getMainHandItem();
            if (stack.is(GodWandMod.GOD_WAND_ITEM) && GodWandItem.getSkillMode(stack) == GodWandItem.MODE_ARCANE_ORB) {
                if (ShockwaveSlicer.hasActiveTask(serverPlayer)) {
                    boolean snapped = ShockwaveSlicer.triggerEarlySnap(serverPlayer);
                    if (snapped) {
                        serverPlayer.displayClientMessage(Component.literal("§dCollapsed!"), true);
                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SpatialPathWalker.deactivate(serverPlayer);
        }
    }

    private void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SpatialPathWalker.deactivate(serverPlayer);
        }
    }

    private void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SpatialPathWalker.deactivate(serverPlayer);
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        ShockwaveSlicer.clearAllTasks();
        RatioCollapseManager.clearAllTasks();
        SupernovaManager.clearAllTasks();
        AtomicManager.clearAllTasks();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SpatialBridge.clearVisuals(level);
            SpatialDisplacementManager.emergencyRestoreAll(level);
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        SpatialDisplacementManager.clearRuntimeState();
        SpatialBridge.resetRuntime();
        SpatialPathWalker.resetRuntime();
    }

    private void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            DisplacementPersistentState state = DisplacementPersistentState.getServerState(level);
            state.restoreAndCleanup(level);
        }
    }
}
