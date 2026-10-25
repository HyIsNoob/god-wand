package net.godwand.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record DrillWindPayload(long id, double x, double y, double z, float radius, float angle, float speed) implements GodWandPacket {
    public static final ResourceLocation ID = new ResourceLocation("godwand", "drill_wind");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeLong(id);
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeFloat(radius); buf.writeFloat(angle); buf.writeFloat(speed);
    }

    public static DrillWindPayload read(FriendlyByteBuf buf) {
        return new DrillWindPayload(
                buf.readLong(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat()
        );
    }
}
