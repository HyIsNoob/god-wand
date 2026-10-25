package net.godwand.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record CannonPressurePayload(Vec3 center, Vec3 direction, int level, Vec3 surfaceNormal) implements GodWandPacket {
    public CannonPressurePayload(Vec3 center, Vec3 direction, int level) {
        this(center, direction, level, direction.reverse());
    }

    public static final ResourceLocation ID = new ResourceLocation("godwand", "cannon_pressure");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf b) {
        b.writeDouble(center.x); b.writeDouble(center.y); b.writeDouble(center.z);
        b.writeFloat((float)direction.x); b.writeFloat((float)direction.y); b.writeFloat((float)direction.z);
        b.writeVarInt(level);
        b.writeFloat((float)surfaceNormal.x); b.writeFloat((float)surfaceNormal.y); b.writeFloat((float)surfaceNormal.z);
    }

    public static CannonPressurePayload read(FriendlyByteBuf b) {
        return new CannonPressurePayload(
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                new Vec3(b.readFloat(), b.readFloat(), b.readFloat()),
                Mth.clamp(b.readVarInt(), 1, 4),
                new Vec3(b.readFloat(), b.readFloat(), b.readFloat())
        );
    }
}
