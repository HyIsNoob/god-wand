package net.godwand.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.godwand.GodWandMod;
import net.godwand.config.GodWandConfig;
import net.godwand.entity.ArcaneOrbEntity;
import net.godwand.entity.StoneCannonProjectileEntity;
import net.godwand.item.GodWandItem;
import net.godwand.network.CannonPressurePayload;
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
import net.minecraft.resources.ResourceLocation;
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
                new ResourceLocation(GodWandMod.MOD_ID, "arcane_orb"),
                EntityType.Builder.<ArcaneOrbEntity>of(ArcaneOrbEntity::new, MobCategory.MISC)
                        .sized(0.5f, 0.5f)
                        .build("godwand:arcane_orb")
        );

        GodWandMod.STONE_CANNON_ENTITY_TYPE = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                new ResourceLocation(GodWandMod.MOD_ID, "stone_cannon"),
                EntityType.Builder.<StoneCannonProjectileEntity>of(StoneCannonProjectileEntity::new, MobCategory.MISC)
                        .sized(0.6f, 0.6f)
                        .build("godwand:stone_cannon")
        );

        GodWandMod.GOD_WAND_ITEM = Registry.register(
                BuiltInRegistries.ITEM,
                new ResourceLocation(GodWandMod.MOD_ID, "god_wand"),
                new GodWandItem(new Item.Properties())
        );

        ServerPlayNetworking.registerGlobalReceiver(net.godwand.network.AtomicSkipPayload.ID, (server,player,handler,buf,responseSender) -> {
            var payload=net.godwand.network.AtomicSkipPayload.read(buf);
            server.execute(() -> net.godwand.skill.AtomicManager.releaseMovement(player,payload.startedAt()));
        });

        ServerPlayNetworking.registerGlobalReceiver(SelectSkillPayload.ID, (server, player, handler, buf, responseSender) -> {
            SelectSkillPayload payload = SelectSkillPayload.read(buf);
            server.execute(() -> {
                if (!player.isAlive() || player.isSpectator() || player.isUsingItem()
                        || !payload.matchesHeldSlot(player.getInventory().selected)) return;
                InteractionHand hand = payload.slot() == 40 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                if (!player.getItemInHand(hand).is(GodWandMod.GOD_WAND_ITEM)) return;
                if (GodWandItem.getSkillMode(player.getItemInHand(hand)) == payload.mode()) return;
                GodWandItem.selectSkill(player, hand, payload.mode());
            });
        });

        GodWandMod.GODWAND_ITEM_GROUP = Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                GodWandMod.GODWAND_ITEM_GROUP_KEY,
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(GodWandMod.GOD_WAND_ITEM))
                        .title(Component.translatable("itemGroup.godwand.tab"))
                        .displayItems((displayContext, entries) -> entries.accept(GodWandMod.GOD_WAND_ITEM))
                        .build()
        );

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> entries.accept(GodWandMod.GOD_WAND_ITEM));

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
                            serverPlayer.displayClientMessage(Component.literal("§dCollapsed!"), true);
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
                            serverPlayer.displayClientMessage(Component.literal("§dCollapsed!"), true);
                            return InteractionResult.SUCCESS;
                        }
                    }
                }
            }
            return InteractionResult.PASS;
        });

        ServerTickEvents.END_WORLD_TICK.register(level -> {
            ShockwaveSlicer.tick(level);
            RatioCollapseManager.tick(level);
            SupernovaManager.tick(level);
            net.godwand.skill.AtomicManager.tick(level);
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
            net.godwand.skill.AtomicManager.clearAllTasks();
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

        ServerWorldEvents.LOAD.register((server, level) -> {
            DisplacementPersistentState state = DisplacementPersistentState.getServerState(level);
            state.restoreAndCleanup(level);
        });

        GodWandMod.LOGGER.info("God Wand Fabric initialized.");
    }
}
