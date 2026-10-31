package net.godwand.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.godwand.GodWandMod;
import net.godwand.config.GodWandConfig;
import net.godwand.entity.ArcaneOrbEntity;
import net.godwand.entity.StoneCannonProjectileEntity;
import net.godwand.item.GodWandItem;
import net.godwand.network.CannonPressurePayload;
import net.godwand.network.AtomicFxPayload;
import net.godwand.skill.AtomicManager;
import net.godwand.network.CinematicFxPayload;
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
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class GodWandFabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        GodWandConfig.loadServer(FabricLoader.getInstance().getConfigDir());
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> net.godwand.command.GodWandCommands.register(dispatcher));

        GodWandMod.ARCANE_ORB_ENTITY_TYPE = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(GodWandMod.MOD_ID, "arcane_orb"),
                EntityType.Builder.<ArcaneOrbEntity>of(ArcaneOrbEntity::new, MobCategory.MISC)
                        .sized(0.5f, 0.5f)
                        .build(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath("godwand","arcane_orb")))
        );

        GodWandMod.STONE_CANNON_ENTITY_TYPE = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(GodWandMod.MOD_ID, "stone_cannon"),
                EntityType.Builder.<StoneCannonProjectileEntity>of(StoneCannonProjectileEntity::new, MobCategory.MISC)
                        .sized(0.6f, 0.6f)
                        .build(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath("godwand","stone_cannon")))
        );

        GodWandMod.GOD_WAND_ITEM = Registry.register(
                BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath(GodWandMod.MOD_ID, "god_wand"),
                new GodWandItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,Identifier.fromNamespaceAndPath("godwand","god_wand"))))
        );

        PayloadTypeRegistry.clientboundPlay().register(DrillWindPayload.ID, DrillWindPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AtomicFxPayload.ID, AtomicFxPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(net.godwand.network.SkillCooldownPayload.ID, net.godwand.network.SkillCooldownPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SpatialAuraPayload.ID, SpatialAuraPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CannonPressurePayload.ID, CannonPressurePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SingularityThreadsPayload.ID, SingularityThreadsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ImpactFxPayload.ID, ImpactFxPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CinematicFxPayload.ID, CinematicFxPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SelectSkillPayload.ID, SelectSkillPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(net.godwand.network.AtomicSkipPayload.ID, net.godwand.network.AtomicSkipPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.godwand.network.AtomicSkipPayload.ID, (payload, context) ->
                context.server().execute(() -> net.godwand.skill.AtomicManager.releaseMovement(context.player(),payload.startedAt())));


        ServerPlayNetworking.registerGlobalReceiver(SelectSkillPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!player.isAlive() || player.isSpectator() || player.isUsingItem()
                    || !payload.matchesHeldSlot(player.getInventory().getSelectedSlot())) return;
            InteractionHand hand = payload.slot() == 40 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            if (!player.getItemInHand(hand).is(GodWandMod.GOD_WAND_ITEM)) return;
            if (GodWandItem.getSkillMode(player.getItemInHand(hand)) == payload.mode()) return;
            GodWandItem.selectSkill(player, hand, payload.mode());
        });

        GodWandMod.GODWAND_ITEM_GROUP = Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                GodWandMod.GODWAND_ITEM_GROUP_KEY,
                FabricCreativeModeTab.builder()
                        .icon(() -> new ItemStack(GodWandMod.GOD_WAND_ITEM))
                        .title(Component.translatable("itemGroup.godwand.tab"))
                        .displayItems((displayContext, entries) -> entries.accept(GodWandMod.GOD_WAND_ITEM))
                        .build()
        );

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(entries -> entries.accept(GodWandMod.GOD_WAND_ITEM));

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                SpatialPathWalker.tickPlayer(player);
            }
        });

        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = serverPlayer.getItemInHand(hand);
                if (stack.is(GodWandMod.GOD_WAND_ITEM) && GodWandItem.getSkillMode(stack) == GodWandItem.MODE_ARCANE_ORB) {
                    if (ShockwaveSlicer.hasActiveTask(serverPlayer)) {
                        boolean snapped = ShockwaveSlicer.triggerEarlySnap(serverPlayer);
                        if (snapped) {
                            serverPlayer.sendOverlayMessage(Component.literal("§dCollapsed!"));
                            return InteractionResult.SUCCESS;
                        }
                    }
                }
            }
            return InteractionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = serverPlayer.getItemInHand(hand);
                if (stack.is(GodWandMod.GOD_WAND_ITEM) && GodWandItem.getSkillMode(stack) == GodWandItem.MODE_ARCANE_ORB) {
                    if (ShockwaveSlicer.hasActiveTask(serverPlayer)) {
                        boolean snapped = ShockwaveSlicer.triggerEarlySnap(serverPlayer);
                        if (snapped) {
                            serverPlayer.sendOverlayMessage(Component.literal("§dCollapsed!"));
                            return InteractionResult.SUCCESS;
                        }
                    }
                }
            }
            return InteractionResult.PASS;
        });

        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            ShockwaveSlicer.tick(level);
            RatioCollapseManager.tick(level);
            SupernovaManager.tick(level);
            AtomicManager.tick(level);
            SpatialBridge.tickWorld(level);
            SpatialDisplacementManager.tick(level);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SpatialPathWalker.deactivate(handler.player);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                SpatialPathWalker.deactivate(player);
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            SpatialPathWalker.deactivate(newPlayer);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ShockwaveSlicer.clearAllTasks();
            RatioCollapseManager.clearAllTasks();
            SupernovaManager.clearAllTasks();
            AtomicManager.clearAllTasks();
            for (ServerLevel level : server.getAllLevels()) {
                SpatialBridge.clearVisuals(level);
                SpatialDisplacementManager.emergencyRestoreAll(level);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SpatialDisplacementManager.clearRuntimeState();
            SpatialBridge.resetRuntime();
            SpatialPathWalker.resetRuntime();
        });

        ServerLevelEvents.LOAD.register((server, level) -> {
            DisplacementPersistentState state = DisplacementPersistentState.getServerState(level);
            state.restoreAndCleanup(level);
        });

        GodWandMod.LOGGER.info("God Wand Fabric initialized.");
    }
}
