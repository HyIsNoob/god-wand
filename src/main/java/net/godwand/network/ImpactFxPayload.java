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
