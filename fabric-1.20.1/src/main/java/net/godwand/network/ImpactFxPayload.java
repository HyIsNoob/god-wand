package net.godwand.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record ImpactFxPayload(double x, double y, double z, int fxType, int durationTicks, float intensity) implements GodWandPacket {
    public static final ResourceLocation ID = new ResourceLocation("godwand", "impact_fx");

    public static final int TYPE_DETONATION = 0;
    public static final int TYPE_IMPLOSION = 1;
    public static final int TYPE_COLLAPSE = 2;
    public static final int TYPE_SUPERNOVA = 3;
    public static final int TYPE_BLACK_HOLE = 4;
    public static final int TYPE_SEISMIC_SLAM = 5;
    public static final int TYPE_GRAVITY_CORE = 6;
    public static final int TYPE_REASSEMBLY = 7;
    public static final int TYPE_SUPERNOVA_IMPACT = 8;
    public static final int SUPERNOVA_IMPACT_TICKS = 4;

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeInt(fxType);
        buf.writeInt(durationTicks);
        buf.writeFloat(intensity);
    }

    public static ImpactFxPayload read(FriendlyByteBuf buf) {
        return new ImpactFxPayload(
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readInt(),
                buf.readInt(),
                buf.readFloat()
        );
    }
}
