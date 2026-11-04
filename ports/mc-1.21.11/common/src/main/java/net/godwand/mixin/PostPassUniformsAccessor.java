package net.godwand.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;

@Mixin(value = PostPass.class)
public interface PostPassUniformsAccessor {
    @Accessor("customUniforms") Map<String, GpuBuffer> godwand$uniforms();
}
