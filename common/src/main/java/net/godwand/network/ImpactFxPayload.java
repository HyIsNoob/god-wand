package net.godwand.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ImpactFxPayload(double x, double y, double z, int fxType, int durationTicks, float intensity) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ImpactFxPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("godwand", "impact_fx"));

    public static final int TYPE_DETONATION = 0;
    public static final int TYPE_IMPLOSION = 1;
    public static final int TYPE_COLLAPSE = 2;
    public static final int TYPE_SUPERNOVA = 3;
    public static final int TYPE_BLACK_HOLE = 4;
    public static final int TYPE_SEISMIC_SLAM = 5;
    public static final int TYPE_GRAVITY_CORE = 6;
    public static final int TYPE_REASSEMBLY = 7;
    public static final int TYPE_SUPERNOVA_IMPACT = 8;
    public static final int SUPERNOVA_IMPACT_TICKS = 8;

    public static final StreamCodec<RegistryFriendlyByteBuf, ImpactFxPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, ImpactFxPayload::x,
            ByteBufCodecs.DOUBLE, ImpactFxPayload::y,
            ByteBufCodecs.DOUBLE, ImpactFxPayload::z,
            ByteBufCodecs.INT, ImpactFxPayload::fxType,
            ByteBufCodecs.INT, ImpactFxPayload::durationTicks,
            ByteBufCodecs.FLOAT, ImpactFxPayload::intensity,
            ImpactFxPayload::new
    );

    @Override
    public Type<ImpactFxPayload> type() {
        return ID;
    }
}
