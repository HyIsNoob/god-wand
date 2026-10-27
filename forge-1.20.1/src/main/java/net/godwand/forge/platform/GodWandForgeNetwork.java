package net.godwand.forge.platform;

import net.godwand.GodWandMod;
import net.godwand.client.CannonPressureRenderer;
import net.godwand.client.DrillWindRenderer;
import net.godwand.client.EarthDrillSoundscape;
import net.godwand.client.GodWandCinematicEngine;
import net.godwand.client.GodWandPostFxManager;
import net.godwand.client.ImpactDebrisRenderer;
import net.godwand.client.SingularityThreadRenderer;
import net.godwand.client.SpatialAuraRenderer;
import net.godwand.client.SupernovaAccretionRenderer;
import net.godwand.client.SupernovaSoundscape;
import net.godwand.item.GodWandItem;
import net.godwand.network.CannonPressurePayload;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.DrillWindPayload;
import net.godwand.network.GodWandPacket;
import net.godwand.network.ImpactFxPayload;
import net.godwand.network.SelectSkillPayload;
import net.godwand.network.SingularityThreadsPayload;
import net.godwand.network.SpatialAuraPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class GodWandForgeNetwork {
    private static final String PROTOCOL_VERSION = "1.3.0";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GodWandMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private GodWandForgeNetwork() {}

    public static void register() {
        int id = 0;

        CHANNEL.messageBuilder(DrillWindPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DrillWindPayload::write)
                .decoder(DrillWindPayload::read)
                .consumerMainThread((payload, ctx) -> {
                    handleDrillWind(payload);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(SpatialAuraPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SpatialAuraPayload::write)
                .decoder(SpatialAuraPayload::read)
                .consumerMainThread((payload, ctx) -> {
                    handleSpatialAura(payload);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(CannonPressurePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CannonPressurePayload::write)
                .decoder(CannonPressurePayload::read)
                .consumerMainThread((payload, ctx) -> {
                    handleCannonPressure(payload);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(SingularityThreadsPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SingularityThreadsPayload::write)
                .decoder(SingularityThreadsPayload::read)
                .consumerMainThread((payload, ctx) -> {
                    handleSingularityThreads(payload);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(ImpactFxPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ImpactFxPayload::write)
                .decoder(ImpactFxPayload::read)
                .consumerMainThread((payload, ctx) -> {
                    handleImpactFx(payload);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(CinematicFxPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CinematicFxPayload::write)
                .decoder(CinematicFxPayload::read)
                .consumerMainThread((payload, ctx) -> {
                    handleCinematicFx(payload);
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(net.godwand.network.AtomicFxPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(net.godwand.network.AtomicFxPayload::write)
                .decoder(net.godwand.network.AtomicFxPayload::read)
                .consumerMainThread((payload, ctx) -> {
                    handleAtomic(payload);
                    ctx.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(SelectSkillPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectSkillPayload::write)
                .decoder(SelectSkillPayload::read)
                .consumerMainThread((payload, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    if (player != null) {
                        ctx.get().enqueueWork(() -> {
                            if (!player.isAlive() || player.isSpectator() || player.isUsingItem()
                                    || !payload.matchesHeldSlot(player.getInventory().selected)) return;
                            InteractionHand hand = payload.slot() == 40 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                            if (!player.getItemInHand(hand).is(GodWandMod.GOD_WAND_ITEM)) return;
                            if (GodWandItem.getSkillMode(player.getItemInHand(hand)) == payload.mode()) return;
                            GodWandItem.selectSkill(player, hand, payload.mode());
                        });
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();
        CHANNEL.messageBuilder(net.godwand.network.AtomicSkipPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(net.godwand.network.AtomicSkipPayload::write)
                .decoder(net.godwand.network.AtomicSkipPayload::read)
                .consumerMainThread((payload,ctx) -> {
                    var player=ctx.get().getSender();
                    if(player!=null)ctx.get().enqueueWork(() -> net.godwand.skill.AtomicManager.releaseMovement(player,payload.startedAt()));
                    ctx.get().setPacketHandled(true);
                }).add();
    }

    public static void sendToPlayer(ServerPlayer player, GodWandPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToNearby(ServerLevel level, Vec3 pos, double radius, GodWandPacket packet) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(pos.x, pos.y, pos.z, radius, level.dimension())), packet);
    }

    public static void sendToServer(GodWandPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleAtomic(net.godwand.network.AtomicFxPayload payload) { net.godwand.client.AtomicRenderer.receive(payload); }

    @OnlyIn(Dist.CLIENT)
    private static void handleDrillWind(DrillWindPayload payload) {
        DrillWindRenderer.receive(payload);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleSpatialAura(SpatialAuraPayload payload) {
        SpatialAuraRenderer.receive(payload);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleCannonPressure(CannonPressurePayload payload) {
        CannonPressureRenderer.receive(payload);
        ImpactDebrisRenderer.receive(payload);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleSingularityThreads(SingularityThreadsPayload payload) {
        SingularityThreadRenderer.receive(payload);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleImpactFx(ImpactFxPayload payload) {
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
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleCinematicFx(CinematicFxPayload payload) {
        GodWandCinematicEngine.onCinematicEvent(payload);
    }
}
