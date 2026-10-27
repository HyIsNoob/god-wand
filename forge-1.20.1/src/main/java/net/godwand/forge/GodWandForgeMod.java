package net.godwand.forge;

import net.godwand.GodWandMod;
import net.godwand.config.GodWandConfig;
import net.godwand.entity.ArcaneOrbEntity;
import net.godwand.entity.StoneCannonProjectileEntity;
import net.godwand.forge.client.GodWandForgeClientMod;
import net.godwand.forge.platform.GodWandForgeNetwork;
import net.godwand.item.GodWandItem;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(GodWandMod.MOD_ID)
public class GodWandForgeMod {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, GodWandMod.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, GodWandMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GodWandMod.MOD_ID);

    public static final RegistryObject<GodWandItem> GOD_WAND = ITEMS.register("god_wand", () -> {
        GodWandItem item = new GodWandItem(new Item.Properties());
        GodWandMod.GOD_WAND_ITEM = item;
        return item;
    });

    public static final RegistryObject<EntityType<ArcaneOrbEntity>> ARCANE_ORB = ENTITY_TYPES.register("arcane_orb", () -> {
        EntityType<ArcaneOrbEntity> type = EntityType.Builder.<ArcaneOrbEntity>of(ArcaneOrbEntity::new, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                .build("godwand:arcane_orb");
        GodWandMod.ARCANE_ORB_ENTITY_TYPE = type;
        return type;
    });

    public static final RegistryObject<EntityType<StoneCannonProjectileEntity>> STONE_CANNON = ENTITY_TYPES.register("stone_cannon", () -> {
        EntityType<StoneCannonProjectileEntity> type = EntityType.Builder.<StoneCannonProjectileEntity>of(StoneCannonProjectileEntity::new, MobCategory.MISC)
                .sized(0.6f, 0.6f)
                .build("godwand:stone_cannon");
        GodWandMod.STONE_CANNON_ENTITY_TYPE = type;
        return type;
    });

    public static final RegistryObject<CreativeModeTab> GODWAND_TAB = CREATIVE_MODE_TABS.register("godwand_tab", () -> {
        CreativeModeTab tab = CreativeModeTab.builder()
                .icon(() -> new ItemStack(GOD_WAND.get()))
                .title(Component.translatable("itemGroup.godwand.tab"))
                .displayItems((parameters, output) -> output.accept(GOD_WAND.get()))
                .build();
        GodWandMod.GODWAND_ITEM_GROUP = tab;
        return tab;
    });

    public GodWandForgeMod() {
        this(FMLJavaModLoadingContext.get().getModEventBus());
    }

    public GodWandForgeMod(IEventBus modBus) {
        GodWandConfig.loadServer(FMLPaths.CONFIGDIR.get());

        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        CREATIVE_MODE_TABS.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::buildCreativeModeTabContents);

        MinecraftForge.EVENT_BUS.register(this);

        if (FMLEnvironment.dist.isClient()) {
            GodWandForgeClientMod.init(modBus);
        }

        GodWandMod.LOGGER.info("God Wand Forge initialized.");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            GodWandForgeNetwork.register();
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

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SpatialPathWalker.tickPlayer(player);
        }
        for (ServerLevel level : server.getAllLevels()) {
            ShockwaveSlicer.tick(level);
            RatioCollapseManager.tick(level);
            SupernovaManager.tick(level);
            net.godwand.skill.AtomicManager.tick(level);
            SpatialBridge.tickWorld(level);
            SpatialDisplacementManager.tick(level);
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
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

    @SubscribeEvent
    public void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
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

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
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

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SpatialPathWalker.deactivate(serverPlayer);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SpatialPathWalker.deactivate(serverPlayer);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SpatialPathWalker.deactivate(serverPlayer);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ShockwaveSlicer.clearAllTasks();
        RatioCollapseManager.clearAllTasks();
        SupernovaManager.clearAllTasks();
        net.godwand.skill.AtomicManager.clearAllTasks();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SpatialBridge.clearVisuals(level);
            SpatialDisplacementManager.emergencyRestoreAll(level);
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        SpatialDisplacementManager.clearRuntimeState();
        SpatialBridge.resetRuntime();
        SpatialPathWalker.resetRuntime();
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            DisplacementPersistentState state = DisplacementPersistentState.getServerState(level);
            state.restoreAndCleanup(level);
        }
    }
}
