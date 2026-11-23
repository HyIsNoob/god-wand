package net.godwand.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Prevents dedicated Survival servers (allow-flight=false) from kicking players for "Flying is not enabled"
 * while legally airborne using Block Wings or Air-Step Block Stairs.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerAccessor {
    @Accessor("aboveGroundTickCount")
    void godwand$setAboveGroundTickCount(int ticks);
}
