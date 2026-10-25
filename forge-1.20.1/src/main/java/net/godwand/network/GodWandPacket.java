package net.godwand.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public interface GodWandPacket {
    ResourceLocation getId();
    void write(FriendlyByteBuf buf);
}
