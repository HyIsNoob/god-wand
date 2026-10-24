package net.godwand.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DrillWindPayload(long id, double x, double y, double z, float radius, float angle, float speed)
        implements CustomPayload {
    public static final Id<DrillWindPayload> ID = new Id<>(Identifier.of("godwand", "drill_wind"));
    public static final PacketCodec<RegistryByteBuf, DrillWindPayload> CODEC = new PacketCodec<>() {
        public DrillWindPayload decode(RegistryByteBuf buf) {
            return new DrillWindPayload(buf.readLong(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat());
        }
        public void encode(RegistryByteBuf buf, DrillWindPayload value) {
            buf.writeLong(value.id());
            buf.writeDouble(value.x()); buf.writeDouble(value.y()); buf.writeDouble(value.z());
            buf.writeFloat(value.radius()); buf.writeFloat(value.angle()); buf.writeFloat(value.speed());
        }
    };
    public Id<? extends CustomPayload> getId() { return ID; }
}
