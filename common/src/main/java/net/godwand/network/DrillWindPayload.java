package net.godwand.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DrillWindPayload(long id, double x, double y, double z, float radius, float angle, float speed)
        implements CustomPacketPayload {
    public static final Type<DrillWindPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("godwand", "drill_wind"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DrillWindPayload> CODEC = new StreamCodec<>() {
        public DrillWindPayload decode(RegistryFriendlyByteBuf buf) {
            return new DrillWindPayload(buf.readLong(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat());
        }
        public void encode(RegistryFriendlyByteBuf buf, DrillWindPayload value) {
            buf.writeLong(value.id());
            buf.writeDouble(value.x()); buf.writeDouble(value.y()); buf.writeDouble(value.z());
            buf.writeFloat(value.radius()); buf.writeFloat(value.angle()); buf.writeFloat(value.speed());
        }
    };
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
