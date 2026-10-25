package net.godwand.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record CinematicFxPayload(
        int eventType,
        double x,
        double y,
        double z,
        float intensity,
        int durationTicks
) implements GodWandPacket {

    public static final ResourceLocation ID = new ResourceLocation("godwand", "cinematic_fx");

    public static final int EVENT_TRAUMA_SHAKE        = 0;
    public static final int EVENT_FOV_PUNCH           = 1;
    public static final int EVENT_VACUUM_SILENCE      = 2;
    public static final int EVENT_METEOR_FOCUS        = 3;
    public static final int EVENT_METEOR_SLAM         = 4;
    public static final int EVENT_SUPERNOVA_COMPRESS  = 5;
    public static final int EVENT_SUPERNOVA_BURST     = 6;

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeInt(eventType);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(intensity);
        buf.writeInt(durationTicks);
    }

    public static CinematicFxPayload read(FriendlyByteBuf buf) {
        return new CinematicFxPayload(
                buf.readInt(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readInt()
        );
    }
}
