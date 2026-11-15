package net.godwand.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One Blue/Red/Purple event (mode 8). ownerId: the Blue owner or Red shooter; otherId: the Red shooter on PURPLE, else -1.
 * x,y,z: Blue centre, Red origin or Purple centre; dx,dy,dz: Red direction (unit) on RED_FIRE; state: BlueState ordinal on BLUE.
 */
public record BlueRedPayload(int kind, int ownerId, int otherId, long tick, double x, double y, double z,
                             double dx, double dy, double dz, int state) implements CustomPacketPayload {
    public static final int BLUE = 0, BLUE_GONE = 1, BLUE_SHATTER = 2, RED_CHARGE = 3, RED_CANCEL = 4, RED_FIRE = 5, RED_END = 6, PURPLE = 7;

    public BlueRedPayload {
        if (kind < BLUE || kind > PURPLE || state < 0 || state > 2) throw new IllegalArgumentException("Invalid Blue/Red event");
        for (double c : new double[]{x, y, z})
            if (!Double.isFinite(c) || Math.abs(c) > 30_000_000) throw new IllegalArgumentException("Invalid Blue/Red coordinate");
        for (double c : new double[]{dx, dy, dz})
            if (!Double.isFinite(c) || Math.abs(c) > 2) throw new IllegalArgumentException("Invalid Blue/Red direction");
    }

    public static final Type<BlueRedPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("godwand", "blue_red"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueRedPayload> CODEC = new StreamCodec<>() {
        public BlueRedPayload decode(RegistryFriendlyByteBuf buf) {
            return new BlueRedPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1, buf.readLong(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
        }
        public void encode(RegistryFriendlyByteBuf buf, BlueRedPayload v) {
            buf.writeVarInt(v.kind); buf.writeVarInt(v.ownerId); buf.writeVarInt(v.otherId + 1); buf.writeLong(v.tick);
            buf.writeDouble(v.x); buf.writeDouble(v.y); buf.writeDouble(v.z);
            buf.writeDouble(v.dx); buf.writeDouble(v.dy); buf.writeDouble(v.dz); buf.writeVarInt(v.state);
        }
    };
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
