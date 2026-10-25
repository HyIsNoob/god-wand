package net.godwand.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record SpatialAuraPayload(int playerId, boolean active, Vec3 hitNormal) implements GodWandPacket {
    public SpatialAuraPayload(int playerId, boolean active) { this(playerId, active, Vec3.ZERO); }
    public static final ResourceLocation ID = new ResourceLocation("godwand", "spatial_aura");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(playerId);
        buf.writeBoolean(active);
        buf.writeFloat((float)hitNormal.x);
        buf.writeFloat((float)hitNormal.y);
        buf.writeFloat((float)hitNormal.z);
    }

    public static SpatialAuraPayload read(FriendlyByteBuf buf) {
        return new SpatialAuraPayload(
                buf.readVarInt(),
                buf.readBoolean(),
                new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat())
        );
    }
}
