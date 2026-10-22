package net.godwand;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
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
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
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

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                ItemStack stack = serverPlayer.getStackInHand(hand);
                if (stack.isOf(GOD_WAND_ITEM) && GodWandItem.getSkillMode(stack) == GodWandItem.MODE_ARCANE_ORB) {
                    if (ShockwaveSlicer.hasActiveTask(serverPlayer)) {
                        boolean snapped = ShockwaveSlicer.triggerEarlySnap(serverPlayer);
                        if (snapped) {
                            serverPlayer.sendMessage(Text.literal("§d§l✦ Singularity Collapsed! ✦"), true);
                            return ActionResult.SUCCESS;
                        }
                    }
                }
            }
            return ActionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                ItemStack stack = serverPlayer.getStackInHand(hand);
                if (stack.isOf(GOD_WAND_ITEM) && GodWandItem.getSkillMode(stack) == GodWandItem.MODE_ARCANE_ORB) {
                    if (ShockwaveSlicer.hasActiveTask(serverPlayer)) {
                        boolean snapped = ShockwaveSlicer.triggerEarlySnap(serverPlayer);
                        if (snapped) {
                            serverPlayer.sendMessage(Text.literal("§d§l✦ Singularity Collapsed! ✦"), true);
                            return ActionResult.SUCCESS;
                        }
                    }
                }
            }
            return ActionResult.PASS;
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
