package net.godwand.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record AtomicSkipPayload(long startedAt) implements GodWandPacket {
    public static final ResourceLocation ID=new ResourceLocation("godwand","atomic_skip");
    public ResourceLocation getId() { return ID; }
    public void write(FriendlyByteBuf buf) { buf.writeLong(startedAt); }
    public static AtomicSkipPayload read(FriendlyByteBuf buf) { return new AtomicSkipPayload(buf.readLong()); }
}
