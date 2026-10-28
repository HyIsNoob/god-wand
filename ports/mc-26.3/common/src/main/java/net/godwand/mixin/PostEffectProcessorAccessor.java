package net.godwand.mixin;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = PostChain.class, remap = false)
public interface PostEffectProcessorAccessor {
    @Accessor("passes")
    List<PostPass> getPasses();
}
