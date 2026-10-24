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
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.network.SelectSkillPayload;
import net.godwand.entity.ArcaneOrbEntity;
import net.godwand.entity.StoneCannonProjectileEntity;
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
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GodWandMod implements ModInitializer {
    public static final String MOD_ID = "godwand";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static EntityType<ArcaneOrbEntity> ARCANE_ORB_ENTITY_TYPE;
    public static EntityType<StoneCannonProjectileEntity> STONE_CANNON_ENTITY_TYPE;
    public static Item GOD_WAND_ITEM;
    public static final RegistryKey<ItemGroup> GODWAND_ITEM_GROUP_KEY = RegistryKey.of(
            RegistryKeys.ITEM_GROUP,
            Identifier.of(MOD_ID, "godwand_tab")
    );
    public static ItemGroup GODWAND_ITEM_GROUP;

    @Override
    public void onInitialize() {
        net.godwand.config.GodWandConfig.loadServer(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
        ARCANE_ORB_ENTITY_TYPE = Registry.register(
                Registries.ENTITY_TYPE,
                Identifier.of(MOD_ID, "arcane_orb"),
                EntityType.Builder.<ArcaneOrbEntity>create(ArcaneOrbEntity::new, SpawnGroup.MISC)
                        .dimensions(0.5f, 0.5f)
                        .build()
        );

        STONE_CANNON_ENTITY_TYPE = Registry.register(
                Registries.ENTITY_TYPE,
                Identifier.of(MOD_ID, "stone_cannon"),
                EntityType.Builder.<StoneCannonProjectileEntity>create(StoneCannonProjectileEntity::new, SpawnGroup.MISC)
                        .dimensions(0.6f, 0.6f)
                        .build()
        );

        GOD_WAND_ITEM = Registry.register(
                Registries.ITEM,
                Identifier.of(MOD_ID, "god_wand"),
                new GodWandItem(new Item.Settings())
        );

        PayloadTypeRegistry.playS2C().register(net.godwand.network.DrillWindPayload.ID, net.godwand.network.DrillWindPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.godwand.network.SpatialAuraPayload.ID, net.godwand.network.SpatialAuraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.godwand.network.CannonPressurePayload.ID, net.godwand.network.CannonPressurePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.godwand.network.SingularityThreadsPayload.ID, net.godwand.network.SingularityThreadsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ImpactFxPayload.ID, ImpactFxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CinematicFxPayload.ID, CinematicFxPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectSkillPayload.ID, SelectSkillPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SelectSkillPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (!player.isAlive() || player.isSpectator() || player.isUsingItem()
                    || !payload.matchesHeldSlot(player.getInventory().selectedSlot)) return;
            Hand hand = payload.slot() == 40 ? Hand.OFF_HAND : Hand.MAIN_HAND;
            if (!player.getStackInHand(hand).isOf(GOD_WAND_ITEM)) return;
            if (GodWandItem.getSkillMode(player.getStackInHand(hand)) == payload.mode()) return;
            GodWandItem.selectSkill(player, hand, payload.mode());
        });

        GODWAND_ITEM_GROUP = Registry.register(
                Registries.ITEM_GROUP,
                GODWAND_ITEM_GROUP_KEY,
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(GOD_WAND_ITEM))
                        .displayName(Text.translatable("itemGroup.godwand.tab"))
                        .entries((context, entries) -> entries.add(GOD_WAND_ITEM))
                        .build()
        );

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
                            serverPlayer.sendMessage(Text.literal("§dCollapsed!"), true);
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
                            serverPlayer.sendMessage(Text.literal("§dCollapsed!"), true);
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
            net.godwand.skill.SpatialBridge.tickWorld(world);
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
                net.godwand.skill.SpatialBridge.clearVisuals(world);
                SpatialDisplacementManager.emergencyRestoreAll(world);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SpatialDisplacementManager.clearRuntimeState();
            net.godwand.skill.SpatialBridge.resetRuntime();
        });

        ServerWorldEvents.LOAD.register((server, world) -> {
            // Restore any crash-orphaned displaced blocks and cleanup orphaned display entities
            DisplacementPersistentState state = DisplacementPersistentState.getServerState(world);
            state.restoreAndCleanup(world);
        });

        LOGGER.info("God Wand Mod initialized.");
    }
}
